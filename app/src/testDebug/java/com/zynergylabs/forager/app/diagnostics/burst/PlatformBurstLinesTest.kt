package com.zynergylabs.forager.app.diagnostics.burst

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fast-burst test's lines, run summary and swap summary (dispatch 2026-10-15-09). The planner tabulates burst rate,
 * swap pause, hand-back time and the processing modes from these lines, so the frame line is asserted exactly, and every
 * line is parsed as JSON with its value types checked: a number written as a string, or a missing reading written as 0,
 * would pass a "the text appears" check and still break the table.
 */
class PlatformBurstLinesTest {

    private fun frame(
        index: Int,
        timestampNs: Long?,
        arrivalMs: Double? = 1000.0 + 40.0 * index,
        noiseReduction: Int? = 0,
        error: String? = null,
        dngOutcome: String? = PlatformBurstLines.DNG_WRITTEN,
        resultMatched: Boolean = true,
    ) = PlatformFrame(
        index = index,
        arrivalMs = arrivalMs,
        sensorTimestampNs = timestampNs,
        resultMatched = resultMatched,
        exposureTimeNs = 8_333_333L,
        iso = 69,
        frameDurationNs = 33_333_333L,
        postRawBoost = 100,
        noiseReductionMode = noiseReduction,
        hotPixelMode = 0,
        edgeMode = 1,
        shadingMode = 2,
        tonemapMode = null,
        blackLevelLock = false,
        lensShadingMapMode = 1,
        blackLevel = listOf(64.0f, 64.0f, 64.0f, 64.0f),
        whiteLevel = 1023,
        copyMs = 12.3456,
        cornerToCentre = 0.98766,
        dngFile = "raw-burst-test/20261015-221502Z-s1/nr_hotpixel_off/frame-0${index + 1}.dng",
        dngOutcome = dngOutcome,
        error = error,
    )

    private fun obj(line: String): JsonObject = Json.parseToJsonElement(line).jsonObject

    private fun assertNumber(o: JsonObject, key: String) {
        val v = o[key]
        assertTrue("$key should be a JSON number, was $v", v is JsonPrimitive && !v.isString && v !is JsonNull)
    }

    @Test
    fun `the frame line is exactly this, with modes by name`() {
        val line = PlatformBurstLines.frameLine(1, PlatformRun.NR_HOTPIXEL_OFF, frame(0, timestampNs = 5_000_000_000L))
        assertEquals(
            "{\"kind\":\"frame\",\"session\":1,\"run\":\"nr_hotpixel_off\",\"index\":0,\"arrivalMs\":1000.000," +
                "\"sensorTimestampNs\":5000000000,\"resultMatched\":true,\"exposureTimeNs\":8333333,\"iso\":69," +
                "\"frameDurationNs\":33333333,\"postRawBoost\":100,\"noiseReduction\":\"OFF\",\"hotPixel\":\"OFF\"," +
                "\"edge\":\"FAST\",\"shading\":\"HIGH_QUALITY\",\"tonemap\":null,\"blackLevelLock\":false," +
                "\"lensShadingMapMode\":\"ON\",\"blackLevel\":[64.0,64.0,64.0,64.0],\"whiteLevel\":1023,\"copyMs\":12.346," +
                "\"cornerToCentre\":0.9877,\"dngFile\":\"raw-burst-test/20261015-221502Z-s1/nr_hotpixel_off/frame-01.dng\"," +
                "\"dngOutcome\":\"written\",\"notes\":[],\"error\":null}",
            line,
        )
    }

    @Test
    fun `every camera2 noise-reduction mode is named, and a value camera2 does not define is kept as UNKNOWN`() {
        val names = (0..5).map { mode ->
            obj(PlatformBurstLines.frameLine(1, PlatformRun.DEFAULT_LOCKED, frame(0, 1L, noiseReduction = mode))).getValue("noiseReduction").jsonPrimitive.content
        }
        assertEquals(listOf("OFF", "FAST", "HIGH_QUALITY", "MINIMAL", "ZERO_SHUTTER_LAG", "UNKNOWN_5"), names)
    }

