import SwiftUI

/// Localized copy. The bundle carries en/fr/es and iOS resolves against the
/// system language, falling back to English, which is the product decision
/// (docs/GAME_DESIGN.md). Keys come from design/strings.json.
func L(_ key: String) -> String {
    NSLocalizedString(key, comment: "")
}

/// The wordmark: MER in ivory, CATO in yellow, over a hard ink offset shadow.
struct Wordmark: View {
    var size: CGFloat = TypeToken.logo.size

    var body: some View {
        (Text("MER").foregroundColor(DesignTokens.Color.ivory)
            + Text("CATO").foregroundColor(DesignTokens.Color.yellow))
            .font(DS.unbounded(size, weight: TypeToken.logo.weight))
            .tracking(DS.tracking(TypeToken.logo, at: size))
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
                    Capsule().fill(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.chip))
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
                // The wordmark and the two modes are centred as one block in the
                // space above the banner and tab bar, so the screen reads as
                // balanced rather than emptied out on tall devices.
                Spacer(minLength: 0)

                Wordmark()

                VStack(spacing: 14) {
                    modeButton(title: L("l1"), fill: DesignTokens.Color.yellow) {
                        onPlay(.easy)
                    }
                    modeButton(title: L("l3"), fill: DesignTokens.Color.ivory) {
                        onPlay(.hardcore)
                    }
                }
                .padding(.top, DesignTokens.Space.xl)

                Spacer(minLength: 0)

                BannerSlot(game: game)
                    .frame(maxWidth: .infinity)
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

    private func modeButton(title: String, fill: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .typeStyle(TypeToken.screenTitle)
                .foregroundStyle(DesignTokens.Color.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
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
                        .typeStyle(TypeToken.recapTitle)
                        .foregroundStyle(DesignTokens.Color.ivory)
                        .padding(.top, 22)

                    HStack(spacing: 12) {
                        ForEach(0..<3, id: \.self) { index in
                            Text("\u{2605}")
                                .font(.system(size: 46))
                                .foregroundStyle(
                                    index < stars
                                        ? DesignTokens.Color.yellow
                                        : Color.white.opacity(DesignTokens.Opacity.starOff)
                                )
                                .shadow(color: DesignTokens.Color.ink, radius: 0, x: 4, y: 4)
                        }
                    }
                    .padding(.top, 18)

                    scoreCard.padding(.top, 22)

                    VStack(spacing: 10) {
                        Button(action: onAgain) {
                            Text(L("again"))
                                .typeStyle(TypeToken.answer)
                                .foregroundStyle(DesignTokens.Color.ink)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 17)
                                .background(DesignTokens.Color.yellow)
                                .solidRaised(radius: DesignTokens.Radius.button, depth: 8)
                        }
                        .buttonStyle(.plain)

                        // A full-width secondary button, not a thin text link,
                        // so it is a comfortable finger target next to Play again.
                        Button(action: onHome) {
                            Text(L("home"))
                                .typeStyle(TypeToken.ctaSmall)
                                .foregroundStyle(DesignTokens.Color.ivory)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 16)
                                .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.row))
                                .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.medium, style: .continuous))
                                .overlay(
                                    RoundedRectangle(cornerRadius: DesignTokens.Radius.medium, style: .continuous)
                                        .strokeBorder(Color.white.opacity(DesignTokens.Opacity.borderPanel), lineWidth: 2)
                                )
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 22)

                    // Below the CTAs so it never pushes Play Again down.
                    RectangleSlot(game: game)
                        .padding(.top, 24)
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
                .typeStyle(TypeToken.scoreHero)
                .foregroundStyle(DesignTokens.Color.ink)
            Text(L("pts").uppercased())
                .typeStyle(TypeToken.label)
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
                .typeStyle(TypeToken.scorePill)
                .foregroundStyle(tint)
            Text(label)
                .typeStyle(TypeToken.tileLabel)
                .foregroundStyle(DesignTokens.Color.muted)
                .multilineTextAlignment(.center)
        }
        .padding(13)
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.surfaceTint))
        .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.tile, style: .continuous))
    }

}

// MARK: - Onboarding

