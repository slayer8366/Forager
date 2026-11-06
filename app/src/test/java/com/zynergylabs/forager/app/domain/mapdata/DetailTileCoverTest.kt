package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known bug 14 (dispatch 2026-11-02-02, B3: "the cover counts at z11"): the zoom-11 tiles an area's download rectangle touches.
 * Made-up points; the counts are the worst over a sweep of the centre across one zoom-11 tile, so they do not depend on where
 * a point happens to sit.
 */
class DetailTileCoverTest {

    private fun worstCover(lat: Double, radiusKm: Int): Long {
        val tile = 360.0 / (1 shl DetailTileCover.ZOOM)
        var worst = 0L
        for (i in 0 until 12) for (j in 0 until 12) {
            val centre = LatLng(lat + j * tile * Math.cos(Math.toRadians(lat)) / 12, -100.0 + i * tile / 12)
            worst = maxOf(worst, DetailTileCover.of(routeAreaFor(Region(centre.lat, centre.lng, radiusKm)))!!.size)
        }
        return worst
    }

    @Test
    fun `zoom 11, the owner's Medium`() {
        assertEquals(11, DetailTileCover.ZOOM)
    }

    @Test
    fun `the largest area's worst cover is 16 tiles at 16 degrees, 25 at 45, 36 at 53 and 81 at 70`() {
        assertEquals(16L, worstCover(16.0, 24))
        assertEquals(25L, worstCover(45.0, 24))
        assertEquals(36L, worstCover(53.0, 24))
        assertEquals(81L, worstCover(70.0, 24))
    }

    @Test
    fun `the smallest area covers one to four tiles`() {
        val worst = worstCover(45.0, 1)
        assertTrue("was $worst", worst in 1L..4L)
    }

    @Test
    fun `the tiles listed are the tiles counted, all at zoom 11, inside the rectangle's corners`() {
        val box = routeAreaFor(Region(41.0, -102.0, 24))
        val cover = DetailTileCover.of(box)!!
        val tiles = cover.tiles()
        assertEquals(cover.size, tiles.size.toLong())
        assertEquals(tiles.size, tiles.toSet().size)
        assertTrue(tiles.all { it.z == 11 })
        val nw = TileFeatureReader.tileOf(LatLng(box.north, box.west), 11)
        val se = TileFeatureReader.tileOf(LatLng(box.south, box.east), 11)
        assertEquals(nw, tiles.first())
        assertEquals(se, tiles.last())
    }

    @Test
    fun `an area across the antimeridian is not covered - unsupported, never guessed`() {
        assertNull(DetailTileCover.of(GeoBoundingBox(north = 10.0, south = 9.0, east = -179.5, west = 179.5)))
    }
}
