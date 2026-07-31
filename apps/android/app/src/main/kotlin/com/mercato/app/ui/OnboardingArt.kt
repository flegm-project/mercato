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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import com.mercato.app.BuildConfig
import com.mercato.design.DesignTokens
import com.mercato.art.OnboardingPiece
import com.mercato.art.OnboardingScene
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The intro's three scenes, drawn rather than shipped.
 *
 * Each one is a moment of the game held still and animated in place: the years
 * of the mercato waiting for a name, the two shirts with the answer landing
 * between them, the three stars at the end of a round. Nothing in them is
 * translatable, which is what lets them carry real content instead of grey
 * bars standing in for text.
 *
 * Every coordinate, radius and timing comes from [OnboardingScene], generated
 * from design/onboarding.json. iOS plays the same keyframes from the same file
 * with its own primitives, and a browser page plays them a third time so a
 * change can be judged without two builds. None of the three owns a number.
 *
 * @param pane which scene of the intro, 0 to 2.
 */
@Composable
fun OnboardingArt(pane: Int, modifier: Modifier = Modifier) {
    val pieces = OnboardingScene.scenes[pane.coerceIn(0, OnboardingScene.scenes.lastIndex)]
    val measurer = rememberTextMeasurer()
    val display = LocalFonts.current.display
    val face = remember(display) { TextStyle(fontFamily = display, fontWeight = FontWeight.Black) }

    // Held still for the parity captures, and only for them.
    //
    // scripts/capture-parity.sh shoots a screen once two consecutive frames are
    // identical, which an animation never gives it: it would wait out its
    // twenty tries and then compare one arbitrary moment of the loop against
    // another. Pinning both platforms to the same instant is what makes the
    // screen measurable at all. 0.78 is the one instant of the loop where every
    // piece of all three scenes is at rest and at full strength.
    val context = LocalContext.current
    val pinned = BuildConfig.DEBUG &&
        (context as? Activity)?.intent?.getStringExtra("route") != null
    if (pinned) {
        Canvas(modifier) { drawScene(pieces, PINNED_PHASE, measurer, face) }
    } else {
        // Linear and restarting: the shape of the motion is in the keyframes,
        // not in the animation curve, so the two platforms only have to agree
        // on one number, the phase.
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
        Canvas(modifier) { drawScene(pieces, phase, measurer, face) }
    }
}

private const val PINNED_PHASE = 0.78f

// --- Playback ---------------------------------------------------------------

/**
 * Cubic-bezier easing, solved for x then read for y, the way CSS does it.
 *
 * Newton on the x polynomial rather than a lookup table: the curves here
 * overshoot, so a table fine enough to keep the overshoot smooth is larger
 * than the eight iterations it replaces.
 */
