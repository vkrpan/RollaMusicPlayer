# Jetpack Compose Conventions

## Overview
Best practices and conventions for Jetpack Compose UI development in RollaMusicPlayer, following Material Design 3 guidelines.

## Composable Naming

### Screen Composables
- Use noun describing the screen
- Suffix with "Screen" for top-level screens
- PascalCase naming

```kotlin
// Good
@Composable
fun PlayerScreen()

@Composable
fun LibraryScreen()

@Composable
fun PlaylistDetailScreen()

// Bad
@Composable
fun player()

@Composable
fun ShowLibrary()
```

### Component Composables
- Use descriptive noun
- No "Composable" suffix
- PascalCase naming

```kotlin
// Good
@Composable
fun SongListItem()

@Composable
fun AlbumCard()

@Composable
fun PlaybackControls()

// Bad
@Composable
fun SongListItemComposable()

@Composable
fun albumcard()
```

### Preview Composables
- Prefix with "Preview"
- Describe what's being previewed

```kotlin
// Good
@Preview
@Composable
fun PreviewSongListItem() {
    SongListItem(song = sampleSong)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun PreviewSongListItemDark() {
    SongListItem(song = sampleSong)
}

// Bad
@Preview
@Composable
fun SongListItemPreview() { }
```

## Composable Structure

### State Hoisting Pattern
- Separate stateful and stateless composables
- Hoist state to appropriate level
- Pass state and events as parameters

```kotlin
// Good - Stateful wrapper
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    PlayerScreenContent(
        uiState = uiState,
        onPlayPause = viewModel::togglePlayback,
        onSkipNext = viewModel::skipNext
    )
}

// Good - Stateless content
@Composable
private fun PlayerScreenContent(
    uiState: PlayerUiState,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit
) {
    // UI implementation
}

// Bad - State mixed with UI
@Composable
fun PlayerScreen() {
    val viewModel: PlayerViewModel = hiltViewModel()
    val song by viewModel.currentSong.collectAsState()
    
    // UI directly accessing ViewModel
}
```

### Parameter Order
1. Required parameters
2. Modifier (always last required parameter)
3. Optional parameters with defaults
4. Lambda parameters (last)

```kotlin
// Good
@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    showArtwork: Boolean = true,
    onClick: () -> Unit = {}
) {
    // Implementation
}

// Bad
@Composable
fun SongListItem(
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    song: Song,
    showArtwork: Boolean = true
) {
    // Implementation
}
```

## State Management

### Remember
- Use `remember` for computed values
- Use `rememberSaveable` for configuration changes
- Use `derivedStateOf` for derived state

```kotlin
// Good
@Composable
fun SongList(songs: List<Song>) {
    val listState = rememberLazyListState()
    val isScrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    
    // Use isScrolled
}

// Bad
@Composable
fun SongList(songs: List<Song>) {
    var isScrolled by remember { mutableStateOf(false) }
    
    LaunchedEffect(listState.firstVisibleItemIndex) {
        isScrolled = listState.firstVisibleItemIndex > 0
    }
}
```

### Side Effects
- Use appropriate side effect
- `LaunchedEffect`: Coroutines
- `DisposableEffect`: Cleanup
- `SideEffect`: Non-Compose state

```kotlin
// Good - LaunchedEffect for coroutines
@Composable
fun PlayerScreen(songId: String) {
    LaunchedEffect(songId) {
        loadSong(songId)
    }
}

// Good - DisposableEffect for cleanup
@Composable
fun AudioVisualizer() {
    DisposableEffect(Unit) {
        val visualizer = createVisualizer()
        onDispose {
            visualizer.release()
        }
    }
}

// Bad - Side effect in composition
@Composable
fun PlayerScreen(songId: String) {
    loadSong(songId) // Don't do this!
}
```

### State Collection
- Use `collectAsStateWithLifecycle` for flows
- Avoid `collectAsState` in production
- Handle lifecycle properly

```kotlin
// Good
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // Use uiState
}

// Bad
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
}
```

## Modifier Usage

### Modifier Order
1. Size modifiers (fillMaxWidth, size, etc.)
2. Padding
3. Border/background
4. Interaction (clickable, etc.)
5. Semantics

```kotlin
// Good
Box(
    modifier = Modifier
        .fillMaxWidth()
        .height(200.dp)
        .padding(16.dp)
        .background(MaterialTheme.colorScheme.surface)
        .clickable { onClick() }
)

// Bad
Box(
    modifier = Modifier
        .clickable { onClick() }
        .padding(16.dp)
        .fillMaxWidth()
        .background(MaterialTheme.colorScheme.surface)
)
```

