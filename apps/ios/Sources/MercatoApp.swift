import SwiftUI

@main
struct MercatoApp: App {
    var body: some Scene {
        WindowGroup {
            RootView()
        }
    }
}

/// Loads the core once and hands it to the game screen. Loading parses the
/// bundled CSVs and validates them, so a broken dataset surfaces here as an
/// error rather than as a broken round later.
struct RootView: View {
    @State private var loadResult: Result<Game, Error>?

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
                GameView(game: game)
            case .failure(let error):
                ZStack {
                    DS.appBackground
                    VStack(spacing: DesignTokens.Space.sm) {
                        Text("Could not load the dataset")
                            .font(.system(size: 22, weight: .black))
                        Text(String(describing: error))
                            .font(.system(size: 13))
                            .multilineTextAlignment(.center)
                            .opacity(0.75)
                    }
                    .foregroundStyle(DesignTokens.Color.ivory)
                    .padding(DesignTokens.Space.gutter)
                }
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
