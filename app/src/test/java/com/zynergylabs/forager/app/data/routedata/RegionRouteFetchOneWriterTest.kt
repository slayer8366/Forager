package com.zynergylabs.forager.app.data.routedata

import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.routedata.RegionRouteData
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * One writer per region folder (Sorrel's review of #383, M1; dispatch 2026-10-23-03 Amendment 1), and only the
 * record's own files kept on a commit (L1). M1's path, as Sorrel traced it: a download's route fetch is still running
 * when Offline maps is entered again, the region lists with routes wanted, and the automatic fetch for the same id
 * used to clear the running fetch's `.partial`, failing both.
 */
class RegionRouteFetchOneWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val server = FakeRouteServer()
    private val logs = java.util.Collections.synchronizedList(mutableListOf<String>())
    private val region = Region(45.02, -120.01, 10)
    private val squareA = Random(1).nextBytes(3_000)
    private val squareB = Random(2).nextBytes(5_000)
    private val root by lazy { File(temp.root, "offline_routes") }
    private val log: (String, Throwable?) -> Unit = { message, _ -> logs += message }
    private val store by lazy { RegionRouteStore(root, log) }

    private fun folders() = LocalRegionRouteFolders(
        store = store,
        client = RouteAreaClient(server.client, "https://routes.test/".toHttpUrl(), KEY),
        appKey = KEY,
        log = log,
        io = Dispatchers.Unconfined,
    )

    @Test
    fun `a second fetch for a region being fetched touches nothing, and the first one stores its routes`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA, "W120_N45.rd5" to squareB))
        // The first fetch stops inside its second file's request, its first file already in its .partial.
        val inSecondFile = CountDownLatch(1)
        val release = CountDownLatch(1)
        val second = server.answers.getValue("/routing/run-1/W120_N45.rd5")
        server.answers["/routing/run-1/W120_N45.rd5"] = {
            inSecondFile.countDown()
            release.await(10, TimeUnit.SECONDS)
            second()
        }
        val shared = folders()
        var first: RegionRouteData? = null
        val running = Thread { first = runBlocking { shared.fetchFor(REGION_ID, region) } }.apply { start() }
        assertTrue("the first fetch reached its second file", inSecondFile.await(10, TimeUnit.SECONDS))
        val partialBefore = File(root, "$REGION_ID.partial").list()!!.toSet()

        val again = runBlocking { shared.fetchFor(REGION_ID, region) }
        val listed = runBlocking { shared.status(REGION_ID) }
        val partialAfter = File(root, "$REGION_ID.partial").list()!!.toSet()
        release.countDown()
        running.join(10_000)

        assertEquals(RegionRouteData.Fetching, again)
        assertEquals("a listing during the fetch reads it as running", RegionRouteData.Fetching, listed)
        assertEquals(setOf("W125_N45.rd5"), partialBefore)
        assertEquals("the running fetch's .partial was not touched", partialBefore, partialAfter)
        assertEquals(RegionRouteData.Stored("run-1", "2026-10-21T00:00:00Z", 8_000L, File(root, "$REGION_ID")), first)
        assertEquals(3, server.requests.size) // one list and two files: the second fetch sent nothing
        assertEquals(1, logs.count { "already being fetched" in it })
    }

    @Test
    fun `once a fetch ends, the region can be fetched again`() {
        server.serveArea("run-1", mapOf("W125_N45.rd5" to squareA))
        val shared = folders()
        runBlocking { shared.fetchFor(REGION_ID, region) }

        assertTrue(runBlocking { shared.fetchFor(REGION_ID, region) } is RegionRouteData.Stored)
    }

    @Test
    fun `a stored commit keeps only the files its record names`() {
        val partial = store.beginFetch(REGION_ID)!!
        File(partial, "W125_N45.rd5").writeBytes(squareA)
        File(partial, "W130_N45.rd5").writeBytes(squareB) // a stray from an earlier attempt, named by no record
        store.commit(REGION_ID, partial, RegionRouteRecord.Stored("run-2", "t", listOf(RecordedFile("W125_N45.rd5", 3_000L, FakeRouteServer.sha256(squareA)))))
        store.endFetch(REGION_ID)

        assertEquals(setOf("W125_N45.rd5", RECORD_FILE_NAME), File(root, "$REGION_ID").list()!!.toSet())
    }

    private companion object {
        const val KEY = "test-key-not-the-real-one"
        const val REGION_ID = 42L
    }
}
