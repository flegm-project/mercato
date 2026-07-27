# Core (Rust)

The shared Rust workspace: all game logic and data access, consumed by the native
iOS/Android apps through UniFFI. See [`../docs/ARCHITECTURE.md`](../docs/ARCHITECTURE.md).

## Crates (added in Phase 0/1)

| Crate | Responsibility |
| --- | --- |
| `mercato-core` | Pure game logic: matching, decoys, RNG, rounds, scoring, i18n. No I/O, deterministic. |
| `mercato-data` | Data model + loading the bundled dataset into memory. |
| `mercato-ffi` | UniFFI surface — the `Game` facade the apps call. |

## `reference/`

`reference/engine.reference.js` is the matching + decoy engine extracted verbatim
from the web prototype. It is the **port source** for `mercato-core` — not built,
not shipped. Port it exactly; do not approximate. Behavior is locked by parity
tests against the web reference.
