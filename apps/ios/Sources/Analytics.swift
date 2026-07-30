import Foundation
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics

/// Usage measurement and crash reporting, behind one small surface.
///
/// Every event name and parameter name comes from `AnalyticsEvents.swift`,
/// generated from `design/analytics.json` for both platforms at once. Nothing
/// here spells a string, which is what stops this app and the Android one from
/// drifting into two datasets that look like one.
///
/// The whole thing is a no-op when Firebase has no credentials.
/// `GoogleService-Info.plist` is per-account and is not in the repo, so a
/// clone has to build and run without it. `FirebaseApp.configure()` traps on a
/// missing plist rather than returning an error, so the file's presence is
/// checked first.
final class Analytics {
    static let shared = Analytics()

    private let ready: Bool

    private init() {
        if Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil {
            FirebaseApp.configure()
            ready = true
        } else {
            ready = false
        }
    }

    /// True when a Firebase project is configured.
    var enabled: Bool { ready }

    /// Mirror the ad consent the UMP flow already resolved onto Firebase's
    /// consent signals.
    ///
    /// The app has exactly one consent surface and it must stay that way: a
    /// second dialog asking about measurement would be both worse for the
    /// player and inconsistent with what the first one promised. So the same
    /// decision drives both. A refusal means non-personalised ads *and*
    /// measurement without ad identifiers.
    ///
    /// `analyticsStorage` stays granted either way: counting rounds is what
    /// the privacy policy describes, it carries no advertising identifier, and
    /// denying it would leave the app with no idea whether it works at all.
    func setConsent(personalised: Bool) {
        guard ready else { return }
        let ads: ConsentStatus = personalised ? .granted : .denied
        FirebaseAnalytics.Analytics.setConsent([
            .analyticsStorage: .granted,
            .adStorage: ads,
            .adUserData: ads,
            .adPersonalization: ads,
        ])
    }

    /// Log `event`. Parameters are keyed by the generated `Param` enum, so a
    /// call site cannot invent one.
    ///
    /// Values are Int or String only. Analytics stores numbers as doubles and
    /// strings verbatim; a boolean would arrive as "true"/"false" on one
    /// platform and 1/0 on the other, so booleans are written as 1 and 0 at
    /// the call site and the spec says so.
    func log(_ event: Event, _ params: [Param: Any] = [:]) {
        guard ready else { return }
        var out: [String: Any] = [:]
        for (key, value) in params {
            switch value {
            case let n as Int: out[key.rawValue] = n
            case let b as Bool: out[key.rawValue] = b ? 1 : 0
            default: out[key.rawValue] = String(describing: value).prefix(100).description
            }
        }
        FirebaseAnalytics.Analytics.logEvent(event.rawValue, parameters: out)
    }

    /// Record something that went wrong but did not crash. The app swallows a
    /// number of failures on purpose (`try?` around every FFI call), and those
    /// are exactly the ones nobody ever hears about otherwise.
    func recordError(_ where_: String, _ error: Error) {
        guard ready else { return }
        Crashlytics.crashlytics().log(where_)
        Crashlytics.crashlytics().record(error: error)
    }
}
