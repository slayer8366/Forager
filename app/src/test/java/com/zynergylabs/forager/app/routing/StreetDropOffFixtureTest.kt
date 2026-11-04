package com.zynergylabs.forager.app.routing

import com.zynergylabs.forager.app.domain.GeoDistance
import com.zynergylabs.forager.app.domain.dropoff.GateResult
import com.zynergylabs.forager.app.domain.dropoff.LegGates
import com.zynergylabs.forager.app.domain.dropoff.LegGates.Crossing
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff.Choice
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff.End
import com.zynergylabs.forager.app.domain.dropoff.StreetDropOff.Rank
import com.zynergylabs.forager.app.domain.mapdata.NearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.OfflineBasemap
import com.zynergylabs.forager.app.domain.mapdata.ReadNearbyMapData
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.routing.RouteWay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The street-or-trail call, the drop-off and its gates on the drop-off fixture, with the real readers under them: the
 * fixture's tile through [ReadNearbyMapData], and its route square through [BRouterRouteWays] (way-to dispatch
 * 2026-10-28, PR 4). Each case's trap (`DropOffFixtureTest`) is where BRouter alone ends the route; here the drop-off
 * must end it elsewhere, or say that nothing qualifies.
 */
class StreetDropOffFixtureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val fixtureTile = TileId(15, 9875, 12393)
    private val tile = DropOffTestData.resource(DropOffTestData.TILE).readBytes()
    private fun tileJson(name: String) = File(checkNotNull(javaClass.classLoader?.getResource("mapdata/$name")).toURI()).readText()

    private val tileBefore0003 = DropOffTestData.resource(DropOffTestData.TILE_BEFORE_0003).readBytes()

    private fun data(pin: String, json: String = tileJson("tilejson-with-0003.json"), stored: ByteArray = tile): NearbyMapData = runBlocking {
        val basemap = object : OfflineBasemap {
            override suspend fun tileJson() = Result.success<String?>(json)
            override suspend fun tile(id: TileId) = Result.success(if (id == fixtureTile) stored else null)
        }
        ReadNearbyMapData(basemap)(DropOffTestData.point(pin), 300.0).getOrThrow()
    }

    private val ways: List<RouteWay> by lazy {
        runBlocking { BRouterRouteWays(RoutingTestData.profileDir(tmp)).near(DropOffTestData.point("pin_a"), 600.0, DropOffTestData.segments(tmp)).getOrThrow() }
    }

    private fun p(name: String) = DropOffTestData.point(name)
    private fun street(pin: String, d: NearbyMapData = data(pin)) = StreetDropOff.classify(p(pin), d, ways)
    private fun choose(pin: String, d: NearbyMapData = data(pin)): Choice = StreetDropOff.choose(p(pin), (street(pin, d) as End.Street).building, d, ways)

    private fun own(pin: String, d: NearbyMapData = data(pin)) = listOf((street(pin, d) as End.Street).building!!)

    private fun assertFound(choice: Choice, at: String, rank: Rank) {
        assertTrue("found, not $choice", choice is Choice.Found)
        val found = (choice as Choice.Found).dropOff
        assertEquals(rank, found.rank)
        assertEquals("drop-off ${found.at} vs $at", 0.0, GeoDistance.metersBetween(found.at, p(at)), 1.0)
    }

    @Test
    fun `the houses are street ends with their own building, the wood pins trail ends`() {
        for (pin in listOf("pin_a", "pin_b", "pin_c")) {
            val end = street(pin)
            assertTrue("$pin: $end", end is End.Street && end.building != null)
        }
        assertEquals(End.Trail, street("pin_d"))
        assertEquals(End.Trail, street("pin_d2"))
    }

    @Test
    fun `an end in a tile that is not stored cannot be called`() {
        val far = LatLng(p("pin_a").lat + 0.02, p("pin_a").lng)
        assertTrue(StreetDropOff.classify(far, data("pin_a"), ways) is End.Unknown)
    }

    @Test
    fun `case A, the owner's case - the drop-off is where the driveway meets the street in front, not the dead end behind`() {
        val choice = choose("pin_a")
        assertFound(choice, "dropoff_a", Rank.ACCESS_WAY)
        assertEquals(77.6, (choice as Choice.Found).dropOff.legMeters, 0.5)
        assertTrue(GeoDistance.metersBetween(choice.dropOff.at, p("engine_a")) > 100)
    }

    @Test
    fun `case B - the address street, Front Road, not the lane beyond the fence`() {
        assertFound(choose("pin_b"), "dropoff_b", Rank.ADDRESS_STREET)
    }

    @Test
    fun `case C - the address street, West Road, across only a culvert`() {
        assertFound(choose("pin_c"), "dropoff_c", Rank.ADDRESS_STREET)
    }

    @Test
    fun `with no street names, the frontage rank passes over the fenced and the stream-crossed street for the clear one`() {
        // Addresses with no street name, as OpenStreetMap often has them: the address rank finds nothing to go on.
        fun nameless(d: NearbyMapData) = d.copy(features = d.features.copy(addresses = d.features.addresses.map { it.copy(street = null) }))
        assertFound(choose("pin_b", nameless(data("pin_b"))), "dropoff_b", Rank.FRONTAGE)
        assertFound(choose("pin_c", nameless(data("pin_c"))), "dropoff_c", Rank.FRONTAGE)
    }

    @Test
    fun `a tile set from before patch 0003 qualifies no drop-off, saying fences could not be checked`() {
        val choice = choose("pin_a", data("pin_a", tileJson("tilejson-before-0003.json"), tileBefore0003))
        assertTrue("$choice", choice is Choice.NoneQualifies)
        val tried = (choice as Choice.NoneQualifies).tried
        assertTrue(tried.none { it.gates == GateResult.Passes })
        assertTrue(tried.any { t -> (t.gates as? GateResult.Unchecked)?.what?.any { it.what == Crossing.FENCE && "no barrier mark" in it.reason } == true })
    }

    @Test
    fun `the gates on each trap's own leg`() {
        val a = data("pin_a")
        val own = (street("pin_a", a) as End.Street).building!!
        assertEquals(GateResult.Fails(listOf(Crossing.BUILDING)), LegGates.check(p("engine_a"), p("pin_a"), a, listOf(own)))
        assertEquals(GateResult.Fails(listOf(Crossing.FENCE)), LegGates.check(p("engine_b"), p("pin_b"), data("pin_b"), own("pin_b")))
        assertEquals(GateResult.Fails(listOf(Crossing.WATER)), LegGates.check(p("engine_c"), p("pin_c"), data("pin_c"), own("pin_c")))
        // The target's own house is crossed on the way to its door: without it named as the target's own, it fails.
        assertEquals(GateResult.Fails(listOf(Crossing.BUILDING, Crossing.FENCE)), LegGates.check(p("engine_b"), p("pin_b"), data("pin_b")))
    }

    @Test
    fun `trail legs - the plain one passes, the one across the stream fails`() {
        assertEquals(GateResult.Passes, LegGates.check(p("engine_d"), p("pin_d"), data("pin_d")))
        assertEquals(GateResult.Fails(listOf(Crossing.WATER)), LegGates.check(p("engine_d2"), p("pin_d2"), data("pin_d2")))
    }

    @Test
    fun `in a tile set from before culverts were marked, a stream crossed may be in one - unchecked, not failed`() {
        val old = data("pin_c", tileJson("tilejson-before-0003.json"), tileBefore0003)
        val result = LegGates.check(p("engine_c"), p("pin_c"), old, own("pin_c", old))
        assertTrue("$result", result is GateResult.Unchecked && result.what.any { it.what == Crossing.WATER && it.reason.startsWith("a stream is crossed") })
    }

    @Test
    fun `a leg into a tile that is not stored is unchecked`() {
        val north = LatLng(p("pin_d").lat + 300 / 111_195.0, p("pin_d").lng) // past the fixture tile's north edge
        val result = LegGates.check(p("pin_d"), north, data("pin_d"))
        assertTrue("$result", result is GateResult.Unchecked && result.what.any { it.what == Crossing.BUILDING })
    }

    @Test
    fun `fences are read from each tile's mark - an old tile is unchecked even when the stored TileJSON is new`() {
        // The owner's choice (b), 10-28: a TileJSON refreshed by browsing says nothing about a tile downloaded before.
        val choice = choose("pin_a", data("pin_a", tileJson("tilejson-with-0003.json"), tileBefore0003))
        assertTrue("$choice", choice is Choice.NoneQualifies)
        // And the other way round: a marked tile is checked even under the old TileJSON.
        assertFound(choose("pin_a", data("pin_a", tileJson("tilejson-before-0003.json"), tile)), "dropoff_a", Rank.ACCESS_WAY)
    }
}
