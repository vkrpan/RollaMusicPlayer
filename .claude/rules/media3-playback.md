# Media3 Playback Guidelines

## Overview
Best practices and patterns for implementing audio playback using Media3 ExoPlayer in RollaMusicPlayer.

## ExoPlayer Setup

### Basic Initialization
```kotlin
class PlaybackService : Service() {
    private lateinit var player: ExoPlayer
    
    override fun onCreate() {
        super.onCreate()
        
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            
        player.addListener(playerListener)
    }
    
    override fun onDestroy() {
        player.removeListener(playerListener)
        player.release()
        super.onDestroy()
    }
}
```

### Audio Attributes
- Always set appropriate audio attributes
- Use `CONTENT_TYPE_MUSIC` for music playback
- Use `USAGE_MEDIA` for media usage
- Enable audio focus handling

```kotlin
// Good
val audioAttributes = AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .build()

player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)

// Bad
// Not setting audio attributes at all
```

### Wake Lock
- Use wake lock for background playback
- Set in ExoPlayer builder
- Use `WAKE_MODE_LOCAL` for audio

```kotlin
// Good
ExoPlayer.Builder(context)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()

// Bad
// Not using wake lock - playback may stop when screen locks
```

## Media Items

### Creating Media Items
```kotlin
// Good - Complete media item
fun createMediaItem(song: Song): MediaItem {
    return MediaItem.Builder()
        .setMediaId(song.id)
        .setUri(song.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.artworkUri)
                .setDurationMs(song.duration)
                .build()
        )
        .build()
}

// Bad - Minimal media item
fun createMediaItem(song: Song): MediaItem {
    return MediaItem.fromUri(song.uri)
}
```

### Setting Media Items
```kotlin
// Good - Set list and prepare
fun setPlaylist(songs: List<Song>) {
    val mediaItems = songs.map { createMediaItem(it) }
    player.setMediaItems(mediaItems)
    player.prepare()
}

// Good - Add to existing playlist
fun addToQueue(song: Song) {
    player.addMediaItem(createMediaItem(song))
}

// Bad - Not preparing
fun setPlaylist(songs: List<Song>) {
    val mediaItems = songs.map { createMediaItem(it) }
    player.setMediaItems(mediaItems)
    // Missing player.prepare()
}
```

## Player Listener

### Implementing Listener
```kotlin
private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> handleIdle()
            Player.STATE_BUFFERING -> handleBuffering()
            Player.STATE_READY -> handleReady()
            Player.STATE_ENDED -> handleEnded()
        }
    }
    
    override fun onPlayerError(error: PlaybackException) {
        handleError(error)
    }
    
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        handleMediaItemTransition(mediaItem, reason)
    }
    
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handlePlayingStateChanged(isPlaying)
    }
}
```

### State Handling
```kotlin
// Good - Handle all states
private fun handlePlaybackState(state: Int) {
    when (state) {
        Player.STATE_IDLE -> {
            // Player is idle, no media set
            updateNotification(isPlaying = false)
        }
        Player.STATE_BUFFERING -> {
            // Loading media
            showBuffering()
        }
        Player.STATE_READY -> {
            // Ready to play
            hideBuffering()
        }
        Player.STATE_ENDED -> {
            // Playback ended
            handlePlaybackEnded()
        }
    }
}

// Bad - Incomplete handling
private fun handlePlaybackState(state: Int) {
    if (state == Player.STATE_READY) {
        // Only handling one state
    }
}
```

### Error Handling
```kotlin
// Good - Comprehensive error handling
override fun onPlayerError(error: PlaybackException) {
    when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            // Network error
            showError("Network error occurred")
        }
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> {
            // File not found
            showError("Audio file not found")
        }
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> {
            // Decoder error
            showError("Unable to play this audio format")
        }
        else -> {
            // Generic error
            showError("Playback error: ${error.message}")
        }
    }
    
    // Log for debugging
    Log.e(TAG, "Player error", error)
    
    // Try to recover
    tryRecovery()
}

// Bad - Ignoring errors
override fun onPlayerError(error: PlaybackException) {
    // Do nothing
}
```

## MediaSession Integration

