package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The zoom diagnostics' rules (dispatch 2026-10-17-01, Phase 2a), headless. The phones' keys are the dumpsys camera
 * tables' (RECORD -1150, Phase 1 report): the S22 lists cropRegion, zoomRatio and opticalStabilizationMode per lens and
 * each lens's zoom ratio range is 1 to 8; the S26 lists no per-lens key at all; both logical ranges are 0.6 to 10. The
 * S26's reported crops (2.89 on the 3x lens, 4.90 on the 5x, no crop asked) are RECORD -1147's log.
 */
class ZoomDiagnosticsTest {

    private val s22 = LensFixtures.s22Set()
    private val s26 = LensFixtures.s26Set()
    private val s22Keys = ZoomKeys(lensCropRegion = true, lensZoomRatio = true, logicalZoomRatio = true, logicalZoomRange = 0.6..10.0)
    private val s26Keys = ZoomKeys(lensCropRegion = false, lensZoomRatio = false, logicalZoomRatio = true, logicalZoomRange = 0.6..10.0)
    private val lensRange = 1.0..8.0

    /** A line's `key=value` tokens; the free-text note is last and not parsed. */
    private fun fields(line: String): Map<String, String> =
        line.substringBefore(" note=").split(' ').filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    @Test
    fun `the extra's four values parse, and anything else is refused`() {
        assertEquals(ZoomForm.Auto, parseZoomForm("auto"))
        assertEquals(ZoomForm.Crop, parseZoomForm("crop"))
        assertEquals(ZoomForm.LensRatio, parseZoomForm("lens_ratio"))
        assertEquals(ZoomForm.LogicalRatio, parseZoomForm("logical_ratio"))
        assertNull(parseZoomForm("Crop"))
        assertNull(parseZoomForm("lens-ratio"))
        assertNull(parseZoomForm(null))
    }

    @Test
    fun `auto on the S22 is today's form, the lens's own crop region and nothing else`() {
        val lens = s22.byId("6")!!
        val crop = lensCrop(lens, 3.18)
        val plan = zoomPlan(ZoomForm.Auto, crop, lens.activeArray, lensRange, s22Keys)
        assertEquals(ZoomForm.Crop, plan.used)
        assertEquals(ZoomForm.Auto, plan.asked)
        assertEquals(crop.region, plan.lensCropRegion)
        assertNull(plan.lensZoomRatio)
        assertNull(plan.logicalZoomRatio)
        assertTrue(plan.needsLensSettings)
        assertNull(plan.note)
    }

    @Test
    fun `auto on the S26 is today's form, the logical zoom ratio in the main wide's terms`() {
        val lens = s26.byId("6")!!
        val plan = zoomPlan(ZoomForm.Auto, lensCrop(lens, 3.18), lens.activeArray, lensRange, s26Keys)
        assertEquals(ZoomForm.LogicalRatio, plan.used)
        assertEquals(3.18, plan.logicalZoomRatio!!, 1e-9)
        assertNull(plan.lensCropRegion)
        assertNull(plan.lensZoomRatio)
        assertFalse(plan.needsLensSettings)
    }

    @Test
    fun `lens_ratio on the S22 asks the lens's own zoom ratio, with its crop region at the full array`() {
        val lens = s22.byId("6")!!
        val crop = lensCrop(lens, 3.18)
        val plan = zoomPlan(ZoomForm.LensRatio, crop, lens.activeArray, lensRange, s22Keys)
        assertEquals(ZoomForm.LensRatio, plan.used)
        assertEquals(crop.crop, plan.lensZoomRatio!!, 1e-9)
        assertEquals(PixelRect(0, 0, 3648, 2736), plan.lensCropRegion)
        assertNull(plan.logicalZoomRatio)
        assertNull(plan.note)
    }

    @Test
    fun `lens_ratio past the lens's own range is clamped to it, and the note says so`() {
        val main = s22.main
        val plan = zoomPlan(ZoomForm.LensRatio, lensCrop(main, 9.99), main.activeArray, lensRange, s22Keys)
        assertEquals(8.0, plan.lensZoomRatio!!, 1e-9)
        assertTrue(plan.note.toString(), plan.note!!.contains("clamped"))
    }

