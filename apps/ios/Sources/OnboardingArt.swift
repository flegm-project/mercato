import SwiftUI

/// The intro's three pictures, drawn rather than photographed.
///
/// One ball crosses each pane and leaves a yellow trail behind it: a pass from
/// one club to the next, a choice landing on the right answer, a climb that
/// bursts into stars. The three PNGs this replaced said the same three things
/// with grey bars standing in for text, which read as an unfinished screen
/// rather than as a picture of the game.
///
/// Vector and looping, so it costs no download bytes, stays sharp at any
/// density, and can show the pass instead of the moment after it.
///
/// Every coordinate, radius and timing below comes from `OnboardingScene`,
/// generated from design/onboarding.json. Android draws the same scene from
/// the same file with its own primitives, which is the only thing keeping the
/// two intros the same intro.
struct OnboardingArtView: View {
    /// Which pane of the intro, 0 to 2.
    let pane: Int

    var body: some View {
        if let pinned = Self.pinnedPhase {
            Canvas { context, size in
                Self.draw(&context, size: size, pane: scene, phase: pinned)
            }
        } else {
            TimelineView(.animation) { timeline in
                Canvas { context, size in
                    Self.draw(&context, size: size, pane: scene, phase: Self.phase(at: timeline.date))
                }
            }
        }
    }

    /// Held still for the parity captures, and only for them.
    ///
    /// scripts/capture-parity.sh shoots a screen once two consecutive frames
    /// are identical, which an animation never gives it: it would wait out its
    /// twenty tries and then compare one arbitrary moment of the loop against
    /// another. Pinning both platforms to the same instant of the same scene
    /// is what makes the screen measurable at all. 0.8 is the hold, after the
    /// accent has landed and before the fade, which is also the most complete
    /// frame of the three.
    private static var pinnedPhase: CGFloat? {
        #if DEBUG
        CommandLine.arguments.contains("-MercatoRoute") ? 0.8 : nil
        #else
        nil
        #endif
    }

    private var scene: OnboardingPane {
        OnboardingScene.panes[min(max(pane, 0), OnboardingScene.panes.count - 1)]
    }

    /// The phase is a function of the wall clock rather than of animated
    /// state: `TimelineView(.animation)` redraws on the display's own cadence,
    /// so nothing has to start, stop or be reset when the pager moves.
    private static func phase(at date: Date) -> CGFloat {
        let t = date.timeIntervalSinceReferenceDate
            .truncatingRemainder(dividingBy: OnboardingScene.duration)
        return CGFloat(t / OnboardingScene.duration)
    }
}

// MARK: - Timing

private extension OnboardingArtView {
    /// Smoothstep, clamped. Everything in this scene eases in and out of rest;
    /// nothing snaps, which is what "boucle courte et douce" has to mean at
    /// 2.8 seconds a turn.
    static func ease(_ t: CGFloat) -> CGFloat {
        let c = min(max(t, 0), 1)
        return c * c * (3 - 2 * c)
    }

    /// Overshoot on the way in, settling exactly on 1. Used for the things
    /// that arrive rather than travel: the badge that lights up, the stars.
    static func pop(_ t: CGFloat) -> CGFloat {
        let c = min(max(t, 0), 1) - 1
        return 1 + 2.7 * c * c * c + 1.7 * c * c
    }
}

// MARK: - The path the ball takes

private extension OnboardingArtView {
    /// The cubic flattened to a polyline, with the distance travelled at each
    /// sample.
    ///
    /// Flattening rather than asking each platform to trim a curve is
    /// deliberate: `Path.trimmedPath` and Compose's `PathMeasure` do not have
    /// to agree on where halfway is, and the ball has to sit exactly on the
    /// end of its own trail. One ruler, computed the same way twice.
    static func flatten(_ p: OnboardingPane) -> (points: [CGPoint], travelled: [CGFloat]) {
        var points: [CGPoint] = []
        for i in 0...OnboardingScene.samples {
            let t = CGFloat(i) / CGFloat(OnboardingScene.samples)
            let u = 1 - t
            let (a, b, c, d) = (u * u * u, 3 * u * u * t, 3 * u * t * t, t * t * t)
            points.append(CGPoint(
                x: a * p.from.x + b * p.c1.x + c * p.c2.x + d * p.to.x,
                y: a * p.from.y + b * p.c1.y + c * p.c2.y + d * p.to.y
            ))
        }
        var travelled: [CGFloat] = [0]
        for i in 1..<points.count {
            travelled.append(travelled[i - 1] + hypot(
                points[i].x - points[i - 1].x,
                points[i].y - points[i - 1].y
            ))
        }
        return (points, travelled)
    }