### Creating MediaSession
```kotlin
class PlaybackService : MediaSessionService() {
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    
    override fun onCreate() {
        super.onCreate()
        
        player = createPlayer()
        
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(MediaSessionCallback())
            .build()
    }
    
    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession = mediaSession
    
    override fun onDestroy() {
        mediaSession.release()
        player.release()
        super.onDestroy()
    }
}
```

### MediaSession Callback
```kotlin
private inner class MediaSessionCallback : MediaSession.Callback {
    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        val sessionCommands = MediaSession.ConnectionResult
            .DEFAULT_SESSION_COMMANDS
            .buildUpon()
            .add(customCommand)
            .build()
            
        return MediaSession.ConnectionResult.accept(
            sessionCommands,
            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
        )
    }
    
    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        // Handle custom commands
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
```

## Notification

### Creating Notification
```kotlin
private fun createNotification(): Notification {
    val mediaSession = mediaSession ?: return createDefaultNotification()
    
    return NotificationCompat.Builder(this, CHANNEL_ID)
        .setStyle(
            MediaStyle()
                .setMediaSession(mediaSession.sessionCompatToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getCurrentSong()?.title)
        .setContentText(getCurrentSong()?.artist)
        .setLargeIcon(getAlbumArt())
        .addAction(createAction(ACTION_PREVIOUS))
        .addAction(createPlayPauseAction())
        .addAction(createAction(ACTION_NEXT))
        .setContentIntent(createContentIntent())
        .setDeleteIntent(createDeleteIntent())
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()
}

private fun createAction(action: String): NotificationCompat.Action {
    val intent = Intent(this, PlaybackService::class.java).apply {
        this.action = action
    }
    val pendingIntent = PendingIntent.getService(
        this,
        action.hashCode(),
        intent,
        PendingIntent.FLAG_IMMUTABLE
    )
    
    return when (action) {
        ACTION_PREVIOUS -> NotificationCompat.Action(
            R.drawable.ic_skip_previous,
            "Previous",
            pendingIntent
        )
        ACTION_NEXT -> NotificationCompat.Action(
            R.drawable.ic_skip_next,
            "Next",
            pendingIntent
        )
        else -> throw IllegalArgumentException("Unknown action: $action")
    }
}
```

### Updating Notification
```kotlin
// Good - Update on state changes
private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateNotification()
    }
    
    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int
    ) {
        updateNotification()
    }
}

private fun updateNotification() {
    val notification = createNotification()
    notificationManager.notify(NOTIFICATION_ID, notification)
}

// Bad - Not updating notification
```

## Playback Control

### Play/Pause
```kotlin
// Good
fun togglePlayback() {
    if (player.isPlaying) {
        player.pause()
    } else {
        player.play()
    }
}

// Also good - Direct control
fun play() {
    player.play()
}

fun pause() {
    player.pause()
}
```

### Skip Next/Previous
```kotlin
// Good
fun skipToNext() {
    if (player.hasNextMediaItem()) {
        player.seekToNext()
    }
}

fun skipToPrevious() {
    if (player.hasPreviousMediaItem()) {
        player.seekToPrevious()
    }
}

// Bad - Not checking if items exist
fun skipToNext() {
    player.seekToNext() // May crash if no next item
}
```

### Seek
```kotlin
// Good
fun seekTo(positionMs: Long) {
    if (positionMs >= 0 && positionMs <= player.duration) {
        player.seekTo(positionMs)
    }
}

// Bad - No validation
fun seekTo(positionMs: Long) {
    player.seekTo(positionMs)
}
```

### Shuffle and Repeat
```kotlin
// Good
fun setShuffleMode(enabled: Boolean) {
    player.shuffleModeEnabled = enabled
}

fun setRepeatMode(mode: RepeatMode) {
    player.repeatMode = when (mode) {
        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
    }
}

enum class RepeatMode {
    OFF, ONE, ALL
}
```

## Background Playback

### Foreground Service
```kotlin
class PlaybackService : MediaSessionService() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification())
        
        return START_STICKY
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Handle task removal
        if (!player.isPlaying) {
            stopSelf()
        }
    }
}
```

### Service Manifest
```xml
<service
    android:name=".service.PlaybackService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="true">
    <intent-filter>
        <action android:name="androidx.media3.session.MediaSessionService"/>
    </intent-filter>
</service>
```

