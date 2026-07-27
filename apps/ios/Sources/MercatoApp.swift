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
                ProgressView().task { loadResult = Self.loadGame() }
            case .success(let game):
                GameView(game: game)
            case .failure(let error):
                VStack(spacing: 12) {
                    Text("Could not load the dataset")
                        .font(.headline)
                    Text(String(describing: error))
                        .font(.footnote)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(.secondary)
                }
                .padding()
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
