package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sensor watch's lines (dispatch 2026-10-15-12: "Pure tests for: the line formats"). The planner tabulates which
 * physical sensor is active by zoom, distance and light from these lines, so the frame line is asserted exactly and the
 * others are parsed as JSON with their value types checked: a number written as a string, or a missing reading written
 * as 0, would pass a "the text appears" check and still break the table.
 */
class SensorWatchLinesTest {

    private fun parse(line: String): JsonObject = Json.parseToJsonElement(line).jsonObject
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.isNull(key: String) = this[key] == JsonNull
    private fun JsonObject.number(key: String): Double {
        val value = this[key]!!.jsonPrimitive
        assertTrue("$key is a JSON number, not a string: $value", !value.isString && value != JsonNull)
        return value.content.toDouble()
    }

    private val reading = FrameReading(
        arrivalNanos = 999_000_000L,
        sensorTimestampNs = 123_456_789L,
        still = false,
        captureIntent = 1,
        activePhysicalId = "5",
        zoomRatio = 1f,
        focusDistanceDiopters = 4f,
        afState = 2,
        exposureTimeNs = 16_666_666L,
        iso = 400,
        aperture = 1.8f,
        brightnessKey = "vendor.control.brightnessValue",
        brightnessValue = 1471.0,
    )

    @Test
    fun `the frame line, exactly`() {
        assertEquals(
            """{"kind":"frame","source":"preview","captureIntent":"PREVIEW","activePhysicalId":"5","zoomRatio":1.0,""" +
                """"focusDistanceDiopters":4.0,"focusDistanceM":0.250,"afState":"PASSIVE_FOCUSED","exposureTimeNs":16666666,""" +
                """"iso":400,"aperture":1.8,"ev100":5.60,"brightnessKey":"vendor.control.brightnessValue","brightnessValue":1471.000,""" +
                """"sensorTimestampNs":123456789}""",
            SensorWatchLines.frameLine(reading),
        )
    }

    @Test
    fun `a still's frame line says still, and readings not reported are null, never 0`() {
        val line = parse(
            SensorWatchLines.frameLine(
                reading.copy(
                    still = true, captureIntent = 2, activePhysicalId = null, zoomRatio = null, focusDistanceDiopters = 0f,
                    afState = null, aperture = null, brightnessKey = null, brightnessValue = null, sensorTimestampNs = null,
                ),
            ),
        )
        assertEquals("still", line.text("source"))
        assertEquals("STILL_CAPTURE", line.text("captureIntent"))
        assertEquals(0.0, line.number("focusDistanceDiopters"), 0.0)
        for (key in listOf("activePhysicalId", "zoomRatio", "focusDistanceM", "afState", "aperture", "ev100", "brightnessKey", "brightnessValue", "sensorTimestampNs")) {
            assertTrue("$key is null: $line", line.isNull(key))
        }
    }

    @Test
    fun `ev100 is log2 of N squared over t, less log2 of ISO over 100`() {
        assertEquals(5.6029, SensorWatchLines.ev100(1.8f, 16_666_666L, 400)!!, 1e-3)
        assertEquals("f/1 at 1 s, ISO 100 is EV 0", 0.0, SensorWatchLines.ev100(1f, 1_000_000_000L, 100)!!, 1e-9)
        assertEquals(null, SensorWatchLines.ev100(null, 16_666_666L, 400))
        assertEquals(null, SensorWatchLines.ev100(1.8f, 0L, 400))
        assertEquals(null, SensorWatchLines.ev100(1.8f, 16_666_666L, 0))
    }

