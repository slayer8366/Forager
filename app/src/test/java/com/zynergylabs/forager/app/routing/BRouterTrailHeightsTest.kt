package com.zynergylabs.forager.app.routing

import btools.router.OsmPathElement
import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The routing adapter's heights (dispatch 2026-10-30-06, RECORD -1836): BRouter stores a node's height in quarter metres,
 * with [Short.MIN_VALUE] for "no height". The Harriman fixture has no heights at all (`BRouterTrailRouterTest`), so a
 * mix of heighted and missing points, and the conversion itself, are tried here on nodes built by hand, through the
 * same mapping [BRouterTrailRouter.route] uses.
 */
class BRouterTrailHeightsTest {

    @Test
    fun `BRouter's no-height value is missing, never 0`() {
        assertNull(BRouterTrailRouter.heightMetresOf(Short.MIN_VALUE))
    }

    @Test
    fun `a stored height is quarter metres, and 0 and below are real heights`() {
        assertEquals(1.0, BRouterTrailRouter.heightMetresOf(4)!!, 0.0)
        assertEquals(0.75, BRouterTrailRouter.heightMetresOf(3)!!, 0.0)
        assertEquals("sea level is a height, not a missing one", 0.0, BRouterTrailRouter.heightMetresOf(0)!!, 0.0)
        assertEquals("below sea level too", -2.0, BRouterTrailRouter.heightMetresOf(-8)!!, 0.0)
        assertEquals("Paradise, about 1,656 m", 1_656.5, BRouterTrailRouter.heightMetresOf((1_656.5 * 4).toInt().toShort())!!, 0.0)
    }

    @Test
    fun `a route keeps each point's height in order, missing where the data has none`() {
        val nodes = listOf(
            node(LatLng(46.7865, -121.7356), 6_626),
            node(LatLng(46.7900, -121.7340), Short.MIN_VALUE),
            node(LatLng(46.8022, -121.7300), 8_134),
        )
        val route = BRouterTrailRouter.trailRouteOf(nodes, lengthMetres = 1_800)

        assertEquals(listOf(LatLng(46.7865, -121.7356), LatLng(46.79, -121.734), LatLng(46.8022, -121.73)), route.points)
        assertEquals(1_800, route.lengthMetres)
        assertEquals(listOf(1_656.5, null, 2_033.5), route.heightsMetres)
    }

    /** A track node at [at] with BRouter's stored height [selev]; positions are micro-degrees offset by 90 and 180. */
    private fun node(at: LatLng, selev: Short): OsmPathElement =
        OsmPathElement.create(Math.round(at.lng * 1e6).toInt() + 180_000_000, Math.round(at.lat * 1e6).toInt() + 90_000_000, selev, null)
}
