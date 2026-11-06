package com.zynergylabs.forager.app.location

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.LocationFix
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
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
 * The tracker's half of Battery saver (dispatch 2026-09-28-767): the interval each provider is asked at,
 * read from the platform's own record of the registration, for the switch off, on, and changed while a
 * collection is live; and the permission contract kept with a switch flow that never completes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidLocationTrackerBatterySaverTest {

    private lateinit var context: Application
    private lateinit var shadowLocationManager: ShadowLocationManager
    private val saverOn = MutableStateFlow(false)
    private lateinit var tracker: AndroidLocationTracker

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        tracker = AndroidLocationTracker(context, saverOn)
    }

    /** Every live request's interval, per provider, as the platform holds them. */
    private fun intervals(): Map<String, List<Long>> =
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .associateWith { provider -> shadowLocationManager.getLocationRequests(provider).map { it.intervalMillis } }

    @Test
    fun `with Battery saver off, GPS and network are asked every second`() = runTest {
        val job = launch { tracker.fixes.collect() }
        advanceUntilIdle()
        assertEquals(mapOf("gps" to listOf(1_000L), "network" to listOf(1_000L)), intervals())
        job.cancel()
    }

    @Test
    fun `with Battery saver on, GPS and network are asked every 5 seconds`() = runTest {
        saverOn.value = true
        val job = launch { tracker.fixes.collect() }
        advanceUntilIdle()
        assertEquals(mapOf("gps" to listOf(5_000L), "network" to listOf(5_000L)), intervals())
        job.cancel()
    }

    @Test
    fun `turning Battery saver on and off during a collection re-registers at once, one request per provider`() = runTest {
        val job = launch { tracker.fixes.collect() }
        advanceUntilIdle()
        saverOn.value = true
        advanceUntilIdle()
        assertEquals("on mid-collection", mapOf("gps" to listOf(5_000L), "network" to listOf(5_000L)), intervals())
        saverOn.value = false
        advanceUntilIdle()
        assertEquals("off again", mapOf("gps" to listOf(1_000L), "network" to listOf(1_000L)), intervals())
        job.cancel()
        advanceUntilIdle()
        assertTrue("the collection's end removes its registration", shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }

    /** The tracker's contract, kept with a switch flow that never completes: one PermissionDenied, then the end. */
    @Test
    fun `without permission the collection still ends after one PermissionDenied`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        assertEquals(listOf(LocationFix.PermissionDenied), tracker.fixes.toList())
        assertTrue(shadowLocationManager.requestLocationUpdateListeners.isEmpty())
    }
}
