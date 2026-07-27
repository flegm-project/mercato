# MERCATO, functional specification

Mobile guessing game. The app shows a football transfer (club left, club joined, year) and the player has to name the footballer. Names only, no photos and no club badges.

## Data

Read-only SQLite database, shipped inside the app. Ready-made file: `mercato-en.db`.

```sql
club        (id, name, notoriety)
player      (id, name, position, nationality, birth_year, notoriety)
player_alias(player_id, alias)
transfer    (id, player_id, from_club, to_club, year, kind, tier)
```

- `position` : `gk` | `def` | `mid` | `fw`
- `kind` : `transfer` | `loan` | `free`
- `tier` : 1 mainstream, 2 informed fan, 3 expert
- `notoriety` : integer, used for sorting and for picking decoys

Current volumes: 412 clubs, 513 players, 945 aliases, 1 905 transfers.

## Two modes

| Mode | Pool | Answering |
|---|---|---|
| Easy | tier 1 and 2 transfers (956) | 4-option multiple choice |
| Hardcore | every transfer (1 905) | free text input, 3 attempts |

## Game loop

1. Pick a random transfer from the mode's pool
2. Show the card: kind and year in the header band, club left (dimmed), vertical track with a token, club joined
3. Player answers
4. Reveal: the token slides down, card and name turn green if correct, red if not
5. After 1 900 ms, or on tapping the card, move to the next transfer

No intermediate screen, no skip button.

## Decoy selection (easy mode)

Score every other player, then take the top 3:

```
+3   same position
+2   age gap <= 6 years
+1   same nationality
+2 * notoriety / max_notoriety                        decoy must be plausible
-1.5 * |notoriety - target_notoriety| / max_notoriety  comparable standing
+ random in [0, 0.9]
```

Shuffle the 4 options before display.

## Name matching (hardcore mode)

The critical component. Strict order, stop at the first match.

**Normalisation**: Unicode decomposition, strip diacritics, `ø→o`, `ł→l`, lowercase, punctuation to spaces, collapse whitespace.

**Adaptive threshold**, based on the length of the target string:

| Length | Max Levenshtein distance |
|---|---|
| <= 4 | 0 |
| 5 to 6 | 1 |
| > 6 | 2 |

**Surname variants**: last token, plus the form including particles (`van`, `von`, `de`, `del`, `di`, `da`, `dos`, `du`, `le`, `la`, `bin`, `mc`, `ter`, `den`…). "Virgil van Dijk" yields both `van dijk` and `dijk`.

**Steps**

1. Exact match against the canonical name or any alias → accept
2. Levenshtein distance within threshold against name or alias → accept
3. Levenshtein distance within threshold against a surname variant → accept
4. Otherwise reject

**Two safety rules, both mandatory**

- *Exact beats fuzzy.* A fuzzy match is rejected if the input exactly matches the name or surname of a **different** player in the database. Without this rule, "kane" is accepted for Kanté.
- *Ambiguity.* If a surname is shared by several players, reject it with a "add the first name" message. This does not consume an attempt.

## Attempts and hints (hardcore)

3 attempts. A failed attempt or a hint request unlocks the next hint, in order: nationality, then position, then initial and letter count of the surname.

## Scoring

- Correct answer: +3, gain animates in green
- Wrong answer: +0, animates in red
- The counter keeps the colour of the last result
- Streak: consecutive correct answers, reset to zero on the first miss
- Score and streak reset at the start of every session

## Languages

English, French and Spanish. Language read from the system setting, falling back to English. No in-app language picker.

Club names, positions and nationalities are translated in the database. Player names are identical across all three languages.

The development dataset is English only. Add `name_fr` and `name_es` columns to `club`, and a translation table for positions and nationalities, when wiring up localisation.

## Out of scope

No server, no accounts, no online leaderboard, no daily challenge, no sharing. Progress is not persisted between sessions in this version.

## Files

| File | Purpose |
|---|---|
| `mercato-en.db` | SQLite database, ready to embed. Foreign keys and check constraints included |
| `dataset-en.json` | Same data as a single JSON file |
| `clubs.csv`, `players.csv`, `player_aliases.csv`, `transfers.csv` | Same data, inspectable and diffable |
| `tokens.json` | Design system source of truth |
| `DesignTokens.swift`, `DesignTokens.kt` | Generated token exports |
| `design-system.html` | Visual reference sheet |
| `mercato.html` | Playable demo. Reference implementation of the matching engine and the game loop |

## Data provenance

Transfers were derived from Wikidata (CC0) by chaining consecutive club spells, not read from a transfer feed. Chaining is deliberately strict: about 570 candidate pairs were discarded for implausible date gaps or overly obscure clubs. The dataset invents nothing, but it is incomplete, and each row should be confirmed against a second source before shipping.
