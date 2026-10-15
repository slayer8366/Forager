package com.zynergylabs.forager.app.diagnostics.burst

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe borrows the camera dialog's camera and must give it back exactly as it was, on every path (dispatch
 * 2026-10-15-06: "The probe must never leave the camera dead or in a different format", and the lock "is released in a
 * `finally` on every path"). Driven through the probe's real entry point, [BurstProbe.runAll], over a fake camera that
 * models the two things that matter: whether the dialog's own binding is the active one (the probe never touches it, so
 * when it is active again the user's chosen format is back), and whether the AE/AWB lock is still set.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BurstProbeRestoreTest {

    private class FakeCamera(
        val bindOutcome: (ProbeOutput) -> BindOutcome = { BindOutcome.Bound() },
        val lockOutcome: () -> LockOutcome = { LockOutcome.Reached(waitMs = 50.0, aeState = 3, awbState = 3) },
        val captureBehaviour: suspend (call: Int, output: ProbeOutput, count: Int) -> BurstCapture = { _, _, count -> okCapture(count) },
        val unlockBehaviour: () -> LockRelease = { LockRelease(cleared = true) },
        val releaseBehaviour: () -> Handback = { Handback(probeUnbound = true) },
    ) : BurstProbeCamera {
        val calls = mutableListOf<String>()
        var userBindingActive = true
        var lockSet = false
        var probeBound = false
        private var captureCalls = 0

        override fun memory() = MemoryReading(maxMemoryBytes = 512L shl 20, usedBytes = 64L shl 20)

        override suspend fun bind(output: ProbeOutput): BindOutcome {
            calls += "bind:${output.label}"
            val outcome = bindOutcome(output)
            if (outcome is BindOutcome.Bound) {
                probeBound = true
                userBindingActive = false // CameraX suspends the dialog's binding while the probe's is active
            }
            return outcome
        }

        override suspend fun lock(timeoutMillis: Long): LockOutcome {
            calls += "lock"
            lockSet = true // the request options are set before the wait, whatever the wait finds
            return lockOutcome()
        }

        override suspend fun unlock(): LockRelease {
            calls += "unlock"
            val release = unlockBehaviour()
            if (release.cleared) lockSet = false
            return release
        }

        override suspend fun capture(output: ProbeOutput, count: Int, frameTimeoutMillis: Long): BurstCapture {
            calls += "capture"
            return captureBehaviour(captureCalls++, output, count)
        }

        override suspend fun release(): Handback {
            calls += "release"
            val handback = releaseBehaviour()
            if (handback.probeUnbound) {
                probeBound = false
                userBindingActive = true // CameraX resumes the dialog's binding once the probe's has no use cases
            }
            return handback
        }
    }

    private val lines = mutableListOf<String>()
    private fun parsed(): List<JsonObject> = lines.map { Json.parseToJsonElement(it).jsonObject }
    private fun ofKind(kind: String) = parsed().filter { it["kind"]?.jsonPrimitive?.content == kind }

    private fun assertRestored(camera: FakeCamera) {
        assertTrue("the dialog's own binding must be active again", camera.userBindingActive)
        assertFalse("the probe's use cases must be unbound", camera.probeBound)
        assertFalse("the AE/AWB lock must be released", camera.lockSet)
    }

    @Test
    fun `four runs in the dispatch's order, each handing the camera back, the locked ones releasing their lock`() = runTest {
        val camera = FakeCamera()
        val finished = BurstProbe(camera, session = 1, log = { lines += it }).runAll()

        assertTrue(finished)
        assertEquals(
            listOf(
                "bind:raw+jpeg", "capture", "release",
                "bind:raw+jpeg", "lock", "capture", "unlock", "release",
                "bind:raw", "capture", "release",
                "bind:raw", "lock", "capture", "unlock", "release",
            ),
            camera.calls,
        )
        assertRestored(camera)
        assertEquals(ProbeRun.entries.map { it.id }, ofKind("run").map { it["run"]!!.jsonPrimitive.content })
        assertTrue(ofKind("run").all { it["outcome"]!!.jsonPrimitive.content == "complete" })
        assertEquals(4 * BurstProbe.FRAMES_PER_RUN, ofKind("frame").size)
        assertEquals(4, ofKind("handback").size)
        assertTrue(ofKind("handback").all { it["probeUnbound"] == JsonPrimitive(true) })
        assertEquals(1, ofKind("start").size)
        assertEquals(JsonPrimitive(true), ofKind("end").single()["completed"])
    }

    @Test
    fun `a failure in the middle of a locked run still releases the lock and hands the camera back, and the next runs go on`() = runTest {
        val camera = FakeCamera(captureBehaviour = { call, _, count ->
            if (call == 1) error("the capture session closed mid-burst") else okCapture(count)
        })
        val finished = BurstProbe(camera, session = 1, log = { lines += it }).runAll()

        assertTrue(finished)
        assertEquals(listOf("bind:raw+jpeg", "lock", "capture", "unlock", "release"), camera.calls.subList(3, 8))
        assertRestored(camera)
        val failed = ofKind("run").single { it["run"]!!.jsonPrimitive.content == "raw_jpeg_locked" }
        assertEquals("failed", failed["outcome"]!!.jsonPrimitive.content)
        assertTrue(failed["reason"]!!.jsonPrimitive.content, failed["reason"]!!.jsonPrimitive.content.contains("the capture session closed mid-burst"))
        assertEquals(4, camera.calls.count { it.startsWith("bind:") })
    }

    @Test
    fun `a lock that never reaches LOCKED captures nothing, is released, and is reported with its reason`() = runTest {
        val camera = FakeCamera(lockOutcome = { LockOutcome.NotReached("no LOCKED result within 3000 ms", lastAeState = 2, lastAwbState = 1) })
        BurstProbe(camera, session = 1, log = { lines += it }).runAll(runs = listOf(ProbeRun.RAW_JPEG_LOCKED))

        assertEquals(listOf("bind:raw+jpeg", "lock", "unlock", "release"), camera.calls)
        assertRestored(camera)
        val run = ofKind("run").single()
        assertEquals("failed", run["outcome"]!!.jsonPrimitive.content)
        val lock = run["lock"]!!.jsonObject
        assertEquals(JsonPrimitive(false), lock["reached"])
        assertEquals("no LOCKED result within 3000 ms", lock["reason"]!!.jsonPrimitive.content)
        assertEquals("CONVERGED", lock["lastAeState"]!!.jsonPrimitive.content)
        assertEquals(0, ofKind("frame").size)
    }

    @Test
    fun `a bind that fails asks for nothing more and still hands back`() = runTest {
        val camera = FakeCamera(bindOutcome = { BindOutcome.Failed("the camera refused the streams") })
        BurstProbe(camera, session = 1, log = { lines += it }).runAll(runs = listOf(ProbeRun.RAW_ONLY_LOCKED))

        assertEquals(listOf("bind:raw", "release"), camera.calls)
        assertRestored(camera)
        val run = ofKind("run").single()
        assertEquals("failed", run["outcome"]!!.jsonPrimitive.content)
        assertEquals("the camera refused the streams", run["reason"]!!.jsonPrimitive.content)
    }

    @Test
    fun `an output the camera does not offer is reported unsupported, not failed and not skipped`() = runTest {
        val camera = FakeCamera(bindOutcome = { output ->
            if (output == ProbeOutput.RAW_ONLY) BindOutcome.Unsupported("RAW-only is not among this camera's output formats") else BindOutcome.Bound()
        })
        BurstProbe(camera, session = 1, log = { lines += it }).runAll()

        assertRestored(camera)
        val outcomes = ofKind("run").associate { it["run"]!!.jsonPrimitive.content to it["outcome"]!!.jsonPrimitive.content }
        assertEquals(
            mapOf("raw_jpeg_unlocked" to "complete", "raw_jpeg_locked" to "complete", "raw_only_unlocked" to "unsupported", "raw_only_locked" to "unsupported"),
            outcomes,
        )
    }

    @Test
    fun `a hand-back that cannot be confirmed stops the probe before it borrows the camera again`() = runTest {
        val camera = FakeCamera(releaseBehaviour = { Handback(probeUnbound = false, detail = "still bound after unbind") })
        val finished = BurstProbe(camera, session = 1, log = { lines += it }).runAll()

        assertFalse(finished)
        assertEquals(listOf("bind:raw+jpeg", "capture", "release"), camera.calls)
        val end = ofKind("end").single()
        assertEquals(JsonPrimitive(false), end["completed"])
        assertTrue(end["reason"]!!.jsonPrimitive.content, end["reason"]!!.jsonPrimitive.content.contains("raw_jpeg_unlocked"))
    }

    @Test
    fun `a lock release that throws still hands the camera back, and stops the probe`() = runTest {
        val camera = FakeCamera(unlockBehaviour = { error("clear failed") })
        val finished = BurstProbe(camera, session = 1, log = { lines += it }).runAll()

        assertFalse(finished)
        assertEquals(listOf("bind:raw+jpeg", "capture", "release", "bind:raw+jpeg", "lock", "capture", "unlock", "release"), camera.calls)
        val handback = ofKind("handback").last()
        assertEquals(JsonPrimitive(false), handback["lockReleased"])
        assertTrue(handback["lockDetail"]!!.jsonPrimitive.content, handback["lockDetail"]!!.jsonPrimitive.content.contains("clear failed"))
    }

    @Test
    fun `cancelling the probe mid-burst still releases the lock and hands the camera back`() = runTest {
        val camera = FakeCamera(captureBehaviour = { _, _, _ -> awaitCancellation() })
        val job = launch { BurstProbe(camera, session = 1, log = { lines += it }).runAll(runs = listOf(ProbeRun.RAW_ONLY_LOCKED)) }
        runCurrent()
        assertEquals(listOf("bind:raw", "lock", "capture"), camera.calls)

        job.cancel()
        job.join()

        assertEquals(listOf("bind:raw", "lock", "capture", "unlock", "release"), camera.calls)
        assertRestored(camera)
        assertEquals("failed", ofKind("run").single()["outcome"]!!.jsonPrimitive.content)
    }

    private companion object {
        fun okCapture(count: Int) = BurstCapture(
            frames = (0 until count).map { i ->
                ProbeFrame(
                    index = i, submitMs = 0.1 * i, startedMs = 50.0 + 300 * i, rawArrivalMs = 100.0 + 300 * i, jpegArrivalMs = null,
                    sensorTimestampNs = 1_000_000_000L + i * 300_000_000L, resultMatched = true, exposureTimeNs = 10_000_000L,
                    iso = 100, postRawBoost = 100, aeState = 3, awbState = 3, afState = 2, focusDistanceDiopters = 1f,
                    blackLevel = listOf(64f, 64f, 64f, 64f), whiteLevel = 1023, rawCopyMs = 10.0, sharpness = 0.01,
                )
            },
            facts = ProbeRunFacts(
                rawSize = "4000x3000", jpegSize = null, minFrameDurationNs = 33_333_333L, stallDurationNs = 0L,
                heapUsedPeakBytes = 100L shl 20, timestampSource = "REALTIME", pipelineMaxDepth = 8,
            ),
        )
    }
}
