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
    let mode: GameMode
    let onFinish: (RoundSummary) -> Void
    let onQuit: () -> Void

    @State private var question: QuestionView?
    @State private var answer: AnswerView?
    @State private var score: ScoreView?
    @State private var pickedIndex: Int?
    @State private var results: [Bool] = []
    @State private var bumpToken = 0
    @State private var roundOver = false
    /// Cancelled when the player taps the card to advance early.
    @State private var advanceWork: DispatchWorkItem?
    /// Hardcore only.
    @State private var guess = ""
    @State private var hints: [HintView] = []
    @State private var ambiguous = false
    @State private var quitOpen = false

    private var roundLength: Int { Int(DesignTokens.Game.roundLength) }

    /// The mode as the shared event vocabulary spells it, not as Swift does.
    private var modeName: String { mode == .easy ? "easy" : "hardcore" }

    var body: some View {
        ZStack {
            DS.appBackground

            VStack(spacing: 0) {
                topBar

                if let q = question {
                    transferCard(q)
                    Spacer(minLength: 12)
                    if mode == .easy {
                        answers(q)
                    } else {
                        hardcore(q)
                    }
                    Spacer(minLength: 12)
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
        .overlay {
            if quitOpen {
                QuitDialog(onStay: { quitOpen = false }, onQuit: {
                    Analytics.shared.log(.roundQuit, [.mode: modeName, .answered: results.count])
                    onQuit()
                })
            }
        }
        .onAppear(perform: startRound)
    }

    // MARK: - Pieces

    private func transferCard(_ q: QuestionView) -> some View {
        TransferCard(
            kindLabel: kindLabel(q.kind),
            isLoan: q.kind == .loan,
            year: q.year,
            fromClub: q.fromClub,
            toClub: q.toClub,
            verdict: answer?.correct,
            maskedName: q.maskedName
        )
        .padding(.top, 14)
        .contentShape(Rectangle())
        .onTapGesture { if answer != nil { advanceNow() } }
    }

    private var topBar: some View {
        HStack(spacing: 12) {
            CloseButton { quitOpen = true }
            ProgressPips(states: pipStates)
            if mode == .hardcore {
                Hearts(remaining: Int(question?.attemptsLeft ?? 3), total: 3)
            }
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

    /// Free-text entry, the hint ladder, and the two controls.
    @ViewBuilder
    private func hardcore(_ q: QuestionView) -> some View {
        VStack(spacing: 12) {
            if !hints.isEmpty {
                // Wraps onto a second line once three chips are showing.
                FlowRow(spacing: 8) {
                    ForEach(Array(hints.enumerated()), id: \.offset) { _, hint in
                        HintChip(text: hintText(hint))
                    }
                }
            }

            if ambiguous {
                Text(L("rAmb"))
                    .typeStyle(TypeToken.bodySmallStrong)
                    .foregroundStyle(DesignTokens.Color.yellow)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity)
            }

            GuessField(
                text: $guess,
                placeholder: L("ph"),
                verdict: answer?.correct,
                onSubmit: submitGuess
            )

            HardcoreControls(
                hintLabel: "\(L("hint")) (\(3 - hints.count))",
                hintEnabled: answer == nil && hints.count < 3,
                submitLabel: L("go"),
                submitEnabled: answer == nil && !guess.trimmingCharacters(in: .whitespaces).isEmpty,
                onHint: takeHint,
                onSubmit: submitGuess
            )
        }
    }

    /// The hint ladder in order: nationality, then position, then the shape of
    /// the surname.
    private func hintText(_ hint: HintView) -> String {
        if let nationality = hint.nationality { return nationality }
        if let position = hint.position {
            switch position {
            case .gk: return L("posGk")
            case .def: return L("posDef")
            case .mid: return L("posMid")
            case .fw: return L("posFw")
            }
        }
        if let initial = hint.surnameInitial, let letters = hint.surnameLetters {
            // The initial, then one dot per remaining letter. "R · 7" made the
            // player guess the convention before they could use the hint;
            // "R ••••••" is read at a glance and needs no wording, so it works
            // the same in all three languages.
            return "\(initial) " + String(repeating: "\u{2022}", count: max(0, Int(letters) - 1))
        }
        return ""
    }

    private func takeHint() {
        if let hint = game.nextHint() {
            Analytics.shared.log(.hintTaken, [.rank: hints.count + 1])
            hints.append(hint)
        }
    }

    private func submitGuess() {
        let text = guess.trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty, answer == nil else { return }
        guard let result = try? game.submitGuess(text: text) else { return }

        // An ambiguous surname asks for the first name and costs no attempt,
        // so the question stays open and nothing is recorded.
        if result.rejection == .ambiguousSurname {
            ambiguous = true
            return
        }
        ambiguous = false

        if !result.finished {
            // Wrong but attempts remain: clear the field and let them try
            // again. The cached question still carries the old attempt count,
            // so re-read it to make the hearts drop.
            guess = ""
            score = game.score()
            question = game.currentQuestion()
            return
        }

        answer = result
        score = game.score()
        results.append(result.correct)
        bumpToken += 1
        Sounds.shared.play(result.correct ? .correct : .wrong)

        let work = DispatchWorkItem { advanceNow() }
        advanceWork = work
        DispatchQueue.main.asyncAfter(
            deadline: .now() + Double(DesignTokens.Motion.autoAdvance) / 1000,
            execute: work
        )
    }

    private func answers(_ q: QuestionView) -> some View {
        VStack(spacing: 14) {
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


    private func kindLabel(_ kind: MoveKind) -> String {
        switch kind {
        case .transfer: return L("perm")
        case .loan: return L("loan")
        }
    }

    // MARK: - Actions

    private func startRound() {
        let lang = languageForLocale(tag: Locale.current.identifier)
        // Seeded from the clock so each round differs. The core stays
        // deterministic for a given seed, which is what the tests rely on.
        game.startRound(lang: lang, mode: mode, seed: UInt32.random(in: 0...UInt32.max))
        Analytics.shared.log(.roundStart, [.mode: modeName])
        advanceWork?.cancel()
        results = []
        answer = nil
        pickedIndex = nil
        guess = ""
        hints = []
        ambiguous = false
        roundOver = false
        advance()
        #if DEBUG
        // QA: pre-unlock the nationality and position hints for screenshots.
        if CommandLine.arguments.contains("-MercatoHints") {
            for _ in 0..<2 where question != nil {
                if let h = game.nextHint() { hints.append(h) }
            }
        }
        // QA: submit a (wrong) answer so the reveal state can be captured.
        if CommandLine.arguments.contains("-MercatoAnswer"), question != nil {
            if mode == .easy {
                choose(0)
            } else {
                guess = "zzzzzz"
                submitGuess()
            }
            advanceWork?.cancel() // freeze the reveal for the screenshot
        }
        // QA: show the quit dialog.
        if CommandLine.arguments.contains("-MercatoQuit") { quitOpen = true }
        #endif
    }

    private func choose(_ index: Int) {
        guard let result = try? game.submitChoice(index: UInt32(index)) else { return }
        pickedIndex = index
        answer = result
        score = game.score()
        results.append(result.correct)
        bumpToken += 1
        Sounds.shared.play(result.correct ? .correct : .wrong)

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
        guess = ""
        hints = []
        ambiguous = false
        advance()
    }

    private func advance() {
        question = game.nextQuestion()
        score = game.score()
        if question == nil, !roundOver {
            roundOver = true
            Sounds.shared.play(.roundOver)
            // Recomputed here rather than read from the recap view, which is
            // built by the caller: the two must agree, and the star rule lives
            // in the core's tokens (game.stars).
            let right = results.filter { $0 }.count
            let ratio = roundLength == 0 ? 0 : Double(right) / Double(roundLength)
            let stars = ratio >= 0.9 ? 3 : (ratio >= 0.6 ? 2 : (ratio > 0 ? 1 : 0))
            Analytics.shared.log(.roundEnd, [
                .mode: modeName,
                .score: Int(game.score().points),
                .correct: right,
                .stars: stars,
                .won: stars >= 2,
            ])
            // The denominator is the fixed round length, not the number of
            // questions answered: a Hardcore round that ends early on lost lives
            // still reports the score out of the full round (e.g. 4/10).
            onFinish(RoundSummary(
                mode: mode,
                score: game.score(),
                correct: results.filter { $0 }.count,
                total: roundLength
            ))
        }
    }
}
