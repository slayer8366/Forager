package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known bug 14 (dispatch 2026-11-02-02, A8: "Recompute the tile cap for z11: the 24 km area's worst case up to the highest
 * latitude a download allows. Record it, with its reason, in the limits object"). Re-derives [TrailMapDetailLimits.MAX_AREA_TILES]
 * from the tile budget and the radius ceiling, so moving either without the cap fails here and names it, as
 * `OfflineMapRadiusBudgetGuardTest` does for the radius ceiling.
 */
class TrailMapDetailCapGuardTest {

    private val budget = OfflineMapRepository.TILE_COUNT_LIMIT
    private val largest = OfflineMapRepository.MAX_RADIUS_KM
    private val z11Tile = 360.0 / (1 shl DetailTileCover.ZOOM)

    private fun cover(lat: Double, lng: Double, radiusKm: Int): Long = DetailTileCover.of(routeAreaFor(Region(lat, lng, radiusKm)))!!.size

    /** Whether some placement of an area of [radiusKm] near [lat] fits the whole budget: the centre swept across one zoom-15 tile. */
    private fun fitsSomewhere(lat: Double, radiusKm: Int): Boolean {
        for (i in 0 until 16) for (j in 0 until 16) {
            val region = Region(lat + j * 0.005 / 16, -100.0 + i * (360.0 / 32_768) / 16, radiusKm)
            if (estimateServedOfflineTileCount(region) <= budget) return true
        }
        return false
    }

    /** The highest latitude, in steps of 0.05 degrees, at which a download of the largest area is allowed on an empty budget. */
    private fun highestLatitudeTheLargestAreaDownloads(): Double =
        (0..1_700).map { it * 0.05 }.last { fitsSomewhere(it, largest) }

    @Test
    fun `the largest area stops fitting the tile budget between 53 and 54 degrees`() {
        val highest = highestLatitudeTheLargestAreaDownloads()
        assertTrue("the largest area fits the budget up to $highest degrees", highest >= 53.0 && highest < 54.0)
        assertTrue("positive control: it does not fit at 60 degrees", !fitsSomewhere(60.0, largest))
    }

    @Test
    fun `the cap is the largest area's worst cover up to the highest latitude its download is allowed at`() {
        val highest = highestLatitudeTheLargestAreaDownloads()
        var worst = 0L
        for (step in 0..(highest / 0.25).toInt()) {
            val lat0 = step * 0.25
            for (i in 0 until 12) for (j in 0 until 12) {
                val lat = lat0 + j * z11Tile * Math.cos(Math.toRadians(lat0)) / 12
                if (lat > highest) continue
                worst = maxOf(worst, cover(lat, -100.0 + i * z11Tile / 12, largest))
            }
        }
        assertEquals(
            "the largest area's worst zoom-11 cover up to $highest degrees; move TrailMapDetailLimits.MAX_AREA_TILES with it",
            worst,
            TrailMapDetailLimits.MAX_AREA_TILES,
        )
    }

    @Test
    fun `no area the budget allows, of any radius at any latitude, covers more than the cap`() {
        var worst = 0L
        var areas = 0
        for (half in 0..170) {
            val lat0 = half * 0.5
            for (radiusKm in 1..largest) for (i in 0 until 6) for (j in 0 until 6) {
                val lat = lat0 + j * z11Tile * Math.cos(Math.toRadians(lat0)) / 6
                if (lat > 85.0) continue
                val lng = -100.0 + i * z11Tile / 6
                if (estimateServedOfflineTileCount(Region(lat, lng, radiusKm)) > budget) continue
                areas++
                worst = maxOf(worst, cover(lat, lng, radiusKm))
            }
        }
        assertTrue("positive control: the sweep found areas the budget allows ($areas)", areas > 10_000)
        assertTrue("the largest cover of an area the budget allows is $worst, over the cap", worst <= TrailMapDetailLimits.MAX_AREA_TILES)
    }
}
