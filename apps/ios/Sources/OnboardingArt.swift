import SwiftUI

/// The intro's three scenes, drawn rather than shipped.
///
/// Each one is a moment of the game held still and animated in place: the
/// years of the mercato waiting for a name, the two shirts with the answer
/// landing between them, the three stars at the end of a round. Nothing in
/// them is translatable, which is what lets them carry real content instead of
/// grey bars standing in for text.
///
/// Every coordinate, radius and timing comes from `OnboardingScene`, generated
/// from design/onboarding.json. Android plays the same keyframes from the same
/// file with its own primitives, and a browser page plays them a third time so
/// a change can be judged without two builds. None of the three owns a number.
struct OnboardingArtView: View {
    /// Which scene of the intro, 0 to 2.
    let pane: Int

    var body: some View {
        if let pinned = Self.pinnedPhase {
            Canvas { context, size in
                Self.draw(&context, size: size, pieces: pieces, phase: pinned)
            }
        } else {
            TimelineView(.animation) { timeline in
                Canvas { context, size in
                    Self.draw(&context, size: size, pieces: pieces, phase: Self.phase(at: timeline.date))
                }
            }
        }
    }

    private var pieces: [OnboardingPiece] {
        OnboardingScene.scenes[min(max(pane, 0), OnboardingScene.scenes.count - 1)]
    }

    /// The phase is a function of the wall clock rather than of animated
    /// state: `TimelineView(.animation)` redraws on the display's own cadence,
    /// so nothing has to start, stop or be reset when the pager moves.
    private static func phase(at date: Date) -> CGFloat {
        let t = date.timeIntervalSinceReferenceDate
            .truncatingRemainder(dividingBy: OnboardingScene.duration)
        return CGFloat(t / OnboardingScene.duration)
    }

    /// Held still for the parity captures, and only for them.
    ///
    /// scripts/capture-parity.sh shoots a screen once two consecutive frames
    /// are identical, which an animation never gives it: it would wait out its
    /// twenty tries and then compare one arbitrary moment of the loop against
    /// another. Pinning both platforms to the same instant is what makes the
    /// screen measurable at all. 0.78 is the one instant of the loop where
    /// every piece of all three scenes is at rest and at full strength.
    private static var pinnedPhase: CGFloat? {
        #if DEBUG
        CommandLine.arguments.contains("-MercatoRoute") ? 0.78 : nil
        #else
        nil
        #endif
    }
}

// MARK: - Playback

private extension OnboardingArtView {
    /// Cubic-bezier easing, solved for x then read for y, the way CSS does it.
    ///
    /// Newton on the x polynomial rather than a lookup table: the curves here
    /// overshoot, so a table fine enough to keep the overshoot smooth is
    /// larger than the eight iterations it replaces.
    static func bezier(_ x1: CGFloat, _ y1: CGFloat, _ x2: CGFloat, _ y2: CGFloat, _ t: CGFloat) -> CGFloat {
        let cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
        let cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
        var u = t
        for _ in 0..<8 {
            let x = ((ax * u + bx) * u + cx) * u - t
            let d = (3 * ax * u + 2 * bx) * u + cx
            if abs(x) < 1e-5 || d == 0 { break }
            u -= x / d
        }
        u = min(max(u, 0), 1)
        return ((ay * u + by) * u + cy) * u
    }

    /// The four curves the spec is allowed to name.
    static func ease(_ name: String, _ t: CGFloat) -> CGFloat {
        let c = min(max(t, 0), 1)
        switch name {
        case "inout": return bezier(0.42, 0, 0.58, 1, c)
        case "out": return bezier(0, 0, 0.58, 1, c)
        case "back": return bezier(0.3, 1.3, 0.4, 1, c)
        default: return c
        }
    }

    struct State {
        var dx: CGFloat = 0, dy: CGFloat = 0, dist: CGFloat = 0
        var scale: CGFloat = 1, rot: CGFloat = 0, opacity: CGFloat = 1, dash: CGFloat = 0
    }

