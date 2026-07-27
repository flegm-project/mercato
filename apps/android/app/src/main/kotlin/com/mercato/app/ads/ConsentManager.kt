package com.mercato.app

import android.app.Activity
import android.content.Context
import android.preference.PreferenceManager
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.mercato_ffi.AdConsent
import uniffi.mercato_ffi.Game

/**
 * Google UMP consent flow, mapped onto the shared core contract
 * (`Game.setAdConsent`; the core is the single source for
 * `ad_personalization_allowed`, same contract as iOS).
 *
 * Where GDPR applies, the UMP form is the legal consent surface: it shows at
 * first launch, writes the TCF string, and the app derives
 * personalised/non-personalised from the TCF purposes below. Where UMP is
 * not required, the app's own consent screen (spec screen 03) remains the
 * choice surface, since refusing personalisation must stay possible
 * everywhere. The GMA SDK also reads the TCF string on its own; our npa flag
 * from the core is the conservative overlay Google documents ("most
 * restrictive wins").
 */
class ConsentManager(
    private val gameProvider: () -> Game,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {

    /** True once UMP itself collected consent (GDPR surface shown). The
     *  onboarding then skips the app's own consent screen. */
    private val _handledByUmp = MutableStateFlow(false)
    val handledByUmp: StateFlow<Boolean> = _handledByUmp.asStateFlow()

    /**
     * Run the UMP flow: refresh consent info, show the form when required,
     * then push the outcome into the core. Call at every activity launch;
     * it is a silent no-op outside GDPR scopes.
     */
    fun gather(activity: Activity, onDone: () -> Unit = {}) {
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    apply(activity, info)
                    onDone()
                }
            },
            { _ ->
                // Offline or misconfigured: keep the last known consent.
                onDone()
            },
        )
    }

    /** Whether Settings must expose the UMP privacy options form. */
    fun privacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context)
            .privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    /** Reopen the UMP form from Settings, then re-derive the outcome. */
    fun showPrivacyOptions(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            apply(activity, UserMessagingPlatform.getConsentInformation(activity))
            onDismissed()
        }
    }

    private fun apply(context: Context, info: ConsentInformation) {
        val consent = when (info.consentStatus) {
            // No GDPR framework applies: the app's own consent screen (or
            // its stored answer) stays authoritative; change nothing here.
            ConsentInformation.ConsentStatus.NOT_REQUIRED -> {
                _handledByUmp.value = false
                return
            }
            ConsentInformation.ConsentStatus.OBTAINED -> {
                _handledByUmp.value = true
                if (tcfAllowsPersonalizedAds(context)) AdConsent.PERSONALIZED
                else AdConsent.NON_PERSONALIZED
            }
            // REQUIRED / UNKNOWN without a completed form: most conservative.
            else -> AdConsent.NON_PERSONALIZED
        }
        // Off the main thread: at first launch this can race the corpus
        // parsing, and the Game accessor must not block the UI.
        scope.launch {
            gameProvider().setAdConsent(consent)
            prefs.setConsent(
                if (consent == AdConsent.PERSONALIZED) "personalized" else "non_personalized"
            )
        }
    }

    /**
     * Google's documented TCF requirements for personalised ads
     * (support.google.com/admob/answer/9760862): consent on purposes 1, 3, 4
     * and consent or legitimate interest on purposes 2, 7, 9, 10. Purpose N
     * lives at zero-based index N-1 of the bit-strings UMP writes to the
     * default SharedPreferences.
     */
    @Suppress("DEPRECATION") // PreferenceManager: where the IABTCF keys live, per spec.
    private fun tcfAllowsPersonalizedAds(context: Context): Boolean {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        return tcfAllowsPersonalized(
            sp.getString("IABTCF_PurposeConsents", "") ?: "",
            sp.getString("IABTCF_PurposeLegitimateInterests", "") ?: "",
        )
    }

    companion object {
        /** Pure TCF purpose-bit rule, unit-tested in TcfPersonalizationTest. */
        fun tcfAllowsPersonalized(consents: String, legitimate: String): Boolean {
            fun granted(bits: String, purpose: Int) =
                bits.length >= purpose && bits[purpose - 1] == '1'
            fun grantedEither(purpose: Int) =
                granted(consents, purpose) || granted(legitimate, purpose)
            return granted(consents, 1) && granted(consents, 3) && granted(consents, 4) &&
                grantedEither(2) && grantedEither(7) && grantedEither(9) && grantedEither(10)
        }
    }
}
