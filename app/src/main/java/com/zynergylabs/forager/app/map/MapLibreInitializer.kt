package com.zynergylabs.forager.app.map

import android.content.Context

/**
 * Initialises MapLibre's native side once per process. `MapLibre.getInstance` is itself safe to
 * repeat, but this makes the first success and every failure visible in the log, and lets a test
 * replace the SDK call ([getInstance]): MapLibre's native library cannot load under Robolectric.
 *
 * A failure is logged and rethrown, never swallowed, and [initialize] tries again on the next call
 * (nothing is remembered as done until the SDK call returns).
 *
 * [installHttpClient] gives MapLibre the client that sends the app's own User-Agent, straight after the SDK call
 * succeeds and in the same synchronised call (dispatch 2026-10-18-04, item 2). It used to run separately at
 * application start, after a start-up initialisation that may have failed; MapLibre's `HttpRequestImpl` reads
 * `MapLibre.getApplicationContext()` in its static initialiser, which throws while MapLibre is not initialised
 * (`javap` on 13.5.0: `HttpIdentifier.getIdentifier()` calls it outside its own try; `validateMapLibre` throws
 * `MapLibreConfigurationException`). That install failed, logged "tiles go out with MapLibre's default User-Agent",
 * and left the class in its failed state. Here the client cannot be installed before MapLibre is ready, and every
 * entry point (application start, the map, the offline repository) goes through this one function.
 *
 * If the install throws, that is an initialisation failure like any other: logged, rethrown, nothing remembered as
 * done, and the next call tries both again. MapLibre is never left running on its own client, whose User-Agent
 * OpenStreetMap's tile policy says "will be blocked". The owner chose this ("option i") over keeping the map running
 * on that User-Agent, or running it with OpenStreetMap and OpenTopoMap tiles blocked and a message; the map is blank,
 * as on any initialisation failure, until a call succeeds. `MapLibre.getInstance` is safe to repeat.
 */
internal class MapLibreInitializer(
    private val getInstance: (Context) -> Unit,
    private val installHttpClient: () -> Unit,
    private val info: (String) -> Unit,
    private val error: (String, Throwable) -> Unit,
) {
    private var initialized = false

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        try {
            getInstance(context.applicationContext)
            installHttpClient()
        } catch (e: Throwable) {
            // Throwable, not Exception: a missing or unloadable native library is an UnsatisfiedLinkError.
            error("MapLibre initialisation failed; it will be retried on the next call.", e)
            throw e
        }
        initialized = true
        info("MapLibre initialised.")
    }
}
