package com.rolla.musicplayer.core.designsystem.component

import android.os.Build
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.rolla.musicplayer.core.designsystem.theme.LocalRollaDarkTheme
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.sectionHeader

/**
 * The One UI bottom sheet (spec §8.7): surfaceContainerHigh, the large shape with square bottom corners, and an
 * optional sectionHeader [title] that TalkBack reads as a heading. The sheet's window is edge to edge (M3 1.3.0), so
 * statusBarsPadding() keeps the expanded sheet below the status bar; it is composed, so it reads the sheet window's
 * insets (the QueueSheet lesson, Phase 3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OneUiBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
        modifier = modifier.statusBarsPadding(),
    ) {
        SheetWindowSystemBars(darkTheme = LocalRollaDarkTheme.current)
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.sectionHeader,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = RollaDimens.listThumbStart)
                    .semantics { heading() },
            )
        }
        content()
    }
}

/**
 * Fix F1 (3-button navigation). M3 1.3.0 gives the sheet its own edge-to-edge dialog window with a transparent
 * navigation bar, and that window keeps the platform default enforceNavigationBarContrast = true. On 3-button
 * navigation its DecorView then paints a contrast scrim (90% white in light, translucent black in dark) across the
 * navigation-bar strip, on top of the sheet's own scrim and surface, so the strip is undimmed and hides what the sheet
 * draws there. Turning enforcement off lets the sheet's scrim and its surfaceContainerHigh surface (M3 lays it out to
 * the screen bottom; its content is padded above the bar) show through. The navigation buttons then sit on the sheet,
 * so their appearance follows the app's resolved theme, and so do the status-bar icons over the dimmed app: M3 sets
 * both from isSystemInDarkTheme(), which is wrong when Settings forces a theme.
 *
 * Composed inside the sheet, so LocalView is the sheet window's Compose view, whose parent provides the window.
 */
@Composable
private fun SheetWindowSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window ?: return
    DisposableEffect(window, darkTheme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightNavigationBars = !darkTheme
            isAppearanceLightStatusBars = !darkTheme
        }
        onDispose {}
    }
}
