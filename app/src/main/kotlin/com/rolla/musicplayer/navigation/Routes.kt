package com.rolla.musicplayer.navigation

import kotlinx.serialization.Serializable

interface Route

@Serializable
data object Library : Route

@Serializable
data object NowPlaying : Route
