package com.rolla.musicplayer.core.model

/**
 * An album: the songs MediaStore files under one album id. It carries no picture: MediaStore's per-album cover is
 * shared by unrelated untagged tracks, so albums always show the placeholder (per-track artwork spec goal 3).
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
)
