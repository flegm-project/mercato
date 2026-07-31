import SwiftUI

/// The Mercato design system, ported from the source design
/// (reference/design-source/Mercato.dc.html) and docs/specs/components.md.
/// Numbers come from that design or from design/tokens.json, never invented
/// here, so a component can be diffed against its origin.
/// The generated token namespace is `DesignTokens.Type`, and `Type` in
/// expression position is Swift's metatype keyword, so every use would need
/// back-ticks. Aliased once here instead.
typealias TypeToken = DesignTokens.`Type`

enum DS {
    // MARK: - Type
    //
    // Unbounded carries the display voice, Figtree the UI voice, IBM Plex Mono
    // the technical labels. Upstream ships variable fonts, which iOS cannot
    // pick a weight from reliably, so design/fonts holds static cuts and each
    // weight is its own family (see design/fonts/README.md).

    static func unbounded(_ size: CGFloat, weight: Int = 900) -> Font {
        .custom(weight >= 900 ? "Unbounded-Black" : "Unbounded-ExtraBold", fixedSize: size)
    }

    static func figtree(_ size: CGFloat, weight: Int = 700) -> Font {
        let name: String
        switch weight {
        case ..<600: name = "Figtree-Medium"
        case ..<700: name = "Figtree-SemiBold"
        case ..<800: name = "Figtree-Bold"
        case ..<900: name = "Figtree-ExtraBold"
        default: name = "Figtree-Black"
        }
        return .custom(name, fixedSize: size)
    }

    static func mono(_ size: CGFloat) -> Font {
        .custom("IBMPlexMono-Medium", fixedSize: size)
    }

    // MARK: - Type tokens

    /// Resolve a generated type token to a Font.
    ///
    /// Prefer `typeStyle(_:)` wherever a View modifier will do: it applies the
    /// tracking as well. This exists for the call sites that take a `Font`
    /// as a parameter rather than as a modifier.
    static func font(_ style: DesignTokens.TypeStyle) -> Font {
        switch style.font {
        case "display": return unbounded(style.size, weight: style.weight)
        case "mono": return mono(style.size)
        default: return figtree(style.size, weight: style.weight)
        }
    }

    /// Token tracking at a size other than the token's own, for the one view
    /// that scales: the wordmark shrinks to fit its column.
    static func tracking(_ style: DesignTokens.TypeStyle, at size: CGFloat) -> CGFloat {
        guard style.size > 0 else { return 0 }
        return tracking(style) / style.size * size
    }

    /// Token tracking converted to points, the unit `.tracking()` expects.
    /// tokens.json states it in em, as CSS does.
    static func tracking(_ style: DesignTokens.TypeStyle) -> CGFloat {
        guard let raw = style.tracking,
              let em = Double(raw.replacingOccurrences(of: "em", with: ""))
        else { return 0 }
        return CGFloat(em) * style.size
    }

    // MARK: - Surfaces

    /// gradient.app-background: radial-gradient(130% 85% at 50% 0%, ...)
    static var appBackground: some View {
        GeometryReader { geo in
            RadialGradient(
                stops: [
                    .init(color: DesignTokens.Color.blueTop, location: 0),
                    .init(color: DesignTokens.Color.blue, location: 0.44),
                    .init(color: DesignTokens.Color.blueDeep, location: 1),
                ],
                center: .top,
                startRadius: 0,
                endRadius: geo.size.width * 1.3
            )
            .ignoresSafeArea()
        }
    }

    /// The 45-degree hatch behind an ad slot, so a placeholder reads as part of
    /// the game rather than a broken image. Stripes are 11pt on a 22pt period,
    /// matching gradient.ad-hatch.
    static var adHatch: some View {
        Canvas { context, size in
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DesignTokens.Color.blueNight))
            let period: CGFloat = 22
            var x = -size.height
            while x < size.width + size.height {
                var stripe = Path()
                stripe.move(to: CGPoint(x: x, y: size.height))
                stripe.addLine(to: CGPoint(x: x + size.height, y: 0))
                stripe.addLine(to: CGPoint(x: x + size.height + 11, y: 0))
                stripe.addLine(to: CGPoint(x: x + 11, y: size.height))
                stripe.closeSubpath()
                context.fill(stripe, with: .color(Color(red: 0.07, green: 0.13, blue: 0.48)))
                x += period
            }
        }
    }
}

