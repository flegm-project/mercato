package com.mercato.app

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * REJOUER shipped to the store doing nothing at all: the screen went blank and
 * stayed there.
 *
 * The button was wired correctly. What broke it was that the recap of the
 * finished round outlives the screen that showed it, the ViewModel being
 * scoped to the activity, so the next game screen composed with the old recap
 * still in hand and its round-is-over effect fired on that stale value,
 * bouncing straight back to a recap that had since been cleared.
 *
 * This test does not drive the UI. It states the invariant the fix rests on,
 * which is that nothing navigates away from the recap while the recap is still
 * set: a first attempt merely gated the effect and still lost the race on a
 * real device, so the ordering is the thing worth pinning.
 */
class RecapHandoffTest {

    /** The two lines of MainActivity that matter, in the order they run. */
    private class Handoff(private val recap: MutableStateFlow<String?>) {
        var landedOnGameWith: String? = null
            private set

        fun playAgain(clearFirst: Boolean) {
            if (clearFirst) recap.value = null
            // Composing the game screen reads whatever the flow holds now.
            landedOnGameWith = recap.value
        }
    }

    @Test
    fun clearingBeforeNavigating_leavesTheGameScreenNothingStaleToReactTo() {
        val recap = MutableStateFlow<String?>("last round")
        val h = Handoff(recap)
        h.playAgain(clearFirst = true)
        assertNull(
            "the game screen must not see the finished round's recap",
            h.landedOnGameWith,
        )
    }

    @Test
    fun navigatingFirst_isTheBugThatShipped() {
        val recap = MutableStateFlow<String?>("last round")
        val h = Handoff(recap)
        h.playAgain(clearFirst = false)
        assertNotNull(
            "pinning the failing order: this is what made REJOUER look dead",
            h.landedOnGameWith,
        )
    }
}
