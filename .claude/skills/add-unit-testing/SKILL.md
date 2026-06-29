---
name: add-unit-testing
description: "Comprehensive guide for implementing unit tests in Android applications using JUnit, MockK, Kotlin Coroutines Test, and Turbine. Covers testing ViewModels, repositories, use cases, and state flows."
---

# Skill: Add Unit Testing

## Overview
Comprehensive guide for implementing unit tests in Android applications using JUnit, MockK, Kotlin Coroutines Test, and Turbine. Covers testing ViewModels, repositories, use cases, and state flows.

## When to Use
- Setting up testing infrastructure in a new project
- Writing tests for ViewModels with coroutines
- Testing repositories with fake implementations
- Testing use cases and business logic
- Verifying StateFlow and SharedFlow behavior
- Achieving good test coverage

## Prerequisites
- Understanding of testing principles (AAA pattern, test doubles)
- Kotlin coroutines knowledge
- Familiarity with the codebase architecture
- Basic JUnit experience

## Workflow Steps

### Step 1: Add Testing Dependencies
**Goal**: Configure project for unit testing

**Actions**:
1. Add testing libraries
2. Configure test source sets
3. Setup test runners

**Implementation**:
```kotlin
// build.gradle.kts (app module)
dependencies {
    // JUnit 4
    testImplementation("junit:junit:4.13.2")
    
    // Kotlin Test
    testImplementation("org.jetbrains.kotlin:kotlin-test:1.9.20")
    
    // Coroutines Test
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    
    // MockK for mocking
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    
    // Turbine for Flow testing
    testImplementation("app.cash.turbine:turbine:1.0.0")
    
    // Truth for assertions (optional but recommended)
    testImplementation("com.google.truth:truth:1.1.5")
    
    // Arch Core Testing for LiveData (if using LiveData)
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    
    // Hilt Testing
    testImplementation("com.google.dagger:hilt-android-testing:2.48")
    kspTest("com.google.dagger:hilt-compiler:2.48")
    
    // Robolectric for Android framework dependencies (optional)
    testImplementation("org.robolectric:robolectric:4.11.1")
}
```

### Step 2: Setup Test Infrastructure
**Goal**: Create base classes and utilities for testing

**Actions**:
1. Create test rule for coroutines
2. Setup test dispatcher
3. Create base test classes

**Implementation**:
```kotlin
// test/util/MainDispatcherRule.kt
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

// test/util/TestDispatchers.kt
@ExperimentalCoroutinesApi
class TestDispatchers(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) {
    val main: CoroutineDispatcher = testDispatcher
    val io: CoroutineDispatcher = testDispatcher
    val default: CoroutineDispatcher = testDispatcher
}

// test/util/FlowTestExtensions.kt
suspend fun <T> Flow<T>.test(
    timeout: Duration = 1.seconds,
    validate: suspend FlowTurbine<T>.() -> Unit
) {
    return withTimeout(timeout) {
        testIn(this, validate)
    }
}
```

### Step 3: Create Fake Repositories
**Goal**: Build test doubles for repositories

**Actions**:
1. Create fake implementations
2. Make them controllable for tests
3. Avoid using mocks when possible

