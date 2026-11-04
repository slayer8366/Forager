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
 * Dispatch 2026-11-04-02, A6 and S2 (RECORD -2039, -2040), through the real entry point, a tap into [MapTapHandler], over the
 * scene and camera [CrowdedStackZoomTest] uses ([FanOutTestScene], [SceneStackCamera]).
 *
 * A6, the owner: "have the zoom-in and zoom-out animate rather than jump in also so it looks smooth"; S5: 750 ms, with the
 * verify-first report's approach (i): the zoom's steps run as before, at once, then the camera goes back to where it was and
 * eases to where the steps put it ([StackCamera.showMoveFrom]), and the fan or the list opens when it lands. Ends exactly where
 * the jump would: checked against the same tap under Reduce Motion, which jumps as before. A touch during the move takes over.
 * The ease itself is MapLibre's (`MapStackCamera`), device-only; what is held here is what the zoom asks of the camera and
 * when it opens what.
 *
 * S2: a pan or zoom by the user's finger keeps the list and its bubble follows the stack ([CrowdedStackHost.followList]); a move
 * the user asked for (Locate, a search result, Return to Route) still closes it.
 */
class CrowdedStackEaseTest {

    private val warnings = mutableListOf<String>()

    /** One map: its scene, fan, classifier, camera and host, as [CrowdedStackZoomTest]'s fields. */
    private inner class Rig(reduceMotion: Boolean = false) {
        val scene = FanOutTestScene()
        val fan = MarkerFanOutState()
        val looper = FakeLooper()
        val classifier = CameraMoveClassifier(looper::post)
        val camera = SceneStackCamera(scene, 300f, 600f, classifier)
        val host = CrowdedStackHost(warn = { warnings += it })
        private val space = object : FanSpace {
            override fun boundsPx() = BOUNDS
            override fun keepOutsPx(): List<FanRect> = emptyList()
        }
        val handler = MapTapHandler(
            fan, scene, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, RecordingSinks(), space,
            warn = { warnings += it },
            crowded = CrowdedStackZoom(camera, { host }, { warnings += it }, reduceMotion = { reduceMotion }),
        ).also { scene.hidden = { fan.members.map { it.key }.toSet() } }

        fun tap(at: LatLng) = handler.onMapTap(at, scene.xPx(at.lng), scene.yPx(at.lat))

        fun addAt(layer: String, prefix: String, count: Int, lat: Double, lng: Double) = repeat(count) { scene.add(layer, "$prefix$it", lat, lng) }

        /** 20 photos at [a] and 10 finds just east: one stack of 30 whose fan does not fit, two apart once zoomed ([CrowdedStackZoomTest]). */
        fun thirtyThatSplitInTwo() {
            addAt(MapLayerIds.PHOTOS, "p", 20, a.lat, a.lng)
            addAt(MapLayerIds.FINDS, "f", 10, a.lat, a.lng + 0.012)
        }
    }

    private val a = LatLng(45.0, -122.0)

    /** Where the same tap leaves the camera when it jumps, as before A6: under Reduce Motion. */
    private fun jumpEnd(arrange: Rig.() -> Unit): CameraSpot {
        val jumped = Rig(reduceMotion = true)
        jumped.arrange()
        jumped.tap(a)
        assertTrue("positive control: the jump moved no ease", jumped.camera.eases.isEmpty())
        return checkNotNull(jumped.camera.spot())
    }

    @Test
    fun `the zoom eases from where the camera was to exactly where the jump would end, and the fan opens as it lands`() {
        val expected = jumpEnd { thirtyThatSplitInTwo() }
        val m = Rig()
        m.thirtyThatSplitInTwo()
        m.camera.holdEases = true
        val before = checkNotNull(m.camera.spot())

        m.tap(a)

        val ease = m.camera.eases.singleOrNull()
        assertNotNull("the zoom showed its move (eases: ${m.camera.eases})", ease)
        assertEquals("from where the camera was", before, ease!!.from)
        assertEquals("to exactly where the jump would end", expected, ease.to)
        assertTrue("positive control: a zoom in", ease.to.zoom > before.zoom)
        assertFalse("the fan waits for the camera to land", m.fan.isOpen)
        assertEquals("the camera starts from where it was", before.zoom, m.scene.zoom, 1e-9)

        m.camera.finishEase()

        assertTrue("the fan opened as the camera landed", m.fan.isOpen)
        assertEquals((0 until 20).map { FanKey(MapLayerIds.PHOTOS, "p$it") }.toSet(), m.fan.members.map { it.key }.toSet())
        assertEquals(expected, m.camera.spot())
        assertTrue("Back has the camera from before the tap", m.host.cameraReturnPending)
    }

    @Test
    fun `still one spot at the closest zoom, the list opens when the camera lands`() {
        val m = Rig()
        m.addAt(MapLayerIds.FINDS, "f", 30, a.lat, a.lng)
        m.camera.holdEases = true

        m.tap(a)

        assertEquals("one move shown", 1, m.camera.eases.size)
        assertNull("the list waits for the camera to land", m.host.list)
        m.camera.finishEase()
        assertEquals("the list opened as it landed", 30, checkNotNull(m.host.list).keys.size)
    }

