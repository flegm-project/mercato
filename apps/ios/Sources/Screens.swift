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
    /// Play/Profile is the top-level navigation, matching Android; Settings is
    /// reached from Profile, not from here.
    let onProfile: () -> Void
    /// The core gate decides whether the banner shows.
    let game: Game

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(alignment: .leading, spacing: 0) {
                // The wordmark sits near the top and the modes are anchored to
                // the bottom, as in the source design, rather than the whole
                // block floating in the middle.
                Wordmark()
                    .padding(.top, 14)
                Spacer(minLength: DesignTokens.Space.xl)

                VStack(spacing: 14) {
                    modeButton(title: L("l1"), chip: L("c1"), fill: DesignTokens.Color.yellow) {
                        onPlay(.easy)
                    }
                    modeButton(title: L("l3"), chip: L("c3"), fill: DesignTokens.Color.ivory) {
                        onPlay(.hardcore)
                    }
                }

                BannerSlot(game: game)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 14)
                    .padding(.bottom, 8)

                MercatoTabBar(tabs: [L("tPlay"), L("tProfile")], selected: 0) { index in
                    if index == 1 { onProfile() }
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
    /// The core gate decides whether the rectangle shows.
    let game: Game
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

                    RectangleSlot(game: game)
                        .padding(.top, 20)

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

// MARK: - Onboarding

/// Three panes explaining the game, skippable. Shown once on first launch.
struct OnboardingView: View {
    let onDone: () -> Void
    @State private var step = 0

    private let steps = 3

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(L("obSkip").uppercased(), action: onDone)
                        .font(DS.figtree(13, weight: 900))
                        .tracking(0.06 * 13)
                        .foregroundStyle(Color.white.opacity(0.68))
                        .padding(8)
                }
                .frame(height: 38)

                Spacer(minLength: 0)

                VStack(alignment: .leading, spacing: 26) {
                    illustration
                    VStack(alignment: .leading, spacing: 12) {
                        Text(L("ob.\(step).t"))
                            .font(DS.unbounded(30, weight: 900))
                            .tracking(-0.05 * 30)
                            .foregroundStyle(DesignTokens.Color.ivory)
                        Text(L("ob.\(step).b"))
                            .font(DS.figtree(16, weight: 700))
                            .foregroundStyle(Color.white.opacity(0.68))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Spacer(minLength: 0)

                HStack(spacing: 8) {
                    ForEach(0..<steps, id: \.self) { index in
                        Capsule()
                            .fill(index == step ? DesignTokens.Color.yellow : Color.white.opacity(0.25))
                            .frame(width: index == step ? 26 : 10, height: 10)
                    }
                }
                .padding(.bottom, 18)

                Button {
                    if step + 1 < steps { step += 1 } else { onDone() }
                } label: {
                    Text(step + 1 < steps ? L("obNext") : L("obStart"))
                        .font(DS.unbounded(22, weight: 900))
                        .tracking(-0.03 * 22)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .frame(maxWidth: .infinity)
                        .padding(20)
                        .background(DesignTokens.Color.yellow)
                        .solidRaised(radius: 22, depth: 9)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.top, DesignTokens.Space.gutter)
            .padding(.bottom, 20)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity)
        }
    }

    /// Placeholder art, hatched so it reads as a deliberate slot rather than a
    /// failed image.
    private var illustration: some View {
        ZStack {
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DesignTokens.Color.ivory))
                var x = -size.height
                while x < size.width + size.height {
                    var stripe = Path()
                    stripe.move(to: CGPoint(x: x, y: size.height))
                    stripe.addLine(to: CGPoint(x: x + size.height, y: 0))
                    stripe.addLine(to: CGPoint(x: x + size.height + 14, y: 0))
                    stripe.addLine(to: CGPoint(x: x + 14, y: size.height))
                    stripe.closeSubpath()
                    context.fill(stripe, with: .color(Color(red: 0.937, green: 0.929, blue: 0.890)))
                    x += 28
                }
            }
            Text(L("ob.\(step).a"))
                .font(DS.mono(11))
                .tracking(0.18 * 11)
                .foregroundStyle(DesignTokens.Color.clubGrey)
                .multilineTextAlignment(.center)
                .padding(20)
        }
        .frame(height: 200)
        .solidRaised(radius: DesignTokens.Radius.card, depth: 10)
    }
}

// MARK: - Ad consent

