package com.zynergylabs.forager.app.map

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.ForagerApplication
import com.zynergylabs.forager.app.net.AppUserAgent
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The map's User-Agent (dispatch 2026-09-28-325). MapLibre 13.5.0 adds its own `User-Agent` to
 * every request it builds (`HttpRequestImpl.executeRequest`, verified with `javap`), so the header
 * is tested the way it is sent: a request that already carries MapLibre's value goes through the
 * real client [mapHttpClient] builds, and the last interceptor in the chain records what would
 * have gone on the wire. No MockWebServer is a dependency of this project, and none was added.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapUserAgentTest {

    @Test
    fun `the string is exactly the agreed shape, built from the given parts`() {
        assertEquals(
            "Forager/1.0.7+gabc123 (Android 15; com.example.app; +https://zynergy-labs.com; support@zynergy-labs.com)",
            AppUserAgent.build(versionName = "1.0.7+gabc123", androidRelease = "15", applicationId = "com.example.app"),
        )
    }

    @Test
    fun `the app's own string takes its version and id from BuildConfig and its release from the device`() {
        val expected = "Forager/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; " +
            "${BuildConfig.APPLICATION_ID}; ${AppUserAgent.CONTACT})"

        assertEquals(expected, AppUserAgent.forThisApp())
        assertTrue(BuildConfig.VERSION_NAME.isNotEmpty())
        assertTrue(AppUserAgent.forThisApp().startsWith("Forager/${BuildConfig.VERSION_NAME} (Android "))
    }

    @Test
    fun `the contact is the owner's, not the placeholder`() {
        assertEquals("+https://zynergy-labs.com; support@zynergy-labs.com", AppUserAgent.CONTACT)
    }

    @Test
    fun `the client replaces the library's own User-Agent rather than adding a second`() {
        val seen = sendThrough(mapHttpClient("Forager/test (Android 15; id; +c)"), libraryUserAgent = "MapLibre Android/13.5.0")

        assertEquals(listOf("Forager/test (Android 15; id; +c)"), seen.headers("User-Agent"))
    }

    @Test
    fun `the client keeps MapLibre's own request limit per host`() {
        assertEquals(20, mapHttpClient("x").dispatcher.maxRequestsPerHost)
    }

    /**
     * The owner's "option i" (dispatch 2026-10-18-04, item 2; RECORD -1278, and -1283 for this test): the app's client goes
     * into MapLibre only once MapLibre has initialised, so MapLibre is never left running on a client installed beside a
     * failed start. Under Robolectric MapLibre's native library cannot load, so the start-up initialisation fails; this
     * reads what the real `ForagerApplication.onCreate` that Robolectric booted left in MapLibre's client slot. It replaces
     * "starting the application installs the client…", which pinned the old install-anyway behaviour (RECORD -1283).
     */
    @Test
    fun `application start where MapLibre fails to initialise installs no HTTP client`() {
        // Robolectric has already booted the manifest's ForagerApplication; its onCreate is what is under test.
        assertTrue(ApplicationProvider.getApplicationContext<android.content.Context>() is ForagerApplication)

        assertNull("ForagerApplication.onCreate installed an HTTP client although MapLibre failed to initialise", mapLibreClient())
    }

    /**
     * A successful install puts a client into MapLibre's client slot that sends the app's own User-Agent, replacing the
     * library's. [installMapHttpClient] is what `MapLibreStorage`'s initialiser calls once MapLibre is ready; it is called
     * directly here because MapLibre cannot initialise under Robolectric. The slot is static, so it is put back afterwards.
     */
    @Test
    fun `a successful install carries the app's User-Agent into MapLibre`() {
        val before = mapLibreClient()
        try {
            installMapHttpClient()

            val installed = mapLibreClient()
            assertNotNull("installMapHttpClient put no HTTP client into MapLibre", installed)
            val seen = sendThrough(installed as OkHttpClient, libraryUserAgent = "MapLibre Android/13.5.0")
            assertEquals(listOf(AppUserAgent.forThisApp()), seen.headers("User-Agent"))
        } finally {
            mapLibreClientField().set(null, before)
        }
    }

    private fun mapLibreClientField() = Class.forName("org.maplibre.android.module.http.HttpRequestImpl")
        .getDeclaredField("client").apply { isAccessible = true }

    private fun mapLibreClient(): Call.Factory? = mapLibreClientField().get(null) as? Call.Factory

    /** Sends a request carrying [libraryUserAgent] through [client]; returns the request as the last interceptor saw it. */
    private fun sendThrough(client: OkHttpClient, libraryUserAgent: String): Request {
        lateinit var seen: Request
        val recording = Interceptor { chain ->
            seen = chain.request()
            Response.Builder().request(seen).protocol(Protocol.HTTP_1_1).code(200).message("OK").body("".toResponseBody(null)).build()
        }
        client.newBuilder().addInterceptor(recording).build()
            .newCall(Request.Builder().url("https://tile.openstreetmap.org/1/0/0.png").addHeader("User-Agent", libraryUserAgent).build())
            .execute().close()
        return seen
    }
}