    /// The piece's state at `phase`, its own delay already taken off.
    static func sample(_ piece: OnboardingPiece, _ phase: CGFloat) -> State {
        var t = (phase - piece.delay).truncatingRemainder(dividingBy: 1)
        if t < 0 { t += 1 }
        let keys = piece.keys
        var i = 0
        while i < keys.count - 2 && keys[i + 1].at <= t { i += 1 }
        let a = keys[i], b = keys[min(i + 1, keys.count - 1)]
        let span = b.at - a.at
        let k = span > 0 ? ease(b.ease, (t - a.at) / span) : 0
        let mix = { (x: CGFloat, y: CGFloat) in x + (y - x) * k }
        return State(
            dx: mix(a.dx, b.dx), dy: mix(a.dy, b.dy), dist: mix(a.dist, b.dist),
            scale: mix(a.scale, b.scale), rot: mix(a.rot, b.rot),
            opacity: mix(a.opacity, b.opacity), dash: mix(a.dash, b.dash)
        )
    }
}

// MARK: - Shapes

private extension OnboardingArtView {
    static func color(_ token: String) -> Color {
        switch token {
        case "ink": return DesignTokens.Color.ink
        case "yellow": return DesignTokens.Color.yellow
        case "ivory": return DesignTokens.Color.ivory
        case "club-grey": return DesignTokens.Color.clubGrey
        case "green": return DesignTokens.Color.green
        default: return DesignTokens.Color.blueDeep
        }
    }

    /// The app's rounded rectangle, with the four corners stated separately:
    /// a shirt is a card whose bottom is fully round.
    static func rect(_ r: CGRect, _ radii: [CGFloat]) -> Path {
        UnevenRoundedRectangle(
            topLeadingRadius: max(0, radii[0]),
            bottomLeadingRadius: max(0, radii[3]),
            bottomTrailingRadius: max(0, radii[2]),
            topTrailingRadius: max(0, radii[1]),
            style: .continuous
        ).path(in: r)
    }

    /// The recap's star, so the intro's stars and the recap's stars are one
    /// shape. Same construction as `RecapStar` on Android: the sharp pentagram
    /// with every corner replaced by a tangent arc.
    static func star(_ c: CGPoint, _ outer: CGFloat) -> Path {
        let waist: CGFloat = 0.392, tip: CGFloat = 0.0665, valley: CGFloat = 0.031
        let pts = (0..<10).map { i -> CGPoint in
            let r = i % 2 == 0 ? outer : outer * waist
            let a = CGFloat(-90 + i * 36) * .pi / 180
            return CGPoint(x: c.x + r * cos(a), y: c.y + r * sin(a))
        }
        var path = Path()
        for i in 0..<10 {
            let p = pts[i], prev = pts[(i + 9) % 10], next = pts[(i + 1) % 10]
            let len = { (a: CGPoint, b: CGPoint) in hypot(b.x - a.x, b.y - a.y) }
            let unit = { (a: CGPoint, b: CGPoint) -> CGPoint in
                let l = len(a, b); return CGPoint(x: (b.x - a.x) / l, y: (b.y - a.y) / l)
            }
            let u = unit(p, prev), v = unit(p, next)
            let half = acos(min(max(u.x * v.x + u.y * v.y, -1), 1)) / 2
            let corner = outer * (i % 2 == 0 ? tip : valley)
            let trim = min(corner / tan(half), min(len(p, prev), len(p, next)) / 2)
            let a = CGPoint(x: p.x + u.x * trim, y: p.y + u.y * trim)
            let b = CGPoint(x: p.x + v.x * trim, y: p.y + v.y * trim)
            let k = 4.0 / 3.0 * tan((.pi - 2 * half) / 4) * tan(half)
            if i == 0 { path.move(to: a) } else { path.addLine(to: a) }
            path.addCurve(
                to: b,
                control1: CGPoint(x: a.x + (p.x - a.x) * k, y: a.y + (p.y - a.y) * k),
                control2: CGPoint(x: b.x + (p.x - b.x) * k, y: b.y + (p.y - b.y) * k)
            )
        }
        path.closeSubpath()
        return path
    }
}

// MARK: - Drawing

