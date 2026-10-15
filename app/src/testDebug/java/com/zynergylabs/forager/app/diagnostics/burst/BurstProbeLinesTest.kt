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
 * The probe's machine-readable lines and its run summary (dispatch 2026-10-15-06). The planner tabulates these lines by
 * hand from logcat, so the format is asserted exactly, and each line is also parsed as JSON and its value types checked:
 * a number written as a string, or a missing reading written as 0, would pass a "the text appears" check and still break
 * the table.
 */
class BurstProbeLinesTest {

    private fun frame(
        index: Int,
        timestampMs: Long?,
        submitMs: Double? = 0.5 + index,
        rawArrivalMs: Double? = timestampMs?.let { it + 100.0 },
        exposureTimeNs: Long? = 16_666_666L,
        iso: Int? = 100,
        aeState: Int? = 3,
        awbState: Int? = 3,
        focus: Float? = 2.5f,
        resultMatched: Boolean = true,
        error: String? = null,
    ) = ProbeFrame(
        index = index,
        submitMs = submitMs,
        startedMs = null,
        rawArrivalMs = rawArrivalMs,
        jpegArrivalMs = null,
        sensorTimestampNs = timestampMs?.let { 1_000_000_000L + it * 1_000_000L },
        resultMatched = resultMatched,
        exposureTimeNs = exposureTimeNs,
        iso = iso,
        postRawBoost = 100,
        aeState = aeState,
        awbState = awbState,
        afState = 2,
        focusDistanceDiopters = focus,
        blackLevel = listOf(64f, 64f, 64f, 64f),
        whiteLevel = 1023,
        rawCopyMs = 10.0,
        sharpness = 0.01,
        error = error,
    )

