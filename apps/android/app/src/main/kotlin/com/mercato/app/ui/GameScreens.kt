package com.mercato.app.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercato.app.AppGraph
import com.mercato.app.GameViewModel
import com.mercato.app.QuestionUi
import com.mercato.app.R
import com.mercato.app.RecapUi
import com.mercato.app.RecapRectangle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.mercato.design.DesignTokens
import uniffi.mercato_ffi.GameMode
import uniffi.mercato_ffi.HintView
import uniffi.mercato_ffi.MoveKind
import uniffi.mercato_ffi.PlayerPosition
import uniffi.mercato_ffi.RejectionReason

/** 05 Game: top bar, transfer card, centered answers. Both modes, no ads. */
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
    val bumpToken by vm.bumpToken.collectAsState()
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
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val closeLabel = stringResource(R.string.a11yClose)
            Box(
                Modifier
                    .semantics { contentDescription = closeLabel }
                    .size(38.dp)
                    .background(
                        DesignTokens.Color.ink.dim(DesignTokens.Opacity.control),
                        RoundedCornerShape(DesignTokens.Radius.control),
                    )
                    .border(4.dp, DesignTokens.Color.ink, RoundedCornerShape(DesignTokens.Radius.control))
                    .clickable { quitAsked = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("✕", style = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(900),
                        fontSize = 16.sp,
                    ))
            }
            Gap(DesignTokens.Space.md)
            ProgressPips(pips, liveIndex = q.question.index.toInt() - 1, Modifier.weight(1f))
            if (mode == GameMode.HARDCORE) {
                LivesRow(q.attemptsLeft, Modifier.padding(start = DesignTokens.Space.md))
            }
            ScorePill(
                score?.points ?: 0,
                // The question's own verdict, not the score's lastCorrect: the
                // core keeps that set for the rest of the round, which left the
                // pill green or coral from the first answer onwards.
                q.verdict,
                bumpToken,
                Modifier.padding(start = DesignTokens.Space.md),
            )
        }
        Gap(DesignTokens.Space.md)
        TransferCard(q, onTap = vm::advance)
        // No ad slot during a question (parity with iOS): the answers zone
        // floats centered between the card and the bottom edge.
        Spacer(Modifier.weight(1f))
        if (mode == GameMode.EASY) EasyAnswers(q, vm) else HardcoreAnswers(q, vm)
        Spacer(Modifier.weight(1f))
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
    val borderColor = when (ui.verdict) {
        true -> DesignTokens.Color.greenDeep
        false -> DesignTokens.Color.coralDeep
        null -> DesignTokens.Color.ink
    }
    // Outline and solid drop shadow, both following the verdict, as iOS. The
    // card was flat here while iOS showed it raised.
    Column(
        Modifier
            .fillMaxWidth()
            .solidRaised(DesignTokens.Radius.card, depth = 11.dp, outline = borderColor)
            .background(DesignTokens.Color.ivory)
            .clickable(onClick = onTap),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(DesignTokens.Color.ink)
                .padding(
                    horizontal = DesignTokens.Space.md,
                    vertical = DesignTokens.Space.sm,
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KindChip(ui.question.kind)
            // Compact card (iOS parity): year 32, from 16, to 30, masked 18.
            Text(
                "${ui.question.year}",
                style = typeStyle(DesignTokens.Type.year, DesignTokens.Color.yellow),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = DesignTokens.Space.lg,
                    vertical = DesignTokens.Space.md,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                ui.question.fromClub,
                style = typeStyle(DesignTokens.Type.clubFrom, DesignTokens.Color.clubGrey),
                textAlign = TextAlign.Center,
            )
            Text(
                "▼",
                style = TextStyle(
                    color = if (ui.verdict == null) {
                        DesignTokens.Color.ink.dim(DesignTokens.Opacity.arrowIdle)
                    } else DesignTokens.Color.ink,
                    fontFamily = FontFamily.Default,
                    fontSize = 13.sp,
                ),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            )
            Text(
                ui.question.toClub,
                style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ink),
                textAlign = TextAlign.Center,
            )
            // The card never shows the answer: the masked name is only
            // visible while the question is open (iOS parity).
            if (ui.question.maskedName.isNotEmpty() && ui.verdict == null) {
                Gap(DesignTokens.Space.sm)
                Text(
                    ui.question.maskedName,
                    style = typeStyle(DesignTokens.Type.maskedName, Color(0xFFD6D3C4)),
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
            style = typeStyle(DesignTokens.Type.cardKind, DesignTokens.Color.ink),
        )
    }
}

