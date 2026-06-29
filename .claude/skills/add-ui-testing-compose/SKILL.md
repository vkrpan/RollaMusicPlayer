---
name: add-ui-testing-compose
description: "Comprehensive guide for implementing UI tests in Jetpack Compose applications. Covers test setup, finding and interacting with elements, testing user flows, navigation, accessibility, and screenshot testing."
---

# Skill: Add UI Testing (Compose)

## Overview
Comprehensive guide for implementing UI tests in Jetpack Compose applications. Covers test setup, finding and interacting with elements, testing user flows, navigation, accessibility, and screenshot testing.

## When to Use
- Setting up UI testing infrastructure
- Testing user interactions and flows
- Verifying UI state changes
- Testing navigation between screens
- Ensuring accessibility compliance
- Implementing screenshot tests
- Testing complex UI components

## Prerequisites
- Jetpack Compose UI implementation
- Understanding of Compose semantics
- Basic testing knowledge
- Familiarity with the app's UI structure

## Workflow Steps

### Step 1: Add UI Testing Dependencies
**Goal**: Configure project for Compose UI testing

**Actions**:
1. Add Compose testing dependencies
2. Configure test runner
3. Setup test rules

**Implementation**:
```kotlin
// build.gradle.kts (app module)
android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose UI Testing
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.5.4")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.5.4")
    
    // Test rules and runners
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    
    // Espresso (for some utilities)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    
    // Navigation Testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")
    
    // Hilt Testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.48")
    
    // Screenshot Testing (optional)
    androidTestImplementation("com.github.sergio-sastre:AndroidUiTestingUtils:2.1.0")
}
```

### Step 2: Create Test Setup
**Goal**: Setup base test infrastructure

**Actions**:
1. Create test rule
2. Setup Hilt for tests
3. Create base test class

**Implementation**:
```kotlin
// androidTest/BaseComposeTest.kt
@HiltAndroidTest
abstract class BaseComposeTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    @Before
    open fun setup() {
        hiltRule.inject()
    }
}

// For testing individual composables
abstract class ComposeUnitTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    protected fun setContent(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            MusicPlayerTheme {
                content()
            }
        }
    }
}
```

### Step 3: Find UI Elements
**Goal**: Learn to locate composables for testing

**Actions**:
1. Use semantic properties
2. Find by text, content description, tag
3. Use matchers for complex queries

**Implementation**:
```kotlin
// Finding elements by different properties
class FindingElementsTest : ComposeUnitTest() {
    
    @Test
    fun findByText() {
        setContent {
            Text("Hello World")
        }
        
        composeTestRule
            .onNodeWithText("Hello World")
            .assertIsDisplayed()
    }
    
    @Test
    fun findByContentDescription() {
        setContent {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play button"
            )
        }
        
        composeTestRule
            .onNodeWithContentDescription("Play button")
            .assertIsDisplayed()
    }
    
    @Test
    fun findByTestTag() {
        setContent {
            Button(
                onClick = {},
                modifier = Modifier.testTag("play_button")
            ) {
                Text("Play")
            }
        }
        
        composeTestRule
            .onNodeWithTag("play_button")
            .assertIsDisplayed()
    }
    
    @Test
    fun findWithMatcher() {
        setContent {
            Column {
                Text("Song 1")
                Text("Song 2")
                Text("Song 3")
            }
        }
        
        // Find all text nodes
        composeTestRule
            .onAllNodesWithText("Song", substring = true)
            .assertCountEquals(3)
        
        // Find specific node
        composeTestRule
            .onAllNodesWithText("Song", substring = true)[1]
            .assertTextEquals("Song 2")
    }
    
    @Test
    fun findBySemanticProperty() {
        setContent {
            Text(
                text = "Clickable text",
                modifier = Modifier.clickable { }
            )
        }
        
        composeTestRule
            .onNode(hasClickAction())
            .assertIsDisplayed()
    }
    
    @Test
    fun findInScrollableList() {
        setContent {
            LazyColumn {
                items(100) { index ->
                    Text(
                        text = "Item $index",
                        modifier = Modifier.testTag("item_$index")
                    )
                }
            }
        }
        
        // Scroll to item and verify
        composeTestRule
            .onNodeWithTag("item_50")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
```

