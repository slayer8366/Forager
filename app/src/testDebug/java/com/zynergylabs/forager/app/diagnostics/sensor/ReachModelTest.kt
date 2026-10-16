package com.zynergylabs.forager.app.diagnostics.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reach probe's plan, roles, verdicts, burst rate and per-phone table (dispatch 2026-10-16-01: "The enumeration and
 * summary formats"), over a phone shaped like the S26 ([ReachFixtures]).
 */
class ReachModelTest {

    private val s26 = ReachFixtures.s26

    // ── Plan ────────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the plan covers every back camera in turn, through each logical camera, directly, then the pairs`() {
        val plan = ReachPlan.plan(s26)
        assertEquals(ReachFixtures.s26Labels, plan.map { it.label })
        val runs = plan.filterIsInstance<PlannedReach.Run>().map { it.attempt }
        assertEquals(ReachAttempt.ThroughLogical("0", "5"), runs.single { it.label == "through_logical_0_5" })
        assertEquals(ReachAttempt.Direct("2"), runs.single { it.label == "direct_2" })
        assertEquals(ReachAttempt.TwoStreams("0", "2", "5", "4080x3060"), runs.single { it.label == "pair_0_2_5" })
    }

    @Test
    fun `a camera not in getCameraIdList is not opened directly, and the reason names the list`() {
        val skip = ReachPlan.plan(s26).single { it.label == "direct_5" } as PlannedReach.Skip
        assertEquals("5", skip.cameraId)
        assertEquals("direct", skip.method)
        assertTrue(skip.reason, skip.reason.contains("not in getCameraIdList [0, 1, 2, 3]"))
    }

    @Test
    fun `a pair whose cameras share no RAW size is not attempted, and the reason gives both cameras' sizes`() {
        val skip = ReachPlan.plan(s26).single { it.label == "pair_0_5_6" } as PlannedReach.Skip
        assertEquals("pair", skip.method)
        assertTrue(skip.reason, skip.reason.contains("[4080x3060]") && skip.reason.contains("[3648x2736]"))
        assertTrue(skip.reason, skip.reason.contains("identical type and size"))
    }

    @Test
    fun `front cameras are enumerated but no attempt is planned on them`() {
        val plan = ReachPlan.plan(s26)
        assertTrue(plan.none { it.cameraId == "1" || it.cameraId == "3" })
    }

    @Test
    fun `a camera whose facing could not be read is not attempted, and says why`() {
        val unread = s26.copy(cameras = s26.cameras + CameraEntry("9", true, null, emptyList(), null, null, null, emptyList(), emptyList(), emptyList(), emptyList(), error = "IllegalArgumentException"))
        val skip = ReachPlan.plan(unread).single { it.cameraId == "9" } as PlannedReach.Skip
        assertTrue(skip.reason, skip.reason.contains("facing") && skip.reason.contains("IllegalArgumentException"))
    }

    @Test
    fun `the RAW size chosen is the largest at or under the operating limit`() {
        assertEquals("4080x3060", ReachPlan.largestWithin(listOf("8160x6120", "4080x3060", "2040x1530")))
        assertEquals("2040x1530", ReachPlan.largestWithin(listOf("4080x3060", "2040x1530"), limit = 4_000_000L))
        assertNull(ReachPlan.largestWithin(listOf("8160x6120")))
        assertNull("a size that does not parse is never chosen", ReachPlan.largestWithin(listOf("big", "4080by3060")))
        assertNull(ReachPlan.largestWithin(emptyList()))
    }

    // ── Roles ───────────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `roles come from the characteristics, each back camera's field of view against the main wide's`() {
        assertEquals(
            mapOf(
                "0" to "logical", "1" to "front", "2" to "ultra_wide_0.6x", "3" to "front",
                "5" to "main_wide", "6" to "telephoto_2.9x", "7" to "telephoto_4.9x",
            ),
            ReachRoles.assign(s26),
        )
    }

    @Test
    fun `with no main wide to measure against, a back camera is only back`() {
        val noLogical = s26.copy(cameras = s26.cameras.filter { it.cameraId != "0" }.map { it.copy(logicalIds = emptyList()) })
        assertEquals("back", ReachRoles.assign(noLogical)["2"])
    }

    // ── Verdicts ────────────────────────────────────────────────────────────────────────────────────────────────────

    private val direct = ReachAttempt.Direct("2")
    private val reached = ReachFixtures.reached(direct)

    @Test
    fun `each outcome has its own verdict, and none is guessed`() {
        assertEquals("reachable_raw", ReachVerdict.of(reached))
        assertEquals("refused", ReachVerdict.of(ReachFixtures.refused(direct, "2")))
        assertEquals(
            "the session query said no",
            "unsupported",
            ReachVerdict.of(reached.copy(sessionQuery = "not supported", configured = null, frames = emptyList(), stoppedAt = ReachStage.QUERY, error = "the session query said not supported")),
        )
        assertEquals(
            "a session the camera would not configure is a refusal",
            "refused",
            ReachVerdict.of(reached.copy(configured = false, frames = emptyList(), stoppedAt = ReachStage.CONFIGURE, error = "onConfigureFailed")),
        )
        assertEquals("no RAW size offered", "reachable_no_raw", ReachVerdict.of(reached.copy(rawSize = null, frames = emptyList())))
        assertEquals(
            "configured, but no RAW frame arrived",
            "reachable_no_raw",
            ReachVerdict.of(reached.copy(frames = reached.frames.map { it.copy(sensorTimestampNs = null, error = "the RAW image did not arrive within 3000 ms") })),
        )
        assertEquals("timed_out", ReachVerdict.of(reached.copy(timedOut = true, frames = emptyList())))
        assertEquals("failed", ReachVerdict.of(reached.copy(opened = false, stoppedAt = ReachStage.CHARACTERISTICS, frames = emptyList(), error = "unreadable")))
    }

    @Test
    fun `a pair reaches RAW only when both of its streams delivered`() {
        val pair = ReachFixtures.reached(ReachAttempt.TwoStreams("0", "2", "5", "4080x3060"))
        assertEquals("reachable_raw", ReachVerdict.of(pair))
        assertEquals("reachable_no_raw", ReachVerdict.of(pair.copy(frames = pair.frames.filter { it.streamCameraId == "5" })))
    }

    // ── Burst rate ──────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the burst rate is frames less one over the first-to-last sensor time`() {
        val rate = ReachSummary.burstRate(listOf(ReachFixtures.single("5")) + ReachFixtures.burst("5"))!!
        assertEquals(8, rate.frames)
        assertEquals(233.333, rate.spanMs, 1e-3)
        assertEquals(30.0, rate.framesPerSecond, 1e-3)
    }

    @Test
    fun `frames that did not arrive are not counted, and fewer than two give no rate`() {
        val burst = ReachFixtures.burst("5")
        val lost = burst.mapIndexed { i, f -> if (i >= 4) f.copy(sensorTimestampNs = null, error = "lost") else f }
        assertEquals(4, ReachSummary.burstRate(lost)!!.frames)
        assertNull(ReachSummary.burstRate(burst.take(1)))
        assertNull("a single is not a burst", ReachSummary.burstRate(listOf(ReachFixtures.single("5"))))
    }

    // ── Rows and table ──────────────────────────────────────────────────────────────────────────────────────────────

    /** The S26 as the pin probe found it: 5 reached through camera 0, its direct open refused; the rest by the fixture. */
    private fun outcomes(): List<ReachOutcome> = ReachPlan.plan(s26).map { planned ->
        when (planned) {
            is PlannedReach.Skip -> ReachOutcome(planned, ReachVerdict.NOT_ATTEMPTED, null)
            is PlannedReach.Run -> {
                val result = when (planned.label) {
                    "through_logical_0_6" -> ReachFixtures.refused(planned.attempt, "0")
                    else -> ReachFixtures.reached(planned.attempt)
                }
                ReachOutcome(planned, ReachVerdict.of(result), result)
            }
        }
    }

    @Test
    fun `one row per back camera and one per pair, with how it was reached, its RAW size and its burst rate`() {
        val rows = ReachSummary.rows(s26, ReachRoles.assign(s26), outcomes())
        assertEquals(listOf("0", "2", "5", "6", "7", "2+5", "2+6", "2+7", "5+6", "5+7", "6+7"), rows.map { it.cameraId })
        val five = rows.single { it.cameraId == "5" }
        assertEquals("main_wide", five.role)
        assertEquals(listOf("through_logical_0" to "reachable_raw", "direct" to "not_attempted"), five.outcomes)
        assertEquals(listOf("through_logical_0"), five.reachableRaw)
        assertEquals("4080x3060", five.rawSize)
        assertEquals(30.0, five.burstFps!!, 1e-3)
        val six = rows.single { it.cameraId == "6" }
        assertEquals(emptyList<String>(), six.reachableRaw)
        assertNull(six.rawSize)
        assertNull(six.burstFps)
        val pair = rows.single { it.cameraId == "2+5" }
        assertEquals("pair_on_0", pair.role)
        assertEquals(listOf("pair"), pair.reachableRaw)
        assertNull("a pair takes one frame, so it has no burst rate", pair.burstFps)
    }

    @Test
    fun `the table, exactly`() {
        val table = ReachSummary.table("samsung SM-S948U", ReachSummary.rows(s26, ReachRoles.assign(s26), outcomes()))
        assertEquals(
            listOf(
                "samsung SM-S948U",
                "camera | role | reachable how | RAW size | frame rate",
                "0 | logical | direct | 4080x3060 | 30.0 fps",
                "2 | ultra_wide_0.6x | through_logical_0, direct | 4080x3060 | 30.0 fps",
                "5 | main_wide | through_logical_0 (direct: not_attempted) | 4080x3060 | 30.0 fps",
                "6 | telephoto_2.9x | none (through_logical_0: refused; direct: not_attempted) | - | -",
                "7 | telephoto_4.9x | through_logical_0 (direct: not_attempted) | 4080x3060 | 30.0 fps",
                "2+5 | pair_on_0 | pair | 4080x3060 | -",
                "2+6 | pair_on_0 | none (pair: not_attempted) | - | -",
                "2+7 | pair_on_0 | pair | 4080x3060 | -",
                "5+6 | pair_on_0 | none (pair: not_attempted) | - | -",
                "5+7 | pair_on_0 | pair | 4080x3060 | -",
                "6+7 | pair_on_0 | none (pair: not_attempted) | - | -",
            ).joinToString("\n"),
            table,
        )
    }
}
