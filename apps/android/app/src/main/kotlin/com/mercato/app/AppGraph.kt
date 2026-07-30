package com.mercato.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import uniffi.mercato_ffi.AdConsent
import uniffi.mercato_ffi.Game

/**
 * Process-wide services. The Rust [Game] parses the CSV dataset once and is
 * shared by every screen; the ads gate lives inside it (see mercato_core::ads),
 * so ad decisions survive round restarts.
 */
class AppGraph(private val context: Context, private val scope: CoroutineScope) {

    val prefs = Prefs(context)

    /**
     * The FFI facade. Created on first access after [warmUp] copied the
     * bundled CSVs; `lazy` keeps creation off the app's critical startup path.
     */
    val game: Game by lazy {
        Game(stageDataset().absolutePath).also { g ->
            // Restore what the store and the consent flow decided last time.
            g.setAdsRemoved(prefs.adsRemovedBlocking())
            g.setAdConsent(prefs.consentBlocking())
        }
    }

    val ads by lazy { AdsController(context, game) }

    /** The three game cues, gated by the sound setting. */
    val sounds by lazy { Sounds(context, prefs, scope) }

    /** Usage measurement and crash reporting. A no-op without credentials. */
    val analytics by lazy { Analytics(context) }

    /** Play Billing bridge for the remove-ads entitlement. */
    val billing by lazy { BillingManager(context, { game }, prefs, scope, { analytics }) }

    /** Google UMP consent flow, mapped onto the core's consent contract. */
    val consent by lazy { ConsentManager({ game }, prefs, scope, { analytics }) }

    /** Called from Application.onCreate on a background dispatcher. */
    suspend fun warmUp() = withContext(Dispatchers.IO) {
        stageDataset()
        game // force corpus parsing off the main thread
    }

    /**
     * The Rust loader reads a directory of CSV files, so the assets are copied
     * to the app's files dir once per install (assets have no filesystem path).
     */
    private fun stageDataset(): File {
        val dir = File(context.filesDir, "data")
        dir.mkdirs()
        context.assets.list("data")?.forEach { name ->
            val out = File(dir, name)
            if (!out.exists()) {
                context.assets.open("data/$name").use { src ->
                    out.outputStream().use { dst -> src.copyTo(dst) }
                }
            }
        }
        return dir
    }
}

/** Maps the persisted consent string back to the FFI enum. */
fun consentFromPref(value: String?): AdConsent = when (value) {
    "personalized" -> AdConsent.PERSONALIZED
    "non_personalized" -> AdConsent.NON_PERSONALIZED
    else -> AdConsent.UNKNOWN
}
