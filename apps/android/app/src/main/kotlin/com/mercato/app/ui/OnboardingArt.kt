package com.mercato.app.ui

import android.app.Activity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import com.mercato.app.BuildConfig
import com.mercato.art.OnboardingBlock
import com.mercato.art.OnboardingPane
import com.mercato.art.OnboardingScene
import com.mercato.design.DesignTokens
import kotlin.math.hypot
import kotlin.math.max

/**
 * The intro's three pictures, drawn rather than photographed.
 *
 * One ball crosses each pane and leaves a yellow trail behind it: a pass from
 * one club to the next, a choice landing on the right answer, a climb that
 * bursts into stars. The three PNGs this replaced said the same three things
 * with grey bars standing in for text, which read as an unfinished screen
 * rather than as a picture of the game.
 *
 * Vector and looping, so it costs no download bytes, stays sharp at any
 * density, and can show the pass instead of the moment after it.
 *
 * Every coordinate, radius and timing below comes from [OnboardingScene],
 * generated from design/onboarding.json. iOS draws the same scene from the
 * same file with its own primitives, which is the only thing keeping the two
 * intros the same intro.
 *
 * @param pane which pane of the intro, 0 to 2.
 */
@Composable
fun OnboardingArt(pane: Int, modifier: Modifier = Modifier) {
    val scene = OnboardingScene.panes[pane.coerceIn(0, OnboardingScene.panes.lastIndex)]
    // Held still for the parity captures, and only for them.
    //
    // scripts/capture-parity.sh shoots a screen once two consecutive frames are
    // identical, which an animation never gives it: it would wait out its
    // twenty tries and then compare one arbitrary moment of the loop against
    // another. Pinning both platforms to the same instant of the same scene is
    // what makes the screen measurable at all. 0.8 is the hold, after the
    // accent has landed and before the fade, which is also the most complete
    // frame of the three.
    val context = LocalContext.current
    val pinned = BuildConfig.DEBUG &&
        (context as? Activity)?.intent?.getStringExtra("route") != null
    if (pinned) {
        Canvas(modifier) { drawScene(scene, PINNED_PHASE) }
    } else {
        // Linear and restarting: the shape of the motion is in the easings
        // below, not in the animation curve, so that the two platforms only
        // have to agree on one number, the phase.
        val transition = rememberInfiniteTransition(label = "onboarding")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(OnboardingScene.DURATION_MS, easing = LinearEasing),
                RepeatMode.Restart,
            ),
            label = "phase",
        )
        Canvas(modifier) { drawScene(scene, phase) }
    }
}

private const val PINNED_PHASE = 0.8f

// --- Timing ----------------------------------------------------------------

/**
 * Smoothstep, clamped. Everything in this scene eases in and out of rest;
 * nothing snaps, which is what a soft 2.8 second loop has to mean.
 */
private fun ease(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/**
 * Overshoot on the way in, settling exactly on 1. Used for the things that
 * arrive rather than travel: the badge that lights up, the stars.
 */
private fun pop(t: Float): Float {
    val c = t.coerceIn(0f, 1f) - 1f
    return 1f + 2.7f * c * c * c + 1.7f * c * c
}

// --- The path the ball takes ------------------------------------------------

/**
 * The cubic flattened to a polyline, with the distance travelled at each
 * sample.
 *
 * Flattening rather than asking each platform to trim a curve is deliberate:
 * `PathMeasure` here and `Path.trimmedPath` on iOS do not have to agree on
 * where halfway is, and the ball has to sit exactly on the end of its own
 * trail. One ruler, computed the same way twice.
 */
private fun flatten(p: OnboardingPane): Pair<List<Offset>, List<Float>> {
    val points = (0..OnboardingScene.SAMPLES).map { i ->
        val t = i.toFloat() / OnboardingScene.SAMPLES
        val u = 1f - t
        val a = u * u * u
        val b = 3f * u * u * t
        val c = 3f * u * t * t
        val d = t * t * t
        Offset(
            a * p.from.x + b * p.c1.x + c * p.c2.x + d * p.to.x,
            a * p.from.y + b * p.c1.y + c * p.c2.y + d * p.to.y,
        )
    }
    val travelled = ArrayList<Float>(points.size).apply { add(0f) }
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        travelled.add(travelled[i - 1] + hypot(points[i].x - prev.x, points[i].y - prev.y))
    }
    return points to travelled
}

