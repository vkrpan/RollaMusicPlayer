package com.rolla.musicplayer.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.dao.CustomTagDao
import com.rolla.musicplayer.core.database.entity.CustomTagEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
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
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(40L), subTagIds = emptySet()), genre = null)

        val result = repository.create(CustomTagKind.TAG, "New")

        assertEquals(CustomTagNameResult.Saved(CustomTag(CustomTagKind.TAG, 41L, "New")), result)
    }

    // A file carried the ceiling ID itself: the list can issue nothing more.
    @Test
    fun create_atCeiling_isLimitReachedAndInsertsNothing() = runTest {
        fakeDao.seedSongs(listOf(song("1")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(MAX_CUSTOM_TAG_ID), subTagIds = emptySet()), null)

        assertEquals(CustomTagNameResult.LimitReached, repository.create(CustomTagKind.TAG, "New"))
        assertTrue(repository.observeCustomTags(CustomTagKind.TAG).first().isEmpty())
        assertEquals(MAX_CUSTOM_TAG_ID, fakeDao.counter(CustomTagKind.TAG))
    }

    // One below the ceiling: the ceiling itself is still issued.
    @Test
    fun create_belowCeiling_issuesTheCeiling() = runTest {
        fakeDao.seedSongs(listOf(song("1")))
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(MAX_CUSTOM_TAG_ID - 1), subTagIds = emptySet()), null)

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
        fakeDao.applyFileTags("1", CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = setOf(1L)), genre = null)

        assertEquals(
            CustomTagIds(tagIds = setOf(2L, 99L), subTagIds = setOf(1L)),
            repository.observeSongCustomTagIds("1").first(),
        )
    }

    // ── DAO ceiling guard ─────────────────────────────────────────────────────

    // The fake runs CustomTagDao's own insertNew default body, so this pins the DAO's guard for direct callers: at the
    // ceiling, and at Long.MAX_VALUE where `highest + 1` would wrap to a negative ID.
    @Test
    fun daoInsertNew_atCeiling_throwsAndInsertsNothing() = runTest {
        fakeDao.raiseCounter(CustomTagKind.TAG, MAX_CUSTOM_TAG_ID)
        fakeDao.raiseCounter(CustomTagKind.SUBTAG, Long.MAX_VALUE)

        val atCeiling = runCatching { fakeDao.insertNew(CustomTagKind.TAG, "Techno", "techno") }
        val atLongMax = runCatching { fakeDao.insertNew(CustomTagKind.SUBTAG, "Peak", "peak") }

        assertTrue("at the ceiling: $atCeiling", atCeiling.exceptionOrNull() is IllegalStateException)
        assertTrue("at Long.MAX_VALUE: $atLongMax", atLongMax.exceptionOrNull() is IllegalStateException)
        assertTrue(fakeDao.observeCustomTags(CustomTagKind.TAG).first().isEmpty())
        assertTrue(fakeDao.observeCustomTags(CustomTagKind.SUBTAG).first().isEmpty())
        assertEquals(MAX_CUSTOM_TAG_ID, fakeDao.counter(CustomTagKind.TAG))
    }

    /** A DAO whose name lookup finds nothing, as when another writer saves the same name between check and write. */
    private class NameCheckMissingDao(private val delegate: CustomTagDao) : CustomTagDao by delegate {
        override suspend fun findByNameKey(kind: CustomTagKind, nameKey: String): CustomTagEntity? = null
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