/// Ink outline plus a solid (unblurred) drop shadow: the signature of every
/// raised surface here. The token shadows are pure offsets, so they are drawn
/// as an offset copy of the shape rather than with SwiftUI's blurred shadow.
///
/// The outline is a filled shape sitting *behind* inset content, not a stroke
/// drawn on top of a clipped background. Stroking over a clipped fill leaves a
/// hairline of that fill visible outside the stroke, which reads as a pale
/// fringe around every surface against this dark background.
struct SolidRaised<S: InsettableShape>: ViewModifier {
    let shape: S
    var border: CGFloat
    var depth: CGFloat
    var outline: Color = DesignTokens.Color.ink
    /// The card also carries an ambient shadow to lift it off the background.
    var ambient: Bool = false
    var pressed: Bool = false

    func body(content: Content) -> some View {
        content
            // The content is clipped to the shape pulled in by the border
            // width, and a solid fill of the full shape sits behind it: the
            // ring left over IS the border. Nothing clipped ever reaches the
            // outer edge, so the outermost pixel is always opaque outline.
            // Stroking on top of a full-size clip instead leaves that edge
            // pixel partly fill and partly stroke, and neither hides the
            // other: a pale seam traced the corners.
            .clipShape(shape.inset(by: border))
            .background(shape.fill(outline))
            .background(
                // Pressing sinks the surface onto its shadow, which is what
                // motion.press (translateY 5px) describes.
                shape.fill(outline).offset(y: pressed ? max(0, depth - 5) : depth)
            )
            .background(
                ambient
                    ? shape
                        .fill(Color(red: 0.016, green: 0.031, blue: 0.196).opacity(DesignTokens.Opacity.splashTrack))
                        .blur(radius: 22)
                        .offset(y: 22)
                    : nil
            )
            .offset(y: pressed ? 5 : 0)
    }
}

extension View {
    /// Rounded-rectangle surfaces (cards, answer buttons).
    func solidRaised(
        radius: CGFloat,
        border: CGFloat = 5,
        depth: CGFloat,
        outline: Color = DesignTokens.Color.ink,
        ambient: Bool = false,
        pressed: Bool = false
    ) -> some View {
        modifier(SolidRaised(
            shape: RoundedRectangle(cornerRadius: radius, style: .continuous),
            border: border,
            depth: depth,
            outline: outline,
            ambient: ambient,
            pressed: pressed
        ))
    }

    /// Pill surfaces (the score readout).
    func solidRaisedCapsule(
        border: CGFloat = 4,
        depth: CGFloat,
        outline: Color = DesignTokens.Color.ink,
        pressed: Bool = false
    ) -> some View {
        modifier(SolidRaised(shape: Capsule(), border: border, depth: depth, outline: outline, pressed: pressed))
    }

    /// Outlined but not raised (the close button, the sponsor board).
    func inkOutlined<S: InsettableShape>(_ shape: S, border: CGFloat = 4) -> some View {
        clipShape(shape.inset(by: border))
            .background(shape.fill(DesignTokens.Color.ink))
    }
}

/// Closes the round. Sits at the head of the top bar.
struct CloseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text("\u{2715}")
                .font(.system(size: 16, weight: .black))
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .background(DesignTokens.Color.ink.opacity(DesignTokens.Opacity.control))
                .inkOutlined(RoundedRectangle(cornerRadius: DesignTokens.Radius.control, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Quit")
    }
}

/// One pip per question, filling the width between the close button and the
/// score. Green correct, coral wrong, yellow live, ink 45% still to come.
struct ProgressPips: View {
    enum State { case correct, wrong, live, pending }
    let states: [State]

    var body: some View {
        HStack(spacing: 5) {
            ForEach(Array(states.enumerated()), id: \.offset) { _, state in
                Capsule()
                    .fill(color(state))
                    .overlay(Capsule().strokeBorder(DesignTokens.Color.ink, lineWidth: DesignTokens.Border.hairline))
                    .frame(height: 9)
            }
        }
    }

