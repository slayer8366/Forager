package com.zynergylabs.forager.app.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.backup.Phone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Download again" makes a new MapLibre region with a new id, so the restored row's identity has to move to it: the
 * finds and the entries' region references follow, and the old row goes, in one transaction. Real Room, real rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomOfflineRegionIdReplacerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val phones = mutableListOf<Phone>()

    @After
    fun close() = phones.forEach { runCatching { it.close() } }

    private fun phone(): Phone = Phone(ApplicationProvider.getApplicationContext<Context>(), "replacer-${phones.size}.db", tmp.root).also { phones += it }

    private fun seed(p: Phone) {
        p.insert("offline_regions", "id" to 5L, "name" to "Restored")
        p.insert("offline_regions", "id" to 42L, "name" to "Downloaded again")
        p.insert("offline_regions", "id" to 7L, "name" to "Unrelated")
        p.insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 5L)
        p.insert("mushroom_log_entries", "id" to "f7", "isDraft" to 0L, "offlineRegionId" to 7L)
        p.insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
        p.insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 5L)
        p.insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 7L)
    }

    @Test
    fun `references move to the new id, the old row goes, and other regions are untouched`() {
        val p = phone().also(::seed)

        val result = runBlocking { RoomOfflineRegionIdReplacer(p.database).replace(5L, 42L) }

        assertTrue(result.isSuccess)
        assertEquals("42", p.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
        assertEquals("7", p.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f7'"))
        assertEquals("42", p.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1' AND offlineRegionId <> 7"))
        assertEquals("7", p.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1' AND offlineRegionId = 7"))
        assertEquals("0", p.scalar("SELECT COUNT(*) FROM offline_regions WHERE id=5"))
        assertEquals("Downloaded again", p.scalar("SELECT name FROM offline_regions WHERE id=42"))
        assertEquals("Unrelated", p.scalar("SELECT name FROM offline_regions WHERE id=7"))
    }

    @Test
    fun `an entry that already names the new region keeps one reference, not two`() {
        val p = phone().also(::seed)
        p.insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 42L)

        val result = runBlocking { RoomOfflineRegionIdReplacer(p.database).replace(5L, 42L) }

        assertTrue(result.isSuccess)
        assertEquals("1", p.scalar("SELECT COUNT(*) FROM cartography_entry_offline_region_refs WHERE offlineRegionId = 42"))
        assertEquals("0", p.scalar("SELECT COUNT(*) FROM cartography_entry_offline_region_refs WHERE offlineRegionId = 5"))
    }

    @Test
    fun `replacing an id with itself changes nothing and does not delete the row`() {
        val p = phone().also(::seed)
        val before = p.dump()

        val result = runBlocking { RoomOfflineRegionIdReplacer(p.database).replace(42L, 42L) }

        assertTrue(result.isSuccess)
        assertEquals(before, p.dump())
    }
}
