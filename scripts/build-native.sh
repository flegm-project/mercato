#!/usr/bin/env bash
# Build the Rust core for the native apps.
#
#   ./scripts/build-native.sh bindings   generate Swift + Kotlin sources
#   ./scripts/build-native.sh ios        build the xcframework
#   ./scripts/build-native.sh android    build the per-ABI .so files
#   ./scripts/build-native.sh all
#
# Outputs go to build/ (gitignored). Bindings are generated, never committed:
# the Rust FFI surface in core/mercato-ffi is the single source of truth.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CORE="$ROOT/core"
OUT="$ROOT/build"

IOS_TARGETS=(aarch64-apple-ios aarch64-apple-ios-sim x86_64-apple-ios)
ANDROID_TARGETS=(aarch64-linux-android armv7-linux-androideabi x86_64-linux-android)

die() { echo "error: $*" >&2; exit 1; }

need_targets() {
  command -v rustup >/dev/null 2>&1 || die \
    "rustup is required to cross-compile. A Homebrew-only Rust cannot add targets.
     Install it from https://rustup.rs and re-run."
  local missing=()
  for t in "$@"; do
    rustup target list --installed | grep -qx "$t" || missing+=("$t")
  done
  if [ ${#missing[@]} -gt 0 ]; then
    echo "installing rust targets: ${missing[*]}"
    rustup target add "${missing[@]}"
  fi
}

bindings() {
  echo "==> generating bindings"
  ( cd "$CORE" && cargo build -p mercato-ffi )
  local lib="$CORE/target/debug/libmercato_ffi.dylib"
  [ -f "$lib" ] || lib="$CORE/target/debug/libmercato_ffi.so"
  [ -f "$lib" ] || die "no mercato-ffi dynamic library found; did the build succeed?"

  rm -rf "$OUT/bindings"
  mkdir -p "$OUT/bindings"
  ( cd "$CORE" && cargo run -q --bin uniffi-bindgen -- \
      generate --library "$lib" --language swift --out-dir "$OUT/bindings/swift" )
  ( cd "$CORE" && cargo run -q --bin uniffi-bindgen -- \
      generate --library "$lib" --language kotlin --out-dir "$OUT/bindings/kotlin" )
  echo "    -> $OUT/bindings"
}

ios() {
  # A Command Line Tools install still ships an xcodebuild shim that fails when
  # run, so probe it rather than just checking PATH. If the active developer
  # directory is the CLT one but Xcode is installed, point at Xcode ourselves:
  # that needs no sudo, unlike `xcode-select -s`.
  if ! xcodebuild -version >/dev/null 2>&1; then
    if [ -d /Applications/Xcode.app/Contents/Developer ]; then
      export DEVELOPER_DIR=/Applications/Xcode.app/Contents/Developer
    fi
  fi
  xcodebuild -version >/dev/null 2>&1 || die \
    "xcodebuild is not usable. Full Xcode is required (Command Line Tools alone is not enough).
     Install Xcode, then either run
       sudo xcode-select -s /Applications/Xcode.app/Contents/Developer
     or export DEVELOPER_DIR to that path."
  need_targets "${IOS_TARGETS[@]}"

  echo "==> building iOS static libs"
  for t in "${IOS_TARGETS[@]}"; do
    ( cd "$CORE" && cargo build -p mercato-ffi --release --target "$t" )
  done

  # The two simulator slices must be fattened before they can share a slice.
  local sim="$OUT/ios/sim"
  mkdir -p "$sim"
  lipo -create \
    "$CORE/target/aarch64-apple-ios-sim/release/libmercato_ffi.a" \
    "$CORE/target/x86_64-apple-ios/release/libmercato_ffi.a" \
    -output "$sim/libmercato_ffi.a"

  # The xcframework must carry the C header and a module map, otherwise the
  # generated mercato_ffi.swift cannot import the FFI module. Xcode expects the
  # map to be named module.modulemap, while uniffi emits it under the module's
  # own name, so it is copied rather than referenced.
  local headers="$OUT/ios/headers"
  rm -rf "$headers"
  mkdir -p "$headers"
  cp "$OUT/bindings/swift/mercato_ffiFFI.h" "$headers/"
  cp "$OUT/bindings/swift/mercato_ffiFFI.modulemap" "$headers/module.modulemap"

  echo "==> assembling xcframework"
  rm -rf "$OUT/ios/Mercato.xcframework"
  xcodebuild -create-xcframework \
    -library "$CORE/target/aarch64-apple-ios/release/libmercato_ffi.a" -headers "$headers" \
    -library "$sim/libmercato_ffi.a" -headers "$headers" \
    -output "$OUT/ios/Mercato.xcframework"
  echo "    -> $OUT/ios/Mercato.xcframework"
}

android() {
  command -v cargo-ndk >/dev/null 2>&1 || die \
    "cargo-ndk not found. Install it with: cargo install cargo-ndk"

  # Homebrew's android-commandlinetools does not live where the Android Studio
  # installer puts things, so fall back to it before giving up.
  if [ -z "${ANDROID_HOME:-}" ] && [ -d /opt/homebrew/share/android-commandlinetools ]; then
    export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
  fi
  [ -n "${ANDROID_HOME:-}" ] || die \
    "Android SDK not found. Install it with: brew install --cask android-commandlinetools
     (or install Android Studio), then export ANDROID_HOME."
  export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

  # cargo-ndk resolves the toolchain through ANDROID_NDK_HOME; pick the
  # highest installed NDK when the caller has not pinned one.
  if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    local ndk
    ndk=$(ls -1d "$ANDROID_HOME"/ndk/*/ 2>/dev/null | sort -V | tail -1)
    [ -n "$ndk" ] || die \
      "No NDK found under $ANDROID_HOME/ndk. Install one with:
       sdkmanager --install 'ndk;29.0.14206865'"
    export ANDROID_NDK_HOME="${ndk%/}"
  fi
  echo "    using NDK $ANDROID_NDK_HOME"

  need_targets "${ANDROID_TARGETS[@]}"

  echo "==> building Android shared libs"
  mkdir -p "$OUT/android/jniLibs"
  ( cd "$CORE" && cargo ndk -o "$OUT/android/jniLibs" \
      -t arm64-v8a -t armeabi-v7a -t x86_64 \
      build -p mercato-ffi --release )
  echo "    -> $OUT/android/jniLibs"
}

case "${1:-all}" in
  bindings) bindings ;;
  ios)      bindings; ios ;;
  android)  bindings; android ;;
  all)      bindings; ios; android ;;
  *)        die "unknown command '${1}'. Use: bindings | ios | android | all" ;;
esac
