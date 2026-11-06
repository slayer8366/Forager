package com.zynergylabs.forager.app.ui.map

import android.location.Location
import android.os.Looper
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.location.engine.LocationEngineCallback
import org.maplibre.android.location.engine.LocationEngineRequest
import org.maplibre.android.location.engine.LocationEngineResult
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-510 (the owner: "App feeds the dot"): what MapLibre's location component is handed
 * by the app's own engine. The component itself, and the camera following what it is handed, are
 * native and device-only; this is everything up to that boundary.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AppLocationEngineTest {

    private val approximate = LocationFix.Update(lat = 45.51, lng = -122.61, altitude = 210.0, accuracyMeters = 120f, timestampEpochMillis = 1_700_000_000_123L, provider = FixProvider.NETWORK)

    private class Recording : LocationEngineCallback<LocationEngineResult> {
        val delivered = mutableListOf<Location>()
        val failures = mutableListOf<Exception>()
        override fun onSuccess(result: LocationEngineResult) {
            delivered += result.lastLocation!!
        }
        override fun onFailure(exception: Exception) {
            failures += exception
        }
    }

    private fun AppLocationEngine.listen(): Recording =
        Recording().also { requestLocationUpdates(LocationEngineRequest.Builder(1_000L).build(), it, Looper.getMainLooper()) }

    @Test
    fun `an approximate reading reaches MapLibre as the platform location, field for field, accuracy included`() {
        val engine = AppLocationEngine()
        val heard = engine.listen()

        engine.update(approximate)

        val location = heard.delivered.single()
        assertEquals(45.51, location.latitude, 0.0)
        assertEquals(-122.61, location.longitude, 0.0)
        assertTrue(location.hasAccuracy())
        assertEquals("the circle MapLibre draws is this radius", 120f, location.accuracy, 0f)
        assertEquals(1_700_000_000_123L, location.time)
        assertTrue(location.hasAltitude())
        assertEquals(210.0, location.altitude, 0.0)
    }

    @Test
    fun `a fix without accuracy or altitude carries none, never a zero`() {
        val engine = AppLocationEngine()
        val heard = engine.listen()

        engine.update(LocationFix.Update(45.5, -122.6, null, null, 1_700_000_000_000L, provider = FixProvider.GPS))

        val location = heard.delivered.single()
        assertFalse(location.hasAccuracy())
        assertFalse(location.hasAltitude())
    }

    @Test
    fun `the same fix again is not delivered again, so MapLibre's own stale clock keeps running`() {
        val engine = AppLocationEngine()
        val heard = engine.listen()

        engine.update(approximate)
        engine.update(approximate.copy())
        engine.update(null)

        assertEquals(1, heard.delivered.size)
    }

    @Test
    fun `asked for the last location, it answers the current fix, and fails, saying why, before there is one`() {
        val engine = AppLocationEngine()
        val before = Recording()
        engine.getLastLocation(before)
        assertTrue(before.delivered.isEmpty())
        assertEquals(1, before.failures.size)

        engine.update(approximate)
        val after = Recording()
        engine.getLastLocation(after)

        assertEquals(1_700_000_000_123L, after.delivered.single().time)
    }

    @Test
    fun `a removed callback hears nothing more`() {
        val engine = AppLocationEngine()
        val heard = engine.listen()
        engine.removeLocationUpdates(heard)

        engine.update(approximate)

        assertTrue(heard.delivered.isEmpty())
    }

    @Test
    fun `the PendingIntent requests, which MapLibre's component never makes, are refused as unsupported`() {
        val engine = AppLocationEngine()

        assertThrows(UnsupportedOperationException::class.java) { engine.requestLocationUpdates(LocationEngineRequest.Builder(1_000L).build(), null as android.app.PendingIntent?) }
        assertThrows(UnsupportedOperationException::class.java) { engine.removeLocationUpdates(null as android.app.PendingIntent?) }
    }
}
