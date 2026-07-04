package com.rolla.musicplayer.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EqualizerPresetDao {

    /** Stable order: oldest-created first, with insertion order (`id`) as the tie-breaker. */
    @Query("SELECT * FROM equalizer_presets ORDER BY created_at ASC, id ASC")
    fun observeAllPresets(): Flow<List<EqualizerPresetEntity>>

    @Insert
    suspend fun insertPreset(preset: EqualizerPresetEntity): Long

    @Query("DELETE FROM equalizer_presets WHERE id = :presetId")
    suspend fun deletePreset(presetId: Long)
}
