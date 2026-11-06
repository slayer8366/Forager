package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Test

/** Dispatch 2026-09-28-549, Amendment 1 (RECORD -557): the sighting bubble's accuracy note follows Units. */
class AccuracyLabelTest {

    @Test
    fun `accuracy reads in whole feet under imperial`() {
        // 12 / 0.3048 = 39.37
        assertEquals("±39 ft accuracy", accuracyLabel(12, UnitSystem.IMPERIAL))
    }

    @Test
    fun `accuracy reads in metres as before under metric`() {
        assertEquals("±12 m accuracy", accuracyLabel(12, UnitSystem.METRIC))
    }

    @Test
    fun `a missing accuracy is said plainly in both systems`() {
        assertEquals("Accuracy not reported", accuracyLabel(null, UnitSystem.METRIC))
        assertEquals("Accuracy not reported", accuracyLabel(null, UnitSystem.IMPERIAL))
    }
}
