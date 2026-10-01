package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FannedFrom
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules for what the Maps tab is given back after Back from a find opened on the map (dispatch 2026-09-29-57,
 * item 8, amendments -256 and -262): remembered on "Open in Journal", used once when that find is closed, forgotten
 * by any other way of leaving it. The wiring to the screen's real Back is `AvailabilityScreenReturnToMapTest`.
 */
class MapReturnMemoryTest {

    private val warnings = mutableListOf<String>()
    private val memory = MapReturnMemory(warn = { warnings += it })
    private val anchor = Offset(200f, 300f)
    private val findMarkers = listOf(RecordPoint("find-1", LatLng(45.51, -122.61)), RecordPoint("find-2", LatLng(45.6, -122.7)))
    private val keys = listOf(FanKey(MapLayerIds.FINDS, "find-1"), FanKey(MapLayerIds.PHOTOS, "ph-1"))

    @Test
    fun `closing the remembered find hands the bubble and the fan keys to the next Maps tab, once`() {
        memory.openFanKeys = keys
        memory.remember("find-1", anchor, 30f)

        assertTrue(memory.onFindClosed("find-1"))

        assertEquals(keys, memory.pendingFanKeys)
        val bubble = memory.takeBubble(findMarkers)
        assertNotNull(bubble)
        assertEquals(MapBubbleTarget.FeatureTarget(MapBubbleKind.FIND, MapLayerIds.FINDS, "find-1", LatLng(45.51, -122.61)), bubble!!.target)
        assertEquals(anchor, bubble.anchorPx)
        assertEquals(30f, bubble.bearingDeg, 0f)
        assertEquals(keys, memory.takeFanKeys())
        assertNull("the bubble is used once", memory.takeBubble(findMarkers))
        assertNull("the fan is used once", memory.takeFanKeys())
    }

    @Test
    fun `with no fan open only the bubble comes back`() {
        memory.openFanKeys = emptyList()
        memory.remember("find-1", anchor, 0f)
        assertTrue(memory.onFindClosed("find-1"))
        assertNull(memory.pendingFanKeys)
        assertNotNull(memory.takeBubble(findMarkers))
        assertNull(memory.takeFanKeys())
    }

    @Test
    fun `the request is taken when Open in Journal was tapped, not when Back is`() {
        memory.openFanKeys = keys
        memory.remember("find-1", anchor, 0f)
        memory.openFanKeys = emptyList() // the map that was showing the fan is gone; the new map writes an empty list

        assertTrue(memory.onFindClosed("find-1"))
        assertEquals(keys, memory.takeFanKeys())
    }

    @Test
    fun `a forgotten request returns nothing`() {
        memory.remember("find-1", anchor, 0f)
        memory.forget()
        assertFalse(memory.onFindClosed("find-1"))
        assertNull(memory.takeBubble(findMarkers))
    }

    @Test
    fun `closing a different find returns nothing and forgets the request`() {
        memory.remember("find-1", anchor, 0f)
        assertFalse(memory.onFindClosed("find-2"))
        assertFalse("the request went with the other record", memory.onFindClosed("find-1"))
    }

    @Test
    fun `a find that is no longer drawn has no bubble`() {
        memory.remember("find-1", anchor, 0f)
        memory.onFindClosed("find-1")
        assertNull(memory.takeBubble(findMarkers.filterNot { it.recordId == "find-1" }))
        assertEquals("the failed reopen is reported, not silent", 1, warnings.size)
        assertTrue(warnings.single(), "find-1" in warnings.single())
    }

    @Test
    fun `a deleted find returns to the map with no bubble and a fan without it`() {
        memory.openFanKeys = keys
        memory.remember("find-1", anchor, 0f)

        assertTrue(memory.onFindDeleted("find-1"))

        assertNull("no bubble for a deleted find, even if its marker is still in the list", memory.takeBubble(findMarkers))
        assertEquals(listOf(FanKey(MapLayerIds.PHOTOS, "ph-1")), memory.takeFanKeys())
    }

    @Test
    fun `leaving the Maps tab without using the restore drops it`() {
        memory.openFanKeys = keys
        memory.remember("find-1", anchor, 0f)
        memory.onFindClosed("find-1")
        memory.clearRestore()
        assertNull(memory.takeFanKeys())
        assertNull(memory.takeBubble(findMarkers))
    }

    // A find picked from a fan (dispatch 2026-09-28-381): the fan folded on that tap, so the open fan's keys are empty by the time "Open in Journal" is tapped.

    private val find1 = FanKey(MapLayerIds.FINDS, "find-1")

    @Test
    fun `a find picked from a fan brings the same fan back, though the fan had folded by Open in Journal`() {
        memory.fannedFrom = FannedFrom(find1, keys)
        memory.openFanKeys = emptyList() // the fan folded on the tap that showed the bubble
        memory.remember("find-1", anchor, 0f)

        assertTrue(memory.onFindClosed("find-1"))

        assertEquals(keys, memory.pendingFanKeys)
        assertEquals(keys, memory.takeFanKeys())
    }

    @Test
    fun `a deleted find from a fan returns the fan without it`() {
        memory.fannedFrom = FannedFrom(find1, keys)
        memory.remember("find-1", anchor, 0f)

        assertTrue(memory.onFindDeleted("find-1"))

        assertEquals("one member is left, which the map will not fan", listOf(FanKey(MapLayerIds.PHOTOS, "ph-1")), memory.pendingFanKeys)
    }

    @Test
    fun `a find that was not picked from a fan returns no fan`() {
        memory.fannedFrom = null
        memory.openFanKeys = emptyList()
        memory.remember("find-1", anchor, 0f)

        assertTrue(memory.onFindClosed("find-1"))

        assertNull(memory.pendingFanKeys)
        assertNull(memory.takeFanKeys())
    }

    @Test
    fun `a fan picked from for a different find is not used for this one`() {
        memory.fannedFrom = FannedFrom(FanKey(MapLayerIds.FINDS, "find-9"), keys)
        memory.openFanKeys = emptyList()
        memory.remember("find-1", anchor, 0f)

        assertTrue(memory.onFindClosed("find-1"))

        assertNull(memory.pendingFanKeys)
    }

    @Test
    fun `closing the bubble without opening the page leaves nothing remembered`() {
        memory.fannedFrom = FannedFrom(find1, keys) // the bubble was shown from a fan, then closed; remember() was never called

        assertFalse("nothing was remembered, so closing a find's page is nothing to return from", memory.onFindClosed("find-1"))
        assertNull(memory.pendingFanKeys)
    }

    @Test
    fun `the fan picked from is used once, and the next round reads the open fan`() {
        memory.fannedFrom = FannedFrom(find1, keys)
        memory.remember("find-1", anchor, 0f)
        assertTrue(memory.onFindClosed("find-1"))
        assertNull("used up", memory.fannedFrom)

        val reopened = listOf(FanKey(MapLayerIds.FINDS, "find-1"), FanKey(MapLayerIds.WAYPOINTS, "w-1"))
        memory.openFanKeys = reopened // the returned fan is open again
        memory.remember("find-1", anchor, 0f)
        assertTrue(memory.onFindClosed("find-1"))

        assertEquals(reopened, memory.pendingFanKeys)
    }

    @Test
    fun `forgetting the origin forgets the fan picked from`() {
        memory.fannedFrom = FannedFrom(find1, keys)
        memory.remember("find-1", anchor, 0f)

        memory.forget()

        assertNull(memory.fannedFrom)
        assertFalse(memory.onFindClosed("find-1"))
    }
}
