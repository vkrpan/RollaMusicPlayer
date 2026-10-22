package com.rolla.musicplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.FolderSummary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FolderDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var folderDao: FolderDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        folderDao = database.folderDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun song(
        id: String,
        title: String,
        folderPath: String?,
        artworkUri: String = "art-$id",
    ): SongEntity = SongEntity(
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
        artworkUri = artworkUri,
        dateModified = 1_000_000L,
        folderPath = folderPath,
    )

    // Fixture: in "Music" the alphabetically first title ("alpha") is the MIDDLE insert, and binary order would pick
    // "Mid" ('M' < 'a'). Mutations: drop the MIN() column → the bare column comes from an arbitrary (last) row "Mid" →
    // RED; MIN(title) without COLLATE NOCASE → "Mid" → RED; COUNT(*) → COUNT(DISTINCT album_id) → 1 → RED.
    @Test
    fun observeFolders_countsSongsAndTakesTheFirstTitlesArtwork() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "Zeta", "Music", artworkUri = "art-zeta"),
                song("2", "alpha", "Music", artworkUri = "art-alpha"),
                song("3", "Mid", "Music", artworkUri = "art-mid"),
            ),
        )
        assertEquals(listOf(FolderSummary("Music", 3, "art-alpha")), folderDao.observeFolders().first())
    }

    // Review Focus 1. Mutations: drop `WHERE folder_path IS NOT NULL` → a NULL-path group appears (or the mapping to a
    // non-null `path` throws) → RED; treat '' as NULL (`WHERE folder_path <> ''`) → the root group is missing → RED.
    @Test
    fun observeFolders_keepsTheRootAndSkipsUnscannedRows() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "At root", "", artworkUri = "art-root"),
                song("2", "Unscanned", null),
                song("3", "In music", "Music", artworkUri = "art-music"),
            ),
        )
        assertEquals(
            listOf(FolderSummary("", 1, "art-root"), FolderSummary("Music", 1, "art-music")),
            folderDao.observeFolders().first(),
        )
    }

    // Fixture: insertion order is neither the expected order nor binary order. Mutation: drop COLLATE NOCASE from the
    // outer ORDER BY → [Alpha, Charlie, beta] → RED.
    @Test
    fun observeFolders_ordersPathsCaseInsensitively() = runTest {
        songDao.upsertSongs(
            listOf(song("1", "c", "Charlie"), song("2", "b", "beta"), song("3", "a", "Alpha")),
        )
        assertEquals(listOf("Alpha", "beta", "Charlie"), folderDao.observeFolders().first().map { it.path })
    }

    // Plan ruling 15: folders group by the exact path. Fixture: the insertion order (music, Music, Music) is the
    // reverse of the expected order, and "Music"'s artwork comes from its first title "b" (song 2), not its first
    // insert.
    // Device mutation: `GROUP BY folder_path COLLATE NOCASE` → the two paths fold into one row → RED.
    @Test
    fun observeFolders_groupsByExactPath() = runTest {
        songDao.upsertSongs(
            listOf(song("1", "a", "music"), song("2", "b", "Music"), song("3", "c", "Music")),
        )
        assertEquals(
            listOf(FolderSummary("Music", 2, "art-2"), FolderSummary("music", 1, "art-1")),
            folderDao.observeFolders().first(),
        )
    }

    // Fixture: a sibling subfolder ("Music/Chill") and a same-prefix folder ("MusicBox") must stay out. Mutations:
    // `folder_path LIKE :path || '%'` → both leak in → RED; ORDER BY title (binary) → [Alpha, Charlie, beta] → RED.
    @Test
    fun observeSongsInFolder_returnsOnlyDirectChildrenByTitleCaseInsensitively() = runTest {
        songDao.upsertSongs(
            listOf(
                song("1", "Charlie", "Music"),
                song("2", "beta", "Music"),
                song("3", "Alpha", "Music"),
                song("4", "Chill one", "Music/Chill"),
                song("5", "Boxed", "MusicBox"),
            ),
        )
        assertEquals(
            listOf("Alpha", "beta", "Charlie"),
            folderDao.observeSongsInFolder("Music").first().map { it.title },
        )
    }

    // Review Focus 1. Mutation: `WHERE IFNULL(folder_path, '') = :path` → the unscanned song joins the root → RED.
    @Test
    fun observeSongsInFolder_rootHoldsOnlyRootSongs() = runTest {
        songDao.upsertSongs(listOf(song("1", "At root", ""), song("2", "Unscanned", null)))
        assertEquals(listOf("At root"), folderDao.observeSongsInFolder("").first().map { it.title })
    }
}
