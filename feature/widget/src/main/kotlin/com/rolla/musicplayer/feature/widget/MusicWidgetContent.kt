package com.rolla.musicplayer.feature.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

/**
 * Root Glance composable for [MusicWidget], laid out per `ui-style-guide.md` §8: a dark rounded
 * card with artwork + title/artist on top, a progress bar, and a five-icon control row
 * (previous · -15s · play/pause · +15s · next). Tapping anywhere on the card body (outside the
 * control icons) opens the app via [MAIN_ACTIVITY_CLASS_NAME].
 *
 * [context] is the [Context] Glance's `provideGlance` callback already received -- it is used
 * synchronously within this single composition pass (string lookups, building the launch
 * [ComponentName]) and is never stored on a property or captured past this call, so there is no
 * retained-Context risk.
 */
@Composable
internal fun MusicWidgetContent(context: Context, state: MusicWidgetState, artwork: Bitmap?) {
    val openAppAction = actionStartActivity(ComponentName(context.packageName, MAIN_ACTIVITY_CLASS_NAME))
    val artworkSize = artworkSizeFor(LocalSize.current)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_card_background))
            .padding(WidgetDimens.CardPadding)
            .clickable(openAppAction)
            .semantics { contentDescription = context.getString(R.string.widget_action_open_app) },
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // Header takes all height the fixed progress/controls rows leave: no dead space below.
            WidgetHeaderRow(
                context = context,
                state = state,
                artwork = artwork,
                artworkSize = artworkSize,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.height(WidgetDimens.SectionSpacing))
            WidgetProgressBar(progress = state.progress)
            Spacer(modifier = GlanceModifier.height(WidgetDimens.SectionSpacing))
            WidgetControlsRow(context = context, state = state)
        }
    }
}

@Composable
private fun WidgetProgressBar(progress: Float) {
    LinearProgressIndicator(
        modifier = GlanceModifier.fillMaxWidth().height(WidgetDimens.ProgressHeight),
        progress = progress,
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.surfaceVariant,
    )
}

/**
 * Grows the artwork into the header's leftover height (widget height minus padding and the fixed
 * progress/controls rows), capped by a fraction of the width so the title keeps room, and clamped
 * to `[ArtworkMinSize, ArtworkMaxSize]`.
 */
private fun artworkSizeFor(widgetSize: DpSize): Dp {
    val headerHeight = widgetSize.height - WidgetDimens.CardPadding * 2 - WidgetDimens.SectionSpacing * 2 -
        WidgetDimens.ProgressHeight - WidgetDimens.ControlTouchSize
    val widthCap = widgetSize.width * ARTWORK_MAX_WIDTH_FRACTION
    return minOf(headerHeight, widthCap).coerceIn(WidgetDimens.ArtworkMinSize, WidgetDimens.ArtworkMaxSize)
}

@Composable
private fun WidgetHeaderRow(
    context: Context,
    state: MusicWidgetState,
    artwork: Bitmap?,
    artworkSize: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetArtwork(context = context, artwork = artwork, title = state.title, size = artworkSize)
        Spacer(modifier = GlanceModifier.width(WidgetDimens.RowSpacing))
        WidgetMetadata(context = context, state = state, isLarge = artworkSize >= WidgetDimens.LargeTextArtworkSize)
    }
}

@Composable
private fun WidgetArtwork(context: Context, artwork: Bitmap?, title: String, size: Dp) {
    val artworkModifier = GlanceModifier
        .size(size)
        .background(ImageProvider(R.drawable.widget_card_background))
    if (artwork != null) {
        Image(
            provider = ImageProvider(artwork),
            contentDescription = context.getString(R.string.widget_artwork_content_description, title),
            modifier = artworkModifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(modifier = artworkModifier, contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_music_note),
                contentDescription = context.getString(R.string.widget_artwork_placeholder_content_description),
                modifier = GlanceModifier.size(WidgetDimens.PlaceholderIconSize),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun WidgetMetadata(context: Context, state: MusicWidgetState, isLarge: Boolean) {
    val hasSong = state.title.isNotBlank()
    val title = if (hasSong) state.title else context.getString(R.string.widget_empty_title)
    val artist = if (hasSong) state.artist else context.getString(R.string.widget_empty_artist)
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = title,
            maxLines = if (isLarge) 2 else 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = if (isLarge) WidgetDimens.TitleFontSizeLarge else WidgetDimens.TitleFontSize,
                fontWeight = FontWeight.Medium,
            ),
        )
        Text(
            text = artist,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = if (isLarge) WidgetDimens.ArtistFontSizeLarge else WidgetDimens.ArtistFontSize,
            ),
        )
    }
}

