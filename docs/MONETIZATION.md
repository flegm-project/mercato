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

- The **decision** "should ads be shown right now?" is a small, testable rule
  (ads not removed AND placement allowed AND frequency cap satisfied). Keep the
  rule parameters in one place; the SDK calls stay native.
- The `ads_removed` entitlement is owned natively (from the store) and passed to
  the core if any core logic needs to branch on it.

## Compliance checklist (pre-launch)

- [ ] AdMob consent (UMP) + iOS App Tracking Transparency prompt.
- [ ] Privacy policy covering ads/analytics data.
- [ ] App Store / Play data-safety & privacy nutrition labels.
- [ ] Restore-purchases flow.
- [ ] No ads shown to users who bought remove-ads (verified on fresh install +
      restore).
