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
import android.app.Activity
import com.mercato.app.AppGraph
import com.mercato.app.BuildConfig
import com.mercato.app.MenuBanner
import com.mercato.app.Prefs
import com.mercato.app.R
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mercato.analytics.Event
import com.mercato.analytics.Param
import com.mercato.art.OnboardingScene
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
                .background(DesignTokens.Color.ink.dim(DesignTokens.Opacity.splashTrack))
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
    // iOS lets the wordmark shrink to fit (minimumScaleFactor). Compose has no
    // equivalent, so it is measured down to a size that fits, the way the guess
    // field does. Estimating it as 4.4em per seven glyphs was optimistic by
    // about a glyph and a half: the word rendered as MERCA with the rest
    // clipped, and the block came out the wrong height as well.
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val base = typeStyle(DesignTokens.Type.logo, DesignTokens.Color.ivory)
        val availPx = with(LocalDensity.current) { maxWidth.toPx() }
        var pt = size.value
        while (pt > size.value * 0.5f &&
            measurer.measure("MERCATO", base.copy(fontSize = pt.sp), maxLines = 1).size.width > availPx
        ) pt -= 1f
        val fitted = pt.sp
        val style = base.copy(fontSize = fitted)
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
    // Title and body. The picture is drawn, not loaded: see OnboardingArt.kt.
    // It carries no text either way, so one scene covers all three languages.
    val panes = listOf(
        R.string.ob_0_t to R.string.ob_0_b,
        R.string.ob_1_t to R.string.ob_1_b,
        R.string.ob_2_t to R.string.ob_2_b,
    )
    // Which pane a capture opens on. The three scenes are three different
    // pictures, and only the first was reachable without swiping, so the other
    // two were never measured against iOS at all. iOS reads the same names
    // from its own launch argument.
    val activity = LocalContext.current as? Activity
    val start = if (!BuildConfig.DEBUG) 0 else when (activity?.intent?.getStringExtra("route")) {
        "onboarding2" -> 1
        "onboarding3" -> 2
        else -> 0
    }
    val pager = rememberPagerState(initialPage = start) { panes.size }
    val scope = rememberCoroutineScope()

    ScreenColumn {
        // iOS pads the top by the gutter and gives the skip row a fixed 38
        // (Screens.swift:272).
        Gap(DesignTokens.Space.gutter)
        Row(
            Modifier.fillMaxWidth().height(38.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.obSkip).uppercase(),
                style = typeStyle(DesignTokens.Type.skipLabel, Color.White.dim(DesignTokens.Opacity.textSoft)),
                modifier = Modifier.clickable(onClick = onDone).padding(8.dp),
            )
        }
        HorizontalPager(pager, Modifier.weight(1f)) { page ->
            val (title, body) = panes[page]
            // iOS leaves the art and the copy left-aligned in a block centred
            // between two spacers; Android centred every line of them
            // (Screens.swift:284).
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
            ) {
                // The pane's picture: the pass, the answer, the three stars,
                // each drawn live from design/onboarding.json rather than
                // shipped as an image. See OnboardingArt.kt for why it moved.
                OnboardingArt(
                    page,
                    Modifier
                        .fillMaxWidth()
                        .height((OnboardingScene.HEIGHT + OnboardingScene.BORDER * 2).dp)
                        .solidRaised(DesignTokens.Radius.card, depth = 10.dp)
                        .clip(RoundedCornerShape(DesignTokens.Radius.card)),
                )
                // iOS: 26 under the art, 12 between title and body
                // (Screens.swift:284).
                Gap(26.dp)
                Text(
                    stringResource(title),
                    style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
                )
                Gap(DesignTokens.Space.block)
                Text(
                    stringResource(body),
                    style = typeStyle(
                        DesignTokens.Type.bodyLarge,
                        Color.White.dim(DesignTokens.Opacity.textSoft),
                    ),
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 18.dp),
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
                            else Color.White.dim(DesignTokens.Opacity.trackOff),
                            CircleShape,
                        )
                )
            }
        }
        val last = pager.currentPage == panes.lastIndex
        InkButton(
            stringResource(if (last) R.string.obStart else R.string.obNext),
            ButtonStyle.Primary,
            Modifier.padding(bottom = 20.dp),
            fontSize = 22.sp, fontWeight = 900, tracking = -0.03f,
            depth = 9.dp, verticalPadding = 20.dp,
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
        // iOS pads the column by 20 top and bottom (Screens.swift:435).
        Gap(DesignTokens.Space.section)
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
                style = typeStyle(DesignTokens.Type.consentTitle, DesignTokens.Color.ink),
            )
            // iOS: 12 under the title, 16 above the bullets (Screens.swift:372).
            Gap(DesignTokens.Space.block)
            Text(
                // iOS: Figtree 14.5/600 in muted, and the bullet text in ink.
                // The two colours were swapped on Android.
                stringResource(R.string.cnBody),
                style = typeStyle(DesignTokens.Type.consentBody, DesignTokens.Color.muted),
            )
            Gap(DesignTokens.Space.gutter)
            listOf(R.string.cnPoints_0, R.string.cnPoints_1, R.string.cnPoints_2).forEach {
                Row(
                    Modifier.padding(vertical = 4.5.dp),
                    horizontalArrangement = Arrangement.spacedBy(DesignTokens.Space.sm),
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
        //
        // The flexible space goes between the card and the actions, which is
        // what holds the actions on the bottom edge. Android put both spacers
        // above the card and below the footnote, so the actions rode up under
        // the card and the bottom third of the screen was empty.
        Spacer(Modifier.weight(1f))
        InkButton(
            stringResource(R.string.cnAccept), ButtonStyle.Primary,
            fontSize = 18.sp, fontWeight = 900, tracking = -0.03f,
            depth = 9.dp, verticalPadding = 19.dp,
        ) { choose(true) }
        // iOS spaces the actions and the footnote by 11 (Screens.swift:403).
        Gap(DesignTokens.Space.control)
        InkButton(
            stringResource(R.string.cnRefuse), ButtonStyle.Secondary,
            fontSize = 15.sp, fontWeight = 800, tracking = -0.03f,
            depth = 9.dp, verticalPadding = 17.dp,
        ) { choose(false) }
        // 11 of stack spacing plus the footnote's own 4 (Screens.swift:431).
        Gap(15.dp)
        Text(
            stringResource(R.string.cnFoot),
            style = typeStyle(
                DesignTokens.Type.footnote,
                Color.White.dim(DesignTokens.Opacity.textFootnote),
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.section)
    }
}

/** 04 Home: logo, the two mode buttons pushed to the bottom, banner + tabs. */
@Composable
fun HomeScreen(graph: AppGraph, onPlay: (GameMode) -> Unit, onProfile: () -> Unit) {
    LaunchedEffect(Unit) { graph.ads.preloadInterstitial() }
    ScreenColumn {
        // iOS treats the wordmark and both buttons as one block, with the
        // flexible space above and below it, not between them.
        // iOS pads the column by the gutter on both axes (Screens.swift:102).
        Gap(DesignTokens.Space.gutter)
        Spacer(Modifier.weight(1f))
        Wordmark(DesignTokens.Type.logo.size)
        // iOS: 22 under the wordmark, 14 between the modes (Screens.swift:82).
        Gap(DesignTokens.Space.xl)
        ModeButton(R.string.l1, DesignTokens.Color.yellow) { onPlay(GameMode.EASY) }
        Gap(DesignTokens.Space.md)
        ModeButton(R.string.l3, DesignTokens.Color.ivory) { onPlay(GameMode.HARDCORE) }
        Spacer(Modifier.weight(1f))
        MenuBanner(graph.ads)
        Gap(DesignTokens.Space.chip)
        MercatoTabBar(
            tabs = listOf(stringResource(R.string.tPlay), stringResource(R.string.tProfile)),
            selected = 0,
        ) { if (it == 1) onProfile() }
        Gap(DesignTokens.Space.gutter)
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
            // iOS: 24 vertical, 20 horizontal. The two were the wrong way round
            // here, which made the button squat and its label inset too far
            // (Screens.swift:115).
            .padding(vertical = 24.dp, horizontal = 20.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            stringResource(title),
            style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ink),
        )
    }
}

