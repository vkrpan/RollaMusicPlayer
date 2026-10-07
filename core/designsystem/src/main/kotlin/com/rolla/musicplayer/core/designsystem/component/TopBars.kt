package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.rolla.musicplayer.core.designsystem.icon.RollaIcons
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.appTitle
import com.rolla.musicplayer.core.designsystem.theme.screenTitle

/**
 * Home header: the bold app title on the left and trailing action icons on the right (spec §7.2). It owns the
 * status-bar inset by default.
 */
@Composable
fun OneUiTopBar(
    title: String,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .heightIn(min = RollaDimens.headerHeight)
            .padding(start = RollaDimens.headerTitleStart, end = RollaDimens.topBarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.appTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}

/**
 * Bar for pushed screens: a thin "<" chevron, then the bold title (spec §6).
 * It owns the status-bar inset by default.
 *
 * [navigationIcon] and [navigationContentDescription] replace the leading chevron, for example with
 * [RollaIcons.Close] / "Close selection" while a list is in selection mode. Tapping it calls [onNavigateUp].
 */
// Each parameter is a distinct, defaulted slot of the bar; grouping them would only add a holder type.
@Suppress("LongParameterList")
@Composable
fun OneUiDetailTopBar(
    title: String,
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: ImageVector = RollaIcons.ChevronBack,
    navigationContentDescription: String = "Navigate up",
    windowInsets: WindowInsets = WindowInsets.statusBars,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .heightIn(min = RollaDimens.headerHeight)
            .padding(horizontal = RollaDimens.topBarEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OneUiIconButton(
            icon = navigationIcon,
            contentDescription = navigationContentDescription,
            onClick = onNavigateUp,
        )
        Spacer(modifier = Modifier.width(RollaDimens.detailTitleGap))
        Text(
            text = title,
            style = MaterialTheme.typography.screenTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
    }
}
