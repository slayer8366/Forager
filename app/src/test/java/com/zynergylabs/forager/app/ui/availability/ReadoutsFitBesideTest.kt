package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * RECORD -713 (the owner: "Drop labels, then values (Recommended)"): coordinates whole first; then every readout with its
 * label, else every readout without labels, else facing dropped, then altitude; whatever is shown is whole. Widths in px:
 * coordinates 200, each separator 20, labels 40 (facing) and 30 (altitude), values 60 and 50.
 */
class ReadoutsFitBesideTest {

    private fun fit(available: Int, withCoordinates: Boolean = true) = readoutsFitBeside(
        availablePx = available,
        coordinatesPx = if (withCoordinates) 200 else 0,
        labelsPx = listOf(40, 30),
        valuesPx = listOf(60, 50),
        separatorPx = 20,
        withCoordinates = withCoordinates,
    )

    private val all = 200 + 2 * 20 + 40 + 60 + 30 + 50 // 420

    @Test
    fun `room for everything keeps the labels and both readouts`() =
        assertEquals(ReadoutsFit(labels = true, shown = listOf(true, true)), fit(all))

    @Test
    fun `one px short drops both labels first, and both values stay whole`() =
        assertEquals(ReadoutsFit(labels = false, shown = listOf(true, true)), fit(all - 1))

    @Test
    fun `short of both values without labels drops facing, and altitude stays`() {
        val bothValues = 200 + 2 * 20 + 60 + 50 // 350
        assertEquals(ReadoutsFit(labels = false, shown = listOf(true, true)), fit(bothValues))
        assertEquals(ReadoutsFit(labels = false, shown = listOf(false, true)), fit(bothValues - 1))
    }

    @Test
    fun `short of altitude alone drops it too, and the coordinates stay`() {
        val altitudeOnly = 200 + 20 + 50 // 270
        assertEquals(ReadoutsFit(labels = false, shown = listOf(false, true)), fit(altitudeOnly))
        assertEquals(ReadoutsFit(labels = false, shown = listOf(false, false)), fit(altitudeOnly - 1))
    }

    @Test
    fun `with no coordinates a separator sits only between readouts`() {
        assertEquals(ReadoutsFit(labels = true, shown = listOf(true, true)), fit(20 + 40 + 60 + 30 + 50, withCoordinates = false))
        assertEquals(ReadoutsFit(labels = false, shown = listOf(true, true)), fit(20 + 40 + 60 + 30 + 50 - 1, withCoordinates = false))
    }

    @Test
    fun `a readout with no label costs nothing for it`() {
        val noLabels = readoutsFitBeside(availablePx = 200 + 40 + 60 + 50, coordinatesPx = 200, labelsPx = listOf(0, 0), valuesPx = listOf(60, 50), separatorPx = 20)
        assertEquals(ReadoutsFit(labels = true, shown = listOf(true, true)), noLabels)
    }
}
