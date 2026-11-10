package com.rolla.musicplayer.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.rolla.musicplayer.core.model.CustomTagKind

/**
 * One custom tag a song carries (v6), as read from the file. [tagId] has no foreign key to `custom_tags`: a file may
 * carry an ID the list does not hold, and the link keeps it. Deleting the song deletes its links.
 */
@Entity(
    tableName = "song_custom_tags",
    primaryKeys = ["song_id", "kind", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["kind", "tag_id"]),
        Index(value = ["song_id"]),
    ],
)
data class SongCustomTagCrossRef(
    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "kind")
    val kind: CustomTagKind,

    @ColumnInfo(name = "tag_id")
    val tagId: Long,
)
