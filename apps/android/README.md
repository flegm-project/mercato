# Mercato Android

Jetpack Compose app over the shared Rust core, mirroring the iOS app in
`apps/ios`. All game rules live in `core/` and reach Kotlin through the
generated UniFFI bindings; this module owns only screens, navigation,
preferences, and the Google Mobile Ads SDK calls.

## Building

Generated inputs are never committed. From the repo root, before the first
build (and after any change to the FFI surface, tokens, or strings):

```sh
./scripts/build-native.sh android    # UniFFI Kotlin bindings + per-ABI .so
node scripts/gen-design-tokens.mjs   # build/tokens/DesignTokens.kt
node scripts/gen-strings.mjs         # build/strings/android resource tree
```

Then:

```sh
cd apps/android
./gradlew assembleDebug
```

`app/build.gradle.kts` wires those `build/` outputs in as source sets, stages
the CSV dataset and fonts as assets, and injects the AdMob IDs per build type:
debug always uses Google's demo IDs, release carries the production IDs of
the "Mercato" Android app entry in the AdMob console. CI runs the exact same
steps (see `.github/workflows/ci.yml`, job `android`).

## Ads

Every slot asks the shared gate first (`Game.shouldShowAd`, backed by
`mercato_core::ads`): banner on menu screens, sponsor board in game,
interstitial at the round break, rectangle on the recap.

Consent: the Google UMP form shows at first launch where GDPR applies
(`ConsentManager`), derives personalised/non-personalised from the TCF
purpose bits, and feeds `Game.setAdConsent`; the app's own consent screen
stays the choice surface everywhere else, and Settings opens whichever is
authoritative. The core's `adPersonalizationAllowed` drives the SDK's
publisher privacy personalisation state (the modern npa equivalent) on every
request, the conservative overlay on top of the SDK's own TCF reading.

Remove-ads: `BillingManager` (Play Billing, one-time product
`mercato_remove_ads`) mirrors the store entitlement into
`Game.setAdsRemoved` at launch, on resume, on purchase and on restore, and
acknowledges new purchases. The purchase and restore rows live in Settings;
there is no shop screen.

## 16 KB memory pages

Android 15 runs on devices whose memory pages are 16 KB, and since 1 November
2025 Play refuses uploads whose 64-bit `.so` files are laid out for 4 KB ones.
Version code 2 was refused for exactly one library: JNA 5.14.0's
`x86_64/libjnidispatch.so`, whose `PT_LOAD` segments declare a 0x1000
alignment. Nothing in the Gradle build, and nothing in lint, looks at that.

Two things keep it fixed:

- `scripts/build-native.sh android` refuses to run on an NDK older than r28,
  which is the first one that links for 16 KB pages by default, and passes
  `-Wl,-z,max-page-size=16384` anyway. It checks its own output afterwards.
- `scripts/check-16k.py` reads the alignment straight out of the finished
  `.aab`/`.apk`; `verify16kAlignmentBundle` runs it after `bundleRelease` and
  `verify16kAlignmentApk` after `assembleRelease`, one task per artifact so
  neither judges a file the build it follows did not produce. It sees what
  Play sees, which is the point: most of the libraries in the upload are
  prebuilt and arrive from dependencies, so the only honest check is on the
  artifact.

For a dependency's library there is no linker flag to reach for; the version
is the fix. JNA 5.17.0 was the first release with the flags applied
(java-native-access/jna#1654).

## Native libraries and the NDK

`ndkDir` in `app/build.gradle.kts` sets both `ndkPath` and `ndkVersion`. Both,
because they do different jobs: the path says where the NDK is, the version is
what the strip and symbol-extraction tasks resolve their tool through. Setting
the path alone changes nothing except the warning about the two disagreeing,
which is how the first attempt at this looked like it had worked.

What it buys, measured on the same source: `libmercato_ffi.so` for arm64 goes
from 1 085 680 bytes to 753 208, and the bundle gains
`BUNDLE-METADATA/com.android.tools.build.debugsymbols/` for all three ABIs, so
a panic in the Rust core reaches the Play console as named frames rather than
a column of addresses. Without it AGP says "Unable to strip the following
libraries, packaging them as they are" in one line among a thousand, and the
release ships heavier and mute.

### Warnings that are not ours to fix

Play also reports `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, deprecated in
Android 15, at obfuscated `j1.s.a`. That is not app code: `mapping.txt`
resolves the only class in the release that touches
`WindowManager.LayoutParams.layoutInDisplayCutoutMode` to
`com.google.android.gms.ads.internal.util.zzx`, inside the Google Mobile Ads
SDK, which is already on its newest version. Nothing to change here until
Google ships it; the API still works, it is only deprecated.

Play's separate note that some libraries were "compiled with an older NDK"
also lands on `libjnidispatch.so`. JNA links its Android libraries with a
plain Makefile that never emits the `.note.android.ident` section Play reads
the NDK version from, so the notice can survive a version bump even though
the alignment, which is the part that actually crashes, is correct.
