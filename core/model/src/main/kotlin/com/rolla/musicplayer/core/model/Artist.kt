package com.rolla.musicplayer.core.model

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    /** The cover of the artist's alphabetically first album; "" when unknown. A list key never uses it. */
    val artworkUri: String = "",
)
