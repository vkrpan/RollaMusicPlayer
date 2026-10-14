package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.QueuedSongRow
import com.rolla.musicplayer.core.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestoredPlaybackTest {

    private fun entity(id: String) = SongEntity(
        id = id,
        mediaStoreId = 1L,
        title = "Title $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 1_000L,
        trackNumber = null,
        year = null,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "",
        dateModified = 0L,
    )

    /** Slot i holds song "s<i>" with rank ranks[i]; slots listed in [missing] have no song. */
    private fun rows(ranks: List<Int>, missing: Set<Int> = emptySet()) = ranks.mapIndexed { slot, rank ->
        QueuedSongRow(slot, rank, "s$slot", if (slot in missing) null else entity("s$slot"))
    }

    private fun state(currentSlot: Int, positionMs: Long = 83_000L, shuffle: Boolean = true, repeat: String = "ALL") =
        PlaybackStateEntity(
            currentSlot = currentSlot,
            positionMs = positionMs,
            shuffleEnabled = shuffle,
            repeatMode = repeat,
            savedAt = 0L,
        )

    // Ranks [2,0,3,1]: rank order is slots 1,3,0,2, unlike slot order. Mutations: shuffleOrder from slot order →
    // [0,1,2,3] → RED; positionMs = 0 → RED; repeat parsed as OFF → RED.
    @Test
    fun everySongSurvives_keepsQueueShuffleOrderCurrentAndPosition() {
        val saved = restoredPlayback(rows(listOf(2, 0, 3, 1)), state(currentSlot = 2))!!

        assertEquals(listOf("s0", "s1", "s2", "s3"), saved.songs.map { it.id })
        assertEquals(listOf(1, 3, 0, 2), saved.shuffleOrder)
        assertEquals(2, saved.state.currentIndex)
        assertEquals(83_000L, saved.state.positionMs)
        assertEquals(true, saved.state.shuffleEnabled)
        assertEquals(RepeatMode.ALL, saved.state.repeatMode)
    }

    // Slots 1 and 3 missing; survivors are slots 0, 2, 4 with ranks 4, 2, 3 → new indices 0, 1, 2 → shuffle order
    // [1, 2, 0]. Mutation: build the order from the old slot numbers (`sortedBy rank, map slot`) → [2, 4, 0] → RED.
    @Test
    fun missingSongsAreDropped_survivorsRenumberedAndKeepTheirRelativeShuffleOrder() {
        val saved = restoredPlayback(rows(listOf(4, 0, 2, 1, 3), missing = setOf(1, 3)), state(currentSlot = 4))!!

        assertEquals(listOf("s0", "s2", "s4"), saved.songs.map { it.id })
        assertEquals(listOf(1, 2, 0), saved.shuffleOrder)
        assertEquals(2, saved.state.currentIndex)
        assertEquals(83_000L, saved.state.positionMs)
    }

    // Review Focus 3. Ranks [3,1,0,2]; the current slot 1 (rank 1) is missing and shuffle is on. Shuffle playback
    // order: slot 2 (r0), slot 1 (r1, missing), slot 3 (r2), slot 0 (r3) → next survivor is slot 3 → new index 2
    // (survivors 0, 2, 3). Slot order would give slot 2 → index 1. Mutations: always use slot order → 1 → RED; keep
    // the saved position → RED.
    @Test
    fun missingCurrentWithShuffleOn_movesToTheNextSurvivorInShuffleOrderAtZero() {
        val saved = restoredPlayback(rows(listOf(3, 1, 0, 2), missing = setOf(1)), state(currentSlot = 1))!!

        assertEquals(2, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Same rows, shuffle off: next survivor in slot order is slot 2 → index 1 (rank order would give index 2).
    // Mutations: always use rank order → 2 → RED; restore shuffleEnabled as a constant true → RED.
    @Test
    fun missingCurrentWithShuffleOff_movesToTheNextSlot() {
        val saved = restoredPlayback(
            rows(listOf(3, 1, 0, 2), missing = setOf(1)),
            state(currentSlot = 1, shuffle = false),
        )!!

        assertEquals(1, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
        assertEquals(false, saved.state.shuffleEnabled)
    }

    // Ranks [1,3,2,0]; the current slot 1 holds the last rank (r3) and is missing → wrap to the first survivor in
    // shuffle order. Survivors are slots 0 (r1), 2 (r2), 3 (r0) → indices 0, 1, 2; slot 3 (r0) comes first → index 2.
    // The next slot (slot 2) would give index 1. Mutations: wrap to index 0 instead of playbackOrder.first() → 0 →
    // RED; walk the slot order (next slot) → 1 → RED.
    @Test
    fun missingCurrentAtTheEnd_wrapsToTheFirstSurvivorInPlaybackOrder() {
        val saved = restoredPlayback(rows(listOf(1, 3, 2, 0), missing = setOf(1)), state(currentSlot = 1))!!

        assertEquals(2, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Ranks [1,0,2], shuffle off; the current slot 2 is the last slot and is missing → wrap to the first survivor in
    // slot order, slot 0 → index 0 (survivors 0, 1). The shuffle order's first survivor is slot 1 (r0) → index 1.
    // Mutation: `next ?: shuffleOrder.first()` (wrap in shuffle order regardless of mode) → 1 → RED.
    @Test
    fun missingCurrentAtTheEndWithShuffleOff_wrapsToTheFirstSlot() {
        val saved = restoredPlayback(
            rows(listOf(1, 0, 2), missing = setOf(2)),
            state(currentSlot = 2, shuffle = false),
        )!!

        assertEquals(0, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Ranks [0,1,1] (corrupt), shuffle on; the current slot 1 (r1) is missing. In (rank, slot) order the survivor at
    // slot 2 shares rank 1 but sorts after slot 1, so it plays next → index 1 (survivors 0, 2). Mutation: compare by
    // rank alone (`compareBy { it.shuffleRank }`) → no survivor has rank > 1 → wraps to index 0 → RED.
    @Test
    fun missingCurrentWithDuplicateRank_followsTheReturnedShuffleOrder() {
        val saved = restoredPlayback(rows(listOf(0, 1, 1), missing = setOf(1)), state(currentSlot = 1))!!

        assertEquals(1, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Final-review I1 (the deferred T2 minor): a saved current slot that names no row at all (7, with slots 0-2) falls
    // back to the first song in playback order at position 0. Ranks [2,0,1], shuffle on → shuffle order [1,2,0] →
    // index 1; slot order would give 0, and clamping the slot into range would give 2. Mutations: read the missing slot
    // as an index, clamped (`state.currentSlot.coerceIn(survivors.indices)`) → 2 → RED; fall back to index 0 → RED;
    // keep the saved position → RED.
    @Test
    fun aCurrentSlotWithNoRow_startsTheFirstSongInPlaybackOrderAtZero() {
        val saved = restoredPlayback(rows(listOf(2, 0, 1)), state(currentSlot = 7))!!

        assertEquals(listOf(1, 2, 0), saved.shuffleOrder)
        assertEquals(1, saved.state.currentIndex)
        assertEquals(0L, saved.state.positionMs)
    }

    // Mutation: return a SavedPlayback with empty songs instead of null → RED.
    @Test
    fun noSurvivors_restoreNothing() {
        assertNull(restoredPlayback(rows(listOf(0, 1), missing = setOf(0, 1)), state(currentSlot = 0)))
    }

    // Review Focus 1. Mutation: deduplicate songs by id (`distinctBy { it.song?.id }`) → one song → RED.
    @Test
    fun duplicateSongsKeepBothSlots() {
        val duplicate = listOf(
            QueuedSongRow(0, 1, "s0", entity("s0")),
            QueuedSongRow(1, 0, "s0", entity("s0")),
        )

        val saved = restoredPlayback(duplicate, state(currentSlot = 1))!!

        assertEquals(listOf("s0", "s0"), saved.songs.map { it.id })
        assertEquals(listOf(1, 0), saved.shuffleOrder)
        assertEquals(1, saved.state.currentIndex)
    }

    // Review Focus 5. Mutation: `RepeatMode.valueOf(state.repeatMode)` → IllegalArgumentException → RED.
    @Test
    fun unknownRepeatModeFallsBackToOff() {
        val saved = restoredPlayback(rows(listOf(0, 1)), state(currentSlot = 0, repeat = "SHUFFLE"))!!

        assertEquals(RepeatMode.OFF, saved.state.repeatMode)
    }

    // Review Focus 5: ranks [1,1,0] (corrupt). The order is still a permutation, with ties broken by slot.
    // Mutation: build the order with `distinctBy { rank }` → a 2-element order → RED.
    @Test
    fun duplicateRanksStillGiveAPermutation() {
        val saved = restoredPlayback(rows(listOf(1, 1, 0)), state(currentSlot = 0))!!

        assertEquals(listOf(2, 0, 1), saved.shuffleOrder)
    }
}
