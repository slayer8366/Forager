package com.forager.app.ui.motion

import androidx.compose.animation.core.TweenSpec
import org.junit.Assert.assertTrue
import org.junit.Test

/** Table-driven checks that MotionTokens.kt stays inside docs/motion-spec.md §2's ranges. */
class MotionTokensTest {

    @Test
    fun `feedback motion duration is within the 150 to 400 ms range`() {
        assertTrue(
            "FEEDBACK_MOTION_DURATION_MS=${MotionTokens.FEEDBACK_MOTION_DURATION_MS} outside 150..400",
            MotionTokens.FEEDBACK_MOTION_DURATION_MS in 150..400,
        )
    }

    @Test
    fun `narrative reveal duration is within the 800 to 1200 ms range`() {
        assertTrue(
            "NARRATIVE_REVEAL_DURATION_MS=${MotionTokens.NARRATIVE_REVEAL_DURATION_MS} outside 800..1200",
            MotionTokens.NARRATIVE_REVEAL_DURATION_MS in 800..1200,
        )
    }

    @Test
    fun `selection pulse scale range is low-amplitude`() {
        // "low-amplitude breathing pulse" -- guards against a future edit turning this into a
        // large, alarm-style scale swing.
        val amplitude = MotionTokens.SELECTION_PULSE_MAX_SCALE - MotionTokens.SELECTION_PULSE_MIN_SCALE
        assertTrue("selection pulse amplitude $amplitude is not low-amplitude", amplitude in 0.0f..0.15f)
    }

    @Test
    fun `all named specs are tween-based, never spring, so panels stay free of overshoot`() {
        val specs = listOf(
            "feedbackMotionSpec" to MotionTokens.feedbackMotionSpec,
            "narrativeRevealSpec" to MotionTokens.narrativeRevealSpec,
            "markerEntranceSpec" to MotionTokens.markerEntranceSpec,
            "selectionPulseHalfCycleSpec" to MotionTokens.selectionPulseHalfCycleSpec,
            "locationIndicatorMoveSpec" to MotionTokens.locationIndicatorMoveSpec,
            "routeRecalculationMorphSpec" to MotionTokens.routeRecalculationMorphSpec,
            "panelMotionSpec" to MotionTokens.panelMotionSpec,
            "dataLayerOverlaySpec" to MotionTokens.dataLayerOverlaySpec,
        )
        for ((name, spec) in specs) {
            assertTrue("$name is not a TweenSpec (spring-based specs can overshoot)", spec is TweenSpec<Float>)
        }
    }

    @Test
    fun `panel motion spec duration matches the named constant`() {
        val spec = MotionTokens.panelMotionSpec as TweenSpec<Float>
        assertTrue(spec.durationMillis == MotionTokens.PANEL_MOTION_DURATION_MS)
    }
}
