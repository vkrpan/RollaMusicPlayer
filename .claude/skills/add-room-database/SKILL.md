---
name: add-room-database
description: "Comprehensive guide for setting up and managing Room database in Android applications, including entity creation, DAOs, migrations, type converters, relationships, and testing strategies."
---

# Skill: Add Room Database

## Overview
Comprehensive guide for setting up and managing Room database in Android applications, including entity creation, DAOs, migrations, type converters, relationships, and testing strategies.

## When to Use
- Setting up local database in a new project
- Adding new tables or entities
- Implementing database migrations
- Creating complex queries and relationships
- Setting up reactive data flows with Flow

## Prerequisites
- Basic SQL knowledge
- Understanding of Kotlin coroutines
- Familiarity with data persistence concepts
- Hilt dependency injection setup (recommended)

## Workflow Steps

### Step 1: Add Room Dependencies
**Goal**: Configure project for Room database

**Actions**:
1. Add Room dependencies
2. Enable KSP for annotation processing
3. Configure database schema export
4. Sync project

**Implementation**:
```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.google.devtools.ksp")
}

android {
    defaultConfig {
        // Export database schema for version control
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
    
    // Include schema files in source sets
    sourceSets {
        getByName("androidTest").assets.srcDirs("$projectDir/schemas")
    }
}

dependencies {
    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    
    // Paging 3 with Room (optional)
    implementation("androidx.room:room-paging:2.6.1")
    
    // Testing
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
}
```

### Step 2: Define Entities
**Goal**: Create database table schemas

**Actions**:
1. Create entity classes with @Entity
2. Define primary keys
3. Add indices for performance
4. Set up foreign keys if needed

**Implementation**:
```kotlin
// data/local/entity/SongEntity.kt
@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"]),
        Index(value = ["artist"]),
        Index(value = ["album_id"])
    ]
)
data class SongEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "artist")
    val artist: String,
    
    @ColumnInfo(name = "album")
    val album: String,
    
    @ColumnInfo(name = "album_id")
    val albumId: String?,
    
    @ColumnInfo(name = "duration")
    val duration: Long,
    
    @ColumnInfo(name = "file_path")
    val filePath: String,
    
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,
    
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    
    @ColumnInfo(name = "genre")
    val genre: String?,
    
    @ColumnInfo(name = "year")
    val year: Int?,
    
    @ColumnInfo(name = "track_number")
    val trackNumber: Int?,
    
    @ColumnInfo(name = "play_count")
    val playCount: Int = 0,
    
    @ColumnInfo(name = "last_played")
    val lastPlayed: Long? = null
)

// data/local/entity/PlaylistEntity.kt
@Entity(
    tableName = "playlists"
)
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String?,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    
    @ColumnInfo(name = "song_count")
    val songCount: Int = 0
)

// data/local/entity/PlaylistSongCrossRef.kt
@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlist_id", "song_id"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["song_id"])
    ]
)
data class PlaylistSongCrossRef(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    
    @ColumnInfo(name = "song_id")
    val songId: String,
    
    @ColumnInfo(name = "position")
    val position: Int,
    
    @ColumnInfo(name = "added_at")
    val addedAt: Long
)

// data/local/entity/EqualizerPresetEntity.kt
@Entity(tableName = "equalizer_presets")
data class EqualizerPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean,
    
    @ColumnInfo(name = "bands")
    val bands: List<Float>, // Will need TypeConverter
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
```

### Step 3: Create Type Converters
**Goal**: Handle complex data types

**Actions**:
1. Create converter classes
2. Add @TypeConverter annotations
3. Register converters in database

**Implementation**:
```kotlin
// data/local/converter/Converters.kt
class Converters {
    
    @TypeConverter
    fun fromFloatList(value: List<Float>): String {
        return value.joinToString(",")
    }
    
    @TypeConverter
    fun toFloatList(value: String): List<Float> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split(",").map { it.toFloat() }
        }
    }
    
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString("||")
    }
    
    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) {
            emptyList()
        } else {
            value.split("||")
        }
    }
    
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? {
        return value?.let { Instant.ofEpochMilli(it) }
    }
    
    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }
}
```

### Step 4: Create DAOs
**Goal**: Define database access operations

**Actions**:
1. Create DAO interfaces
2. Define CRUD operations
3. Add custom queries
4. Use Flow for reactive queries

