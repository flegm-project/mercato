# Release checklist

What stands between the current `main` and a build in review on both stores.
Written from an audit of the two app projects, the manifests, the R8 output
and the release artifacts already on disk, not from memory. Every item names
the file it lives in, so none of them needs rediscovering.

The order matters: the blockers are the things that make an upload bounce or a
review fail. Everything under "Should fix" ships, but ships worse.

## Blockers

### Shared

1. ~~**The privacy policy URL is still a 404.**~~ *Closed in the repo, pending
   merge and mailbox.* Both apps point at
   `https://flegm-project.github.io/mercato/privacy`, Pages serves `main` /
   `docs`, and `docs/privacy/index.html` is committed, regenerated from the
   three policies with the real contact address. It goes live the moment the
   branch `docs/privacy-firebase-contact` reaches `main`. Both stores require a
   reachable policy for an app with ads and a purchase, and the Settings row
   opens it. See publishing-identity.md.

2. ~~**No support URL and no contact address exist anywhere.**~~ *Address set,
   mailbox pending.* `docs/legal/contact.txt` now holds `mercato@flegm.fr`, the
   three policies and the generated page print it, and App Store Connect can
   use it for support. What remains, on Nico's side and outside the repo:
   create the mailbox at Hostinger and confirm it receives mail before
   submission, since a bouncing support address is itself a rejection.

3. **No store assets.** `store/` holds three listing texts and nothing else.
   Needed: iPhone 6.9" screenshots, Play phone screenshots, a 512x512 Play
   icon and a 1024x500 feature graphic. `scripts/capture-parity.sh` already
   drives every screen deterministically through the `-MercatoRoute` hooks, so
   the frames are a script away rather than a photo session.

4. **Neither in-app purchase exists in its console.** iOS expects
   `com.flegm.mercato.removeads` (`apps/ios/Sources/Store.swift:7`), Android expects
   `mercato_remove_ads` (`billing/BillingManager.kt:46`). Until each is created
   and active, both apps show the "shop unavailable" state while the listing
   promises the purchase, which is a review rejection on its own. Note the
   ordering on Play: a signed build has to reach an internal track before the
   product can be created. The three listings now name both ids.

### Android

5. **No release signing key, so `bundleRelease` emits an unsigned bundle.**
   `apps/android/app/build.gradle.kts:117` reads
   `signingConfigs.findByName("release")`, which is only created when
   `apps/android/keystore.properties` or `MERCATO_KEYSTORE` is present. Neither
   is. `findByName` returns null and AGP quietly ships an unsigned `.aab`
   rather than failing: the one on disk has no `META-INF` at all. Generate an
   upload keystore, keep it out of the repo, enrol in Play App Signing, and
   make the release build fail loudly when no config resolved.

5b. ~~**Firebase has to be created and its two config files dropped in.**~~
   *Done.* Project `mercato-fb6ba`, both apps registered under
   `com.flegm.mercato`, both config files in place and gitignored, Analytics
   and Crashlytics verified live on both platforms. The project runs on the
   no-cost Spark plan, event-level retention is set to 14 months rather than
   the 2 it defaults to, and both AdMob apps are linked to it, so ad revenue
   lands next to the usage numbers. See `docs/specs/analytics.md`.

   One thing to know, because it looks like a fault and is not: AdMob reports
   **"diffusion d'annonces limitée"** on both apps, because neither is attached
   to a store listing yet. That clears itself at publication. The AdMob app ids
   already match what the release builds carry, checked both ways
   (`~6149652518` Android, `~6829263654` iOS).

6. **Play Console declarations are all still open.** The merged release
   manifest pulls in `AD_ID`, the three `ACCESS_ADSERVICES_*` permissions,
   `WAKE_LOCK`, `FOREGROUND_SERVICE` and `BILLING` transitively from GMA and
   Billing. That means the advertising-ID declaration, the contains-ads
   declaration, Data Safety and the target-audience questionnaire all have to
   be filled in. `docs/MONETIZATION.md:50` still has them unchecked.

### iOS

