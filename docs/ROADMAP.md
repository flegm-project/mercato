# Roadmap

Because the prototype kit already provides real data, a fully specified engine, a
playable reference, a design system, and EN/FR/ES strings, this is a **porting**
plan (web → Rust core + native UI), not a design-from-scratch plan. See
[REUSE.md](REUSE.md). Each phase is shippable/reviewable on its own.

**Status:** Phases 0 to 6 are done. The Android app is in closed testing on
Google Play: both modes, EN/FR/ES, ads with UMP consent, and the remove-ads
purchase. Phase 7 is in progress, and what remains in it is not engineering:
Google requires a new personal developer account to hold 12 testers opted in
for 14 continuous days before it will grant production access, and the store's
own content declarations have to be filled in by hand. iOS trails Android.

## Phase 0 - Foundations [DONE]

- Init repo, `LICENSE`, CONTRIBUTING, English-only policy, `.gitignore`
  (Rust / Xcode / Gradle).
- Rust workspace (`core/`) with `mercato-core`, `mercato-data`, `mercato-ffi`
  crates; empty native shells (`apps/ios`, `apps/android`).
- CSVs are the committed source of truth in `data/`; no separate DB artifact is
  needed, the loader reads them directly.
- CI: fmt + clippy + `cargo test` on push.

## Phase 1 - Port the engine (highest value first) [DONE]

- Port `matching.rs`, `decoy.rs`, `rng.rs` from
  `core/reference/engine.reference.js` **exactly**.
- Build a **parity test fixture** from the web reference (incl. matching-lab
  ambiguity cases) and assert identical routes/verdicts.
- Port scoring (+3/+0, streak) and per-mode pool selection.
- **Exit**: `cargo test` green; engine provably matches the reference.

## Phase 2 - Data loading [DONE]

- `mercato-data::load_corpus` parses the CSVs into the core model, joins
  aliases onto players, and validates referential integrity so a broken dataset
  fails at startup rather than mid-round.
- `Corpus` owns the EXACT / SURNAME matching indexes, built once at load.
- Integration tests assert the real volumes (412 / 513 / 1905 / 945 aliases) and
  that the Easy pool is exactly the 956 transfers the spec quotes.

## Phase 3 - Cross the FFI, first playable [DONE]

Done:
- `mercato-core::session` drives a round (10 questions, Easy 4-option / Hardcore
  free text with 3 attempts and the free hint ladder), deterministic per seed.
- `mercato-ffi` exposes the UniFFI `Game` facade: `start_round`,
  `next_question`, `submit_choice`, `submit_guess`, `next_hint`, `score`,
  `missed`, plus `language_for_locale` for the system-language fallback.
- Swift and Kotlin bindings generate cleanly (`scripts/build-native.sh bindings`).
- Integration tests play full rounds through the facade against the real data.

- Toolchain installed (rustup with the six mobile targets, full Xcode, Android
  SDK/NDK, cargo-ndk). See the toolchain notes in
  [ARCHITECTURE.md](ARCHITECTURE.md#toolchain).
- `Mercato.xcframework` builds: an arm64 device slice plus a fat
  x86_64 + arm64 simulator slice.
- `scripts/smoke-swift.sh` compiles a Swift binary against the generated
  bindings and plays a real round, so the Rust-to-Swift chain is proven at
  runtime and not merely typechecked.

- Android `.so` per ABI (`scripts/build-native.sh android`), one per
  arm64-v8a / armeabi-v7a / x86_64, checked for 16 KB page alignment because
  Play refuses anything else.
- **Exit**: same core logic playable on simulator + emulator. Met.

## Phase 4 - Trilingual data (v1 requirement) [DONE]

- `clubs.csv` and `players.csv` now carry `name_en` / `name_fr` / `name_es`, and
  `nationalities.csv` is the nationality translation table (players reference it
  by Wikidata id, so decoy comparison stays exact while display follows the UI
  language).
- This was an extraction, not a translation: the values came from the
  prototype's inline DATA (`scripts/enrich-csv-i18n.mjs`), which was verified to
  agree with the CSVs on every id first. 155 clubs differ EN/FR, 137 EN/ES, and
  43 of the 53 nationalities differ EN/FR.
- Positions are a fixed four-value set, mapped in the UI layer.

## Phase 5 - Build the screens [DONE]

- Generate `DesignTokens.swift` / `.kt` from `tokens.json`; wire `strings.json`
  (system language, English fallback).
- Implement screens per `specs/screens.md`, components per `specs/components.md`:
  Splash, Onboarding, Home, Game (Easy + Hardcore), Quit, Recap, Profile,
  Settings, Offline. Dev-only matching lab behind a flag. **No Shop screen.**
- Hardcore free-text input + 3 attempts + hint ladder (nationality → position →
  initial + letter count). Hints are free.
- Recap: stars, missed transfers, play again. **No balls, no rewarded video.**
- **Exit**: full offline game, both modes, EN/FR/ES.

## Phase 6 - Monetization [DONE]

- AdMob per `specs/ads.md`: sponsor board, interstitial (1/round), banner on
  menus, recap rectangle. Consent (UMP) + iOS ATT. **No rewarded ads.**
- **Remove-ads only** (€3.99, StoreKit 2 / Play Billing non-consumable) +
  restore. No shop, no other products.
- **Exit**: no ads after purchase (verified fresh install + restore).

## Phase 7 - Data confidence & hardening [IN PROGRESS]

- Confirm dataset rows against a second source (per authors' caveat); record
  sources in `data/SOURCES.md`.
- Done: privacy policy published, store listings and screenshots in all three
  languages, generated icons, closed beta running on the alpha track.
- Remaining: 12 testers opted in for 14 continuous days, the console's App
  content declarations (data safety, content rating, target audience, ads),
  and linking both AdMob apps to their store entries so real ads fill.

## Phase 8 - Launch

- Store submission (iOS + Android). Post-launch: crash/analytics monitoring,
  dataset updates per transfer window.

## Post-launch candidates

- Daily challenge (seeded RNG already in the reference).
- Themed packs (league / season / club). Online leaderboards (first real reason
  to add a backend).