## Audio Focus

### Handling Audio Focus
```kotlin
// Media3 handles audio focus automatically when configured
ExoPlayer.Builder(context)
    .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
    .build()

// Manual handling if needed
private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
    when (focusChange) {
        AudioManager.AUDIOFOCUS_GAIN -> {
            // Resume playback
            player.play()
            player.volume = 1.0f
        }
        AudioManager.AUDIOFOCUS_LOSS -> {
            // Stop playback
            player.pause()
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
            // Pause temporarily
            player.pause()
        }
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
            // Lower volume
            player.volume = 0.3f
        }
    }
}
```

## Equalizer Integration

### Setting Up Equalizer
```kotlin
class EqualizerManager(private val player: ExoPlayer) {
    private var equalizer: Equalizer? = null
    
    fun initialize() {
        val audioSessionId = player.audioSessionId
        if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
        }
    }
    
    fun setBandLevel(band: Short, level: Short) {
        equalizer?.setBandLevel(band, level)
    }
    
    fun release() {
        equalizer?.release()
        equalizer = null
    }
}
```

### Applying Equalizer Settings
```kotlin
// Good - Apply settings when player is ready
private val playerListener = object : Player.Listener {
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) {
            applyEqualizerSettings()
        }
    }
}

private fun applyEqualizerSettings() {
    val settings = loadEqualizerSettings()
    settings.forEach { (band, level) ->
        equalizerManager.setBandLevel(band, level)
    }
}
```

## Performance Optimization

### Buffer Configuration
```kotlin
// Good - Custom buffer for better performance
val loadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 15000,
        /* maxBufferMs = */ 50000,
        /* bufferForPlaybackMs = */ 2500,
        /* bufferForPlaybackAfterRebufferMs = */ 5000
    )
    .build()

ExoPlayer.Builder(context)
    .setLoadControl(loadControl)
    .build()
```

### Memory Management
```kotlin
// Good - Release resources properly
override fun onDestroy() {
    player.removeListener(playerListener)
    player.stop()
    player.clearMediaItems()
    player.release()
    
    equalizerManager.release()
    mediaSession.release()
    
    super.onDestroy()
}

// Bad - Not releasing resources
override fun onDestroy() {
    super.onDestroy()
}
```

## Testing

### Testing Playback
```kotlin
@Test
fun `play starts playback`() = runTest {
    // Given
    val song = createTestSong()
    player.setMediaItem(createMediaItem(song))
    player.prepare()
    
    // When
    player.play()
    
    // Then
    assertTrue(player.isPlaying)
}

@Test
fun `pause stops playback`() = runTest {
    // Given
    player.play()
    
    // When
    player.pause()
    
    // Then
    assertFalse(player.isPlaying)
}
```

## Common Issues

### Issue: Playback Stops on Screen Lock
**Solution**: Use wake lock and foreground service
```kotlin
ExoPlayer.Builder(context)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()

startForeground(NOTIFICATION_ID, notification)
```

### Issue: Audio Focus Not Working
**Solution**: Enable audio focus handling
```kotlin
player.setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
```

### Issue: Notification Not Updating
**Solution**: Update on player state changes
```kotlin
override fun onIsPlayingChanged(isPlaying: Boolean) {
    updateNotification()
}
```

### Issue: Memory Leak
**Solution**: Release all resources
```kotlin
override fun onDestroy() {
    player.removeListener(playerListener)
    player.release()
    mediaSession.release()
    super.onDestroy()
}
```

## Best Practices

1. **Always prepare the player** before playing
2. **Handle all player states** in listener
3. **Release resources** in onDestroy
4. **Use MediaSession** for system integration
5. **Handle audio focus** properly
6. **Update notification** on state changes
7. **Use foreground service** for background playback
8. **Handle errors** gracefully
9. **Test on multiple devices** and Android versions
10. **Monitor battery usage** and optimize

## References

- [Media3 Documentation](https://developer.android.com/guide/topics/media/media3)
- [ExoPlayer Guide](https://developer.android.com/guide/topics/media/exoplayer)
- [MediaSession Guide](https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide)
- [Background Playback](https://developer.android.com/guide/topics/media/media3/getting-started/playing-in-background)