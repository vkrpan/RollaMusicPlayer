---
name: debug-playback-issue
description: "A systematic workflow for diagnosing and fixing audio playback issues in RollaMusicPlayer, covering common problems with Media3 ExoPlayer, MediaSession, and background playback."
---

# Skill: Debug Playback Issue

## Overview
A systematic workflow for diagnosing and fixing audio playback issues in RollaMusicPlayer, covering common problems with Media3 ExoPlayer, MediaSession, and background playback.

## When to Use
- Audio playback not working
- Playback stops unexpectedly
- Audio stuttering or glitches
- Background playback issues
- Notification controls not working
- Audio focus problems

## Prerequisites
- Basic understanding of Media3 ExoPlayer
- Access to device logs
- Test device or emulator
- Sample audio files for testing

## Workflow Steps

### Step 1: Identify the Problem
**Goal**: Understand what's not working

**Actions**:
1. Reproduce the issue consistently
2. Note exact symptoms
3. Check when it occurs
4. Identify affected devices/versions
5. Gather error messages

**Questions to Answer**:
- Does playback start at all?
- Does it stop after a certain time?
- Is there an error message?
- Does it happen on all devices?
- Is it specific to certain audio files?

**Output**: Clear problem description

---

### Step 2: Check Logs
**Goal**: Find error messages and stack traces

**Actions**:
1. Connect device via ADB
2. Clear logcat
3. Reproduce the issue
4. Capture logs
5. Filter for relevant messages

**Commands**:
```
# Clear logs
adb logcat -c

# Capture logs
adb logcat > playback_logs.txt

# Filter for ExoPlayer
adb logcat | grep ExoPlayer

# Filter for MediaSession
adb logcat | grep MediaSession

# Filter for app logs
adb logcat | grep RollaMusicPlayer
```

**Look For**:
- Exception stack traces
- ExoPlayer error messages
- MediaSession warnings
- Audio focus messages
- Service lifecycle events

**Output**: Relevant log messages

---

### Step 3: Verify Basic Setup
**Goal**: Ensure fundamental configuration is correct

**Actions**:
1. Check ExoPlayer initialization
2. Verify MediaSession setup
3. Check audio attributes
4. Verify service configuration
5. Check permissions

**ExoPlayer Checklist**:
- [ ] ExoPlayer instance created correctly
- [ ] Audio attributes set
- [ ] Listeners attached
- [ ] Media items prepared
- [ ] Player released properly

**MediaSession Checklist**:
- [ ] MediaSession created
- [ ] Callback implemented
- [ ] Session active
- [ ] Metadata set
- [ ] Playback state updated

**Service Checklist**:
- [ ] Foreground service declared in manifest
- [ ] Service started correctly
- [ ] Notification posted
- [ ] Service not killed by system

**Output**: Configuration verified

---

### Step 4: Test Audio File
**Goal**: Rule out file-related issues

**Actions**:
1. Try different audio files
2. Check file format support
3. Verify file accessibility
4. Test with known-good file
5. Check file permissions

**Supported Formats**:
- MP3
- FLAC
- WAV
- OGG
- M4A
- AAC

**File Checks**:
```kotlin
// Check if file exists (local storage)
val file = File(path)
if (!file.exists()) {
    Log.e(TAG, "File not found: $path")
}

// Check if readable
if (!file.canRead()) {
    Log.e(TAG, "Cannot read file: $path")
}

// Check file size
Log.d(TAG, "File size: ${file.length()} bytes")

// Verify it's a local file (not a URL)
if (path.startsWith("http://") || path.startsWith("https://")) {
    Log.e(TAG, "ERROR: Network URL detected - app should only use local files!")
}
```

**Output**: File issues identified or ruled out

---

### Step 5: Check Audio Focus
**Goal**: Verify audio focus handling

**Actions**:
1. Check audio focus request
2. Verify focus change handling
3. Test with other audio apps
4. Check focus abandonment
5. Test phone calls interruption

**Audio Focus Issues**:
- Not requesting focus
- Not handling focus loss
- Not abandoning focus
- Wrong focus type

**Code to Check**:
```kotlin
// Request audio focus
val audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(audioAttributes)
    .setOnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Stop playback
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Pause playback
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Resume playback
            }
        }
    }
    .build()

val result = audioManager.requestAudioFocus(focusRequest)
```

**Output**: Audio focus issues identified

---

### Step 6: Check Service Lifecycle
**Goal**: Ensure service stays alive

**Actions**:
1. Verify foreground service
2. Check notification
3. Monitor service lifecycle
4. Check battery optimization
5. Test background restrictions

**Service Issues**:
- Service not in foreground
- Notification not posted
- Service killed by system
- Battery optimization enabled
- Background restrictions active

**Verification**:
```kotlin
// Start foreground service
startForeground(NOTIFICATION_ID, notification)

// Check if service is foreground
val am = getSystemService(ActivityManager::class.java)
val services = am.getRunningServices(Integer.MAX_VALUE)
services.find { it.service.className == PlaybackService::class.java.name }
    ?.let { Log.d(TAG, "Service foreground: ${it.foreground}") }
```

**Output**: Service lifecycle issues identified

---

### Step 7: Check MediaSession Integration
**Goal**: Verify MediaSession works correctly

**Actions**:
1. Check session creation
2. Verify callback implementation
3. Test notification controls
4. Check metadata updates
5. Verify state updates

**MediaSession Issues**:
- Session not created
- Callback not set
- Controls not working
- Metadata not updating
- State not syncing

