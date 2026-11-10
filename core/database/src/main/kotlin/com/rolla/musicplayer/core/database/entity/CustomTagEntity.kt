package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import com.rolla.musicplayer.core.model.CustomTagKind

/**
 * One entry of a custom tag list (v6). [kind] is stored as the [CustomTagKind] name, and each kind issues its own
 * [id]s from `custom_tag_counters`, so the key is the pair. [nameKey] is the normalised name the duplicate check
 * compares; the unique index on (kind, name_key) rejects a second entry with the same key in one list.
 */
@Entity(
    tableName = "custom_tags",
    primaryKeys = ["kind", "id"],
    indices = [Index(value = ["kind", "name_key"], unique = true)],
)
data class CustomTagEntity(
    @ColumnInfo(name = "kind")
    val kind: CustomTagKind,

    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_key")
    val nameKey: String,
)
