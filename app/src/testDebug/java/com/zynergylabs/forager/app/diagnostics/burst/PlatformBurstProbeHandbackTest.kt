package com.zynergylabs.forager.app.diagnostics.burst

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
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
 * The fast-burst test borrows the camera dialog's camera and must hand it back on every path (dispatch 2026-10-15-09:
 * "Every path, including a failure mid-run, must hand back and release locks in a `finally`. The camera must never be
 * left dead."; abort condition: "The camera is left dead in any test").
 *
 * Driven through the probe's real entry point, [PlatformBurstProbe.runAll], over a fake camera that models the three
 * things that matter: whether CameraX has let the camera go (the dialog's binding suspended), whether the platform
 * session holds the device, and whether AE/AWB are locked. "Dead" here is the state the probe can leave: the dialog's
 * binding still suspended, or the platform still holding the device, after runAll returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlatformBurstProbeHandbackTest {

    private class FakeCamera(
        val borrowBehaviour: (FakeCamera) -> BorrowOutcome = { fake ->
            fake.cameraxReleased = true
            fake.platformOpen = true
            BorrowOutcome.Borrowed(timing())
        },
        val convergeBehaviour: (FakeCamera) -> ConvergeOutcome = { fake ->
            fake.locked = true
            LOCKED
        },
        val burstBehaviour: suspend (run: PlatformRun, count: Int) -> PlatformBurstOutcome = { _, count -> captured(count) },
        val releaseLocksBehaviour: (FakeCamera) -> LockRelease = { fake ->
            fake.locked = false
            LockRelease(cleared = true)
        },
        val handBackBehaviour: (FakeCamera) -> PlatformHandback = { fake ->
            fake.platformOpen = false
            fake.cameraxReleased = false
            fake.locked = false // closing the platform session ends every request it carried, locks included
            handback()
        },
    ) : PlatformBurstCamera {
        val calls = mutableListOf<String>()
        var cameraxReleased = false
        var platformOpen = false
        var locked = false

        override fun memory() = MemoryReading(maxMemoryBytes = 512L shl 20, usedBytes = 64L shl 20)

        override suspend fun borrow(): BorrowOutcome {
            calls += "borrow"
            return borrowBehaviour(this)
        }

        override suspend fun converge(): ConvergeOutcome {
            calls += "converge"
            return convergeBehaviour(this)
        }

        override suspend fun burst(run: PlatformRun, count: Int, converged: ConvergeOutcome.Locked): PlatformBurstOutcome {
            calls += "burst:${run.id}"
            return burstBehaviour(run, count)
        }

        override suspend fun releaseLocks(): LockRelease {
            calls += "releaseLocks"
            return releaseLocksBehaviour(this)
        }

        override suspend fun handBack(): PlatformHandback {
            calls += "handBack"
            return handBackBehaviour(this)
        }

        /** The camera is live for the dialog again: CameraX has it back, the platform holds nothing, nothing is locked. */
        fun liveForTheDialog() = !cameraxReleased && !platformOpen && !locked
    }

    private val lines = mutableListOf<String>()

    private fun parsed(): List<JsonObject> = lines.map { Json.parseToJsonElement(it).jsonObject }
    private fun ofKind(kind: String) = parsed().filter { it["kind"]!!.jsonPrimitive.content == kind }
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content

    private fun assertHandedBackOnce(camera: FakeCamera) {
        assertEquals("handBack is called exactly once, whatever happened: ${camera.calls}", 1, camera.calls.count { it == "handBack" })
        assertTrue("the camera is live for the dialog after runAll: ${camera.calls}", camera.liveForTheDialog())
        assertEquals("one handback line is logged", 1, ofKind("handback").size)
    }

    @Test
    fun `a clean session runs the four runs in the dispatch's order, then releases the locks and hands back`() = runTest {
        val camera = FakeCamera()
        val ok = PlatformBurstProbe(camera, session = 1) { lines += it }.runAll()

        assertTrue(ok)
        assertEquals(
            listOf(
                "borrow", "converge",
                "burst:default_locked", "burst:nr_hotpixel_off", "burst:manual_exposure", "burst:shading_off",
                "releaseLocks", "handBack",
            ),
            camera.calls,
        )
        assertHandedBackOnce(camera)
        val end = ofKind("end").single()
        assertEquals("true", end.text("completed"))
        assertEquals(listOf("default_locked", "nr_hotpixel_off", "manual_exposure", "shading_off"), ofKind("run").map { it.text("run") })
        assertTrue(ofKind("run").all { it.text("outcome") == "complete" })
    }

    @Test
    fun `a borrow that fails after CameraX let the camera go still hands it back`() = runTest {
        val camera = FakeCamera(borrowBehaviour = { fake ->
            fake.cameraxReleased = true
            BorrowOutcome.Failed("the platform open reported ERROR_CAMERA_IN_USE", timing())
        })
        val ok = PlatformBurstProbe(camera, session = 2) { lines += it }.runAll()

        assertFalse(ok)
        assertEquals(listOf("borrow", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("failed", ofKind("borrow").single().text("outcome"))
        assertEquals("false", ofKind("end").single().text("completed"))
        assertTrue(ofKind("end").single().text("reason").contains("not borrowed"))
    }

    @Test
    fun `a borrow that throws after opening the platform device still hands it back`() = runTest {
        val camera = FakeCamera(borrowBehaviour = { fake ->
            fake.cameraxReleased = true
            fake.platformOpen = true
            throw IllegalStateException("session configuration failed")
        })
        val ok = PlatformBurstProbe(camera, session = 3) { lines += it }.runAll()

        assertFalse(ok)
        assertHandedBackOnce(camera)
        assertTrue(ofKind("end").single().text("reason").contains("session configuration failed"))
    }

    @Test
    fun `a converge that throws after setting the lock releases the lock and hands back`() = runTest {
        val camera = FakeCamera(convergeBehaviour = { fake ->
            fake.locked = true
            throw IllegalStateException("the session closed during convergence")
        })
        val ok = PlatformBurstProbe(camera, session = 4) { lines += it }.runAll()

        assertFalse(ok)
        assertEquals(listOf("borrow", "converge", "releaseLocks", "handBack"), camera.calls)
        assertHandedBackOnce(camera)
        assertEquals("true", ofKind("release").single().text("lockReleased"))
    }

    @Test
    fun `a run that fails mid-run is logged as failed, the remaining runs go on, and the camera is handed back`() = runTest {
        val camera = FakeCamera(burstBehaviour = { run, count ->
            if (run == PlatformRun.NR_HOTPIXEL_OFF) throw IllegalStateException("frame 5 never arrived and the session died")
            captured(count)
        })
        val ok = PlatformBurstProbe(camera, session = 5) { lines += it }.runAll()

        assertTrue("one failed run does not fail the session; its run line says so", ok)
        assertHandedBackOnce(camera)
        val runs = ofKind("run").associateBy { it.text("run") }
        assertEquals("failed", runs.getValue("nr_hotpixel_off").text("outcome"))
        assertTrue(runs.getValue("nr_hotpixel_off").text("reason").contains("frame 5 never arrived"))
        assertEquals("complete", runs.getValue("manual_exposure").text("outcome"))
        assertEquals("complete", runs.getValue("shading_off").text("outcome"))
    }

    @Test
    fun `a run the phone cannot do is logged as unsupported with its reason`() = runTest {
        val camera = FakeCamera(burstBehaviour = { run, count ->
            if (run == PlatformRun.MANUAL_EXPOSURE) PlatformBurstOutcome.Unsupported("MANUAL_SENSOR is not offered") else captured(count)
        })
        PlatformBurstProbe(camera, session = 6) { lines += it }.runAll()

        val manual = ofKind("run").single { it.text("run") == "manual_exposure" }
        assertEquals("unsupported", manual.text("outcome"))
        assertEquals("MANUAL_SENSOR is not offered", manual.text("reason"))
        assertHandedBackOnce(camera)
    }

    @Test
    fun `cancelling the probe in the middle of a burst still releases the locks and hands back`() = runTest {
        val started = CompletableDeferred<Unit>()
        val camera = FakeCamera(burstBehaviour = { _, _ ->
            started.complete(Unit)
            awaitCancellation()
        })
        val job = launch { PlatformBurstProbe(camera, session = 7) { lines += it }.runAll() }
        runCurrent()
        assertTrue("the burst started", started.isCompleted)
        job.cancel()
        job.join()

        assertTrue(camera.calls.contains("releaseLocks"))
        assertHandedBackOnce(camera)
        assertEquals("false", ofKind("end").single().text("completed"))
        assertTrue(ofKind("end").single().text("reason").contains("cancelled"))
    }

    @Test
    fun `a lock release that throws cannot skip the hand-back`() = runTest {
        val camera = FakeCamera(releaseLocksBehaviour = { throw IllegalStateException("session already closed") })
        PlatformBurstProbe(camera, session = 8) { lines += it }.runAll()

        assertHandedBackOnce(camera)
        val release = ofKind("release").single()
        assertEquals("false", release.text("lockReleased"))
        assertTrue(release.text("detail").contains("session already closed"))
    }

    @Test
    fun `a hand-back that throws is reported as not handed back, never as success`() = runTest {
        val camera = FakeCamera(handBackBehaviour = { throw IllegalStateException("unbind threw") })
        val ok = PlatformBurstProbe(camera, session = 9) { lines += it }.runAll()

        assertFalse(ok)
        val handback = ofKind("handback").single()
        assertEquals("false", handback.text("handedBack"))
        assertTrue(handback.text("detail").contains("unbind threw"))
        val end = ofKind("end").single()
        assertEquals("false", end.text("completed"))
        assertTrue(end.text("reason").contains("could not be confirmed handed back"))
    }

    @Test
    fun `a hand-back that cannot confirm CameraX has the camera again fails the session`() = runTest {
        val camera = FakeCamera(handBackBehaviour = { fake ->
            fake.platformOpen = false
            handback().copy(probeUnbound = false, detail = "1 of the probe's use cases still read as bound after unbind")
        })
        val ok = PlatformBurstProbe(camera, session = 10) { lines += it }.runAll()

        assertFalse(ok)
        assertEquals("false", ofKind("end").single().text("completed"))
    }

    private companion object {
        val LOCKED = ConvergeOutcome.Locked(convergedMs = 900.0, lockedMs = 1000.0, exposureTimeNs = 8_333_333L, iso = 69, frameDurationNs = 33_333_333L)

        fun timing() = SwapTiming(
            cameraId = "0",
            cameraxClosedMs = 120.0,
            viewfinderIdleMs = 110.0,
            platformOpenedMs = 300.0,
            sessionConfiguredMs = 450.0,
            rawSize = "4000x3000",
            previewSize = "1440x1080",
            viewfinder = "PreviewView COMPATIBLE",
            cameraStates = listOf("CLOSING@20.000", "CLOSED@120.000"),
        )

        fun handback() = PlatformHandback(
            platformClosed = true,
            probeUnbound = true,
            handbackStartMs = 5000.0,
            platformClosedMs = 5050.0,
            cameraxOpenMs = 5400.0,
            viewfinderLiveMs = 5700.0,
            cameraStates = listOf("OPENING@5100.000", "OPEN@5400.000"),
        )

        fun captured(count: Int) = PlatformBurstOutcome.Captured(
            frames = List(count) { i ->
                PlatformFrame(
                    index = i,
                    arrivalMs = 1200.0 + 33.3 * i,
                    sensorTimestampNs = 1_000_000_000L + 33_333_333L * i,
                    resultMatched = true,
                    exposureTimeNs = 8_333_333L,
                    iso = 69,
                    frameDurationNs = 33_333_333L,
                    postRawBoost = 100,
                    noiseReductionMode = 1,
                    hotPixelMode = 1,
                    edgeMode = 1,
                    shadingMode = 1,
                    tonemapMode = null,
                    blackLevelLock = false,
                    lensShadingMapMode = 1,
                    blackLevel = listOf(64f, 64f, 64f, 64f),
                    whiteLevel = 1023,
                    copyMs = 12.0,
                    cornerToCentre = 0.98,
                    dngFile = "raw-burst-test/x/frame-%02d.dng".format(i + 1),
                    dngOutcome = PlatformBurstLines.DNG_WRITTEN,
                )
            },
            facts = PlatformRunFacts(
                rawSize = "4000x3000",
                requestMethod = "captureBurst",
                requested = emptyList(),
                minFrameDurationNs = 33_333_333L,
                stallDurationNs = 0L,
                maxMemoryBytes = 268_435_456L,
                heapBeforeBytes = 40_000_000L,
                heapPeakBytes = 70_000_000L,
                heapAfterBytes = 41_000_000L,
                submitMs = 1100.0,
                saveMs = 2400.0,
                folder = "raw-burst-test/x",
            ),
        )
    }
}
