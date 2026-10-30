package com.zynergylabs.forager.app.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The backup's table list against the exported schema: every table is journal data or explicitly excluded, and each
 * spec's key columns are the schema's. Moved out of [JournalBackupTest] unchanged (dispatch 2026-10-30-02, Amendment 1:
 * the move-only split before that file, over 800 lines, gains the route tables), with [JOURNAL_TABLE_NAMES] beside it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalTablesSchemaTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val phone: Phone by lazy { Phone(context, "phone-0.db", tmp.root, RoomJournalBackupHooks()) }

    @After
    fun closeAll() { runCatching { phone.close() } }

    // ---- what the table list is ----------------------------------------------------------------

    @Test
    fun `every table in the schema is either journal data or explicitly excluded, and the specs match the schema`() {
        val schemaTables = Phone.allTables()
        val journal = JournalTables.journal.map { it.name }
        assertEquals("the journal list, as ruling 3 B names it", JOURNAL_TABLE_NAMES.sorted(), journal.sorted())
        assertEquals("nothing is both, and nothing is neither", schemaTables.sorted(), (journal + JournalTables.excluded.keys).sorted())
        assertEquals(emptyList<String>(), journal.intersect(JournalTables.excluded.keys).toList())
        val pk = schemaPrimaryKeys(Phone.SCHEMA)
        for (spec in JournalTables.journal) assertEquals("${spec.name}'s key columns", pk.getValue(spec.name), spec.keyColumns)
        assertEquals("the schema version this build restores up to is the one the database declares", 22, com.zynergylabs.forager.app.data.local.ForagerDatabase.SCHEMA_VERSION)
        assertEquals(com.zynergylabs.forager.app.data.local.ForagerDatabase.SCHEMA_VERSION, phone.database.openHelper.readableDatabase.version)
    }
}

internal val JOURNAL_TABLE_NAMES = listOf(
    "mushroom_log_entries", "log_photos", "log_entry_photos", "tracks", "track_points", "waypoints", "offline_regions",
    "planned_trips", "cartography_entries", "cartography_entry_track_refs", "cartography_entry_waypoint_refs",
    "cartography_entry_offline_region_refs", "cartography_entry_find_refs", "cartography_entry_photo_refs", "cartography_entry_track_paths",
)
