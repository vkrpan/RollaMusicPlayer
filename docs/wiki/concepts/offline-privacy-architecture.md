---
type: concept
summary: "No network layer, local data only, no analytics, storage-only permissions."
aliases: ["Offline & Privacy Architecture", "Offline and privacy first"]
tags: [architecture, privacy]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# Offline and privacy first

No network layer, local data only, no analytics, storage-only permissions.

Rolla Music is fully offline and privacy-first ([[claude-md]]):

- **No network layer:** no Retrofit, OkHttp, Firebase or data-collecting SDKs, and no `INTERNET` permission. A
  release check (`checkReleaseManifestNoInternet`) fails the build if one appears ([[module-build-logic]]).
- **Local data only:** music via MediaStore, the library and playlists in Room ([[module-core-database]]), settings in
  DataStore ([[module-core-datastore]]), artwork cached in app storage ([[per-track-artwork]]).
- **Minimal permissions:** `READ_MEDIA_AUDIO` (13+) or `READ_EXTERNAL_STORAGE` ([[module-core-permissions]]).
- **Consequences:** no live audio visualiser, which would need `RECORD_AUDIO` ([[module-feature-equalizer]]); no network
  loading states in the UI.

## Related
[[claude-md]], [[module-build-logic]], [[module-core-database]], [[module-core-datastore]], [[per-track-artwork]], [[module-core-permissions]], [[module-feature-equalizer]]
