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
import androidx.navigation.compose.rememberNavController
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
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as MercatoApplication).graph
        // GDPR consent surface: shows the UMP form when required, silent
        // no-op elsewhere. The outcome lands in the core via setAdConsent.
        graph.consent.gather(this)
        setContent {
            CompositionLocalProvider(LocalFonts provides rememberFonts()) {
                Box(Modifier.fillMaxSize().appBackground()) {
                    MercatoNav(graph)
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

/** One ViewModel for the whole round/recap flow, scoped to the activity. */
private class GameVmFactory(private val graph: AppGraph) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = GameViewModel(graph) as T
}

@Composable
fun MercatoNav(graph: AppGraph) {
    val nav = rememberNavController()
    val vm: GameViewModel = viewModel(factory = GameVmFactory(graph))
    val onboarded by graph.prefs.onboarded.collectAsState(initial = null)

    NavHost(nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen {
                nav.navigate(if (onboarded == true) Routes.HOME else Routes.ONBOARDING) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            }
        }
        composable(Routes.ONBOARDING) {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            OnboardingScreen(onDone = {
                // Where UMP already collected GDPR consent, the app's own
                // consent screen would double-ask; skip straight home.
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
                })
        }
    }
}
