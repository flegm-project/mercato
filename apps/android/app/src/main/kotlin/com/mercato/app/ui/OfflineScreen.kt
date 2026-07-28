package com.mercato.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
        Spacer(Modifier.weight(1f))
        // iOS shows a bare 92x92 hatched square, no wordmark stamped on it
        // (Screens.swift:772). A full-width 160dp strip read as a dead ad slot.
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(92.dp)
                .solidRaised(radius = 22.dp, depth = 0.dp)
                .adHatch()
        )
        Gap(DesignTokens.Space.xl)
        Text(
            stringResource(R.string.offT),
            style = typeStyle(DesignTokens.Type.screenTitle, DesignTokens.Color.ivory),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.sm)
        Text(
            stringResource(R.string.offB),
            style = typeStyle(DesignTokens.Type.body, DesignTokens.Color.ivory.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Gap(DesignTokens.Space.xl)
        InkButton(
            stringResource(R.string.retry), ButtonStyle.Primary,
            fontSize = 17.sp, fontWeight = 900, tracking = -0.03f,
            depth = 9.dp, radius = 20.dp, onClick = onRetry,
        )
        Spacer(Modifier.weight(1.2f))
    }
}
