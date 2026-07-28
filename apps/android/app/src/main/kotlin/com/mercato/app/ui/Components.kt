package com.mercato.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
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

/** The blue app background, approximating the CSS radial gradient. */
fun Modifier.appBackground(): Modifier = background(
    Brush.verticalGradient(
        listOf(
            DesignTokens.Color.blueTop,
            DesignTokens.Color.blue,
            DesignTokens.Color.blueDeep,
        )
    )
)

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
 * The outline is a filled shape behind content inset by the border width,
 * rather than a stroke over a clipped fill, for the same reason as on iOS: a
 * stroke leaves the fill's antialiased edge showing past it as a pale seam.
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
    val inner = if (radius == null) CircleShape
                else RoundedCornerShape((radius - border).coerceAtLeast(0.dp))
    // Pressing sinks the surface onto its shadow, as motion.press describes.
    val sink = if (pressed) 5.dp else 0.dp
    val drop = (depth - sink).coerceAtLeast(0.dp)
    this
        .offset(y = sink)
        .drawBehind {
            drawRoundRect(
                color = outline,
                topLeft = Offset(0f, drop.toPx()),
                size = size,
                cornerRadius = CornerRadius((radius?.toPx() ?: (size.height / 2f))),
            )
        }
        .background(outline, shape)
        .padding(border)
        .clip(inner)
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
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val drop = 8.dp
    val shape = RoundedCornerShape(DesignTokens.Radius.large)
    val (fill, textColor) = when (style) {
        ButtonStyle.Primary -> DesignTokens.Color.yellow to DesignTokens.Color.ink
        ButtonStyle.Secondary -> DesignTokens.Color.ivory to DesignTokens.Color.ink
        ButtonStyle.Destructive -> DesignTokens.Color.coral to Color.White
        ButtonStyle.Ghost -> Color.Transparent to Color.White
    }
    val borderColor =
        if (style == ButtonStyle.Ghost) Color.White.copy(alpha = 0.24f) else DesignTokens.Color.ink
    val borderWidth = if (style == ButtonStyle.Ghost) 3.dp else DesignTokens.Border.heavy

    Box(modifier.padding(bottom = drop)) {
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
                .height(56.dp)
                .background(fill, shape)
                .border(borderWidth, borderColor, shape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, style = typeStyle(DesignTokens.Type.answer, textColor))
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
        AnswerState.Dimmed -> DesignTokens.Color.blueNight.copy(alpha = 0.4f) to
            Color.White.copy(alpha = 0.4f)
    }
    // Same metrics as the Home mode cards, matching iOS: an answer reads as the
    // same kind of object as a mode.
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
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
                else -> DesignTokens.Color.ink.copy(alpha = 0.45f)
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
                        else DesignTokens.Color.ink.copy(alpha = 0.45f),
                        CircleShape,
                    )
                    .border(3.dp, DesignTokens.Color.ink, CircleShape)
            )
        }
    }
}

/** Score readout, tinted by the last verdict. */
@Composable
fun ScorePill(points: Long, lastCorrect: Boolean?, modifier: Modifier = Modifier) {
    val color = when (lastCorrect) {
        true -> DesignTokens.Color.green
        false -> DesignTokens.Color.coral
        null -> DesignTokens.Color.ivory
    }
    // A bordered, raised capsule as on iOS: bare tinted text read as a stray
    // number rather than part of the top bar.
    Box(
        modifier
            .padding(bottom = 6.dp)
            .solidRaisedCapsule(depth = 6.dp)
            .background(color)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$points",
            style = typeStyle(DesignTokens.Type.year, DesignTokens.Color.ink)
                .copy(fontSize = 22.sp),
        )
    }
}

/** 52x30 track, ink border, green when on. */
@Composable
fun MercatoToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val track =
        if (checked) DesignTokens.Color.green else DesignTokens.Color.ink.copy(alpha = 0.25f)
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .background(track, RoundedCornerShape(15.dp))
            .border(3.dp, DesignTokens.Color.ink, RoundedCornerShape(15.dp))
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(3.dp)
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
    val shape = RoundedCornerShape(14.dp)
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
                DesignTokens.Type.clubTo,
                if (active) DesignTokens.Color.ink
                else DesignTokens.Color.ivory.copy(alpha = 0.7f),
            ).copy(fontSize = 14.sp, letterSpacing = (-0.02 * 14).sp),
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
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = Color.White.copy(alpha = 0.6f)) {
    Text(text.uppercase(), style = typeStyle(DesignTokens.Type.label, color), modifier = modifier)
}

/** Vertical spacer shorthand. */
@Composable
fun Gap(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.height(height))
