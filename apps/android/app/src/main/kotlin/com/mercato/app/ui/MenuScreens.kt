package com.mercato.app.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mercato.app.AppGraph
import com.mercato.app.BuildConfig
import com.mercato.app.MenuBanner
import com.mercato.app.Prefs
import com.mercato.app.R
import java.util.Locale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mercato.design.DesignTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.mercato_ffi.AdConsent
import uniffi.mercato_ffi.GameMode

/** 01 Splash: logo and a 1.3s loading bar, then auto-advance. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        // iOS holds the full bar for 100ms before leaving; Android cut away the
        // instant it filled, which read as a jump.
        delay(1400)
        onDone()
    }
    ScreenColumn {
        Spacer(Modifier.weight(1f))
        Wordmark(DesignTokens.Type.logo.size)
        Gap(DesignTokens.Space.xl)
        // An ink-bordered capsule that actually fills, as on iOS. It used to
        // sit frozen at two thirds, which read as a stalled load.
        val progress = remember { Animatable(0.08f) }
        LaunchedEffect(Unit) { progress.animateTo(1f, tween(1300)) }
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .width(172.dp)
                .height(14.dp)
                .solidRaised(7.dp, depth = 0.dp, border = 3.dp)  // iOS: 3, not 4
                .background(DesignTokens.Color.ink.copy(alpha = 0.4f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(progress.value)
                    .fillMaxHeight()
                    .background(DesignTokens.Color.yellow, CircleShape)
            )
        }
        Spacer(Modifier.weight(1.2f))
    }
}

/**
 * The wordmark: MER ivory, CATO yellow, over a hard ink offset. iOS draws it
 * this way on both Splash and Home; a flat single-colour word made the two
 * apps look unrelated.
 */
