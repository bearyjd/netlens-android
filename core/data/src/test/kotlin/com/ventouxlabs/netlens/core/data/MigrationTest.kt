package com.ventouxlabs.netlens.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ventouxlabs.netlens.core.data.di.DataModule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs every real [DataModule] migration against the exported schema JSONs
 * (`core/data/schemas/com.ventouxlabs.netlens.core.data.NetLensDatabase/`) under Robolectric. This
 * is the only place any of the 12 migrations (4→5 … 15→16) get exercised — previously they were
 * hand-checked only, per `.omc/skills/room-migration-schema-default-expertise.md`.
 *
 * Note: schema `3.json` is not exported (migrations start at 4→5), so the chain below starts at 4.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val allMigrations = arrayOf(
        DataModule.MIGRATION_4_5, DataModule.MIGRATION_5_6, DataModule.MIGRATION_6_7,
        DataModule.MIGRATION_7_8, DataModule.MIGRATION_8_9, DataModule.MIGRATION_9_10,
        DataModule.MIGRATION_10_11, DataModule.MIGRATION_11_12, DataModule.MIGRATION_12_13,
        DataModule.MIGRATION_13_14, DataModule.MIGRATION_14_15, DataModule.MIGRATION_15_16,
    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NetLensDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `migrate full chain 4 through 16`() {
        helper.createDatabase(TEST_DB, 4).close()
        helper.runMigrationsAndValidate(TEST_DB, 16, true, *allMigrations)
    }

    // v15 adds tags/notes/location to known_devices and the two wifi_survey_* tables — the
    // roadmap singles this migration out as hand-checked-only.
    @Test
    fun `migrate 14 to 15 adds user-detail columns and wifi survey tables`() {
        helper.createDatabase(TEST_DB, 14).apply {
            execSQL(
                "INSERT INTO known_devices (macAddress, hostname, ip, vendor, firstSeen, lastSeen, " +
                    "isKnown, deviceType, osGuess, customName, networkId) VALUES " +
                    "('AA:BB:CC:DD:EE:FF', 'host', '192.168.1.1', 'vendor', 0, 0, 0, NULL, NULL, NULL, NULL)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 15, true, DataModule.MIGRATION_14_15)

        migrated.query("SELECT tags, notes, location FROM known_devices").use { cursor ->
            org.junit.Assert.assertTrue(cursor.moveToFirst())
            // Additive migration: pre-existing rows get NULL for the new columns, not a crash.
            org.junit.Assert.assertTrue(cursor.isNull(0))
            org.junit.Assert.assertTrue(cursor.isNull(1))
            org.junit.Assert.assertTrue(cursor.isNull(2))
        }

        migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('wifi_survey_sessions', 'wifi_survey_points')",
        ).use { cursor ->
            var tableCount = 0
            while (cursor.moveToNext()) tableCount++
            org.junit.Assert.assertEquals(2, tableCount)
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
