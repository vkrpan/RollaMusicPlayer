---
name: add-new-screen
description: "A comprehensive workflow for adding a new screen to RollaMusicPlayer using Jetpack Compose and MVVM architecture."
---

# Skill: Add New Screen

## Overview
A comprehensive workflow for adding a new screen to RollaMusicPlayer using Jetpack Compose and MVVM architecture.

## When to Use
- Adding a new feature that requires a dedicated screen
- Creating a new section in the app
- Implementing a new user flow

## Prerequisites
- Project structure is set up
- Navigation component is configured
- Base architecture is in place
- Theme and styling are defined

## Workflow Steps

### Step 1: Define Requirements
**Goal**: Understand what the screen needs to do

**Actions**:
1. Identify the screen's purpose
2. List required data to display
3. Define user interactions
4. Determine navigation requirements
5. Identify dependencies on other features

**Questions to Answer**:
- What is the primary function of this screen?
- What data needs to be displayed?
- What actions can users perform?
- How do users navigate to/from this screen?
- Does it need to persist state?
- **Does this feature work completely offline?** (Answer: Yes, always)
- **What local data sources does it use?** (Room, SharedPreferences, local files)
- **Are there any network dependencies?** (Answer: No, never)

**Output**: Requirements document or checklist

---

### Step 2: Design UI Layout
**Goal**: Create the visual structure

**Actions**:
1. Sketch the screen layout
2. Identify Material 3 components to use
3. Define the composable hierarchy
4. Plan responsive behavior
5. Consider accessibility requirements

**Components to Consider**:
- Scaffold with TopAppBar
- Content area (LazyColumn, Grid, etc.)
- FloatingActionButton if needed
- Bottom sheet or dialogs
- Loading and error states

**Output**: UI component structure plan

---

### Step 3: Define UI State
**Goal**: Model the screen's state

**Actions**:
1. Create a data class for UI state
2. Include loading, success, and error states
3. Define user input state if applicable
4. Plan state updates and transitions

**Example Structure**:
```
data class ScreenNameUiState(
    val isLoading: Boolean = false,
    val data: List<Item> = emptyList(),
    val error: String? = null,
    val selectedItem: Item? = null
)
```

**Output**: UI state data class definition

---

### Step 4: Create ViewModel
**Goal**: Implement business logic and state management

**Actions**:
1. Create ViewModel class
2. Inject required dependencies (repositories, use cases)
3. Define StateFlow for UI state
4. Implement methods for user actions
5. Handle loading and error states
6. Add proper coroutine scope management

**Key Considerations**:
- Use `viewModelScope` for coroutines
- Emit state updates via StateFlow
- Handle errors gracefully
- Don't hold Activity/Fragment references
- Make it testable

**Output**: ViewModel implementation

---

### Step 5: Implement Composable Screen
**Goal**: Build the UI with Jetpack Compose

**Actions**:
1. Create main screen composable
2. Collect state from ViewModel
3. Implement UI based on state
4. Add user interaction handlers
5. Implement loading and error states
6. Add content descriptions for accessibility

**Structure**:
```
@Composable
fun ScreenName(
    viewModel: ScreenNameViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    ScreenNameContent(
        uiState = uiState,
        onAction = viewModel::handleAction,
        onNavigateBack = onNavigateBack
    )
}

@Composable
private fun ScreenNameContent(
    uiState: ScreenNameUiState,
    onAction: (Action) -> Unit,
    onNavigateBack: () -> Unit
) {
    // UI implementation
}
```

**Output**: Screen composable implementation

---

### Step 6: Add Navigation
**Goal**: Integrate screen into navigation graph

**Actions**:
1. Define navigation route
2. Add route to navigation graph
3. Implement navigation arguments if needed
4. Add navigation calls from other screens
5. Handle back navigation

**Navigation Pattern**:
```
// Define route
const val SCREEN_ROUTE = "screen_name"

// Add to navigation graph
composable(SCREEN_ROUTE) {
    ScreenName(
        onNavigateBack = { navController.navigateUp() }
    )
}

// Navigate to screen
navController.navigate(SCREEN_ROUTE)
```

**Output**: Navigation integration complete

---

### Step 7: Implement Data Layer (if needed)
**Goal**: Add data operations if screen needs new data

**Actions**:
1. Create/update repository methods
2. Add database entities if needed
3. Implement data transformations
4. Add caching logic if applicable
5. Handle error cases

**Only if**:
- Screen needs new data not available elsewhere
- New database tables are required
- New API endpoints need to be called

**Output**: Data layer implementation

---

### Step 8: Add Use Cases (if needed)
**Goal**: Implement business logic

**Actions**:
1. Create use case classes
2. Implement business rules
3. Coordinate between repositories
4. Add validation logic
5. Handle complex operations

