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
    case home
    case game(GameMode)
    case recap
}

/// Loads the core once and routes between screens. Loading parses the bundled
/// CSVs and validates them, so a broken dataset surfaces here as an error
/// rather than as a broken round later.
struct RootView: View {
    @State private var loadResult: Result<Game, Error>?
    @State private var route: Route = .splash
    /// Carried out of the last round so the recap can render it.
    @State private var summary: RoundSummary?

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
            SplashView { route = .home }

        case .home:
            HomeView { mode in route = .game(mode) }

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
