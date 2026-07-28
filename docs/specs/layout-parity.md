# Layout parity brief

Goal: the Android app lays out the same as the iOS app, screen for screen.
iOS is the reference and must not change. This brief lists what is still
different, how to measure it, and what "done" means.

This is the sequel to `android-parity.md`, which is finished. That one was
about the values each side declared. This one is about where things land.

## What is already settled

Do not redo any of this.

**Every design value is shared.** `design/tokens.json` is the single source for
colour, typography, radii and opacities. Both apps read it: iOS through
`TypeToken` and `DS.font(_:)` / `View.typeStyle(_:)`, Android through
`typeStyle(token, colour)` and `Color.dim(opacity)`. Nothing is hardcoded on
either side. `node scripts/check-design-parity.mjs` proves it and is green.

**The type scale was rebuilt from iOS**, losslessly: 55 roles, each carrying
the exact font, size, weight and tracking an iOS call site already stated.
Three tokens had held values iOS never used (`year`, `club-from`, `label`) and
were retargeted; Android had been inheriting them.

## What is left

Spacing, weighting and container heights are **not** in tokens.json, so nothing
constrains them, and the two apps drifted. The parity checker cannot see this:
it reads declarations, not positions. It will stay green throughout this work.

Measured on 2026-07-28, at an identical logical size of 402x874: **83
differences over 11 screens**.

## How to measure

Both apps carry a debug route so any screen can be reached directly, without
walking the flow and without the UMP consent form landing on the capture.

```bash
./gradlew :app:assembleDebug                 # in apps/android
./scripts/capture-parity.sh                  # all 11 screens, both platforms
./scripts/capture-parity.sh easy settings    # or just these
./scripts/layout-diff build/parity-shots easy
open build/parity-shots/side-easy.png        # the visual, iOS left
```

`capture-parity.sh` resizes the emulator to the iPhone's logical size, so a
difference in the output is a difference in the layout rather than in the
canvas. It installs the APK first, so rebuild before capturing.

`layout-diff` classifies every pixel row by the nearest token colour, turns
runs into bands, and compares them in dp. Numbers below are `start` and
`height` in dp, iOS then Android.

**Known limit.** A large glyph inside a solid button breaks the tool's
"mostly one colour" test and splits the band, so some band-count mismatches are
measurement noise. Trust a clear delta on a matched pair of bands; treat a
`mismatch` line as a prompt to look at the side-by-side image, not as a fact.

## The screens

Ordered by how badly they read. Each entry gives the measurement, then the
suspected cause. Verify the cause before acting: the measurement is reliable,
the diagnosis is a starting point.

### 1. Settings

```
ivory row   258  52   ->  301  60    +43  +8
ivory row   334  22   ->  376  60    +41 +38
ivory row   366  48   ->  450  60    +84 +12
field       434 335   ->  520  96    +86 -239
```

The rows are a different height and the whole stack sits up to 86dp lower.
Worse, iOS leaves 335dp of flexible space before the footer and Android
leaves 96.

Cause: `MenuScreens.kt` uses a fixed `Gap` before the banner because a weighted
spacer is illegal inside a scrollable column, and says so in a comment. iOS
uses a real spacer. Either give the Android column a non-scrolling layout with
a weighted spacer, or compute the remaining height.

### 2. Onboarding

```
ivory art   268 190  ->  465  21    mismatch
field       473  23  ->  504 260    +30 +237
```

The illustration block is a completely different size and the copy below it
sits far lower.

### 3. Offline

```
field       237  81  ->  241  46     +4  -35
field       506 263  ->  427  21    -79 -241
```

The whole screen is compressed on Android and the yellow retry button lands in
a different place entirely.

### 4. Easy

```
ivory card  221  49  ->  198  21    -23 -28
answers     464  30  ->  379  34    -86  +5
            511  24  ->  431  29    -81  +5
            560  30  ->  490  34    -70  +5
            607  20  ->  541  30    -66 +10
            642  26  ->  600  34    -42  +8
            692  30  ->  652  29    -40  -0
            739  24  ->  711  34    -28 +10
```

