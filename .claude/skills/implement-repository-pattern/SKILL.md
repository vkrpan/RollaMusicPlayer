---
name: implement-repository-pattern
description: "Guide for creating data repositories following clean architecture principles. This skill helps implement the repository pattern to abstract data sources and provide a clean API for data access throughout the application."
---

# Skill: Implement Repository Pattern

## Overview
Guide for creating data repositories following clean architecture principles. This skill helps implement the repository pattern to abstract data sources and provide a clean API for data access throughout the application.

## Steps

### 1. Analyze Data Requirements
- Identify data entities and their sources (local/remote)
- Determine caching strategy needs
- Map out data flow and transformations
- Review existing data models

### 2. Create Data Models
```kotlin
// Domain model (what the app uses)
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long
)

// Local entity (Room database)
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val duration: Long
)

// Remote DTO (API response)
data class SongDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("duration_ms") val durationMs: Long
)
```

### 3. Create Data Source Interfaces
```kotlin
// Local data source
interface LocalSongDataSource {
    suspend fun getSongs(): List<SongEntity>
    suspend fun getSongById(id: String): SongEntity?
    suspend fun insertSongs(songs: List<SongEntity>)
    suspend fun deleteSong(id: String)
    fun observeSongs(): Flow<List<SongEntity>>
}

// Remote data source
interface RemoteSongDataSource {
    suspend fun fetchSongs(): Result<List<SongDto>>
    suspend fun fetchSongById(id: String): Result<SongDto>
}
```

### 4. Implement Data Sources
```kotlin
// Local implementation
class LocalSongDataSourceImpl @Inject constructor(
    private val songDao: SongDao
) : LocalSongDataSource {
    override suspend fun getSongs(): List<SongEntity> = 
        songDao.getAllSongs()
    
    override suspend fun getSongById(id: String): SongEntity? = 
        songDao.getSongById(id)
    
    override suspend fun insertSongs(songs: List<SongEntity>) = 
        songDao.insertSongs(songs)
    
    override suspend fun deleteSong(id: String) = 
        songDao.deleteSong(id)
    
    override fun observeSongs(): Flow<List<SongEntity>> = 
        songDao.observeSongs()
}

// Remote implementation
class RemoteSongDataSourceImpl @Inject constructor(
    private val apiService: MusicApiService
) : RemoteSongDataSource {
    override suspend fun fetchSongs(): Result<List<SongDto>> = 
        runCatching { apiService.getSongs() }
    
    override suspend fun fetchSongById(id: String): Result<SongDto> = 
        runCatching { apiService.getSongById(id) }
}
```

### 5. Create Mappers
```kotlin
// Extension functions for mapping
fun SongEntity.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    duration = duration
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    duration = duration
)

fun SongDto.toEntity(): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    duration = durationMs
)

fun SongDto.toDomain(): Song = Song(
    id = id,
    title = title,
    artist = artist,
    duration = durationMs
)
```

### 6. Define Repository Interface
```kotlin
interface SongRepository {
    // Observe data (reactive)
    fun observeSongs(): Flow<List<Song>>
    fun observeSongById(id: String): Flow<Song?>
    
    // One-time fetch
    suspend fun getSongs(forceRefresh: Boolean = false): Result<List<Song>>
    suspend fun getSongById(id: String): Result<Song>
    
    // Mutations
    suspend fun addSong(song: Song): Result<Unit>
    suspend fun deleteSong(id: String): Result<Unit>
    suspend fun syncWithRemote(): Result<Unit>
}
```

### 7. Implement Repository with Caching
```kotlin
class SongRepositoryImpl @Inject constructor(
    private val localDataSource: LocalSongDataSource,
    private val remoteDataSource: RemoteSongDataSource,
    private val networkMonitor: NetworkMonitor,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : SongRepository {
    
    override fun observeSongs(): Flow<List<Song>> = 
        localDataSource.observeSongs()
            .map { entities -> entities.map { it.toDomain() } }
            .flowOn(ioDispatcher)
    
    override fun observeSongById(id: String): Flow<Song?> = 
        flow {
            localDataSource.getSongById(id)?.let { entity ->
                emit(entity.toDomain())
            }
        }.flowOn(ioDispatcher)
    
    override suspend fun getSongs(forceRefresh: Boolean): Result<List<Song>> = 
        withContext(ioDispatcher) {
            try {
                // Check if refresh is needed
                if (forceRefresh || shouldRefresh()) {
                    syncWithRemote().getOrThrow()
                }
                
                // Return local data
                val songs = localDataSource.getSongs().map { it.toDomain() }
                Result.success(songs)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    override suspend fun getSongById(id: String): Result<Song> = 
        withContext(ioDispatcher) {
            try {
                // Try local first
                localDataSource.getSongById(id)?.let { entity ->
                    return@withContext Result.success(entity.toDomain())
                }
                
                // Fetch from remote if not found locally
                if (networkMonitor.isOnline) {
                    remoteDataSource.fetchSongById(id)
                        .map { dto ->
                            val entity = dto.toEntity()
                            localDataSource.insertSongs(listOf(entity))
                            entity.toDomain()
                        }
                } else {
                    Result.failure(NoSuchElementException("Song not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    override suspend fun addSong(song: Song): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                localDataSource.insertSongs(listOf(song.toEntity()))
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    override suspend fun deleteSong(id: String): Result<Unit> = 
        withContext(ioDispatcher) {
            try {
                localDataSource.deleteSong(id)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    
    override suspend fun syncWithRemote(): Result<Unit> = 
        withContext(ioDispatcher) {
            if (!networkMonitor.isOnline) {
                return@withContext Result.failure(
                    IOException("No network connection")
                )
            }
            
            remoteDataSource.fetchSongs()
                .map { dtos ->
                    val entities = dtos.map { it.toEntity() }
                    localDataSource.insertSongs(entities)
                }
        }
    
    private suspend fun shouldRefresh(): Boolean {
        // Implement cache expiration logic
        // e.g., check last sync timestamp
        return false
    }
}
```