**Implementation**:
```kotlin
// test/fake/FakeMusicRepository.kt
class FakeMusicRepository : MusicRepository {
    
    private val songs = MutableStateFlow<List<Song>>(emptyList())
    private val playCounts = mutableMapOf<String, Int>()
    
    var shouldReturnError = false
    var errorToReturn: Exception = Exception("Test error")
    
    // Control methods for tests
    fun setSongs(newSongs: List<Song>) {
        songs.value = newSongs
    }
    
    fun addSong(song: Song) {
        songs.value = songs.value + song
    }
    
    fun clear() {
        songs.value = emptyList()
        playCounts.clear()
        shouldReturnError = false
    }
    
    // Repository implementation
    override fun observeSongs(): Flow<List<Song>> = songs
    
    override suspend fun getSongById(id: String): Result<Song> {
        if (shouldReturnError) {
            return Result.failure(errorToReturn)
        }
        
        return songs.value.find { it.id == id }
            ?.let { Result.success(it) }
            ?: Result.failure(Exception("Song not found"))
    }
    
    override suspend fun searchSongs(query: String): Flow<List<Song>> {
        return songs.map { songList ->
            songList.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true)
            }
        }
    }
    
    override suspend fun incrementPlayCount(songId: String): Result<Unit> {
        if (shouldReturnError) {
            return Result.failure(errorToReturn)
        }
        
        playCounts[songId] = (playCounts[songId] ?: 0) + 1
        return Result.success(Unit)
    }
    
    fun getPlayCount(songId: String): Int = playCounts[songId] ?: 0
}

// test/fake/FakePlaylistRepository.kt
class FakePlaylistRepository : PlaylistRepository {
    
    private val playlists = MutableStateFlow<List<Playlist>>(emptyList())
    private var nextId = 1L
    
    var shouldReturnError = false
    
    fun setPlaylists(newPlaylists: List<Playlist>) {
        playlists.value = newPlaylists
    }
    
    fun clear() {
        playlists.value = emptyList()
        nextId = 1L
        shouldReturnError = false
    }
    
    override fun observePlaylists(): Flow<List<Playlist>> = playlists
    
    override suspend fun createPlaylist(
        name: String,
        description: String?
    ): Result<Long> {
        if (shouldReturnError) {
            return Result.failure(Exception("Failed to create playlist"))
        }
        
        val id = nextId++
        val playlist = Playlist(
            id = id,
            name = name,
            description = description,
            songCount = 0,
            createdAt = System.currentTimeMillis()
        )
        playlists.value = playlists.value + playlist
        return Result.success(id)
    }
    
    override suspend fun deletePlaylist(playlistId: Long): Result<Unit> {
        if (shouldReturnError) {
            return Result.failure(Exception("Failed to delete playlist"))
        }
        
        playlists.value = playlists.value.filter { it.id != playlistId }
        return Result.success(Unit)
    }
}
```

### Step 4: Test ViewModels
**Goal**: Write comprehensive ViewModel tests

**Actions**:
1. Setup ViewModel with fake dependencies
2. Test initial state
3. Test state updates
4. Test user actions
5. Test error handling

