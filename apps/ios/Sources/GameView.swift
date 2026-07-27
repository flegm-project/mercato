import SwiftUI

/// The Game screen, matching the source design
/// (reference/design-source/Mercato.dc.html, the `isGame` block): a top bar of
/// close button, progress pips and score pill; the transfer card; the sponsor
/// board; and the answers pushed to the bottom of the column.
///
/// There is no skip and no result sheet. The answer turns green, a wrong pick
/// turns coral, the card outline follows, the score bumps, and the round
/// auto-advances. Tapping the card advances early.
///
/// The other screens (Splash, Onboarding, Home, Recap, Settings) are phase 5.
struct GameView: View {
    let game: Game

    @State private var question: QuestionView?
    @State private var answer: AnswerView?
    @State private var score: ScoreView?
    @State private var pickedIndex: Int?
    @State private var results: [Bool] = []
    @State private var bumpToken = 0
    @State private var roundOver = false
    /// Cancelled when the player taps the card to advance early.
    @State private var advanceWork: DispatchWorkItem?

    private var roundLength: Int { Int(DesignTokens.Game.roundLength) }

    var body: some View {
        ZStack {
            DS.appBackground

            VStack(spacing: 0) {
                topBar

                if let q = question {
                    TransferCard(
                        kindLabel: kindLabel(q.kind),
                        isLoan: q.kind == .loan,
                        year: q.year,
                        fromClub: q.fromClub,
                        toClub: q.toClub,
                        verdict: answer?.correct,
                        revealedName: answer?.revealedName
                    )
                    .padding(.top, 14)
                    .contentShape(Rectangle())
                    .onTapGesture { if answer != nil { advanceNow() } }

                    SponsorBoard(label: "SPONSOR")
                        .padding(.top, 10)

                    Spacer(minLength: 16)
                    answers(q)
                } else if roundOver {
                    recap
                    Spacer(minLength: 0)
                } else {
                    Spacer(minLength: 0)
                }
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.top, DesignTokens.Space.gutter)
            .padding(.bottom, DesignTokens.Space.gutter)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity)
        }
        .onAppear(perform: startRound)
    }

    // MARK: - Pieces

    private var topBar: some View {
        HStack(spacing: 12) {
            CloseButton(action: startRound)
            ProgressPips(states: pipStates)
            ScorePill(
                points: score?.points ?? 0,
                verdict: answer?.correct,
                bumpToken: bumpToken
            )
        }
    }

    /// Answered questions carry their verdict, the one on screen is live, the
    /// rest are pending.
    private var pipStates: [ProgressPips.State] {
        (0..<roundLength).map { index in
            if index < results.count { return results[index] ? .correct : .wrong }
            if index == results.count && question != nil && answer == nil { return .live }
            return .pending
        }
    }

    private func answers(_ q: QuestionView) -> some View {
        VStack(spacing: 11) {
            ForEach(Array(q.options.enumerated()), id: \.offset) { index, name in
                AnswerButton(title: name, verdict: verdict(index: index, name: name)) {
                    choose(index)
                }
            }
        }
    }

    /// Once answered, the answer is always shown green even on a miss so the
    /// player still learns it, and the wrong pick is called out in coral.
    private func verdict(index: Int, name: String) -> AnswerButton.Verdict {
        guard let answer else { return .idle }
        if name == answer.revealedName { return .correct }
        if index == pickedIndex { return .wrongPick }
        return .unpicked
    }

    private var recap: some View {
        VStack(spacing: DesignTokens.Space.lg) {
            Text("ROUND OVER")
                .font(DS.unbounded(30, weight: 900))
                .tracking(-0.05 * 30)
                .foregroundStyle(DesignTokens.Color.ivory)
            Text("\(score?.points ?? 0) points  ·  best streak \(score?.bestStreak ?? 0)")
                .font(DS.figtree(15, weight: 700))
                .foregroundStyle(DesignTokens.Color.ivory.opacity(0.8))

            Button(action: startRound) {
                Text("PLAY AGAIN")
                    .font(DS.unbounded(18, weight: 800))
                    .tracking(-0.045 * 18)
                    .foregroundStyle(DesignTokens.Color.ink)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 17)
                    .background(DesignTokens.Color.yellow)
                    .solidRaised(radius: 20, depth: 8)
            }
            .buttonStyle(.plain)
        }
        .padding(.top, 40)
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
        // Seeded from the clock so each round differs. The core stays
        // deterministic for a given seed, which is what the tests rely on.
        game.startRound(lang: lang, mode: .easy, seed: UInt32.random(in: 0...UInt32.max))
        advanceWork?.cancel()
        results = []
        answer = nil
        pickedIndex = nil
        roundOver = false
        advance()
    }

    private func choose(_ index: Int) {
        guard let result = try? game.submitChoice(index: UInt32(index)) else { return }
        pickedIndex = index
        answer = result
        score = game.score()
        results.append(result.correct)
        bumpToken += 1

        // motion.auto-advance: hold the reveal long enough to read it.
        let work = DispatchWorkItem { advanceNow() }
        advanceWork = work
        let delay = Double(DesignTokens.Motion.autoAdvance) / 1000
        DispatchQueue.main.asyncAfter(deadline: .now() + delay, execute: work)
    }

    private func advanceNow() {
        advanceWork?.cancel()
        advanceWork = nil
        answer = nil
        pickedIndex = nil
        advance()
    }

    private func advance() {
        question = game.nextQuestion()
        score = game.score()
        if question == nil { roundOver = true }
    }
}
