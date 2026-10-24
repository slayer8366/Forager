package com.zynergylabs.forager.app.domain.wayhome

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.wayhome.Ground.at
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.5× length check runs only when the walker went over 100 m between the two trail points
 * (`WAY_HOME_DETOUR_CHECK_MIN_TRACK_METERS`); under that it is skipped and the route is used as found. The owner:
 * "Switch to (b) (Recommended)" (RECORD -1600; dispatch 2026-10-23-09 Amendment 4), in place of Amendment 3's rule that
 * gave a short trail part the track home with no note, which dropped a real shortcut on a looped walk.
 *
 * Without the floor, a short walked stretch near a trail can trip the 1.5× check on the joints alone and read "This
 * route leaves your downloaded area", which is false (Sorrel's review of #389, M-389-2).
 *
 * Same synthetic ground as `FindWayHomeUseCaseTest`: a trail running east from the start for a kilometre. Track points
 * near the trail here are at least 30 m apart, so the scan tests every one of them ([WAY_HOME_SCAN_SPACING_METERS] is 20 m).
 */
class FindWayHomeShortWalkTest {

    private val logs = mutableListOf<String>()
    private val errorLog = ErrorLog { _, message, _ -> logs += message }

    private val snapper = FakeSnapper(listOf(at(0.0, 0.0) to at(1_000.0, 0.0)))

    private fun find(track: Track, router: FakeRouter) =
        runBlocking { FindWayHomeUseCase(FakeRegions(Ground.region()), router, snapper, errorLog)(track, null) }

    private fun alongTrails(result: WayHomeResult): WayHome {
        assertTrue("along trails, not $result (log: $logs)", result is WayHomeResult.AlongTrails)
        assertEquals("nothing fell back, nothing logged", emptyList<String>(), logs)
        return (result as WayHomeResult.AlongTrails).wayHome
    }

    private fun assertLeavesArea(result: WayHomeResult) {
        assertEquals(WayHomeResult.TrackOnly(WayHomeNote.LEAVES_AREA), result)
        assertEquals("logged once: $logs", 1, logs.size)
    }

    private fun column(east: Double, norths: IntProgression): Array<LatLng> = norths.map { at(east, it.toDouble()) }.toTypedArray()

    @Test
    fun `a walk that crosses a trail gets the route, not a false leaves-the-area note`() {
        // South to north across the trail 200 m east of the start. The first and last points near the trail are 20 m
        // either side of it: 40 m walked between them. The engine's route between its two matched points is 25 m (a trail
        // that bends at the crossing), and each joint is √(5² + 20²) ≈ 20.6 m, so the trail part is about 66.2 m: over
        // 1.5 times the 40 m walked, which is under 100 m, so the length check does not run.
        val crossing = Ground.track(*column(200.0, -200..-20 step 30), *column(200.0, 20..200 step 30))
        val router = FakeRouter(FakeRouter.found(25, at(205.0, 0.0), at(195.0, 0.0)))

        val wayHome = alongTrails(find(crossing, router))
        assertEquals(66.2, wayHome.trail.meters, 0.1)
        assertEquals(listOf(at(200.0, 20.0) to at(200.0, -20.0)), router.asked)
    }

    @Test
    fun `a looped walk keeps the trail shortcut between where it crossed the trail twice`() {
        // Hazel's Amendment 3 open item 1. The walk starts 190 m south of the trail, off any way, crosses it at 100 m
        // east, loops a kilometre north and back, and crosses it again at 160 m east, then walks 180 m south. About
        // 2,040 m walked between the two crossings; the trail part is 60 m of route plus two 10 m joints, 80 m.
        val loop = Ground.track(
            *column(100.0, -190..-10 step 30),
            *column(100.0, 20..980 step 30),
            at(130.0, 980.0),
            *column(160.0, 980 downTo -10 step 30),
            *column(160.0, -40 downTo -190 step 30),
        )
        val router = FakeRouter(FakeRouter.found(60, at(160.0, 0.0), at(100.0, 0.0)))

        val wayHome = alongTrails(find(loop, router))
        assertEquals("the trail part is the 80 m shortcut", 80.0, wayHome.trail.meters, 0.05)
        assertEquals("back to where the walker left the trail", 180.0, wayHome.trackBack.meters, 0.5)
        assertEquals("from where they first joined it to the start", 180.0, wayHome.trackToStart.meters, 0.5)
        assertEquals("not the 2 km loop", 440.0, wayHome.totalMeters, 1.0)
    }

    /**
     * Starts on the trail, walks south 40 m, east [east] m, back north onto the trail, then away south-east: entry and
     * leave on the trail, with 80 + [east] m walked between them.
     */
    private fun detourOf(east: Double) = Ground.track(
        at(0.0, 0.0), at(0.0, -40.0), at(east, -40.0), at(east, 0.0), at(east + 30.0, -30.0), at(east + 60.0, -60.0),
    )

    @Test
    fun `the length check is skipped at 98 m walked and runs at 102 m walked`() {
        // A 200 m trail part: over 1.5 times either stretch (147 m, 153 m), so the two sides differ only by the floor.
        assertEquals(200.0, alongTrails(find(detourOf(18.0), FakeRouter(FakeRouter.found(200, at(18.0, 0.0), at(0.0, 0.0))))).trail.meters, 0.05)

        logs.clear()
        assertLeavesArea(find(detourOf(22.0), FakeRouter(FakeRouter.found(200, at(22.0, 0.0), at(0.0, 0.0)))))
    }

    @Test
    fun `a short route outside the area's rectangle still leaves the area`() {
        // The rectangle check is unchanged and runs first, whatever the walked stretch.
        val router = FakeRouter(FakeRouter.found(50, at(18.0, 0.0), at(9.0, 20_000.0), at(0.0, 0.0)))
        assertLeavesArea(find(detourOf(18.0), router))
    }
}