    @Test
    fun `the switch line carries from, to, what changed and the before and after values`() {
        val after = reading.copy(activePhysicalId = "2", zoomRatio = 0.6f, iso = 800, sensorTimestampNs = 200L)
        val line = parse(SensorWatchLines.switchLine(SwitchEvent(from = "5", to = "2", before = reading, after = after)))

        assertEquals("switch", line.text("kind"))
        assertEquals("5", line.text("from"))
        assertEquals("2", line.text("to"))
        assertEquals(JsonArray(listOf(JsonPrimitive("zoom"), JsonPrimitive("exposure"))), line["changed"])
        assertEquals(1.0, line.number("zoomFrom"), 1e-6)
        assertEquals(0.6, line.number("zoomTo"), 1e-6)
        assertEquals(4.0, line.number("focusFrom"), 1e-6)
        assertEquals(4.0, line.number("focusTo"), 1e-6)
        assertEquals(16_666_666.0, line.number("exposureFrom"), 0.0)
        assertEquals(16_666_666.0, line.number("exposureTo"), 0.0)
        assertEquals(400.0, line.number("isoFrom"), 0.0)
        assertEquals(800.0, line.number("isoTo"), 0.0)
        assertEquals(200.0, line.number("sensorTimestampNs"), 0.0)
    }

    @Test
    fun `the summary line carries frames per camera as an object of counts`() {
        val summary = WatchSummary(
            framesSeen = 300, previewFramesLogged = 50, previewFramesSkipped = 248, stills = 2, switches = 3,
            framesByPhysicalId = linkedMapOf("5" to 200, "2" to 90), framesWithoutId = 10, firstPhysicalId = "5",
            lastPhysicalId = "2", zoomMin = 0.6f, zoomMax = 1f, spanMs = 10_000.5,
        )
        val line = parse(SensorWatchLines.summaryLine(summary, reason = "turned off by broadcast", notes = listOf("2 results had no \"platform\" result")))

        assertEquals("summary", line.text("kind"))
        assertEquals("turned off by broadcast", line.text("reason"))
        assertEquals(300.0, line.number("framesSeen"), 0.0)
        assertEquals(50.0, line.number("previewFramesLogged"), 0.0)
        assertEquals(248.0, line.number("previewFramesSkipped"), 0.0)
        assertEquals(2.0, line.number("stills"), 0.0)
        assertEquals(3.0, line.number("switches"), 0.0)
        val byId = line["framesByPhysicalId"]!!.jsonObject
        assertEquals(listOf("5", "2"), byId.keys.toList())
        assertEquals(200.0, byId.number("5"), 0.0)
        assertEquals(90.0, byId.number("2"), 0.0)
        assertEquals(10.0, line.number("framesWithoutId"), 0.0)
        assertEquals("5", line.text("firstPhysicalId"))
        assertEquals("2", line.text("lastPhysicalId"))
        assertEquals(0.6, line.number("zoomMin"), 1e-6)
        assertEquals(1.0, line.number("zoomMax"), 1e-6)
        assertEquals(10_000.5, line.number("spanMs"), 1e-9)
        assertEquals("2 results had no \"platform\" result", (line["notes"] as JsonArray).single().jsonPrimitive.content)
    }

    @Test
    fun `a zoom step line carries the ask, what was applied, the range and the settled sensor`() {
        val line = parse(
            SensorWatchLines.zoomLine(
                mode = "step", index = 3, requested = 2f, applied = 2f, clamped = false, range = ZoomRange(0.6f, 10f),
                outcome = "applied", reason = null, settled = reading.copy(activePhysicalId = "6", zoomRatio = 2f),
                settledAgeMs = 12.5, switchesDuringHold = 1,
            ),
        )
        assertEquals("zoom", line.text("kind"))
        assertEquals("step", line.text("mode"))
        assertEquals(3.0, line.number("index"), 0.0)
        assertEquals(2.0, line.number("requested"), 1e-6)
        assertEquals(2.0, line.number("applied"), 1e-6)
        assertEquals("false", line.text("clamped"))
        assertEquals(0.6, line.number("rangeMin"), 1e-6)
        assertEquals(10.0, line.number("rangeMax"), 1e-6)
        assertEquals("applied", line.text("outcome"))
        assertTrue(line.isNull("reason"))
        assertEquals("6", line.text("settledActivePhysicalId"))
        assertEquals(2.0, line.number("settledZoomRatio"), 1e-6)
        assertEquals(4.0, line.number("settledFocusDistanceDiopters"), 1e-6)
        assertEquals(16_666_666.0, line.number("settledExposureTimeNs"), 0.0)
        assertEquals(400.0, line.number("settledIso"), 0.0)
        assertEquals(12.5, line.number("settledAgeMs"), 1e-9)
        assertEquals(1.0, line.number("switchesDuringHold"), 0.0)
    }

