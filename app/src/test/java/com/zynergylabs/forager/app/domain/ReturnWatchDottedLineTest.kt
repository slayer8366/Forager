package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.domain.model.TrackRecordingMode
import com.zynergylabs.forager.app.domain.wayhome.WayHome
import com.zynergylabs.forager.app.domain.wayhome.WayHomePart
import com.zynergylabs.forager.app.domain.wayhome.WayHomePartKind
import com.zynergylabs.forager.app.domain.wayhome.WayHomeResult
import com.zynergylabs.forager.app.domain.wayhome.WayHomeWatch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The dotted part of a re-routed way home counts as on course only while the walker heads back along it (dispatch
 * 2026-10-25-09 Amendments 1 and 2; RECORD -1637, -1640; Sorrel's 402-1).
 *
 * Wired as `AppContainer` wires the two watches: a real [WayHomeWatch] (its re-routes, its place on the line) beside a real
 * [ReturnWatch], each fix given to the return watch first and the way-home watch second, then kept, as
 * `TrackRecordingService` does (:223, :239, :244). Only the route search is faked: each re-route's dotted part is the
 * stray kept since Return, newest first, back to where the walker left the trail; the store has no write lag.
 *
 * Synthetic ground, no real place: the walk out runs north from the start along one meridian; the trail way home runs
 * parallel to it 80 m east. After Return the walker stands on the trail at 45.0015 and strays due east, one GPS reading
 * a second at 1.4 m/s. "d" is metres east of the trail. A re-route comes at each 50 m from the line shown: the first at
 * d = 50.4, the second at d = 100.8. Readings carry no accuracy, so the off-track line is 40 m exactly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReturnWatchDottedLineTest {

    private val scope = TestScope(StandardTestDispatcher())
    private val delivered = mutableListOf<Alert>()
    private val stored = mutableListOf<TrackPoint>()
    private var keptAtReturn = 0
    private var searches = 0

    private lateinit var wayHome: WayHomeWatch

    /** The walker's metres east of the trail, and the next reading's time. */
    private var d = 0.0
    private var t = 100_000L

    private val watch = ReturnWatch(
        computeReturnToStart = ComputeReturnToStartUseCase(),
        alertDelivery = { delivered += it },
        wayHomeTrail = { id -> shownTrailOf(wayHome.shown.value, id) },
    )

    init {
        wayHome = WayHomeWatch(
            scope = scope,
            readTrack = { id -> Result.success(Track(id, null, 0L, null, stored.toList())) },
            findWayHome = { track, _ -> searches++; WayHomeResult.AlongTrails(if (searches == 1) firstWayHome() else rerouted(track)) },
            isReturning = { id -> watch.state.value.let { it.trackId == id && it.isReturning } },
            errorLog = ErrorLog { _, _, _ -> },
        )
        watch.begin(TRACK, TrackRecordingMode.HIGH_ACCURACY)
        watch.setStartPoint(TRACK, point(45.0, 0.0, 1_000L))
        (0..6).forEach { i -> keep(point(45.0 + i * 0.0005, 0.0, 1_000L + i * 10_000L)) }
        watch.startReturn(TRACK)
        keptAtReturn = stored.size
        wayHome.find(TRACK, null)
        scope.runCurrent()
    }

    private fun at(lat: Double, metersEast: Double) = LatLng(lat, -122.0 + metersEast / METERS_PER_DEGREE_LNG)
    private fun point(lat: Double, metersEast: Double, t: Long) =
        TrackPoint(lat = lat, lng = -122.0 + metersEast / METERS_PER_DEGREE_LNG, altitude = null, accuracyMeters = null, timestampEpochMillis = t)

    private fun keep(p: TrackPoint) {
        watch.onKeptPoint(p)
        stored += p
    }

    /** One reading at [d] metres east of the trail, in the service's order, then a second passes. */
    private fun reading() {
        val p = point(WALK_LAT, TRAIL_EAST_METERS + d, t)
        watch.onFix(p, FixProvider.GPS)
        wayHome.onFix(p, FixProvider.GPS)
        keep(p)
        scope.runCurrent()
        t += 1_000L
    }

    /** East or west at walking pace to [target], one reading a second, the last one on it. */
    private fun walkTo(target: Double) {
        while (d != target) {
            d = if (target > d) minOf(target, d + PACE) else maxOf(target, d - PACE)
            reading()
        }
    }

    /** [seconds] readings where the walker stands, each shifted by the next of [jitter], cycled. */
    private fun pause(seconds: Int, jitter: List<Double> = listOf(0.0)) {
        val here = d
        repeat(seconds) { i ->
            d = here + jitter[i % jitter.size]
            reading()
        }
        d = here
    }

    private val generation get() = wayHome.shown.value!!.generation

    @Test
    fun `a steady stray out past two re-routes alerts once`() {
        reading()
        walkTo(130.0)

        assertEquals("the sample must include two re-routes", 3L, generation)
        assertEquals("walking out along the stray must alert once", 1, delivered.size)
    }

    @Test
    fun `standing at the far end of a stray, where the dotted line starts, alerts`() {
        reading()
        walkTo(52.0)
        pause(60, jitter = listOf(0.0, -3.0, 3.0, -2.0, 2.0, -1.0, 1.0))

        assertEquals("the sample must include the re-route", 2L, generation)
        assertEquals("a walker held at the line's start is walking out, not heading back", 1, delivered.size)
    }

    @Test
    fun `heading back along the dotted line clears off track, and a pause on it keeps it clear`() {
        reading()
        walkTo(110.0)
        walkTo(80.0)
        pause(60)

        assertEquals("the sample must include two re-routes and no third", 3L, generation)
        assertEquals("only the stray out alerted", 1, delivered.size)
        assertFalse("heading back along the dotted line, and stopping on it, is on course", watch.state.value.isOffTrack)
    }

    @Test
    fun `after heading back, walking out again past the line's start alerts again`() {
        reading()
        walkTo(110.0)
        walkTo(70.0)
        walkTo(145.0)

        assertEquals("the sample must include two re-routes and no third", 3L, generation)
        assertEquals("back on course along the dotted line, then a second stray out: two alerts", 2, delivered.size)
    }

    /** The way home at Return: across to the trail, down it, and across to the start. */
    private fun firstWayHome() = WayHome(
        trackBack = part(WayHomePartKind.TRACK_BACK, listOf(at(45.003, 0.0), at(45.003, TRAIL_EAST_METERS))),
        trail = part(WayHomePartKind.TRAIL, (6 downTo 0).map { i -> at(45.0 + i * 0.0005, TRAIL_EAST_METERS) }),
        trackToStart = part(WayHomePartKind.TRACK_TO_START, listOf(at(45.0, TRAIL_EAST_METERS), at(45.0, 0.0))),
        trailJointMeters = 0.0,
        regionId = 1L,
    )

    /** A re-route: the stray kept since Return, newest first, to where the walker left the trail, then the trail home. */
    private fun rerouted(track: Track): WayHome {
        val leftTrail = at(WALK_LAT, TRAIL_EAST_METERS)
        val stray = track.points.drop(keptAtReturn).reversed().map { LatLng(it.lat, it.lng) }
        return WayHome(
            trackBack = part(WayHomePartKind.TRACK_BACK, stray + leftTrail),
            trail = part(WayHomePartKind.TRAIL, listOf(leftTrail, at(45.001, TRAIL_EAST_METERS), at(45.0005, TRAIL_EAST_METERS), at(45.0, TRAIL_EAST_METERS))),
            trackToStart = part(WayHomePartKind.TRACK_TO_START, listOf(at(45.0, TRAIL_EAST_METERS), at(45.0, 0.0))),
            trailJointMeters = 0.0,
            regionId = 1L,
        )
    }

    private fun part(kind: WayHomePartKind, points: List<LatLng>) =
        WayHomePart(kind, points, points.zipWithNext { a, b -> GeoDistance.metersBetween(a, b) }.sum())

    private companion object {
        const val TRACK = "track-1"
        const val TRAIL_EAST_METERS = 80.0
        const val WALK_LAT = 45.0015
        const val PACE = 1.4

        /** Metres per degree of longitude at 45° N, near enough for tens of metres (as ReturnWatchTest uses). */
        const val METERS_PER_DEGREE_LNG = 78_620.0
    }
}
