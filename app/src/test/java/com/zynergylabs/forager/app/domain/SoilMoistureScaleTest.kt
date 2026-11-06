package com.zynergylabs.forager.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The plain soil moisture scale (data part C, dispatch -668), at and either side of each proposed
 * threshold. The thresholds themselves are a stop for the owner; these tests hold whichever values
 * the constants carry, by reading the constants rather than restating them.
 */
class SoilMoistureScaleTest {

    @Test
    fun `below the dry threshold is Dry, and the threshold itself is Moist`() {
        assertEquals(SoilMoistureLevel.DRY, SoilMoistureScale.levelOf(0.0))
        assertEquals(SoilMoistureLevel.DRY, SoilMoistureScale.levelOf(SoilMoistureScale.DRY_BELOW_M3M3 - 0.001))
        assertEquals(SoilMoistureLevel.MOIST, SoilMoistureScale.levelOf(SoilMoistureScale.DRY_BELOW_M3M3))
    }

    @Test
    fun `below the wet threshold is Moist, and the threshold itself and above is Wet`() {
        assertEquals(SoilMoistureLevel.MOIST, SoilMoistureScale.levelOf(SoilMoistureScale.WET_FROM_M3M3 - 0.001))
        assertEquals(SoilMoistureLevel.WET, SoilMoistureScale.levelOf(SoilMoistureScale.WET_FROM_M3M3))
        assertEquals(SoilMoistureLevel.WET, SoilMoistureScale.levelOf(0.5))
    }

    @Test
    fun `the proposed thresholds are the ones the report names`() {
        // Pinned so a change to either is a deliberate edit to this line as well, and so the
        // figures the owner is asked about are the figures the code carries.
        assertEquals(0.15, SoilMoistureScale.DRY_BELOW_M3M3, 0.0)
        assertEquals(0.30, SoilMoistureScale.WET_FROM_M3M3, 0.0)
    }
}
