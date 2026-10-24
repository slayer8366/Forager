package com.zynergylabs.forager.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.zynergylabs.forager.app.domain.NetworkSignal
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * [NetworkSignal] from Android's own judgement: `ConnectivityManager.registerDefaultNetworkCallback`, reading
 * `NET_CAPABILITY_VALIDATED` (dispatch 2026-10-22-01 Amendment 2, RECORD -1556). A callback, so nothing polls.
 *
 * **Permission.** `ACCESS_NETWORK_STATE`, a normal permission with no prompt. The app's own manifest does not declare
 * it; MapLibre 13.5.0's and WorkManager 2.12.0's library manifests do, so it is in the merged manifest already (the
 * verify-first report for this dispatch, item 2). Nothing is added to the manifest by this file.
 *
 * The first value is read directly ([isValidated] of the active network), because the callback says nothing at all
 * when there is no default network, which is exactly the no-signal case. If the platform refuses the callback
 * (it throws for an app over its callback limit), that is logged and the flow reports the first reading only.
 */
internal class AndroidNetworkSignal(context: Context) : NetworkSignal {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override val validated: Flow<Boolean> = callbackFlow {
        val manager = connectivity
        if (manager == null) {
            Log.w(TAG, "No ConnectivityManager; treating the network as validated, so the chosen map is never swapped.")
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }
        trySend(isValidated(manager.getNetworkCapabilities(manager.activeNetwork)))
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySendBlocking(isValidated(capabilities))
            }

            override fun onLost(network: Network) {
                trySendBlocking(false)
            }
        }
        val registered = try {
            manager.registerDefaultNetworkCallback(callback)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't register for network changes; the map keeps the first reading.", e)
            false
        }
        awaitClose { if (registered) manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private companion object {
        const val TAG = "NetworkSignal"
    }
}

/** Whether [capabilities] say the network reaches the internet: present and carrying `NET_CAPABILITY_VALIDATED`. */
internal fun isValidated(capabilities: NetworkCapabilities?): Boolean =
    capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
