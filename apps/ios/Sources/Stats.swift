import SwiftUI

/// Lifetime stats shown on the Profile screen. The session score stays in the
/// Rust core and resets every round (docs/GAME_DESIGN.md); only these
/// cross-session aggregates persist, mirroring the Android `Prefs` store so the
/// two platforms report the same four numbers.
@MainActor
final class AppStats: ObservableObject {
    @Published private(set) var roundsPlayed: Int
    @Published private(set) var bestScore: Int
    @Published private(set) var bestStreak: Int
    @Published private(set) var correct: Int
    @Published private(set) var answered: Int

    private enum Key {
        static let roundsPlayed = "stats.roundsPlayed"
        static let bestScore = "stats.bestScore"
        static let bestStreak = "stats.bestStreak"
        static let correct = "stats.answersCorrect"
        static let answered = "stats.answersTotal"
    }

    init() {
        let store = UserDefaults.standard
        roundsPlayed = store.integer(forKey: Key.roundsPlayed)
        bestScore = store.integer(forKey: Key.bestScore)
        bestStreak = store.integer(forKey: Key.bestStreak)
        correct = store.integer(forKey: Key.correct)
        answered = store.integer(forKey: Key.answered)
    }

    /// Accuracy as a whole-percent string, or "-" before the first answer.
    var accuracy: String {
        answered == 0 ? "-" : "\(correct * 100 / answered)%"
    }

    /// Fold one finished round into the lifetime aggregates.
    func recordRound(score: Int, bestStreak roundBestStreak: Int, correct roundCorrect: Int, answered roundAnswered: Int) {
        roundsPlayed += 1
        bestScore = max(bestScore, score)
        bestStreak = max(bestStreak, roundBestStreak)
        correct += roundCorrect
        answered += roundAnswered

        let store = UserDefaults.standard
        store.set(roundsPlayed, forKey: Key.roundsPlayed)
        store.set(bestScore, forKey: Key.bestScore)
        store.set(bestStreak, forKey: Key.bestStreak)
        store.set(correct, forKey: Key.correct)
        store.set(answered, forKey: Key.answered)
    }
}
