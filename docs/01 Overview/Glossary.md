---
type: hub
status: active
date: 2026-11-13
tags: [overview, glossary, vocabulary]
related: ["[[00 Home]]", "[[Model vocabulary]]", "[[Song in code, Tracks on screen]]"]
---

# Glossary

Model names follow [[Model vocabulary]] (`.claude/rules/model-vocabulary.md`, the source of truth).

## Library
- **Song**: one audio file in the library. Code always says `Song`, never `Track`. See [[Song]].
- **Track**: the word the UI uses for a song ("Tracks" tab, "12 tracks"). Copy only:
  [[Song in code, Tracks on screen]].
- **Album**: songs grouped by MediaStore `album_id`. Untagged tracks can share one: [[Untagged tracks share a MediaStore album]].
  See [[Album]].
- **Artist**: songs grouped by artist name; the name is the identity ([[Artists are identified by name]]). A missing
  artist shows as "Unknown" ([[Unknown artists show as Unknown]]). See [[Artist]].
- **Folder**: a folder that directly holds songs, identified by its path relative to its volume (`""` is the root).
  See [[Folder]].
- **Library order**: the one sort used by every list and the A–Z rail ([[One library order]]).
- **Scan / sync**: `LibraryIndexer` copying MediaStore rows into Room; the **file pass** (`FileTagPass`) then reads
  each new or changed file's `ROLLA_TAGS` and genre ([[Data Flow]]).

## Playback
- **Queue**: the songs Media3 plays, in slot order; the **shuffle order** is a permutation of it.
- **Saved playback**: the queue, shuffle order, position and modes saved in Room and restored paused at the next start
  ([[Playback queue restore]], [[SavedPlayback]]).
- **Smart playlist**: a computed playlist: Recently added, Most played, Recently played, Favourites ([[Playlists]]).
- **Favourites**: songs with `is_favorite`; the heart on Now Playing.
- **Resume card**: the System UI media card that survives process death; stock Android only
  ([[Panel resume targets stock Android]]).
- **Preset**: a saved set of equaliser gains in millibels ([[EqualizerPreset]]).

## Tags and artwork
- **Tags (ID3 fields)**: title, artist, album, genre, year and so on, edited in the [[Tag editor]].
- **Custom tag / subtag**: a user-defined label, in two independent lists that each issue their own IDs. Not an ID3
  field. See [[CustomTag]], [[Custom tags]].
- **`ROLLA_TAGS`**: the file field holding a song's custom tag IDs: [[ROLLA_TAGS field]].
- **Tag list file**: the exported JSON that maps IDs to names: [[Tag list file]].
- **Artwork value**: `rolla-artwork://song/{id}?v={version}`, the per-track picture key in `Song.artworkUri`
  ([[Per-track artwork]]).
- **White note**: the placeholder drawn when a song has no picture; albums, artists and folders always show it
  ([[Albums, artists and folders show the placeholder]]).

## Design
- **Tokens / the kit**: the One UI colours, type, shapes, sizes and components in `:core:designsystem`
  ([[Build UI from tokens and the kit]], [[UI style guide]]).
- **Content panel, pill mini-player, A–Z rail**: the One UI building blocks of Home ([[One UI redesign decisions]]).

## Process
- **Agent**: a project subagent that owns a module ([[Agents]], [[Ownership map]]).
- **Spec / plan**: the binding design and its implementation plan in `docs/superpowers/` ([[Spec wins over plan]]).
- **Ledger / ruling**: decisions made while executing a plan ([[Ledgers]]).
- **Phase**: one planned slice of work ([[Timeline]]).
- **Vault / wiki**: this authored knowledge base, and the derived LLM wiki beside it ([[Vault conventions]],
  [[LLM wiki beside the vault]]).
