package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Only the owner's own records fan out (continuation `-208`; the owner's "1 A"): finds, photos,
 * waypoints and planned trips. iNaturalist sighting dots never join a fan, and a tap on one behaves as
 * it did before F4. The handler is the real class the click listener delegates to.
 */
class MapTapHandlerRecordsOnlyTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val order = orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT)
    private val handler = MapTapHandler(fan, scene, { order }, sinks).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun tapAtSpot() = handler.onMapTap(LatLng(scene.centre.lat, scene.centre.lng), scene.xPx(scene.centre.lng), scene.yPx(scene.centre.lat))

    @Test
    fun `the layers that fan are exactly finds, photos, waypoints and planned trips`() {
        assertEquals(
            setOf(MapLayerIds.FINDS, MapLayerIds.PHOTOS, MapLayerIds.WAYPOINTS, MapLayerIds.PLANNED_TRIPS),
            fanOutLayerIds(order).toSet(),
        )
        assertFalse("sightings are not among them", MapLayerIds.SIGHTINGS in fanOutLayerIds(order))
    }

    @Test
    fun `a lone sighting dot opens its bubble as before, and no fan opens`() {
        scene.add(MapLayerIds.SIGHTINGS, "9001")
        tapAtSpot()
        assertFalse(fan.isOpen)
        assertEquals(listOf("sighting:9001"), sinks.events)
    }

    @Test
    fun `several dots at one spot never fan - the tap opens one, as before`() {
        scene.add(MapLayerIds.SIGHTINGS, "9001")
        scene.add(MapLayerIds.SIGHTINGS, "9002")
        scene.add(MapLayerIds.SIGHTINGS, "9003")
        tapAtSpot()
        assertFalse(fan.isOpen)
        assertEquals(1, sinks.events.size)
        assertTrue(sinks.events.single().startsWith("sighting:"))
    }

    @Test
    fun `records stacked over dots fan without the dots, and the dots stay put`() {
        scene.add(MapLayerIds.SIGHTINGS, "9001")
        scene.add(MapLayerIds.SIGHTINGS, "9002")
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.FINDS, "f1")
        tapAtSpot()
        assertTrue(fan.isOpen)
        assertEquals(setOf(FanKey(MapLayerIds.PHOTOS, "p1"), FanKey(MapLayerIds.FINDS, "f1")), fan.members.map { it.key }.toSet())
        assertTrue("no dot is hidden", fan.members.none { it.key.layerId == MapLayerIds.SIGHTINGS })
        assertEquals(emptyList<String>(), sinks.events)
    }

    @Test
    fun `one record over dots is a lone record - its bubble opens, no fan, dots unmoved`() {
        scene.add(MapLayerIds.SIGHTINGS, "9001")
        scene.add(MapLayerIds.WAYPOINTS, "w1")
        tapAtSpot()
        assertFalse("a stack is two or more records", fan.isOpen)
        assertEquals(listOf("feature:${MapLayerIds.WAYPOINTS}:w1"), sinks.events)
    }

    @Test
    fun `a planned trip and a waypoint fan together`() {
        scene.add(MapLayerIds.PLANNED_TRIPS, "t1")
        scene.add(MapLayerIds.WAYPOINTS, "w1")
        tapAtSpot()
        assertEquals(setOf(FanKey(MapLayerIds.PLANNED_TRIPS, "t1"), FanKey(MapLayerIds.WAYPOINTS, "w1")), fan.members.map { it.key }.toSet())
    }

    @Test
    fun `a dot next to an open fan is still tappable as before`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        scene.add(MapLayerIds.SIGHTINGS, "9001", lat = 45.0, lng = -121.95) // about 73 dp east at zoom 10, clear of a two-marker ring (it is vertical)
        tapAtSpot()
        fan.progress = 1f
        handler.onMapTap(LatLng(45.0, -121.95), scene.xPx(-121.95), scene.yPx(45.0))
        assertFalse("the tap folded the fan", fan.isOpen)
        assertEquals(listOf("sighting:9001"), sinks.events)
    }
}
