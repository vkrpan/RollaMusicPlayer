package com.rolla.musicplayer.core.data.scanner

import android.database.MatrixCursor
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.songArtworkUri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // New rows agree with toDomain's read-time derivation (spec §5.4). The cursor row has id 1, album id 10 and
    // date_modified 1000. Trivial stub ("") → RED; the old album-art uri → RED. Mutation: build the value from the
    // album id instead of the row id → "rolla-artwork://song/10?v=1000" → RED.
    @Test
    @Config(sdk = [29])
    fun readSongs_writesThePerTrackArtworkValue() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Chill/"))
        assertEquals(
            "the scanner stores the track's own versioned artwork value",
            songArtworkUri(1L.toString(), 1_000L),
            songs.single().artworkUri,
        )
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

    // GENRE exists from API 30. Mutations: never read the column (genre = null) → RED; leave GENRE out of PROJECTION →
    // the projection assertion is RED.
    @Test
    @Config(sdk = [30])
    fun api30ReadsTheGenre() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.RELATIVE_PATH, "Music/", genre = "Techno"))

        assertEquals("Techno", songs.single().genre)
        assertTrue(MediaStoreColumns.PROJECTION.contains(MediaStore.Audio.Media.GENRE))
    }

    // Mutation: keep the raw value (drop the blank check) → "  " → RED.
    @Test
    @Config(sdk = [30])
    fun api30BlankGenreReadsAsNull() {
        val songs = scanner.readSongs(cursorWith(MediaStore.Audio.Media.RELATIVE_PATH, "Music/", genre = "  "))

        assertNull(songs.single().genre)
    }

    /** A one-row cursor; [genre] adds the API 30+ GENRE column, which the scanner asks for only there. */
    private fun cursorWith(folderColumn: String, folderValue: String, genre: String? = null): MatrixCursor {
        val columns = listOfNotNull(
            MediaStoreColumns.ID, MediaStoreColumns.TITLE, MediaStoreColumns.ARTIST, MediaStoreColumns.ALBUM,
            MediaStoreColumns.ALBUM_ID, MediaStoreColumns.DURATION, MediaStoreColumns.TRACK, MediaStoreColumns.YEAR,
            MediaStoreColumns.DATE_MODIFIED, folderColumn, genre?.let { MediaStore.Audio.Media.GENRE },
        ).toTypedArray()
        val values = listOf<Any?>(1L, "Title", "Artist", "Album", 10L, 200_000L, 1, 2020, 1_000L, folderValue)
        return MatrixCursor(columns).apply {
            addRow((if (genre == null) values else values + genre).toTypedArray())
        }
    }
}
