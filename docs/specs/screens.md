# Screens

Column: 440px max, 16px gutters. Tab bar and banner share the exact same column.

## 01 Splash
Logo, loading bar (1.3s). Auto advances to onboarding on first launch, to Home afterwards.

## 02 Onboarding
Three panes, illustration placeholder + title + body. Dots, Skip, then NEXT / START.
1. Two clubs, one year. 2. Name the player, four options or free typing. 3. +3 per correct answer, points become balls.

## 03 Ad consent
Ivory card, three bullet points, ACCEPT ALL (primary) and NON PERSONALISED ADS (secondary). Reachable again from Settings.

## 04 Home
Ball balance chip, logo, two mode buttons pushed to the bottom.
EASY: 4 options. HARDCORE: 3 lives, free typing. No daily challenge, no XP, no level.

## 05 Game
Top bar: close (opens quit dialog), 10 progress pips, 3 lives (hardcore only), score pill.
Card: ink header with kind chip (TRANSFER / LOAN / FREE TRANSFER, yellow chip for loans) and the year at 42px, then origin club in grey, arrow, destination club large. Hardcore adds a masked name.
Sponsor board strip below the card.
Answers: four ink buttons, or input + HINT (3) + SUBMIT in hardcore.
No skip. No result sheet: the answer turns green or red, the card border follows, the score bumps +3 or 0, auto advance after 1.9s (tap the card to go faster).

## 06 Quit dialog
Modal over the game. KEEP PLAYING (yellow) / QUIT (coral).

## 07 Interstitial
Full screen slot between the last question and the recap. Countdown 5s, then explicit close. Skipped entirely when ads are removed.

## 08 Recap
Stars (3 at 90% correct, 2 at 60%, 1 above zero), points, balls won, rewarded video for double balls (hidden at zero), 300x250 rectangle, list of missed transfers, PLAY AGAIN / Home.

## 09 Shop
Remove ads (3,99 €), 10 hints (120 balls), 50 hints (500 balls), 200 balls (1,99 €), restore purchases.

## 10 Profile
Four lifetime stats, entry to Settings.

## 11 Settings
Sound, vibration, notifications toggles. Rows: language (system, read only), ad consent, replay intro, offline screen, matching lab. Version footer.

## 12 Offline
Placeholder mark, title, body, RETRY. Downloaded rounds stay playable.

## Dev only
Matching lab: target player, typed guess, Levenshtein threshold, live verdict trace, dataset stats, surname collisions. Hidden with the `showLab` prop.