### 8. Setup Dependency Injection
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    
    @Binds
    @Singleton
    abstract fun bindSongRepository(
        impl: SongRepositoryImpl
    ): SongRepository
    
    @Binds
    abstract fun bindLocalSongDataSource(
        impl: LocalSongDataSourceImpl
    ): LocalSongDataSource
    
    @Binds
    abstract fun bindRemoteSongDataSource(
        impl: RemoteSongDataSourceImpl
    ): RemoteSongDataSource
}
```

### 9. Create Repository Tests
```kotlin
@ExperimentalCoroutinesApi
class SongRepositoryImplTest {
    
    private lateinit var repository: SongRepositoryImpl
    private lateinit var localDataSource: FakeLocalSongDataSource
    private lateinit var remoteDataSource: FakeRemoteSongDataSource
    private lateinit var networkMonitor: FakeNetworkMonitor
    
    @Before
    fun setup() {
        localDataSource = FakeLocalSongDataSource()
        remoteDataSource = FakeRemoteSongDataSource()
        networkMonitor = FakeNetworkMonitor()
        
        repository = SongRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            networkMonitor = networkMonitor,
            ioDispatcher = UnconfinedTestDispatcher()
        )
    }
    
    @Test
    fun `getSongs returns local data when available`() = runTest {
        // Given
        val expectedSongs = listOf(
            SongEntity("1", "Song 1", "Artist 1", 180000)
        )
        localDataSource.songs = expectedSongs
        
        // When
        val result = repository.getSongs()
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.size)
    }
    
    @Test
    fun `getSongs syncs with remote when forceRefresh is true`() = runTest {
        // Given
        networkMonitor.isOnline = true
        remoteDataSource.songs = listOf(
            SongDto("1", "Song 1", "Artist 1", 180000)
        )
        
        // When
        repository.getSongs(forceRefresh = true)
        
        // Then
        assertEquals(1, localDataSource.songs.size)
    }
    
    @Test
    fun `observeSongs emits updates when data changes`() = runTest {
        // Given
        val songs = mutableListOf<List<Song>>()
        val job = launch {
            repository.observeSongs().take(2).toList(songs)
        }
        
        // When
        localDataSource.insertSongs(listOf(
            SongEntity("1", "Song 1", "Artist 1", 180000)
        ))
        
        // Then
        job.join()
        assertEquals(2, songs.size)
        assertEquals(0, songs[0].size)
        assertEquals(1, songs[1].size)
    }
}
```

## Addons

### Recommended Dependencies
```kotlin
// build.gradle.kts (app module)
dependencies {
    // Room for local storage
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Retrofit for remote API
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    // Hilt for DI
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    
    // Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.0.0")
}
```

### Network Monitor Utility
```kotlin
interface NetworkMonitor {
    val isOnline: Boolean
    fun observeNetworkStatus(): Flow<Boolean>
}

@Singleton
class NetworkMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkMonitor {
    
    private val connectivityManager = 
        context.getSystemService<ConnectivityManager>()
    
    override val isOnline: Boolean
        get() = connectivityManager?.activeNetwork != null
    
    override fun observeNetworkStatus(): Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }
            
            override fun onLost(network: Network) {
                trySend(false)
            }
        }
        
        connectivityManager?.registerDefaultNetworkCallback(callback)
        
        awaitClose {
            connectivityManager?.unregisterNetworkCallback(callback)
        }
    }
}
```

### Cache Timestamp Manager
```kotlin
@Singleton
class CacheTimestampManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val CACHE_DURATION = 5.minutes
    
    suspend fun shouldRefresh(key: String): Boolean {
        val lastSync = dataStore.data.first()[stringPreferencesKey(key)]
            ?.let { Instant.parse(it) }
            ?: return true
        
        return Duration.between(lastSync, Instant.now()) > CACHE_DURATION
    }
    
    suspend fun updateTimestamp(key: String) {
        dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = Instant.now().toString()
        }
    }
}
```

## Related Files
- `data/repository/` - Repository implementations
- `data/source/local/` - Local data sources
- `data/source/remote/` - Remote data sources
- `data/mapper/` - Data mapping functions
- `domain/repository/` - Repository interfaces
- `domain/model/` - Domain models

## Notes
- Always use domain models in the repository interface, not entities or DTOs
- Implement offline-first strategy: read from local, sync in background
- Use Flow for reactive data that updates over time
- Use suspend functions for one-time operations
- Handle errors gracefully with Result type
- Keep mappers simple and testable
- Consider implementing a generic repository base class for common operations
- Use proper coroutine dispatchers (IO for database/network operations)
- Implement proper cache invalidation strategies
- Test repositories with fake implementations, not mocks

## Common Patterns

### Single Source of Truth
Always read from local database, sync with remote in background:
```kotlin
override fun observeSongs(): Flow<List<Song>> = 
    localDataSource.observeSongs()
        .map { it.map(SongEntity::toDomain) }
        .onStart { syncWithRemote() }
```

### Error Handling
Use Result type for operations that can fail:
```kotlin
sealed class DataError {
    data class Network(val message: String) : DataError()
    data class Database(val message: String) : DataError()
    data class Unknown(val throwable: Throwable) : DataError()
}

typealias DataResult<T> = Result<T, DataError>
```

### Pagination Support
```kotlin
interface PaginatedRepository<T> {
    fun observeItems(): Flow<PagingData<T>>
    suspend fun refresh()
}