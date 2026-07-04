package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rolla.musicplayer.core.model.EqualizerPreset

@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean,

    @ColumnInfo(name = "gains_millibel")
    val gainsMillibel: List<Short>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

fun EqualizerPresetEntity.toDomain(): EqualizerPreset = EqualizerPreset(
    id = id,
    name = name,
    isCustom = isCustom,
    gainsMillibel = gainsMillibel,
)

// EqualizerPreset (the domain model) has no createdAt field, so — same as SongEntity.toEntity's
// extra mediaStoreId/dateModified params for fields the domain model doesn't carry — the caller
// supplies it explicitly rather than this function inventing a timestamp.
fun EqualizerPreset.toEntity(createdAt: Long): EqualizerPresetEntity = EqualizerPresetEntity(
    id = id,
    name = name,
    isCustom = isCustom,
    gainsMillibel = gainsMillibel,
    createdAt = createdAt,
)
