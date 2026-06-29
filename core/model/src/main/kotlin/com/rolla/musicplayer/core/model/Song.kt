package com.rolla.musicplayer.core.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val contentUri: String,
    val artworkUri: String,
)
