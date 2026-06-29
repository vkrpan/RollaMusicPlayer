package com.rolla.musicplayer.core.model

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String,
)
