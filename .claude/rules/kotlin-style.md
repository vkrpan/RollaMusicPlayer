# Kotlin Style Guide

## Overview
Coding style conventions for Kotlin development in RollaMusicPlayer, following official Kotlin conventions and Android best practices.

## Naming Conventions

### Classes and Interfaces
- Use PascalCase
- Names should be nouns or noun phrases
- Interfaces can be adjectives (e.g., `Playable`)

```kotlin
// Good
class MusicPlayer
class PlaybackService
interface AudioRenderer

// Bad
class musicPlayer
class playback_service
interface IAudioRenderer
```

### Functions
- Use camelCase
- Names should be verbs or verb phrases
- Boolean functions should start with `is`, `has`, `can`, `should`

```kotlin
// Good
fun playMusic()
fun isPlaying(): Boolean
fun hasPermission(): Boolean
fun canPlayNext(): Boolean

// Bad
fun PlayMusic()
fun playing(): Boolean
fun permission(): Boolean
```

### Properties
- Use camelCase
- Boolean properties should start with `is`, `has`, `can`
- Avoid prefixes like `m` or `_`

```kotlin
// Good
val currentSong: Song
var isPlaying: Boolean
val hasAudioFocus: Boolean

// Bad
val CurrentSong: Song
var m_isPlaying: Boolean
val _hasAudioFocus: Boolean
```

### Constants
- Use UPPER_SNAKE_CASE
- Place in companion object or top-level

```kotlin
// Good
const val MAX_VOLUME = 100
const val DEFAULT_BUFFER_SIZE = 8192

companion object {
    const val NOTIFICATION_ID = 1001
}

// Bad
const val maxVolume = 100
const val defaultBufferSize = 8192
```

### Package Names
- Use lowercase
- No underscores
- Use reverse domain notation

```kotlin
// Good
package com.rolla.musicplayer.presentation.player
package com.rolla.musicplayer.data.repository

// Bad
package com.rolla.MusicPlayer.Presentation.Player
package com.rolla.music_player.data.repository
```

## Code Organization

### File Structure
1. Package declaration
2. Import statements (sorted)
3. Top-level declarations
4. Class declaration

```kotlin
package com.rolla.musicplayer.presentation.player

import android.content.Context
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

const val DEFAULT_VOLUME = 50

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: MusicRepository
) : ViewModel() {
    // Class body
}
```

### Class Member Order
1. Companion object
2. Properties (abstract, override, public, internal, protected, private)
3. Init blocks
4. Constructors
5. Abstract methods
6. Override methods
7. Public methods
8. Internal methods
9. Protected methods
10. Private methods
11. Inner classes

```kotlin
class MusicPlayer {
    companion object {
        const val TAG = "MusicPlayer"
    }
    
    private val songs = mutableListOf<Song>()
    var currentSong: Song? = null
    
    init {
        // Initialization
    }
    
    fun play() {
        // Public method
    }
    
    private fun loadSong() {
        // Private method
    }
    
    inner class PlaybackState {
        // Inner class
    }
}
```

## Formatting

### Indentation
- Use 4 spaces (no tabs)
- Continuation indent: 4 spaces

```kotlin
// Good
fun longFunctionName(
    parameter1: String,
    parameter2: Int
): Boolean {
    return true
}

// Bad
fun longFunctionName(
  parameter1: String,
  parameter2: Int
): Boolean {
  return true
}
```

### Line Length
- Maximum 120 characters
- Break long lines at logical points

```kotlin
// Good
val message = "This is a long message that needs to be " +
    "split across multiple lines for readability"

// Bad
val message = "This is a very long message that exceeds the maximum line length and should be split but isn't"
```

### Blank Lines
- One blank line between functions
- One blank line between logical sections
- No blank line at start or end of blocks

```kotlin
// Good
fun function1() {
    // Implementation
}

fun function2() {
    // Implementation
}

// Bad
fun function1() {

    // Implementation
}
fun function2() {
    // Implementation

}
```

### Braces
- Opening brace on same line
- Closing brace on new line
- Use braces even for single-line if/else

```kotlin
// Good
if (condition) {
    doSomething()
}

when (value) {
    1 -> doOne()
    2 -> doTwo()
}

// Bad
if (condition)
    doSomething()

if (condition) doSomething()
```

## Language Features

### Null Safety
- Prefer non-null types
- Use `?` for nullable types
- Use `?.` for safe calls
- Use `?:` for default values
- Avoid `!!` unless absolutely necessary

```kotlin
// Good
val name: String = song.title ?: "Unknown"
val artist: String? = song.artist
val duration = song.duration?.toInt() ?: 0

// Bad
val name: String = song.title!!
val artist: String = song.artist!!
```

### Type Inference
- Use type inference when type is obvious
- Specify type when it improves readability

```kotlin
// Good
val count = 10
val songs = listOf<Song>()
val result: Result<Song> = repository.getSong(id)

// Bad
val count: Int = 10
val songs: List<Song> = listOf()
val result = repository.getSong(id)
```

### Data Classes
- Use for data-holding classes
- Keep them simple
- Avoid business logic

```kotlin
// Good
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val duration: Long
)

// Bad
data class Song(
    val id: String,
    val title: String
) {
    fun play() {
        // Business logic doesn't belong here
    }
}
```

### Extension Functions
- Use to add functionality to existing classes
- Keep them focused and simple
- Place in appropriate file

```kotlin
// Good
fun Context.showToast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun Long.formatDuration(): String {
    val minutes = this / 60000
    val seconds = (this % 60000) / 1000
    return String.format("%d:%02d", minutes, seconds)
}

// Bad
fun Context.doEverything() {
    // Too much functionality
}
```

