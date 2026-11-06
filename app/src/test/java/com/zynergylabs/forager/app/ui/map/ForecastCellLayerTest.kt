package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.ColourRamp
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.RampStop
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.style.expressions.Expression
import org.maplibre.geojson.Polygon

/**
 * What the map's colour-field sources receive and how their fills are coloured (map layers L0b, B5 and
 * B6), up to the native boundary `SightingsMapOverlayDataTest` describes: the pure GeoJSON and
 * `Expression` values are built and read here; `SightingsMap` only hands them to MapLibre.
 */
class ForecastCellLayerTest {

    private val week = LocalDate.of(2026, 9, 28)

    private fun cell(lat: Double, lng: Double, chance: Double, applicable: Boolean = true, weatherThrough: LocalDate = LocalDate.of(2026, 9, 26)) =
        ForecastCell(
            group = "chanterelles", week = week, centre = LatLng(lat, lng), chance = chance,
            uncertaintyLow = 0.0, uncertaintyHigh = 1.0, applicable = applicable, drivers = emptyList(),
            weatherThrough = weatherThrough, modelVersion = "synthetic-1",
        )

    @Test
    fun `only applicable cells become features, each a 0_1-degree square around its centre carrying its chance`() {
        val collection = forecastCellsFeatureCollection(listOf(cell(45.1, -122.3, 0.3), cell(45.2, -122.3, 0.9, applicable = false)))

        val features = collection.features().orEmpty()
        assertEquals("a cell that is not applicable draws nothing", 1, features.size)
        val ring = (features.single().geometry() as Polygon).coordinates().single().map { it.longitude() to it.latitude() }
        val expected = listOf(-122.35 to 45.05, -122.25 to 45.05, -122.25 to 45.15, -122.35 to 45.15, -122.35 to 45.05)
        assertEquals(expected.size, ring.size)
        ring.zip(expected).forEach { (actual, want) ->
            assertEquals(want.first, actual.first, 1e-9)
            assertEquals(want.second, actual.second, 1e-9)
        }
        assertEquals(0.3, features.single().getNumberProperty("chance").toDouble(), 1e-9)
    }

    @Test
    fun `the fill colour interpolates the ramp linearly on each cell's chance`() {
        val ramp = ColourRamp(listOf(RampStop(0f, 0xFFFFD54F.toInt()), RampStop(1f, 0xFF8D2B00.toInt())))

        assertEquals(
            Expression.interpolate(
                Expression.linear(),
                Expression.get("chance"),
                Expression.stop(0f, Expression.color(0xFFFFD54F.toInt())),
                Expression.stop(1f, Expression.color(0xFF8D2B00.toInt())),
            ),
            colourFieldFillColour(ramp),
        )
    }

    @Test
    fun `a view asks for the blocks its cells can reach`() {
        // Cells reach 0.05 degree past their centres, so a view whose south edge is 45.02 still shows
        // the cells centred on 45.0 (block 45) but none centred on 44.9 (block 44).
        assertEquals(setOf(ForecastBlock(45, -123)), forecastBlocksToRequest(zoom = 10.0, south = 45.02, west = -122.98, north = 45.5, east = -122.3))
        // A north edge of 45.96 shows the cells centred on 46.0, which are in block 46.
        assertEquals(
            setOf(ForecastBlock(45, -123), ForecastBlock(46, -123)),
            forecastBlocksToRequest(zoom = 10.0, south = 45.02, west = -122.98, north = 45.96, east = -122.3),
        )
    }

    /**
     * The planner's ruling on Q9 (message 3): below a minimum zoom the colour fields request nothing
     * and draw nothing, and the zoom is chosen so the count stays bounded. At [MIN_FORECAST_ZOOM] a
     * 1280 by 800 dp map (a tablet's whole window, larger than any map pane this app lays out) sees at
     * most 360 / (512 * 2^7) degrees per dp: 7.03 by 4.39 degrees at the equator, where a Mercator view
     * spans the most latitude. [MAX_FORECAST_BLOCKS] stays as a backstop for a tilted camera, whose
     * visible area zoom alone does not bound.
     */
    @Test
    fun `below the minimum zoom nothing is requested, and at it even a tablet-sized view stays under the backstop`() {
        val view = { zoom: Double -> forecastBlocksToRequest(zoom = zoom, south = 45.02, west = -122.98, north = 45.5, east = -122.3) }
        assertNull(view(MIN_FORECAST_ZOOM - 0.01))
        // Data part C: the same edge decides the legend's "Zoom in to see the forecast".
        assertTrue(isBelowForecastZoom(MIN_FORECAST_ZOOM - 0.01))
        assertFalse(isBelowForecastZoom(MIN_FORECAST_ZOOM))
        assertEquals(setOf(ForecastBlock(45, -123)), view(MIN_FORECAST_ZOOM))

        val degreesPerDp = 360.0 / (512 * Math.pow(2.0, MIN_FORECAST_ZOOM))
        val worstCase = forecastBlocksToRequest(
            zoom = MIN_FORECAST_ZOOM,
            // Just inside a block edge, so the half cell past each side reaches one more block.
            south = 0.95,
            west = 0.95,
            north = 0.95 + 800 * degreesPerDp,
            east = 0.95 + 1280 * degreesPerDp,
        )
        assertEquals("a 1280 by 800 dp map at zoom 7, placed to touch the most blocks: 6 by 9", 54, worstCase?.size)
        assertNull(
            "a tilted camera's far larger view is still refused by the backstop",
            forecastBlocksToRequest(zoom = MIN_FORECAST_ZOOM, south = 40.0, west = -125.0, north = 50.0, east = -115.0),
        )
    }

    @Test
    fun `the dates in view are the cells' week and the earliest weather date among them`() {
        val shown = forecastCellsShownOf(
            listOf(
                cell(45.1, -122.3, 0.3, weatherThrough = LocalDate.of(2026, 9, 26)),
                cell(45.2, -122.3, 0.4, weatherThrough = LocalDate.of(2026, 9, 25)),
                cell(45.3, -122.3, 0.5, applicable = false, weatherThrough = LocalDate.of(2026, 9, 20)),
            ),
        )

        assertEquals("a cell that draws nothing does not date the legend", ForecastCellsShown(week, LocalDate.of(2026, 9, 25)), shown)
        assertNull(forecastCellsShownOf(emptyList()))
    }
}
