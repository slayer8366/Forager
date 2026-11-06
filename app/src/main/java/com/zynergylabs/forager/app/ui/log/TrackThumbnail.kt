package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

/**
 * A track drawn as a small static path — journal redesign J3, C3 (owner rulings "Rows and Entries
 * cards" and "Join in memory (Recommended)", `docs/audits/2026-09-27-journal-j0-pulse.md`). Nothing
 * else in the app draws a track except the live MapLibre map (J0 B3); this is a Compose [Canvas]
 * `drawPath` over [projectTrackToBox], in the Tracks colour role ([RecordTypeStyle], J6).
 *
 * Fewer than two points draws nothing, and composes nothing: there is no line to show, and an empty
 * box would read as a track that failed to draw.
 *
 * The scaled path is remembered, keyed on [trackId], the box's size and the point count. The point
 * count is there because a track still recording gains points under the same id (the Records list
 * shows it, "· recording"), and a path keyed on id and size alone would stay stale.
 */
@Composable
internal fun TrackThumbnail(
    trackId: String,
    points: List<TrackPoint>,
    modifier: Modifier = Modifier,
) {
    TracksThumbnail(trackIds = listOf(trackId), tracks = listOf(points), modifier = modifier)
}

/**
 * Several tracks in one thumbnail, on one shared projection ([projectTracksToBox]) — journal redesign
 * J4, D5, for an Entries card keeping two or more tracks. Each track is its own subpath, so no line
 * joins the end of one walk to the start of the next. Composes nothing when no track has two points.
 * The path is remembered on every track's id and point count and the box size, [TrackThumbnail]'s
 * keys for each track.
 */
