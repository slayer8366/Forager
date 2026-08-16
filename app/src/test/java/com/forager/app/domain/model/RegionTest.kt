package com.forager.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RegionTest {

    @Test
    fun `clamps radius below the minimum up to 1km`() {
        assertEquals(1, Region.clampRadiusKm(0))
        assertEquals(1, Region.clampRadiusKm(-5))
    }

    @Test
    fun `clamps radius above the maximum down to 50km`() {
        assertEquals(50, Region.clampRadiusKm(500))
    }

    @Test
    fun `leaves an in-range radius unchanged`() {
        assertEquals(15, Region.clampRadiusKm(15))
    }
}