### Scope Functions
- Use appropriate scope function
- `let`: null checks, transformations
- `apply`: object configuration
- `run`: execute block and return result
- `also`: side effects
- `with`: multiple calls on same object

```kotlin
// Good
val song = Song("1", "Title", "Artist", 180000).apply {
    // Configure song
}

song?.let {
    player.play(it)
}

// Bad
val song = Song("1", "Title", "Artist", 180000)
if (song != null) {
    player.play(song)
}
```

### Collections
- Use immutable collections by default
- Use `listOf`, `setOf`, `mapOf` for immutable
- Use `mutableListOf`, etc. for mutable

```kotlin
// Good
val songs: List<Song> = listOf()
val mutableSongs: MutableList<Song> = mutableListOf()

// Bad
val songs: MutableList<Song> = mutableListOf() // When immutable would work
```

### String Templates
- Use string templates instead of concatenation
- Use `${}` for expressions

```kotlin
// Good
val message = "Playing: ${song.title} by ${song.artist}"
val count = "Total: $songCount songs"

// Bad
val message = "Playing: " + song.title + " by " + song.artist
```

## Comments and Documentation

### KDoc
- Use for public API
- Include description, parameters, return value
- Use `@param`, `@return`, `@throws`

```kotlin
/**
 * Plays the specified song.
 *
 * @param song The song to play
 * @return true if playback started successfully
 * @throws IllegalStateException if player is not initialized
 */
fun play(song: Song): Boolean {
    // Implementation
}
```

### Inline Comments
- Use sparingly
- Explain why, not what
- Keep comments up to date

```kotlin
// Good
// Delay needed to prevent audio glitches on some devices
delay(100)

// Bad
// Set volume to 50
volume = 50
```

### TODO Comments
- Use for temporary code
- Include ticket number if applicable
- Format: `// TODO: Description`

```kotlin
// TODO: Implement shuffle algorithm
// TODO(#123): Fix memory leak in player
```

## Best Practices

### Immutability
- Prefer `val` over `var`
- Use immutable collections
- Make classes immutable when possible

```kotlin
// Good
val songs = listOf<Song>()
val player = MusicPlayer(songs)

// Bad
var songs = mutableListOf<Song>()
var player = MusicPlayer(songs)
```

### Single Responsibility
- Each class should have one responsibility
- Keep functions small and focused
- Extract complex logic into separate functions

```kotlin
// Good
class SongRepository {
    fun getSongs(): List<Song> { }
}

class PlaybackController {
    fun play(song: Song) { }
}

// Bad
class MusicManager {
    fun getSongs(): List<Song> { }
    fun play(song: Song) { }
    fun updateUI() { }
    fun saveToDatabase() { }
}
```

### Error Handling
- Use Result or sealed classes for errors
- Don't swallow exceptions
- Provide meaningful error messages

```kotlin
// Good
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val message: String) : Result<Nothing>()
}

suspend fun getSong(id: String): Result<Song> {
    return try {
        val song = repository.getSong(id)
        Result.Success(song)
    } catch (e: Exception) {
        Result.Error("Failed to load song: ${e.message}")
    }
}

// Bad
fun getSong(id: String): Song? {
    try {
        return repository.getSong(id)
    } catch (e: Exception) {
        return null // Lost error information
    }
}
```

### Coroutines
- Use appropriate scope
- Handle cancellation
- Use structured concurrency

```kotlin
// Good
viewModelScope.launch {
    try {
        val songs = repository.getSongs()
        _uiState.update { it.copy(songs = songs) }
    } catch (e: CancellationException) {
        throw e // Don't catch cancellation
    } catch (e: Exception) {
        _uiState.update { it.copy(error = e.message) }
    }
}

// Bad
GlobalScope.launch {
    val songs = repository.getSongs()
    _uiState.value = songs // Not cancellation-aware
}
```

## Android-Specific

### Context Usage
- Don't hold Activity context in long-lived objects
- Use Application context when possible
- Pass context as parameter, don't store

```kotlin
// Good
class MusicRepository(
    private val context: Context // Application context
) {
    fun loadSongs() {
        // Use context
    }
}

// Bad
class MusicRepository {
    private lateinit var activity: Activity // Memory leak!
}
```

### Resource Access
- Use string resources for user-facing text
- Use dimension resources for sizes
- Use color resources for colors

```kotlin
// Good
val title = context.getString(R.string.app_name)
val padding = context.resources.getDimensionPixelSize(R.dimen.padding_default)

// Bad
val title = "RollaMusicPlayer"
val padding = 16
```

## Code Review Checklist

Before submitting code, verify:
- [ ] Follows naming conventions
- [ ] Proper indentation and formatting
- [ ] No unused imports
- [ ] Comments are meaningful
- [ ] No magic numbers
- [ ] Null safety handled
- [ ] Error handling implemented
- [ ] No memory leaks
- [ ] Resources cleaned up
- [ ] Tests written
- [ ] **No network dependencies**
- [ ] **No internet permission**
- [ ] **Uses only local storage**
- [ ] **No analytics or tracking**
- [ ] **Works in airplane mode**

## Tools

### Ktlint
Use ktlint for automatic formatting:
```
./gradlew ktlintFormat
```

### Detekt
Use detekt for static analysis:
```
./gradlew detekt
```

### Android Lint
Run lint checks:
```
./gradlew lint
```

## References

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Kotlin Style Guide](https://developer.android.com/kotlin/style-guide)
- [Effective Kotlin](https://kt.academy/book/effectivekotlin)