private fun bezier(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Float {
    val cx = 3f * x1; val bx = 3f * (x2 - x1) - cx; val ax = 1f - cx - bx
    val cy = 3f * y1; val by = 3f * (y2 - y1) - cy; val ay = 1f - cy - by
    var u = t
    repeat(8) {
        val x = ((ax * u + bx) * u + cx) * u - t
        val d = (3f * ax * u + 2f * bx) * u + cx
        if (abs(x) < 1e-5f || d == 0f) return@repeat
        u -= x / d
    }
    u = u.coerceIn(0f, 1f)
    return ((ay * u + by) * u + cy) * u
}

/** The four curves the spec is allowed to name. */
private fun ease(name: String, t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return when (name) {
        "inout" -> bezier(0.42f, 0f, 0.58f, 1f, c)
        "out" -> bezier(0f, 0f, 0.58f, 1f, c)
        "back" -> bezier(0.3f, 1.3f, 0.4f, 1f, c)
        else -> c
    }
}

private class State(
    val dx: Float, val dy: Float, val dist: Float,
    val scale: Float, val rot: Float, val opacity: Float, val dash: Float,
)

/** The piece's state at [phase], its own delay already taken off. */
private fun sample(piece: OnboardingPiece, phase: Float): State {
    var t = (phase - piece.delay) % 1f
    if (t < 0f) t += 1f
    val keys = piece.keys
    var i = 0
    while (i < keys.size - 2 && keys[i + 1].at <= t) i++
    val a = keys[i]
    val b = keys[min(i + 1, keys.size - 1)]
    val span = b.at - a.at
    val k = if (span > 0f) ease(b.ease, (t - a.at) / span) else 0f
    fun mix(x: Float, y: Float) = x + (y - x) * k
    return State(
        mix(a.dx, b.dx), mix(a.dy, b.dy), mix(a.dist, b.dist),
        mix(a.scale, b.scale), mix(a.rot, b.rot), mix(a.opacity, b.opacity), mix(a.dash, b.dash),
    )
}

// --- Shapes -----------------------------------------------------------------

private fun token(name: String): Color = when (name) {
    "ink" -> DesignTokens.Color.ink
    "yellow" -> DesignTokens.Color.yellow
    "ivory" -> DesignTokens.Color.ivory
    "club-grey" -> DesignTokens.Color.clubGrey
    "green" -> DesignTokens.Color.green
    else -> DesignTokens.Color.blueDeep
}

/**
 * The app's rounded rectangle, with the four corners stated separately: a
 * shirt is a card whose bottom is fully round.
 */
private fun rectPath(x: Float, y: Float, w: Float, h: Float, r: List<Float>): Path =
    Path().apply {
        addRoundRect(
            RoundRect(
                Rect(x, y, x + w, y + h),
                topLeft = CornerRadius(max(0f, r[0])),
                topRight = CornerRadius(max(0f, r[1])),
                bottomRight = CornerRadius(max(0f, r[2])),
                bottomLeft = CornerRadius(max(0f, r[3])),
            )
        )
    }

// --- Drawing ----------------------------------------------------------------

private fun DrawScope.drawScene(
    pieces: List<OnboardingPiece>,
    phase: Float,
    measurer: TextMeasurer,
    face: TextStyle,
) {
    drawRect(DesignTokens.Color.blueDeep)

    // The stage is the inside of the card, so the border the screen draws
    // around this view is taken off first. Then scale to fill and centre: the
    // stage is 190 tall and between 360 and 398 wide, so fitting would
    // letterbox the scene inside its own card, where filling crops the side
    // margin instead. Nothing in the scene comes near the sides.
    val b = OnboardingScene.BORDER
    val stageW = size.width - b * 2f
    val stageH = size.height - b * 2f
    val s = max(stageW / OnboardingScene.WIDTH, stageH / OnboardingScene.HEIGHT)
    translate(
        b + (stageW - OnboardingScene.WIDTH * s) / 2f,
        b + (stageH - OnboardingScene.HEIGHT * s) / 2f,
    ) {
        scale(s, pivot = Offset.Zero) {
            for (p in pieces) drawPiece(p, sample(p, phase), measurer, face)
        }
    }
}

private fun DrawScope.drawPiece(
    p: OnboardingPiece,
    st: State,
    measurer: TextMeasurer,
    face: TextStyle,
) {
    if (st.opacity <= 0.001f || st.scale <= 0.001f) return
    val a = p.angle * PI.toFloat() / 180f
    val cx = p.x + p.w / 2f + st.dx + cos(a) * st.dist
    val cy = p.y + p.h / 2f + st.dy + sin(a) * st.dist
    val d = OnboardingScene.DEPTH

    translate(cx, cy) {
        rotate(st.rot, pivot = Offset.Zero) {
            scale(st.scale, pivot = Offset.Zero) {
                when (p.kind) {
                    "text" -> {
                        val t = p.text!!
                        text(measurer, face, t.value, 0f, 0f, t.size, DesignTokens.Color.ink,
                             st.opacity, t.align, t.tracking, d)
                        text(measurer, face, t.value, 0f, 0f, t.size, token(t.fill),
                             st.opacity, t.align, t.tracking, 0f)
                    }
                    "star" -> {
                        // The outline is stated in the piece's own unit box, so
                        // it thickens with the star instead of staying a
                        // hairline on the big one. 1.631 is the star's ink
                        // width over its sharp radius.
                        val path = starPath(0f, 0f, p.w / 1.631f)
                        drawPath(path, token(p.fill), alpha = st.opacity)
                        drawPath(
                            path, token(p.stroke), alpha = st.opacity,
                            style = Stroke(p.border * p.w / p.space, join = StrokeJoin.Round),
                        )
                    }
                    "polyline" -> {
                        val k = p.w / p.space
                        val path = Path().apply {
                            p.points.forEachIndexed { i, q ->
                                val px = q.x * k - p.w / 2f
                                val py = q.y * k - p.h / 2f
                                if (i == 0) moveTo(px, py) else lineTo(px, py)
                            }
                        }
                        drawPath(
                            path, token(p.stroke), alpha = st.opacity,
                            style = Stroke(
                                width = p.width * k,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(p.length * k, p.length * k), st.dash * k,
                                ),
                            ),
                        )
                    }
                    else -> {
                        val x = -p.w / 2f
                        val y = -p.h / 2f
                        if (p.shadow) {
                            drawPath(rectPath(x + d, y + d, p.w, p.h, p.radii),
                                     DesignTokens.Color.ink, alpha = st.opacity)
                        }
                        if (p.border > 0f) {
                            // Three fills rather than a stroke: a stroke
                            // straddles its path, so half a 5 border would sit
                            // outside the shape and the pieces would not line
                            // up with the app's own cards.
                            drawPath(rectPath(x, y, p.w, p.h, p.radii),
                                     DesignTokens.Color.ink, alpha = st.opacity)
                            drawPath(
                                rectPath(x + p.border, y + p.border,
                                         p.w - p.border * 2f, p.h - p.border * 2f,
                                         p.radii.map { it - p.border }),
                                token(p.fill), alpha = st.opacity,
                            )
                        } else {
                            drawPath(rectPath(x, y, p.w, p.h, p.radii),
                                     token(p.fill), alpha = st.opacity)
                        }
                        p.text?.let {
                            text(measurer, face, it.value, 0f, 0f, it.size, token(it.fill),
                                 st.opacity, "center", 0f, 0f)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A word in the display face, anchored on its measured layout box.
 *
 * That box is the one SwiftUI's `anchor:` centres on too, so neither platform
 * has to reproduce the other's glyph metrics for the two to land together.
 */
private fun DrawScope.text(
    measurer: TextMeasurer,
    face: TextStyle,
    value: String,
    cx: Float,
    cy: Float,
    size: Float,
    color: Color,
    alpha: Float,
    align: String,
    tracking: Float,
    offset: Float,
) {
    val laid = measurer.measure(
        AnnotatedString(value),
        face.copy(
            color = color.copy(alpha = color.alpha * alpha),
            fontSize = TextUnit(size / density, TextUnitType.Sp),
            letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
        ),
    )
    val left = if (align == "left") cx else cx - laid.size.width / 2f
    drawText(laid, topLeft = Offset(left + offset, cy - laid.size.height / 2f + offset))
}
