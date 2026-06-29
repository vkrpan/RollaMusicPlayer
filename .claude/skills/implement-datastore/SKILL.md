---
name: implement-datastore
description: "Step-by-step workflow for replacing SharedPreferences with Jetpack DataStore in RollaMusicPlayer — Preferences DataStore for settings and equalizer active-state, optional Proto DataStore for structured playback state, a Flow-based repository, Hilt wiring, and a one-time SharedPreferences migration. Fully local and offline."
---

# Skill: Implement DataStore

## Overview
A workflow for moving the app's key-value and small structured state off SharedPreferences and onto Jetpack DataStore — the current best practice. CLAUDE.md still names SharedPreferences for settings; DataStore replaces it with an async, Flow-based, transactional API that won't block the main thread. This covers Preferences DataStore (settings, equalizer enabled/gains, theme), optional Proto DataStore (structured playback state), a repository wrapper, Hilt provisioning, and a one-time migration from existing SharedPreferences. All on-device, no network.

## When to Use
- Storing app settings, equalizer active-state, theme mode, or playback position
- Replacing SharedPreferences usage
- Anywhere you need observable, non-blocking persisted state

## Prerequisites
- Hilt configured; coroutines/Flow available
- Decide per use case: Preferences DataStore (loose key-value) vs Proto DataStore (typed schema)

## Workflow Steps

### Step 1: Add DataStore
**Goal**: Preferences (and optionally Proto) DataStore

**Implementation**:
```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // For Proto DataStore (typed):
    // implementation("androidx.datastore:datastore:1.1.1")
    // implementation("com.google.protobuf:protobuf-javalite:3.25.3")
}
```

### Step 2: Create the Preferences DataStore
**Goal**: A single app-scoped DataStore for settings

**Implementation**:
```kotlin
// data/preferences/SettingsDataStore.kt
private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")          // "system"/"light"/"dark"
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_GAINS = stringPreferencesKey("eq_gains")              // CSV of millibel
        val LAST_SONG_ID = stringPreferencesKey("last_song_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
    }

    val themeMode: Flow<String> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[Keys.THEME_MODE] ?: "system" }

    val equalizerEnabled: Flow<Boolean> = context.dataStore.data
        .map { it[Keys.EQ_ENABLED] ?: false }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun setEqualizerGains(gainsMillibel: List<Short>) {
        context.dataStore.edit { it[Keys.EQ_GAINS] = gainsMillibel.joinToString(",") }
    }
}
```
> Always `catch` `IOException` on the read flow and emit `emptyPreferences()` — DataStore reads can throw on disk errors.

### Step 3: Wrap in a Repository (optional but recommended)
**Goal**: Expose domain types, not raw strings

**Implementation**:
```kotlin
// data/preferences/SettingsRepository.kt
class SettingsRepository @Inject constructor(
    private val store: SettingsDataStore
) {
    val themeMode: Flow<ThemeMode> = store.themeMode.map(ThemeMode::fromKey)
    suspend fun setThemeMode(mode: ThemeMode) = store.setThemeMode(mode.key)
    // equalizer active-state read/write delegated here for equalizer-agent
}

enum class ThemeMode(val key: String) {
    SYSTEM("system"), LIGHT("light"), DARK("dark");
    companion object { fun fromKey(k: String) = entries.firstOrNull { it.key == k } ?: SYSTEM }
}
```

### Step 4: Provide via Hilt
**Goal**: Inject anywhere

**Implementation**:
```kotlin
// di/PreferencesModule.kt
@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {
    @Provides @Singleton
    fun provideSettingsDataStore(@ApplicationContext c: Context) = SettingsDataStore(c)

    @Provides @Singleton
    fun provideSettingsRepository(store: SettingsDataStore) = SettingsRepository(store)
}
```

### Step 5: Migrate Existing SharedPreferences (one-time)
**Goal**: Carry over any existing settings without data loss

**Implementation**:
```kotlin
private val Context.dataStore by preferencesDataStore(
    name = "settings",
    produceMigrations = { context ->
        listOf(
            SharedPreferencesMigration(context, "rolla_prefs")  // old SharedPreferences file
        )
    }
)
```
DataStore runs the migration on first access and (by default) clears the old SharedPreferences keys it imports. Keep the old key names aligned so the migration maps them.

### Step 6: Consume in ViewModel / Theme
**Goal**: Drive UI reactively from persisted state

**Implementation**:
```kotlin
// e.g. theme mode feeding the root theme
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)
}
```

### Step 7 (optional): Proto DataStore for Structured Playback State
**Goal**: Type-safe persisted playback snapshot (song id, position, queue, shuffle/repeat)

**Notes**:
- Define a `.proto` schema, a `Serializer<PlaybackStatePb>`, and `dataStore(fileName, serializer)`.
- Prefer Proto when the state is a record with several fields and you want schema/versioning; Preferences is fine for loose flags.
- Persist playback position on a throttled cadence (reuse the player's ~1s tick), not every frame.

### Step 8: Verify
**Checklist**:
- [ ] Reads are Flow-based and never block the main thread
- [ ] `IOException` handled on read flows
- [ ] Writes use `edit {}` (transactional)
- [ ] SharedPreferences migration runs once and preserves values
- [ ] Settings/equalizer state survive app restart
- [ ] No SharedPreferences usage remains for migrated keys
- [ ] Fully local; no network
- [ ] Equalizer active-state read/written here (coordinate with equalizer-agent)

## Related Files
- `data/preferences/SettingsDataStore.kt` — Preferences DataStore
- `data/preferences/SettingsRepository.kt` — domain wrapper
- `di/PreferencesModule.kt` — Hilt provisioning
- `proto/playback_state.proto` + serializer — optional Proto DataStore

## Notes
- DataStore replaces SharedPreferences; update CLAUDE.md's storage notes accordingly.
- One DataStore instance per file — never construct it twice; the `by preferencesDataStore` delegate enforces this at the Context level.
- Equalizer **active-state** (enabled + gains) fits Preferences DataStore; named **presets** stay in Room (see `implement-equalizer`).
- Keep playback-position writes throttled to protect the disk and battery.

## Common Pitfalls
- ❌ Reading DataStore synchronously / on the main thread — it's async by design; collect the Flow.
- ❌ Forgetting the `IOException` catch — a disk read error crashes the collector.
- ❌ Creating multiple DataStore instances for the same file — throws at runtime.
- ❌ Leaving old SharedPreferences reads in place after migrating — split source of truth.
