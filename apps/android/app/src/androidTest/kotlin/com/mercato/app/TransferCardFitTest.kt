package com.mercato.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import com.mercato.app.ui.LocalFonts
import com.mercato.app.ui.TransferCard
import com.mercato.app.ui.rememberFonts
import com.mercato.design.DesignTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uniffi.mercato_ffi.MoveKind
import uniffi.mercato_ffi.QuestionView

/**
 * A property, not a list of cases.
 *
 * The bug this guards was found by playing until the right club came up: a
 * name long enough to wrap the card onto a second line, which grew it by a
 * whole line and pushed the hint and submit buttons off the bottom with the
 * keyboard open. Finding it took an hour of tapping, and the club that did it
 * (Club Deportivo Leganés) was not one anybody would have thought to write a
 * case for.
 *
 * So the assertion is over the corpus rather than over a name someone chose:
 * no club name may make the card taller than one line's worth of slack. Data
 * that changes is checked by the same rule as data that exists today, which
 * is the only way this stays true after the next dataset import.
 */
class TransferCardFitTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * The longest names in the shipping corpus, one per script and shape that
     * behaves differently when measured: accents, a hyphen, a wide glyph.
     * check-data caps the whole corpus at 21 characters, so these stand for
     * the worst it can contain rather than for themselves.
     */
    private val worstNames = listOf(
        "Ajax",                     // one-line reference, index 0
        "Athletico Paranaense",     // two-line reference, index 1
        "New England",
        "Wolverhampton",
        "Mönchengladbach",
        "Sporting de Gijón",
        "Vancouver Whitecaps",
        "Željezničar",
        "Beşiktaş",
    )

    private fun question(from: String, to: String) = QuestionView(
        index = 1u,
        total = 10u,
        kind = MoveKind.TRANSFER,
        year = 2020,
        fromClub = from,
        toClub = to,
        options = emptyList(),
        attemptsLeft = 3u,
        maskedName = "•••• ••••••",
    )

    /**
     * Every case in one composition. The test rule allows a single
     * setContent, and measuring afterwards is also closer to the truth: the
     * cards share one density and one font resolution, so a difference
     * between them is a difference in the layout rather than in the harness.
     */
    private var budgetPx = 0f

    private fun composeAll() {
        compose.setContent {
            CompositionLocalProvider(LocalFonts provides rememberFonts()) {
                budgetPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    (DesignTokens.Layout.compactHeight / 2).toPx()
                }
                // Scrolling, so every card is measured at its own height.
                // Stacked in a plain Column they overflow the screen and the
                // ones past the fold measure zero, which reads as a card that
                // shrank rather than as a harness that ran out of room.
                Column(
                    Modifier
                        .width(DesignTokens.Layout.columnMax)
                        .verticalScroll(rememberScrollState())
                ) {
                    for ((i, name) in worstNames.withIndex()) {
                        for (compact in listOf(true, false)) {
                            TransferCard(
                                QuestionUi(question = question("Ajax", name), attemptsLeft = 3),
                                compact = compact,
                                onTap = {},
                                modifier = Modifier.testTag(tag(i, compact)),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun tag(i: Int, compact: Boolean) = "card-$i-" + if (compact) "c" else "f"

    private fun heightOf(i: Int, compact: Boolean): Float =
        compose.onNodeWithTag(tag(i, compact)).fetchSemanticsNode().size.height.toFloat()

    @Test
    fun noCorpusNameTakesTheCardPastTwoLines() {
        composeAll()
        // Wrapping is normal and allowed: 118 club names run past 17
        // characters and "Queens Park Rangers" is what people call the club.
        // An earlier version of this test forbade wrapping outright, and the
        // data was right while the test was wrong.
        //
        // What the screen has to be able to plan for is a bound, so the rule
        // is two lines and no more. Both references are measured here rather
        // than assumed, since a line costs a different number of pixels at
        // every density.
        val oneLine = heightOf(0, compact = true)
        val twoLines = heightOf(1, compact = true)
        assertTrue(
            "the two-line reference ($twoLines) should exceed the one-line one ($oneLine);" +
                " the references no longer measure what they are named for",
            twoLines > oneLine,
        )
        val over = worstNames.indices.drop(2)
            .map { it to heightOf(it, compact = true) }
            .filter { (_, h) -> h > twoLines + 1f }
            .map { (i, h) -> "${worstNames[i]} (${h}px vs ${twoLines}px)" }
        assertTrue(
            "these names take the card past two lines: ${over.joinToString()}",
            over.isEmpty(),
        )
    }

    @Test
    fun compactIsNeverTallerThanFull() {
        composeAll()
        val wrong = worstNames.indices
            .map { Triple(worstNames[it], heightOf(it, true), heightOf(it, false)) }
            .filter { (_, c, f) -> c > f }
            .map { (n, c, f) -> "$n: compact $c > full $f" }
        assertTrue(
            "compact must never be the taller of the two: ${wrong.joinToString()}",
            wrong.isEmpty(),
        )
    }
}
