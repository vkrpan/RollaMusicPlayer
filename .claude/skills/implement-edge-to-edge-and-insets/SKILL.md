---
name: implement-edge-to-edge-and-insets
description: "Step-by-step workflow for drawing RollaMusicPlayer edge-to-edge and handling window insets correctly — enableEdgeToEdge, transparent system bars, Scaffold/insets padding, IME handling, and the mini-player + bottom-nav overlap that music apps get wrong. Required on Android 15+."
---

# Skill: Implement Edge-to-Edge & Window Insets

## Overview
A workflow for making the app draw behind the system bars (edge-to-edge) and handling window insets so nothing is hidden behind the status bar, navigation bar, or keyboard. Android 15 (API 35) enforces edge-to-edge for apps targeting SDK 35, so this is required, not optional. The tricky part for a music player is the persistent mini-player and bottom navigation stacking above the gesture/navigation bar — this skill covers that explicitly.

## When to Use
- Initial app theming / first screens
- Targeting SDK 35+ (edge-to-edge is enforced)
- Fixing content hidden behind status/nav bars or the keyboard
- Building the persistent mini-player / bottom nav

## Prerequisites
- Compose with Material 3 (Scaffold handles most insets)
- Single-activity architecture (MainActivity hosts the NavHost)
- Theme available (m3-design-system-agent)

## Workflow Steps

### Step 1: Enable Edge-to-Edge
**Goal**: Draw behind the system bars

**Implementation**:
```kotlin
// MainActivity.kt
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()              // BEFORE setContent
        super.onCreate(savedInstanceState)
        setContent {
            RollaMusicPlayerTheme {
                AppRoot()
            }
        }
    }
}
```
`enableEdgeToEdge()` makes system bars transparent and lets content draw under them. Avoid setting hardcoded system-bar colors in the theme XML — that fights edge-to-edge and is deprecated on 35+.

### Step 2: Let the Theme Stay Transparent
**Goal**: No opaque system-bar colors overriding edge-to-edge

**Implementation**:
```xml
<!-- themes.xml — do NOT set android:statusBarColor / navigationBarColor on API 35+ -->
<style name="Theme.RollaMusicPlayer" parent="android:Theme.Material.NoActionBar">
    <item name="android:windowLightStatusBar">true</item>
    <!-- system bar colors are managed by enableEdgeToEdge() -->
</style>
```
Control icon contrast (light/dark bar icons) via `enableEdgeToEdge(statusBarStyle = ...)` or by toggling at runtime based on theme.

### Step 3: Apply Insets with Scaffold
**Goal**: Keep content out from under the bars by default

**Implementation**:
```kotlin
@Composable
fun AppRoot() {
    Scaffold(
        // Scaffold consumes system bar insets and passes padding to content
        bottomBar = { AppBottomNav() }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Library,
            modifier = Modifier.padding(innerPadding)
        ) { /* destinations */ }
    }
}
```
Material 3 `Scaffold` reads `WindowInsets.systemBars` and hands you `innerPadding`. Apply that padding to the content root.

### Step 4: Handle the Mini-Player + Bottom Nav Stack
**Goal**: The persistent mini-player sits above the nav bar, not under it

**Implementation**:
```kotlin
@Composable
fun AppBottomNav() {
    Column {
        MiniPlayer(
            modifier = Modifier.fillMaxWidth()   // no bottom inset here…
        )
        NavigationBar(
            // NavigationBar already adds navigationBars inset padding
        ) { /* items */ }
    }
}

// If the mini-player is shown WITHOUT a nav bar (e.g. full-screen list),
// it must add the navigation bar inset itself:
MiniPlayer(
    modifier = Modifier
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.navigationBars)
)
```
Rule of thumb: exactly one component in a vertical stack consumes the `navigationBars` inset — usually the lowest one. Double-padding (both mini-player and nav bar) leaves a gap; zero-padding hides content behind the gesture pill.

### Step 5: Handle the Keyboard (IME)
**Goal**: Search/tag-editor fields stay visible when the keyboard opens

**Implementation**:
```kotlin
// Search field / tag editor content
Column(
    modifier = Modifier
        .fillMaxSize()
        .imePadding()                 // pushes content above the keyboard
) {
    SearchField(...)
    SearchResults(...)
}
```
For full-screen scrollable content, prefer `Modifier.imePadding()` on the container or `WindowInsets.ime` consumption; avoid `adjustResize` XML flags in a Compose single-activity setup.

### Step 6: Full-Bleed Surfaces (Player Artwork)
**Goal**: Let album art fill behind the status bar, but keep controls tappable

**Implementation**:
```kotlin
// Now-playing: artwork draws edge-to-edge; controls respect insets
Box(Modifier.fillMaxSize()) {
    AlbumArtwork(... , modifier = Modifier.fillMaxSize())   // behind bars — intentional
    PlayerControls(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .windowInsetsPadding(WindowInsets.systemBars)    // controls stay clear
    )
    TopAppBar(
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
    )
}
```

### Step 7: Verify
**Checklist**:
- [ ] `enableEdgeToEdge()` called before `setContent`
- [ ] No hardcoded opaque status/nav bar colors fighting it
- [ ] Content not hidden behind status or navigation bars on gesture- and 3-button-nav devices
- [ ] Mini-player sits directly above the nav bar with no gap and no overlap
- [ ] Keyboard doesn't cover search/tag-editor fields (`imePadding`)
- [ ] Player artwork goes full-bleed while controls stay inside insets
- [ ] System bar icon contrast correct in light and dark themes
- [ ] Looks correct on a device with a display cutout/notch

## Related Files
- `MainActivity.kt` — enableEdgeToEdge
- `res/values/themes.xml` — transparent system bars
- `presentation/common/AppRoot.kt` — Scaffold + insets
- `presentation/player/MiniPlayer.kt` — inset handling for the persistent player

## Notes
- Android 15 (target SDK 35) enforces edge-to-edge; on 35+ the old `statusBarColor` APIs are deprecated/ignored.
- Prefer `Modifier.windowInsetsPadding(...)` over manual padding math; it reacts to gesture vs button nav automatically.
- Consume each inset once per axis — the most common bug is double-consumption (gaps) or none (overlap).
- Test on both gesture navigation and 3-button navigation, and with a cutout.

## Common Pitfalls
- ❌ Setting `android:navigationBarColor`/`statusBarColor` and expecting edge-to-edge — they conflict.
- ❌ Double-applying `navigationBars` inset to both the mini-player and the nav bar — visible gap.
- ❌ Forgetting `imePadding()` — keyboard covers the search/tag fields.
- ❌ Calling `enableEdgeToEdge()` after `setContent` — has no effect.
