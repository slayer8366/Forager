package com.zynergylabs.forager.app.diagnostics.sensor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Part 3 needs the main wide's ID "read from characteristics and never hard-coded". The facts below are the two test
 * phones' own, read from the dumpsys captures (logical camera 0 and its physical cameras 5, 6 and 7; camera 2, the
 * ultra-wide, is a camera of its own with a different lens and is listed separately there).
 */
class MainWideChooserTest {

    private fun facts(id: String, focal: Float, w: Float, h: Float, cfa: Int, error: String? = null) = LensFacts(
        cameraId = id,
        inCameraIdList = id == "0",
        focalLengths = listOf(focal),
        physicalSizeMm = listOf(w, h),
        cfa = cfa,
        pixelArray = null,
        rawSizes = emptyList(),
        capabilities = emptyList(),
        error = error,
    )

    private val grbg = 1
    private val gbrg = 2

    @Test
    fun `on the first test phone the main wide is physical camera 5`() {
        val logical = facts("0", 6.4f, 9.6f, 7.2f, grbg)
        val physicals = listOf(
            facts("2", 2.2f, 9.6f, 7.2f, 0),
            facts("5", 6.4f, 9.6f, 7.2f, grbg),
            facts("6", 7.9f, 4.08576f, 3.06432f, grbg),
            facts("7", 27.2f, 4.08576f, 3.06432f, grbg),
        )
        val choice = MainWideChooser.choose(logical, physicals)
        assertEquals("5", (choice as MainWideChoice.Chosen).cameraId)
    }

    @Test
    fun `on the second test phone the main wide is physical camera 5 too`() {
        val logical = facts("0", 6.5f, 9.792f, 7.344f, gbrg)
        val physicals = listOf(
            facts("2", 2.2f, 9.792f, 7.344f, grbg),
            facts("5", 6.5f, 9.792f, 7.344f, gbrg),
            facts("6", 7.0f, 3.648f, 2.736f, gbrg),
            facts("7", 18.6f, 5.712f, 4.284f, grbg),
        )
        assertEquals("5", (MainWideChooser.choose(logical, physicals) as MainWideChoice.Chosen).cameraId)
    }

    @Test
    fun `a physical camera that matches in lens but not in colour pattern is not the main wide`() {
        val logical = facts("0", 6.4f, 9.6f, 7.2f, grbg)
        val choice = MainWideChooser.choose(logical, listOf(facts("5", 6.4f, 9.6f, 7.2f, gbrg)))
        assertTrue("no camera matches all three: $choice", choice is MainWideChoice.NotFound)
    }

    @Test
    fun `no match is reported with every candidate named, not guessed`() {
        val logical = facts("0", 6.4f, 9.6f, 7.2f, grbg)
        val choice = MainWideChooser.choose(logical, listOf(facts("6", 7.9f, 4.1f, 3.1f, grbg), facts("7", 27.2f, 4.1f, 3.1f, grbg)))
        val reason = (choice as MainWideChoice.NotFound).reason
        assertTrue(reason, reason.contains("6") && reason.contains("7"))
    }

    @Test
    fun `two matches are reported as not found, naming both`() {
        val logical = facts("0", 6.4f, 9.6f, 7.2f, grbg)
        val choice = MainWideChooser.choose(logical, listOf(facts("5", 6.4f, 9.6f, 7.2f, grbg), facts("8", 6.4f, 9.6f, 7.2f, grbg)))
        val reason = (choice as MainWideChoice.NotFound).reason
        assertTrue(reason, reason.contains("5") && reason.contains("8"))
    }

    @Test
    fun `a physical camera whose characteristics could not be read is never chosen`() {
        val logical = facts("0", 6.4f, 9.6f, 7.2f, grbg)
        val choice = MainWideChooser.choose(logical, listOf(facts("5", 6.4f, 9.6f, 7.2f, grbg, error = "unreadable")))
        assertTrue("$choice", choice is MainWideChoice.NotFound)
    }
}