### Step 4: Interact with UI Elements
**Goal**: Perform user actions on composables

**Actions**:
1. Click elements
2. Enter text
3. Scroll and swipe
4. Perform gestures

**Implementation**:
```kotlin
// Interacting with UI elements
class InteractionTest : ComposeUnitTest() {
    
    @Test
    fun clickButton() {
        var clicked = false
        
        setContent {
            Button(onClick = { clicked = true }) {
                Text("Click me")
            }
        }
        
        composeTestRule
            .onNodeWithText("Click me")
            .performClick()
        
        assertTrue(clicked)
    }
    
    @Test
    fun enterText() {
        var text = ""
        
        setContent {
            TextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Search") }
            )
        }
        
        composeTestRule
            .onNodeWithText("Search")
            .performTextInput("Test query")
        
        assertEquals("Test query", text)
    }
    
    @Test
    fun clearAndEnterText() {
        setContent {
            var text by remember { mutableStateOf("Initial") }
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.testTag("text_field")
            )
        }
        
        composeTestRule
            .onNodeWithTag("text_field")
            .performTextClearance()
        
        composeTestRule
            .onNodeWithTag("text_field")
            .performTextInput("New text")
        
        composeTestRule
            .onNodeWithText("New text")
            .assertIsDisplayed()
    }
    
    @Test
    fun scrollToItem() {
        setContent {
            LazyColumn(modifier = Modifier.testTag("list")) {
                items(50) { index ->
                    Text(
                        text = "Item $index",
                        modifier = Modifier.testTag("item_$index")
                    )
                }
            }
        }
        
        composeTestRule
            .onNodeWithTag("item_45")
            .performScrollTo()
            .assertIsDisplayed()
    }
    
    @Test
    fun swipeToRefresh() {
        var refreshed = false
        
        setContent {
            val pullRefreshState = rememberPullRefreshState(
                refreshing = false,
                onRefresh = { refreshed = true }
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pullRefresh(pullRefreshState)
                    .testTag("refresh_container")
            ) {
                Text("Pull to refresh")
            }
        }
        
        composeTestRule
            .onNodeWithTag("refresh_container")
            .performTouchInput {
                swipeDown()
            }
        
        assertTrue(refreshed)
    }
}
```

### Step 5: Assert UI State
**Goal**: Verify UI displays correct state

**Actions**:
1. Assert visibility
2. Assert text content
3. Assert enabled/disabled state
4. Assert selection state

**Implementation**:
```kotlin
// Asserting UI state
class AssertionTest : ComposeUnitTest() {
    
    @Test
    fun assertIsDisplayed() {
        setContent {
            Text("Visible text")
        }
        
        composeTestRule
            .onNodeWithText("Visible text")
            .assertIsDisplayed()
    }
    
    @Test
    fun assertIsNotDisplayed() {
        setContent {
            Column {
                Text("Visible")
                if (false) {
                    Text("Hidden")
                }
            }
        }
        
        composeTestRule
            .onNodeWithText("Visible")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Hidden")
            .assertDoesNotExist()
    }
    
    @Test
    fun assertTextEquals() {
        setContent {
            Text("Expected text")
        }
        
        composeTestRule
            .onNodeWithText("Expected text")
            .assertTextEquals("Expected text")
    }
    
    @Test
    fun assertIsEnabled() {
        setContent {
            Button(
                onClick = {},
                enabled = true
            ) {
                Text("Enabled button")
            }
        }
        
        composeTestRule
            .onNodeWithText("Enabled button")
            .assertIsEnabled()
    }
    
    @Test
    fun assertIsNotEnabled() {
        setContent {
            Button(
                onClick = {},
                enabled = false
            ) {
                Text("Disabled button")
            }
        }
        
        composeTestRule
            .onNodeWithText("Disabled button")
            .assertIsNotEnabled()
    }
    
    @Test
    fun assertIsSelected() {
        setContent {
            var selected by remember { mutableStateOf(true) }
            
            FilterChip(
                selected = selected,
                onClick = { selected = !selected },
                label = { Text("Filter") }
            )
        }
        
        composeTestRule
            .onNodeWithText("Filter")
            .assertIsSelected()
    }
    
    @Test
    fun assertHasClickAction() {
        setContent {
            Text(
                text = "Clickable",
                modifier = Modifier.clickable { }
            )
        }
        
        composeTestRule
            .onNodeWithText("Clickable")
            .assert(hasClickAction())
    }
}
```

