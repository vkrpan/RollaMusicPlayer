---
type: hub
status: active
date: 2026-11-13
tags: [overview]
related: ["[[00 Home]]", "[[Tech Stack]]", "[[Glossary]]", "[[Architecture Overview]]"]
---

# Project Overview

## Purpose
Rolla Music (code name RollaMusicPlayer) plays the music already on an Android phone, without a network, an account
or any data leaving the device. It is for someone with a local library who wants a fast, good-looking player with
real library tools: tag editing, custom tags, playlists and an equaliser. Privacy is a design constraint, not a
setting: [[Offline and privacy first]].

## Core features
- Local playback (MP3, FLAC, WAV, OGG, M4A, AAC) in the background, with a restored queue: [[Playback and queue]]
- Home with six tabs (Favourites · Playlists · Tracks · Albums · Artists · Folders) and an A–Z rail:
  [[Home and library]]
- Now Playing, the floating mini-player and the queue sheet: [[Now Playing and mini-player]]
- Playlists and smart playlists: [[Playlists]]
- 8-band equaliser with presets: [[Equaliser]]
- Tag editing, single and batch, with artwork: [[Tag editor]]
- Custom tags and subtags stored in each file's `ROLLA_TAGS` field, Manage tags, Find tracks: [[Custom tags]]
- Every track's own embedded picture everywhere: [[Per-track artwork]]
- Search across tracks, albums and artists: [[Search]]
- Settings: [[Settings]] · the home-screen widget: [[Home-screen widget]] · the media permission: [[Permissions]]

Full list with status and modules: [[Features]].

## Out of scope
- Anything that needs a network: streaming, online lyrics or artwork lookups, cloud sync, accounts, analytics, crash
  reporting ([[Offline and privacy first]]).
- A live audio visualiser (needs `RECORD_AUDIO`): [[Equaliser visualizer rejected]].
- Decorative settings that change nothing: [[No decorative toggles]], [[Settings toggles rejected]].
- Android Auto and browsable media libraries (the session is a `MediaLibraryService` only for the resume card:
  [[Panel resume targets stock Android]]).

## Status
- v1.0.0 shipped on `main` (2026-07-23): [[v1.0 release]].
- The One UI redesign, per-track artwork and custom tags are on `feat/oneui-redesign`, pushed, waiting for the user's
  merge: [[One UI redesign]], [[Changelog]], [[Roadmap]].

## Repo
`github.com/vkrpan/RollaMusicPlayer` · main branch: `main` · working branch: `feat/oneui-redesign`.
Project instructions for Claude: `CLAUDE.md` ([[CLAUDE]]); public readme: `README.md` ([[README]]).