/// Asked before the first round and reachable again from Settings. Refusing
/// gives non-personalised ads, not fewer of them (docs/specs/ads.md).
struct ConsentView: View {
    let onChoice: (Bool) -> Void

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 0) {
                Spacer(minLength: 0)

                VStack(alignment: .leading, spacing: 0) {
                    Text(L("cnTitle"))
                        .font(DS.unbounded(24, weight: 900))
                        .tracking(-0.05 * 24)
                    Text(L("cnBody"))
                        .font(DS.figtree(14.5, weight: 600))
                        .foregroundStyle(DesignTokens.Color.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 12)

                    VStack(alignment: .leading, spacing: 9) {
                        ForEach(0..<3, id: \.self) { index in
                            HStack(alignment: .top, spacing: 10) {
                                Text("\u{00B7}")
                                    .font(DS.unbounded(14, weight: 900))
                                    .foregroundStyle(DesignTokens.Color.blue)
                                Text(L("cnPoints.\(index)"))
                                    .font(DS.figtree(13.5, weight: 700))
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                    }
                    .padding(.top, 16)
                }
                .foregroundStyle(DesignTokens.Color.ink)
                .padding(22)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(DesignTokens.Color.ivory)
                .solidRaised(radius: DesignTokens.Radius.card, depth: 10)

                Spacer(minLength: 0)

                VStack(spacing: 11) {
                    Button { onChoice(true) } label: {
                        Text(L("cnAccept"))
                            .font(DS.unbounded(18, weight: 900))
                            .tracking(-0.03 * 18)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .frame(maxWidth: .infinity)
                            .padding(19)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: 22, depth: 9)
                    }
                    .buttonStyle(.plain)

                    Button { onChoice(false) } label: {
                        Text(L("cnRefuse"))
                            .font(DS.unbounded(15, weight: 800))
                            .tracking(-0.03 * 15)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity)
                            .padding(17)
                            .background(DesignTokens.Color.ivory)
                            .solidRaised(radius: 22, depth: 9)
                    }
                    .buttonStyle(.plain)

                    Text(L("cnFoot"))
                        .font(DS.figtree(11.5, weight: 700))
                        .foregroundStyle(Color.white.opacity(0.56))
                        .padding(.top, 4)
                }
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.vertical, 20)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Quit dialog

/// Confirms leaving a round in progress, since the score is not persisted.
struct QuitDialog: View {
    let onStay: () -> Void
    let onQuit: () -> Void

    var body: some View {
        ZStack {
            Color(red: 0.024, green: 0.035, blue: 0.118).opacity(0.78)
                .ignoresSafeArea()
                .onTapGesture(perform: onStay)

            VStack(alignment: .leading, spacing: 0) {
                Text(L("quitT"))
                    .font(DS.unbounded(22, weight: 900))
                    .tracking(-0.05 * 22)
                Text(L("quitB"))
                    .font(DS.figtree(14, weight: 700))
                    .foregroundStyle(DesignTokens.Color.muted)
                    .padding(.top, 10)
                    .padding(.bottom, 18)

                VStack(spacing: 10) {
                    Button(action: onStay) {
                        Text(L("quitStay"))
                            .font(DS.unbounded(17, weight: 900))
                            .tracking(-0.03 * 17)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .frame(maxWidth: .infinity)
                            .padding(16)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: 20, depth: 8)
                    }
                    .buttonStyle(.plain)

                    Button(action: onQuit) {
                        Text(L("quitGo"))
                            .font(DS.unbounded(15, weight: 800))
                            .tracking(-0.03 * 15)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(15)
                            .background(DesignTokens.Color.coral)
                            .solidRaised(radius: 20, depth: 8)
                    }
                    .buttonStyle(.plain)
                }
            }
            .foregroundStyle(DesignTokens.Color.ink)
            .padding(22)
            .frame(maxWidth: 360)
            .background(DesignTokens.Color.ivory)
            .solidRaised(radius: DesignTokens.Radius.card, depth: 12)
            .padding(24)
        }
    }
}

// MARK: - Settings

/// Sound, vibration and notification switches, plus the rows that lead back to
/// the consent choice and the intro. The language row is read only: the game
/// follows the system language and has no in-app picker.
struct SettingsView: View {
    let onBack: () -> Void
    let onConsent: () -> Void
    let onReplayIntro: () -> Void
    @ObservedObject var store: Store

