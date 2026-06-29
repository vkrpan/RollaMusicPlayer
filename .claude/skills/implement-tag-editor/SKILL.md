---
name: implement-tag-editor
description: "Step-by-step workflow for the ID3 tag editor in RollaMusicPlayer — reading and writing title/artist/album/genre/year/track/album-artist/composer and artwork on local files, handling scoped-storage write permission (MediaStore.createWriteRequest / RecoverableSecurityException), single and batch edits, and re-indexing after save. Fully local, no online metadata lookups."
---

# Skill: Implement ID3 Tag Editor

## Overview
A workflow for editing audio file metadata in place. Covers reading existing tags, writing changes to the file, the scoped-storage permission dance required to modify files the app doesn't own (Android 10+), single-file and batch editing, and re-indexing the library after a successful write. All tag data is read from and written to local files — there is **no online metadata or artwork lookup**.

## When to Use
- Building the tag editor screen (Phase 5)
- Writing edited metadata back to files
- Implementing batch tag edits across multiple songs
- Handling write-permission failures on scoped storage

## Prerequisites
- Audio read permission granted (see `handle-runtime-permissions`)
- Media scanning indexes songs with their `mediaStoreId` and `content://` URI (see `implement-media-scanning`)
- An Activity/Fragment able to launch an `IntentSender` (for the write request)
- A local tag library for reading/writing ID3/Vorbis/MP4 atoms

> **Library choice**: Android has no built-in ID3 *writer*. Use a local, offline tag library — JAudiotagger (Android-compatible fork) is the common choice; covers ID3v2 (MP3), Vorbis comments (FLAC/OGG), and MP4 atoms (M4A/AAC). It runs entirely on-device and pulls in no network code, so it's compatible with the offline/privacy constraint. Confirm the dependency with code-reviewer (local-only, no telemetry).

## The Scoped-Storage Write Problem

On Android 10+ (API 29+), the app cannot freely write to media files it didn't create. To modify another app's / the user's media file you must obtain user consent:

- **API 30+ (R)**: batch a write request with `MediaStore.createWriteRequest(resolver, uris)` → launch the returned `IntentSender` → user approves once for the whole batch.
- **API 29 (Q)**: catch `RecoverableSecurityException` on write and launch its `userAction.actionIntent.intentSender`.
- **API ≤28**: direct file write works with the storage permission (no consent dialog).

Plan the flow so the actual file write happens **after** consent is granted.

## Workflow Steps

### Step 1: Add the Tag Library
**Goal**: Enable reading/writing tags offline

**Implementation**:
```kotlin
// build.gradle.kts (app module)
dependencies {
    // Android-compatible JAudiotagger fork (local processing, no network)
    implementation("com.github.Adonai:jaudiotagger:2.3.15")
}
```
> Verify the exact coordinate/version with the build owner; the key requirement is an offline, on-device tag library. No INTERNET permission is added.

### Step 2: Define the Editable Tag Model
**Goal**: Represent the fields the editor exposes

**Implementation**:
```kotlin
// tageditor/TrackTags.kt
@Immutable
data class TrackTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",          // string to allow blank/partial input
    val trackNumber: String = "",
    val composer: String = ""
    // artwork handled separately (bytes)
)
```

### Step 3: Read Tags From a File
**Goal**: Populate the editor from existing metadata

**Implementation**:
```kotlin
// tageditor/TagReader.kt
class TagReader @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun read(file: File): TrackTags = withContext(ioDispatcher) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        TrackTags(
            title = tag.getFirst(FieldKey.TITLE),
            artist = tag.getFirst(FieldKey.ARTIST),
            album = tag.getFirst(FieldKey.ALBUM),
            albumArtist = tag.getFirst(FieldKey.ALBUM_ARTIST),
            genre = tag.getFirst(FieldKey.GENRE),
            year = tag.getFirst(FieldKey.YEAR),
            trackNumber = tag.getFirst(FieldKey.TRACK),
            composer = tag.getFirst(FieldKey.COMPOSER)
        )
    }
}
```

> Resolve a writable `File` from the song. On API ≤29 you may use the file path; on 30+ prefer copying through the content URI or operating on the path after consent. Keep the path-resolution helper in one place and coordinate with media-scanning on what's stored.

### Step 4: Request Write Consent (scoped storage)
**Goal**: Get permission to modify the user's file(s) before writing

**Implementation**:
```kotlin
// tageditor/MediaWriteRequester.kt
class MediaWriteRequester(
    private val activity: ComponentActivity,
    private val resolver: ContentResolver
) {
    private lateinit var pendingWrite: () -> Unit

    private val consentLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) pendingWrite()
            // else: user declined — surface a message, do not write
        }

    /** uris = the content uris of every file in this (possibly batch) edit. */
    fun ensureWritable(uris: List<Uri>, onGranted: () -> Unit) {
        pendingWrite = onGranted
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pi = MediaStore.createWriteRequest(resolver, uris)
            consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
        } else {
            // API <= 29: try the write; handle RecoverableSecurityException at the write site
            onGranted()
        }
    }
}
```

