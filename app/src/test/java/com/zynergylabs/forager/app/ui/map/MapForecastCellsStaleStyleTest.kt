package com.zynergylabs.forager.app.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.StyleLifecycleFixture
import org.maplibre.android.style.sources.GeoJsonSource
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Dispatch 2026-10-25-01, the S22's finding F1: `IllegalStateException: Calling getSourceAs when a newer style is
 * loading/has loaded` at `MapForecastCells.kt:58`, 4 times in 17 style changes. `MapLibreMap.setStyle` clears the old
 * style at once. `SightingsMapState.loadedStyle` keeps pointing at that style until the new style's callback. A run of
 * the cells effect in that gap read the source from the cleared style.
 *
 * What this can prove: given a style in the cleared state, the cells push logs and stops instead of throwing. The
 * style is a real MapLibre [org.maplibre.android.maps.Style], cleared by MapLibre's own `clear()`. What it cannot
 * prove: that the effect runs in that gap on a phone, or how often. A real MapView cannot be built under
 * Robolectric, so the gap between `setStyle` and its callback is device-only. The S22 check covers that.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapForecastCellsStaleStyleTest {

    @Before
    fun clearLog() {
        ShadowLog.clear()
    }

    private fun mapLines() = ShadowLog.getLogsForTag(SIGHTINGS_MAP_TAG).map { it.msg }

    @Test
    fun `the fixture's replaced style throws the S22's exception, so the case below is the one that crashed`() {
        val replaced = StyleLifecycleFixture.replacedStyle()
        val thrown = runCatching { replaced.getSourceAs<GeoJsonSource>("forecast-cells-test") }.exceptionOrNull()
        assertTrue("expected IllegalStateException, got $thrown", thrown is IllegalStateException)
        assertEquals("Calling getSourceAs when a newer style is loading/has loaded.", thrown!!.message)
    }

    @Test
    fun `a style replaced while the cells are pushed stops the push, logged, instead of crashing`() {
        val replaced = StyleLifecycleFixture.replacedStyle()

        val goOn = pushForecastCells(replaced, "forecast-cells-test", emptyList())

        assertFalse("the effect must stop on a replaced style", goOn)
        assertEquals(
            listOf(
                "The forecast-cells-test source was not read: a newer style is loading. The cells are drawn again " +
                    "when it finishes.",
            ),
            mapLines(),
        )
    }

    @Test
    fun `a loaded style without the source still logs the missing source and goes on, as before`() {
        val loaded = StyleLifecycleFixture.loadedStyle()

        val goOn = pushForecastCells(loaded, "forecast-cells-test", emptyList())

        assertTrue(goOn)
        assertEquals(
            listOf("The forecast-cells-test source is not in the loaded style; its cells were not drawn."),
            mapLines(),
        )
    }
}
