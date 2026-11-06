package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.theme.MapPalette
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapSourceIds
import com.zynergylabs.forager.app.ui.map.layers.TRACK_WIDTH_ZOOM_STOPS
import com.zynergylabs.forager.app.ui.map.layers.ZoomWidthStop
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.geojson.FeatureCollection

/**
 * One overlay [LineLayer], described without constructing it: [LineLayer] calls a native
 * initialiser from its constructor, so a headless test cannot build one, but it can read this
 * (`SightingsMapOverlayDataTest`). [lineLayerFor] is the only thing that turns one into a layer, so
 * what the test reads is what the map draws. Widths are MapLibre style units, which are dp.
 */
internal data class LineLayerSpec(
    val layerId: String,
    val sourceId: String,
    /** The palette role this line is drawn in. */
    val colour: (MapPalette) -> Int,
    val widthDp: Float,
    /** `line-dasharray`, in multiples of [widthDp]; `null` for a solid line. */
    val dashPattern: List<Float>?,
    /** Round caps and joins (the tracks); `false` leaves MapLibre's butt caps and miter joins. */
    val roundCaps: Boolean,
    /**
     * How this line's width follows the zoom, as fractions of [widthDp] (track widths by zoom, owner,
     * 2026-09-28), or `null` for a constant [widthDp] at every zoom (the offline outline).
     * [lineWidthExpression] is what turns it into the layer's `line-width`.
     */
    val widthByZoom: List<ZoomWidthStop>? = null,
)

/**
 * [spec]'s width stops as (zoom, width in dp) pairs, in zoom order, or `null` when its width is a
 * constant: each stop's fraction of [LineLayerSpec.widthDp].
 */
internal fun lineWidthStops(spec: LineLayerSpec): List<Pair<Float, Float>>? =
    spec.widthByZoom?.sortedBy { it.zoom }?.map { it.zoom to spec.widthDp * it.fractionOfFullWidth }

/**
 * [spec]'s width in dp at [zoom], evaluated as MapLibre's linear `interpolate` does: linear between
 * stops, the end values outside them. What [lineWidthExpression] asks the map to draw, in a form a
 * headless test can read at any zoom.
 */
internal fun lineWidthAtZoom(spec: LineLayerSpec, zoom: Float): Float {
    val stops = lineWidthStops(spec)?.takeIf { it.isNotEmpty() } ?: return spec.widthDp
    if (zoom <= stops.first().first) return stops.first().second
    if (zoom >= stops.last().first) return stops.last().second
    val upper = stops.indexOfFirst { it.first >= zoom }
    val (z0, w0) = stops[upper - 1]
    val (z1, w1) = stops[upper]
    return w0 + (w1 - w0) * (zoom - z0) / (z1 - z0)
}

/**
 * The `line-width` [lineLayerFor] gives [spec]'s layer: `interpolate(linear, zoom, …)` over
 * [lineWidthStops] when it has stops (track widths by zoom, owner, 2026-09-28), the constant
 * [LineLayerSpec.widthDp] otherwise.
 */
internal fun lineWidthExpression(spec: LineLayerSpec): Expression {
    val stops = lineWidthStops(spec)?.takeIf { it.isNotEmpty() } ?: return Expression.literal(spec.widthDp)
    return Expression.interpolate(
        Expression.linear(),
        Expression.zoom(),
        *stops.map { (zoom, width) -> Expression.stop(zoom, width) }.toTypedArray(),
    )
}

/**
 * The track lines, in draw order: each track's casing immediately before it, so it draws directly
 * below it (colour build C2 (c)). A casing is the track's own line, [CASING_WIDTH_DP] wider on each
 * side, in [MapPalette.casing], and always solid: under the dashed breadcrumb it still outlines the
 * whole trail, so the dashes read as one path against a busy ground.
 *
 * Each track and its casing thin out together as the map zooms out ([TRACK_WIDTH_ZOOM_STOPS], owner "2 A",
 * 2026-09-29): the casing copies its track's stops, so the widths above are the full widths, at zoom
 * 18 and above, and the casing keeps its ratio to its line (9 to 6) at every zoom. At zoom 12 and
 * below that is a 2.25 dp casing over a 1.5 dp line, 0.375 dp a side, not [CASING_WIDTH_DP].
 */
