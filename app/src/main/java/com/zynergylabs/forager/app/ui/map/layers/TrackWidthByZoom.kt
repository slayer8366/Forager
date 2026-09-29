package com.zynergylabs.forager.app.ui.map.layers

/**
 * One stop of a track line's width across zoom: at [zoom], the line is [fractionOfFullWidth] of its
 * full width (the `widthDp` of its `LineLayerSpec`). MapLibre's `interpolate` is linear between
 * stops and holds the end values outside them, so the first stop's fraction applies at every zoom
 * below it and the last stop's at every zoom above it.
 */
internal data class ZoomWidthStop(val zoom: Float, val fractionOfFullWidth: Float)

/**
 * Track widths by zoom. The stops are the owner's ruling "2 A" (2026-09-29,
 * `docs/plans/journal-redesign.md`, "Tracks by zoom, revised", verbatim "2 A, 3 I'll take your
 * recommendations"), which replaced the planner's 2026-09-28-34 proposal (today's width at zoom 15 and
 * above, 40% of it at zoom 11 and below). They came from the original request (owner, 2026-09-28: "At some
 * point in zooming out, the tracks get muddied up from the thickness + distance. Can we have the track lines
 * thin out as we zoom out?"): full width, 100%, at zoom 18 and above (a 6 dp line and a 1.5 dp casing each
 * side, 9 dp in all); about 67% at zoom 16; about 42% at 14; 25% at zoom 12 and below (a 1.5 dp line, 2.25 dp
 * with its casing); linear between stops. Every track line and its casing uses these stops, so a casing keeps
 * its ratio to its line at every zoom, and the highlight halo follows them as before.
 *
 * Defined once, here, so a tweak is one edit. The offline outline does not use them: it is 1.5 dp at every
 * zoom, as the earlier dispatch asked. `line-dasharray` is in multiples of the line width, so the breadcrumb's
 * dots and gaps shrink with it (a device-only look).
 */
internal val TRACK_WIDTH_ZOOM_STOPS: List<ZoomWidthStop> = listOf(
    ZoomWidthStop(zoom = 12f, fractionOfFullWidth = 0.25f),
    ZoomWidthStop(zoom = 14f, fractionOfFullWidth = 0.42f),
    ZoomWidthStop(zoom = 16f, fractionOfFullWidth = 0.67f),
    ZoomWidthStop(zoom = 18f, fractionOfFullWidth = 1f),
)
