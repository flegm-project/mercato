package com.mercato.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mercato.app.AppGraph
import com.mercato.app.R
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.mercato.design.DesignTokens
import uniffi.mercato_ffi.LabOutcome
import uniffi.mercato_ffi.LabVerdict

/**
 * Dev-only matching lab (spec "Dev only"): pick a target player, type a
 * guess, and watch the real engine's verdict trace live, plus dataset stats
 * and the surname collisions Hardcore must refuse. Reached from Settings,
 * debug builds only; all logic comes from the shared lab facade.
 */
@Composable
fun LabScreen(graph: AppGraph, onBack: () -> Unit) {
    val game = graph.game
    val players = remember { game.labPlayers() }
    val stats = remember { game.labStats() }
    val collisions = remember { game.labCollisions() }

    var filter by remember { mutableStateOf("") }
    var targetId by remember { mutableStateOf<String?>(null) }
    var guess by remember { mutableStateOf("") }
    val outcome: LabOutcome? = remember(targetId, guess) {
        targetId?.let { if (guess.isNotBlank()) game.labEvaluate(it, guess) else null }
    }
    val targetName = players.firstOrNull { it.id == targetId }?.name

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Gap(DesignTokens.Space.xl)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val backLabel = stringResource(R.string.a11yBack)
            // iOS renders the chevron in the system face inside a 38x38 ink
            // box (Lab.swift:50); a bare 30px Unbounded glyph read as text.
            Box(
                Modifier
                    .semantics { contentDescription = backLabel }
                    .size(38.dp)
                    .background(
                        DesignTokens.Color.ink.dim(DesignTokens.Opacity.control),
                        RoundedCornerShape(DesignTokens.Radius.control),
                    )
                    .border(4.dp, DesignTokens.Color.ink, RoundedCornerShape(DesignTokens.Radius.control))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "‹",
                    style = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(900),
                        fontSize = 20.sp,
                    ),
                )
            }
            Gap(DesignTokens.Space.sm)
            Text(
                stringResource(R.string.labT),
                style = typeStyle(DesignTokens.Type.panelTitle, DesignTokens.Color.ivory),
            )
        }
        Gap(DesignTokens.Space.xs)
        Text(
            stringResource(R.string.labN),
            style = typeStyle(
                DesignTokens.Type.body,
                DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textMuted),
                13.sp,
                null,
            ),
        )
        Gap(DesignTokens.Space.lg)

        // Target picker: filter by name, tap a row to lock the target.
        CapsLabel(stringResource(R.string.labTarget), style = DesignTokens.Type.labCaps)
        Gap(DesignTokens.Space.xs)
        LabField(
            value = if (targetId == null) filter else targetName.orEmpty(),
            onChange = {
                filter = it
                targetId = null
            },
            placeholder = stringResource(R.string.labTarget),
        )
        if (targetId == null && filter.length >= 2) {
            val hits = players.filter { it.name.contains(filter, ignoreCase = true) }.take(6)
            hits.forEach { p ->
                Text(
                    p.name,
                    style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.yellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { targetId = p.id }
                        .padding(vertical = 6.dp),
                )
            }
        }
        Gap(DesignTokens.Space.md)

        CapsLabel(stringResource(R.string.labGuess), style = DesignTokens.Type.labCaps)
        Gap(DesignTokens.Space.xs)
        LabField(
            value = guess,
            onChange = { guess = it },
            placeholder = stringResource(R.string.ph),
        )
        Gap(DesignTokens.Space.md)

        outcome?.let { o ->
            val (verdictLabel, verdictColor) = when (o.verdict) {
                LabVerdict.ACCEPT -> R.string.yes to DesignTokens.Color.green
                LabVerdict.REJECT -> R.string.no to DesignTokens.Color.coral
                LabVerdict.AMBIGUOUS -> R.string.amb to DesignTokens.Color.yellow
            }
            Box(
                Modifier
                    // iOS uses ink at 40% in a capsule (Lab.swift:118), not an
                    // opaque blueNight rounded rect.
                    .background(DesignTokens.Color.ink.dim(DesignTokens.Opacity.chip), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(verdictLabel),
                    style = typeStyle(DesignTokens.Type.verdictChip, verdictColor),
                )
            }
            Gap(DesignTokens.Space.xs)
            Text(
                "${stringResource(R.string.labTh)}: ${o.threshold}",
                style = typeStyle(DesignTokens.Type.monoPlain, DesignTokens.Color.ivory),
            )
            Gap(DesignTokens.Space.xs)
            // iOS prints the matched form and its edit distance (Lab.swift:125);
            // Android showed the trace but never this line.
            Text(
                stringResource(R.string.kBest) + ": " +
                    (o.bestMatch ?: stringResource(R.string.rNoneL)) +
                    (o.distance?.let { " ($it)" } ?: ""),
                style = typeStyle(
                    DesignTokens.Type.technical,
                    DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textNearly),
                    11.sp,
                    null,
                ),
            )
            Gap(DesignTokens.Space.xs)
            LabPanel {
                o.trace.forEach { line ->
                    Text(
                        line,
                        style = typeStyle(
                            DesignTokens.Type.technical,
                            // iOS sets the trace one step quieter than the line
                            // above it, at 85% (Lab.swift:131).
                            DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textNear),
                        ),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Gap(DesignTokens.Space.lg)
        }

        CapsLabel(stringResource(R.string.stats), style = DesignTokens.Type.labCaps)
        Gap(DesignTokens.Space.xs)
        LabPanel {
            LabStatLine(stringResource(R.string.sPlayers), stats.players.toString())
            LabStatLine(stringResource(R.string.sClubs), stats.clubs.toString())
            LabStatLine(stringResource(R.string.sTr), stats.transfers.toString())
            LabStatLine(stringResource(R.string.kAlias), stats.aliases.toString())
            LabStatLine(stringResource(R.string.sAmb), collisions.size.toString())
        }
        Gap(DesignTokens.Space.lg)

        CapsLabel(stringResource(R.string.ambT), style = DesignTokens.Type.labCaps)
        Gap(DesignTokens.Space.xs)
        Text(
            stringResource(R.string.ambN),
            style = typeStyle(
                DesignTokens.Type.labCaption,
                DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textFaint),
            ),
        )
        Gap(DesignTokens.Space.xs)
        collisions.take(30).forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        DesignTokens.Color.blueNight,
                        RoundedCornerShape(DesignTokens.Radius.xsmall),
                    )
                    .padding(DesignTokens.Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    c.surname,
                    style = typeStyle(DesignTokens.Type.monoValue, DesignTokens.Color.yellow),
                )
                Text(
                    c.players.joinToString(", "),
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textQuiet),
                    ),
                    modifier = Modifier.padding(start = DesignTokens.Space.md),
                )
            }
        }
        Gap(DesignTokens.Space.xl)
    }
}

@Composable
private fun LabField(value: String, onChange: (String) -> Unit, placeholder: String) {
    // iOS deliberately omits a border here (Lab.swift:212): ivory fill,
    // radius 12, Figtree 15/700.
    val style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ink)
    Box(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.ivory, RoundedCornerShape(DesignTokens.Radius.control))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, style = style.copy(color = DesignTokens.Color.muted), maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(DesignTokens.Color.ink),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun LabPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                DesignTokens.Color.ink.dim(DesignTokens.Opacity.panel),
                RoundedCornerShape(DesignTokens.Radius.xsmall),
            )
            .padding(DesignTokens.Space.md),
        content = content,
    )
}

@Composable
private fun LabStatLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory))
        Text(value, style = typeStyle(DesignTokens.Type.technical, DesignTokens.Color.yellow))
    }
}
