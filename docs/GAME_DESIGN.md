# Game Design

> This mirrors the shipped prototype (`reference/web-prototype/functional-spec.md`). The prototype is the
> source of truth; this doc restates it for the Voie A (Rust core + native UI)
> build. See [REUSE.md](REUSE.md).

## Concept

The app shows a football transfer - club left, club joined, year, and move kind -
with the player's name hidden. The player must name the footballer. **Names only,
no photos, no club badges.**

## Two modes

| Mode | Pool | Answering |
| --- | --- | --- |
| **Easy** | tier 1 + 2 transfers (~956) | 4-option multiple choice |
| **Hardcore** | every transfer (1 905) | free-text input, 3 attempts + hints |

Difficulty comes from the **answer mode**, not only transfer rarity. `tier`:
1 = mainstream, 2 = informed fan, 3 = expert.

## Game loop

1. Pick a random transfer from the mode's pool.
2. Show the card: kind + year in the header band; origin club dimmed; arrow;
   destination club large. Hardcore adds a masked name.
3. Player answers (tap an option, or type + submit).
4. Reveal: card and name turn **green** if correct, **red** if not.
5. After ~1.9 s, or on tapping the card, advance. **No skip, no result sheet
   between questions.**

A round is 10 questions (10 progress pips).

## Decoy selection (Easy mode)

Score every other player, take the top 3 (then shuffle the 4 options). A decoy
must be roughly as famous as the answer or it fools nobody.

```
+3    same position
+2    birth-year gap <= 6
+1    same nationality
+2  * notoriety / max_notoriety           (plausibly famous)
-1.5 * |notoriety - target| / max_notoriety   (comparable standing)
+ random in [0, 0.9)
```

Port `distractorsFor` from the reference exactly, including the seeded RNG.

## Answer matching (Hardcore mode)

The critical component - **port exactly**, do not approximate. Full algorithm in
[ARCHITECTURE.md](ARCHITECTURE.md#matching-engine) and
`core/reference/engine.reference.js`. Summary:

- Normalize (NFD, strip diacritics, `ø→o` `ł→l` …, lowercase, punctuation→space).
- Adaptive Levenshtein threshold by target length: `<=4 → 0`, `5-6 → 1`, `>6 → 2`.
- Routes in order: exact → alias → fuzzy → surname → none.
- Surname variants keep particles (`van`, `de`, `di`, `dos`, `mc` …).
- **Two mandatory safety rules**: exact match on another player beats a fuzzy
  match ("kane" ≠ Kanté); a surname shared by several players is rejected asking
  for the first name (does **not** consume an attempt).

## Attempts & hints (Hardcore)

3 attempts. Each failed attempt or hint request unlocks the next hint, in order:
**nationality → position → initial + surname letter count.**

## Scoring

- Correct: **+3** (animates green). Wrong: **+0** (animates red).
- The score pill keeps the color of the last result.
- **Streak**: consecutive correct answers; resets to 0 on the first miss.
- Score and streak reset at the start of every session (no persistence in v1).

## Monetization surface

- **No shop and no soft currency.** The only purchase is **Remove ads (€3.99)**,
  a one-time non-consumable + Restore purchases. See [MONETIZATION.md](MONETIZATION.md).
- Hints in Hardcore are **free gameplay** (unlocked by attempts), not a product.
- This drops the prototype's balls currency, the hint/ball shop items, and the
  rewarded "double balls" video (nothing to reward without a currency).

## Localization

- UI strings: EN / FR / ES (114 keys, ready in `strings/strings.json`).
- Language read from the system setting, **fallback English**, no in-app picker.
- The game ships in all three languages, so club names, positions, and
  nationalities are **translated in the data for v1** (player names are identical
  across languages). See the i18n requirement in [DATA.md](DATA.md).

## Out of scope (v1)

No server, no accounts, no online leaderboard, no daily challenge, no sharing, no
cross-session persistence. (A seeded RNG exists in the reference and could enable
a daily challenge post-launch.)
