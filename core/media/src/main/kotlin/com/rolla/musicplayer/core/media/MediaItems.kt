package com.rolla.musicplayer.core.media

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.artistDisplayName

/**
 * The [MediaMetadata.extras] key that carries a song's raw artist name (Phase 7 ruling P8). [MediaMetadata.artist]
 * holds the display name, which the notification and the System UI media card show; artists are keyed by the raw
 * name (Phase 4a ruling 12), so everything that turns metadata back into data reads this extra ([rawArtist]).
 */
internal const val EXTRA_RAW_ARTIST = "com.rolla.musicplayer.core.media.RAW_ARTIST"

/**
 * The one way a [Song] becomes a Media3 item: PlaybackController, the restore and playback resumption share it. The
 * artist is shown as [artistDisplayName] (MediaStore's `<unknown>` as "Unknown"), and the raw name rides in
 * [EXTRA_RAW_ARTIST].
 */
internal fun Song.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(id)
        .setUri(contentUri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistDisplayName(artist))
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri.ifEmpty { null }?.toUri())
                .setExtras(Bundle().apply { putString(EXTRA_RAW_ARTIST, artist) })
                .build(),
        )
        .build()

/** The raw artist name: [EXTRA_RAW_ARTIST] when [toMediaItem] wrote it, else [MediaMetadata.artist]. */
internal fun MediaMetadata.rawArtist(): String = extras?.getString(EXTRA_RAW_ARTIST) ?: artist?.toString().orEmpty()

/**
 * The current [Song] as the service rebuilds it from a Media3 item for PlaybackStateHolder. The artist is the raw name
 * ([rawArtist]): Now Playing's "Go to artist" routes ArtistDetail with it. The item carries no album id, duration,
 * track number or year, so those are 0/null.
 */
internal fun MediaItem.toPlaybackSong(): Song = Song(
    id = mediaId,
    title = mediaMetadata.title?.toString().orEmpty(),
    artist = mediaMetadata.rawArtist(),
    album = mediaMetadata.albumTitle?.toString().orEmpty(),
    albumId = 0L,
    durationMs = 0L,
    trackNumber = null,
    year = null,
    contentUri = localConfiguration?.uri?.toString().orEmpty(),
    artworkUri = mediaMetadata.artworkUri?.toString().orEmpty(),
)

/** A playable leaf for a MediaLibrarySession result (Media3's LibraryResult.verifyMediaItem requires both flags). */
internal fun MediaItem.asPlayableLeaf(): MediaItem =
    buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setIsBrowsable(false).setIsPlayable(true).build())
        .build()

internal fun RepeatMode.toPlayerRepeatMode(): Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
}

internal fun Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}
