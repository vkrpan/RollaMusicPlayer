package com.rolla.musicplayer.core.data.di

import android.content.Context
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCacheDir
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCacheImpl
import com.rolla.musicplayer.core.data.artwork.ArtworkDecoder
import com.rolla.musicplayer.core.data.artwork.BitmapArtworkDecoder
import com.rolla.musicplayer.core.data.repository.AlbumRepository
import com.rolla.musicplayer.core.data.repository.AlbumRepositoryImpl
import com.rolla.musicplayer.core.data.repository.ArtistRepository
import com.rolla.musicplayer.core.data.repository.ArtistRepositoryImpl
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.data.repository.SearchRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.data.repository.SettingsRepositoryImpl
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.data.repository.SongRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindSongRepository(impl: SongRepositoryImpl): SongRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: ArtistRepositoryImpl): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindAlbumArtworkCache(impl: AlbumArtworkCacheImpl): AlbumArtworkCache

    @Binds
    @Singleton
    abstract fun bindArtworkDecoder(impl: BitmapArtworkDecoder): ArtworkDecoder

    companion object {

        /** App-specific cache dir (auto-reclaimable by the OS) -- see `AlbumArtworkCacheImpl`. */
        @Provides
        @Singleton
        @AlbumArtworkCacheDir
        fun provideAlbumArtworkCacheDir(@ApplicationContext context: Context): File =
            File(context.cacheDir, ALBUM_ART_CACHE_DIR_NAME)

        private const val ALBUM_ART_CACHE_DIR_NAME = "album_art"
    }
}