**Code to Check**:
```kotlin
// Create MediaSession
val mediaSession = MediaSession.Builder(context, player)
    .setCallback(object : MediaSession.Callback {
        override fun onPlay() {
            player.play()
        }
        
        override fun onPause() {
            player.pause()
        }
        
        override fun onSkipToNext() {
            player.seekToNext()
        }
        
        override fun onSkipToPrevious() {
            player.seekToPrevious()
        }
    })
    .build()

// Set session active
mediaSession.isActive = true
```

**Output**: MediaSession issues identified

---

### Step 8: Test Playback States
**Goal**: Verify state transitions work

**Actions**:
1. Test play/pause
2. Test skip next/previous
3. Test seek
4. Test stop
5. Monitor state changes

**State Transitions to Test**:
- Idle → Buffering → Ready → Playing
- Playing → Paused
- Playing → Ended
- Any → Error

**State Monitoring**:
```kotlin
player.addListener(object : Player.Listener {
    override fun onPlaybackStateChanged(state: Int) {
        when (state) {
            Player.STATE_IDLE -> Log.d(TAG, "State: IDLE")
            Player.STATE_BUFFERING -> Log.d(TAG, "State: BUFFERING")
            Player.STATE_READY -> Log.d(TAG, "State: READY")
            Player.STATE_ENDED -> Log.d(TAG, "State: ENDED")
        }
    }
    
    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "Player error: ${error.message}", error)
    }
})
```

**Output**: State transition issues identified

---

### Step 9: Check Memory and Resources
**Goal**: Identify resource-related issues

**Actions**:
1. Monitor memory usage
2. Check for memory leaks
3. Verify resource cleanup
4. Test under low memory
5. Check thread usage

**Memory Issues**:
- Memory leaks
- Out of memory errors
- Resources not released
- Too many threads
- Large bitmap loading

**Tools**:
- Android Studio Profiler
- LeakCanary
- Memory Analyzer (MAT)

**Code to Check**:
```kotlin
// Release player
override fun onDestroy() {
    player.release()
    mediaSession.release()
    super.onDestroy()
}

// Cancel coroutines
override fun onCleared() {
    viewModelScope.cancel()
    super.onCleared()
}
```

**Output**: Resource issues identified

---

### Step 10: Implement Fix
**Goal**: Resolve the identified issue

**Actions**:
1. Implement solution
2. Test fix thoroughly
3. Verify no regressions
4. Update documentation
5. Add tests to prevent recurrence

**Common Fixes**:

**Fix 1: Audio Focus Not Requested**
```kotlin
// Request audio focus before playing
private fun requestAudioFocus(): Boolean {
    val result = audioManager.requestAudioFocus(focusRequest)
    return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
}

fun play() {
    if (requestAudioFocus()) {
        player.play()
    }
}
```

**Fix 2: Service Killed by System**
```kotlin
// Ensure foreground service
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(NOTIFICATION_ID, createNotification())
    return START_STICKY
}
```

**Fix 3: MediaSession Not Active**
```kotlin
// Activate session
mediaSession.isActive = true

// Keep session active
override fun onDestroy() {
    mediaSession.isActive = false
    mediaSession.release()
    super.onDestroy()
}
```

**Fix 4: Player Not Released**
```kotlin
// Proper cleanup
override fun onDestroy() {
    player.removeListener(playerListener)
    player.release()
    mediaSession.release()
    abandonAudioFocus()
    super.onDestroy()
}
```

**Output**: Issue resolved

---

## Common Issues & Solutions

### Issue: Playback Stops After Screen Lock
**Cause**: Service not in foreground or audio focus lost
**Solution**:
- Ensure service is foreground with notification
- Handle audio focus properly
- Use PARTIAL_WAKE_LOCK if needed

### Issue: Notification Controls Don't Work
**Cause**: MediaSession callback not implemented
**Solution**:
- Implement MediaSession.Callback
- Handle all playback actions
- Update playback state

### Issue: Audio Stuttering
**Cause**: Buffer too small or main thread blocking
**Solution**:
- Increase buffer size
- Move operations off main thread
- Optimize data loading

### Issue: Playback Doesn't Resume After Call
**Cause**: Audio focus not handled correctly
**Solution**:
- Handle AUDIOFOCUS_LOSS_TRANSIENT
- Resume on AUDIOFOCUS_GAIN
- Check ducking behavior

### Issue: App Crashes on Playback
**Cause**: Various (check logs)
**Solution**:
- Check exception stack trace
- Verify file accessibility
- Check permissions
- Validate player state

## Testing Checklist

After fixing, verify:
- [ ] Playback starts correctly from local files
- [ ] Play/pause works
- [ ] Skip next/previous works
- [ ] Seek works
- [ ] Background playback continues
- [ ] Notification controls work
- [ ] Audio focus handled correctly
- [ ] Service survives screen lock
- [ ] No memory leaks
- [ ] No crashes
- [ ] **Works in airplane mode**
- [ ] **No network calls attempted**
- [ ] **Only local files accessed**

## Prevention

To avoid future issues:
1. Write comprehensive tests
2. Test on multiple devices
3. Test different Android versions
4. Handle all error cases
5. Monitor crash reports
6. Keep ExoPlayer updated
7. Follow best practices
8. Document known issues

## Success Criteria

Issue is resolved when:
- [ ] Problem no longer reproduces
- [ ] Root cause identified
- [ ] Fix implemented and tested
- [ ] No regressions introduced
- [ ] Tests added to prevent recurrence
- [ ] Documentation updated
- [ ] Code reviewed
- [ ] **Verified to work offline**
- [ ] **No network dependencies introduced**

## Related Skills
- [Add New Screen](../add-new-screen/SKILL.md)
- [Release Build](../release-build/SKILL.md)

## Related Agents
- [Audio Engineer](../../agents/audio-engineer.md) - For audio expertise
- [Code Reviewer](../../agents/code-reviewer.md) - For code review
- [Test Writer](../../agents/test-writer.md) - For adding tests