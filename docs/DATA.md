# Data

> The dataset lives in `data/` as CSV, and is trilingual. See [REUSE.md](REUSE.md).

## What we use (and what we do not)

**Used - factual, non-copyrightable:** player names, club names, and transfer
facts (from-club, to-club, year, kind, tier). **Never used:** crests, logos,
photos, kits, competition logos, or any likeness. The app is text-only for
entities, keeping it clear of image/likeness IP.

## Existing dataset

- **412 clubs, 513 players, 945 aliases, 1 905 transfers, 53 nationalities.**
- IDs are **Wikidata Q-IDs**; provenance is **Wikidata (CC0)**, built by chaining
  consecutive club spells (~570 implausible pairs discarded).
- Committed form: four CSVs in `data/` (`clubs`, `players`, `transfers`,
  `player_aliases`) - the source of truth. The bundled SQLite DB is generated from
  them (see Bundling).
- **Authors' caveat**: the dataset invents nothing but is **incomplete**; confirm
  each row against a second source before shipping.

## Schema (target bundled DB, generated from the CSVs)

```sql
club        (id, name, notoriety)
player      (id, name, position, nationality, birth_year, notoriety)
player_alias(player_id, alias)
transfer    (id, player_id, from_club, to_club, year, kind, tier)
```

- `position` : `gk` | `def` | `mid` | `fw`
- `kind`     : `transfer` | `loan` | `free`
- `tier`     : 1 mainstream · 2 informed fan · 3 expert
- `notoriety`: integer; drives sorting and decoy plausibility
- Indexes on `transfer(tier)`, `transfer(player_id)`, `player_alias(player_id)`.
- Constraints: `from_club <> to_club`, `kind`/`position`/`tier` checks, FKs.

Target model in `mercato-data` mirrors this, with per-language name fields once
translations exist:

```
Player   { id, name(per-lang), position?, nationality?, birth_year?, notoriety, aliases[] }
Club     { id, name(per-lang), notoriety }
Transfer { id, player_id, from_club, to_club, year, kind, tier }
```

## i18n: trilingual DB is a v1 requirement

The game ships in **EN / FR / ES**, so the data must carry all three (English is
only the fallback). **Required for v1**, not deferred - but the translations
**already exist**: the prototype's inline `DATA` in
`reference/web-prototype/mercato.html` is already trilingual for clubs and
nationalities (e.g. Zaragoza → "Real Saragosse" fr; United States → "États-Unis"
fr / "Estados Unidos" es). Player names are identical across languages.

So this is an **extraction** task, not translation:

- Extract FR/ES club and nationality names from the inline DATA and enrich the
  CSVs: add `name_en` / `name_fr` / `name_es` to `club` (English is the existing
  `name`), plus a nationalities translation table.
- Positions (`gk/def/mid/fw`) are a tiny fixed set - a static 3-language map.
- Player names need nothing (identical across languages; aliases cover spelling
  variants and feed the matching engine).

An English-only build is acceptable as an intermediate milestone; v1 ships once
the extracted FR/ES club/position/nationality data is folded into the CSVs.

## Bundling

- **CSVs under `data/` are the source of truth** (committed, diffable), and the
  apps bundle them directly.
- `mercato-data::load_corpus` parses them into memory at startup and validates
  referential integrity, so a broken dataset fails immediately rather than
  producing a broken round. At these volumes (a few thousand rows) an in-memory
  corpus is faster and simpler than an embedded database; revisit only if the
  dataset grows by an order of magnitude.
- CI runs that load against the committed CSVs on every push.

## Answer matching data needs

The matching engine ([ARCHITECTURE.md](ARCHITECTURE.md#matching-engine)) consumes
canonical names (FR/EN/ES) + aliases and builds EXACT / SURNAME indexes at load.
Keep aliases rich - they drive both the alias route and collision detection.

## Maintenance

- Stable Wikidata IDs already dedupe players/clubs across seasons.
- Appending new transfer windows = add rows keyed by existing IDs; CI validates
  referential integrity (every transfer references existing player/club IDs).
