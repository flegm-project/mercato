# Core (Rust)

The shared Rust workspace: all game logic and data access, consumed by the native
iOS/Android apps through UniFFI. See [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Crates

| Crate | Responsibility |
| --- | --- |
| `mercato-core` | Pure game logic: matching, decoys, RNG, rounds, scoring, session. No I/O, deterministic given a seed. |
| `mercato-data` | Loads the `data/` CSVs into a `Corpus`, validating referential integrity. |
| `mercato-ffi` | UniFFI surface - the `Game` facade the apps call. |

Run the tests with `cargo test --all` from this directory. Generate the Swift and
Kotlin bindings with `../scripts/build-native.sh bindings`; the native library
builds (`ios`, `android`) additionally need full Xcode / the Android NDK and a
rustup toolchain.

## `reference/`

`reference/engine.reference.js` is the matching + decoy engine extracted verbatim
from the web prototype. It is the **port source** for `mercato-core` - not built,
not shipped. Port it exactly; do not approximate. Behavior is locked by parity
tests against the web reference.
