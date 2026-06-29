---
name: add-dependency-injection-hilt
description: "Comprehensive workflow for setting up and using Hilt dependency injection in an Android application. This skill covers module creation, dependency provision, scoping strategies, and testing with Hilt."
---

# Skill: Add Dependency Injection (Hilt)

## Overview
Comprehensive workflow for setting up and using Hilt dependency injection in an Android application. This skill covers module creation, dependency provision, scoping strategies, and testing with Hilt.

## When to Use
- Setting up DI in a new project
- Adding new dependencies to the DI graph
- Refactoring to use dependency injection
- Implementing testable architecture
- Managing complex dependency trees

## Prerequisites
- Basic understanding of dependency injection concepts
- Kotlin and Android development knowledge
- Project uses Kotlin with KSP (Kotlin Symbol Processing)
- Understanding of Android component lifecycle

## Workflow Steps

### Step 1: Add Hilt Dependencies
**Goal**: Configure project for Hilt

**Actions**:
1. Add Hilt plugin to project-level build.gradle
2. Add Hilt dependencies to app-level build.gradle
3. Enable KSP for annotation processing
4. Sync project

**Implementation**:
```kotlin
// project-level build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android") version "2.48" apply false
}

// app-level build.gradle.kts
plugins {
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

dependencies {
    // Hilt
    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-compiler:2.48")
    
    // Hilt Navigation Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Hilt Testing
    androidTestImplementation("com.google.dagger:hilt-android-testing:2.48")
    kspAndroidTest("com.google.dagger:hilt-compiler:2.48")
    testImplementation("com.google.dagger:hilt-android-testing:2.48")
    kspTest("com.google.dagger:hilt-compiler:2.48")
    
    // Hilt Worker (if using WorkManager)
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
}
```

### Step 2: Setup Application Class
**Goal**: Initialize Hilt in the application

**Actions**:
1. Create or modify Application class
2. Add @HiltAndroidApp annotation
3. Update AndroidManifest.xml

**Implementation**:
```kotlin
// MusicPlayerApplication.kt
@HiltAndroidApp
class MusicPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Application initialization
    }
}
```

```xml
<!-- AndroidManifest.xml -->
<application
    android:name=".MusicPlayerApplication"
    ...>
</application>
```

### Step 3: Annotate Android Components
**Goal**: Enable injection in Activities, Fragments, Services

**Actions**:
1. Add @AndroidEntryPoint to Activities
2. Add @AndroidEntryPoint to Fragments
3. Add @AndroidEntryPoint to Services
4. Use @HiltViewModel for ViewModels

**Implementation**:
```kotlin
// Activity
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Compose content
        }
    }
}

// ViewModel
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playbackController: PlaybackController
) : ViewModel() {
    // ViewModel implementation
}

// Service
@AndroidEntryPoint
class PlaybackService : Service() {
    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var mediaSession: MediaSession
    
    // Service implementation
}

// Compose Screen (ViewModel injection)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel = hiltViewModel()
) {
    // Screen implementation
}
```

### Step 4: Create Hilt Modules
**Goal**: Organize dependency provision

**Actions**:
1. Create module classes for different concerns
2. Use @Module and @InstallIn annotations
3. Organize by feature or layer

**Module Organization**:
```
di/
├── AppModule.kt           # Application-level dependencies
├── DatabaseModule.kt      # Room database
├── RepositoryModule.kt    # Repository bindings
├── PlaybackModule.kt      # Media3 ExoPlayer
├── DataSourceModule.kt    # Data sources
└── DispatcherModule.kt    # Coroutine dispatchers
```

### Step 5: Provide Dependencies
**Goal**: Define how dependencies are created

**Actions**:
1. Use @Provides for concrete implementations
2. Use @Binds for interface implementations
3. Apply appropriate scopes
4. Handle constructor injection when possible

**Implementation Examples**:

**Constructor Injection (Preferred)**:
```kotlin
// No module needed - Hilt handles it automatically
class MusicRepository @Inject constructor(
    private val localDataSource: LocalMusicDataSource,
    private val mediaStoreDataSource: MediaStoreDataSource
) {
    // Repository implementation
}
```

**@Provides for Complex Creation**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_database"
        )
            .addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()
    }
    
    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao {
        return database.songDao()
    }
    
    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao {
        return database.playlistDao()
    }
}
```

**@Binds for Interface Implementations**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    
    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository
    
    @Binds
    abstract fun bindPlaylistRepository(
        impl: PlaylistRepositoryImpl
    ): PlaylistRepository
    
    @Binds
    abstract fun bindEqualizerRepository(
        impl: EqualizerRepositoryImpl
    ): EqualizerRepository
}
```

