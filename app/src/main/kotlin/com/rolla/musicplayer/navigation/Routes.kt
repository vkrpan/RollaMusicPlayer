package com.rolla.musicplayer.navigation

import kotlinx.serialization.Serializable

interface Route

@Serializable
data object Library : Route

@Serializable
data object NowPlaying : Route

@Serializable
data object Equalizer : Route

@Serializable
data object Playlists : Route

@Serializable
data class PlaylistDetail(val playlistId: Long) : Route

@Serializable
data class SmartPlaylist(val kind: String) : Route

@Serializable
data class TagEditor(val songId: Long) : Route