/** 10 Profile: four lifetime stats and the door to Settings. */
@Composable
fun ProfileScreen(graph: AppGraph, onPlayTab: () -> Unit, onSettings: () -> Unit) {
    val stats by graph.prefs.stats.collectAsState(initial = Prefs.Stats(0, 0, 0, 0, 0))
    ScreenColumn {
        // iOS pads the column by the gutter on both axes, then the title by 8
        // and the banner by 16 (Profile.swift:49).
        Gap(DesignTokens.Space.gutter)
        Gap(DesignTokens.Space.chip)
        Text(
            stringResource(R.string.profile),
            style = typeStyle(DesignTokens.Type.panelTitle, DesignTokens.Color.ivory),
        )
        // Banner at the top, far from the tab bar, so a mistap near the
        // bottom never lands on an ad (iOS parity).
        Gap(DesignTokens.Space.gutter)
        MenuBanner(graph.ads)
        Spacer(Modifier.weight(1f))
        if (stats.roundsPlayed == 0) {
            // A column of zeros reads as broken: invite the first round
            // instead (iOS parity).
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        DesignTokens.Color.ink.dim(DesignTokens.Opacity.row),
                        RoundedCornerShape(DesignTokens.Radius.row),
                    )
                    .border(
                        2.dp,
                        Color.White.dim(DesignTokens.Opacity.borderFaint),
                        RoundedCornerShape(DesignTokens.Radius.row),
                    )
                    // iOS: 18 horizontal, 34 vertical (Profile.swift:120). A
                    // flat 22 collapsed the card by 24dp.
                    .padding(horizontal = 18.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.statsEmptyTitle),
                    style = typeStyle(DesignTokens.Type.sectionTitle, DesignTokens.Color.ivory),
                    textAlign = TextAlign.Center,
                )
                Gap(DesignTokens.Space.sm)
                Text(
                    stringResource(R.string.statsEmptyBody),
                    // Soft ivory at 72%, readable on the dark card (iOS parity).
                    style = typeStyle(
                        DesignTokens.Type.bodyMid,
                        DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textSubtle),
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            val accuracy =
                if (stats.answered == 0) "-"
                else "${(stats.correct * 100) / stats.answered}%"
            // iOS spaces the four rows by 12 (Profile.swift:70).
            Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.block)) {
                StatRow(R.string.stPlayed, "${stats.roundsPlayed}")
                StatRow(R.string.stBest, "${stats.bestScore}")
                StatRow(R.string.stStreak, "${stats.bestStreak}")
                StatRow(R.string.stAcc, accuracy)
            }
        }
        Gap(DesignTokens.Space.section)
        InkButton(
            stringResource(R.string.settings), ButtonStyle.Secondary,
            fontSize = 16.sp, fontWeight = 900, tracking = -0.03f,
            depth = 6.dp, radius = DesignTokens.Radius.medium,
            verticalPadding = 16.dp, onClick = onSettings,
        )
        Spacer(Modifier.weight(1f))
        MercatoTabBar(
            tabs = listOf(stringResource(R.string.tPlay), stringResource(R.string.tProfile)),
            selected = 1,
        ) { if (it == 0) onPlayTab() }
        Gap(DesignTokens.Space.gutter)
    }
}

