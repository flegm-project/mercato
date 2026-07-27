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
  # A Command Line Tools install still provides an xcodebuild shim that fails
  # when run, so probe it rather than just checking that it is on PATH.
  xcodebuild -version >/dev/null 2>&1 || die \
    "xcodebuild is not usable. Full Xcode is required (Command Line Tools alone is not enough):
     install Xcode, then run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
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

  echo "==> assembling xcframework"
  rm -rf "$OUT/ios/Mercato.xcframework"
  xcodebuild -create-xcframework \
    -library "$CORE/target/aarch64-apple-ios/release/libmercato_ffi.a" \
    -library "$sim/libmercato_ffi.a" \
    -output "$OUT/ios/Mercato.xcframework"
  echo "    -> $OUT/ios/Mercato.xcframework"
}

android() {
  command -v cargo-ndk >/dev/null 2>&1 || die \
    "cargo-ndk not found. Install it with: cargo install cargo-ndk"
  [ -n "${ANDROID_NDK_HOME:-}${ANDROID_HOME:-}" ] || die \
    "Android SDK/NDK not found. Install Android Studio and set ANDROID_HOME."
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
