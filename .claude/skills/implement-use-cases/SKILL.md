---
name: implement-use-cases
description: "Comprehensive guide for creating a clean business logic layer using the Use Case pattern (also known as Interactors). This skill covers use case structure, naming conventions, error handling, testing, and integration with repositories and ViewModels."
---

# Skill: Implement Use Cases/Interactors

## Overview
Comprehensive guide for creating a clean business logic layer using the Use Case pattern (also known as Interactors). This skill covers use case structure, naming conventions, error handling, testing, and integration with repositories and ViewModels.

## When to Use
- Implementing business logic separate from UI and data layers
- Creating reusable operations across multiple screens
- Enforcing single responsibility principle
- Building testable business logic
- Coordinating multiple repositories

## Prerequisites
- Understanding of Clean Architecture principles
- Repository pattern implementation
- Hilt dependency injection setup
- Kotlin coroutines knowledge
- Flow and StateFlow understanding

## Workflow Steps

### Step 1: Understand Use Case Structure
**Goal**: Learn the anatomy of a use case

**Concepts**:
- Single responsibility: One use case = one business operation
- Input/Output: Clear parameters and return types
- Operator invoke: Makes use case callable like a function
- Coroutine-friendly: Uses suspend or Flow

**Basic Structure**:
```kotlin
class GetSongsUseCase @Inject constructor(
    private val repository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<Song>> {
        return repository.observeSongs()
            .flowOn(dispatcher)
    }
}
```

### Step 2: Define Use Case Naming Convention
**Goal**: Establish consistent naming patterns

**Naming Rules**:
- Start with verb: Get, Create, Update, Delete, Load, Save, etc.
- Describe the action: GetSongsUseCase, PlaySongUseCase
- End with "UseCase": Clear identification
- Be specific: GetSongsByArtistUseCase vs GetSongsUseCase

**Examples**:
```kotlin
// Query operations
GetSongsUseCase
GetSongByIdUseCase
GetPlaylistsUseCase
SearchSongsUseCase
GetRecentlyPlayedSongsUseCase

// Command operations
PlaySongUseCase
PauseSongUseCase
CreatePlaylistUseCase
UpdatePlaylistUseCase
DeletePlaylistUseCase
AddSongToPlaylistUseCase

// Complex operations
SyncMusicLibraryUseCase
ApplyEqualizerPresetUseCase
UpdateSongMetadataUseCase
```

### Step 3: Create Simple Use Cases
**Goal**: Implement basic single-repository use cases

**Actions**:
1. Create use case class
2. Inject repository
3. Implement invoke operator
4. Add proper dispatchers

**Implementation**:
```kotlin
// domain/usecase/GetSongsUseCase.kt
class GetSongsUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<Song>> {
        return musicRepository.observeSongs()
            .flowOn(dispatcher)
    }
}

// domain/usecase/GetSongByIdUseCase.kt
class GetSongByIdUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(songId: String): Result<Song> {
        return withContext(dispatcher) {
            musicRepository.getSongById(songId)
        }
    }
}

// domain/usecase/SearchSongsUseCase.kt
class SearchSongsUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(query: String): Flow<List<Song>> {
        return musicRepository.searchSongs(query)
            .flowOn(dispatcher)
    }
}

// domain/usecase/CreatePlaylistUseCase.kt
class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(name: String, description: String?): Result<Long> {
        return withContext(dispatcher) {
            playlistRepository.createPlaylist(name, description)
        }
    }
}
```

### Step 4: Create Parameterized Use Cases
**Goal**: Handle use cases with input parameters

**Actions**:
1. Define parameter data classes
2. Use sealed classes for complex inputs
3. Validate parameters

**Implementation**:
```kotlin
// domain/usecase/GetSongsByFilterUseCase.kt
class GetSongsByFilterUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(filter: SongFilter): Flow<List<Song>> {
        return musicRepository.getSongsFiltered(
            artist = filter.artist,
            album = filter.album,
            genre = filter.genre,
            year = filter.year
        ).flowOn(dispatcher)
    }
}

data class SongFilter(
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null
)

// domain/usecase/UpdateSongMetadataUseCase.kt
class UpdateSongMetadataUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(params: Params): Result<Unit> {
        return withContext(dispatcher) {
            // Validate parameters
            if (params.songId.isBlank()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Song ID cannot be blank")
                )
            }
            
            musicRepository.updateSongMetadata(
                songId = params.songId,
                title = params.title,
                artist = params.artist,
                album = params.album,
                genre = params.genre,
                year = params.year
            )
        }
    }
    
    data class Params(
        val songId: String,
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val year: Int?
    )
}
```

