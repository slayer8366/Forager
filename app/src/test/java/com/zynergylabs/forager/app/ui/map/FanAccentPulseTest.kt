package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.testing.MadeUpPlace
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.Feature
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 7 (RECORD -2187): the selected stop's copy in an open fan fades and pulses with its highlight. What the
 * fan's icon source is given ([fanFrameCollections]), headless: while the highlight moves, the plain copy with the highlight over it at
 * that opacity and size, just above its layer's copies; at rest, the accent copy alone, as before Amendment 7.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FanAccentPulseTest {

    private val creek = FanKey(MapLayerIds.WAYPOINTS, "wp-creek")
    private val ridge = FanKey(MapLayerIds.WAYPOINTS, "wp-ridge")
    private val members = listOf(
        FanMember(creek, MadeUpPlace.LAT, MadeUpPlace.LNG, 200f, 300f, FanOffset(0f, -48f)),
        FanMember(ridge, MadeUpPlace.LAT, MadeUpPlace.LNG, 200f, 300f, FanOffset(41.6f, 24f)),
    )
    private val creekMarker = SelectedStopMarker(LatLng(MadeUpPlace.LAT, MadeUpPlace.LNG), trip = false, featureId = "wp-creek")

    private fun icons(motion: Map<FanKey, RouteStopAccentShown>): List<Feature> =
        fanFrameCollections(members, { LatLng(it.lat, it.lng) }, null, 1f, accent = creek, accentMotion = motion).icons.features().orEmpty()

    private fun Feature.image() = getStringProperty(FanOutIds.IMAGE_PROPERTY)

    @Test
    fun `mid-fade and swelling, the copy is plain with the highlight over it at that opacity and size`() {
        val creekIcons = icons(mapOf(creek to RouteStopAccentShown(creekMarker, opacity = 0.3f, scale = 1.1f)))
            .filter { it.getStringProperty(FEATURE_ID_PROPERTY) == "wp-creek" }

        assertEquals("the plain copy, then the highlight", listOf(MarkerIcon.WAYPOINT.imageId, MarkerIcon.WAYPOINT_ROUTE_SELECTED.imageId), creekIcons.map { it.image() })
        val (plain, highlight) = creekIcons
        assertEquals(0.3f, highlight.getNumberProperty(FanOutIds.ACCENT_OPACITY_PROPERTY).toFloat(), 1e-6f)
        assertEquals(1.1f, highlight.getNumberProperty(FanOutIds.ACCENT_SCALE_PROPERTY).toFloat(), 1e-6f)
        assertEquals("the plain copy is drawn full", false, plain.hasProperty(FanOutIds.ACCENT_OPACITY_PROPERTY))
        assertEquals(
            "the highlight sorts half a place above its own layer's copies",
            plain.getNumberProperty(FanOutIds.SORT_KEY_PROPERTY).toFloat() + 0.5f,
            highlight.getNumberProperty(FanOutIds.SORT_KEY_PROPERTY).toFloat(),
            1e-6f,
        )
        val plainY = plain.getProperty(FanOutIds.ICON_OFFSET_PROPERTY).asJsonArray[1].asFloat
        val highlightY = highlight.getProperty(FanOutIds.ICON_OFFSET_PROPERTY).asJsonArray[1].asFloat
        assertEquals("the swell stays centred: its offset is divided by its size (icon-offset scales with icon-size)", plainY, highlightY * 1.1f, 1e-4f)
    }

    @Test
    fun `at rest the selected stop's copy is the accent copy alone, as before`() {
        val creekIcons = icons(mapOf(creek to RouteStopAccentShown(creekMarker, 1f, 1f))).filter { it.getStringProperty(FEATURE_ID_PROPERTY) == "wp-creek" }

        assertEquals(listOf(MarkerIcon.WAYPOINT_ROUTE_SELECTED.imageId), creekIcons.map { it.image() })
        assertEquals("the same as with no motion given", icons(emptyMap()).map { it.image() }, icons(mapOf(creek to RouteStopAccentShown(creekMarker, 1f, 1f))).map { it.image() })
    }

    @Test
    fun `a highlight starting to fade out, still full, is the plain copy with the highlight over it`() {
        val ridgeMarker = SelectedStopMarker(LatLng(MadeUpPlace.LAT, MadeUpPlace.LNG), trip = false, featureId = "wp-ridge")
        val ridgeIcons = icons(mapOf(ridge to RouteStopAccentShown(ridgeMarker, 1f, 1f))).filter { it.getStringProperty(FEATURE_ID_PROPERTY) == "wp-ridge" }

        assertEquals(listOf(MarkerIcon.WAYPOINT.imageId, MarkerIcon.WAYPOINT_ROUTE_SELECTED.imageId), ridgeIcons.map { it.image() })
    }

    @Test
    fun `the other copies are untouched while one highlight moves`() {
        val ridgeIcons = icons(mapOf(creek to RouteStopAccentShown(creekMarker, 0.5f, 1f))).filter { it.getStringProperty(FEATURE_ID_PROPERTY) == "wp-ridge" }

        assertEquals(listOf(MarkerIcon.WAYPOINT.imageId), ridgeIcons.map { it.image() })
        assertEquals(false, ridgeIcons.single().hasProperty(FanOutIds.ACCENT_SCALE_PROPERTY))
    }
}