**Only if**:
- Complex business logic is required
- Multiple repositories need coordination
- Validation rules are complex

**Output**: Use case implementations

---

### Step 9: Write Tests
**Goal**: Ensure screen works correctly

**Actions**:
1. Write ViewModel unit tests
2. Test state transitions
3. Test user action handling
4. Test error scenarios
5. Write UI tests for critical flows
6. Test navigation

**Test Coverage**:
- ViewModel state management
- User interactions
- Error handling
- Loading states
- Navigation flows

**Output**: Comprehensive test suite

---

### Step 10: Review and Polish
**Goal**: Ensure quality and consistency

**Actions**:
1. Run code review checklist
2. Test on different screen sizes
3. Test dark mode
4. Verify accessibility
5. Check performance
6. Update documentation

**Review Checklist**:
- [ ] Follows MVVM architecture
- [ ] Material 3 guidelines followed
- [ ] Accessibility implemented
- [ ] Error handling complete
- [ ] Loading states shown (for local operations only)
- [ ] Navigation works correctly
- [ ] Tests pass
- [ ] No memory leaks
- [ ] Performance is acceptable
- [ ] Code is documented
- [ ] **Works completely offline/airplane mode**
- [ ] **No network dependencies**
- [ ] **Uses only local data sources**
- [ ] **No internet permission required**

**Output**: Production-ready screen

---

## Example: Adding a Playlist Detail Screen

### 1. Requirements
- Display playlist name and songs
- Allow adding/removing songs
- Support reordering songs
- Show total duration
- Navigate to player when song tapped

### 2. UI State
```
data class PlaylistDetailUiState(
    val isLoading: Boolean = false,
    val playlist: Playlist? = null,
    val songs: List<Song> = emptyList(),
    val totalDuration: Duration = Duration.ZERO,
    val error: String? = null
)
```

### 3. ViewModel
```
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val getPlaylistUseCase: GetPlaylistUseCase,
    private val updatePlaylistUseCase: UpdatePlaylistUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val playlistId: String = 
        savedStateHandle.get<String>("playlistId")!!
    
    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()
    
    init {
        loadPlaylist()
    }
    
    private fun loadPlaylist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            getPlaylistUseCase(playlistId)
                .onSuccess { playlist ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playlist = playlist,
                            songs = playlist.songs,
                            totalDuration = calculateDuration(playlist.songs)
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
    
    fun removeSong(song: Song) {
        // Implementation
    }
    
    fun reorderSongs(from: Int, to: Int) {
        // Implementation
    }
}
```

### 4. Composable
```
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (Song) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.playlist?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingState()
            uiState.error != null -> ErrorState(uiState.error!!)
            else -> PlaylistContent(
                songs = uiState.songs,
                onSongClick = onNavigateToPlayer,
                onRemoveSong = viewModel::removeSong
            )
        }
    }
}
```

### 5. Navigation
```
// In navigation graph
composable(
    route = "playlist/{playlistId}",
    arguments = listOf(
        navArgument("playlistId") { type = NavType.StringType }
    )
) {
    PlaylistDetailScreen(
        onNavigateBack = { navController.navigateUp() },
        onNavigateToPlayer = { song ->
            navController.navigate("player/${song.id}")
        }
    )
}
```

## Common Pitfalls

### Pitfall 1: Business Logic in Composables
**Problem**: Putting business logic directly in composables
**Solution**: Keep composables focused on UI, move logic to ViewModel

### Pitfall 2: Not Handling Loading States
**Problem**: No feedback while data loads
**Solution**: Always show loading indicators

### Pitfall 3: Ignoring Error States
**Problem**: Crashes or blank screens on errors
**Solution**: Implement proper error handling and display

### Pitfall 4: Memory Leaks
**Problem**: Holding references to Activity/Fragment
**Solution**: Use Application Context, cancel coroutines properly

### Pitfall 5: Poor State Management
**Problem**: State scattered across multiple places
**Solution**: Single source of truth in ViewModel

## Success Criteria

Screen is complete when:
- [ ] All requirements are met
- [ ] UI follows Material Design 3
- [ ] MVVM architecture is followed
- [ ] Navigation works correctly
- [ ] Loading states are shown (local operations only)
- [ ] Errors are handled gracefully
- [ ] Tests are written and passing
- [ ] Accessibility is implemented
- [ ] Performance is acceptable
- [ ] Code review is complete
- [ ] **Works in airplane mode**
- [ ] **No network dependencies**
- [ ] **Privacy requirements met**

## Related Skills
- [Debug Playback Issue](../debug-playback-issue/SKILL.md)
- [Release Build](../release-build/SKILL.md)

## Related Agents
- [UI Builder](../../agents/ui-builder.md) - For UI implementation
- [Code Reviewer](../../agents/code-reviewer.md) - For code review
- [Test Writer](../../agents/test-writer.md) - For writing tests