/// Three panes explaining the game, skippable. Shown once on first launch.
struct OnboardingView: View {
    let onDone: () -> Void
    @State private var step = OnboardingView.debugStep

    private let steps = 3

    /// Which pane a capture opens on. The three scenes are three different
    /// pictures, and only the first was reachable without swiping, so the
    /// other two were never measured against Android at all.
    /// scripts/capture-parity.sh asks for them as `onboarding2` and
    /// `onboarding3`; Android reads the same names.
    private static var debugStep: Int {
        #if DEBUG
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "-MercatoRoute"), i + 1 < args.count else { return 0 }
        switch args[i + 1] {
        case "onboarding2": return 1
        case "onboarding3": return 2
        default: return 0
        }
        #else
        0
        #endif
    }

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 0) {
                HStack {
                    Spacer()
                    Button(L("obSkip").uppercased(), action: onDone)
                        .typeStyle(TypeToken.skipLabel)
                        .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.textSoft))
                        .padding(8)
                }
                .frame(height: 38)

                Spacer(minLength: 0)

                VStack(alignment: .leading, spacing: 26) {
                    illustration
                    VStack(alignment: .leading, spacing: 12) {
                        Text(L("ob.\(step).t"))
                            .typeStyle(TypeToken.screenTitle)
                            .foregroundStyle(DesignTokens.Color.ivory)
                        Text(L("ob.\(step).b"))
                            .typeStyle(TypeToken.bodyLarge)
                            .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.textSoft))
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                Spacer(minLength: 0)

                HStack(spacing: 8) {
                    ForEach(0..<steps, id: \.self) { index in
                        Capsule()
                            .fill(index == step ? DesignTokens.Color.yellow : Color.white.opacity(DesignTokens.Opacity.trackOff))
                            .frame(width: index == step ? 26 : 10, height: 10)
                    }
                }
                .padding(.bottom, 18)

                Button {
                    if step + 1 < steps { step += 1 } else { onDone() }
                } label: {
                    Text(step + 1 < steps ? L("obNext") : L("obStart"))
                        .typeStyle(TypeToken.ctaLarge)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .frame(maxWidth: .infinity)
                        .padding(20)
                        .background(DesignTokens.Color.yellow)
                        .solidRaised(radius: DesignTokens.Radius.large, depth: 9)
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

    /// The pane's picture: the pass, the answer, the three stars, each drawn
    /// live from design/onboarding.json rather than shipped as an image. See
    /// OnboardingArt.swift for why it moved.
    private var illustration: some View {
        OnboardingArtView(pane: step)
            .frame(height: OnboardingScene.height + OnboardingScene.border * 2)
            .frame(maxWidth: .infinity)
            .clipped()
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
                        .typeStyle(TypeToken.consentTitle)
                    Text(L("cnBody"))
                        .typeStyle(TypeToken.consentBody)
                        .foregroundStyle(DesignTokens.Color.muted)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 12)

                    VStack(alignment: .leading, spacing: 9) {
                        ForEach(0..<3, id: \.self) { index in
                            HStack(alignment: .top, spacing: 10) {
                                Text("\u{00B7}")
                                    .typeStyle(TypeToken.bulletGlyph)
                                    .foregroundStyle(DesignTokens.Color.blue)
                                Text(L("cnPoints.\(index)"))
                                    .typeStyle(TypeToken.labLine)
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
                            .typeStyle(TypeToken.sectionTitle)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .frame(maxWidth: .infinity)
                            .padding(19)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: DesignTokens.Radius.large, depth: 9)
                    }
                    .buttonStyle(.plain)

                    Button { onChoice(false) } label: {
                        Text(L("cnRefuse"))
                            .typeStyle(TypeToken.ctaCompact)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .lineLimit(1)
                            .minimumScaleFactor(0.7)
                            .frame(maxWidth: .infinity)
                            .padding(17)
                            .background(DesignTokens.Color.ivory)
                            .solidRaised(radius: DesignTokens.Radius.large, depth: 9)
                    }
                    .buttonStyle(.plain)

                    Text(L("cnFoot"))
                        .typeStyle(TypeToken.footnote)
                        .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.textFootnote))
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
            Color(red: 0.024, green: 0.035, blue: 0.118).opacity(DesignTokens.Opacity.scrim)
                .ignoresSafeArea()
                .onTapGesture(perform: onStay)

            VStack(alignment: .leading, spacing: 0) {
                Text(L("quitT"))
                    .typeStyle(TypeToken.dialogTitle)
                Text(L("quitB"))
                    .typeStyle(TypeToken.bodyMid)
                    .foregroundStyle(DesignTokens.Color.muted)
                    .padding(.top, 10)
                    .padding(.bottom, 18)

                VStack(spacing: 10) {
                    Button(action: onStay) {
                        Text(L("quitStay"))
                            .typeStyle(TypeToken.ctaMedium)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .frame(maxWidth: .infinity)
                            .padding(16)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: DesignTokens.Radius.button, depth: 8)
                    }
                    .buttonStyle(.plain)

                    Button(action: onQuit) {
                        Text(L("quitGo"))
                            .typeStyle(TypeToken.ctaCompact)
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity)
                            .padding(15)
                            .background(DesignTokens.Color.coral)
                            .solidRaised(radius: DesignTokens.Radius.button, depth: 8)
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

/// Sound and notification switches, plus the rows that lead back to the
/// consent choice and the intro. The language row is read only: the game
/// follows the system language and has no in-app picker.
///
/// There is no vibration switch. It was offered and stored, but nothing in the
/// app ever asked for haptics, so it was a control that did nothing and told
/// the player it did something.
///
/// The notification switch is kept deliberately, and is still only stored: it
/// is the setting a daily reminder will read, and removing it now would mean
/// asking for the permission and the preference again later.
struct SettingsView: View {
    let onBack: () -> Void
    let onConsent: () -> Void
    let onReplayIntro: () -> Void
    let onOffline: () -> Void
    let onLab: () -> Void
    /// The core gate decides whether the settings banner shows.
    let game: Game
    @ObservedObject var store: Store

    @AppStorage("soundOn") private var soundOn = true
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
                            .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.control))
                            .inkOutlined(RoundedRectangle(cornerRadius: DesignTokens.Radius.control, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Back")

                    Text(L("settings"))
                        .typeStyle(TypeToken.panelTitle)
                        .foregroundStyle(DesignTokens.Color.ivory)
                }
                .frame(height: 46)

                VStack(spacing: 10) {
                    toggleRow(L("soundS"), isOn: $soundOn)
                        .onChange(of: soundOn) { _, on in
                            Analytics.shared.log(.soundSet, [.on: on])
                        }
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
                    linkRow(L("rowPrivacy"), value: "\u{2197}") {
                        if let url = URL(string: AppLinks.privacyPolicy) {
                            UIApplication.shared.open(url)
                        }
                    }
                    linkRow(L("rowIntro"), value: "\u{203A}", action: onReplayIntro)
                    linkRow(L("rowOffline"), value: "\u{203A}", action: onOffline)
                    #if DEBUG
                    linkRow(L("rowLab"), value: "\u{203A}", action: onLab)
                    #endif
                }
                .padding(.top, 16)

                Spacer(minLength: 16)

                BannerSlot(game: game)
                    .frame(maxWidth: .infinity)

                Text("\(L("version")) \(AppLinks.shortVersion)")
                    .typeStyle(TypeToken.monoPlain)
                    .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.textFaintest))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 12)
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
            // The whole row is the buy control: tapping "Remove ads" starts the
            // purchase. The descriptive subtitle was dropped to keep it to one
            // obvious action.
            if store.adsRemoved {
                noAdsRow {
                    Text(L("owned"))
                        .typeStyle(TypeToken.ownedBadge)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 7)
                        .background(DesignTokens.Color.green)
                        .clipShape(Capsule())
                }
            } else if let price = store.displayPrice {
                Button { Task { await store.purchase() } } label: {
                    noAdsRow {
                        Text(price)
                            .typeStyle(TypeToken.badgeValue)
                            .foregroundStyle(DesignTokens.Color.ink)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 11)
                            .background(DesignTokens.Color.yellow)
                            .solidRaised(radius: DesignTokens.Radius.row, border: 4, depth: 6)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(store.purchasing)
            } else {
                // The store product (hence its price) has not loaded: a quiet,
                // non-interactive label rather than a dead CTA.
                noAdsRow {
                    Text(L("shopUnavailable"))
                        .typeStyle(TypeToken.bodySmall)
                        .foregroundStyle(DesignTokens.Color.muted)
                }
            }

            if !store.adsRemoved {
                Button { Task { await store.restore() } } label: {
                    Text(L("restore"))
                        .typeStyle(TypeToken.bodySmallStrong)
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
        .solidRaised(radius: DesignTokens.Radius.medium, border: 4, depth: 6)
        .alert(L("restoreToast"), isPresented: $store.restoreFoundNothing) {
            Button("OK", role: .cancel) {}
        }
        .alert(L("buyFailed"), isPresented: $store.purchaseFailed) {
            Button("OK", role: .cancel) {}
        }
    }

    /// The "Remove ads" title with a trailing control (price, owned badge or
    /// the unavailable label), shared by every state of the purchase row.
    private func noAdsRow<Trailing: View>(@ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(alignment: .center, spacing: 12) {
            Text(L("shopNoAds"))
                .typeStyle(TypeToken.purchaseTitle)
                .foregroundStyle(DesignTokens.Color.ink)
            Spacer(minLength: 0)
            trailing()
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
                    .typeStyle(TypeToken.ctaCompact)
                    .frame(maxWidth: .infinity, alignment: .leading)
                SwitchTrack(isOn: isOn.wrappedValue)
            }
            .foregroundStyle(DesignTokens.Color.ink)
            .padding(.horizontal, 16)
            .padding(.vertical, 15)
            .background(DesignTokens.Color.ivory)
            .solidRaised(radius: DesignTokens.Radius.medium, border: 4, depth: 6)
        }
        .buttonStyle(.plain)
    }

    private func linkRow(
        _ label: String,
        value: String,
        action: (() -> Void)? = nil
    ) -> some View {
        Button { action?() } label: {
            HStack(spacing: 12) {
                Text(label)
                    .typeStyle(TypeToken.rowValue)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(value)
                    .typeStyle(TypeToken.monoPlain)
                    .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.textQuiet))
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.row))
            .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: DesignTokens.Radius.row, style: .continuous)
                    .strokeBorder(Color.white.opacity(DesignTokens.Opacity.borderRow), lineWidth: 3)
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
                .fill(isOn ? DesignTokens.Color.green : DesignTokens.Color.ink.opacity(DesignTokens.Opacity.trackOff))
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

