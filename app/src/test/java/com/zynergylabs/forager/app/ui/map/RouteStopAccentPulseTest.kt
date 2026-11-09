package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 7 (RECORD -2187): the selected stop's highlight fades in and pulses once. Driven through
 * [rememberRouteStopAccentFrame], the composable the map reads the selected stop through (MapRouteStopAccent.kt), on the paused
 * frame clock, a frame every 16 ms; each frame is read as the accent source receives it ([routeStopAccentFeatures]), so the claim is
 * on what MapLibre is given: the feature's opacity and size properties, and its image.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RouteStopAccentPulseTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val creek = SelectedStopMarker(LatLng(45.20, -122.50), trip = false, featureId = "wp-creek")
    private val ridge = SelectedStopMarker(LatLng(45.23, -122.53), trip = false, featureId = "wp-ridge")
    private val saddle = SelectedStopMarker(LatLng(45.25, -122.55), trip = true, featureId = "trip-saddle")

    private val selected = mutableStateOf<SelectedStopMarker?>(null)
    private var latest = RouteStopAccentFrame.NONE

    private fun show(reduceMotion: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
                val frame = rememberRouteStopAccentFrame(selected.value)
                SideEffect { latest = frame }
            }
        }
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
    }

    private class Drawn(val featureId: String, val image: String, val opacity: Float, val scale: Float)

    /** What the accent source is given now, one entry per highlight, in draw order. */
    private fun drawn(): List<Drawn> = routeStopAccentFeatures(latest).features().orEmpty().map {
        Drawn(
            it.getStringProperty(FAN_ACCENT_KEY_PROPERTY).substringAfter('/'),
            it.getStringProperty(PlottedRouteIds.SELECTED_ICON_PROPERTY),
            it.getNumberProperty(PlottedRouteIds.SELECTED_OPACITY_PROPERTY).toFloat(),
            it.getNumberProperty(PlottedRouteIds.SELECTED_SCALE_PROPERTY).toFloat(),
        )
    }

    /** Selects [marker], then records what is drawn at each of the next [frames] frames. */
    private fun selectAndRecord(marker: SelectedStopMarker?, frames: Int = 45): List<List<Drawn>> {
        composeRule.runOnUiThread { selected.value = marker }
        return (1..frames).map {
            composeRule.mainClock.advanceTimeBy(FRAME_MS)
            drawn()
        }
    }

    private fun List<List<Drawn>>.of(id: String): List<Drawn?> = map { frame -> frame.firstOrNull { it.featureId == id } }

    @Test
    fun `a selected pin fades in, swells about 15 percent once, and settles at the accent`() {
        show(reduceMotion = false)
        val creekFrames = selectAndRecord(creek).of("wp-creek")

        val firstDrawn = creekFrames.first { it != null }!!
        assertTrue("the highlight starts faint, not at full (it was ${firstDrawn.opacity})", firstDrawn.opacity < 0.5f)
        val opacities = creekFrames.filterNotNull().map { it.opacity }
        assertTrue("the opacity never falls while it arrives: $opacities", opacities.zipWithNext().all { (a, b) -> b >= a })
        val scales = creekFrames.filterNotNull().map { it.scale }
        val peak = scales.max()
        assertTrue("the pin swells about 15% (peak $peak)", peak in 1.12f..1.151f)
        val peakAt = scales.indexOf(peak)
        assertTrue("it is not swelling at its first frame", scales.first() == 1f)
        assertTrue("it grows up to the peak, then settles back: $scales", scales.take(peakAt + 1).zipWithNext().all { (a, b) -> b >= a } && scales.drop(peakAt).zipWithNext().all { (a, b) -> b <= a })
        assertTrue("the swell comes as the fade arrives: at the peak the opacity is ${opacities[peakAt]}", opacities[peakAt] >= 0.9f)
        val settled = creekFrames.last()!!
        assertEquals(1f, settled.opacity)
        assertEquals(1f, settled.scale)
        assertEquals("settled on the accent pin", MarkerIcon.WAYPOINT_ROUTE_SELECTED.imageId, settled.image)
        val arrivedAt = creekFrames.indexOfFirst { it != null }
        val settledAt = creekFrames.indices.first { it > arrivedAt + peakAt && creekFrames[it]!!.scale == 1f }
        assertTrue("the motion is over in about half a second (${(settledAt - arrivedAt) * FRAME_MS} ms)", (settledAt - arrivedAt) * FRAME_MS in 400..560)
    }

    @Test
    fun `a selected trip flag fades in and pulses the same, on the accent flag`() {
        show(reduceMotion = false)
        val flag = selectAndRecord(saddle).of("trip-saddle").filterNotNull()

        assertTrue("faint at first", flag.first().opacity < 0.5f)
        assertTrue("it swells", flag.maxOf { it.scale } > 1.12f)
        assertEquals(MarkerIcon.PLANNED_TRIP_ROUTE_SELECTED.imageId, flag.last().image)
        assertEquals(1f, flag.last().opacity)
        assertEquals(1f, flag.last().scale)
    }

    @Test
    fun `another stop picked, the old highlight fades out while the new one fades in and pulses`() {
        show(reduceMotion = false)
        selectAndRecord(creek)
        val frames = selectAndRecord(ridge)
        val old = frames.of("wp-creek")
        val new = frames.of("wp-ridge")

        val leaving = old.takeWhile { it != null }.map { it!!.opacity }
        assertTrue("the old highlight is still drawn, fading, for a few frames: $leaving", leaving.size >= 3)
        assertTrue("it fades, never rises: $leaving", leaving.zipWithNext().all { (a, b) -> b <= a } && leaving.last() < leaving.first())
        assertTrue("and is gone once faded", old.last() == null)
        assertEquals("the old one is drawn under the new while both are", listOf("wp-creek", "wp-ridge"), frames.first { it.size == 2 }.map { it.featureId })
        assertTrue("the new one starts faint", new.filterNotNull().first().opacity < 0.5f)
        assertTrue("the new one pulses", new.filterNotNull().maxOf { it.scale } > 1.12f)
        assertEquals(1f, new.last()!!.opacity)
    }

    @Test
    fun `under Reduce Motion the highlight is there at once, with no pulse`() {
        show(reduceMotion = true)
        val creekFrames = selectAndRecord(creek).of("wp-creek").filterNotNull()

        assertTrue("drawn within two frames of the selection", creekFrames.size >= 43)
        assertEquals("full from its first frame", 1f, creekFrames.first().opacity)
        assertTrue("never swells: ${creekFrames.map { it.scale }}", creekFrames.all { it.scale == 1f })
        val switched = selectAndRecord(ridge)
        val ridgeFrom = switched.indexOfFirst { frame -> frame.any { it.featureId == "wp-ridge" } }
        assertTrue("the new one is drawn within two frames", ridgeFrom in 0..1)
        assertTrue("the old highlight goes at once, in the frame the new one comes", switched.drop(ridgeFrom).all { frame -> frame.map { it.featureId } == listOf("wp-ridge") })
        assertTrue("the new one is full and its size at once", switched.drop(ridgeFrom).all { it.single().opacity == 1f && it.single().scale == 1f })
    }

    @Test
    fun `the highlight goes at once with its layer, not fading over nothing`() {
        show(reduceMotion = false)
        selectAndRecord(creek, frames = 10) // still pulsing
        val frames = selectAndRecord(null, frames = 4)

        val goneFrom = frames.indexOfFirst { it.isEmpty() }
        assertTrue("gone within two frames", goneFrom in 0..1)
        assertTrue("and nothing is drawn after, no fade-out over a pin that is not there", frames.drop(goneFrom).all { it.isEmpty() })
        assertNull(latest.selectedKey)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
