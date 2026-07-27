#!/usr/bin/env bash
#
# Build the Rust core (mercato-ffi) for iOS and assemble an xcframework.
#
# NOT RUNNABLE ON THIS MACHINE: this VPS has no Xcode (macOS-only tooling)
# and is not meant to have one. This script is written for future use in
# CI (GitHub Actions macOS runners) or on a Mac dev machine with Xcode
# installed. It has only been syntax-checked (`bash -n build-ios.sh`),
# never executed.
#
# NOTES on the mercato-ffi crate (checked against core/mercato-ffi/Cargo.toml
# and src/lib.rs at the time this script was written):
#   - `[lib] name = "mercato_ffi"`, `crate-type = ["lib", "cdylib",
#     "staticlib"]`, so the iOS build artifact is libmercato_ffi.a.
#   - It exposes a `uniffi-bindgen` bin (uniffi-bindgen.rs calling
#     `uniffi::uniffi_bindgen_main()`), gated behind `required-features =
#     ["cli"]` -> invocations below pass `--features cli`. Invoked as
#     `cargo run -p mercato-ffi --features cli --bin uniffi-bindgen --
#     generate --library <path-to-.a> --language swift --out-dir <dir>`.
#     This reads the compiled library's embedded UniFFI metadata, so any
#     one of the freshly built .a files works as the --library input.
#   - `uniffi::setup_scaffolding!()` is called with no explicit namespace,
#     so it defaults to the crate name: Swift generation produces
#     `mercato_ffiFFI.h`, `mercato_ffiFFI.modulemap` and `mercato_ffi.swift`.
#     The modulemap is renamed to `module.modulemap` when staged for
#     `-create-xcframework`, matching the uniffi-rs Swift binding-generation
#     guide's expected xcframework header layout. Adjust the file names
#     below if that namespace changes.
#
# Usage: scripts/build-ios.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE_NAME="mercato-ffi"
CRATE_PATH="${REPO_ROOT}/core/mercato-ffi"
CRATE_MANIFEST="${CRATE_PATH}/Cargo.toml"
LIB_NAME="mercato_ffi"

XCFRAMEWORK_OUT="${REPO_ROOT}/apps/ios/MercatoFFI.xcframework"
SWIFT_OUT_DIR="${REPO_ROOT}/apps/ios/Generated"
BUILD_STAGE_DIR="${REPO_ROOT}/core/target/ios-stage"

# iOS targets: device + both simulator archs (Apple Silicon and Intel Macs)
IOS_TARGETS=(
  aarch64-apple-ios
  aarch64-apple-ios-sim
  x86_64-apple-ios-sim
)

echo "==> Checking prerequisites"

if ! command -v xcodebuild >/dev/null 2>&1; then
  echo "ERROR: xcodebuild not found." >&2
  echo "  Xcode is required to build for iOS; this only works on macOS." >&2
  echo "  Install Xcode from the Mac App Store, then run:" >&2
  echo "    xcode-select --install" >&2
  echo "    sudo xcode-select -s /Applications/Xcode.app/Contents/Developer" >&2
  exit 1
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "ERROR: cargo not found. Install Rust: https://rustup.rs" >&2
  exit 1
fi

if ! command -v lipo >/dev/null 2>&1; then
  echo "ERROR: lipo not found (should ship with Xcode command line tools)." >&2
  echo "  Run: xcode-select --install" >&2
  exit 1
fi

if [[ ! -f "${CRATE_MANIFEST}" ]]; then
  echo "ERROR: ${CRATE_MANIFEST} not found." >&2
  echo "  This script builds the ${CRATE_NAME} crate; it must exist at" >&2
  echo "  core/mercato-ffi before this script can run." >&2
  exit 1
fi

echo "==> Ensuring rust targets are installed"
for target in "${IOS_TARGETS[@]}"; do
  rustup target add "${target}" >/dev/null
done

echo "==> Building ${CRATE_NAME} for: ${IOS_TARGETS[*]}"
for target in "${IOS_TARGETS[@]}"; do
  echo "----> ${target}"
  cargo build --release --target "${target}" --manifest-path "${CRATE_MANIFEST}"
done

for target in "${IOS_TARGETS[@]}"; do
  lib_path="${REPO_ROOT}/core/target/${target}/release/lib${LIB_NAME}.a"
  if [[ ! -f "${lib_path}" ]]; then
    echo "ERROR: expected build output not found: ${lib_path}" >&2
    echo "  Check that ${CRATE_NAME}'s Cargo.toml has crate-type = [\"staticlib\"]" >&2
    echo "  and lib name = \"${LIB_NAME}\"." >&2
    exit 1
  fi
done

echo "==> Generating Swift bindings"
rm -rf "${SWIFT_OUT_DIR}"
mkdir -p "${SWIFT_OUT_DIR}"
device_lib="${REPO_ROOT}/core/target/aarch64-apple-ios/release/lib${LIB_NAME}.a"

cargo run -p "${CRATE_NAME}" --manifest-path "${CRATE_MANIFEST}" --features cli --bin uniffi-bindgen -- \
  generate \
  --library "${device_lib}" \
  --language swift \
  --out-dir "${SWIFT_OUT_DIR}"

echo "==> Combining simulator archs (arm64 + x86_64) into one fat lib"
rm -rf "${BUILD_STAGE_DIR}"
mkdir -p "${BUILD_STAGE_DIR}/device" "${BUILD_STAGE_DIR}/sim-universal" "${BUILD_STAGE_DIR}/headers"

cp -f "${device_lib}" "${BUILD_STAGE_DIR}/device/lib${LIB_NAME}.a"

lipo -create \
  "${REPO_ROOT}/core/target/aarch64-apple-ios-sim/release/lib${LIB_NAME}.a" \
  "${REPO_ROOT}/core/target/x86_64-apple-ios-sim/release/lib${LIB_NAME}.a" \
  -output "${BUILD_STAGE_DIR}/sim-universal/lib${LIB_NAME}.a"

echo "==> Staging headers + modulemap"
# Two copies: -create-xcframework needs a headers dir per library slice.
for slice_dir in "${BUILD_STAGE_DIR}/device" "${BUILD_STAGE_DIR}/sim-universal"; do
  headers_dir="${slice_dir}/headers"
  mkdir -p "${headers_dir}"
  cp -f "${SWIFT_OUT_DIR}/${LIB_NAME}FFI.h" "${headers_dir}/"
  cp -f "${SWIFT_OUT_DIR}/${LIB_NAME}FFI.modulemap" "${headers_dir}/module.modulemap"
done

echo "==> Assembling xcframework"
rm -rf "${XCFRAMEWORK_OUT}"
mkdir -p "$(dirname "${XCFRAMEWORK_OUT}")"

xcodebuild -create-xcframework \
  -library "${BUILD_STAGE_DIR}/device/lib${LIB_NAME}.a" \
  -headers "${BUILD_STAGE_DIR}/device/headers" \
  -library "${BUILD_STAGE_DIR}/sim-universal/lib${LIB_NAME}.a" \
  -headers "${BUILD_STAGE_DIR}/sim-universal/headers" \
  -output "${XCFRAMEWORK_OUT}"

echo "==> Done."
echo "    xcframework: ${XCFRAMEWORK_OUT}"
echo "    Swift bindings: ${SWIFT_OUT_DIR}/${LIB_NAME}.swift"
echo "    Add both to the Xcode project (xcframework as a framework"
echo "    dependency, the .swift file as a regular source file)."
