package com.rolla.musicplayer.feature.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens

/**
 * Root Glance composable for [MusicWidget], laid out per spec §13 (Phase 6c): a black card with 24 dp corners holding
 * a 69 dp artwork box with 12 dp corners on the left (the `#363636` placeholder with a white note when there is no
 * local artwork) and, on the right, a column of the title (16 sp Bold, one line) and artist (13 sp, one line), the
 * transport row (previous · −15 s · play/pause · +15 s · next) and a 3 dp progress bar under it. Tapping the card
 * outside the controls opens the app via [MAIN_ACTIVITY_CLASS_NAME].
 *
 * Sizes come from `RollaDimens.widget*` and colours from [GlanceTheme] (the widget palette in [RollaWidgetColors]).
 * [widgetLayoutFor] decides, from the `LocalSize` the launcher reports (`SizeMode.Exact`) and the system font scale,
 * whether the artwork shows (below 339 dp it hides, so each of the five control cells keeps a 48 dp box) and which of
 * two vertical modes the card uses (ruling P14). The launcher can report a smaller height than it draws (on the
 * Motorola edge 70 about 167 dp for a 211 dp two-row card), so centring needs height it is known to draw:
 * - centred ([WidgetLayout.centreContent]): the reported height holds the nominal content plus a corner clearance
 *   above and below. The row and the text column wrap their height and the card centres them.
 * - filled: the row fills the card the launcher draws, and its top and bottom padding is computed from the reported
 *   height. The text block takes what the controls row and the bar leave (`defaultWeight`), so a large font cuts the
 *   title or artist, never a control, and the bar's corner inset keeps its end inside the card's rounded corner.
 *
 * [context] is the [Context] Glance's `provideGlance` callback already received -- it is used synchronously within
 * this single composition pass (string lookups, the font scale, building the launch [ComponentName]) and is never
 * stored on a property or captured past this call, so there is no retained-Context risk.
 */