7. **Nothing builds iOS in CI, and no Release build has ever been produced on
   this machine.** `.github/workflows/ci.yml` covers the Rust core, the
   generated assets, a Swift smoke test and `assembleDebug`. The four items
   below were fixed unverified by any archive. Add an `xcodegen` +
   `xcodebuild` job, and a `bundleRelease` step on the Android job.

   *Done:* the identifiers and the device family were all XcodeGen presets
   nobody had chosen. `TARGETED_DEVICE_FAMILY` was `"1,2"`, making this a
   universal app that declared portrait only, which is the `ITMS-90474`
   rejection; Release pinned `CODE_SIGN_IDENTITY = "iPhone Developer"`, so an
   archive resolved a development certificate; the bundle id was
   `com.mercato.Mercato` under a prefix nobody owns; and the purchase was
   `com.mercato.removeads` under the same one. They are now `"1"`, `Apple
   Distribution`, `com.flegm.mercato` and `com.flegm.mercato.removeads`,
   set on the target rather than the project, since a target setting is what
   beats the preset. `gen-ios-project.sh` also runs `gen-app-icon.mjs` now: it
   never did, and `build/` is gitignored, so a fresh clone could not generate
   a project at all.

## Should fix

- **JNA's x86_64 library is 4 KB aligned** where Play now wants 16 KB.
  Measured on the bundle: `libmercato_ffi.so` is fine on both ABIs,
  `libjnidispatch.so` (JNA 5.14.0, `build.gradle.kts:179`) is 64 KB aligned on
  arm64 and 4 KB on x86_64. Either bump JNA or drop `x86_64` from
  `abiFilters`, where it only serves emulators.
- **`allowBackup="true"` with no rules** (`AndroidManifest.xml:13`). The whole
  DataStore is backed up and device-transferred, including `ads_removed`, so a
  restore grants the entitlement on a new device until `restore()` corrects it.
  Add `dataExtractionRules` excluding it.
- **ATT is requested after the ad SDK starts** (`AdsManager.swift:47`). Google
  documents the reverse, and the SDK snapshots IDFA availability at init, so a
  player who allows tracking still spends the whole first session as if they
  had refused. The UMP ordering around it is correct.
- **`ITSAppUsesNonExemptEncryption` is missing**, so every upload stalls on the
  export-compliance question. The app has no crypto of its own; add it as
  `false`.
- **The SKAdNetwork list is a stale subset**: 46 identifiers against Google's
  current list of well over a hundred (`project.yml:112`). Not a rejection,
  just attribution revenue left on the floor. The file's own comment already
  says to regenerate it before release.
- **`PrivacyInfo.xcprivacy` declares tracking with an empty domain list.**
  The required-reason section is correct and complete; the tracking section
  contradicts itself. Either list the AdMob domains or say it does not track.
  The App Store Connect privacy labels are a separate exercise and are
  untouched.
- **No pinned SPM resolution.** `project.yml:29` takes GoogleMobileAds
  `from: 13.0.0` and the resolved file lives inside the gitignored project, so
  two machines can build two different SDKs. Pin it.
- **The Notifications switch still controls nothing** (`MenuScreens.kt:570`,
  `Screens.swift:543`). Kept on purpose, as the setting a daily reminder will
  read, but until that exists it is a visible control with no effect.
- **Dataset confidence.** `docs/ROADMAP.md` phase 7 asks for a second source
  per row and a `data/SOURCES.md`; neither has been done. 1905 transfers built
  by chaining Wikidata club spells will contain wrong years.

## Nice to have

- `android:localeConfig` so Android 13+ offers the per-app language picker,
  since the app already ships `values-fr` and `values-es`.
- `versionCode` is hand-edited (`build.gradle.kts:81`); drive it from CI before
  the second upload.
- `data/name-decisions.csv` ships inside the IPA: the resource rule excludes
  only `*.md`.
- `AdConfig.applicationID` and `AdConfig.sponsor` are dead constants; the SDK
  reads the app id from the plist and the sponsor slot was removed.
- The `ads/`, `billing/` and `ui/` Kotlin files all declare
  `package com.mercato.app` regardless of their directory.

## Confirmed already right

Worth writing down so nobody re-audits it: SDK levels are above Play's floor
(compile and target 36, min 26); R8 is on with shrinking, and its own mapping,
seeds and usage output prove the uniffi and JNA keep rules work; the AdMob ids
are split per build type on both platforms with no test id reaching release
and no real id reaching debug; every debug affordance is compiled out
(`BuildConfig.DEBUG` on Android, `#if DEBUG` on iOS); billing acknowledges
inside the three-day window and restores on launch, resume and from Settings;
the UMP flow derives personalisation from the TCF purpose bits and is unit
tested; `NSUserTrackingUsageDescription` is present and localised in all three
languages; and both platforms report version 1.0.0.