**Implementation**:
```kotlin
// presentation/library/LibraryViewModelTest.kt
@ExperimentalCoroutinesApi
class LibraryViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var getSongsUseCase: GetSongsUseCase
    private lateinit var playSongUseCase: PlaySongUseCase
    
    @Before
    fun setup() {
        musicRepository = FakeMusicRepository()
        getSongsUseCase = GetSongsUseCase(
            musicRepository,
            UnconfinedTestDispatcher()
        )
        playSongUseCase = PlaySongUseCase(
            musicRepository,
            FakePlaybackRepository(),
            FakeAnalyticsRepository(),
            UnconfinedTestDispatcher()
        )
        
        viewModel = LibraryViewModel(
            getSongsUseCase,
            playSongUseCase
        )
    }
    
    @After
    fun teardown() {
        musicRepository.clear()
    }
    
    @Test
    fun `initial state is loading`() {
        // Then
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }
    
    @Test
    fun `uiState updates to success when songs are loaded`() = runTest {
        // Given
        val songs = listOf(
            Song("1", "Song 1", "Artist 1", "Album 1", 180000),
            Song("2", "Song 2", "Artist 2", "Album 2", 200000)
        )
        
        // When
        musicRepository.setSongs(songs)
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state is LibraryUiState.Success)
        assertEquals(2, (state as LibraryUiState.Success).songs.size)
    }
    
    @Test
    fun `uiState updates to empty when no songs`() = runTest {
        // When
        musicRepository.setSongs(emptyList())
        advanceUntilIdle()
        
        // Then
        assertEquals(LibraryUiState.Empty, viewModel.uiState.value)
    }
    
    @Test
    fun `search filters songs correctly`() = runTest {
        // Given
        val songs = listOf(
            Song("1", "Rock Song", "Rock Artist", "Album", 180000),
            Song("2", "Pop Song", "Pop Artist", "Album", 200000)
        )
        musicRepository.setSongs(songs)
        advanceUntilIdle()
        
        // When
        viewModel.onSearchQueryChanged("Rock")
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value as LibraryUiState.Success
        assertEquals(1, state.songs.size)
        assertEquals("Rock Song", state.songs[0].title)
    }
    
    @Test
    fun `onSongClicked plays song successfully`() = runTest {
        // Given
        val song = Song("1", "Test Song", "Artist", "Album", 180000)
        musicRepository.addSong(song)
        
        // When
        viewModel.onSongClicked("1")
        advanceUntilIdle()
        
        // Then
        assertEquals(1, musicRepository.getPlayCount("1"))
    }
    
    @Test
    fun `onSongClicked shows error when song not found`() = runTest {
        // When
        viewModel.onSongClicked("nonexistent")
        advanceUntilIdle()
        
        // Then
        val state = viewModel.uiState.value
        assertTrue(state is LibraryUiState.Error)
    }
}

// Test with Turbine for Flow testing
@ExperimentalCoroutinesApi
class LibraryViewModelTurbineTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: LibraryViewModel
    private lateinit var musicRepository: FakeMusicRepository
    
    @Before
    fun setup() {
        musicRepository = FakeMusicRepository()
        viewModel = LibraryViewModel(
            GetSongsUseCase(musicRepository, UnconfinedTestDispatcher()),
            mockk(relaxed = true)
        )
    }
    
    @Test
    fun `uiState emits loading then success`() = runTest {
        // Given
        val songs = listOf(
            Song("1", "Song 1", "Artist", "Album", 180000)
        )
        
        viewModel.uiState.test {
            // Initial state
            assertEquals(LibraryUiState.Loading, awaitItem())
            
            // Update songs
            musicRepository.setSongs(songs)
            
            // Success state
            val successState = awaitItem() as LibraryUiState.Success
            assertEquals(1, successState.songs.size)
        }
    }
}
```

### Step 5: Test Use Cases
**Goal**: Verify business logic in use cases

**Actions**:
1. Test with fake repositories
2. Test success scenarios
3. Test error scenarios
4. Test edge cases

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
        useCase().test {
            // Initial empty state
            assertEquals(emptyList<Song>(), awaitItem())
            
            // Add songs
            repository.addSong(Song("1", "Song 1", "Artist", "Album", 180000))
            
            // Verify update
            val songs = awaitItem()
            assertEquals(1, songs.size)
            assertEquals("Song 1", songs[0].title)
        }
    }
    
    @Test
    fun `invoke returns empty list when no songs`() = runTest {
        // When
        val result = useCase().first()
        
        // Then
        assertTrue(result.isEmpty())
    }
}

// domain/usecase/PlaySongUseCaseTest.kt
@ExperimentalCoroutinesApi
class PlaySongUseCaseTest {
    
    private lateinit var useCase: PlaySongUseCase
    private lateinit var musicRepository: FakeMusicRepository
    private lateinit var playbackRepository: FakePlaybackRepository
    private lateinit var analyticsRepository: FakeAnalyticsRepository
    
    @Before
    fun setup() {
        musicRepository = FakeMusicRepository()
        playbackRepository = FakePlaybackRepository()
        analyticsRepository = FakeAnalyticsRepository()
        
        useCase = PlaySongUseCase(
            musicRepository,
            playbackRepository,
            analyticsRepository,
            UnconfinedTestDispatcher()
        )
    }
    
