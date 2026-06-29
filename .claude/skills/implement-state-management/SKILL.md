---
name: implement-state-management
description: "Comprehensive guide for implementing robust state management in Android applications using StateFlow, sealed classes, one-time events, and proper state handling patterns for Jetpack Compose."
---

# Skill: Implement State Management Patterns

## Overview
Comprehensive guide for implementing robust state management in Android applications using StateFlow, sealed classes, one-time events, and proper state handling patterns for Jetpack Compose.

## When to Use
- Setting up ViewModel state management
- Handling UI state updates
- Implementing one-time events
- Managing loading/error/success states
- Handling complex state updates
- Implementing state restoration
- Testing state changes

## Prerequisites
- Understanding of Kotlin coroutines and Flow
- Familiarity with ViewModel
- Jetpack Compose knowledge
- Understanding of reactive programming

## Workflow Steps

### Step 1: Define UI State with Sealed Classes
**Goal**: Create type-safe UI state representations

**Actions**:
1. Create sealed interface/class for UI states
2. Define all possible states
3. Include data in state classes
4. Use sealed classes for exhaustive when expressions

**Implementation**:
```kotlin
// presentation/library/LibraryUiState.kt
sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data object Empty : LibraryUiState
    data class Success(
        val songs: List<Song>,
        val selectedFilter: SongFilter = SongFilter.All,
        val sortOrder: SortOrder = SortOrder.TitleAsc
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

// presentation/player/PlayerUiState.kt
sealed interface PlayerUiState {
    data object Idle : PlayerUiState
    
    data class Playing(
        val song: Song,
        val progress: Float,
        val currentPosition: Long,
        val duration: Long,
        val isShuffleEnabled: Boolean,
        val repeatMode: RepeatMode
    ) : PlayerUiState
    
    data class Paused(
        val song: Song,
        val progress: Float,
        val currentPosition: Long,
        val duration: Long
    ) : PlayerUiState
    
    data class Error(val message: String) : PlayerUiState
}

// Complex state with nested data
sealed interface PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState
    
    data class Success(
        val playlist: Playlist,
        val songs: List<Song>,
        val isEditing: Boolean = false,
        val selectedSongs: Set<String> = emptySet()
    ) : PlaylistDetailUiState
    
    data class Error(val message: String) : PlaylistDetailUiState
}
```

### Step 2: Implement StateFlow in ViewModel
**Goal**: Expose UI state reactively

**Actions**:
1. Create private MutableStateFlow
2. Expose public StateFlow
3. Update state immutably
4. Use stateIn for Flow transformations

**Implementation**:
```kotlin
// presentation/library/LibraryViewModel.kt
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase,
    private val searchSongsUseCase: SearchSongsUseCase
) : ViewModel() {
    
    // Private mutable state
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    
    // Public immutable state
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // Alternative: Using stateIn for Flow transformations
    val songs: StateFlow<List<Song>> = getSongsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    init {
        loadSongs()
    }
    
    private fun loadSongs() {
        viewModelScope.launch {
            getSongsUseCase()
                .catch { error ->
                    _uiState.value = LibraryUiState.Error(
                        error.message ?: "Unknown error"
                    )
                }
                .collect { songs ->
                    _uiState.value = if (songs.isEmpty()) {
                        LibraryUiState.Empty
                    } else {
                        LibraryUiState.Success(songs)
                    }
                }
        }
    }
    
    // Update state immutably
    fun onFilterChanged(filter: SongFilter) {
        val currentState = _uiState.value
        if (currentState is LibraryUiState.Success) {
            _uiState.value = currentState.copy(selectedFilter = filter)
        }
    }
}
```

### Step 3: Handle One-Time Events
**Goal**: Implement events that should be consumed once

**Actions**:
1. Create event sealed class
2. Use SharedFlow for events
3. Emit events from ViewModel
4. Collect and consume in UI

**Implementation**:
```kotlin
// presentation/library/LibraryEvent.kt
sealed interface LibraryEvent {
    data class ShowSnackbar(val message: String) : LibraryEvent
    data class NavigateToPlayer(val songId: String) : LibraryEvent
    data class NavigateToAlbum(val albumId: String) : LibraryEvent
    data object NavigateBack : LibraryEvent
}

// presentation/library/LibraryViewModel.kt
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase,
    private val playSongUseCase: PlaySongUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    // Events channel - replay = 0 means events are not replayed
    private val _events = MutableSharedFlow<LibraryEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()
    
    fun onSongClicked(songId: String) {
        viewModelScope.launch {
            playSongUseCase(songId)
                .onSuccess {
                    _events.emit(LibraryEvent.NavigateToPlayer(songId))
                }
                .onFailure { error ->
                    _events.emit(
                        LibraryEvent.ShowSnackbar(
                            error.message ?: "Failed to play song"
                        )
                    )
                }
        }
    }
    
    fun onAlbumClicked(albumId: String) {
        viewModelScope.launch {
            _events.emit(LibraryEvent.NavigateToAlbum(albumId))
        }
    }
}

// presentation/library/LibraryScreen.kt
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToAlbum: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Collect events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is LibraryEvent.NavigateToPlayer -> {
                    onNavigateToPlayer(event.songId)
                }
                is LibraryEvent.NavigateToAlbum -> {
                    onNavigateToAlbum(event.albumId)
                }
                LibraryEvent.NavigateBack -> {
                    // Handle back navigation
                }
            }
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        // UI content
    }
}
```