@Composable
internal fun TracksThumbnail(
    trackIds: List<String>,
    tracks: List<List<TrackPoint>>,
    modifier: Modifier = Modifier,
) {
    if (tracks.none { it.size >= 2 }) return
    val color = RecordTypeStyle.colors(RecordType.TRACKS).accent
    val strokePx = with(LocalDensity.current) { THUMBNAIL_STROKE.toPx() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val path = remember(trackIds, tracks.map { it.size }, boxSize) {
        val projected = projectTracksToBox(tracks, boxSize.width.toFloat(), boxSize.height.toFloat(), inset = strokePx)
        Path().apply {
            projected.forEach { track ->
                track.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
        }
    }
    Canvas(modifier = modifier.onSizeChanged { boxSize = it }) {
        drawPath(path, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private val THUMBNAIL_STROKE = 2.dp

/** One projected point, in the box's own pixel coordinates (origin top-left, y down). */
internal data class ThumbnailPoint(val x: Float, val y: Float)

/**
 * Projects [points] into a [width] × [height] box, [inset] in from every edge, by bounding box and
 * linear scale: pure, no Compose, so it is tested headless.
 *
 * - **Shape kept.** One scale for both axes (the smaller of the two that fit), centred on the axis
 *   with room to spare, so a north–south walk stays tall and thin rather than being stretched to
 *   fill the box.
 * - **Longitude scaled by the cosine of the middle latitude**, the plain equirectangular projection:
 *   a degree of longitude is shorter than a degree of latitude away from the equator (about 0.7 of
 *   it at 45° N), so without it every track would come out wider than it is.
 * - **North up:** larger latitude, smaller y.
 * - **Degenerate boxes.** A bounding box of zero width (every point on one meridian) or zero height
 *   scales by the other axis and sits centred on the flat one; a single location repeated puts
 *   every point at the box's centre. The inset is capped at half the box's shorter side, so an inset
 *   too large for the box leaves no room on that axis and the points sit on its centre line.
 * - **Fewer than two points: an empty list.** There is no line.
 *
 * Not handled: a track crossing the antimeridian (±180°) would project as spanning the globe. No
 * track in this app's reports comes near it.
 */
internal fun projectTrackToBox(points: List<TrackPoint>, width: Float, height: Float, inset: Float = 0f): List<ThumbnailPoint> =
    // One track is the several-track projection with one track in it (J4, D5 moved the arithmetic
    // there unchanged, so the two cannot drift apart).
    projectTracksToBox(listOf(points), width, height, inset).single()

/**
 * Several tracks projected into one box on **one shared projection** — journal redesign J4, D5
 * (owner ruling "All in one box (Recommended)"): an Entries card keeping two or more tracks draws
 * them all in its one thumbnail, scaled together, so their sizes and positions stay true to each
 * other. The rules are [projectTrackToBox]'s, applied to the bounding box of every track with two or
 * more points; one list out per track in, in order. A track with fewer than two points projects to an
 * empty list and takes no part in the shared box.
 */
internal fun projectTracksToBox(tracks: List<List<TrackPoint>>, width: Float, height: Float, inset: Float = 0f): List<List<ThumbnailPoint>> {
    val drawable = tracks.filter { it.size >= 2 }
    if (drawable.isEmpty()) return tracks.map { emptyList() }
    val frame = BoxFrame.around(drawable.flatten().map { LatLng(it.lat, it.lng) }, width, height, inset)
    return tracks.map { points -> if (points.size < 2) emptyList() else points.map { frame.project(it.lat, it.lng) } }
}

/**
 * One equirectangular projection of a set of locations into a box: [projectTracksToBox]'s arithmetic,
 * moved here unchanged (dispatch -616) so [projectWalkToBox] places its marks on the very same
 * projection rather than a second copy of it. The rules are [projectTrackToBox]'s.
 */
private class BoxFrame(
    private val minLat: Double,
    private val maxLat: Double,
    private val minLng: Double,
    private val lngScale: Double,
    private val scale: Double,
    private val left: Double,
    private val top: Double,
) {
    fun project(lat: Double, lng: Double): ThumbnailPoint = ThumbnailPoint(
        x = (left + (lng - minLng) * lngScale * scale).toFloat(),
        y = (top + (maxLat - lat) * scale).toFloat(),
    )

    companion object {
        /** The frame whose bounding box is [all]'s, which must not be empty. */
        fun around(all: List<LatLng>, width: Float, height: Float, inset: Float): BoxFrame {
            val minLat = all.minOf { it.lat }
            val maxLat = all.maxOf { it.lat }
            val minLng = all.minOf { it.lng }
            val maxLng = all.maxOf { it.lng }
            val lngScale = cos(Math.toRadians((minLat + maxLat) / 2.0))
            val spanX = (maxLng - minLng) * lngScale
            val spanY = maxLat - minLat
            val margin = min(inset.toDouble(), min(width, height) / 2.0).coerceAtLeast(0.0)
            val availW = max(0.0, width - 2.0 * margin)
            val availH = max(0.0, height - 2.0 * margin)
            val scale = when {
                spanX == 0.0 && spanY == 0.0 -> 0.0
                spanX == 0.0 -> availH / spanY
                spanY == 0.0 -> availW / spanX
                else -> min(availW / spanX, availH / spanY)
            }
            val left = margin + (availW - spanX * scale) / 2.0
            val top = margin + (availH - spanY * scale) / 2.0
            return BoxFrame(minLat, maxLat, minLng, lngScale, scale, left, top)
        }
    }
}

// ── Dispatch -616 (plan T10), amended by -618: the walk's drawing in its details sheet ──

/**
 * The waypoints the user dropped while [track] was recording: linked to it by `trackId` and ordinary
 * (no [com.zynergylabs.forager.app.domain.model.WaypointDesignation]), so the walk's own start and end
 * markers are left out (owner, -618: the list leaves them out; the drawing has its own start and end
 * dots). Oldest first, the order they were dropped in, as `WaypointDao.getForTrack` returns them. A
 * filter over the list the sheet already holds, not a new query: the sheet shows only what is in
 * memory where the row is (`RecordDetailsSheet`'s doc comment).
 */
internal fun waypointsDroppedOn(track: Track, waypoints: List<Waypoint>): List<Waypoint> =
    waypoints.filter { it.trackId == track.id && it.designation == null }.sortedBy { it.createdAtEpochMillis }

/**
 * Where the walk's drawing puts a dot. [start] is the first recorded point. [end] is the last, and only
 * once the walk has ended: while it records, its last point is where the walker is now, not where it
 * ended. [waypoints] are [waypointsDroppedOn]'s, in that order.
 */
internal data class WalkMarks(val start: LatLng?, val end: LatLng?, val waypoints: List<LatLng>)

internal fun walkMarks(track: Track, waypoints: List<Waypoint>): WalkMarks = WalkMarks(
    start = track.points.firstOrNull()?.let { LatLng(it.lat, it.lng) },
    end = track.points.lastOrNull()?.takeIf { track.endedAtEpochMillis != null }?.let { LatLng(it.lat, it.lng) },
    waypoints = waypointsDroppedOn(track, waypoints).map { LatLng(it.lat, it.lng) },
)

/** [projectWalkToBox]'s result: the line, and each mark on the same projection. */
internal data class ProjectedWalk(
    val line: List<ThumbnailPoint>,
    val start: ThumbnailPoint?,
    val end: ThumbnailPoint?,
    val waypoints: List<ThumbnailPoint>,
)

/**
 * The walk's line and its [marks] projected into one box together. The bounding box takes in every
 * mark as well as the line, so a waypoint dropped off the line (the '+' places it at the map's centre,
 * which need not be where the walker stood) is drawn inside the box rather than cut off at its edge;
 * with no mark off the line this is exactly [projectTrackToBox]. Fewer than two points: no line and no
 * marks, as the plain thumbnail draws nothing then.
 */
internal fun projectWalkToBox(points: List<TrackPoint>, marks: WalkMarks, width: Float, height: Float, inset: Float = 0f): ProjectedWalk {
    if (points.size < 2) return ProjectedWalk(emptyList(), null, null, emptyList())
    val all = points.map { LatLng(it.lat, it.lng) } + listOfNotNull(marks.start, marks.end) + marks.waypoints
    val frame = BoxFrame.around(all, width, height, inset)
    return ProjectedWalk(
        line = points.map { frame.project(it.lat, it.lng) },
        start = marks.start?.let { frame.project(it.lat, it.lng) },
        end = marks.end?.let { frame.project(it.lat, it.lng) },
        waypoints = marks.waypoints.map { frame.project(it.lat, it.lng) },
    )
}

/**
 * The walk's drawing in its details sheet (dispatch -616 as amended by -618; owner: "Dots on the
 * drawing + list (Recommended)"): [TrackThumbnail]'s line, plus a dot at the start, at the end and at
 * every waypoint dropped on the walk. The dots are not tappable; the list under the drawing is how a
 * waypoint is opened. Start and end differ by form, not colour alone: the start is a ring, the end a
 * filled dot, both in the Tracks colour; a waypoint is a filled dot in the Waypoints colour
 * ([RecordTypeStyle]). Composes nothing when the walk has fewer than two points, as [TrackThumbnail] does.
 */
@Composable
internal fun WalkThumbnail(track: Track, waypoints: List<Waypoint>, modifier: Modifier = Modifier) {
    if (track.points.size < 2) return
    val lineColor = RecordTypeStyle.colors(RecordType.TRACKS).accent
    val waypointColor = RecordTypeStyle.colors(RecordType.WAYPOINTS).accent
    val ringFill = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    val strokePx = with(density) { THUMBNAIL_STROKE.toPx() }
    val dotPx = with(density) { WALK_DOT_RADIUS.toPx() }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val marks = walkMarks(track, waypoints)
    val projected = remember(track.id, track.points.size, marks, boxSize) {
        // Inset by a dot's radius and the stroke, so a dot at the edge of the box is drawn whole.
        projectWalkToBox(track.points, marks, boxSize.width.toFloat(), boxSize.height.toFloat(), inset = dotPx + strokePx)
    }
    val path = remember(projected) {
        Path().apply { projected.line.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
    }
    Canvas(modifier = modifier.onSizeChanged { boxSize = it }) {
        drawPath(path, color = lineColor, style = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round))
        projected.waypoints.forEach { drawCircle(waypointColor, radius = dotPx, center = Offset(it.x, it.y)) }
        projected.end?.let { drawCircle(lineColor, radius = dotPx, center = Offset(it.x, it.y)) }
        projected.start?.let {
            drawCircle(ringFill, radius = dotPx, center = Offset(it.x, it.y))
            drawCircle(lineColor, radius = dotPx - strokePx / 2, center = Offset(it.x, it.y), style = Stroke(width = strokePx))
        }
    }
}

private val WALK_DOT_RADIUS = 4.dp