### Step 6: Test User Flows
**Goal**: Test complete user interactions

**Actions**:
1. Test multi-step flows
2. Verify state changes
3. Test error scenarios
4. Test loading states

**Implementation**:
```kotlin
// Testing complete user flows
@HiltAndroidTest
class LibraryScreenTest : BaseComposeTest() {
    
    @Test
    fun searchFlow_filtersResults() {
        // Given - Screen is displayed with songs
        composeTestRule
            .onNodeWithText("Song 1")
            .assertIsDisplayed()
        
        // When - User enters search query
        composeTestRule
            .onNodeWithContentDescription("Search")
            .performClick()
        
        composeTestRule
            .onNodeWithTag("search_field")
            .performTextInput("Rock")
        
        // Then - Only matching songs are displayed
        composeTestRule
            .onNodeWithText("Rock Song")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Pop Song")
            .assertDoesNotExist()
    }
    
    @Test
    fun playSongFlow_navigatesToPlayer() {
        // Given - Song is displayed
        composeTestRule
            .onNodeWithText("Test Song")
            .assertIsDisplayed()
        
        // When - User clicks play button
        composeTestRule
            .onNodeWithContentDescription("Play Test Song")
            .performClick()
        
        // Then - Player screen is displayed
        composeTestRule
            .onNodeWithTag("player_screen")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Test Song")
            .assertIsDisplayed()
    }
    
    @Test
    fun loadingState_showsProgressIndicator() {
        // Given - Loading state
        composeTestRule
            .onNodeWithTag("loading_indicator")
            .assertIsDisplayed()
        
        // Wait for content to load
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule
                .onAllNodesWithTag("song_item")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        
        // Then - Content is displayed
        composeTestRule
            .onNodeWithTag("loading_indicator")
            .assertDoesNotExist()
    }
    
    @Test
    fun errorState_showsErrorMessage() {
        // Given - Error state is triggered
        // (This would be set up through test data or mocking)
        
        // Then - Error message is displayed
        composeTestRule
            .onNodeWithText("Failed to load songs")
            .assertIsDisplayed()
        
        // And - Retry button is available
        composeTestRule
            .onNodeWithText("Retry")
            .assertIsDisplayed()
            .performClick()
    }
    
    @Test
    fun emptyState_showsEmptyMessage() {
        // Given - No songs available
        
        // Then - Empty state is displayed
        composeTestRule
            .onNodeWithText("No songs found")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Add some music to get started")
            .assertIsDisplayed()
    }
}
```

### Step 7: Test Navigation
**Goal**: Verify navigation between screens

**Actions**:
1. Test navigation actions
2. Verify back navigation
3. Test deep links
4. Verify navigation arguments

**Implementation**:
```kotlin
// Testing navigation
@HiltAndroidTest
class NavigationTest {
    
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)
    
    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var navController: TestNavHostController
    
    @Before
    fun setup() {
        hiltRule.inject()
        
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(
                ComposeNavigator()
            )
            
            MusicPlayerTheme {
                MusicPlayerApp(navController)
            }
        }
    }
    
    @Test
    fun navigateToAlbumDetail() {
        // When - Click on album
        composeTestRule
            .onNodeWithText("Test Album")
            .performClick()
        
        // Then - Album detail screen is displayed
        val route = navController.currentBackStackEntry?.destination?.route
        assertTrue(route?.contains("AlbumDetail") == true)
        
        composeTestRule
            .onNodeWithText("Album Details")
            .assertIsDisplayed()
    }
    
    @Test
    fun navigateBackFromAlbumDetail() {
        // Given - On album detail screen
        composeTestRule
            .onNodeWithText("Test Album")
            .performClick()
        
        // When - Press back
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .performClick()
        
        // Then - Back at library screen
        composeTestRule
            .onNodeWithText("Library")
            .assertIsDisplayed()
    }
    
    @Test
    fun bottomNavigationWorks() {
        // When - Click on playlists tab
        composeTestRule
            .onNodeWithText("Playlists")
            .performClick()
        
        // Then - Playlists screen is displayed
        composeTestRule
            .onNodeWithTag("playlists_screen")
            .assertIsDisplayed()
        
        // When - Click on settings tab
        composeTestRule
            .onNodeWithText("Settings")
            .performClick()
        
        // Then - Settings screen is displayed
        composeTestRule
            .onNodeWithTag("settings_screen")
            .assertIsDisplayed()
    }
}
```