### Step 4: Implement Loading/Error/Success Pattern
**Goal**: Handle async operations with proper states

**Actions**:
1. Define state for each phase
2. Show loading indicators
3. Display errors with retry
4. Show success content

**Implementation**:
```kotlin
// Generic result state
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>
}

// ViewModel implementation
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val getPlaylistUseCase: GetPlaylistUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val playlistId: Long = savedStateHandle["playlistId"] ?: 0L
    
    private val _uiState = MutableStateFlow<UiState<PlaylistDetail>>(UiState.Loading)
    val uiState: StateFlow<UiState<PlaylistDetail>> = _uiState.asStateFlow()
    
    init {
        loadPlaylist()
    }
    
    private fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            
            getPlaylistUseCase(playlistId)
                .onSuccess { playlist ->
                    _uiState.value = UiState.Success(playlist)
                }
                .onFailure { error ->
                    _uiState.value = UiState.Error(
                        message = error.message ?: "Failed to load playlist",
                        throwable = error
                    )
                }
        }
    }
    
    fun retry() {
        loadPlaylist()
    }
}

// UI implementation
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    when (val state = uiState) {
        is UiState.Loading -> {
            LoadingContent()
        }
        is UiState.Success -> {
            PlaylistContent(playlist = state.data)
        }
        is UiState.Error -> {
            ErrorContent(
                message = state.message,
                onRetry = { viewModel.retry() }
            )
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}
```

### Step 5: Handle Complex State Updates
**Goal**: Manage state with multiple properties

**Actions**:
1. Use data classes for state
2. Update state immutably with copy
3. Combine multiple flows
4. Handle partial updates

**Implementation**:
```kotlin
// Complex state
data class EqualizerUiState(
    val isEnabled: Boolean = false,
    val currentPreset: EqualizerPreset? = null,
    val presets: List<EqualizerPreset> = emptyList(),
    val bands: List<BandState> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

data class BandState(
    val frequency: Int,
    val gain: Float,
    val isAdjusting: Boolean = false
)

@HiltViewModel
class EqualizerViewModel @Inject constructor(
    private val getPresetsUseCase: GetEqualizerPresetsUseCase,
    private val applyPresetUseCase: ApplyEqualizerPresetUseCase,
    private val updateBandUseCase: UpdateBandUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()
    
    init {
        loadPresets()
    }
    
    private fun loadPresets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            getPresetsUseCase()
                .onSuccess { presets ->
                    _uiState.update { 
                        it.copy(
                            presets = presets,
                            isLoading = false,
                            error = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message
                        )
                    }
                }
        }
    }
    
    fun onPresetSelected(preset: EqualizerPreset) {
        viewModelScope.launch {
            applyPresetUseCase(preset)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            currentPreset = preset,
                            bands = preset.bands.map { band ->
                                BandState(band.frequency, band.gain)
                            }
                        )
                    }
                }
        }
    }
    
    fun onBandAdjusting(bandIndex: Int, isAdjusting: Boolean) {
        _uiState.update { state ->
            state.copy(
                bands = state.bands.mapIndexed { index, band ->
                    if (index == bandIndex) {
                        band.copy(isAdjusting = isAdjusting)
                    } else {
                        band
                    }
                }
            )
        }
    }
    
    fun onBandGainChanged(bandIndex: Int, gain: Float) {
        _uiState.update { state ->
            state.copy(
                bands = state.bands.mapIndexed { index, band ->
                    if (index == bandIndex) {
                        band.copy(gain = gain)
                    } else {
                        band
                    }
                }
            )
        }
        
        // Debounce and apply changes
        viewModelScope.launch {
            delay(100)
            updateBandUseCase(bandIndex, gain)
        }
    }
    
    fun toggleEqualizer() {
        _uiState.update { it.copy(isEnabled = !it.isEnabled) }
    }
}

// Extension function for cleaner updates
fun <T> MutableStateFlow<T>.update(function: (T) -> T) {
    value = function(value)
}
```