### Step 5: Create Complex Use Cases
**Goal**: Coordinate multiple repositories

**Actions**:
1. Inject multiple repositories
2. Combine data from different sources
3. Handle complex business logic
4. Maintain transaction integrity

**Implementation**:
```kotlin
// domain/usecase/PlaySongUseCase.kt
class PlaySongUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackRepository: PlaybackRepository,
    private val analyticsRepository: AnalyticsRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(songId: String): Result<Unit> {
        return withContext(dispatcher) {
            try {
                // Get song details
                val song = musicRepository.getSongById(songId)
                    .getOrElse { return@withContext Result.failure(it) }
                
                // Start playback
                playbackRepository.playSong(song)
                    .getOrElse { return@withContext Result.failure(it) }
                
                // Update play count
                musicRepository.incrementPlayCount(songId)
                
                // Track analytics
                analyticsRepository.trackSongPlayed(songId)
                
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

// domain/usecase/CreatePlaylistFromAlbumUseCase.kt
class CreatePlaylistFromAlbumUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(albumId: String, playlistName: String): Result<Long> {
        return withContext(dispatcher) {
            try {
                // Get album songs
                val songs = musicRepository.getSongsByAlbum(albumId)
                    .getOrElse { return@withContext Result.failure(it) }
                
                if (songs.isEmpty()) {
                    return@withContext Result.failure(
                        IllegalStateException("Album has no songs")
                    )
                }
                
                // Create playlist
                val playlistId = playlistRepository.createPlaylist(
                    name = playlistName,
                    description = "Created from album"
                ).getOrElse { return@withContext Result.failure(it) }
                
                // Add songs to playlist
                playlistRepository.addSongsToPlaylist(
                    playlistId = playlistId,
                    songIds = songs.map { it.id }
                ).getOrElse { 
                    // Rollback: delete playlist if adding songs fails
                    playlistRepository.deletePlaylist(playlistId)
                    return@withContext Result.failure(it)
                }
                
                Result.success(playlistId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}

// domain/usecase/SyncMusicLibraryUseCase.kt
class SyncMusicLibraryUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    private val mediaStoreRepository: MediaStoreRepository,
    private val metadataRepository: MetadataRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(): Flow<SyncProgress> = flow {
        emit(SyncProgress.Started)
        
        try {
            // Scan media store
            emit(SyncProgress.Scanning)
            val mediaFiles = mediaStoreRepository.scanAudioFiles()
                .getOrElse { 
                    emit(SyncProgress.Error(it))
                    return@flow
                }
            
            // Extract metadata
            emit(SyncProgress.ExtractingMetadata(0, mediaFiles.size))
            val songs = mediaFiles.mapIndexed { index, file ->
                emit(SyncProgress.ExtractingMetadata(index + 1, mediaFiles.size))
                metadataRepository.extractMetadata(file)
            }
            
            // Save to database
            emit(SyncProgress.Saving)
            musicRepository.saveSongs(songs)
                .getOrElse {
                    emit(SyncProgress.Error(it))
                    return@flow
                }
            
            emit(SyncProgress.Completed(songs.size))
        } catch (e: Exception) {
            emit(SyncProgress.Error(e))
        }
    }.flowOn(dispatcher)
}

sealed interface SyncProgress {
    data object Started : SyncProgress
    data object Scanning : SyncProgress
    data class ExtractingMetadata(val current: Int, val total: Int) : SyncProgress
    data object Saving : SyncProgress
    data class Completed(val songsCount: Int) : SyncProgress
    data class Error(val throwable: Throwable) : SyncProgress
}
```

### Step 6: Implement Error Handling
**Goal**: Handle errors gracefully in use cases

**Actions**:
1. Use Result type for operations that can fail
2. Create domain-specific error types
3. Map repository errors to domain errors
4. Provide meaningful error messages

