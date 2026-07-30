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
    case profile
    case game(GameMode)
    case recap
    case settings
    case offline
    case lab
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
    /// Starts the ad SDK and owns the interstitial shown at game -> recap.
    @StateObject private var ads = AdsManager()
    /// Lifetime stats shown on Profile; updated at the end of every round.
    @StateObject private var stats = AppStats()
    /// Google UMP. Where GDPR applies its form is the legal consent surface;
    /// elsewhere the app's own screen stays the choice.
    @StateObject private var consent = ConsentManager()

    var body: some View {
        Group {
            switch loadResult {
            case .none:
                ZStack {
                    DS.appBackground
                    ProgressView().tint(DesignTokens.Color.ivory)
                }
                .task {
                    loadResult = Self.loadGame()
                    #if DEBUG
                    // Apply the QA route before the splash can route away.
                    applyDebugRoute()
                    #endif
                }

            case .success(let game):
                content(game)
                    // One hook for every screen rather than a call in each
                    // view: a screen added later is counted without anyone
                    // remembering to count it, and the names come from the
                    // shared vocabulary rather than from the Route enum.
                    .onChange(of: route) { _, new in
                        if let name = Self.screenName(new) {
                            Analytics.shared.log(.screenOpened, [.screen: name])
                        }
                    }
                    .onAppear {
                        // The gate is app-lifetime state that resets with each
                        // fresh Game, so seed it on load: the store entitlement
                        // and the stored consent choice.
                        game.setAdsRemoved(removed: store.adsRemoved)
                        if consentAnswered {
                            let personalised = UserDefaults.standard.bool(forKey: "adsPersonalised")
                            game.setAdConsent(consent: personalised ? .personalized : .nonPersonalized)
                        }
                        #if DEBUG
                        // QA affordance: jump straight to a screen for
                        // screenshots (SIMCTL_CHILD_MERCATO_ROUTE=...), and skip
                        // the ad SDK start so the ATT prompt and ad loads never
                        // interfere with deterministic captures.
                        if debugRouteArg() != nil {
                            return
                        }
                        #endif
                        // Only once the player has answered the app's own ad
                        // consent screen: the system tracking prompt must not
                        // land on top of onboarding.
                        if consentAnswered {
                            Task {
                                // UMP first: inside the EEA its form is the
                                // legal surface and it overrides the stored
                                // answer. Outside, it returns without asking.
                                await consent.gather(game: game)
                                ads.bootstrap(game: game)
                            }
                        }
                    }
                    .onChange(of: store.adsRemoved) { _, removed in
                        game.setAdsRemoved(removed: removed)
                    }

            case .failure(let error):
                ZStack {
                    DS.appBackground
                    VStack(spacing: DesignTokens.Space.sm) {
                        Text("Could not load the dataset")
                            .typeStyle(TypeToken.scorePill)
                        Text(String(describing: error))
                            .typeStyle(TypeToken.labNote)
                            .multilineTextAlignment(.center)
                            .opacity(DesignTokens.Opacity.textQuiet)
                    }
                    .foregroundStyle(DesignTokens.Color.ivory)
                    .padding(DesignTokens.Space.gutter)
                }
            }
        }
    }

    /// The screen name the shared vocabulary uses, or nil for the screens not
    /// worth counting. Splash and consent are unavoidable, the game has its
    /// own round events, and the lab is debug only.
    private static func screenName(_ route: Route) -> String? {
        switch route {
        case .home: return "home"
        case .profile: return "profile"
        case .settings: return "settings"
        case .recap: return "recap"
        case .offline: return "offline"
        case .onboarding: return "onboarding"
        case .splash, .consent, .game, .lab: return nil
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
                // Refusing means non-personalised ads, not fewer of them. The
                // choice is reported to the core gate and mirrored to
                // UserDefaults so Settings can show its current state.
                UserDefaults.standard.set(personalised, forKey: "adsPersonalised")
                game.setAdConsent(consent: personalised ? .personalized : .nonPersonalized)
                consentAnswered = true
                route = .home
                // The player has just been told the app is ad-funded, which is
                // the moment to run UMP where it applies and then ask for
                // tracking.
                Task {
                    await consent.gather(game: game)
                    ads.bootstrap(game: game)
                }
            }

        case .home:
            HomeView(
                onPlay: { mode in route = .game(mode) },
                onProfile: { route = .profile },
                game: game
            )

        case .profile:
            ProfileView(
                stats: stats,
                game: game,
                onPlayTab: { route = .home },
                onSettings: { route = .settings }
            )

        case .settings:
            SettingsView(
                onBack: { route = .profile },
                onConsent: {
                    // Inside GDPR scope the UMP privacy options form is the
                    // legal surface; elsewhere the app's own screen handles it.
                    if consent.privacyOptionsRequired {
                        Task { await consent.showPrivacyOptions(game: game) }
                    } else {
                        route = .consent
                    }
                },
                onReplayIntro: { route = .onboarding },
                onOffline: { route = .offline },
                onLab: { route = .lab },
                game: game,
                store: store
            )

        case .offline:
            OfflineView(onRetry: { route = .settings })

        case .lab:
            // Dev-only matching lab; compiled out of release, where the row
            // that reaches it does not exist either.
            #if DEBUG
            LabView(game: game, onBack: { route = .settings })
            #else
            Color.clear.onAppear { route = .settings }
            #endif

        case .game(let mode):
            GameView(game: game, mode: mode) { finished in
                summary = finished
                // Fold the round into the lifetime stats before anything else,
                // so a shown interstitial cannot swallow the update.
                stats.recordRound(
                    score: Int(finished.score.points),
                    bestStreak: Int(finished.score.bestStreak),
                    correct: finished.correct,
                    answered: finished.total
                )
                // One interstitial may play between the last question and the
                // recap, if the core gate allows it (docs/specs/ads.md).
                ads.onRoundFinished { route = .recap }
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
                    game: game,
                    onAgain: { route = .game(summary.mode) },
                    onHome: { route = .home }
                )
            } else {
                Color.clear.onAppear { route = .home }
            }
        }
    }

    #if DEBUG
    /// The QA screen to jump to, from a launch argument (-MercatoRoute <name>).
    private func debugRouteArg() -> String? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "-MercatoRoute"), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    /// Jump straight to a screen for screenshots, never compiled into release.
    private func applyDebugRoute() {
        guard let r = debugRouteArg() else { return }
        seenOnboarding = true
        consentAnswered = true
        switch r {
        case "home": route = .home
        case "profile": route = .profile
        case "settings": route = .settings
        case "offline": route = .offline
        case "lab": route = .lab
        case "onboarding": seenOnboarding = false; route = .onboarding
        case "consent": route = .consent
        case "easy": route = .game(.easy)
        case "hardcore": route = .game(.hardcore)
        case "recap":
            summary = RoundSummary(
                mode: .easy,
                score: ScoreView(points: 21, streak: 3, bestStreak: 5, lastCorrect: true),
                correct: 7,
                total: 10
            )
            route = .recap
        case "recaplose":
            summary = RoundSummary(
                mode: .hardcore,
                score: ScoreView(points: 6, streak: 1, bestStreak: 1, lastCorrect: false),
                correct: 2,
                total: 10
            )
            route = .recap
        default: break
        }
    }
    #endif

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
