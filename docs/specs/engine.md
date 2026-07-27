# Matching engine

The hardcore mode depends entirely on this. Port it exactly, do not approximate.

## Normalisation
NFD, strip diacritics, map Ø ø Ł ł Ð ð Þ þ, lowercase, turn `. ' ’ \` - _` into spaces, drop everything outside [a-z0-9 ], collapse spaces, trim.

## Distance
Levenshtein. Threshold depends on the target length: 0 up to 4 characters, min(1, base) up to 6, base above (base = 2).

## Routes, in order
1. exact: the normalised guess equals a canonical name (fr / en / es).
2. alias: equals a known alias.
3. fuzzy: distance within threshold, only if the guess is not an exact name of another player.
4. surname: distance within threshold against a surname variant. Particles (van, de, di, dos, le, mc...) are kept with the surname, never eaten into the first name.
5. none.

## Cardinal rule
An approximate match must never beat an exact match on another player. Without it, "kane" passes for Kanté.

## Ambiguity
A surname shared by several players is refused with a dedicated message asking for the first name. It does not consume a life.

## Distractors (easy mode)
Score candidates by same position (+3), birth year within 6 (+2), same nationality (+1), fame bonus, fame distance penalty, plus noise. Take the top 3. A decoy must be as famous as the answer or it fools nobody.
