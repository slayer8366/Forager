package com.zynergylabs.forager.app.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.FixProvider
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
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 12.5f, timestampEpochMillis = 1_700_000_000_000L, provider = FixProvider.NETWORK),
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
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 3.7900925f, timestampEpochMillis = 1_788_801_910_000L, speedMetersPerSecond = 0.96f, speedAccuracyMetersPerSecond = 0.6945308f, provider = FixProvider.GPS),
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

    // ── Dispatch 2026-09-28-527: every fix carries its true source ──

    /** Grants, starts one collection with [providers] enabled, delivers [location], and returns what the collection received. */
    private suspend fun kotlinx.coroutines.test.TestScope.deliver(location: Location, vararg providers: String): LocationFix {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        providers.forEach { shadowLocationManager.setProviderEnabled(it, true) }
        val pending = async { tracker.fixes.first() }
        advanceUntilIdle()
        shadowLocationManager.simulateLocation(location)
        shadowOf(Looper.getMainLooper()).idle()
        return pending.await()
    }

    private fun location(provider: String, time: Long, accuracy: Float = 15f) = Location(provider).apply {
        latitude = 45.52
        longitude = -122.68
        this.accuracy = accuracy
        this.time = time
    }

    /**
     * The provider survives from `Location` to [LocationFix.Update], beside every other field: a 15 m fix from
     * each of the two providers this app asks for, compared whole (this and the next). The two tests above
     * compare whole fixes too; these are the same claim at the accuracy the dispatch is about.
     */
    @Test
    fun `a 15 m GPS fix arrives as GPS, field for field`() = runTest {
        val gps = deliver(location(LocationManager.GPS_PROVIDER, 1_700_000_000_000L), LocationManager.GPS_PROVIDER)
        assertEquals(
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 15f, timestampEpochMillis = 1_700_000_000_000L, provider = FixProvider.GPS),
            gps,
        )
    }

    @Test
    fun `a 15 m network fix arrives as network, field for field`() = runTest {
        val network = deliver(location(LocationManager.NETWORK_PROVIDER, 1_700_000_000_123L), LocationManager.NETWORK_PROVIDER)
        assertEquals(
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 15f, timestampEpochMillis = 1_700_000_000_123L, provider = FixProvider.NETWORK),
            network,
        )
    }

    /**
     * Anything but the two platform constants is unknown, never a guess at one of them. A live collection only
     * ever registers GPS and network, so no other name can reach it under Robolectric or on the S22 (the
     * report, item 3); the mapping is read where the names come from, in [AndroidLocationTracker.fixProviderOf],
     * and through the one real path that can carry another name, the passive provider's last known location,
     * in [AndroidLastKnownLocationSourceTest].
     */
    @Test
    fun `fused, passive, an unheard-of name and a missing one are all unknown`() {
        listOf(LocationManager.FUSED_PROVIDER, LocationManager.PASSIVE_PROVIDER, "vendor-x", null).forEach { name ->
            assertEquals("provider \"$name\"", FixProvider.UNKNOWN, AndroidLocationTracker.fixProviderOf(name))
        }
        assertEquals(FixProvider.GPS, AndroidLocationTracker.fixProviderOf(LocationManager.GPS_PROVIDER))
        assertEquals(FixProvider.NETWORK, AndroidLocationTracker.fixProviderOf(LocationManager.NETWORK_PROVIDER))
    }

    private fun ruleLines() = org.robolectric.shadows.ShadowLog.getLogsForTag(AndroidLocationTracker.RULE_LOG_TAG).map { it.msg }

    /** The S22's own walk: a GPS fix stamped 479 ms past the second (`2026-10-03-walk-t21`). */
    @Test
    fun `a GPS fix stamped with milliseconds logs a disagreement with the timestamp rule`() = runTest {
        deliver(location(LocationManager.GPS_PROVIDER, 1_700_000_000_479L), LocationManager.GPS_PROVIDER)
        assertEquals(listOf("provider=gps timestampRule=network time=1700000000479"), ruleLines())
    }

    /** The S22's own walk: a network fix on the whole second (`2026-10-03-walk-t21`, 300 m). */
    @Test
    fun `a network fix on the whole second logs a disagreement with the timestamp rule`() = runTest {
        deliver(location(LocationManager.NETWORK_PROVIDER, 1_700_000_000_000L), LocationManager.NETWORK_PROVIDER)
        assertEquals(listOf("provider=network timestampRule=gps time=1700000000000"), ruleLines())
    }

    @Test
    fun `a GPS fix on the whole second and a network fix with milliseconds log nothing`() = runTest {
        deliver(location(LocationManager.GPS_PROVIDER, 1_700_000_000_000L), LocationManager.GPS_PROVIDER)
        assertEquals(emptyList<String>(), ruleLines())
        // Control on the same tag: the instrument line is there, so the fix was delivered and logged at all.
        assertEquals(1, org.robolectric.shadows.ShadowLog.getLogsForTag(AndroidLocationTracker.FIX_LOG_TAG).size)
    }

    @Test
    fun `a network fix with milliseconds logs nothing`() = runTest {
        deliver(location(LocationManager.NETWORK_PROVIDER, 1_700_000_000_123L), LocationManager.NETWORK_PROVIDER)
        assertEquals(emptyList<String>(), ruleLines())
        assertEquals(1, org.robolectric.shadows.ShadowLog.getLogsForTag(AndroidLocationTracker.FIX_LOG_TAG).size)
    }
}
