# Native app shells

`ios/` (SwiftUI) and `android/` (Jetpack Compose) consume the shared Rust
core through the UniFFI bindings built by `scripts/build-ios.sh` and
`scripts/build-android.sh`. The app projects themselves arrive with Phase 5
(screens); what exists now is their build configuration.

## AdMob configuration

One config file per build flavor, checked in (AdMob app and ad unit IDs are
public by nature: they ship inside the released binaries). Debug always uses
Google's demo IDs so development never generates invalid traffic; production
IDs load only in Release builds.

- iOS: `ios/Config/AdMob-Debug.xcconfig` / `ios/Config/AdMob-Release.xcconfig`.
  Attach them as base configurations of the Debug/Release configurations in
  Xcode. Expose `$(ADMOB_APP_ID)` as `GADApplicationIdentifier` in Info.plist
  and the four `ADMOB_UNIT_*` settings as Info.plist keys the app reads.
- Android: `android/config/admob-debug.properties` /
  `android/config/admob-release.properties`. Load the matching file per build
  type in `build.gradle.kts`; map `ADMOB_APP_ID` to the
  `com.google.android.gms.ads.APPLICATION_ID` manifest placeholder and the
  four `ADMOB_UNIT_*` entries to `BuildConfig` fields.

The demo rectangle slot reuses the fixed-size banner demo unit (Google
publishes no dedicated 300x250 demo unit; a banner unit serves that size).
Which slot renders when is decided by the core (`Game.should_show_ad`), not
by this configuration. See `docs/MONETIZATION.md` and `docs/specs/ads.md`.

## Identifiers

- Bundle ID / application ID (both platforms): `com.nicogaray.mercato`.
- Remove-ads product ID (both stores): `mercato_remove_ads`, 3.99 EUR,
  non-consumable.
