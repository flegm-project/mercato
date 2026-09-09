package com.mercato.app

import android.app.Activity
import com.google.android.play.core.review.ReviewManagerFactory
import com.mercato.analytics.Event

/**
 * The in-app review sheet, asked for at the one moment a player is pleased:
 * just after a round they won.
 *
 * Play throttles the sheet itself and gives no way to know whether it was
 * shown, so the gating here is not about the quota. It is about not asking
 * someone who has played twice and has nothing to say yet, and not asking the
 * same person again a month later as if the first time had not happened.
 */
object ReviewPrompt {

    /** Rounds a player has to have finished before the question is fair. */
    private const val MIN_ROUNDS = 3

    /** Roughly a season between two asks. Play's own quota is stricter. */
    private const val COOLDOWN_MS = 90L * 24 * 60 * 60 * 1000

    fun shouldAsk(roundsPlayed: Int, askedAt: Long, now: Long): Boolean =
        roundsPlayed >= MIN_ROUNDS && now - askedAt >= COOLDOWN_MS

    /**
     * Request and launch the flow. Every failure path is silent on purpose:
     * this is a courtesy the app asks for, not a step the player is in.
     */
    fun ask(activity: Activity, analytics: Analytics) {
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (!request.isSuccessful) return@addOnCompleteListener
            analytics.log(Event.REVIEW_PROMPTED, emptyMap())
            runCatching { manager.launchReviewFlow(activity, request.result) }
        }
    }
}
