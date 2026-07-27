# Monetization

Two mechanisms, both native:

1. **Ads** (default, free users).
2. **Remove-ads** - a one-time in-app purchase (€3.99) that permanently disables
   ads. **This is the only IAP.** No shop, no soft currency, no consumables, no
   rewarded ads. (The prototype's balls/hints shop is dropped.)

## Ads

- **Provider**: Google AdMob (single SDK covering iOS + Android).
- **Formats**:
  - **Interstitial** between rounds/sessions - shown at natural breaks (e.g.
    after a game-over in Endless), frequency-capped to protect retention.
  - Optional **banner** on non-gameplay screens (menus/results). Avoid banners
    during active play.
  - Static **sponsor board** below the transfer card in-game (no interaction).
  - **No rewarded ads** (they existed only to double the removed balls currency).
- **Frequency capping** and placement rules live in native code but read a
  single source of truth (e.g. minimum rounds between interstitials) to stay
  consistent across platforms.
- **Consent/privacy**: implement the AdMob consent flow (GDPR/UMP for EU,
  App Tracking Transparency on iOS). Required for store compliance.

## Remove-ads purchase

- **iOS**: StoreKit 2, a **non-consumable** product.
- **Android**: Play Billing, a one-time (non-consumable) product.
- On purchase (or restore), set a local `ads_removed` flag; the app then never
  requests or shows ads.
- **Restore purchases** must be supported (App Store requirement; good practice
  on Play too).
- Validate/restore entitlement on launch so reinstalls and new devices recover
  the purchase.

## Where the logic lives

- The **decision** "should ads be shown right now?" is implemented in
  `mercato_core::ads` (`AdsGate`) and exposed on the FFI `Game`:
  `should_show_ad(placement)`, `record_interstitial_shown()`,
  `set_ads_removed(bool)`, `set_ad_consent(...)`,
  `ad_personalization_allowed()`. Answered rounds advance the gate
  automatically inside `submit_guess`; frequency parameters (`AdsConfig`:
  warmup rounds, min rounds between interstitials) live in one place so both
  platforms stay consistent.
- The `ads_removed` entitlement is owned natively (from the store): pass the
  launch-time value to the `Game` constructor, then call `set_ads_removed`
  on purchase or restore.

## Store and AdMob setup (operational)

Product identifiers (same product on both stores):

- iOS (App Store Connect): non-consumable `mercato_remove_ads`, 3.99 EUR.
  Localized display name/description in EN/FR/ES.
- Android (Play Console): one-time product `mercato_remove_ads`, 3.99 EUR.

AdMob console (one app entry per platform, same AdMob account), four ad units
per platform:

| Ad unit | Format | Used on |
| --- | --- | --- |
| `mercato_banner` | Banner 320x50 | Home, Profile |
| `mercato_sponsor` | Banner (full width, fixed) | In game, below the card |
| `mercato_interstitial` | Interstitial | Before the recap |
| `mercato_rectangle` | Medium rectangle 300x250 | Recap |

During development always use Google's published test ad unit IDs, never the
production ones (invalid-traffic risk). Real IDs are injected per build
configuration (xcconfig on iOS, gradle property on Android), not hardcoded.

Status: both AdMob app entries and the four ad units per platform exist
(account ca-app-pub-5435447054359850). The IDs live in
`apps/ios/Config/AdMob-*.xcconfig` and `apps/android/config/admob-*.properties`
(debug = Google demo IDs, release = production IDs); see `apps/README.md`
for the wiring. The `mercato_remove_ads` product is still to be created in
App Store Connect and Play Console once the accounts are active.

Consent: Google UMP SDK on both platforms; on iOS also App Tracking
Transparency (UMP can chain the ATT prompt). Feed the outcome to the core via
`set_ad_consent`; when `ad_personalization_allowed()` is false, send
non-personalised requests (npa).

## Compliance checklist (pre-launch)

- [ ] AdMob consent (UMP) + iOS App Tracking Transparency prompt.
- [ ] Privacy policy covering ads/analytics data.
- [ ] App Store / Play data-safety & privacy nutrition labels.
- [ ] Restore-purchases flow.
- [ ] No ads shown to users who bought remove-ads (verified on fresh install +
      restore).
