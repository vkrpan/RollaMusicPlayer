---
name: implement-media-scanning
description: "Step-by-step workflow for scanning local audio with the MediaStore API and indexing it into the Room library database — querying songs, extracting metadata and album-art URIs, incremental re-scans, and exposing the library as Flow. Fully offline, no network metadata lookups."
---

# Skill: Implement Media Scanning

## Overview
A workflow for discovering the user's local audio via `MediaStore.Audio` and indexing it into the Room database so the rest of the app reads from a fast local source of truth. Covers the projection/query, mapping cursor rows to entities, album-art URIs, incremental re-scans (only changed files), and exposing the indexed library as a reactive `Flow`. All metadata comes from local files and MediaStore — there are no online lookups.

## When to Use
- First launch after the audio permission is granted
- Refreshing the library when the user adds/removes music
- Building the indexer that feeds the Library, Player, and Widget
- Adding "last scan" / incremental update logic

## Prerequisites
- Audio read permission granted (see `handle-runtime-permissions`)
- Room database with a `SongEntity` and `SongDao` (see `add-room-database` / data-layer-agent owns the schema)
- Hilt configured; coroutines available
- Min SDK 24, target SDK 34

> The Room schema is owned by data-layer-agent. If `SongEntity` needs new columns for scanned metadata, request the change there — do not edit entities from the scanner.

## Workflow Steps

### Step 1: Define the MediaStore Projection
**Goal**: Request only the columns the library needs

**Implementation**:
```kotlin
// data/scanner/MediaStoreColumns.kt
object MediaStoreColumns {
    val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ARTIST_ID,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TRACK,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.DATA  // absolute file path; still usable for read on 24..34
    )

    // Only real music files, exclude ringtones/notifications/alarms
    const val SELECTION = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    const val SORT = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

    val COLLECTION: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
}
```

### Step 2: Build the Scanner (query → domain rows)
**Goal**: Read the cursor on a background thread and produce scan rows

**Implementation**:
```kotlin
// data/scanner/MediaScanner.kt
data class ScannedSong(
    val mediaStoreId: Long,
    val title: String,
    val artist: String,
    val artistId: Long,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val trackNumber: Int?,
    val year: Int?,
    val dateAdded: Long,
    val dateModified: Long,
    val contentUri: String,   // stable content:// uri for playback
    val artworkUri: String    // album-art content uri (local)
)

class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun scan(): List<ScannedSong> = withContext(ioDispatcher) {
        val result = mutableListOf<ScannedSong>()
        context.contentResolver.query(
            MediaStoreColumns.COLLECTION,
            MediaStoreColumns.PROJECTION,
            MediaStoreColumns.SELECTION,
            null,
            MediaStoreColumns.SORT
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val artistIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val modCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStoreColumns.COLLECTION, id
                ).toString()
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    c.getLong(albumIdCol)
                ).toString()

                // TRACK is encoded as disc*1000 + track; normalize
                val rawTrack = c.getInt(trackCol)
                val track = if (rawTrack > 0) rawTrack % 1000 else null

                result += ScannedSong(
                    mediaStoreId = id,
                    title = c.getString(titleCol).orEmpty(),
                    artist = c.getString(artistCol).orEmpty(),
                    artistId = c.getLong(artistIdCol),
                    album = c.getString(albumCol).orEmpty(),
                    albumId = c.getLong(albumIdCol),
                    durationMs = c.getLong(durCol),
                    trackNumber = track,
                    year = c.getInt(yearCol).takeIf { it > 0 },
                    dateAdded = c.getLong(addedCol),
                    dateModified = c.getLong(modCol),
                    contentUri = contentUri,
                    artworkUri = artworkUri
                )
            }
        }
        result
    }
}
```

> Prefer the stable `content://` URI for playback (Media3 plays content URIs directly). `DATA` (file path) still works for reads on the project's SDK range but is deprecated; keep it only if a feature truly needs a raw path.

### Step 3: Index into Room (incremental diff)
**Goal**: Insert new/changed songs, remove deleted ones — without wiping the table each scan

