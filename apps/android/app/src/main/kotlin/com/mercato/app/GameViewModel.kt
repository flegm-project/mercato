package com.mercato.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.mercato.analytics.Event
import com.mercato.analytics.Param
import com.mercato.design.DesignTokens
import uniffi.mercato_ffi.GameMode
import uniffi.mercato_ffi.HintView
import uniffi.mercato_ffi.QuestionView
import uniffi.mercato_ffi.RejectionReason
import uniffi.mercato_ffi.ScoreView
import uniffi.mercato_ffi.languageForLocale

/** Everything one question needs on screen, plus its resolution. */
data class QuestionUi(
    val question: QuestionView,
    /** Index of the option the player tapped (Easy), if any. */
    val picked: Int? = null,
    /** Index of the correct option, revealed with the verdict (Easy). */
    val correctOption: Int? = null,
    val verdict: Boolean? = null,
    val revealedName: String? = null,
    /** Hardcore: lives left for the WHOLE round (core owns the rule). */
    val attemptsLeft: Int,
    val hints: List<HintView> = emptyList(),
    /** Transient rejection feedback (wrong guess / ambiguous surname). */
    val rejection: RejectionReason? = null,
)

data class RecapUi(
    val won: Boolean,
    val points: Long,
    val correct: Int,
    val total: Int,
    val bestStreak: Int,
    val stars: Int,
    val missed: List<uniffi.mercato_ffi.MissedView>,
)

/**
 * Drives one round through the FFI facade. The Rust session owns every rule;
 * this class only sequences questions, the 1.9s auto-advance, and the recap.
 */
class GameViewModel(private val graph: AppGraph) : ViewModel() {

    private val game get() = graph.game

    val mode = MutableStateFlow(GameMode.EASY)
    private val _question = MutableStateFlow<QuestionUi?>(null)
    val question: StateFlow<QuestionUi?> = _question.asStateFlow()
    private val _score = MutableStateFlow<ScoreView?>(null)
    val score: StateFlow<ScoreView?> = _score.asStateFlow()
    private val _pips = MutableStateFlow<List<Boolean?>>(emptyList())
    val pips: StateFlow<List<Boolean?>> = _pips.asStateFlow()
    private val _recap = MutableStateFlow<RecapUi?>(null)
    val recap: StateFlow<RecapUi?> = _recap.asStateFlow()

    /**
     * Seed a recap without playing a round, for the debug route that drives
     * deterministic screenshots. Mirrors the iOS QA affordance, and matches
     * its numbers so the two captures are comparable.
     */
    fun debugSeedRecap(won: Boolean) {
        _recap.value = if (won) {
            RecapUi(won = true, points = 21, correct = 7, total = 10, bestStreak = 5, stars = 3, missed = emptyList())
        } else {
            RecapUi(won = false, points = 6, correct = 2, total = 10, bestStreak = 1, stars = 0, missed = emptyList())
        }
    }

    /** Counts settled answers, so the score pill can replay its fly-up. */
    private val _bumpToken = MutableStateFlow(0)
    val bumpToken: StateFlow<Int> = _bumpToken.asStateFlow()

    private var advanceJob: Job? = null
    private var correctCount = 0

    fun startRound(gameMode: GameMode, localeTag: String) {
        mode.value = gameMode
        _recap.value = null
        correctCount = 0
        game.startRound(
            languageForLocale(localeTag),
            gameMode,
            (System.currentTimeMillis() and 0xFFFF_FFFFL).toUInt(),
        )
        _pips.value = List(game.questionsPerRound().toInt()) { null }
        _score.value = game.score()
        graph.analytics.log(Event.ROUND_START, mapOf(Param.MODE to modeName(gameMode)))
        nextQuestion()
    }

    /** The mode as the event vocabulary spells it, not as Kotlin does. */
    private fun modeName(m: GameMode) = if (m == GameMode.EASY) "easy" else "hardcore"

    private fun nextQuestion() {
        advanceJob?.cancel()
        val q = game.nextQuestion()
        if (q == null) {
            finishRound()
        } else {
            _question.value = QuestionUi(question = q, attemptsLeft = q.attemptsLeft.toInt())
        }
    }

