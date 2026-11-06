package com.zynergylabs.forager.app.data.mapdetail

import com.zynergylabs.forager.app.data.dropoffmap.FakeTileServer
import com.zynergylabs.forager.app.domain.mapdata.AreaDetail
import com.zynergylabs.forager.app.domain.mapdata.DetailTileCover
import com.zynergylabs.forager.app.domain.mapdata.TestMvt
import com.zynergylabs.forager.app.domain.mapdata.TileId
import com.zynergylabs.forager.app.domain.mapdata.TrailMapDetailLimits
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Known bug 14 (dispatch 2026-11-02-02, A7 to A9, B2): the map server asked about an area, through a real OkHttp chain whose
 * last interceptor answers as the Worker does ([FakeTileServer]): a 200 tile, a 204, or another status. The tiles are
 * [TestMvt]'s. Made-up points.
 */
class WorkerTrailMapDetailTest {

    private val area = routeAreaFor(Region(41.0, -102.0, 24))
    private val cover: List<TileId> = DetailTileCover.of(area)!!.tiles()

    private fun server(body: ByteArray = TestMvt.DETAIL) = FakeTileServer(body)

    private fun detail(server: FakeTileServer) = WorkerTrailMapDetail(server.client, FakeTileServer.TEMPLATE)

    private fun slow(bytes: ByteArray, millis: Long): () -> Response.Builder = {
        Thread.sleep(millis)
        FakeTileServer.tileAnswer(bytes)
    }

    @Test
    fun `every tile with detail - all inside, and exactly the area's zoom-11 cover was asked for`() = runBlocking<Unit> {
        val server = server()
        assertTrue("positive control: the area covers several tiles (${cover.size})", cover.size in 9..36)
        assertEquals(AreaDetail.AllInside, detail(server).check(area))
        assertEquals(cover.toSet(), server.asked.toSet())
        assertEquals("each tile once", cover.size, server.asked.size)
    }

