package com.zynergylabs.forager.app.domain.mapdata

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import com.zynergylabs.forager.app.routing.DropOffTestData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An area's zoom-15 tiles (dispatch 2026-11-04-09): the cover of the rectangle MapLibre downloads the area for
 * (`routeAreaFor`), with no margin past its edge (B1 P6). Areas are made up round the drop-off fixture's invented block,
 * in the open Atlantic. The expected cover is found another way: every tile a grid of points about 20 m apart across the
 * rectangle, corners and edges included, falls in.
 */
class DropOffTileCoverTest {

    private val centre = DropOffTestData.point("pin_a")

    private fun sampled(box: GeoBoundingBox): Set<TileId> {
        val rows = 2 + ((box.north - box.south) * 111_000 / 20).toInt()
        val cols = 2 + ((box.east - box.west) * 111_000 / 20).toInt()
        return (0..rows).flatMap { i ->
            val lat = box.south + (box.north - box.south) * i / rows
            (0..cols).map { j -> TileFeatureReader.tileOf(LatLng(lat, box.west + (box.east - box.west) * j / cols), 15) }
        }.toSet()
    }

    @Test
    fun `a 1 km area's cover is every zoom-15 tile its rectangle touches, and no other`() {
        val box = routeAreaFor(Region(centre.lat, centre.lng, 1))
        val cover = DropOffTileCover.of(box)!!

        assertEquals(sampled(box), cover.tiles().toSet())
        assertEquals(cover.tiles().size.toLong(), cover.size)
        // At 40.1°N a zoom-15 tile is about 0.94 km on a side, and the rectangle 2 km on a side.
        assertTrue("${cover.xs} x ${cover.ys}", cover.xs.count() in 3..4 && cover.ys.count() in 3..4)
    }

    @Test
    fun `a 10 km area's cover is every zoom-15 tile its rectangle touches, and no other`() {
        val box = routeAreaFor(Region(centre.lat, centre.lng, 10))
        val cover = DropOffTileCover.of(box)!!

        assertEquals(sampled(box), cover.tiles().toSet())
        assertTrue("${cover.xs} x ${cover.ys}", cover.xs.count() in 22..23 && cover.ys.count() in 22..23)
    }

    @Test
    fun `every tile is at zoom 15, once`() {
        val tiles = DropOffTileCover.of(routeAreaFor(Region(centre.lat, centre.lng, 1)))!!.tiles()
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.all { it.z == 15 })
        assertEquals(tiles.size, tiles.toSet().size)
    }

    @Test
    fun `a rectangle that crosses the antimeridian is not covered`() {
        assertNull(DropOffTileCover.of(GeoBoundingBox(north = 1.0, south = 0.0, east = -179.9, west = 179.9)))
    }
}
