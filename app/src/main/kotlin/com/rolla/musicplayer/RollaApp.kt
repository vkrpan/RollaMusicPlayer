package com.rolla.musicplayer

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.rolla.musicplayer.artwork.AlbumArtworkInterceptor
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import com.rolla.musicplayer.core.media.PlaybackController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Implements [ImageLoaderFactory] so Coil's singleton loader (used by every `AsyncImage`) is built
 * with [AlbumArtworkInterceptor], routing album artwork through the app-local downscaled
 * [AlbumArtworkCache]. Coil calls [newImageLoader] lazily on the first image request — long after
 * `onCreate` has run Hilt field injection — so [albumArtworkCache] is always populated by then.
 */
@HiltAndroidApp
class RollaApp : Application(), ImageLoaderFactory {

    @Inject
    lateinit var playbackController: PlaybackController

    @Inject
    lateinit var albumArtworkCache: AlbumArtworkCache

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    playbackController.release()
                }
            },
        )
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(AlbumArtworkInterceptor(albumArtworkCache)) }
            .build()
}