    @Test
    fun `a frame that never arrived writes its missing readings as null, not zero, and carries its error`() {
        val missing = PlatformFrame(
            index = 7, arrivalMs = null, sensorTimestampNs = null, resultMatched = false, exposureTimeNs = null, iso = null,
            frameDurationNs = null, postRawBoost = null, noiseReductionMode = null, hotPixelMode = null, edgeMode = null,
            shadingMode = null, tonemapMode = null, blackLevelLock = null, lensShadingMapMode = null, blackLevel = null,
            whiteLevel = null, copyMs = null, cornerToCentre = null, dngFile = null, dngOutcome = null,
            error = "the RAW image did not arrive within 9000 ms of the burst's submit",
        )
        val o = obj(PlatformBurstLines.frameLine(2, PlatformRun.SHADING_OFF, missing))
        for (key in listOf("arrivalMs", "sensorTimestampNs", "exposureTimeNs", "iso", "noiseReduction", "blackLevel", "copyMs", "dngFile")) {
            assertEquals("$key is null", JsonNull, o[key])
        }
        assertEquals("the RAW image did not arrive within 9000 ms of the burst's submit", o.getValue("error").jsonPrimitive.content)
    }

    @Test
    fun `the summary reads the sensor's spacing in timestamp order, its rate, spread and hold-still time`() {
        // Delivered out of order; spaced 33.3, 33.4, 33.3 ms by the sensor.
        val frames = listOf(
            frame(1, 1_033_300_000L),
            frame(0, 1_000_000_000L),
            frame(3, 1_100_000_000L),
            frame(2, 1_066_700_000L),
        )
        val s = PlatformBurstLines.summarize(framesExpected = 4, frames = frames, submitMs = 900.0)

        assertEquals(4, s.framesArrived)
        assertEquals(listOf(33.3, 33.4, 33.3), s.intervalsMs.map { Math.round(it * 10) / 10.0 })
        assertEquals(100.0 / 3, s.meanIntervalMs!!, 1e-6)
        assertEquals(0.1, s.spreadMs!!, 1e-6)
        assertEquals(30.0, s.framesPerSecond!!, 1e-6)
        assertEquals(100.0, s.firstToLastMs!!, 1e-6)
        assertEquals("last arrival 1120 minus submit 900", 220.0, s.submitToLastArrivalMs!!, 1e-6)
        assertEquals(4, s.framesWithResult)
        assertEquals(4, s.framesSaved)
    }

    @Test
    fun `the summary leaves out frames that failed, and counts only written DNGs as saved`() {
        val frames = listOf(
            frame(0, 1_000_000_000L),
            frame(1, 1_033_333_333L, dngOutcome = "not saved: the DNG writer refused the file: check failed"),
            frame(2, null, error = "never arrived", dngOutcome = null),
            frame(3, 1_066_666_666L, resultMatched = false),
        )
        val s = PlatformBurstLines.summarize(framesExpected = 4, frames = frames, submitMs = null)

        assertEquals(3, s.framesArrived)
        assertEquals(2, s.framesWithResult)
        assertEquals(2, s.framesSaved)
        assertNull("no submit time, no submit-to-arrival figure", s.submitToLastArrivalMs)
    }

    @Test
    fun `one frame has no interval and no rate but a hold-still time of zero, and none has neither`() {
        val one = PlatformBurstLines.summarize(8, listOf(frame(0, 1_000_000_000L)), 0.0)
        assertTrue(one.intervalsMs.isEmpty())
        assertNull(one.meanIntervalMs)
        assertNull(one.framesPerSecond)
        assertNull(one.spreadMs)
        assertEquals(0.0, one.firstToLastMs!!, 0.0)

        val none = PlatformBurstLines.summarize(8, emptyList(), 0.0)
        assertEquals(0, none.framesArrived)
        assertNull(none.firstToLastMs)
    }