**Implementation**:
```kotlin
// domain/model/DomainError.kt
sealed class DomainError : Exception() {
    data class NotFound(val message: String) : DomainError()
    data class InvalidInput(val message: String) : DomainError()
    data class PermissionDenied(val message: String) : DomainError()
    data class StorageError(val message: String) : DomainError()
    data class Unknown(val throwable: Throwable) : DomainError()
}

// domain/usecase/GetSongByIdUseCase.kt
class GetSongByIdUseCase @Inject constructor(
    private val musicRepository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    suspend operator fun invoke(songId: String): Result<Song> {
        return withContext(dispatcher) {
            try {
                if (songId.isBlank()) {
                    return@withContext Result.failure(
                        DomainError.InvalidInput("Song ID cannot be blank")
                    )
                }
                
                musicRepository.getSongById(songId)
                    .mapError { error ->
                        when (error) {
                            is RepositoryError.NotFound -> 
                                DomainError.NotFound("Song not found: $songId")
                            is RepositoryError.DatabaseError -> 
                                DomainError.StorageError(error.message)
                            else -> 
                                DomainError.Unknown(error)
                        }
                    }
            } catch (e: Exception) {
                Result.failure(DomainError.Unknown(e))
            }
        }
    }
}

// Extension function for error mapping
inline fun <T, R> Result<T>.mapError(
    transform: (Throwable) -> Throwable
): Result<R> where R : T {
    return when {
        isSuccess -> Result.success(getOrThrow() as R)
        else -> Result.failure(transform(exceptionOrNull()!!))
    }
}
```

### Step 7: Add Use Case Testing
**Goal**: Ensure use cases work correctly

**Actions**:
1. Create test class for each use case
2. Mock repositories
3. Test success and failure scenarios
4. Verify business logic

**Implementation**:
```kotlin
// domain/usecase/GetSongsUseCaseTest.kt
@ExperimentalCoroutinesApi
class GetSongsUseCaseTest {
    
    private lateinit var useCase: GetSongsUseCase
    private lateinit var repository: FakeMusicRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    
    @Before
    fun setup() {
        repository = FakeMusicRepository()
        useCase = GetSongsUseCase(repository, testDispatcher)
    }
    
    @Test
    fun `invoke returns songs from repository`() = runTest {
        // Given
        val expectedSongs = listOf(
            Song("1", "Song 1", "Artist 1", "Album 1", 180000),
            Song("2", "Song 2", "Artist 2", "Album 2", 200000)
        )
        repository.setSongs(expectedSongs)
        
        // When
        val result = useCase().first()
        
        // Then
        assertEquals(expectedSongs, result)
    }
    
    @Test
    fun `invoke emits updates when songs change`() = runTest {
        // Given
        val songs = mutableListOf<List<Song>>()
        val job = launch {
            useCase().take(2).toList(songs)
        }
        
        // When
        repository.setSongs(listOf(Song("1", "Song 1", "Artist", "Album", 180000)))
        
        // Then
        job.join()
        assertEquals(2, songs.size)
        assertEquals(0, songs[0].size)
        assertEquals(1, songs[1].size)
    }
}

// domain/usecase/PlaySongUseCaseTest.kt
@ExperimentalCoroutinesApi
class PlaySongUseCaseTest {
    
    private lateinit var useCase: PlaySongUseCase
    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var playbackRepository: FakePlaybackRepository
    private lateinit var analyticsRepository: FakeAnalyticsRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    
    @Before
    fun setup() {
        musicRepository = FakeMusicRepository()
        playbackRepository = FakePlaybackRepository()
        analyticsRepository = FakeAnalyticsRepository()
        
        useCase = PlaySongUseCase(
            musicRepository,
            playbackRepository,
            analyticsRepository,
            testDispatcher
        )
    }
    
    @Test
    fun `invoke plays song successfully`() = runTest {
        // Given
        val song = Song("1", "Test Song", "Artist", "Album", 180000)
        musicRepository.addSong(song)
        
        // When
        val result = useCase("1")
        
        // Then
        assertTrue(result.isSuccess)
        assertTrue(playbackRepository.isPlaying)
        assertEquals(1, musicRepository.getPlayCount("1"))
        assertTrue(analyticsRepository.wasTracked("1"))
    }
    
    @Test
    fun `invoke fails when song not found`() = runTest {
        // When
        val result = useCase("nonexistent")
        
        // Then
        assertTrue(result.isFailure)
        assertFalse(playbackRepository.isPlaying)
    }
    
    @Test
    fun `invoke fails when playback fails`() = runTest {
        // Given
        val song = Song("1", "Test Song", "Artist", "Album", 180000)
        musicRepository.addSong(song)
        playbackRepository.shouldFail = true
        
        // When
        val result = useCase("1")
        
        // Then
        assertTrue(result.isFailure)
        assertEquals(0, musicRepository.getPlayCount("1"))
    }
}

// Test doubles
class FakeMusicRepository : MusicRepository {
    private val songs = MutableStateFlow<List<Song>>(emptyList())
    private val playCounts = mutableMapOf<String, Int>()
    
    fun setSongs(newSongs: List<Song>) {
        songs.value = newSongs
    }
    
    fun addSong(song: Song) {
        songs.value = songs.value + song
    }
    
    override fun observeSongs(): Flow<List<Song>> = songs
    
    override suspend fun getSongById(id: String): Result<Song> {
        return songs.value.find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(Exception("Song not found"))
    }
    
    override suspend fun incrementPlayCount(songId: String): Result<Unit> {
        playCounts[songId] = (playCounts[songId] ?: 0) + 1
        return Result.success(Unit)
    }
    
    fun getPlayCount(songId: String): Int = playCounts[songId] ?: 0
}
```

