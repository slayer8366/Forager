package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.data.routedata.FakeRouteServer.Companion.LIST_PATH
import com.zynergylabs.forager.app.data.routedata.FakeRouteServer.Companion.error
import com.zynergylabs.forager.app.data.routedata.FakeRouteServer.Companion.status
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import com.zynergylabs.forager.app.domain.routedata.RouteDataMissingReason
import com.zynergylabs.forager.app.domain.routedata.routeAreaFor
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A region's route fetch through [LocalRegionRouteFolders.fetchFor], the call `MapLibreOfflineMapRepository.download`
 * makes once a region's tiles are in (dispatch 2026-10-23-03), against [FakeRouteServer]. Every answer checks two
 * things: what the fetch returned, and what a later listing reads back from the phone's storage, since the listing is
 * what the region list shows. No test reaches the real Worker, whose routes are not deployed.
 */
class RegionRouteFetchTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = FakeRouteServer()
    private val logs = mutableListOf<String>()
    private val region = Region(45.02, -120.01, 10)
    private val squareA = Random(1).nextBytes(3_000)
    private val squareB = Random(2).nextBytes(5_000)

    private fun folders(key: String = KEY): LocalRegionRouteFolders {
        val log: (String, Throwable?) -> Unit = { message, _ -> logs += message }
        return LocalRegionRouteFolders(
            store = RegionRouteStore(File(temp.root, "offline_routes"), log),
            client = RouteAreaClient(server.client, "https://routes.test/".toHttpUrl(), key),
            appKey = key,
            log = log,
            io = Dispatchers.Unconfined,
        )
    }

    private fun fetch(folders: LocalRegionRouteFolders = folders()) = runBlocking { folders.fetchFor(REGION_ID, region) }

    private fun listed(folders: LocalRegionRouteFolders = folders()) = runBlocking { folders.status(REGION_ID) }

    private fun regionFolder() = File(temp.root, "offline_routes/$REGION_ID")

    @Test
    fun `the fake server's sha256 is the real one, so a match below is evidence`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FakeRouteServer.sha256("abc".toByteArray()))
    }

    @Test
    fun `an area's files are stored under BRouter's names, byte for byte, with the run id, and listed as stored`() {
        server.serveArea("usca-20261021T111429Z", mapOf("W125_N45.rd5" to squareA, "W120_N45.rd5" to squareB))

        val fetched = fetch()

        val expected = RegionRouteData.Stored("usca-20261021T111429Z", "2026-10-21T00:00:00Z", 8_000L, regionFolder())
        assertEquals(expected, fetched)
        assertEquals("a later listing reads the same from storage", expected, listed())
        assertArrayEquals(squareA, File(regionFolder(), "W125_N45.rd5").readBytes())
        assertArrayEquals(squareB, File(regionFolder(), "W120_N45.rd5").readBytes())
        assertEquals(setOf("W125_N45.rd5", "W120_N45.rd5", RECORD_FILE_NAME), regionFolder().list()!!.toSet())
    }

    @Test
    fun `the list is asked for the region's own rectangle, and every request carries the app key`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA))

        fetch()

        val box = routeAreaFor(region)
        val list = server.requests.first().url
        assertEquals(LIST_PATH, list.encodedPath)
        assertEquals(
            mapOf("south" to box.south, "west" to box.west, "north" to box.north, "east" to box.east),
            listOf("south", "west", "north", "east").associateWith { list.queryParameter(it)!!.toDouble() },
        )
        assertEquals(listOf(LIST_PATH, "/routing/run-1/W125_N45.rd5"), server.requests.map { it.url.encodedPath })
        assertEquals(listOf(listOf(KEY), listOf(KEY)), server.requests.map { it.headers(RouteAreaClient.APP_KEY_HEADER) })
    }

    @Test
    fun `404 outside_coverage is no routes here, recorded as such and logged`() {
        server.answers[LIST_PATH] = { error(404, "outside_coverage") }

        assertEquals(RegionRouteData.NoneHere, fetch())
        assertEquals(RegionRouteData.NoneHere, listed())
        assertEquals(1, logs.count { "outside coverage" in it })
    }

    @Test
    fun `a 404 that is not outside_coverage is never read as no routes here`() {
        // What the live Worker answers today, with the route not deployed: FakeRouteServer's default 404 page.
        assertMissing(RouteDataMissingReason.UNEXPECTED_RESPONSE)
    }

    @Test
    fun `each agreed error on the list is recorded as missing with its own reason, and logged`() {
        val cases = listOf(
            Triple(503, "manifest_missing", RouteDataMissingReason.MANIFEST_MISSING),
            Triple(503, "run_missing_square", RouteDataMissingReason.RUN_MISSING_SQUARE),
            Triple(502, "checksum", RouteDataMissingReason.SERVER_CHECKSUM),
            Triple(413, "too_large", RouteDataMissingReason.TOO_LARGE),
            Triple(400, "bad_request", RouteDataMissingReason.BAD_REQUEST),
            Triple(403, "forbidden", RouteDataMissingReason.FORBIDDEN),
            Triple(429, "rate_limited", RouteDataMissingReason.RATE_LIMITED),
        )
        for ((code, error, reason) in cases) {
            server.answers[LIST_PATH] = { error(code, error) }
            logs.clear()
            assertMissing(reason, "HTTP $code $error")
            assertEquals("HTTP $code $error is logged with its code", 1, logs.count { "(${reason.code})" in it && error in it })
        }
    }

    @Test
    fun `a rate limit's Retry-After is logged`() {
        server.answers[LIST_PATH] = { error(429, "rate_limited", retryAfter = "120") }

        assertMissing(RouteDataMissingReason.RATE_LIMITED)
        assertEquals(1, logs.count { "Retry-After 120" in it })
    }

    @Test
    fun `an error code with a status it was not agreed with is unexpected, not that code`() {
        server.answers[LIST_PATH] = { error(503, "forbidden") }

        assertMissing(RouteDataMissingReason.UNEXPECTED_RESPONSE)
    }

    @Test
    fun `a dropped connection on the list is a network failure`() {
        server.dropped += LIST_PATH

        assertMissing(RouteDataMissingReason.NETWORK)
    }

    @Test
    fun `a dropped connection on a file fails the whole area and leaves no file behind`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA, "W120_N45.rd5" to squareB))
        server.dropped += "/routing/run-1/W120_N45.rd5"

        assertMissing(RouteDataMissingReason.NETWORK)
        assertEquals(setOf(RECORD_FILE_NAME), regionFolder().list()!!.toSet())
    }

    @Test
    fun `an agreed error on a file is recorded with that file's reason`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA, "W120_N45.rd5" to squareB))
        server.answers["/routing/run-1/W120_N45.rd5"] = { error(502, "checksum") }

        assertMissing(RouteDataMissingReason.SERVER_CHECKSUM)
        assertEquals(setOf(RECORD_FILE_NAME), regionFolder().list()!!.toSet())
    }

    @Test
    fun `bytes that do not hash to the list's sha256 are refused and not kept`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA), shaOverride = mapOf("W125_N45.rd5" to FakeRouteServer.sha256(squareB)))

        assertMissing(RouteDataMissingReason.CHECKSUM_MISMATCH)
        assertFalse(File(regionFolder(), "W125_N45.rd5").exists())
        assertEquals(1, logs.count { "sha256" in it && "W125_N45.rd5" in it })
    }

    @Test
    fun `more bytes than the list promised are refused`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA), sizeOverride = mapOf("W125_N45.rd5" to 1_000L))

        assertMissing(RouteDataMissingReason.CHECKSUM_MISMATCH)
        assertEquals(1, logs.count { "more than the 1000 bytes" in it })
    }

    @Test
    fun `fewer bytes than the list promised are refused`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA), sizeOverride = mapOf("W125_N45.rd5" to 4_000L))

        assertMissing(RouteDataMissingReason.CHECKSUM_MISMATCH)
    }

    @Test
    fun `a build with no app key sends nothing and records why`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA))

        assertEquals(RegionRouteData.Missing(RouteDataMissingReason.NO_APP_KEY), fetch(folders(key = "")))
        assertEquals(RegionRouteData.Missing(RouteDataMissingReason.NO_APP_KEY), listed())
        assertEquals(emptyList<Any>(), server.requests)
    }

    @Test
    fun `a file url on another host is refused, and the key goes nowhere but the routing host`() {
        server.serveList(
            """{"run_id":"r","osm_timestamp":"t","files":[{"name":"W125_N45.rd5","size":3000,"sha256":"${FakeRouteServer.sha256(squareA)}","url":"https://elsewhere.test/W125_N45.rd5"}]}""",
        )

        assertMissing(RouteDataMissingReason.MALFORMED_RESPONSE)
        assertEquals(listOf("routes.test"), server.requests.map { it.url.host })
    }

    @Test
    fun `a file name that is not BRouter's cannot write outside the region's folder`() {
        server.serveList(
            """{"run_id":"r","osm_timestamp":"t","files":[{"name":"../../escape.rd5","size":3000,"sha256":"${FakeRouteServer.sha256(squareA)}","url":"/x"}]}""",
        )

        assertMissing(RouteDataMissingReason.MALFORMED_RESPONSE)
        assertEquals(listOf(LIST_PATH), server.requests.map { it.url.encodedPath })
        assertFalse(File(temp.root, "escape.rd5").exists())
    }

    @Test
    fun `a 200 that is not the agreed JSON is malformed`() {
        server.answers[LIST_PATH] = { status(200, "<html>hello</html>", "text/html") }

        assertMissing(RouteDataMissingReason.MALFORMED_RESPONSE)
    }

    @Test
    fun `a list that names no files is malformed, not none here`() {
        server.serveList("""{"run_id":"r","osm_timestamp":"t","files":[]}""")

        assertMissing(RouteDataMissingReason.MALFORMED_RESPONSE)
    }

    @Test
    fun `a list over the phone's own limits is refused before any file is fetched`() {
        val five = (0 until 5).associate { "W${125 - 5 * it}_N45.rd5" to squareA }
        server.serveArea("run-1", five)

        assertMissing(RouteDataMissingReason.OVER_PHONE_LIMIT)
        assertEquals(listOf(LIST_PATH), server.requests.map { it.url.encodedPath })
    }

    @Test
    fun `a list whose sizes add up past the phone's byte limit is refused`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA), sizeOverride = mapOf("W125_N45.rd5" to RouteAreaLimits.MAX_AREA_BYTES + 1))

        assertMissing(RouteDataMissingReason.OVER_PHONE_LIMIT)
    }

    @Test
    fun `an error this code does not recognise is a missing state, never a thrown download`() {
        server.answers[LIST_PATH] = { throw IllegalStateException("something new") }

        assertEquals(RegionRouteData.Missing(RouteDataMissingReason.INTERNAL_ERROR), fetch())
        assertTrue(logs.any { "failed unexpectedly" in it })
    }

    @Test
    fun `fetching again replaces what was stored`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA))
        fetch()
        server.answers.clear()
        server.answers[LIST_PATH] = { error(404, "outside_coverage") }

        assertEquals(RegionRouteData.NoneHere, fetch())
        assertEquals(setOf(RECORD_FILE_NAME), regionFolder().list()!!.toSet())
        assertEquals(setOf(REGION_ID.toString()), File(temp.root, "offline_routes").list()!!.toSet())
    }

    private fun assertMissing(reason: RouteDataMissingReason, case: String = reason.code) {
        assertEquals(case, RegionRouteData.Missing(reason), fetch())
        assertEquals("$case, read back by a listing", RegionRouteData.Missing(reason), listed())
    }

    private companion object {
        const val KEY = "test-key-not-the-real-one"
        const val REGION_ID = 42L
    }
}