**ExoPlayer Module**:
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object PlaybackModule {
    
    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context
    ): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideMediaSession(
        @ApplicationContext context: Context,
        player: ExoPlayer
    ): MediaSession {
        return MediaSession.Builder(context, player)
            .setSessionActivity(getPendingIntent(context))
            .build()
    }
    
    private fun getPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
```

### Step 6: Use Qualifiers for Multiple Implementations
**Goal**: Distinguish between similar dependencies

**Actions**:
1. Create qualifier annotations
2. Apply to providers and injection points
3. Use for different configurations

**Implementation**:
```kotlin
// Define qualifiers
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

// Provide qualified dependencies
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {
    
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    
    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

// Inject qualified dependencies
class MusicRepository @Inject constructor(
    private val songDao: SongDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun getSongs(): List<Song> = withContext(ioDispatcher) {
        songDao.getAllSongs()
    }
}
```

### Step 7: Apply Scoping
**Goal**: Control dependency lifecycle

**Actions**:
1. Choose appropriate scope for each dependency
2. Understand scope hierarchy
3. Avoid scope violations

**Scoping Options**:
```kotlin
// Singleton - Lives for entire app lifecycle
@Provides
@Singleton
fun provideDatabase(@ApplicationContext context: Context): MusicDatabase

// ViewModelScoped - Lives as long as ViewModel
@Module
@InstallIn(ViewModelComponent::class)
object ViewModelModule {
    @Provides
    @ViewModelScoped
    fun provideUseCaseHelper(): UseCaseHelper
}

// ActivityRetainedScoped - Survives configuration changes
@Provides
@ActivityRetainedScoped
fun provideActivityHelper(): ActivityHelper

// ActivityScoped - Lives as long as Activity
@Provides
@ActivityScoped
fun provideActivityDependency(): ActivityDependency

// Unscoped - New instance every time
@Provides
fun provideTemporaryHelper(): TemporaryHelper
```

**Scope Hierarchy**:
```
SingletonComponent (Application lifetime)
    ↓
ActivityRetainedComponent (Survives config changes)
    ↓
ViewModelComponent (ViewModel lifetime)
    ↓
ActivityComponent (Activity lifetime)
    ↓
FragmentComponent (Fragment lifetime)
    ↓
ViewComponent (View lifetime)
```

### Step 8: Setup Testing with Hilt
**Goal**: Enable dependency injection in tests

**Actions**:
1. Create test application class
2. Use @HiltAndroidTest annotation
3. Create test modules to replace dependencies
4. Use @UninstallModules when needed

**Implementation**:
```kotlin
// Test Application
class HiltTestApplication : Application()

// Configure test runner
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?
    ): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}

// build.gradle.kts
android {
    defaultConfig {
        testInstrumentationRunner = "com.rolla.musicplayer.HiltTestRunner"
    }
}

// Unit Test
@HiltAndroidTest
@UninstallModules(RepositoryModule::class)
class PlayerViewModelTest {
    
    @get:Rule
    var hiltRule = HiltAndroidRule(this)
    
    @Inject
    lateinit var repository: MusicRepository
    
    private lateinit var viewModel: PlayerViewModel
    
    @Before
    fun setup() {
        hiltRule.inject()
        viewModel = PlayerViewModel(repository)
    }
    
    @Test
    fun testPlaySong() {
        // Test implementation
    }
}

// Test Module
@Module
@InstallIn(SingletonComponent::class)
object TestRepositoryModule {
    
    @Provides
    @Singleton
    fun provideFakeMusicRepository(): MusicRepository {
        return FakeMusicRepository()
    }
}
```

### Step 9: Handle Special Cases
**Goal**: Address common DI scenarios

**Actions**:
1. Inject into non-Android classes
2. Handle assisted injection
3. Provide context-dependent dependencies

**Assisted Injection**:
```kotlin
// For classes that need both injected and runtime parameters
class SongPlayer @AssistedInject constructor(
    private val exoPlayer: ExoPlayer,
    @Assisted private val songId: String
) {
    @AssistedFactory
    interface Factory {
        fun create(songId: String): SongPlayer
    }
}

// Usage in ViewModel
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val songPlayerFactory: SongPlayer.Factory
) : ViewModel() {
    fun playSong(songId: String) {
        val player = songPlayerFactory.create(songId)
        // Use player
    }
}
```

**Entry Point for Non-Android Classes**:
```kotlin
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PlaybackServiceEntryPoint {
    fun exoPlayer(): ExoPlayer
    fun mediaSession(): MediaSession
}

