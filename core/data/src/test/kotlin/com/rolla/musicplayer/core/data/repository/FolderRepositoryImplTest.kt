package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.FolderDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.FolderSummary
import com.rolla.musicplayer.core.model.Folder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderRepositoryImplTest {

    private val folderDao: FolderDao = mockk()
    private val repository = FolderRepositoryImpl(folderDao)

    // Ruling 2. The DAO hands rows in path order, which is not name order. Mutation: drop the sort →
    // [Internal storage, Alarms, Download, Abba] → RED; sort by path → the same → RED.
    @Test
    fun observeFolders_sortsByDisplayNameNotPath() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(
                FolderSummary("", 4, "art-root"),
                FolderSummary("Alarms", 1, "art-alarms"),
                FolderSummary("Download", 2, "art-download"),
                FolderSummary("Music/Abba", 3, "art-abba"),
            ),
        )
        assertEquals(
            listOf(
                Folder("Music/Abba", "Abba", 3, "art-abba"),
                Folder("Alarms", "Alarms", 1, "art-alarms"),
                Folder("Download", "Download", 2, "art-download"),
                Folder("", "Internal storage", 4, "art-root"),
            ),
            repository.observeFolders().first(),
        )
    }

    // Review Focus 3. The names tie case-insensitively ("Music", "music"), so the path decides. The DAO order puts the
    // later path first, so a stable sort without the tie-break keeps the wrong order. Mutation: drop the path
    // tie-break → [Music, Download/music] → RED.
    @Test
    fun observeFolders_breaksNameTiesByPath() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(FolderSummary("Music", 1, "a"), FolderSummary("Download/music", 1, "b")),
        )
        assertEquals(listOf("Download/music", "Music"), repository.observeFolders().first().map { it.path })
    }

    // Ruling 2's middle layer. The names tie and the paths differ only by case-sensitive order: exactly,
    // "B/music" sorts before "a/Music" ('B' < 'a'); case-insensitively it sorts after. The fixture hands the exact
    // order in. Mutation: drop the case-insensitive path layer (keep the exact one) → [B/music, a/Music] → RED.
    @Test
    fun observeFolders_breaksNameTiesByPathCaseInsensitivelyFirst() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(FolderSummary("B/music", 1, "b"), FolderSummary("a/Music", 1, "a")),
        )
        assertEquals(listOf("a/Music", "B/music"), repository.observeFolders().first().map { it.path })
    }

    // Ruling 2's last layer. ["aÚ", "Aú"] is the real DAO order: SQLite NOCASE folds ASCII only, so a = A and then
    // UTF-8 C3 9A ('Ú') < C3 BA ('ú'). String.CASE_INSENSITIVE_ORDER folds Unicode case, so the names and the
    // case-insensitive paths both tie, and only the exact layer decides ('A' < 'a'). Mutation: drop
    // .thenBy { it.path } → ["aÚ", "Aú"] → RED.
    @Test
    fun observeFolders_breaksUnicodeCaseTiesByExactPath() = runTest {
        every { folderDao.observeFolders() } returns flowOf(
            listOf(FolderSummary("aÚ", 1, "x"), FolderSummary("Aú", 1, "y")),
        )
        assertEquals(listOf("Aú", "aÚ"), repository.observeFolders().first().map { it.path })
    }

    // Mutation: pass "" (or a hardcoded path) to the DAO → the stub doesn't match and MockK throws → RED.
    @Test
    fun observeFolderSongs_queriesThatPathAndMapsToDomain() = runTest {
        every { folderDao.observeSongsInFolder("Music/Chill") } returns flowOf(listOf(songEntity("7", "Seven")))
        val songs = repository.observeFolderSongs("Music/Chill").first()
        assertEquals(listOf("7" to "Seven"), songs.map { it.id to it.title })
        verify(exactly = 1) { folderDao.observeSongsInFolder("Music/Chill") }
    }

    private fun songEntity(id: String, title: String) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "content://media/external/audio/albumart/10",
        dateModified = 1_000L,
        folderPath = "Music/Chill",
    )
}
