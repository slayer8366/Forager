package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceAgency
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapTapOutcome
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import com.zynergylabs.forager.app.ui.map.layers.mapTapOutcome
import org.junit.Assert.assertEquals
import org.junit.Test
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Step 8c (dispatch 2026-10-26-16 and Amendment 1): a band's tap carries the tapped feature's own fields from the map's
 * query ([tapHitOf], over MapLibre's own GeoJSON [Feature], as `queryRenderedFeatures` returns it) to the bubble's two
 * lines, through the same feature-tap path as every other bubble ([mapTapOutcome], [MapFeatureTap], [tappedThingOf]).
 * Headless: every step is a plain function. What a real tile's feature carries is a device item.
 */
class ReferenceTrailTapTest {

    private fun bandFeature(vararg properties: Pair<String, Any?>): Feature {
        val json = JsonObject()
        properties.forEach { (name, value) ->
            when (value) {
                null -> json.add(name, JsonNull.INSTANCE)
                is Number -> json.addProperty(name, value)
                else -> json.addProperty(name, value.toString())
            }
        }
        return Feature.fromGeometry(LineString.fromLngLats(listOf(Point.fromLngLat(-115.0, 45.3), Point.fromLngLat(-115.1, 45.4))), json)
    }

    @Test
    fun `a Forest Service band's hit carries its number and name, and no feature id`() {
        val hit = tapHitOf(MapLayerIds.REFERENCE_FS_BAND, bandFeature("trail_no" to "017", "trail_name" to "Three Blaze", "trail_class" to "3"))
        assertEquals(TapHit(MapLayerIds.REFERENCE_FS_BAND, null, mapOf("trail_no" to "017", "trail_name" to "Three Blaze")), hit)
    }

    @Test
    fun `a BLM band's hit carries its route name only`() {
        val hit = tapHitOf(MapLayerIds.REFERENCE_BLM_BAND, bandFeature("ROUTE_PRMRY_NM" to "Badlands Rock", "trail_name" to "not BLM's", "FAMS_ID" to "9"))
        assertEquals(TapHit(MapLayerIds.REFERENCE_BLM_BAND, null, mapOf("ROUTE_PRMRY_NM" to "Badlands Rock")), hit)
    }

    @Test
    fun `a number stored as a number is read as its text, and a null or absent field is left out`() {
        assertEquals(mapOf("trail_no" to "17"), tapHitOf(MapLayerIds.REFERENCE_FS_BAND, bandFeature("trail_no" to 17, "trail_name" to null)).attributes)
        assertEquals(emptyMap<String, String>(), tapHitOf(MapLayerIds.REFERENCE_FS_BAND, bandFeature()).attributes)
    }

    @Test
    fun `an app layer's hit carries no attributes, as before`() {
        assertEquals(TapHit(MapLayerIds.KEPT_TRACKS, null), tapHitOf(MapLayerIds.KEPT_TRACKS, bandFeature("trail_name" to "Three Blaze")))
    }

    @Test
    fun `a band hit is a reference-trail outcome with the record read from it, not an unidentified feature`() {
        val fsHit = tapHitOf(MapLayerIds.REFERENCE_FS_BAND, bandFeature("trail_no" to "017", "trail_name" to "Three Blaze"))
        assertEquals(
            MapTapOutcome.OnReferenceTrail(MapLayerIds.REFERENCE_FS_BAND, ReferenceTrailRecord(ReferenceAgency.FOREST_SERVICE, "017", "Three Blaze")),
            mapTapOutcome(fsHit),
        )
        val blmHit = tapHitOf(MapLayerIds.REFERENCE_BLM_BAND, bandFeature())
        assertEquals(MapTapOutcome.OnReferenceTrail(MapLayerIds.REFERENCE_BLM_BAND, ReferenceTrailRecord(ReferenceAgency.BLM, null, null)), mapTapOutcome(blmHit))
    }

    @Test
    fun `a band tap becomes its own bubble target, and the bubble is the record's two lines`() {
        val record = ReferenceTrailRecord(ReferenceAgency.FOREST_SERVICE, "017", "Three Blaze")
        val at = LatLng(45.3, -115.0)
        val tapped = tappedThingOf(MapFeatureTap.forReferenceTrail(MapLayerIds.REFERENCE_FS_BAND, record, Offset(10f, 20f), 30f, at))
        assertEquals(TappedMapThing(MapBubbleTarget.ReferenceTrailTarget(MapLayerIds.REFERENCE_FS_BAND, record, at), Offset(10f, 20f), 30f), tapped)
        assertEquals(
            MapBubbleContent.ReferenceTrail("Forest Service trail 017 · Three Blaze", "Official record. Routes follow the trail map, not this layer."),
            referenceTrailBubble(record),
        )
        // Not a point: the bubble keeps the tap point and names no point feature to re-anchor on.
        assertEquals(null, tapped.focusedFeature)
    }
}