// Usage in custom class
class CustomPlaybackHandler(context: Context) {
    private val entryPoint = EntryPointAccessors.fromApplication(
        context,
        PlaybackServiceEntryPoint::class.java
    )
    
    private val player = entryPoint.exoPlayer()
    private val mediaSession = entryPoint.mediaSession()
}
```

## Addons

### Hilt Extensions
```kotlin
// build.gradle.kts
dependencies {
    // Hilt ViewModel
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Hilt WorkManager
    implementation("androidx.hilt:hilt-work:1.1.0")
    ksp("androidx.hilt:hilt-compiler:1.1.0")
    
    // Hilt Navigation
    implementation("androidx.hilt:hilt-navigation-fragment:1.1.0")
}
```

### WorkManager Integration
```kotlin
@HiltWorker
class MusicScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val musicRepository: MusicRepository
) : CoroutineWorker(appContext, workerParams) {
    
    override suspend fun doWork(): Result {
        return try {
            musicRepository.scanLibrary()
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}

// Initialize WorkManager with Hilt
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {
    
    @Provides
    @Singleton
    fun provideWorkManager(
        @ApplicationContext context: Context
    ): WorkManager {
        return WorkManager.getInstance(context)
    }
}
```

### Multi-Module Setup
```kotlin
// In feature module build.gradle.kts
plugins {
    id("com.google.dagger.hilt.android")
}

// Feature module can access app-level dependencies
@Module
@InstallIn(SingletonComponent::class)
abstract class FeatureModule {
    @Binds
    abstract fun bindFeatureRepository(
        impl: FeatureRepositoryImpl
    ): FeatureRepository
}
```

## Related Files
- `di/` - Hilt modules
- `Application.kt` - Application class with @HiltAndroidApp
- `build.gradle.kts` - Hilt dependencies
- `*ViewModel.kt` - ViewModels with @HiltViewModel
- `*Activity.kt` - Activities with @AndroidEntryPoint

## Notes
- Prefer constructor injection over field injection
- Use @Singleton sparingly - only for truly app-wide dependencies
- Keep modules focused on single responsibility
- Use @Binds instead of @Provides when possible (more efficient)
- Avoid circular dependencies
- Use qualifiers for multiple implementations of same type
- Test with Hilt to ensure DI graph is correct
- Consider using @EntryPoint for non-Android classes
- Understand scope hierarchy to avoid scope violations
- Use assisted injection for runtime parameters

## Common Patterns

### Repository Pattern with Hilt
```kotlin
// Interface in domain layer
interface MusicRepository {
    fun observeSongs(): Flow<List<Song>>
    suspend fun getSongById(id: String): Song?
}

// Implementation in data layer
class MusicRepositoryImpl @Inject constructor(
    private val songDao: SongDao,
    private val mediaStoreDataSource: MediaStoreDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : MusicRepository {
    // Implementation
}

// Binding in DI module
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        impl: MusicRepositoryImpl
    ): MusicRepository
}
```

### Use Case Pattern with Hilt
```kotlin
// Use case with constructor injection
class GetSongsUseCase @Inject constructor(
    private val repository: MusicRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher
) {
    operator fun invoke(): Flow<List<Song>> = 
        repository.observeSongs()
            .flowOn(dispatcher)
}

// ViewModel using use case
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val getSongsUseCase: GetSongsUseCase
) : ViewModel() {
    val songs = getSongsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}
```

### Conditional Dependencies
```kotlin
@Module
@InstallIn(SingletonComponent::class)
object ConditionalModule {
    
    @Provides
    @Singleton
    fun provideAnalytics(
        @ApplicationContext context: Context
    ): Analytics {
        return if (BuildConfig.DEBUG) {
            DebugAnalytics()
        } else {
            ProductionAnalytics(context)
        }
    }
}
```

## Troubleshooting

### Common Issues

**Issue**: "Dagger does not support injection into private fields"
**Solution**: Make injected fields internal or public, or use constructor injection

**Issue**: "Cannot be provided without an @Inject constructor or an @Provides-annotated method"
**Solution**: Add @Inject constructor or create a module with @Provides method

**Issue**: "Scope X is not compatible with scope Y"
**Solution**: Ensure dependency scope is same or broader than dependent's scope

**Issue**: "Circular dependency"
**Solution**: Refactor to break the cycle, use Provider<T>, or Lazy<T>

**Issue**: "Multiple bindings for the same type"
**Solution**: Use qualifiers to distinguish between implementations