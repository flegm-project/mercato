# Analytics

Firebase Analytics and Crashlytics, on both platforms, gated by the consent
the app already asks for. Ten events, and no free-text event name anywhere in
either app.

## The vocabulary is generated, not written twice

`design/analytics.json` is the source. `scripts/gen-analytics.mjs` emits
`build/analytics/AnalyticsEvents.{swift,kt}` and both apps compile the result,
exactly as they do for the design tokens and the localized strings.

This is not ceremony. Two apps that log `round_end` and `roundEnd` produce two
datasets that look like one, and nobody notices until a quarter of the history
has to be thrown away. The generator also refuses names Google reserves, names
that are not lower_snake_case, and events with no `why` field, because an event
nobody can justify is an event nobody will ever read.

Adding an event is one entry in the JSON and a call on each side. Renaming one
costs the history behind it, so the names were chosen to survive.

## Why Firebase

Analytics and Crashlytics are both no-cost on the Spark plan with no usage
limits. The parts of Firebase that cost money are the backend, and this app has
none. Crashlytics matters more than the counting does: the app runs on a range
of phones nobody here owns, and a crash that only happens on some of them is
otherwise invisible.

The other reason is AdMob. Linking the two gives ad revenue per cohort, which
turns "people play a lot" into "this is what it earns", and nothing else free
does that.

Google's structural limits, for reference: 500 distinct event names per app, 25
parameters per event, 25 user properties, names capped at 40 characters and
parameter values at 100. Ten events is not close to any of them.

## Consent

The app has exactly one consent surface, the UMP form, and it stays that way. A
second dialog asking about measurement would be worse for the player and would
contradict what the first one promised, so the same decision drives both.

A refusal denies `ad_storage`, `ad_user_data` and `ad_personalization`.
`analytics_storage` stays granted either way: counting rounds is what the
privacy policy describes, it carries no advertising identifier, and denying it
would leave the app with no idea whether it works at all.

Both platforms push this from the same place they push the ad consent into the
core, so the two can never disagree.

## Where the events fire

| Event | Android | iOS |
|---|---|---|
| `round_start` | `GameViewModel.startRound` | `GameView.startRound` |
| `round_end` | `GameViewModel.finishRound` | `GameView.advance`, at round over |
| `round_quit` | `GameViewModel.quitRound` | the quit dialog's confirm |
| `hint_taken` | `GameViewModel.requestHint` | `GameView.takeHint` |
| `screen_opened` | one `LaunchedEffect` on the nav back stack | one `onChange(of: route)` |
| `purchase_started` | after `launchBillingFlow` | before `product.purchase()` |
| `purchase_done` | `onPurchasesUpdated`, first grant only | after a verified transaction |
| `purchase_restored` | `restore(explicit = true)` only | `Store.restore`, when it found something |
| `sound_set` | the Settings toggle | the Settings toggle |

`screen_opened` is one hook per platform rather than a call in each screen, so
a screen added later is counted without anyone remembering to count it.
`purchase_started` fires when the sheet actually opens, not on the tap: a store
that never answered would otherwise flatter the funnel with people who saw
nothing.

## Running without credentials

`google-services.json` and `GoogleService-Info.plist` are per-account and are
not in the repo. Both apps build and run without them:

- Android applies the `google-services` and `crashlytics` plugins only when the
  JSON is present, because that plugin fails the build outright when it is not.
  Gradle prints a line saying so.
- iOS checks for the plist before calling `FirebaseApp.configure()`, which
  traps rather than returning an error when the file is missing.

In both cases the `Analytics` type becomes a no-op. Nothing else in either app
knows or cares.

## What is not measured, and why

- **Nothing per question.** The one thing worth knowing per question is whether
  the dataset's own 1/2/3 difficulty tier matches how people actually do, and
  `QuestionView` does not carry the tier across the FFI. Without it the event
  would be ten times the volume of `round_end` for something `round_end`
  already answers. Add the field to the core first.
- **No user identifiers.** No user id is ever set, and no parameter carries
  anything a player typed. The guess field in Hardcore is never logged.
- **No screen views beyond the six named ones.** Firebase collects its own
  automatic screen views; `screen_opened` exists so the screens that matter are
  countable without reading an automatic report.

## What is set up

Project `mercato-fb6ba`, both apps registered as `com.flegm.mercato`, on the
no-cost **Spark** plan. Blaze was attached at creation by a stray URL
parameter and was taken off again: nothing here needs a metered product, and
Spark makes an accidental bill structurally impossible rather than merely
unlikely.

Event-level retention is **14 months**, not the 2 it defaults to. That setting
only governs Explorations; the standard reports are kept regardless. Two months
of raw events is not enough to compare a season to the one before it.

Both AdMob apps are **linked to the project**, which is what puts ad revenue
next to the usage numbers instead of in a separate console. The package name
that link is bound to cannot be changed afterwards, which is the reason the
identifiers were settled first.

## Before release

Adding Firebase changes what has to be declared. Play's Data Safety form and
Apple's privacy labels both need Analytics and Crashlytics added, and the
privacy policy needs a paragraph on measurement. `docs/specs/release-checklist.md`
tracks that alongside the rest.
