# Marketing assets

Social and campaign artwork, kept apart from `store/play-assets/`, which holds
only what Google Play and the App Store consume.

## Contents

| File | Format | Use |
| --- | --- | --- |
| `stories/launch-en-1080x1920.png` | 1080x1920, English | Launch announcement story (Instagram, TikTok). This is the one to post. |
| `stories/launch-en-1080x1920-guide.png` | 1080x1920, English | Same layout with the link-sticker area outlined. Working guide only, never post it. |

## Social clips

`scripts/gen-clip.py` turns any five transfers from `data/` into a 1080x1920,
24-second "guess the player" clip: intro, five questions with a countdown and a
reveal, outro. Output goes to `build/clips/` and is not committed, the same
convention the rest of the generated artwork follows.

    python3 scripts/gen-clip.py --out build/clips --count 7

It draws from tier 1 only and never asks for the same player twice in a clip.
The dataset is the point: 1,900 transfers is a year of daily posts that nobody
has to write.

## Brand rules these follow

Everything is derived from `design/tokens.json`, `design/fonts/` and
`store/play-assets/`. Nothing is redrawn by hand.

- **Wordmark**: Unbounded Black, MER in white, CATO in yellow, tracking -5 px
  measured at 118 px, exactly as `scripts/gen-feature-graphic.py` builds it. In
  marketing artwork the wordmark is **flat**: no ink offset shadow, unlike the
  in-app `Wordmark` component and the Play feature graphic.
- **App icon**: `store/play-assets/icon-512.png` placed as is. The file has square
  corners; only the rounded mask the store applies is reproduced, at roughly 22%
  of the side. No border, no drop shadow.
- **Studio signature**: `A FLEGM GAME`, set above the icon.
- **Surfaces**: buttons, chips and cards keep the design system's ink borders and
  solid shadows. Those are part of the system, not decoration added on top.
- **Copy**: no leaderboard and no multiplayer may be mentioned, per the note for
  reviewers in `store/listing.en.md`.
- **Safe area**: the bottom third is deliberately empty so the link sticker has
  room. Keep it clear in any new story.
