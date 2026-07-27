import SwiftUI

@main
struct MercatoApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// Which screen is on. The set is deliberately small for now: Onboarding,
/// Settings and the rest follow in phase 5 (docs/specs/screens.md).
enum Route: Equatable {
    case splash
    case onboarding
    case consent
    case home
    case game(GameMode)
    case recap
    case settings
}

/// Loads the core once and routes between screens. Loading parses the bundled
/// CSVs and validates them, so a broken dataset surfaces here as an error
/// rather than as a broken round later.
struct RootView: View {
    @State private var loadResult: Result<Game, Error>?
    @State private var route: Route = .splash
    /// First launch shows the intro and the ad-consent choice once. Only these
    /// two flags persist; the score and streak deliberately do not
    /// (docs/GAME_DESIGN.md).
    @AppStorage("seenOnboarding") private var seenOnboarding = false
    @AppStorage("consentAnswered") private var consentAnswered = false
    /// Carried out of the last round so the recap can render it.
    @State private var summary: RoundSummary?
    /// Owns the remove-ads entitlement; every ad slot reads it.
    @StateObject private var store = Store()

    var body: some View {
        Group {
            switch loadResult {
            case .none:
                ZStack {
                    DS.appBackground
                    ProgressView().tint(DesignTokens.Color.ivory)
                }
                .task { loadResult = Self.loadGame() }

            case .success(let game):
                content(game)

            case .failure(let error):
                ZStack {
                    DS.appBackground
                    VStack(spacing: DesignTokens.Space.sm) {
                        Text("Could not load the dataset")
                            .font(DS.unbounded(22, weight: 900))
                        Text(String(describing: error))
                            .font(DS.figtree(13, weight: 600))
                            .multilineTextAlignment(.center)
                            .opacity(0.75)
                    }
                    .foregroundStyle(DesignTokens.Color.ivory)
                    .padding(DesignTokens.Space.gutter)
                }
            }
        }
    }

    @ViewBuilder
    private func content(_ game: Game) -> some View {
        switch route {
        case .splash:
            SplashView {
                route = seenOnboarding ? (consentAnswered ? .home : .consent) : .onboarding
            }

        case .onboarding:
            OnboardingView {
                seenOnboarding = true
                route = consentAnswered ? .home : .consent
            }

        case .consent:
            ConsentView { personalised in
                // Stored for the ad SDK to read in phase 6. Refusing means
                // non-personalised ads, not fewer of them.
                UserDefaults.standard.set(personalised, forKey: "adsPersonalised")
                consentAnswered = true
                route = .home
            }

        case .home:
            HomeView(
                onPlay: { mode in route = .game(mode) },
                onSettings: { route = .settings }
            )

        case .settings:
            SettingsView(
                onBack: { route = .home },
                onConsent: { route = .consent },
                onReplayIntro: { route = .onboarding },
                store: store
            )

        case .game(let mode):
            GameView(game: game, mode: mode) { finished in
                summary = finished
                route = .recap
            } onQuit: {
                route = .home
            }
            // A fresh identity per mode so a new round starts clean rather
            // than reusing the previous round's view state.
            .id(mode)

        case .recap:
            if let summary {
                RecapView(
                    score: summary.score,
                    correct: summary.correct,
                    total: summary.total,
                    onAgain: { route = .game(summary.mode) },
                    onHome: { route = .home }
                )
            } else {
                Color.clear.onAppear { route = .home }
            }
        }
    }

    private static func loadGame() -> Result<Game, Error> {
        // The CSVs are bundled as loose resources, so the bundle root is the
        // directory the core should read.
        let dir = Bundle.main.bundleURL.path
        do {
            return .success(try Game(dataDir: dir))
        } catch {
            return .failure(error)
        }
    }
}

/// What a finished round hands to the recap.
struct RoundSummary {
    let mode: GameMode
    let score: ScoreView
    let correct: Int
    let total: Int
}
