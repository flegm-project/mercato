# Data sources & provenance

## Origin

The dataset was derived from **Wikidata**, which publishes structured data under
**CC0 1.0** (public domain dedication). Entities are keyed by their Wikidata
Q-IDs (e.g. `Q99760796`).

Transfers were **not** read from a transfer feed. They were reconstructed by
chaining a player's consecutive club spells on Wikidata into (from-club →
to-club, year) moves. Chaining was deliberately strict: ~570 candidate pairs were
discarded for implausible date gaps or overly obscure clubs.

## What is and isn't claimed

- **Facts** (who moved where and when, club names, player names) are not
  copyrightable and are used freely.
- The dataset **invents nothing but is incomplete**. Treat each row as a
  candidate: confirm against a second independent source before shipping.
- No IP-sensitive material is included or permitted: no crests, logos, kits,
  competition marks, or photos.

## Files (source of truth)

| File | Columns |
| --- | --- |
| `clubs.csv` | `id, name, notoriety` |
| `players.csv` | `id, name, position, nationality, birth_year, notoriety` |
| `player_aliases.csv` | `player_id, alias` |
| `transfers.csv` | `id, player_id, from_club, to_club, year, kind, tier` |

`position ∈ {gk,def,mid,fw}`, `kind ∈ {transfer,loan,free}`, `tier ∈ {1,2,3}`.

## Trilingual names

The English CSVs above are the current committed form. FR/ES club and nationality
names exist in the prototype's inline DATA
(`reference/web-prototype/mercato.html`) and are to be extracted into the CSVs -
see `docs/DATA.md`.

## To do before shipping

- [ ] Record the exact Wikidata query/snapshot date used to build the dataset.
- [ ] Second-source verification pass on transfer rows.
- [ ] Fold extracted FR/ES club + nationality names into the CSVs.
