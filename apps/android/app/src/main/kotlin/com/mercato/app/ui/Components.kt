package com.mercato.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.animation.core.animateDpAsState
import com.mercato.design.DesignTokens

/** Resolve a generated [DesignTokens.TypeStyle] into a Compose [TextStyle]. */
@Composable
fun typeStyle(ts: DesignTokens.TypeStyle, color: Color): TextStyle {
    val fonts = LocalFonts.current
    val family = when (ts.font) {
        "display" -> fonts.display
        "mono" -> fonts.mono
        else -> fonts.body
    }
    val tracking = ts.tracking?.removeSuffix("em")?.toFloatOrNull()
    return TextStyle(
        color = color,
        fontFamily = family,
        fontWeight = FontWeight(ts.weight),
        fontSize = ts.size,
        letterSpacing = tracking?.em ?: androidx.compose.ui.unit.TextUnit.Unspecified,
    )
}

/**
 * Same, but with the size and tracking stated explicitly.
 *
 * Overriding only `fontSize` on a token silently keeps that token's tracking,
 * which iOS does not do: it sets tracking per call site and often sets none.
 * Passing `tracking = null` reproduces "no tracking" rather than the token's.
 */
@Composable
fun typeStyle(
    ts: DesignTokens.TypeStyle,
    color: Color,
    size: TextUnit,
    tracking: Float?,
): TextStyle = typeStyle(ts, color).copy(
    fontSize = size,
    letterSpacing = tracking?.em ?: androidx.compose.ui.unit.TextUnit.Unspecified,
)

/**
 * The blue app background: a radial burst centred on the top edge, matching
 * iOS `DS.appBackground` and `gradient.app-background` in tokens.json.
 * A vertical gradient reads as flat horizontal bands instead.
 */
fun Modifier.appBackground(): Modifier = drawBehind {
    drawRect(
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to DesignTokens.Color.blueTop,
                0.44f to DesignTokens.Color.blue,
                1f to DesignTokens.Color.blueDeep,
            ),
            center = Offset(size.width / 2f, 0f),
            radius = size.width * 1.3f,
        )
    )
}

/** Diagonal ink hatch used behind every ad slot, per the ads spec. */
fun Modifier.adHatch(): Modifier = drawBehind {
    drawRect(Color(0xFF0F1A66))
    val step = 24.dp.toPx()
    val stripe = 12.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = Color(0xFF12207A),
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = stripe,
        )
        x += step
    }
}

/**
 * Ink outline plus a solid (unblurred) drop shadow: the signature of every
 * raised surface, and the iOS `solidRaised` counterpart. Without it Android
 * surfaces read flat next to the same screen on iOS.
 *
 * The outline hugs the outer edge and is painted over the content, rather than
 * being a stroke straddling the boundary of a clipped fill: a straddling stroke
 * leaves the fill's antialiased edge showing past it as a pale seam.
 *
 * It costs no layout. iOS `SolidRaised` only clips and paints, so a raised row
 * is exactly as tall as its padded content and the border eats into it.
 * Reserving the border as padding instead, as this did, made every raised
 * surface on Android 2x`border` taller than the same surface on iOS, and the
 * error accumulated down every stack of them.
 */
fun Modifier.solidRaisedCapsule(
    depth: Dp,
    border: Dp = 4.dp,
    outline: Color = DesignTokens.Color.ink,
): Modifier = solidRaised(radius = null, depth = depth, border = border, outline = outline)

/** @param radius null for a capsule, which a fixed radius cannot express. */
fun Modifier.solidRaised(
    radius: Dp?,
    depth: Dp,
    border: Dp = DesignTokens.Border.heavy,
    outline: Color = DesignTokens.Color.ink,
    pressed: Boolean = false,
): Modifier = composed {
    val shape = if (radius == null) CircleShape else RoundedCornerShape(radius)
    // Pressing sinks the surface onto its shadow, as motion.press describes.
    val sink = if (pressed) 5.dp else 0.dp
    val drop = (depth - sink).coerceAtLeast(0.dp)
    this
        .offset(y = sink)
        // Outside the clip below: the drop shadow sits proud of the surface.
        .drawBehind {
            drawRoundRect(
                color = outline,
                topLeft = Offset(0f, drop.toPx()),
                size = size,
                cornerRadius = CornerRadius((radius?.toPx() ?: (size.height / 2f))),
            )
        }
        .clip(shape)
        .drawWithContent {
            drawContent()
            val b = border.toPx()
            val r = radius?.toPx() ?: (size.height / 2f)
            drawRoundRect(
                color = outline,
                topLeft = Offset(b / 2f, b / 2f),
                size = Size(size.width - b, size.height - b),
                cornerRadius = CornerRadius((r - b / 2f).coerceAtLeast(0f)),
                style = Stroke(width = b),
            )
        }
}

