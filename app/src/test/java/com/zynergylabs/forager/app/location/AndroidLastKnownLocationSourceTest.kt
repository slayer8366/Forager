package com.zynergylabs.forager.app.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * Dispatch 2026-09-28-510, the report's item 4: the platform's own last known location, through the
 * real [LocationManager] API as Robolectric's shadow implements it, no Play services.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidLastKnownLocationSourceTest {

    private lateinit var context: Application
    private lateinit var shadowLocationManager: ShadowLocationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
    }

    private fun location(provider: String, lat: Double, lng: Double, time: Long, accuracy: Float, altitude: Double? = null) =
        Location(provider).apply {
            latitude = lat
            longitude = lng
            this.time = time
            this.accuracy = accuracy
            altitude?.let { this.altitude = it }
        }

    @Test
    fun `the newest of the providers' last known locations, mapped field for field`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        // GPS an hour older than the network's. setLastKnownLocation is deprecated in Robolectric 4.16 in favour of
        // simulateLocation, which also delivers to listeners; this seeds what the provider holds and nothing else.
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location(LocationManager.GPS_PROVIDER, 45.5, -122.6, 1_700_000_000_000L - 3_600_000L, 8f, altitude = 100.0))
        shadowLocationManager.setLastKnownLocation(LocationManager.NETWORK_PROVIDER, location(LocationManager.NETWORK_PROVIDER, 45.51, -122.61, 1_700_000_000_123L, 120f))

        val known = AndroidLastKnownLocationSource(context).lastKnown()

        assertEquals(
            LocationFix.Update(lat = 45.51, lng = -122.61, altitude = null, accuracyMeters = 120f, timestampEpochMillis = 1_700_000_000_123L, provider = FixProvider.NETWORK),
            known,
        )
    }

    @Test
    fun `without location permission there is none, whatever the providers hold`() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowLocationManager.setLastKnownLocation(LocationManager.GPS_PROVIDER, location(LocationManager.GPS_PROVIDER, 45.5, -122.6, 1_700_000_000_000L, 8f))

        assertNull(AndroidLastKnownLocationSource(context).lastKnown())
    }

    @Test
    fun `no provider holding one is none`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        assertNull(AndroidLastKnownLocationSource(context).lastKnown())
    }

    /**
     * Dispatch 2026-09-28-527: the one real path a provider other than GPS or network can arrive by. The
     * passive provider holds whatever any app on the phone last received, here a fused fix; it is carried as
     * unknown, never as one of the two it might have been.
     */
    @Test
    fun `a fused fix held by the passive provider is carried as unknown`() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowLocationManager.setLastKnownLocation(LocationManager.PASSIVE_PROVIDER, location(LocationManager.FUSED_PROVIDER, 45.52, -122.68, 1_700_000_000_000L, 15f))

        val known = AndroidLastKnownLocationSource(context).lastKnown()

        assertEquals(
            LocationFix.Update(lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 15f, timestampEpochMillis = 1_700_000_000_000L, provider = FixProvider.UNKNOWN),
            known,
        )
    }
}
