package com.zynergylabs.forager.app.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.LocationFix
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * The real tracker's half of the first-launch dispatch: permission is checked at each collection
 * start, so a collection begun before the grant is finished (one `PermissionDenied`, then
 * completion, no OS listener), and a *fresh* collection begun after the grant is the one that
 * registers listeners and delivers. Same Robolectric idioms as [AndroidLocationProviderTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidLocationTrackerTest {

    private lateinit var context: Application
    private lateinit var shadowLocationManager: ShadowLocationManager
    private lateinit var tracker: AndroidLocationTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        tracker = AndroidLocationTracker(context)
    }

    @Test
    fun `a collection started before the grant completes with PermissionDenied and registers no listener`() = runTest {
        val everything = tracker.fixes.toList()

        assertEquals(listOf(LocationFix.PermissionDenied), everything)
        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }

    @Test
    fun `a fresh collection after the grant registers a listener and delivers the fix`() = runTest {
        // The first-launch sequence: collected once too early, then granted, then collected again.
        assertEquals(listOf(LocationFix.PermissionDenied), tracker.fixes.toList())
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val pending = async { tracker.fixes.first() }
        // Not a single yield() as in AndroidLocationProviderTest: callbackFlow runs its producer
        // block in its own coroutine, one dispatch hop further than that test's
        // suspendCancellableCoroutine, so run the scheduler to idle before looking for the listener.
        advanceUntilIdle()
        assertEquals(1, shadowLocationManager.requestLocationUpdateListeners.size)

        shadowLocationManager.simulateLocation(
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 45.52
                longitude = -122.68
                accuracy = 12.5f
                time = 1_700_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 12.5f, timestampEpochMillis = 1_700_000_000_000L),
            pending.await(),
        )
    }

    /**
     * Return-estimate dispatch, Item 3: a fix whose platform `hasSpeed()`/`hasSpeedAccuracy()` are
     * true delivers both values on the Update; the network-fix test above, which sets neither,
     * pins the other side (`null`, via the Update's defaults — the same values the fix must carry).
     */
    @Test
    fun `a GPS fix with Doppler speed delivers the speed and its accuracy on the update`() = runTest {
        assertEquals(listOf(LocationFix.PermissionDenied), tracker.fixes.toList())
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        val pending = async { tracker.fixes.first() }
        advanceUntilIdle()

        shadowLocationManager.simulateLocation(
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = 45.52
                longitude = -122.68
                accuracy = 3.7900925f
                speed = 0.96f
                speedAccuracyMetersPerSecond = 0.6945308f
                time = 1_788_801_910_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 3.7900925f, timestampEpochMillis = 1_788_801_910_000L, speedMetersPerSecond = 0.96f, speedAccuracyMetersPerSecond = 0.6945308f),
            pending.await(),
        )
    }

    /**
     * Return-estimate dispatch, the instrument walk: every fix logs what the platform reports and
     * this app discards — provider, Doppler speed and its accuracy — so one walk with
     * `adb logcat -s ForagerFix` answers whether this phone's GNSS fixes carry a usable speed. The
     * fix itself is unchanged by the log (the delivered Update still carries the five fields).
     */
    @Test
    fun `every fix is logged with its provider, speed and speed accuracy for the instrument walk`() = runTest {
        assertEquals(listOf(LocationFix.PermissionDenied), tracker.fixes.toList())
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val pending = async { tracker.fixes.first() }
        advanceUntilIdle()

        shadowLocationManager.simulateLocation(
            Location(LocationManager.NETWORK_PROVIDER).apply {
                latitude = 45.52
                longitude = -122.68
                accuracy = 12.5f
                speed = 1.2f
                speedAccuracyMetersPerSecond = 0.3f
                time = 1_700_000_000_000L
            },
        )
        shadowOf(Looper.getMainLooper()).idle()
        pending.await()

        val line = org.robolectric.shadows.ShadowLog.getLogsForTag(AndroidLocationTracker.FIX_LOG_TAG).single().msg
        assertEquals(
            "provider=network acc=12.5 hasSpeed=true speed=1.2 hasSpeedAccuracy=true speedAccuracy=0.3 hasBearing=false time=1700000000000",
            line,
        )
    }
}
