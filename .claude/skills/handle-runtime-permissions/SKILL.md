---
name: handle-runtime-permissions
description: "Step-by-step workflow for requesting and handling the local media permissions RollaMusicPlayer needs — READ_MEDIA_AUDIO on Android 13+, READ_EXTERNAL_STORAGE on older versions — with rationale UI, permanent-denial handling, and a Compose permission gate. Storage-only, no internet permission."
---

# Skill: Handle Runtime Permissions

## Overview
A workflow for correctly requesting the only runtime permission RollaMusicPlayer needs: read access to local audio. The required permission differs by Android version (READ_MEDIA_AUDIO on API 33+, READ_EXTERNAL_STORAGE below), so this skill centralizes that branching, shows a rationale before requesting, handles permanent denial gracefully (route to Settings), and gates the library UI on the granted state. The app requests **no internet permission** — storage access only.

## When to Use
- First launch / before the first media scan
- Building the permission gate that wraps Library/Player content
- Handling "denied" and "don't ask again" states
- Re-checking permission after the user returns from system Settings

## Prerequisites
- Min SDK 24, target SDK 34 (per project)
- Jetpack Compose with `androidx.activity:activity-compose` (for `rememberLauncherForActivityResult`)
- The media-scanning workflow exists or will run once permission is granted (see `implement-media-scanning`)

## Permission Matrix

| Android version | Permission to request | Manifest declaration |
| --- | --- | --- |
| 13+ (API 33+) | `READ_MEDIA_AUDIO` | `READ_MEDIA_AUDIO` |
| 7–12 (API 24–32) | `READ_EXTERNAL_STORAGE` | `READ_EXTERNAL_STORAGE` with `android:maxSdkVersion="32"` |

> Do NOT request WRITE_EXTERNAL_STORAGE for reading or scanning. Writing tags to files is handled separately via scoped-storage write requests (see `implement-tag-editor`). Never add the INTERNET permission — the app is fully offline.

## Workflow Steps

### Step 1: Declare Permissions in the Manifest
**Goal**: Declare only the storage permissions needed, scoped by SDK

**Implementation**:
```xml
<!-- AndroidManifest.xml -->
<!-- Android 13+ -->
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />

<!-- Android 12 and below -->
<uses-permission
    android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />

<!-- NO android.permission.INTERNET — app is fully offline -->
```

### Step 2: Centralize the Permission Name
**Goal**: One source of truth for which permission to use on this device

**Implementation**:
```kotlin
// util/MediaPermission.kt
object MediaPermission {
    val name: String
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
```

### Step 3: Model the Permission State
**Goal**: Represent the states the UI must react to

**Implementation**:
```kotlin
// permission/PermissionState.kt
enum class MediaPermissionStatus {
    GRANTED,            // proceed to library/scan
    NEEDS_REQUEST,      // not yet asked, or can ask again
    SHOW_RATIONALE,     // denied once, explain why before re-asking
    PERMANENTLY_DENIED  // "don't ask again" — must route to Settings
}
```

### Step 4: Build a Reusable Compose Permission Gate
**Goal**: Wrap content so it only shows once permission is granted; otherwise show rationale / settings UI

