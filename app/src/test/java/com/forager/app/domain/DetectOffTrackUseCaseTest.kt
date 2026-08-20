package com.forager.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectOffTrackUseCaseTest {

    private val useCase = DetectOffTrackUseCase()

    @Test
    fun `fewer than three readings is never enough to call a trend`() {
        assertFalse(useCase(emptyList()))
        assertFalse(useCase(listOf(100.0)))
        assertFalse(useCase(listOf(100.0, 200.0)))
    }

    @Test
    fun `a steadily decreasing distance is not off track`() {
        assertFalse(useCase(listOf(300.0, 200.0, 100.0)))
    }

    @Test
    fun `a flat distance within GPS jitter is not off track`() {
        assertFalse(useCase(listOf(100.0, 104.0, 108.0)))
    }

    @Test
    fun `a real net increase across the window is off track`() {
        assertTrue(useCase(listOf(100.0, 115.0, 140.0)))
    }

    @Test
    fun `only the most recent window matters, so an old downward trend can't mask a fresh upward one`() {
        // Net decrease over the full history, but the last three readings are climbing.
        assertTrue(useCase(listOf(500.0, 100.0, 105.0, 115.0, 140.0)))
    }

    @Test
    fun `a single spike back down clears an otherwise rising window`() {
        assertFalse(useCase(listOf(140.0, 115.0, 100.0)))
    }
}