    private func color(_ state: State) -> Color {
        switch state {
        case .correct: return DesignTokens.Color.green
        case .wrong: return DesignTokens.Color.coral
        case .live: return DesignTokens.Color.yellow
        case .pending: return DesignTokens.Color.ink.opacity(DesignTokens.Opacity.control)
        }
    }
}

/// Lives, Hardcore only.
struct Hearts: View {
    let remaining: Int
    let total: Int

    var body: some View {
        HStack(spacing: 5) {
            ForEach(0..<total, id: \.self) { index in
                Circle()
                    .fill(index < remaining ? DesignTokens.Color.coral : DesignTokens.Color.ink.opacity(DesignTokens.Opacity.control))
                    .overlay(Circle().strokeBorder(DesignTokens.Color.ink, lineWidth: DesignTokens.Border.hairline))
                    .frame(width: 13, height: 13)
            }
        }
    }
}

/// Score readout: an ivory pill that takes the colour of the last verdict, with
/// the gain flying up from underneath it.
struct ScorePill: View {
    let points: Int64
    /// nil while the question is open, then the verdict.
    let verdict: Bool?
    /// Drives the fly-up; changes on every settled answer.
    let bumpToken: Int

    @State private var flying = false

    var body: some View {
        ZStack(alignment: .top) {
            Text("\(points)")
                .typeStyle(TypeToken.scorePill)
                .monospacedDigit()
                .foregroundStyle(foreground)
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
                .background(background)
                .solidRaisedCapsule(depth: 6)

            if let verdict {
                Text(verdict ? "+3" : "0")
                    .typeStyle(TypeToken.answerReveal)
                    .foregroundStyle(verdict ? DesignTokens.Color.green : DesignTokens.Color.coral)
                    .shadow(color: DesignTokens.Color.ink, radius: 0, x: 3, y: 3)
                    .offset(y: flying ? -4 : 40)
                    .opacity(flying ? 0 : 1)
                    .allowsHitTesting(false)
                    .onAppear {
                        flying = false
                        // motion.score-bump's 1500ms, but easeOut rather than
                        // its cubic-bezier(.2,.9,.3,1): that curve is 95% done
                        // by 30% of the duration, which fades the value out
                        // before it has travelled far enough to read.
                        withAnimation(.easeOut(duration: 1.5)) { flying = true }
                    }
            }
        }
        .id(bumpToken)
        .fixedSize()
    }

    private var background: Color {
        switch verdict {
        case .some(true): return DesignTokens.Color.green
        case .some(false): return DesignTokens.Color.coral
        case .none: return DesignTokens.Color.ivory
        }
    }

    private var foreground: Color {
        verdict == false ? .white : DesignTokens.Color.ink
    }
}

/// An answer option: left-aligned display type on blue-night, turning green
/// when it is the answer and coral when it was the wrong pick. Options left
/// untouched fade so the eye goes to the answer.
struct AnswerButton: View {
    enum Verdict { case idle, correct, wrongPick, unpicked }

    let title: String
    let verdict: Verdict
    let action: () -> Void

    @GestureState private var pressed = false

    var body: some View {
        Text(title)
            // Same metrics as the mode cards on Home, so an answer reads as
            // the same kind of object and the four of them fill the column.
            .typeStyle(TypeToken.screenTitle)
            .foregroundStyle(verdict == .correct ? DesignTokens.Color.ink : .white)
            .lineLimit(2)
            .minimumScaleFactor(0.5)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 22)
            .padding(.horizontal, 20)
            .background(background)
            .solidRaised(radius: DesignTokens.Radius.card, depth: 10, pressed: pressed)
            .opacity(verdict == .unpicked ? 0.4 : 1)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .updating($pressed) { _, state, _ in if verdict == .idle { state = true } }
                    .onEnded { _ in if verdict == .idle { action() } }
            )
    }

    private var background: Color {
        switch verdict {
        case .correct: return DesignTokens.Color.green
        case .wrongPick: return DesignTokens.Color.coral
        case .idle, .unpicked: return DesignTokens.Color.blueNight
        }
    }
}