    /// The polyline up to `fraction` of its length, and where that leaves the
    /// ball. Returned together because they are the same walk.
    static func trail(_ p: OnboardingPane, upTo fraction: CGFloat) -> (path: Path, head: CGPoint) {
        let (points, travelled) = flatten(p)
        let target = travelled[travelled.count - 1] * min(max(fraction, 0), 1)
        var path = Path()
        path.move(to: points[0])
        var head = points[0]
        for i in 1..<points.count {
            if travelled[i] >= target {
                let span = travelled[i] - travelled[i - 1]
                let k = span > 0 ? (target - travelled[i - 1]) / span : 0
                head = CGPoint(
                    x: points[i - 1].x + (points[i].x - points[i - 1].x) * k,
                    y: points[i - 1].y + (points[i].y - points[i - 1].y) * k
                )
                path.addLine(to: head)
                break
            }
            path.addLine(to: points[i])
            head = points[i]
        }
        return (path, head)
    }
}

// MARK: - The app's surfaces, inside a Canvas

private extension OnboardingArtView {
    /// The flat fill, hard ink border and offset ink shadow every surface in
    /// this app wears. Built from three fills rather than a stroke: a stroke
    /// straddles its path, so a 5 border would put 2.5 of it outside the
    /// shape and the badges would not line up with the app's own cards.
    static func raised(_ context: GraphicsContext, _ shape: Path, _ inset: Path, fill: Color) {
        context.fill(shape.offsetBy(dx: OnboardingScene.depth, dy: OnboardingScene.depth),
                     with: .color(DesignTokens.Color.ink))
        context.fill(shape, with: .color(DesignTokens.Color.ink))
        context.fill(inset, with: .color(fill))
    }

    static func disc(_ c: CGPoint, _ r: CGFloat) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2))
    }

    static func raisedDisc(_ context: GraphicsContext, at c: CGPoint, r: CGFloat, border: CGFloat, fill: Color) {
        raised(context, disc(c, r), disc(c, r - border), fill: fill)
    }

    static func raisedBlock(_ context: GraphicsContext, _ b: OnboardingBlock, fill: Color) {
        let e = OnboardingScene.border
        raised(
            context,
            Path(roundedRect: CGRect(x: b.x, y: b.y, width: b.w, height: b.h), cornerRadius: b.r, style: .continuous),
            Path(roundedRect: CGRect(x: b.x + e, y: b.y + e, width: b.w - e * 2, height: b.h - e * 2),
                 cornerRadius: b.r - e, style: .continuous),
            fill: fill
        )
    }

    /// The recap's star, at whatever size the scene asks for.
    ///
    /// This is the SF Pro glyph and not a path, because on iOS the glyph *is*
    /// the reference: `RecapStar` on Android is a reproduction of it, measured
    /// off this exact face. Drawing a second star here would give the app two
    /// stars that are nearly the same.
    ///
    /// `r` is the sharp pentagram radius, the same number Android's
    /// `starPath` takes, so the two scenes agree on size. The glyph's ink sits
    /// 0.0348em above the centre of its cell (Components.kt records the
    /// measurement), which is the only reason this is not a plain draw at the
    /// centre point.
    static func star(_ context: GraphicsContext, at c: CGPoint, r: CGFloat, fill: Color, shade: Color) {
        let em = r / 0.524
        var text = context.resolve(Text("\u{2605}").font(.system(size: em)))
        let at = CGPoint(x: c.x, y: c.y + em * 0.0348)
        let d = OnboardingScene.depth
        text.shading = .color(shade)
        context.draw(text, at: CGPoint(x: at.x + d, y: at.y + d), anchor: .center)
        text.shading = .color(fill)
        context.draw(text, at: at, anchor: .center)
    }
}

// MARK: - The scene

