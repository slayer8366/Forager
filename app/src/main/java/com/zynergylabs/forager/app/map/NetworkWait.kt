package com.zynergylabs.forager.app.map

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.zynergylabs.forager.app.domain.NetworkSignal
import com.zynergylabs.forager.app.domain.TileHoldGate
import com.zynergylabs.forager.app.domain.TileRetryPolicy
import com.zynergylabs.forager.app.net.AndroidNetworkSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * The network as a blocking caller needs it: [TileRetryInterceptor] runs on an OkHttp thread and waits there, where a
 * [NetworkSignal] flow cannot be collected. Tested through a fake in `TileRetryInterceptorTest`.
 */
internal interface NetworkWait {
    /** Whether the network is validated now. `false` until the first reading has arrived. */
    fun isValidated(): Boolean

    /** Returns when the network is validated, or after [timeoutMs], whichever is first. */
    fun awaitValidated(timeoutMs: Long)
}

/**
 * [NetworkWait] from a [NetworkSignal], collected in [scope] for as long as the scope lives. No polling: a waiter is
 * woken by each new reading.
 */
internal class SignalNetworkWait(signal: NetworkSignal, scope: CoroutineScope) : NetworkWait {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()

    @Volatile
    private var validated: Boolean? = null

    init {
        scope.launch {
            signal.validated.collect { value ->
                lock.withLock {
                    validated = value
                }
            }
        }
    }

    override fun isValidated(): Boolean = validated == true

    override fun awaitValidated(timeoutMs: Long) {
        if (timeoutMs <= 0) return
        lock.withLock {
            if (validated != true) changed.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }
}

/**
 * The [TileRetryInterceptor] the app's map client carries: the named limits, the phone's own validated-network signal
 * (the one the no-signal map rule reads, `AndroidNetworkSignal`), the monotonic clock, and a log line per held tile.
 * The signal is collected for the life of the process, like the client it serves.
 */
internal fun tileRetryInterceptor(context: Context): TileRetryInterceptor = TileRetryInterceptor(
    policy = TileRetryPolicy(),
    gate = TileHoldGate(),
    network = SignalNetworkWait(AndroidNetworkSignal(context), CoroutineScope(SupervisorJob() + Dispatchers.Default)),
    clock = { SystemClock.elapsedRealtime() },
    pause = { ms -> Thread.sleep(ms) },
    log = { message -> Log.i(TILE_RETRY_LOG_TAG, message) },
)

internal const val TILE_RETRY_LOG_TAG = "TileRetry"