// MARK: - Offline

/// Screen 12: the game itself is fully offline, so this is the placeholder it
/// is described as (docs/specs/screens.md): a hatched mark, the copy, and a
/// retry that simply returns. Reachable from Settings.
struct OfflineView: View {
    let onRetry: () -> Void

    var body: some View {
        ZStack {
            DS.appBackground
            VStack(spacing: 22) {
                Spacer()
                DS.adHatch
                    .frame(width: 92, height: 92)
                    .clipShape(RoundedRectangle(cornerRadius: DesignTokens.Radius.large, style: .continuous))
                    .inkOutlined(RoundedRectangle(cornerRadius: DesignTokens.Radius.large, style: .continuous))

                VStack(spacing: 12) {
                    Text(L("offT"))
                        .typeStyle(TypeToken.offlineTitle)
                        .foregroundStyle(DesignTokens.Color.ivory)
                        .multilineTextAlignment(.center)
                    Text(L("offB"))
                        .typeStyle(TypeToken.bodySoft)
                        .foregroundStyle(DesignTokens.Color.ivory.opacity(DesignTokens.Opacity.textMuted))
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }

                Spacer()

                Button(action: onRetry) {
                    Text(L("retry"))
                        .typeStyle(TypeToken.ctaMedium)
                        .foregroundStyle(DesignTokens.Color.ink)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 17)
                        .background(DesignTokens.Color.yellow)
                        .solidRaised(radius: DesignTokens.Radius.button, depth: 8)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, DesignTokens.Space.gutter)
            .padding(.vertical, DesignTokens.Space.gutter)
            .frame(maxWidth: DesignTokens.Layout.columnMax)
            .frame(maxWidth: .infinity)
        }
    }
}
