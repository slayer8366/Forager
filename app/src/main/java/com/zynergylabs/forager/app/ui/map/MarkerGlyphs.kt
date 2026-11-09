package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.theme.MapPalette
import kotlin.math.ceil
import kotlin.math.max

/**
 * The map's bitmap marker silhouettes (colour build C2 (d)), each distinct in shape as well as
 * colour, since the owner found that "color isn't enough" to tell the markers apart. The geometry is
 * the glyph board's (`docs/audits/2026-09-26-marker-glyph-board.md` §1, with its §2 shape choices),
 * ported from its Python polygons to Android [Path]s, plus the search centre's reticle, which the
 * owner changed after the board (arms extending beyond the ring).
 *
 * Every dimension is in dp, measured from the top-left of the glyph's **fill extent** (its fill, not
 * counting the casing). The anchor is the point that sits on the feature's coordinate.
 *
 * Differences from the board's Python, recorded rather than silent:
 *  - **Waypoint.** The board's `pin_poly` is the head's upper half-circle joined to the tip, which
 *    drops the parts of the head's lower half that lie outside the triangle. Its §1 text describes
 *    today's `waypointPinBitmap` instead (the whole head circle and a triangle from its equator to
 *    the tip), and that is what is drawn here.
 *  - **Casing.** The board grew each polygon by 1.5dp with a round-joined stroke; here each fill part
 *    is stroked 3dp wide with round joins in the casing colour, then filled on top. That is the same
 *    footprint, drawn with the platform's stroker instead of a polygon approximation.
 *  - **Curves.** Arcs are true Path arcs and circles, not the board's 120- to 180-point polygons.
 *  - **Flag and find size.** The board's flag is 20 x 28 and its mushroom 24 x 26. Dispatch 2026-09-28-286 draws both
 *    evenly scaled to 25 dp tall, anchors included (the flag 17.86 x 25, the mushroom 23.08 x 25); the casing and detail
 *    strokes are not scaled. The owner, of an S22 photo: "I noticed the flag for the planned trip is a bit large compared
 *    to the other icons. Can its size be reduced a bit to visually align with the other icons? The finds icon is a bit
 *    large also, but not by much. Location pin icon is acceptable. Somewhere between the photo icon and location pin size
 *    would be preferable"; scope, "Everywhere". The coordinates in [parts] below are the design's, times [MarkerGlyph.scale].
 *    Dispatch 2026-09-28-358 then drew the flag alone at 23 dp tall ([PLANNED_TRIP_HEIGHT_DP]; 16.43 x 23, anchor (1.23, 23)),
 *    the find staying at 25. The owner, after seeing it in its 36 dp circle: "The circle size was perfect at 36dp since the trip
 *    icon fit neatly in it", and "the trip icon is a bit large still. Scale it down very slightly so that it fits more
 *    comfortably inside the circle." The 23 is the planner's number, not the owner's; scope again everywhere, as for -286.
 *  - **Search centre.** The board drew a plus 3dp from the centre, inside the ring. The owner's
 *    tweak puts the arms beyond the ring: see [SEARCH_CENTRE_ARM_DP].
 */
