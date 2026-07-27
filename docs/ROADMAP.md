# Roadmap

Because the prototype kit already provides real data, a fully specified engine, a
playable reference, a design system, and EN/FR/ES strings, this is a **porting**
plan (web → Rust core + native UI), not a design-from-scratch plan. See
[REUSE.md](REUSE.md). Each phase is shippable/reviewable on its own.

## Phase 0 — Foundations

- Init repo, `LICENSE`, CONTRIBUTING, English-only policy, `.gitignore`
  (Rust / Xcode / Gradle).
- Rust workspace (`core/`) with `mercato-core`, `mercato-data`, `mercato-ffi`
  crates; empty native shells (`apps/ios`, `apps/android`).
- Data pipeline `data/build/`: CSV (source of truth in `data/`) → generated
  bundled DB (gitignored artifact). CSVs are already in place.
- CI skeleton: `cargo test` on push.

## Phase 1 — Port the engine (highest value first)

- Port `matching.rs`, `decoy.rs`, `rng.rs` from
  `core/reference/engine.reference.js` **exactly**.
- Build a **parity test fixture** from the web reference (incl. matching-lab
  ambiguity cases) and assert identical routes/verdicts.
- Port scoring (+3/+0, streak) and per-mode pool selection.
- **Exit**: `cargo test` green; engine provably matches the reference.

## Phase 2 — Data loading

- `mercato-data`: load the generated bundled SQLite DB into the model; build
  EXACT / SURNAME indexes at load.
- Validate referential integrity in CI (regenerate DB from CSVs, check FKs).
- **Exit**: core generates real rounds from the real dataset.

## Phase 3 — Cross the FFI, first playable

- `mercato-ffi` UniFFI `Game` facade (start_round, submit_guess, options, score).
- Build iOS `xcframework` and Android `.so` (cargo-ndk); smoke-test bindings.
- Minimal SwiftUI + Compose screen: one round, Easy multiple choice, score.
- **Exit**: same core logic playable on simulator + emulator.

## Phase 4 — Trilingual data (v1 requirement)

- Add per-language club names (`name_en`/`name_fr`/`name_es`) + translation
  tables for positions and nationalities. Regenerate the bundled DB from CSVs.
- **Exit**: EN/FR/ES render club/position/nationality correctly; English fallback.

## Phase 5 — Build the screens

- Generate `DesignTokens.swift` / `.kt` from `tokens.json`; wire `strings.json`
  (system language, English fallback).
- Implement screens per `specs/screens.md`, components per `specs/components.md`:
  Splash, Onboarding, Home, Game (Easy + Hardcore), Quit, Recap, Profile,
  Settings, Offline. Dev-only matching lab behind a flag. **No Shop screen.**
- Hardcore free-text input + 3 attempts + hint ladder (nationality → position →
  initial + letter count). Hints are free.
- Recap: stars, missed transfers, play again. **No balls, no rewarded video.**
- **Exit**: full offline game, both modes, EN/FR/ES.

## Phase 6 — Monetization

- AdMob per `specs/ads.md`: sponsor board, interstitial (1/round), banner on
  menus, recap rectangle. Consent (UMP) + iOS ATT. **No rewarded ads.**
- **Remove-ads only** (€3.99, StoreKit 2 / Play Billing non-consumable) +
  restore. No shop, no other products.
- **Exit**: no ads after purchase (verified fresh install + restore).

## Phase 7 — Data confidence & hardening

- Confirm dataset rows against a second source (per authors' caveat); record
  sources in `data/SOURCES.md`.
- Privacy policy, store listings (FR/EN/ES), icons, screenshots, data-safety
  labels. QA across device sizes + accessibility. Closed beta.

## Phase 8 — Launch

- Store submission (iOS + Android). Post-launch: crash/analytics monitoring,
  dataset updates per transfer window.

## Post-launch candidates

- Daily challenge (seeded RNG already in the reference).
- Themed packs (league / season / club). Online leaderboards (first real reason
  to add a backend).
