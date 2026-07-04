package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.database.dao.EqualizerPresetDao
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [EqualizerPresetDao] backed by a [MutableStateFlow].
 *
 * Mirrors [EqualizerPresetDao.observeAllPresets]'s `created_at ASC, id ASC` ordering so tests
 * exercising ordering behavior stay consistent with the real Room-backed DAO.
 */
class FakeEqualizerPresetDao : EqualizerPresetDao {

    private val presetsFlow = MutableStateFlow<List<EqualizerPresetEntity>>(emptyList())

    private var nextId = 1L

    override fun observeAllPresets(): Flow<List<EqualizerPresetEntity>> =
        presetsFlow.asStateFlow().map { presets ->
            presets.sortedWith(compareBy({ it.createdAt }, { it.id }))
        }

    override suspend fun insertPreset(preset: EqualizerPresetEntity): Long {
        val id = if (preset.id != 0L) preset.id else nextId++
        presetsFlow.update { it + preset.copy(id = id) }
        return id
    }

    override suspend fun deletePreset(presetId: Long) {
        presetsFlow.update { current -> current.filter { it.id != presetId } }
    }
}