### Modifier Parameter
- Always provide Modifier parameter
- Default to `Modifier`
- Apply at root composable

```kotlin
// Good
@Composable
fun SongCard(
    song: Song,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        // Content
    }
}

// Bad
@Composable
fun SongCard(song: Song) {
    Card {
        // No way to customize from outside
    }
}
```

### Modifier Reuse
- Don't reuse modifier instances
- Create new modifier for each composable

```kotlin
// Good
val commonModifier = Modifier.padding(16.dp)

Column {
    Text("Title", modifier = commonModifier)
    Text("Subtitle", modifier = commonModifier)
}

// Bad
val sharedModifier = Modifier
    .padding(16.dp)
    .fillMaxWidth()

Column {
    Text("Title", modifier = sharedModifier)
    Text("Subtitle", modifier = sharedModifier) // Reusing same instance
}
```

## Lists and Performance

### LazyColumn/LazyRow
- Use for long lists
- Provide stable keys
- Use `items()` with key parameter

```kotlin
// Good
LazyColumn {
    items(
        items = songs,
        key = { song -> song.id }
    ) { song ->
        SongListItem(song = song)
    }
}

// Bad
LazyColumn {
    items(songs.size) { index ->
        SongListItem(song = songs[index])
    }
}
```

### Avoid Nested Scrolling
- Don't nest scrollable composables
- Use single scrollable container
- Use `Modifier.verticalScroll` for small lists

```kotlin
// Good
LazyColumn {
    item {
        Header()
    }
    items(songs) { song ->
        SongListItem(song)
    }
    item {
        Footer()
    }
}

// Bad
Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
    Header()
    LazyColumn { // Nested scrolling!
        items(songs) { song ->
            SongListItem(song)
        }
    }
    Footer()
}
```

### Recomposition Optimization
- Use `remember` for expensive calculations
- Use `derivedStateOf` for derived state
- Avoid lambda allocations in loops

```kotlin
// Good
@Composable
fun SongList(songs: List<Song>) {
    val sortedSongs = remember(songs) {
        songs.sortedBy { it.title }
    }
    
    LazyColumn {
        items(sortedSongs, key = { it.id }) { song ->
            SongListItem(song = song)
        }
    }
}

// Bad
@Composable
fun SongList(songs: List<Song>) {
    LazyColumn {
        items(songs.sortedBy { it.title }) { song -> // Sorts on every recomposition!
            SongListItem(song = song)
        }
    }
}
```

## Material Design 3

### Theme Usage
- Use MaterialTheme for colors, typography, shapes
- Don't hardcode colors or dimensions
- Support dark mode

```kotlin
// Good
Text(
    text = "Song Title",
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onSurface
)

Card(
    colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
) {
    // Content
}

// Bad
Text(
    text = "Song Title",
    fontSize = 16.sp,
    color = Color.Black
)

Card(
    colors = CardDefaults.cardColors(
        containerColor = Color(0xFFEEEEEE)
    )
) {
    // Content
}
```

### Component Usage
- Use Material 3 components
- Follow component guidelines
- Customize through parameters

```kotlin
// Good
Button(
    onClick = { play() },
    colors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary
    )
) {
    Icon(Icons.Default.PlayArrow, contentDescription = "Play")
    Spacer(Modifier.width(8.dp))
    Text("Play")
}

// Bad
Box(
    modifier = Modifier
        .background(Color.Blue)
        .clickable { play() }
        .padding(16.dp)
) {
    Text("Play", color = Color.White)
}
```

### Scaffold Pattern
- Use Scaffold for screen structure
- Include TopAppBar, BottomBar, FAB
- Handle insets properly

```kotlin
// Good
@Composable
fun PlayerScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = { navigateBack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { shuffle() }) {
                Icon(Icons.Default.Shuffle, "Shuffle")
            }
        }
    ) { paddingValues ->
        Content(modifier = Modifier.padding(paddingValues))
    }
}

// Bad
@Composable
fun PlayerScreen() {
    Column {
        // Manual top bar
        Row {
            Icon(Icons.Default.ArrowBack, "Back")
            Text("Now Playing")
        }
        // Content without proper padding
        Content()
    }
}
```

## Accessibility

### Content Descriptions
- Provide for all interactive elements
- Describe purpose, not appearance
- Use null for decorative elements

```kotlin
// Good
IconButton(onClick = { play() }) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Play song"
    )
}

Image(
    painter = painterResource(R.drawable.album_art),
    contentDescription = "Album artwork for ${album.title}",
    modifier = Modifier.size(200.dp)
)

// Decorative
Divider(
    modifier = Modifier.semantics { 
        contentDescription = null 
    }
)

// Bad
IconButton(onClick = { play() }) {
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Play arrow icon"
    )
}
```

