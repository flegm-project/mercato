# Architecture

## Goal

Write the game logic **once** in Rust and run it natively on iOS and Android,
while keeping the platform-specific, hard-to-share concerns — **ads** and
**in-app purchase** — in native code where the SDKs live.

## Chosen approach: Rust core + native UI via UniFFI

- **Rust core** — all game logic and data access.
- **[UniFFI](https://mozilla.github.io/uniffi-rs/)** — generates Swift and
  Kotlin bindings from the Rust API, so native code calls the core like a normal
  library.
- **iOS** — SwiftUI app, links the Rust core as a static library
  (`xcframework`). Ads via Google AdMob SDK, purchases via StoreKit 2.
- **Android** — Jetpack Compose app, links the Rust core via JNI (built with
  `cargo-ndk`). Ads via Google AdMob SDK, purchases via Play Billing.

### Why not an all-Rust UI (Dioxus / Slint / Bevy)?

A single Rust UI codebase is attractive for DRY, but for this game the
monetization stack is the deciding factor:

- AdMob and the store billing SDKs are **native-first**. In an all-Rust UI you
  would hand-write and maintain FFI bridges to those SDKs — the exact
  cross-platform pain we want to avoid, now on the revenue path.
- The game is UI-light (lists, cards, buttons, a timer). Native UI toolkits do
  this trivially and give platform-correct look, accessibility, and store
  compliance for free.

Conclusion: share the *logic* (the valuable, testable part) in Rust; keep the
thin UI + monetization native. Revisit an all-Rust UI only if a future,
animation-heavy mode justifies it.

## Rust core module layout

```
core/
├── reference/            # verbatim JS engine — PORT SOURCE, not built
│   └── engine.reference.js
├── mercato-core/     # pure game logic — no I/O, no platform deps
│   ├── matching.rs   #   answer matching — port engine.reference.js EXACTLY
│   ├── decoy.rs      #   distractorsFor — decoy scoring + seeded RNG
│   ├── rng.rs        #   mulberry32 + hashStr (seedable, deterministic)
│   ├── round.rs      #   round/question generation, pool selection per mode
│   ├── scoring.rs    #   +3 / +0, streaks
│   └── i18n.rs       #   per-language name selection (fr/en/es)
├── mercato-data/     # data model + loading of the bundled dataset
│   ├── model.rs      #   Player, Club, Transfer types (mirror the CSV/DB schema)
│   └── loader.rs     #   load bundled SQLite DB / JSON into the model
└── mercato-ffi/      # UniFFI surface: the API the apps call
    └── lib.rs        #   Game facade: start_round, submit_guess, score, ...
```

## Matching engine

The Hardcore mode depends entirely on this. **Port it exactly** from
`core/reference/engine.reference.js` (extracted from the web reference); do not
approximate. Spec: `docs/specs/engine.md` and `reference/web-prototype/functional-spec.md`.

- **Normalize**: NFD, strip diacritics, map `Ø ø Ł ł Ð ð Þ þ`, lowercase,
  turn `. ' ’ \` - _` into spaces, drop non-`[a-z0-9 ]`, collapse + trim.
- **Distance**: Levenshtein; adaptive threshold by target length —
  `<=4 → 0`, `5–6 → min(1, base)`, `>6 → base` (base = 2).
- **Routes, in order**: exact (canonical FR/EN/ES) → alias → fuzzy → surname →
  none. Surname variants keep particles (`van`, `de`, `di`, `dos`, `mc` …) and
  never eat into the first name.
- **Two mandatory safety rules**:
  - *Exact beats fuzzy* — reject a fuzzy match if the input exactly matches the
    name/surname of a **different** player (`claimedByOther`). Without it, "kane"
    passes for Kanté.
  - *Ambiguity* — a surname shared by several players is rejected with an
    "add the first name" message and **does not consume an attempt**.
- Build `EXACT_INDEX` and `SURNAME_INDEX` at data load, as in the reference.

**Parity testing**: derive a fixture of `(guess, player_id, expected route/ok)`
cases from the web reference (`reference/web-prototype/mercato.html`, incl. its "matching
lab" ambiguity cases) and assert the Rust port matches, so behavior is provably
identical.

Randomness (`mulberry32` + `hashStr`) is ported as a seedable RNG so decoy
selection and any future daily challenge are reproducible and tests are stable.

Design rules:

- `mercato-core` is **pure and deterministic** (given a seed) — trivial to unit
  test, no platform or I/O dependencies. This is where correctness lives.
- Randomness is seedable so decoy selection is reproducible and tests are stable
  (and a daily challenge stays possible post-launch).
- The FFI surface is small and coarse-grained (a `Game` object with a handful of
  methods), to minimize FFI chatter and keep bindings simple.

## Native layer reuse (tokens & strings)

- **Design tokens**: generate `DesignTokens.swift` and `DesignTokens.kt` from
  `design/tokens.json` (the source of truth for colors, type, and game
  constants). The original kit referenced these generated files but they are not
  present — generating them is a build step, not a design task.
- **Strings**: ship `design/strings.json` (114 keys × FR/EN/ES) and
  resolve by system language, no in-app picker.
- **Screens/components/ads**: implement per `docs/specs/*.md` in SwiftUI +
  Compose; `design-system.html` is the visual QA reference.

## Persistence

- v1 has **no cross-session persistence** (per SPEC): score, streak, and balls
  reset each session. Only the **remove-ads entitlement** must persist — and it
  comes from the store (StoreKit / Play), owned natively.
- If later versions add persisted stats, keep it native (UserDefaults / DataStore)
  or a small core-owned key-value blob across FFI; avoid a DB dependency in the
  core.

## Build & CI (target)

- `cargo test` for the core (fast, runs on every push).
- iOS: build `xcframework` via a script; assemble in Xcode.
- Android: `cargo-ndk` to produce `.so` per ABI; assemble in Gradle.
- CI (GitHub Actions): test core → build iOS xcframework → build Android libs.

## Decisions & open questions

- **Language fallback: English** (decided). System language chooses EN/FR/ES; no
  in-app picker.
- **Monetization: Remove-ads (€3.99) only** (decided). No shop, no currency, no
  rewarded ads — see [MONETIZATION.md](MONETIZATION.md).
- **Trilingual DB required for v1** (decided) — see [DATA.md](DATA.md).
- Open: keep the bundled DB as-is or regenerate from CSVs in CI (leaning: CSVs
  are source of truth, DB is a build artifact).