    /** Easy mode: the tap resolves the question in one go. */
    fun submitChoice(index: Int) {
        val ui = _question.value ?: return
        if (ui.verdict != null) return
        val answer = runCatching { game.submitChoice(index.toUInt()) }.getOrNull() ?: return
        val correctIdx = ui.question.options.indexOfFirst { it == answer.revealedName }
        applyVerdict(
            ui.copy(
                picked = index,
                correctOption = if (correctIdx >= 0) correctIdx else null,
                verdict = answer.correct,
                revealedName = answer.revealedName,
            ),
            answer.correct,
        )
    }

    /**
     * Hardcore mode: one typed guess settles the question (a wrong answer
     * costs one of the round's lives and reveals the name). Only an
     * ambiguous surname leaves the question open, at no cost.
     */
    fun submitGuess(text: String) {
        val ui = _question.value ?: return
        if (ui.verdict != null || text.isBlank()) return
        val answer = runCatching { game.submitGuess(text) }.getOrNull() ?: return
        if (answer.finished) {
            applyVerdict(
                ui.copy(
                    verdict = answer.correct,
                    revealedName = answer.revealedName,
                    attemptsLeft = answer.attemptsLeft.toInt(),
                    rejection = null,
                ),
                answer.correct,
            )
        } else {
            // Ambiguous surname: ask for the first name, no life spent.
            _question.value = ui.copy(
                attemptsLeft = answer.attemptsLeft.toInt(),
                rejection = answer.rejection,
            )
        }
    }

    fun requestHint() {
        val ui = _question.value ?: return
        if (ui.verdict != null || ui.hints.size >= 3) return
        val hint = game.nextHint() ?: return
        graph.analytics.log(Event.HINT_TAKEN, mapOf(Param.RANK to ui.hints.size + 1))
        _question.value = ui.copy(hints = ui.hints + hint)
    }

    private fun applyVerdict(resolved: QuestionUi, correct: Boolean) {
        // Here rather than in the screens: both modes settle a question
        // through this one function, so the cue cannot end up firing on a tap
        // in Easy and on a submit in Hardcore but not on the keyboard's Go.
        graph.sounds.play(if (correct) Cue.CORRECT else Cue.WRONG)
        if (correct) correctCount++
        val index = resolved.question.index.toInt() - 1
        _pips.value = _pips.value.toMutableList().also { if (index in it.indices) it[index] = correct }
        _score.value = game.score()
        _question.value = resolved
        _bumpToken.value++
        advanceJob = viewModelScope.launch {
            delay(DesignTokens.Motion.autoAdvance.toLong())
            advance()
        }
    }

    /** Called by the auto-advance timer, or earlier by a tap on the card. */
    fun advance() {
        advanceJob?.cancel()
        if (_question.value?.verdict == null) return
        nextQuestion()
    }

    private fun finishRound() {
        graph.sounds.play(Cue.ROUND_OVER)
        val s = game.score()
        val total = game.questionsPerRound().toInt()
        val ratio = if (total == 0) 0f else correctCount.toFloat() / total
        val stars = when {
            ratio >= 0.9f -> 3
            ratio >= 0.6f -> 2
            ratio > 0f -> 1
            else -> 0
        }
        _recap.value = RecapUi(
            // Two stars is the win, as on iOS. Calling a single star a win
            // meant one right answer out of ten reported "round won".
            won = stars >= 2,
            points = s.points,
            correct = correctCount,
            total = total,
            bestStreak = s.bestStreak.toInt(),
            stars = stars,
            missed = game.missed(),
        )
        _question.value = null
        graph.analytics.log(
            Event.ROUND_END,
            mapOf(
                Param.MODE to modeName(mode.value),
                Param.SCORE to s.points.toInt(),
                Param.CORRECT to correctCount,
                Param.STARS to stars,
                Param.WON to (stars >= 2),
            ),
        )
        viewModelScope.launch {
            graph.prefs.recordRound(
                score = s.points.toInt(),
                bestStreak = s.bestStreak.toInt(),
                correct = correctCount,
                answered = total,
            )
        }
    }

    fun quitRound() {
        advanceJob?.cancel()
        graph.analytics.log(
            Event.ROUND_QUIT,
            mapOf(Param.MODE to modeName(mode.value), Param.ANSWERED to correctCountSoFar()),
        )
        _question.value = null
        _recap.value = null
    }

    /** Questions already settled, which is where the player gave up. */
    private fun correctCountSoFar() = _pips.value.count { it != null }
}
