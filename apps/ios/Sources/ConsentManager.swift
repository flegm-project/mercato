import GoogleMobileAds
import SwiftUI
import UserMessagingPlatform

/// Google UMP consent flow, mapped onto the shared core contract
/// (`Game.setAdConsent`; the core is the single source for
/// `adPersonalizationAllowed`, the same contract as Android).
///
/// Where GDPR applies, the UMP form is the legal consent surface: Google has
/// required a certified CMP to serve personalised ads in the EEA and the UK
/// since January 2024, and without one AdMob restricts the account to limited
/// ads there. It shows at launch, writes the TCF string, and the app derives
/// personalised or non-personalised from the purpose bits below. Where UMP is
/// not required, the app's own consent screen stays the choice surface, since
/// refusing personalisation must remain possible everywhere. The SDK reads the
/// TCF string itself; the npa flag the core hands us is the conservative
/// overlay Google documents, where the most restrictive answer wins.
@MainActor
final class ConsentManager: ObservableObject {
    /// True once UMP itself collected consent, so onboarding can skip the
    /// app's own consent screen rather than asking twice.
    @Published private(set) var handledByUmp = false
    /// Whether Settings must expose the UMP privacy options form.
    @Published private(set) var privacyOptionsRequired = false

    /// Refresh consent info, show the form when required, then push the
    /// outcome into the core. Safe to call at every launch: outside a GDPR
    /// scope it is a silent no-op.
    func gather(game: Game) async {
        let parameters = RequestParameters()
        parameters.isTaggedForUnderAgeOfConsent = false

        do {
            try await ConsentInformation.shared.requestConsentInfoUpdate(with: parameters)
        } catch {
            // Offline or misconfigured: keep the last known consent rather
            // than downgrading someone who already answered.
            return
        }

        if let root = Self.rootViewController {
            try? await ConsentForm.loadAndPresentIfRequired(from: root)
        }
        apply(game: game)
    }

    /// Reopen the UMP form from Settings, then re-derive the outcome.
    func showPrivacyOptions(game: Game) async {
        guard let root = Self.rootViewController else { return }
        try? await ConsentForm.presentPrivacyOptionsForm(from: root)
        apply(game: game)
    }

    private func apply(game: Game) {
        let info = ConsentInformation.shared
        privacyOptionsRequired = info.privacyOptionsRequirementStatus == .required

        switch info.consentStatus {
        case .notRequired:
            // No GDPR framework applies: the app's own consent screen, or the
            // answer it already stored, stays authoritative. Change nothing.
            handledByUmp = false
            return
        case .obtained:
            handledByUmp = true
            let personalised = Self.tcfAllowsPersonalizedAds()
            game.setAdConsent(consent: personalised ? .personalized : .nonPersonalized)
            UserDefaults.standard.set(personalised, forKey: "adsPersonalised")
            // The same decision drives measurement. The app has exactly one
            // consent surface and it stays that way: a second dialog asking
            // about analytics would be worse for the player and would
            // contradict what the first one promised.
            Analytics.shared.setConsent(personalised: personalised)
        default:
            // Required or unknown without a completed form: most conservative.
            game.setAdConsent(consent: .nonPersonalized)
            UserDefaults.standard.set(false, forKey: "adsPersonalised")
            Analytics.shared.setConsent(personalised: false)
        }
    }

    private static var rootViewController: UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
    }

    /// Google's documented TCF requirements for personalised ads
    /// (support.google.com/admob/answer/9760862): consent on purposes 1, 3 and
    /// 4, and consent or legitimate interest on 2, 7, 9 and 10. Purpose N sits
    /// at zero-based index N-1 of the bit strings UMP writes to UserDefaults.
    static func tcfAllowsPersonalizedAds(
        consents: String? = UserDefaults.standard.string(forKey: "IABTCF_PurposeConsents"),
        legitimate: String? = UserDefaults.standard.string(forKey: "IABTCF_PurposeLegitimateInterests")
    ) -> Bool {
        let consents = consents ?? ""
        let legitimate = legitimate ?? ""

        func granted(_ bits: String, _ purpose: Int) -> Bool {
            guard bits.count >= purpose else { return false }
            return Array(bits)[purpose - 1] == "1"
        }
        func grantedEither(_ purpose: Int) -> Bool {
            granted(consents, purpose) || granted(legitimate, purpose)
        }

        return granted(consents, 1) && granted(consents, 3) && granted(consents, 4)
            && grantedEither(2) && grantedEither(7) && grantedEither(9) && grantedEither(10)
    }
}
