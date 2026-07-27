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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mercato.app.AppGraph
import com.mercato.app.R
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
            Text(
                "‹",
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(end = DesignTokens.Space.md),
            )
            Text(
                stringResource(R.string.labT),
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
            )
        }
        Gap(DesignTokens.Space.xs)
        Text(
            stringResource(R.string.labN),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory.copy(alpha = 0.8f)),
        )
        Gap(DesignTokens.Space.lg)

        // Target picker: filter by name, tap a row to lock the target.
        CapsLabel(stringResource(R.string.labTarget))
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

        CapsLabel(stringResource(R.string.labGuess))
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
                    .background(
                        DesignTokens.Color.blueNight,
                        RoundedCornerShape(DesignTokens.Radius.chip),
                    )
                    .padding(horizontal = DesignTokens.Space.md, vertical = 6.dp),
            ) {
                Text(
                    stringResource(verdictLabel),
                    style = typeStyle(DesignTokens.Type.answer, verdictColor),
                )
            }
            Gap(DesignTokens.Space.xs)
            Text(
                "${stringResource(R.string.labTh)}: ${o.threshold}",
                style = typeStyle(DesignTokens.Type.technical, DesignTokens.Color.ivory),
            )
            Gap(DesignTokens.Space.xs)
            LabPanel {
                o.trace.forEach { line ->
                    Text(
                        line,
                        style = typeStyle(
                            DesignTokens.Type.technical,
                            DesignTokens.Color.ivory.copy(alpha = 0.9f),
                        ),
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            Gap(DesignTokens.Space.lg)
        }

        CapsLabel(stringResource(R.string.stats))
        Gap(DesignTokens.Space.xs)
        LabPanel {
            LabStatLine(stringResource(R.string.sPlayers), stats.players.toString())
            LabStatLine(stringResource(R.string.sClubs), stats.clubs.toString())
            LabStatLine(stringResource(R.string.sTr), stats.transfers.toString())
            LabStatLine(stringResource(R.string.kAlias), stats.aliases.toString())
            LabStatLine(stringResource(R.string.sAmb), collisions.size.toString())
        }
        Gap(DesignTokens.Space.lg)

        CapsLabel(stringResource(R.string.ambT))
        Gap(DesignTokens.Space.xs)
        Text(
            stringResource(R.string.ambN),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory.copy(alpha = 0.7f)),
        )
        Gap(DesignTokens.Space.xs)
        collisions.take(30).forEach { c ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .background(
                        DesignTokens.Color.blueNight,
                        RoundedCornerShape(DesignTokens.Radius.small),
                    )
                    .padding(DesignTokens.Space.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    c.surname,
                    style = typeStyle(DesignTokens.Type.answer, DesignTokens.Color.yellow),
                )
                Text(
                    c.players.joinToString(", "),
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.copy(alpha = 0.8f),
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
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = {
            Text(placeholder, style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted))
        },
        textStyle = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ink),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = DesignTokens.Color.ivory,
            unfocusedContainerColor = DesignTokens.Color.ivory,
            focusedBorderColor = DesignTokens.Color.ink,
            unfocusedBorderColor = DesignTokens.Color.ink,
        ),
        shape = RoundedCornerShape(DesignTokens.Radius.medium),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun LabPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.blueNight, RoundedCornerShape(DesignTokens.Radius.medium))
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
