package com.zynergylabs.forager.app.diagnostics.sensor

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reach probe's lines (dispatch 2026-10-16-01: "The enumeration and summary formats"). The planner builds each
 * phone's reach table from these, so the enumeration line is asserted exactly and the others are parsed as JSON with
 * their value types checked: a number written as a string, or a reading not made written as 0, would pass a "the text
 * appears" check and still break the table.
 */
class ReachLinesTest {

    private fun parse(line: String): JsonObject = Json.parseToJsonElement(line).jsonObject
    private fun JsonObject.text(key: String) = this[key]!!.jsonPrimitive.content
    private fun JsonObject.isNull(key: String) = this[key] == JsonNull
    private fun JsonObject.number(key: String): Double {
        val value = this[key]!!.jsonPrimitive
        assertTrue("$key is a JSON number, not a string: $value", !value.isString && value != JsonNull)
        return value.content.toDouble()
    }
    private fun JsonObject.texts(key: String) = (this[key] as JsonArray).map { it.jsonPrimitive.content }

    @Test
    fun `the camera line, exactly`() {
        val five = ReachFixtures.s26.cameras.single { it.cameraId == "5" }
        assertEquals(
            """{"kind":"reach_camera","cameraId":"5","inCameraIdList":false,"role":"main_wide","facing":"BACK","focalLengths":[6.5],""" +
                """"physicalSizeMm":[9.792,7.344],"cfa":"GBRG","pixelArray":"4080x3060","rawSizes":["4080x3060"],"capabilities":[0,3],""" +
                """"physicalIds":[],"logicalIds":["0"],"error":null}""",
            ReachLines.cameraLine(five, "main_wide"),
        )
    }

