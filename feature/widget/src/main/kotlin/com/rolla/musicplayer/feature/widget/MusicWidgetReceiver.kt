package com.rolla.musicplayer.feature.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * The `AppWidgetProvider` entry point the launcher/system binds to (declared in
 * `AndroidManifest.xml`, configured by `res/xml/music_widget_info.xml`). Holds no state of its
 * own -- [GlanceAppWidgetReceiver] forwards every lifecycle callback (`onUpdate`,
 * `onAppWidgetOptionsChanged`, `onDeleted`, ...) to [glanceAppWidget], and a fresh [MusicWidget]
 * instance is cheap to allocate per callback (it carries no fields itself; all state comes from
 * [WidgetEntryPoint] at render time), so there is nothing here that could retain a stale
 * `Context`/player reference between calls.
 */
class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
