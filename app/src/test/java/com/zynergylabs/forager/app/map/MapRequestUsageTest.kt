package com.zynergylabs.forager.app.map

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.http.HttpRequest
import org.maplibre.android.http.HttpResponder
import org.maplibre.android.module.http.HttpRequestImpl
import org.maplibre.android.module.http.HttpRequestUtil
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A request MapLibre makes for an offline-area download reaches the map client marked as one (dispatch 2026-10-23-06
 * Amendment 3). Driven through MapLibre 13.5.0's own `HttpRequestImpl`, the class the engine calls, so the test holds
 * the detail the marking rests on: that it builds and creates its call on the calling thread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapRequestUsageTest {

    private val seen = mutableListOf<Request>()
    private val answered = CountDownLatch(1)
    private val wire = Interceptor { chain ->
        synchronized(seen) { seen += chain.request() }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("t".toResponseBody(null)).build()
    }
    private val responder = object : HttpResponder {
        override fun onResponse(
            responseCode: Int, eTag: String?, lastModified: String?, cacheControl: String?, expires: String?,
            retryAfter: String?, xRateLimitReset: String?, body: ByteArray?,
        ) = answered.countDown()

        override fun handleFailure(type: Int, errorMessage: String?) = answered.countDown()
    }
    private val tileUrl = "https://forager-pmtiles.brandonlee1-894.workers.dev/us/12/660/1464.mvt"

    @Before
    fun install() {
        HttpRequestUtil.setOkHttpClient(UsageTaggingCallFactory(OkHttpClient.Builder().addInterceptor(wire).build()))
    }

    @After
    fun uninstall() {
        HttpRequestImpl::class.java.getDeclaredField("client").apply { isAccessible = true }.set(null, null)
    }

    private fun send(offlineUsage: Boolean): Request {
        UsageMarkingHttpRequest(HttpRequestImpl()).executeRequest(responder, 0L, tileUrl, "", "", "", offlineUsage)
        assertTrue("MapLibre's request never answered", answered.await(10, TimeUnit.SECONDS))
        return synchronized(seen) { seen.single() }
    }

    @Test
    fun `a download's request is marked offline`() {
        assertEquals(MapRequestUsage(offline = true), send(offlineUsage = true).tag(MapRequestUsage::class.java))
    }

    @Test
    fun `the map's request is marked as the map's`() {
        assertEquals(MapRequestUsage(offline = false), send(offlineUsage = false).tag(MapRequestUsage::class.java))
    }

    @Test
    fun `a call made outside MapLibre's request is not marked`() {
        val factory = UsageTaggingCallFactory(OkHttpClient.Builder().addInterceptor(wire).build())
        factory.newCall(Request.Builder().url(tileUrl).build()).execute().close()
        assertNull(seen.single().tag(MapRequestUsage::class.java))
    }

    @Test
    fun `the mark does not outlive the request, even when MapLibre's request throws`() {
        val throwing = object : HttpRequest {
            override fun executeRequest(
                httpRequest: HttpResponder, nativePtr: Long, resourceUrl: String, dataRange: String, etag: String,
                modified: String, offlineUsage: Boolean,
            ) = throw IllegalStateException("boom")

            override fun cancelRequest() = Unit
        }
        runCatching { UsageMarkingHttpRequest(throwing).executeRequest(responder, 0L, tileUrl, "", "", "", true) }
        val factory = UsageTaggingCallFactory(OkHttpClient.Builder().addInterceptor(wire).build())
        factory.newCall(Request.Builder().url(tileUrl).build()).execute().close()
        assertNull("a later call on this thread carried the mark", seen.single().tag(MapRequestUsage::class.java))
    }
}