/**
 * The polyline up to [fraction] of its length, and where that leaves the ball.
 * Returned together because they are the same walk.
 */
private fun trail(p: OnboardingPane, fraction: Float): Pair<Path, Offset> {
    val (points, travelled) = flatten(p)
    val target = travelled.last() * fraction.coerceIn(0f, 1f)
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    var head = points[0]
    for (i in 1 until points.size) {
        if (travelled[i] >= target) {
            val span = travelled[i] - travelled[i - 1]
            val k = if (span > 0f) (target - travelled[i - 1]) / span else 0f
            head = Offset(
                points[i - 1].x + (points[i].x - points[i - 1].x) * k,
                points[i - 1].y + (points[i].y - points[i - 1].y) * k,
            )
            path.lineTo(head.x, head.y)
            break
        }
        path.lineTo(points[i].x, points[i].y)
        head = points[i]
    }
    return path to head
}

// --- The app's surfaces -----------------------------------------------------

/**
 * The flat fill, hard ink border and offset ink shadow every surface in this
 * app wears. Built from three fills rather than a stroke: a stroke straddles
 * its path, so a 5 border would put 2.5 of it outside the shape and the badges
 * would not line up with the app's own cards.
 *
 * [alpha] rather than a layer: SwiftUI can set an opacity on a whole
 * `GraphicsContext`, Compose cannot without saving a layer every frame, so the
 * fade is carried down to the draw calls instead.
 */
private fun DrawScope.raised(shape: Path, inset: Path, fill: Color, alpha: Float) {
    translate(OnboardingScene.DEPTH, OnboardingScene.DEPTH) {
        drawPath(shape, DesignTokens.Color.ink, alpha = alpha)
    }
    drawPath(shape, DesignTokens.Color.ink, alpha = alpha)
    drawPath(inset, fill, alpha = alpha)
}

private fun discPath(cx: Float, cy: Float, r: Float): Path =
    Path().apply { addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }

private fun DrawScope.raisedDisc(cx: Float, cy: Float, r: Float, border: Float, fill: Color, alpha: Float) {
    raised(discPath(cx, cy, r), discPath(cx, cy, r - border), fill, alpha)
}

private fun roundedPath(x: Float, y: Float, w: Float, h: Float, r: Float): Path =
    Path().apply { addRoundRect(RoundRect(Rect(x, y, x + w, y + h), CornerRadius(r, r))) }

private fun DrawScope.raisedBlock(b: OnboardingBlock, fill: Color, alpha: Float) {
    val e = OnboardingScene.BORDER
    raised(
        roundedPath(b.x, b.y, b.w, b.h, b.r),
        roundedPath(b.x + e, b.y + e, b.w - e * 2f, b.h - e * 2f, b.r - e),
        fill,
        alpha,
    )
}

/**
 * The recap's star, at whatever size the scene asks for: the same [starPath]
 * `RecapStar` draws, so the intro's stars and the recap's stars are one shape.
 * [r] is the sharp pentagram radius, which is also the number iOS derives its
 * point size from.
 */
private fun DrawScope.star(cx: Float, cy: Float, r: Float, fill: Color, shade: Color, alpha: Float) {
    val path = starPath(cx, cy, r)
    translate(OnboardingScene.DEPTH, OnboardingScene.DEPTH) { drawPath(path, shade, alpha = alpha) }
    drawPath(path, fill, alpha = alpha)
}

// --- The scene --------------------------------------------------------------

