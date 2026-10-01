package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tap on a fanned icon shows that item and folds the fan, on that one tap (dispatch 2026-09-28-381; the owner: "when an icon gets tapped,
 * immediately display the icon and dismiss the fan upon that single tap"). The handler is the real class; the map SDK behind it is [FanOutTestScene].
 * What the fold looks like, and the bubble at the marker on a real device, are the recordings'.
 */
class MapTapHandlerFanTapTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val fannedFrom = mutableListOf<FannedFrom?>()
    private var bubbleUp = false
    private val handler = MapTapHandler(
        fan = fan,
        probe = scene,
        drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
        sinks = sinks,
        bubbleOpen = { bubbleUp },
        onFannedFrom = { fannedFrom += it },
    ).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun tapAtSpot() = handler.onMapTap(LatLng(scene.centre.lat, scene.centre.lng), scene.xPx(scene.centre.lng), scene.yPx(scene.centre.lat))

    private fun tapAtPx(xPx: Float, yPx: Float, at: LatLng = LatLng(scene.centre.lat, scene.centre.lng)) = handler.onMapTap(at, xPx, yPx)

    /** A stack of a [layerId] marker and a photo, fanned. */
    private fun openFanWith(layerId: String): List<FanMember> {
        val other = if (layerId == MapLayerIds.PHOTOS) MapLayerIds.FINDS else MapLayerIds.PHOTOS
        scene.add(layerId, "m1", lat = 45.0001, lng = -122.0001)
        scene.add(other, "o1")
        tapAtSpot()
        fan.progress = 1f
        assertTrue("the fan is open", fan.isOpen)
        sinks.events.clear()
        sinks.featureTaps.clear()
        return fan.members
    }

    private fun drawnPx(m: FanMember): Pair<Float, Float> {
        val marker = scene.markersOf(listOf(m.key)).single()
        return (marker.xPx + m.offset.xDp * scene.density) to (marker.yPx + m.offset.yDp * scene.density)
    }

    @Test
    fun `a tap on a fanned icon gives its own outcome and folds the fan, for each kind a fan can hold`() {
        for (layer in listOf(MapLayerIds.PHOTOS, MapLayerIds.FINDS, MapLayerIds.WAYPOINTS, MapLayerIds.PLANNED_TRIPS)) {
            val s = FanOutTestScene()
            val f = MarkerFanOutState()
            val k = RecordingSinks()
            val h = MapTapHandler(f, s, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, k)
            s.hidden = { f.members.map { it.key }.toSet() }
            s.add(layer, "m1", lat = 45.0001, lng = -122.0001)
            s.add(MapLayerIds.PHOTOS.takeIf { layer != MapLayerIds.PHOTOS } ?: MapLayerIds.FINDS, "o1")
            h.onMapTap(LatLng(s.centre.lat, s.centre.lng), s.xPx(s.centre.lng), s.yPx(s.centre.lat))
            f.progress = 1f
            assertTrue("$layer: the fan is open", f.isOpen)
            k.events.clear()
            val member = f.members.single { it.key.layerId == layer }
            val marker = s.markersOf(listOf(member.key)).single()
            val x = marker.xPx + member.offset.xDp * s.density
            val y = marker.yPx + member.offset.yDp * s.density

            h.onMapTap(LatLng(s.centre.lat, s.centre.lng), x, y)

            assertEquals("$layer: its own outcome, once", listOf("feature:$layer:m1"), k.events)
            assertFalse("$layer: the fan folds on that one tap", f.isOpen)
        }
    }

    @Test
    fun `the bubble is anchored at the marker's own place and carries its own coordinates, not the finger's`() {
        val members = openFanWith(MapLayerIds.FINDS)
        val find = members.single { it.key.layerId == MapLayerIds.FINDS }
        val (x, y) = drawnPx(find)
        val fingerAt = LatLng(46.0, -121.0)

        tapAtPx(x, y, at = fingerAt)

        val tap = sinks.featureTaps.single()
        val marker = scene.markersOf(listOf(find.key)).single()
        assertEquals("anchored where the marker sits in its stack, x", marker.xPx, tap.xPx, 0.5f)
        assertEquals("anchored where the marker sits in its stack, y", marker.yPx, tap.yPx, 0.5f)
        assertEquals("the record's own latitude", find.lat, tap.at.lat, 0.0)
        assertEquals("the record's own longitude", find.lng, tap.at.lng, 0.0)
    }

    @Test
    fun `after the map has moved the bubble is still anchored at the marker now`() {
        val members = openFanWith(MapLayerIds.FINDS)
        val find = members.single { it.key.layerId == MapLayerIds.FINDS }
        scene.panXPx = 140f
        scene.panYPx = -90f
        val (x, y) = drawnPx(find)

        tapAtPx(x, y)

        val tap = sinks.featureTaps.single()
        val marker = scene.markersOf(listOf(find.key)).single()
        assertEquals(marker.xPx, tap.xPx, 0.5f)
        assertEquals(marker.yPx, tap.yPx, 0.5f)
    }

    @Test
    fun `a tap on another stack folds the old fan and opens the new one, as before`() {
        openFanWith(MapLayerIds.FINDS)
        scene.addAtScreen(MapLayerIds.WAYPOINTS, "w1", 100f, 1500f)
        scene.addAtScreen(MapLayerIds.PLANNED_TRIPS, "t1", 100f, 1500f)

        tapAtPx(100f, 1500f)

        assertTrue("a fan is open", fan.isOpen)
        assertEquals("and it is the new one", setOf("w1", "t1"), fan.members.map { it.key.featureId }.toSet())
        assertTrue("no outcome was reported for the stack tap", sinks.events.isEmpty())
    }

    @Test
    fun `a tap on a single marker with a fan open gives its outcome and folds the fan, as before`() {
        openFanWith(MapLayerIds.FINDS)
        scene.addAtScreen(MapLayerIds.WAYPOINTS, "w-single", 100f, 1700f)

        tapAtPx(100f, 1700f)

        assertEquals(listOf("feature:${MapLayerIds.WAYPOINTS}:w-single"), sinks.events)
        assertFalse(fan.isOpen)
    }

    @Test
    fun `a tap on empty map with a fan open and no bubble folds the fan, as before`() {
        openFanWith(MapLayerIds.FINDS)

        tapAtPx(900f, 1900f)

        assertEquals(listOf("plain"), sinks.events)
        assertFalse(fan.isOpen)
    }

    @Test
    fun `a tap on a stack while a bubble is up fans it and leaves the bubble, which is what the app does today`() {
        bubbleUp = true // a bubble from a single marker is showing and no fan is open
        scene.add(MapLayerIds.FINDS, "m1", lat = 45.0001, lng = -122.0001)
        scene.add(MapLayerIds.PHOTOS, "o1")

        tapAtSpot()

        assertTrue("the stack fanned", fan.isOpen)
        assertTrue("no plain tap went on to close the bubble", sinks.events.isEmpty())
    }

    @Test
    fun `the fan an icon was picked from is reported with the tap, and cleared by every other tap`() {
        val members = openFanWith(MapLayerIds.FINDS)
        val find = members.single { it.key.layerId == MapLayerIds.FINDS }
        val (x, y) = drawnPx(find)
        fannedFrom.clear()

        tapAtPx(x, y)

        assertEquals(
            listOf(FannedFrom(find.key, members.map { it.key })),
            fannedFrom.toList(),
        )

        fannedFrom.clear()
        tapAtPx(900f, 1900f) // empty map
        assertEquals("an empty tap clears it", listOf<FannedFrom?>(null), fannedFrom.toList())

        fannedFrom.clear()
        scene.addAtScreen(MapLayerIds.WAYPOINTS, "w-single", 100f, 1700f)
        tapAtPx(100f, 1700f) // a single marker
        assertEquals("a single marker's tap clears it", listOf<FannedFrom?>(null), fannedFrom.toList())
    }

    @Test
    fun `a tap on a stack, which only opens a fan, clears it too`() {
        scene.add(MapLayerIds.FINDS, "m1", lat = 45.0001, lng = -122.0001)
        scene.add(MapLayerIds.PHOTOS, "o1")

        tapAtSpot()

        assertTrue(fan.isOpen)
        assertNull("a stack tap picked nothing from a fan", fannedFrom.last())
    }
}
