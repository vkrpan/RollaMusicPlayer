package com.rolla.musicplayer.feature.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent

/**
 * The home-screen widget's [GlanceAppWidget]. A thin, offline mirror of the playback service: it
 * observes [WidgetStateProvider.states] and a downscaled local artwork [android.graphics.Bitmap]
 * from inside the composition, then renders [MusicWidgetContent] -- it never holds its own player.
 *
 * State is observed INSIDE `provideContent`, not snapshotted before it: Glance keeps the session's
 * composition alive (~45s, extended by every `update` request -- and the service fires one every
 * second while playing) and `update`/`updateAll` never re-run [provideGlance] while it is alive.
 * A pre-`provideContent` snapshot therefore froze the progress bar, play/pause icon, and title for
 * as long as music played. The snapshot below only seeds the first frame.
 *
 * [SizeMode.Exact] so [MusicWidgetContent] can read the real launcher-allotted size via
 * `LocalSize` and stretch to fill it (the default `SizeMode.Single` only reports the provider's
 * minimum size).
 *
 * Dependencies come from [WidgetEntryPoint] (Hilt `@EntryPoint`), not constructor injection --
 * Glance instantiates this class itself via a no-arg constructor (see
 * `MusicWidgetReceiver.glanceAppWidget`), so there is no Hilt-managed constructor to inject into.
 */
class MusicWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.get(context)
        val stateProvider = entryPoint.widgetStateProvider()
        val artworkLoader = entryPoint.artworkLoader()
        val initialState = stateProvider.current()
        val initialArtwork = artworkLoader.load(initialState.artworkPath)

        provideContent {
            val state by stateProvider.states.collectAsState(initial = initialState)
            val artworkPath = state.artworkPath
            // Keyed on the path: re-resolves only on a track (album) change; the loader memoizes
            // the decode, so the first-frame re-run for the same path is a cheap memo hit.
            val artwork by produceState(initialArtwork, artworkPath) {
                value = artworkLoader.load(artworkPath)
            }
            GlanceTheme(colors = RollaWidgetColors) {
                MusicWidgetContent(context = context, state = state, artwork = artwork)
            }
        }
    }
}