The card is shorter and higher; the four answers start 86dp too high and
each is a few dp taller, so the error shrinks down the stack. The card is not
taking its share of the column.

### 5. Recap and Recap (lost)

```
ivory       219  36  ->  204  74    -15 +38
ivory       263 179  ->  290 147    +27 -32
ivory       686  62  ->  599  35    -87 -27
ivory       759  71  ->  676  61    -84 -10
```

The stars block and the score card trade height, and both buttons at the
bottom sit ~85dp too high with an extra ivory band below them on Android.

### 6. Profile

```
field       429  85  ->  447  44    +18 -41
ivory       518  25  ->  555 223    mismatch
```

The stat block collapses and something ivory runs for 223dp where iOS has 25.

### 7. Consent

```
ivory card  351 138  ->  346  70     -5 -67
field       504 163  ->  450  31    mismatch
```

The card is half the height. Note the actions and footer were already moved
outside the card in the previous parity pass; this is the card itself.

### 8. Hardcore

```
ivory       221  41  ->  198  78    -23 +37
ivory       501  72  ->  529  35    +28 -37
```

The card and the guess field trade height in opposite directions.

### 9. Lab (developer only, lowest priority)

```
ivory       279  46  ->  302 247    mismatch
field       325  72  ->  724  22    mismatch
```

### 10. Home

```
yellow      355  75  ->  386  31    +31 -44
ivory       454  27  ->  441  27    -13  +1
field       544 164  ->  533 185    -11 +21
ivory        -   -   ->  748  21    mismatch
```

Likely the least broken of the set, and the yellow delta may be the tool
splitting a band around the label. Check the image before changing anything.

## How to fix

Two rules, in this order.

**Read the iOS layout first.** Every fix is "make Android do what
`apps/ios/Sources/*.swift` already does". Padding, spacer weights, fixed
heights, `maxWidth`, alignment. Do not invent a third layout.

**Promote a value to a token only when both platforms need the same number in
the same role.** `space` currently holds xs/sm/md/lg/xl and a gutter, `layout`
holds `column-max` and `tap-min`. If iOS states a padding of 22 in four places
and Android needs the same, that is a token. A one-off inset is not; put it in
the call site with a comment. Growing the token file with 40 single-use
spacings would repeat the mistake the type rebuild had to undo.

## Constraints

- **iOS does not change.** If a screen looks wrong on both, say so and stop;
  do not fix it here.
- Do not weaken `check-design-parity.mjs` or add entries to its `IGNORE` to
  make a number go away.
- Do not touch `build/`: it is generated. Edit `design/tokens.json` and run
  `node scripts/gen-design-tokens.mjs`.
- Commit style is conventional commits, one screen or one root cause per
  commit, body explaining why rather than what.

## Definition of done

For each screen, in order:

1. `./scripts/capture-parity.sh <route>` then `./scripts/layout-diff` shows no
   matched band differing by 8dp or more in start or height.
2. The side-by-side image agrees. The tool is a filter, not a verdict.
3. `xcodebuild ... build` succeeds for iOS.
4. `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug`
   passes for Android.
5. `node scripts/check-design-parity.mjs` is still green.

Routes: `onboarding consent home easy hardcore recap recaplose profile
settings lab offline`.

## Environment

macOS. `JAVA_HOME`, `ANDROID_HOME` and the SDK path are set in `~/.zshenv`;
Gradle also reads `org.gradle.java.home` from `~/.gradle/gradle.properties`.
The iOS simulator used for captures is an iPhone 17 Pro; its UDID is at the
top of `capture-parity.sh`. The Android AVD is `mercato_pixel`, resized by the
script.

One caveat worth knowing: `lintDebug` was red before this work on three
`NewApi` errors in the generated uniffi bindings. `scripts/build-native.sh`
now annotates them, so regenerating the bindings keeps lint green.