@Composable
private fun StatRow(label: Int, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                DesignTokens.Color.ink.dim(DesignTokens.Opacity.row),
                RoundedCornerShape(DesignTokens.Radius.row),
            )
            .border(
                2.dp,
                Color.White.dim(DesignTokens.Opacity.borderFaint),
                RoundedCornerShape(DesignTokens.Radius.row),
            )
            // iOS: 18 horizontal, 20 vertical (Profile.swift:140).
            .padding(horizontal = 18.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.bodyStrong, DesignTokens.Color.ivory),
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
    val notifications by graph.prefs.notifications.collectAsState(initial = false)
    val consent by graph.prefs.consent.collectAsState(initial = null)
    val adsRemoved by graph.billing.adsRemoved.collectAsState()
    val price by graph.billing.formattedPrice.collectAsState()

    // iOS lays this out as a plain column with a real spacer before the banner,
    // so the footer sits on the bottom edge whatever the content above adds up
    // to (Screens.swift:521). Android scrolled instead, which forbids a
    // weighted spacer, so it used a fixed gap and left 96dp of slack where iOS
    // leaves 335, and the footer floated in the middle of the screen.
    ScreenColumn {
        // iOS pads the column by the gutter on both axes and gives the header
        // row a fixed 46.
        Gap(DesignTokens.Space.gutter)
        Row(
            Modifier.height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backLabel = stringResource(R.string.a11yBack)
            Box(
                Modifier
                    .semantics { contentDescription = backLabel }
                    .size(38.dp)
                    .inkOutlined(DesignTokens.Radius.control)
                    .background(DesignTokens.Color.ink.dim(DesignTokens.Opacity.control))
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
            Gap(DesignTokens.Space.block)
            Text(
                stringResource(R.string.settings),
                style = typeStyle(DesignTokens.Type.panelTitle, DesignTokens.Color.ivory),
            )
        }
        // The switches come first, then the purchase, then the link rows: the
        // purchase was at the head of the screen here and iOS puts it in the
        // middle (Screens.swift:540).
        Gap(DesignTokens.Space.block)
        // iOS spaces the switches by 10 (Screens.swift:540). Two of them, not
        // three: vibration was stored but nothing ever asked for haptics, so
        // it was a control that did nothing while telling the player it did
        // something. Notifications stays, still only stored, because it is the
        // setting a daily reminder will read.
        Column(verticalArrangement = Arrangement.spacedBy(DesignTokens.Space.sm)) {
            ToggleRow(R.string.soundS, sound) {
                scope.launch { graph.prefs.setSound(it) }
                graph.analytics.log(Event.SOUND_SET, mapOf(Param.ON to it))
            }
            ToggleRow(R.string.notifS, notifications) {
                scope.launch { graph.prefs.setNotifications(it) }
            }
        }
        Gap(DesignTokens.Space.gutter)
        // The one purchase: the remove-ads entitlement (no shop screen).
        // Without a loaded store price there is nothing to sell: show a
        // discreet, non-clickable note instead of a dead CTA (iOS parity).
        val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
        PurchaseCard(
            title = stringResource(R.string.shopNoAds),
            trailing = when {
                adsRemoved -> stringResource(R.string.owned)
                price != null -> price ?: ""
                else -> stringResource(R.string.shopUnavailable)
            },
            owned = adsRemoved,
            enabled = !adsRemoved && price != null && activity != null,
            restore = if (adsRemoved) null else ({ scope.launch { graph.billing.restore(explicit = true) }; Unit }),
        ) { activity?.let { graph.billing.launchPurchase(it) } }
        Gap(DesignTokens.Space.gutter)
        // iOS spaces the link rows by 9 (Screens.swift:549).
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
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
            // Both stores require a reachable privacy policy for an app that
            // shows ads and sells an in-app purchase.
            LinkRow(stringResource(R.string.rowPrivacy), null, onClick = onPrivacy, external = true)
            LinkRow(stringResource(R.string.rowIntro), null, onClick = onReplayIntro)
            LinkRow(stringResource(R.string.rowOffline), null, onClick = onOffline)
            if (BuildConfig.DEBUG) {
                LinkRow(stringResource(R.string.rowLab), null, onClick = onLab)
            }
        }
        // iOS `Spacer(minLength: 16)`: at least 16, then all the room left over.
        Gap(DesignTokens.Space.gutter)
        Spacer(Modifier.weight(1f))
        MenuBanner(graph.ads)
        Gap(DesignTokens.Space.block)
        Text(
            "MERCATO ${BuildConfig.VERSION_NAME}",
            style = typeStyle(
                DesignTokens.Type.monoPlain,
                Color.White.dim(DesignTokens.Opacity.textFaintest),
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.gutter)
    }
}

