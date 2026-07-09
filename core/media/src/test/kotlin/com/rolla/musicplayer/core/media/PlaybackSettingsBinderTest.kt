package com.rolla.musicplayer.core.media

import androidx.media3.exoplayer.ExoPlayer
import com.rolla.musicplayer.core.testing.FakeSettingsRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val PERSISTED_SPEED = 1.5f
private const val UPDATED_SPEED = 0.75f
private const val DEFAULT_SPEED = 1.0f

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSettingsBinderTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settingsRepository = FakeSettingsRepository()
    private val binder = PlaybackSettingsBinder(settingsRepository)
    private val player: ExoPlayer = mockk(relaxed = true)

    @Test
    fun `bind applies the persisted playback speed immediately`() = runTest {
        settingsRepository.setPlaybackSpeed(PERSISTED_SPEED)

        binder.bind(player, this)
        advanceUntilIdle()

        verify { player.setPlaybackSpeed(PERSISTED_SPEED) }
        binder.unbind()
    }

    @Test
    fun `bind applies the persisted skip-silence flag immediately`() = runTest {
        settingsRepository.setSkipSilence(true)

        binder.bind(player, this)
        advanceUntilIdle()

        verify { player.skipSilenceEnabled = true }
        binder.unbind()
    }

    @Test
    fun `a speed emission after bind is applied to the player`() = runTest {
        binder.bind(player, this)
        advanceUntilIdle()
        // The first (default) value is applied on bind -- confirm the fake actually starts there.
        verify { player.setPlaybackSpeed(DEFAULT_SPEED) }

        settingsRepository.setPlaybackSpeed(UPDATED_SPEED)
        advanceUntilIdle()

        verify { player.setPlaybackSpeed(UPDATED_SPEED) }
        binder.unbind()
    }

    @Test
    fun `a skip-silence emission after bind is applied to the player`() = runTest {
        binder.bind(player, this)
        advanceUntilIdle()
        verify { player.skipSilenceEnabled = false }

        settingsRepository.setSkipSilence(true)
        advanceUntilIdle()

        verify { player.skipSilenceEnabled = true }
        binder.unbind()
    }

    @Test
    fun `unbind cancels collection so a later emission is never applied`() = runTest {
        binder.bind(player, this)
        advanceUntilIdle()

        binder.unbind()
        settingsRepository.setPlaybackSpeed(UPDATED_SPEED)
        settingsRepository.setSkipSilence(true)
        advanceUntilIdle()

        verify(exactly = 0) { player.setPlaybackSpeed(UPDATED_SPEED) }
        verify(exactly = 0) { player.skipSilenceEnabled = true }
    }

    @Test
    fun `unbind is safe when never bound`() = runTest {
        binder.unbind()
    }

    @Test
    fun `applyPlaybackSpeed calls setPlaybackSpeed on the player`() = runTest {
        binder.applyPlaybackSpeed(player, UPDATED_SPEED)

        verify { player.setPlaybackSpeed(UPDATED_SPEED) }
    }

    @Test
    fun `applySkipSilence sets skipSilenceEnabled on the player`() = runTest {
        binder.applySkipSilence(player, true)

        verify { player.skipSilenceEnabled = true }
    }
}
