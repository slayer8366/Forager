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
        val provider = selectProvider(locationManager) ?: return LocationResult.LocationUnavailable

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            awaitSingleLocation(locationManager, provider)
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

    private fun selectProvider(locationManager: LocationManager): String? = when {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    @SuppressLint("MissingPermission")
    private suspend fun awaitSingleLocation(locationManager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (continuation.isActive) continuation.resume(location) { _, _, _ -> }
                }

                @Suppress("OVERRIDE_DEPRECATION")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                override fun onProviderEnabled(provider: String) = Unit
                override fun onProviderDisabled(provider: String) = Unit
            }

            try {
                @Suppress("DEPRECATION")
                locationManager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null) { _, _, _ -> }
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation { locationManager.removeUpdates(listener) }
        }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 15_000L
    }
}
