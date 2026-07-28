package com.mercato.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercato.app.AppGraph
import com.mercato.app.BuildConfig
import com.mercato.app.MenuBanner
import com.mercato.app.Prefs
import com.mercato.app.R
import com.mercato.design.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.mercato_ffi.AdConsent
import uniffi.mercato_ffi.GameMode

/** 01 Splash: logo and a 1.3s loading bar, then auto-advance. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1300)
        onDone()
    }
    ScreenColumn {
        Spacer(Modifier.weight(1f))
        Text(
            "MERCATO",
            style = typeStyle(DesignTokens.Type.logo, DesignTokens.Color.ivory)
                .copy(fontSize = 52.sp),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.xl)
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(DesignTokens.Color.blueNight, RoundedCornerShape(4.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(0.66f)
                    .height(8.dp)
                    .background(DesignTokens.Color.yellow, RoundedCornerShape(4.dp))
            )
        }
        Spacer(Modifier.weight(1.2f))
    }
}

/** 02 Onboarding: three panes, dots, Skip, NEXT then START. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val panes = listOf(
        Triple(R.string.ob_0_a, R.string.ob_0_t, R.string.ob_0_b),
        Triple(R.string.ob_1_a, R.string.ob_1_t, R.string.ob_1_b),
        Triple(R.string.ob_2_a, R.string.ob_2_t, R.string.ob_2_b),
    )
    val pager = rememberPagerState { panes.size }
    val scope = rememberCoroutineScope()

    ScreenColumn {
        Row(Modifier.fillMaxWidth().padding(top = DesignTokens.Space.lg)) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.obSkip),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory),
                modifier = Modifier.clickable(onClick = onDone),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val (art, title, body) = panes[page]
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .adHatch()
                        .border(
                            DesignTokens.Border.hairline,
                            DesignTokens.Color.ink,
                            RoundedCornerShape(DesignTokens.Radius.card),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    CapsLabel(stringResource(art))
                }
                Gap(DesignTokens.Space.xl)
                Text(
                    stringResource(title),
                    style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
                    textAlign = TextAlign.Center,
                )
                Gap(DesignTokens.Space.sm)
                Text(
                    stringResource(body),
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.copy(alpha = 0.85f),
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(panes.size) { i ->
                Box(
                    Modifier
                        .padding(3.dp)
                        .size(8.dp)
                        .background(
                            if (i == pager.currentPage) DesignTokens.Color.yellow
                            else DesignTokens.Color.ivory.copy(alpha = 0.35f),
                            CircleShape,
                        )
                )
            }
        }
        val last = pager.currentPage == panes.lastIndex
        InkButton(
            stringResource(if (last) R.string.obStart else R.string.obNext),
            ButtonStyle.Primary,
            Modifier.padding(bottom = DesignTokens.Space.xl),
        ) {
            if (last) onDone()
            else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
    }
}

/** 03 Ad consent: ivory card, three bullets, accept-all or non-personalised. */
@Composable
fun ConsentScreen(graph: AppGraph, fromSettings: Boolean, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()

    fun choose(personalized: Boolean) {
        graph.game.setAdConsent(
            if (personalized) AdConsent.PERSONALIZED else AdConsent.NON_PERSONALIZED
        )
        scope.launch {
            graph.prefs.setConsent(if (personalized) "personalized" else "non_personalized")
            if (!fromSettings) graph.prefs.setOnboarded()
            onDone()
        }
    }

    ScreenColumn {
        Spacer(Modifier.weight(1f))
        Column(
            Modifier
                .fillMaxWidth()
                .background(DesignTokens.Color.ivory, RoundedCornerShape(DesignTokens.Radius.card))
                .border(
                    DesignTokens.Border.heavy,
                    DesignTokens.Color.ink,
                    RoundedCornerShape(DesignTokens.Radius.card),
                )
                .padding(DesignTokens.Space.xl),
        ) {
            Text(
                stringResource(R.string.cnTitle),
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ink),
            )
            Gap(DesignTokens.Space.sm)
            Text(
                stringResource(R.string.cnBody),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ink),
            )
            Gap(DesignTokens.Space.md)
            listOf(R.string.cnPoints_0, R.string.cnPoints_1, R.string.cnPoints_2).forEach {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text("• ", style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ink))
                    Text(
                        stringResource(it),
                        style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted),
                    )
                }
            }
            Gap(DesignTokens.Space.lg)
            InkButton(stringResource(R.string.cnAccept), ButtonStyle.Primary) { choose(true) }
            InkButton(stringResource(R.string.cnRefuse), ButtonStyle.Secondary) { choose(false) }
            Text(
                stringResource(R.string.cnFoot),
                style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.muted),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/** 04 Home: logo, the two mode buttons pushed to the bottom, banner + tabs. */
