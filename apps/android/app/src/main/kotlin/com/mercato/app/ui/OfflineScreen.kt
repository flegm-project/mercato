package com.mercato.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mercato.app.R
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import com.mercato.design.DesignTokens

/**
 * 12 Offline: hatched placeholder mark, title, body, RETRY. Gameplay is
 * fully offline (the dataset ships in the APK), so retry simply leaves the
 * screen; it exists for score sync once a backend appears.
 */
@Composable
fun OfflineScreen(onRetry: () -> Unit) {
    ScreenColumn {
        // iOS pads the column by the gutter on both axes (Screens.swift:783).
        Gap(DesignTokens.Space.gutter)
        // Equal flexible space above and below the mark and its copy, which is
        // what puts RETRY on the bottom edge. Android weighted the two spacers
        // 1 to 1.2 and placed the button before the second one, so the button
        // sat just under the copy and the empty half of the screen was below
        // it (Screens.swift:751).
        Spacer(Modifier.weight(1f))
        Gap(DesignTokens.Space.xl)
        // iOS shows a bare 92x92 hatched square, no wordmark stamped on it
        // (Screens.swift:772). A full-width 160dp strip read as a dead ad slot.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(92.dp)
                .solidRaised(radius = DesignTokens.Radius.large, depth = 0.dp, border = 4.dp)
                .adHatch()
        )
        Gap(DesignTokens.Space.xl)
        Text(
            stringResource(R.string.offT),
            style = typeStyle(DesignTokens.Type.offlineTitle, DesignTokens.Color.ivory),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(12.dp)
        Text(
            stringResource(R.string.offB),
            style = typeStyle(
                DesignTokens.Type.bodySoft,
                DesignTokens.Color.ivory.dim(DesignTokens.Opacity.textMuted),
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.xl)
        Spacer(Modifier.weight(1f))
        Gap(DesignTokens.Space.xl)
        InkButton(
            stringResource(R.string.retry), ButtonStyle.Primary,
            fontSize = 17.sp, fontWeight = 900, tracking = -0.03f,
            depth = 8.dp, radius = DesignTokens.Radius.button,
            verticalPadding = 17.dp, onClick = onRetry,
        )
        Gap(DesignTokens.Space.gutter)
    }
}