/// The transfer card: an ink header with the move kind and the year, over an
/// ivory body holding the two clubs. The answer is never shown here.
struct TransferCard: View {
    let kindLabel: String
    let isLoan: Bool
    let year: Int32
    let fromClub: String
    let toClub: String
    /// nil while the question is open.
    let verdict: Bool?
    /// Hardcore only: the answer as dots, shown while the question is open.
    var maskedName: String? = nil

    var body: some View {
        VStack(spacing: 0) {
            header
            content
        }
        // No card-wide background: the header paints ink and the body paints
        // ivory, each only where it belongs. A full-bounds ivory fill would sit
        // behind the ink header too, and its antialiased clip edge then traced
        // a pale arc through the top corners where no ivory should exist.
        .solidRaised(radius: DesignTokens.Radius.card, depth: DesignTokens.Depth.card, outline: edge, ambient: true)
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 12) {
            Text(kindLabel)
                .typeStyle(TypeToken.cardKind)
                .foregroundStyle(DesignTokens.Color.ink)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                // A loan is called out with a yellow chip.
                .background(isLoan ? DesignTokens.Color.yellow : DesignTokens.Color.ivory)
                .clipShape(Capsule())
            Spacer(minLength: 0)
            Text(String(year))
                .typeStyle(TypeToken.year)
                .monospacedDigit()
                .foregroundStyle(DesignTokens.Color.yellow)
        }
        .padding(.horizontal, 18)
        .padding(.top, 10)
        .padding(.bottom, 10)
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ink)
    }

    private var content: some View {
        VStack(spacing: 0) {
            Text(fromClub)
                .typeStyle(TypeToken.clubFrom)
                .foregroundStyle(DesignTokens.Color.clubGrey)
                .multilineTextAlignment(.center)
                .lineLimit(2)
                .minimumScaleFactor(0.5)

            Text("\u{25BC}")
                .font(.system(size: 13))
                .foregroundStyle(arrowColor)
                .padding(.top, 6)
                .padding(.bottom, 8)

            Text(toClub)
                .typeStyle(TypeToken.screenTitle)
                .foregroundStyle(DesignTokens.Color.ink)
                .multilineTextAlignment(.center)
                // Capping the lines makes a long name scale down rather than
                // wrap mid-word, but keep the floor high so it stays prominent.
                .lineLimit(2)
                .minimumScaleFactor(0.6)

            if let maskedName, verdict == nil, !maskedName.isEmpty {
                Text(maskedName)
                    .typeStyle(TypeToken.maskedName)
                    .foregroundStyle(Color(red: 0.84, green: 0.83, blue: 0.77))
                    .lineLimit(2)
                    .minimumScaleFactor(0.5)
                    .padding(.top, 10)
            }

            // The correct answer is intentionally never revealed inside the
            // card: the masked name disappears once the question is settled and
            // nothing replaces it.
        }
        .padding(.horizontal, 18)
        .padding(.top, 14)
        .padding(.bottom, 16)
        .frame(maxWidth: .infinity)
        .background(DesignTokens.Color.ivory)
    }

    private var edge: Color {
        switch verdict {
        case .some(true): return DesignTokens.Color.greenDeep
        case .some(false): return DesignTokens.Color.coralDeep
        case .none: return DesignTokens.Color.ink
        }
    }

    private var arrowColor: Color {
        verdict == nil ? DesignTokens.Color.ink.opacity(DesignTokens.Opacity.arrowIdle) : edge
    }
}

/// Static sponsor board under the card. Never animates, never interactive, so
/// it cannot be mistaken for part of the question.
struct SponsorBoard: View {
    let label: String

