package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.rolla.musicplayer.core.model.CustomTagKind

/**
 * The highest ID a custom tag list has ever issued or seen in a file (v6). It only rises, so an ID freed by a delete
 * is never issued again. A kind with no row reads as 0.
 */
@Entity(tableName = "custom_tag_counters")
data class CustomTagCounterEntity(
    @PrimaryKey
    @ColumnInfo(name = "kind")
    val kind: CustomTagKind,

    @ColumnInfo(name = "highest_id")
    val highestId: Long,
)
