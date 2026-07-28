# Android parity brief

Goal: the Android app renders the same as the iOS app, screen for screen.
iOS is the reference. This brief lists what is still different and why.

Written after a full screen-by-screen and component-by-component comparison of
the two implementations against `design/tokens.json`. It is a work list, not a
report: each item names the file to change on Android and the iOS value to
match.

Roughly 50 individual differences remain. They are not 50 separate problems.
They collapse into the eight root causes below, and fixing those eight closes
almost all of them.

## Status

Fixed since the comparison was run:

- score pill fly-up ("+3" / "0") ported to Android
- score pill kept the verdict colour for the whole round instead of resetting
  on the next question

Everything below is outstanding.

---

## Root cause 1: `solidRaised` is not applied to four surfaces

The ink outline plus solid offset shadow is the signature of the design. The
modifier exists on Android and is correct. It is simply missing from four
places, and each one is a large surface, so the screens read as flat.

| Surface | iOS | Android |
| --- | --- | --- |
| Home mode buttons (Easy / Hardcore) | `solidRaised(radius: card, depth: 10)` `Screens.swift:109` | `background` + `border` only, `MenuScreens.kt:305` |
| Consent card | `solidRaised(radius: card, depth: 10)` `Screens.swift:409` | `background` + `border` only, `MenuScreens.kt:242` |
| Quit dialog card | `solidRaised(radius: card, depth: 12)` `Screens.swift:507` | `background` + `border` only, `GameScreens.kt:393` |
| Hardcore guess field | `solidRaised(radius: card, depth: 10)` `DesignSystem.swift:447` | Material3 `OutlinedTextField`, `GameScreens.kt:285` |

Effort: one line each for the first three. The guess field is a rewrite, see
root cause 6.

Severity: the Home mode buttons and the guess field are the largest interactive
elements in the app.

## Root cause 2: the app background is a linear gradient, not a radial one

Affects every screen in both apps.

- iOS: `RadialGradient`, centred at the top, stops at 0 / 0.44 / 1, radius 130%
  of the width (`DesignSystem.swift:38`)
- Android: `Brush.verticalGradient`, straight top to bottom with no horizontal
  falloff (`Components.kt:68`)

The difference is a soft central burst versus flat horizontal bands. It is the
backdrop of every screenshot, so it colours every side-by-side comparison.

Effort: one function.

## Root cause 3: `InkButton` ignores its call site

Android's `InkButton` hardcodes the label style (`Type.answer`: 18px, weight
800, tracking `-0.045em`), the height (56dp), the shadow depth (8dp) and the
radius (`Radius.large` = 22) for every button in the app (`Components.kt:176`,
`:201`, `:212`).

iOS varies all four per button:

| Button | iOS label | iOS depth / radius |
| --- | --- | --- |
| Onboarding CTA | 22 / 900 / `-0.03em` | 8 / 22 |
| Consent Accept | 18 / 900 / `-0.03em` | 8 / 22 |
| Consent Refuse | 15 / 800 / `-0.03em` | 8 / 22 |
| Quit Stay | 17 / 900 / `-0.03em` | 12 / 20 |
| Quit Go | 15 / 800 / `-0.03em` | 8 / 20 |
| Offline Retry | 17 / 900 / `-0.03em` | 9 / 20 |
| Recap Again | 18 / 800 / `-0.045em` | 8 / 20 |

Only Recap's "Again" matches the Android default. Six of seven buttons are the
wrong size, weight, tracking or depth, which flattens the primary/secondary
hierarchy on every screen that has two buttons side by side.

Fix: give `InkButton` label-style, depth and radius parameters, then pass the
iOS values at each call site.

## Root cause 4: `InkButton` has no disabled state

iOS dims Hint and Submit to 40% opacity when disabled
(`DesignSystem.swift:562`). Android's `InkButton` only gates the click handler
(`Components.kt:166`), so a spent Hint button and an empty-guess Submit button
both look fully active.

This is a functional regression, not only a visual one: in Hardcore the player
has no way to see that the hint is used up.

## Root cause 5: text overrides inherit tracking they should not

Recurring pattern. Android takes a token and overrides only the font size:

```kotlin
typeStyle(DesignTokens.Type.year, ...).copy(fontSize = 64.sp)
```

The `year` token carries `tracking: 0.01em`, `clubTo` carries `-0.05em`, and
`answer` carries `-0.045em`. iOS applies tracking explicitly per call site and
frequently applies none at all.

Affected, all visible at large sizes:

- Recap score number, 64sp, iOS has no tracking (`Screens.swift:226`)
- Recap stat-tile values, 22sp (`GameScreens.kt:376`)
- Profile stat values, 26sp, iOS has no tracking (`Profile.swift:140`)
- Recap Home button, iOS `-0.03em` not `-0.045em` (`Screens.swift:196`)
- Hint chip, iOS `-0.02em` not `-0.045em` (`DesignSystem.swift:489`)
- Score pill number, iOS has no tracking (`DesignSystem.swift:233`)

Fix: override `letterSpacing` alongside `fontSize` at each site, or add a
`typeStyle(token, color, size, tracking)` overload so the omission cannot
happen silently.

## Root cause 6: the Hardcore guess field is a stock Material control

The single most important control in Hardcore mode.

- iOS: raised ivory box, heavy ink border, verdict-tinted fill, input text at
  `unbounded(30, 900)`, placeholder in the same display type
  (`DesignSystem.swift:447`)