/**
 * One full-width tap target: title and price both launch the purchase, with
 * Restore purchases underneath, inside the card. Restore was a separate link
 * row of its own on the blue background; iOS keeps it in the card as a plain
 * blue text button (Screens.swift:629).
 */
@Composable
private fun PurchaseCard(
    title: String,
    trailing: String,
    owned: Boolean,
    enabled: Boolean,
    /** null once the entitlement is owned: there is nothing left to restore. */
    restore: (() -> Unit)?,
    onClick: () -> Unit,
) {
    // A raised ivory card, as on iOS. It was a dark navy row, and when the
    // store had no price the whole card vanished for a grey line of text.
    Column(
        Modifier
            .fillMaxWidth()
            .solidRaised(DesignTokens.Radius.medium, depth = 6.dp, border = 4.dp)
            .background(DesignTokens.Color.ivory)
            .padding(18.dp),
    ) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = DesignTokens.Space.md)) {
            Text(
                title,
                style = typeStyle(DesignTokens.Type.purchaseTitle, DesignTokens.Color.ink),
            )
        }
        when {
            owned -> Box(
                Modifier
                    // CircleShape on a wide box drew an ellipse; iOS uses a
                    // capsule.
                    .background(DesignTokens.Color.green, RoundedCornerShape(percent = 50))
                    // iOS: 12 by 7 around the owned badge (Screens.swift:598).
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(
                    trailing,
                    style = typeStyle(DesignTokens.Type.ownedBadge, DesignTokens.Color.ink),
                )
            }
            enabled -> Box(
                Modifier
                    .solidRaised(DesignTokens.Radius.row, depth = 6.dp, border = 4.dp)
                    .background(DesignTokens.Color.yellow)
                    .clickable(onClick = onClick)
                    // iOS: 16 by 11 around the price (Screens.swift:611).
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    trailing,
                    style = typeStyle(DesignTokens.Type.badgeValue, DesignTokens.Color.ink),
                )
            }
            // No price loaded: iOS drops the badge entirely for a quiet muted
            // label, so there is no dead CTA (Screens.swift:622). Android kept
            // drawing the yellow pill, which read as tappable and made the card
            // 28dp taller than the same card on iOS.
            else -> Text(
                trailing,
                style = typeStyle(DesignTokens.Type.bodySmall, DesignTokens.Color.muted),
            )
        }
    }
        if (restore != null) {
            Text(
                stringResource(R.string.restore),
                style = typeStyle(DesignTokens.Type.bodySmallStrong, DesignTokens.Color.blue),
                modifier = Modifier.padding(top = 14.dp).clickable(onClick = restore),
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
            .solidRaised(DesignTokens.Radius.medium, depth = 6.dp, border = 4.dp)
            .background(DesignTokens.Color.ivory)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(label),
            style = typeStyle(DesignTokens.Type.ctaCompact, DesignTokens.Color.ink),
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
    val shape = RoundedCornerShape(DesignTokens.Radius.row)
    Row(
        Modifier
            .fillMaxWidth()
            .background(DesignTokens.Color.ink.dim(DesignTokens.Opacity.row), shape)
            .border(DesignTokens.Border.hairline, Color.White.dim(DesignTokens.Opacity.borderRow), shape)
            .clip(shape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = typeStyle(DesignTokens.Type.rowValue, Color.White))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (value != null) {
                // iOS sets these values in IBM Plex Mono 11 (Screens.swift:709).
                Text(
                    value,
                    style = typeStyle(
                        DesignTokens.Type.technical,
                        Color.White.dim(DesignTokens.Opacity.textQuiet),
                        11.sp,
                        null,
                    ),
                )
            }
            if (onClick != null) {
                Text(
                    if (external) " ↗" else " ›",
                    style = typeStyle(DesignTokens.Type.label, Color.White.dim(DesignTokens.Opacity.textQuiet)),
                )
            }
        }
    }
}
