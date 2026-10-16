package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pin probe's stall on the S26 (RECORD -1079; dispatch 2026-10-16-01, "Fix the stall"): attempt 2, the direct open
 * of physical camera 5, was refused with CameraAccessException; after it nothing more was logged, neither attempt 3 nor
 * the hand-back, and the app kept camera 0 until it was force-stopped.
 *
 * What the code shows (READ, `PinProbe.kt` and `PlatformPinCamera.kt` at dev eec76f4e): the sequencer did go on to
 * attempt 3 after the refused attempt 2 (the loop catches nothing it has to; attempt 2's own line was logged), and the
 * hand-back sits in a `finally`. So the probe can only have stopped inside attempt 3 or the hand-back; and two of the
 * platform attempt's waits, the open and the session configure, had no bound, so a callback that never came would hold
 * the probe, and the camera, for ever. Which wait it was on the phone is not in the log (COULD NOT DETERMINE).
 *
 * The reproduction models that: attempt 2 refused as it was on the S26, then attempt 3 never finishing. A hang is a
 * day-long `delay`, not an endless suspend, so that on code without the fix the test still ends (virtual time) and
 * fails on its assertion rather than stalling the build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinProbeStallTest {


    /** The S26's attempt 2, as its log line reads (pin.jsonl, READ). */
    private fun refusedDirect() = PinAttemptResult(
        attempt = PinAttempt.PHYSICAL_DIRECT, openedId = "5", physicalStreamId = null, configured = null, sessionQuery = null,
        rawSize = "4080x3060", frames = emptyList(),
        error = "camera 5 was not opened: openCamera threw CameraAccessException reason 2: CAMERA_DISCONNECTED (2): " +
            "validateConnectLocked:2005: No camera device with ID \"5\" available",
        notes = listOf("camera 5 is not in getCameraIdList [0, 1, 2, 3]"),
    )

    private fun frames(attempt: PinAttempt) = PinAttemptResult(
        attempt = attempt, openedId = "0", physicalStreamId = "5", configured = true, sessionQuery = "supported", rawSize = "4080x3060",
        frames = (0 until 5).map { PinFrame(it, if (it == 0) "single" else "burst", it.toLong(), "5", listOf("5"), "5", "4080x3060", 2, null) },
        error = null, notes = emptyList(),
    )

    private class Camera(
        val attemptBehaviour: suspend Camera.(PinAttempt) -> PinAttemptResult,
        val handBackBehaviour: suspend Camera.() -> PinHandback = {
            platformOpen = false
            cameraxReleased = false
            PinHandback(platformClosed = true, cameraxBack = true, detail = null)
        },
    ) : PinCamera {
        val calls = mutableListOf<String>()
        var cameraxReleased = false
        var platformOpen = false

        override suspend fun survey(): PinSurvey = READY.also { calls += "survey" }

        override suspend fun borrow(): PinBorrow {
            calls += "borrow"
            cameraxReleased = true
            return PinBorrow.Borrowed(emptyList())
        }

        override suspend fun attempt(attempt: PinAttempt, survey: PinSurvey.Ready, burstFrames: Int): PinAttemptResult {
            calls += "attempt:${attempt.id}"
            return attemptBehaviour(attempt)
        }

        override suspend fun handBack(): PinHandback {
            calls += "handBack"
            return handBackBehaviour()
        }
    }

    private val lines = mutableListOf<String>()
    private fun ofKind(kind: String): List<JsonObject> =
        lines.map { Json.parseToJsonElement(it).jsonObject }.filter { it["kind"]!!.jsonPrimitive.content == kind }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    /** Far beyond any timeout the probe sets: a hang, in virtual time. */
    private suspend fun hang(): Nothing {
        delay(DAY_MILLIS)
        error("a hang that ended: the probe never cut it off")
    }

    private val allThree = listOf("survey", "borrow", "attempt:physical_stream", "attempt:physical_direct", "attempt:logical_control", "handBack")

    @Test
    fun `after a refused direct open, an attempt that never finishes is cut off at its timeout and the camera is handed back`() = runTest {
        val camera = Camera(attemptBehaviour = { attempt ->
            when (attempt) {
                PinAttempt.PHYSICAL_STREAM -> frames(attempt)
                PinAttempt.PHYSICAL_DIRECT -> refusedDirect()
                PinAttempt.LOGICAL_CONTROL -> openThenHang()
            }
        })
        val ok = withTimeoutOrNull(TEN_MINUTES) { PinProbe(camera, attemptTimeoutMillis = 45_000L) { lines += it }.runAll() }

        assertNotNull("runAll never returned: the probe stalled after the refused direct open, as on the S26 (RECORD -1079). Calls: ${camera.calls}", ok)
        assertEquals(allThree, camera.calls)
        assertEquals("the hung attempt was cut off at its 45 s timeout", 45_000L, currentTime)
        assertFalse("the camera is not left open by the probe", camera.platformOpen)
        assertFalse("CameraX has the camera back", camera.cameraxReleased)
        val control = ofKind("pin_attempt").single { it.text("attempt") == "logical_control" }
        assertEquals("timed_out", control.text("verdict"))
        assertTrue(control.text("error"), control.text("error").contains("45000 ms"))
        assertEquals("the refused open keeps the pin probe's own word for it", "failed", ofKind("pin_attempt").single { it.text("attempt") == "physical_direct" }.text("verdict"))
        assertEquals(1, ofKind("pin_handback").size)
        val end = ofKind("pin_end").single()
        assertEquals("timed_out", end["verdicts"]!!.jsonObject.text("logical_control"))
        assertTrue("a run whose camera came back is complete, its timed-out attempt a finding", ok!!)
    }

    @Test
    fun `a refused open that throws, then a hang, still runs every attempt and hands back`() = runTest {
        val camera = Camera(attemptBehaviour = { attempt ->
            when (attempt) {
                PinAttempt.PHYSICAL_STREAM -> frames(attempt)
                PinAttempt.PHYSICAL_DIRECT -> throw IllegalStateException("openCamera threw CameraAccessException reason 2: CAMERA_DISCONNECTED")
                PinAttempt.LOGICAL_CONTROL -> openThenHang()
            }
        })
        val ok = withTimeoutOrNull(TEN_MINUTES) { PinProbe(camera, attemptTimeoutMillis = 45_000L) { lines += it }.runAll() }

        assertNotNull("runAll never returned. Calls: ${camera.calls}", ok)
        assertEquals(allThree, camera.calls)
        assertEquals(1, ofKind("pin_handback").size)
        val verdicts = ofKind("pin_attempt").associate { it.text("attempt") to it.text("verdict") }
        assertEquals(mapOf("physical_stream" to "main_wide", "physical_direct" to "failed", "logical_control" to "timed_out"), verdicts)
    }

    @Test
    fun `a hand-back that never finishes is cut off at its own timeout and reported as not handed back`() = runTest {
        val camera = Camera(attemptBehaviour = { frames(it) }, handBackBehaviour = { hang() })
        val ok = withTimeoutOrNull(TEN_MINUTES) {
            PinProbe(camera, attemptTimeoutMillis = 45_000L, handBackTimeoutMillis = 30_000L) { lines += it }.runAll()
        }

        assertNotNull("runAll never returned: the hand-back held the probe", ok)
        assertFalse("a hand-back that did not finish is never success", ok!!)
        assertEquals(30_000L, currentTime)
        val handback = ofKind("pin_handback").single()
        assertEquals("false", handback.text("cameraxBack"))
        assertEquals("false", handback.text("platformClosed"))
        assertTrue(handback.text("detail"), handback.text("detail").contains("30000 ms"))
        assertEquals("false", ofKind("pin_end").single().text("completed"))
    }

    /** Attempt 3 as it may have gone on the phone: a device open, then a callback that never comes. */
    private suspend fun Camera.openThenHang(): Nothing {
        platformOpen = true
        hang()
    }

    private companion object {
        val LOGICAL = LensFacts("0", true, listOf(6.5f), listOf(9.792f, 7.344f), 2, "4080x3060", listOf("4080x3060"), listOf(0, 3, 11))
        val MAIN = LensFacts("5", false, listOf(6.5f), listOf(9.792f, 7.344f), 2, "4080x3060", listOf("4080x3060"), listOf(0, 3))
        val READY = PinSurvey.Ready(LOGICAL, listOf(MAIN), "5", "matches the logical camera")
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val TEN_MINUTES = 10 * 60 * 1_000L
    }
}
