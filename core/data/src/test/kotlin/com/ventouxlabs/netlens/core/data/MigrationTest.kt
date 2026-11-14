package com.ventouxlabs.netlens.core.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.ventouxlabs.netlens.core.data.di.DataModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Runs every real [DataModule] migration against the exported schema JSONs
 * (`core/data/schemas/com.ventouxlabs.netlens.core.data.NetLensDatabase/`) under Robolectric. This
 * is the only place any of the 15 migrations (4→5 … 18→19) get exercised — previously they were
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
        DataModule.MIGRATION_16_17, DataModule.MIGRATION_17_18, DataModule.MIGRATION_18_19,
    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NetLensDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // Schema-shape only: creates the v4 DB empty, so this validates that TableInfo matches at
    // every step but does NOT exercise row-preserving migrations (e.g. MIGRATION_11_12's
    // known_devices rename-and-copy). That needs its own data-integrity test as a follow-up.
    @Test
    fun `migrate full chain 4 through 19 preserves schema shape`() {
        helper.createDatabase(TEST_DB, 4).close()
        helper.runMigrationsAndValidate(TEST_DB, 19, true, *allMigrations)
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
            assertTrue(cursor.moveToFirst())
            // Additive migration: pre-existing rows get NULL for the new columns, not a crash.
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }

        migrated.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN " +
                "('wifi_survey_sessions', 'wifi_survey_points')",
        ).use { cursor ->
            var tableCount = 0
            while (cursor.moveToNext()) tableCount++
            assertEquals(2, tableCount)
        }
    }

    // v17 adds fingerprintConfidence/fingerprintEvidence to known_devices for confidence-scored
    // device fingerprinting — additive only, so a pre-existing row must migrate to NULL on both
    // new columns rather than a crash or a default value that looks like a real reading.
    @Test
    fun `migrate 16 to 17 adds fingerprint confidence and evidence columns`() {
        helper.createDatabase(TEST_DB, 16).apply {
            execSQL(
                "INSERT INTO known_devices (ip, firstSeen, lastSeen, isKnown) VALUES " +
                    "('192.168.1.1', 0, 0, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 17, true, DataModule.MIGRATION_16_17)

        migrated.query("SELECT fingerprintConfidence, fingerprintEvidence FROM known_devices").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertTrue(cursor.isNull(1))
        }
    }

    // v18 adds the scan-derived model column plus three user override columns — additive only,
    // so a pre-existing row must keep its data and read NULL on all four.
    @Test
    fun `migrate 17 to 18 keeps rows and adds null model and override columns`() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO known_devices (ip, hostname, deviceType, osGuess, firstSeen, lastSeen, isKnown) " +
                    "VALUES ('192.168.1.1', 'router', 'Router', 'Linux', 0, 0, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 18, true, DataModule.MIGRATION_17_18)

        migrated.query(
            "SELECT ip, hostname, deviceType, osGuess, model, deviceTypeOverride, osOverride, modelOverride " +
                "FROM known_devices",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("192.168.1.1", cursor.getString(0))
            assertEquals("router", cursor.getString(1))
            assertEquals("Router", cursor.getString(2))
            assertEquals("Linux", cursor.getString(3))
            for (column in 4..7) assertTrue(cursor.isNull(column))
            assertEquals(1, cursor.count)
        }
    }

    // v19 backfills one identifier per stored MAC. Four rows: locally administered (0xAA has the U/L
    // bit set), universal, no MAC, and a lowercase spelling of the locally administered MAC (the v17 unique index is case-sensitive, so
    // both spellings can coexist; the migration must collapse them via INSERT OR IGNORE, not abort).
    @Test
    fun `migrate 18 to 19 backfills mac identifiers and keeps user columns byte-identical`() {
        seedV18Devices()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        assertIdentifiersBackfilled(migrated)
        assertUserColumnsUnchanged(migrated)
    }

    @Test
    fun `migrate chained 17 to 19 backfills identifiers`() {
        helper.createDatabase(TEST_DB, 17).apply {
            execSQL(
                "INSERT INTO known_devices (macAddress, ip, firstSeen, lastSeen, isKnown) VALUES " +
                    "('00:11:22:33:44:55', '192.168.1.2', 10, 20, 0), (NULL, '192.168.1.9', 10, 20, 0)",
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 19, true, DataModule.MIGRATION_17_18, DataModule.MIGRATION_18_19,
        )

        migrated.query("SELECT kind, value, scopeKey, source, proven, firstSeen, lastSeen FROM device_identifiers").use {
            assertEquals(1, it.count)
            assertTrue(it.moveToFirst())
            assertEquals("MAC_UNIVERSAL", it.getString(0))
            assertEquals("001122334455", it.getString(1))
            assertEquals("", it.getString(2))
            assertEquals("ARP", it.getString(3))
            assertEquals(0, it.getInt(4))
            assertEquals(10L, it.getLong(5))
            assertEquals(20L, it.getLong(6))
        }
    }

    @Test
    fun `migrate 18 to 19 lets two devices share a mac after dropping the unique index`() {
        helper.createDatabase(TEST_DB, 18).close()
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        for (ip in listOf("10.0.0.1", "10.0.0.2")) {
            migrated.execSQL(
                "INSERT INTO known_devices (macAddress, ip, firstSeen, lastSeen, isKnown) VALUES " +
                    "('AA:BB:CC:DD:EE:FF', '$ip', 0, 0, 0)",
            )
        }
        migrated.query("SELECT COUNT(*) FROM known_devices").use {
            assertTrue(it.moveToFirst())
            assertEquals(2, it.getInt(0))
        }
    }

    private fun seedV18Devices() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                "INSERT INTO known_devices (id, macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, " +
                    "deviceType, osGuess, customName, networkId, tags, notes, location, model, " +
                    "deviceTypeOverride, osOverride, modelOverride) VALUES " +
                    "(1, 'AA:BB:CC:DD:EE:FF', 'router', '192.168.1.1', 'Acme', 100, 200, 1, 'Router', 'Linux', " +
                    "'My Router', 7, 'a,b', 'a note', 'Garage', 'X1', 'Switch', 'BSD', 'X2'), " +
                    "(2, '00:11:22:33:44:55', 'phone', '192.168.1.2', NULL, 110, 210, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL), " +
                    "(3, NULL, NULL, '192.168.1.3', NULL, 120, 220, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL), " +
                    "(4, 'aa:bb:cc:dd:ee:ff', 'dup', '192.168.1.4', NULL, 130, 230, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
            )
            close()
        }
    }

    private fun assertIdentifiersBackfilled(db: SupportSQLiteDatabase) {
        val rows = mutableListOf<List<Any?>>()
        db.query("SELECT deviceId, kind, value, scopeKey, source, proven, firstSeen, lastSeen FROM device_identifiers ORDER BY deviceId").use {
            while (it.moveToNext()) {
                rows += listOf(
                    it.getLong(0), it.getString(1), it.getString(2), it.getString(3),
                    it.getString(4), it.getInt(5), it.getLong(6), it.getLong(7),
                )
            }
        }
        // Device 4 collapses into device 1's identifier (lowest id wins); device 3 has no MAC.
        assertEquals(
            listOf(
                listOf(1L, "MAC_LOCAL", "aabbccddeeff", "", "ARP", 0, 100L, 200L),
                listOf(2L, "MAC_UNIVERSAL", "001122334455", "", "ARP", 0, 110L, 210L),
            ),
            rows,
        )
    }

    private fun assertUserColumnsUnchanged(db: SupportSQLiteDatabase) {
        db.query(
            "SELECT hostname, ip, vendor, firstSeen, lastSeen, isKnown, deviceType, osGuess, customName, " +
                "networkId, tags, notes, location, model, deviceTypeOverride, osOverride, modelOverride, " +
                "typeConfidence, osConfidence FROM known_devices WHERE id = 1",
        ).use {
            assertTrue(it.moveToFirst())
            val expected = listOf(
                "router", "192.168.1.1", "Acme", "100", "200", "1", "Router", "Linux", "My Router",
                "7", "a,b", "a note", "Garage", "X1", "Switch", "BSD", "X2",
            )
            expected.forEachIndexed { i, v -> assertEquals(v, it.getString(i)) }
            assertTrue(it.isNull(17))
            assertTrue(it.isNull(18))
        }
        db.query("SELECT COUNT(*) FROM known_devices").use {
            assertTrue(it.moveToFirst())
            assertEquals(4, it.getInt(0))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
