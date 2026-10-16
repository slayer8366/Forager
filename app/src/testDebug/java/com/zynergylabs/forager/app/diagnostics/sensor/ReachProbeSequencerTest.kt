package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
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
 * The reach probe's attempt sequencer (dispatch 2026-10-16-01, Tests: "every attempt runs after any failure, hand-back
 * always happens, the timeout fires"), driven through [ReachProbe.runAll] over a fake that models whether CameraX has
 * let the camera go and whether a platform device is open. Hangs are day-long `delay`s, so a missing timeout fails an
 * assertion in virtual time instead of stalling the build.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReachProbeSequencerTest {

    private class Camera(
        val surveyBehaviour: suspend Camera.() -> ReachSurvey = { ReachFixtures.s26 },
        val borrowBehaviour: suspend Camera.() -> PinBorrow = {
            cameraxReleased = true
            PinBorrow.Borrowed(emptyList())
        },
        val attemptBehaviour: suspend Camera.(ReachAttempt, Int) -> ReachResult = { attempt, burst -> ReachFixtures.reached(attempt, burst) },
        val handBackBehaviour: suspend Camera.() -> PinHandback = {
            platformOpen = false
            cameraxReleased = false
            PinHandback(platformClosed = true, cameraxBack = true, detail = null)
        },
    ) : ReachCamera {
        val calls = mutableListOf<String>()
        val startedAt = mutableMapOf<String, Long>()
        var cameraxReleased = false
        var platformOpen = false
        var clock: () -> Long = { 0L }

        override suspend fun survey(): ReachSurvey {
            calls += "survey"
            return surveyBehaviour()
        }

        override suspend fun borrow(): PinBorrow {
            calls += "borrow"
            return borrowBehaviour()
        }

        override suspend fun attempt(attempt: ReachAttempt, burstFrames: Int): ReachResult {
            calls += attempt.label
            startedAt[attempt.label] = clock()
            return attemptBehaviour(attempt, burstFrames)
        }

        override suspend fun handBack(): PinHandback {
            calls += "handBack"
            return handBackBehaviour()
        }

        fun liveForTheDialog() = !cameraxReleased && !platformOpen
    }

    private val lines = mutableListOf<String>()
    private fun parsed(): List<JsonObject> = lines.map { Json.parseToJsonElement(it).jsonObject }
    private fun ofKind(kind: String) = parsed().filter { it["kind"]!!.jsonPrimitive.content == kind }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private suspend fun hang(): Nothing {
        delay(DAY_MILLIS)
        error("a hang that ended: the probe never cut it off")
    }

    /** The attempts the S26 plan makes (its skips are logged, not made). */
    private val made = listOf(
        "direct_0", "through_logical_0_2", "direct_2", "through_logical_0_5", "through_logical_0_6", "through_logical_0_7",
        "pair_0_2_5", "pair_0_2_7", "pair_0_5_7",
    )

    private fun assertHandedBackOnce(camera: Camera) {
        assertEquals("handBack exactly once: ${camera.calls}", 1, camera.calls.count { it == "handBack" })
        assertTrue("the camera is live for the dialog: ${camera.calls}", camera.liveForTheDialog())
        assertEquals(1, ofKind("reach_handback").size)
    }

    private suspend fun TestScope.probe(camera: Camera, attemptTimeout: Long = 45_000L, handBackTimeout: Long = 30_000L): Boolean? {
        camera.clock = { currentTime }
        return withTimeoutOrNull(TEN_MINUTES) {
            ReachProbe(camera, attemptTimeoutMillis = attemptTimeout, handBackTimeoutMillis = handBackTimeout) { lines += it }.runAll()
        }
    }

    @Test
    fun `a clean run surveys, borrows, makes every planned attempt in order, hands back and logs the table`() = runTest {
        val camera = Camera()
        val ok = probe(camera)

        assertEquals(true, ok)
        assertEquals(listOf("survey", "borrow") + made + "handBack", camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("one attempt line per planned step, made or not", ReachFixtures.s26Labels, ofKind("reach_attempt").map { it.text("label") })
        assertEquals(7, ofKind("reach_camera").size)
        assertEquals("one frame line per frame: 9 per single-camera attempt, 2 per pair", 6 * 9 + 3 * 2, ofKind("reach_frame").size)
        assertEquals(1, ofKind("reach_table").size)
        val end = ofKind("reach_end").single()
        assertEquals("true", end.text("completed"))
        assertEquals("reachable_raw", end["verdicts"]!!.jsonObject.text("through_logical_0_5"))
        assertEquals("not_attempted", end["verdicts"]!!.jsonObject.text("direct_5"))
    }

    @Test
    fun `every attempt runs after a throw, a refusal and a hang, and the camera is handed back once`() = runTest {
        val camera = Camera(attemptBehaviour = { attempt, burst ->
            when (attempt.label) {
                "direct_0" -> {
                    platformOpen = true
                    throw IllegalStateException("createCaptureSession threw")
                }
                "through_logical_0_2" -> ReachFixtures.refused(attempt, "0")
                "direct_2" -> {
                    platformOpen = true
                    hang()
                }
                else -> ReachFixtures.reached(attempt, burst)
            }
        })
        val ok = probe(camera)

        assertNotNull("runAll never returned: an attempt held the probe. Calls: ${camera.calls}", ok)
        assertEquals(listOf("survey", "borrow") + made + "handBack", camera.calls)
        assertHandedBackOnce(camera)
        val verdicts = ofKind("reach_attempt").associate { it.text("label") to it.text("verdict") }
        assertEquals("failed", verdicts["direct_0"])
        assertEquals("refused", verdicts["through_logical_0_2"])
        assertEquals("timed_out", verdicts["direct_2"])
        assertEquals("reachable_raw", verdicts["through_logical_0_5"])
        val thrown = ofKind("reach_attempt").single { it.text("label") == "direct_0" }
        assertTrue(thrown.text("error"), thrown.text("error").contains("createCaptureSession threw"))
        assertTrue("every attempt was made and the camera came back", ok!!)
    }

    @Test
    fun `the timeout fires at the probe's own setting, and the next attempt starts then`() = runTest {
        val camera = Camera(attemptBehaviour = { attempt, burst -> if (attempt.label == "direct_0") hang() else ReachFixtures.reached(attempt, burst) })
        val ok = probe(camera, attemptTimeout = 1_234L)

        assertNotNull("runAll never returned", ok)
        assertEquals("the hung attempt was cut off at 1,234 ms", 1_234L, camera.startedAt["through_logical_0_2"])
        val cut = ofKind("reach_attempt").single { it.text("label") == "direct_0" }
        assertEquals("timed_out", cut.text("verdict"))
        assertTrue(cut.text("error"), cut.text("error").contains("1234 ms"))
        assertHandedBackOnce(camera)
    }

    @Test
    fun `a borrow that fails after CameraX let go still hands back, and no attempt is made`() = runTest {
        val camera = Camera(borrowBehaviour = {
            cameraxReleased = true
            PinBorrow.Failed("CameraX's camera did not read CLOSED", emptyList())
        })
        val ok = probe(camera)

        assertEquals(false, ok)
        assertEquals(listOf("survey", "borrow", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("false", ofKind("reach_end").single().text("completed"))
    }

    @Test
    fun `a run cancelled in an attempt still hands the camera back`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val camera = Camera(attemptBehaviour = { attempt, burst ->
            platformOpen = true
            gate.await()
            ReachFixtures.reached(attempt, burst)
        })
        val job = launch { ReachProbe(camera) { lines += it }.runAll() }
        runCurrent()
        job.cancel()
        job.join()

        assertEquals(listOf("survey", "borrow", "direct_0", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("false", ofKind("reach_end").single().text("completed"))
    }

    @Test
    fun `a hand-back that throws is reported as not handed back, never as success`() = runTest {
        val camera = Camera(handBackBehaviour = { throw IllegalStateException("unbind threw") })
        val ok = probe(camera)

        assertEquals(false, ok)
        val handback = ofKind("reach_handback").single()
        assertEquals("false", handback.text("cameraxBack"))
        assertTrue(handback.text("detail"), handback.text("detail").contains("unbind threw"))
        assertEquals("false", ofKind("reach_end").single().text("completed"))
    }

    @Test
    fun `a hand-back that never finishes is cut off at its own timeout and reported as not handed back`() = runTest {
        val camera = Camera(handBackBehaviour = { hang() })
        val ok = probe(camera, handBackTimeout = 30_000L)

        assertNotNull("runAll never returned: the hand-back held the probe", ok)
        assertFalse(ok!!)
        assertEquals(30_000L, currentTime)
        val handback = ofKind("reach_handback").single()
        assertEquals("false", handback.text("cameraxBack"))
        assertTrue(handback.text("detail"), handback.text("detail").contains("30000 ms"))
    }

    @Test
    fun `a survey that throws borrows nothing and so has nothing to hand back`() = runTest {
        val camera = Camera(surveyBehaviour = { throw IllegalStateException("characteristics unreadable") })
        val ok = probe(camera)

        assertEquals(false, ok)
        assertEquals(listOf("survey"), camera.calls)
        val end = ofKind("reach_end").single()
        assertEquals("false", end.text("completed"))
        assertTrue(end.text("reason"), end.text("reason").contains("characteristics unreadable"))
    }

    @Test
    fun `a phone with no back camera borrows nothing, says so, and has nothing to hand back`() = runTest {
        val frontOnly = ReachFixtures.s26.copy(cameras = ReachFixtures.s26.cameras.filter { it.facing == "FRONT" }, cameraIdList = listOf("1", "3"))
        val camera = Camera(surveyBehaviour = { frontOnly })
        val ok = probe(camera)

        assertEquals(false, ok)
        assertEquals(listOf("survey"), camera.calls)
        assertEquals(2, ofKind("reach_camera").size)
        val end = ofKind("reach_end").single()
        assertEquals("false", end.text("completed"))
        assertTrue(end.text("reason"), end.text("reason").contains("no back camera"))
    }

    private companion object {
        const val DAY_MILLIS = 24 * 60 * 60 * 1_000L
        const val TEN_MINUTES = 10 * 60 * 1_000L
    }
}