/**
 * The pale hatch behind an onboarding illustration. The ad slots use the dark
 * navy [adHatch]; using it here made the intro art look like an ad.
 */
fun Modifier.paperHatch(): Modifier = drawBehind {
    drawRect(DesignTokens.Color.ivory)
    val step = 28.dp.toPx()
    val stripe = 14.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(
            color = Color(0xFFEFEDE3),
            start = Offset(x, size.height),
            end = Offset(x + size.height, 0f),
            strokeWidth = stripe,
        )
        x += step
    }
}

enum class ButtonStyle { Primary, Secondary, Destructive, Ghost }

/**
 * Design-system button: 5px ink border, solid ink shadow, and the whole
 * face sinking by the shadow height while pressed.
 */
@Composable
fun InkButton(
    text: String,
    style: ButtonStyle,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fontSize: TextUnit = 18.sp,
    fontWeight: Int = 800,
    tracking: Float = -0.045f,
    depth: Dp = 8.dp,
    radius: Dp = DesignTokens.Radius.large,
    height: Dp = 56.dp,
    /**
     * iOS sizes every one of these from the padding around the label rather
     * than from a fixed height, so the same button is a different height on
     * each screen. Set this to reproduce a specific one; [height] is the
     * fallback for the call sites that have no iOS counterpart.
     */
    verticalPadding: Dp? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop = depth
    val shape = RoundedCornerShape(radius)
    val (fill, textColor) = when (style) {
        ButtonStyle.Primary -> DesignTokens.Color.yellow to DesignTokens.Color.ink
        ButtonStyle.Secondary -> DesignTokens.Color.ivory to DesignTokens.Color.ink
        ButtonStyle.Destructive -> DesignTokens.Color.coral to Color.White
        ButtonStyle.Ghost -> Color.Transparent to Color.White
    }
    val borderColor =
        if (style == ButtonStyle.Ghost) Color.White.dim(DesignTokens.Opacity.borderRow) else DesignTokens.Color.ink
    val borderWidth = if (style == ButtonStyle.Ghost) 3.dp else DesignTokens.Border.heavy

    // iOS dims a disabled button to 40% (DesignSystem.swift:562). Gating only
    // the click handler left a spent Hint looking fully active.
    // No room reserved for the drop shadow: iOS draws it as an offset copy
    // behind the button, which costs no layout, and the spacing between two
    // stacked buttons is wide enough to clear it.
    Box(modifier.alpha(if (enabled) 1f else 0.4f)) {
        if (style != ButtonStyle.Ghost) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(y = drop)
                    .background(DesignTokens.Color.ink, shape)
            )
        }
        Box(
            Modifier
                .offset(y = if (pressed && enabled) 5.dp else 0.dp)
                .fillMaxWidth()
                .then(if (verticalPadding == null) Modifier.height(height) else Modifier)
                .background(fill, shape)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                )
                .then(
                    if (verticalPadding != null) Modifier.padding(vertical = verticalPadding)
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                style = typeStyle(DesignTokens.Type.answer, textColor).copy(
                    fontSize = fontSize,
                    fontWeight = FontWeight(fontWeight),
                    letterSpacing = tracking.em,
                ),
                maxLines = 1,
            )
        }
    }
}

enum class AnswerState { Idle, Correct, Wrong, Dimmed }