    @Test
    fun `invoke plays song and updates play count`() = runTest {
        // Given
        val song = Song("1", "Test Song", "Artist", "Album", 180000)
        musicRepository.addSong(song)
        
        // When
        val result = useCase("1")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals(1, musicRepository.getPlayCount("1"))
        assertTrue(playbackRepository.isPlaying)
        assertTrue(analyticsRepository.wasTracked("1"))
    }
    
    @Test
    fun `invoke returns failure when song not found`() = runTest {
        // When
        val result = useCase("nonexistent")
        
        // Then
        assertTrue(result.isFailure)
        assertFalse(playbackRepository.isPlaying)
    }
    
    @Test
    fun `invoke returns failure when playback fails`() = runTest {
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
    
    @Test
    fun `invoke does not update play count when playback fails`() = runTest {
        // Given
        val song = Song("1", "Test Song", "Artist", "Album", 180000)
        musicRepository.addSong(song)
        playbackRepository.shouldFail = true
        
        // When
        useCase("1")
        
        // Then
        assertEquals(0, musicRepository.getPlayCount("1"))
    }
}
```

### Step 6: Test Repositories
**Goal**: Test repository implementations

**Actions**:
1. Test with fake data sources
2. Test data mapping
3. Test caching logic
4. Test error handling

**Implementation**:
```kotlin
// data/repository/MusicRepositoryImplTest.kt
@ExperimentalCoroutinesApi
class MusicRepositoryImplTest {
    
    private lateinit var repository: MusicRepositoryImpl
    private lateinit var localDataSource: FakeLocalMusicDataSource
    private lateinit var mediaStoreDataSource: FakeMediaStoreDataSource
    private val testDispatcher = UnconfinedTestDispatcher()
    
    @Before
    fun setup() {
        localDataSource = FakeLocalMusicDataSource()
        mediaStoreDataSource = FakeMediaStoreDataSource()
        
        repository = MusicRepositoryImpl(
            localDataSource,
            mediaStoreDataSource,
            testDispatcher
        )
    }
    
    @Test
    fun `observeSongs returns mapped domain models`() = runTest {
        // Given
        val entities = listOf(
            SongEntity("1", "Song 1", "Artist 1", "Album 1", null, 180000, "/path1", 0, 0, null, null, null)
        )
        localDataSource.setSongs(entities)
        
        // When
        repository.observeSongs().test {
            val songs = awaitItem()
            
            // Then
            assertEquals(1, songs.size)
            assertEquals("Song 1", songs[0].title)
            assertEquals("Artist 1", songs[0].artist)
        }
    }
    
    @Test
    fun `getSongById returns song when found`() = runTest {
        // Given
        val entity = SongEntity("1", "Song 1", "Artist", "Album", null, 180000, "/path", 0, 0, null, null, null)
        localDataSource.addSong(entity)
        
        // When
        val result = repository.getSongById("1")
        
        // Then
        assertTrue(result.isSuccess)
        assertEquals("Song 1", result.getOrNull()?.title)
    }
    
    @Test
    fun `getSongById returns failure when not found`() = runTest {
        // When
        val result = repository.getSongById("nonexistent")
        
        // Then
        assertTrue(result.isFailure)
    }
    
    @Test
    fun `incrementPlayCount updates local data source`() = runTest {
        // Given
        val entity = SongEntity("1", "Song", "Artist", "Album", null, 180000, "/path", 0, 0, null, null, null, playCount = 0)
        localDataSource.addSong(entity)
        
        // When
        repository.incrementPlayCount("1")
        
        // Then
        assertEquals(1, localDataSource.getPlayCount("1"))
    }
}
```

### Step 7: Test StateFlow and SharedFlow
**Goal**: Verify reactive state management

**Actions**:
1. Test StateFlow emissions
2. Test SharedFlow events
3. Test state updates
4. Use Turbine for flow testing

**Implementation**:
```kotlin
// presentation/player/PlayerViewModelTest.kt
@ExperimentalCoroutinesApi
class PlayerViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: PlayerViewModel
    private lateinit var playbackRepository: FakePlaybackRepository
    