internal enum class MarkerGlyph(
    designWidthDp: Float,
    designHeightDp: Float,
    designAnchorXDp: Float,
    designAnchorYDp: Float,
    /**
     * An even scale applied to the design shape and its anchor, and to nothing else: the casing and detail strokes stay
     * the width they are (dispatch 2026-09-28-286). 1 for every glyph but the two it resized.
     */
    internal val scale: Float = 1f,
) {
    /** A teardrop pin with a 7dp hollow ring on its head; anchored at the tip. */
    WAYPOINT(22f, 28f, 11f, 28f),

    /** A mushroom: a half-ellipse dome with a flat underside and a straight stem; anchored at the stem foot. Designed 24 x 26, drawn at 25 dp tall ([GLYPH_TARGET_HEIGHT_DP]). */
    FIND(24f, 26f, 12f, 26f, scale = GLYPH_TARGET_HEIGHT_DP / 26f),

    /**
     * A flag: a pole with a rectangular pennant at its top; anchored at the pole foot. Designed 20 x 28, drawn at 23 dp
     * tall ([PLANNED_TRIP_HEIGHT_DP]), 16.43 x 23 with its anchor at (1.23, 23). Dispatch 2026-09-28-358, the owner: "the
     * trip icon is a bit large still. Scale it down very slightly so that it fits more comfortably inside the circle."
     */
    PLANNED_TRIP(20f, 28f, 1.5f, 28f, scale = PLANNED_TRIP_HEIGHT_DP / 28f),

    /** A 22dp rounded square with a camera in the casing colour; anchored at its centre. */
    PHOTO(22f, 22f, 11f, 11f),

    /** A reticle: a ring with crosshair arms that pass beyond it; anchored at its centre. */
    SEARCH_CENTRE(2 * SEARCH_CENTRE_ARM_DP, 2 * SEARCH_CENTRE_ARM_DP, SEARCH_CENTRE_ARM_DP, SEARCH_CENTRE_ARM_DP);

    /** The drawn size and anchor, in dp: the design's, times [scale]. */
    val widthDp: Float = designWidthDp * scale
    val heightDp: Float = designHeightDp * scale
    val anchorXDp: Float = designAnchorXDp * scale
    val anchorYDp: Float = designAnchorYDp * scale
}

/**
 * The height the find was drawn at, and the flag too until dispatch 2026-09-28-358 (dispatch 2026-09-28-286): between the
 * photo's 22 dp and the pin's 28 dp. A glyph's scale is its height over its designed height, so its proportions are kept.
 * The flag has a height of its own, [PLANNED_TRIP_HEIGHT_DP], so that shrinking it does not shrink the find.
 */
internal const val GLYPH_TARGET_HEIGHT_DP = 25f

/**
 * The height the planned-trip flag is drawn at (dispatch 2026-09-28-358). In its 36 dp circle (radius 18 dp, the fan
 * centring the flag on its fill extent) the flag's farthest corner from the circle's centre is sqrt((10s)^2 + (14s)^2) + 1.5 dp
 * of casing, s being its scale: 16.86 dp at 25 dp tall, which left 1.14 dp of circle, and 15.63 dp at 23, which leaves 2.37,
 * close to the pin's 2.5 at its top and tip. The 23 is the planner's number; the owner said "very slightly" and gave none.
 */
internal const val PLANNED_TRIP_HEIGHT_DP = 23f

/**
 * The `icon-offset`, in dp, that puts this glyph's fill-extent centre on the point its image is placed
 * at with `icon-anchor: center` (dispatch 2026-09-28-275). The image is centred on the glyph's anchor,
 * and `icon-offset` moves it from there, positive right and down, so it moves by the anchor minus the
 * extent's half: a pin whose body centre is 14dp above its tip moves 14dp down. Zero for a glyph
 * anchored at its centre. Only a fan's copies apply it, and **times the fold's progress** (dispatch 2026-09-28-299): this is
 * the offset when the fan is fully spread. Folded, a copy must draw where its original does, and the original has no offset
 * (its anchor is on the coordinate), so the copy's is nothing there. The owner: "After being fanned out, the icons return
 * to their start position. But sometimes they don't perfectly align back in their position when the animation finishes,
 * resulting in the icons snapping into place." At the full offset the pin (0, 14), the find (0, 12.5) and the flag
 * (-7.59, 12.5, as it was then; -358 made it (-6.98, 11.5)) jumped by that much when the copies were cleared; the photo and the search centre, at (0, 0), did not.
 * Everywhere else the anchor is on the coordinate.
 */
internal fun MarkerGlyph.fanCentringOffsetDp(): FanOffset = FanOffset(anchorXDp - widthDp / 2, anchorYDp - heightDp / 2)

/** How a [GlyphPart] is painted. Only [FILL] parts are cased. */
internal enum class GlyphPaint {
    /** The glyph's body: cased, then filled in its role colour. */
    FILL,

    /** Detail drawn on the body in the casing colour (the waypoint's ring, the camera). */
    DETAIL_IN_CASING,

