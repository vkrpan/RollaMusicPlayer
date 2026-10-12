package com.rolla.musicplayer.core.ui

/** The "N tracks" count shown on playlist rows and feature cards: "1 track", otherwise "N tracks" (UI copy). */
fun tracksCountLabel(count: Int): String = if (count == 1) "1 track" else "$count tracks"

/** The "N albums" count on artist rows: "1 album", otherwise "N albums" (UI copy). */
fun albumsCountLabel(count: Int): String = if (count == 1) "1 album" else "$count albums"