    @Test
    fun `the run line carries the summary and facts with numbers as numbers`() {
        val frames = (0 until 8).map { frame(it, 1_000_000_000L + 33_333_333L * it) }
        val facts = PlatformRunFacts(
            rawSize = "4000x3000",
            requestMethod = "captureBurst",
            requested = listOf("NOISE_REDUCTION_MODE=OFF", "HOT_PIXEL_MODE=OFF"),
            minFrameDurationNs = 33_333_333L,
            stallDurationNs = 0L,
            maxMemoryBytes = 268_435_456L,
            heapBeforeBytes = 40_000_000L,
            heapPeakBytes = 66_000_000L,
            heapAfterBytes = 41_000_000L,
            submitMs = 950.0,
            saveMs = 2400.5,
            folder = "raw-burst-test/20261015-221502Z-s1/nr_hotpixel_off",
            notes = listOf("a note"),
        )
        val summary = PlatformBurstLines.summarize(8, frames, facts.submitMs)
        val o = obj(PlatformBurstLines.runLine(1, PlatformRun.NR_HOTPIXEL_OFF, RunOutcomeKind.COMPLETE, null, summary, facts))

        assertEquals("run", o.getValue("kind").jsonPrimitive.content)
        assertEquals("complete", o.getValue("outcome").jsonPrimitive.content)
        assertEquals("captureBurst", o.getValue("requestMethod").jsonPrimitive.content)
        assertEquals(listOf("NOISE_REDUCTION_MODE=OFF", "HOT_PIXEL_MODE=OFF"), (o.getValue("requested") as JsonArray).map { it.jsonPrimitive.content })
        for (key in listOf("framesArrived", "meanIntervalMs", "spreadMs", "framesPerSecond", "firstToLastMs", "minFrameDurationNs", "stallDurationNs", "heapPeakBytes", "saveMs")) {
            assertNumber(o, key)
        }
        assertEquals(8, o.getValue("framesArrived").jsonPrimitive.content.toInt())
        assertEquals("233.333", o.getValue("firstToLastMs").jsonPrimitive.content)
        assertEquals("30.000", o.getValue("framesPerSecond").jsonPrimitive.content)
        assertEquals(7, (o.getValue("intervalsMs") as JsonArray).size)
    }

    @Test
    fun `an unsupported run line has its reason and nulls for every figure`() {
        val o = obj(PlatformBurstLines.runLine(1, PlatformRun.MANUAL_EXPOSURE, RunOutcomeKind.UNSUPPORTED, "MANUAL_SENSOR is not offered", null, null))
        assertEquals("unsupported", o.getValue("outcome").jsonPrimitive.content)
        assertEquals("MANUAL_SENSOR is not offered", o.getValue("reason").jsonPrimitive.content)
        assertEquals(JsonNull, o["framesArrived"])
        assertEquals(JsonNull, o["framesPerSecond"])
    }

    @Test
    fun `the swap summary is the pause from trigger to the first RAW frame, and the viewfinder's time not live`() {
        val borrow = SwapTiming(
            cameraId = "0", cameraxClosedMs = 150.0, viewfinderIdleMs = 140.0, platformOpenedMs = 400.0,
            sessionConfiguredMs = 600.0, rawSize = "4000x3000", previewSize = "1440x1080", viewfinder = "PreviewView COMPATIBLE",
            cameraStates = emptyList(),
        )
        val handback = PlatformHandback(
            platformClosed = true, probeUnbound = true, handbackStartMs = 9000.0, platformClosedMs = 9040.0,
            cameraxOpenMs = 9300.0, viewfinderLiveMs = 9650.0, cameraStates = emptyList(),
        )
        val s = PlatformBurstLines.swapSummary(borrow, firstRawArrivalMs = 1250.0, handback = handback)

        assertEquals(400.0, s.triggerToPlatformOpenMs!!, 0.0)
        assertEquals(850.0, s.platformOpenToFirstRawMs!!, 0.0)
        assertEquals(1250.0, s.triggerToFirstRawMs!!, 0.0)
        assertEquals(9510.0, s.viewfinderNotLiveMs!!, 0.0)
        assertEquals(650.0, s.handbackToViewfinderLiveMs!!, 0.0)

        val end = obj(PlatformBurstLines.endLine(1, completed = true, reason = null, swap = s))
        assertEquals("850.000", end.getValue("platformOpenToFirstRawMs").jsonPrimitive.content)
        assertNumber(end, "handbackToViewfinderLiveMs")
    }