    /** Detail drawn on a casing-colour detail in the fill colour (the camera's lens). */
    DETAIL_IN_FILL,
}

internal class GlyphPart(val path: Path, val paint: GlyphPaint)

/**
 * A drawn glyph: the bitmap, and the anchor's pixel in it. The anchor is always the bitmap's exact
 * centre (both dimensions are even), so the layer registers the image with `icon-anchor: center`
 * and the anchor lands on the coordinate without relying on `icon-offset`'s units.
 */
internal class GlyphImage(val bitmap: Bitmap, val anchorXPx: Int, val anchorYPx: Int)

/**
 * The reticle's arm half-length: each arm runs from the centre to 13dp out, 4dp past the ring's outer
 * edge ([SEARCH_CENTRE_RING_RADIUS_DP] + half of [SEARCH_CENTRE_STROKE_DP] = 9dp) and 2.5dp past the
 * ring's casing. Chosen (the dispatch asked for arms that clearly pass the ring's outer edge): 4dp is
 * two stroke widths beyond it, so the arms read as crossing the ring rather than touching it.
 */
internal const val SEARCH_CENTRE_ARM_DP = 13f

/** The reticle ring's radius at its stroke's centre line: today's search-centre circle radius (glyph board §2.5). */
internal const val SEARCH_CENTRE_RING_RADIUS_DP = 8f

/** The reticle's ring and arm stroke width: today's search-centre stroke (glyph board §2.5). */
internal const val SEARCH_CENTRE_STROKE_DP = 2f

/** Extra transparent margin beyond the casing, so the antialiased casing edge is never clipped. */
private const val GLYPH_MARGIN_DP = 1f

/** Each glyph's parts, in dp from the top-left of its fill extent, in drawing order within each [GlyphPaint]. */
internal fun MarkerGlyph.parts(): List<GlyphPart> = when (this) {
    MarkerGlyph.WAYPOINT -> listOf(
        GlyphPart(circle(11f, 11f, 11f), GlyphPaint.FILL),
        GlyphPart(
            Path().apply {
                moveTo(0f, 11f)
                lineTo(11f, 28f)
                lineTo(22f, 11f)
                close()
            },
            GlyphPaint.FILL,
        ),
        // A ring 7dp across (outer radius 3.5dp), 1.5dp stroke, on the head centre; its hollow stays
        // the pin's fill (glyph board §2.3).
        GlyphPart(annulus(11f, 11f, outer = 3.5f, inner = 2f), GlyphPaint.DETAIL_IN_CASING),
    )
    MarkerGlyph.FIND -> listOf(
        // The dome: the upper half of an ellipse rx 12, ry 14 centred at (12, 14), flat underside at y 14, in design
        // coordinates times [scale] (the design is 24 x 26; see [MarkerGlyph.scale]).
        GlyphPart(
            Path().apply {
                arcTo(RectF(0f, 0f, 24f * scale, 28f * scale), 180f, 180f, true)
                close()
            },
            GlyphPaint.FILL,
        ),
        GlyphPart(rect(8f * scale, 13.5f * scale, 16f * scale, 26f * scale), GlyphPaint.FILL),
    )
    MarkerGlyph.PLANNED_TRIP -> listOf(
        GlyphPart(rect(0f, 0f, 3f * scale, 28f * scale), GlyphPaint.FILL),
        GlyphPart(rect(3f * scale, 0f, 20f * scale, 12f * scale), GlyphPaint.FILL),
    )
    MarkerGlyph.PHOTO -> listOf(
        GlyphPart(roundRect(0f, 0f, 22f, 22f, 5f), GlyphPaint.FILL),
        // A hand-drawn camera approximating Material PhotoCamera (glyph board §2.4).
        GlyphPart(roundRect(4f, 7f, 18f, 17f, 1.5f), GlyphPaint.DETAIL_IN_CASING),
        GlyphPart(rect(8f, 5f, 14f, 8f), GlyphPaint.DETAIL_IN_CASING),
        GlyphPart(circle(11f, 12f, 3.2f), GlyphPaint.DETAIL_IN_FILL),
    )
    MarkerGlyph.SEARCH_CENTRE -> {
        val c = SEARCH_CENTRE_ARM_DP
        val half = SEARCH_CENTRE_STROKE_DP / 2
        listOf(
            GlyphPart(
                annulus(c, c, outer = SEARCH_CENTRE_RING_RADIUS_DP + half, inner = SEARCH_CENTRE_RING_RADIUS_DP - half),
                GlyphPaint.FILL,
            ),
            GlyphPart(rect(0f, c - half, 2 * c, c + half), GlyphPaint.FILL),
            GlyphPart(rect(c - half, 0f, c + half, 2 * c), GlyphPaint.FILL),
        )
    }
}

