package com.zynergylabs.forager.app.data.local

// PROBE — wire-migration-tests dispatch §2, 2026-09-13. One question: does Room's MigrationTestHelper
// run under Robolectric in this project, reading the committed schema JSON from app/schemas/ as test
// assets and validating a migration's result against the next committed file? Creates v4 from 4.json,
// inserts one row, runs MIGRATION_4_5, validates against 5.json. If it cannot find the schema asset or
// cannot obtain Instrumentation, that is the structural answer and the finding.

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MigrationSchemaProbeTest {
    private val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ForagerDatabase::class.java,
    )

    @Test
    fun `PROBE - v4 from 4_json, migrate 4 to 5, validate against 5_json`() {
        val name = "probe-4-5.db"
        helper.createDatabase(name, 4).use { db ->
            db.execSQL("INSERT INTO mushroom_log_entries (id, lat, lng, foundOnEpochDay) VALUES ('probe', 45.5, -122.6, 20000)")
        }
        val migrated = helper.runMigrationsAndValidate(name, 5, true, MIGRATION_4_5)
        migrated.query("SELECT COUNT(*) FROM mushroom_log_entries").use { c ->
            c.moveToFirst(); assertEquals("PROBE: row survived 4->5", 1, c.getInt(0))
        }
        migrated.close()
    }
}