@Composable
private fun EasyAnswers(ui: QuestionUi, vm: GameViewModel) {
    // iOS spaces the four answers by 14 (GameView.swift:210).
    Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.md)) {
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
        GuessField(
            value = text,
            onValueChange = { text = it },
            placeholder = stringResource(R.string.ph),
            verdict = ui.verdict,
            onSubmit = { vm.submitGuess(text) },
        )
        Gap(DesignTokens.Space.sm)
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.sm)) {
            Box(Modifier.weight(0.38f)) {
                InkButton(
                    "${stringResource(R.string.hint)} (${3 - ui.hints.size})",
                    ButtonStyle.Secondary,
                    enabled = ui.verdict == null && ui.hints.size < 3,
                ) { vm.requestHint() }
            }
            Box(Modifier.weight(0.62f)) {
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
    // Localized position abbreviations (posGk/posDef/posMid/posFw), same
    // strings as the iOS hint chip.
    val text = when {
        hint.nationality != null -> hint.nationality ?: ""
        hint.position != null -> stringResource(
            when (hint.position) {
                PlayerPosition.GK -> R.string.posGk
                PlayerPosition.DEF -> R.string.posDef
                PlayerPosition.MID -> R.string.posMid
                else -> R.string.posFw
            }
        )
        else -> "${hint.surnameInitial ?: "?"} · ${hint.surnameLetters ?: 0}"
    }
    // Ivory face with ink text: the colours were inverted against iOS.
    Box(
        Modifier
            .background(DesignTokens.Color.ivory, RoundedCornerShape(DesignTokens.Radius.chip))
            .border(4.dp, DesignTokens.Color.ink, RoundedCornerShape(DesignTokens.Radius.chip))
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            text,
            style = typeStyle(DesignTokens.Type.hintChip, DesignTokens.Color.ink),
        )
    }
}

