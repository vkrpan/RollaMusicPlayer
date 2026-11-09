package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
 * [RollaIcons.Close] / "Close selection" while a list is in selection mode. Tapping it calls [onNavigateUp]; while
 * [navigationEnabled] is false it is disabled (faded, ignores taps), as Home's ✕ is while selected tracks are written.
 *
 * The title is one line in [titleStyle] (screenTitle). With a [compactTitleStyle], the title is measured against the
 * width the bar leaves it, and only when it would not fit on one line in [titleStyle] is it drawn in
 * [compactTitleStyle] instead (Home's selection bar at large font, ruling P24); it still ellipsizes if even that does
 * not fit, and never wraps.
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
    navigationEnabled: Boolean = true,
    titleStyle: TextStyle = MaterialTheme.typography.screenTitle,
    compactTitleStyle: TextStyle? = null,
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
            enabled = navigationEnabled,
        )
        Spacer(modifier = Modifier.width(RollaDimens.detailTitleGap))
        if (compactTitleStyle == null) {
            DetailTitle(text = title, style = titleStyle, modifier = Modifier.weight(1f))
        } else {
            FittingDetailTitle(
                text = title,
                style = titleStyle,
                compactStyle = compactTitleStyle,
                modifier = Modifier.weight(1f),
            )
        }
        actions()
    }
}

/** The detail bar's title: one line in [style], ellipsized, a heading for TalkBack. */
@Composable
private fun DetailTitle(text: String, style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * [DetailTitle] in [style] when [text] fits on one line in the width this slot gets, else in [compactStyle]. The fit
 * is measured with a [androidx.compose.ui.text.TextMeasurer] at the current density and font scale, not guessed.
 */
@Composable
private fun FittingDetailTitle(text: String, style: TextStyle, compactStyle: TextStyle, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val maxWidth = constraints.maxWidth
        val fits = remember(measurer, text, style, maxWidth) {
            measurer.measure(text = text, style = style, maxLines = 1, softWrap = false).size.width <= maxWidth
        }
        DetailTitle(text = text, style = if (fits) style else compactStyle, modifier = Modifier.fillMaxWidth())
    }
}
