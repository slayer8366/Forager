package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cap on a trail part when the length check is skipped (RECORD -1603, "Add a cap in part 2"; RECORD -1613, the
 * owner: "150 m, no note"; dispatch 2026-10-24-10 Amendment 1, PR A). At 100 m or less walked between the two trail
 * points the 1.5× check does not run (RECORD -1600), so without a cap a crossing where the ways only meet far away
 * could be handed a very long route (Sorrel's re-check of #389, L-389R-1). Over 150 m (1.5 × 100) the route is not
 * used: the walker's track is the way home, with no note, logged once.
 *
 * Same synthetic ground as `FindWayHomeShortWalkTest`: a trail running east from the start; the walk starts on it,
 * dips south and comes back onto it, so the entry and leave points are on the trail with no joints.
 */
class FindWayHomeCapTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    private val snapper = FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0)))

    private fun find(track: Track, router: FakeRouter) =
        runBlocking { FindWayHomeUseCase(FakeRegions(Ground.region()), router, snapper, errorLog)(track, null) }

    /** 80 + [east] m walked between entry (0, 0) and leave ([east], 0), both on the trail. */
    private fun detourOf(east: Double) = Ground.track(
        at(0.0, 0.0), at(0.0, -40.0), at(east, -40.0), at(east, 0.0), at(east + 30.0, -30.0), at(east + 60.0, -60.0),
    )

    private fun routeOf(lengthMetres: Int, east: Double) = FakeRouter(FakeRouter.found(lengthMetres, at(east, 0.0), at(0.0, 0.0)))

    private fun trailMetersOf(result: WayHomeResult): Double {
        assertTrue("along trails, not $result (log: $logs)", result is WayHomeResult.AlongTrails)
        assertEquals("nothing fell back, nothing logged", emptyList<String>(), logs)
        return (result as WayHomeResult.AlongTrails).wayHome.trail.meters
    }

    @Test
    fun `at 98 m walked a 149 m trail part is used`() {
        assertEquals(149.0, trailMetersOf(find(detourOf(18.0), routeOf(149, 18.0))), 0.05)
    }

    @Test
    fun `at 98 m walked a 151 m trail part is not used, and the track is the way home with no note, logged once`() {
        val result = find(detourOf(18.0), routeOf(151, 18.0))
        assertEquals("the track home, with no note (log: $logs)", WayHomeResult.TrackOnly(null), result)
        assertEquals("logged once: $logs", 1, logs.size)
        assertTrue("the log names the cap and the metres: $logs", logs.single().contains("151 m") && logs.single().contains("150"))
    }

    @Test
    fun `the cap applies only when the length check is skipped - at 102 m walked a 152 m trail part is used`() {
        // 152 m is over the cap but under 1.5 × 102 = 153 m, so the length check, which runs here, passes it.
        assertEquals(152.0, trailMetersOf(find(detourOf(22.0), routeOf(152, 22.0))), 0.05)
    }

    @Test
    fun `a capped route outside the area's rectangle still leaves the area`() {
        // The rectangle check runs first, as it does for every route.
        val router = FakeRouter(FakeRouter.found(400, at(18.0, 0.0), at(9.0, 20_000.0), at(0.0, 0.0)))
        assertEquals(WayHomeResult.TrackOnly(WayHomeNote.LEAVES_AREA), find(detourOf(18.0), router))
        assertEquals("logged once: $logs", 1, logs.size)
    }
}
