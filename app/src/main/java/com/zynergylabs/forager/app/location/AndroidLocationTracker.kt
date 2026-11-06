package com.zynergylabs.forager.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.disagreesWithTimestampRule
import com.zynergylabs.forager.app.domain.fixIntervalMillis
import com.zynergylabs.forager.app.domain.isNetworkProviderTimestamp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.transformWhile

/**
 * Plain [LocationManager], not a fused/Play-Services provider — matching
 * [AndroidLocationProvider]'s existing choice, so track recording doesn't introduce this project's
 * first Play Services dependency for a capability the platform API already provides.
 *
 * Requests updates from every enabled provider (GPS and network) at once rather than picking one,
 * unlike [AndroidLocationProvider.selectProvider]'s one-shot pick: a multi-hour recording can
 * outlast a single provider losing its fix (GPS under canopy, say), and [LocationSampler] downstream
 * already filters on reported accuracy regardless of which provider a fix came from.
 *
 * **How often** (dispatch 2026-09-28-767, Battery saver): every [FIX_INTERVAL_MILLIS][com.zynergylabs.forager.app.domain.FIX_INTERVAL_MILLIS],
 * or every [BATTERY_SAVER_FIX_INTERVAL_MILLIS][com.zynergylabs.forager.app.domain.BATTERY_SAVER_FIX_INTERVAL_MILLIS]
 * while [batterySaverOn] is true. A change while a collection is live re-registers at the new interval
 * at once, so the switch takes effect mid-recording. Every collector follows it: see
 * [com.zynergylabs.forager.app.domain.BatterySaverPreferenceRepository] for why the saver slows them all.
 * The contract on a missing permission is unchanged: one [LocationFix.PermissionDenied], then the flow
 * completes, which [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel] relies on to
 * collect again after a grant.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLocationTracker(
    private val context: Context,
    /** The Battery saver switch; never on when not given. */
    private val batterySaverOn: Flow<Boolean> = flowOf(false),
) : LocationTracker {

    override val fixes: Flow<LocationFix> = batterySaverOn
        .distinctUntilChanged()
        .flatMapLatest { saverOn -> registration(fixIntervalMillis(saverOn)) }
        // The switch's flow never completes; this keeps the tracker's own contract that a missing
        // permission ends the collection after its one PermissionDenied.
        .transformWhile { fix ->
            emit(fix)
            fix !is LocationFix.PermissionDenied
        }

    /** One platform registration at [intervalMillis], on every enabled provider; removed when its collection ends. */
    @SuppressLint("MissingPermission")
    private fun registration(intervalMillis: Long): Flow<LocationFix> = callbackFlow {
        if (!hasLocationPermission(context)) {
            trySend(LocationFix.PermissionDenied)
            close()
            return@callbackFlow
        }

        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                // The return-estimate dispatch's instrument walk (owner ruling: "do the per-fix log
                // first"). Everything the platform reports per fix that this app currently discards
                // -- provider, Doppler speed and its accuracy, bearing -- logged so one walk with
                // `adb logcat -s ForagerFix` answers whether this phone's GNSS fixes carry a usable
                // speed (and whether hasSpeed() == false is the network-fix tell the pre-build
                // report predicted). hasSpeedAccuracy()/getSpeedAccuracyMetersPerSecond() are API 26,
                // this app's minSdk. Diagnostic; nothing reads it. Debug level so it costs nothing
                // in a release build's logcat filter.
                Log.d(
                    FIX_LOG_TAG,
                    "provider=${location.provider} " +
                        "acc=${if (location.hasAccuracy()) location.accuracy else null} " +
                        "hasSpeed=${location.hasSpeed()} speed=${if (location.hasSpeed()) location.speed else null} " +
                        "hasSpeedAccuracy=${location.hasSpeedAccuracy()} " +
                        "speedAccuracy=${if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else null} " +
                        "hasBearing=${location.hasBearing()} time=${location.time}",
                )
                val fix = location.toFix()
                // Dispatch 2026-09-28-527: where the provider and the timestamp rule disagree, so a
                // phone where the rule fails becomes visible. Silent while they agree; debug level, as
                // the line above.
                if (fix.provider.disagreesWithTimestampRule(fix.timestampEpochMillis)) {
                    Log.d(
                        RULE_LOG_TAG,
                        "provider=${location.provider} " +
                            "timestampRule=${if (isNetworkProviderTimestamp(fix.timestampEpochMillis)) "network" else "gps"} " +
                            "time=${fix.timestampEpochMillis}",
                    )
                }
                trySend(fix)
            }

            @Suppress("OVERRIDE_DEPRECATION")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val requestedProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { locationManager.isProviderEnabled(it) }
        requestedProviders.forEach { provider ->
            locationManager.requestLocationUpdates(provider, intervalMillis, 0f, listener, Looper.getMainLooper())
        }
        // Dispatch 2026-09-28-767: each registration and its interval, so a device check can see the
        // saver take effect -- `adb logcat -s ForagerFixRate`. Debug level, as the per-fix log.
        Log.d(RATE_LOG_TAG, "intervalMillis=$intervalMillis providers=$requestedProviders")

        awaitClose { locationManager.removeUpdates(listener) }
    }

    // Speed and its accuracy (return-estimate dispatch, Item 3) under the same has*() rule as
    // altitude and accuracy: the platform says whether the value is meaningful, and "not
    // reported" travels as null, never as a zero that would read as "stopped".
    private fun Location.toFix() = LocationFix.Update(
        lat = latitude,
        lng = longitude,
        altitude = if (hasAltitude()) altitude else null,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
        timestampEpochMillis = time,
        provider = fixProviderOf(provider),
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        speedAccuracyMetersPerSecond = if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond else null,
    )

    internal companion object {
        /**
         * The platform's provider name as this app's own [FixProvider] (dispatch 2026-09-28-527): GPS and
         * network by the platform's constants, anything else, `null` included, [FixProvider.UNKNOWN] and
         * never a guess. Shared with [AndroidLastKnownLocationSource], which reads the same `Location`s.
         */
        internal fun fixProviderOf(provider: String?): FixProvider = when (provider) {
            LocationManager.GPS_PROVIDER -> FixProvider.GPS
            LocationManager.NETWORK_PROVIDER -> FixProvider.NETWORK
            else -> FixProvider.UNKNOWN
        }

        /** Each registration's interval and providers (dispatch 2026-09-28-767) — `adb logcat -s ForagerFixRate`. */
        internal const val RATE_LOG_TAG = "ForagerFixRate"

        /** The per-fix instrument log's tag — `adb logcat -s ForagerFix`. */
        internal const val FIX_LOG_TAG = "ForagerFix"

        /**
         * Dispatch 2026-09-28-527: a fix whose provider and the timestamp rule disagree —
         * `adb logcat -s ForagerFixRule`. Silent while they agree.
         */
        internal const val RULE_LOG_TAG = "ForagerFixRule"
    }
}
