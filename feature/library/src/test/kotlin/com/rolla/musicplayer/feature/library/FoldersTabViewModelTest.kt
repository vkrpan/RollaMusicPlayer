package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.testing.FakeFolderRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mutation: seed stateIn with emptyList() → [] before any subscriber → RED (Phase 4a ruling 6).
    @Test
    fun `folders are null before the first emission`() {
        assertNull(FoldersTabViewModel(FakeFolderRepository()).folders.value)
    }

    // Two folders share a name: both must come through, in the repository's order. The fixture is NOT in path order
    // ("Music" before "Download/Music"), so a ViewModel that re-sorts is caught.
    // Mutations: upstream flowOf(emptyList()) → RED; `.map { list -> list?.sortedBy(Folder::path) }` upstream of
    // stateIn → [Download/Music, Music] → RED.
    @Test
    fun `folders mirror the repository while collected`() = runTest {
        val repository = FakeFolderRepository()
        val viewModel = FoldersTabViewModel(repository)
        val job = launch { viewModel.folders.collect {} }
        val folders = listOf(Folder("Music", "Music", 4, "b"), Folder("Download/Music", "Music", 1, "a"))

        repository.emitFolders(folders)
        advanceUntilIdle()

        assertEquals(folders, viewModel.folders.value)
        job.cancel()
    }
}