### Step 6: Implement State Restoration
**Goal**: Preserve state across configuration changes

**Actions**:
1. Use SavedStateHandle
2. Save state to handle
3. Restore state on recreation
4. Handle process death

**Implementation**:
```kotlin
// ViewModel with state restoration
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchSongsUseCase: SearchSongsUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    // Restore search query from saved state
    private val _searchQuery = MutableStateFlow(
        savedStateHandle.get<String>(KEY_SEARCH_QUERY) ?: ""
    )
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // Restore selected filter
    private val _selectedFilter = MutableStateFlow(
        savedStateHandle.get<String>(KEY_FILTER)?.let { 
            SongFilter.valueOf(it) 
        } ?: SongFilter.All
    )
    val selectedFilter: StateFlow<SongFilter> = _selectedFilter.asStateFlow()
    
    val searchResults: StateFlow<List<Song>> = searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) {
                flowOf(emptyList())
            } else {
                searchSongsUseCase(query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        savedStateHandle[KEY_SEARCH_QUERY] = query
    }
    
    fun onFilterChanged(filter: SongFilter) {
        _selectedFilter.value = filter
        savedStateHandle[KEY_FILTER] = filter.name
    }
    
    companion object {
        private const val KEY_SEARCH_QUERY = "search_query"
        private const val KEY_FILTER = "selected_filter"
    }
}

// Compose state restoration
@Composable
fun SearchScreen(
    viewModel: SearchViewModel = hiltViewModel()
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    
    // Restore scroll position
    val listState = rememberLazyListState()
    
    Column {
        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::onSearchQueryChanged
        )
        
        LazyColumn(state = listState) {
            items(searchResults) { song ->
                SongItem(song = song)
            }
        }
    }
}
```

### Step 7: Combine Multiple State Sources
**Goal**: Merge state from different sources

**Actions**:
1. Use combine for multiple flows
2. Create derived state
3. Handle dependencies between states
4. Optimize recomposition

**Implementation**:
```kotlin
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {
    
    // Combine multiple state sources
    val uiState: StateFlow<PlayerUiState> = combine(
        playbackRepository.currentSong,
        playbackRepository.playbackState,
        playbackRepository.progress,
        settingsRepository.shuffleEnabled,
        settingsRepository.repeatMode
    ) { song, playbackState, progress, shuffle, repeat ->
        when {
            song == null -> PlayerUiState.Idle
            playbackState == PlaybackState.PLAYING -> PlayerUiState.Playing(
                song = song,
                progress = progress,
                currentPosition = (song.duration * progress).toLong(),
                duration = song.duration,
                isShuffleEnabled = shuffle,
                repeatMode = repeat
            )
            playbackState == PlaybackState.PAUSED -> PlayerUiState.Paused(
                song = song,
                progress = progress,
                currentPosition = (song.duration * progress).toLong(),
                duration = song.duration
            )
            else -> PlayerUiState.Idle
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState.Idle
    )
    
    // Derived state
    val isPlaying: StateFlow<Boolean> = uiState
        .map { it is PlayerUiState.Playing }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
}
```

### Step 8: Handle Side Effects
**Goal**: Manage effects that shouldn't be part of state

**Actions**:
1. Use LaunchedEffect for one-time effects
2. Use DisposableEffect for cleanup
3. Use SideEffect for non-suspending effects
4. Separate state from effects

**Implementation**:
```kotlin
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    // Keep screen on while playing
    DisposableEffect(uiState) {
        val window = (context as? Activity)?.window
        if (uiState is PlayerUiState.Playing) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    // Update media session metadata
    LaunchedEffect(uiState) {
        if (uiState is PlayerUiState.Playing) {
            val song = (uiState as PlayerUiState.Playing).song
            viewModel.updateMediaSession(song)
        }
    }
    
    // Analytics tracking
    SideEffect {
        if (uiState is PlayerUiState.Playing) {
            // Non-suspending analytics call
            Analytics.trackScreenView("player")
        }
    }
    
    // UI content
}
```

### Step 9: Test State Management
**Goal**: Verify state updates work correctly

**Actions**:
1. Test initial state
2. Test state transitions
3. Test event emissions
4. Test state restoration