@Composable
fun HomeScreen(graph: AppGraph, onPlay: (GameMode) -> Unit, onProfile: () -> Unit) {
    LaunchedEffect(Unit) { graph.ads.preloadInterstitial() }
    ScreenColumn {
        Spacer(Modifier.weight(1f))
        Text(
            "MERCATO",
            style = typeStyle(DesignTokens.Type.logo, DesignTokens.Color.ivory)
                .copy(fontSize = 46.sp),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        ModeButton(R.string.l1) { onPlay(GameMode.EASY) }
        Gap(DesignTokens.Space.md)
        ModeButton(R.string.l3) { onPlay(GameMode.HARDCORE) }
        Gap(DesignTokens.Space.lg)
        MenuBanner(graph.ads)
        Gap(DesignTokens.Space.sm)
        MercatoTabBar(
            tabs = listOf(stringResource(R.string.tPlay), stringResource(R.string.tProfile)),
            selected = 0,
        ) { if (it == 1) onProfile() }
        Gap(DesignTokens.Space.md)
    }
}

/** Mode buttons carry only the mode name (iOS parity, no chips). */
@Composable
private fun ModeButton(title: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.blueNight, RoundedCornerShape(DesignTokens.Radius.large))
            .border(
                DesignTokens.Border.heavy,
                DesignTokens.Color.ink,
                RoundedCornerShape(DesignTokens.Radius.large),
            )
            .clickable(onClick = onClick)
            .padding(vertical = DesignTokens.Space.xl, horizontal = DesignTokens.Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(title),
            style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.yellow),
        )
    }
}

/** 10 Profile: four lifetime stats and the door to Settings. */
@Composable
fun ProfileScreen(graph: AppGraph, onPlayTab: () -> Unit, onSettings: () -> Unit) {
    val stats by graph.prefs.stats.collectAsState(initial = Prefs.Stats(0, 0, 0, 0, 0))
    ScreenColumn {
        Gap(DesignTokens.Space.xl)
        Text(
            stringResource(R.string.profile),
            style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
        )
        // Banner at the top, far from the tab bar, so a mistap near the
        // bottom never lands on an ad (iOS parity).
        Gap(DesignTokens.Space.md)
        MenuBanner(graph.ads)
        Spacer(Modifier.weight(1f))
        if (stats.roundsPlayed == 0) {
            // A column of zeros reads as broken: invite the first round
            // instead (iOS parity).
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        DesignTokens.Color.blueNight,
                        RoundedCornerShape(DesignTokens.Radius.medium),
                    )
                    .padding(DesignTokens.Space.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.statsEmptyTitle),
                    style = typeStyle(DesignTokens.Type.answer, DesignTokens.Color.ivory),
                    textAlign = TextAlign.Center,
                )
                Gap(DesignTokens.Space.xs)
                Text(
                    stringResource(R.string.statsEmptyBody),
                    // Soft ivory at 72%, readable on the dark card (iOS parity).
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.copy(alpha = 0.72f),
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val accuracy =
                if (stats.answered == 0) "-"
                else "${(stats.correct * 100) / stats.answered}%"
            StatRow(R.string.stPlayed, "${stats.roundsPlayed}")
            StatRow(R.string.stBest, "${stats.bestScore}")
            StatRow(R.string.stStreak, "${stats.bestStreak}")
            StatRow(R.string.stAcc, accuracy)
        }
        Gap(DesignTokens.Space.lg)
        InkButton(stringResource(R.string.settings), ButtonStyle.Secondary, onClick = onSettings)
        Spacer(Modifier.weight(1f))
        MercatoTabBar(
            tabs = listOf(stringResource(R.string.tPlay), stringResource(R.string.tProfile)),
            selected = 1,
        ) { if (it == 0) onPlayTab() }
        Gap(DesignTokens.Space.md)
    }
}

@Composable
private fun StatRow(label: Int, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(DesignTokens.Color.blueNight, RoundedCornerShape(DesignTokens.Radius.medium))
            .padding(DesignTokens.Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory),
        )
        Text(
            value,
            // Unbounded 26/900, per the iOS profile stats.
            style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.yellow)
                .copy(fontSize = 26.sp),
        )
    }
}

