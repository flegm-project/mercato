package com.mercato.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mercato.app.ui.Gap
import com.mercato.design.DesignTokens
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A screen header is a back control and a title, and the gap between them is
 * the whole of its layout. It went missing on Settings and stayed missing
 * across several commits, because a height-only spacer inside a Row compiles,
 * runs, and does nothing.
 *
 * The two tests below are the same header built the two ways, so the file
 * states both what is required and what silently fails to deliver it.
 */
class HeaderSpacingTest {

    @get:Rule
    val compose = createComposeRule()

    private fun gapBetween(): Float {
        val arrow = compose.onNodeWithTag("arrow").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("title").fetchSemanticsNode().boundsInRoot
        return title.left - arrow.right
    }

    @Test
    fun arrangementSpacedBy_putsTheTitleClearOfTheArrow() {
        var expected = 0f
        compose.setContent {
            expected = with(androidx.compose.ui.platform.LocalDensity.current) {
                DesignTokens.Space.block.toPx()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.block)) {
                Box(Modifier.size(38.dp).testTag("arrow"))
                Text("RÉGLAGES", Modifier.testTag("title"))
            }
        }
        val gap = gapBetween()
        assertTrue(
            "expected at least ${expected}px between the arrow and the title, measured $gap",
            gap >= expected - 1f,
        )
    }

    @Test
    fun verticalGapInARow_spacesNothing() {
        compose.setContent {
            Row {
                Box(Modifier.size(38.dp).testTag("arrow"))
                Gap(DesignTokens.Space.block)
                Text("RÉGLAGES", Modifier.testTag("title"))
            }
        }
        // Pinned deliberately: this is the shape that shipped. If a future
        // Gap ever grows a width, this test fails and the rule in
        // check-ui-idioms can be retired with it.
        assertTrue(
            "Gap inside a Row now spaces something; check-ui-idioms can be revisited",
            gapBetween() < 1f,
        )
    }
}