**Implementation**:
```kotlin
// data/scanner/LibraryIndexer.kt
class LibraryIndexer @Inject constructor(
    private val scanner: MediaScanner,
    private val songDao: SongDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val scanned = scanner.scan()
        val scannedById = scanned.associateBy { it.mediaStoreId }

        val existing = songDao.getAllSongs().associateBy { it.mediaStoreId }

        // Upsert new or modified (dateModified changed)
        val toUpsert = scanned.filter { s ->
            val e = existing[s.mediaStoreId]
            e == null || e.dateModified != s.dateModified
        }.map { it.toEntity() }

        // Delete rows no longer present in MediaStore
        val toDeleteIds = existing.keys - scannedById.keys

        songDao.upsertSongs(toUpsert)
        if (toDeleteIds.isNotEmpty()) songDao.deleteByMediaStoreIds(toDeleteIds.toList())

        SyncResult(added = toUpsert.size, removed = toDeleteIds.size)
    }
}

data class SyncResult(val added: Int, val removed: Int)
```

DAO additions to request from data-layer-agent:
```kotlin
@Upsert suspend fun upsertSongs(songs: List<SongEntity>)
@Query("DELETE FROM songs WHERE media_store_id IN (:ids)")
suspend fun deleteByMediaStoreIds(ids: List<Long>)
@Query("SELECT * FROM songs") suspend fun getAllSongs(): List<SongEntity>
```

### Step 4: Trigger the Scan and Expose Progress
**Goal**: Run sync after permission granted; surface state to the UI

**Implementation**:
```kotlin
// presentation/library/LibraryViewModel.kt (state-management owned by viewmodel-architect)
sealed interface ScanState {
    data object Idle : ScanState
    data object Scanning : ScanState
    data class Done(val added: Int, val removed: Int) : ScanState
    data class Error(val message: String) : ScanState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val indexer: LibraryIndexer,
    observeSongs: ObserveSongsUseCase
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val songs: StateFlow<List<Song>> = observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onPermissionGranted() {
        if (_scanState.value == ScanState.Scanning) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanState.value = try {
                val r = indexer.sync()
                ScanState.Done(r.added, r.removed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanState.Error(e.message ?: "Scan failed")
            }
        }
    }
}
```

The library list reads from Room via a Flow, so the UI updates automatically as rows are indexed.

### Step 5: Keep the Library Fresh
**Goal**: Re-scan when content likely changed

**Options** (pick what fits the UX):
- Re-run `sync()` on app resume if it's been a while, or via pull-to-refresh.
- Observe `MediaStore` for changes with a `ContentObserver` on `MediaStoreColumns.COLLECTION` and debounce a re-sync.

```kotlin
// data/scanner/MediaStoreObserver.kt
class MediaStoreObserver(
    private val contentResolver: ContentResolver,
    private val onChanged: () -> Unit
) {
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { onChanged() }
    }
    fun start() = contentResolver.registerContentObserver(
        MediaStoreColumns.COLLECTION, true, observer
    )
    fun stop() = contentResolver.unregisterContentObserver(observer)
}
```

### Step 6: Verify
**Checklist**:
- [ ] Scan runs on a background dispatcher; cursor always closed (`use {}`)
- [ ] Only `IS_MUSIC` files indexed (no ringtones/alarms)
- [ ] Playback uses the stable `content://` URI
- [ ] Album-art URI resolves to local album art (no network)
- [ ] Incremental sync adds new, updates modified (dateModified), removes deleted — no full wipe
- [ ] Library Flow updates the UI as rows land
- [ ] TRACK normalized (disc*1000 + track handled)
- [ ] Works fully in airplane mode
- [ ] No INTERNET permission, no online metadata calls

## Related Files
- `data/scanner/MediaStoreColumns.kt` — projection/selection/collection uri
- `data/scanner/MediaScanner.kt` — MediaStore query → ScannedSong
- `data/scanner/LibraryIndexer.kt` — incremental diff into Room
- `data/scanner/MediaStoreObserver.kt` — change observer (optional)
- `presentation/library/LibraryViewModel.kt` — triggers scan, exposes state

## Notes
- Map `ScannedSong` → `SongEntity` with an extension; the entity shape is owned by data-layer-agent.
- Store `mediaStoreId` on the entity so incremental sync can diff and delete precisely.
- Album art via the `albumart` content uri is local; Coil/Glance can render it without network.
- Heavy libraries: keep the projection minimal and batch upserts; consider a `@Transaction` for the upsert+delete pair.
- Do not request WRITE permission here — scanning is read-only.

## Common Pitfalls
- ❌ Re-inserting the whole library every scan (loses play counts, churns the DB) — diff instead.
- ❌ Using `DATA`/file paths for playback on newer Android — prefer content URIs.
- ❌ Forgetting `IS_MUSIC` filter — pulls in system sounds.
- ❌ Querying on the main thread — always `Dispatchers.IO`.
