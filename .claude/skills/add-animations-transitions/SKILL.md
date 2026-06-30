---
name: add-animations-transitions
description: "Comprehensive guide for implementing smooth animations and transitions in Jetpack Compose applications. Covers animation APIs, shared element transitions, list animations, state-based animations, and performance optimization following Material Design motion principles."
---

# Skill: Add Animations and Transitions

## Overview
Comprehensive guide for implementing smooth animations and transitions in Jetpack Compose applications. Covers animation APIs, shared element transitions, list animations, state-based animations, and performance optimization following Material Design motion principles.

> Motion language (durations, mini-player → now-playing shared element, reduced-motion) follows `.claude/rules/ui-style-guide.md` §9.

## When to Use
- Adding polish to UI interactions
- Implementing screen transitions
- Animating list items
- Creating loading animations
- Implementing shared element transitions
- Animating state changes
- Improving user experience with motion

## Prerequisites
- Jetpack Compose knowledge
- Understanding of Compose state
- Familiarity with Material Design motion
- Basic animation concepts

## Workflow Steps

### Step 1: Understand Compose Animation APIs
**Goal**: Learn the different animation APIs available

**Concepts**:
- `animate*AsState` - Simple state-based animations
- `Animatable` - Low-level animation control
- `AnimatedVisibility` - Enter/exit animations
- `AnimatedContent` - Content change animations
- `Transition` - Multiple coordinated animations
- `updateTransition` - State-based transitions

**Overview**:
```kotlin
// Simple value animation
val alpha by animateFloatAsState(
    targetValue = if (visible) 1f else 0f,
    label = "alpha"
)

// Low-level control
val animatable = remember { Animatable(0f) }
LaunchedEffect(key1 = visible) {
    animatable.animateTo(if (visible) 1f else 0f)
}

// Visibility animations
AnimatedVisibility(visible = isVisible) {
    Text("Hello")
}

// Content animations
AnimatedContent(targetState = currentScreen) { screen ->
    when (screen) {
        Screen.Home -> HomeContent()
        Screen.Details -> DetailsContent()
    }
}
```

### Step 2: Implement Simple Animations
**Goal**: Add basic animations to UI elements

**Actions**:
1. Animate size changes
2. Animate color changes
3. Animate position
4. Animate alpha/visibility

**Implementation**:
```kotlin
// Animate size
@Composable
fun ExpandableCard(expanded: Boolean) {
    val size by animateDpAsState(
        targetValue = if (expanded) 200.dp else 100.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "size"
    )
    
    Box(
        modifier = Modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primary)
    )
}

// Animate color
@Composable
fun ColorChangingButton(isActive: Boolean) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isActive) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(durationMillis = 300),
        label = "backgroundColor"
    )
    
    Button(
        onClick = {},
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor
        )
    ) {
        Text("Button")
    }
}

// Animate position
@Composable
fun SlidingElement(isVisible: Boolean) {
    val offsetX by animateDpAsState(
        targetValue = if (isVisible) 0.dp else (-100).dp,
        animationSpec = tween(durationMillis = 300),
        label = "offsetX"
    )
    
    Box(
        modifier = Modifier
            .offset(x = offsetX)
            .size(100.dp)
            .background(Color.Blue)
    )
}

// Animate alpha
@Composable
fun FadingText(isVisible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "alpha"
    )
    
    Text(
        text = "Fading Text",
        modifier = Modifier.alpha(alpha)
    )
}
```

### Step 3: Use AnimatedVisibility
**Goal**: Animate element appearance and disappearance

**Actions**:
1. Add enter animations
2. Add exit animations
3. Customize transitions
4. Animate children separately

**Implementation**:
```kotlin
// Basic AnimatedVisibility
@Composable
fun AnimatedCard(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically()
    ) {
        Card {
            Text("Animated Card")
        }
    }
}

// Custom enter/exit animations
@Composable
fun CustomAnimatedContent(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(
            initialOffsetX = { fullWidth -> fullWidth },
            animationSpec = tween(durationMillis = 300)
        ) + fadeIn(animationSpec = tween(durationMillis = 300)),
        exit = slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth },
            animationSpec = tween(durationMillis = 300)
        ) + fadeOut(animationSpec = tween(durationMillis = 300))
    ) {
        PlayerControls()
    }
}

// Animate children with different delays
@Composable
fun StaggeredList(visible: Boolean) {
    AnimatedVisibility(visible = visible) {
        Column {
            items.forEachIndexed { index, item ->
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = index * 50
                        )
                    )
                ) {
                    ListItem(item)
                }
            }
        }
    }
}

// Expand/collapse animation
@Composable
fun ExpandableSection(expanded: Boolean) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy
            )
        ) + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column {
            Text("Expanded content")
            Text("More details here")
        }
    }
}
```

