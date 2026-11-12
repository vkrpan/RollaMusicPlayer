package com.rolla.musicplayer.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.CustomTagEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CUSTOM_TAG_NAME_MAX_LENGTH
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.CustomTagListFile
import com.rolla.musicplayer.core.model.MAX_CUSTOM_TAG_ID
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [CustomTagRepositoryImpl] against [FakeCustomTagDao], which issues IDs from per-kind counters like the Room DAO. The
 * fake's unique-index and foreign-key throws construct `SQLiteConstraintException`, which needs Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class CustomTagRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeDao = FakeCustomTagDao()
    private val repository = repositoryOn(fakeDao)

    private fun repositoryOn(dao: CustomTagDao) =
        CustomTagRepositoryImpl(dao, mainDispatcherRule.testDispatcher, mainDispatcherRule.testDispatcher)

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    fun create_trimsAndIssuesIds() = runTest {
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 1L, "Techno")),
            repository.create(CustomTagKind.TAG, "  Techno "),
        )
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 2L, "House")),
            repository.create(CustomTagKind.TAG, "House"),
        )
    }

    @Test
    fun create_blank_isRejected() = runTest {
        assertEquals(CustomTagNameResult.Blank, repository.create(CustomTagKind.TAG, "   "))
        assertTrue(fakeDao.observeCustomTags(CustomTagKind.TAG).first().isEmpty())
        assertEquals(0L, fakeDao.counter(CustomTagKind.TAG))
    }

    @Test
    fun create_duplicateIgnoringCase_isRejected() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")

        assertEquals(CustomTagNameResult.Duplicate, repository.create(CustomTagKind.TAG, " techno"))
    }

    @Test
    fun create_sameNameInOtherList_isAllowed() = runTest {
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 1L, "Hard")),
            repository.create(CustomTagKind.TAG, "Hard"),
        )
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.SUBTAG, 1L, "Hard")),
            repository.create(CustomTagKind.SUBTAG, "Hard"),
        )
    }

    // 40 emoji are 40 code points but 80 UTF-16 units: the limit counts code points.
    @Test
    fun create_fortyEmoji_isSavedAndFortyOne_isTooLong() = runTest {
        val forty = "😀".repeat(CUSTOM_TAG_NAME_MAX_LENGTH)

        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 1L, forty)),
            repository.create(CustomTagKind.TAG, forty),
        )
        assertEquals(CustomTagNameResult.TooLong, repository.create(CustomTagKind.TAG, forty + "😀"))
        assertEquals(listOf(forty), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
        assertEquals("the rejected name issued no ID", 1L, fakeDao.counter(CustomTagKind.TAG))
    }

    @Test
    fun create_afterFileCarriedHigherId_issuesAboveIt() = runTest {
        fakeDao.seedSongs(listOf(song("1")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(40L), subTagIds = emptySet()), null, 0L)

        val result = repository.create(CustomTagKind.TAG, "New")

        assertEquals(CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 41L, "New")), result)
    }

    // A file carried the ceiling ID itself: the list can issue nothing more.
    @Test
    fun create_atCeiling_isLimitReachedAndInsertsNothing() = runTest {
        fakeDao.seedSongs(listOf(song("1")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(MAX_CUSTOM_TAG_ID), subTagIds = emptySet()), null, 0L)

        assertEquals(CustomTagNameResult.LimitReached, repository.create(CustomTagKind.TAG, "New"))
        assertTrue(repository.observeCustomTags(CustomTagKind.TAG).first().isEmpty())
        assertEquals(MAX_CUSTOM_TAG_ID, fakeDao.counter(CustomTagKind.TAG))
    }

    // One below the ceiling: the ceiling itself is still issued.
    @Test
    fun create_belowCeiling_issuesTheCeiling() = runTest {
        fakeDao.seedSongs(listOf(song("1")))
        val ids = CustomTagIds(tagIds = setOf(MAX_CUSTOM_TAG_ID - 1), subTagIds = emptySet())
        fakeDao.applyFileTags("1", ids, null, 0L)

        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, MAX_CUSTOM_TAG_ID, "New")),
            repository.create(CustomTagKind.TAG, "New"),
        )
    }

    // The repository's own check misses a name another writer saved in between; the unique index rejects it instead.
    @Test
    fun create_lostRaceToUniqueIndex_isDuplicate() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")
        val racing = repositoryOn(NameCheckMissingDao(fakeDao))

        assertEquals(CustomTagNameResult.Duplicate, racing.create(CustomTagKind.TAG, "techno"))
    }

    // ── rename ────────────────────────────────────────────────────────────────

    @Test
    fun rename_changesCaseOfItself() = runTest {
        repository.create(CustomTagKind.TAG, "techno")

        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 1L, "Techno")),
            repository.rename(CustomTagKind.TAG, 1L, "Techno"),
        )
        assertEquals(listOf("Techno"), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
    }

    @Test
    fun rename_toAnotherEntrysName_isDuplicate() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")
        repository.create(CustomTagKind.TAG, "House")

        assertEquals(CustomTagNameResult.Duplicate, repository.rename(CustomTagKind.TAG, 2L, "TECHNO"))
        assertEquals(
            listOf("House", "Techno"),
            repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name },
        )
    }

    @Test
    fun rename_missing_isNotFound() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")

        assertEquals(CustomTagNameResult.NotFound, repository.rename(CustomTagKind.TAG, 7L, "House"))
    }

    @Test
    fun rename_blank_isBlank() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")

        assertEquals(CustomTagNameResult.Blank, repository.rename(CustomTagKind.TAG, 1L, "  "))
        assertEquals(listOf("Techno"), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
    }

    @Test
    fun rename_tooLong_isTooLong() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")

        assertEquals(
            CustomTagNameResult.TooLong,
            repository.rename(CustomTagKind.TAG, 1L, "a".repeat(CUSTOM_TAG_NAME_MAX_LENGTH + 1)),
        )
        assertEquals(listOf("Techno"), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
    }

    @Test
    fun rename_lostRaceToUniqueIndex_isDuplicate() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")
        repository.create(CustomTagKind.TAG, "House")
        val racing = repositoryOn(NameCheckMissingDao(fakeDao))

        assertEquals(CustomTagNameResult.Duplicate, racing.rename(CustomTagKind.TAG, 2L, "techno"))
    }

    // ── observe ───────────────────────────────────────────────────────────────

    // Created in neither the expected nor the binary order.
    @Test
    fun observeCustomTags_libraryOrder() = runTest {
        listOf("Zebra", "Émile", "apple").forEach { repository.create(CustomTagKind.TAG, it) }

        assertEquals(
            listOf("apple", "Émile", "Zebra"),
            repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name },
        )
    }

    // "Émile" and "Emile" are one library name (the collator ignores accents) but two entries (their name keys
    // differ); as with the playlists, the tie-break is the name, then the ID, so the creation order does not decide.
    @Test
    fun observeCustomTags_libraryTie_breaksByNameThenId() = runTest {
        repository.create(CustomTagKind.TAG, "Émile")
        repository.create(CustomTagKind.TAG, "Emile")

        assertEquals(
            listOf("Emile", "Émile"),
            repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name },
        )
    }

    @Test
    fun observeSongCustomTagIds_splitsByKindAndKeepsUnknownIds() = runTest {
        repository.create(CustomTagKind.TAG, "One")
        repository.create(CustomTagKind.TAG, "Two")
        repository.create(CustomTagKind.SUBTAG, "Sub")
        fakeDao.seedSongs(listOf(song("1")))
        // TAG 99 has no entry in the list: the link keeps it.
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = setOf(1L)), null, 0L)

        assertEquals(
            CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = setOf(1L)),
            repository.observeSongCustomTagIds("1").first(),
        )
    }

    // Another writer took the last ID after this create's name check: insertNew's own transaction finds the list full
    // and returns null, which reads as LimitReached rather than a crash (closes phase 1's last-ID race).
    // Mutation: ignore null from insertNew (`!!`) → Failure(NullPointerException) ≠ Success(LimitReached) → RED.
    @Test
    fun create_atCeiling_isLimitReachedWithoutThrowing() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")
        val racing = repositoryOn(LastIdTakenDao(fakeDao))

        val result = runCatching { racing.create(CustomTagKind.TAG, "House") }

        assertEquals(Result.success(CustomTagNameResult.LimitReached), result)
        assertEquals(listOf("Techno"), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
        assertEquals(MAX_CUSTOM_TAG_ID, fakeDao.counter(CustomTagKind.TAG))
    }

    // ── delete ────────────────────────────────────────────────────────────────

    // Mutation: the repository skips the DAO delete → the entry remains → RED.
    @Test
    fun delete_removesEntryAndLinks() = runTest {
        repository.create(CustomTagKind.TAG, "Techno")
        repository.create(CustomTagKind.TAG, "House")
        fakeDao.seedSongs(listOf(song("1"), song("2")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(1L, 2L), subTagIds = emptySet()), null, 0L)
        fakeDao.applyFileTags("2", CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), null, 0L)
        assertEquals(listOf("1", "2"), repository.songIdsCarrying(CustomTagKind.TAG, 2L))

        repository.delete(CustomTagKind.TAG, 2L)

        assertEquals(listOf("Techno"), repository.observeCustomTags(CustomTagKind.TAG).first().map { it.name })
        assertEquals(emptyList<String>(), repository.songIdsCarrying(CustomTagKind.TAG, 2L))
        assertEquals(mapOf(1L to 1), repository.observeLinkCounts(CustomTagKind.TAG).first())
        assertEquals(2L, fakeDao.counter(CustomTagKind.TAG))
    }

    // ── import ────────────────────────────────────────────────────────────────

    // Here: TAG 2 "Techno" on 14 songs, TAG 5 "House" on 3, TAG 6 "Acid" on none. The file renames 2 and 6 and lacks 5.
    // Mutation: skip IDs absent from the file → the House clash is missing → RED.
    @Test
    fun previewImport_reportsRenamedAndRemovedCarriedIds() = runTest {
        seedTechnoHouseAcid()
        val file = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "Jazz"), CustomTag(CustomTagKind.TAG, 6L, "Acid2")),
            subTags = emptyList(),
        )

        assertEquals(
            listOf(
                ImportClash(CustomTagKind.TAG, 2L, currentName = "Techno", importedName = "Jazz", songCount = 14),
                ImportClash(CustomTagKind.TAG, 5L, currentName = "House", importedName = null, songCount = 3),
            ),
            repository.previewImport(file),
        )
    }

    // Mutation: report every carried ID → clashes for 2 and 5 → RED.
    @Test
    fun previewImport_sameNames_noClashes() = runTest {
        seedTechnoHouseAcid()
        val file = CustomTagListFile(
            tags = listOf(
                CustomTag(CustomTagKind.TAG, 2L, "Techno"),
                CustomTag(CustomTagKind.TAG, 5L, "House"),
                CustomTag(CustomTagKind.TAG, 9L, "New"),
            ),
            subTags = emptyList(),
        )

        assertEquals(emptyList<ImportClash>(), repository.previewImport(file))
    }

    // Before: TAG "Old" 1, SUBTAG counter 30 from a file. The import's highest TAG is 40 and SUBTAG is 2.
    // Mutation: insert without the counter raise → create(TAG) issues 2 ≠ 41 → RED.
    @Test
    fun applyImport_replacesListsAndRaisesCounters() = runTest {
        repository.create(CustomTagKind.TAG, "Old")
        fakeDao.raiseCounter(CustomTagKind.SUBTAG, 30L)
        val file = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 3L, "House"), CustomTag(CustomTagKind.TAG, 40L, "Techno")),
            subTags = listOf(CustomTag(CustomTagKind.SUBTAG, 2L, "Hard")),
        )

        repository.applyImport(file)

        assertEquals(file, repository.exportLists())
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 41L, "Acid")),
            repository.create(CustomTagKind.TAG, "Acid"),
        )
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.SUBTAG, 31L, "Soft")),
            repository.create(CustomTagKind.SUBTAG, "Soft"),
        )
    }

    // TAG 2 and SUBTAG 1 are both carried and both renamed. Mutation: subtags before tags → [SUBTAG, TAG] → RED.
    @Test
    fun previewImport_tagAndSubtagClashes_tagsFirst() = runTest {
        fakeDao.insertTag(CustomTagEntity(CustomTagKind.TAG, 2L, "Techno", "techno"))
        fakeDao.insertTag(CustomTagEntity(CustomTagKind.SUBTAG, 1L, "Hard", "hard"))
        fakeDao.seedSongs(listOf(song("1")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(1L)), null, 0L)
        val file = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "Jazz")),
            subTags = listOf(CustomTag(CustomTagKind.SUBTAG, 1L, "Soft")),
        )

        assertEquals(
            listOf(
                ImportClash(CustomTagKind.TAG, 2L, currentName = "Techno", importedName = "Jazz", songCount = 1),
                ImportClash(CustomTagKind.SUBTAG, 1L, currentName = "Hard", importedName = "Soft", songCount = 1),
            ),
            repository.previewImport(file),
        )
    }

    // A file built in code is checked with the repository's own name rules before the DAO is touched: a blank name,
    // and two TAG names equal ignoring case and spaces, each throw and leave the lists as they were.
    // Mutation: drop the validation → the blank-name file replaces the lists, no throw → RED.
    @Test
    fun applyImport_invalidFile_throwsAndChangesNothing() = runTest {
        repository.create(CustomTagKind.TAG, "Old")
        val before = repository.exportLists()
        val blankName = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "  ")),
            subTags = emptyList(),
        )
        val duplicateKey = CustomTagListFile(
            tags = listOf(CustomTag(CustomTagKind.TAG, 2L, "Techno"), CustomTag(CustomTagKind.TAG, 3L, " techno")),
            subTags = emptyList(),
        )

        val blankResult = runCatching { repository.applyImport(blankName) }
        val duplicateResult = runCatching { repository.applyImport(duplicateKey) }

        assertTrue("blank: $blankResult", blankResult.exceptionOrNull() is IllegalArgumentException)
        assertTrue("duplicate: $duplicateResult", duplicateResult.exceptionOrNull() is IllegalArgumentException)
        assertEquals(before, repository.exportLists())
    }

    // ── reserve ───────────────────────────────────────────────────────────────

    // A file the editor opened carries TAG 40 before the scan has read it. Mutation: no-op reserveIds → 1 ≠ 41 → RED.
    @Test
    fun reserveIds_raisesCountersSoCreateIssuesAbove() = runTest {
        repository.reserveIds(CustomTagIds(tagIds = setOf(3L, 40L), subTagIds = emptySet()))

        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 41L, "Techno")),
            repository.create(CustomTagKind.TAG, "Techno"),
        )
        assertEquals(
            CustomTagNameResult.Saved(CustomTag(CustomTagKind.SUBTAG, 1L, "Hard")),
            repository.create(CustomTagKind.SUBTAG, "Hard"),
        )
    }

    // ── DAO ceiling guard ─────────────────────────────────────────────────────

    // The fake runs CustomTagDao's own insertNew default body, so this pins the DAO's guard for direct callers: at the
    // ceiling, and at Long.MAX_VALUE where `highest + 1` would wrap to a negative ID.
    @Test
    fun daoInsertNew_atCeiling_returnsNullAndInsertsNothing() = runTest {
        fakeDao.raiseCounter(CustomTagKind.TAG, MAX_CUSTOM_TAG_ID)
        fakeDao.raiseCounter(CustomTagKind.SUBTAG, Long.MAX_VALUE)

        val atCeiling = runCatching { fakeDao.insertNew(CustomTagKind.TAG, "Techno", "techno") }
        val atLongMax = runCatching { fakeDao.insertNew(CustomTagKind.SUBTAG, "Peak", "peak") }

        assertEquals("at the ceiling", Result.success(null), atCeiling)
        assertEquals("at Long.MAX_VALUE", Result.success(null), atLongMax)
        assertTrue(fakeDao.observeCustomTags(CustomTagKind.TAG).first().isEmpty())
        assertTrue(fakeDao.observeCustomTags(CustomTagKind.SUBTAG).first().isEmpty())
        assertEquals(MAX_CUSTOM_TAG_ID, fakeDao.counter(CustomTagKind.TAG))
    }

    /** TAG 2 "Techno" on songs 1–14, TAG 5 "House" on songs 15–17, TAG 6 "Acid" on none. */
    private suspend fun seedTechnoHouseAcid() {
        listOf(2L to "Techno", 5L to "House", 6L to "Acid").forEach { (id, name) ->
            fakeDao.insertTag(CustomTagEntity(CustomTagKind.TAG, id, name, name.lowercase()))
        }
        fakeDao.raiseCounter(CustomTagKind.TAG, 6L)
        val songIds = (1..17).map { it.toString() }
        fakeDao.seedSongs(songIds.map { song(it) })
        songIds.forEachIndexed { index, songId ->
            val tagId = if (index < 14) 2L else 5L
            fakeDao.applyFileTags(songId, CustomTagIds(tagIds = setOf(tagId), subTagIds = emptySet()), null, 0L)
        }
    }

    /** A DAO whose name lookup finds nothing, as when another writer saves the same name between check and write. */
    private class NameCheckMissingDao(private val delegate: CustomTagDao) : CustomTagDao by delegate {
        override suspend fun findByNameKey(kind: CustomTagKind, nameKey: String): CustomTagEntity? = null
    }

    /**
     * A DAO where another writer takes the kind's last ID just before each [insertNew]: it raises the counter to
     * [MAX_CUSTOM_TAG_ID], then runs the real default body, whose own check must find the list full.
     */
    private class LastIdTakenDao(private val delegate: CustomTagDao) : CustomTagDao by delegate {
        override suspend fun insertNew(kind: CustomTagKind, name: String, nameKey: String): Long? {
            delegate.raiseCounter(kind, MAX_CUSTOM_TAG_ID)
            return delegate.insertNew(kind, name, nameKey)
        }
    }

    private fun song(id: String): SongEntity = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 200_000L,
        trackNumber = 1,
        year = 2020,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "rolla-artwork://song/$id?v=1000",
        dateModified = 1_000L,
        folderPath = "Music",
    )
}
