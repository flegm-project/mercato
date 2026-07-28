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
  Intermediate spells can be missing (e.g. a short half-season stint), so a
  row's `from_club` is "the previous known club", not a guarantee that no
  club sat in between.
- No IP-sensitive material is included or permitted: no crests, logos, kits,
  competition marks, or photos.

## Files (source of truth)

| File | Columns |
| --- | --- |
| `clubs.csv` | `id, name_en, name_fr, name_es, notoriety` |
| `players.csv` | `id, name_en, name_fr, name_es, position, nationality, birth_year, notoriety` |
| `player_aliases.csv` | `player_id, alias` |
| `transfers.csv` | `id, player_id, from_club, to_club, year, kind, tier` |
| `nationalities.csv` | `id, name_en, name_fr, name_es` |

`position ∈ {gk,def,mid,fw}`, `kind ∈ {transfer,loan,free}`, `tier ∈ {1,2,3}`.
FR/ES club and nationality names were extracted from the validated prototype
DATA (see `docs/DATA.md`); player names are identical across languages.

## Second-source verification

Method: sample rows (highest-notoriety players first, then random tiers),
check player, both clubs, direction, year and move type against at least one
independent public source (Wikipedia career tables, established sports
media). Record every discrepancy here and fix the CSV in the same commit.
The `kind` column deserves particular attention: free transfers at contract
expiry are easily mislabelled as paid transfers by spell-chaining.

### Pass 1 (2026-07-27, 11 rows, tier-1 heavy)

| Row | Claim | Verdict | Action |
| --- | --- | --- | --- |
| 118 | Sukur, Bursaspor → Galatasaray 1992 | confirmed | none |
| 119 | Sukur, Galatasaray → Inter 2000 | confirmed | none |
| 120 | Sukur, Inter → Blackburn 2002 | wrong origin: he was released by Parma (Inter → Parma Jan 2002 is a separate, missing move) | `from_club` fixed to Parma (Q2693), `kind` fixed to `free` |
| 121 | Sukur, Blackburn → Galatasaray 2003 | contract expiry | `kind` fixed to `free` |
| 1371 | Messi, Barcelona → PSG 2021 | contract expiry | `kind` fixed to `free` |
| 1372 | Messi, PSG → Inter Miami 2023 | contract expiry | `kind` fixed to `free` |
| 1415 | Ronaldo, Sporting → Man Utd 2003 | confirmed | none |
| 1416 | Ronaldo, Man Utd → Real Madrid 2009 | confirmed | none |
| 1417 | Ronaldo, Real Madrid → Juventus 2018 | confirmed | none |
| 1418 | Ronaldo, Juventus → Man Utd 2021 | confirmed (paid, ~EUR 15m) | none |
| 1419 | Ronaldo, Man Utd → Al-Nassr 2023 | contract terminated Nov 2022, joined free Jan 2023 | `kind` fixed to `free` |

Missing intermediate move noted, deliberately NOT invented into the data:
Inter → Parma (Jan 2002, Sukur). Rows are only added when a source confirms
both clubs, the year and the type.

### Pass 2 (2026-07-28, 40 highest-notoriety players, 132 rows, `kind` focus)

Scope: the full move history of the 40 highest-notoriety players. Every
end-of-contract or free-agent move was checked against public sources (ESPN,
Sky Sports, BBC, club announcements). 17 moves recorded as paid `transfer`
were in fact contract-expiry frees and were relabelled `free`. The remaining
rows were left unchanged (consistent with sources as paid moves, or already
labelled `loan`). This lifted the corpus `free` count from 10 to 27.

| Row | Claim | Verdict | Action |
| --- | --- | --- | --- |
| 1402 | Beckham, Real Madrid → LA Galaxy 2007 | contract expiry, joined free | `kind` → `free` |
| 981 | Lewandowski, Dortmund → Bayern 2014 | Bosman / free at contract end | `kind` → `free` |
| 51 | Klose, Bayern → Lazio 2011 | contract expiry, joined free | `kind` → `free` |
| 297 | Kaka, Real Madrid → AC Milan 2013 | released on a free | `kind` → `free` |
| 1378 | Casillas, Real Madrid → Porto 2015 | left on a free | `kind` → `free` |
| 8 | Buffon, Juventus → PSG 2018 | free at contract end | `kind` → `free` |
| 9 | Buffon, PSG → Juventus 2019 | free after PSG exit | `kind` → `free` |
| 1717 | Iniesta, Barcelona → Vissel Kobe 2018 | free at contract end | `kind` → `free` |
| 1275 | Gerrard, Liverpool → LA Galaxy 2015 | released on a free | `kind` → `free` |
| 1763 | Henry, Barcelona → NY Red Bulls 2010 | free move | `kind` → `free` |
| 1701 | Ronaldinho, AC Milan → Flamengo 2011 | contract ended, free to Brazil | `kind` → `free` |
| 1779 | Ibrahimovic, PSG → Man Utd 2016 | free at contract end | `kind` → `free` |
| 1780 | Ibrahimovic, Man Utd → LA Galaxy 2018 | released, joined free | `kind` → `free` |
| 1781 | Ibrahimovic, LA Galaxy → AC Milan 2020 | free agent | `kind` → `free` |
| 62 | Ozil, Arsenal → Fenerbahce 2021 | contract terminated, free | `kind` → `free` |
| 1259 | Modric, Real Madrid → AC Milan 2025 | free after Real exit | `kind` → `free` |
| 1716 | Muller, Bayern → Vancouver Whitecaps 2025 | free after Bayern exit | `kind` → `free` |

Left as `transfer` after review (ambiguous fee, no confirmation of a free):
Xavi, Barcelona → Al Sadd 2015 (row 1595).

The tier-1 rows for players outside this famous set were verified in a
parallel pass (see `verification-pass2-bot.md`).

## To do before shipping

- [ ] Record the exact Wikidata query/snapshot date used to build the dataset.
- [ ] Extend the verification pass beyond tier 1 (random sample of tiers 2-3,
      target: every tier-1 row checked, 10% of the rest).
- [x] Fold extracted FR/ES club + nationality names into the CSVs.