/** One of the four Easy options. Ink blue at rest, verdict-coloured after. */
@Composable
fun AnswerButton(
    text: String,
    state: AnswerState,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val (fill, textColor) = when (state) {
        AnswerState.Idle -> DesignTokens.Color.blueNight to Color.White
        AnswerState.Correct -> DesignTokens.Color.green to DesignTokens.Color.ink
        AnswerState.Wrong -> DesignTokens.Color.coral to Color.White
        AnswerState.Dimmed -> DesignTokens.Color.blueNight.dim(DesignTokens.Opacity.chip) to
            Color.White.dim(DesignTokens.Opacity.chip)
    }
    // Same metrics as the Home mode cards, matching iOS: an answer reads as the
    // same kind of object as a mode.
    Box(
        modifier
            .fillMaxWidth()
            .solidRaised(DesignTokens.Radius.large, depth = 10.dp, pressed = pressed)
            .background(fill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 22.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text,
            style = typeStyle(DesignTokens.Type.clubTo, textColor),
            textAlign = TextAlign.Start,
            maxLines = 2,
        )
    }
}

/** One pip per question: green/coral for played, yellow live, dim pending. */
@Composable
fun ProgressPips(results: List<Boolean?>, liveIndex: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        results.forEachIndexed { i, r ->
            val fill = when {
                r == true -> DesignTokens.Color.green
                r == false -> DesignTokens.Color.coral
                i == liveIndex -> DesignTokens.Color.yellow
                else -> DesignTokens.Color.ink.dim(DesignTokens.Opacity.control)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(9.dp)
                    .background(fill, CircleShape)
                    .border(3.dp, DesignTokens.Color.ink, CircleShape)
            )
        }
    }
}

/** Three dots, coral while available, dimmed once spent. Hardcore only. */
@Composable
fun LivesRow(livesLeft: Int, modifier: Modifier = Modifier, total: Int = 3) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(13.dp)
                    .background(
                        if (i < livesLeft) DesignTokens.Color.coral
                        else DesignTokens.Color.ink.dim(DesignTokens.Opacity.control),
                        CircleShape,
                    )
                    .border(3.dp, DesignTokens.Color.ink, CircleShape)
            )
        }
    }
}

/** Score readout, tinted by the last verdict. */
@Composable
fun ScorePill(
    points: Long,
    lastCorrect: Boolean?,
    bumpToken: Int,
    modifier: Modifier = Modifier,
) {
    val color = when (lastCorrect) {
        true -> DesignTokens.Color.green
        false -> DesignTokens.Color.coral
        null -> DesignTokens.Color.ivory
    }
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        // A bordered, raised capsule as on iOS: bare tinted text read as a stray
        // number rather than part of the top bar.
        Box(
            Modifier
                .solidRaisedCapsule(depth = 6.dp)
                .background(color)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$points",
                style = typeStyle(DesignTokens.Type.scorePill, DesignTokens.Color.ink)
                    // monospacedDigit on iOS: the pill must not resize as the
                    // score climbs.
                    .copy(fontFeatureSettings = "tnum"),
            )
        }
        if (lastCorrect != null) ScoreBump(lastCorrect, bumpToken)
    }
}

/**
 * SwiftUI's `.easeOut`, so the fly-up reads the same on both platforms.
 *
 * motion.score-bump supplies the 1500ms but names cubic-bezier(.2,.9,.3,1),
 * which iOS does not use: that curve is 95% done by 30% of the duration, so
 * the value fades out before it has travelled far enough to read.
 */
private val scoreBumpEasing = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The value of the answer, flying up out of the score pill and fading.
 *
 * Keyed on [bumpToken] so it replays on every settled answer, including two
 * correct answers in a row, where nothing else about the pill changes. It is
 * laid out unbounded: at a low score the glyph is wider than the pill, and
 * letting it measure would shove the progress pips sideways for the duration.
 */
@Composable
private fun ScoreBump(correct: Boolean, bumpToken: Int) = key(bumpToken) {
    val flight = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        flight.animateTo(1f, tween(1500, easing = scoreBumpEasing))
    }
    Text(
        if (correct) "+3" else "0",
        style = typeStyle(
            DesignTokens.Type.year,
            if (correct) DesignTokens.Color.green else DesignTokens.Color.coral,
        ).copy(
            fontSize = 30.sp,
            shadow = Shadow(DesignTokens.Color.ink, Offset(3f, 3f), blurRadius = 0f),
        ),
        modifier = Modifier
            .wrapContentSize(unbounded = true)
            .offset { IntOffset(0, lerp(40.dp, (-4).dp, flight.value).roundToPx()) }
            .alpha(1f - flight.value),
    )
}

