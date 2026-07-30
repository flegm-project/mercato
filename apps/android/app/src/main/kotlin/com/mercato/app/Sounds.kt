package com.mercato.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    private val ids = mutableMapOf<Cue, Int>()

    /**
     * Whether a cue has finished decoding. Playing an id SoundPool has not
     * loaded yet is silently dropped, which on a cold start would swallow the
     * first answer of the first round.
     */
    private val ready = mutableSetOf<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) ready.add(id) }
        scope.launch(Dispatchers.IO) {
            for (cue in Cue.entries) {
                runCatching {
                    context.assets.openFd(cue.asset).use { ids[cue] = pool.load(it, 1) }
                }
            }
        }
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
            val id = ids[cue] ?: return@launch
            if (id !in ready) return@launch
            pool.play(id, 1f, 1f, 1, 0, 1f)
        }
    }
}