/**
 * Draws [glyph] in [fill], cased [CASING_WIDTH_DP] on each side in [casing], at [density] px per dp.
 * The bitmap is padded symmetrically round the anchor by the glyph's farthest extent from it, plus the
 * casing, plus a 1dp margin, so the anchor is its exact centre and the casing is never clipped.
 */
internal fun drawGlyph(glyph: MarkerGlyph, density: Float, fill: Int, casing: Int): GlyphImage {
    val pad = CASING_WIDTH_DP + GLYPH_MARGIN_DP
    val halfWidthPx = ceil((max(glyph.anchorXDp, glyph.widthDp - glyph.anchorXDp) + pad) * density).toInt()
    val halfHeightPx = ceil((max(glyph.anchorYDp, glyph.heightDp - glyph.anchorYDp) + pad) * density).toInt()
    val bitmap = createBitmap(2 * halfWidthPx, 2 * halfHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.translate(halfWidthPx - glyph.anchorXDp * density, halfHeightPx - glyph.anchorYDp * density)
    canvas.scale(density, density)

    val parts = glyph.parts()
    // The casing is a stroke over the part's outline plus the part filled, drawn as two passes. One
    // FILL_AND_STROKE pass is not used: under it an even-odd part (the reticle's ring) lost its hole
    // and came out solid, which MarkerGlyphsTest caught on the reticle.
    val casingStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = casing
        style = Paint.Style.STROKE
        strokeWidth = 2 * CASING_WIDTH_DP
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    val casingFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = casing; style = Paint.Style.FILL }
    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill; style = Paint.Style.FILL }
    // Every casing first, then every fill: a part's casing never covers a neighbouring part's fill.
    parts.filter { it.paint == GlyphPaint.FILL }.forEach {
        canvas.drawPath(it.path, casingFill)
        canvas.drawPath(it.path, casingStroke)
    }
    parts.filter { it.paint == GlyphPaint.FILL }.forEach { canvas.drawPath(it.path, fillPaint) }
    parts.filter { it.paint == GlyphPaint.DETAIL_IN_CASING }.forEach { canvas.drawPath(it.path, casingFill) }
    parts.filter { it.paint == GlyphPaint.DETAIL_IN_FILL }.forEach { canvas.drawPath(it.path, fillPaint) }
    return GlyphImage(bitmap, halfWidthPx, halfHeightPx)
}

/**
 * J8: how far a journal-entry halo reaches beyond the record's own casing, on each side. The marker
 * halos ([drawGlyphHalo]) and the line halos (`SightingsMap`'s `journalHaloLineSpecs`) both use it.
 */
internal const val JOURNAL_HALO_WIDTH_DP = 3f

/**
 * J8: [glyph]'s halo — its fill parts grown by [CASING_WIDTH_DP] plus [JOURNAL_HALO_WIDTH_DP] on each
 * side, all in [halo] — drawn under the glyph's own marker so that a ring [JOURNAL_HALO_WIDTH_DP] wide
 * shows around the marker's casing. Laid out as [drawGlyph] lays out a marker: padded symmetrically
 * round the anchor by the glyph's farthest extent, the halo's reach and a 1dp margin, so the anchor is
 * the bitmap's exact centre and `icon-anchor: center` puts halo and marker on the same coordinate.
 */
