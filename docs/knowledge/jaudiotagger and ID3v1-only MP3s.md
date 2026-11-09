---
type: knowledge
status: active
date: 2026-11-08
source: "per-track artwork Task 6 review: a probe against jaudiotagger 2.3.15 on a temporary ID3v1-only MP3, plus bytecode reading of MP3File and FlacTag"
tags: [tag-editor, jaudiotagger, mp3, flac, artwork]
related: ["[[Knowledge]]", "[[Tag editor]]", "[[Per-track artwork]]", "[[feature-tageditor]]"]
---

# jaudiotagger and ID3v1-only MP3s

**Symptom:** removing artwork from an old MP3 that carries only an ID3v1 tag throws
`UnsupportedOperationException: Not implemented for this format`; writing artwork to it leaves the track without its
title, artist and album after a rescan.

**Cause (jaudiotagger 2.3.15):**
- `AudioFile.getTag()` on an MP3 with no ID3v2 tag returns the `ID3v1Tag`. Its `deleteArtworkField()` always throws and
  its `getFirstArtwork()` returns null (ID3v1 has no picture frame).
- `getTagOrCreateDefault()` creates an **empty** ID3v2 tag; it does not convert the ID3v1 fields. Android reads ID3v1
  only when there is no valid ID3v2 tag, so the new picture-only v2 tag hides the old text.
- FLAC: `FlacTag`'s artwork methods only see the PICTURE metadata blocks. A picture stored as a Vorbis-comment field
  (`METADATA_BLOCK_PICTURE` / `COVERART`) is invisible to them, although Android may still show it.

**Fix (per-track artwork Task 6, rulings P16–P19):**
- `removeArtwork` does nothing when the tag has no picture (covers ID3v1-only and untagged files).
- `writeArtwork` on an ID3v1-only MP3 first builds the ID3v2 tag from the ID3v1 tag, then adds the picture.
- `removeArtwork` / `hasArtwork` also handle the FLAC Vorbis-comment picture.

- In memory, jaudiotagger's ID3v1 → ID3v2.3 conversion reads the genre back as `Rock  Rock` until the file is
  committed, so a read-only conversion sets the genre again from the ID3v1 tag.
- `VorbisCommentTag.getArtworkList()` base64-decodes and can throw on a malformed field, so presence is checked with
  `hasField`, never by decoding (P19).

**Rule for tests:** every tag-IO contract test needs an ID3v1-only MP3 fixture next to the untagged one.