    @Test
    fun `a clamped set says so, and a skipped step has no settled reading`() {
        val clamped = parse(SensorWatchLines.zoomLine("set", null, 100f, 10f, true, ZoomRange(0.6f, 10f), "applied", null, null, null, null))
        assertEquals("true", clamped.text("clamped"))
        assertEquals(10.0, clamped.number("applied"), 1e-6)
        assertTrue(clamped.isNull("index"))

        val skipped = parse(SensorWatchLines.zoomLine("step", 11, 20f, null, null, ZoomRange(0.6f, 10f), "skipped", "outside the reported range 0.6 to 10.0", null, null, null))
        assertEquals("skipped", skipped.text("outcome"))
        assertTrue(skipped.isNull("applied"))
        assertTrue(skipped.isNull("settledActivePhysicalId"))
        assertTrue(skipped.isNull("settledAgeMs"))
    }

    @Test
    fun `the zoom end, watch and busy lines`() {
        val end = parse(SensorWatchLines.zoomEndLine("steps", returnedToOne = true, reason = null))
        assertEquals("zoom_end", end.text("kind"))
        assertEquals("steps", end.text("mode"))
        assertEquals("true", end.text("returnedToOne"))
        assertTrue(end.isNull("reason"))

        val watch = parse(SensorWatchLines.watchLine("on", null, "0", listOf("Preview", "ImageCapture RAW_JPEG"), listOf("a note")))
        assertEquals("watch", watch.text("kind"))
        assertEquals("on", watch.text("state"))
        assertEquals("0", watch.text("cameraId"))
        assertEquals(2, (watch["dialog"] as JsonArray).size)

        val busy = parse(SensorWatchLines.busyLine("zoom", "the pinning probe holds the camera"))
        assertEquals("busy", busy.text("kind"))
        assertEquals("zoom", busy.text("asked"))
    }

    @Test
    fun `the pin frame line names the attempt, the camera the frame came from and its colour pattern`() {
        val frame = PinFrame(
            index = 1, kind = "burst", sensorTimestampNs = 77L, activePhysicalId = "5", physicalResultIds = listOf("5"),
            attributedTo = "5", rawSize = "4000x3000", cfa = 1, error = null,
        )
        val line = parse(SensorWatchLines.pinFrameLine(PinAttempt.PHYSICAL_STREAM, frame))
        assertEquals("pin_frame", line.text("kind"))
        assertEquals("physical_stream", line.text("attempt"))
        assertEquals(1.0, line.number("index"), 0.0)
        assertEquals("burst", line.text("capture"))
        assertEquals(77.0, line.number("sensorTimestampNs"), 0.0)
        assertEquals("5", line.text("activePhysicalId"))
        assertEquals(JsonArray(listOf(JsonPrimitive("5"))), line["physicalResultIds"])
        assertEquals("5", line.text("attributedTo"))
        assertEquals("4000x3000", line.text("rawSize"))
        assertEquals("GRBG", line.text("cfa"))
        assertTrue(line.isNull("error"))
    }