**Implementation**:
```kotlin
// permission/MediaPermissionGate.kt
@Composable
fun MediaPermissionGate(
    onGranted: () -> Unit,                 // e.g. trigger the media scan
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    var status by remember {
        mutableStateOf(
            if (MediaPermission.isGranted(context)) MediaPermissionStatus.GRANTED
            else MediaPermissionStatus.NEEDS_REQUEST
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = when {
            granted -> MediaPermissionStatus.GRANTED
            activity.shouldShowRequestPermissionRationale(MediaPermission.name) ->
                MediaPermissionStatus.SHOW_RATIONALE
            else -> MediaPermissionStatus.PERMANENTLY_DENIED
        }
    }

    // Re-check when returning from Settings (lifecycle RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && MediaPermission.isGranted(context)) {
                status = MediaPermissionStatus.GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(status) {
        if (status == MediaPermissionStatus.GRANTED) onGranted()
    }

    when (status) {
        MediaPermissionStatus.GRANTED -> content()

        MediaPermissionStatus.NEEDS_REQUEST -> PermissionPrompt(
            message = "RollaMusicPlayer needs access to the audio on your device to build your library. Your music never leaves your phone.",
            buttonText = "Allow access",
            onClick = { launcher.launch(MediaPermission.name) },
            modifier = modifier
        )

        MediaPermissionStatus.SHOW_RATIONALE -> PermissionPrompt(
            message = "Without audio access there's nothing to play. Grant access to scan and play your local music. No data is collected or uploaded.",
            buttonText = "Grant access",
            onClick = { launcher.launch(MediaPermission.name) },
            modifier = modifier
        )

        MediaPermissionStatus.PERMANENTLY_DENIED -> PermissionPrompt(
            message = "Audio access is turned off. Open Settings to enable it so RollaMusicPlayer can load your music.",
            buttonText = "Open Settings",
            onClick = { activity.openAppSettings() },
            modifier = modifier
        )
    }
}

private fun Activity.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
    )
}
```

### Step 5: Build the Prompt UI (themed, offline-appropriate)
**Goal**: A reusable, accessible explainer card

**Implementation**:
```kotlin
// permission/PermissionPrompt.kt
@Composable
fun PermissionPrompt(
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.LibraryMusic,
            contentDescription = null,
            modifier = Modifier.size(56.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClick) { Text(buttonText) }
    }
}
```

### Step 6: Wire the Gate at the Library Entry Point
**Goal**: Trigger the scan only after permission is granted

**Implementation**:
```kotlin
@Composable
fun LibraryRoute(viewModel: LibraryViewModel = hiltViewModel()) {
    MediaPermissionGate(
        onGranted = { viewModel.onPermissionGranted() }  // kicks off media scan
    ) {
        LibraryScreen(viewModel = viewModel)
    }
}
```

### Step 7: Verify
**Goal**: Confirm correct behavior across versions and denial paths

**Checklist**:
- [ ] On API 33+ device, requests READ_MEDIA_AUDIO; on API ≤32, requests READ_EXTERNAL_STORAGE
- [ ] First launch shows rationale before the system dialog
- [ ] Granting proceeds to the library and triggers the scan exactly once
- [ ] Denying once shows the rationale state and can re-request
- [ ] "Don't ask again" shows the Settings route; returning from Settings with permission granted auto-proceeds
- [ ] No INTERNET or WRITE permission requested
- [ ] Works in airplane mode
- [ ] Prompt UI uses theme tokens and has content descriptions / 48dp targets

## Related Files
- `util/MediaPermission.kt` — version-aware permission name + check
- `permission/PermissionState.kt` — status model
- `permission/MediaPermissionGate.kt` — Compose gate
- `permission/PermissionPrompt.kt` — themed explainer UI
- `AndroidManifest.xml` — permission declarations

## Notes
- Re-check permission on `ON_RESUME` so returning from Settings updates the UI without a restart.
- `shouldShowRequestPermissionRationale` returns false both before the first ask and after permanent denial — distinguish them by tracking whether you've asked, or treat the post-callback false as permanent denial (as above).
- Keep all messaging privacy-forward ("your music never leaves your phone") to match the app's positioning.
- This is the only runtime permission flow in the app. Tag writing uses a separate scoped-storage write request, not a runtime permission (see `implement-tag-editor`).

## Common Pitfalls
- ❌ Requesting WRITE_EXTERNAL_STORAGE to "be safe" — not needed for reading and flagged by code-reviewer.
- ❌ Hardcoding READ_EXTERNAL_STORAGE on all versions — silently fails to grant audio access on Android 13+.
- ❌ Launching the scan from composition instead of from the granted callback — causes repeated scans on recomposition.
