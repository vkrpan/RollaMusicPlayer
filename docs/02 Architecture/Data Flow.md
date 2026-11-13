---
type: hub
status: active
date: 2026-11-13
tags: [architecture, data-flow]
related: ["[[Architecture Overview]]", "[[Database Schema]]", "[[Playback and queue]]", "[[Tag editor]]", "[[Per-track artwork]]"]
---

# Data Flow

The four flows most changes touch. Class names are the code's; details live in the linked notes.

## 1. The library scan
1. The media permission is granted ([[Permissions]]).
2. `MediaScanner` reads MediaStore audio rows; `LibraryIndexer.sync` diffs them against `songs` by `date_modified` and
   upserts or deletes rows ([[core-data]], [[Song]]). A manual rescan is mutex-serialised.
3. After the upsert, `FileTagPass` reads each new or changed file in batches of 50, newest first: its `ROLLA_TAGS`
   and its genre ([[ROLLA_TAGS field]], [[core-tagio]]). It applies a read only while the row still holds the
   `file_tags_generation` it queried, so a stale read never overwrites a newer save.
4. Room `Flow`s re-emit; repositories map entities to models; ViewModels map models to UI state; the Home tabs,
   detail screens and Search update ([[Home and library]], [[Search]]).

## 2. Playing a track
1. A tap on a track plays the whole visible list from that track (`PlaybackController` through a `MediaController`).
2. `PlaybackService` (ExoPlayer + `MediaSession`) plays; `QueuePublisher` publishes the queue (`QueueState`) and
   `PlaybackStateHolder` the current song and position.
3. The mini-player, Now Playing, the queue sheet, the notification, the lock screen and the widget all read that
   state ([[Now Playing and mini-player]], [[Widget actions]]).
4. `QueuePersister` saves the queue, shuffle order and position to `playback_queue` / `playback_state`; at the next
   start `RestoreCoordinator` restores it paused ([[Playback queue restore]], [[SavedPlayback]]).

## 3. Saving tags
1. The tag editor (single or batch) asks write consent first: `MediaStore.createWriteRequest` on API 30+,
   `RecoverableSecurityException` on 29 ([[Tag editor]]).
2. Each file is copied into the cache (`createEditableCopy`), written there with jaudiotagger, then copied back
   ([[Tag writes go through a cache copy]]). Custom tags go into `ROLLA_TAGS` (MP3 and FLAC only).
3. `TagSaveFinalizer` re-indexes the saved songs (`syncSongs`, which forces a file-pass re-read), invalidates their
   cached pictures and refreshes playback metadata.

## 4. Showing artwork
1. `Song.artworkUri` is `rolla-artwork://song/{id}?v={version}`, derived when the entity is mapped ([[Song]]).
2. In the app, Coil's `SongArtworkInterceptor` asks `SongArtworkCache` (`cacheDir/song_art`), which decodes the
   file's embedded picture once (512 px) or records that it has none.
3. The session's `SongArtworkBitmapLoader` and the widget's `WidgetArtworkLoader` read the same cache; no picture
   shows the white note ([[Per-track artwork]], [[Artwork comes from the track's own file]]).

## Interfaces involved
[[Navigation routes]] · [[Widget actions]] · [[ROLLA_TAGS field]] · [[Tag list file]]
