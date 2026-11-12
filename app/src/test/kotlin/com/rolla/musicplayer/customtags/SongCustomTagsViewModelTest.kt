package com.rolla.musicplayer.customtags

import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.SongCustomTags
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** [SongCustomTagsViewModel]: the app-wide map behind the track rows' chips (custom tags phase 4, Task 3). */
class SongCustomTagsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The map follows the repository: empty first, then each emission. Stub: always the empty map → the second
    // assertion fails. Mutation: stateIn over flowOf(emptyMap()) → RED.
    @Test
    fun tags_followsRepository() = runTest {
        val source = MutableStateFlow(emptyMap<String, SongCustomTags>())
        val viewModel = SongCustomTagsViewModel(repositoryEmitting(source))
        subscribe(viewModel)
        assertEquals("the map before any tag", emptyMap<String, SongCustomTags>(), viewModel.tags.value)

        source.value = mapOf("1" to TECHNO)
        assertEquals("the map after song 1 is tagged", mapOf("1" to TECHNO), viewModel.tags.value)
    }

    // A failing repository flow degrades to the empty map: the chips vanish and nothing crashes. Stub: always the
    // repository's last map → the empty map never follows. Mutation: catch without emitting → the map stays → RED.
    @Test
    fun tags_repositoryFails_emptyMap() = runTest {
        val fail = CompletableDeferred<Unit>()
        val failing = flow {
            emit(mapOf("1" to TECHNO))
            fail.await()
            throw IOException("custom tag table unreadable")
        }
        val viewModel = SongCustomTagsViewModel(repositoryEmitting(failing))
        subscribe(viewModel)
        assertEquals("the repository's map", mapOf("1" to TECHNO), viewModel.tags.value)

        fail.complete(Unit)
        assertEquals("the fallback after the failure", emptyMap<String, SongCustomTags>(), viewModel.tags.value)
    }

    /** Keeps [viewModel]'s WhileSubscribed map collecting for the rest of the test, as the app's root does. */
    private fun TestScope.subscribe(viewModel: SongCustomTagsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.tags.collect {} }
    }

    private fun repositoryEmitting(source: Flow<Map<String, SongCustomTags>>): CustomTagRepository = mockk {
        every { observeSongCustomTags() } returns source
    }

    private companion object {
        val TECHNO = SongCustomTags(
            tags = listOf(CustomTag(kind = CustomTagKind.TAG, id = 1L, name = "Techno")),
            subTags = emptyList(),
        )
    }
}
