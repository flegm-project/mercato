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
