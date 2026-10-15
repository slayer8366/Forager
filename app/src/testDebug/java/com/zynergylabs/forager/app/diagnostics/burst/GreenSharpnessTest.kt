package com.zynergylabs.forager.app.diagnostics.burst

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The probe's per-frame sharpness figure (dispatch 2026-10-15-06): the mean absolute green gradient on a downsampled green
 * plane. Each case is a synthetic mosaic whose answer is worked out by hand in the test, so a passing value is the
 * definition, not a figure read back from the code.
 *
 * The definition: each 2×2 cell's green is the mean of its two green photosites, each normalised by its own black level,
 * (x − black[site]) / (white − black[site]); cells are averaged in blocks of k × k; the figure is the mean of
 * |G(i+1, j) − G(i, j)| and |G(i, j+1) − G(i, j)| over every horizontal and vertical neighbour pair of blocks.
 *
 * Red and blue photosites are filled with an unrelated value throughout, so a figure that read them would be wrong.
 */
class GreenSharpnessTest {

    private val rggb = 0
    private val grbg = 1

    /** Builds a mosaic: [green] gives each green photosite's raw value from (x, y, siteIndex); red and blue get [other]. */
    private fun mosaic(width: Int, height: Int, cfa: Int, other: Int = 999, green: (x: Int, y: Int, site: Int) -> Int): ShortArray {
        val samples = ShortArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            val site = (y % 2) * 2 + (x % 2)
            val isGreen = when (cfa) {
                0, 3 -> site == 1 || site == 2
                1, 2 -> site == 0 || site == 3
                else -> false
            }
            samples[y * width + x] = (if (isGreen) green(x, y, site) else other).toShort()
        }
        return samples
    }

    private fun value(result: Sharpness): Double {
        assertTrue("expected a value, got $result", result is Sharpness.Value)
        return (result as Sharpness.Value).meanAbsGradient
    }

    @Test
    fun `a horizontal green ramp gives 2k times the slope over the range on each horizontal pair and nothing vertically`() {
        // 16x8 photosites: 8x4 cells; k = 2 gives a 4x2 grid. Green = 64 + 10x. A cell's two greens sit at x = 2c+1 and
        // x = 2c, so its green is 10(2c + 0.5)/959; a block of two cells differs from the next by 2*2*10/959 = 40/959.
        // Six horizontal pairs carry 40/959, four vertical pairs carry 0: mean = 6 * (40/959) / 10 = 24/959.
        val samples = mosaic(16, 8, rggb) { x, _, _ -> 64 + 10 * x }
        val result = greenSharpness(samples, 16, 8, rggb, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)
        assertEquals(24.0 / 959.0, value(result), 1e-12)
        assertEquals(4, (result as Sharpness.Value).gridWidth)
        assertEquals(2, result.gridHeight)
    }

    @Test
    fun `a ramp falling to the right counts the same as one rising, because the gradient is absolute`() {
        // Green = 64 + 10(15 - x): the mirror of the rising ramp, every block step -40/959. Its magnitude is the same
        // 24/959; a figure that summed signed steps would come out negative.
        val samples = mosaic(16, 8, rggb) { x, _, _ -> 64 + 10 * (15 - x) }
        assertEquals(24.0 / 959.0, value(greenSharpness(samples, 16, 8, rggb, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)), 1e-12)
    }

    @Test
    fun `a vertical green ramp is read through the vertical pairs`() {
        // Green = 64 + 10y; the same reasoning by rows: four vertical pairs of 40/959, six horizontal pairs of 0:
        // mean = 4 * (40/959) / 10 = 16/959.
        val samples = mosaic(16, 8, rggb) { _, y, _ -> 64 + 10 * y }
        assertEquals(16.0 / 959.0, value(greenSharpness(samples, 16, 8, rggb, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)), 1e-12)
    }

    @Test
    fun `the green photosites are found from the colour filter arrangement`() {
        // GRBG puts the greens at (0,0) and (1,1) of each cell: x = 2c and 2c+1, the same mean x as RGGB, so the same
        // ramp gives the same 24/959. Read from the RGGB positions instead, these would be the red and blue fillers.
        val samples = mosaic(16, 8, grbg) { x, _, _ -> 64 + 10 * x }
        assertEquals(24.0 / 959.0, value(greenSharpness(samples, 16, 8, grbg, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)), 1e-12)
    }

    @Test
    fun `a flat green field has no gradient at all`() {
        val samples = mosaic(16, 8, rggb) { _, _, _ -> 500 }
        assertEquals(0.0, value(greenSharpness(samples, 16, 8, rggb, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)), 0.0)
    }

    @Test
    fun `each green photosite is normalised by its own channel's black level`() {
        // Black levels in the camera's row-major 2x2 order: R 50, Gr 60, Gb 68, B 80. Each green = its own black + 10x,
        // so (x - black) is 10x exactly at both. Normalised: 10x/963 at Gr (x = 2c+1) and 10x/955 at Gb (x = 2c). A cell's
        // green rises by 10(1/963 + 1/955) per cell, a block of two by 20(1/963 + 1/955); mean = 6 * that / 10.
        val black = listOf(50f, 60f, 68f, 80f)
        val samples = mosaic(16, 8, rggb) { x, _, site -> black[site].toInt() + 10 * x }
        val expected = 6.0 * 20.0 * (1.0 / 963.0 + 1.0 / 955.0) / 10.0
        assertEquals(expected, value(greenSharpness(samples, 16, 8, rggb, black, 1023, cellsPerBlock = 2)), 1e-12)
    }

    @Test
    fun `samples are read as unsigned 16-bit values`() {
        // Green = 40000 + 100x, above Short.MAX_VALUE; black 4096, white 65535. Block step 2*2*100/61439 = 400/61439;
        // mean = 6 * that / 10 = 240/61439. Read as signed shorts these would be negative and the steps would not match.
        val samples = mosaic(16, 8, rggb, other = 50_000) { x, _, _ -> 40_000 + 100 * x }
        val result = greenSharpness(samples, 16, 8, rggb, listOf(4096f, 4096f, 4096f, 4096f), 65535, cellsPerBlock = 2)
        assertEquals(240.0 / 61439.0, value(result), 1e-12)
    }

    @Test
    fun `a sensor with no Bayer greens is reported unsupported with the arrangement it has`() {
        val samples = ShortArray(16 * 8)
        val mono = greenSharpness(samples, 16, 8, 5, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)
        assertTrue("MONO (5) must be unsupported, got $mono", mono is Sharpness.Unsupported)
        assertTrue((mono as Sharpness.Unsupported).reason, mono.reason.contains("5"))
        val unknown = greenSharpness(samples, 16, 8, null, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 2)
        assertTrue("an unread arrangement must be unsupported, got $unknown", unknown is Sharpness.Unsupported)
    }

    @Test
    fun `a frame too small for two blocks in either direction is reported unsupported`() {
        val samples = mosaic(8, 8, rggb) { x, _, _ -> 64 + 10 * x }
        val result = greenSharpness(samples, 8, 8, rggb, listOf(64f, 64f, 64f, 64f), 1023, cellsPerBlock = 4)
        assertTrue("4x4 cells in blocks of 4 is one block: no neighbour pair, got $result", result is Sharpness.Unsupported)
    }
}
