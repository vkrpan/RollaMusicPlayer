package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent

/**
 * The home-screen widget's [GlanceAppWidget]. A thin, offline mirror of the playback service: it
 * snapshots the current [MusicWidgetState] and a downscaled local artwork [android.graphics.Bitmap]
 * once per composition pass, then renders [MusicWidgetContent] -- it never holds its own player.
 *
 * Dependencies come from [WidgetEntryPoint] (Hilt `@EntryPoint`), not constructor injection --
 * Glance instantiates this class itself via a no-arg constructor (see
 * `MusicWidgetReceiver.glanceAppWidget`), so there is no Hilt-managed constructor to inject into.
 */
class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.get(context)
        val state = entryPoint.widgetStateProvider().current()
        val artwork = entryPoint.artworkLoader().load(state.artworkPath)

        provideContent {
            GlanceTheme(colors = RollaWidgetColors) {
                MusicWidgetContent(context = context, state = state, artwork = artwork)
            }
        }
    }
}
