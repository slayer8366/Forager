package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng

/**
 * Where a day entry's map opens (owner, 2026-09-28: "Can the entry map open to their last recorded
 * track location, zoomed in to where you see in the next photo?", chose "Fit all kept records").
 */
sealed interface EntryMapFrame {
    /** Fit [bounds] with [paddingDp] on every side, zoomed no closer than [maxZoom]. */
    data class Fit(
        val bounds: GeoBoundingBox,
        val paddingDp: Int = ENTRY_MAP_FRAME_PADDING_DP,
        val maxZoom: Double = ENTRY_MAP_FRAME_MAX_ZOOM,
    ) : EntryMapFrame

    /** Everything the entry keeps is at one place: centre on it at [zoom]. */
    data class SinglePoint(val at: LatLng, val zoom: Double = ENTRY_MAP_SINGLE_POINT_ZOOM) : EntryMapFrame
}

/** The padding around the fitted bounds, in dp. */
const val ENTRY_MAP_FRAME_PADDING_DP = 48

/** The closest a fit may zoom. */
const val ENTRY_MAP_FRAME_MAX_ZOOM = 17.0

/** The zoom an entry whose kept records are all at one place opens at. */
const val ENTRY_MAP_SINGLE_POINT_ZOOM = 16.0

/**
 * Where [data]'s map opens: the bounds of every drawable point it keeps (kept tracks' points, finds,
 * located photos and waypoints, [CartographyEntryMapData.drawablePoints]), as a [EntryMapFrame.Fit];
 * a [EntryMapFrame.SinglePoint] when every one of them is at the same place, which is how "a single
 * point" is read (planner message 2026-09-28-35); and `null` when there is nothing to frame, where
 * the screen shows no map at all, as it always has.
 *
 * **Kept offline regions never count** (planner message 2026-09-28-35, option (c)): a region is where
 * tiles were downloaded, not where anything happened, and a regions-only entry has no map at all
 * (plate pulse, owner ruling on item 4, "a green circle with nothing in it is not a day";
 * [CartographyEntryMapData.isEmpty]). So a region is never in the frame and can never pull it away
 * from the day's own records, which [CartographyEntryMapData.allPoints] (the old framing's input) did.
 *
 * The bounds are plain minimum and maximum latitude and longitude, like
 * [GeoDistance.boundingRegion]'s box: records on both sides of the antimeridian would give a box
 * around the world. Not handled, as there is no data showing that case.
 */
fun entryMapFrame(data: CartographyEntryMapData): EntryMapFrame? {
    val points = data.drawablePoints
    if (points.isEmpty()) return null
    val distinct = points.distinct()
    if (distinct.size == 1) return EntryMapFrame.SinglePoint(distinct.single())
    return EntryMapFrame.Fit(
        GeoBoundingBox(
            north = points.maxOf { it.lat },
            south = points.minOf { it.lat },
            east = points.maxOf { it.lng },
            west = points.minOf { it.lng },
        ),
    )
}
