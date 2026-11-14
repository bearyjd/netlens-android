package com.ventouxlabs.netlens.core.data

import android.database.sqlite.SQLiteConstraintException
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
    // bit set), universal, no MAC, and a lowercase spelling of the locally administered MAC (the v17
    // unique index is case-sensitive, so both spellings coexist and must collapse via OR IGNORE).
    @Test
    fun `migrate 18 to 19 backfills mac identifiers and leaves every known_devices row identical`() {
        val before = seedV18Devices()

        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        assertEquals(before, snapshot(migrated, V18_COLUMNS))
        assertIdentifiersBackfilled(migrated)
    }

    @Test
    fun `migrate chained 17 to 19 backfills identifiers and leaves every known_devices row identical`() {
        val before = helper.createDatabase(TEST_DB, 17).run {
            execSQL(
                "INSERT INTO known_devices (id, macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, " +
                    "deviceType, osGuess, fingerprintConfidence, fingerprintEvidence, customName, networkId, " +
                    "tags, notes, location) VALUES " +
                    "(1, '00:11:22:33:44:55', 'h', '192.168.1.2', 'V', 10, 20, 1, 'Router', 'Linux', 85, 'port 22', " +
                    "'Name', 3, 't1,t2', 'note', 'Attic'), " +
                    "(2, NULL, NULL, '192.168.1.9', NULL, 11, 21, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
            )
            snapshot(this, V17_COLUMNS).also { close() }
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB, 19, true, DataModule.MIGRATION_17_18, DataModule.MIGRATION_18_19,
        )

        assertEquals(before, snapshot(migrated, V17_COLUMNS))
        assertEquals(
            listOf(listOf("1", "MAC_UNIVERSAL", "001122334455", "", "ARP", "0", "10", "20")),
            rows(migrated, "SELECT deviceId, kind, value, scopeKey, source, proven, firstSeen, lastSeen FROM device_identifiers"),
        )
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
        assertEquals(listOf(listOf("2")), rows(migrated, "SELECT COUNT(*) FROM known_devices"))
    }

    @Test
    fun `backfill accepts hyphen and mixed-case spellings`() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                "INSERT INTO known_devices (id, macAddress, ip, firstSeen, lastSeen, isKnown) VALUES " +
                    "(1, '00-11-22-33-44-55', '10.0.0.1', 0, 0, 0), (2, 'aA:bB:cC:dD:eE:fF', '10.0.0.2', 0, 0, 0)",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        assertEquals(
            listOf(listOf("1", "MAC_UNIVERSAL", "001122334455"), listOf("2", "MAC_LOCAL", "aabbccddeeff")),
            rows(migrated, "SELECT deviceId, kind, value FROM device_identifiers ORDER BY deviceId"),
        )
    }

    @Test
    fun `backfill classifies every second nibble by the locally administered bit`() {
        val nibbles = "0123456789abcdef"
        helper.createDatabase(TEST_DB, 18).apply {
            nibbles.forEachIndexed { i, n ->
                execSQL(
                    "INSERT INTO known_devices (id, macAddress, ip, firstSeen, lastSeen, isKnown) VALUES " +
                        "(${i + 1}, '0$n:11:22:33:44:55', '10.0.0.${i + 1}', 0, 0, 0)",
                )
            }
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        val expected = nibbles.mapIndexed { i, n ->
            val kind = if (n in "2367abef") "MAC_LOCAL" else "MAC_UNIVERSAL"
            listOf((i + 1).toString(), kind, "0${n}1122334455")
        }
        assertEquals(expected, rows(migrated, "SELECT deviceId, kind, value FROM device_identifiers ORDER BY deviceId"))
    }

    @Test
    fun `malformed macs yield no identifier and the parent row survives`() {
        helper.createDatabase(TEST_DB, 18).apply {
            val macs = listOf("NULL", "''", "'---'", "'notamac'", "'AA:BB:CC:DD:EE'", "'AA:BB:CC:DD:EE:GG'")
            macs.forEachIndexed { i, mac ->
                execSQL(
                    "INSERT INTO known_devices (id, macAddress, ip, firstSeen, lastSeen, isKnown) VALUES " +
                        "(${i + 1}, $mac, '10.0.0.${i + 1}', 0, 0, 0)",
                )
            }
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        assertEquals(listOf(listOf("0")), rows(migrated, "SELECT COUNT(*) FROM device_identifiers"))
        assertEquals(listOf(listOf("6")), rows(migrated, "SELECT COUNT(*) FROM known_devices"))
    }

    @Test
    fun `10000 distinct macs migrate to exactly 10000 identifiers`() {
        helper.createDatabase(TEST_DB, 18).apply {
            execSQL(
                "WITH RECURSIVE c(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM c WHERE i < 10000) " +
                    "INSERT INTO known_devices (macAddress, ip, firstSeen, lastSeen, isKnown) " +
                    "SELECT printf('%012x', 1000000 + i), '10.0.0.1', 0, 0, 0 FROM c",
            )
            close()
        }
        val migrated = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)

        val expected = (1..10_000).map { listOf(it.toString(), "MAC_UNIVERSAL", "%012x".format(1_000_000 + it)) }
        assertEquals(expected, rows(migrated, "SELECT deviceId, kind, value FROM device_identifiers ORDER BY deviceId"))
    }

    @Test
    fun `deleting a device cascades to its children and merge pairs only`() {
        helper.createDatabase(TEST_DB, 18).close()
        val db = helper.runMigrationsAndValidate(TEST_DB, 19, true, DataModule.MIGRATION_18_19)
        db.setForeignKeyConstraintsEnabled(true)
        (1..3).forEach { id ->
            db.execSQL("INSERT INTO known_devices (id, ip, firstSeen, lastSeen, isKnown) VALUES ($id, '10.0.0.$id', 0, 0, 0)")
            db.execSQL(
                "INSERT INTO device_identifiers (deviceId, kind, value, scopeKey, source, proven, firstSeen, lastSeen) " +
                    "VALUES ($id, 'MAC_UNIVERSAL', 'v$id', '', 'ARP', 0, 0, 0)",
            )
            db.execSQL("INSERT INTO device_observations (deviceId, source, field, value, weight, detail, lastSeen) VALUES ($id, 'MDNS', 'OS', 'x', 1, NULL, 0)")
            db.execSQL("INSERT INTO device_sightings (deviceId, networkKey, networkLabel, lastIp, firstSeen, lastSeen) VALUES ($id, 'n', NULL, '10.0.0.$id', 0, 0)")
        }
        for ((low, high) in listOf(1 to 2, 2 to 3, 1 to 3)) {
            db.execSQL("INSERT INTO device_merge_decisions VALUES ($low, $high, 'SUGGESTED', 5, NULL, 0)")
        }

        db.execSQL("DELETE FROM known_devices WHERE id = 2")

        assertEquals(listOf(listOf("1", "3")), rows(db, "SELECT deviceIdLow, deviceIdHigh FROM device_merge_decisions"))
        for (table in listOf("device_identifiers", "device_observations", "device_sightings")) {
            assertEquals(listOf(listOf("1"), listOf("3")), rows(db, "SELECT deviceId FROM $table ORDER BY deviceId"))
        }
        assertEquals(listOf(listOf("1"), listOf("3")), rows(db, "SELECT id FROM known_devices ORDER BY id"))
        val orphan = runCatching {
            db.execSQL("INSERT INTO device_sightings (deviceId, networkKey, networkLabel, lastIp, firstSeen, lastSeen) VALUES (99, 'n', NULL, '1.1.1.1', 0, 0)")
        }
        assertTrue(orphan.exceptionOrNull() is SQLiteConstraintException)
    }

    private fun seedV18Devices(): List<List<String?>> =
        helper.createDatabase(TEST_DB, 18).run {
            execSQL(
                "INSERT INTO known_devices (id, macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, " +
                    "deviceType, osGuess, fingerprintConfidence, fingerprintEvidence, customName, networkId, " +
                    "tags, notes, location, model, deviceTypeOverride, osOverride, modelOverride) VALUES " +
                    "(1, 'AA:BB:CC:DD:EE:FF', 'router', '192.168.1.1', 'Acme', 100, 200, 1, 'Router', 'Linux', " +
                    "90, 'port 22; ttl 64', 'My Router', 7, 'a,b', 'a note', 'Garage', 'X1', 'Switch', 'BSD', 'X2'), " +
                    "(2, '00:11:22:33:44:55', 'phone', '192.168.1.2', NULL, 110, 210, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL), " +
                    "(3, NULL, NULL, '192.168.1.3', NULL, 120, 220, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL), " +
                    "(4, 'aa:bb:cc:dd:ee:ff', 'dup', '192.168.1.4', NULL, 130, 230, 0, NULL, NULL, " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
            )
            snapshot(this, V18_COLUMNS).also { close() }
        }

    private fun assertIdentifiersBackfilled(db: SupportSQLiteDatabase) {
        // Device 4 collapses into device 1's identifier (lowest id wins); device 3 has no MAC.
        assertEquals(
            listOf(
                listOf("1", "MAC_LOCAL", "aabbccddeeff", "", "ARP", "0", "100", "200"),
                listOf("2", "MAC_UNIVERSAL", "001122334455", "", "ARP", "0", "110", "210"),
            ),
            rows(db, "SELECT deviceId, kind, value, scopeKey, source, proven, firstSeen, lastSeen FROM device_identifiers ORDER BY deviceId"),
        )
    }

    private fun snapshot(db: SupportSQLiteDatabase, columns: String): List<List<String?>> {
        return rows(db, "SELECT $columns FROM known_devices ORDER BY id")
    }

    private fun rows(db: SupportSQLiteDatabase, sql: String): List<List<String?>> =
        db.query(sql).use { c ->
            buildList {
                while (c.moveToNext()) add((0 until c.columnCount).map { i -> if (c.isNull(i)) null else c.getString(i) })
            }
        }

    private companion object {
        const val TEST_DB = "migration-test"
        const val V17_COLUMNS = "id, macAddress, hostname, ip, vendor, firstSeen, lastSeen, isKnown, deviceType, " +
            "osGuess, fingerprintConfidence, fingerprintEvidence, customName, networkId, tags, notes, location"
        const val V18_COLUMNS = "$V17_COLUMNS, model, deviceTypeOverride, osOverride, modelOverride"
    }
}
