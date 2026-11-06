package com.zynergylabs.forager.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.util.Log
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.LocationFix

/**
 * [LastKnownLocationSource] over the platform's [LocationManager], no Play services — dispatch
 * 2026-09-28-510, the report's item 4. Asks each provider for the position it still holds
 * ([LocationManager.getLastKnownLocation], API 1) and returns the newest by its own fix time, the same
 * clock [LocationFix.Update.timestampEpochMillis] carries everywhere else, so its age reads the same way.
 *
 * The passive provider is asked too: it holds the newest fix any app on the phone received, which is
 * often newer than the GPS or network provider's own when this app has not been running.
 */
class AndroidLastKnownLocationSource(
    private val context: Context,
) : LastKnownLocationSource {

    @SuppressLint("MissingPermission") // hasLocationPermission(context) is the real (runtime) check.
    override fun lastKnown(): LocationFix.Update? {
        if (!hasLocationPermission(context)) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return PROVIDERS.mapNotNull { lastKnownFrom(locationManager, it) }
            .maxByOrNull { it.time }
            ?.toFix()
    }

    @SuppressLint("MissingPermission")
    private fun lastKnownFrom(locationManager: LocationManager, provider: String): Location? = try {
        locationManager.getLastKnownLocation(provider)
    } catch (e: SecurityException) {
        // Permission withdrawn between the check and the call: that provider's position is not read, and said so.
        Log.w(TAG, "Not allowed to read the $provider provider's last known location.", e)
        null
    } catch (e: IllegalArgumentException) {
        // A phone without this provider: nothing to read from it, and said so.
        Log.w(TAG, "No $provider provider on this phone; its last known location is not read.", e)
        null
    }

    // Field for field as AndroidLocationTracker maps a live fix, under the same has*() rule: "not
    // reported" travels as null, never as a zero.
    private fun Location.toFix() = LocationFix.Update(
        lat = latitude,
        lng = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampEpochMillis = time,
        // Display only, never acted on (dispatch -510); its provider is carried all the same, by the
        // tracker's own rule, so a fix never travels without one (dispatch -527).
        provider = AndroidLocationTracker.fixProviderOf(provider),
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    )

    private companion object {
        val PROVIDERS = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        const val TAG = "ForagerLastKnown"
    }
}