    @Before
    fun setup() {
        playbackRepository = FakePlaybackRepository()
        viewModel = PlayerViewModel(playbackRepository)
    }
    
    @Test
    fun `playbackState updates when song plays`() = runTest {
        viewModel.playbackState.test {
            // Initial state
            assertEquals(PlaybackState.Idle, awaitItem())
            
            // Play song
            viewModel.playSong("1")
            
            // Verify state change
            assertEquals(PlaybackState.Playing, awaitItem())
        }
    }
    
    @Test
    fun `events are emitted once`() = runTest {
        // Collect events
        val events = mutableListOf<PlayerEvent>()
        val job = launch {
            viewModel.events.take(1).toList(events)
        }
        
        // Trigger event
        viewModel.showError("Test error")
        
        // Verify
        job.join()
        assertEquals(1, events.size)
        assertTrue(events[0] is PlayerEvent.Error)
    }
    
    @Test
    fun `multiple collectors receive same state`() = runTest {
        // Given
        val collector1 = mutableListOf<PlaybackState>()
        val collector2 = mutableListOf<PlaybackState>()
        
        // When
        val job1 = launch {
            viewModel.playbackState.take(2).toList(collector1)
        }
        val job2 = launch {
            viewModel.playbackState.take(2).toList(collector2)
        }
        
        viewModel.playSong("1")
        
        // Then
        job1.join()
        job2.join()
        assertEquals(collector1, collector2)
    }
}
```

### Step 8: Test with MockK
**Goal**: Use mocking when necessary

**Actions**:
1. Mock external dependencies
2. Verify method calls
3. Stub return values
4. Use relaxed mocks when appropriate

**Implementation**:
```kotlin
// Using MockK for complex dependencies
@ExperimentalCoroutinesApi
class PlaybackServiceTest {
    
    private lateinit var service: PlaybackService
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var mediaSession: MediaSession
    
    @Before
    fun setup() {
        exoPlayer = mockk(relaxed = true)
        mediaSession = mockk(relaxed = true)
        service = PlaybackService(exoPlayer, mediaSession)
    }
    
    @Test
    fun `play calls exoPlayer play`() {
        // When
        service.play()
        
        // Then
        verify { exoPlayer.play() }
    }
    
    @Test
    fun `pause calls exoPlayer pause`() {
        // When
        service.pause()
        
        // Then
        verify { exoPlayer.pause() }
    }
    
    @Test
    fun `seekTo calls exoPlayer seekTo with correct position`() {
        // Given
        val position = 30000L
        
        // When
        service.seekTo(position)
        
        // Then
        verify { exoPlayer.seekTo(position) }
    }
    
    @Test
    fun `getCurrentPosition returns exoPlayer position`() {
        // Given
        every { exoPlayer.currentPosition } returns 15000L
        
        // When
        val position = service.getCurrentPosition()
        
        // Then
        assertEquals(15000L, position)
    }
}

// Verify call order
@Test
fun `operations happen in correct order`() {
    // Given
    val operations = mutableListOf<String>()
    every { exoPlayer.prepare() } answers { operations.add("prepare") }
    every { exoPlayer.play() } answers { operations.add("play") }
    
    // When
    service.prepareAndPlay()
    
    // Then
    verifyOrder {
        exoPlayer.prepare()
        exoPlayer.play()
    }
}

// Capture arguments
@Test
fun `setMediaItem receives correct media item`() {
    // Given
    val slot = slot<MediaItem>()
    
    // When
    service.loadSong("song_uri")
    
    // Then
    verify { exoPlayer.setMediaItem(capture(slot)) }
    assertEquals("song_uri", slot.captured.localConfiguration?.uri.toString())
}
```

### Step 9: Measure Test Coverage
**Goal**: Ensure adequate test coverage

**Actions**:
1. Run tests with coverage
2. Identify untested code
3. Add missing tests
4. Set coverage goals

**Implementation**:
```kotlin
// build.gradle.kts
android {
    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }
}

