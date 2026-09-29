package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.TRACK_WIDTH_ZOOM_STOPS
import com.zynergylabs.forager.app.ui.map.layers.ZoomWidthStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.maplibre.android.style.expressions.Expression

/**
 * Track widths by zoom. Stops: owner "2 A" (2026-09-29, `docs/plans/journal-redesign.md`, "Tracks by
 * zoom, revised"), replacing the 2026-09-28-34 proposal: 100% at zoom 18 and above, about 67% at 16,
 * about 42% at 14, 25% at 12 and below, linear between. The expected numbers below are that ruling's
 * arithmetic on today's widths (a 6 dp line, a 9 dp casing), written out, not read back from the stop
 * data.
 */
class TrackWidthByZoomTest {

    private val specs = trackLayerSpecs().associateBy { it.layerId }
    private val breadcrumb = specs.getValue(MapLayerIds.BREADCRUMB)
    private val breadcrumbCasing = specs.getValue(MapLayerIds.BREADCRUMB_CASING)
    private val keptTrack = specs.getValue(MapLayerIds.KEPT_TRACKS)
    private val keptTrackCasing = specs.getValue(MapLayerIds.KEPT_TRACKS_CASING)

    @Test
    fun `the stops are 25 percent at zoom 12, 42 at 14, 67 at 16 and the full width at 18`() {
        assertEquals(
            listOf(ZoomWidthStop(12f, 0.25f), ZoomWidthStop(14f, 0.42f), ZoomWidthStop(16f, 0.67f), ZoomWidthStop(18f, 1f)),
            TRACK_WIDTH_ZOOM_STOPS,
        )
    }

    @Test
    fun `each track line is 1,5 2,52 4,02 and 6 dp at zoom 12 14 16 and 18, and each casing 2,25 3,78 6,03 and 9 dp`() {
        for ((name, spec, widths) in listOf(
            Row("breadcrumb", breadcrumb, listOf(1.5f, 2.52f, 4.02f, 6f)),
            Row("breadcrumb casing", breadcrumbCasing, listOf(2.25f, 3.78f, 6.03f, 9f)),
            Row("kept track", keptTrack, listOf(1.5f, 2.52f, 4.02f, 6f)),
            Row("kept-track casing", keptTrackCasing, listOf(2.25f, 3.78f, 6.03f, 9f)),
        )) {
            val stops = lineWidthStops(spec)
            assertNotNull("$name has zoom stops", stops)
            assertEquals("$name has four stops", 4, stops!!.size)
            listOf(12f, 14f, 16f, 18f).forEachIndexed { i, zoom ->
                assertEquals("$name stop $i's zoom", zoom, stops[i].first, 0f)
                assertEquals("$name width at zoom $zoom", widths[i], stops[i].second, 1e-4f)
            }
        }
    }

    @Test
    fun `below zoom 12 and above zoom 18 the width holds, and between the stops it is linear`() {
        for ((name, spec, widths) in listOf(
            Row("breadcrumb", breadcrumb, listOf(1.5f, 2.52f, 4.02f, 6f)),
            Row("kept-track casing", keptTrackCasing, listOf(2.25f, 3.78f, 6.03f, 9f)),
        )) {
            assertEquals("$name at zoom 5", widths[0], lineWidthAtZoom(spec, 5f), 1e-4f)
            assertEquals("$name at zoom 12", widths[0], lineWidthAtZoom(spec, 12f), 1e-4f)
            assertEquals("$name at zoom 13, halfway 12 to 14", (widths[0] + widths[1]) / 2, lineWidthAtZoom(spec, 13f), 1e-4f)
            assertEquals("$name at zoom 15, halfway 14 to 16", (widths[1] + widths[2]) / 2, lineWidthAtZoom(spec, 15f), 1e-4f)
            assertEquals("$name at zoom 17, halfway 16 to 18", (widths[2] + widths[3]) / 2, lineWidthAtZoom(spec, 17f), 1e-4f)
            assertEquals("$name at zoom 18", widths[3], lineWidthAtZoom(spec, 18f), 1e-4f)
            assertEquals("$name at zoom 22", widths[3], lineWidthAtZoom(spec, 22f), 1e-4f)
        }
    }

    @Test
    fun `each casing keeps today's 1,5 ratio to its line at every zoom`() {
        for ((line, casing) in listOf(breadcrumb to breadcrumbCasing, keptTrack to keptTrackCasing)) {
            var zoom = 8f
            while (zoom <= 18f) {
                assertEquals("${casing.layerId} over ${line.layerId} at zoom $zoom", 1.5f, lineWidthAtZoom(casing, zoom) / lineWidthAtZoom(line, zoom), 1e-4f)
                zoom += 0.5f
            }
        }
        assertNotEquals("the widths do change with zoom", lineWidthAtZoom(keptTrack, 12f), lineWidthAtZoom(keptTrack, 18f))
    }

    @Test
    fun `a track's line-width is a linear zoom interpolation from its stops`() {
        val expected = Expression.interpolate(
            Expression.linear(),
            Expression.zoom(),
            Expression.stop(12f, 1.5f),
            Expression.stop(14f, 2.52f),
            Expression.stop(16f, 4.02f),
            Expression.stop(18f, 6f),
        )
        assertEquals(expected, lineWidthExpression(keptTrack))
        assertEquals(expected, lineWidthExpression(breadcrumb))
    }

    @Test
    fun `the offline outline stays a constant 1,5 dp while the tracks thin out`() {
        val outline = offlineRegionOutlineSpec()
        assertNotNull("the kept track thins out", lineWidthStops(keptTrack))
        assertNull("the offline outline has no stops", lineWidthStops(outline))
        assertEquals(Expression.literal(1.5f), lineWidthExpression(outline))
        assertEquals(1.5f, lineWidthAtZoom(outline, 12f), 0f)
        assertEquals(1.5f, lineWidthAtZoom(outline, 18f), 0f)
    }

    private data class Row(val name: String, val spec: LineLayerSpec, val widths: List<Float>)
}
