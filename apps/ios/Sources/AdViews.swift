import GoogleMobileAds
import SwiftUI

/// A fixed-size AdMob banner bridged into SwiftUI. Whether it renders at all is
/// the caller's job, decided by the core gate (see the slot views below).
private struct AdBanner: UIViewRepresentable {
    let adUnitID: String
    let size: AdSize
    let personalizationAllowed: Bool

    func makeUIView(context: Context) -> BannerView {
        let view = BannerView(adSize: size)
        view.adUnitID = adUnitID
        view.rootViewController = UIApplication.shared.topViewController
        view.load(AdRequestFactory.make(personalizationAllowed: personalizationAllowed))
        return view
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}

/// The Home banner: 320x50, above the mode buttons. The core gate hides it once
/// ads are removed (docs/specs/ads.md, rule 4).
struct BannerSlot: View {
    let game: Game

    var body: some View {
        if game.shouldShowAd(placement: .banner) {
            AdBanner(
                adUnitID: AdConfig.banner,
                size: AdSizeBanner,
                personalizationAllowed: game.adPersonalizationAllowed()
            )
            .frame(width: 320, height: 50)
        }
    }
}

// The in-game sponsor board was removed for UX: no ad over a live question.

/// The recap medium rectangle: 300x250, placed below the recap buttons so it
/// never sits above the primary CTA. Hidden once ads are removed.
struct RectangleSlot: View {
    let game: Game

    var body: some View {
        if game.shouldShowAd(placement: .rectangle) {
            AdBanner(
                adUnitID: AdConfig.rectangle,
                size: AdSizeMediumRectangle,
                personalizationAllowed: game.adPersonalizationAllowed()
            )
            .frame(width: 300, height: 250)
        }
    }
}

extension UIApplication {
    /// The view controller an ad should attach to: the top of the active
    /// window's presentation stack.
    var topViewController: UIViewController? {
        let scene = connectedScenes
            .first { $0.activationState == .foregroundActive } as? UIWindowScene
        var top = scene?.windows.first(where: \.isKeyWindow)?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
