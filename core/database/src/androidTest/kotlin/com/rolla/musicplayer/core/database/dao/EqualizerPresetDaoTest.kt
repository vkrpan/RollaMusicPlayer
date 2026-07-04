package com.rolla.musicplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.EqualizerPresetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EqualizerPresetDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var equalizerPresetDao: EqualizerPresetDao

    private val sampleGains = listOf<Short>(300, 200, 100, 0, -100, -200, 0, 100)
    private val negativeAndZeroGains = listOf<Short>(-1200, 0, 1200, -1, 1, 0, -600, 600)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        equalizerPresetDao = database.equalizerPresetDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildPreset(
        name: String = "Rock",
        isCustom: Boolean = false,
        gainsMillibel: List<Short> = sampleGains,
        createdAt: Long = DEFAULT_CREATED_AT,
    ): EqualizerPresetEntity = EqualizerPresetEntity(
        name = name,
        isCustom = isCustom,
        gainsMillibel = gainsMillibel,
        createdAt = createdAt,
    )

    @Test
    fun insertPreset_thenObserveAllPresets_returnsInsertedPreset() = runTest {
        equalizerPresetDao.insertPreset(buildPreset(name = "Rock"))

        val presets = equalizerPresetDao.observeAllPresets().first()

        assertEquals(1, presets.size)
        assertEquals("Rock", presets.first().name)
    }

    @Test
    fun observeAllPresets_ordersByCreatedAtAscending() = runTest {
        equalizerPresetDao.insertPreset(buildPreset(name = "Third", createdAt = 3_000L))
        equalizerPresetDao.insertPreset(buildPreset(name = "First", createdAt = 1_000L))
        equalizerPresetDao.insertPreset(buildPreset(name = "Second", createdAt = 2_000L))

        val names = equalizerPresetDao.observeAllPresets().first().map { it.name }

        assertEquals(listOf("First", "Second", "Third"), names)
    }

    @Test
    fun observeAllPresets_sameCreatedAt_ordersByIdAscendingAsTieBreaker() = runTest {
        val firstId = equalizerPresetDao.insertPreset(buildPreset(name = "A", createdAt = 1_000L))
        val secondId = equalizerPresetDao.insertPreset(buildPreset(name = "B", createdAt = 1_000L))

        val ids = equalizerPresetDao.observeAllPresets().first().map { it.id }

        assertEquals(listOf(firstId, secondId), ids)
    }

    @Test
    fun deletePreset_removesOnlyTargetedPreset() = runTest {
        val keepId = equalizerPresetDao.insertPreset(buildPreset(name = "Keep"))
        val removeId = equalizerPresetDao.insertPreset(buildPreset(name = "Remove", createdAt = 2_000L))

        equalizerPresetDao.deletePreset(removeId)

        val presets = equalizerPresetDao.observeAllPresets().first()
        assertEquals(listOf(keepId), presets.map { it.id })
    }

    @Test
    fun deletePreset_nonExistentId_isNoOpAndDoesNotThrow() = runTest {
        val id = equalizerPresetDao.insertPreset(buildPreset(name = "Solo"))

        equalizerPresetDao.deletePreset(id + 999L)

        assertEquals(1, equalizerPresetDao.observeAllPresets().first().size)
    }

    @Test
    fun insertPreset_roundTripsNegativeAndZeroGains() = runTest {
        equalizerPresetDao.insertPreset(buildPreset(name = "Custom", gainsMillibel = negativeAndZeroGains))

        val stored = equalizerPresetDao.observeAllPresets().first().first()

        assertEquals(negativeAndZeroGains, stored.gainsMillibel)
    }

    @Test
    fun insertPreset_roundTripsEmptyGainsList() = runTest {
        equalizerPresetDao.insertPreset(buildPreset(name = "Flat", gainsMillibel = emptyList()))

        val stored = equalizerPresetDao.observeAllPresets().first().first()

        assertTrue(stored.gainsMillibel.isEmpty())
    }

    @Test
    fun insertPreset_isCustomFlagRoundTrips() = runTest {
        equalizerPresetDao.insertPreset(buildPreset(name = "BuiltIn", isCustom = false))
        equalizerPresetDao.insertPreset(buildPreset(name = "MyCustom", isCustom = true, createdAt = 2_000L))

        val presets = equalizerPresetDao.observeAllPresets().first()

        assertEquals(false, presets.first { it.name == "BuiltIn" }.isCustom)
        assertEquals(true, presets.first { it.name == "MyCustom" }.isCustom)
    }

    @Test
    fun observeAllPresets_corruptGainsMillibelCsv_decodesToEmptyList() = runTest {
        // Insert a row with a corrupt CSV value directly via raw SQL, bypassing
        // ShortListConverter's encode path entirely, to simulate on-disk corruption. Reading it
        // back through the DAO exercises ShortListConverter.toShortList's decode-defensively rule.
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO equalizer_presets (id, name, is_custom, gains_millibel, created_at)
            VALUES (1, 'Corrupt', 0, '300,notANumber,100', 1000)
            """.trimIndent(),
        )

        val stored = equalizerPresetDao.observeAllPresets().first().first()

        assertTrue(stored.gainsMillibel.isEmpty())
    }

    private companion object {
        private const val DEFAULT_CREATED_AT = 1_000L
    }
}
