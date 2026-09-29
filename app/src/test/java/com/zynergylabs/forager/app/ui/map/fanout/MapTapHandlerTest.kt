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
 * The map's tap, as `SightingsMap`'s click listener now hands it over (dispatch 2026-09-28-197; the
 * owner's rules 1 to 5): a stack fans out, a fanned marker opens its bubble, and every fold-back
 * trigger folds. The handler is the real class the listener delegates to; only the SDK behind
 * [MapProbe] is the test's own ([FanOutTestScene], a real Web-Mercator projection). A fully open
 * fan is `progress = 1`, which `MarkerFanOutHostTest` shows the host reaching in 400 ms.
 */
class MapTapHandlerTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val handler = MapTapHandler(
        fan = fan,
        probe = scene,
        drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
        sinks = sinks,
    ).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun tap(xPx: Float, yPx: Float) = handler.onMapTap(LatLng(scene.centre.lat, scene.centre.lng), xPx, yPx)

    private fun tapOn(lat: Double, lng: Double) = tap(scene.xPx(lng), scene.yPx(lat))

    private fun tapAtSpot() = tapOn(scene.centre.lat, scene.centre.lng)

    private fun fullyOpen() { fan.progress = 1f }

    /** Where a fanned member is drawn, in px, at full spread. */
    private fun placeOf(m: FanMember): Pair<Float, Float> {
        val at = memberPositionDp(m, 1f)
        return at.xDp * scene.density to at.yDp * scene.density
    }

    // Rules 1 and 2: a stack fans out.

    @Test
    fun `a tap on two photos at one spot fans them out and opens no bubble`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        tapAtSpot()
        assertTrue("the fan is open", fan.isOpen)
        assertEquals(setOf(FanKey(MapLayerIds.PHOTOS, "p1"), FanKey(MapLayerIds.PHOTOS, "p2")), fan.members.map { it.key }.toSet())
        assertEquals("nothing else is told", emptyList<String>(), sinks.events)
    }

    @Test
    fun `a lone marker opens its bubble as it always did, and no fan opens`() {
        scene.add(MapLayerIds.FINDS, "f1")
        tapAtSpot()
        assertFalse(fan.isOpen)
        assertEquals(listOf("feature:${MapLayerIds.FINDS}:f1"), sinks.events)
    }

    @Test
    fun `the same two markers are a stack when zoomed out and are not once zoom separates them`() {
        scene.add(MapLayerIds.PHOTOS, "west", lat = 45.0, lng = -122.0)
        scene.add(MapLayerIds.PHOTOS, "east", lat = 45.0, lng = -122.0 + 0.0001)

        scene.zoom = 10.0
        tapAtSpot()
        assertTrue("at zoom 10 they are about 0.3 dp apart", fan.isOpen)
        fan.fold(); fan.release(); sinks.events.clear()

        scene.zoom = 20.0
        tapOn(45.0, -122.0)
        assertFalse("at zoom 20 they are about 75 dp apart", fan.isOpen)
        assertEquals(listOf("feature:${MapLayerIds.PHOTOS}:west"), sinks.events)
    }

    @Test
    fun `a photo over a find fans both, so the find is reachable`() {
        scene.add(MapLayerIds.FINDS, "find")
        scene.add(MapLayerIds.PHOTOS, "photo")
        tapAtSpot()
        assertEquals(setOf(FanKey(MapLayerIds.FINDS, "find"), FanKey(MapLayerIds.PHOTOS, "photo")), fan.members.map { it.key }.toSet())
        fullyOpen()
        val find = fan.members.single { it.key.layerId == MapLayerIds.FINDS }
        val (x, y) = placeOf(find)
        tap(x, y)
        assertEquals(listOf("feature:${MapLayerIds.FINDS}:find"), sinks.events)
    }

    @Test
    fun `the layer drawn on top comes first in the ring`() {
        scene.add(MapLayerIds.FINDS, "find")
        scene.add(MapLayerIds.PHOTOS, "photo")
        scene.add(MapLayerIds.WAYPOINTS, "waypoint")
        tapAtSpot()
        assertEquals(listOf(MapLayerIds.PHOTOS, MapLayerIds.FINDS, MapLayerIds.WAYPOINTS), fan.members.map { it.key.layerId })
    }

    // A sighting no longer joins a fan (the owner's "1 A", continuation -208): MapTapHandlerRecordsOnlyTest holds that.

    // Rule 4: a tap on a fanned marker opens its bubble.

    @Test
    fun `a tap on each fanned marker opens that marker's bubble, from the centre and from every corner of its own square`() {
        val ids = (1..5).map { "p$it" }
        ids.forEach { scene.add(MapLayerIds.PHOTOS, it) }
        tapAtSpot()
        fullyOpen()
        assertEquals(5, fan.members.size)
        for (member in fan.members.toList()) {
            val (cx, cy) = placeOf(member)
            val reach = 20f * scene.density
            for ((dx, dy) in listOf(0f to 0f, -reach to -reach, reach to -reach, -reach to reach, reach to reach)) {
                sinks.events.clear()
                tap(cx + dx, cy + dy)
                assertEquals("touch at $dx,$dy from ${member.key.featureId}", listOf("feature:${MapLayerIds.PHOTOS}:${member.key.featureId}"), sinks.events)
                assertTrue("the fan stays open behind its bubble", fan.isOpen)
            }
        }
    }

    @Test
    fun `more than eight fan out and every one can be opened`() {
        val ids = (1..12).map { "s$it" }
        ids.forEach { scene.add(MapLayerIds.PHOTOS, it) }
        tapAtSpot()
        fullyOpen()
        assertEquals(12, fan.members.size)
        val opened = fan.members.toList().map { member ->
            sinks.events.clear()
            val (x, y) = placeOf(member)
            tap(x, y)
            sinks.events.single()
        }
        assertEquals(ids.map { "feature:${MapLayerIds.PHOTOS}:$it" }.toSet(), opened.toSet())
    }

    // Rule 5: the folds.

    @Test
    fun `a tap on the empty map folds the fan and is also the plain tap it would have been`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        tapAtSpot()
        fullyOpen()
        tap(50f, 50f)
        assertFalse(fan.isOpen)
        assertEquals(listOf("plain"), sinks.events)
    }

    @Test
    fun `a tap on another marker folds the fan and opens that marker's bubble`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        scene.add(MapLayerIds.FINDS, "elsewhere", lat = 45.05, lng = -122.0) // about 73 dp away at zoom 10
        tapAtSpot()
        fullyOpen()
        tapOn(45.05, -122.0)
        assertFalse(fan.isOpen)
        assertEquals(listOf("feature:${MapLayerIds.FINDS}:elsewhere"), sinks.events)
    }

    @Test
    fun `a camera move folds the fan, whether a pan or a zoom, and reports nothing`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        repeat(2) {
            tapAtSpot()
            fullyOpen()
            assertTrue(fan.isOpen)
            handler.onCameraMoveStarted()
            assertFalse("after camera move #${it + 1}", fan.isOpen)
            fan.release()
        }
        assertEquals(emptyList<String>(), sinks.events)
    }

    @Test
    fun `a change to what the map draws folds the fan`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        tapAtSpot()
        assertTrue(fan.isOpen)
        handler.onContentChanged()
        assertFalse(fan.isOpen)
    }

    @Test
    fun `while a fan folds, its hidden originals take no tap`() {
        scene.add(MapLayerIds.PHOTOS, "p1")
        scene.add(MapLayerIds.PHOTOS, "p2")
        tapAtSpot()
        fullyOpen()
        handler.onCameraMoveStarted() // folding: members held until the host releases them
        tapAtSpot()
        assertFalse("no fan reopens on hidden originals", fan.isOpen)
        assertEquals(listOf("plain"), sinks.events)
    }
}
