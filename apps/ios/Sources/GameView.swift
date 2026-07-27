import SwiftUI

/// Minimal playable screen: enough to prove the Rust core drives a real round
/// on device. The full screen set (Splash, Onboarding, Home, Recap and the
/// rest) is Phase 5 and follows docs/specs/screens.md; this deliberately does
/// not try to be that.
struct GameView: View {
    let game: Game

    @State private var question: QuestionView?
    @State private var answer: AnswerView?
    @State private var score: ScoreView?
    @State private var roundOver = false

    var body: some View {
        VStack(spacing: 24) {
            header

            if let q = question {
                transferCard(q)
                options(q)
            } else if roundOver {
                recap
            }

            Spacer()
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(DesignTokens.Color.blue)
        .foregroundStyle(DesignTokens.Color.ivory)
        .onAppear(perform: startRound)
    }

    // MARK: - Pieces

    private var header: some View {
        HStack {
            if let q = question {
                Text("\(q.index) / \(q.total)")
                    .font(.subheadline.monospacedDigit())
            }
            Spacer()
            Text("\(score?.points ?? 0) pts")
                .font(.title3.bold().monospacedDigit())
                .foregroundStyle(scoreColor)
        }
    }

    private var scoreColor: Color {
        switch score?.lastCorrect {
        case .some(true): return DesignTokens.Color.green
        case .some(false): return DesignTokens.Color.coral
        case .none: return DesignTokens.Color.ivory
        }
    }

    private func transferCard(_ q: QuestionView) -> some View {
        VStack(spacing: 8) {
            HStack {
                Text(kindLabel(q.kind))
                    .font(.caption.bold())
                Spacer()
                Text(String(q.year))
                    .font(.title.bold().monospacedDigit())
            }
            .padding(.bottom, 4)

            Text(q.fromClub)
                .font(.subheadline)
                .foregroundStyle(DesignTokens.Color.clubGrey)
            Image(systemName: "arrow.down")
            Text(q.toClub)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ivory)
        .foregroundStyle(DesignTokens.Color.ink)
        .clipShape(RoundedRectangle(cornerRadius: 20))
    }

    private func options(_ q: QuestionView) -> some View {
        VStack(spacing: 12) {
            ForEach(Array(q.options.enumerated()), id: \.offset) { index, name in
                Button {
                    choose(index)
                } label: {
                    Text(name)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(optionBackground(index, name: name))
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
                .disabled(answer != nil)
            }
        }
    }

    /// After answering, the chosen option turns green or red and the correct
    /// one is always shown green, so a miss still teaches the answer.
    private func optionBackground(_ index: Int, name: String) -> Color {
        guard let answer else { return DesignTokens.Color.blueNight }
        if name == answer.revealedName { return DesignTokens.Color.green }
        return DesignTokens.Color.blueNight.opacity(0.4)
    }

    private var recap: some View {
        VStack(spacing: 16) {
            Text("Round over")
                .font(.title.bold())
            Text("\(score?.points ?? 0) points, best streak \(score?.bestStreak ?? 0)")
            Button("Play again", action: startRound)
                .padding()
                .background(DesignTokens.Color.yellow)
                .foregroundStyle(DesignTokens.Color.ink)
                .clipShape(RoundedRectangle(cornerRadius: 16))
        }
    }

    private func kindLabel(_ kind: MoveKind) -> String {
        switch kind {
        case .transfer: return "TRANSFER"
        case .loan: return "LOAN"
        case .free: return "FREE TRANSFER"
        }
    }

    // MARK: - Actions

    private func startRound() {
        let lang = languageForLocale(tag: Locale.current.identifier)
        // Seeded from the clock so each round differs; the core stays
        // deterministic for a given seed, which is what the tests rely on.
        game.startRound(lang: lang, mode: .easy, seed: UInt32.random(in: 0...UInt32.max))
        roundOver = false
        answer = nil
        advance()
    }

    private func choose(_ index: Int) {
        answer = try? game.submitChoice(index: UInt32(index))
        score = game.score()
        // Give the reveal a moment to read before moving on, matching the
        // prototype's auto-advance.
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
            answer = nil
            advance()
        }
    }

    private func advance() {
        question = game.nextQuestion()
        score = game.score()
        if question == nil { roundOver = true }
    }
}
