import SwiftUI

/// Localized copy. The bundle carries en/fr/es and iOS resolves against the
/// system language, falling back to English, which is the product decision
/// (docs/GAME_DESIGN.md). Keys come from design/strings.json.
func L(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

/// The wordmark: MER in ivory, CATO in yellow, over a hard ink offset shadow.
struct Wordmark: View {
    var size: CGFloat = 74

    var body: some View {
        (Text("MER").foregroundColor(DesignTokens.Color.ivory)
            + Text("CATO").foregroundColor(DesignTokens.Color.yellow))
            .font(DS.unbounded(size, weight: 900))
            .tracking(-0.06 * size)
            .shadow(color: DesignTokens.Color.ink, radius: 0, x: 6, y: 7)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
    }
}

// MARK: - Splash

/// Wordmark over a loading bar. Hands over once the bar completes.
struct SplashView: View {
    let onDone: () -> Void
    @State private var progress: CGFloat = 0.08

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 34) {
                Wordmark()
                ZStack(alignment: .leading) {
                    Capsule().fill(DesignTokens.Color.ink.opacity(0.4))
                    GeometryReader { geo in
                        Capsule()
                            .fill(DesignTokens.Color.yellow)
                            .frame(width: geo.size.width * progress)
                    }
                }
                .frame(width: 172, height: 14)
                .inkOutlined(Capsule())
            }
            .padding(.bottom, 60)
        }
        .onAppear {
            withAnimation(.timingCurve(0.5, 0.1, 0.3, 1, duration: 1.3)) { progress = 1 }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.4, execute: onDone)
        }
    }
}

// MARK: - Home

/// Wordmark with the two modes pushed to the bottom of the column.
///
/// The source design also carries a balls counter here; there is no soft
/// currency in this product (docs/MONETIZATION.md), so it is left out.
struct HomeView: View {
    let onPlay: (GameMode) -> Void

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(alignment: .leading, spacing: 0) {
                // The wordmark sits near the top and the modes are anchored to
                // the bottom, as in the source design, rather than the whole
                // block floating in the middle.
                Wordmark()
                    .padding(.top, 36)
                Spacer(minLength: DesignTokens.Space.xl)

                VStack(spacing: 14) {
                    modeButton(title: L("l1"), chip: L("c1"), fill: DesignTokens.Color.yellow) {
                        onPlay(.easy)
                    }
                    modeButton(title: L("l3"), chip: L("c3"), fill: DesignTokens.Color.ivory) {
                        onPlay(.hardcore)
                    }
                }
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.vertical, DesignTokens.Space.gutter)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func modeButton(title: String, chip: String, fill: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Text(title)
                    .font(DS.unbounded(30, weight: 900))
                    .tracking(-0.05 * 30)
                    .foregroundStyle(DesignTokens.Color.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(chip)
                    .font(DS.unbounded(12, weight: 800))
                    .foregroundStyle(DesignTokens.Color.yellow)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(DesignTokens.Color.ink)
                    .clipShape(Capsule())
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 22)
            .background(fill)
            .solidRaised(radius: DesignTokens.Radius.card, depth: 10)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Recap

/// End of round: verdict, stars and the score card.
///
/// The source design also awards balls, offers a rewarded video, and lists the
/// transfers that were missed. None of those belong here: there is no soft
/// currency and no rewarded ads (docs/MONETIZATION.md), and the round is not
/// meant to be corrected answer by answer. The screen shows the score and the
/// two stats, nothing else.
struct RecapView: View {
    let score: ScoreView
    let correct: Int
    let total: Int
    let onAgain: () -> Void
    let onHome: () -> Void

    /// 3 stars at 90% correct, 2 at 60%, 1 above zero (tokens: game.stars).
    private var stars: Int {
        guard total > 0 else { return 0 }
        let share = Double(correct) / Double(total)
        if share >= 0.9 { return 3 }
        if share >= 0.6 { return 2 }
        return correct > 0 ? 1 : 0
    }

    var body: some View {
        ZStack {
            DS.appBackground
            ScrollView {
                VStack(spacing: 0) {
                    Text(stars >= 2 ? L("winT") : L("loseT"))
                        .font(DS.unbounded(28, weight: 900))
                        .tracking(-0.05 * 28)
                        .foregroundStyle(DesignTokens.Color.ivory)
                        .padding(.top, 22)

                    HStack(spacing: 12) {
                        ForEach(0..<3, id: \.self) { index in
                            Text("\u{2605}")
                                .font(.system(size: 46))
                                .foregroundStyle(
                                    index < stars
                                        ? DesignTokens.Color.yellow
                                        : Color.white.opacity(0.15)
                                )
                                .shadow(color: DesignTokens.Color.ink, radius: 0, x: 4, y: 4)
                        }
                    }
                    .padding(.top, 18)

                    scoreCard.padding(.top, 22)

                    VStack(spacing: 10) {
                        Button(action: onAgain) {
                            Text(L("again"))
                                .font(DS.unbounded(18, weight: 800))
                                .tracking(-0.045 * 18)
                                .foregroundStyle(DesignTokens.Color.ink)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 17)
                                .background(DesignTokens.Color.yellow)
                                .solidRaised(radius: 20, depth: 8)
                        }
                        .buttonStyle(.plain)

                        Button(action: onHome) {
                            Text(L("home"))
                                .font(DS.figtree(15, weight: 800))
                                .foregroundStyle(DesignTokens.Color.ivory.opacity(0.75))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 22)
                }
                .padding(.horizontal, DesignTokens.Space.gutter)
                .padding(.bottom, 20)
                .frame(maxWidth: DesignTokens.Layout.columnMax)
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var scoreCard: some View {
        VStack(spacing: 0) {
            Text("\(score.points)")
                .font(DS.unbounded(64, weight: 900))
                .foregroundStyle(DesignTokens.Color.ink)
            Text(L("pts").uppercased())
                .font(DS.figtree(12.5, weight: 900))
                .tracking(0.16 * 12.5)
                .foregroundStyle(DesignTokens.Color.muted)
                .padding(.top, 8)

            HStack(spacing: 11) {
                statTile(value: "\(correct)/\(total)", label: L("rGood"), tint: DesignTokens.Color.greenDeep)
                statTile(value: "\(score.bestStreak)", label: L("rStreak"), tint: DesignTokens.Color.ink)
            }
            .padding(.top, 16)
        }
        .padding(22)
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ivory)
        .solidRaised(radius: DesignTokens.Radius.card, depth: 10)
    }

    private func statTile(value: String, label: String, tint: Color) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(DS.unbounded(22, weight: 900))
                .foregroundStyle(tint)
            Text(label)
                .font(DS.figtree(11.5, weight: 900))
                .foregroundStyle(DesignTokens.Color.muted)
                .multilineTextAlignment(.center)
        }
        .padding(13)
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ink.opacity(0.07))
        .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
    }

}
