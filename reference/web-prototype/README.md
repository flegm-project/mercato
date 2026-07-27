# Web prototype (historical reference)

The original playable prototype of Mercato. Kept as a **behavior oracle** for the
Rust port — not shipped, not built. Current planning lives in [`../../docs/`](../../docs/).

## Contents

| Path | What it is |
| --- | --- |
| `mercato.html` | Self-contained playable demo: game loop + matching engine + **inline trilingual DATA** (EN/FR/ES). Reference implementation to port. |
| `functional-spec.md` | Original functional spec. **Historical**: it predates the current decisions (no shop, remove-ads only, English fallback). Where it disagrees with `docs/`, `docs/` wins. |

## Run it

Open `mercato.html` in a browser. It is self-contained (data + engine inline);
the only external dependency is Google Fonts.

## Relationship to the rest of the repo

- Engine extracted verbatim → [`../../core/reference/engine.reference.js`](../../core/reference/engine.reference.js) (Rust port source).
- Dataset → [`../../data/`](../../data/) (CSV source of truth; the inline DATA here
  is also the source of the FR/ES translations — see [`../../docs/DATA.md`](../../docs/DATA.md)).
- Design tokens / strings / specs → [`../../design/`](../../design/) and [`../../docs/specs/`](../../docs/specs/).

## Removed from the original kit

- `mercato-en.db`, `dataset-en.json` — generated from the CSVs; rebuilt by the
  data pipeline rather than committed.
- `support.js`, `mercato-data.js`, the `*.dc.html` app files — a separate runtime
  that never shipped with the kit. `mercato.html` does not need them.

## Fonts

Unbounded (800, 900) display, Figtree (500–900) UI, IBM Plex Mono (500) technical
labels — all on Google Fonts.

## Note on language

This prototype reads `navigator.language` and historically fell back to **French**.
The product decision is **English fallback** — see [`../../docs/GAME_DESIGN.md`](../../docs/GAME_DESIGN.md).