### Semantic Properties
- Use for screen readers
- Merge semantics when appropriate
- Provide state descriptions

```kotlin
// Good
Row(
    modifier = Modifier
        .clickable { togglePlayback() }
        .semantics(mergeDescendants = true) {
            contentDescription = if (isPlaying) {
                "Pause ${song.title}"
            } else {
                "Play ${song.title}"
            }
            stateDescription = if (isPlaying) "Playing" else "Paused"
        }
) {
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = null // Merged into parent
    )
    Text(song.title)
}

// Bad
Row(modifier = Modifier.clickable { togglePlayback() }) {
    Icon(
        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
        contentDescription = "Play pause"
    )
    Text(song.title)
}
```

### Touch Targets
- Minimum 48dp for interactive elements
- Use `Modifier.minimumInteractiveComponentSize()`
- Provide adequate spacing

```kotlin
// Good
IconButton(
    onClick = { skip() },
    modifier = Modifier.size(48.dp)
) {
    Icon(Icons.Default.SkipNext, "Next")
}

// Bad
Icon(
    imageVector = Icons.Default.SkipNext,
    contentDescription = "Next",
    modifier = Modifier
        .size(24.dp)
        .clickable { skip() }
)
```

## Testing

### Test Tags
- Add for testable elements
- Use descriptive names
- Use constants for tags

```kotlin
// Good
object TestTags {
    const val PLAY_BUTTON = "play_button"
    const val SONG_LIST = "song_list"
    const val SEARCH_FIELD = "search_field"
}

@Composable
fun PlayerControls() {
    IconButton(
        onClick = { play() },
        modifier = Modifier.testTag(TestTags.PLAY_BUTTON)
    ) {
        Icon(Icons.Default.PlayArrow, "Play")
    }
}

// Bad
@Composable
fun PlayerControls() {
    IconButton(
        onClick = { play() },
        modifier = Modifier.testTag("button1")
    ) {
        Icon(Icons.Default.PlayArrow, "Play")
    }
}
```

### Preview Annotations
- Provide multiple previews
- Test different states
- Include dark mode previews

```kotlin
// Good
@Preview(name = "Light Mode")
@Preview(name = "Dark Mode", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large Font", fontScale = 1.5f)
@Composable
fun PreviewSongListItem() {
    RollaMusicPlayerTheme {
        SongListItem(
            song = Song(
                id = "1",
                title = "Sample Song",
                artist = "Sample Artist",
                duration = 180000
            )
        )
    }
}

// Bad
@Preview
@Composable
fun Preview() {
    SongListItem(song = Song("1", "Song", "Artist", 180000))
}
```

## Common Patterns

### Loading State
```kotlin
@Composable
fun LoadingState(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}
```

### Error State
```kotlin
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
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

### Empty State
```kotlin
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

## Anti-Patterns

### Don't: Business Logic in Composables
```kotlin
// Bad
@Composable
fun PlayerScreen() {
    val songs = loadSongsFromDatabase() // Don't do this!
    // UI code
}

// Good
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    // UI code
}
```

### Don't: Mutable State Without remember
```kotlin
// Bad
@Composable
fun Counter() {
    var count = 0 // Lost on recomposition!
    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}

// Good
@Composable
fun Counter() {
    var count by remember { mutableStateOf(0) }
    Button(onClick = { count++ }) {
        Text("Count: $count")
    }
}
```

### Don't: Conditional Composable Calls
```kotlin
// Bad
@Composable
fun ConditionalContent(showContent: Boolean) {
    if (showContent) {
        remember { /* ... */ } // Conditional remember!
    }
}

// Good
@Composable
fun ConditionalContent(showContent: Boolean) {
    val data = remember { /* ... */ }
    if (showContent) {
        Content(data)
    }
}
```

## Code Review Checklist

Before submitting Compose code:
- [ ] State is hoisted appropriately
- [ ] Modifier parameter provided
- [ ] Content descriptions added
- [ ] Test tags added for testable elements
- [ ] Material 3 components used
- [ ] Theme colors and typography used
- [ ] No business logic in composables
- [ ] Previews provided
- [ ] Performance optimized
- [ ] Accessibility considered

## References

- [Jetpack Compose Guidelines](https://developer.android.com/jetpack/compose/guidelines)
- [Material Design 3](https://m3.material.io/)
- [Compose Performance](https://developer.android.com/jetpack/compose/performance)
- [Compose Accessibility](https://developer.android.com/jetpack/compose/accessibility)