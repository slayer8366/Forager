package com.zynergylabs.forager.app.ui.map

import android.hardware.SensorManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.android.location.CompassListener

/**
 * Dispatch 2026-09-28-430 (plan task T22): the pure halves of the navigation view. What "stuck"
 * means and how long it is retried ([NavigationFacingJudge]), and what MapLibre is handed as the
 * compass ([TrueHeadingCompassEngine]). The camera calls themselves are device-only: a real
 * MapView cannot run under Robolectric.
 */
class NavigationViewTest {

    private val t = 1_700_000_000_000L

    @Test
    fun `a stuck compass is retried for 15 s, then north-up, and facing-up again the moment it recovers`() {
        val judge = NavigationFacingJudge()
        assertEquals(NavigationFacing.FACING_UP, judge.next(TrueHeadingReading.Available(90f), t))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 1_000L))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 15_999L))
        assertEquals("15 s after it became unreliable", NavigationFacing.NORTH_UP, judge.next(TrueHeadingReading.Unreliable, t + 16_000L))
        assertEquals(NavigationFacing.NORTH_UP, judge.next(TrueHeadingReading.Unreliable, t + 60_000L))
        assertEquals("facing-up comes back by itself", NavigationFacing.FACING_UP, judge.next(TrueHeadingReading.Available(95f), t + 61_000L))
        // A second stuck stretch gets its own full window, not what was left of the first.
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 70_000L))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 84_999L))
        assertEquals(NavigationFacing.NORTH_UP, judge.next(TrueHeadingReading.Unreliable, t + 85_000L))
    }

    @Test
    fun `recovering inside the window clears it, so a later stuck stretch starts a new 15 s`() {
        val judge = NavigationFacingJudge()
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t))
        assertEquals(NavigationFacing.FACING_UP, judge.next(TrueHeadingReading.Available(10f), t + 10_000L))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 12_000L))
        assertEquals("only 14 s into the second stretch", NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 26_000L))
    }

    @Test
    fun `no sensor is north-up at once, with no retry window, and no fix yet is not a compass fault`() {
        assertEquals(NavigationFacing.NORTH_UP, NavigationFacingJudge().next(TrueHeadingReading.NoSensor, t))
        val judge = NavigationFacingJudge()
        assertEquals(NavigationFacing.FACING_UP, judge.next(TrueHeadingReading.NeedsFix, t))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 1_000L))
        assertEquals("losing the fix ends the stuck stretch", NavigationFacing.FACING_UP, judge.next(TrueHeadingReading.NeedsFix, t + 2_000L))
        assertEquals(NavigationFacing.CALIBRATING, judge.next(TrueHeadingReading.Unreliable, t + 20_000L))
    }

    @Test
    fun `the compass engine hands MapLibre the app's true heading, and nothing while it is not available`() {
        val engine = TrueHeadingCompassEngine()
        val headings = mutableListOf<Float>()
        val statuses = mutableListOf<Int>()
        engine.addCompassListener(object : CompassListener {
            override fun onCompassChanged(userHeading: Float) { headings += userHeading }
            override fun onCompassAccuracyChange(compassStatus: Int) { statuses += compassStatus }
        })

        engine.update(TrueHeadingReading.Available(95f))
        assertEquals(listOf(95f), headings)
        assertEquals(95f, engine.lastHeading, 0f)
        assertEquals(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, engine.lastAccuracySensorStatus)

        engine.update(TrueHeadingReading.Unreliable)
        engine.update(TrueHeadingReading.NeedsFix)
        engine.update(TrueHeadingReading.NoSensor)
        assertEquals("no heading is handed over while it cannot be trusted", listOf(95f), headings)
        assertEquals("the last heading stands", 95f, engine.lastHeading, 0f)
        assertEquals(SensorManager.SENSOR_STATUS_UNRELIABLE, engine.lastAccuracySensorStatus)
        assertEquals(listOf(SensorManager.SENSOR_STATUS_ACCURACY_HIGH, SensorManager.SENSOR_STATUS_UNRELIABLE), statuses)

        engine.update(TrueHeadingReading.Available(100f))
        assertEquals(listOf(95f, 100f), headings)
    }

    /**
     * Dispatch 2026-09-28-440, Amendment 1 (the owner: "Zoom in past the cap"): while navigating the
     * camera may reach the start zoom on every basemap; otherwise each keeps its own operating limit.
     * The tile sources' own limit is not this function's: `BasemapStyleTest`'s "the style's declared
     * maxzoom matches Basemap's own operating limit" holds it, and the style takes no navigation input.
     */
    @Test
    fun `while navigating the camera may reach the start zoom on every basemap, and not otherwise`() {
        assertEquals(18.0, navigationMaxZoom(Basemap.OPEN_TOPO_MAP, navigating = true), 0.0)
        assertEquals("a higher cap is not lowered", 19.0, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = true), 0.0)
        assertEquals(17.0, navigationMaxZoom(Basemap.OPEN_TOPO_MAP, navigating = false), 0.0)
        assertEquals(19.0, navigationMaxZoom(Basemap.OSM_STANDARD, navigating = false), 0.0)
    }

    /**
     * Dispatch 2026-09-28-457, Part A, the sequence the S22 logged at Return: the view asked for with
     * no start zoom (the mode transition starts), then again with 18 while it runs, then again with
     * none. Before the gate the second request re-sent the mode, MapLibre called it finished at once,
     * and the zoom was issued mid-transition and refused. Now the second and third wait, and the
     * transition actually in flight hands out the zoom when it finishes, once.
     */
    @Test
    fun `the start zoom waits for the transition in flight and lands when it finishes`() {
        val gate = StartZoomGate()
        val first = gate.request(sameModeAsInFlight = false, startZoom = null)!!
        assertEquals("the second request waits", null, gate.request(sameModeAsInFlight = true, startZoom = 18.0))
        assertEquals("so does the third", null, gate.request(sameModeAsInFlight = true, startZoom = null))
        assertEquals("the transition in flight hands it out", 18.0, gate.finished(first))
        assertEquals("once", null, gate.finished(first))
    }

    @Test
    fun `a superseded transition hands out nothing, and the newer one gets the zoom`() {
        val gate = StartZoomGate()
        val first = gate.request(sameModeAsInFlight = false, startZoom = 18.0)!!
        // A facing change mid-transition asks for another mode.
        val second = gate.request(sameModeAsInFlight = false, startZoom = null)!!
        assertEquals(null, gate.finished(first))
        assertEquals(18.0, gate.finished(second))
    }

    @Test
    fun `a cancelled transition keeps the zoom for the next one, and Stop drops it`() {
        val gate = StartZoomGate()
        val first = gate.request(sameModeAsInFlight = false, startZoom = 18.0)!!
        gate.cancelled(first)
        val again = gate.request(sameModeAsInFlight = true, startZoom = null)!!
        assertEquals(18.0, gate.finished(again))

        val third = gate.request(sameModeAsInFlight = false, startZoom = 18.0)!!
        gate.reset()
        assertEquals(null, gate.finished(third))
    }

    @Test
    fun `the walker sits below the centre by an eighth of the map's height`() {
        // Top padding of a quarter: the map centres in the lower three quarters, so the walker is an eighth below the middle.
        assertEquals(500.0, navigationViewTopPaddingPx(2_000), 0.0)
    }
}
