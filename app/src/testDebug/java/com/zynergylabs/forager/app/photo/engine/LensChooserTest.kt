package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.raw.DngDefaultCrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lens chooser's rules (dispatch 2026-10-16-04), headless, over both test phones' own characteristics
 * ([LensFixtures]): the lens set and its labels, which lens a zoom uses and how much it crops, the crop region, the DNG's
 * hard crop, each lens's JPG size, and the close-focus rule. Built through [buildLensSet], the function the camera's own
 * describe step calls, so the labels are what a phone with these characteristics would show.
 */
class LensChooserTest {

    private fun near(expected: Double, actual: Double, what: String) =
        assertTrue("$what: expected about $expected, got $actual", kotlin.math.abs(expected - actual) < 0.01)

    // ── The set and its labels ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the S26's lenses are labelled dot6 1 3 5, shortest first`() {
        val set = LensFixtures.s26Set()
        assertEquals(listOf(".6", "1", "3", "5"), set.lenses.map { it.label })
        assertEquals(listOf("2", "5", "6", "7"), set.lenses.map { it.physicalId })
        assertEquals("the main wide is camera 5, by the shared rule", "5", set.main.physicalId)
    }

    @Test
    fun `the S22's lenses are labelled dot6 1 3 10, shortest first`() {
        val set = LensFixtures.s22Set()
        assertEquals(listOf(".6", "1", "3", "10"), set.lenses.map { it.label })
        assertEquals(listOf("2", "5", "6", "7"), set.lenses.map { it.physicalId })
    }

    @Test
    fun `the zoom numbers are the measured fields of view against the main wide`() {
        val s26 = LensFixtures.s26Set().lenses.associate { it.physicalId to it.zoom }
        near(0.580, s26.getValue("2"), "S26 ultra-wide")
        near(1.0, s26.getValue("5"), "S26 main")
        near(2.891, s26.getValue("6"), "S26 3x")
        near(4.906, s26.getValue("7"), "S26 5x")
        val s22 = LensFixtures.s22Set().lenses.associate { it.physicalId to it.zoom }
        near(0.589, s22.getValue("2"), "S22 ultra-wide")
        near(2.900, s22.getValue("6"), "S22 3x")
        near(9.986, s22.getValue("7"), "S22 10x")
    }

    @Test
    fun `the label rule rounds tenths below 1 and whole numbers from 1`() {
        assertEquals(".6", lensLabel(0.58))
        assertEquals(".5", lensLabel(0.54))
        assertEquals("1", lensLabel(1.0))
        assertEquals("3", lensLabel(2.89))
        assertEquals("5", lensLabel(4.91))
        assertEquals("10", lensLabel(9.986))
    }

    // ── Each lens's own JPG (RECORD -1107: "Lens's own size") ─────────────────────────────────────────────────────────

    @Test
    fun `each lens saves its own full-size JPG, the camera enlarging any crop to it`() {
        assertEquals(
            mapOf("2" to PixelSize(4080, 3060), "5" to PixelSize(4080, 3060), "6" to PixelSize(3648, 2736), "7" to PixelSize(4080, 3060)),
            LensFixtures.s26Set().lenses.associate { it.physicalId to it.jpegSize },
        )
        assertEquals(
            mapOf("2" to PixelSize(4000, 3000), "5" to PixelSize(4000, 3000), "6" to PixelSize(3648, 2736), "7" to PixelSize(3648, 2736)),
            LensFixtures.s22Set().lenses.associate { it.physicalId to it.jpegSize },
        )
    }

    @Test
    fun `each lens binds RAW at its own sensor size`() {
        assertEquals(PixelSize(3648, 2736), LensFixtures.s22Set().byId("7")!!.rawSize)
        assertEquals(PixelSize(4080, 3060), LensFixtures.s26Set().byId("7")!!.rawSize)
        assertNull("a lens without the RAW capability has no RAW", LensFixtures.s26SetWithoutRawOn6().byId("6")!!.rawSize)
    }

    // ── Which lens a zoom uses, and its crop ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a zoom uses the longest lens at or below it and crops the rest, on the S26`() {
        val set = LensFixtures.s26Set()
        val cases = listOf(
            0.58 to ("2" to 1.0),
            0.9 to ("2" to 0.9 / 0.580),
            1.0 to ("5" to 1.0),
            2.0 to ("5" to 2.0),
            2.95 to ("6" to 2.95 / 2.891),
            4.0 to ("6" to 4.0 / 2.891),
            4.91 to ("7" to 4.91 / 4.906),
            9.0 to ("7" to 9.0 / 4.906),
        )
        for ((zoom, expected) in cases) {
            val lens = set.lensFor(zoom)
            assertEquals("zoom $zoom chose ${lens.physicalId}", expected.first, lens.physicalId)
            near(expected.second, cropFor(zoom, lens), "crop at zoom $zoom")
        }
    }

    @Test
    fun `a zoom uses the longest lens at or below it and crops the rest, on the S22`() {
        val set = LensFixtures.s22Set()
        assertEquals("1", set.lensFor(2.5).label)
        assertEquals("3", set.lensFor(5.0).label)
        assertEquals("10", set.lensFor(15.0).label)
        near(15.0 / 9.986, cropFor(15.0, set.lensFor(15.0)), "crop at 15")
    }

    @Test
    fun `the pinch runs from the shortest lens to 2x past the longest`() {
        val s26 = LensFixtures.s26Set()
        near(0.580, s26.minZoom, "S26 floor")
        near(9.81, s26.maxZoom, "S26 ceiling")
        val s22 = LensFixtures.s22Set()
        near(19.97, s22.maxZoom, "S22 ceiling")
        near(s22.maxZoom, s22.clamp(50.0), "clamped above")
        near(s22.minZoom, s22.clamp(0.1), "clamped below")
    }

    @Test
    fun `the crop region is centred on the lens's active array with even sides`() {
        assertEquals(PixelRect(912, 684, 2736, 2052), cropRegion(PixelSize(3648, 2736), 2.0))
        assertEquals(PixelRect(0, 0, 4080, 3060), cropRegion(PixelSize(4080, 3060), 1.0))
        val region = cropRegion(PixelSize(4080, 3060), 4.0 / 2.891)
        assertEquals("even width", 0, (region.right - region.left) % 2)
        assertEquals("even origin", 0, region.left % 2)
        assertEquals("centred", 4080 - region.right, region.left, )
    }

    // ── The DNG's hard crop (RECORD -1107) ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a RAW at twice the lens's zoom carries a centred half-size default crop`() {
        assertEquals(DngDefaultCrop(originH = 1020, originV = 764, sizeH = 2040, sizeV = 1530), dngDefaultCrop(4080, 3060, null, 2.0))
    }

    @Test
    fun `the default crop is measured from the ActiveArea's corner, within it`() {
        // ActiveArea top 0, left 8, bottom 3000, right 4008: a 4000x3000 area inside a wider buffer.
        assertEquals(DngDefaultCrop(originH = 1000, originV = 750, sizeH = 2000, sizeV = 1500), dngDefaultCrop(4016, 3000, intArrayOf(0, 8, 3000, 4008), 2.0))
    }

    @Test
    fun `a RAW at the lens's own zoom carries no crop at all`() {
        assertNull(dngDefaultCrop(4080, 3060, null, 1.0))
    }

    // ── The close-focus rule (RECORD -1107: "Use the main lens, zoomed") ──────────────────────────────────────────────

    private val s26 = LensFixtures.s26Set()
    private val main = s26.main
    private val tele = s26.byId("6")!!

    private fun frames(rule: CloseFocusRule, streaming: Lens, n: Int, diopters: Float?, af: Int?): List<Boolean> =
        List(n) { rule.onFrame(streaming, tele, main, FocusReading(diopters, af)) }

    @Test
    fun `the telephoto parked at its near limit without focus for half a second hands over to the main lens`() {
        val rule = CloseFocusRule()
        val changes = frames(rule, tele, CloseFocusRule.FRAMES_TO_DECIDE, 2.5f, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        assertEquals("decided on the last of the frames, not before", listOf(false).repeated(CloseFocusRule.FRAMES_TO_DECIDE - 1) + true, changes)
        assertTrue(rule.tooClose)
        assertEquals("the main lens stands in", main, effectiveLens(tele, s26, rule.tooClose))
    }

    @Test
    fun `a telephoto that focuses, or one scanning short of its limit, stays`() {
        val rule = CloseFocusRule()
        frames(rule, tele, 30, 2.5f, CloseFocusRule.AF_PASSIVE_FOCUSED)
        frames(rule, tele, 30, 1.0f, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        assertFalse(rule.tooClose)
    }

    @Test
    fun `one focused frame at the limit starts the count again`() {
        val rule = CloseFocusRule()
        frames(rule, tele, CloseFocusRule.FRAMES_TO_DECIDE - 1, 2.5f, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        frames(rule, tele, 1, 2.5f, CloseFocusRule.AF_PASSIVE_FOCUSED)
        frames(rule, tele, CloseFocusRule.FRAMES_TO_DECIDE - 1, 2.5f, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        assertFalse(rule.tooClose)
    }

    @Test
    fun `the telephoto comes back once the main lens reads the subject well past its limit`() {
        val rule = CloseFocusRule()
        frames(rule, tele, CloseFocusRule.FRAMES_TO_DECIDE, 2.5f, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        // 45 cm (2.2 diopters) is past the 3x lens's 40 cm but inside the margin: the main lens stays.
        frames(rule, main, 30, 2.2f, CloseFocusRule.AF_PASSIVE_FOCUSED)
        assertTrue("inside the margin the main lens stays", rule.tooClose)
        // 1 m (1 diopter) is well past it.
        val changes = frames(rule, main, CloseFocusRule.FRAMES_TO_DECIDE, 1.0f, CloseFocusRule.AF_PASSIVE_FOCUSED)
        assertTrue(changes.last())
        assertFalse(rule.tooClose)
    }

    @Test
    fun `moving to the telephoto with the main lens already focused closer than it can, keeps the main lens`() {
        val rule = CloseFocusRule()
        frames(rule, main, 3, 5f, CloseFocusRule.AF_PASSIVE_FOCUSED) // 20 cm, read by the main wide
        assertTrue(rule.wantedChanged(tele, main, streaming = main))
        assertTrue(rule.tooClose)
    }

    @Test
    fun `a lens that reports no focus distance never switches by this rule`() {
        val rule = CloseFocusRule()
        frames(rule, tele, 60, null, CloseFocusRule.AF_PASSIVE_UNFOCUSED)
        assertFalse(rule.tooClose)
    }

    @Test
    fun `the rule does not apply to the main lens or the ultra-wide`() {
        val rule = CloseFocusRule()
        assertFalse(rule.applies(main, main))
        assertFalse(rule.applies(s26.byId("2")!!, main))
        assertTrue(rule.applies(tele, main))
    }

    private fun <T> List<T>.repeated(n: Int): List<T> = List(n) { this[0] }
}