    @Test
    fun `a zoom that splits the stack shows its move too, and opens nothing`() {
        val m = Rig()
        for (row in 0 until 5) for (col in 0 until 6) m.scene.add(MapLayerIds.FINDS, "g$row-$col", a.lat + row * 0.0006, a.lng + col * 0.0006)
        m.camera.holdEases = true

        m.tap(a)
        assertEquals("one move shown", 1, m.camera.eases.size)
        m.camera.finishEase()

        assertFalse(m.fan.isOpen)
        assertNull(m.host.list)
        assertTrue(m.host.cameraReturnPending)
    }

    @Test
    fun `a touch during the move takes over, nothing opens and Back no longer moves the camera`() {
        val m = Rig()
        m.thirtyThatSplitInTwo()
        m.camera.holdEases = true
        m.tap(a)
        assertTrue("positive control: the move is under way", m.camera.easing)

        // The finger lands: MapLibre reports a gesture's move start, then cancels the ease.
        m.handler.onCameraMoveStarted(m.classifier.classify(isGesture = true, followingLocation = false))
        m.camera.takeOver()

        assertFalse("no fan", m.fan.isOpen)
        assertNull("no list", m.host.list)
        assertFalse("the map is the user's: no camera return", m.host.cameraReturnPending)
        assertTrue("said so: $warnings", warnings.any { it.contains("taken over") })
    }

    @Test
    fun `under Reduce Motion the zoom jumps, as before, and the fan opens at once`() {
        val m = Rig(reduceMotion = true)
        m.thirtyThatSplitInTwo()
        m.camera.holdEases = true

        m.tap(a)

        assertTrue("no move shown", m.camera.eases.isEmpty())
        assertTrue("the fan opened at once", m.fan.isOpen)
    }

    @Test
    fun `Back's camera step eases the camera back, or jumps when asked not to animate`() {
        val m = Rig()
        m.thirtyThatSplitInTwo()
        m.tap(a)
        m.fan.fold()
        m.fan.release()

        assertTrue(m.host.stepBack(animate = true))
        assertEquals(listOf(true), m.camera.restoresAnimated)

        m.tap(a)
        m.fan.fold()
        m.fan.release()
        assertTrue(m.host.stepBack(animate = false))
        assertEquals(listOf(true, false), m.camera.restoresAnimated)
    }

    @Test
    fun `a pan keeps the list open and its bubble follows the stack, and a move the user asked for still closes it`() {
        val m = Rig()
        m.host.mode = CrowdedIconsMode.LIST
        m.addAt(MapLayerIds.FINDS, "f", 3, a.lat, a.lng)
        m.tap(a)
        val listed = checkNotNull(m.host.list) { "positive control: the list opened" }

        // The user's finger pans the map 40 by 60 px; at the idle the list follows the stack.
        m.scene.panXPx += 40f
        m.scene.panYPx += 60f
        m.handler.onCameraMoveStarted(m.classifier.classify(isGesture = true, followingLocation = false))
        m.classifier.onCameraIdle()
        m.looper.runAll()
        assertNotNull("a pan keeps the list (S2)", m.host.list)
        m.host.followList({ at -> (m.scene.xPx(at.lng) + m.scene.panXPx) to (m.scene.yPx(at.lat) + m.scene.panYPx) }, bearingDeg = 30f)

        val followed = checkNotNull(m.host.list)
        assertEquals("the anchor moved with the pan", listed.anchorXPx + 40f, followed.anchorXPx, 0.01f)
        assertEquals(listed.anchorYPx + 60f, followed.anchorYPx, 0.01f)
        assertEquals("and turns with the map", 30f, followed.bearingDeg, 0f)
        assertEquals("the same records", listed.keys, followed.keys)

        m.classifier.markUserAskedMove()
        m.handler.onCameraMoveStarted(m.classifier.classify(isGesture = false, followingLocation = false))
        assertNull("Locate, a search result or Return to Route closes it", m.host.list)
    }

    @Test
    fun `a list whose stack cannot be placed stays where it was, and says so`() {
        val m = Rig()
        m.host.mode = CrowdedIconsMode.LIST
        m.addAt(MapLayerIds.FINDS, "f", 3, a.lat, a.lng)
        m.tap(a)
        val listed = checkNotNull(m.host.list)

        m.host.followList({ null }, bearingDeg = 0f)

        assertEquals(listed, m.host.list)
        assertTrue("said so: $warnings", warnings.any { it.contains("could not be placed") })
    }

    private companion object {
        /** 300 x 600 dp at density 2, centred on the scene's viewport centre (500, 1000 px), as [CrowdedStackZoomTest]'s. */
        val BOUNDS = FanRect(200f, 400f, 800f, 1600f)
    }
}