// Run tests with coverage
// ./gradlew testDebugUnitTestCoverage

// Coverage goals (example)
// - ViewModels: 90%+
// - Use Cases: 95%+
// - Repositories: 85%+
// - Utilities: 80%+
```

## Addons

### Parameterized Tests
```kotlin
@RunWith(Parameterized::class)
class SongValidationTest(
    private val input: String,
    private val expected: Boolean
) {
    
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: isValid({0}) = {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("Valid Song", true),
                arrayOf("", false),
                arrayOf("   ", false),
                arrayOf("A", true)
            )
        }
    }
    
    @Test
    fun `validate song title`() {
        assertEquals(expected, SongValidator.isValidTitle(input))
    }
}
```

### Test Fixtures
```kotlin
// test/fixture/SongFixtures.kt
object SongFixtures {
    fun createSong(
        id: String = "1",
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "Test Album",
        duration: Long = 180000
    ) = Song(id, title, artist, album, duration)
    
    fun createSongs(count: Int): List<Song> {
        return (1..count).map { i ->
            createSong(
                id = i.toString(),
                title = "Song $i",
                artist = "Artist $i"
            )
        }
    }
}

// Usage
@Test
fun `test with fixture`() {
    val song = SongFixtures.createSong(title = "Custom Title")
    // Test with song
}
```

### Custom Matchers
```kotlin
// test/matcher/SongMatchers.kt
fun assertSongEquals(expected: Song, actual: Song) {
    assertEquals(expected.id, actual.id)
    assertEquals(expected.title, actual.title)
    assertEquals(expected.artist, actual.artist)
    assertEquals(expected.album, actual.album)
    assertEquals(expected.duration, actual.duration)
}

fun assertSongsEqual(expected: List<Song>, actual: List<Song>) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (exp, act) ->
        assertSongEquals(exp, act)
    }
}
```

## Related Files
- `test/` - Unit test files
- `test/fake/` - Fake implementations
- `test/util/` - Test utilities
- `test/fixture/` - Test fixtures

## Notes
- Prefer fakes over mocks for repositories
- Use UnconfinedTestDispatcher for immediate execution
- Test one thing per test
- Use descriptive test names
- Follow AAA pattern (Arrange, Act, Assert)
- Test edge cases and error scenarios
- Use Turbine for Flow testing
- Keep tests fast and independent
- Mock external dependencies only
- Aim for high coverage on business logic

## Common Patterns

### Testing Coroutines with Delay
```kotlin
@Test
fun `test with delay`() = runTest {
    // Given
    val viewModel = MyViewModel()
    
    // When
    viewModel.startOperation()
    advanceTimeBy(1000) // Advance virtual time
    
    // Then
    assertEquals(OperationState.InProgress, viewModel.state.value)
    
    advanceUntilIdle() // Complete all pending coroutines
    assertEquals(OperationState.Completed, viewModel.state.value)
}
```

### Testing Error Recovery
```kotlin
@Test
fun `retries operation on failure`() = runTest {
    // Given
    var attempts = 0
    repository.shouldReturnError = true
    
    // When
    val result = useCase.invoke()
    
    // Then - verify retry logic
    verify(exactly = 3) { repository.performOperation() }
}
```

### Testing State Restoration
```kotlin
@Test
fun `restores state from SavedStateHandle`() {
    // Given
    val savedState = SavedStateHandle(mapOf("song_id" to "123"))
    
    // When
    val viewModel = PlayerViewModel(savedState, repository)
    
    // Then
    assertEquals("123", viewModel.currentSongId.value)
}