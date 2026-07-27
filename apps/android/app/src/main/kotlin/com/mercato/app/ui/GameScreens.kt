package com.mercato.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import com.mercato.app.AppGraph
import com.mercato.app.GameViewModel
import com.mercato.app.QuestionUi
import com.mercato.app.R
import com.mercato.app.RecapUi
import com.mercato.app.SponsorBoard
import com.mercato.app.RecapRectangle
import com.mercato.design.DesignTokens
import uniffi.mercato_ffi.GameMode
import uniffi.mercato_ffi.HintView
import uniffi.mercato_ffi.MoveKind
import uniffi.mercato_ffi.PlayerPosition
import uniffi.mercato_ffi.RejectionReason

/** 05 Game: top bar, transfer card, sponsor board, answers. Both modes. */
@Composable
fun GameScreen(
    graph: AppGraph,
    vm: GameViewModel,
    mode: GameMode,
    onRoundOver: () -> Unit,
    onQuit: () -> Unit,
) {
    val context = LocalContext.current
    val question by vm.question.collectAsState()
    val score by vm.score.collectAsState()
    val pips by vm.pips.collectAsState()
    val recap by vm.recap.collectAsState()
    var quitAsked by remember { mutableStateOf(false) }

    LaunchedEffect(mode) {
        val locale = context.resources.configuration.locales[0]?.toLanguageTag() ?: "en"
        vm.startRound(mode, locale)
        graph.ads.preloadInterstitial()
    }
    // 07 Interstitial: shown at the round break, gate willing, then recap.
    LaunchedEffect(recap) {
        if (recap != null) {
            val activity = context as? Activity
            if (activity != null) {
                graph.ads.maybeShowInterstitial(activity) { onRoundOver() }
            } else {
                onRoundOver()
            }
        }
    }

    val q = question ?: return
    ScreenColumn {
        Gap(DesignTokens.Space.lg)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✕",
                style = typeStyle(DesignTokens.Type.answer, DesignTokens.Color.ivory),
                modifier = Modifier
                    .clickable { quitAsked = true }
                    .padding(end = DesignTokens.Space.md),
            )
            ProgressPips(pips, liveIndex = q.question.index.toInt() - 1, Modifier.weight(1f))
            if (mode == GameMode.HARDCORE) {
                LivesRow(q.attemptsLeft, Modifier.padding(start = DesignTokens.Space.md))
            }
            ScorePill(
                score?.points ?: 0,
                score?.lastCorrect,
                Modifier.padding(start = DesignTokens.Space.md),
            )
        }
        Gap(DesignTokens.Space.md)
        TransferCard(q, onTap = vm::advance)
        Gap(DesignTokens.Space.sm)
        SponsorBoard(graph.ads)
        Gap(DesignTokens.Space.md)
        if (mode == GameMode.EASY) EasyAnswers(q, vm) else HardcoreAnswers(q, vm)
        Gap(DesignTokens.Space.md)
    }

    if (quitAsked) {
        QuitDialog(
            onStay = { quitAsked = false },
            onQuit = {
                quitAsked = false
                vm.quitRound()
                onQuit()
            },
        )
    }
}

