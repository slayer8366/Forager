package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Dispatch -616 (plan T10) as amended by -618 (owner: "Dots on the drawing + list (Recommended)"): the
 * walk's drawing in its details sheet carries a start dot, an end dot and a dot for every waypoint
 * dropped on the walk. Which marks ([walkMarks], [waypointsDroppedOn]) and where they land
 * ([projectWalkToBox]) are pure and tested here headless; the drawing itself is a Canvas and is not.
 */
class WalkThumbnailTest {

    private fun pt(lat: Double, lng: Double, t: Long = 0L) =
        TrackPoint(lat = lat, lng = lng, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private fun wp(id: String, lat: Double, lng: Double, trackId: String?, createdAt: Long, designation: WaypointDesignation? = null) =
        Waypoint(id = id, lat = lat, lng = lng, altitude = null, name = id, note = "", createdAtEpochMillis = createdAt, trackId = trackId, designation = designation)

    private val walk = Track(
        id = "walk",
        name = null,
        startedAtEpochMillis = 0L,
        endedAtEpochMillis = 10L,
        points = listOf(pt(45.00, -122.0), pt(45.01, -122.0), pt(45.02, -122.0)),
    )

    @Test
    fun `the waypoints dropped on a walk are its own ordinary ones, oldest first, without its start and end markers`() {
        val waypoints = listOf(
            wp("later", 45.015, -122.0, "walk", createdAt = 300L),
            wp("origin", 45.0, -122.0, "walk", createdAt = 100L, designation = WaypointDesignation.ORIGIN),
            wp("end", 45.02, -122.0, "walk", createdAt = 900L, designation = WaypointDesignation.END),
            wp("earlier", 45.005, -122.0, "walk", createdAt = 200L),
            wp("other-walk", 45.01, -122.0, "another", createdAt = 150L),
            wp("standalone", 45.01, -122.0, null, createdAt = 160L),
        )

        assertEquals(listOf("earlier", "later"), waypointsDroppedOn(walk, waypoints).map { it.id })
    }

    @Test
    fun `an ended walk is marked at its first and last points and at each dropped waypoint`() {
        val marks = walkMarks(walk, listOf(wp("w", 45.005, -121.99, "walk", createdAt = 1L)))

        assertEquals(LatLng(45.00, -122.0), marks.start)
        assertEquals(LatLng(45.02, -122.0), marks.end)
        assertEquals(listOf(LatLng(45.005, -121.99)), marks.waypoints)
    }

    @Test
    fun `a walk still recording has a start and no end, since its last point is not where it ended`() {
        val marks = walkMarks(walk.copy(endedAtEpochMillis = null), emptyList())

        assertEquals(LatLng(45.00, -122.0), marks.start)
        assertNull(marks.end)
    }

    @Test
    fun `the line and every mark share one projection, and a waypoint off the line widens the box to keep it in`() {
        // A north-south line at the equator and a waypoint 0.02 deg east of its south end: the box is
        // square (0.02 by 0.02), so the line sits on the left edge and the waypoint on the right.
        val points = listOf(pt(0.0, 0.0), pt(0.02, 0.0))
        val marks = WalkMarks(start = LatLng(0.0, 0.0), end = LatLng(0.02, 0.0), waypoints = listOf(LatLng(0.0, 0.02)))

        val out = projectWalkToBox(points, marks, width = 100f, height = 100f)

        assertEquals(listOf(0f to 100f, 0f to 0f), out.line.map { it.x to it.y }.rounded())
        assertEquals(0f to 100f, out.start?.let { it.x to it.y }?.rounded())
        assertEquals(0f to 0f, out.end?.let { it.x to it.y }?.rounded())
        assertEquals(listOf(100f to 100f), out.waypoints.map { it.x to it.y }.rounded())
    }

    @Test
    fun `with no marks off the line the walk projects exactly as the plain track thumbnail does`() {
        val marks = walkMarks(walk, emptyList())

        val out = projectWalkToBox(walk.points, marks, width = 80f, height = 120f, inset = 4f)

        assertEquals(projectTrackToBox(walk.points, width = 80f, height = 120f, inset = 4f), out.line)
    }

    @Test
    fun `fewer than two points draws no line and no marks`() {
        val one = listOf(pt(45.0, -122.0))
        val out = projectWalkToBox(one, WalkMarks(LatLng(45.0, -122.0), null, listOf(LatLng(45.1, -122.0))), width = 100f, height = 100f)

        assertEquals(emptyList<ThumbnailPoint>(), out.line)
        assertNull(out.start)
        assertNull(out.end)
        assertEquals(emptyList<ThumbnailPoint>(), out.waypoints)
    }

    private fun Pair<Float, Float>.rounded() = Math.round(first * 100) / 100f to Math.round(second * 100) / 100f
    private fun List<Pair<Float, Float>>.rounded() = map { it.rounded() }
}
