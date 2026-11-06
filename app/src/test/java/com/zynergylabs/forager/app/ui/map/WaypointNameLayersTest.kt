package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.map.layers.LayerState
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.geojson.Point
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Waypoint names (dispatch 2026-11-02-04, C2): what can run headless. What each name source is given, per waypoint and per
 * route stop, blank names skipped; the two name layers' properties (neither waypoint names nor, since A5, the stops' overlap);
 * the names a fan hides; the Waypoints switch and the setting gating the names; and the bitmap itself, drawn with
 * Robolectric's native graphics, in the palette's colours. The collision itself is MapLibre's native placement and is
 * untested until a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WaypointNameLayersTest {

    private fun waypoint(id: String, name: String, lat: Double = 45.0, lng: Double = -122.0) = Waypoint(id, lat, lng, null, name, "", 0L)

    private fun propertiesOf(properties: Array<PropertyValue<*>>): Map<String, String> =
        properties.associate { it.name to it.value.let { v -> if (v is Array<*>) v.toList().toString() else v.toString() } }

    @Test
    fun `each waypoint with a name gets one label with its own id and image, and blank names get none`() {
        val labels = waypointNameLabels(
            listOf(waypoint("a", "Creek", 45.1, -122.1), waypoint("b", ""), waypoint("c", "   "), waypoint("d", " Ridge ", 45.2, -122.2)),
            drawn = true,
        )

        assertEquals(
            listOf(NameLabel("a", "Creek", 45.1, -122.1), NameLabel("d", "Ridge", 45.2, -122.2)),
            labels,
        )
        assertEquals(listOf("waypoint-name:Creek", "waypoint-name:Ridge"), labels.map { it.imageId })
    }

    @Test
    fun `no labels at all when names are not drawn`() {
        assertEquals(emptyList<NameLabel>(), waypointNameLabels(listOf(waypoint("a", "Creek")), drawn = false))
    }

    @Test
    fun `the name source gets a point per label, carrying the waypoint's id and its image, a stop carrying no id`() {
        val collection = nameLabelFeatures(listOf(NameLabel("a", "Creek", 45.1, -122.1), NameLabel(null, "Ridge", 45.2, -122.2)))
        val features = collection.features().orEmpty()

        assertEquals(2, features.size)
        val first = features[0].geometry() as Point
        assertEquals(listOf(45.1, -122.1), listOf(first.latitude(), first.longitude()))
        assertEquals("a", features[0].getStringProperty(FEATURE_ID_PROPERTY))
        assertEquals("waypoint-name:Creek", features[0].getStringProperty(WaypointNameIds.IMAGE_PROPERTY))
        assertFalse("a stop has no waypoint id", features[1].hasProperty(FEATURE_ID_PROPERTY))
        assertEquals("waypoint-name:Ridge", features[1].getStringProperty(WaypointNameIds.IMAGE_PROPERTY))
    }

    @Test
    fun `the route's stops are labelled from RouteStop name, blank names skipped`() {
        val labels = routeStopNameLabels(
            listOf(RouteStop("Creek", LatLng(45.1, -122.1)), RouteStop(" ", LatLng(45.3, -122.3)), RouteStop("Ridge", LatLng(45.2, -122.2))),
        )

        assertEquals(listOf(NameLabel(null, "Creek", 45.1, -122.1), NameLabel(null, "Ridge", 45.2, -122.2)), labels)
    }

    @Test
    fun `waypoint names never overlap, nor since A5 do the stops' names, and neither ignores placement`() {
        val names = propertiesOf(waypointNameLayerProperties())
        val stops = propertiesOf(stopNameLayerProperties())

        assertEquals("waypoint names: a name that would collide is not drawn", "false", names["icon-allow-overlap"])
        assertEquals("A5: a stop's name that would collide with another stop's is not drawn until you zoom in", "false", stops["icon-allow-overlap"])
        assertEquals("false", names["icon-ignore-placement"])
        assertEquals("false", stops["icon-ignore-placement"])
        for (props in listOf(names, stops)) {
            assertEquals(listOf("get", WaypointNameIds.IMAGE_PROPERTY), props["icon-image"]!!.trim('[', ']').split(",").map { it.trim().trim('"') })
            assertEquals("left", props["icon-anchor"])
            assertEquals("[13.0, -14.0]", props["icon-offset"])
        }
    }

    @Test
    fun `a fan hides the names of its fanned waypoints only`() {
        fun member(layer: String, id: String) = FanMember(FanKey(layer, id), 45.0, -122.0, 0f, 0f, FanOffset(0f, 0f))
        val members = listOf(member(MapLayerIds.WAYPOINTS, "a"), member(MapLayerIds.FINDS, "f"), member(MapLayerIds.WAYPOINTS, "b"))

        assertEquals(listOf("a", "b"), fanHiddenNameIds(members))
        assertEquals(emptyList<String>(), fanHiddenNameIds(emptyList()))
    }

    @Test
    fun `names are drawn only with the setting on and the Waypoints switch on`() {
        val waypointsOff = MapLayersState(layers = mapOf(MapLayerIds.WAYPOINTS to LayerState(visible = false)))
        val findsOff = MapLayersState(layers = mapOf(MapLayerIds.FINDS to LayerState(visible = false)))

        assertTrue(waypointNamesDrawn(setting = true, MapLayersState()))
        assertTrue(waypointNamesDrawn(setting = true, findsOff))
        assertFalse(waypointNamesDrawn(setting = false, MapLayersState()))
        assertFalse(waypointNamesDrawn(setting = true, waypointsOff))
    }

    @Test
    fun `each image is registered once per style and palette, again on a new style or a night switch`() {
        val registry = NameImageRegistry()
        val styleA = Any()
        val styleB = Any()
        val creek = NameLabel("a", "Creek", 0.0, 0.0)
        val creek2 = NameLabel("b", "Creek", 1.0, 1.0)
        val ridge = NameLabel("c", "Ridge", 0.0, 0.0)

        assertEquals(listOf(creek, ridge), registry.toRegister(styleA, MapPalette.DAY, listOf(creek, creek2, ridge)))
        assertEquals(emptyList<NameLabel>(), registry.toRegister(styleA, MapPalette.DAY, listOf(creek, ridge)))
        assertEquals(listOf(creek), registry.toRegister(styleA, MapPalette.NIGHT, listOf(creek)))
        assertEquals(listOf(creek), registry.toRegister(styleB, MapPalette.NIGHT, listOf(creek)))
    }

    private fun countNear(bitmap: Bitmap, colour: Int): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val p = bitmap.getPixel(x, y)
            if ((p ushr 24) < 250) continue
            val d = maxOf(kotlin.math.abs((p shr 16 and 0xFF) - (colour shr 16 and 0xFF)), kotlin.math.abs((p shr 8 and 0xFF) - (colour shr 8 and 0xFF)), kotlin.math.abs((p and 0xFF) - (colour and 0xFF)))
            if (d <= 8) n++
        }
        return n
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a name is drawn in the waypoint colour over a casing halo, by day and by night`() {
        val typeface = nameLabelTypeface(ApplicationProvider.getApplicationContext())
        for (palette in listOf(MapPalette.DAY, MapPalette.NIGHT)) {
            val bitmap = NameLabelPainter(typeface, density = 3f, palette = palette).bitmap("Creek")

            assertTrue("$palette: text height about 12 dp at 3x, got ${bitmap.height}", bitmap.height in 36..60)
            assertTrue("$palette: the text fill", countNear(bitmap, palette.waypoint) > 20)
            assertTrue("$palette: the halo", countNear(bitmap, palette.casing) > 20)
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long name ends in an ellipsis at the most width a name takes`() {
        val painter = NameLabelPainter(nameLabelTypeface(ApplicationProvider.getApplicationContext()), density = 3f, palette = MapPalette.DAY)
        val long = "The old logging road junction past the second creek crossing"

        val shown = painter.fitted(long)
        assertTrue("cut: \"$shown\"", shown.length < long.length && shown.endsWith("…"))
        assertEquals("a short name is kept whole", "Creek", painter.fitted("Creek"))
        val width = painter.bitmap(long).width
        assertTrue("at most ${NAME_MAX_WIDTH_DP} dp plus the halo at 3x: $width", width <= ((NAME_MAX_WIDTH_DP + 2 * NAME_HALO_DP) * 3f).toInt() + 1)
    }
}