private extension OnboardingArtView {
    static func draw(_ context: inout GraphicsContext, size: CGSize, pane: OnboardingPane, phase: CGFloat) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(DesignTokens.Color.blueDeep))

        // Scale to fill and centre. The slot is 200 tall and between 370 and
        // 408 wide, so fitting would letterbox the art inside its own card;
        // filling crops the side margin instead, which is why nothing is
        // allowed within 24 of the edge.
        let s = max(size.width / OnboardingScene.width, size.height / OnboardingScene.height)
        context.translateBy(
            x: (size.width - OnboardingScene.width * s) / 2,
            y: (size.height - OnboardingScene.height * s) / 2
        )
        context.scaleBy(x: s, y: s)

        let travel = ease(phase / OnboardingScene.travelEnd)
        let land = (phase - OnboardingScene.travelEnd)
            / (OnboardingScene.accentEnd - OnboardingScene.travelEnd)
        // The trail and the ball fade out over the last stretch so the loop
        // restarts on an empty frame rather than cutting back to the start.
        let alpha = 1 - ease((phase - OnboardingScene.holdEnd) / (1 - OnboardingScene.holdEnd))

        let (path, head) = trail(pane, upTo: travel)

        drawFurniture(context, pane: pane, land: land, alpha: alpha)

        var fading = context
        fading.opacity = alpha
        drawTrail(fading, path)
        if pane.stars.isEmpty {
            drawBall(fading, at: head, scale: 1)
        } else {
            // The climb does not stop at the top, it becomes the stars.
            drawBall(fading, at: head, scale: 1 - ease(land / 0.4))
        }
    }

    static func drawTrail(_ context: GraphicsContext, _ path: Path) {
        let stroke = StrokeStyle(lineWidth: OnboardingScene.trail, lineCap: .round, lineJoin: .round)
        context.stroke(
            path.offsetBy(dx: OnboardingScene.depth, dy: OnboardingScene.depth),
            with: .color(DesignTokens.Color.ink), style: stroke
        )
        context.stroke(path, with: .color(DesignTokens.Color.yellow), style: stroke)
    }

    static func drawBall(_ context: GraphicsContext, at c: CGPoint, scale: CGFloat) {
        guard scale > 0.01 else { return }
        raisedDisc(context, at: c, r: OnboardingScene.ballRadius * scale,
                   border: OnboardingScene.ballBorder * scale, fill: DesignTokens.Color.ivory)
    }
}

// MARK: - What each pane is made of

private extension OnboardingArtView {
    /// Everything that is already in frame when the ball sets off, plus what
    /// the arrival does to it.
    ///
    /// `land` runs 0 to 1 across the accent stage and is left unclamped here:
    /// the easings clamp it themselves, and passing the raw value is what lets
    /// the accent be an overshoot rather than a fade.
    static func drawFurniture(_ context: GraphicsContext, pane: OnboardingPane, land: CGFloat, alpha: CGFloat) {
        var lit = context
        lit.opacity = ease(land) * alpha

        // "Deux clubs. Une année." Two badges; the ball leaves one and the
        // other lights up. "Quatre réponses." Four rows; the ball settles on
        // one and it turns green. The same shape and the same accent either
        // way, which is why there is one loop rather than two.
        let accent = pane.accentColor == "green"
            ? DesignTokens.Color.green
            : DesignTokens.Color.yellow
        for (i, block) in pane.blocks.enumerated() {
            raisedBlock(context, block, fill: DesignTokens.Color.clubGrey)
            guard i == pane.accent else { continue }
            raisedBlock(lit, block, fill: accent)
        }

        // "Trois étoiles." The three slots sit there dim, exactly as the recap
        // shows them, and fill in from the one the ball climbed to outwards.
        for (i, slot) in pane.stars.enumerated() {
            let c = CGPoint(x: slot.cx, y: slot.cy)
            star(context, at: c, r: slot.r,
                 fill: .white.opacity(DesignTokens.Opacity.starOff),
                 shade: DesignTokens.Color.ink.opacity(DesignTokens.Opacity.starOff))
            // Staggered by rank in `order`, so the burst starts at the star
            // the trail arrives at instead of reading left to right.
            let rank = CGFloat(pane.order.firstIndex(of: i) ?? 0)
            let grow = pop((land - rank * 0.17) / 0.66)
            guard grow > 0.01 else { continue }
            var arriving = context
            arriving.opacity = alpha
            star(arriving, at: c, r: slot.r * grow,
                 fill: DesignTokens.Color.yellow, shade: DesignTokens.Color.ink)
        }
    }
}
