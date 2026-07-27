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
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .adHatch()
                .border(
                    DesignTokens.Border.hairline,
                    DesignTokens.Color.ink,
                    RoundedCornerShape(DesignTokens.Radius.card),
                ),
            contentAlignment = Alignment.Center,
        ) {
            CapsLabel("MERCATO")
        }
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
        InkButton(stringResource(R.string.retry), ButtonStyle.Primary, onClick = onRetry)
        Spacer(Modifier.weight(1.2f))
    }
}
