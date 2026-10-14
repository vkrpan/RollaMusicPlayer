package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState

/**
 * Turns saved rows ([rows] in slot order, missing songs included as `song == null`) into a [SavedPlayback]
 * (queue-restore spec §7.4, ruling R2), or null when no song survived.
 *
 * Survivors are renumbered `0 until n` in slot order. The shuffle order sorts them by their saved rank (a stable sort,
 * so duplicate ranks fall back to slot order). When the current song itself is gone, playback moves to the next
 * survivor after it in playback order ((rank, slot) order with shuffle on, slot order with it off), wrapping to the
 * first, at position 0. A current slot that names no row starts the first survivor in playback order, at position 0.
 */
internal fun restoredPlayback(rows: List<QueuedSongRow>, state: PlaybackStateEntity): SavedPlayback? {
    val survivors = rows.filter { it.song != null }
    if (survivors.isEmpty()) return null
    val songs = survivors.mapNotNull { it.song?.toDomain() }
    val shuffleOrder = survivors.indices.sortedBy { survivors[it].shuffleRank }

    val savedIndex = survivors.indexOfFirst { it.slot == state.currentSlot }
    val currentSurvived = savedIndex >= 0
    val currentIndex = if (currentSurvived) savedIndex else nextSurvivorIndex(rows, survivors, shuffleOrder, state)
    val positionMs = if (currentSurvived) state.positionMs.coerceAtLeast(0L) else 0L

    return SavedPlayback(
        songs = songs,
        shuffleOrder = shuffleOrder,
        state = SavedPlaybackState(
            currentIndex = currentIndex,
            positionMs = positionMs,
            shuffleEnabled = state.shuffleEnabled,
            repeatMode = repeatModeOf(state.repeatMode),
        ),
    )
}

/**
 * The survivor index that plays after the missing current slot in playback order, wrapping to the first survivor in
 * that order. With shuffle on, the order is (rank, slot), the same tie-break as the stable sort behind `shuffleOrder`,
 * so even corrupt duplicate ranks walk exactly the returned order. With shuffle off, the order is the slot order.
 */
private fun nextSurvivorIndex(
    rows: List<QueuedSongRow>,
    survivors: List<QueuedSongRow>,
    shuffleOrder: List<Int>,
    state: PlaybackStateEntity,
): Int {
    val playbackOrder = if (state.shuffleEnabled) shuffleOrder else survivors.indices.toList()
    val order: Comparator<QueuedSongRow> = if (state.shuffleEnabled) {
        compareBy<QueuedSongRow> { it.shuffleRank }.thenBy { it.slot }
    } else {
        compareBy { it.slot }
    }
    val missingCurrent = rows.firstOrNull { it.slot == state.currentSlot }
    val next = missingCurrent?.let { gone ->
        playbackOrder.firstOrNull { order.compare(survivors[it], gone) > 0 }
    }
    return next ?: playbackOrder.first()
}

/** An unknown or corrupt name restores as [RepeatMode.OFF] (Review Focus 5). */
internal fun repeatModeOf(name: String): RepeatMode =
    RepeatMode.entries.firstOrNull { it.name == name } ?: RepeatMode.OFF