    @Test
    fun `a form the phone does not list falls back to today's form, with the reason`() {
        val lens = s26.byId("6")!!
        val crop = lensCrop(lens, 3.18)
        val asCrop = zoomPlan(ZoomForm.Crop, crop, lens.activeArray, lensRange, s26Keys)
        assertEquals(ZoomForm.Crop, asCrop.asked)
        assertEquals(ZoomForm.LogicalRatio, asCrop.used)
        assertEquals(3.18, asCrop.logicalZoomRatio!!, 1e-9)
        assertTrue(asCrop.note.toString(), asCrop.note!!.contains("crop unsupported"))
        val asRatio = zoomPlan(ZoomForm.LensRatio, crop, lens.activeArray, lensRange, s26Keys)
        assertEquals(ZoomForm.LogicalRatio, asRatio.used)
        assertNull(asRatio.lensZoomRatio)
        assertTrue(asRatio.note.toString(), asRatio.note!!.contains("lens_ratio unsupported"))
    }

    @Test
    fun `logical_ratio on the S22 asks only the logical zoom ratio, clamped to its range`() {
        val lens = s22.byId("6")!!
        val plan = zoomPlan(ZoomForm.LogicalRatio, lensCrop(lens, 3.18), lens.activeArray, lensRange, s22Keys)
        assertEquals(ZoomForm.LogicalRatio, plan.used)
        assertEquals(3.18, plan.logicalZoomRatio!!, 1e-9)
        assertFalse(plan.needsLensSettings)
        val tele = s22.byId("7")!!
        val past = zoomPlan(ZoomForm.LogicalRatio, lensCrop(tele, 19.0), tele.activeArray, lensRange, s22Keys)
        assertEquals(10.0, past.logicalZoomRatio!!, 1e-9)
        assertTrue(past.note.toString(), past.note!!.contains("clamped"))
    }

    @Test
    fun `stabilization is asked ON only where the lens offers it and the phone lists the key per lens`() {
        assertEquals("the S22", OIS_ON, lensOisRequest(listOf(0, 1), lensOisKeyListed = true))
        assertNull("the S26: offered by the lens, but no per-lens key", lensOisRequest(listOf(0, 1), lensOisKeyListed = false))
        assertNull("a lens without it", lensOisRequest(listOf(0), lensOisKeyListed = true))
        assertNull(lensOisRequest(emptyList(), lensOisKeyListed = true))
    }

    @Test
    fun `under the logical zoom ratio a telephoto's reported crop is read in its own units`() {
        val three = s26.byId("6")!!
        val five = s26.byId("7")!!
        assertEquals(1.0, cropInLensUnits(2.89, ZoomForm.LogicalRatio, three)!!, 0.01)
        assertEquals(1.0, cropInLensUnits(4.90, ZoomForm.LogicalRatio, five)!!, 0.01)
        assertEquals(1.10, cropInLensUnits(3.18, ZoomForm.LogicalRatio, three)!!, 0.01)
        assertEquals("the main wide's units are its own", 1.55, cropInLensUnits(1.55, ZoomForm.LogicalRatio, s26.main)!!, 1e-9)
    }

    @Test
    fun `the lens's own forms report its own units already, and an unknown form is left as reported`() {
        val three = s22.byId("6")!!
        assertEquals(1.10, cropInLensUnits(1.10, ZoomForm.Crop, three)!!, 1e-9)
        assertEquals(1.10, cropInLensUnits(1.10, ZoomForm.LensRatio, three)!!, 1e-9)
        assertEquals(2.89, cropInLensUnits(2.89, null, three)!!, 1e-9)
        assertNull(cropInLensUnits(null, ZoomForm.LogicalRatio, three))
    }

    @Test
    fun `the limiter lets one line a second through for a key, and a changed key at once`() {
        val limiter = LineLimiter(1_000_000_000L)
        assertTrue(limiter.allow(0L, "a"))
        assertFalse(limiter.allow(500_000_000L, "a"))
        assertTrue("a changed key", limiter.allow(600_000_000L, "b"))
        assertFalse(limiter.allow(1_500_000_000L, "b"))
        assertTrue("a second after the last line", limiter.allow(1_600_000_000L, "b"))
    }