    @Test
    fun `a figure whose end was not seen is null, not zero`() {
        val s = PlatformBurstLines.swapSummary(borrow = null, firstRawArrivalMs = null, handback = null)
        assertNull(s.triggerToPlatformOpenMs)
        assertNull(s.platformOpenToFirstRawMs)
        assertNull(s.viewfinderNotLiveMs)
        assertNull(s.handbackToViewfinderLiveMs)
        val end = obj(PlatformBurstLines.endLine(3, completed = false, reason = "the camera was not borrowed", swap = s))
        assertEquals(JsonNull, end["triggerToPlatformOpenMs"])
        assertEquals("false", end.getValue("completed").jsonPrimitive.content)
    }

    @Test
    fun `the handback line says handed back only when the platform closed and CameraX has the camera again`() {
        val base = PlatformHandback(
            platformClosed = true, probeUnbound = true, handbackStartMs = 100.0, platformClosedMs = 130.0,
            cameraxOpenMs = 400.0, viewfinderLiveMs = 700.0, cameraStates = listOf("OPENING@150.000", "OPEN@400.000"),
        )
        val ok = obj(PlatformBurstLines.handbackLine(1, base))
        assertEquals("true", ok.getValue("handedBack").jsonPrimitive.content)
        assertEquals("300.000", ok.getValue("handbackToCameraOpenMs").jsonPrimitive.content)
        assertEquals("600.000", ok.getValue("handbackToViewfinderLiveMs").jsonPrimitive.content)

        val notClosed = obj(PlatformBurstLines.handbackLine(1, base.copy(platformClosed = false)))
        assertEquals("false", notClosed.getValue("handedBack").jsonPrimitive.content)
        val stillBound = obj(PlatformBurstLines.handbackLine(1, base.copy(probeUnbound = false)))
        assertEquals("false", stillBound.getValue("handedBack").jsonPrimitive.content)
        val noViewfinder = obj(PlatformBurstLines.handbackLine(1, base.copy(viewfinderLiveMs = null)))
        assertEquals(JsonNull, noViewfinder["handbackToViewfinderLiveMs"])
    }

    @Test
    fun `the borrow and converge lines parse, with the swap times as numbers`() {
        val borrowed = obj(
            PlatformBurstLines.borrowLine(
                1,
                BorrowOutcome.Borrowed(
                    SwapTiming(
                        cameraId = "0", cameraxClosedMs = 150.0, viewfinderIdleMs = null, platformOpenedMs = 400.0,
                        sessionConfiguredMs = 600.0, rawSize = "4000x3000", previewSize = "1440x1080",
                        viewfinder = "no PreviewView found", cameraStates = listOf("CLOSED@150.000"),
                    ),
                ),
            ),
        )
        assertEquals("borrowed", borrowed.getValue("outcome").jsonPrimitive.content)
        assertNumber(borrowed, "platformOpenedMs")
        assertEquals(JsonNull, borrowed["viewfinderIdleMs"])

        val notLocked = obj(PlatformBurstLines.convergeLine(1, ConvergeOutcome.NotReached("timed out", lastAeState = 1, lastAwbState = 2)))
        assertEquals("false", notLocked.getValue("locked").jsonPrimitive.content)
        assertEquals("SEARCHING", notLocked.getValue("lastAeState").jsonPrimitive.content)
        assertEquals("CONVERGED", notLocked.getValue("lastAwbState").jsonPrimitive.content)
        assertFalse(notLocked.containsKey("frames"))
    }
}
