package com.mercato.app

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mercato.analytics.Event
import com.mercato.analytics.Param
import uniffi.mercato_ffi.AdConsent

/**
 * Usage measurement and crash reporting, behind one small surface.
 *
 * Every event name and parameter name comes from `com.mercato.analytics`,
 * generated from `design/analytics.json` for both platforms at once. Nothing
 * here spells a string, which is what stops this app and the iOS one from
 * drifting into two datasets that look like one.
 *
 * The whole thing is a no-op when Firebase has no credentials.
 * `google-services.json` is per-account and is not in the repo, so a clone has
 * to build and run without it; `FirebaseApp.getInstance()` throwing is the
 * signal, and it is caught once at construction rather than on every event.
 */
class Analytics(context: Context) {

    private val firebase: FirebaseAnalytics? = runCatching {
        FirebaseApp.getInstance()
        FirebaseAnalytics.getInstance(context)
    }.getOrNull()

    private val crashlytics: FirebaseCrashlytics? = runCatching {
        FirebaseApp.getInstance()
        FirebaseCrashlytics.getInstance()
    }.getOrNull()

    /** True when a Firebase project is configured. Reported once at startup. */
    val enabled: Boolean get() = firebase != null

    /**
     * Mirror the ad consent the UMP flow already resolved onto Firebase's
     * consent signals.
     *
     * The app has exactly one consent surface and it must stay that way: a
     * second dialog asking about measurement would be both worse for the
     * player and inconsistent with what the first one promised. So the same
     * decision drives both. A refusal means non-personalised ads *and*
     * measurement without ad identifiers, which is what
     * `ad_storage`/`ad_user_data`/`ad_personalization` denied buys.
     *
     * `analytics_storage` stays granted either way: counting rounds is what
     * the privacy policy describes, it carries no advertising identifier, and
     * denying it would leave the app with no idea whether it works at all.
     */
    fun setConsent(consent: AdConsent) {
        val ads = if (consent == AdConsent.PERSONALIZED) {
            FirebaseAnalytics.ConsentStatus.GRANTED
        } else {
            FirebaseAnalytics.ConsentStatus.DENIED
        }
        firebase?.setConsent(
            mapOf(
                FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to FirebaseAnalytics.ConsentStatus.GRANTED,
                FirebaseAnalytics.ConsentType.AD_STORAGE to ads,
                FirebaseAnalytics.ConsentType.AD_USER_DATA to ads,
                FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to ads,
            )
        )
    }

    /**
     * Log [event]. Parameters are keyed by the generated [Param] enum, so a
     * call site cannot invent one.
     *
     * Values are Long or String only. Analytics stores numbers as doubles and
     * strings verbatim; a boolean would arrive as "true"/"false" on one
     * platform and 1/0 on the other, so booleans are written as 1 and 0 at the
     * call site and the spec says so.
     */
    fun log(event: Event, params: Map<Param, Any> = emptyMap()) {
        val fb = firebase ?: return
        val bundle = Bundle()
        for ((key, value) in params) {
            when (value) {
                is Int -> bundle.putLong(key.key, value.toLong())
                is Long -> bundle.putLong(key.key, value)
                is Boolean -> bundle.putLong(key.key, if (value) 1L else 0L)
                else -> bundle.putString(key.key, value.toString().take(100))
            }
        }
        fb.logEvent(event.key, bundle)
    }

    /**
     * Record something that went wrong but did not crash. The app swallows a
     * number of failures on purpose (a store that will not load, an FFI call
     * that returns null), and those are exactly the ones nobody ever hears
     * about otherwise.
     */
    fun recordError(where: String, error: Throwable) {
        crashlytics?.log(where)
        crashlytics?.recordException(error)
    }
}