internal fun drawGlyphHalo(glyph: MarkerGlyph, density: Float, halo: Int): GlyphImage {
    val reach = CASING_WIDTH_DP + JOURNAL_HALO_WIDTH_DP
    val pad = reach + GLYPH_MARGIN_DP
    val halfWidthPx = ceil((max(glyph.anchorXDp, glyph.widthDp - glyph.anchorXDp) + pad) * density).toInt()
    val halfHeightPx = ceil((max(glyph.anchorYDp, glyph.heightDp - glyph.anchorYDp) + pad) * density).toInt()
    val bitmap = createBitmap(2 * halfWidthPx, 2 * halfHeightPx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.translate(halfWidthPx - glyph.anchorXDp * density, halfHeightPx - glyph.anchorYDp * density)
    canvas.scale(density, density)
    // The fill and a stroke over its outline, as two passes, for the reason drawGlyph gives.
    val haloFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = halo; style = Paint.Style.FILL }
    val haloStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = halo
        style = Paint.Style.STROKE
        strokeWidth = 2 * reach
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    glyph.parts().filter { it.paint == GlyphPaint.FILL }.forEach {
        canvas.drawPath(it.path, haloFill)
        canvas.drawPath(it.path, haloStroke)
    }
    return GlyphImage(bitmap, halfWidthPx, halfHeightPx)
}

/**
 * The map's bitmap markers: the `Style` image id each is registered under, its glyph, and its role.
 * `SightingsMap`'s `initializeOverlayLayers` registers exactly these, through [markerIconImage].
 */
internal enum class MarkerIcon(
    val imageId: String,
    val glyph: MarkerGlyph,
    val colour: (MapPalette) -> Int,
    /** J8: a journal-entry halo image ([drawGlyphHalo]) rather than a marker. */
    val halo: Boolean = false,
) {
    WAYPOINT("waypoint-pin", MarkerGlyph.WAYPOINT, MapPalette::waypoint),
    FIND("find-mushroom", MarkerGlyph.FIND, MapPalette::find),
    PLANNED_TRIP("planned-trip-flag", MarkerGlyph.PLANNED_TRIP, MapPalette::plannedTrip),
    PHOTO("photo-square", MarkerGlyph.PHOTO, MapPalette::photo),
    SEARCH_CENTRE("search-centre-reticle", MarkerGlyph.SEARCH_CENTRE, MapPalette::searchCentre),

    // J8: the halos under the three marker kinds an entry keeps.
    WAYPOINT_JOURNAL_HALO("waypoint-journal-halo", MarkerGlyph.WAYPOINT, MapPalette::journalEntry, halo = true),
    FIND_JOURNAL_HALO("find-journal-halo", MarkerGlyph.FIND, MapPalette::journalEntry, halo = true),
    PHOTO_JOURNAL_HALO("photo-journal-halo", MarkerGlyph.PHOTO, MapPalette::journalEntry, halo = true),
}

/** [icon]'s glyph drawn in [palette]'s colours: its own role's fill and the palette's casing, or, for a halo, its halo. */
internal fun markerIconImage(icon: MarkerIcon, palette: MapPalette, density: Float): GlyphImage =
    if (icon.halo) {
        drawGlyphHalo(icon.glyph, density, halo = icon.colour(palette))
    } else {
        drawGlyph(icon.glyph, density, fill = icon.colour(palette), casing = palette.casing)
    }

private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

private fun annulus(cx: Float, cy: Float, outer: Float, inner: Float) = Path().apply {
    fillType = Path.FillType.EVEN_ODD
    addCircle(cx, cy, outer, Path.Direction.CW)
    addCircle(cx, cy, inner, Path.Direction.CW)
}

private fun rect(left: Float, top: Float, right: Float, bottom: Float) =
    Path().apply { addRect(left, top, right, bottom, Path.Direction.CW) }

private fun roundRect(left: Float, top: Float, right: Float, bottom: Float, r: Float) =
    Path().apply { addRoundRect(RectF(left, top, right, bottom), r, r, Path.Direction.CW) }
