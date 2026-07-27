#!/usr/bin/env bash
#
# Build the Rust core (mercato-ffi) for Android and stage it for Gradle.
#
# NOT RUNNABLE ON THIS MACHINE: this VPS has no Android NDK / cargo-ndk
# installed and is not meant to. This script is written for future use in
# CI (GitHub Actions) or on a developer machine that has the Android NDK
# and `cargo-ndk` installed. It has only been syntax-checked
# (`bash -n build-android.sh`), never executed.
#
# NOTES on the mercato-ffi crate (checked against core/mercato-ffi/Cargo.toml
# and src/lib.rs at the time this script was written):
#   - `[lib] name = "mercato_ffi"`, `crate-type = ["lib", "cdylib",
#     "staticlib"]`, so the Android build artifact is libmercato_ffi.so.
#   - It exposes a `uniffi-bindgen` bin (uniffi-bindgen.rs calling
#     `uniffi::uniffi_bindgen_main()`), gated behind `required-features =
#     ["cli"]` -> invocations below pass `--features cli`. Invoked as
#     `cargo run -p mercato-ffi --features cli --bin uniffi-bindgen --
#     generate --library <path-to-.so> --language kotlin --out-dir <dir>`.
#     This reads the compiled library's embedded UniFFI metadata, so any
#     one of the freshly built .so files (any ABI) works as the --library
#     input, it does not need to match the host architecture.
#   - `uniffi::setup_scaffolding!()` is called with no explicit namespace,
#     so it defaults to the crate name: the generated Kotlin package is
#     `uniffi.mercato_ffi`. Adjust KOTLIN_OUT_DIR below if that changes.
#
# Usage: scripts/build-android.sh
# Env overrides: ANDROID_API_LEVEL (default 24)

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE_NAME="mercato-ffi"
CRATE_PATH="${REPO_ROOT}/core/mercato-ffi"
CRATE_MANIFEST="${CRATE_PATH}/Cargo.toml"
LIB_NAME="mercato_ffi"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-24}"

JNI_LIBS_DIR="${REPO_ROOT}/apps/android/app/src/main/jniLibs"
KOTLIN_OUT_DIR="${REPO_ROOT}/apps/android/app/src/main/java/uniffi/mercato_ffi"

# rust-target -> Android ABI (the directory name Gradle expects under jniLibs/)
declare -A ANDROID_TARGETS=(
  [aarch64-linux-android]="arm64-v8a"
  [armv7-linux-androideabi]="armeabi-v7a"
  [x86_64-linux-android]="x86_64"
  [i686-linux-android]="x86"
)

echo "==> Checking prerequisites"

if ! command -v cargo-ndk >/dev/null 2>&1; then
  echo "ERROR: cargo-ndk not found." >&2
  echo "  Install it with: cargo install cargo-ndk" >&2
  echo "  You also need the Android NDK installed, with ANDROID_NDK_HOME (or" >&2
  echo "  ANDROID_NDK_ROOT) pointing at it. Get it via Android Studio > SDK" >&2
  echo "  Manager > SDK Tools > NDK, or https://developer.android.com/ndk/downloads" >&2
  exit 1
fi

if ! command -v cargo >/dev/null 2>&1; then
  echo "ERROR: cargo not found. Install Rust: https://rustup.rs" >&2
  exit 1
fi

if [[ -z "${ANDROID_NDK_HOME:-}" && -z "${ANDROID_NDK_ROOT:-}" ]]; then
  echo "WARNING: neither ANDROID_NDK_HOME nor ANDROID_NDK_ROOT is set." >&2
  echo "  cargo-ndk may still find the NDK via the Android SDK layout, but" >&2
  echo "  if the build fails, set one of those env vars to the NDK path." >&2
fi

if [[ ! -f "${CRATE_MANIFEST}" ]]; then
  echo "ERROR: ${CRATE_MANIFEST} not found." >&2
  echo "  This script builds the ${CRATE_NAME} crate; it must exist at" >&2
  echo "  core/mercato-ffi before this script can run." >&2
  exit 1
fi

echo "==> Ensuring rust targets are installed"
for target in "${!ANDROID_TARGETS[@]}"; do
  rustup target add "${target}" >/dev/null
done

echo "==> Building ${CRATE_NAME} for: ${!ANDROID_TARGETS[*]}"
mkdir -p "${JNI_LIBS_DIR}"

for target in "${!ANDROID_TARGETS[@]}"; do
  abi="${ANDROID_TARGETS[${target}]}"
  echo "----> ${target} (ABI ${abi})"
  cargo ndk \
    --target "${target}" \
    --platform "${ANDROID_API_LEVEL}" \
    --manifest-path "${CRATE_MANIFEST}" \
    -- build --release
done

echo "==> Staging .so files into jniLibs/<abi>/"
for target in "${!ANDROID_TARGETS[@]}"; do
  abi="${ANDROID_TARGETS[${target}]}"
  src_so="${REPO_ROOT}/core/target/${target}/release/lib${LIB_NAME}.so"
  dest_dir="${JNI_LIBS_DIR}/${abi}"

  if [[ ! -f "${src_so}" ]]; then
    echo "ERROR: expected build output not found: ${src_so}" >&2
    echo "  Check that ${CRATE_NAME}'s Cargo.toml has crate-type = [\"cdylib\"]" >&2
    echo "  and lib name = \"${LIB_NAME}\"." >&2
    exit 1
  fi

  mkdir -p "${dest_dir}"
  cp -f "${src_so}" "${dest_dir}/lib${LIB_NAME}.so"
  echo "----> ${dest_dir}/lib${LIB_NAME}.so"
done

echo "==> Generating Kotlin bindings"
mkdir -p "${KOTLIN_OUT_DIR}"
host_so="${REPO_ROOT}/core/target/aarch64-linux-android/release/lib${LIB_NAME}.so"

cargo run -p "${CRATE_NAME}" --manifest-path "${CRATE_MANIFEST}" --features cli --bin uniffi-bindgen -- \
  generate \
  --library "${host_so}" \
  --language kotlin \
  --out-dir "${KOTLIN_OUT_DIR}"

echo "==> Done."
echo "    Native libs: ${JNI_LIBS_DIR}/<abi>/lib${LIB_NAME}.so"
echo "    Kotlin bindings: ${KOTLIN_OUT_DIR}"