    @Test
    fun `one tile answering 204 - some outside`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.last()] = { FakeTileServer.empty() }
        assertEquals(AreaDetail.SomeOutside, detail(server).check(area))
    }

    @Test
    fun `one tile of land only - some outside`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.last()] = { FakeTileServer.tileAnswer(TestMvt.LAND_ONLY) }
        assertEquals(AreaDetail.SomeOutside, detail(server).check(area))
    }

    @Test
    fun `one tile of land and sea - some outside`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.last()] = { FakeTileServer.tileAnswer(TestMvt.LAND_AND_SEA) }
        assertEquals(AreaDetail.SomeOutside, detail(server).check(area))
    }

    @Test
    fun `sea counts neither way - all sea, or sea beside detail, is all inside`() = runBlocking<Unit> {
        assertEquals(AreaDetail.AllInside, detail(server(TestMvt.SEA_ONLY)).check(area))
        val mixed = server()
        mixed.answers[cover.first()] = { FakeTileServer.tileAnswer(TestMvt.SEA_ONLY) }
        assertEquals(AreaDetail.AllInside, detail(mixed).check(area))
    }

    @Test
    fun `a tile without detail is a real answer even when another tile failed`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.first()] = { FakeTileServer.status(503) }
        server.answers[cover.last()] = { FakeTileServer.empty() }
        assertEquals(AreaDetail.SomeOutside, detail(server).check(area))
    }

    @Test
    fun `a failed tile and none without detail - not run, with the counts and the first reason`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover[1]] = { FakeTileServer.status(503) }
        assertEquals(
            AreaDetail.NotRun("1 of ${cover.size} zoom-11 tiles could not be read (the first: HTTP 503), and none answered without detail"),
            detail(server).check(area),
        )
    }

    /** One tile of the area set up by [setUp] to fail in some way, every other tile with detail: not run, naming [reason] first. */
    private fun assertUnread(reason: String, setUp: (FakeTileServer) -> Unit) = runBlocking<Unit> {
        val server = server()
        setUp(server)
        val answer = detail(server).check(area)
        assertTrue("$reason: was $answer", answer is AreaDetail.NotRun && answer.reason.contains("(the first: $reason"))
    }

    @Test
    fun `a 403 is unread`() = assertUnread("HTTP 403") { it.answers[cover[1]] = { FakeTileServer.status(403) } }

    @Test
    fun `a dropped connection is unread`() = assertUnread("a connection failure (connection reset)") { it.dropped += cover[1] }

    @Test
    fun `a tile over the byte cap is unread`() = assertUnread("a tile over ${TrailMapDetailLimits.MAX_TILE_BYTES} bytes") {
        it.answers[cover[1]] = { FakeTileServer.tileAnswer(ByteArray((TrailMapDetailLimits.MAX_TILE_BYTES + 1).toInt())) }
    }

    @Test
    fun `bytes that are not a tile are unread`() = assertUnread("not a vector tile") {
        it.answers[cover[1]] = {
            Response.Builder().code(200).message("OK").body(byteArrayOf(0x1A, 0x7F, 0x01).toResponseBody("application/x-protobuf".toMediaType()))
        }
    }

    @Test
    fun `an area over the tile cap is not run, and nothing is asked`() = runBlocking<Unit> {
        val server = server()
        val far = routeAreaFor(Region(70.0, -100.0, 24))
        val size = DetailTileCover.of(far)!!.size
        assertTrue("positive control: the area is over the cap ($size)", size > TrailMapDetailLimits.MAX_AREA_TILES)
        assertEquals(AreaDetail.NotRun("the area covers $size zoom-11 tiles, over the limit of 36"), detail(server).check(far))
        assertEquals(emptyList<TileId>(), server.asked.toList())
    }

    @Test
    fun `an area across the antimeridian is not run, and nothing is asked`() = runBlocking<Unit> {
        val server = server()
        val across = GeoBoundingBox(north = 10.1, south = 9.9, east = -179.95, west = 179.95)
        assertEquals(AreaDetail.NotRun("the area crosses the antimeridian, which the check does not cover"), detail(server).check(across))
        assertEquals(emptyList<TileId>(), server.asked.toList())
    }

    @Test
    fun `at most four requests are out at once`() = runBlocking<Unit> {
        val server = server()
        server.holdMillis = 60
        assertEquals(AreaDetail.AllInside, detail(server).check(area))
        assertEquals("positive control: every tile was asked", cover.size, server.asked.size)
        assertEquals(TrailMapDetailLimits.MAX_IN_FLIGHT, server.maxInFlight.get())
    }

    @Test
    fun `the first tile without detail ends the check - nothing more is asked`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.first()] = { FakeTileServer.empty() }
        for (tile in cover.drop(1)) server.answers[tile] = slow(TestMvt.DETAIL, 300)
        assertEquals(AreaDetail.SomeOutside, detail(server).check(area))
        // The first answer can land before the loop has started the other three, so the claim is "nothing past the first batch".
        val firstBatch = cover.take(TrailMapDetailLimits.MAX_IN_FLIGHT).toSet()
        assertTrue("the tile without detail was asked", cover.first() in server.asked)
        assertTrue("nothing past the first four was asked: ${server.asked.size} of ${cover.size}", firstBatch.containsAll(server.asked.toSet()))
    }

    @Test
    fun `a check cancelled from outside starts nothing more`() = runBlocking<Unit> {
        val server = server()
        for (tile in cover) server.answers[tile] = slow(TestMvt.DETAIL, 400)
        val running = launch(Dispatchers.Default) { detail(server).check(area) }
        delay(150)
        running.cancelAndJoin()
        val firstBatch = cover.take(TrailMapDetailLimits.MAX_IN_FLIGHT).toSet()
        assertTrue("positive control: the check had started", server.asked.isNotEmpty())
        assertTrue("nothing past the first four was started: ${server.asked.size} of ${cover.size}", firstBatch.containsAll(server.asked.toSet()))
    }

    @Test
    fun `answers are kept for the session - the same area again asks nothing, and known answers it`() = runBlocking<Unit> {
        val server = server()
        val check = detail(server)
        assertNull("nothing known before any request", check.known(area))
        assertEquals(AreaDetail.AllInside, check.check(area))
        val asked = server.asked.size
        assertEquals(AreaDetail.AllInside, check.known(area))
        assertEquals(AreaDetail.AllInside, check.check(area))
        assertEquals("no request the second time", asked, server.asked.size)
    }

    @Test
    fun `a tile known to have no detail answers the area at once next time`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover.last()] = { FakeTileServer.empty() }
        val check = detail(server)
        assertEquals(AreaDetail.SomeOutside, check.check(area))
        assertEquals(AreaDetail.SomeOutside, check.known(area))
    }

    @Test
    fun `an unread tile is not kept - it is asked again next time`() = runBlocking<Unit> {
        val server = server()
        server.answers[cover[1]] = { FakeTileServer.status(503) }
        val check = detail(server)
        assertTrue(check.check(area) is AreaDetail.NotRun)
        assertNull("an unread tile leaves the area unknown", check.known(area))
        server.answers.remove(cover[1])
        val before = server.asked.size
        assertEquals(AreaDetail.AllInside, check.check(area))
        assertEquals("only the unread tile is asked again", listOf(cover[1]), server.asked.drop(before))
    }
}
