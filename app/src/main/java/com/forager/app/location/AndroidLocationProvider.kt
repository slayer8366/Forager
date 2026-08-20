package com.forager.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult {
        if (!hasLocationPermission()) return LocationResult.PermissionDenied

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = enabledProviders(locationManager)
        if (providers.isEmpty()) return LocationResult.LocationUnavailable

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            awaitFirstLocation(locationManager, providers)
        }

        return location?.let {
            LocationResult.Success(
                lat = it.latitude,
                lng = it.longitude,
                altitude = if (it.hasAltitude()) it.altitude else null,
            )
        } ?: LocationResult.LocationUnavailable
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun enabledProviders(locationManager: LocationManager): List<String> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }

    /**
     * Requests a single update from every provider in [providers] at once and resolves with
     * whichever produces a fix first, cancelling the rest.
     *
     * This used to request GPS alone whenever it was enabled, falling back to network only if GPS
     * was off entirely. That starves a real fix on a cold GPS lock: a first-use or indoor GPS fix
     * routinely takes well past this call's timeout, while a network-based fix typically resolves
     * in a second or two — so preferring GPS unconditionally meant "couldn't determine your
     * location" even though a network fix was available the whole time. Racing both is strictly
     * better than picking one upfront: whichever answers first wins, and if only one provider is
     * enabled, this degrades to exactly the old single-provider behavior.
     */
    @SuppressLint("MissingPermission")
    private suspend fun awaitFirstLocation(locationManager: LocationManager, providers: List<String>): Location? =
        suspendCancellableCoroutine { continuation ->
            val listeners = mutableMapOf<String, LocationListener>()

            fun removeAllUpdates() {
                listeners.values.forEach { locationManager.removeUpdates(it) }
            }

            for (provider in providers) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        if (continuation.isActive) {
                            removeAllUpdates()
                            continuation.resume(location) { _, _, _ -> }
                        }
                    }

                    @Suppress("OVERRIDE_DEPRECATION")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                listeners[provider] = listener

                try {
                    @Suppress("DEPRECATION")
                    locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                } catch (e: SecurityException) {
                    // Another provider in the race may still resolve; only fail outright if none do
                    // (the withTimeoutOrNull wrapping this call then returns null, same as before).
                    listeners.remove(provider)
                }
            }

            if (listeners.isEmpty() && continuation.isActive) {
                continuation.resume(null) { _, _, _ -> }
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation { removeAllUpdates() }
        }

    private companion object {
        // Long enough for a real cold GPS fix to have a fair shot when network locating isn't
        // available at all (the race above is what actually protects the common case — this bound
        // only matters when GPS is the only enabled provider).
        const val LOCATION_TIMEOUT_MS = 20_000L
    }
}
