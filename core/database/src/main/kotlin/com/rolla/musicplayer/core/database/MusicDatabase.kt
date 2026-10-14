package com.rolla.musicplayer.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.rolla.musicplayer.core.database.converter.ShortListConverter
import com.rolla.musicplayer.core.database.dao.AlbumDao
import com.rolla.musicplayer.core.database.dao.ArtistDao
import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.dao.PlaylistDao
import com.rolla.musicplayer.core.database.dao.SearchDao
import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        EqualizerPresetEntity::class,
        PlaybackQueueEntity::class,
        PlaybackStateEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
@TypeConverters(ShortListConverter::class)
abstract class MusicDatabase : RoomDatabase() {

    abstract fun songDao(): SongDao

    abstract fun playlistDao(): PlaylistDao

    abstract fun equalizerPresetDao(): EqualizerPresetDao

    // A new DAO getter over the existing `songs` table — not a schema change, so this does NOT
    // require a version bump or migration (see SearchDao's KDoc for the full rationale).
    abstract fun searchDao(): SearchDao

    // Same rationale as searchDao() above — album-detail queries over the existing `songs`
    // table, no schema change (see AlbumDao's KDoc).
    abstract fun albumDao(): AlbumDao

    // Same rationale as albumDao() above — artist-detail queries over the existing `songs`
    // table, no schema change (see ArtistDao's KDoc).
    abstract fun artistDao(): ArtistDao

    // Folder queries over `songs.folder_path`. Unlike the DAOs above, this one comes WITH a schema change:
    // the v4 column, added by MIGRATION_3_4 (see FolderDao's KDoc).
    abstract fun folderDao(): FolderDao

    // The saved playback queue and state (queue restore). Comes with a schema change: the v5 tables, added by
    // MIGRATION_4_5 (see PlaybackQueueDao's KDoc).
    abstract fun playbackQueueDao(): PlaybackQueueDao

    companion object {
        const val DATABASE_NAME = "music_database"
    }
}
