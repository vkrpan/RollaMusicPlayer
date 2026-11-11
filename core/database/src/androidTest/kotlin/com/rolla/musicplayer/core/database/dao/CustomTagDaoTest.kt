package com.rolla.musicplayer.core.database.dao

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.SongCustomTagCrossRef
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The v6 custom tag lists, their never-reused ID counters and the song links (custom tags phase 1, Task 4). */
@RunWith(AndroidJUnit4::class)
class CustomTagDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var dao: CustomTagDao
    private lateinit var songDao: SongDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.customTagDao()
        songDao = database.songDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(id: String, mediaStoreId: Long = id.hashCode().toLong(), dateModified: Long = 1_000L) =
        SongEntity(
            id = id,
            mediaStoreId = mediaStoreId,
            title = "Song $id",
            artist = "Artist",
            album = "Album",
            albumId = 10L,
            durationMs = 180_000L,
            trackNumber = 1,
            year = 2024,
            contentUri = "content://media/external/audio/media/$mediaStoreId",
            artworkUri = "rolla-artwork://song/$id",
            dateModified = dateModified,
        )

    private fun link(songId: String, kind: CustomTagKind, tagId: Long) = SongCustomTagCrossRef(songId, kind, tagId)

    private suspend fun linksOf(songId: String): Set<SongCustomTagCrossRef> =
        dao.observeSongCustomTagIds(songId).first().toSet()

    private suspend fun songRow(id: String): SongEntity = requireNotNull(songDao.observeSong(id).first())

    // Mutation: one shared counter for both kinds → the SUBTAG's first ID is 3 → RED.
    @Test
    fun insertNew_issuesOneThenTwoPerKind() = runTest {
        assertEquals(1L, dao.insertNew(CustomTagKind.TAG, "Techno", "techno"))
        assertEquals(2L, dao.insertNew(CustomTagKind.TAG, "House", "house"))
        assertEquals(1L, dao.insertNew(CustomTagKind.SUBTAG, "Peak", "peak"))

        val tags = dao.observeCustomTags(CustomTagKind.TAG).first()
        assertEquals(listOf(1L to "Techno", 2L to "House"), tags.map { it.id to it.name })
        assertEquals("Peak", dao.findById(CustomTagKind.SUBTAG, 1L)?.name)
    }

    // Mutation: compute the next ID from MAX(id) → the deleted top row's ID 2 comes back → RED.
    @Test
    fun insertNew_afterDeleteOfTopRow_neverReusesId() = runTest {
        dao.insertNew(CustomTagKind.TAG, "Techno", "techno")
        dao.insertNew(CustomTagKind.TAG, "House", "house")
        database.openHelper.writableDatabase.execSQL("DELETE FROM custom_tags WHERE kind = 'TAG' AND id = 2")

        assertEquals(3L, dao.insertNew(CustomTagKind.TAG, "Trance", "trance"))
    }

    // Mutation: the (kind, name_key) index without `unique = true` → no throw → RED.
    @Test
    fun insertNew_duplicateNameKey_throws() = runTest {
        dao.insertNew(CustomTagKind.TAG, "Techno", "techno")

        try {
            dao.insertNew(CustomTagKind.TAG, "TECHNO", "techno")
            fail("A second TAG with the same name key must fail the insert")
        } catch (expected: SQLiteConstraintException) {
            // The unique (kind, name_key) index rejected it.
        }
        assertEquals(1L, dao.insertNew(CustomTagKind.SUBTAG, "Techno", "techno"))
        // A failed insert does not consume an ID: the next TAG is 2.
        assertEquals(2L, dao.insertNew(CustomTagKind.TAG, "House", "house"))
    }

    // Mutation: drop insertNew's ceiling check → TAG 1000000000 is inserted, no throw → RED.
    @Test
    fun insertNew_atCeiling_throwsAndInsertsNothing() = runTest {
        dao.raiseCounter(CustomTagKind.TAG, MAX_CUSTOM_TAG_ID)

        try {
            dao.insertNew(CustomTagKind.TAG, "Techno", "techno")
            fail("A list at the ceiling must refuse a new ID")
        } catch (expected: IllegalStateException) {
            // insertNew's guard refused it before inserting.
        }
        assertEquals(emptyList<Long>(), dao.observeCustomTags(CustomTagKind.TAG).first().map { it.id })
        assertEquals(MAX_CUSTOM_TAG_ID, dao.highestId(CustomTagKind.TAG))
    }

    // Mutation: skip the delete of the song's old links → {TAG 2, SUBTAG 1, TAG 3} remain → RED.
    @Test
    fun applyFileTags_replacesLinksAndSetsGenreAndReadFlag() = runTest {
        songDao.upsertSongs(listOf(song("a")))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(1L)), "House", 0L)

        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(3L), subTagIds = emptySet()), "Techno", 0L)

        assertEquals(setOf(link("a", CustomTagKind.TAG, 3L)), linksOf("a"))
        val row = songRow("a")
        assertEquals("Techno", row.genre)
        assertTrue(row.fileTagsRead)
    }

    // Mutation: skip raising the counter → the next ID is 1 → RED.
    @Test
    fun applyFileTags_raisesCounterToHighestFileId() = runTest {
        songDao.upsertSongs(listOf(song("a")))

        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(5L, 40L), subTagIds = emptySet()), null, 0L)

        assertEquals(41L, dao.insertNew(CustomTagKind.TAG, "Techno", "techno"))
        assertEquals(1L, dao.insertNew(CustomTagKind.SUBTAG, "Peak", "peak"))
    }

    // Mutation: set the counter to the candidate (`highest_id = :candidate`) → the counter drops to 3 → 4 → RED.
    @Test
    fun applyFileTags_lowerIdsNeverLowerCounter() = runTest {
        songDao.upsertSongs(listOf(song("a"), song("b")))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(40L), subTagIds = emptySet()), null, expectedGeneration = 0L)
        assertEquals(41L, dao.insertNew(CustomTagKind.TAG, "Techno", "techno"))

        dao.applyFileTags("b", CustomTagIds(tagIds = setOf(3L), subTagIds = emptySet()), null, expectedGeneration = 0L)

        assertEquals(42L, dao.insertNew(CustomTagKind.TAG, "House", "house"))
    }

    // The pass read a file whose song row the next scan deleted. The generation check finds no row and writes nothing:
    // no link and no counter raise, so the file's ID 50 does not move the next ID (phase 2 Task 1; before it, the link
    // insert failed on the songs foreign key and the transaction rolled back).
    // Mutation: treat a missing row as matching (`?: expectedGeneration`) → the link insert throws the foreign-key
    // SQLiteConstraintException → RED.
    @Test
    fun applyFileTags_onDeletedSong_isSkippedAndKeepsCounter() = runTest {
        songDao.upsertSongs(listOf(song("a", mediaStoreId = 7L)))
        songDao.deleteByMediaStoreIds(listOf(7L))

        val applied = dao.applyFileTags(
            "a",
            CustomTagIds(tagIds = setOf(50L), subTagIds = emptySet()),
            "Techno",
            expectedGeneration = 0L,
        )

        assertFalse(applied)
        assertEquals(emptyList<SongCustomTagCrossRef>(), dao.observeSongCustomTagIds("a").first())
        assertEquals(1L, dao.insertNew(CustomTagKind.TAG, "Techno", "techno"))
    }

    // Mutation: skip when the generations are equal (`==` for `!=`) → 0 == 0 → skipped, false → RED.
    @Test
    fun applyFileTags_matchingGeneration_applies() = runTest {
        songDao.upsertSongs(listOf(song("a")))

        val applied = dao.applyFileTags(
            "a",
            CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(1L)),
            "Techno",
            expectedGeneration = 0L,
        )

        assertTrue(applied)
        assertEquals(setOf(link("a", CustomTagKind.TAG, 2L), link("a", CustomTagKind.SUBTAG, 1L)), linksOf("a"))
        assertEquals("Techno", songRow("a").genre)
        assertTrue(songRow("a").fileTagsRead)
    }

    // The scanner reset the row (a save, a changed file) after the pass queried it at generation 0: the pass's read is
    // stale and must not overwrite anything.
    // Mutation: drop the generation check → the stale read applies: links {TAG 50}, counter 50 → RED.
    @Test
    fun applyFileTags_staleGeneration_isSkipped() = runTest {
        songDao.upsertSongs(listOf(song("a")))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(4L), subTagIds = emptySet()), "House", 0L)
        resetRow("a", generation = 1L)

        val applied = dao.applyFileTags(
            "a",
            CustomTagIds(tagIds = setOf(50L), subTagIds = emptySet()),
            "Techno",
            expectedGeneration = 0L,
        )

        assertFalse(applied)
        assertEquals(setOf(link("a", CustomTagKind.TAG, 4L)), linksOf("a"))
        val row = songRow("a")
        assertEquals("House", row.genre)
        assertFalse(row.fileTagsRead)
        assertEquals(1L, row.fileTagsGeneration)
        assertEquals(4L, dao.highestId(CustomTagKind.TAG))
    }

    // Mutation: the songs foreign key without ON DELETE CASCADE → the links outlive the song → RED.
    @Test
    fun songDelete_cascadesLinks() = runTest {
        songDao.upsertSongs(listOf(song("a", mediaStoreId = 7L)))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(1L), subTagIds = setOf(2L)), null, expectedGeneration = 0L)

        songDao.deleteByMediaStoreIds(listOf(7L))

        assertEquals(emptyList<SongCustomTagCrossRef>(), dao.observeSongCustomTagIds("a").first())
    }

    // Control: SongDao.upsertSongs is an @Upsert (update in place). Mutation: @Insert(REPLACE) → the row is deleted
    // and re-inserted, the cascade empties the links → RED.
    @Test
    fun songUpsert_keepsLinks() = runTest {
        songDao.upsertSongs(listOf(song("a")))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(1L), subTagIds = setOf(2L)), null, expectedGeneration = 0L)

        songDao.upsertSongs(listOf(song("a").copy(title = "Renamed")))

        assertEquals(
            setOf(link("a", CustomTagKind.TAG, 1L), link("a", CustomTagKind.SUBTAG, 2L)),
            linksOf("a"),
        )
    }

    // Mutation: ORDER BY date_modified ASC → ids for 1, 2 → RED.
    @Test
    fun songsNeedingFileTagRead_newestFirstAndLimited() = runTest {
        songDao.upsertSongs(
            listOf(
                song("one", dateModified = 1L),
                song("three", dateModified = 3L),
                song("two", dateModified = 2L),
                song("read", dateModified = 9L).copy(fileTagsRead = true),
            ),
        )

        assertEquals(listOf("three", "two"), dao.songsNeedingFileTagRead(limit = 2).map { it.id })
    }

    // Mutation: implement markFileTagsRead as applyFileTags(EMPTY, null) → links empty and genre null → RED.
    @Test
    fun markFileTagsRead_keepsLinksAndGenre() = runTest {
        songDao.upsertSongs(listOf(song("a")))
        dao.applyFileTags("a", CustomTagIds(tagIds = setOf(4L), subTagIds = emptySet()), "Techno", 0L)
        database.openHelper.writableDatabase.execSQL("UPDATE songs SET file_tags_read = 0 WHERE id = 'a'")
        assertFalse(songRow("a").fileTagsRead)

        assertTrue(dao.markFileTagsRead("a", expectedGeneration = 0L))

        val row = songRow("a")
        assertTrue(row.fileTagsRead)
        assertEquals("Techno", row.genre)
        assertEquals(setOf(link("a", CustomTagKind.TAG, 4L)), linksOf("a"))
    }

    // A read failed on a file the scanner reset meanwhile: the new file must still be read, so the flag stays unset.
    // Mutation: drop the generation check → the flag is set → RED.
    @Test
    fun markFileTagsRead_staleGeneration_isSkipped() = runTest {
        songDao.upsertSongs(listOf(song("a")))
        resetRow("a", generation = 1L)

        val marked = dao.markFileTagsRead("a", expectedGeneration = 0L)

        assertFalse(marked)
        assertFalse(songRow("a").fileTagsRead)
    }

    /** What the scanner's merge does to a row whose file changed: unset the read flag and bump the generation. */
    private fun resetRow(songId: String, generation: Long) {
        database.openHelper.writableDatabase.execSQL(
            "UPDATE songs SET file_tags_read = 0, file_tags_generation = $generation WHERE id = '$songId'",
        )
    }
}