/** 11 Settings: remove-ads purchase, toggles, consent, replay intro, footer. */
@Composable
fun SettingsScreen(
    graph: AppGraph,
    onBack: () -> Unit,
    onConsent: () -> Unit,
    onReplayIntro: () -> Unit,
    onPrivacy: () -> Unit,
    onOffline: () -> Unit,
    onLab: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sound by graph.prefs.sound.collectAsState(initial = true)
    val vibration by graph.prefs.vibration.collectAsState(initial = true)
    val notifications by graph.prefs.notifications.collectAsState(initial = false)
    val consent by graph.prefs.consent.collectAsState(initial = null)
    val adsRemoved by graph.billing.adsRemoved.collectAsState()
    val price by graph.billing.formattedPrice.collectAsState()

    ScreenColumn(Modifier.verticalScroll(rememberScrollState())) {
        Gap(DesignTokens.Space.xl)
        Row(verticalAlignment = Alignment.CenterVertically) {
            val backLabel = stringResource(R.string.a11yBack)
            Text(
                "‹",
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
                modifier = Modifier
                    .semantics { contentDescription = backLabel }
                    .clickable(onClick = onBack)
                    .padding(end = DesignTokens.Space.md, top = 6.dp, bottom = 6.dp),
            )
            Text(
                stringResource(R.string.settings),
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
            )
        }
        Gap(DesignTokens.Space.lg)
        // The one purchase: the remove-ads entitlement (no shop screen).
        // Without a loaded store price there is nothing to sell: show a
        // discreet, non-clickable note instead of a dead CTA (iOS parity).
        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        when {
            adsRemoved -> PurchaseRow(
                title = stringResource(R.string.shopNoAds),
                trailing = stringResource(R.string.owned),
                enabled = false,
            ) {}
            price != null -> PurchaseRow(
                title = stringResource(R.string.shopNoAds),
                trailing = price,
                enabled = activity != null,
            ) { activity?.let { graph.billing.launchPurchase(it) } }
            else -> Text(
                stringResource(R.string.shopUnavailable),
                style = typeStyle(
                    DesignTokens.Type.body,
                    DesignTokens.Color.ivory.copy(alpha = 0.5f),
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (!adsRemoved) {
            LinkRow(stringResource(R.string.restore), null) {
                scope.launch { graph.billing.restore() }
            }
        }
        Gap(DesignTokens.Space.md)
        ToggleRow(R.string.soundS, sound) { scope.launch { graph.prefs.setSound(it) } }
        ToggleRow(R.string.vibrateS, vibration) { scope.launch { graph.prefs.setVibration(it) } }
        ToggleRow(R.string.notifS, notifications) {
            scope.launch { graph.prefs.setNotifications(it) }
        }
        Gap(DesignTokens.Space.md)
        LinkRow(
            stringResource(R.string.rowLang),
            stringResource(R.string.systemV),
            onClick = null,
        )
        LinkRow(
            stringResource(R.string.rowConsent),
            stringResource(
                if (consent == "personalized") R.string.consentOn else R.string.consentOff
            ),
            onClick = onConsent,
        )
        // Both stores require a reachable privacy policy for an app that shows
        // ads and sells an in-app purchase.
        LinkRow(stringResource(R.string.rowPrivacy), null, onClick = onPrivacy)
        LinkRow(stringResource(R.string.rowIntro), null, onClick = onReplayIntro)
        LinkRow(stringResource(R.string.rowOffline), null, onClick = onOffline)
        if (BuildConfig.DEBUG) {
            LinkRow(stringResource(R.string.rowLab), null, onClick = onLab)
        }
        // Fixed gap: weighted spacers are illegal inside a scrollable column.
        Gap(DesignTokens.Space.xl)
        MenuBanner(graph.ads)
        Text(
            "MERCATO ${BuildConfig.VERSION_NAME}",
            style = typeStyle(
                DesignTokens.Type.technical,
                DesignTokens.Color.ivory.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = DesignTokens.Space.lg),
            textAlign = TextAlign.Center,
        )
    }
}

/** One full-width tap target: title and price both launch the purchase. */
@Composable
private fun PurchaseRow(
    title: String,
    trailing: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.blueNight, RoundedCornerShape(DesignTokens.Radius.medium))
            .border(
                DesignTokens.Border.hairline,
                DesignTokens.Color.ink,
                RoundedCornerShape(DesignTokens.Radius.medium),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(DesignTokens.Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory),
            modifier = Modifier.padding(end = DesignTokens.Space.md),
        )
        if (trailing != null) {
            Text(trailing, style = typeStyle(DesignTokens.Type.answer, DesignTokens.Color.yellow))
        }
    }
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory),
        )
        MercatoToggle(checked, onChange)
    }
}

@Composable
private fun LinkRow(label: String, value: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                Text(
                    value,
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.copy(alpha = 0.6f),
                    ),
                )
            }
            if (onClick != null) {
                Text(
                    " ›",
                    style = typeStyle(
                        DesignTokens.Type.body,
                        DesignTokens.Color.ivory.copy(alpha = 0.6f),
                    ),
                )
            }
        }
    }
}
