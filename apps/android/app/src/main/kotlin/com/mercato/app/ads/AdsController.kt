package com.mercato.app

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.mercato.design.DesignTokens
import uniffi.mercato_ffi.AdPlacement
import uniffi.mercato_ffi.Game

/**
 * Thin bridge between the shared ads gate (mercato_core::ads, reached through
 * the FFI [Game]) and the Google Mobile Ads SDK. Every visual slot asks the
 * gate first; the SDK is only touched when the gate says yes, so remove-ads
 * users never even send an ad request.
 */
class AdsController(private val context: Context, private val game: Game) {

    private var interstitial: InterstitialAd? = null

    private companion object {
        val TEST_DEVICES: List<String> =
            BuildConfig.ADMOB_TEST_DEVICES.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }

    /**
     * Sync the SDK's personalisation state from the core before building a
     * request. The publisher privacy state is the modern npa equivalent;
     * where UMP wrote a TCF string the SDK also reads it on its own, and
     * Google applies whichever signal is the most restrictive.
     */
    fun request(): AdRequest {
        val state =
            if (game.adPersonalizationAllowed()) {
                RequestConfiguration.PublisherPrivacyPersonalizationState.DEFAULT
            } else {
                RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
            }
        MobileAds.setRequestConfiguration(
            MobileAds.getRequestConfiguration()
                .toBuilder()
                .setPublisherPrivacyPersonalizationState(state)
                // Empty in every build that did not opt in through
                // admob.properties, and an empty list is the SDK's own
                // default, so this line is inert in what reaches Play. See
                // the comment next to admobTestDevices in build.gradle.kts.
                .setTestDeviceIds(TEST_DEVICES)
                .build()
        )
        return AdRequest.Builder().build()
    }

    fun shouldShow(placement: AdPlacement): Boolean = game.shouldShowAd(placement)

    /** Keep one interstitial preloaded whenever the gate could allow it. */
    fun preloadInterstitial() {
        if (interstitial != null || game.adsRemoved()) return
        InterstitialAd.load(
            context,
            BuildConfig.ADMOB_INTERSTITIAL_ID,
            request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                }
            },
        )
    }

    /**
     * Show the round-break interstitial if the gate allows it and one is
     * loaded. Returns true when an ad was actually presented; the caller
     * navigates to the recap either way ([onDismissed] always fires).
     */
    fun maybeShowInterstitial(activity: Activity, onDismissed: () -> Unit): Boolean {
        val ad = interstitial
        if (ad == null || !game.shouldShowAd(AdPlacement.INTERSTITIAL)) {
            onDismissed()
            return false
        }
        interstitial = null
        ad.fullScreenContentCallback =
            object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    preloadInterstitial()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(
                    error: com.google.android.gms.ads.AdError
                ) {
                    onDismissed()
                }
            }
        game.recordInterstitialShown()
        ad.show(activity)
        return true
    }
}

/** A banner slot that renders nothing at all when the gate says no. */
@Composable
private fun GateAdView(
    ads: AdsController,
    placement: AdPlacement,
    adUnitId: String,
    size: AdSize,
    modifier: Modifier = Modifier,
) {
    if (!ads.shouldShow(placement)) return
    Box(modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(size)
                    this.adUnitId = adUnitId
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            // The ink hatch background behind the view keeps
                            // the slot readable when no fill arrives.
                            visibility = View.INVISIBLE
                        }

                        override fun onAdLoaded() {
                            visibility = View.VISIBLE
                        }
                    }
                    loadAd(ads.request())
                }
            }
        )
    }
}

/** 320x50 banner above the tab bar on menu screens. Never during a question. */
@Composable
fun MenuBanner(ads: AdsController, modifier: Modifier = Modifier) {
    GateAdView(
        ads,
        AdPlacement.BANNER,
        BuildConfig.ADMOB_BANNER_ID,
        AdSize.BANNER,
        // iOS BannerSlot draws a bare 320x50 and lets the blue gradient show
        // through on wider screens. A full-width ink bar was the loudest
        // difference on Home, and worst before the ad had loaded.
        //
        // The slot is full width and the banner centres inside it, which is
        // what `.frame(maxWidth: .infinity)` does around every iOS call site.
        // Sizing the slot itself to 320 left it ranged against the gutter,
        // because a Column aligns its children to the start.
        modifier
            .fillMaxWidth()
            .height(50.dp),
    )
}

/** Static full-width strip below the transfer card, in game. */
@Composable
fun SponsorBoard(ads: AdsController, modifier: Modifier = Modifier) {
    GateAdView(
        ads,
        AdPlacement.SPONSOR_BOARD,
        BuildConfig.ADMOB_SPONSOR_ID,
        AdSize.BANNER,
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(DesignTokens.Color.ink),
    )
}

/** 300x250 rectangle on the recap screen. */
@Composable
fun RecapRectangle(ads: AdsController, modifier: Modifier = Modifier) {
    GateAdView(
        ads,
        AdPlacement.RECTANGLE,
        BuildConfig.ADMOB_RECTANGLE_ID,
        AdSize.MEDIUM_RECTANGLE,
        modifier
            .fillMaxWidth()
            .height(250.dp),
    )
}
