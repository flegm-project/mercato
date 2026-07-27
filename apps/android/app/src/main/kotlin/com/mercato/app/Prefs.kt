package com.mercato.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import uniffi.mercato_ffi.AdConsent

private val Context.dataStore by preferencesDataStore(name = "mercato")

/**
 * Small preference store: first-run flag, consent, toggles, lifetime stats.
 * Session score stays in the Rust core; only what must survive a process
 * death lives here.
 */
class Prefs(private val context: Context) {

    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val consent = stringPreferencesKey("ad_consent")
        val adsRemoved = booleanPreferencesKey("ads_removed")
        val sound = booleanPreferencesKey("sound")
        val vibration = booleanPreferencesKey("vibration")
        val notifications = booleanPreferencesKey("notifications")
        val roundsPlayed = intPreferencesKey("rounds_played")
        val bestScore = intPreferencesKey("best_score")
        val bestStreak = intPreferencesKey("best_streak")
        val correct = intPreferencesKey("answers_correct")
        val answered = intPreferencesKey("answers_total")
    }

    data class Stats(
        val roundsPlayed: Int,
        val bestScore: Int,
        val bestStreak: Int,
        val correct: Int,
        val answered: Int,
    )

    val onboarded: Flow<Boolean> = context.dataStore.data.map { it[Keys.onboarded] ?: false }
    val consent: Flow<String?> = context.dataStore.data.map { it[Keys.consent] }
    val sound: Flow<Boolean> = context.dataStore.data.map { it[Keys.sound] ?: true }
    val vibration: Flow<Boolean> = context.dataStore.data.map { it[Keys.vibration] ?: true }
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

    suspend fun setOnboarded() = context.dataStore.edit { it[Keys.onboarded] = true }
    suspend fun resetOnboarding() = context.dataStore.edit { it[Keys.onboarded] = false }

    suspend fun setConsent(value: String) = context.dataStore.edit { it[Keys.consent] = value }
    suspend fun setAdsRemoved(value: Boolean) =
        context.dataStore.edit { it[Keys.adsRemoved] = value }

    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[Keys.sound] = value }
    suspend fun setVibration(value: Boolean) = context.dataStore.edit { it[Keys.vibration] = value }
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
        runBlocking { context.dataStore.data.first()[Keys.adsRemoved] ?: false }

    fun consentBlocking(): AdConsent =
        runBlocking { consentFromPref(context.dataStore.data.first()[Keys.consent]) }
}
