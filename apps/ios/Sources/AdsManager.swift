import AppTrackingTransparency
import GoogleMobileAds
import SwiftUI

/// Builds ad requests. Personalisation follows the shared gate: a refused
/// consent asks the network for non-personalised ads, not fewer slots
/// (docs/specs/ads.md, rule 3).
enum AdRequestFactory {
    static func make(personalizationAllowed: Bool) -> Request {
        let request = Request()
        if !personalizationAllowed {
            let extras = Extras()
            extras.additionalParameters = ["npa": "1"]
            request.register(extras)
        }
        return request
    }
}

/// Owns SDK start-up, the interstitial lifecycle and ATT.
///
/// The decision to show any ad, and the interstitial frequency cap, live in
/// the Rust core behind the `Game` facade (`should_show_ad`); this type only
/// drives the SDK and defers every "should it show?" question to the core, so
/// iOS and Android cannot drift.
@MainActor
final class AdsManager: NSObject, ObservableObject {
    private var interstitial: InterstitialAd?
    /// The core gate. Set at bootstrap; every ad decision is asked of it.
    private var game: Game?
    private var started = false
    /// Runs once the interstitial is dismissed, to hand control to the recap.
    private var pendingCompletion: (() -> Void)?

    /// Called once the game has loaded: starts the SDK, asks for tracking
    /// permission, then warms the first interstitial.
    func bootstrap(game: Game) {
        self.game = game
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
    /// personalised is the separate consent choice reported to the core.
    private func requestTracking() async {
        if ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            _ = await ATTrackingManager.requestTrackingAuthorization()
        }
    }

    private func loadInterstitial() {
        let personalised = game?.adPersonalizationAllowed() ?? false
        Task { @MainActor in
            interstitial = try? await InterstitialAd.load(
                with: AdConfig.interstitial,
                request: AdRequestFactory.make(personalizationAllowed: personalised)
            )
            interstitial?.fullScreenContentDelegate = self
        }
    }

    /// Shows one interstitial before running `then` (which reveals the recap),
    /// but only if the core gate allows it and one is loaded. Otherwise `then`
    /// runs immediately.
    func onRoundFinished(then: @escaping () -> Void) {
        guard let game,
              game.shouldShowAd(placement: .interstitial),
              let ad = interstitial
        else {
            then()
            return
        }
        pendingCompletion = then
        interstitial = nil
        ad.present(from: nil)
        // Record now that it is on screen, so the core's cap counts from here.
        game.recordInterstitialShown()
        loadInterstitial() // warm the next one
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