/** Each control sits in an equal-weight cell, so the five spread across the full card width. */
@Suppress("LongMethod")
@Composable
private fun WidgetControlsRow(context: Context, state: MusicWidgetState) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetIconButton(
            iconRes = R.drawable.widget_ic_previous,
            contentDescription = context.getString(R.string.widget_action_previous),
            action = actionRunCallback<PreviousActionCallback>(),
        )
        WidgetIconButton(
            iconRes = R.drawable.widget_ic_skip_back_15,
            contentDescription = context.getString(R.string.widget_action_skip_back_15),
            action = actionRunCallback<SkipBack15ActionCallback>(),
        )
        WidgetIconButton(
            iconRes = if (state.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
            contentDescription = context.getString(
                if (state.isPlaying) R.string.widget_action_pause else R.string.widget_action_play,
            ),
            action = actionRunCallback<PlayPauseActionCallback>(),
        )
        WidgetIconButton(
            iconRes = R.drawable.widget_ic_skip_forward_15,
            contentDescription = context.getString(R.string.widget_action_skip_forward_15),
            action = actionRunCallback<SkipForward15ActionCallback>(),
        )
        WidgetIconButton(
            iconRes = R.drawable.widget_ic_next,
            contentDescription = context.getString(R.string.widget_action_next),
            action = actionRunCallback<NextActionCallback>(),
        )
    }
}

/**
 * `.size(...).clickable(...).padding(...)` -- deliberately in that order. [WidgetDimens.ControlTouchSize]
 * (48dp, the a11y minimum) sets the tappable bounds and [androidx.glance.action.clickable] is
 * chained onto that FULL-size node; [WidgetDimens.ControlIconPadding] then insets only the icon
 * artwork drawn inside it. Padding before `clickable` would instead shrink the actual hit target
 * down to the icon's inner (24dp) box, silently failing the 48dp touch-target requirement.
 */
@Composable
private fun RowScope.WidgetIconButton(iconRes: Int, contentDescription: String, action: Action) {
    Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = contentDescription,
            modifier = GlanceModifier
                .size(WidgetDimens.ControlTouchSize)
                .clickable(action)
                .padding(WidgetDimens.ControlIconPadding),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        )
    }
}

/**
 * The main app's launcher activity, referenced by fully-qualified name only. `:feature:widget`
 * cannot depend on `:app` (feature -> core -> core:model is the only allowed direction; app
 * depends on features, never the reverse -- see CLAUDE.md's module dependency rules), so
 * [ComponentName] by string is the sanctioned indirection for "open the app" from a module that
 * structurally cannot import `MainActivity`.
 */
private const val MAIN_ACTIVITY_CLASS_NAME = "com.rolla.musicplayer.MainActivity"

/** Artwork never takes more than this share of the card width, so title/artist keep room. */
private const val ARTWORK_MAX_WIDTH_FRACTION = 0.4f

/** Dimension tokens for [MusicWidgetContent] -- sized per `ui-style-guide.md` §5/§8. */
private object WidgetDimens {
    val CardPadding = 12.dp
    val RowSpacing = 12.dp
    val SectionSpacing = 8.dp
    val ArtworkMinSize = 56.dp
    val ArtworkMaxSize = 160.dp
    val LargeTextArtworkSize = 80.dp
    val PlaceholderIconSize = 28.dp
    val ProgressHeight = 4.dp
    val ControlTouchSize = 48.dp
    val ControlIconPadding = 12.dp
    val TitleFontSize = 14.sp
    val ArtistFontSize = 12.sp
    val TitleFontSizeLarge = 16.sp
    val ArtistFontSizeLarge = 14.sp
}
