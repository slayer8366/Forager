package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routing.TrailEngine
import com.zynergylabs.forager.app.domain.routing.TrailEngineSource
import com.zynergylabs.forager.app.domain.routing.TrailSnapMethod
import com.zynergylabs.forager.app.domain.wayhome.FakeRegions
import com.zynergylabs.forager.app.domain.wayhome.Ground
import com.zynergylabs.forager.app.domain.wayto.AcceptEnds
import com.zynergylabs.forager.app.domain.wayto.FindWayToWaypointUseCase
import com.zynergylabs.forager.app.domain.wayto.StreetDropOffEnds
import com.zynergylabs.forager.app.domain.wayto.WayToEnds
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointNote
import com.zynergylabs.forager.app.domain.wayto.WayToWaypointResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What each end of a way-to search became, in the log (dispatch 2026-11-05-07; the owner: "Add one log line first
 * (Recommended)"). End to end on the drop-off fixture, as FindWayToWaypointPickUpTest and FindWayToWaypointDropOffTest
 * are: the real use case, the real vendored BRouter and both real readers. Each case's lines are asserted whole and in
 * order, with their level and tag, as logcat shows them: the new info line first, then any of today's lines, which stay
 * word for word (A3). The new line carries lengths, ranks and reasons only, never a position (A2).
 *
 * Not covered by the fixture (the planner, RECORD -2149): the chooser running at the walker's end with no pick-up
 * qualifying, since every street-end pin here finds one; a walker whose own tile is not stored; and rank 3, the nearest
 * public street, since cases B and C are reached by their address street first.
 */
class FindWayToWaypointEndLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** The use case's lines, in order, as logcat shows them: level/tag: message. */
    private val lines = mutableListOf<String>()
    private val errorLog = ErrorLog { tag, message, _ -> synchronized(lines) { lines += "W/$tag: $message" } }
    private val info: (String, String) -> Unit = { tag, message -> synchronized(lines) { lines += "I/$tag: $message" } }

    /** The engine's own lines are not this dispatch's: kept apart so [lines] holds only the use case's, and shown on a failure. */
    private val engineLines = mutableListOf<String>()
    private val profile by lazy { RoutingTestData.profileDir(tmp) }
    private val router by lazy { BRouterTrailRouter(profile, ErrorLog { _, message, _ -> synchronized(engineLines) { engineLines += message } }) }
    private val data: File by lazy { DropOffTestData.segments(tmp) }
    private val engines = object : TrailEngineSource {
        override suspend fun engine(): Result<TrailEngine> = Result.success(TrailEngine(router, BRouterTrailSnapper(router, TrailSnapMethod.SNAP)))
    }
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private val tileBefore0003 = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()
    private val tileJson = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/tilejson-with-0003.json")).toURI()).readText()

    private fun basemap(stored: ByteArray) = object : OfflineBasemap {
        override suspend fun tileJson() = Result.success<String?>(tileJson)
        override suspend fun tile(id: TileId) = Result.success(if (id == TileId(15, 9875, 12393)) stored else null)
    }

    private fun find(walker: String, accuracyMeters: Float?, to: String, stored: ByteArray = tile, ends: WayToEnds? = null) = runBlocking {
        val region = Ground.region(id = 7, centre = DropOffTestData.point("pin_a"), radiusKm = 2, routeData = RegionRouteData.Stored("run", "t", 10L, data))
        val settle = ends ?: StreetDropOffEnds(ReadNearbyMapData(basemap(stored)), BRouterRouteWays(profile))
        FindWayToWaypointUseCase(FakeRegions(region), engines, errorLog, settle, info = info)(DropOffTestData.point(walker), DropOffTestData.point(to), accuracyMeters)
    }

    private fun assertAlongTrails(result: WayToWaypointResult) =
        assertTrue("along trails, not $result (log: $lines; engine: $engineLines)", result is WayToWaypointResult.AlongTrails)

    private fun assertLines(vararg expected: String) =
        assertEquals("engine: $engineLines", expected.toList(), synchronized(lines) { lines.toList() })

    @Test
    fun `a pick-up is logged with its rank and its leg, beside the waypoint's trail end`() {
        val result = find("pin_a", accuracyMeters = 5f, to = "pin_d")
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end moves to a pick-up at the private way's mouth (rank 1 of 3, ACCESS_WAY), its leg 77 m; " +
                "the waypoint's end keeps the engine's end and gates its leg: $NOT_STREET.",
        )
    }

    @Test
    fun `a pick-up from the address street and a drop-off at a driveway are both logged, the waypoint's end treated the same`() {
        val result = find("pin_b", accuracyMeters = 5f, to = "pin_a")
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end moves to a pick-up at the addressed street (rank 2 of 3, ADDRESS_STREET), its leg 75 m; " +
                "the waypoint's end moves to a drop-off at the private way's mouth (rank 1 of 3, ACCESS_WAY), its leg 77 m.",
        )
    }

    @Test
    fun `a walker who is no street end keeps the engine's end, gated, and the line says why`() {
        val result = find("walker", accuracyMeters = 5f, to = "pin_d")
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end keeps the engine's end and gates its lead-in: $NOT_STREET; " +
                "the waypoint's end keeps the engine's end and gates its leg: $NOT_STREET.",
        )
    }

    @Test
    fun `a fix worse than 15 m is not judged for a pick-up, and the line gives the accuracy`() {
        val result = find("walker", accuracyMeters = 15.5f, to = "pin_d")
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end is not judged for a pick-up, the fix reporting 15.5 m, worse than 15 m, so keeps the engine's end and gates its lead-in; " +
                "the waypoint's end keeps the engine's end and gates its leg: $NOT_STREET.",
        )
    }

    @Test
    fun `a fix with no accuracy is not judged for a pick-up, and the line says it reports none`() {
        val result = find("walker", accuracyMeters = null, to = "pin_a")
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end is not judged for a pick-up, the fix reporting no accuracy, so keeps the engine's end and gates its lead-in; " +
                "the waypoint's end moves to a drop-off at the private way's mouth (rank 1 of 3, ACCESS_WAY), its leg 77 m.",
        )
    }

    @Test
    fun `ends that go by trail are logged by their reason's kind, and today's by-trail lines follow word for word`() {
        val result = find("walker", accuracyMeters = null, to = "pin_a", stored = tileBefore0003)
        assertAlongTrails(result)
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end goes by trail: a stored tile in reach has no fence mark; " +
                "the waypoint's end goes by trail: a stored tile in reach has no fence mark.",
            "W/$TAG: The way to the waypoint keeps the engine's end by trail, with no drop-off and the leg not gated: " +
                "the waypoint's end: tile 15/9875/12393 has no barrier mark: it was built before fences were carried.",
            "W/$TAG: The way to the waypoint keeps the engine's end by trail, with no drop-off and the leg not gated: " +
                "the lead-in: tile 15/9875/12393 has no barrier mark: it was built before fences were carried.",
        )
    }

    @Test
    fun `a waypoint's end refused leaves the walker's end not reached, and today's refusal line follows word for word`() {
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.WAY_IN_BLOCKED), find("walker", accuracyMeters = null, to = "pin_d2"))
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end is not reached, the waypoint's end being refused first; the waypoint's end is refused (WAY_IN_BLOCKED).",
            "W/$TAG: The way to the waypoint is the straight line (WAY_IN_BLOCKED): the route's ends: the leg from the trail to the waypoint crosses [WATER].",
        )
    }

    @Test
    fun `a lead-in refused at its gate keeps the line of what the walker's end was, and today's refusal line follows word for word`() {
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.LEAD_IN_BLOCKED), find("pin_a", accuracyMeters = 15.5f, to = "pin_d"))
        assertLines(
            "I/$TAG: The way to the waypoint's ends: the walker's end is not judged for a pick-up, the fix reporting 15.5 m, worse than 15 m, so keeps the engine's end and gates its lead-in; " +
                "the waypoint's end keeps the engine's end and gates its leg: $NOT_STREET.",
            "W/$TAG: The way to the waypoint is the straight line (LEAD_IN_BLOCKED): the route's ends: the lead-in crosses [BUILDING].",
        )
    }

    @Test
    fun `ends that record nothing are said not to have been recorded, never silently`() {
        val result = find("walker", accuracyMeters = null, to = "pin_d", ends = AcceptEnds)
        assertAlongTrails(result)
        assertLines("I/$TAG: The way to the waypoint's ends were not recorded by the ends step.")
    }

    @Test
    fun `a search that falls back before the ends step logs no end line, since no end was judged`() {
        // Passes over the stub too, by design: it pins that the line is not logged before the ends step runs.
        val result = runBlocking {
            FindWayToWaypointUseCase(FakeRegions(), engines, errorLog, AcceptEnds, info = info)(DropOffTestData.point("walker"), DropOffTestData.point("pin_d"), 5f)
        }
        assertEquals(WayToWaypointResult.StraightLine(WayToWaypointNote.NO_TRAIL_ROUTES), result)
        assertLines("W/$TAG: The way to the waypoint is the straight line (NO_TRAIL_ROUTES): no downloaded area covers either end.")
    }

    private companion object {
        const val TAG = "FindWayToWaypoint"

        /** StreetDropOff.classify's whole rule for a trail end, in the line's words (F3: the line cannot say which half failed). */
        const val NOT_STREET = "not a street end (no building within 15 m, and not built-up land with a public street within 150 m)"
    }
}