### Step 8: Integrate with ViewModels
**Goal**: Use use cases in ViewModels

**Actions**:
1. Inject use cases into ViewModels
2. Call use cases in ViewModel functions
3. Handle use case results
4. Update UI state

**Implementation**:
```kotlin
// presentation/library/LibraryViewModel.kt
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase,
    private val searchSongsUseCase: SearchSongsUseCase,
    private val playSongUseCase: PlaySongUseCase,
    private val getSongsByFilterUseCase: GetSongsByFilterUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(SongFilter())
    
    init {
        loadSongs()
    }
    
    private fun loadSongs() {
        viewModelScope.launch {
            combine(
                searchQuery,
                filter,
                getSongsUseCase()
            ) { query, filter, songs ->
                when {
                    query.isNotBlank() -> searchSongsUseCase(query).first()
                    filter.hasActiveFilters() -> getSongsByFilterUseCase(filter).first()
                    else -> songs
                }
            }.catch { error ->
                _uiState.value = LibraryUiState.Error(error.message ?: "Unknown error")
            }.collect { songs ->
                _uiState.value = if (songs.isEmpty()) {
                    LibraryUiState.Empty
                } else {
                    LibraryUiState.Success(songs)
                }
            }
        }
    }
    
    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }
    
    fun onFilterChanged(newFilter: SongFilter) {
        filter.value = newFilter
    }
    
    fun onSongClicked(songId: String) {
        viewModelScope.launch {
            playSongUseCase(songId)
                .onSuccess {
                    // Navigate to player or show success
                }
                .onFailure { error ->
                    // Show error message
                    _uiState.value = LibraryUiState.Error(
                        error.message ?: "Failed to play song"
                    )
                }
        }
    }
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Success(val songs: List<Song>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}
```

### Step 9: Organize Use Cases
**Goal**: Structure use cases by feature

**Actions**:
1. Group related use cases
2. Create feature-specific packages
3. Document use case dependencies

**Structure**:
```
domain/
├── usecase/
│   ├── song/
│   │   ├── GetSongsUseCase.kt
│   │   ├── GetSongByIdUseCase.kt
│   │   ├── SearchSongsUseCase.kt
│   │   ├── UpdateSongMetadataUseCase.kt
│   │   └── DeleteSongUseCase.kt
│   ├── playlist/
│   │   ├── GetPlaylistsUseCase.kt
│   │   ├── CreatePlaylistUseCase.kt
│   │   ├── UpdatePlaylistUseCase.kt
│   │   ├── DeletePlaylistUseCase.kt
│   │   ├── AddSongToPlaylistUseCase.kt
│   │   └── RemoveSongFromPlaylistUseCase.kt
│   ├── playback/
│   │   ├── PlaySongUseCase.kt
│   │   ├── PauseSongUseCase.kt
│   │   ├── SkipToNextUseCase.kt
│   │   ├── SkipToPreviousUseCase.kt
│   │   └── SeekToPositionUseCase.kt
│   ├── equalizer/
│   │   ├── GetEqualizerPresetsUseCase.kt
│   │   ├── ApplyEqualizerPresetUseCase.kt
│   │   ├── SaveEqualizerPresetUseCase.kt
│   │   └── DeleteEqualizerPresetUseCase.kt
│   └── library/
│       ├── SyncMusicLibraryUseCase.kt
│       ├── GetLibraryStatsUseCase.kt
│       └── CleanupLibraryUseCase.kt
```

