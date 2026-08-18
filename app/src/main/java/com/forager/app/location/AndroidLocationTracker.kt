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
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationTracker
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Plain [LocationManager], not a fused/Play-Services provider — matching
 * [AndroidLocationProvider]'s existing choice, so track recording doesn't introduce this project's
 * first Play Services dependency for a capability the platform API already provides.
 *
 * Requests updates from every enabled provider (GPS and network) at once rather than picking one,
 * unlike [AndroidLocationProvider.selectProvider]'s one-shot pick: a multi-hour recording can
 * outlast a single provider losing its fix (GPS under canopy, say), and [LocationSampler] downstream
 * already filters on reported accuracy regardless of which provider a fix came from.
 */
class AndroidLocationTracker(
    private val context: Context,
) : LocationTracker {

    @SuppressLint("MissingPermission")
    override val fixes: Flow<LocationFix> = callbackFlow {
        if (!hasLocationPermission()) {
            trySend(LocationFix.PermissionDenied)
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location.toFix())
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val requestedProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        requestedProviders.forEach { provider ->
            locationManager.requestLocationUpdates(provider, MIN_UPDATE_INTERVAL_MILLIS, 0f, listener, Looper.getMainLooper())
        }

        awaitClose { locationManager.removeUpdates(listener) }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    private fun Location.toFix() = LocationFix.Update(
        lat = latitude,
        lng = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampEpochMillis = time,
    )

    private companion object {
        // The platform's own throttle on how often it invokes the listener at all; the real
        // sampling decision (which of these become a persisted TrackPoint) is LocationSampler's,
        // downstream — this is only a ceiling on how much raw, unfiltered work this stream does.
        const val MIN_UPDATE_INTERVAL_MILLIS = 1_000L
    }
}