**Implementation**:
```kotlin
// data/local/dao/SongDao.kt
@Dao
interface SongDao {
    
    // Insert operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: SongEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)
    
    // Update operations
    @Update
    suspend fun updateSong(song: SongEntity)
    
    @Query("UPDATE songs SET play_count = play_count + 1, last_played = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String, timestamp: Long)
    
    // Delete operations
    @Delete
    suspend fun deleteSong(song: SongEntity)
    
    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteSongById(songId: String)
    
    @Query("DELETE FROM songs")
    suspend fun deleteAllSongs()
    
    // Query operations - Flow for reactive updates
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun observeAllSongs(): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE id = :songId")
    fun observeSongById(songId: String): Flow<SongEntity?>
    
    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): SongEntity?
    
    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongs(): List<SongEntity>
    
    // Search queries
    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%' 
        OR artist LIKE '%' || :query || '%'
        OR album LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>
    
    // Filter queries
    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title ASC")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE album_id = :albumId ORDER BY track_number ASC")
    fun getSongsByAlbum(albumId: String): Flow<List<SongEntity>>
    
    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title ASC")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>
    
    // Aggregate queries
    @Query("SELECT COUNT(*) FROM songs")
    fun getSongCount(): Flow<Int>
    
    @Query("SELECT DISTINCT artist FROM songs ORDER BY artist ASC")
    fun getAllArtists(): Flow<List<String>>
    
    @Query("SELECT DISTINCT album FROM songs ORDER BY album ASC")
    fun getAllAlbums(): Flow<List<String>>
    
    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>
    
    // Most played songs
    @Query("SELECT * FROM songs ORDER BY play_count DESC LIMIT :limit")
    fun getMostPlayedSongs(limit: Int): Flow<List<SongEntity>>
    
    // Recently played songs
    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL ORDER BY last_played DESC LIMIT :limit")
    fun getRecentlyPlayedSongs(limit: Int): Flow<List<SongEntity>>
    
    // Recently added songs
    @Query("SELECT * FROM songs ORDER BY date_added DESC LIMIT :limit")
    fun getRecentlyAddedSongs(limit: Int): Flow<List<SongEntity>>
}

// data/local/dao/PlaylistDao.kt
@Dao
interface PlaylistDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long
    
    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)
    
    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)
    
    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    fun observePlaylistById(playlistId: Long): Flow<PlaylistEntity?>
    
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): PlaylistEntity?
    
    // Playlist songs operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)
    
    @Delete
    suspend fun deletePlaylistSong(crossRef: PlaylistSongCrossRef)
    
    @Query("DELETE FROM playlist_songs WHERE playlist_id = :playlistId")
    suspend fun deleteAllPlaylistSongs(playlistId: Long)
    
    @Query("""
        SELECT songs.* FROM songs
        INNER JOIN playlist_songs ON songs.id = playlist_songs.song_id
        WHERE playlist_songs.playlist_id = :playlistId
        ORDER BY playlist_songs.position ASC
    """)
    fun getPlaylistSongs(playlistId: Long): Flow<List<SongEntity>>
    
    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs?
}

// data/local/dao/EqualizerPresetDao.kt
@Dao
interface EqualizerPresetDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: EqualizerPresetEntity): Long
    
    @Update
    suspend fun updatePreset(preset: EqualizerPresetEntity)
    
    @Delete
    suspend fun deletePreset(preset: EqualizerPresetEntity)
    
    @Query("SELECT * FROM equalizer_presets ORDER BY is_custom DESC, name ASC")
    fun observeAllPresets(): Flow<List<EqualizerPresetEntity>>
    
    @Query("SELECT * FROM equalizer_presets WHERE id = :presetId")
    suspend fun getPresetById(presetId: Long): EqualizerPresetEntity?
    
    @Query("SELECT * FROM equalizer_presets WHERE is_custom = 1 ORDER BY name ASC")
    fun observeCustomPresets(): Flow<List<EqualizerPresetEntity>>
}
```

### Step 5: Define Relationships
**Goal**: Model complex data relationships

**Actions**:
1. Create relationship data classes
2. Use @Relation annotation
3. Define one-to-many and many-to-many relationships

**Implementation**:
```kotlin
// data/local/relation/PlaylistWithSongs.kt
data class PlaylistWithSongs(
    @Embedded val playlist: PlaylistEntity,
    
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = PlaylistSongCrossRef::class,
            parentColumn = "playlist_id",
            entityColumn = "song_id"
        )
    )
    val songs: List<SongEntity>
)

// data/local/relation/AlbumWithSongs.kt
data class AlbumWithSongs(
    val albumId: String,
    val albumName: String,
    val artist: String,
    val songs: List<SongEntity>
)

// Query for album with songs
@Query("""
    SELECT * FROM songs 
    WHERE album_id = :albumId 
    ORDER BY track_number ASC
""")
suspend fun getAlbumWithSongs(albumId: String): List<SongEntity>
```

