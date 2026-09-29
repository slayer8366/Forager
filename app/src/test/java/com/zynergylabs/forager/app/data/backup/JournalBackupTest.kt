package com.zynergylabs.forager.app.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.BackupException
import com.zynergylabs.forager.app.domain.RestoreMode
import com.zynergylabs.forager.app.domain.UnreadablePhotoPolicy
import com.zynergylabs.forager.app.domain.UnreadablePhotosException
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Journal backup and restore (dispatch 2026-09-28-127), through [RoomJournalBackup]'s public operations over
 * real Room databases in real files: the archive, Replace, Merge, refusal of a bad or newer backup, and that a
 * failure leaves the phone exactly as it was.
 *
 * What is compared is the data, not a proxy: every column of every row (`Phone.dump`), and every photo file's
 * bytes (`Phone.files`). A test that restores and then asserts "a row exists" would pass with half the columns
 * lost.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class JournalBackupTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val phones = mutableListOf<Phone>()
    private var made = 0

    private fun phone(hooks: RoomJournalBackupHooks = RoomJournalBackupHooks()): Phone =
        Phone(context, "phone-${made++}.db", tmp.root, hooks).also { phones += it }

    @After
    fun closeAll() = phones.forEach { runCatching { it.close() } }

    private fun Phone.restore(bytes: ByteArray, mode: RestoreMode) = runBlocking { service.restore(ByteArrayInputStream(bytes), mode) }

    private fun assertRefused(result: Result<*>, why: String) {
        val e = result.exceptionOrNull()
        assertNotNull("$why: expected a failure, got $result", e)
        assertTrue("$why: expected a BackupException, got ${e!!.javaClass.simpleName}: ${e.message}", e is BackupException)
    }

    private fun assertUnchanged(p: Phone, before: Map<String, List<String>>, filesBefore: Map<String, String>) {
        assertEquals("the phone's rows are unchanged", before, p.dump())
        assertEquals("the phone's photo files are unchanged", filesBefore, p.files())
        assertEquals("no scratch file is left behind", emptyList<String>(), p.scratchLeftovers())
    }

    // ---- the archive ---------------------------------------------------------------------------

    @Test
    fun `a backup is a zip of the manifest first, the snapshot and the photos, and nothing else`() {
        val a = phone().apply { seedFullJournal() }
        val zip = readZip(a.backUp())

        assertEquals(listOf("manifest.json", "forager.db", "photos/p1.jpg", "photos/p2.jpg", "photos/p3.jpg", "photos/p4.jpg"), zip.keys.toList())
        val manifest = JSONObject(String(zip.getValue("manifest.json")))
        assertEquals(1, manifest.getInt("formatVersion"))
        assertEquals(42L, manifest.getLong("appVersionCode"))
        assertEquals(17, manifest.getInt("schemaVersion"))
        assertTrue("created time is stamped", manifest.getLong("createdAtEpochMillis") > 0)
        val files = manifest.getJSONArray("files")
        assertEquals(5, files.length())
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            assertEquals("hash of ${f.getString("path")}", sha256(zip.getValue(f.getString("path"))), f.getString("sha256"))
            assertEquals("size of ${f.getString("path")}", zip.getValue(f.getString("path")).size.toLong(), f.getLong("bytes"))
        }
    }

    @Test
    fun `the snapshot inside a backup is a whole database that passes an integrity check and holds the phone's rows`() {
        val a = phone().apply { seedFullJournal() }
        val snapshot = tmp.newFile("snapshot.db").apply { writeBytes(readZip(a.backUp()).getValue("forager.db")) }

        val db = SQLiteDatabase.openDatabase(snapshot.path, null, SQLiteDatabase.OPEN_READONLY)
        db.use {
            assertEquals("ok", it.rawQuery("PRAGMA integrity_check", null).use { c -> c.moveToFirst(); c.getString(0) })
            assertEquals(17, it.version)
            assertEquals(a.count("track_points"), it.rawQuery("SELECT COUNT(*) FROM track_points", null).use { c -> c.moveToFirst(); c.getLong(0) })
        }
    }

    @Test
    fun `told to skip, a photo row whose file is missing is left out of the archive, counted, and the backup succeeds`() {
        val a = phone().apply {
            seedFullJournal()
            File(filesDir, "photos/p4.jpg").delete()
        }
        val out = java.io.ByteArrayOutputStream()
        val report = runBlocking { a.service.backUp(out, UnreadablePhotoPolicy.SKIP) }.getOrThrow()

        assertEquals(3, report.photoFiles)
        assertEquals(1, report.photoFilesMissing)
        assertFalse(readZip(out.toByteArray()).containsKey("photos/p4.jpg"))
        assertTrue("the gap is logged, not swallowed", a.logged.any { "p4" in it })
    }

    @Test
    fun `the snapshot holds the write lock while it copies, so no writer can commit mid-copy`() {
        var writerFinishedDuringCopy: Boolean? = null
        val writerDone = AtomicBoolean(false)
        lateinit var a: Phone
        a = phone(
            RoomJournalBackupHooks(snapshotLockHeld = {
                thread {
                    a.insert("waypoints", "id" to "late")
                    writerDone.set(true)
                }
                Thread.sleep(600)
                writerFinishedDuringCopy = writerDone.get()
            }),
        )
        a.seedFullJournal()

        runBlocking { a.service.backUp(java.io.ByteArrayOutputStream()) }.getOrThrow()

        assertEquals("a writer started during the copy must still be waiting", false, writerFinishedDuringCopy)
        val deadline = System.currentTimeMillis() + 5_000
        while (!writerDone.get() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue("and gets through once the copy is done", writerDone.get())
    }

    @Test
    fun `told to ask, a backup with an unreadable photo writes nothing to the sink and reports how many`() {
        val a = phone().apply {
            seedFullJournal()
            File(filesDir, "photos/p4.jpg").delete()
            File(filesDir, "photos/p2.jpg").delete()
        }
        val out = java.io.ByteArrayOutputStream()

        val result = runBlocking { a.service.backUp(out, UnreadablePhotoPolicy.ASK) }

        val e = result.exceptionOrNull()
        assertTrue("expected UnreadablePhotosException, got $e", e is UnreadablePhotosException)
        assertEquals(2, (e as UnreadablePhotosException).count)
        assertEquals("nothing was written before the person chose", 0, out.size())
        assertEquals("no scratch file is left behind", emptyList<String>(), a.scratchLeftovers())
    }

    @Test
    fun `told to ask, a backup with every photo readable is written whole`() {
        val a = phone().apply { seedFullJournal() }

        val out = java.io.ByteArrayOutputStream()
        val report = runBlocking { a.service.backUp(out, UnreadablePhotoPolicy.ASK) }.getOrThrow()

        assertEquals(0, report.photoFilesMissing)
        assertEquals(4, report.photoFiles)
    }

    @Test
    fun `a photo file that exists but cannot be read counts as unreadable`() {
        val a = phone().apply { seedFullJournal() }
        val file = File(a.filesDir, "photos/p3.jpg")
        assertTrue(file.setReadable(false, false))
        // A file this process owns can still be opened by root-like test runners; only assert when the OS honours it.
        org.junit.Assume.assumeFalse("the OS ignores the read bit here", file.canRead())

        val result = runBlocking { a.service.backUp(java.io.ByteArrayOutputStream(), UnreadablePhotoPolicy.ASK) }

        assertEquals(1, (result.exceptionOrNull() as UnreadablePhotosException).count)
    }

    // ---- Replace -------------------------------------------------------------------------------

    @Test
    fun `back up then Replace into an empty phone gives every journal table's rows and every photo file back, equal`() {
        val a = phone().apply { seedFullJournal() }
        for (t in JOURNAL_TABLE_NAMES) assertTrue("the seed fills $t, so equality below is not vacuous", a.count(t) > 0)
        val b = phone()

        val report = b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals(RestoreMode.REPLACE, report.mode)
        val regionId = b.scalar("SELECT id FROM offline_regions")!!.toLong()
        assertTrue("the restored region has a fresh id of this phone's own (owner, \"2 A\"): $regionId", regionId < 0)
        assertEquals("everything else equal, the region's id and the references to it aside", a.dump(JOURNAL_TABLE_NAMES), b.dump(JOURNAL_TABLE_NAMES).withRegionId(regionId, 7L))
        assertEquals("photo files, byte for byte", a.files(), b.files())
        assertEquals("planned trips are backed up and restored too (owner, \"4 A\")", 2L, b.count("planned_trips"))
        assertEquals("no scratch file is left behind", emptyList<String>(), b.scratchLeftovers())
    }

    @Test
    fun `Replace deletes the phone's own journal data, planned trips included, and the photo files no restored row uses`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply {
            insert("waypoints", "id" to "own-w", "name" to "Phone's own")
            addPhoto("own-p", ByteArray(20) { 9 })
            insert("mushroom_log_entries", "id" to "own-f", "isDraft" to 0L)
            insert("planned_trips", "id" to "own-trip")
        }

        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals(a.dump(JOURNAL_TABLE_NAMES), b.dump(JOURNAL_TABLE_NAMES).withRegionId(b.scalar("SELECT id FROM offline_regions")!!.toLong(), 7L))
        assertFalse("the phone's own photo file is gone", File(b.filesDir, "photos/own-p.jpg").exists())
        assertEquals(a.files(), b.files())
        assertEquals("its own planned trip is gone, the backup's two are in", 2L, b.count("planned_trips"))
        assertEquals(0L, b.scalar("SELECT COUNT(*) FROM planned_trips WHERE id='own-trip'")!!.toLong())
    }

    @Test
    fun `Replace keeps a live photo file that shares a name with the backup's until the swap is done, and puts it back if the swap fails`() {
        val a = phone().apply { seedFullJournal() }
        var seenDuringSwap: ByteArray? = null
        lateinit var b: Phone
        b = phone(
            RoomJournalBackupHooks(afterPhotosCopied = {
                // Copied in, rows not yet written: the phone's own same-named file must still be recoverable.
                seenDuringSwap = File(b.filesDir, "photos").listFiles()!!.firstOrNull { it.name.startsWith("p1.jpg") && it.name != "p1.jpg" }?.readBytes()
                error("stop here")
            }),
        )
        b.apply {
            addPhoto("p1", ByteArray(10) { 5 })
            insert("waypoints", "id" to "own-w")
        }
        val rows = b.dump()
        val files = b.files()

        val result = b.restore(a.backUp(), RestoreMode.REPLACE)

        assertRefused(result, "a fault after the photos are copied")
        assertNotNull("the phone's own p1 was set aside, not overwritten in place", seenDuringSwap)
        assertUnchanged(b, rows, files)
    }

    @Test
    fun `a failure after the rows are written and before the commit leaves the phone's rows and files exactly as they were`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone(RoomJournalBackupHooks(afterRowsWritten = { error("power cut") })).apply {
            insert("waypoints", "id" to "own-w", "name" to "Phone's own")
            addPhoto("own-p", ByteArray(20) { 9 })
        }
        val rows = b.dump()
        val files = b.files()

        val result = b.restore(a.backUp(), RestoreMode.REPLACE)

        assertRefused(result, "a fault inside the transaction")
        assertUnchanged(b, rows, files)
    }

    // ---- Merge ---------------------------------------------------------------------------------

    @Test
    fun `Merge adds what the phone lacks, skips an id it already has and keeps the phone's copy, and leaves no row dangling`() {
        val a = phone().apply {
            seedFullJournal()
            // A second entry keeping a waypoint that A itself no longer has, and a photo that exists on both phones.
            insert("cartography_entries", "id" to "e2", "text" to "Another day", "isDraft" to 0L)
            insert("cartography_entry_track_refs", "entryId" to "e2", "trackId" to "t1")
            insert("cartography_entry_waypoint_refs", "entryId" to "e2", "waypointId" to "gone-w")
            insert("cartography_entry_photo_refs", "entryId" to "e2", "photoId" to "p1")
        }
        val b = phone().apply {
            insert("waypoints", "id" to "w1", "name" to "Device copy")
            insert("waypoints", "id" to "own-w", "name" to "Phone's own")
            insert("planned_trips", "id" to "trip-1", "name" to "Device trip")
            addPhoto("p1", ByteArray(10) { 5 })
            insert("cartography_entries", "id" to "e1", "text" to "Device entry", "isDraft" to 0L)
            insert("cartography_entry_track_refs", "entryId" to "e1", "trackId" to "own-t", "name" to "device ref")
            insert("tracks", "id" to "own-t", "name" to "Own track")
            repeat(5) { insert("track_points", "trackId" to "own-t") }
        }
        val devicePhoto = File(b.filesDir, "photos/p1.jpg").readBytes()
        val backup = a.backUp()

        val report = b.restore(backup, RestoreMode.MERGE).getOrThrow()

        // The phone's copy wins, whole.
        assertEquals("Device copy", b.scalar("SELECT name FROM waypoints WHERE id='w1'"))
        assertEquals("Device entry", b.scalar("SELECT text FROM cartography_entries WHERE id='e1'"))
        assertEquals("a trip on both phones keeps the phone's copy", "Device trip", b.scalar("SELECT name FROM planned_trips WHERE id='trip-1'"))
        assertEquals("a trip only the backup has arrives", "1", b.scalar("SELECT COUNT(*) FROM planned_trips WHERE id='trip-2'"))
        assertEquals("only the phone's own ref row for e1: none of the backup's were added", "1", b.scalar("SELECT COUNT(*) FROM cartography_entry_track_refs WHERE entryId='e1'"))
        assertEquals("device ref", b.scalar("SELECT name FROM cartography_entry_track_refs WHERE entryId='e1'"))
        assertTrue("its own photo file is untouched", devicePhoto.contentEquals(File(b.filesDir, "photos/p1.jpg").readBytes()))
        assertEquals("the phone's own rows are all still there", "Phone's own", b.scalar("SELECT name FROM waypoints WHERE id='own-w'"))
        // What the phone lacked arrives.
        assertEquals("Big oak", b.scalar("SELECT name FROM waypoints WHERE id='w2'"))
        assertEquals("Morning loop", b.scalar("SELECT name FROM tracks WHERE id='t1'"))
        assertEquals("t1's points arrive", "3", b.scalar("SELECT COUNT(*) FROM track_points WHERE trackId='t1'"))
        assertTrue("with ids from this phone's own counter, not the backup's 1 to 3", b.scalar("SELECT MIN(id) FROM track_points WHERE trackId='t1'")!!.toLong() > 5)
        assertEquals("the region arrives, under a new id of the phone's own (owner, \"2 A\")", "1", b.scalar("SELECT COUNT(*) FROM offline_regions WHERE name='Cedar Creek' AND id < 0"))
        assertEquals("e2 arrives", "Another day", b.scalar("SELECT text FROM cartography_entries WHERE id='e2'"))
        assertEquals("f1's photo p1 exists on the phone, so the link is kept", "1", b.scalar("SELECT COUNT(*) FROM log_entry_photos WHERE entryId='f1' AND photoId='p1'"))
        // Photo files: only for inserted rows.
        assertEquals(listOf("p1.jpg", "p2.jpg", "p3.jpg", "p4.jpg"), File(b.filesDir, "photos").list()!!.sorted())
        // No dangling rows anywhere, and the one that would have dangled is dropped and counted.
        assertEquals("gone-w had no record to point at", "0", b.scalar("SELECT COUNT(*) FROM cartography_entry_waypoint_refs WHERE entryId='e2'"))
        assertEquals(emptyList<String>(), danglingRows(b))
        assertEquals("the one dependent row that would have dangled was dropped and counted", 1, report.rowsDropped)
    }

    @Test
    fun `Merge sets a link to NULL when its target is on neither phone, the rule the app already applies on a delete`() {
        val a = phone().apply {
            insert("waypoints", "id" to "w-lonely", "name" to "Lonely", "trackId" to "no-such-track")
            insert("mushroom_log_entries", "id" to "f-lonely", "isDraft" to 0L, "offlineRegionId" to 99L)
        }
        val b = phone()

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("NULL", b.scalar("SELECT COALESCE(CAST(trackId AS TEXT), 'NULL') FROM waypoints WHERE id='w-lonely'"))
        assertEquals("NULL", b.scalar("SELECT COALESCE(CAST(offlineRegionId AS TEXT), 'NULL') FROM mushroom_log_entries WHERE id='f-lonely'"))
    }

    @Test
    fun `Merge gives an incoming region a new id, negative so no future MapLibre id can meet it, and rewrites every reference`() {
        val a = phone().apply {
            insert("offline_regions", "id" to 7L, "name" to "Cedar Creek")
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 7L)
            insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 7L, "name" to "Cedar Creek")
        }
        val b = phone().apply {
            // The phone's own, different region that happens to carry the same MapLibre number.
            insert("offline_regions", "id" to 7L, "name" to "Phone's own region")
            insert("mushroom_log_entries", "id" to "own-f", "isDraft" to 0L, "offlineRegionId" to 7L)
        }

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("the phone's own region is untouched", "Phone's own region", b.scalar("SELECT name FROM offline_regions WHERE id=7"))
        assertEquals("and its find still points at it", "7", b.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='own-f'"))
        val newId = b.scalar("SELECT id FROM offline_regions WHERE name='Cedar Creek'")!!.toLong()
        assertTrue("the incoming region has a new id, negative: $newId", newId < 0)
        assertEquals("the incoming find names the new id", newId.toString(), b.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
        assertEquals("so does the entry's ref row", newId.toString(), b.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1'"))
        assertEquals(emptyList<String>(), danglingRows(b))
    }

    @Test
    fun `two incoming regions get two different new ids`() {
        val a = phone().apply {
            insert("offline_regions", "id" to 1L, "name" to "One")
            insert("offline_regions", "id" to 2L, "name" to "Two")
        }
        val b = phone().apply { insert("offline_regions", "id" to 1L, "name" to "Phone's own") }

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        val ids = listOf("One", "Two").map { b.scalar("SELECT id FROM offline_regions WHERE name='$it'")!!.toLong() }
        assertEquals(2, ids.toSet().size)
        assertTrue(ids.all { it < 0 })
        assertEquals(3L, b.count("offline_regions"))
    }

    @Test
    fun `Replace gives a restored region a fresh id, the same rule Merge uses, and rewrites every reference`() {
        val a = phone().apply {
            insert("offline_regions", "id" to 7L, "name" to "Cedar Creek")
            insert("offline_regions", "id" to 9L, "name" to "Fir Ridge")
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 7L)
            insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 9L, "name" to "Fir Ridge")
        }
        val b = phone().apply { insert("offline_regions", "id" to 7L, "name" to "Phone's own, to be replaced") }

        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("the phone's own region went with the replace", "0", b.scalar("SELECT COUNT(*) FROM offline_regions WHERE name LIKE 'Phone%'"))
        val cedar = b.scalar("SELECT id FROM offline_regions WHERE name='Cedar Creek'")!!.toLong()
        val fir = b.scalar("SELECT id FROM offline_regions WHERE name='Fir Ridge'")!!.toLong()
        assertTrue("fresh and negative: $cedar, $fir", cedar < 0 && fir < 0 && cedar != fir)
        assertEquals("the find names its region's new id", cedar.toString(), b.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
        assertEquals("the entry's ref row names its region's new id", fir.toString(), b.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1'"))
        assertEquals(emptyList<String>(), danglingRows(b))
    }

    @Test
    fun `after a Replace a later download that arrives with the backup's old id cannot overwrite a restored row`() {
        val a = phone().apply {
            insert("offline_regions", "id" to 7L, "name" to "Cedar Creek", "lat" to 45.5)
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 7L)
        }
        val b = phone()
        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        // A download finishing under MapLibre's id 7 upserts its own row by id (MapLibreOfflineMapRepository.download).
        kotlinx.coroutines.runBlocking {
            b.database.offlineRegionDao().upsert(
                com.zynergylabs.forager.app.data.local.OfflineRegionEntity(7L, "A new download", 1.0, 2.0, 5, 10.0, 15.0, 1_000L),
            )
        }

        assertEquals("the restored row is still there, unchanged", "1", b.scalar("SELECT COUNT(*) FROM offline_regions WHERE name='Cedar Creek' AND lat=45.5"))
        assertEquals("and the download has a row of its own", "A new download", b.scalar("SELECT name FROM offline_regions WHERE id=7"))
        assertEquals("the find still points at the restored region, not at the download", "Cedar Creek", b.scalar("SELECT name FROM offline_regions WHERE id = (SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1')"))
    }

    @Test
    fun `a find that names a region the backup does not hold has that link cleared by a Replace, not left to meet a future id`() {
        val a = phone().apply { insert("mushroom_log_entries", "id" to "f-lonely", "isDraft" to 0L, "offlineRegionId" to 99L) }
        val b = phone()

        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("NULL", b.scalar("SELECT COALESCE(CAST(offlineRegionId AS TEXT), 'NULL') FROM mushroom_log_entries WHERE id='f-lonely'"))
    }

    @Test
    fun `a Merge of a backup into the phone that made it changes nothing at all, its regions included`() {
        val a = phone().apply { seedFullJournal() }
        val rows = a.dump(JOURNAL_TABLE_NAMES)
        val files = a.files()

        val report = a.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        // Every record is skipped, the region too: it is the same region (owner 3.1, "no duplicate regions on restore").
        // This test used to say the one region arrived again under a new id, which is what 3.1 rules out.
        assertEquals(0, report.rowsInserted)
        assertEquals(rows, a.dump(JOURNAL_TABLE_NAMES))
        assertEquals(1L, a.count("offline_regions"))
        assertEquals(files, a.files())
        assertEquals("no scratch file is left behind", emptyList<String>(), a.scratchLeftovers())
    }

    // ---- no duplicate regions on restore (dispatch 2026-09-28-182, item 1; owner 3.1) ----------

    private fun Phone.region(id: Long, name: String, lat: Double, lng: Double, radiusKm: Long) =
        insert("offline_regions", "id" to id, "name" to name, "lat" to lat, "lng" to lng, "radiusKm" to radiusKm)

    private val metresPerDegree = 6_371_000.0 * Math.PI / 180.0

    private fun regionIds(p: Phone): List<Long> = p.scalar("SELECT GROUP_CONCAT(id) FROM (SELECT id FROM offline_regions ORDER BY id)")?.split(",")?.map { it.toLong() } ?: emptyList()

    @Test
    fun `Replace onto the phone that made the backup keeps its own regions, adds none, and points every reference at them`() {
        val a = phone().apply {
            region(7, "Cedar Creek", 45.5, -122.5, 5)
            region(9, "Fir Ridge", 46.0, -121.0, 2)
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 7L)
            insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 9L)
        }
        val regionsBefore = a.dump(listOf("offline_regions"))

        a.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("the same two regions, not four", listOf(7L, 9L), regionIds(a))
        assertEquals("and the live rows are untouched", regionsBefore, a.dump(listOf("offline_regions")))
        assertEquals("the find names the live region", "7", a.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
        assertEquals("the entry's ref names the live region", "9", a.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1'"))
        assertEquals(emptyList<String>(), danglingRows(a))
    }

    @Test
    fun `Replace keeps a phone region the backup matches, drops one it does not, and gives an unmatched incoming region a new negative id`() {
        val a = phone().apply {
            region(7, "Cedar Creek", 45.5, -122.5, 5)
            region(8, "Only in the backup", 44.0, -120.0, 3)
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 8L)
        }
        val b = phone().apply {
            region(3, "Cedar Creek", 45.5, -122.5, 5)
            region(4, "Only on the phone", 40.0, -100.0, 1)
        }

        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("Cedar Creek keeps the phone's own id 3", "3", b.scalar("SELECT id FROM offline_regions WHERE name='Cedar Creek'"))
        assertEquals("the phone-only region goes with the replace, as before", "0", b.scalar("SELECT COUNT(*) FROM offline_regions WHERE name='Only on the phone'"))
        val fresh = b.scalar("SELECT id FROM offline_regions WHERE name='Only in the backup'")!!.toLong()
        assertTrue("the unmatched one is fresh and negative: $fresh", fresh < 0)
        assertEquals("two regions in all", 2L, b.count("offline_regions"))
        assertEquals("the find names the fresh one", fresh.toString(), b.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
    }

    @Test
    fun `Merge skips an incoming region that is the phone's own within half a metre, and rewrites the incoming references to the phone's region`() {
        val a = phone().apply {
            region(7, "Cedar Creek", 45.5 + 0.5 / metresPerDegree, -122.5, 5)
            insert("mushroom_log_entries", "id" to "f1", "isDraft" to 0L, "offlineRegionId" to 7L)
            insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 7L)
        }
        val b = phone().apply { region(3, "Cedar Creek", 45.5, -122.5, 5) }
        val regionsBefore = b.dump(listOf("offline_regions"))

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("no second region, and the phone's row is untouched", regionsBefore, b.dump(listOf("offline_regions")))
        assertEquals("the incoming find names the phone's region", "3", b.scalar("SELECT offlineRegionId FROM mushroom_log_entries WHERE id='f1'"))
        assertEquals("so does the incoming entry's ref", "3", b.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs WHERE entryId='e1'"))
        assertEquals(emptyList<String>(), danglingRows(b))
    }

    /** Passes at base by design (the base inserts every region): it is the guard that the rule is a match, not "always skip". */
    @Test
    fun `Merge still adds an incoming region that differs in name, in radius, or by two metres`() {
        val a = phone().apply {
            region(1, "Cedar Creek Two", 45.5, -122.5, 5)
            region(2, "Cedar Creek", 45.5 + 2.0 / metresPerDegree, -122.5, 5)
            region(3, "Cedar Creek", 45.5, -122.5, 6)
        }
        val b = phone().apply { region(3, "Cedar Creek", 45.5, -122.5, 5) }

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("the phone's own plus the three that differ", 4L, b.count("offline_regions"))
        assertEquals("the three came in under new negative ids", "3", b.scalar("SELECT COUNT(*) FROM offline_regions WHERE id < 0"))
    }

    @Test
    fun `a backup that holds the same region twice, both matching the phone's one, adds nothing and keeps one reference per entry, in both modes`() {
        for (mode in RestoreMode.entries) {
            val a = phone().apply {
                region(1, "Cedar Creek", 45.5, -122.5, 5)
                region(2, "Cedar Creek", 45.5, -122.5, 5)
                insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
                insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 1L)
                insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 2L)
            }
            val b = phone().apply { region(3, "Cedar Creek", 45.5, -122.5, 5) }

            b.restore(a.backUp(), mode).getOrThrow()

            assertEquals("$mode: one region", listOf(3L), regionIds(b))
            assertEquals("$mode: one ref row for the entry, on the phone's region", "1|3", b.scalar("SELECT COUNT(*) || '|' || MIN(offlineRegionId) FROM cartography_entry_offline_region_refs WHERE entryId='e1'"))
        }
    }

    // ---- Replace and an orphaned region reference (item 6; -155, -156) --------------------------

    @Test
    fun `Replace drops an entry's region ref whose region the backup lacks, counts each, and logs it`() {
        val a = phone().apply {
            region(7, "Cedar Creek", 45.5, -122.5, 5)
            insert("cartography_entries", "id" to "e1", "isDraft" to 0L)
            insert("cartography_entries", "id" to "e2", "isDraft" to 0L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 7L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e1", "offlineRegionId" to 99L)
            insert("cartography_entry_offline_region_refs", "entryId" to "e2", "offlineRegionId" to 98L)
        }
        val b = phone()

        val report = b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("only the ref to the region the backup holds is left", "1", b.scalar("SELECT COUNT(*) FROM cartography_entry_offline_region_refs"))
        assertEquals("and it names that region's new id", b.scalar("SELECT id FROM offline_regions WHERE name='Cedar Creek'"), b.scalar("SELECT offlineRegionId FROM cartography_entry_offline_region_refs"))
        assertEquals("both orphans are counted", 2, report.rowsDropped)
        assertEquals("and each is logged, by table", 2, b.logged.count { "cartography_entry_offline_region_refs" in it && "left out" in it })
        assertEquals(emptyList<String>(), danglingRows(b))
    }

    // ---- refusing a bad backup -----------------------------------------------------------------

    @Test
    fun `a truncated zip is refused and leaves the phone untouched`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply { insert("waypoints", "id" to "own-w"); addPhoto("own-p", ByteArray(8) { 1 }) }
        val rows = b.dump(); val files = b.files()
        val backup = a.backUp()

        val result = b.restore(backup.copyOf(backup.size / 2), RestoreMode.REPLACE)

        assertRefused(result, "half a zip")
        assertUnchanged(b, rows, files)
    }

    @Test
    fun `a photo whose bytes do not match the manifest's hash is refused by name, and the phone is untouched`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply { insert("waypoints", "id" to "own-w"); addPhoto("own-p", ByteArray(8) { 1 }) }
        val rows = b.dump(); val files = b.files()
        val entries = readZip(a.backUp())
        entries["photos/p2.jpg"] = entries.getValue("photos/p2.jpg").also { it[0] = (it[0] + 1).toByte() } // a valid zip, a changed photo

        val result = b.restore(writeZip(entries), RestoreMode.REPLACE)

        assertRefused(result, "a changed photo")
        assertTrue("the reason names the file and the hash: ${result.exceptionOrNull()?.message}", result.exceptionOrNull()!!.message!!.let { "photos/p2.jpg" in it && "hash" in it })
        assertUnchanged(b, rows, files)
    }

    @Test
    fun `a snapshot that fails its integrity check is refused, and so is a zip with no manifest`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply { insert("waypoints", "id" to "own-w") }
        val rows = b.dump(); val files = b.files()
        val entries = readZip(a.backUp())

        val noManifest = LinkedHashMap(entries).also { it.remove("manifest.json") }
        assertRefused(b.restore(writeZip(noManifest), RestoreMode.REPLACE), "no manifest")

        val scrambled = LinkedHashMap(entries)
        val snapshot = scrambled.getValue("forager.db").copyOf()
        for (i in 4096 until minOf(snapshot.size, 4096 + 2048)) snapshot[i] = 0x55 // body of page 2 onward, header intact
        scrambled["forager.db"] = snapshot
        val manifest = JSONObject(String(scrambled.getValue("manifest.json")))
        manifest.getJSONArray("files").let { files ->
            for (i in 0 until files.length()) if (files.getJSONObject(i).getString("path") == "forager.db") {
                files.getJSONObject(i).put("sha256", sha256(snapshot))
            }
        }
        scrambled["manifest.json"] = manifest.toString().toByteArray()
        assertRefused(b.restore(writeZip(scrambled), RestoreMode.REPLACE), "a damaged snapshot with a matching hash")

        assertUnchanged(b, rows, files)
    }

    @Test
    fun `an archive entry the manifest does not list, or one that would land outside the scratch folder, is refused`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply { insert("waypoints", "id" to "own-w") }
        val rows = b.dump(); val files = b.files()
        val outside = File(tmp.root, "evil.txt")

        for (name in listOf("../../evil.txt", "photos/../../evil.txt", "extra.bin")) {
            val entries = readZip(a.backUp())
            entries[name] = "not a backup file".toByteArray()

            assertRefused(b.restore(writeZip(entries), RestoreMode.REPLACE), "an entry named $name")
            assertFalse("nothing was written outside the scratch folder for $name", outside.exists())
        }
        assertUnchanged(b, rows, files)
    }

    // ---- F3: a kept track keeps its path (dispatch 2026-09-28-195, item 6) -----------------------
    //
    // The dispatch's ruling, quoted: "`cartography_entry_track_refs` loses `needs = tracks`, so Merge keeps a
    // track ref whose track is missing; the backup report's decision 8 is reversed for track refs only. Waypoint,
    // region and find refs keep today's behaviour." And: "the new table is journal data, owned by entries, with
    // no `needs`". `danglingRows` reads each OWNED spec's owner and `needs`, so with the track ref's `needs`
    // gone it no longer lists a track ref that names no track: that is the expectation that changes, and it is
    // deliberate, not a loosened check (the tests below assert the row is present, and the waypoint case
    // still drops).

    private val keptPath = listOf(com.zynergylabs.forager.app.domain.model.LatLng(45.2, -122.5), com.zynergylabs.forager.app.domain.model.LatLng(45.21, -122.51))

    private fun Phone.savedPath(entryId: String, trackId: String): List<com.zynergylabs.forager.app.domain.model.LatLng>? =
        database.openHelper.readableDatabase.query("SELECT path FROM cartography_entry_track_paths WHERE entryId = ? AND trackId = ?", arrayOf<Any?>(entryId, trackId)).use { c ->
            if (c.moveToFirst()) com.zynergylabs.forager.app.domain.TrackPathCodec.decode(c.getBlob(0)) else null
        }

    /** A phone whose entry `e-gone` kept a track that has since been deleted: the ref and its saved path, and no track row. */
    private fun phoneWithDeletedTrackKept(): Phone = phone().apply {
        insert("cartography_entries", "id" to "e-gone", "text" to "Kept a track later deleted", "isDraft" to 0L)
        insert("cartography_entry_track_refs", "entryId" to "e-gone", "trackId" to "deleted-t", "name" to "Old ridge")
        insert("cartography_entry_track_paths", "entryId" to "e-gone", "trackId" to "deleted-t", "path" to com.zynergylabs.forager.app.domain.TrackPathCodec.encode(keptPath))
    }

    @Test
    fun `Replace keeps an entry's ref to a deleted track and its saved path, the path byte for byte`() {
        val a = phoneWithDeletedTrackKept()
        val b = phone()

        b.restore(a.backUp(), RestoreMode.REPLACE).getOrThrow()

        assertEquals("Old ridge", b.scalar("SELECT name FROM cartography_entry_track_refs WHERE entryId='e-gone' AND trackId='deleted-t'"))
        assertEquals(keptPath, b.savedPath("e-gone", "deleted-t"))
        assertEquals("there is no track row, and none was invented", 0L, b.count("tracks"))
    }

    @Test
    fun `Merge keeps an entry's ref to a track that is on neither phone, with its saved path, and drops nothing`() {
        val a = phoneWithDeletedTrackKept()
        val b = phone().apply { insert("waypoints", "id" to "own-w", "name" to "Phone's own") }

        val report = b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("Old ridge", b.scalar("SELECT name FROM cartography_entry_track_refs WHERE entryId='e-gone' AND trackId='deleted-t'"))
        assertEquals(keptPath, b.savedPath("e-gone", "deleted-t"))
        assertEquals("no row was dropped: a track ref whose track is missing is kept now", 0, report.rowsDropped)
        assertEquals(emptyList<String>(), danglingRows(b))
    }

    @Test
    fun `Merge still drops a waypoint ref whose waypoint is gone, in the same entry that keeps its deleted track`() {
        val a = phoneWithDeletedTrackKept().apply {
            insert("cartography_entry_waypoint_refs", "entryId" to "e-gone", "waypointId" to "gone-w", "name" to "Old gate")
        }
        val b = phone()

        val report = b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals("the track ref stays", "1", b.scalar("SELECT COUNT(*) FROM cartography_entry_track_refs WHERE entryId='e-gone'"))
        assertEquals("the waypoint ref keeps today's rule and is dropped", "0", b.scalar("SELECT COUNT(*) FROM cartography_entry_waypoint_refs WHERE entryId='e-gone'"))
        assertEquals("and counted", 1, report.rowsDropped)
    }

    @Test
    fun `Merge of an entry the phone already has keeps the phone's saved path and adds none of the backup's`() {
        val a = phoneWithDeletedTrackKept()
        val phonesPath = listOf(com.zynergylabs.forager.app.domain.model.LatLng(1.0, 2.0))
        val b = phone().apply {
            insert("cartography_entries", "id" to "e-gone", "text" to "The phone's own copy", "isDraft" to 0L)
            insert("cartography_entry_track_refs", "entryId" to "e-gone", "trackId" to "deleted-t", "name" to "Phone's ref")
            insert("cartography_entry_track_paths", "entryId" to "e-gone", "trackId" to "deleted-t", "path" to com.zynergylabs.forager.app.domain.TrackPathCodec.encode(phonesPath))
        }

        b.restore(a.backUp(), RestoreMode.MERGE).getOrThrow()

        assertEquals(phonesPath, b.savedPath("e-gone", "deleted-t"))
        assertEquals(1L, b.count("cartography_entry_track_paths"))
    }

    @Test
    fun `a backup from schema 16 restores through the registered migration, gaining an empty path table`() {
        val helper = OlderBackup.helper()
        val old = OlderBackup.build(helper, tmp.root, version = 16)
        val b = phone().apply { insert("waypoints", "id" to "own-w", "name" to "Phone's own") }

        b.restore(old.archive, RestoreMode.REPLACE).getOrThrow()

        assertEquals("a value from the old snapshot survived the migration", "name-1", b.scalar("SELECT name FROM waypoints"))
        for (t in JOURNAL_TABLE_NAMES - "cartography_entry_track_paths") assertEquals("one seeded row in $t", 1L, b.count(t))
        assertEquals("no path was invented for the old backup's track ref", 0L, b.count("cartography_entry_track_paths"))
        assertEquals("the scratch copy is gone", emptyList<String>(), b.scratchLeftovers())
    }

    // ---- versions ------------------------------------------------------------------------------

    @Test
    fun `a backup newer than the app is refused, with the reason logged, and the phone is untouched`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone().apply { insert("waypoints", "id" to "own-w") }
        val rows = b.dump(); val files = b.files()
        val entries = readZip(a.backUp())
        val manifest = JSONObject(String(entries.getValue("manifest.json"))).put("schemaVersion", 18)
        entries["manifest.json"] = manifest.toString().toByteArray()

        val result = b.restore(writeZip(entries), RestoreMode.REPLACE)

        assertRefused(result, "a backup from a newer schema")
        assertTrue("the reason is logged: ${b.logged}", b.logged.any { "18" in it && "newer" in it })
        assertUnchanged(b, rows, files)
    }

    @Test
    fun `a backup whose format version this build does not know is refused`() {
        val a = phone().apply { seedFullJournal() }
        val b = phone()
        val entries = readZip(a.backUp())
        entries["manifest.json"] = JSONObject(String(entries.getValue("manifest.json"))).put("formatVersion", 2).toString().toByteArray()

        assertRefused(b.restore(writeZip(entries), RestoreMode.REPLACE), "an unknown format version")
        assertEquals(0L, b.count("waypoints"))
    }

    @Test
    fun `a backup from schema 15 restores through the registered migration, on a scratch copy`() {
        val helper = OlderBackup.helper()
        val old = OlderBackup.build(helper, tmp.root, version = 15)
        val b = phone().apply { insert("waypoints", "id" to "own-w", "name" to "Phone's own") }

        b.restore(old.archive, RestoreMode.REPLACE).getOrThrow()

        assertEquals("the migrated column exists and took its default", "0", b.scalar("SELECT shownOnMap FROM cartography_entries"))
        assertEquals("a value from the old snapshot survived the migration", "name-1", b.scalar("SELECT name FROM waypoints"))
        assertEquals("text-1", b.scalar("SELECT text FROM cartography_entries"))
        // A v15 file has no path table: it arrives through MIGRATION_16_17, present and empty (F3).
        for (t in JOURNAL_TABLE_NAMES - "cartography_entry_track_paths") assertEquals("one seeded row in $t", 1L, b.count(t))
        assertEquals("the path table the migration created is there and empty", 0L, b.count("cartography_entry_track_paths"))
        assertEquals("the scratch copy is gone", emptyList<String>(), b.scratchLeftovers())
    }

    @Test
    fun `an older backup's migration runs on a scratch copy and never on the live database`() {
        val helper = OlderBackup.helper()
        val old = OlderBackup.build(helper, tmp.root, version = 15)
        var liveVersionDuringRestore = -1
        lateinit var b: Phone
        b = phone(RoomJournalBackupHooks(afterPhotosCopied = { liveVersionDuringRestore = b.database.openHelper.readableDatabase.version }))

        b.restore(old.archive, RestoreMode.REPLACE).getOrThrow()

        assertEquals("the live database stayed at the app's own version throughout", 17, liveVersionDuringRestore)
    }

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
        assertEquals("the schema version this build restores up to is the one the database declares", 17, com.zynergylabs.forager.app.data.local.ForagerDatabase.SCHEMA_VERSION)
        assertEquals(com.zynergylabs.forager.app.data.local.ForagerDatabase.SCHEMA_VERSION, phone().database.openHelper.readableDatabase.version)
    }

    /** These rows with a restored region's fresh [new] id read as its old [old]: the id column and every reference to it. */
    private fun Map<String, List<String>>.withRegionId(new: Long, old: Long): Map<String, List<String>> =
        mapValues { (_, rows) ->
            rows.map { row ->
                row.replace("offlineRegionId=$new", "offlineRegionId=$old")
                    .replace(Regex("(^|\\|)id=$new(\\||$)")) { it.value.replace("id=$new", "id=$old") }
            }.sorted()
        }

    // ---- helpers -------------------------------------------------------------------------------

    /** Every owned row's owner and named records, checked to exist: the rows a Merge must never leave dangling. */
    private fun danglingRows(p: Phone): List<String> {
        val out = mutableListOf<String>()
        for (spec in JournalTables.journal.filter { it.kind == JournalTables.Kind.OWNED }) {
            val refs = listOfNotNull(spec.owner) + spec.needs
            for (r in refs) {
                val n = p.scalar("SELECT COUNT(*) FROM `${spec.name}` WHERE `${r.column}` NOT IN (SELECT `${r.targetKey}` FROM `${r.table}`)")!!.toLong()
                if (n > 0) out += "${spec.name}.${r.column} -> ${r.table}: $n"
            }
        }
        return out
    }
}

internal val JOURNAL_TABLE_NAMES = listOf(
    "mushroom_log_entries", "log_photos", "log_entry_photos", "tracks", "track_points", "waypoints", "offline_regions",
    "planned_trips", "cartography_entries", "cartography_entry_track_refs", "cartography_entry_waypoint_refs",
    "cartography_entry_offline_region_refs", "cartography_entry_find_refs", "cartography_entry_photo_refs", "cartography_entry_track_paths",
)