    @Test
    fun `the pin attempt line counts frames and names every camera they came from`() {
        val frames = listOf(
            PinFrame(0, "single", 1L, "5", emptyList(), "5", "4000x3000", 1, null),
            PinFrame(1, "burst", 2L, "5", emptyList(), "5", "4000x3000", 1, null),
            PinFrame(2, "burst", null, null, emptyList(), null, null, null, "the RAW image did not arrive"),
        )
        val result = PinAttemptResult(
            attempt = PinAttempt.LOGICAL_CONTROL, openedId = "0", physicalStreamId = null, configured = true,
            sessionQuery = "supported", rawSize = "4000x3000", frames = frames, error = null, notes = listOf("AE settled"),
        )
        val line = parse(SensorWatchLines.pinAttemptLine(result, verdict = "main_wide"))
        assertEquals("pin_attempt", line.text("kind"))
        assertEquals("logical_control", line.text("attempt"))
        assertEquals("main_wide", line.text("verdict"))
        assertEquals("0", line.text("openedId"))
        assertTrue(line.isNull("physicalStreamId"))
        assertEquals("true", line.text("configured"))
        assertEquals("supported", line.text("sessionQuery"))
        assertEquals(3.0, line.number("framesAsked"), 0.0)
        assertEquals(2.0, line.number("framesArrived"), 0.0)
        assertEquals(JsonArray(listOf(JsonPrimitive("5"))), line["attributedTo"])
        assertTrue(line.isNull("error"))

        val skipped = parse(SensorWatchLines.pinSkippedLine(PinAttempt.PHYSICAL_DIRECT, "no main wide was found"))
        assertEquals("pin_attempt", skipped.text("kind"))
        assertEquals("skipped", skipped.text("verdict"))
        assertEquals("no main wide was found", skipped.text("error"))
        assertEquals(0.0, skipped.number("framesArrived"), 0.0)
    }

    @Test
    fun `the lens, choice, borrow, hand-back and end lines`() {
        val lens = parse(
            SensorWatchLines.lensLine(
                "physical",
                LensFacts("5", false, listOf(6.4f), listOf(9.6f, 7.2f), 1, "4000x3000", listOf("4000x3000"), listOf(0, 3), null),
            ),
        )
        assertEquals("lens", lens.text("kind"))
        assertEquals("physical", lens.text("role"))
        assertEquals("5", lens.text("cameraId"))
        assertEquals("false", lens.text("inCameraIdList"))
        assertEquals(6.4, (lens["focalLengths"] as JsonArray).single().jsonPrimitive.content.toDouble(), 1e-6)
        assertEquals("GRBG", lens.text("cfa"))
        assertEquals(2, (lens["capabilities"] as JsonArray).size)

        val choice = parse(SensorWatchLines.choiceLine("5", "matches the logical camera"))
        assertEquals("choice", choice.text("kind"))
        assertEquals("5", choice.text("mainWideId"))

        val borrow = parse(SensorWatchLines.pinBorrowLine(PinBorrow.Failed("CameraX did not let go", listOf("OPEN@1.0"))))
        assertEquals("pin_borrow", borrow.text("kind"))
        assertEquals("failed", borrow.text("outcome"))
        assertEquals("CameraX did not let go", borrow.text("reason"))

        val handback = parse(SensorWatchLines.pinHandbackLine(PinHandback(platformClosed = true, cameraxBack = false, detail = "not OPEN")))
        assertEquals("pin_handback", handback.text("kind"))
        assertEquals("true", handback.text("platformClosed"))
        assertEquals("false", handback.text("cameraxBack"))

        val end = parse(SensorWatchLines.pinEndLine(true, null, linkedMapOf("physical_stream" to "main_wide", "physical_direct" to "failed")))
        assertEquals("pin_end", end.text("kind"))
        assertEquals("main_wide", end["verdicts"]!!.jsonObject.text("physical_stream"))
        assertEquals("failed", end["verdicts"]!!.jsonObject.text("physical_direct"))
    }

    @Test
    fun `a reason with quotes and a line break still makes one valid JSON line`() {
        val line = SensorWatchLines.zoomEndLine("set", false, "the \"restore\" failed:\nTimeout")
        assertTrue("one line", !line.contains('\n'))
        assertEquals("the \"restore\" failed:\nTimeout", parse(line).text("reason"))
    }
}
