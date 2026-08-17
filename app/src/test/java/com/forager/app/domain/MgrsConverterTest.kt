package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MgrsCoordinate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [MgrsConverter] against pinned reference values, not "it runs without crashing" — CLAUDE.md is
 * explicit that this is the one place in this whole feature where plausible isn't good enough.
 *
 * Each expected string below was cross-checked against `mgrs` (the Python binding over NGA's own
 * GeoTrans C implementation — a second, independently-written codebase, not a second copy of
 * `mil.nga:mgrs`) and matched exactly on every point: 7 different UTM zones, both hemispheres, and
 * the New York point additionally matches a worked example independently published for the
 * Military Grid Reference System (18T WL 80735 04695 at one-metre precision) — see
 * [MgrsConverter]'s own doc comment for the full reasoning.
 */
class MgrsConverterTest {

    @Test
    fun `New York, a mid-northern-latitude point, matches a published worked example`() {
        val result = MgrsConverter.convert(LatLng(40.6892, -74.0445))

        assertEquals(MgrsCoordinate.Grid("18T WL 80735 04695"), result)
    }

    @Test
    fun `Sydney, a southern-hemisphere point, converts correctly`() {
        val result = MgrsConverter.convert(LatLng(-33.8688, 151.2093))

        assertEquals(MgrsCoordinate.Grid("56H LH 34368 50948"), result)
    }

    @Test
    fun `the equator and prime meridian intersection converts correctly`() {
        val result = MgrsConverter.convert(LatLng(0.0, 0.0))

        assertEquals(MgrsCoordinate.Grid("31N AA 66021 00000"), result)
    }

    @Test
    fun `Aachen Cathedral, near a UTM zone boundary, converts correctly`() {
        val result = MgrsConverter.convert(LatLng(50.7747, 6.0836))

        assertEquals(MgrsCoordinate.Grid("32U KB 94385 28826"), result)
    }

    @Test
    fun `Ushuaia, a southern-hemisphere and western-longitude point, converts correctly`() {
        val result = MgrsConverter.convert(LatLng(-54.8019, -68.3030))

        assertEquals(MgrsCoordinate.Grid("19F EV 44805 27029"), result)
    }

    @Test
    fun `a point in this app's own Portland OR region converts correctly`() {
        val result = MgrsConverter.convert(LatLng(45.5152, -122.6784))

        assertEquals(MgrsCoordinate.Grid("10T ER 25118 40235"), result)
    }

    @Test
    fun `Tokyo converts correctly`() {
        val result = MgrsConverter.convert(LatLng(35.6762, 139.6503))

        assertEquals(MgrsCoordinate.Grid("54S UE 77855 48874"), result)
    }

    @Test
    fun `McMurdo Station, a high-southern-latitude point still inside MGRS coverage, converts correctly`() {
        val result = MgrsConverter.convert(LatLng(-77.8463, 166.6683))

        assertEquals(MgrsCoordinate.Grid("58C EU 39204 58225"), result)
    }

    @Test
    fun `the southern coverage boundary, exactly 80 degrees south, is still supported`() {
        val result = MgrsConverter.convert(LatLng(-80.0, 0.0))

        assertEquals(MgrsCoordinate.Grid("31C DM 41867 16915"), result)
    }

    @Test
    fun `the northern coverage boundary, exactly 84 degrees north, is still supported`() {
        val result = MgrsConverter.convert(LatLng(84.0, 0.0))

        assertEquals(MgrsCoordinate.Grid("31X DP 65005 29005"), result)
    }

    /**
     * The real edge case this feature exists to guard: `mil.nga:mgrs`'s own `MGRS.from()` does
     * not reject a latitude past its supported range — it silently clamps to the boundary and
     * returns a grid reference for that clamped point instead, which is exactly the "fabricated
     * plausible value" CLAUDE.md rules out. Confirmed by comparing this same point's clamped
     * output against the northern-boundary test above: without [MgrsConverter]'s own range check,
     * this would silently return `Grid("31X DP 65005 29005")` — the 84°N answer — instead of
     * flagging that Universal Polar Stereographic applies here, not MGRS.
     */
    @Test
    fun `just north of the coverage boundary is explicitly unsupported, not silently clamped`() {
        val result = MgrsConverter.convert(LatLng(84.5, 0.0))

        assertTrue(result is MgrsCoordinate.Unsupported)
        assertTrue((result as MgrsCoordinate.Unsupported).reason.contains("Universal Polar Stereographic"))
    }

    @Test
    fun `just south of the coverage boundary is explicitly unsupported, not silently clamped`() {
        val result = MgrsConverter.convert(LatLng(-80.5, 0.0))

        assertTrue(result is MgrsCoordinate.Unsupported)
        assertTrue((result as MgrsCoordinate.Unsupported).reason.contains("Universal Polar Stereographic"))
    }

    @Test
    fun `the north pole is unsupported`() {
        val result = MgrsConverter.convert(LatLng(90.0, 0.0))

        assertTrue(result is MgrsCoordinate.Unsupported)
    }

    @Test
    fun `the south pole is unsupported`() {
        val result = MgrsConverter.convert(LatLng(-90.0, 0.0))

        assertTrue(result is MgrsCoordinate.Unsupported)
    }
}