    // ── The per-frame line ─────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a frame line is one JSON object with every reading under its own key`() {
        val line = BurstProbeLines.frameLine(
            session = 3,
            run = ProbeRun.RAW_JPEG_LOCKED,
            frame = ProbeFrame(
                index = 2,
                submitMs = 1.5,
                startedMs = 180.25,
                rawArrivalMs = 301.125,
                jpegArrivalMs = 305.0,
                sensorTimestampNs = 123_456_789_012_345L,
                resultMatched = true,
                exposureTimeNs = 16_666_666L,
                iso = 100,
                postRawBoost = 100,
                aeState = 3,
                awbState = 3,
                afState = 2,
                focusDistanceDiopters = 2.5f,
                blackLevel = listOf(64f, 64f, 64f, 64f),
                whiteLevel = 1023,
                rawCopyMs = 12.75,
                sharpness = 0.0123456,
                notes = emptyList(),
                error = null,
            ),
        )
        assertEquals(
            "{\"kind\":\"frame\",\"session\":3,\"run\":\"raw_jpeg_locked\",\"index\":2," +
                "\"submitMs\":1.500,\"startedMs\":180.250,\"rawArrivalMs\":301.125,\"jpegArrivalMs\":305.000," +
                "\"sensorTimestampNs\":123456789012345,\"resultMatched\":true,\"exposureTimeNs\":16666666,\"iso\":100,\"postRawBoost\":100," +
                "\"aeState\":\"LOCKED\",\"awbState\":\"LOCKED\",\"afState\":\"PASSIVE_FOCUSED\",\"focusDistanceDiopters\":2.5," +
                "\"blackLevel\":[64.0,64.0,64.0,64.0],\"whiteLevel\":1023,\"rawCopyMs\":12.750,\"sharpness\":0.012346," +
                "\"notes\":[],\"error\":null}",
            line,
        )
        val json = Json.parseToJsonElement(line).jsonObject
        assertTrue(json["sensorTimestampNs"] is JsonPrimitive && !(json["sensorTimestampNs"] as JsonPrimitive).isString)
        assertTrue((json["aeState"] as JsonPrimitive).isString)
        assertTrue(json["blackLevel"] is JsonArray)
        assertEquals(JsonNull, json["error"])
    }

    @Test
    fun `a frame that failed keeps nulls for what was not read and says why, escaped`() {
        val line = BurstProbeLines.frameLine(
            session = 1,
            run = ProbeRun.RAW_ONLY_UNLOCKED,
            frame = ProbeFrame(
                index = 5, submitMs = 2.0, startedMs = null, rawArrivalMs = null, jpegArrivalMs = null, sensorTimestampNs = null,
                resultMatched = false, exposureTimeNs = null, iso = null, postRawBoost = null, aeState = null, awbState = null,
                afState = null, focusDistanceDiopters = null, blackLevel = null, whiteLevel = null, rawCopyMs = null,
                sharpness = null, notes = listOf("no capture result"), error = "the library said \"closed\"\\",
            ),
        )
        val json = Json.parseToJsonElement(line).jsonObject
        assertEquals("frame", json["kind"]!!.jsonPrimitive.content)
        assertEquals("raw_only_unlocked", json["run"]!!.jsonPrimitive.content)
        for (key in listOf("rawArrivalMs", "sensorTimestampNs", "exposureTimeNs", "iso", "aeState", "blackLevel", "whiteLevel", "sharpness")) {
            assertEquals("$key must be null when it was not read", JsonNull, json[key])
        }
        assertEquals(JsonPrimitive(false), json["resultMatched"])
        assertEquals("the library said \"closed\"\\", json["error"]!!.jsonPrimitive.content)
        assertEquals(listOf("no capture result"), (json["notes"] as JsonArray).map { it.jsonPrimitive.content })
    }

    @Test
    fun `camera2 states are written by name, and an unknown value is written as unknown with its number`() {
        fun states(ae: Int, awb: Int, af: Int): JsonObject = Json.parseToJsonElement(
            BurstProbeLines.frameLine(1, ProbeRun.RAW_JPEG_UNLOCKED, frame(0, 0).copy(aeState = ae, awbState = awb, afState = af)),
        ).jsonObject
        val a = states(ae = 2, awb = 1, af = 6)
        assertEquals("CONVERGED", a["aeState"]!!.jsonPrimitive.content)
        assertEquals("SEARCHING", a["awbState"]!!.jsonPrimitive.content)
        assertEquals("PASSIVE_UNFOCUSED", a["afState"]!!.jsonPrimitive.content)
        val b = states(ae = 4, awb = 0, af = 4)
        assertEquals("FLASH_REQUIRED", b["aeState"]!!.jsonPrimitive.content)
        assertEquals("INACTIVE", b["awbState"]!!.jsonPrimitive.content)
        assertEquals("FOCUSED_LOCKED", b["afState"]!!.jsonPrimitive.content)
        assertEquals("UNKNOWN_9", states(ae = 9, awb = 3, af = 0)["aeState"]!!.jsonPrimitive.content)
    }

    // ── The run summary: rate, spread and lock hold ────────────────────────────────────────────────────────────────────

    @Test
    fun `the rate and spread come from the sensor timestamps of the frames that arrived`() {
        // Exposures at 0, 300, 610 and 900 ms: intervals 300, 310, 290. Mean 300 ms, 3.333 frames a second, spread 20 ms.
        // The fifth frame never arrived and is left out of every figure.
        val frames = listOf(frame(0, 0), frame(1, 300), frame(2, 610), frame(3, 900), frame(4, null, error = "never arrived"))
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_UNLOCKED, 5, frames)
        assertEquals(5, summary.framesExpected)
        assertEquals(4, summary.framesArrived)
        assertEquals(listOf(300.0, 310.0, 290.0), summary.intervalsMs)
        assertEquals(300.0, summary.meanIntervalMs!!, 1e-9)
        assertEquals(290.0, summary.minIntervalMs!!, 1e-9)
        assertEquals(310.0, summary.maxIntervalMs!!, 1e-9)
        assertEquals(20.0, summary.spreadMs!!, 1e-9)
        assertEquals(1000.0 / 300.0, summary.framesPerSecond!!, 1e-9)
        // First submit 0.5 ms; the last arrival is frame 3's, 900 + 100 ms.
        assertEquals(999.5, summary.firstSubmitToLastArrivalMs!!, 1e-9)
    }

    @Test
    fun `frames are ordered by when they were exposed, not by the order they are listed`() {
        val inOrder = BurstProbeLines.summarize(ProbeRun.RAW_ONLY_UNLOCKED, 3, listOf(frame(0, 0), frame(1, 250), frame(2, 600)))
        val shuffled = BurstProbeLines.summarize(ProbeRun.RAW_ONLY_UNLOCKED, 3, listOf(frame(2, 600), frame(0, 0), frame(1, 250)))
        assertEquals(listOf(250.0, 350.0), inOrder.intervalsMs)
        assertEquals(inOrder, shuffled)
    }

    @Test
    fun `one frame has no interval, so no rate, and says so with nulls rather than zeros`() {
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_ONLY_UNLOCKED, 8, listOf(frame(0, 0)))
        assertEquals(1, summary.framesArrived)
        assertEquals(emptyList<Double>(), summary.intervalsMs)
        assertNull(summary.meanIntervalMs)
        assertNull(summary.spreadMs)
        assertNull(summary.framesPerSecond)
    }

    @Test
    fun `a locked run whose every frame reads LOCKED with one exposure and one ISO held its lock`() {
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_LOCKED, 3, listOf(frame(0, 0), frame(1, 300), frame(2, 600)))
        assertEquals(3, summary.framesWithResult)
        assertEquals(3, summary.aeLockedFrames)
        assertEquals(3, summary.awbLockedFrames)
        assertEquals(true, summary.exposureHeld)
        assertEquals(true, summary.isoHeld)
        assertEquals(true, summary.focusHeld)
        assertEquals(true, summary.lockHeld)
    }

    @Test
    fun `a locked run with one frame at a different exposure did not hold`() {
        val frames = listOf(frame(0, 0), frame(1, 300, exposureTimeNs = 8_333_333L), frame(2, 600))
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_LOCKED, 3, frames)
        assertEquals(false, summary.exposureHeld)
        assertEquals(false, summary.lockHeld)
    }

    @Test
    fun `a locked run with a frame that left LOCKED did not hold, even at one exposure`() {
        val frames = listOf(frame(0, 0), frame(1, 300, aeState = 2), frame(2, 600))
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_ONLY_LOCKED, 3, frames)
        assertEquals(2, summary.aeLockedFrames)
        assertEquals(true, summary.exposureHeld)
        assertEquals(false, summary.lockHeld)
    }

    @Test
    fun `an unlocked run has no lock to hold, however its frames read`() {
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_UNLOCKED, 3, listOf(frame(0, 0), frame(1, 300), frame(2, 600)))
        assertNull(summary.lockHeld)
        assertEquals(true, summary.exposureHeld)
    }

    @Test
    fun `a frame without its capture result makes the hold unknown, not held and not broken`() {
        val frames = listOf(
            frame(0, 0),
            frame(1, 300, exposureTimeNs = null, iso = null, aeState = null, awbState = null, focus = null, resultMatched = false),
            frame(2, 600),
        )
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_LOCKED, 3, frames)
        assertEquals(3, summary.framesArrived)
        assertEquals(2, summary.framesWithResult)
        assertNull(summary.exposureHeld)
        assertNull(summary.lockHeld)
    }

    @Test
    fun `focus is reported as its range over the frames`() {
        val frames = listOf(frame(0, 0, focus = 2.5f), frame(1, 300, focus = 3.0f), frame(2, 600, focus = 2.75f))
        val summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_UNLOCKED, 3, frames)
        assertEquals(2.5f, summary.focusMinDiopters!!, 0f)
        assertEquals(3.0f, summary.focusMaxDiopters!!, 0f)
        assertEquals(false, summary.focusHeld)
    }

    // ── The run line carries the summary ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a run line carries its outcome, lock, summary, sizes, sensor limits and memory as typed values`() {
        val frames = listOf(frame(0, 0), frame(1, 300), frame(2, 610))
        val line = BurstProbeLines.runLine(
            session = 2,
            run = ProbeRun.RAW_JPEG_LOCKED,
            outcome = RunOutcomeKind.PARTIAL,
            reason = "1 of 4 frames never arrived",
            lock = LockOutcome.Reached(waitMs = 120.5, aeState = 3, awbState = 3),
            summary = BurstProbeLines.summarize(ProbeRun.RAW_JPEG_LOCKED, 4, frames),
            facts = ProbeRunFacts(
                rawSize = "4000x3000", jpegSize = "4000x3000", minFrameDurationNs = 33_333_333L, stallDurationNs = 0L,
                heapUsedPeakBytes = 300_000_000L, timestampSource = "REALTIME", pipelineMaxDepth = 8,
            ),
            before = MemoryReading(maxMemoryBytes = 536_870_912L, usedBytes = 100_000_000L),
            after = MemoryReading(maxMemoryBytes = 536_870_912L, usedBytes = 110_000_000L),
        )
        val json = Json.parseToJsonElement(line).jsonObject
        assertEquals("run", json["kind"]!!.jsonPrimitive.content)
        assertEquals("raw_jpeg_locked", json["run"]!!.jsonPrimitive.content)
        assertEquals("partial", json["outcome"]!!.jsonPrimitive.content)
        assertEquals("1 of 4 frames never arrived", json["reason"]!!.jsonPrimitive.content)
        val lock = json["lock"]!!.jsonObject
        assertEquals(JsonPrimitive(true), lock["reached"])
        assertEquals("LOCKED", lock["aeState"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(4), json["framesExpected"])
        assertEquals(JsonPrimitive(3), json["framesArrived"])
        assertEquals(listOf(300.0, 310.0), (json["intervalsMs"] as JsonArray).map { it.jsonPrimitive.content.toDouble() })
        assertEquals(305.0, json["meanIntervalMs"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(10.0, json["spreadMs"]!!.jsonPrimitive.content.toDouble(), 1e-9)
        assertEquals(JsonPrimitive(true), json["lockHeld"])
        assertEquals("4000x3000", json["rawSize"]!!.jsonPrimitive.content)
        assertEquals(JsonPrimitive(33_333_333L), json["minFrameDurationNs"])
        assertEquals(JsonPrimitive(0L), json["stallDurationNs"])
        assertEquals(JsonPrimitive(536_870_912L), json["maxMemoryBytes"])
        assertEquals(JsonPrimitive(100_000_000L), json["heapUsedBeforeBytes"])
        assertEquals(JsonPrimitive(110_000_000L), json["heapUsedAfterBytes"])
        assertEquals(JsonPrimitive(300_000_000L), json["heapUsedPeakBytes"])
        assertFalse((json["framesPerSecond"] as JsonPrimitive).isString)
    }
}
