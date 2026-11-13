---
type: hub
status: active
date: 2026-11-13
tags: [interfaces]
related: ["[[00 Home]]", "[[Architecture Overview]]", "[[Data Flow]]"]
---

# Interfaces Overview

The app has **no HTTP API**: it is offline by design ([[Offline and privacy first]]). This section takes the place of
an API section and documents the contracts other software, or a future version of this app, relies on. A change to
any of them is a compatibility question, so each one gets its own note.

| Interface | Who uses it | Note |
|---|---|---|
| Navigation routes and the `rollamusic://search` deep link | the app itself, other apps (deep link) | [[Navigation routes]] |
| The media session (Media3) | System UI, headsets, Bluetooth, the widget | [[Playback and queue]] |
| Widget actions | the launcher's widget host | [[Widget actions]] |
| The `ROLLA_TAGS` file field | any tag reader or another system reading the files | [[ROLLA_TAGS field]] |
| The tag list file (export / import) | the user, other systems | [[Tag list file]] |

## Conventions
- The media session exposes Media3's default player commands only; there are no custom `SessionCommand`s.
- Formats written to files or exported carry a version or a format name so they can evolve
  (`{"format":"rolla-tags","version":1,…}`).
- New interface notes start from the template `99 Meta/Templates/interface-template.md`.
