package com.zynergylabs.forager.app.map

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * [MapLibreInitializer] (dispatch 2026-09-28-106, F2), with the SDK call replaced by a lambda:
 * MapLibre's native library cannot load under Robolectric, so what these tests reach is the
 * once-only, retry-after-failure and logging behaviour, not `MapLibre.getInstance` itself. The
 * application-start test drives the real `ForagerApplication` that Robolectric boots and reads its
 * log; on the JVM the real call fails, and that failure being logged is what it observes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapLibreInitializerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val infos = mutableListOf<String>()
    private val errors = mutableListOf<Pair<String, Throwable>>()

    private fun initializer(installHttpClient: () -> Unit = {}, getInstance: (Context) -> Unit) =
        MapLibreInitializer(getInstance, installHttpClient, { infos += it }, { message, cause -> errors += message to cause })

    @Test
    fun `the SDK is initialised once however many callers ask`() {
        var calls = 0
        val initializer = initializer { calls++ }

        repeat(3) { initializer.initialize(context) }

        assertEquals(1, calls)
        assertEquals(1, infos.size)
    }

    @Test
    fun `a failed initialisation is logged and rethrown, and the next call tries again`() {
        val boom = IllegalStateException("boom")
        var calls = 0
        val initializer = initializer { if (calls++ == 0) throw boom }

        val thrown = assertThrows(IllegalStateException::class.java) { initializer.initialize(context) }
        assertSame(boom, thrown)
        assertEquals(1, errors.size)
        assertSame(boom, errors.single().second)

        initializer.initialize(context)
        initializer.initialize(context)
        assertEquals(2, calls)
    }

    @Test
    fun `the application initialises MapLibre at start, before any screen or read`() {
        val logged = ShadowLog.getLogs().filter { it.tag == "MapLibreStorage" }

        assertTrue("no MapLibre initialisation was attempted at application start: $logged", logged.isNotEmpty())
    }

    /**
     * Dispatch 2026-10-18-04, item 2. MapLibre's `HttpRequestImpl` reads `MapLibre.getApplicationContext()` in its static
     * initialiser, which throws while MapLibre is not initialised (`javap` on 13.5.0: `HttpIdentifier.getIdentifier()`
     * calls it outside its own try, and `validateMapLibre` throws `MapLibreConfigurationException`). Installing the app's
     * client before a successful initialisation therefore fails, and that failure was the path on which tiles would go out
     * with MapLibre's own User-Agent. The client goes in straight after the SDK call succeeds, inside the one function every
     * entry point calls, and never before it.
     */
    @Test
    fun `the map's HTTP client is installed straight after MapLibre initialises, and once`() {
        val order = mutableListOf<String>()
        val initializer = initializer(getInstance = { order += "sdk" }, installHttpClient = { order += "client" })

        repeat(3) { initializer.initialize(context) }

        assertEquals(listOf("sdk", "client"), order)
    }

    @Test
    fun `no HTTP client is installed while MapLibre has failed to initialise, and it is on the next success`() {
        var installs = 0
        var calls = 0
        val initializer = initializer(
            getInstance = { if (calls++ == 0) throw IllegalStateException("boom") },
            installHttpClient = { installs++ },
        )

        assertThrows(IllegalStateException::class.java) { initializer.initialize(context) }
        assertEquals("the client was installed although MapLibre had not initialised", 0, installs)

        initializer.initialize(context)
        assertEquals(1, installs)
    }

    /**
     * The owner's "option i" (dispatch 2026-10-18-04, item 2): if the app's client cannot be installed, that is a MapLibre
     * initialisation failure. It is logged and rethrown, nothing is remembered as done, and the next call tries both
     * again, so MapLibre is never left running on its own client and User-Agent.
     */
    @Test
    fun `a client that cannot be installed fails the initialisation, which the next call retries`() {
        val boom = IllegalStateException("no client")
        var sdkCalls = 0
        var installs = 0
        val initializer = initializer(installHttpClient = { if (installs++ == 0) throw boom }) { sdkCalls++ }

        val thrown = assertThrows(IllegalStateException::class.java) { initializer.initialize(context) }
        assertSame(boom, thrown)
        assertSame(boom, errors.single().second)
        assertTrue("an install failure was reported as initialised: $infos", infos.isEmpty())

        initializer.initialize(context)
        initializer.initialize(context)
        assertEquals(2, sdkCalls)
        assertEquals(2, installs)
        assertEquals(1, infos.size)
    }
}