### Step 4: Implement AnimatedContent
**Goal**: Animate content changes smoothly

**Actions**:
1. Animate between different content
2. Use SizeTransform for size changes
3. Add custom transitions
4. Handle content switching

**Implementation**:
```kotlin
// Basic content switching
@Composable
fun TabContent(selectedTab: Tab) {
    AnimatedContent(
        targetState = selectedTab,
        transitionSpec = {
            fadeIn(animationSpec = tween(300)) togetherWith
                fadeOut(animationSpec = tween(300))
        },
        label = "tabContent"
    ) { tab ->
        when (tab) {
            Tab.Songs -> SongsContent()
            Tab.Albums -> AlbumsContent()
            Tab.Artists -> ArtistsContent()
        }
    }
}

// Slide transition
@Composable
fun ScreenSwitcher(currentScreen: Screen) {
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState > initialState) {
                // Forward navigation
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
            } else {
                // Backward navigation
                slideInHorizontally { width -> -width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> width } + fadeOut()
            }.using(
                SizeTransform(clip = false)
            )
        },
        label = "screenSwitcher"
    ) { screen ->
        when (screen) {
            Screen.Library -> LibraryScreen()
            Screen.Player -> PlayerScreen()
        }
    }
}

// Counter with slide animation
@Composable
fun AnimatedCounter(count: Int) {
    AnimatedContent(
        targetState = count,
        transitionSpec = {
            if (targetState > initialState) {
                slideInVertically { height -> height } + fadeIn() togetherWith
                    slideOutVertically { height -> -height } + fadeOut()
            } else {
                slideInVertically { height -> -height } + fadeIn() togetherWith
                    slideOutVertically { height -> height } + fadeOut()
            }.using(
                SizeTransform(clip = false)
            )
        },
        label = "counter"
    ) { targetCount ->
        Text(
            text = targetCount.toString(),
            style = MaterialTheme.typography.displayLarge
        )
    }
}
```

### Step 5: Create List Animations
**Goal**: Animate list item changes

**Actions**:
1. Animate item appearance
2. Animate item removal
3. Animate item reordering
4. Add scroll animations

**Implementation**:
```kotlin
// Animated LazyColumn items
@Composable
fun AnimatedSongList(songs: List<Song>) {
    LazyColumn {
        items(
            items = songs,
            key = { song -> song.id }
        ) { song ->
            AnimatedSongItem(
                song = song,
                modifier = Modifier.animateItemPlacement(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            )
        }
    }
}

@Composable
fun AnimatedSongItem(
    song: Song,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        visible = true
    }
    
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(),
        modifier = modifier
    ) {
        SongListItem(song = song)
    }
}

// Swipe to dismiss with animation
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDismissItem(
    song: Song,
    onDismiss: () -> Unit
) {
    val dismissState = rememberDismissState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == DismissValue.DismissedToEnd ||
                dismissValue == DismissValue.DismissedToStart
            ) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    
    SwipeToDismiss(
        state = dismissState,
        background = {
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    DismissValue.Default -> Color.Transparent
                    else -> Color.Red
                },
                label = "backgroundColor"
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
            )
        },
        dismissContent = {
            SongListItem(song = song)
        }
    )
}

// Scroll-based animations
@Composable
fun ScrollAnimatedList(songs: List<Song>) {
    val listState = rememberLazyListState()
    
    LazyColumn(state = listState) {
        itemsIndexed(songs) { index, song ->
            val itemInfo = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == index }
            
            val offset = itemInfo?.let {
                val itemTop = it.offset
                val viewportHeight = listState.layoutInfo.viewportEndOffset
                (itemTop.toFloat() / viewportHeight).coerceIn(0f, 1f)
            } ?: 0f
            
            SongListItem(
                song = song,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = 1f - (offset * 0.5f)
                        scaleX = 1f - (offset * 0.1f)
                        scaleY = 1f - (offset * 0.1f)
                    }
            )
        }
    }
}
```

### Step 6: Implement State-Based Animations
**Goal**: Coordinate multiple animations based on state

**Actions**:
1. Use updateTransition
2. Animate multiple properties
3. Create complex transitions
4. Handle state changes

