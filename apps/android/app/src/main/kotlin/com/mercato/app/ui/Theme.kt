@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.mercato.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.mercato.design.DesignTokens

/**
 * Typography per the design system: Unbounded for display numbers and big
 * titles, Figtree for everything else, IBM Plex Mono for data readouts.
 * Fonts ship as assets (see stageAssets in build.gradle.kts); the generated
 * [DesignTokens] object carries the colors, radii and spacings.
 */
class MercatoFonts(context: Context) {
    private val assets = context.assets

    val display = FontFamily(
        Font("fonts/Unbounded-Black.ttf", assets, FontWeight.Black),
        Font("fonts/Unbounded-ExtraBold.ttf", assets, FontWeight.ExtraBold),
    )
    val body = FontFamily(
        Font("fonts/Figtree-Medium.ttf", assets, FontWeight.Medium),
        Font("fonts/Figtree-SemiBold.ttf", assets, FontWeight.SemiBold),
        Font("fonts/Figtree-Bold.ttf", assets, FontWeight.Bold),
        Font("fonts/Figtree-ExtraBold.ttf", assets, FontWeight.ExtraBold),
        Font("fonts/Figtree-Black.ttf", assets, FontWeight.Black),
    )
    val mono = FontFamily(
        Font("fonts/IBMPlexMono-Medium.ttf", assets, FontWeight.Medium),
    )
}

val LocalFonts = staticCompositionLocalOf<MercatoFonts> {
    error("MercatoFonts not provided")
}

@Composable
fun rememberFonts(): MercatoFonts {
    val context = LocalContext.current
    return androidx.compose.runtime.remember { MercatoFonts(context) }
}
