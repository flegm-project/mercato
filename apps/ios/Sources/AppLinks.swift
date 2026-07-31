import Foundation

/// Links and identifiers that must match what is filed with the stores.
enum AppLinks {
    /// Both stores require a reachable privacy policy for an app that shows
    /// ads and sells an in-app purchase. The same document is published for
    /// Android; the page picks its own language.
    static let privacyPolicy = "https://flegm.github.io/mercato/privacy"

    /// The shipped version, read from the bundle so the settings footer cannot
    /// drift from what was actually submitted.
    static var shortVersion: String {
        Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? ""
    }
}
