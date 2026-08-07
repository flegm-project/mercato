package com.mercato.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** The three cues, named after the moment rather than the sound. */
enum class Cue(val asset: String) {
    CORRECT("sounds/correct.wav"),
    WRONG("sounds/wrong.wav"),
    ROUND_OVER("sounds/roundover.wav"),
}

/**
 * Plays the game's cues, or does not, depending on the sound setting.
 *
 * [SoundPool] rather than MediaPlayer: three files of a few tens of
 * kilobytes, decoded once at startup and fired with no latency, which is what
 * an answer cue needs. A MediaPlayer per cue would prepare on each play and
 * arrive after the card had already turned green.
 *
 * `USAGE_GAME` with `CONTENT_TYPE_SONIFICATION` is the pair that makes the
 * system treat these as game feedback: they duck under a call, mix with the
 * player's own music instead of stopping it, and follow the media volume.
 */
class Sounds(context: Context, private val prefs: Prefs, private val scope: CoroutineScope) {

    private val pool = SoundPool.Builder()
        // Two answer cues can overlap when a tap lands on the tail of the one
        // before it; three streams covers that without ever cutting one off.
        .setMaxStreams(3)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = ConcurrentHashMap<Cue, Int>()

    /**
     * Completes once SoundPool has answered for every cue, decoded or failed.
     *
     * Playing an id it has not finished decoding is dropped without a word,
     * and the first version of this class only tested for that and gave up,
     * which is not a fix: it turned a silent drop into a deliberate one. The
     * first answer cue of a session was missing every single time, because
     * `AppGraph.sounds` is lazy and the very first thing to touch it is the
     * play() that should already be making a noise. Loading started at the
     * instant the sound was due.
     *
     * So play() waits here instead of testing. AppGraph.warmUp now builds
     * this object at launch too, so by the time anyone answers a question the
     * wait is already over and costs nothing.
     */
    private val decoded = CompletableDeferred<Unit>()
    private val pending = AtomicInteger(Cue.entries.size)

    init {
        pool.setOnLoadCompleteListener { _, _, _ -> settle() }
        scope.launch(Dispatchers.IO) {
            for (cue in Cue.entries) {
                val opened = runCatching {
                    context.assets.openFd(cue.asset).use { ids[cue] = pool.load(it, 1) }
                }.isSuccess
                // An asset that cannot be opened never reaches the listener,
                // so it has to be counted here or `decoded` never completes
                // and every cue waits out the timeout instead.
                if (!opened) settle()
            }
        }
    }

    private fun settle() {
        if (pending.decrementAndGet() == 0) decoded.complete(Unit)
    }

    /**
     * Play [cue] unless the player has turned sound off. The preference is
     * read at the moment of playing rather than cached, so switching it in
     * Settings takes effect on the next answer without any wiring between the
     * two screens.
     */
    fun play(cue: Cue) {
        scope.launch {
            if (!prefs.sound.first()) return@launch
            // Bounded: a cue that never decodes must not leave a coroutine
            // waiting for the life of the process. Three seconds is far past
            // what decoding these files takes and far under what a player
            // would sit through, so it only ever fires when something is
            // actually broken.
            withTimeoutOrNull(DECODE_TIMEOUT_MS) { decoded.await() } ?: return@launch
            val id = ids[cue] ?: return@launch
            pool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }

    private companion object {
        const val DECODE_TIMEOUT_MS = 3_000L
    }
}
