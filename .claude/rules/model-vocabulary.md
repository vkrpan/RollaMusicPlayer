# Model Vocabulary (Shared Domain Language)

This is the single source of truth for domain model names in RollaMusicPlayer. All agents, skills, and code must use these names. It lives in `:core:model` as the shared contract; every other module imports from there.

## The one rule that matters

**The audio item model is `Song`. Never `Track`.**

Earlier drafts used `Track` in a few places (notably the navigation examples and some audio-engineer code samples). `Track` as a *model name* is deprecated and must be renamed to `Song`. This doc also lists the `Track`-spelled terms that are **not** the model and must be left alone — a blind find/replace would break the build.

## Canonical Models (`:core:model`)

```kotlin
// The audio item. THE canonical name — not Track, not Media, not AudioFile.
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,        // ← a FIELD named trackNumber. Stays. Not "songNumber".
    val year: Int?,
    val contentUri: String,       // stable content:// uri for playback
    val artworkUri: String        // local album-art uri
)

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val songCount: Int,
    val artworkUri: String
)

data class Artist(
    val id: Long,
    val name: String,
    val albumCount: Int,
    val songCount: Int
)

data class Playlist(
    val id: Long,
    val name: String,
    val songCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class EqualizerPreset(
    val id: Long,
    val name: String,
    val isCustom: Boolean,
    val gainsMillibel: List<Short>
)

enum class RepeatMode { OFF, ONE, ALL }
enum class ShuffleMode { OFF, ON }
```

Naming conventions:
- A single audio item is always a **`Song`** (variable: `song`, list: `songs`, id: `songId`).
- Detail route is **`SongDetail(val songId: Long)`** → screen `SongDetailScreen`, callback `onSongClick`.
- Entities map to these models via `toDomain()` / `toEntity()`; entities may be named `SongEntity`, etc.

## Rename map (model `Track` → `Song`)

| Old (Track-as-model) | New (Song) |
| --- | --- |
| `Track` (the data class / type) | `Song` |
| `List<Track>` | `List<Song>` |
| `track: Track`, `val track: Track` | `song: Song` |
| `tracks` (collection of the model) | `songs` |
| `TrackDetail` (route) | `SongDetail` |
| `trackId` (route arg / id of the model) | `songId` |
| `TrackDetailScreen` | `SongDetailScreen` |
| `onTrackClick` | `onSongClick` |
| `createTestTracks(...)` (test factory) | `createTestSongs(...)` |
| `TrackTags` (tag-editor model) | `SongTags` |
| deep link `rollamusic://track` | `rollamusic://song` |

## DO NOT rename (these are NOT the model)

These contain the letters "track" but are framework APIs, field names, or feature wording. Renaming them breaks the build or the meaning:

| Term | Why it stays |
| --- | --- |
| `trackNumber` / `track_number` | A field of `Song` (the disc track number). Not the model. |
| `AudioTrack`, `preInitializeAudioTrack` | `android.media.AudioTrack` — framework class. |
| `C.TRACK_TYPE_AUDIO`, `TRACK_TYPE_*` | Media3 constants. |
| `player.currentTracks`, `Tracks` | Media3 `Tracks` API. |
| `getTrackFormat(...)` | Media3 track-format API. |
| `MediaStore.Audio.Media.TRACK` | MediaStore column (encodes disc*1000 + track). |
| "track number", "previous/next track", "current track" (prose) | UI/feature wording, not a type. |

Rule of thumb: rename `Track` only when it refers to *the app's audio-item model*. If it's an Android/Media3 symbol or the `trackNumber` field, leave it.

## Per-file change list (files containing model `Track`)

All under `.claude/` (write-protected this session — apply by editing the files directly or request a regenerated copy).

**`.claude/agents/navigation-agent.md`** — pervasive route examples:
- `TrackDetail` → `SongDetail` (all occurrences)
- `trackId` → `songId` (all occurrences)
- `TrackDetailScreen` → `SongDetailScreen`
- `onTrackClick` → `onSongClick`
- `track: Track` → `song: Song` (the "don't pass full object" example)
- deep link `rollamusic://track` → `rollamusic://song`
- test fn `navigateToTrackDetail_updatesCurrentDestination` → `navigateToSongDetail_updatesCurrentDestination`

**`.claude/rules/navigation-conventions.md`** — same route example:
- `TrackDetail(val trackId: Long)` → `SongDetail(val songId: Long)` and all dependent lines (`composable<SongDetail>`, `toRoute<SongDetail>()`, `SongDetailScreen(songId = args.songId)`, `navigate(SongDetail(songId = 42L))`)

**`.claude/agents/audio-engineer.md`** — code samples only:
- ~line 715 `setupGaplessPlaylist(tracks: List<Track>)` → `setupGaplessPlaylist(songs: List<Song>)` (and `track.uri`/`track.id` inside → `song.uri`/`song.id`)
- ~line 899 `normalizeAudioLevels(tracks: List<Track>): List<Track>` → `...(songs: List<Song>): List<Song>`
- ~lines 1002, 1033 `createTestTracks(count = ...)` → `createTestSongs(count = ...)`
- LEAVE: `preInitializeAudioTrack`, `AudioTrack.Builder`, `C.TRACK_TYPE_AUDIO`, `player.currentTracks`, `getTrackFormat`, `playTrack(uri)` (method name, optional)

**`.claude/skills/implement-tag-editor/SKILL.md`** — optional consistency rename:
- `data class TrackTags(...)` → `data class SongTags(...)` (and references)

**No change needed** (their "Track" is field/framework/prose, already verified): `CLAUDE.md`, `setup-modularization`, `implement-media-scanning`, `compose-performance-auditor`, `ui-builder`, `release-build`, `implement-use-cases`, `add-unit-testing`.

## Why this matters
With ~16 agents writing code, an inconsistent model name (`Track` in nav, `Song` everywhere else) compounds into real bugs and merge friction. `:core:model` is the shared vocabulary; this doc is its rulebook. When in doubt about a new model name, add it here first.