### Step 6: Create Database Class
**Goal**: Setup Room database instance

**Actions**:
1. Create abstract database class
2. Register entities and DAOs
3. Add type converters
4. Configure database settings

**Implementation**:
```kotlin
// data/local/MusicDatabase.kt
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        EqualizerPresetEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun equalizerPresetDao(): EqualizerPresetDao
    
    companion object {
        const val DATABASE_NAME = "music_database"
    }
}
```

### Step 7: Setup Dependency Injection
**Goal**: Provide database instance via Hilt

**Actions**:
1. Create database module
2. Provide database instance
3. Provide DAOs

**Implementation**:
```kotlin
// di/DatabaseModule.kt
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
            MusicDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration() // Remove in production
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
    
    @Provides
    fun provideEqualizerPresetDao(database: MusicDatabase): EqualizerPresetDao {
        return database.equalizerPresetDao()
    }
}
```

### Step 8: Implement Database Migrations
**Goal**: Handle schema changes safely

**Actions**:
1. Create migration objects
2. Define schema changes
3. Test migrations
4. Add migrations to database builder

**Implementation**:
```kotlin
// data/local/migration/Migrations.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column to songs table
        database.execSQL(
            "ALTER TABLE songs ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create new table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                color INTEGER NOT NULL
            )
        """)
        
        // Create junction table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS song_tags (
                song_id TEXT NOT NULL,
                tag_id INTEGER NOT NULL,
                PRIMARY KEY(song_id, tag_id),
                FOREIGN KEY(song_id) REFERENCES songs(id) ON DELETE CASCADE,
                FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE
            )
        """)
        
        // Create indices
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_song_tags_song_id ON song_tags(song_id)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_song_tags_tag_id ON song_tags(tag_id)"
        )
    }
}

// Update database builder
@Provides
@Singleton
fun provideDatabase(
    @ApplicationContext context: Context
): MusicDatabase {
    return Room.databaseBuilder(
        context,
        MusicDatabase::class.java,
        MusicDatabase.DATABASE_NAME
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
        .build()
}
```

### Step 9: Test Database
**Goal**: Ensure database operations work correctly

**Actions**:
1. Create database tests
2. Test CRUD operations
3. Test queries and relationships
4. Test migrations

**Implementation**:
```kotlin
// data/local/SongDaoTest.kt
@RunWith(AndroidJUnit4::class)
class SongDaoTest {
    
    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            MusicDatabase::class.java
        ).build()
        songDao = database.songDao()
    }
    
    @After
    fun teardown() {
        database.close()
    }
    
    @Test
    fun insertAndGetSong() = runTest {
        // Given
        val song = SongEntity(
            id = "1",
            title = "Test Song",
            artist = "Test Artist",
            album = "Test Album",
            albumId = "album1",
            duration = 180000,
            filePath = "/path/to/song",
            dateAdded = System.currentTimeMillis(),
            dateModified = System.currentTimeMillis(),
            genre = "Rock",
            year = 2024,
            trackNumber = 1
        )
        
        // When
        songDao.insertSong(song)
        val retrieved = songDao.getSongById("1")
        
        // Then
        assertNotNull(retrieved)
        assertEquals(song.title, retrieved?.title)
        assertEquals(song.artist, retrieved?.artist)
    }
    
    @Test
    fun observeSongsReturnsFlowOfSongs() = runTest {
        // Given
        val songs = listOf(
            SongEntity("1", "Song 1", "Artist 1", "Album 1", null, 180000, "/path1", 0, 0, null, null, null),
            SongEntity("2", "Song 2", "Artist 2", "Album 2", null, 200000, "/path2", 0, 0, null, null, null)
        )
        songDao.insertSongs(songs)
        
        // When
        val flow = songDao.observeAllSongs()
        
        // Then
        flow.test {
            val result = awaitItem()
            assertEquals(2, result.size)
            assertEquals("Song 1", result[0].title)
            assertEquals("Song 2", result[1].title)
        }
    }
    
    @Test
    fun searchSongsFindsMatches() = runTest {
        // Given
        val songs = listOf(
            SongEntity("1", "Rock Song", "Rock Artist", "Rock Album", null, 180000, "/path1", 0, 0, null, null, null),
            SongEntity("2", "Pop Song", "Pop Artist", "Pop Album", null, 200000, "/path2", 0, 0, null, null, null)
        )
        songDao.insertSongs(songs)
        
        // When
        val flow = songDao.searchSongs("Rock")
        
        // Then
        flow.test {
            val result = awaitItem()
            assertEquals(1, result.size)
            assertEquals("Rock Song", result[0].title)
        }
    }
    
    @Test
    fun incrementPlayCountUpdatesCorrectly() = runTest {
        // Given
        val song = SongEntity("1", "Test", "Artist", "Album", null, 180000, "/path", 0, 0, null, null, null, playCount = 0)
        songDao.insertSong(song)
        
        // When
        songDao.incrementPlayCount("1", System.currentTimeMillis())
        val updated = songDao.getSongById("1")
        
        // Then
        assertEquals(1, updated?.playCount)
        assertNotNull(updated?.lastPlayed)
    }
}

// Test migrations
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusicDatabase::class.java
    )
    
    @Test
    fun migrate1To2() {
        // Create database with version 1
        helper.createDatabase(MusicDatabase.DATABASE_NAME, 1).apply {
            execSQL("""
                INSERT INTO songs VALUES (
                    '1', 'Test', 'Artist', 'Album', null, 180000, 
                    '/path', 0, 0, null, null, null, 0, null
                )
            """)
            close()
        }
        
        // Migrate to version 2
        helper.runMigrationsAndValidate(
            MusicDatabase.DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2
        )
        
        // Verify migration
        helper.getMigrationDatabase().query("SELECT * FROM songs").use { cursor ->
            assertTrue(cursor.moveToFirst())
            val favoriteIndex = cursor.getColumnIndex("favorite")
            assertTrue(favoriteIndex >= 0)
        }
    }
}
```