    @Test
    fun `a camera whose characteristics could not be read says so, with nulls, not zeros`() {
        val line = parse(ReachLines.cameraLine(CameraEntry("9", false, null, emptyList(), null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), error = "IllegalArgumentException"), null))
        for (key in listOf("role", "facing", "physicalSizeMm", "cfa", "pixelArray")) assertTrue("$key is null: $line", line.isNull(key))
        assertEquals("IllegalArgumentException", line.text("error"))
    }

    @Test
    fun `the frame line carries the active physical camera and the per-physical result IDs`() {
        val frame = ReachFrame(3, "burst", "5", 1_150_738_122_103_079L, "5", listOf("2", "5"), "4080x3060", null)
        val line = parse(ReachLines.frameLine(ReachAttempt.ThroughLogical("0", "5"), frame))
        assertEquals("reach_frame", line.text("kind"))
        assertEquals("through_logical_0_5", line.text("label"))
        assertEquals(3.0, line.number("index"), 0.0)
        assertEquals("burst", line.text("capture"))
        assertEquals("5", line.text("streamCameraId"))
        assertEquals(1_150_738_122_103_079.0, line.number("sensorTimestampNs"), 0.0)
        assertEquals("5", line.text("activePhysicalId"))
        assertEquals(listOf("2", "5"), line.texts("physicalResultIds"))
        assertTrue(line.isNull("error"))
    }

    @Test
    fun `the attempt line gives the session query, whether it configured, the single, the burst and its rate`() {
        val result = ReachFixtures.reached(ReachAttempt.ThroughLogical("0", "5"))
        val line = parse(ReachLines.attemptLine(result, "reachable_raw"))
        assertEquals("reach_attempt", line.text("kind"))
        assertEquals("through_logical_0_5", line.text("label"))
        assertEquals("through_logical_0", line.text("method"))
        assertEquals("reachable_raw", line.text("verdict"))
        assertEquals("0", line.text("openedId"))
        assertEquals(listOf("5"), line.texts("streamIds"))
        assertEquals("supported", line.text("sessionQuery"))
        assertEquals("true", line.text("configured"))
        assertEquals("true", line.text("opened"))
        assertEquals(1.0, line.number("singleArrived"), 0.0)
        assertEquals(8.0, line.number("burstAsked"), 0.0)
        assertEquals(8.0, line.number("burstArrived"), 0.0)
        assertEquals(30.0, line.number("burstFps"), 1e-3)
        assertEquals(233.333, line.number("burstSpanMs"), 1e-3)
        assertEquals(listOf("5"), line.texts("activePhysicalIds"))
        assertTrue(line.isNull("stoppedAt"))
        assertTrue(line.isNull("error"))
    }

    @Test
    fun `a refused attempt's line names where it stopped and why, and its burst readings are null`() {
        val line = parse(ReachLines.attemptLine(ReachFixtures.refused(ReachAttempt.Direct("5"), "5"), "refused"))
        assertEquals("refused", line.text("verdict"))
        assertEquals("open", line.text("stoppedAt"))
        assertTrue(line.text("error"), line.text("error").contains("CameraAccessException"))
        assertEquals("false", line.text("opened"))
        assertTrue(line.isNull("configured"))
        assertTrue(line.isNull("burstFps"))
        assertTrue(line.isNull("burstSpanMs"))
        assertEquals(0.0, line.number("burstArrived"), 0.0)
    }

    @Test
    fun `a step not made is an attempt line with its reason`() {
        val skip = ReachPlan.plan(ReachFixtures.s26).single { it.label == "direct_5" } as PlannedReach.Skip
        val line = parse(ReachLines.skippedLine(skip))
        assertEquals("reach_attempt", line.text("kind"))
        assertEquals("direct_5", line.text("label"))
        assertEquals("direct", line.text("method"))
        assertEquals("not_attempted", line.text("verdict"))
        assertEquals(skip.reason, line.text("error"))
        assertTrue(line.isNull("opened"))
    }

    @Test
    fun `the row line is typed, and the table line carries the whole table as text`() {
        val row = ReachRow("5", "main_wide", listOf("through_logical_0" to "reachable_raw", "direct" to "not_attempted"), listOf("through_logical_0"), "4080x3060", 29.93)
        val line = parse(ReachLines.rowLine(row))
        assertEquals("reach_row", line.text("kind"))
        assertEquals("5", line.text("cameraId"))
        assertEquals("main_wide", line.text("role"))
        assertEquals(listOf("through_logical_0"), line.texts("reachableRaw"))
        assertEquals("not_attempted", line["outcomes"]!!.jsonObject.text("direct"))
        assertEquals("4080x3060", line.text("rawSize"))
        assertEquals(29.93, line.number("burstFps"), 1e-3)
        assertTrue(parse(ReachLines.rowLine(row.copy(rawSize = null, burstFps = null))).isNull("burstFps"))

        val table = "S26\ncamera | role\n5 | main_wide"
        val tableLine = parse(ReachLines.tableLine("samsung SM-S948U", table))
        assertEquals("reach_table", tableLine.text("kind"))
        assertEquals("samsung SM-S948U", tableLine.text("device"))
        assertEquals("the table survives the round trip, newlines and all", table, tableLine.text("table"))
    }

    @Test
    fun `the plan, start, end, borrow and hand-back lines`() {
        val plan = parse(ReachLines.planLine(ReachPlan.plan(ReachFixtures.s26)))
        assertEquals("reach_plan", plan.text("kind"))
        assertEquals(9, plan.texts("run").size)
        assertEquals(6, plan.texts("notAttempted").size)
        val start = parse(ReachLines.startLine(8, 45_000L))
        assertEquals(8.0, start.number("burstFrames"), 0.0)
        assertEquals(45_000.0, start.number("attemptTimeoutMs"), 0.0)
        val end = parse(ReachLines.endLine(false, "the probe was cancelled", mapOf("direct_0" to "timed_out")))
        assertEquals("false", end.text("completed"))
        assertEquals("timed_out", end["verdicts"]!!.jsonObject.text("direct_0"))
        assertEquals("failed", parse(ReachLines.borrowLine(PinBorrow.Failed("no", emptyList()))).text("outcome"))
        val back = parse(ReachLines.handbackLine(PinHandback(true, false, "CameraX did not read OPEN")))
        assertEquals("reach_handback", back.text("kind"))
        assertEquals("false", back.text("cameraxBack"))
    }
}
