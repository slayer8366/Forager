package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.TileHoldGate
import com.zynergylabs.forager.app.domain.TileRetryPolicy
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * A failed map tile is held and sent again (dispatch 2026-10-23-06 Amendment 1). Each case runs a request through the
 * map's real client, [mapHttpClient], the chain MapLibre's own calls take, with a last interceptor standing in for the
 * wire, as `MapUserAgentTest` does (no MockWebServer in this project). Time and the network are fakes, so a 5-minute
 * hold runs instantly.
 */
class TileRetryInterceptorTest {

    private val tileUrl = "https://forager-pmtiles.brandonlee1-894.workers.dev/us/12/660/1464.mvt"
    private var now = 0L
    private val logs = mutableListOf<String>()
    private val gate = TileHoldGate()
    private val network = FakeNetwork()
    private var onTime: (Long) -> Unit = {}

    /** The network: validated from [validatedAt] on (never, by default). Waiting on it moves the fake clock. */
    private inner class FakeNetwork : NetworkWait {
        var validatedAt = Long.MAX_VALUE
        override fun isValidated() = now >= validatedAt
        override fun awaitValidated(timeoutMs: Long) {
            advance(if (validatedAt in (now + 1)..(now + timeoutMs)) validatedAt - now else timeoutMs)
        }
    }

    private fun advance(ms: Long) {
        now += ms
        onTime(now)
    }

    private val interceptor = TileRetryInterceptor(
        policy = TileRetryPolicy(),
        gate = gate,
        network = network,
        clock = { now },
        pause = { ms -> advance(ms) },
        log = { logs += it },
    )

    /** The wire: answers each attempt in turn from [answers], recording what it was sent and when. */
    private class Wire(private val answers: List<(Request) -> Response>) : Interceptor {
        val sent = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            sent += chain.request()
            return answers[minOf(sent.size, answers.size) - 1](chain.request())
        }
    }

    private fun ok(body: String): (Request) -> Response = { request -> response(request, 200, body) }
    private fun status(code: Int): (Request) -> Response = { request -> response(request, code, "") }
    private fun throws(e: IOException): (Request) -> Response = { throw e }

    private fun response(request: Request, code: Int, body: String) = Response.Builder().request(request)
        .protocol(Protocol.HTTP_1_1).code(code).message("m").body(body.toResponseBody(null)).build()

    private fun call(wire: Wire, url: String = tileUrl): Call =
        mapHttpClient("Forager/test", interceptor).newBuilder().addInterceptor(wire).build()
            .newCall(Request.Builder().url(url).build())

    @Test
    fun `a tile that failed with no network is sent again when the network returns, and the map gets the tile`() {
        val dns = UnknownHostException("Unable to resolve host")
        val wire = Wire(listOf(throws(dns), ok("tile-bytes")))
        network.validatedAt = 40_000

        val response = call(wire).execute()

        assertEquals(200, response.code)
        assertEquals("tile-bytes", response.body!!.string())
        assertEquals("attempts on the wire", 2, wire.sent.size)
        assertEquals("resent when the network validated, not before", 40_000L, now)
        assertEquals("the hold was given back", 0, gate.heldNow)
    }

    @Test
    fun `every resend carries the app's User-Agent`() {
        val wire = Wire(listOf(throws(UnknownHostException("x")), ok("t")))
        network.validatedAt = 5_000

        call(wire).execute().close()

        assertEquals(listOf("Forager/test", "Forager/test"), wire.sent.map { it.header("User-Agent") })
    }

    @Test
    fun `server errors with the network up are resent after the backoff`() {
        val wire = Wire(listOf(status(503), status(502), ok("t")))
        network.validatedAt = 0

        val response = call(wire).execute()

        assertEquals(200, response.code)
        assertEquals(3, wire.sent.size)
        assertEquals("1 s then 2 s", 3_000L, now)
    }

    @Test
    fun `a timeout is held like a connection error`() {
        val wire = Wire(listOf(throws(SocketTimeoutException("timeout")), ok("t")))
        network.validatedAt = 0

        assertEquals(200, call(wire).execute().code)
        assertEquals(2, wire.sent.size)
    }

    @Test
    fun `after 5 minutes without signal the map gets the original failure`() {
        val dns = UnknownHostException("Unable to resolve host")
        val wire = Wire(listOf(throws(dns)))

        try {
            call(wire).execute()
            fail("a tile that never loaded returned a response")
        } catch (e: IOException) {
            assertSame("the map gets the wire's own exception", dns, e)
        }
        assertEquals("the first attempt and one at the limit", 2, wire.sent.size)
        assertEquals(300_000L, now)
        assertEquals(0, gate.heldNow)
    }

    @Test
    fun `a server that stays down is given to the map as its own answer at the limit`() {
        val wire = Wire(listOf(status(503)))
        network.validatedAt = 0

        val response = call(wire).execute()

        assertEquals(503, response.code)
        assertEquals(300_000L, now)
    }

    @Test
    fun `a held tile that MapLibre cancels is let go within a second`() {
        val wire = Wire(listOf(throws(UnknownHostException("x"))))
        val call = call(wire)
        onTime = { t -> if (t >= 3_000 && !call.isCanceled()) call.cancel() }

        try {
            call.execute()
            fail("a cancelled call returned a response")
        } catch (e: IOException) {
            assertEquals("Canceled", e.message)
        }
        assertEquals(1, wire.sent.size)
        assertTrue("let go at ${now} ms", now <= 4_000)
        assertEquals(0, gate.heldNow)
    }

    @Test
    fun `when 8 are already held a new failure goes straight to the map`() {
        repeat(8) { assertTrue(gate.tryHold()) }
        val dns = UnknownHostException("x")
        val wire = Wire(listOf(throws(dns), ok("t")))
        network.validatedAt = 1_000

        try {
            call(wire).execute()
            fail("a ninth tile was held")
        } catch (e: IOException) {
            assertSame(dns, e)
        }
        assertEquals(1, wire.sent.size)
        assertEquals(0L, now)
        assertEquals(8, gate.heldNow)
        assertTrue(logs.joinToString("\n"), logs.any { "not held" in it })
    }

    @Test
    fun `a style or font request is never held`() {
        val dns = UnknownHostException("x")
        val wire = Wire(listOf(throws(dns), ok("t")))
        network.validatedAt = 1_000

        try {
            call(wire, "https://forager-pmtiles.brandonlee1-894.workers.dev/style/street-v2.json").execute()
            fail("a style request was held")
        } catch (e: IOException) {
            assertSame(dns, e)
        }
        assertEquals(1, wire.sent.size)
    }

    @Test
    fun `a missing tile is not a failure and is not held`() {
        val wire = Wire(listOf(status(404), ok("t")))
        network.validatedAt = 0

        assertEquals(404, call(wire).execute().code)
        assertEquals(1, wire.sent.size)
    }
}
