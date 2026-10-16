package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watch and the zoom stepper on the dialog's own camera (dispatch 2026-10-15-12: "A fake-camera test that the zoom
 * returns to 1.0, the watch stops, and the camera is handed back on every path, including failures"; abort condition:
 * "The camera is left dead or zoomed").
 *
 * Driven through the real entry points, [SensorWatch.start]/[SensorWatch.stop] and [ZoomRunner.set]/[ZoomRunner.steps],
 * over a fake that models the two things these parts can leave behind: the dialog camera's zoom, and a results callback
 * still registered on it. The fake feeds a capture result whenever the zoom is set, naming the physical camera a
 * logical camera would plausibly switch to at that ratio, so the step lines' settled readings can be checked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SensorWatchHandbackTest {

    private class FakeDialogCamera(
        val attachOutcome: DialogAttach = DialogAttach.Attached("0", listOf("Preview", "ImageCapture JPEG"), emptyList()),
        val zoomAccess: ZoomAccess = ZoomAccess.Available(ZoomRange(0.6f, 10f)),
        val startThrows: Boolean = false,
        val setZoomBehaviour: (Float) -> ZoomOutcome = { ZoomOutcome.Applied },
    ) : DialogCamera {
        var zoom = 1f
        var resultsActive = false
        val zoomCalls = mutableListOf<Float>()
        var arrival = 0L
        private var onReading: ((FrameReading) -> Unit)? = null

        override suspend fun attach(): DialogAttach = attachOutcome

        override fun startResults(onReading: (FrameReading) -> Unit) {
            if (startThrows) throw IllegalStateException("the session callback could not be added")
            this.onReading = onReading
            resultsActive = true
        }

        override fun stopResults(): List<String> {
            resultsActive = false
            onReading = null
            return emptyList()
        }

        override suspend fun findZoom(): ZoomAccess = zoomAccess

        override suspend fun setZoom(ratio: Float): ZoomOutcome {
            zoomCalls += ratio
            val outcome = setZoomBehaviour(ratio)
            if (outcome is ZoomOutcome.Applied) {
                zoom = ratio
                feed(idFor(ratio), ratio)
            }
            return outcome
        }

        fun feed(id: String?, zoomRatio: Float = zoom) {
            arrival += 300_000_000L
            onReading?.invoke(
                FrameReading(arrival, arrival, false, 1, id, zoomRatio, 2f, 2, 10_000_000L, 100, 1.8f, null, null),
            )
        }

        fun idFor(ratio: Float) = when {
            ratio < 1f -> "2"
            ratio >= 3f -> "6"
            else -> "5"
        }

        /** Nothing left behind: the zoom back at 1.0, and no results callback registered on the dialog's camera. */
        fun leftAsFound() = zoom == 1f && !resultsActive
    }

    private val lines = mutableListOf<String>()
    private var now = 0L

    private fun parsed(): List<JsonObject> = lines.map { Json.parseToJsonElement(it).jsonObject }
    private fun ofKind(kind: String) = parsed().filter { it["kind"]!!.jsonPrimitive.content == kind }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private fun watchOver(camera: FakeDialogCamera) = SensorWatch(camera, { lines += it }, { now })
    private fun runnerOver(camera: FakeDialogCamera, watch: SensorWatch) = ZoomRunner(camera, watch, { lines += it }, { now })

    // ── Part 1, the watch ───────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the watch starts, logs frames and switches, and on stop unregisters and logs its summary`() = runTest {
        val camera = FakeDialogCamera()
        val watch = watchOver(camera)

        assertTrue(watch.start())
        assertTrue(watch.running)
        assertTrue(camera.resultsActive)
        camera.feed("5")
        camera.feed("2")
        watch.stop("turned off by broadcast")

        assertFalse(watch.running)
        assertFalse("the results callback is unregistered", camera.resultsActive)
        assertEquals(listOf("on", "off"), ofKind("watch").map { it.text("state") })
        assertEquals(2, ofKind("frame").size)
        assertEquals("5", ofKind("switch").single().text("from"))
        val summary = ofKind("summary").single()
        assertEquals("turned off by broadcast", summary.text("reason"))
        assertEquals("2", summary.text("framesSeen"))
    }

    @Test
    fun `a watch that cannot find the dialog's camera says so and registers nothing`() = runTest {
        val camera = FakeDialogCamera(attachOutcome = DialogAttach.Unavailable("no camera dialog is open"))
        val watch = watchOver(camera)

        assertFalse(watch.start())
        assertFalse(watch.running)
        assertFalse(camera.resultsActive)
        val line = ofKind("watch").single()
        assertEquals("unavailable", line.text("state"))
        assertEquals("no camera dialog is open", line.text("reason"))
    }

    @Test
    fun `a watch whose callback cannot be added is reported unavailable, not on`() = runTest {
        val camera = FakeDialogCamera(startThrows = true)
        val watch = watchOver(camera)

        assertFalse(watch.start())
        assertFalse(watch.running)
        assertFalse(camera.resultsActive)
        val line = ofKind("watch").single()
        assertEquals("unavailable", line.text("state"))
        assertTrue(line.text("reason"), line.text("reason").contains("could not be added"))
    }

    @Test
    fun `turning on a watch that is on, or off one that is off, says so and changes nothing`() = runTest {
        val camera = FakeDialogCamera()
        val watch = watchOver(camera)
        watch.stop("turned off by broadcast")
        assertTrue(watch.start())
        assertTrue(watch.start())
        watch.stop("turned off by broadcast")

        assertEquals(listOf("already_off", "on", "already_on", "off"), ofKind("watch").map { it.text("state") })
        assertEquals("one summary, for the one watch that ran", 1, ofKind("summary").size)
        assertFalse(camera.resultsActive)
    }

    // ── Part 2, the zoom stepper ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the step list sets each in-range step, holds it, logs the settled sensor, skips the rest and returns to 1_0`() = runTest {
        val camera = FakeDialogCamera()
        val watch = watchOver(camera)
        val ok = runnerOver(camera, watch).steps(listOf(0.6f, 1f, 3f, 20f), holdMillis = 3_000L)

        assertTrue(ok)
        assertEquals("each in-range step, then the return", listOf(0.6f, 1f, 3f, 1f), camera.zoomCalls)
        assertTrue("zoom 1.0 and the watch it started is off: zoom ${camera.zoom}", camera.leftAsFound())
        val steps = ofKind("zoom").filter { it.text("mode") == "step" }
        assertEquals(listOf("applied", "applied", "applied", "skipped"), steps.map { it.text("outcome") })
        assertEquals(listOf("2", "5", "6"), steps.take(3).map { it.text("settledActivePhysicalId") })
        assertEquals(JsonNull, steps[3]["settledActivePhysicalId"])
        val restore = ofKind("zoom").single { it.text("mode") == "restore" }
        assertEquals("applied", restore.text("outcome"))
        assertEquals("true", ofKind("zoom_end").single().text("returnedToOne"))
        assertEquals("the stepper turned the watch on for itself and off after", listOf("on", "off"), ofKind("watch").map { it.text("state") })
    }

    @Test
    fun `a step the camera refuses is logged failed, the rest go on, and the zoom still returns to 1_0`() = runTest {
        val camera = FakeDialogCamera(setZoomBehaviour = { if (it == 2f) ZoomOutcome.Failed("the zoom was superseded") else ZoomOutcome.Applied })
        val ok = runnerOver(camera, watchOver(camera)).steps(listOf(1.5f, 2f, 5f), holdMillis = 1_000L)

        assertTrue("a refused step is a logged outcome, not a failed run", ok)
        assertEquals(listOf(1.5f, 2f, 5f, 1f), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        val failed = ofKind("zoom").single { it.text("outcome") == "failed" }
        assertEquals("the zoom was superseded", failed.text("reason"))
    }

    @Test
    fun `a step that throws still returns the zoom to 1_0 and turns the watch off`() = runTest {
        val camera = FakeDialogCamera(setZoomBehaviour = { if (it == 3f) throw IllegalStateException("camera closed") else ZoomOutcome.Applied })
        val ok = runnerOver(camera, watchOver(camera)).steps(listOf(1.5f, 3f, 5f), holdMillis = 1_000L)

        assertFalse(ok)
        assertEquals("the run stops at the throw, then returns", listOf(1.5f, 3f, 1f), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        val end = ofKind("zoom_end").single()
        assertEquals("true", end.text("returnedToOne"))
        assertTrue(end.text("reason"), end.text("reason").contains("camera closed"))
    }

    @Test
    fun `a step run cancelled mid-hold still returns the zoom to 1_0 and turns the watch off`() = runTest {
        val camera = FakeDialogCamera()
        val runner = runnerOver(camera, watchOver(camera))
        val job = launch { runner.steps(listOf(2f, 5f), holdMillis = 3_000L) }
        runCurrent()
        advanceTimeBy(1_000L)
        assertEquals(2f, camera.zoom)
        job.cancel()
        job.join()

        assertEquals(listOf(2f, 1f), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        val end = ofKind("zoom_end").single()
        assertEquals("true", end.text("returnedToOne"))
        assertTrue(end.text("reason"), end.text("reason").contains("cancelled"))
    }

    @Test
    fun `a watch the user turned on is left on by the stepper`() = runTest {
        val camera = FakeDialogCamera()
        val watch = watchOver(camera)
        assertTrue(watch.start())
        watch.heldByUser = true
        runnerOver(camera, watch).steps(listOf(2f), holdMillis = 500L)

        assertEquals(1f, camera.zoom)
        assertTrue("the user's watch is still on", camera.resultsActive && watch.running)
    }

    @Test
    fun `a single set is clamped, logged as clamped, held, and returned to 1_0`() = runTest {
        val camera = FakeDialogCamera()
        val ok = runnerOver(camera, watchOver(camera)).set(100f, holdMillis = 15_000L)

        assertTrue(ok)
        assertEquals(listOf(10f, 1f), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        val set = ofKind("zoom").single { it.text("mode") == "set" }
        assertEquals("true", set.text("clamped"))
        assertEquals("100.0", set.text("requested"))
        assertEquals("10.0", set.text("applied"))
        assertEquals("6", set.text("settledActivePhysicalId"))
    }

    @Test
    fun `a single set held is still in effect during its hold`() = runTest {
        val camera = FakeDialogCamera()
        val runner = runnerOver(camera, watchOver(camera))
        val job = launch { runner.set(0.6f, holdMillis = 15_000L) }
        runCurrent()
        advanceTimeBy(10_000L)
        assertEquals(0.6f, camera.zoom)
        job.join()
        assertTrue(camera.leftAsFound())
    }

    @Test
    fun `a ratio that is not a number is rejected, nothing is applied, and the zoom is still set to 1_0`() = runTest {
        val camera = FakeDialogCamera()
        val ok = runnerOver(camera, watchOver(camera)).set(Float.NaN, holdMillis = 15_000L)

        assertFalse(ok)
        assertEquals(listOf(1f), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        assertEquals("rejected", ofKind("zoom").single { it.text("mode") == "set" }.text("outcome"))
    }

    @Test
    fun `no dialog camera to zoom means no zoom call at all and no watch left on`() = runTest {
        val camera = FakeDialogCamera(zoomAccess = ZoomAccess.Unavailable("no active camera with a preview"))
        val ok = runnerOver(camera, watchOver(camera)).steps(holdMillis = 500L)

        assertFalse(ok)
        assertEquals(emptyList<Float>(), camera.zoomCalls)
        assertTrue(camera.leftAsFound())
        val end = ofKind("zoom_end").single()
        assertEquals("false", end.text("returnedToOne"))
        assertEquals("no active camera with a preview", end.text("reason"))
    }

    @Test
    fun `a return to 1_0 the camera refuses is reported as not returned, never as success`() = runTest {
        val camera = FakeDialogCamera(setZoomBehaviour = { if (it == 1f) ZoomOutcome.Failed("the camera is closed") else ZoomOutcome.Applied })
        val ok = runnerOver(camera, watchOver(camera)).set(2f, holdMillis = 500L)

        assertFalse(ok)
        val end = ofKind("zoom_end").single()
        assertEquals("false", end.text("returnedToOne"))
        assertTrue(end.text("reason"), end.text("reason").contains("the camera is closed"))
        assertFalse("the watch is still turned off", camera.resultsActive)
    }
}