### Step 8: Test Accessibility
**Goal**: Ensure UI is accessible

**Actions**:
1. Verify content descriptions
2. Test with TalkBack simulation
3. Check touch target sizes
4. Verify semantic properties

**Implementation**:
```kotlin
// Testing accessibility
class AccessibilityTest : ComposeUnitTest() {
    
    @Test
    fun allInteractiveElementsHaveContentDescription() {
        setContent {
            PlayerControls(
                isPlaying = false,
                onPlayPause = {},
                onSkipNext = {},
                onSkipPrevious = {}
            )
        }
        
        // Verify all buttons have content descriptions
        composeTestRule
            .onNodeWithContentDescription("Play")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Skip to next")
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithContentDescription("Skip to previous")
            .assertIsDisplayed()
    }
    
    @Test
    fun touchTargetsAreLargeEnough() {
        setContent {
            IconButton(
                onClick = {},
                modifier = Modifier.testTag("icon_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play"
                )
            }
        }
        
        // Verify minimum touch target size (48dp)
        composeTestRule
            .onNodeWithTag("icon_button")
            .assertHeightIsAtLeast(48.dp)
            .assertWidthIsAtLeast(48.dp)
    }
    
    @Test
    fun textHasMinimumContrast() {
        // This would typically be done with screenshot testing
        // or visual regression testing tools
        setContent {
            Text(
                text = "Readable text",
                color = Color.Black,
                modifier = Modifier.background(Color.White)
            )
        }
        
        composeTestRule
            .onNodeWithText("Readable text")
            .assertIsDisplayed()
    }
    
    @Test
    fun stateChangesAreAnnounced() {
        setContent {
            var isPlaying by remember { mutableStateOf(false) }
            
            IconButton(
                onClick = { isPlaying = !isPlaying },
                modifier = Modifier.semantics {
                    stateDescription = if (isPlaying) "Playing" else "Paused"
                }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
        
        // Verify state description
        composeTestRule
            .onNode(hasStateDescription("Paused"))
            .assertIsDisplayed()
        
        // Change state
        composeTestRule
            .onNodeWithContentDescription("Play")
            .performClick()
        
        // Verify new state
        composeTestRule
            .onNode(hasStateDescription("Playing"))
            .assertIsDisplayed()
    }
}
```

### Step 9: Screenshot Testing
**Goal**: Capture and compare UI screenshots

**Actions**:
1. Setup screenshot testing
2. Capture baseline screenshots
3. Compare against baselines
4. Test different states

**Implementation**:
```kotlin
// Screenshot testing setup
class ScreenshotTest : ComposeUnitTest() {
    
    @get:Rule
    val screenshotRule = ScreenshotTestRule()
    
    @Test
    fun playerScreen_idle() {
        setContent {
            PlayerScreen(
                uiState = PlayerUiState.Idle,
                onEvent = {}
            )
        }
        
        composeTestRule
            .onRoot()
            .captureToImage()
            .assertAgainstGolden("player_idle")
    }
    
    @Test
    fun playerScreen_playing() {
        setContent {
            PlayerScreen(
                uiState = PlayerUiState.Playing(
                    song = Song("1", "Test Song", "Artist", "Album", 180000),
                    progress = 0.5f,
                    currentPosition = 90000
                ),
                onEvent = {}
            )
        }
        
        composeTestRule
            .onRoot()
            .captureToImage()
            .assertAgainstGolden("player_playing")
    }
    
    @Test
    fun songList_multipleStates() {
        // Test with different data
        listOf(
            emptyList<Song>() to "empty",
            listOf(Song("1", "Song", "Artist", "Album", 180000)) to "single",
            (1..10).map { Song("$it", "Song $it", "Artist", "Album", 180000) } to "multiple"
        ).forEach { (songs, name) ->
            setContent {
                SongList(songs = songs, onSongClick = {})
            }
            
            composeTestRule
                .onRoot()
                .captureToImage()
                .assertAgainstGolden("song_list_$name")
        }
    }
}
```

