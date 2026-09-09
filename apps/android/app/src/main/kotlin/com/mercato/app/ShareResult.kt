package com.mercato.app

import android.content.Context
import android.content.Intent

/**
 * The shareable result: a grid of squares that says how the round went
 * without giving a single answer away.
 *
 * That last part is the whole point. A result someone can post the moment
 * they finish, that spoils nothing for the friend who has not played yet, is
 * the mechanism behind every daily word game: the boast and the invitation
 * are the same message.
 */
object ShareResult {

    /** The store link, tagged so acquisitions from shares are countable. */
    const val STORE_URL =
        "https://play.google.com/store/apps/details?id=com.flegm.mercato&listing=share"

    private const val HIT = "🟩" // green square
    private const val MISS = "⬜" // white square

    /**
     * Build the message.
     *
     * [results] is one entry per question in the order they were asked; an
     * unanswered question (the round was quit) is a miss, because a grid that
     * silently drops questions reports a better score than the round earned.
     */
    fun text(
        title: String,
        results: List<Boolean>,
        correct: Int,
        total: Int,
        streakWord: String,
        streak: Int,
    ): String {
        val grid = results.joinToString("") { if (it) HIT else MISS }
        val line = buildString {
            append(correct)
            append('/')
            append(total)
            if (streak > 1) {
                append(" · ")
                append(streakWord)
                append(' ')
                append(streak)
            }
        }
        return "$title\n$grid\n$line\n$STORE_URL"
    }

    /** Hand the message to the system sheet. */
    fun send(context: Context, chooserTitle: String, message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(
            Intent.createChooser(intent, chooserTitle)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