**Implementation**:
```kotlin
// Multiple coordinated animations
@Composable
fun PlayButton(isPlaying: Boolean) {
    val transition = updateTransition(
        targetState = isPlaying,
        label = "playButton"
    )
    
    val size by transition.animateDp(
        transitionSpec = { spring(stiffness = Spring.StiffnessLow) },
        label = "size"
    ) { playing ->
        if (playing) 64.dp else 56.dp
    }
    
    val color by transition.animateColor(
        transitionSpec = { tween(durationMillis = 300) },
        label = "color"
    ) { playing ->
        if (playing) Color.Red else Color.Green
    }
    
    val rotation by transition.animateFloat(
        transitionSpec = { tween(durationMillis = 300) },
        label = "rotation"
    ) { playing ->
        if (playing) 0f else 180f
    }
    
    IconButton(
        onClick = {},
        modifier = Modifier
            .size(size)
            .rotate(rotation)
            .background(color, CircleShape)
    ) {
        Icon(
            imageVector = if (isPlaying) {
                Icons.Default.Pause
            } else {
                Icons.Default.PlayArrow
            },
            contentDescription = null,
            tint = Color.White
        )
    }
}

// Complex state transitions
enum class PlayerState { IDLE, LOADING, PLAYING, PAUSED, ERROR }

@Composable
fun PlayerStateIndicator(state: PlayerState) {
    val transition = updateTransition(
        targetState = state,
        label = "playerState"
    )
    
    val alpha by transition.animateFloat(
        label = "alpha"
    ) { playerState ->
        when (playerState) {
            PlayerState.IDLE -> 0.3f
            PlayerState.LOADING -> 0.6f
            PlayerState.PLAYING -> 1f
            PlayerState.PAUSED -> 0.8f
            PlayerState.ERROR -> 1f
        }
    }
    
    val scale by transition.animateFloat(
        label = "scale"
    ) { playerState ->
        when (playerState) {
            PlayerState.IDLE -> 0.8f
            PlayerState.LOADING -> 1f
            PlayerState.PLAYING -> 1.2f
            PlayerState.PAUSED -> 1f
            PlayerState.ERROR -> 0.9f
        }
    }
    
    val color by transition.animateColor(
        label = "color"
    ) { playerState ->
        when (playerState) {
            PlayerState.IDLE -> Color.Gray
            PlayerState.LOADING -> Color.Blue
            PlayerState.PLAYING -> Color.Green
            PlayerState.PAUSED -> Color.Yellow
            PlayerState.ERROR -> Color.Red
        }
    }
    
    Box(
        modifier = Modifier
            .size(100.dp)
            .scale(scale)
            .alpha(alpha)
            .background(color, CircleShape)
    )
}
```

### Step 7: Add Loading Animations
**Goal**: Create engaging loading indicators

**Actions**:
1. Create infinite animations
2. Build custom loaders
3. Add shimmer effects
4. Implement progress animations

**Implementation**:
```kotlin
// Rotating loader
@Composable
fun RotatingLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "Loading",
        modifier = Modifier
            .size(48.dp)
            .rotate(rotation)
    )
}

// Pulsing loader
@Composable
fun PulsingLoader() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(48.dp)
            .scale(scale)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}

// Shimmer effect
@Composable
fun ShimmerEffect(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )
    
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.LightGray.copy(alpha = 0.3f),
                        Color.LightGray.copy(alpha = 0.5f),
                        Color.LightGray.copy(alpha = 0.3f)
                    ),
                    start = Offset(offset, offset),
                    end = Offset(offset + 200f, offset + 200f)
                )
            )
    )
}

// Progress animation
@Composable
fun AnimatedProgressBar(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500),
        label = "progress"
    )
    
    LinearProgressIndicator(
        progress = animatedProgress,
        modifier = Modifier.fillMaxWidth()
    )
}
```

### Step 8: Implement Shared Element Transitions
**Goal**: Create smooth transitions between screens

**Actions**:
1. Setup shared element transitions
2. Define shared elements
3. Animate between screens
4. Handle bounds transformation

**Implementation**:
```kotlin
// Note: Shared element transitions in Compose are still experimental
// This is a conceptual example

@Composable
fun AlbumListItem(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row {
            AsyncImage(
                model = album.artworkUrl,
                contentDescription = album.title,
                modifier = Modifier
                    .size(80.dp)
                    .sharedElement(
                        key = "album_art_${album.id}",
                        screenKey = "list"
                    )
            )
            
            Column {
                Text(
                    text = album.title,
                    modifier = Modifier.sharedElement(
                        key = "album_title_${album.id}",
                        screenKey = "list"
                    )
                )
                Text(text = album.artist)
            }
        }
    }
}

@Composable
fun AlbumDetailScreen(album: Album) {
    Column {
        AsyncImage(
            model = album.artworkUrl,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .sharedElement(
                    key = "album_art_${album.id}",
                    screenKey = "detail"
                )
        )
        
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.sharedElement(
                key = "album_title_${album.id}",
                screenKey = "detail"
            )
        )
        
        // Rest of the detail content
    }
}
```

