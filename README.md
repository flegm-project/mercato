# Mercato

A mobile guessing game about football transfers. The app shows a real transfer or
loan move - club left, club joined, year - with the player's name hidden, and you
must name the footballer.

- **Real data, zero IP risk**: only factual data is used - player names and club
  names. No logos, no crests, no photos, no team likenesses.
- **Multilingual**: English, French, Spanish (EN / FR / ES; English is the
  fallback).
- **Platforms**: iOS and Android, powered by a shared Rust core.
- **Monetization**: banner/interstitial ads with a single in-app purchase to
  remove ads (€3.99). No shop, no other purchases.

> Status: porting. A complete v1.0 reference kit (real data - 412 clubs, 513
> players, 1 905 transfers - a specified matching engine, a playable web
> reference, a design system, and EN/FR/ES strings) has been unpacked into this
> repo. This effort **ports** it to a shared Rust core with native iOS/Android
> UIs. See [`docs/REUSE.md`](docs/REUSE.md).

## Modes

- **Easy** - 4-option multiple choice over mainstream transfers.
- **Hardcore** - free-text input over the full dataset, 3 attempts + free hints,
  backed by an accent- and language-tolerant matching engine.

## Architecture at a glance

A shared **Rust core** holds all game logic and data. Native UIs consume it
through [UniFFI](https://mozilla.github.io/uniffi-rs/) bindings.

```
            ┌─────────────────────────┐
            │        Rust core         │
            │   (game logic + data)    │
            │  round generation,       │
            │  matching, decoys,       │
            │  scoring, i18n           │
            └──────────┬───────────────┘
                       │ UniFFI (FFI bindings)
          ┌────────────┴────────────┐
          │                         │
   ┌──────▼───────┐          ┌──────▼───────┐
   │  iOS app      │          │ Android app   │
   │  SwiftUI      │          │ Jetpack       │
   │               │          │ Compose       │
   │  AdMob + IAP  │          │ AdMob + IAP   │
   └───────────────┘          └───────────────┘
```

Rationale: game logic (rounds, matching across 3 languages, decoys, scoring) is
written once in Rust. The concerns that are painful to share cross-platform and
best done natively - **ads (AdMob)** and **in-app purchase / billing** - live in
the native layer. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), including
the all-Rust UI option (Dioxus/Slint) and why it was not chosen for v1.

## Documentation

| Doc | Purpose |
| --- | --- |
| [Reuse](docs/REUSE.md) | Inventory of the prototype kit and how each asset is reused |
| [Game design](docs/GAME_DESIGN.md) | Modes, loop, matching, scoring |
| [Architecture](docs/ARCHITECTURE.md) | Rust core, matching engine, UniFFI, native apps |
| [Data](docs/DATA.md) | Dataset, schema, i18n, bundling |
| [Monetization](docs/MONETIZATION.md) | Ads and the remove-ads purchase |
| [Roadmap](docs/ROADMAP.md) | Phased porting plan to launch |
| [Specs](docs/specs/) | Original screen / component / ad / engine specs |

## Repository layout

```
mercato/
├── core/                     # Rust workspace (crates added in Phase 0/1)
│   └── reference/            #   verbatim JS engine - port source, not built
├── data/                     # dataset - CSV source of truth (DB is generated)
├── design/                   # tokens, strings, design-system reference
├── docs/                     # planning docs + specs/
│   └── specs/                #   screen / component / ad / engine specs
└── reference/
    └── web-prototype/        # playable historical reference (mercato.html)
```

Target additions (Phase 0/1): `core/{mercato-core,mercato-data,mercato-ffi}`,
`apps/{ios,android}`, `data/build/` (CSV → bundled DB).

## Contributing / language policy

All code, comments, commit messages, documentation, and issues are written in
**English**. The game *content* is localized to EN / FR / ES.
