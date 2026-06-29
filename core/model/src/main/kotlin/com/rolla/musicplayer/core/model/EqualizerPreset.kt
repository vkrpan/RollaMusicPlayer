package com.rolla.musicplayer.core.model

data class EqualizerPreset(
    val id: Long,
    val name: String,
    val isCustom: Boolean,
    val gainsMillibel: List<Short>,
)
