package com.rolla.musicplayer.core.database.migration

import androidx.room.migration.Migration

/** Every migration, oldest first. DatabaseModule installs exactly this list, and MigrationTest replays it from v1. */
val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
