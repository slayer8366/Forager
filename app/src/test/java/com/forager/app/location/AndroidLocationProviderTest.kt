package com.forager.app.location

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.LocationResult
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * [AndroidLocationProvider] against Robolectric's real [LocationManager] shadow — not a fake, the
 * same "verify what the platform API actually does" discipline the rest of `location/`/`sensor/`/
 * `map/` uses.
 *
 * The one behavior worth pinning down headlessly: [AndroidLocationProvider.getCurrentLocation]
 * races every enabled provider rather than requesting GPS alone and waiting on it. A device with
 * GPS enabled but slow to lock (routine on a cold/indoor fix) must not block a network fix that's
 * already available — see the class doc comment on [AndroidLocationProvider]'s private
 * `awaitFirstLocation` for the real-hardware symptom this fixes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AndroidLocationProviderTest {

    private lateinit var context: Application
    private lateinit var locationManager: LocationManager
    private lateinit var shadowLocationManager: ShadowLocationManager
    private lateinit var provider: AndroidLocationProvider

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        shadowLocationManager = shadowOf(locationManager)
        provider = AndroidLocationProvider(context)
    }

    @Test
    fun `no location permission returns PermissionDenied without touching LocationManager`() = runTest {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

        val result = provider.getCurrentLocation()

        assertEquals(LocationResult.PermissionDenied, result)
    }

    @Test
    fun `no provider enabled returns LocationUnavailable`() = runTest {
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        val result = provider.getCurrentLocation()

        assertEquals(LocationResult.LocationUnavailable, result)
    }

    @Test
    fun `a network fix wins even though GPS is enabled and never resolves`() = runTest {
        shadowLocationManager.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        // getCurrentLocation() suspends until a fix arrives, so it has to run concurrently with the
        // simulateLocation() call below rather than sequentially before it.
        val pending = async { provider.getCurrentLocation() }
        yield() // let the coroutine above run far enough to register both requestSingleUpdate calls

        assertEquals(
            "expected both GPS and network to have a pending single-update request registered — " +
                "proves this doesn't pick GPS alone and stop there",
            2,
            shadowLocationManager.requestLocationUpdateListeners.size,
        )

        // Only the network provider ever actually produces a location — standing in for GPS's
        // real-world slow cold-lock never resolving before the fix that matters (network) does.
        shadowLocationManager.simulateLocation(fixAt(lat = 45.5, lng = -122.6))
        shadowOf(Looper.getMainLooper()).idle() // dispatches the queued onLocationChanged callback

        assertEquals(LocationResult.Success(lat = 45.5, lng = -122.6, altitude = null), pending.await())
    }

    @Test
    fun `altitude is carried through when the fix reports one, null when it doesn't`() = runTest {
        shadowLocationManager.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)

        val pending = async { provider.getCurrentLocation() }
        yield()
        shadowLocationManager.simulateLocation(fixAt(lat = 1.0, lng = 2.0, altitude = 305.0))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(LocationResult.Success(lat = 1.0, lng = 2.0, altitude = 305.0), pending.await())
    }

    private fun fixAt(lat: Double, lng: Double, altitude: Double? = null) = Location(LocationManager.NETWORK_PROVIDER).apply {
        latitude = lat
        longitude = lng
        if (altitude != null) this.altitude = altitude
    }
}
