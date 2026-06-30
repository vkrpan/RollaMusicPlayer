package com.rolla.musicplayer.core.data.scanner

import android.net.Uri
import android.os.Build
import android.provider.MediaStore

internal object MediaStoreColumns {

    // MediaStore encodes the disc track as disc * 1_000 + track; modulo extracts the track.
    const val DISC_TRACK_MULTIPLIER = 1000

    val ID: String = MediaStore.Audio.Media._ID
    val TITLE: String = MediaStore.Audio.Media.TITLE
    val ARTIST: String = MediaStore.Audio.Media.ARTIST
    val ALBUM: String = MediaStore.Audio.Media.ALBUM
    val ALBUM_ID: String = MediaStore.Audio.Media.ALBUM_ID
    val DURATION: String = MediaStore.Audio.Media.DURATION
    val TRACK: String = MediaStore.Audio.Media.TRACK
    val YEAR: String = MediaStore.Audio.Media.YEAR
    val DATE_MODIFIED: String = MediaStore.Audio.Media.DATE_MODIFIED

    val PROJECTION: Array<String> = arrayOf(
        ID,
        TITLE,
        ARTIST,
        ALBUM,
        ALBUM_ID,
        DURATION,
        TRACK,
        YEAR,
        DATE_MODIFIED,
    )

    val SELECTION: String = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val SORT_ORDER: String = "$TITLE COLLATE NOCASE ASC"

    /** Local content URI used to build per-album artwork URIs — no network involved. */
    val ALBUM_ART_URI: Uri = Uri.parse("content://media/external/audio/albumart")

    val COLLECTION: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
}
