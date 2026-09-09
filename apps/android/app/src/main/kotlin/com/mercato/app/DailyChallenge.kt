package com.mercato.app

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The daily challenge: one round a day, identical for every player.
 *
 * The seed comes from the calendar date through the same FNV-1a hash the Rust
 * core uses for its own seeding, so a given day plans the same ten questions
 * on every device. No server, no clock sync, nothing that would cost the app
 * its offline promise: two players comparing grids are comparing the same
 * questions because both derived them from the date they are living in.
 *
 * The day boundary is the device's local midnight rather than UTC. A player
 * in Sao Paulo starting their day should get the new challenge when their day
 * starts, and the alternative, a shared UTC boundary, moves the reset to the
 * middle of the evening for half the world.
 */
object DailyChallenge {

    /** Day 1. Only the display number depends on it; the seed does not. */
    private val EPOCH: LocalDate = LocalDate.of(2026, 9, 10)

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    /** The persisted form of a day, and what the seed is derived from. */
    fun key(date: LocalDate): String = date.toString()

    /** The challenge number shown to players, counting from [EPOCH]. */
    fun number(date: LocalDate): Int = (ChronoUnit.DAYS.between(EPOCH, date) + 1).toInt()

    /**
     * FNV-1a over UTF-16 code units, the Kotlin port of `hash_str` in
     * mercato_core::rng. Kotlin's UInt arithmetic wraps, which is what makes
     * it match the Rust `wrapping_mul`.
     */
    fun seed(date: LocalDate): UInt {
        var h = 2166136261u
        for (c in key(date)) {
            h = h xor c.code.toUInt()
            h *= 16777619u
        }
        return h
    }

    /**
     * Yesterday's key, so a finished challenge can tell a streak that
     * continues from one that restarts.
     */
    fun previousKey(date: LocalDate): String = key(date.minusDays(1))
}
