package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.photo.FilePhotoExporter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Part 2 finding (e), item 61: the Gallery row for a photo with a time read back with a NULL `datetaken`
 * (`docs/audits/2026-09-29-stage-device-check-part-2-run-record.md`, Session 1 table, item 61), though
 * [FilePhotoExporter] writes DATE_TAKEN at insert. What a Gallery shows is the row after every write, so
 * these tests read [FakeMediaProvider.rowNow], not just the insert's values.
 *
 * **What Robolectric cannot show.** Whether the platform's media scan on publish really replaces
 * DATE_TAKEN is the planner's unverified guess and is device-only. [FakeMediaProvider.scanOnPublishClearsDateTaken]
 * models that guess so the test can bite; it is a model of the observation, not the platform.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoExporterDateTakenTest {

    @Before
    fun setUp() {
        FakeMediaProvider.reset()
        Robolectric.buildContentProvider(FakeMediaProvider::class.java).create("media")
    }

    @After
    fun tearDown() = FakeMediaProvider.reset()

    private fun store(id: String): String {
        val context = ApplicationProvider.getApplicationContext<Application>()
        File(context.filesDir, "photos").mkdirs()
        File(context.filesDir, "photos/$id.jpg").writeBytes(EXPORT_TEST_JPEG)
        return "photos/$id.jpg"
    }

    private fun save(photo: LogPhoto) {
        val exporter = FilePhotoExporter(ApplicationProvider.getApplicationContext<Application>())
        runBlocking { exporter.saveToGallery(photo) }.getOrThrow()
    }

    @Test
    fun `the row a Gallery shows keeps the record's time after the scan on publish replaces it`() {
        FakeMediaProvider.scanOnPublishClearsDateTaken = true
        save(LogPhoto("dt-scan", store("dt-scan"), createdAtEpochMillis = TAKEN_AT))

        val row = FakeMediaProvider.inserted.single()
        assertEquals(TAKEN_AT, FakeMediaProvider.rowNow(row.uri).getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
    }

    @Test
    fun `the time is written after the row is published, not only before`() {
        save(LogPhoto("dt-order", store("dt-order"), createdAtEpochMillis = TAKEN_AT))

        val row = FakeMediaProvider.inserted.single()
        val writes = FakeMediaProvider.updated.filter { it.first == row.uri }.map { it.second }
        val publishedAt = writes.indexOfFirst { it.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0 }
        val lastTimeWrite = writes.indexOfLast { it.containsKey(MediaStore.MediaColumns.DATE_TAKEN) }
        assertEquals("the row is published once", 1, writes.count { it.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 0 })
        assertEquals("a write of the time follows the publish", true, lastTimeWrite >= publishedAt && lastTimeWrite >= 0)
        assertEquals(TAKEN_AT, writes[lastTimeWrite].getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
    }

    @Test
    fun `the time is still written at insert`() {
        save(LogPhoto("dt-insert", store("dt-insert"), createdAtEpochMillis = TAKEN_AT))

        assertEquals(TAKEN_AT, FakeMediaProvider.inserted.single().values.getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
    }

    @Test
    fun `a record with no time gets no time in the Gallery, at insert or after`() {
        save(LogPhoto("dt-none", store("dt-none"), createdAtEpochMillis = null))

        val row = FakeMediaProvider.inserted.single()
        assertFalse(row.values.containsKey(MediaStore.MediaColumns.DATE_TAKEN))
        assertEquals(
            "no write ever carries a made-up time",
            emptyList<Any>(),
            FakeMediaProvider.updated.filter { it.first == row.uri && it.second.containsKey(MediaStore.MediaColumns.DATE_TAKEN) },
        )
        assertNull(FakeMediaProvider.rowNow(row.uri).getAsLong(MediaStore.MediaColumns.DATE_TAKEN))
    }

    private companion object {
        const val TAKEN_AT = 1_800_000_000_000L
    }
}