    var body: some View {
        DS.adHatch
            .frame(height: 44)
            .overlay(
                Text(label)
                    .typeStyle(TypeToken.adLabel)
                    .foregroundStyle(Color.white.opacity(DesignTokens.Opacity.chip))
            )
            .inkOutlined(RoundedRectangle(cornerRadius: DesignTokens.Radius.adSlot, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Hardcore free-text entry. Turns green or coral once the question closes,
/// mirroring the answer buttons in Easy.
struct GuessField: View {
    @Binding var text: String
    let placeholder: String
    /// nil while the question is open.
    let verdict: Bool?
    let onSubmit: () -> Void

    var body: some View {
        // The placeholder is stated, not left to SwiftUI: its default is the
        // system placeholder grey at 30%, which on this ivory field at 30pt
        // was barely there. Android has always drawn it in `muted`.
        TextField(
            "",
            text: $text,
            prompt: Text(placeholder).foregroundStyle(DesignTokens.Color.muted)
        )
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(.go)
            .onSubmit(onSubmit)
            .typeStyle(TypeToken.screenTitle)
            .foregroundStyle(verdict == false ? .white : DesignTokens.Color.ink)
            .minimumScaleFactor(0.5)
            .padding(.vertical, 22)
            .padding(.horizontal, 20)
            // Without this the padding around the field swallows taps and the
            // field never becomes first responder.
            .contentShape(Rectangle())
            .background(background)
            .solidRaised(radius: DesignTokens.Radius.card, depth: DesignTokens.Depth.field)
            .disabled(verdict != nil)
    }

    private var background: Color {
        switch verdict {
        case .some(true): return DesignTokens.Color.green
        case .some(false): return DesignTokens.Color.coral
        case .none: return DesignTokens.Color.ivory
        }
    }
}

/// A revealed hint. Hints are free gameplay here: there is no shop.
struct HintChip: View {
    let text: String

    var body: some View {
        Text(text)
            .typeStyle(TypeToken.hintChip)
            .foregroundStyle(DesignTokens.Color.ink)
            .padding(.horizontal, 15)
            .padding(.vertical, 7)
            .background(DesignTokens.Color.ivory)
            .inkOutlined(Capsule())
    }
}

/// The two controls under the guess field. The hint takes 38 percent of the
/// width and the submit the rest, as in the source design.
struct HardcoreControls: View {
    let hintLabel: String
    let hintEnabled: Bool
    let submitLabel: String
    let submitEnabled: Bool
    let onHint: () -> Void
    let onSubmit: () -> Void

    private let spacing: CGFloat = 11
    private let hintShare: CGFloat = 0.38

    var body: some View {
        GeometryReader { geo in
            let hintWidth = max(0, (geo.size.width - spacing) * hintShare)
            HStack(spacing: spacing) {
                control(
                    title: hintLabel,
                    font: DS.font(TypeToken.toastTitle),
                    tracking: -0.03 * 20,
                    fill: DesignTokens.Color.ivory,
                    enabled: hintEnabled,
                    action: onHint
                )
                .frame(width: hintWidth)

                control(
                    title: submitLabel,
                    font: DS.font(TypeToken.cardTitle),
                    tracking: -0.03 * 24,
                    fill: DesignTokens.Color.yellow,
                    enabled: submitEnabled,
                    action: onSubmit
                )
                .frame(maxWidth: .infinity)
            }
        }
        // GeometryReader has no intrinsic height, so the row is given the one
        // the controls actually need: 22pt padding either side of the label.
        .frame(height: 22 * 2 + 34)
    }

    private func control(
        title: String,
        font: Font,
        tracking: CGFloat,
        fill: Color,
        enabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Text(title)
                .font(font)
                .tracking(tracking)
                .foregroundStyle(DesignTokens.Color.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 22)
                .padding(.horizontal, 12)
                .background(fill)
                .solidRaised(radius: DesignTokens.Radius.card, depth: 10)
                .opacity(enabled ? 1 : 0.4)
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
    }
}

/// Lays children out in a row and wraps to the next line when they no longer
/// fit. Used for the hint chips, which reach three and can exceed the column.
struct FlowRow: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0, x + size.width > maxWidth {
                x = 0
                y += lineHeight + spacing
                lineHeight = 0
            }
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
        return CGSize(width: maxWidth, height: y + lineHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, lineHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX, x + size.width > bounds.maxX {
                x = bounds.minX
                y += lineHeight + spacing
                lineHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            lineHeight = max(lineHeight, size.height)
        }
    }
}

extension View {
    /// Apply a type token: font and tracking together.
    ///
    /// The counterpart of Android's `typeStyle()`. Both platforms now read the
    /// same nine properties from design/tokens.json instead of each restating
    /// them at every call site, which is where the two drifted apart.
    func typeStyle(_ style: DesignTokens.TypeStyle) -> some View {
        self.font(DS.font(style)).tracking(DS.tracking(style))
    }
}