/** The transfer card: kind chip + year header, origin, arrow, destination. */
@Composable
private fun TransferCard(ui: QuestionUi, onTap: () -> Unit) {
    val shape = RoundedCornerShape(DesignTokens.Radius.card)
    val borderColor = when (ui.verdict) {
        true -> DesignTokens.Color.greenDeep
        false -> DesignTokens.Color.coralDeep
        null -> DesignTokens.Color.ink
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.ivory, shape)
            .border(DesignTokens.Border.heavy, borderColor, shape)
            .clickable(onClick = onTap),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    DesignTokens.Color.ink,
                    RoundedCornerShape(
                        topStart = DesignTokens.Radius.card,
                        topEnd = DesignTokens.Radius.card,
                    ),
                )
                .padding(DesignTokens.Space.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindChip(ui.question.kind)
            Text(
                "${ui.question.year}",
                style = typeStyle(DesignTokens.Type.year, DesignTokens.Color.ivory),
            )
        }
        Column(
            Modifier.fillMaxWidth().padding(DesignTokens.Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                ui.question.fromClub,
                style = typeStyle(DesignTokens.Type.clubFrom, DesignTokens.Color.clubGrey),
                textAlign = TextAlign.Center,
            )
            Text(
                "↓",
                style = typeStyle(DesignTokens.Type.clubFrom, DesignTokens.Color.ink),
            )
            Text(
                ui.question.toClub,
                style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ink),
                textAlign = TextAlign.Center,
            )
            if (ui.question.maskedName.isNotEmpty()) {
                Gap(DesignTokens.Space.md)
                Text(
                    ui.revealedName ?: ui.question.maskedName,
                    style = typeStyle(
                        DesignTokens.Type.answer,
                        when (ui.verdict) {
                            true -> DesignTokens.Color.greenDeep
                            false -> DesignTokens.Color.coralDeep
                            null -> DesignTokens.Color.muted
                        },
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun KindChip(kind: MoveKind) {
    val label = when (kind) {
        MoveKind.TRANSFER -> R.string.perm
        MoveKind.LOAN -> R.string.loan
        MoveKind.FREE -> R.string.free
    }
    val fill = if (kind == MoveKind.LOAN) DesignTokens.Color.yellow else DesignTokens.Color.ivory
    Box(
        Modifier
            .background(fill, RoundedCornerShape(DesignTokens.Radius.chip))
            .padding(horizontal = DesignTokens.Space.sm, vertical = 4.dp),
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.label, DesignTokens.Color.ink),
        )
    }
}

@Composable
private fun EasyAnswers(ui: QuestionUi, vm: GameViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.sm)) {
        ui.question.options.forEachIndexed { i, option ->
            val state = when {
                ui.verdict == null -> AnswerState.Idle
                i == ui.correctOption -> AnswerState.Correct
                i == ui.picked -> AnswerState.Wrong
                else -> AnswerState.Dimmed
            }
            AnswerButton(option, state, enabled = ui.verdict == null) { vm.submitChoice(i) }
        }
    }
}

@Composable
private fun HardcoreAnswers(ui: QuestionUi, vm: GameViewModel) {
    var text by remember(ui.question.index) { mutableStateOf("") }
    Column {
        if (ui.hints.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = DesignTokens.Space.sm),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.xs),
            ) {
                ui.hints.forEach { HintChip(it) }
            }
        }
        ui.rejection?.let {
            Text(
                stringResource(
                    if (it == RejectionReason.AMBIGUOUS_SURNAME) R.string.rAmb else R.string.rNone
                ),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.coral),
                modifier = Modifier.padding(bottom = DesignTokens.Space.xs),
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = ui.verdict == null,
            singleLine = true,
            placeholder = {
                Text(
                    stringResource(R.string.ph),
                    style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted),
                )
            },
            textStyle = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.submitGuess(text) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DesignTokens.Color.ivory,
                unfocusedContainerColor = DesignTokens.Color.ivory,
                disabledContainerColor = DesignTokens.Color.ivory.copy(alpha = 0.6f),
                focusedBorderColor = DesignTokens.Color.ink,
                unfocusedBorderColor = DesignTokens.Color.ink,
            ),
            shape = RoundedCornerShape(DesignTokens.Radius.medium),
            modifier = Modifier.fillMaxWidth(),
        )
        Gap(DesignTokens.Space.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.sm)) {
            Box(Modifier.weight(1f)) {
                InkButton(
                    "${stringResource(R.string.hint)} (${3 - ui.hints.size})",
                    ButtonStyle.Secondary,
                    enabled = ui.verdict == null && ui.hints.size < 3,
                ) { vm.requestHint() }
            }
            Box(Modifier.weight(1f)) {
                InkButton(
                    stringResource(R.string.go),
                    ButtonStyle.Primary,
                    enabled = ui.verdict == null,
                ) { vm.submitGuess(text) }
            }
        }
    }
}

