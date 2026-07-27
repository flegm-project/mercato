import GoogleMobileAds
import SwiftUI

/// A fixed-size AdMob banner bridged into SwiftUI. Placement, sizing and the
/// remove-ads check are the caller's job (see the slot views below).
private struct AdBanner: UIViewRepresentable {
    let adUnitID: String
    let size: AdSize

    func makeUIView(context: Context) -> BannerView {
        let view = BannerView(adSize: size)
        view.adUnitID = adUnitID
        view.rootViewController = UIApplication.shared.topViewController
        view.load(AdRequestFactory.make())
        return view
    }

    func updateUIView(_ uiView: BannerView, context: Context) {}
}

/// The Home banner: 320x50, above the mode buttons. Hidden once ads are removed
/// (docs/specs/ads.md, rule 4).
struct BannerSlot: View {
    let adsRemoved: Bool
    var adUnitID: String = AdConfig.banner

    var body: some View {
        if !adsRemoved {
            AdBanner(adUnitID: adUnitID, size: AdSizeBanner)
                .frame(width: 320, height: 50)
        }
    }
}

/// The in-game sponsor board: the only slot allowed alongside a live question
/// (docs/specs/ads.md, rule 1). It fills the same footprint as the hatched
/// placeholder that stands in while the ad is loading, and collapses entirely
/// once ads are removed.
struct SponsorSlot: View {
    let adsRemoved: Bool

    var body: some View {
        if !adsRemoved {
            AdBanner(adUnitID: AdConfig.sponsor, size: AdSizeBanner)
                .frame(height: 50)
                .frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                .inkOutlined(RoundedRectangle(cornerRadius: 13, style: .continuous))
        }
    }
}

/// The recap medium rectangle: 300x250, standard display slot. Hidden once ads
/// are removed.
struct RectangleSlot: View {
    let adsRemoved: Bool

    var body: some View {
        if !adsRemoved {
            AdBanner(adUnitID: AdConfig.rectangle, size: AdSizeMediumRectangle)
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
