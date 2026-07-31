package com.mercato.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mercato.analytics.Event
import com.mercato.analytics.Param
import com.mercato.app.ui.ConsentScreen
import com.mercato.app.ui.GameScreen
import com.mercato.app.ui.HomeScreen
import com.mercato.app.ui.LocalFonts
import com.mercato.app.ui.OnboardingScreen
import com.mercato.app.ui.ProfileScreen
import com.mercato.app.ui.RecapScreen
import com.mercato.app.ui.SettingsScreen
import com.mercato.app.ui.SplashScreen
import com.mercato.app.ui.appBackground
import com.mercato.app.ui.rememberFonts
import kotlinx.coroutines.launch
import uniffi.mercato_ffi.GameMode

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val CONSENT = "consent"
    const val HOME = "home"
    const val GAME_EASY = "game/easy"
    const val GAME_HARDCORE = "game/hardcore"
    const val RECAP = "recap"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val OFFLINE = "offline"
    const val LAB = "lab"
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as MercatoApplication).graph
        // GDPR consent surface: shows the UMP form when required, silent
        // no-op elsewhere. The outcome lands in the core via setAdConsent.
        //
        // Deferred until the intro is behind us, matching iOS: gathering here
        // dropped the form on top of the first onboarding pane, before the
        // player knew what the app was. MercatoNav runs it on the way to Home,
        // which is still well before anything requests an ad.
        setContent {
            CompositionLocalProvider(LocalFonts provides rememberFonts()) {
                Box(Modifier.fillMaxSize().appBackground()) {
                    MercatoNav(graph, debugRoute(intent))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Purchases made outside the app (family, web) surface here.
        (application as MercatoApplication).graph.billing.restore()
    }
}

/**
 * The QA screen to jump to, from an intent extra. Mirrors the iOS launch
 * argument (-MercatoRoute <name>) so the same screen can be captured on both
 * platforms without walking the flow by hand, which is neither deterministic
 * nor free of the UMP form. Debug builds only.
 *
 *   adb shell am start -n <pkg>/com.mercato.app.MainActivity --es route easy
 */
private fun debugRoute(intent: android.content.Intent?): String? {
    if (!BuildConfig.DEBUG) return null
    return when (intent?.getStringExtra("route")) {
        "home" -> Routes.HOME
        "profile" -> Routes.PROFILE
        "settings" -> Routes.SETTINGS
        "offline" -> Routes.OFFLINE
        "lab" -> Routes.LAB
        "onboarding", "onboarding2", "onboarding3" -> Routes.ONBOARDING
        "consent" -> Routes.CONSENT
        "easy" -> Routes.GAME_EASY
        "hardcore" -> Routes.GAME_HARDCORE
        // The quit dialog sits over the Easy game, so the route is the game
        // and GameScreen reads the same extra to open the dialog. iOS does the
        // same with a second launch argument, -MercatoQuit.
        "quit" -> Routes.GAME_EASY
        "recap", "recaplose" -> Routes.RECAP
        else -> null
    }
}

/** One ViewModel for the whole round/recap flow, scoped to the activity. */
private class GameVmFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(graph) as T
}

