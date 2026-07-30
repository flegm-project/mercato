# Release checklist

What stands between the current `main` and a build in review on both stores.
Written from an audit of the two app projects, the manifests, the R8 output
and the release artifacts already on disk, not from memory. Every item names
the file it lives in, so none of them needs rediscovering.

The order matters: the blockers are the things that make an upload bounce or a
review fail. Everything under "Should fix" ships, but ships worse.

## Blockers

### Shared

1. **The privacy policy URL is a 404.** `apps/ios/Sources/AppLinks.swift:8`
   and `apps/android/.../AppLinks.kt:8` both point at
   `https://nicogaray7.github.io/mercato/privacy`. GitHub Pages is not enabled
   on the repository at all, so that host answers 404 for every path. The page
   itself exists (`docs/privacy/index.html`, written by
   `scripts/gen-privacy-site.mjs`) and is simply never published. Both stores
   require a reachable policy for an app with ads and a purchase, and the
   Settings row currently opens a dead page. Enable Pages on `main` / `docs`,
   or host it elsewhere and change the two constants.

2. **No support URL and no contact address exist anywhere.** App Store Connect
   requires one. Decide what it is (a mail address is enough) and put it next
   to the privacy policy so both are published together.

3. **No store assets.** `store/` holds three listing texts and nothing else.
   Needed: iPhone 6.9" screenshots, Play phone screenshots, a 512x512 Play
   icon and a 1024x500 feature graphic. `scripts/capture-parity.sh` already
   drives every screen deterministically through the `-MercatoRoute` hooks, so
   the frames are a script away rather than a photo session.

4. **Neither in-app purchase exists in its console.** iOS expects
   `com.mercato.removeads` (`apps/ios/Sources/Store.swift:7`), Android expects
   `mercato_remove_ads` (`billing/BillingManager.kt:46`). Until each is created
   and active, both apps show the "shop unavailable" state while the listing
   promises the purchase, which is a review rejection on its own. Note the
   ordering on Play: a signed build has to reach an internal track before the
   product can be created. Also `store/listing.en.md:57` gives the reviewer the
   Android id for the iOS build; fix all three listings.

### Android

5. **No release signing key, so `bundleRelease` emits an unsigned bundle.**
   `apps/android/app/build.gradle.kts:117` reads
   `signingConfigs.findByName("release")`, which is only created when
   `apps/android/keystore.properties` or `MERCATO_KEYSTORE` is present. Neither
   is. `findByName` returns null and AGP quietly ships an unsigned `.aab`
   rather than failing: the one on disk has no `META-INF` at all. Generate an
   upload keystore, keep it out of the repo, enrol in Play App Signing, and
   make the release build fail loudly when no config resolved.

6. **Play Console declarations are all still open.** The merged release
   manifest pulls in `AD_ID`, the three `ACCESS_ADSERVICES_*` permissions,
   `WAKE_LOCK`, `FOREGROUND_SERVICE` and `BILLING` transitively from GMA and
   Billing. That means the advertising-ID declaration, the contains-ads
   declaration, Data Safety and the target-audience questionnaire all have to
   be filled in. `docs/MONETIZATION.md:50` still has them unchecked.

### iOS

7. **The app is built for iPhone *and* iPad but only declares portrait.**
   `TARGETED_DEVICE_FAMILY = "1,2"` is an XcodeGen platform default, not a
   decision: it appears in the generated `project.pbxproj` and nowhere in
   `project.yml`. Combined with the portrait-only
   `UISupportedInterfaceOrientations` (`apps/ios/project.yml:163`) that is the
   `ITMS-90474` upload rejection, and if it did pass, App Review would test an
   iPhone-tuned layout on an iPad and ask for 13" screenshots. Set
   `TARGETED_DEVICE_FAMILY: "1"` in `project.yml` and regenerate.

8. **The Release configuration pins a development signing identity.**
   `CODE_SIGN_IDENTITY = "iPhone Developer"` in both configurations, again a
   generator default. An archive will resolve a development certificate and
   then fight `-exportArchive` with `method: app-store`. Override
   `CODE_SIGN_IDENTITY` for Release in `project.yml`.

9. **`scripts/gen-app-icon.mjs` is called by nothing on the iOS side.** The
   Android build runs it as a Gradle task (`build.gradle.kts:54`), but
   `gen-ios-project.sh` does not, and `build/` is gitignored. The project
   references `build/icons/ios/Assets.xcassets`, so a fresh clone cannot even
   generate the project, let alone archive with an icon. It works here only
   because the file happens to be on this machine. Add the call to
   `gen-ios-project.sh`, next to the sounds and the strings.

10. **The bundle identifier is an accident.** `com.mercato.Mercato`, derived
    from `bundleIdPrefix` plus the target name, capital M and all, while
    Android is `com.nicogaray.mercato`. `com.mercato` is a prefix nobody here
    owns. It is permanent once the App Store Connect record exists, so decide
    it now and set `PRODUCT_BUNDLE_IDENTIFIER` explicitly.

11. **Nothing builds iOS in CI, and no Release build has ever been produced on
    this machine.** `.github/workflows/ci.yml` covers the Rust core, the
    generated assets, a Swift smoke test and `assembleDebug`. Every item above
    is therefore unverified by any build. Add an `xcodegen` + `xcodebuild`
    job, and a `bundleRelease` step on the Android job.

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