### Step 9: Optimize Animation Performance
**Goal**: Ensure smooth 60fps animations

**Actions**:
1. Use remember for animation values
2. Avoid recomposition during animation
3. Use graphicsLayer for transformations
4. Profile animation performance

**Implementation**:
```kotlin
// Use graphicsLayer for better performance
@Composable
fun OptimizedAnimation(visible: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        label = "alpha"
    )
    
    // Good: Uses graphicsLayer (GPU accelerated)
    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
            }
            .size(100.dp)
            .background(Color.Blue)
    )
    
    // Avoid: Causes layout recomposition
    // Box(
    //     modifier = Modifier
    //         .alpha(alpha)  // This is fine for alpha
    //         .size(100.dp)
    //         .background(Color.Blue)
    // )
}

// Optimize list animations
@Composable
fun OptimizedList(items: List<Item>) {
    LazyColumn {
        items(
            items = items,
            key = { it.id }  // Important for animation performance
        ) { item ->
            ItemCard(
                item = item,
                modifier = Modifier
                    .animateItemPlacement()  // Smooth reordering
                    .fillMaxWidth()
            )
        }
    }
}

// Use derivedStateOf to avoid unnecessary recompositions
@Composable
fun ScrollBasedAnimation() {
    val listState = rememberLazyListState()
    
    val showButton by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }
    
    AnimatedVisibility(visible = showButton) {
        FloatingActionButton(onClick = {}) {
            Icon(Icons.Default.ArrowUpward, null)
        }
    }
}
```

## Addons

### Animation Specifications
```kotlin
// Spring animation (natural, bouncy)
spring(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

// Tween animation (linear interpolation)
tween(
    durationMillis = 300,
    delayMillis = 0,
    easing = FastOutSlowInEasing
)

// Keyframes animation (custom timing)
keyframes {
    durationMillis = 1000
    0f at 0 with LinearEasing
    0.5f at 500 with FastOutSlowInEasing
    1f at 1000
}

// Snap animation (instant)
snap(delayMillis = 100)

// Repeatable animation
repeatable(
    iterations = 3,
    animation = tween(durationMillis = 300),
    repeatMode = RepeatMode.Reverse
)
```

### Custom Easing Functions
```kotlin
val CustomEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

val BounceEasing = Easing { fraction ->
    val n1 = 7.5625f
    val d1 = 2.75f
    
    when {
        fraction < 1f / d1 -> n1 * fraction * fraction
        fraction < 2f / d1 -> {
            val f = fraction - 1.5f / d1
            n1 * f * f + 0.75f
        }
        fraction < 2.5f / d1 -> {
            val f = fraction - 2.25f / d1
            n1 * f * f + 0.9375f
        }
        else -> {
            val f = fraction - 2.625f / d1
            n1 * f * f + 0.984375f
        }
    }
}
```

### Gesture-Based Animations
```kotlin
@Composable
fun DraggableCard() {
    var offsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy
        ),
        label = "offsetX"
    )
    
    Card(
        modifier = Modifier
            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { offsetX = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        offsetX += dragAmount
                    }
                )
            }
    ) {
        Text("Drag me!")
    }
}
```

## Related Files
- `presentation/common/animations/` - Reusable animations
- `presentation/*/Screen.kt` - Screen animations
- `ui/theme/Motion.kt` - Motion specifications

## Notes
- Use `label` parameter for better debugging
- Prefer `graphicsLayer` for transformations
- Use `key` in lists for smooth animations
- Keep animations under 300ms for responsiveness
- Use spring animations for natural feel
- Test animations on low-end devices
- Follow Material Design motion guidelines
- Avoid animating layout properties when possible
- Use `remember` for animation state
- Profile with Layout Inspector

## Common Patterns

### Fade Through Pattern
```kotlin
@Composable
fun FadeThrough(targetState: Int) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            fadeIn(tween(220, delayMillis = 90)) togetherWith
                fadeOut(tween(90))
        }
    ) { state ->
        Text("Screen $state")
    }
}
```

### Shared Axis Pattern
```kotlin
@Composable
fun SharedAxisX(targetState: Int) {
    AnimatedContent(
        targetState = targetState,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
        }
    ) { state ->
        Text("Screen $state")
    }
}
```

### Container Transform Pattern
```kotlin
@Composable
fun ContainerTransform(expanded: Boolean) {
    AnimatedContent(
        targetState = expanded,
        transitionSpec = {
            fadeIn() + scaleIn(initialScale = 0.8f) togetherWith
                fadeOut() + scaleOut(targetScale = 1.2f)
        }
    ) { isExpanded ->
        if (isExpanded) {
            ExpandedView()
        } else {
            CollapsedView()
        }
    }
}