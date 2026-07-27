# Contributing

## Language policy

All code, comments, commit messages, documentation, issues, and PRs are written
in **English**. The game *content* is localized to EN / FR / ES.

## Source of truth

- **Gameplay & scope**: `docs/` (not `reference/web-prototype/functional-spec.md`,
  which is historical).
- **Data**: `data/*.csv`. The bundled SQLite DB is a generated artifact, never
  edited by hand or committed.
- **Matching engine**: port `core/reference/engine.reference.js` exactly; changes
  must keep the parity tests green.

## Repository map

| Path | What it is |
| --- | --- |
| `core/` | Rust workspace (crates added in Phase 0/1); `core/reference/` holds the JS port source |
| `data/` | CSV dataset (source of truth) + build scripts |
| `design/` | Design tokens, UI strings, design-system reference |
| `docs/` | Planning docs + `docs/specs/` |
| `reference/web-prototype/` | Historical playable prototype (behavior oracle) |
| `apps/` | Native apps (added later): `ios/` SwiftUI, `android/` Compose |

## Commits

Conventional-commit style is encouraged (`feat:`, `fix:`, `docs:`, `chore:` …).
Keep the subject ≤ 50 chars; add a body only when the "why" is not obvious.

## Before opening a PR

- `cargo test` passes (once the workspace exists).
- Data changes keep referential integrity (every transfer references existing
  player/club IDs) - CI validates this.
- No IP-sensitive assets added (no crests, logos, kits, or photos - see
  `docs/DATA.md`).

## Licensing

The code is **proprietary - all rights reserved** (see `LICENSE`). The factual
dataset under `data/` follows `data/SOURCES.md` (Wikidata, CC0). Contributions
are accepted only under the project's proprietary terms; by contributing you
assign the necessary rights to the copyright holder.
