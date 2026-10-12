package com.rolla.musicplayer.core.data.scanner

import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class MediaScannerReadTest {

    private val scanner = MediaScanner(ApplicationProvider.getApplicationContext())

    // Mutation: FOLDER = DATA on every API → getColumnIndexOrThrow(DATA) on a RELATIVE_PATH cursor throws → RED.
    @Test
    @Config(sdk = [29])
    fun api29ReadsTheFolderFromRelativePath() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Chill/"))
        assertEquals("Music/Chill", songs.single().folderPath)
    }

    // Mutations: FOLDER = RELATIVE_PATH on every API → throws → RED; pass SDK 29 to fromColumn → the DATA value is
    // trimmed as a relative path → "storage/emulated/0/Music/Chill/a.mp3" → RED.
    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun api28ReadsTheFolderFromData() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.DATA, "/storage/emulated/0/Music/Chill/a.mp3"))
        assertEquals("Music/Chill", songs.single().folderPath)
    }

    // The MatrixCursor tests cannot see the projection: a cursor built by hand always has the column. Mutation: leave
    // FOLDER out of PROJECTION → RED here (on a device the real query would throw at getColumnIndexOrThrow).
    @Test
    @Config(sdk = [29])
    fun projectionAsksForTheFolderColumn() {
        assertTrue(MediaStoreColumns.PROJECTION.contains(MediaStore.Audio.Media.RELATIVE_PATH))
    }

    // The API 24–28 twin: RELATIVE_PATH does not exist there, so asking for it would make the real query throw.
    // Mutation: replace FOLDER in PROJECTION with MediaStore.Audio.Media.RELATIVE_PATH → RED.
    @Test
    @Config(sdk = [28])
    @Suppress("DEPRECATION")
    fun api28ProjectionAsksForDataNotRelativePath() {
        val projection = MediaStoreColumns.PROJECTION
        assertTrue(
            "API 28 must project DATA and not RELATIVE_PATH, was ${projection.toList()}",
            projection.contains(MediaStore.Audio.Media.DATA) &&
                !projection.contains(MediaStore.Audio.Media.RELATIVE_PATH),
        )
    }

    private fun cursorWith(folderColumn: String, folderValue: String): MatrixCursor {
        val columns = arrayOf(
            MediaStoreColumns.ID, MediaStoreColumns.TITLE, MediaStoreColumns.ARTIST, MediaStoreColumns.ALBUM,
            MediaStoreColumns.ALBUM_ID, MediaStoreColumns.DURATION, MediaStoreColumns.TRACK, MediaStoreColumns.YEAR,
            MediaStoreColumns.DATE_MODIFIED, folderColumn,
        )
        return MatrixCursor(columns).apply {
            addRow(arrayOf<Any?>(1L, "Title", "Artist", "Album", 10L, 200_000L, 1, 2020, 1_000L, folderValue))
        }
    }
}
