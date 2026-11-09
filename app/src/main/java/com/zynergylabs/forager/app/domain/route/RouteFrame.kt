package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox

/**
 * Where the map goes when a saved route opens (dispatch 2026-10-30-08, step 3: "moves the map to fit the whole route"): the
 * bounds of every point of every leg, padded by [sideDp] on the top, left and right, and on the bottom by [bottomDp], the route
 * card's peek height plus the same margin, so the route is not drawn under the card. As [com.zynergylabs.forager.app.domain.entryMapFrame]'s
 * bounds, plain minimum and maximum latitude and longitude: a route across the antimeridian is not handled.
 */
fun routeFrame(route: PlottedRoute, sideDp: Int, bottomDp: Int): EntryMapFrame.FitInsets {
    val points = route.legs.flatMap { it.line }
    return EntryMapFrame.FitInsets(
        bounds = GeoBoundingBox(
            north = points.maxOf { it.lat },
            south = points.minOf { it.lat },
            east = points.maxOf { it.lng },
            west = points.minOf { it.lng },
        ),
        topDp = sideDp,
        bottomDp = bottomDp,
        leftDp = sideDp,
        rightDp = sideDp,
    )
}
