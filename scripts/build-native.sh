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

# Build through the rustup toolchain, not whatever `cargo` PATH happens to
# resolve first. A Homebrew Rust carries only the host target, so a machine
# with both installed cross-compiles fine from a login shell and fails from a
# script with "the aarch64-apple-ios target may not be installed", while
# `rustup target list --installed` cheerfully shows it there.
if [ -x "$HOME/.cargo/bin/cargo" ]; then
  PATH="$HOME/.cargo/bin:$PATH"
  export PATH
fi

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

  # uniffi picks its cleaner at runtime: it probes for java.lang.ref.Cleaner
  # with Class.forName and falls back to a JNA cleaner when the class is
  # missing, so JavaLangRefCleaner is only ever constructed on API 33 and up.
  # Android lint cannot follow a reflective probe, so it reports three NewApi
  # errors against a minSdk of 26 and fails the build. Annotating the two
  # declarations states the guarantee the probe already provides.
  kt="$OUT/bindings/kotlin/uniffi/mercato_ffi/mercato_ffi.kt"
  if [ -f "$kt" ] && ! grep -q "android.annotation.SuppressLint" "$kt"; then
    python3 - "$kt" <<'PYEOF'
import sys
p = sys.argv[1]
lines = open(p, encoding="utf-8").read().split("\n")
# The import has to land after the package declaration, not at the top of the
# file: uniffi opens with comments and a @file: annotation.
for i, l in enumerate(lines):
    if l.startswith("package "):
        lines[i + 1 : i + 1] = ["", "import android.annotation.SuppressLint"]
        break
src = "\n".join(lines)
for decl in (
    "private fun UniffiCleaner.Companion.create",
    "private class JavaLangRefCleaner",
    "private class JavaLangRefCleanable",
):
    src = src.replace("\n" + decl, '\n@SuppressLint("NewApi")\n' + decl, 1)
open(p, "w", encoding="utf-8").write(src)
PYEOF
    echo "    -> annotated the uniffi cleaner for lint"
  fi

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

  # Android 15 runs on devices with 16 KB memory pages, and Play refuses any
  # upload whose 64-bit libraries are laid out for 4 KB ones. NDK r28 is the
  # first release that links them correctly without being asked; r27 and older
  # need the flags below, and Play separately warns about libraries built with
  # them because a build that assumes PAGE_SIZE == 4096 can still crash. The
  # highest installed NDK is picked above, so this only fires on a machine
  # whose newest NDK predates r28 -- exactly the machine that would otherwise
  # produce an upload Play rejects, hours later, with no clue where it came
  # from.
  local ndk_rev ndk_major
  ndk_rev=$(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_NDK_HOME/source.properties" 2>/dev/null)
  ndk_major=${ndk_rev%%.*}
  if [ -z "$ndk_major" ]; then
    die "cannot read Pkg.Revision from $ANDROID_NDK_HOME/source.properties.
     That path does not look like an NDK; point ANDROID_NDK_HOME at one."
  fi
  if [ "$ndk_major" -lt 28 ]; then
    die "NDK $ndk_rev is too old: it links libraries for 4 KB memory pages, and
     Play rejects those. Install r28 or newer with
       sdkmanager --install 'ndk;28.2.13676358'
     and either let this script pick it up or set ANDROID_NDK_HOME to it."
  fi

  need_targets "${ANDROID_TARGETS[@]}"

  echo "==> building Android shared libs"
  mkdir -p "$OUT/android/jniLibs"
  # Belt and braces on top of the r28 floor: stating the page size explicitly
  # means the output stays correct even if a future toolchain changes its
  # default back, and it costs a few kilobytes of padding.
  ( cd "$CORE" && \
    RUSTFLAGS="${RUSTFLAGS:-} -C link-arg=-Wl,-z,max-page-size=16384" \
    cargo ndk -o "$OUT/android/jniLibs" \
      -t arm64-v8a -t armeabi-v7a -t x86_64 \
      build -p mercato-ffi --release )
  echo "    -> $OUT/android/jniLibs"

  # The same check the release build runs, applied to what was just produced,
  # so a bad toolchain is caught here rather than in the Play Console.
  python3 "$ROOT/scripts/check-16k.py" "$OUT/android/jniLibs"
}

case "${1:-all}" in
  bindings) bindings ;;
  ios)      bindings; ios ;;
  android)  bindings; android ;;
  all)      bindings; ios; android ;;
  *)        die "unknown command '${1}'. Use: bindings | ios | android | all" ;;
esac
