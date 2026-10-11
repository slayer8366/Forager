package com.zynergylabs.forager.app.importgpx

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.MainActivity
import com.zynergylabs.forager.app.domain.GpxImportFailure
import com.zynergylabs.forager.app.domain.GpxImportFixtures
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import java.io.ByteArrayInputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

/**
 * Plan T16's Open with and Share (the owner's "Invisible doorway", RECORD -636), through real Intents:
 * the manifest's filters resolve them to [GpxImportActivity] and never to [MainActivity]; launched with
 * one, it saves the file through the real application's container and database, opens MainActivity in
 * Forager's own task with the outcome on the Intent, and finishes. With a recording running it changes
 * nothing about that recording and starts no service.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GpxImportActivityTest {

    private val app: ForagerApplication get() = ApplicationProvider.getApplicationContext()

    private val importComponent get() = ComponentName(app, GpxImportActivity::class.java)

    private fun resolves(intent: Intent): List<String> =
        app.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.name }.distinct()

    // ---- the manifest's filters ----------------------------------------------------------------

    @Test
    fun `Open with and Share for a GPX file reach the import Activity and never MainActivity`() {
        val gpxUri = Uri.parse("content://com.example.files/document/Morning%20Hike.gpx")
        val plainUri = Uri.parse("content://com.example.files/document/12345")
        val offered = listOf(
            "VIEW application/gpx+xml" to Intent(Intent.ACTION_VIEW).setDataAndType(plainUri, "application/gpx+xml"),
            "VIEW text/xml" to Intent(Intent.ACTION_VIEW).setDataAndType(plainUri, "text/xml"),
            "VIEW application/xml" to Intent(Intent.ACTION_VIEW).setDataAndType(plainUri, "application/xml"),
            "VIEW octet-stream with a .gpx path" to Intent(Intent.ACTION_VIEW).setDataAndType(gpxUri, "application/octet-stream"),
            "VIEW */* with a .GPX path" to Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("file:///sdcard/Download/RIDGE.GPX"), "*/*"),
            "SEND application/gpx+xml" to Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, gpxUri),
            "SEND text/xml" to Intent(Intent.ACTION_SEND).setType("text/xml").putExtra(Intent.EXTRA_STREAM, gpxUri),
        )
        for ((label, intent) in offered) {
            assertEquals(label, listOf(GpxImportActivity::class.java.name), resolves(intent))
        }
    }

    @Test
    fun `an octet-stream without a gpx path, and a Share of octet-stream, are not offered to Forager`() {
        val notOffered = listOf(
            Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.example.files/document/photo.jpg"), "application/octet-stream"),
            Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, Uri.parse("content://x/a.gpx")),
        )
        for (intent in notOffered) assertEquals(intent.toString(), emptyList<String>(), resolves(intent))
    }

    // ---- an Open with, end to end -----------------------------------------------------------------

    private fun offer(uri: Uri, xml: String) {
        shadowOf(app.contentResolver).registerInputStream(uri, ByteArrayInputStream(xml.toByteArray()))
    }

    /** Launches the import Activity with [intent] and waits (main looper and the IO save) until it has finished itself. */
    private fun launch(intent: Intent): ActivityController<GpxImportActivity> {
        val controller = Robolectric.buildActivity(GpxImportActivity::class.java, intent.setComponent(importComponent)).setup()
        val deadline = System.currentTimeMillis() + 10_000L
        while (!controller.get().isFinishing && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10L)
        }
        assertTrue("the import Activity finishes itself once the file is saved", controller.get().isFinishing)
        return controller
    }

    private fun startedMainActivity(): Intent {
        val started = shadowOf(app).nextStartedActivity
        assertNotNull("Forager is opened", started)
        assertEquals(MainActivity::class.java.name, started.component?.className)
        assertEquals(
            "in Forager's own task, handed to the MainActivity already there",
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            started.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        return started
    }

    @Test
    fun `Open with saves the file as a track and opens Forager on it`() {
        val uri = Uri.parse("content://com.example.files/document/Morning%20Hike.gpx")
        offer(uri, GpxImportFixtures.SHAPE_A)

        launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/gpx+xml"))

        val stored = runBlocking { app.container.trackRepository.getAll() }.getOrThrow()
        val track = stored.single()
        assertEquals("Morning Hike", track.name)
        assertEquals(3, track.points.size)
        assertNotNull(track.importedAtEpochMillis)
        val outcome = gpxImportOutcomeFrom(startedMainActivity())
        assertEquals(GpxImportOutcome.Imported(listOf(track.id)), outcome)
    }

    @Test
    fun `Share saves the file too`() {
        val uri = Uri.parse("content://com.example.mail/attachment/7")
        offer(uri, GpxImportFixtures.SHAPE_B)

        launch(Intent(Intent.ACTION_SEND).setType("application/gpx+xml").putExtra(Intent.EXTRA_STREAM, uri))

        val track = runBlocking { app.container.trackRepository.getAll() }.getOrThrow().single()
        assertEquals("Chanterelle ridge", track.name)
        assertEquals(GpxImportOutcome.Imported(listOf(track.id)), gpxImportOutcomeFrom(startedMainActivity()))
    }

    @Test
    fun `a file that is not GPX opens Forager with the reason and stores nothing`() {
        val uri = Uri.parse("content://com.example.files/document/notes.gpx")
        offer(uri, GpxImportFixtures.BROKEN)

        launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/octet-stream"))

        assertTrue(runBlocking { app.container.trackRepository.getAll() }.getOrThrow().isEmpty())
        assertEquals(GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE), gpxImportOutcomeFrom(startedMainActivity()))
    }

    @Test
    fun `with a recording running, Open with leaves the recording exactly as it was and starts no service`() {
        val container = app.container
        val start = System.currentTimeMillis() - 600_000L
        // The recording the service would be running: an open track with its points, watched by ReturnWatch.
        runBlocking {
            container.trackRepository.create(Track("running", null, start, null, emptyList())).getOrThrow()
            container.trackRepository.appendPoints("running", listOf(TrackPoint(45.0, -122.0, null, 5f, start), TrackPoint(45.001, -122.001, null, 5f, start + 5_000L))).getOrThrow()
        }
        container.returnWatch.begin("running", TrackRecordingMode.HIGH_ACCURACY)
        // And a track an earlier process left open, not watched: the first TrackRecordingViewModel built in
        // this process would end it (AbandonedTrackSweepOnce), so it staying open shows none was built.
        runBlocking { container.trackRepository.create(Track("left-open", null, start - 3_600_000L, null, emptyList())).getOrThrow() }
        val recordingBefore = runBlocking { container.trackRepository.getById("running") }.getOrThrow()
        val watchBefore = container.returnWatch.state.value
        val uri = Uri.parse("content://com.example.files/document/Morning%20Hike.gpx")
        offer(uri, GpxImportFixtures.SHAPE_A)

        launch(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/gpx+xml"))

        assertEquals("the running recording's row and points, unchanged", recordingBefore, runBlocking { container.trackRepository.getById("running") }.getOrThrow())
        assertEquals("the return watch, unchanged", watchBefore, container.returnWatch.state.value)
        assertNull("still open: no recording ViewModel was built", runBlocking { container.trackRepository.getById("left-open") }.getOrThrow()!!.endedAtEpochMillis)
        assertNull("nothing sent to the recording service", shadowOf(app).nextStartedService)
        startedMainActivity()
        assertNull("MainActivity is the only Activity started", shadowOf(app).nextStartedActivity)
        assertEquals("the import itself landed", 3, runBlocking { container.trackRepository.getAll() }.getOrThrow().size)
    }

    // ---- what MainActivity reads off the Intent ---------------------------------------------------

    @Test
    fun `the outcome survives the Intent both ways and is cleared once read`() {
        val imported = GpxImportOutcome.Imported(listOf("a", "b"))
        val failed = GpxImportOutcome.Failed(GpxImportFailure.ROUTE_ONLY)
        assertEquals(imported, gpxImportOutcomeFrom(mainActivityIntentAfterImport(app, imported)))
        assertEquals(failed, gpxImportOutcomeFrom(mainActivityIntentAfterImport(app, failed)))
        assertNull("an ordinary launch carries none", gpxImportOutcomeFrom(Intent(app, MainActivity::class.java)))

        val intent = mainActivityIntentAfterImport(app, imported)
        clearGpxImportOutcome(intent)
        assertNull(gpxImportOutcomeFrom(intent))
    }

    @Test
    fun `the messages are the owner's words`() {
        assertEquals("This file couldn't be read as GPX", gpxImportMessage(GpxImportOutcome.Failed(GpxImportFailure.UNREADABLE)))
        assertEquals("This file has a route but no track; routes aren't imported yet", gpxImportMessage(GpxImportOutcome.Failed(GpxImportFailure.ROUTE_ONLY)))
        assertEquals("This file is too big to import (over 10 MB).", gpxImportMessage(GpxImportOutcome.Failed(GpxImportFailure.TOO_BIG)))
        assertEquals("Imported 3 tracks", gpxImportMessage(GpxImportOutcome.Imported(listOf("a", "b", "c"))))
        assertNull("one track simply opens", gpxImportMessage(GpxImportOutcome.Imported(listOf("a"))))
    }
}
