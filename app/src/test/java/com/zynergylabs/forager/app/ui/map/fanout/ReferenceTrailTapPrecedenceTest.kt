package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.withReferenceTrailsHiddenUnlessLoaded
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.TapGroup
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import com.zynergylabs.forager.app.ui.map.layers.referenceAgencyOf
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Step 8c (dispatch 2026-10-26-16, item 2): a tap on an official-trails band goes through the real [MapTapHandler] over
 * the real registry's draw order, and the band takes it only where no marker and no app line does: the bands are lines
 * drawn below every app line (8b's registry entries), so tracks, offline-area outlines and markers take the tap first,
 * and a band takes it before a colour field. The map is a stand-in that draws horizontal lines of a given width.
 */
class ReferenceTrailTapPrecedenceTest {

    /** A horizontal line across the map at [yPx], [halfWidthPx] each side, drawn by [layerId]. */
    private data class Line(val layerId: String, val featureId: String?, val yPx: Float, val halfWidthPx: Float, val attributes: Map<String, String> = emptyMap())

    /** Draws [lines] and [markers] (points, [MARKER_HALF_PX] each way); answers the handler's queries as the map would. */
    private class LinesProbe(val lines: List<Line>, val markers: List<Pair<String, Pair<Float, Float>>> = emptyList()) : MapProbe {
        override val density = 1f

        override fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit> = hitsInBox(xPx, yPx, 0f, layerIds)

        override fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit> =
            lines.filter { it.layerId in layerIds && abs(it.yPx - yPx) <= it.halfWidthPx + halfPx }
                .map { TapHit(it.layerId, it.featureId, it.attributes) } +
                markers.filter { (id, at) -> id in layerIds && abs(at.first - xPx) <= MARKER_HALF_PX + halfPx && abs(at.second - yPx) <= MARKER_HALF_PX + halfPx }
                    .map { (id, _) -> TapHit(id, "m-1") }

        override fun markersInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<ProbedMarker> = emptyList()

        override fun markersOf(keys: List<FanKey>): List<ProbedMarker> = emptyList()
    }

    private val threeBlaze = mapOf("trail_no" to "017", "trail_name" to "Three Blaze")
    private val fsBand = Line(MapLayerIds.REFERENCE_FS_BAND, null, BAND_Y, BAND_HALF_PX, threeBlaze)

    private fun tap(probe: MapProbe, xPx: Float = 200f, yPx: Float = BAND_Y, drawn: MapLayersState = MapLayersState.DEFAULT): List<String> {
        val sinks = RecordingSinks()
        MapTapHandler(
            MarkerFanOutState(),
            probe,
            { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
            sinks,
            // As the host passes it (MapListeners.kt): whether the drawn state shows the layer.
            layerDrawn = { id -> MAP_LAYER_REGISTRY.firstOrNull { it.id == id }?.let { layerPaintFor(it, drawn).visible } ?: false },
        ).onMapTap(LatLng(45.3, -115.0), xPx, yPx)
        return sinks.events
    }

    @Test
    fun `on a style without the band, the band takes no tap even where a query would return it`() {
        // 8b's drawn state before a style carrying the band has loaded: the band is off, whatever the switch says.
        val beforeV3 = withReferenceTrailsHiddenUnlessLoaded(MapLayersState.DEFAULT, loaded = false)
        assertEquals(listOf("plain"), tap(LinesProbe(listOf(fsBand)), drawn = beforeV3))
        val blm = Line(MapLayerIds.REFERENCE_BLM_BAND, null, BAND_Y, BAND_HALF_PX)
        assertEquals(listOf("plain"), tap(LinesProbe(listOf(blm)), drawn = beforeV3))
        // Positive control: the same drawn state once such a style has loaded.
        val v3 = withReferenceTrailsHiddenUnlessLoaded(MapLayersState.DEFAULT, loaded = true)
        assertEquals(listOf("reference:${MapLayerIds.REFERENCE_FS_BAND}:Forest Service trail 017 · Three Blaze"), tap(LinesProbe(listOf(fsBand)), drawn = v3))
    }

    @Test
    fun `with the switch off, the band takes no tap, and a track over it still does`() {
        val off = MapLayersState(layers = mapOf(MapLayerIds.REFERENCE_FS_BAND to LayerState(visible = false)))
        assertEquals(listOf("plain"), tap(LinesProbe(listOf(fsBand)), drawn = off))
        val track = Line(MapLayerIds.KEPT_TRACKS, "track-1", BAND_Y, 3f)
        assertEquals(listOf("feature:${MapLayerIds.KEPT_TRACKS}:track-1"), tap(LinesProbe(listOf(fsBand, track)), drawn = off))
    }

    @Test
    fun `a tap on a band alone opens the band's own record`() {
        assertEquals(listOf("reference:${MapLayerIds.REFERENCE_FS_BAND}:Forest Service trail 017 · Three Blaze"), tap(LinesProbe(listOf(fsBand))))
        val blm = Line(MapLayerIds.REFERENCE_BLM_BAND, null, BAND_Y, BAND_HALF_PX, mapOf("ROUTE_PRMRY_NM" to "Badlands Rock"))
        assertEquals(listOf("reference:${MapLayerIds.REFERENCE_BLM_BAND}:BLM trail · Badlands Rock"), tap(LinesProbe(listOf(blm))))
    }

    @Test
    fun `a kept track over a band takes the tap`() {
        val track = Line(MapLayerIds.KEPT_TRACKS, "track-1", BAND_Y, 3f)
        assertEquals(listOf("feature:${MapLayerIds.KEPT_TRACKS}:track-1"), tap(LinesProbe(listOf(fsBand, track))))
    }

    @Test
    fun `every tappable app line takes the tap over either band`() {
        val appLines = MAP_LAYER_REGISTRY.filter { it.tapGroup == TapGroup.LINE && referenceAgencyOf(it.id) == null }.map { it.id }
        assertTrue("the registry has app lines to test against", MapLayerIds.KEPT_TRACKS in appLines && MapLayerIds.OFFLINE_REGION_OUTLINE in appLines)
        for (band in listOf(MapLayerIds.REFERENCE_FS_BAND, MapLayerIds.REFERENCE_BLM_BAND)) {
            for (line in appLines) {
                val events = tap(LinesProbe(listOf(Line(band, null, BAND_Y, BAND_HALF_PX, threeBlaze), Line(line, "rec-1", BAND_Y, 3f))))
                assertEquals("$line over $band", listOf("feature:$line:rec-1"), events)
            }
        }
    }

    @Test
    fun `a marker on a band takes the tap`() {
        assertEquals(listOf("feature:${MapLayerIds.WAYPOINTS}:m-1"), tap(LinesProbe(listOf(fsBand), listOf(MapLayerIds.WAYPOINTS to (200f to BAND_Y)))))
    }

    @Test
    fun `a track within a finger's reach but not under it still loses to the band under the finger, as any two lines do`() {
        // resolveTap's point stage decides before its box: what lies exactly under the finger wins over a line beside it.
        val track = Line(MapLayerIds.KEPT_TRACKS, "track-1", BAND_Y + 20f, 2f)
        assertEquals(listOf("reference:${MapLayerIds.REFERENCE_FS_BAND}:Forest Service trail 017 · Three Blaze"), tap(LinesProbe(listOf(fsBand, track))))
    }

    @Test
    fun `a band takes the tap over a colour field's cell, by the point and from within the finger's box`() {
        val cell = Line(MapLayerIds.FORECAST_CHANTERELLES, "45.3,-115.0", BAND_Y, 500f)
        val expected = listOf("reference:${MapLayerIds.REFERENCE_FS_BAND}:Forest Service trail 017 · Three Blaze")
        assertEquals(expected, tap(LinesProbe(listOf(cell, fsBand))))
        // A finger a little off the band: the cell is under it, the band only in the finger's box, and the band still wins.
        assertEquals(expected, tap(LinesProbe(listOf(cell, fsBand)), yPx = BAND_Y + BAND_HALF_PX + 10f))
    }

    @Test
    fun `a tap clear of the band, its box included, is a plain tap`() {
        assertEquals(listOf("plain"), tap(LinesProbe(listOf(fsBand)), yPx = BAND_Y + BAND_HALF_PX + 100f))
    }

    private companion object {
        const val BAND_Y = 400f
        const val BAND_HALF_PX = 4f
        const val MARKER_HALF_PX = 12f
    }
}