## Addons

### Paging 3 Integration
```kotlin
// DAO with PagingSource
@Query("SELECT * FROM songs ORDER BY title ASC")
fun getSongsPaged(): PagingSource<Int, SongEntity>

// Repository
fun getSongsPaged(): Flow<PagingData<Song>> {
    return Pager(
        config = PagingConfig(
            pageSize = 20,
            enablePlaceholders = false
        ),
        pagingSourceFactory = { songDao.getSongsPaged() }
    ).flow.map { pagingData ->
        pagingData.map { it.toDomain() }
    }
}
```

### Full-Text Search
```kotlin
@Entity(tableName = "songs_fts")
@Fts4(contentEntity = SongEntity::class)
data class SongFts(
    val title: String,
    val artist: String,
    val album: String
)

@Dao
interface SongFtsDao {
    @Query("SELECT * FROM songs_fts WHERE songs_fts MATCH :query")
    fun searchSongs(query: String): Flow<List<SongFts>>
}
```

### Database Inspector Helper
```kotlin
// Debug helper for database inspection
@Provides
@Singleton
fun provideDatabaseCallback(): RoomDatabase.Callback {
    return object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            Log.d("Database", "Database created")
        }
        
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            Log.d("Database", "Database opened")
        }
    }
}
```

## Related Files
- `data/local/entity/` - Entity classes
- `data/local/dao/` - DAO interfaces
- `data/local/MusicDatabase.kt` - Database class
- `data/local/converter/` - Type converters
- `data/local/migration/` - Migration objects
- `di/DatabaseModule.kt` - Hilt module

## Notes
- Always use suspend functions for database operations
- Use Flow for reactive queries that update UI
- Index frequently queried columns
- Use transactions for multiple related operations
- Test migrations thoroughly before release
- Export schema for version control
- Use foreign keys to maintain referential integrity
- Consider using @Transaction for complex operations
- Avoid blocking main thread with database operations
- Use appropriate conflict strategies for inserts

## Common Patterns

### Transaction Operations
```kotlin
@Transaction
suspend fun updatePlaylistWithSongs(
    playlistId: Long,
    songIds: List<String>
) {
    // Delete existing songs
    deleteAllPlaylistSongs(playlistId)
    
    // Insert new songs
    songIds.forEachIndexed { index, songId ->
        insertPlaylistSong(
            PlaylistSongCrossRef(
                playlistId = playlistId,
                songId = songId,
                position = index,
                addedAt = System.currentTimeMillis()
            )
        )
    }
    
    // Update playlist
    val playlist = getPlaylistById(playlistId)
    playlist?.let {
        updatePlaylist(
            it.copy(
                songCount = songIds.size,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
```

### Conditional Queries
```kotlin
@Query("""
    SELECT * FROM songs 
    WHERE (:artist IS NULL OR artist = :artist)
    AND (:genre IS NULL OR genre = :genre)
    ORDER BY title ASC
""")
fun getFilteredSongs(
    artist: String?,
    genre: String?
): Flow<List<SongEntity>>
```

### Batch Operations
```kotlin
@Transaction
suspend fun batchUpdateSongs(updates: List<SongEntity>) {
    updates.forEach { song ->
        updateSong(song)
    }
}