@Composable
fun MercatoNav(graph: AppGraph, startRoute: String? = null) {
    val nav = rememberNavController()
    val vm: GameViewModel = viewModel(factory = GameVmFactory(graph))
    val onboarded by graph.prefs.onboarded.collectAsState(initial = null)
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    // Once per process, on the way to Home rather than at launch.
    // A debug route must never trigger the UMP form: it would land on top of
    // the capture. Marking consent as already gathered short-circuits it.
    val consentGathered = androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(startRoute != null)
    }
    androidx.compose.runtime.LaunchedEffect(startRoute) {
        if (startRoute == Routes.RECAP) {
            vm.debugSeedRecap(won = activity?.intent?.getStringExtra("route") != "recaplose")
        }
    }
    val gatherConsent = { onDone: () -> Unit ->
        if (!consentGathered.value && activity != null) {
            consentGathered.value = true
            graph.consent.gather(activity, onDone)
        } else {
            onDone()
        }
    }

    // One hook for every screen rather than a call in each composable: a
    // screen added later is counted without anyone remembering to count it.
    // The names come from the shared vocabulary, not from the route strings,
    // so "game/easy" cannot land in the data as a screen name iOS never uses.
    val entry by nav.currentBackStackEntryAsState()
    androidx.compose.runtime.LaunchedEffect(entry?.destination?.route) {
        val name = when (entry?.destination?.route) {
            Routes.HOME -> "home"
            Routes.PROFILE -> "profile"
            Routes.SETTINGS -> "settings"
            Routes.RECAP -> "recap"
            Routes.OFFLINE -> "offline"
            Routes.ONBOARDING -> "onboarding"
            else -> null // splash, consent, the game itself and the lab
        }
        if (name != null) graph.analytics.log(Event.SCREEN_OPENED, mapOf(Param.SCREEN to name))
    }

    NavHost(nav, startDestination = startRoute ?: Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                if (onboarded == true) {
                    gatherConsent {
                        nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
                    }
                } else {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(Routes.SPLASH) { inclusive = true } }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            OnboardingScreen(onDone = {
                // UMP runs here rather than at launch, so its form never lands
                // on top of the intro. Where it collected GDPR consent the
                // app's own screen would double-ask, so skip straight home.
                gatherConsent {
                    if (graph.consent.handledByUmp.value) {
                        scope.launch {
                            graph.prefs.setOnboarded()
                            nav.navigate(Routes.HOME) {
                                popUpTo(Routes.ONBOARDING) { inclusive = true }
                            }
                        }
                    } else {
                        nav.navigate(Routes.CONSENT)
                    }
                }
            })
        }
        composable(Routes.CONSENT) {
            ConsentScreen(graph, fromSettings = false) {
                nav.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            }
        }
        composable("${Routes.CONSENT}/settings") {
            ConsentScreen(graph, fromSettings = true) { nav.popBackStack() }
        }
        composable(Routes.HOME) {
            HomeScreen(
                graph = graph,
                onPlay = { mode ->
                    nav.navigate(if (mode == GameMode.EASY) Routes.GAME_EASY else Routes.GAME_HARDCORE)
                },
                onProfile = { nav.navigate(Routes.PROFILE) },
            )
        }
        composable(Routes.GAME_EASY) {
            GameScreen(graph, vm, GameMode.EASY,
                onRoundOver = { nav.navigate(Routes.RECAP) { popUpTo(Routes.HOME) } },
                onQuit = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.GAME_HARDCORE) {
            GameScreen(graph, vm, GameMode.HARDCORE,
                onRoundOver = { nav.navigate(Routes.RECAP) { popUpTo(Routes.HOME) } },
                onQuit = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.RECAP) {
            RecapScreen(graph, vm,
                onPlayAgain = { mode ->
                    nav.navigate(if (mode == GameMode.EASY) Routes.GAME_EASY else Routes.GAME_HARDCORE) {
                        popUpTo(Routes.HOME)
                    }
                },
                onHome = { nav.popBackStack(Routes.HOME, inclusive = false) })
        }
        composable(Routes.PROFILE) {
            ProfileScreen(graph,
                onPlayTab = { nav.popBackStack(Routes.HOME, inclusive = false) },
                onSettings = { nav.navigate(Routes.SETTINGS) })
        }
        composable(Routes.SETTINGS) {
            val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
            SettingsScreen(graph,
                onBack = { nav.popBackStack() },
                onConsent = {
                    // GDPR scope: the UMP privacy options form is the legal
                    // surface; elsewhere the app's own screen handles it.
                    if (activity != null && graph.consent.privacyOptionsRequired(activity)) {
                        graph.consent.showPrivacyOptions(activity)
                    } else {
                        nav.navigate("${Routes.CONSENT}/settings")
                    }
                },
                onReplayIntro = {
                    nav.navigate(Routes.ONBOARDING) { popUpTo(Routes.HOME) }
                },
                onPrivacy = {
                    // The nav graph is not the Activity, so open through the
                    // context this composable already holds.
                    activity?.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(AppLinks.PRIVACY_POLICY),
                        )
                    )
                },
                onOffline = { nav.navigate(Routes.OFFLINE) },
                onLab = { nav.navigate(Routes.LAB) })
        }
        composable(Routes.OFFLINE) {
            com.mercato.app.ui.OfflineScreen(onRetry = { nav.popBackStack() })
        }
        composable(Routes.LAB) {
            // Dev-only surface; the Settings row is hidden in release, and
            // the route itself refuses to render outside debug builds.
            if (BuildConfig.DEBUG) {
                com.mercato.app.ui.LabScreen(graph, onBack = { nav.popBackStack() })
            }
        }
    }
}
