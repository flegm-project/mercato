# Reusing the prototype kit

A complete v1.0 reference kit was produced before this repo was organized: real
data, a fully specified matching engine, a playable web reference, a design
system, all UI strings in EN/FR/ES, and screen/component/ad specs. It has been
unpacked into the repo (see [layout](../README.md#repository-layout)). **We port
and reuse it — we do not redesign it.** This is a porting effort (web → Rust core
+ native UI), not a greenfield design.

## Asset inventory (where each piece now lives)

| Asset | What it is | Reuse target (Voie A) |
| --- | --- | --- |
| `data/{clubs,players,transfers,player_aliases}.csv` | Real dataset, diffable — **source of truth** | Loaded by `mercato-data`; build step generates the bundled DB |
| `reference/web-prototype/mercato.html` | Playable reference impl (loop + engine + **inline trilingual DATA**) | Behavior oracle for parity tests; source of EN/FR/ES names |
| `core/reference/engine.reference.js` | Engine source extracted verbatim | Line-by-line Rust port reference |
| `docs/specs/engine.md` | Exact matching + decoy algorithm | Port to `mercato-core` verbatim |
| `reference/web-prototype/functional-spec.md` | Original functional spec (historical — predates the no-shop / EN-fallback decisions) | Context only; current truth is `docs/` |
| `design/tokens.json` + `tokens.css` | Design tokens + game constants | Generate `DesignTokens.swift` / `.kt` |
| `design/strings.json` | 114 keys × EN/FR/ES | Ship as-is; load per system language |
| `docs/specs/screens.md` | 12-screen spec | SwiftUI + Compose screen blueprint |
| `docs/specs/components.md` | Component anatomy + states | Native component blueprint |
| `docs/specs/ads.md` | Ad placements + rules | Native AdMob integration blueprint |
| `design/design-system.html` | Visual reference sheet | Design QA reference |

Dropped during cleanup (regenerable or orphaned): `mercato-en.db` and
`dataset-en.json` (generated from the CSVs — rebuilt by the data pipeline);
`support.js` and `mercato-data.js` (runtime for `.dc.html` app files that were
never part of the kit — `mercato.html` is self-contained).

## Dataset facts (already real)

- 412 clubs, 513 players, 945 aliases, 1 905 transfers, 53 nationalities.
- IDs are **Wikidata Q-IDs**; provenance is **Wikidata (CC0)**, derived by
  chaining consecutive club spells (~570 implausible pairs discarded).
- Authors' caveat: the dataset **invents nothing but is incomplete**; confirm each
  row against a second source before shipping.

## Ground-truth scope (current decisions)

The prototype defines the gameplay; the decisions below **override** it where they
differ (recorded here so the old `functional-spec.md` is not mistaken for truth):

- **Two modes**: **Easy** (tier 1+2 pool ≈ 956 transfers, 4-option multiple
  choice) and **Hardcore** (all 1 905, free-text input, 3 attempts + hints).
- Free-text matching is **in v1** (the whole point of Hardcore), powered by the
  matching engine — not deferred.
- **No shop, no soft currency.** Drop the balls currency, the hint/ball shop, and
  the rewarded "double balls" video. Hints in Hardcore stay **free gameplay**.
  The **only IAP is Remove ads (€3.99)**.
- **No** daily challenge, accounts/backend, or cross-session persistence in v1
  (a seeded RNG exists — `mulberry32` / `todayKey` — enabling a daily later).
- Language from the system setting, **fallback English**, **no in-app picker**.
- **Trilingual data required for v1** (EN/FR/ES for clubs, positions,
  nationalities) — and it already exists, see below.

## Porting priorities

1. **Matching engine** (`mercato-core`) — port `engine.reference.js` exactly:
   `normalize`, `levenshtein`, `thresholdFor`, `surnameVariants`, the EXACT /
   SURNAME indexes, `claimedByOther`, `matchAnswer`. Preserve the two safety
   rules (exact-beats-fuzzy; ambiguous-surname rejection). Parity-test against the
   web reference.
2. **Decoy selection** (`distractorsFor`) — same scoring weights, seeded RNG.
3. **Data loading** (`mercato-data`) — CSV → model; build EXACT/SURNAME indexes.
4. **Tokens → native** — generate `DesignTokens.swift` / `.kt` from `tokens.json`.
5. **Strings** — ship `strings.json`, resolve by system language.
6. **Screens** — implement per `docs/specs/screens.md` in SwiftUI + Compose.

## i18n: the trilingual data already exists

The prototype's inline `DATA` in `reference/web-prototype/mercato.html` is
**already EN/FR/ES** for clubs and nationalities (e.g. Zaragoza → "Real
Saragosse" fr; United States → "États-Unis" fr / "Estados Unidos" es); player
names are identical across languages. The committed CSVs are English-only.

So closing the i18n gap is an **extraction** task, not translation: pull the
FR/ES club and nationality names out of the inline DATA and enrich the CSVs
(add per-language club-name columns + a nationalities translation table). Player
names need nothing. This is much smaller than "translate the dataset".