## Addons

### Base Use Case Classes
```kotlin
// Base class for Flow-based use cases
abstract class FlowUseCase<in P, out R> {
    operator fun invoke(params: P): Flow<R> = execute(params)
    
    protected abstract fun execute(params: P): Flow<R>
}

// Base class for suspend use cases
abstract class SuspendUseCase<in P, out R> {
    suspend operator fun invoke(params: P): Result<R> {
        return try {
            execute(params)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    protected abstract suspend fun execute(params: P): Result<R>
}

// Usage
class GetSongsUseCase @Inject constructor(
    private val repository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) : FlowUseCase<Unit, List<Song>>() {
    
    override fun execute(params: Unit): Flow<List<Song>> {
        return repository.observeSongs()
            .flowOn(dispatcher)
    }
}
```

### Use Case with Caching
```kotlin
class GetSongsUseCase @Inject constructor(
    private val repository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    private var cachedSongs: List<Song>? = null
    private var lastFetchTime: Long = 0
    
    operator fun invoke(forceRefresh: Boolean = false): Flow<List<Song>> {
        return flow {
            // Emit cached data immediately if available
            cachedSongs?.let { emit(it) }
            
            // Fetch fresh data if needed
            if (forceRefresh || shouldRefresh()) {
                repository.observeSongs()
                    .collect { songs ->
                        cachedSongs = songs
                        lastFetchTime = System.currentTimeMillis()
                        emit(songs)
                    }
            }
        }.flowOn(dispatcher)
    }
    
    private fun shouldRefresh(): Boolean {
        return System.currentTimeMillis() - lastFetchTime > CACHE_DURATION
    }
    
    companion object {
        private const val CACHE_DURATION = 5 * 60 * 1000L // 5 minutes
    }
}
```

## Related Files
- `domain/usecase/` - Use case implementations
- `domain/repository/` - Repository interfaces
- `domain/model/` - Domain models
- `presentation/*/ViewModel.kt` - ViewModel integration

## Notes
- One use case = one business operation
- Use operator invoke for clean syntax
- Always use appropriate coroutine dispatchers
- Return Flow for reactive data, suspend for one-time operations
- Use Result type for operations that can fail
- Keep use cases focused and testable
- Inject repositories, not data sources
- Handle errors at the use case level
- Test use cases with fake repositories
- Document complex business logic

## Common Patterns

### Combining Multiple Flows
```kotlin
class GetDashboardDataUseCase @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase,
    private val getPlaylistsUseCase: GetPlaylistsUseCase,
    private val getRecentlyPlayedUseCase: GetRecentlyPlayedSongsUseCase
) {
    operator fun invoke(): Flow<DashboardData> {
        return combine(
            getSongsUseCase(),
            getPlaylistsUseCase(),
            getRecentlyPlayedUseCase()
        ) { songs, playlists, recentlyPlayed ->
            DashboardData(
                totalSongs = songs.size,
                playlists = playlists,
                recentlyPlayed = recentlyPlayed
            )
        }
    }
}
```

### Retry Logic
```kotlin
class SyncMusicLibraryUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    suspend operator fun invoke(maxRetries: Int = 3): Result<Unit> {
        repeat(maxRetries) { attempt ->
            repository.syncLibrary()
                .onSuccess { return Result.success(Unit) }
                .onFailure { error ->
                    if (attempt == maxRetries - 1) {
                        return Result.failure(error)
                    }
                    delay(1000L * (attempt + 1)) // Exponential backoff
                }
        }
        return Result.failure(Exception("Max retries exceeded"))
    }
}
```

### Pagination
```kotlin
class GetSongsPagedUseCase @Inject constructor(
    private val repository: MusicRepository
) {
    operator fun invoke(): Flow<PagingData<Song>> {
        return repository.getSongsPaged()
    }
}