    @Test
    fun `the crop_diag line states what was asked and what came back, the achieved crop in the lens's units`() {
        val lens = s26.byId("6")!!
        val crop = lensCrop(lens, 3.18)
        val diag = FrameZoomDiag(
            plan = ZoomPlan(ZoomForm.Auto, ZoomForm.LogicalRatio, "6", logicalZoomRatio = 3.18),
            ois = OisAsk(lensAsked = null, lensTemplate = null, logicalTemplate = 0),
            result = ZoomResult(
                logicalZoomRatio = 3.18f,
                logicalCrop = PixelRect(0, 0, 4080, 3060),
                activePhysicalId = "6",
                activePhysicalCrop = PixelRect(10, 8, 3638, 2728),
                lensResult = true,
                // 3648 / 1147 = 3.18 in the main wide's terms, as the S26 reports under the logical ratio.
                lensCrop = PixelRect(1250, 938, 2397, 1798),
                lensOis = 1,
                logicalOis = 0,
                distortionMode = 0,
            ),
        )
        val line = cropDiagLine("preview", diag, lens, crop)
        assertTrue(line, line.startsWith("engine=platform event=crop_diag "))
        val f = fields(line)
        assertEquals("preview", f["kind"])
        assertEquals("6", f["lens"])
        assertEquals("3.18", f["zoom"])
        assertEquals("1.10", f["crop_requested"])
        assertEquals("logical_ratio", f["form"])
        assertEquals("auto", f["asked"])
        assertEquals("unset", f["req_lens_crop"])
        assertEquals("unset", f["req_lens_ratio"])
        assertEquals("3.18", f["req_logical_ratio"])
        assertEquals("3.18", f["res_logical_ratio"])
        assertEquals("0,0,4080,3060", f["res_logical_crop"])
        assertEquals("6", f["res_active_physical"])
        assertEquals("10,8,3638,2728", f["res_active_physical_crop"])
        assertEquals("present", f["res_lens"])
        assertEquals("1250,938,2397,1798", f["res_lens_crop"])
        assertEquals("3.18", f["lens_crop_reported"])
        assertEquals("1.10", f["lens_crop_achieved"])
        assertEquals("unset", f["ois_asked"])
        assertEquals("unset", f["ois_template_lens"])
        assertEquals("0", f["ois_template_logical"])
        assertEquals("1", f["ois_res_lens"])
        assertEquals("0", f["ois_res_logical"])
        assertEquals("0", f["distortion"])
    }

    @Test
    fun `the crop_diag line says so when the lens's own result is missing, and carries the plan's note`() {
        val lens = s22.byId("6")!!
        val crop = lensCrop(lens, 3.18)
        val diag = FrameZoomDiag(
            plan = ZoomPlan(ZoomForm.LensRatio, ZoomForm.LensRatio, "6", lensCropRegion = PixelRect(0, 0, 3648, 2736), lensZoomRatio = crop.crop, note = "a note"),
            ois = OisAsk(lensAsked = OIS_ON, lensTemplate = 0, logicalTemplate = 0),
            result = ZoomResult(lensResult = false),
        )
        val line = cropDiagLine("jpg", diag, lens, crop)
        val f = fields(line)
        assertEquals("jpg", f["kind"])
        assertEquals("absent", f["res_lens"])
        assertEquals("unreported", f["lens_crop_reported"])
        assertEquals("unreported", f["lens_crop_achieved"])
        assertEquals("0,0,3648,2736", f["req_lens_crop"])
        assertEquals("1.10", f["req_lens_ratio"])
        assertEquals("on", f["ois_asked"])
        assertEquals("0", f["ois_template_lens"])
        assertTrue(line, line.endsWith(" note=a note"))
    }

    @Test
    fun `the lens_static line gives a lens's arrays, zoom limits and stabilization, and the logical camera's per-lens keys`() {
        val lens = fields(
            lensStaticLine(LensStatic("6", false, PixelRect(0, 0, 3648, 2736), PixelRect(0, 0, 3664, 2752), 8f, 1.0..8.0, listOf(0, 1), listOf(0, 1))),
        )
        assertEquals("lens_static", lens["event"])
        assertEquals("6", lens["lens"])
        assertEquals("0,0,3648,2736", lens["active"])
        assertEquals("0,0,3664,2752", lens["pre_correction"])
        assertEquals("8.00", lens["max_digital_zoom"])
        assertEquals("1.00-8.00", lens["zoom_ratio_range"])
        assertEquals("0|1", lens["ois_modes"])
        assertEquals("0|1", lens["distortion_modes"])
        val s22Logical = fields(
            lensStaticLine(LensStatic("0", true, PixelRect(0, 0, 4000, 3000), null, 8f, 0.6..10.0, listOf(0, 1), emptyList(), listOf("crop_region", "zoom_ratio", "ois"))),
        )
        assertEquals("logical:0", s22Logical["lens"])
        assertEquals("unreported", s22Logical["pre_correction"])
        assertEquals("0.60-10.00", s22Logical["zoom_ratio_range"])
        assertEquals("none", s22Logical["distortion_modes"])
        assertEquals("crop_region|zoom_ratio|ois", s22Logical["per_lens_keys"])
        val s26Logical = fields(lensStaticLine(LensStatic("0", true, null, null, null, null, listOf(0), emptyList(), emptyList())))
        assertEquals("none", s26Logical["per_lens_keys"])
        assertEquals("unreported", s26Logical["zoom_ratio_range"])
    }
}
