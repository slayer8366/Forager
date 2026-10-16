package com.zynergylabs.forager.app.diagnostics.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zoom clamp and the step list (dispatch 2026-10-15-12, Part 2: "clamped to the camera's reported zoom range and
 * logged as clamped"; the list "as far as the range allows"). Both test phones report 0.6 to 10 for camera 0 (the dumpsys
 * captures, `android.control.zoomRatioRange`), so the range here is theirs.
 */
class ZoomPlanTest {

    private val phones = ZoomRange(0.6f, 10f)

    @Test
    fun `a ratio inside the range is applied as asked and not marked clamped`() {
        assertEquals(ZoomClamp.Valid(requested = 2.9f, applied = 2.9f, clamped = false), ZoomPlan.clamp(2.9f, phones))
        assertEquals(ZoomClamp.Valid(requested = 0.6f, applied = 0.6f, clamped = false), ZoomPlan.clamp(0.6f, phones))
        assertEquals(ZoomClamp.Valid(requested = 10f, applied = 10f, clamped = false), ZoomPlan.clamp(10f, phones))
    }

    @Test
    fun `a ratio above the range is clamped to its top and marked clamped`() {
        assertEquals(ZoomClamp.Valid(requested = 100f, applied = 10f, clamped = true), ZoomPlan.clamp(100f, phones))
    }

    @Test
    fun `a ratio below the range is clamped to its bottom and marked clamped`() {
        assertEquals(ZoomClamp.Valid(requested = 0.5f, applied = 0.6f, clamped = true), ZoomPlan.clamp(0.5f, phones))
    }

    @Test
    fun `something that is not a ratio is rejected, never clamped into one`() {
        for (bad in listOf(Float.NaN, 0f, -1f, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            val outcome = ZoomPlan.clamp(bad, phones)
            assertTrue("$bad is rejected: $outcome", outcome is ZoomClamp.Rejected)
        }
    }

    @Test
    fun `the default list is the dispatch's, and on these phones the steps above 10 are skipped`() {
        assertEquals(listOf(0.6f, 1f, 1.5f, 2f, 2.9f, 3f, 3.1f, 5f, 9.9f, 10f, 10.1f, 20f, 30f, 100f), ZoomPlan.DEFAULT_STEPS)
        val steps = ZoomPlan.steps(ZoomPlan.DEFAULT_STEPS, phones)

        assertEquals((0 until 14).toList(), steps.map { it.index })
        assertEquals(ZoomPlan.DEFAULT_STEPS, steps.map { it.requested })
        assertEquals(listOf(0.6f, 1f, 1.5f, 2f, 2.9f, 3f, 3.1f, 5f, 9.9f, 10f), steps.filter { it.inRange }.map { it.requested })
        assertEquals(listOf(10.1f, 20f, 30f, 100f), steps.filter { !it.inRange }.map { it.requested })
    }

    @Test
    fun `on a camera whose range starts at 1, the 0_6 step is skipped too`() {
        val steps = ZoomPlan.steps(ZoomPlan.DEFAULT_STEPS, ZoomRange(1f, 8f))
        assertEquals(listOf(1f, 1.5f, 2f, 2.9f, 3f, 3.1f, 5f), steps.filter { it.inRange }.map { it.requested })
    }

    @Test
    fun `the hold defaults when not asked for and is kept between half a second and two minutes`() {
        assertEquals(3_000L, ZoomPlan.hold(null, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS))
        assertEquals(15_000L, ZoomPlan.hold(null, ZoomPlan.DEFAULT_SET_HOLD_MILLIS))
        assertEquals(5_000L, ZoomPlan.hold(5_000L, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS))
        assertEquals(500L, ZoomPlan.hold(10L, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS))
        assertEquals(500L, ZoomPlan.hold(-3L, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS))
        assertEquals(120_000L, ZoomPlan.hold(1_000_000_000L, ZoomPlan.DEFAULT_STEP_HOLD_MILLIS))
    }
}