## Addons

### Custom Test Rules
```kotlin
class ComposeTestRuleWithTheme : ComposeContentTestRule by createComposeRule() {
    
    fun setContentWithTheme(
        darkTheme: Boolean = false,
        content: @Composable () -> Unit
    ) {
        setContent {
            MusicPlayerTheme(darkTheme = darkTheme) {
                content()
            }
        }
    }
}
```

### Test Helpers
```kotlin
// Wait for condition
fun ComposeContentTestRule.waitUntilExists(
    matcher: SemanticsMatcher,
    timeoutMillis: Long = 5000
) {
    waitUntil(timeoutMillis) {
        onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }
}

// Wait for text
fun ComposeContentTestRule.waitForText(
    text: String,
    timeoutMillis: Long = 5000
) {
    waitUntilExists(hasText(text), timeoutMillis)
}

// Perform action and wait
fun SemanticsNodeInteraction.performClickAndWait(
    timeoutMillis: Long = 1000
): SemanticsNodeInteraction {
    performClick()
    Thread.sleep(timeoutMillis)
    return this
}
```

### Page Object Pattern
```kotlin
class LibraryScreenRobot(
    private val composeTestRule: ComposeContentTestRule
) {
    fun assertSongDisplayed(title: String) {
        composeTestRule
            .onNodeWithText(title)
            .assertIsDisplayed()
    }
    
    fun clickSong(title: String) {
        composeTestRule
            .onNodeWithText(title)
            .performClick()
    }
    
    fun searchFor(query: String) {
        composeTestRule
            .onNodeWithContentDescription("Search")
            .performClick()
        
        composeTestRule
            .onNodeWithTag("search_field")
            .performTextInput(query)
    }
}

// Usage
@Test
fun testWithPageObject() {
    val robot = LibraryScreenRobot(composeTestRule)
    
    robot.assertSongDisplayed("Test Song")
    robot.searchFor("Rock")
    robot.clickSong("Rock Song")
}
```

## Related Files
- `androidTest/` - UI test files
- `androidTest/robot/` - Page objects
- `androidTest/util/` - Test utilities
- `debug/` - Test manifest

## Notes
- Use testTag for elements that need to be found in tests
- Prefer semantic properties over test tags when possible
- Test user flows, not implementation details
- Use waitUntil for asynchronous operations
- Test accessibility from the start
- Keep tests independent and isolated
- Use descriptive test names
- Test both success and error states
- Consider screenshot testing for visual regression
- Run tests on different screen sizes

## Common Patterns

### Testing Lists
```kotlin
@Test
fun testLazyList() {
    composeTestRule.setContent {
        LazyColumn {
            items(100) { index ->
                Text(
                    text = "Item $index",
                    modifier = Modifier.testTag("item_$index")
                )
            }
        }
    }
    
    // Scroll and verify
    composeTestRule
        .onNodeWithTag("item_50")
        .performScrollTo()
        .assertIsDisplayed()
}
```

### Testing Dialogs
```kotlin
@Test
fun testDialog() {
    var showDialog by mutableStateOf(false)
    
    composeTestRule.setContent {
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("Delete Song?") },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("Delete")
                    }
                }
            )
        }
    }
    
    showDialog = true
    
    composeTestRule
        .onNodeWithText("Delete Song?")
        .assertIsDisplayed()
}
```

### Testing Animations
```kotlin
@Test
fun testAnimation() {
    composeTestRule.mainClock.autoAdvance = false
    
    composeTestRule.setContent {
        AnimatedContent()
    }
    
    // Advance time
    composeTestRule.mainClock.advanceTimeBy(500)
    
    // Verify intermediate state
    composeTestRule
        .onNodeWithTag("animated_element")
        .assertExists()
}