- Android: Material3 `OutlinedTextField`, `RoundedCornerShape(18)`, default
  Material outline and focus chrome, input at 24sp, placeholder styled small
  and muted (`GameScreens.kt:285`)

It looks like an Android form field dropped into the game. Needs to be rebuilt
on `BasicTextField` with the app's own surface.

## Root cause 7: the ad banner paints a full-width ink bar

- iOS `BannerSlot`: a bare 320x50 ad, no background, so the blue gradient shows
  through on any screen wider than 320pt (`AdViews.swift:24`)
- Android `MenuBanner`: `fillMaxWidth().height(50.dp).background(ink)`
  (`ads/AdsController.kt:146`)

On Home, Profile and Settings, Android draws a solid dark bar across the full
width. It is the most visible difference on Home, and it is worst before the ad
loads, when the bar is empty.

## Root cause 8: font family mismatches

iOS uses IBM Plex Mono in several places where Android uses Figtree.

- Onboarding illustration label: iOS `mono(11)`, Android the `label` token
  (`Screens.swift:356` vs `MenuScreens.kt:161`)
- Settings link-row values (language, consent status): iOS `mono(11)`, Android
  the `label` token (`Screens.swift:709` vs `MenuScreens.kt:618`)
- Settings version footer: iOS `mono(11)` with no tracking, Android the
  `technical` token with `0.18em` (`Screens.swift:590` vs `MenuScreens.kt:524`)
- Close and back buttons: iOS renders "✕" and "‹" in the **system** font at 16
  and 20 weight black, Android renders them in Unbounded via `Type.answer`
  (`DesignSystem.swift:159` vs `GameScreens.kt:98`, `MenuScreens.kt:452`)

---

## Missing or wrong content, not styling

These change what the screen says, so they rank above the styling work.

1. **Settings language row drops the language name.** iOS shows
   `"English · System"`; Android shows only `"System"`, because `LinkRow` is
   passed just `R.string.systemV` (`Screens.swift:566` vs
   `MenuScreens.kt:501`).
2. **Consent screen structure.** iOS keeps the title, body and bullets inside
   the ivory card, and puts the Accept/Refuse buttons and the footer outside it
   on the blue background (`Screens.swift:381`). Android puts all of it inside
   the card (`MenuScreens.kt:239`), so the bottom half of the screen is ivory
   where iOS is blue.
3. **Offline placeholder.** iOS shows a 92x92 hatched square with no text
   (`Screens.swift:772`). Android shows a full-width 160dp hatch strip stamped
   with the word "MERCATO" (`OfflineScreen.kt:28`).
4. **Settings purchase card has an extra subtitle line** that iOS does not show
   (`MenuScreens.kt:479` vs `Screens.swift:670`).
5. **Privacy row loses its "↗" cue.** iOS marks the row that opens a browser
   with an external-link glyph (`Screens.swift:572`); Android shows the same
   generic chevron as every other row (`MenuScreens.kt:515`).
6. **Wordmark is undersized.** The `type.logo` token is 74. iOS uses it on both
   Splash and Home. Android uses 52sp on Splash and 58sp on Home
   (`MenuScreens.kt:69`, `:289`), so the logo is up to 30% smaller and the two
   Android screens do not even agree with each other.

## Lab screen (developer only)

Lower priority, listed for completeness.

- Back control has no box at all and is 30px Unbounded against iOS's 38x38 ink
  box with a 20pt system glyph (`Lab.swift:50` vs `LabScreen.kt:58`)
- The best-match and edit-distance readout is missing entirely
  (`Lab.swift:122` vs `LabScreen.kt:134`)
- Verdict chip is opaque `blueNight` against iOS's `ink` at 40%
  (`Lab.swift:118` vs `LabScreen.kt:120`)
- `LabField` adds a visible border iOS deliberately omits

## Smaller differences

Noticeable side by side, cheap to fix, none of them structural.

- Quit dialog: iOS scrim is a custom ink navy at 78%, Android uses the platform
  default; iOS title is 22, Android 30; iOS caps the card at 360pt, Android has
  no cap
- Recap title 28 (iOS) vs 30 (Android); "PTS" label 12.5/`0.16em` vs
  10.5/`0.2em`; stat-tile label weight 900 vs 700
- Onboarding: skip label 13/900/`0.06em` vs 10.5/`0.2em`; body 16 at 68% white
  vs 15 at 85% ivory
- Consent: body and bullet colours are swapped between the platforms; bullet
  glyph "·" in blue vs "• " in ink; body 14.5 vs 15
- Profile stat-row label weight 800 vs 700
- Home mode button radius 26 vs 22, padding 20/24 vs 18/22
- Home: iOS groups the wordmark and both buttons as one block with flexible
  space above and below; Android puts a flexible spacer between the wordmark
  and the first button, so the gap stretches on tall screens
- Settings "Owned" badge is a capsule on iOS, an ellipse on Android
  (`CircleShape` on a non-square box)
- Toggle knob does not animate on Android (iOS uses `.snappy(0.18)`); knob
  inset 2 vs 3
- Score pill has no `monospacedDigit` on Android, so it resizes as the score
  changes
- Splash: track border 4 vs 3; iOS holds the full bar for 100ms before leaving,
  Android cuts away the instant it fills

## Already matching

Verified identical, no work needed: transfer card, answer buttons, progress
pips, hearts, tab bar, recap score card container, Profile empty state,
Settings toggle and link row surfaces, and the decision on both platforms to
show no ad during a question.

## Known layout limit, both platforms

At 320dp wide by 640dp tall the four answer cards do not all fit on screen.
Smaller than most real phones, but it exists.