@Composable
fun Wordmark(size: TextUnit, modifier: Modifier = Modifier) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = DesignTokens.Color.ivory)) { append("MER") }
        withStyle(SpanStyle(color = DesignTokens.Color.yellow)) { append("CATO") }
    }
    // iOS lets the wordmark shrink to fit (minimumScaleFactor); Compose has no
    // equivalent at this version, so the size is derived from the width
    // available. Unbounded Black runs about 0.63em per glyph, so seven glyphs
    // need roughly 4.4em.
    BoxWithConstraints(modifier) {
        val fitted = minOf(size.value, maxWidth.value / 4.4f).sp
        val style = typeStyle(DesignTokens.Type.logo, DesignTokens.Color.ivory)
            .copy(fontSize = fitted)
        Box {
            Text(
                "MERCATO",
                style = style.copy(color = DesignTokens.Color.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = (fitted.value * 0.08f).dp, y = (fitted.value * 0.095f).dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                text,
                style = style,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
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
                stringResource(R.string.obSkip).uppercase(),
                style = typeStyle(
                    DesignTokens.Type.label,
                    Color.White.copy(alpha = 0.68f),
                    13.sp,
                    0.06f,
                ),
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
                        .height(200.dp)
                        .padding(bottom = 10.dp)
                        .solidRaised(DesignTokens.Radius.card, depth = 10.dp)
                        .paperHatch(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Ink-grey on the pale hatch: white at 60 percent was
                    // barely legible there, unlike iOS.
                    CapsLabel(stringResource(art), color = DesignTokens.Color.clubGrey)
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
                        .padding(horizontal = 4.dp)
                        .width(if (i == pager.currentPage) 26.dp else 10.dp)
                        .height(10.dp)
                        .background(
                            if (i == pager.currentPage) DesignTokens.Color.yellow
                            else Color.White.copy(alpha = 0.25f),
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
            fontSize = 22.sp, fontWeight = 900, tracking = -0.03f,
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
                .solidRaised(radius = DesignTokens.Radius.card, depth = 10.dp)
                .background(DesignTokens.Color.ivory)
                .padding(DesignTokens.Space.xl),
        ) {
            Text(
                stringResource(R.string.cnTitle),
                style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ink),
            )
            Gap(DesignTokens.Space.sm)
            Text(
                // iOS: Figtree 14.5/600 in muted, and the bullet text in ink.
                // The two colours were swapped on Android.
                stringResource(R.string.cnBody),
                style = typeStyle(DesignTokens.Type.consentBody, DesignTokens.Color.muted),
            )
            Gap(DesignTokens.Space.md)
            listOf(R.string.cnPoints_0, R.string.cnPoints_1, R.string.cnPoints_2).forEach {
                Row(
                    Modifier.padding(vertical = 4.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "·",
                        style = typeStyle(
                            DesignTokens.Type.clubTo, DesignTokens.Color.blue, 14.sp, null,
                        ),
                    )
                    Text(
                        stringResource(it),
                        style = typeStyle(
                            DesignTokens.Type.body, DesignTokens.Color.ink, 13.5.sp, null,
                        ).copy(fontWeight = FontWeight(700)),
                    )
                }
            }
        }
        // iOS keeps only the title, body and bullets on the ivory card, and
        // leaves the actions and the footer on the blue background
        // (Screens.swift:381). Putting them inside turned the bottom half of
        // the screen ivory.
        Gap(DesignTokens.Space.lg)
        InkButton(
            stringResource(R.string.cnAccept), ButtonStyle.Primary,
            fontSize = 18.sp, fontWeight = 900, tracking = -0.03f,
        ) { choose(true) }
        InkButton(
            stringResource(R.string.cnRefuse), ButtonStyle.Secondary,
            fontSize = 15.sp, fontWeight = 800, tracking = -0.03f,
        ) { choose(false) }
        Text(
            stringResource(R.string.cnFoot),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
    }
}

/** 04 Home: logo, the two mode buttons pushed to the bottom, banner + tabs. */
@Composable
fun HomeScreen(graph: AppGraph, onPlay: (GameMode) -> Unit, onProfile: () -> Unit) {
    LaunchedEffect(Unit) { graph.ads.preloadInterstitial() }
    ScreenColumn {
        // iOS treats the wordmark and both buttons as one block, with the
        // flexible space above and below it, not between them.
        Spacer(Modifier.weight(1f))
        Wordmark(DesignTokens.Type.logo.size)
        Gap(DesignTokens.Space.lg)
        ModeButton(R.string.l1, DesignTokens.Color.yellow) { onPlay(GameMode.EASY) }
        Gap(DesignTokens.Space.md)
        ModeButton(R.string.l3, DesignTokens.Color.ivory) { onPlay(GameMode.HARDCORE) }
        Spacer(Modifier.weight(1f))
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
private fun ModeButton(title: Int, fill: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .solidRaised(radius = DesignTokens.Radius.card, depth = 10.dp)
            .background(fill)
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp, horizontal = 24.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(title),
            style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ink),
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
            style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ivory)
                .copy(fontSize = 20.sp, letterSpacing = (-0.04 * 20).sp),
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
                        DesignTokens.Color.ink.copy(alpha = 0.35f),
                        RoundedCornerShape(DesignTokens.Radius.medium),
                    )
                    .border(
                        2.dp,
                        Color.White.copy(alpha = 0.12f),
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
            .background(
                DesignTokens.Color.ink.copy(alpha = 0.35f),
                RoundedCornerShape(DesignTokens.Radius.medium),
            )
            .border(
                2.dp,
                Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(DesignTokens.Radius.medium),
            )
            .padding(DesignTokens.Space.md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory)
                .copy(fontWeight = FontWeight(800)),
        )
        Text(
            value,
            // Unbounded 26/900, per the iOS profile stats.
            style = typeStyle(DesignTokens.Type.statValue, DesignTokens.Color.yellow),
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
            Box(
                Modifier
                    .semantics { contentDescription = backLabel }
                    .size(38.dp)
                    .background(
                        DesignTokens.Color.ink.copy(alpha = 0.45f),
                        RoundedCornerShape(12.dp),
                    )
                    .border(4.dp, DesignTokens.Color.ink, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("‹", style = TextStyle(
                        color = Color.White,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight(900),
                        fontSize = 20.sp,
                    ))
            }
            Gap(DesignTokens.Space.sm)
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
        PurchaseRow(
            title = stringResource(R.string.shopNoAds),
            subtitle = null,
            trailing = when {
                adsRemoved -> stringResource(R.string.owned)
                price != null -> price ?: ""
                else -> stringResource(R.string.shopUnavailable)
            },
            owned = adsRemoved,
            enabled = !adsRemoved && price != null && activity != null,
        ) { activity?.let { graph.billing.launchPurchase(it) } }
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
            // iOS shows the resolved language next to the source, e.g.
            // "English · System" (Screens.swift:566).
            run {
                val loc = Locale.getDefault()
                val lang = loc.getDisplayLanguage(loc).replaceFirstChar { it.uppercase() }
                lang + " · " + stringResource(R.string.systemV)
            },
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
        LinkRow(stringResource(R.string.rowPrivacy), null, onClick = onPrivacy, external = true)
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
                11.sp,
                null,
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
    /** iOS shows the title alone (Screens.swift:670); null reproduces that. */
    subtitle: String?,
    trailing: String,
    owned: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    // A raised ivory card, as on iOS. It was a dark navy row, and when the
    // store had no price the whole card vanished for a grey line of text.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .solidRaised(18.dp, depth = 6.dp, border = 4.dp)
            .background(DesignTokens.Color.ivory)
            .padding(18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = DesignTokens.Space.md)) {
            Text(
                title,
                style = typeStyle(DesignTokens.Type.purchaseTitle, DesignTokens.Color.ink),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = typeStyle(DesignTokens.Type.bodySmall, DesignTokens.Color.muted),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(
            Modifier
                .then(
                    if (owned) {
                        // CircleShape on a wide box drew an ellipse; iOS uses
                        // a capsule.
                        Modifier.background(
                            DesignTokens.Color.green,
                            RoundedCornerShape(percent = 50),
                        )
                    } else {
                        Modifier
                            .padding(bottom = 6.dp)
                            .solidRaised(16.dp, depth = 6.dp, border = 4.dp)
                            .background(DesignTokens.Color.yellow)
                    }
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                trailing,
                style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ink)
                    .copy(fontSize = 15.sp),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    // A raised ivory card, as iOS: the rows were bare text on the background.
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .solidRaised(18.dp, depth = 6.dp, border = 4.dp)
            .background(DesignTokens.Color.ivory)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.clubTo, DesignTokens.Color.ink)
                .copy(fontSize = 15.sp),
        )
        MercatoToggle(checked, onChange)
    }
}

@Composable
private fun LinkRow(
    label: String,
    value: String?,
    // Declared before onClick so a trailing lambda still binds to onClick.
    external: Boolean = false,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 9.dp)
            .background(DesignTokens.Color.ink.copy(alpha = 0.35f), shape)
            .border(3.dp, Color.White.copy(alpha = 0.16f), shape)
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = typeStyle(DesignTokens.Type.body, Color.White))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                // iOS sets these values in IBM Plex Mono 11 (Screens.swift:709).
                Text(
                    value,
                    style = typeStyle(
                        DesignTokens.Type.technical,
                        Color.White.copy(alpha = 0.75f),
                        11.sp,
                        null,
                    ),
                )
            }
            if (onClick != null) {
                Text(
                    if (external) " ↗" else " ›",
                    style = typeStyle(DesignTokens.Type.label, Color.White.copy(alpha = 0.75f)),
                )
            }
        }
    }
}