@Composable
internal fun MusicWidgetContent(context: Context, state: MusicWidgetState, artwork: Bitmap?) {
    val layout = widgetLayoutFor(LocalSize.current, context.resources.configuration.fontScale)
    Box(
        modifier = cardModifier(context),
        contentAlignment = if (layout.centreContent) Alignment.CenterStart else Alignment.TopStart,
    ) {
        Row(
            modifier = (if (layout.centreContent) GlanceModifier.fillMaxWidth() else GlanceModifier.fillMaxSize())
                .padding(
                    start = RollaDimens.widgetCardPaddingStart,
                    end = RollaDimens.widgetCardPaddingEnd,
                    top = layout.verticalPadding,
                    bottom = layout.verticalPadding,
                ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            if (layout.showArtwork) {
                WidgetArtwork(context = context, artwork = artwork, title = state.title)
                Spacer(modifier = GlanceModifier.width(RollaDimens.widgetArtGap))
            }
            WidgetTextColumn(context = context, state = state, layout = layout)
        }
    }
}

/**
 * The card: the rounded black shape drawable (rounded on every API) plus `cornerRadius`, which clips on API 31+; the
 * whole card opens the app.
 */
private fun cardModifier(context: Context): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .background(ImageProvider(R.drawable.widget_card_background))
    .cornerRadius(RollaDimens.widgetCardRadius)
    .clickable(actionStartActivity(ComponentName(context.packageName, MAIN_ACTIVITY_CLASS_NAME)))
    .semantics { contentDescription = context.getString(R.string.widget_action_open_app) }

/**
 * The right-hand column. Centred ([WidgetLayout.centreContent]), it wraps its height. Filled, it fills the row and the
 * text block takes what the controls row and the progress bar leave; [WidgetLayout.barBottomInset] lifts the bar clear
 * of the card's corner arc (ruling P12) and pads this column only, so the artwork stays centred.
 */
@Composable
private fun RowScope.WidgetTextColumn(context: Context, state: MusicWidgetState, layout: WidgetLayout) {
    val height = if (layout.centreContent) GlanceModifier else GlanceModifier.fillMaxHeight()
    Column(modifier = GlanceModifier.defaultWeight().then(height).padding(bottom = layout.barBottomInset)) {
        WidgetMetadata(context = context, state = state, fillHeight = !layout.centreContent)
        WidgetControlsRow(context = context, state = state)
        LinearProgressIndicator(
            progress = state.progress,
            modifier = GlanceModifier.fillMaxWidth().height(RollaDimens.widgetProgressHeight),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.surfaceVariant,
        )
    }
}

/** The artwork box: the local bitmap, cropped to fill, or the white note on the rounded placeholder. */
@Composable
private fun WidgetArtwork(context: Context, artwork: Bitmap?, title: String) {
    Box(
        modifier = GlanceModifier
            .size(RollaDimens.widgetArt)
            .background(ImageProvider(R.drawable.widget_art_placeholder))
            .cornerRadius(RollaDimens.widgetArtRadius),
        contentAlignment = Alignment.Center,
    ) {
        if (artwork != null) {
            Image(
                provider = ImageProvider(artwork),
                contentDescription = context.getString(R.string.widget_artwork_content_description, title),
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Image(
                provider = ImageProvider(R.drawable.widget_ic_music_note),
                contentDescription = context.getString(R.string.widget_artwork_placeholder_content_description),
                modifier = GlanceModifier.size(RollaDimens.widgetArtGlyph),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
            )
        }
    }
}

/**
 * The title and artist. With [fillHeight] (the filled mode) they sit bottom-aligned in the height the controls and bar
 * leave (`defaultWeight`), so text gives way first at a large font scale; without it (the centred mode) they wrap.
 */
@Composable
private fun ColumnScope.WidgetMetadata(context: Context, state: MusicWidgetState, fillHeight: Boolean) {
    val hasSong = state.title.isNotBlank()
    val title = if (hasSong) state.title else context.getString(R.string.widget_empty_title)
    val artist = if (hasSong) state.artist else context.getString(R.string.widget_empty_artist)
    Column(
        modifier = GlanceModifier.fillMaxWidth().let { if (fillHeight) it.defaultWeight() else it },
        verticalAlignment = Alignment.Vertical.Bottom,
    ) {
        Text(
            text = title,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = RollaDimens.widgetTitleSize,
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = artist,
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = RollaDimens.widgetArtistSize,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

/**
 * Five equal-weight cells across the text column, each one [RollaDimens.minTouchTarget] tall. LongMethod is
 * suppressed as before Phase 6c: five flat, named-argument calls in transport order read better than a table.
 */
@Suppress("LongMethod")
@Composable
private fun WidgetControlsRow(context: Context, state: MusicWidgetState) {
    Row(modifier = GlanceModifier.fillMaxWidth().height(RollaDimens.minTouchTarget)) {
        WidgetControl(
            iconRes = R.drawable.widget_ic_previous,
            contentDescription = context.getString(R.string.widget_action_previous),
            action = actionRunCallback<PreviousActionCallback>(),
        )
        WidgetControl(
            iconRes = R.drawable.widget_ic_skip_back_15,
            contentDescription = context.getString(R.string.widget_action_skip_back_15),
            action = actionRunCallback<SkipBack15ActionCallback>(),
        )
        WidgetControl(
            iconRes = if (state.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
            contentDescription = context.getString(
                if (state.isPlaying) R.string.widget_action_pause else R.string.widget_action_play,
            ),
            action = actionRunCallback<PlayPauseActionCallback>(),
        )
        WidgetControl(
            iconRes = R.drawable.widget_ic_skip_forward_15,
            contentDescription = context.getString(R.string.widget_action_skip_forward_15),
            action = actionRunCallback<SkipForward15ActionCallback>(),
        )
        WidgetControl(
            iconRes = R.drawable.widget_ic_next,
            contentDescription = context.getString(R.string.widget_action_next),
            action = actionRunCallback<NextActionCallback>(),
        )
    }
}

/**
 * One control: the whole cell (as wide as the cell, [RollaDimens.minTouchTarget] tall) is the clickable box and
 * carries the description, so the touch target is never shrunk to the glyph. The glyph inside is decorative.
 */
@Composable
private fun RowScope.WidgetControl(@DrawableRes iconRes: Int, contentDescription: String, action: Action) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(action)
            .semantics {
                this.contentDescription = contentDescription
                testTag = WIDGET_CONTROL_TAG
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            modifier = GlanceModifier.size(RollaDimens.widgetControlGlyph),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurface),
        )
    }
}

/** The test tag on each of the five control boxes (`MusicWidgetContentTest`). */
internal const val WIDGET_CONTROL_TAG = "widget_control"

/**
 * The main app's launcher activity, referenced by fully-qualified name only. `:feature:widget`
 * cannot depend on `:app` (feature -> core -> core:model is the only allowed direction; app
 * depends on features, never the reverse -- see CLAUDE.md's module dependency rules), so
 * [ComponentName] by string is the sanctioned indirection for "open the app" from a module that
 * structurally cannot import `MainActivity`.
 */
private const val MAIN_ACTIVITY_CLASS_NAME = "com.rolla.musicplayer.MainActivity"
