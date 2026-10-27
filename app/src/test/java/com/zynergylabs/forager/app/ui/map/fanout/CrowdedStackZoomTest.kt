package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A crowded group zooms in, then fans (dispatch 2026-10-27-07, RECORD -1736), through the real entry point, a tap into
 * [MapTapHandler], over a scene with a real projection ([FanOutTestScene]) and a camera that moves it ([SceneStackCamera]).
 *
 * The view is 300 x 600 dp ([BOUNDS]): a fan of 20 fits it (288 dp wide with its touch squares) and a fan of 30 does not
 * (327 dp), the spiral report's footprint table. At zoom 10 everything within about 0.017 degrees of longitude is one stack.
 */
class CrowdedStackZoomTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val looper = FakeLooper()
    private val classifier = CameraMoveClassifier(looper::post)
    private val camera = SceneStackCamera(scene, 300f, 600f, classifier)
    private val host = CrowdedStackHost()
    private val warnings = mutableListOf<String>()

    private val space = object : FanSpace {
        override fun boundsPx() = BOUNDS
        override fun keepOutsPx(): List<FanRect> = emptyList()
    }

    private fun handler(crowded: CrowdedStackZoom? = CrowdedStackZoom(camera, { host }, { warnings += it })) =
        MapTapHandler(fan, scene, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, sinks, space, warn = { warnings += it }, crowded = crowded)
            .also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun tapOn(h: MapTapHandler, lat: Double, lng: Double) = h.onMapTap(LatLng(lat, lng), scene.xPx(lng), scene.yPx(lat))

    private fun addAt(layer: String, prefix: String, count: Int, lat: Double, lng: Double) =
        repeat(count) { scene.add(layer, "$prefix$it", lat, lng) }

    private val a = LatLng(45.0, -122.0)

    @Test
    fun `a stack whose fan fits fans as it did, and the camera does not move`() {
        addAt(MapLayerIds.PHOTOS, "p", 3, a.lat, a.lng)
        tapOn(handler(), a.lat, a.lng)

        assertTrue(fan.isOpen)
        assertEquals(3, fan.members.size)
        assertTrue("no zoom for a fan that fits", camera.frames.isEmpty())
        assertFalse(host.cameraReturnPending)
        assertNull(host.list)
    }

    @Test
    fun `a stack whose fan does not fit zooms to its area, and the fan that then fits opens by itself`() {
        // 20 photos at A and 10 finds about 950 m east (18 dp at zoom 10: in the stack, but outside the finger's 12 dp, so a photo is what is tapped): one stack of 30, two apart once zoomed to their bounds.
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)
        val h = handler()

        tapOn(h, a.lat, a.lng)

        assertEquals("one zoom", 1, camera.frames.size)
        assertTrue("to the stack's bounds", camera.frames.single() is EntryMapFrame.Fit)
        assertTrue("closer than before, short of the cap: ${scene.zoom}", scene.zoom > 10.0 && scene.zoom < CROWDED_STACK_CLOSEST_ZOOM)
        assertTrue("the fan opened with no second tap", fan.isOpen)
        assertEquals((0 until 20).map { FanKey(MapLayerIds.PHOTOS, "p$it") }.toSet(), fan.members.map { it.key }.toSet())
        assertTrue("Back has the camera from before the tap", host.cameraReturnPending)
        assertNull(host.list)
    }

    @Test
    fun `the zoom's own move does not fold the fan it opened, and a touch afterwards does`() {
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)
        val h = handler()
        tapOn(h, a.lat, a.lng)
        assertTrue(fan.isOpen)

        // MapLibre queues the move's start after the camera call; it arrives once the fan is open.
        val cause = classifier.classify(isGesture = false, followingLocation = false)
        assertEquals(CameraMoveCause.APP_STACK_ZOOM, cause)
        h.onCameraMoveStarted(cause)
        assertTrue("the zoom's own move did not fold the fan", fan.isOpen)
        classifier.onCameraIdle()
        looper.runAll()

        h.onCameraMoveStarted(classifier.classify(isGesture = true, followingLocation = false))
        assertFalse("a touch folds it, as before", fan.isOpen)
        assertFalse("and the map is the user's: Back no longer moves the camera", host.cameraReturnPending)
    }

    @Test
    fun `a zoom that splits the stack opens nothing, and keeps the way back`() {
        // A 6 x 5 grid 0.0006 degrees apart: one stack at zoom 10, every marker apart once zoomed to the grid.
        for (row in 0 until 5) for (col in 0 until 6) scene.add(MapLayerIds.FINDS, "g$row-$col", a.lat + row * 0.0006, a.lng + col * 0.0006)
        tapOn(handler(), a.lat, a.lng)

        assertEquals(1, camera.frames.size)
        assertFalse("split: nothing opens", fan.isOpen)
        assertNull(host.list)
        assertTrue(host.cameraReturnPending)
    }

    @Test
    fun `still one spot at the closest zoom, the stack becomes a list`() {
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        tapOn(handler(), a.lat, a.lng)

        val frame = camera.frames.single()
        assertTrue("one spot: centred at the closest zoom", frame is EntryMapFrame.SinglePoint && frame.zoom == CROWDED_STACK_CLOSEST_ZOOM)
        assertFalse("no fan past the edge", fan.isOpen)
        val list = assertNotNullList()
        assertEquals((0 until 30).map { FanKey(MapLayerIds.FINDS, "f$it") }.toSet(), list.keys.toSet())
        assertEquals("Back returns to before the tap after the list", 1, host.cameraReturnCount)
    }

    @Test
    fun `a stack tapped at the closest zoom already lists at once, with no zoom and no camera step`() {
        scene.zoom = CROWDED_STACK_CLOSEST_ZOOM
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        tapOn(handler(), a.lat, a.lng)

        assertTrue(camera.frames.isEmpty())
        assertNotNullList()
        assertFalse(host.cameraReturnPending)
    }

    @Test
    fun `a tap on the map closes the list`() {
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        val h = handler()
        tapOn(h, a.lat, a.lng)
        assertNotNullList()

        h.onMapTap(LatLng(0.0, 0.0), 20f, 20f)

        assertNull(host.list)
        assertEquals(listOf("plain"), sinks.events)
    }

    @Test
    fun `Back's camera step puts the camera back where it was before the tap`() {
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)
        tapOn(handler(), a.lat, a.lng)
        fan.fold()
        fan.release()

        assertTrue(host.stepBack())

        val restored = camera.restores.single()
        assertEquals(10.0, restored.zoom, 1e-9)
        assertEquals(a.lat, restored.lat, 1e-9)
        assertEquals(a.lng, restored.lng, 1e-9)
        assertEquals(10.0, scene.zoom, 1e-9)
        assertFalse(host.cameraReturnPending)
        assertFalse("nothing more to step back", host.stepBack())
    }

    @Test
    fun `a map with no zoom still fans past the edge, and says so`() {
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        tapOn(handler(crowded = null), a.lat, a.lng)

        assertTrue("fanned as before", fan.isOpen)
        assertEquals(30, fan.members.size)
        assertTrue("logged: $warnings", warnings.any { it.contains("does not fit") && it.contains("allOnScreen=false") })
    }

    @Test
    fun `a map with no host, or a camera it may not move, fans past the edge and says why`() {
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        tapOn(handler(crowded = CrowdedStackZoom(camera, { null }, { warnings += it })), a.lat, a.lng)
        assertTrue(fan.isOpen)
        assertTrue("no host: $warnings", warnings.any { it.contains("no crowded-stack host") })
        assertTrue("and the overflow itself: $warnings", warnings.any { it.contains("does not fit") })

        fan.fold()
        fan.release()
        warnings.clear()
        camera.available = false
        tapOn(handler(), a.lat, a.lng)
        assertTrue(fan.isOpen)
        assertTrue("navigating: $warnings", warnings.any { it.contains("may not be moved") })
        assertTrue(camera.frames.isEmpty())
    }

    private fun assertNotNullList(): CrowdedList {
        val list = host.list
        assertNotNull("the list opened", list)
        return list!!
    }

    private companion object {
        /** 300 x 600 dp at density 2, centred on the scene's viewport centre (500, 1000 px). */
        val BOUNDS = FanRect(200f, 400f, 800f, 1600f)
    }
}
