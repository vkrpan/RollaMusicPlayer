package com.rolla.musicplayer.core.model

/**
 * An artist: the songs whose artist tag is exactly [name], which is the artist's identity. It carries no picture:
 * artists always show the placeholder (per-track artwork spec goal 3).
 */
data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int,
)
