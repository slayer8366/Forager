package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.CrowdedIconsMode
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
 * Settings > Map > "Crowded icons" = List (dispatch 2026-10-27-14, RECORD -1750, -1758), and the two press rules from
 * Sorrel's quick look at #430 (Amendment 2, Q1 and Q2; RECORD -1757), headless on [CrowdedStackZoomTest]'s pattern: a tap
 * into the real [MapTapHandler] over a [FanOutTestScene] and a [SceneStackCamera] that moves it.
 *
 * The press rules' own call sites (`MapLocationDot.locate`, `MapNavigationView`'s effect) need a MapLibre map, which cannot
 * be built under Robolectric; what is held here is the rule each calls, [CrowdedStackHost.onLocatePressed] and
 * [CrowdedStackHost.onNavigationViewRequested], with the camera events that follow on the phone.
 */
class CrowdedStackListModeTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()
    private val looper = FakeLooper()
    private val classifier = CameraMoveClassifier(looper::post)
    private val camera = SceneStackCamera(scene, 300f, 600f, classifier)
    private val host = CrowdedStackHost()
    private val warnings = mutableListOf<String>()

    private val space = object : FanSpace {
        override fun boundsPx() = FanRect(200f, 400f, 800f, 1600f)
        override fun keepOutsPx(): List<FanRect> = emptyList()
    }

    private fun handler() =
        MapTapHandler(
            fan, scene, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, sinks, space,
            warn = { warnings += it }, crowded = CrowdedStackZoom(camera, { host }, { warnings += it }),
        ).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun tapOn(h: MapTapHandler, lat: Double, lng: Double) = h.onMapTap(LatLng(lat, lng), scene.xPx(lng), scene.yPx(lat))

    private fun addAt(layer: String, prefix: String, count: Int, lat: Double, lng: Double) =
        repeat(count) { scene.add(layer, "$prefix$it", lat, lng) }

    private val a = LatLng(45.0, -122.0)

    /** A crowded tap in "Zoom in, then fan" that zooms and fans, leaving one camera return; the camera's move has settled. */
    private fun zoomedAndFanned(h: MapTapHandler) {
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)
        tapOn(h, a.lat, a.lng)
        assertTrue("positive control: the zoom fanned", fan.isOpen)
        assertTrue("positive control: Back has a camera return", host.cameraReturnPending)
        classifier.onCameraIdle()
        looper.runAll()
    }

    // ── List mode ──

    @Test
    fun `in List mode a stack whose fan would fit opens the list, with no fan, no zoom and no camera step`() {
        host.mode = CrowdedIconsMode.LIST
        addAt(MapLayerIds.PHOTOS, "p", 3, a.lat, a.lng)

        tapOn(handler(), a.lat, a.lng)

        assertFalse("no fan in List mode", fan.isOpen)
        val list = checkNotNull(host.list) { "the list opened" }
        assertEquals((0 until 3).map { FanKey(MapLayerIds.PHOTOS, "p$it") }.toSet(), list.keys.toSet())
        assertTrue("no zoom", camera.frames.isEmpty())
        assertFalse("nothing moved, so Back has no camera step", host.cameraReturnPending)
    }

    @Test
    fun `in List mode a stack whose fan would not fit opens the list at once, with no zoom`() {
        host.mode = CrowdedIconsMode.LIST
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)

        tapOn(handler(), a.lat, a.lng)

        assertEquals("all thirty, as they stand at this zoom", 30, host.list?.keys?.size)
        assertTrue("no zoom", camera.frames.isEmpty())
        assertEquals("the camera did not move", 10.0, scene.zoom, 1e-9)
        assertFalse(fan.isOpen)
        assertFalse(host.cameraReturnPending)
    }

    @Test
    fun `in List mode a single icon still opens its bubble directly`() {
        host.mode = CrowdedIconsMode.LIST
        scene.add(MapLayerIds.FINDS, "alone", a.lat, a.lng)

        tapOn(handler(), a.lat, a.lng)

        assertNull("no list for one icon", host.list)
        assertEquals(listOf("feature:${MapLayerIds.FINDS}:alone"), sinks.events)
    }

    @Test
    fun `in List mode a map tap closes the list`() {
        host.mode = CrowdedIconsMode.LIST
        addAt(MapLayerIds.PHOTOS, "p", 3, a.lat, a.lng)
        val h = handler()
        tapOn(h, a.lat, a.lng)
        assertNotNull(host.list)

        h.onMapTap(LatLng(0.0, 0.0), 20f, 20f)

        assertNull(host.list)
        assertEquals(listOf("plain"), sinks.events)
    }

    @Test
    fun `in List mode the list opens while navigating, when the camera may not be moved`() {
        host.mode = CrowdedIconsMode.LIST
        camera.available = false
        addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
        addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)

        tapOn(handler(), a.lat, a.lng)

        assertEquals("the list opened while navigating (RECORD -1758)", 30, host.list?.keys?.size)
        assertFalse("not fanned past the edge, as the zoom does while navigating", fan.isOpen)
        assertFalse(warnings.any { it.contains("may not be moved") })
        assertEquals("no camera bearing to read: 0", 0f, host.list!!.bearingDeg)
    }

    @Test
    fun `in Zoom mode, the default, a stack whose fan fits still fans`() {
        assertEquals(CrowdedIconsMode.ZOOM_THEN_FAN, host.mode)
        addAt(MapLayerIds.PHOTOS, "p", 3, a.lat, a.lng)

        tapOn(handler(), a.lat, a.lng)

        assertTrue(fan.isOpen)
        assertNull(host.list)
    }

    // ── The edge case: a mode change with camera returns pending (decided beyond the dispatch's scope) ──

    @Test
    fun `switching from Zoom to List drops the camera returns, so Back does not jump the map back`() {
        zoomedAndFanned(handler())
        fan.fold()
        fan.release()

        host.mode = CrowdedIconsMode.LIST

        assertFalse("the returns went with the mode change", host.cameraReturnPending)
        assertFalse("Back has no camera step", host.stepBack())
        assertTrue("the camera was not moved", camera.restores.isEmpty())
    }

    @Test
    fun `setting the mode it already has keeps the camera returns`() {
        zoomedAndFanned(handler())

        host.mode = CrowdedIconsMode.ZOOM_THEN_FAN

        assertTrue(host.cameraReturnPending)
    }

    // ── Q1: Locate's press counts as the user's pan (RECORD -1757) ──

    @Test
    fun `Locate pressed with no fix yet ends the way back, and the follow that comes later leaves nothing to undo`() {
        // Step path: tap crowded > zoom > Locate (no fix yet, nothing moves) > the fix arrives, the map goes to you > Back.
        addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        val h = handler()
        tapOn(h, a.lat, a.lng)
        assertNotNull("positive control: the zoom listed the stack", host.list)
        assertTrue("positive control: Back has a camera return", host.cameraReturnPending)
        classifier.onCameraIdle()
        looper.runAll()

        // MapLocationDot.locate: the press. With no fix, the location component moves nothing.
        classifier.markUserAskedMove()
        host.onLocatePressed()
        looper.runAll()
        // The fix arrives: the follower's own move.
        h.onCameraMoveStarted(classifier.classify(isGesture = false, followingLocation = true))

        assertNull("the list closed at the press", host.list)
        assertFalse("Back has no camera step: the map is the user's", host.cameraReturnPending)
        assertFalse(host.stepBack())
        assertTrue("no jump back", camera.restores.isEmpty())
    }

    // ── Q2: starting navigation ends the camera returns (RECORD -1757, -1758) ──

    @Test
    fun `navigation starting with no camera move ends the way back, so Back restores nothing over the navigation view`() {
        // Step path: tap crowded > zoom > Navigate (the camera does not move) > Back.
        zoomedAndFanned(handler())
        fan.fold()
        fan.release()

        // MapNavigationView's effect, at its top: the view request arrives; no camera move follows.
        host.onNavigationViewRequested()
        camera.available = false

        assertFalse("Back has no camera step while navigating", host.cameraReturnPending)
        assertFalse(host.stepBack())
        assertTrue("nothing restored over the navigation view", camera.restores.isEmpty())
    }

    @Test
    fun `navigation starting leaves a List-mode list open`() {
        host.mode = CrowdedIconsMode.LIST
        addAt(MapLayerIds.PHOTOS, "p", 3, a.lat, a.lng)
        tapOn(handler(), a.lat, a.lng)

        host.onNavigationViewRequested()

        assertNotNull("the list stays: List mode lists while navigating", host.list)
    }
}
