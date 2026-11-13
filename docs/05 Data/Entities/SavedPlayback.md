---
type: entity
status: active
date: 2026-11-13
table: "playback_queue, playback_state"
model: "core/model (SavedPlayback, SavedPlaybackState)"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/PlaybackQueueEntity.kt"
tags: [data, entity, playback]
related: ["[[Database Schema]]", "[[Playback queue restore]]", "[[Playback and queue]]", "[[core-media]]"]
---

# SavedPlayback

The queue, its shuffle order, the current position and the modes, saved so the next start restores them paused
(Room v5, [[Playback queue restore]]).

## Fields
| Column | Type | Null | Meaning |
|---|---|---|---|
| `playback_queue.slot` | INTEGER | no | the Media3 window index; the same song can sit in two slots |
| `playback_queue.song_id` | TEXT | no | **no FK** to `songs`: a deleted song restores as "missing" |
| `playback_queue.shuffle_rank` | INTEGER | no | the slot's place in the shuffled order |
| `playback_state.id` | INTEGER | no | always 0: a single row |
| `playback_state.current_slot` | INTEGER | no | |
| `playback_state.position_ms` | INTEGER | no | |
| `playback_state.shuffle_enabled` | INTEGER | no | |
| `playback_state.repeat_mode` | TEXT | no | a `RepeatMode` name: `OFF`, `ONE`, `ALL` |
| `playback_state.saved_at` | INTEGER | no | |

Model: `SavedPlayback(songs, shuffleOrder, state)` with `SavedPlaybackState(currentIndex, positionMs, shuffleEnabled,
repeatMode)`.

## Invariants
- `shuffleOrder` is a permutation of the queue's indices.
- A restore skips missing songs to the next survivor in playback order.

## Read and written by
`QueuePersister` (save) and `RestoreCoordinator` (restore) in [[core-media]], through `PlaybackStateRepository`.
