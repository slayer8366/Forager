package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * How a stack whose markers are not exactly on one spot is placed, and how the handler uses the space it
 * is given (continuation `-208`): the ring is about the stack's centre, so its markers clear each other
 * whatever their true spots; the shift moves the fan and never the true positions the legs end on.
 */
class MapTapHandlerPlacementTest {

    private val scene = FanOutTestScene()
    private val fan = MarkerFanOutState()
    private val sinks = RecordingSinks()

    private fun handler(space: FanSpace = FanSpace.Unbounded) =
        MapTapHandler(fan, scene, { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) }, sinks, space)
            .also { scene.hidden = { fan.members.map { it.key }.toSet() } }

    private fun at(h: MapTapHandler, xPx: Float, yPx: Float) = h.onMapTap(LatLng(0.0, 0.0), xPx, yPx)

    @Test
    fun `markers a few dp apart fan into places that clear each other, and their true spots stay where they were`() {
        // 0, 12 and 30 dp across: all within a touch size of the first, so one stack.
        scene.addAtScreen(MapLayerIds.PHOTOS, "a", 400f, 800f)
        scene.addAtScreen(MapLayerIds.PHOTOS, "b", 424f, 800f)
        scene.addAtScreen(MapLayerIds.FINDS, "c", 460f, 810f)
        at(handler(), 400f, 800f)
        assertEquals(3, fan.members.size)
        val places = fan.members.map { memberPositionDp(it, 1f) }
        for (i in places.indices) for (j in i + 1 until places.size) {
            val gap = maxOf(abs(places[i].xDp - places[j].xDp), abs(places[i].yDp - places[j].yDp))
            assertTrue("fanned markers $i and $j are $gap dp apart on their wider axis", gap >= FAN_TOUCH_DP - 0.01f)
        }
        val trueSpots = fan.members.associate { it.key.featureId to (it.trueXDp to it.trueYDp) }
        assertEquals(400f / scene.density to 800f / scene.density, trueSpots.getValue("a"))
        assertEquals(424f / scene.density to 800f / scene.density, trueSpots.getValue("b"))
        assertEquals(460f / scene.density to 810f / scene.density, trueSpots.getValue("c"))
    }

    @Test
    fun `the handler shifts the fan by the space it is given - off a control beside the stack, and inside the bounds`() {
        scene.addAtScreen(MapLayerIds.PHOTOS, "a", 20f, 400f)
        scene.addAtScreen(MapLayerIds.PHOTOS, "b", 20f, 400f)
        scene.addAtScreen(MapLayerIds.PHOTOS, "c", 20f, 400f)
        val density = scene.density
        val bounds = FanRect(0f, 0f, 400f * density, 800f * density)
        val control = FanRect(60f * density, 350f * density, 160f * density, 450f * density)
        val space = object : FanSpace {
            override fun boundsPx() = bounds
            override fun keepOutsPx() = listOf(control)
        }
        at(handler(space), 20f, 400f)
        assertTrue(fan.isOpen)
        for (m in fan.members) {
            val p = memberPositionDp(m, 1f)
            val squareLeft = p.xDp - 24f
            val squareRight = p.xDp + 24f
            val squareTop = p.yDp - 24f
            val squareBottom = p.yDp + 24f
            assertTrue("inside the bounds", squareLeft >= 0f - 0.01f && squareTop >= -0.01f && squareRight <= 400f + 0.01f && squareBottom <= 800f + 0.01f)
            val onControl = squareLeft < 160f - 0.01f && 60f + 0.01f < squareRight && squareTop < 450f - 0.01f && 350f + 0.01f < squareBottom
            assertTrue("marker ${m.key.featureId} at $p is off the control", !onControl)
        }
    }
}