    @AppStorage("soundOn") private var soundOn = true
    @AppStorage("vibrationOn") private var vibrationOn = true
    @AppStorage("notificationsOn") private var notificationsOn = false
    @AppStorage("adsPersonalised") private var adsPersonalised = false

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    Button(action: onBack) {
                        Text("\u{2039}")
                            .font(.system(size: 20, weight: .black))
                            .foregroundStyle(.white)
                            .frame(width: 38, height: 38)
                            .background(DesignTokens.Color.ink.opacity(0.45))
                            .inkOutlined(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Back")

                    Text(L("settings"))
                        .font(DS.unbounded(20, weight: 900))
                        .tracking(-0.04 * 20)
                        .foregroundStyle(DesignTokens.Color.ivory)
                }
                .frame(height: 46)

                VStack(spacing: 10) {
                    toggleRow(L("soundS"), isOn: $soundOn)
                    toggleRow(L("vibrateS"), isOn: $vibrationOn)
                    toggleRow(L("notifS"), isOn: $notificationsOn)
                }
                .padding(.top, 12)

                purchaseCard.padding(.top, 16)

                VStack(spacing: 9) {
                    linkRow(L("rowLang"), value: "\(languageName) \u{00B7} \(L("systemV"))", action: nil)
                    linkRow(
                        L("rowConsent"),
                        value: adsPersonalised ? L("consentOn") : L("consentOff"),
                        action: onConsent
                    )
                    linkRow(L("rowIntro"), value: "\u{203A}", action: onReplayIntro)
                }
                .padding(.top, 16)

                Spacer(minLength: 20)

                Text(L("version"))
                    .font(DS.mono(11))
                    .foregroundStyle(Color.white.opacity(0.5))
                    .frame(maxWidth: .infinity)
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.vertical, DesignTokens.Space.gutter)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity)
        }
    }

    /// The only purchase in the app. Once owned it stops offering itself and
    /// simply reports the state, so there is nothing left to buy anywhere.
    private var purchaseCard: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(L("shopNoAds"))
                        .font(DS.unbounded(18, weight: 900))
                        .tracking(-0.04 * 18)
                    Text(L("shopNoAdsSub"))
                        .font(DS.figtree(13, weight: 700))
                        .foregroundStyle(DesignTokens.Color.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
                if store.adsRemoved {
                    Text(L("owned"))
                        .font(DS.unbounded(12, weight: 800))
                        .tracking(0.08 * 12)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(DesignTokens.Color.green)
                        .clipShape(Capsule())
                } else {
                    Button { Task { await store.purchase() } } label: {
                        Text(store.displayPrice ?? "\u{2026}")
                            .font(DS.unbounded(15, weight: 900))
                            .tracking(-0.03 * 15)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 11)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: 16, border: 4, depth: 6)
                    }
                    .buttonStyle(.plain)
                    .disabled(store.purchasing || store.product == nil)
                }
            }

            if !store.adsRemoved {
                Button { Task { await store.restore() } } label: {
                    Text(L("restore"))
                        .font(DS.figtree(13, weight: 800))
                        .foregroundStyle(DesignTokens.Color.blue)
                }
                .buttonStyle(.plain)
                .padding(.top, 14)
            }
        }
        .foregroundStyle(DesignTokens.Color.ink)
        .padding(18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(DesignTokens.Color.ivory)
        .solidRaised(radius: 18, border: 4, depth: 6)
        .alert(L("restoreToast"), isPresented: $store.restoreFoundNothing) {
            Button("OK", role: .cancel) {}
        }
    }

    private var languageName: String {
        switch languageForLocale(tag: Locale.current.identifier) {
        case .fr: return "Français"
        case .es: return "Español"
        case .en: return "English"
        }
    }

    private func toggleRow(_ label: String, isOn: Binding<Bool>) -> some View {
        Button { isOn.wrappedValue.toggle() } label: {
            HStack(spacing: 14) {
                Text(label)
                    .font(DS.unbounded(15, weight: 800))
                    .tracking(-0.03 * 15)
                    .frame(maxWidth: .infinity, alignment: .leading)
                SwitchTrack(isOn: isOn.wrappedValue)
            }
            .foregroundStyle(DesignTokens.Color.ink)
            .padding(.horizontal, 16)
            .padding(.vertical, 15)
            .background(DesignTokens.Color.ivory)
            .solidRaised(radius: 18, border: 4, depth: 6)
        }
        .buttonStyle(.plain)
    }

    private func linkRow(_ label: String, value: String, action: (() -> Void)?) -> some View {
        Button { action?() } label: {
            HStack(spacing: 12) {
                Text(label)
                    .font(DS.figtree(14.5, weight: 800))
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(value)
                    .font(DS.mono(11))
                    .foregroundStyle(Color.white.opacity(0.75))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(DesignTokens.Color.ink.opacity(0.35))
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .strokeBorder(Color.white.opacity(0.16), lineWidth: 3)
            )
        }
        .buttonStyle(.plain)
        // Not `.disabled`: that dims the row, and the language row is meant to
        // read as normal copy that simply cannot be tapped, not as unavailable.
        .allowsHitTesting(action != nil)
    }
}

/// 52x30 track, ink border, green when on, ivory knob.
struct SwitchTrack: View {
    let isOn: Bool

    var body: some View {
        ZStack(alignment: isOn ? .trailing : .leading) {
            Capsule()
                .fill(isOn ? DesignTokens.Color.green : DesignTokens.Color.ink.opacity(0.25))
            Circle()
                .fill(DesignTokens.Color.ivory)
                .overlay(Circle().strokeBorder(DesignTokens.Color.ink, lineWidth: 3))
                .frame(width: 22, height: 22)
                .padding(2)
        }
        .frame(width: 52, height: 30)
        .inkOutlined(Capsule(), border: 3)
        .animation(.snappy(duration: 0.18), value: isOn)
    }
}