**Implementation**:
```kotlin
@ExperimentalCoroutinesApi
class LibraryViewModelTest {
    
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    
    private lateinit var viewModel: LibraryViewModel
    private lateinit var repository: FakeMusicRepository
    
    @Before
    fun setup() {
        repository = FakeMusicRepository()
        viewModel = LibraryViewModel(
            GetSongsUseCase(repository, UnconfinedTestDispatcher())
        )
    }
    
    @Test
    fun `initial state is loading`() {
        assertEquals(LibraryUiState.Loading, viewModel.uiState.value)
    }
    
    @Test
    fun `state updates to success when songs loaded`() = runTest {
        // Given
        val songs = listOf(Song("1", "Song", "Artist", "Album", 180000))
        
        viewModel.uiState.test {
            // Initial loading state
            assertEquals(LibraryUiState.Loading, awaitItem())
            
            // Load songs
            repository.setSongs(songs)
            
            // Success state
            val successState = awaitItem() as LibraryUiState.Success
            assertEquals(1, successState.songs.size)
        }
    }
    
    @Test
    fun `events are emitted once`() = runTest {
        viewModel.events.test {
            viewModel.onSongClicked("1")
            
            val event = awaitItem()
            assertTrue(event is LibraryEvent.ShowSnackbar)
            
            // No more events
            expectNoEvents()
        }
    }
    
    @Test
    fun `state is restored from SavedStateHandle`() {
        // Given
        val savedState = SavedStateHandle(mapOf("search_query" to "Rock"))
        val viewModel = SearchViewModel(
            searchSongsUseCase = mockk(),
            savedStateHandle = savedState
        )
        
        // Then
        assertEquals("Rock", viewModel.searchQuery.value)
    }
}
```

## Addons

### State Reducer Pattern
```kotlin
// Define actions
sealed interface LibraryAction {
    data class LoadSongs(val songs: List<Song>) : LibraryAction
    data class UpdateFilter(val filter: SongFilter) : LibraryAction
    data class UpdateSort(val sort: SortOrder) : LibraryAction
    data class ShowError(val message: String) : LibraryAction
}

// Reducer function
fun reduceLibraryState(
    currentState: LibraryUiState,
    action: LibraryAction
): LibraryUiState {
    return when (action) {
        is LibraryAction.LoadSongs -> {
            if (action.songs.isEmpty()) {
                LibraryUiState.Empty
            } else {
                LibraryUiState.Success(action.songs)
            }
        }
        is LibraryAction.UpdateFilter -> {
            if (currentState is LibraryUiState.Success) {
                currentState.copy(selectedFilter = action.filter)
            } else {
                currentState
            }
        }
        is LibraryAction.UpdateSort -> {
            if (currentState is LibraryUiState.Success) {
                currentState.copy(sortOrder = action.sort)
            } else {
                currentState
            }
        }
        is LibraryAction.ShowError -> {
            LibraryUiState.Error(action.message)
        }
    }
}

// ViewModel with reducer
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    private fun dispatch(action: LibraryAction) {
        _uiState.value = reduceLibraryState(_uiState.value, action)
    }
    
    fun onFilterChanged(filter: SongFilter) {
        dispatch(LibraryAction.UpdateFilter(filter))
    }
}
```

### MVI Pattern
```kotlin
// Model-View-Intent pattern
sealed interface LibraryIntent {
    data object LoadSongs : LibraryIntent
    data class SearchSongs(val query: String) : LibraryIntent
    data class SelectSong(val songId: String) : LibraryIntent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    fun processIntent(intent: LibraryIntent) {
        when (intent) {
            is LibraryIntent.LoadSongs -> loadSongs()
            is LibraryIntent.SearchSongs -> searchSongs(intent.query)
            is LibraryIntent.SelectSong -> selectSong(intent.songId)
        }
    }
    
    private fun loadSongs() {
        // Implementation
    }
}
```

## Related Files
- `presentation/*/UiState.kt` - State definitions
- `presentation/*/Event.kt` - Event definitions
- `presentation/*/ViewModel.kt` - State management
- `presentation/*/Screen.kt` - State consumption

## Notes
- Use sealed classes for type-safe states
- Prefer StateFlow over LiveData in Compose
- Use SharedFlow for one-time events
- Update state immutably
- Keep state minimal and derived
- Use SavedStateHandle for restoration
- Test state transitions thoroughly
- Separate state from events
- Use combine for multiple sources
- Handle loading/error/success consistently

## Common Patterns

### Pagination State
```kotlin
data class PaginatedUiState<T>(
    val items: List<T> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true,
    val page: Int = 0
)
```

### Form State
```kotlin
data class FormUiState(
    val title: String = "",
    val titleError: String? = null,
    val description: String = "",
    val descriptionError: String? = null,
    val isValid: Boolean = false,
    val isSubmitting: Boolean = false
)
```

### Selection State
```kotlin
data class SelectionUiState<T>(
    val items: List<T> = emptyList(),
    val selectedItems: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false
) {
    val selectedCount: Int get() = selectedItems.size
    val hasSelection: Boolean get() = selectedItems.isNotEmpty()
}