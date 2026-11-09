package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * The guard that ties [OfflineMapRepository.MAX_RADIUS_KM], [OfflineMapRepository.TILE_COUNT_LIMIT]
 * and [OfflineMapRepository.SERVED_MAX_ZOOM] together (two-data-corrections dispatch, Part B, owner
 * decision: "stated constant plus the guard test — the guard is the valuable part"). The radius
 * ceiling is a stated constant, not derived at runtime, so this is what stops it going stale the way
 * SERVED_MAX_ZOOM did: move the budget or the ceiling without redoing the radius and this fails.
 *
 * Worst case the app supports: a centre on the tile archive's northern edge, 49.60°N
 * (`server/pmtiles-worker/README.md`, `--bbox=…,49.60`). Tile counts vary with where the centre sits
 * relative to tile edges, so each radius is evaluated at the maximum over a 40 × 40 sweep of the
 * centre across one zoom-15 tile — the same sweep the pre-build report used
 * (docs/audits/2026-09-06-filter-and-tile-cost-prebuild-report.md, B2). The expected counts are
 * that report's hand-derived literals, not recomputed here from the code under test; the sweep only
 * enumerates inputs.
 */
class OfflineMapRadiusBudgetGuardTest {

    @Test
    fun `the offline radius ceiling fits the tile budget at the archive's northern edge, worst alignment`() {
        val atCeiling = worstCaseServedTileCount(OfflineMapRepository.MAX_RADIUS_KM)
        assertEquals(5246, atCeiling)
        assertTrue("$atCeiling tiles at ${OfflineMapRepository.MAX_RADIUS_KM} km must fit ${OfflineMapRepository.TILE_COUNT_LIMIT}", atCeiling <= OfflineMapRepository.TILE_COUNT_LIMIT)
    }

    /**
     * One step above the ceiling still fits — 25 km is the true maximum at 95 % of the budget; the
     * owner chose 24 for margin and for reading "15 mi". Pinned so the headroom is a recorded fact,
     * not a surprise.
     */
    @Test
    fun `one step above the ceiling is the true maximum, at 95 percent of the budget`() {
        val oneAbove = worstCaseServedTileCount(OfflineMapRepository.MAX_RADIUS_KM + 1)
        assertEquals(5718, oneAbove)
        assertTrue(oneAbove <= OfflineMapRepository.TILE_COUNT_LIMIT)
    }

    /** Two steps above does not fit — this is the assertion that fails when the budget rises without the ceiling following. */
    @Test
    fun `two steps above the ceiling exceeds the tile budget`() {
        val twoAbove = worstCaseServedTileCount(OfflineMapRepository.MAX_RADIUS_KM + 2)
        assertEquals(6075, twoAbove)
        assertTrue("the budget has risen or the ceiling has moved: ${OfflineMapRepository.MAX_RADIUS_KM + 2} km now fits — redo the table on MAX_RADIUS_KM", twoAbove > OfflineMapRepository.TILE_COUNT_LIMIT)
    }

    /** The search radius keeps its own 50 km; only the offline radius shrank (owner decision: search costs no tiles). */
    @Test
    fun `the search radius ceiling is untouched by the offline ceiling`() {
        assertEquals(50, Region.MAX_RADIUS_KM)
        assertEquals(24, OfflineMapRepository.MAX_RADIUS_KM)
    }

    private fun worstCaseServedTileCount(radiusKm: Int): Int {
        val baseLat = 49.60
        val baseLng = -122.607
        val steps = 40
        val zoom15TileFraction = 1.0 / 32768.0
        val zoom15TileDegreesLng = 360.0 / 32768.0
        val baseMercatorY = mercatorY(baseLat)
        var worst = 0
        for (i in 0 until steps) {
            for (j in 0 until steps) {
                val lng = baseLng + i.toDouble() / steps * zoom15TileDegreesLng
                val lat = latitudeFromMercatorY(baseMercatorY + j.toDouble() / steps * zoom15TileFraction)
                worst = maxOf(worst, estimateServedOfflineTileCount(Region(lat = lat, lng = lng, radiusKm = radiusKm)))
            }
        }
        return worst
    }

    private fun mercatorY(latDeg: Double): Double {
        val lat = Math.toRadians(latDeg)
        return (1.0 - ln(tan(lat) + 1.0 / cos(lat)) / PI) / 2.0
    }

    private fun latitudeFromMercatorY(y: Double): Double = Math.toDegrees(atan(sinh(PI * (1.0 - 2.0 * y))))
}