private extension OnboardingArtView {
    static func draw(_ context: inout GraphicsContext, size: CGSize,
                     pieces: [OnboardingPiece], phase: CGFloat) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DesignTokens.Color.blueDeep))

        // The stage is the inside of the card, so the border the screen draws
        // around this view is taken off first. Then scale to fill and centre:
        // the stage is 190 tall and between 360 and 398 wide, so fitting would
        // letterbox the scene inside its own card, where filling crops the
        // side margin instead. Nothing in the scene comes near the sides.
        let b = OnboardingScene.border
        let stage = CGSize(width: size.width - b * 2, height: size.height - b * 2)
        let s = max(stage.width / OnboardingScene.width, stage.height / OnboardingScene.height)
        context.translateBy(
            x: b + (stage.width - OnboardingScene.width * s) / 2,
            y: b + (stage.height - OnboardingScene.height * s) / 2
        )
        context.scaleBy(x: s, y: s)

        for piece in pieces { drawPiece(context, piece, sample(piece, phase)) }
    }

    static func drawPiece(_ context: GraphicsContext, _ p: OnboardingPiece, _ st: State) {
        guard st.opacity > 0.001, st.scale > 0.001 else { return }
        let a = p.angle * .pi / 180
        let centre = CGPoint(
            x: p.x + p.w / 2 + st.dx + cos(a) * st.dist,
            y: p.y + p.h / 2 + st.dy + sin(a) * st.dist
        )

        var g = context
        g.opacity = st.opacity
        g.translateBy(x: centre.x, y: centre.y)
        g.rotate(by: .degrees(st.rot))
        g.scaleBy(x: st.scale, y: st.scale)

        let d = OnboardingScene.depth
        switch p.kind {
        case "text":
            guard let t = p.text else { return }
            let anchor: UnitPoint = t.align == "left" ? .leading : .center
            var resolved = context.resolve(
                Text(t.value).font(DS.unbounded(t.size)).tracking(t.tracking * t.size)
            )
            resolved.shading = .color(DesignTokens.Color.ink)
            g.draw(resolved, at: CGPoint(x: d, y: d), anchor: anchor)
            resolved.shading = .color(color(t.fill))
            g.draw(resolved, at: .zero, anchor: anchor)

        case "star":
            // The outline is stated in the piece's own unit box, so it
            // thickens with the star instead of staying a hairline on the big
            // one. 1.631 is the star's ink width over its sharp radius.
            let path = star(.zero, p.w / 1.631)
            g.fill(path, with: .color(color(p.fill)))
            g.stroke(path, with: .color(color(p.stroke)),
                     style: StrokeStyle(lineWidth: p.border * p.w / p.space, lineJoin: .round))

        case "polyline":
            let k = p.w / p.space
            var path = Path()
            for (i, q) in p.points.enumerated() {
                let pt = CGPoint(x: q.x * k - p.w / 2, y: q.y * k - p.h / 2)
                if i == 0 { path.move(to: pt) } else { path.addLine(to: pt) }
            }
            g.stroke(path, with: .color(color(p.stroke)), style: StrokeStyle(
                lineWidth: p.width * k, lineCap: .round, lineJoin: .round,
                dash: [p.length * k, p.length * k], dashPhase: st.dash * k
            ))

        default:
            let box = CGRect(x: -p.w / 2, y: -p.h / 2, width: p.w, height: p.h)
            if p.shadow {
                g.fill(rect(box.offsetBy(dx: d, dy: d), p.radii), with: .color(DesignTokens.Color.ink))
            }
            if p.border > 0 {
                // Three fills rather than a stroke: a stroke straddles its
                // path, so half a 5 border would sit outside the shape and the
                // pieces would not line up with the app's own cards.
                g.fill(rect(box, p.radii), with: .color(DesignTokens.Color.ink))
                g.fill(rect(box.insetBy(dx: p.border, dy: p.border), p.radii.map { $0 - p.border }),
                       with: .color(color(p.fill)))
            } else {
                g.fill(rect(box, p.radii), with: .color(color(p.fill)))
            }
            if let t = p.text {
                var resolved = context.resolve(Text(t.value).font(DS.unbounded(t.size)))
                resolved.shading = .color(color(t.fill))
                g.draw(resolved, at: .zero, anchor: .center)
            }
        }
    }
}