@Composable
private fun HintChip(hint: HintView) {
    // Positions use the same language-neutral abbreviations as the iOS app.
    val text = when {
        hint.nationality != null -> hint.nationality ?: ""
        hint.position != null -> when (hint.position) {
            PlayerPosition.GK -> "GK"
            PlayerPosition.DEF -> "DEF"
            PlayerPosition.MID -> "MID"
            else -> "FW"
        }
        else -> "${hint.surnameInitial ?: "?"} · ${hint.surnameLetters ?: 0}"
    }
    Box(
        Modifier
            .background(DesignTokens.Color.blueNight, RoundedCornerShape(DesignTokens.Radius.chip))
            .border(
                DesignTokens.Border.hairline,
                DesignTokens.Color.ink,
                RoundedCornerShape(DesignTokens.Radius.chip),
            )
            .padding(horizontal = DesignTokens.Space.sm, vertical = 4.dp),
    ) {
        Text(text, style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.yellow))
    }
}

/** 06 Quit dialog: keep playing (yellow) or quit (coral). */
@Composable
private fun QuitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    Dialog(onDismissRequest = onStay) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(DesignTokens.Color.ivory, RoundedCornerShape(DesignTokens.Radius.card))
                .border(
                    DesignTokens.Border.heavy,
                    DesignTokens.Color.ink,
                    RoundedCornerShape(DesignTokens.Radius.card),
                )
                .padding(DesignTokens.Space.xl),
        ) {
            Text(
                stringResource(R.string.quitT),
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ink),
            )
            Gap(DesignTokens.Space.sm)
            Text(
                stringResource(R.string.quitB),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted),
            )
            Gap(DesignTokens.Space.lg)
            InkButton(stringResource(R.string.quitStay), ButtonStyle.Primary, onClick = onStay)
            InkButton(stringResource(R.string.quitGo), ButtonStyle.Destructive, onClick = onQuit)
        }
    }
}

/** 08 Recap: stars, points, missed transfers, rectangle slot, actions. */
@Composable
fun RecapScreen(
    graph: AppGraph,
    vm: GameViewModel,
    onPlayAgain: (GameMode) -> Unit,
    onHome: () -> Unit,
) {
    val recap by vm.recap.collectAsState()
    val mode by vm.mode.collectAsState()
    val r: RecapUi = recap ?: return

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Gap(DesignTokens.Space.xl)
        Text(
            stringResource(if (r.won) R.string.winT else R.string.loseT),
            style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.sm)
        Text(
            List(3) { i -> if (i < r.stars) "★" else "☆" }.joinToString(" "),
            style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.yellow),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.md)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            RecapStat("${r.points}", stringResource(R.string.pts))
            RecapStat("${r.correct}/${r.total}", stringResource(R.string.rGood))
            RecapStat("${r.bestStreak}", stringResource(R.string.rStreak))
        }
        Gap(DesignTokens.Space.lg)
        RecapRectangle(graph.ads)
        if (r.missed.isNotEmpty()) {
            Gap(DesignTokens.Space.lg)
            CapsLabel(stringResource(R.string.rMissed))
            Gap(DesignTokens.Space.xs)
            r.missed.forEach { m ->
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
                        m.playerName,
                        style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory),
                    )
                    Text(
                        "${m.fromClub} > ${m.toClub} · ${m.year}",
                        style = typeStyle(
                            DesignTokens.Type.body,
                            DesignTokens.Color.ivory.copy(alpha = 0.7f),
                        ),
                    )
                }
            }
        }
        Gap(DesignTokens.Space.lg)
        InkButton(stringResource(R.string.again), ButtonStyle.Primary) { onPlayAgain(mode) }
        InkButton(stringResource(R.string.home), ButtonStyle.Ghost, onClick = onHome)
        Gap(DesignTokens.Space.xl)
    }
}

@Composable
private fun RecapStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = typeStyle(DesignTokens.Type.year, DesignTokens.Color.ivory))
        CapsLabel(label)
    }
}