/** One of the two stats inside the recap score card. */
@Composable
private fun StatTile(modifier: Modifier, value: String, label: String, tint: Color) {
    Column(
        modifier
            .background(
                DesignTokens.Color.ink.dim(DesignTokens.Opacity.surfaceTint),
                RoundedCornerShape(DesignTokens.Radius.tile),
            )
            .padding(13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = typeStyle(DesignTokens.Type.scorePill, tint),
        )
        Text(
            label,
            style = typeStyle(DesignTokens.Type.tileLabel, DesignTokens.Color.muted)
                .copy(fontWeight = FontWeight(900)),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 06 Quit dialog: keep playing (yellow) or quit (coral). */
@Composable
private fun QuitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // iOS paints its own scrim, an ink navy at 78% (Screens.swift:463).
        // The platform default was lighter and a different hue.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF060920).dim(DesignTokens.Opacity.scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStay,
                ),
            contentAlignment = Alignment.Center,
        ) {
        Column(
            Modifier
                .padding(24.dp)
                .fillMaxWidth()
                // iOS caps the card at 360pt so it does not span a tablet.
                .widthIn(max = 360.dp)
                .solidRaised(radius = DesignTokens.Radius.card, depth = 12.dp)
                .background(DesignTokens.Color.ivory)
                .padding(DesignTokens.Space.xl),
        ) {
            Text(
                stringResource(R.string.quitT),
                style = typeStyle(DesignTokens.Type.dialogTitle, DesignTokens.Color.ink),
            )
            Gap(DesignTokens.Space.sm)
            Text(
                stringResource(R.string.quitB),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted),
            )
            Gap(DesignTokens.Space.lg)
            InkButton(
                stringResource(R.string.quitStay), ButtonStyle.Primary,
                fontSize = 17.sp, fontWeight = 900, tracking = -0.03f,
                depth = 12.dp, radius = DesignTokens.Radius.button, onClick = onStay,
            )
            InkButton(
                stringResource(R.string.quitGo), ButtonStyle.Destructive,
                fontSize = 15.sp, fontWeight = 800, tracking = -0.03f,
                radius = DesignTokens.Radius.button, onClick = onQuit,
            )
        }
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

    // iOS: 22 above the title, 18 above the stars, 22 above the card, 22 above
    // the CTAs, 24 above the rectangle, 20 at the foot (Screens.swift:155).
    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Gap(DesignTokens.Space.xl)
        Text(
            stringResource(if (r.won) R.string.winT else R.string.loseT),
            style = typeStyle(DesignTokens.Type.recapTitle, DesignTokens.Color.ivory),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.lg)
        // Three separately shadowed 46sp stars, dimmed when unearned, rather
        // than one small line of glyphs.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            repeat(3) { i ->
                Box {
                    Text(
                        "★",
                        style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ink)
                            .copy(fontSize = 46.sp),
                        modifier = Modifier.offset(x = 4.dp, y = 4.dp),
                    )
                    Text(
                        "★",
                        style = typeStyle(
                            DesignTokens.Type.screenTitle,
                            if (i < r.stars) DesignTokens.Color.yellow
                            else Color.White.dim(DesignTokens.Opacity.starOff),
                        ).copy(fontSize = 46.sp),
                    )
                }
            }
        }
        Gap(DesignTokens.Space.xl)
        // The score lives in a raised ivory card, as on iOS: the numbers were
        // sitting bare on the background with no hierarchy between them.
        Column(
            Modifier
                .fillMaxWidth()
                .solidRaised(DesignTokens.Radius.card, depth = 10.dp)
                .background(DesignTokens.Color.ivory)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "${r.points}",
                style = typeStyle(DesignTokens.Type.scoreHero, DesignTokens.Color.ink)
                    .copy(fontFeatureSettings = "tnum"),
            )
            Text(
                stringResource(R.string.pts).uppercase(),
                style = typeStyle(DesignTokens.Type.label, DesignTokens.Color.muted),
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                StatTile(
                    Modifier.weight(1f),
                    "${r.correct}/${r.total}",
                    stringResource(R.string.rGood),
                    DesignTokens.Color.greenDeep,
                )
                StatTile(
                    Modifier.weight(1f),
                    "${r.bestStreak}",
                    stringResource(R.string.rStreak),
                    DesignTokens.Color.ink,
                )
            }
        }
        Gap(DesignTokens.Space.xl)
        InkButton(
            stringResource(R.string.again), ButtonStyle.Primary,
            fontSize = 18.sp, fontWeight = 800, tracking = -0.045f,
            radius = DesignTokens.Radius.button, verticalPadding = 17.dp,
        ) { onPlayAgain(mode) }
        // iOS spaces the two CTAs by 10 (Screens.swift:177).
        Gap(DesignTokens.Space.sm)
        // A full-width secondary button, not a thin text link, so it is a
        // comfortable finger target next to Play again (iOS parity: ink 35%,
        // white 18% border, radius 18, vertical padding 16).
        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    DesignTokens.Color.ink.dim(DesignTokens.Opacity.row),
                    RoundedCornerShape(DesignTokens.Radius.medium),
                )
                .border(
                    2.dp,
                    androidx.compose.ui.graphics.Color.White.dim(DesignTokens.Opacity.borderPanel),
                    RoundedCornerShape(DesignTokens.Radius.medium),
                )
                .clip(RoundedCornerShape(DesignTokens.Radius.medium))
                .clickable(onClick = onHome)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.home),
                style = typeStyle(DesignTokens.Type.ctaSmall, DesignTokens.Color.ivory),
            )
        }
        // Display slot lives below the actions, never above the primary CTA.
        Gap(24.dp)
        RecapRectangle(graph.ads)
        Gap(20.dp)
    }
}

@Composable
private fun RecapStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = typeStyle(DesignTokens.Type.year, DesignTokens.Color.ivory))
        CapsLabel(
            label,
            color = Color.White.dim(DesignTokens.Opacity.chip),
            style = DesignTokens.Type.adLabel,
        )
    }
}
