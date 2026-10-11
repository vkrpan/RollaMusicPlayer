package com.rolla.musicplayer.feature.player

import android.content.Context
import android.media.AudioManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Shows the system volume panel (spec §12 Now Playing Volume). A seam, so the ViewModel test needs no AudioManager. */
interface SystemVolumePanel {
    fun show()
}

internal class AudioManagerVolumePanel @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemVolumePanel {
    override fun show() {
        context.getSystemService(AudioManager::class.java)
            ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SystemVolumePanelModule {
    @Binds
    abstract fun bindSystemVolumePanel(impl: AudioManagerVolumePanel): SystemVolumePanel
}
