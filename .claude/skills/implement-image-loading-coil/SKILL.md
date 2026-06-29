---
name: implement-image-loading-coil
description: "Step-by-step workflow for album-artwork loading with Coil in RollaMusicPlayer — a tuned singleton ImageLoader (memory + disk cache budgets), correctly sized AsyncImage for list/grid/full-screen, crossfade, placeholders, and content-URI artwork. Local images only; the disk cache is on-device, no network."
---

# Skill: Implement Image Loading (Coil)

## Overview
A workflow for loading album artwork efficiently with Coil. Artwork appears on every list row, the album grid, the player, and the widget, so getting caching and sizing right is the difference between smooth scrolling and jank/OOM. This sets up a tuned singleton `ImageLoader`, size-appropriate `AsyncImage` usage, crossfade and placeholders, and loading from local `content://` album-art URIs. The disk cache lives on-device; nothing is fetched from the network.

## When to Use
- Displaying album art anywhere (song rows, album grid, player, now-playing)
- Fixing image-related jank, flicker, or out-of-memory issues
- Tuning memory/disk cache budgets for large libraries

## Prerequisites
- Media scanning provides a local album-art `content://` URI per song (see `implement-media-scanning`)
- Hilt configured (to provide the singleton ImageLoader)
- Compose UI; Application class available

## Workflow Steps

### Step 1: Add Coil
**Goal**: Compose image loading

**Implementation**:
```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("io.coil-kt:coil-compose:2.6.0")
}
```
> Coil 3.x is also available; if adopting it, use `io.coil-kt.coil3:coil-compose` and the matching cache APIs. The patterns below are identical in spirit.

### Step 2: Provide a Tuned Singleton ImageLoader
**Goal**: One ImageLoader with explicit cache budgets and decode settings

**Implementation**:
```kotlin
// di/ImageModule.kt
@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun provideImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.25)   // ~25% of app memory for bitmaps
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("artwork_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)   // 150 MB local cache
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)     // local files have none; always cache
            // allowHardware(true) is the default and best for scrolling lists
            .build()
}
```

### Step 3: Make Coil Use the Singleton
**Goal**: Ensure Compose `AsyncImage` uses your tuned loader, not a default

**Implementation**:
```kotlin
// Application implements ImageLoaderFactory (Coil 2.x)
@HiltAndroidApp
class RollaApp : Application(), ImageLoaderFactory {
    @Inject lateinit var imageLoader: ImageLoader
    override fun newImageLoader(): ImageLoader = imageLoader
}
```
> Or pass `imageLoader = ...` explicitly to each `AsyncImage`. The factory approach is simplest.

### Step 4: Load Artwork at the Right Size
**Goal**: Never decode a 1000px bitmap into a 48dp slot

**Implementation**:
```kotlin
// presentation/common/AlbumArtwork.kt — UI owned by ui-builder; theme by m3-design-system-agent
@Composable
fun AlbumArtwork(
    artworkUri: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(artworkUri)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        placeholder = painterResource(R.drawable.artwork_placeholder),
        error = painterResource(R.drawable.artwork_placeholder),
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    )
}
```
Coil sizes the decode to the composable's measured bounds automatically — so a 56dp list thumbnail decodes small, while the full-screen player decodes large. Keep the same `artworkUri` so all three share cache entries.

### Step 5: Use It Across Surfaces
**Goal**: Consistent, cache-friendly usage everywhere

**Notes**:
- **Song row / album grid**: small `AlbumArtwork` (48–80dp). Stable `key` on the list item so Compose reuses entries (see `compose-performance-auditor`).
- **Player / now-playing**: large `AlbumArtwork`; same URI → served from cache after the list already loaded it.
- **Widget**: the widget does NOT use Coil — it needs a pre-decoded, downscaled `Bitmap` for the RemoteViews memory budget (see `implement-home-widget`/widget-agent). You can share the decoded bitmap, but the widget path stays manual.
- **Notification**: load the bitmap via Coil's `enqueue`/`execute` on a background dispatcher in the audio service, then pass to `MediaMetadata`/`NotificationCompat`.

### Step 6: Verify
**Checklist**:
- [ ] Singleton ImageLoader used everywhere (factory or explicit)
- [ ] Memory and disk cache budgets set explicitly
- [ ] List thumbnails decode small; player decodes large; same URI shares cache
- [ ] Crossfade + placeholder + error states present
- [ ] Smooth scroll through a large library (no jank/flicker)
- [ ] No OutOfMemory on big artwork
- [ ] Disk cache is on-device; no network requests (verify in airplane mode)
- [ ] Artwork has content descriptions

## Related Files
- `di/ImageModule.kt` — singleton ImageLoader
- `RollaApp.kt` — ImageLoaderFactory
- `presentation/common/AlbumArtwork.kt` — reusable artwork composable
- `res/drawable/artwork_placeholder.xml` — themed placeholder

## Notes
- `allowHardware(true)` (default) gives the best scroll performance; only disable it if you must read pixels (e.g. palette extraction) — do that on a separate request.
- Keep one ImageLoader for the whole app; multiple loaders fragment the cache.
- Always supply a placeholder so rows don't pop in; reuse the same drawable for error.
- This is local-only image loading — Coil's disk cache stays in `cacheDir`, consistent with the offline/privacy architecture.

## Common Pitfalls
- ❌ Creating an `ImageLoader` per screen — defeats caching, wastes memory.
- ❌ No explicit cache budgets — defaults may be too small for a large library or too large for low-RAM devices.
- ❌ Loading full-resolution art into list thumbnails — jank and OOM; let Coil size to the slot.
- ❌ Using Coil for the widget — it needs a manual downscaled bitmap, not an async loader.