### Step 5: Write Tags (single + batch)
**Goal**: Persist edited tags after consent

**Implementation**:
```kotlin
// tageditor/TagWriter.kt
class TagWriter @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun write(file: File, tags: TrackTags) = withContext(ioDispatcher) {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        tag.setFieldIfNotBlank(FieldKey.TITLE, tags.title)
        tag.setFieldIfNotBlank(FieldKey.ARTIST, tags.artist)
        tag.setFieldIfNotBlank(FieldKey.ALBUM, tags.album)
        tag.setFieldIfNotBlank(FieldKey.ALBUM_ARTIST, tags.albumArtist)
        tag.setFieldIfNotBlank(FieldKey.GENRE, tags.genre)
        tag.setFieldIfNotBlank(FieldKey.YEAR, tags.year)
        tag.setFieldIfNotBlank(FieldKey.TRACK, tags.trackNumber)
        tag.setFieldIfNotBlank(FieldKey.COMPOSER, tags.composer)
        audioFile.commit()
    }
}

private fun Tag.setFieldIfNotBlank(key: FieldKey, value: String) {
    if (value.isNotBlank()) setField(key, value) else deleteField(key)
}
```

**Batch flow** — request consent for all URIs once, then write each:
```kotlin
// tageditor/BatchTagEditor.kt
suspend fun applyBatch(
    songs: List<Song>,
    changedFields: Map<FieldKey, String>   // only fields the user chose to overwrite
) = withContext(Dispatchers.IO) {
    songs.forEach { song ->
        val file = resolveFile(song)
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tagOrCreateAndSetDefault
        changedFields.forEach { (key, value) -> tag.setField(key, value) }
        audioFile.commit()
    }
}
```
Wire it as: `requester.ensureWritable(songs.map { it.contentUri.toUri() }) { viewModelScope.launch { applyBatch(...) } }`.

### Step 6: Handle API 29 RecoverableSecurityException
**Goal**: Get consent on Q when a direct write is denied

**Implementation**:
```kotlin
try {
    tagWriter.write(file, tags)
} catch (e: RecoverableSecurityException) {
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
        val intentSender = e.userAction.actionIntent.intentSender
        consentLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
        // retry write in the consent callback
    } else throw e
}
```

### Step 7: Re-index After Save
**Goal**: Keep the library/UI consistent with the new tags

**Actions**:
- After a successful write, re-read the affected file(s) and update the corresponding Room rows (or trigger a targeted re-sync via the indexer for those `mediaStoreId`s).
- Notify MediaStore if needed so other apps see the change:
```kotlin
MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
```
- Update the now-playing/widget state if the edited track is current.

### Step 8: Verify
**Checklist**:
- [ ] Reading populates all fields correctly across MP3/FLAC/M4A
- [ ] Single-file write persists and survives re-read
- [ ] Batch write requests consent **once** for all files, then writes each
- [ ] API 30+ uses `createWriteRequest`; API 29 handles `RecoverableSecurityException`; API ≤28 writes directly
- [ ] Declining consent writes nothing and shows a clear message
- [ ] Library/Room re-indexed after save; UI reflects new tags
- [ ] Current track + widget update if edited
- [ ] All work on `Dispatchers.IO`; no main-thread file I/O
- [ ] No online metadata or artwork lookup anywhere
- [ ] Blank field handling is intentional (clear vs keep)

## Related Files
- `tageditor/TrackTags.kt` — editable model
- `tageditor/TagReader.kt` — read tags
- `tageditor/TagWriter.kt` — write tags
- `tageditor/BatchTagEditor.kt` — batch apply
- `tageditor/MediaWriteRequester.kt` — scoped-storage consent
- `presentation/tageditor/TagEditorViewModel.kt` — state (viewmodel-architect)

## Notes
- Tag writing is the one place the app modifies files — it uses a **scoped-storage write request**, NOT a runtime permission, and never WRITE_EXTERNAL_STORAGE on modern Android.
- All file I/O on `Dispatchers.IO`; tag commits can be slow on large files.
- For batch edits, let the user pick **which** fields to overwrite so unpicked fields are left untouched per file.
- Artwork editing (embedding a local image) follows the same consent flow; read/write artwork bytes via the tag's artwork API — source images locally only.
- Re-index after every successful write so play counts and the rest of the library stay correct.

## Common Pitfalls
- ❌ Writing before obtaining consent on Android 10+ — throws SecurityException.
- ❌ Requesting consent per-file in a batch — annoying; batch all URIs into one `createWriteRequest`.
- ❌ Using WRITE_EXTERNAL_STORAGE to write on API 30+ — ignored; you still need the write request.
- ❌ Forgetting to re-index — the library shows stale tags until the next full scan.
- ❌ File I/O on the main thread — ANRs on large libraries.