/** 52x30 track, ink border, green when on. */
@Composable
fun MercatoToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track =
        if (checked) DesignTokens.Color.green else DesignTokens.Color.ink.dim(DesignTokens.Opacity.trackOff)
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .background(track, RoundedCornerShape(DesignTokens.Radius.tile))
            .border(3.dp, DesignTokens.Color.ink, RoundedCornerShape(DesignTokens.Radius.tile))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        // iOS animates the knob with .snappy(0.18); Android snapped instantly.
        val shift by animateDpAsState(
            targetValue = if (checked) 24.dp else 2.dp,
            animationSpec = tween(180),
            label = "toggleKnob",
        )
        Box(
            Modifier
                .offset(x = shift)
                .size(22.dp)
                .background(DesignTokens.Color.ivory, CircleShape)
                .border(3.dp, DesignTokens.Color.ink, CircleShape)
        )
    }
}

/** Ink tab bar; the active tab sits on a yellow rounded block. */
@Composable
fun MercatoTabBar(
    tabs: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(DesignTokens.Color.ink, RoundedCornerShape(DesignTokens.Radius.card))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEachIndexed { i, label ->
            TabCell(label, i == selected) { onSelect(i) }
        }
    }
}

@Composable
private fun RowScope.TabCell(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.Radius.small)
    Box(
        Modifier
            .weight(1f)
            .fillMaxSize()
            .background(if (active) DesignTokens.Color.yellow else Color.Transparent, shape)
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = typeStyle(
                DesignTokens.Type.tabLabel,
                if (active) DesignTokens.Color.ink
                else DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textMuted),
            ),
        )
    }
}

/** Content column shared by every screen: 440dp max, 16dp gutters. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        androidx.compose.foundation.layout.Column(
            modifier
                .widthIn(max = DesignTokens.Layout.columnMax)
                .fillMaxSize()
                // iOS gets the safe area for free; without this the top bar
                // sat under the status bar and the tab bar under the gesture
                // handle.
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = DesignTokens.Space.gutter),
            content = content,
        )
    }
}

/** Small all-caps label (ADVERTISEMENT, section titles...). */
@Composable
fun CapsLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textCaps),
    style: DesignTokens.TypeStyle = DesignTokens.Type.label,
) {
    Text(text.uppercase(), style = typeStyle(style, color), modifier = modifier)
}

/**
 * Apply an opacity token. The generated Kotlin states them as Double, since
 * tokens.json does, while Compose alpha is a Float; this keeps the conversion
 * in one place instead of a .toFloat() at every call site.
 */
fun Color.dim(opacity: Double): Color = copy(alpha = opacity.toFloat())

/** Vertical spacer shorthand. */
@Composable
fun Gap(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))

/**
 * Hardcore free-text entry, the single most important control of that mode.
 *
 * Was a Material3 `OutlinedTextField`, which brought its own outline, focus
 * chrome and label metrics and read as an Android form dropped into the game.
 * Rebuilt on [BasicTextField] over the app's own raised surface, matching
 * iOS `GuessField` (DesignSystem.swift:447): ivory box, heavy ink border,
 * verdict-tinted fill, and the input in the display face at 30/900.
 */
@Composable
fun GuessField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    /** null while the question is still open. */
    verdict: Boolean?,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fill = when (verdict) {
        true -> DesignTokens.Color.green
        false -> DesignTokens.Color.coral
        null -> DesignTokens.Color.ivory
    }
    val fg = if (verdict == false) Color.White else DesignTokens.Color.ink

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .solidRaised(radius = DesignTokens.Radius.card, depth = 10.dp)
            .background(fill)
    ) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val base = typeStyle(DesignTokens.Type.clubTo, fg)
        // iOS shrinks to 50% rather than scrolling (minimumScaleFactor 0.5),
        // so a long name stays readable in full.
        val availPx = with(density) { (maxWidth - 40.dp).toPx() }
        val shown = value.ifEmpty { placeholder }
        var pt = 30f
        while (pt > 15f &&
            measurer.measure(shown, base.copy(fontSize = pt.sp), maxLines = 1).size.width > availPx
        ) pt -= 1f
        val style = base.copy(fontSize = pt.sp)

        Box(Modifier.padding(vertical = 22.dp, horizontal = 20.dp)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    style = style.copy(color = DesignTokens.Color.muted),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = verdict == null,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(fg),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