private fun DrawScope.drawScene(pane: OnboardingPane, phase: Float) {
    drawRect(DesignTokens.Color.blueDeep)

    // Scale to fill and centre. The slot is 200 tall and between 370 and 408
    // wide, so fitting would letterbox the art inside its own card; filling
    // crops the side margin instead, which is why nothing in the scene is
    // allowed within 24 of the edge.
    val s = max(size.width / OnboardingScene.WIDTH, size.height / OnboardingScene.HEIGHT)
    translate(
        (size.width - OnboardingScene.WIDTH * s) / 2f,
        (size.height - OnboardingScene.HEIGHT * s) / 2f,
    ) {
        scale(s, pivot = Offset.Zero) {
            val travel = ease(phase / OnboardingScene.TRAVEL_END)
            val land = (phase - OnboardingScene.TRAVEL_END) /
                (OnboardingScene.ACCENT_END - OnboardingScene.TRAVEL_END)
            // The trail and the ball fade out over the last stretch so the loop
            // restarts on an empty frame rather than cutting back to the start.
            val alpha = 1f - ease((phase - OnboardingScene.HOLD_END) / (1f - OnboardingScene.HOLD_END))

            val (path, head) = trail(pane, travel)
            drawFurniture(pane, land, alpha)
            drawTrail(path, alpha)
            // On the last pane the climb does not stop at the top, it becomes
            // the stars.
            val shrink = if (pane.stars.isEmpty()) 1f else 1f - ease(land / 0.4f)
            if (shrink > 0.01f) {
                raisedDisc(
                    head.x, head.y,
                    OnboardingScene.BALL_RADIUS * shrink,
                    OnboardingScene.BALL_BORDER * shrink,
                    DesignTokens.Color.ivory,
                    alpha,
                )
            }
        }
    }
}

private fun DrawScope.drawTrail(path: Path, alpha: Float) {
    val stroke = Stroke(OnboardingScene.TRAIL, cap = StrokeCap.Round, join = StrokeJoin.Round)
    translate(OnboardingScene.DEPTH, OnboardingScene.DEPTH) {
        drawPath(path, DesignTokens.Color.ink, alpha = alpha, style = stroke)
    }
    drawPath(path, DesignTokens.Color.yellow, alpha = alpha, style = stroke)
}

/**
 * Everything that is already in frame when the ball sets off, plus what the
 * arrival does to it.
 *
 * [land] runs 0 to 1 across the accent stage and is left unclamped: the
 * easings clamp it themselves, and passing the raw value is what lets the
 * accent be an overshoot rather than a fade.
 */
private fun DrawScope.drawFurniture(pane: OnboardingPane, land: Float, alpha: Float) {
    val lit = ease(land) * alpha

    // "Deux clubs. Une année." Two badges; the ball leaves one and the other
    // lights up. "Quatre réponses." Four rows; the ball settles on one and it
    // turns green. The same shape and the same accent either way, which is why
    // there is one loop rather than two.
    val accent =
        if (pane.accentColor == "green") DesignTokens.Color.green else DesignTokens.Color.yellow
    pane.blocks.forEachIndexed { i, block ->
        raisedBlock(block, DesignTokens.Color.clubGrey, alpha)
        if (i == pane.accent) raisedBlock(block, accent, lit)
    }

    // "Trois étoiles." The three slots sit there dim, exactly as the recap
    // shows them, and fill in from the one the ball climbed to outwards.
    pane.stars.forEachIndexed { i, slot ->
        star(
            slot.cx, slot.cy, slot.r,
            Color.White.dim(DesignTokens.Opacity.starOff),
            DesignTokens.Color.ink.dim(DesignTokens.Opacity.starOff),
            alpha,
        )
        // Staggered by rank in `order`, so the burst starts at the star the
        // trail arrives at instead of reading left to right.
        val rank = pane.order.indexOf(i).coerceAtLeast(0)
        val grow = pop((land - rank * 0.17f) / 0.66f)
        if (grow > 0.01f) {
            star(slot.cx, slot.cy, slot.r * grow, DesignTokens.Color.yellow, DesignTokens.Color.ink, alpha)
        }
    }
}
