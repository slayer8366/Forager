package com.zynergylabs.forager.app.map

import com.zynergylabs.forager.app.domain.TileFailure
import com.zynergylabs.forager.app.domain.TileHoldGate
import com.zynergylabs.forager.app.domain.TileRetryDecision
import com.zynergylabs.forager.app.domain.TileRetryPolicy
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holds a failed map tile request open and sends it again, so a patch of the map that failed to load fills in by
 * itself (dispatch 2026-10-23-06 Amendment 1; RECORD -1577). The rules are [TileRetryPolicy]'s; this applies them to
 * OkHttp, the client MapLibre sends every request through ([mapHttpClient]).
 *
 * Only tile requests (`.mvt`) are held; the style, sprites and fonts pass straight through. A tile request is held after
 * a connection error, a timeout or a 5xx answer. While held the tile stays "loading" in MapLibre, which draws it from a
 * lower zoom exactly as it does for a failed tile, until this returns:
 * - the first answer that is not a failure, once the network is back or a backoff has passed;
 * - the last failure, unchanged, once [TileRetryPolicy]'s hold limit has passed, or straight away when
 *   [TileHoldGate]'s limit is already held;
 * - a cancellation, within [checkEveryMs], when MapLibre cancels the call (the tile left the view).
 *
 * Offline-area downloads go through the same client and are held the same way: the owner chose "pause and continue"
 * for a download that loses signal (dispatch 2026-10-23-06 Amendment 1).
 *
 * Blocking by design: an OkHttp interceptor runs on the call's own thread. Waiting is in slices of [checkEveryMs] so a
 * cancelled call is let go promptly; there is no polling of the network, [network] wakes the wait when it changes.
 */
internal class TileRetryInterceptor(
    private val policy: TileRetryPolicy,
    private val gate: TileHoldGate,
    private val network: NetworkWait,
    private val clock: () -> Long,
    private val pause: (Long) -> Unit,
    private val log: (String) -> Unit,
    private val checkEveryMs: Long = TILE_RETRY_CANCEL_CHECK_MS,
) : Interceptor {
    private val notHeldLogged = AtomicInteger(0)

    override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request()) // FAILING-FIRST STUB

    private fun attempt(chain: Interceptor.Chain): Attempt = try {
        val response = chain.proceed(chain.request())
        if (response.code in 500..599) Attempt(response, null, TileFailure.Server) else Attempt(response, null, null)
    } catch (e: IOException) {
        if (chain.call().isCanceled()) throw e
        Attempt(null, e, if (e is SocketTimeoutException) TileFailure.Timeout else TileFailure.Connection)
    }

    /** Waits until the network is validated or [deadline]; the next attempt then runs (and fails into GiveUp past it). */
    private fun waitForNetwork(chain: Interceptor.Chain, deadline: Long) {
        while (true) {
            throwIfCanceled(chain)
            if (network.isValidated()) return
            val remaining = deadline - clock()
            if (remaining <= 0) return
            network.awaitValidated(minOf(checkEveryMs, remaining))
        }
    }

    /** Waits [delayMs], never past [deadline]. */
    private fun waitFor(chain: Interceptor.Chain, delayMs: Long, deadline: Long) {
        val end = minOf(clock() + delayMs, deadline)
        while (true) {
            throwIfCanceled(chain)
            val remaining = end - clock()
            if (remaining <= 0) return
            try {
                pause(minOf(checkEveryMs, remaining))
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Interrupted while holding a tile request").apply { initCause(e) }
            }
        }
    }

    private fun throwIfCanceled(chain: Interceptor.Chain) {
        if (chain.call().isCanceled()) throw IOException("Canceled")
    }

    private fun logNotHeld(tile: String, failure: TileFailure) {
        val line = notHeldLogged.incrementAndGet()
        if (line > NOT_HELD_LINES_LOGGED) return
        val more = if (line == NOT_HELD_LINES_LOGGED) "; further lines like this are not logged in this process" else ""
        log("Tile $tile failed ($failure) and was not held: ${gate.limit} tile requests are already held$more.")
    }

    /** One answer from the wire: a response, or the exception the call threw. */
    private class Attempt(val response: Response?, val exception: IOException?, val failure: TileFailure?) {
        /** Hands the failure to the map unchanged: the 5xx response, or the exception rethrown. */
        fun deliver(): Response {
            exception?.let { throw it }
            return response!!
        }

        /** Closes a failed response before the request is sent again. */
        fun discard() {
            response?.close()
        }
    }

    private companion object {
        const val NOT_HELD = Long.MIN_VALUE
        const val NOT_HELD_LINES_LOGGED = 10
    }
}

/**
 * How often a held tile request checks whether MapLibre cancelled it: once a second, and only while something is held.
 * A cancelled call is let go within this. The coder's choice, not measured.
 */
internal const val TILE_RETRY_CANCEL_CHECK_MS: Long = 1_000

/** Whether [url] is a vector tile, the only kind of map request that is held: `.mvt` at the end of the path. */
internal fun isMapTileRequest(url: HttpUrl): Boolean = url.encodedPath.endsWith(".mvt")

/** `z/x/y` from a tile URL's last three path segments, for the log. */
internal fun tileName(url: HttpUrl): String = url.pathSegments.takeLast(3).joinToString("/").removeSuffix(".mvt")
