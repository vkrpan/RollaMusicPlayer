package com.rolla.musicplayer.feature.widget

import android.content.Context
import com.rolla.musicplayer.core.media.PlaybackController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hilt access point for the widget's `GlanceAppWidget`, `GlanceAppWidgetReceiver`, and
 * `ActionCallback` implementations.
 *
 * Glance instantiates those classes itself (there is no constructor Hilt can inject into), so
 * dependencies are pulled out of the Hilt graph manually via [EntryPointAccessors] instead of
 * `@Inject` constructor parameters -- see [get] for the call site helper.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    /** Dispatches widget control taps (play/pause, previous, next, ±15s) to the playback service. */
    fun playbackController(): PlaybackController

    /** Read-only snapshot of current playback state for rendering the widget. */
    fun widgetStateProvider(): WidgetStateProvider

    companion object {
        /**
         * Resolves [WidgetEntryPoint] from the application [Context]. Always pass
         * `context.applicationContext` from a receiver/callback -- never hold on to the [Context]
         * itself, to avoid leaking it past the single call that needs it.
         */
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
