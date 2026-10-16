package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 3 borrows the dialog's camera for its three pinning attempts and must hand it back on every path (dispatch
 * 2026-10-15-12: "Hand back as before"; abort condition: "The camera is left dead"). Driven through
 * [PinProbe.runAll] over a fake that models whether CameraX has let the camera go and whether a platform device is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinProbeHandbackTest {

    private val logical = LensFacts("0", true, listOf(6.4f), listOf(9.6f, 7.2f), 1, "4000x3000", listOf("4000x3000"), listOf(0, 3, 11))
    private val main = LensFacts("5", false, listOf(6.4f), listOf(9.6f, 7.2f), 1, "4000x3000", listOf("4000x3000"), listOf(0, 3))
    private val ready = PinSurvey.Ready(logical, listOf(main), "5", "matches the logical camera")

    private class FakePinCamera(
        val surveyOutcome: PinSurvey,
        val borrowBehaviour: (FakePinCamera) -> PinBorrow = { fake ->
            fake.cameraxReleased = true
            PinBorrow.Borrowed(emptyList())
        },
        val attemptBehaviour: suspend (FakePinCamera, PinAttempt) -> PinAttemptResult = { _, attempt -> frames(attempt, "5") },
        val handBackBehaviour: (FakePinCamera) -> PinHandback = { fake ->
            fake.platformOpen = false
            fake.cameraxReleased = false
            PinHandback(platformClosed = true, cameraxBack = true, detail = null)
        },
    ) : PinCamera {
        val calls = mutableListOf<String>()
        var cameraxReleased = false
        var platformOpen = false

        override suspend fun survey(): PinSurvey {
            calls += "survey"
            return surveyOutcome
        }

        override suspend fun borrow(): PinBorrow {
            calls += "borrow"
            return borrowBehaviour(this)
        }

        override suspend fun attempt(attempt: PinAttempt, survey: PinSurvey.Ready, burstFrames: Int): PinAttemptResult {
            calls += "attempt:${attempt.id}"
            return attemptBehaviour(this, attempt)
        }

        override suspend fun handBack(): PinHandback {
            calls += "handBack"
            return handBackBehaviour(this)
        }

        fun liveForTheDialog() = !cameraxReleased && !platformOpen

        companion object {
            fun frames(attempt: PinAttempt, id: String) = PinAttemptResult(
                attempt = attempt, openedId = "0", physicalStreamId = null, configured = true, sessionQuery = "supported",
                rawSize = "4000x3000",
                frames = (0 until 5).map { PinFrame(it, if (it == 0) "single" else "burst", it.toLong(), id, emptyList(), id, "4000x3000", 1, null) },
                error = null, notes = emptyList(),
            )
        }
    }

    private val lines = mutableListOf<String>()
    private fun parsed(): List<JsonObject> = lines.map { Json.parseToJsonElement(it).jsonObject }
    private fun ofKind(kind: String) = parsed().filter { it["kind"]!!.jsonPrimitive.content == kind }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private fun assertHandedBackOnce(camera: FakePinCamera) {
        assertEquals("handBack exactly once: ${camera.calls}", 1, camera.calls.count { it == "handBack" })
        assertTrue("the camera is live for the dialog: ${camera.calls}", camera.liveForTheDialog())
        assertEquals(1, ofKind("pin_handback").size)
    }

    @Test
    fun `a clean run surveys, borrows, makes the three attempts in the dispatch's order and hands back`() = runTest {
        val camera = FakePinCamera(ready)
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertTrue(ok)
        assertEquals(
            listOf("survey", "borrow", "attempt:physical_stream", "attempt:physical_direct", "attempt:logical_control", "handBack"),
            camera.calls,
        )
        assertHandedBackOnce(camera)
        assertEquals(listOf("physical_stream", "physical_direct", "logical_control"), ofKind("pin_attempt").map { it.text("attempt") })
        assertEquals(15, ofKind("pin_frame").size)
        assertEquals("5", ofKind("choice").single().text("mainWideId"))
        assertEquals(2, ofKind("lens").size)
        val end = ofKind("pin_end").single()
        assertEquals("true", end.text("completed"))
        assertEquals("main_wide", end["verdicts"]!!.jsonObject.text("logical_control"))
    }

    @Test
    fun `a borrow that fails after CameraX let go still hands back, and no attempt is made`() = runTest {
        val camera = FakePinCamera(ready, borrowBehaviour = { fake ->
            fake.cameraxReleased = true
            PinBorrow.Failed("CameraX's camera did not read CLOSED", emptyList())
        })
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertFalse("the borrow failed, so the run is not complete", ok)
        assertEquals(listOf("survey", "borrow", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("false", ofKind("pin_end").single().text("completed"))
    }

    @Test
    fun `an attempt that throws with a device open is logged failed, the next goes on, and the camera is handed back`() = runTest {
        val camera = FakePinCamera(ready, attemptBehaviour = { fake, attempt ->
            if (attempt == PinAttempt.PHYSICAL_DIRECT) {
                fake.platformOpen = true
                throw IllegalStateException("openCamera refused")
            }
            FakePinCamera.frames(attempt, "5")
        })
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertTrue("a failed attempt is a finding, and the camera came back", ok)
        assertEquals(
            listOf("survey", "borrow", "attempt:physical_stream", "attempt:physical_direct", "attempt:logical_control", "handBack"),
            camera.calls,
        )
        assertHandedBackOnce(camera)
        val failed = ofKind("pin_attempt").single { it.text("attempt") == "physical_direct" }
        assertEquals("failed", failed.text("verdict"))
        assertTrue(failed.text("error"), failed.text("error").contains("openCamera refused"))
    }

    @Test
    fun `a run cancelled in an attempt still hands the camera back`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val camera = FakePinCamera(ready, attemptBehaviour = { fake, attempt ->
            fake.platformOpen = true
            gate.await()
            FakePinCamera.frames(attempt, "5")
        })
        val job = launch { PinProbe(camera) { lines += it }.runAll() }
        runCurrent()
        job.cancel()
        job.join()

        assertEquals(listOf("survey", "borrow", "attempt:physical_stream", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("false", ofKind("pin_end").single().text("completed"))
    }

    @Test
    fun `a hand-back that throws is reported as not handed back, never as success`() = runTest {
        val camera = FakePinCamera(ready, handBackBehaviour = { throw IllegalStateException("unbind threw") })
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertFalse(ok)
        val handback = ofKind("pin_handback").single()
        assertEquals("false", handback.text("cameraxBack"))
        assertTrue(handback.text("detail"), handback.text("detail").contains("unbind threw"))
        assertEquals("false", ofKind("pin_end").single().text("completed"))
    }

    @Test
    fun `with no main wide found, only the logical control runs, the other two are logged skipped, and the camera comes back`() = runTest {
        val camera = FakePinCamera(PinSurvey.Unsupported("no physical camera matches the logical camera", logical, listOf(main)))
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertTrue(ok)
        assertEquals(listOf("survey", "borrow", "attempt:logical_control", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        val attempts = ofKind("pin_attempt").associate { it.text("attempt") to it.text("verdict") }
        assertEquals("skipped", attempts["physical_stream"])
        assertEquals("skipped", attempts["physical_direct"])
    }

    @Test
    fun `a survey that throws borrows nothing and so has nothing to hand back`() = runTest {
        val camera = object : PinCamera {
            val calls = mutableListOf<String>()
            override suspend fun survey(): PinSurvey = throw IllegalStateException("characteristics unreadable")
            override suspend fun borrow(): PinBorrow = PinBorrow.Borrowed(emptyList()).also { calls += "borrow" }
            override suspend fun attempt(attempt: PinAttempt, survey: PinSurvey.Ready, burstFrames: Int) = error("not reached")
            override suspend fun handBack(): PinHandback = PinHandback(true, true, null).also { calls += "handBack" }
        }
        val ok = PinProbe(camera) { lines += it }.runAll()

        assertFalse(ok)
        assertEquals(emptyList<String>(), camera.calls)
        val end = ofKind("pin_end").single()
        assertEquals("false", end.text("completed"))
        assertTrue(end.text("reason"), end.text("reason").contains("characteristics unreadable"))
    }

    @Test
    fun `the verdict names where the frames came from`() {
        val five = FakePinCamera.frames(PinAttempt.LOGICAL_CONTROL, "5")
        assertEquals("main_wide", PinProbe.verdict(five, "5"))
        assertEquals("other_sensor", PinProbe.verdict(FakePinCamera.frames(PinAttempt.LOGICAL_CONTROL, "2"), "5"))
        val mixed = five.copy(frames = five.frames.mapIndexed { i, f -> if (i == 3) f.copy(attributedTo = "6") else f })
        assertEquals("other_sensor", PinProbe.verdict(mixed, "5"))
        val unattributed = five.copy(frames = five.frames.map { it.copy(attributedTo = null) })
        assertEquals("unattributed", PinProbe.verdict(unattributed, "5"))
        assertEquals("no_frames", PinProbe.verdict(five.copy(frames = emptyList()), "5"))
        assertEquals("failed", PinProbe.verdict(five.copy(frames = emptyList(), error = "onConfigureFailed"), "5"))
        val arrivedButErrored = five.copy(frames = five.frames.map { it.copy(error = "the RAW image did not arrive") })
        assertEquals("no_frames", PinProbe.verdict(arrivedButErrored, "5"))
    }
}
