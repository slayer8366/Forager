package com.forager.app.ui.map

import com.forager.app.ui.motion.MotionTokens
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [locationIndicatorTrackingAnimationMultiplier] computes the
 * `LocationComponentOptions.trackingAnimationDurationMultiplier` value
 * [com.forager.app.ui.map.SightingsMap] passes to MapLibre's blue-dot puck, per
 * docs/motion-spec.md §2 "User location". Plain-value test, not a MapLibre integration test —
 * see [SightingsMapOverlayDataTest]'s doc comment for why constructing the real, native-backed
 * `LocationComponentOptions` isn't possible outside a device/emulator.
 */
class LocationIndicatorMotionTest {

    @Test
    fun `multiplier scales MotionTokens' duration against MapLibre's fixed base`() {
        val expected = MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS / LOCATION_COMPONENT_BASE_ANIMATION_DURATION_MS
        assertEquals(expected, locationIndicatorTrackingAnimationMultiplier(), 0.0001f)
    }

    @Test
    fun `multiplier is positive and reasonably close to MapLibre's own default of 1_0`() {
        // Not asserting a specific magic number here: this just guards against the computation
        // producing something pathological (zero, negative, or wildly off MapLibre's own
        // baseline) if MotionTokens.LOCATION_INDICATOR_MOVE_DURATION_MS is edited later.
        val multiplier = locationIndicatorTrackingAnimationMultiplier()
        assertEquals(true, multiplier > 0f)
        assertEquals(true, multiplier < 2f)
    }
}
