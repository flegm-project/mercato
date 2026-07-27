import AppTrackingTransparency
import GoogleMobileAds
import SwiftUI

/// Ad frequency policy, mirrored on the client until the shared Rust gate
/// (`should_show_ad`) is exposed through the FFI facade. The core stays the
/// single source of truth for this rule; this is a faithful copy so the iOS
/// app is not blocked on that binding landing. See docs/specs/ads.md.
///
/// TODO: replace this with the FFI gate once the core exposes it on main.
enum AdGate {
    /// One interstitial per round at most, and at least this many rounds
    /// between two of them (docs/specs/ads.md, rule 2).
    static let minRoundsBetweenInterstitials = 3
}

/// Builds ad requests. Personalisation follows the in-app consent choice: a
/// refused consent asks the network for non-personalised ads, not fewer slots
/// (docs/specs/ads.md, rule 3).
enum AdRequestFactory {
    static func make() -> Request {
        let request = Request()
        let personalised = UserDefaults.standard.bool(forKey: "adsPersonalised")
        if !personalised {
            let extras = Extras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        return request
    }
}

/// Owns SDK start-up, the interstitial lifecycle and the frequency gate.
///
/// The remove-ads entitlement is not read here: it lives in `Store` and is
/// passed in at each call site, so the entitlement stays the single source of
/// truth for whether any slot is allowed to fill.
@MainActor
final class AdsManager: NSObject, ObservableObject {
    private var interstitial: InterstitialAd?
    /// Rounds finished since the last interstitial. Starts at the gap so the
    /// first eligible round can show one without an artificial cold wait.
    private var roundsSinceInterstitial = AdGate.minRoundsBetweenInterstitials
    private var started = false
    /// Runs once the interstitial is dismissed, to hand control to the recap.
    private var pendingCompletion: (() -> Void)?

    /// Called once from the root view: starts the SDK, asks for tracking
    /// permission, then warms the first interstitial.
    func bootstrap() {
        guard !started else { return }
        started = true
        MobileAds.shared.start { [weak self] _ in
            Task { @MainActor in
                await self?.requestTracking()
                self?.loadInterstitial()
            }
        }
    }

    /// The ATT prompt governs the advertising identifier only. Whether ads are
    /// personalised is a separate, in-app choice (see `AdRequestFactory`).
    private func requestTracking() async {
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
    }

    private func loadInterstitial() {
        Task { @MainActor in
            interstitial = try? await InterstitialAd.load(
                with: AdConfig.interstitial,
                request: AdRequestFactory.make()
            )
            interstitial?.fullScreenContentDelegate = self
        }
    }

    /// Marks a finished round and, if the gate allows, shows one interstitial
    /// before running `then` (which reveals the recap). If nothing is eligible
    /// or loaded, `then` runs immediately.
    func onRoundFinished(adsRemoved: Bool, consentAnswered: Bool, then: @escaping () -> Void) {
        roundsSinceInterstitial += 1
        guard !adsRemoved,
              consentAnswered,
              roundsSinceInterstitial >= AdGate.minRoundsBetweenInterstitials,
              let ad = interstitial
        else {
            then()
            return
        }
        pendingCompletion = then
        roundsSinceInterstitial = 0
        interstitial = nil
        ad.present(from: nil)
        loadInterstitial() // warm the next one while this one is on screen
    }
}

extension AdsManager: FullScreenContentDelegate {
    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        pendingCompletion?()
        pendingCompletion = nil
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        pendingCompletion?()
        pendingCompletion = nil
    }
}