internal fun trackLayerSpecs(): List<LineLayerSpec> {
    fun casingFor(track: LineLayerSpec, layerId: String) = track.copy(
        layerId = layerId,
        colour = MapPalette::casing,
        widthDp = track.widthDp + 2 * CASING_WIDTH_DP,
        dashPattern = null,
    )
    val breadcrumb = LineLayerSpec(
        layerId = BREADCRUMB_LAYER_ID,
        sourceId = BREADCRUMB_SOURCE_ID,
        colour = MapPalette::breadcrumb,
        widthDp = BREADCRUMB_STROKE_WIDTH_PX,
        dashPattern = BREADCRUMB_DASH_PATTERN.toList(),
        roundCaps = true,
        widthByZoom = TRACK_WIDTH_ZOOM_STOPS,
    )
    val keptTrack = LineLayerSpec(
        layerId = KEPT_TRACKS_LAYER_ID,
        sourceId = KEPT_TRACKS_SOURCE_ID,
        colour = MapPalette::keptTrack,
        widthDp = KEPT_TRACK_STROKE_WIDTH_PX,
        dashPattern = null,
        roundCaps = true,
        widthByZoom = TRACK_WIDTH_ZOOM_STOPS,
    )
    return listOf(
        casingFor(breadcrumb, BREADCRUMB_CASING_LAYER_ID),
        breadcrumb,
        casingFor(keptTrack, KEPT_TRACKS_CASING_LAYER_ID),
        keptTrack,
    )
}

/**
 * The offline region's outline (colour build C2 (c)): a dashed line in [MapPalette.casing],
 * [OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX] wide, dash 6dp and gap 4dp, with butt ends. The glyph
 * board's dash pattern; the region's fill is its own role, [MapPalette.offlineRegion], at
 * the registry's 0.2 fill opacity (`MAP_LAYER_REGISTRY`).
 */
internal fun offlineRegionOutlineSpec(): LineLayerSpec = LineLayerSpec(
    layerId = OFFLINE_REGION_CIRCLE_OUTLINE_LAYER_ID,
    sourceId = OFFLINE_REGION_CIRCLE_SOURCE_ID,
    colour = MapPalette::casing,
    widthDp = OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
    dashPattern = listOf(
        OFFLINE_REGION_OUTLINE_DASH_DP / OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
        OFFLINE_REGION_OUTLINE_GAP_DP / OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX,
    ),
    roundCaps = false,
)

/**
 * The border under the offline region's dashed outline (dispatch `2026-09-28-79`; owner: "The outline
 * should have a white border", "Yes the night outline only"), built as [trackLayerSpecs] builds a
 * track's casing: the outline's own line, [CASING_WIDTH_DP] wider on each side (4.5 dp in all, the
 * dashes' 1.5 dp in its middle), solid, on the same source, with the outline's butt caps and constant
 * width, in [MapPalette.offlineRegionBorder]. Solid so that the edge carries through the dashes' gaps
 * too. At night that role is white, drawn at the registry's line opacity, so over the darkest night
 * ground, where the black dashes alone could barely be made out (Part 1, check 5 (b)), the edge reads as
 * a light band with the dashes along it; by day the role is fully transparent and the layer draws
 * nothing. The registry places it directly below the outline (`MAP_LAYER_REGISTRY`).
 */
internal fun offlineRegionBorderSpec(): LineLayerSpec {
    val outline = offlineRegionOutlineSpec()
    return outline.copy(
        layerId = MapLayerIds.OFFLINE_REGION_BORDER,
        colour = MapPalette::offlineRegionBorder,
        widthDp = outline.widthDp + 2 * CASING_WIDTH_DP,
        dashPattern = null,
    )
}

/**
 * J8: the two line halos, each [JOURNAL_HALO_WIDTH_DP] wider on each side than what it lies under, solid,
 * in [MapPalette.journalEntry]: the offline region's under its 1.5 dp dashed outline (so the halo reads
 * through the dashes' gaps too), and the kept tracks' under their casing, thinning with the zoom in step
 * with the track and its casing ([TRACK_WIDTH_ZOOM_STOPS]), so at every zoom the halo keeps its ratio to
 * the line. Round joins, so the ring stays smooth round a track's bends and the region's circle.
 */
