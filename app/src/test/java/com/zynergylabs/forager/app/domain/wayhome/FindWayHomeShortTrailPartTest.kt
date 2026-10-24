package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A trail part shorter than 100 m (`WAY_HOME_SHORT_TRAIL_METERS`) gives the track home with no note, before the length check
 * runs (Sorrel's review of #389, M-389-2; the owner's option (c), "No note if short", RECORD -1597; 100 m, RECORD -1598;
 * dispatch 2026-10-23-09 Amendment 3, fix 2). Without it, a walk that crosses a trail can trip the 1.5× check and read
 * "This route leaves your downloaded area", which is false and tells the walker to go and download something.
 *
 * Same synthetic ground as `FindWayHomeUseCaseTest`: a trail running east from the start for a kilometre. Track points
 * here are at least 30 m apart, so the scan tests every one of them ([WAY_HOME_SCAN_SPACING_METERS] is 20 m).
 */
class FindWayHomeShortTrailPartTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    private val snapper = FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0)))

    /**
     * A walk 200 m east of the start that crosses the trail from south to north. The first point within 25 m of the trail
     * is 20 m south of it, the last is 20 m north of it: 40 m of track between them.
     */
    private val crossing = Ground.track(
        *(-200..-20 step 30).map { at(200.0, it.toDouble()) }.toTypedArray(),
        *(20..200 step 30).map { at(200.0, it.toDouble()) }.toTypedArray(),
    )

    /**
     * The engine's route across the trail between where it matched the two points. Its ends lie 5 m either side of the
     * crossing, so each straight joint to the track point is √(5² + 20²) ≈ 20.6 m and the two together about 41.2 m.
     */
    private fun routeAcross(lengthMetres: Int) = FakeRouter(FakeRouter.found(lengthMetres, at(205.0, 0.0), at(195.0, 0.0)))

    /** A walk that starts on the trail, follows it 30 m east, and leaves it due south: entry and leave on the trail, 30 m of track apart. */
    private val shortStretch = Ground.track(at(0.0, 0.0), at(30.0, 0.0), at(30.0, -40.0), at(30.0, -80.0))

    private fun routeAlong(lengthMetres: Int) = FakeRouter(FakeRouter.found(lengthMetres, at(30.0, 0.0), at(0.0, 0.0)))

    private fun find(track: Track, router: FakeRouter) =
        runBlocking { FindWayHomeUseCase(FakeRegions(Ground.region()), router, snapper, errorLog)(track, null) }

    private fun assertTrackOnly(expected: WayHomeNote?, result: WayHomeResult) {
        assertEquals(WayHomeResult.TrackOnly(expected), result)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log names the note: ${logs.single()}", logs.single().contains(expected?.name ?: "no note"))
    }

    @Test
    fun `a walk that crosses a trail gets the track home with no note, not a route leaving the area`() {
        // The trail part is 25 m plus about 41.2 m of joints, 66.2 m: over 1.5 times the 40 m of track, under 100 m.
        val router = routeAcross(25)
        assertTrackOnly(null, find(crossing, router))
        assertEquals("the route across was found before it was judged short", 1, router.asked.size)
    }

    @Test
    fun `the joints count toward the short length`() {
        // 60 m of engine route alone is under 100 m; with the joints the trail part is about 101.2 m, so the length check
        // runs, and 101.2 m is over 1.5 times the 40 m of track.
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find(crossing, routeAcross(60)))
    }

    @Test
    fun `a trail part just under 100 m has no note, and one of 100 m goes on to the length check`() {
        // No joints here: entry and leave are on the trail. 1.5 times the 30 m of track is 45 m, so the length check
        // flags anything it sees.
        assertTrackOnly(null, find(shortStretch, routeAlong(99)))

        logs.clear()
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find(shortStretch, routeAlong(100)))

        logs.clear()
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find(shortStretch, routeAlong(101)))
    }

    @Test
    fun `a short trail part outside the area's rectangle still leaves the area`() {
        // The rectangle check runs before the short check: a route that leaves the cut data is said to, however short.
        val router = FakeRouter(FakeRouter.found(50, at(30.0, 0.0), at(15.0, 20_000.0), at(0.0, 0.0)))
        assertTrackOnly(WayHomeNote.LEAVES_AREA, find(shortStretch, router))
    }
}
