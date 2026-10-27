package com.zynergylabs.forager.app.data.referencetrails

import com.zynergylabs.forager.app.data.referencetrails.WorkerReferenceTrailsManifestSource.Companion.MANIFEST_URL
import com.zynergylabs.forager.app.data.referencetrails.WorkerReferenceTrailsManifestSource.Companion.MAX_MANIFEST_BYTES
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The manifest fetch's own paths (dispatch 2026-10-26-24 Amendment 1, Sorrel's S2 on PR #424): the 256 KB operating
 * limit, a non-200 answer, and a failed connection. Each case goes through a real [OkHttpClient] whose last interceptor
 * stands in for the wire, as `MapUserAgentTest` and `TileRetryInterceptorTest` do (no MockWebServer in this project).
 *
 * The no-body branch (`response.body == null`) is not tested here: OkHttp 4.12's interceptor chain refuses a response
 * without a body before the source sees it ("returned a response with no body", `RealInterceptorChain`), and a response
 * off the network always carries one, so no call through a real client can reach that branch.
 */
class WorkerReferenceTrailsManifestSourceTest {

    /** The wire: answers every request with [answer], recording what it was sent. */
    private class Wire(private val answer: (Request) -> Response) : Interceptor {
        val sent = mutableListOf<Request>()
        override fun intercept(chain: Interceptor.Chain): Response {
            sent += chain.request()
            return answer(chain.request())
        }
    }

    private fun answering(code: Int, body: String): (Request) -> Response = { request ->
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("m")
            .body(body.toResponseBody(null)).build()
    }

    private fun fetch(wire: Wire): Result<String> = runBlocking {
        WorkerReferenceTrailsManifestSource(OkHttpClient.Builder().addInterceptor(wire).build(), io = Dispatchers.Unconfined).fetch()
    }

    private fun failureMessage(result: Result<String>): String? {
        assertTrue("expected a failure, was $result", result.isFailure)
        return result.exceptionOrNull()?.message
    }

    @Test
    fun `a 200 answer is returned as read, from one GET to the Worker's manifest`() {
        val wire = Wire(answering(200, """{"sources":[]}"""))
        assertEquals(Result.success("""{"sources":[]}"""), fetch(wire))
        assertEquals(1, wire.sent.size)
        assertEquals("GET", wire.sent.single().method)
        assertEquals(MANIFEST_URL, wire.sent.single().url)
    }

    @Test
    fun `a non-200 answer is a failure naming the code, and its body is not returned`() {
        listOf(404, 500, 503).forEach { code ->
            val result = fetch(Wire(answering(code, """{"sources":[]}""")))
            assertEquals("answer $code", "the manifest answered $code", failureMessage(result))
        }
    }

    @Test
    fun `a body of exactly the limit is read whole`() {
        val body = "x".repeat(MAX_MANIFEST_BYTES.toInt())
        val result = fetch(Wire(answering(200, body)))
        assertTrue("expected success, was ${result.exceptionOrNull()}", result.isSuccess)
        assertEquals(MAX_MANIFEST_BYTES.toInt(), result.getOrThrow().length)
    }

    @Test
    fun `a body one byte over the limit is a failure, not a truncated read`() {
        val result = fetch(Wire(answering(200, "x".repeat(MAX_MANIFEST_BYTES.toInt() + 1))))
        assertEquals("the manifest is over 262144 bytes", failureMessage(result))
    }

    @Test
    fun `a connection that fails is a failure carrying that error`() {
        val result = fetch(Wire { throw UnknownHostException("no network") })
        assertEquals("no network", failureMessage(result))
        assertTrue(result.exceptionOrNull() is UnknownHostException)
    }
}