internal fun journalHaloLineSpecs(): List<LineLayerSpec> {
    val outline = offlineRegionOutlineSpec()
    val keptTrack = trackLayerSpecs().single { it.layerId == KEPT_TRACKS_LAYER_ID }
    return listOf(
        LineLayerSpec(
            layerId = MapLayerIds.JOURNAL_ENTRY_REGIONS,
            sourceId = MapSourceIds.JOURNAL_ENTRY_REGIONS,
            colour = MapPalette::journalEntry,
            widthDp = outline.widthDp + 2 * JOURNAL_HALO_WIDTH_DP,
            dashPattern = null,
            roundCaps = true,
        ),
        LineLayerSpec(
            layerId = MapLayerIds.JOURNAL_ENTRY_TRACKS,
            sourceId = MapSourceIds.JOURNAL_ENTRY_TRACKS,
            colour = MapPalette::journalEntry,
            widthDp = keptTrack.widthDp + 2 * CASING_WIDTH_DP + 2 * JOURNAL_HALO_WIDTH_DP,
            dashPattern = null,
            roundCaps = true,
            widthByZoom = keptTrack.widthByZoom,
        ),
    )
}

/**
 * J8: what each halo source receives, by source id, from [highlights]: the same pure builders the
 * records' own sources use, so a halo feature carries its record's id and geometry exactly as the
 * record's does. A plain function so a headless test reads what the sources get.
 */
internal fun journalHighlightFeatureCollections(highlights: JournalEntryHighlights): Map<String, FeatureCollection> = mapOf(
    MapSourceIds.JOURNAL_ENTRY_REGIONS to offlineRegionCirclesFeatureCollection(highlights.offlineRegionCircles),
    MapSourceIds.JOURNAL_ENTRY_TRACKS to keptTracksFeatureCollection(highlights.trackPolylines),
    MapSourceIds.JOURNAL_ENTRY_WAYPOINTS to pointsFeatureCollection(highlights.waypointMarkers),
    MapSourceIds.JOURNAL_ENTRY_FINDS to pointsFeatureCollection(highlights.findMarkers),
    MapSourceIds.JOURNAL_ENTRY_PHOTOS to pointsFeatureCollection(highlights.photoMarkers),
)

/** The [LineLayer] [spec] describes, in [palette]'s colours. Its source must already exist when it is added. */
internal fun lineLayerFor(spec: LineLayerSpec, palette: MapPalette): LineLayer {
    val properties = buildList {
        add(PropertyFactory.lineColor(spec.colour(palette)))
        add(PropertyFactory.lineWidth(lineWidthExpression(spec)))
        if (spec.roundCaps) {
            add(PropertyFactory.lineCap(Property.LINE_CAP_ROUND))
            add(PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND))
        }
        spec.dashPattern?.let { add(PropertyFactory.lineDasharray(it.toTypedArray())) }
    }
    return LineLayer(spec.layerId, spec.sourceId).withProperties(*properties.toTypedArray())
}

private const val BREADCRUMB_STROKE_WIDTH_PX = 6f


// Journal Stage 2d.
private const val KEPT_TRACK_STROKE_WIDTH_PX = 6f
private const val OFFLINE_REGION_CIRCLE_OUTLINE_WIDTH_PX = 1.5f

// The offline outline's dash and gap, in dp (colour build C2 (c), from the glyph board §1).
private const val OFFLINE_REGION_OUTLINE_DASH_DP = 6f
private const val OFFLINE_REGION_OUTLINE_GAP_DP = 4f

/**
 * A short dash with round line caps ([Property.LINE_CAP_ROUND], already set on the breadcrumb
 * layer) renders as a trail of small dots — "breadcrumbs" should look like breadcrumbs, the
 * project owner's own reasoning. `4:10` (a short mark, a gap two and a half times longer) is
 * tuned for that dot read, in the same line-width-relative units [PropertyFactory.lineDasharray]
 * always took (see [BREADCRUMB_STROKE_WIDTH_PX]).
 *
 * `internal`: this is the actual array [PropertyFactory.lineDasharray] receives in production, not
 * a copy. `SightingsMapOverlayDataTest` asserts it is non-empty — the closest a headless JVM test
 * can get to "the breadcrumb trail is still dashed" (see [searchCenterFeatureCollection]'s doc
 * comment for why nothing here can construct the real, native-backed [LineLayer] that actually
 * renders it). Whether MapLibre draws it as visibly dot-like on a real basemap is a hardware-only
 * question, not yet re-confirmed.
 */
internal val BREADCRUMB_DASH_PATTERN = arrayOf(0.4f, 1.0f)
