package com.mercato.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import uniffi.mercato_ffi.AdConsent

private val Context.dataStore by preferencesDataStore(name = Prefs.MAIN_STORE)

// The remove-ads entitlement lives alone in its own DataStore so the backup
// rules can drop exactly this file and nothing else. It carries no migration
// from the old shared store on purpose: a local copy cannot tell a genuine
// purchase from a restored backup, so seeding it from disk would recreate the
// very free-forever exploit this split closes. BillingManager.restore() runs
// at every launch and resume and re-grants it from Play, so a real buyer who
// updates offline only sees ads until the next online start.
private val Context.entitlementStore by preferencesDataStore(name = Prefs.ENTITLEMENT_STORE)

/**
 * Small preference store: first-run flag, consent, toggles, lifetime stats.
 * Session score stays in the Rust core; only what must survive a process
 * death lives here.
 */
class Prefs(private val context: Context) {

    companion object {
        /** Progression, stats, onboarding, consent, settings: backed up. */
        const val MAIN_STORE = "mercato"

        /** The remove-ads entitlement only: excluded from every backup path. */
        const val ENTITLEMENT_STORE = "entitlement"
    }

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val consent = stringPreferencesKey("ad_consent")
        val adsRemoved = booleanPreferencesKey("ads_removed")
        val sound = booleanPreferencesKey("sound")
        val notifications = booleanPreferencesKey("notifications")
        val roundsPlayed = intPreferencesKey("rounds_played")
        val bestScore = intPreferencesKey("best_score")
        val bestStreak = intPreferencesKey("best_streak")
        val correct = intPreferencesKey("answers_correct")
        val answered = intPreferencesKey("answers_total")

        // Daily challenge. The key is the ISO date of the last one finished,
        // which is both the "already played today" flag and the anchor the
        // streak counts from.
        val dailyKey = stringPreferencesKey("daily_last_key")
        val dailyStreak = intPreferencesKey("daily_streak")
        val dailyCorrect = intPreferencesKey("daily_correct")
        /** One character per question, '1' right and '0' wrong, in order. */
        val dailyGrid = stringPreferencesKey("daily_grid")

        /** When the in-app review sheet was last requested, epoch millis. */
        val reviewAsked = longPreferencesKey("review_asked_at")
    }

    data class Stats(
        val roundsPlayed: Int,
        val bestScore: Int,
        val bestStreak: Int,
        val correct: Int,
        val answered: Int,
    )

    /** The last finished daily challenge, or null when there is none. */
    data class Daily(
        val key: String,
        val streak: Int,
        val correct: Int,
        val grid: String,
    )

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboarded] ?: false }
    val consent: Flow<String?> = context.dataStore.data.map { it[Keys.consent] }
    val sound: Flow<Boolean> = context.dataStore.data.map { it[Keys.sound] ?: true }
    val notifications: Flow<Boolean> = context.dataStore.data.map { it[Keys.notifications] ?: false }
    val stats: Flow<Stats> = context.dataStore.data.map {
        Stats(
            roundsPlayed = it[Keys.roundsPlayed] ?: 0,
            bestScore = it[Keys.bestScore] ?: 0,
            bestStreak = it[Keys.bestStreak] ?: 0,
            correct = it[Keys.correct] ?: 0,
            answered = it[Keys.answered] ?: 0,
        )
    }

    val daily: Flow<Daily?> = context.dataStore.data.map { p ->
        p[Keys.dailyKey]?.let {
            Daily(
                key = it,
                streak = p[Keys.dailyStreak] ?: 1,
                correct = p[Keys.dailyCorrect] ?: 0,
                grid = p[Keys.dailyGrid] ?: "",
            )
        }
    }

    /**
     * Record a finished daily. The streak continues only when the previous
     * one was yesterday's: a player who skips a day starts again at 1, which
     * is the rule that makes a streak worth keeping.
     */
    suspend fun recordDaily(key: String, previousKey: String, correct: Int, grid: String) {
        context.dataStore.edit {
            val last = it[Keys.dailyKey]
            if (last == key) return@edit // one attempt a day, already taken
            it[Keys.dailyStreak] = if (last == previousKey) (it[Keys.dailyStreak] ?: 0) + 1 else 1
            it[Keys.dailyKey] = key
            it[Keys.dailyCorrect] = correct
            it[Keys.dailyGrid] = grid
        }
    }

    val reviewAskedAt: Flow<Long> = context.dataStore.data.map { it[Keys.reviewAsked] ?: 0L }

    suspend fun setReviewAsked(millis: Long) =
        context.dataStore.edit { it[Keys.reviewAsked] = millis }

    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.onboarded] = true }
    suspend fun resetOnboarding() = context.dataStore.edit { it[Keys.onboarded] = false }

    suspend fun setConsent(value: String) = context.dataStore.edit { it[Keys.consent] = value }

    // Entitlement store, not the main one: this key must never end up in a
    // backup, or restoring the backup would grant remove-ads for free.
    suspend fun setAdsRemoved(value: Boolean) =
        context.entitlementStore.edit { it[Keys.adsRemoved] = value }

    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setNotifications(value: Boolean) =
        context.dataStore.edit { it[Keys.notifications] = value }

    /** Fold one finished round into the lifetime stats. */
    suspend fun recordRound(score: Int, bestStreak: Int, correct: Int, answered: Int) {
        context.dataStore.edit {
            it[Keys.roundsPlayed] = (it[Keys.roundsPlayed] ?: 0) + 1
            it[Keys.bestScore] = maxOf(it[Keys.bestScore] ?: 0, score)
            it[Keys.bestStreak] = maxOf(it[Keys.bestStreak] ?: 0, bestStreak)
            it[Keys.correct] = (it[Keys.correct] ?: 0) + correct
            it[Keys.answered] = (it[Keys.answered] ?: 0) + answered
        }
    }

    // The Rust Game is created synchronously at first use and needs the last
    // known entitlement/consent right away; these two reads are the only
    // blocking calls, on tiny data.
    fun adsRemovedBlocking(): Boolean =
        runBlocking { context.entitlementStore.data.first()[Keys.adsRemoved] ?: false }

    fun consentBlocking(): AdConsent =
        runBlocking { consentFromPref(context.dataStore.data.first()[Keys.consent]) }
}
