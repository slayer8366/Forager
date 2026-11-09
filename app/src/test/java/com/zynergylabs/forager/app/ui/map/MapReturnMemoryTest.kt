package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.ui.availability.BUBBLE_FIND
import com.zynergylabs.forager.app.ui.availability.BUBBLE_PHOTO
import com.zynergylabs.forager.app.ui.availability.BUBBLE_REGION
import com.zynergylabs.forager.app.ui.availability.BUBBLE_TRACK
import com.zynergylabs.forager.app.ui.availability.BUBBLE_WAYPOINT
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
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

    // The way back from a journal entry opened from a bubble's "kept in" line (dispatch 2026-09-28-387, Part B).

    private fun bubbleOf(kind: MapBubbleKind, layerId: String, featureId: String, at: LatLng = LatLng(45.0, -122.0)) =
        TappedMapThing(MapBubbleTarget.FeatureTarget(kind, layerId, featureId, at), anchor, 30f)

    private val findBubble = bubbleOf(MapBubbleKind.FIND, MapLayerIds.FINDS, BUBBLE_FIND.id)
    private val photoBubble = bubbleOf(MapBubbleKind.PHOTO, MapLayerIds.PHOTOS, BUBBLE_PHOTO.photo.id)
    private val waypointBubble = bubbleOf(MapBubbleKind.WAYPOINT, MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id)
    private val trackBubble = bubbleOf(MapBubbleKind.TRACK, MapLayerIds.KEPT_TRACKS, BUBBLE_TRACK.id)
    private val regionBubble = bubbleOf(MapBubbleKind.OFFLINE_REGION, MapLayerIds.OFFLINE_REGION_FILL, BUBBLE_REGION.id.toString())
    private val sources = MapRecordSources(
        finds = listOf(BUBBLE_FIND), galleryPhotos = listOf(BUBBLE_PHOTO), waypoints = listOf(BUBBLE_WAYPOINT),
        tracks = listOf(BUBBLE_TRACK), offlineRegions = listOf(BUBBLE_REGION),
    )

    @Test
    fun `closing the remembered entry from its report hands back its bubble, for every kind that carries the line`() {
        for (bubble in listOf(findBubble, photoBubble, waypointBubble, trackBubble, regionBubble)) {
            val m = MapReturnMemory(warn = { warnings += it })
            m.rememberEntryOpen("entry-a", bubble)

            assertTrue("${bubble.target}", m.onEntryClosed("entry-a", fromReport = true))

            assertEquals("${bubble.target}", bubble, m.takeEntryBubble(sources))
            assertNull("used once", m.takeEntryBubble(sources))
        }
    }

    @Test
    fun `the open fan comes back with the entry's bubble`() {
        memory.openFanKeys = keys
        memory.rememberEntryOpen("entry-a", findBubble)

        assertTrue(memory.onEntryClosed("entry-a", fromReport = true))

        assertEquals(keys, memory.pendingFanKeys)
        assertEquals(keys, memory.takeFanKeys())
    }

    @Test
    fun `with no fan open, only the entry's bubble comes back`() {
        memory.openFanKeys = emptyList()
        memory.rememberEntryOpen("entry-a", waypointBubble)

        assertTrue(memory.onEntryClosed("entry-a", fromReport = true))

        assertNull(memory.pendingFanKeys)
        assertEquals(waypointBubble, memory.takeEntryBubble(sources))
    }

    @Test
    fun `an entry left from its editor returns nothing and forgets the request`() {
        memory.rememberEntryOpen("entry-a", findBubble)

        assertFalse(memory.onEntryClosed("entry-a", fromReport = false))

        assertNull(memory.takeEntryBubble(sources))
        assertFalse("and the request is gone", memory.onEntryClosed("entry-a", fromReport = true))
    }

    @Test
    fun `another entry closing leaves the request alone, because the Journal swaps the open entry when a kept-in line is tapped over it`() {
        memory.rememberEntryOpen("entry-a", findBubble)

        assertFalse("a different entry closing is not the remembered one", memory.onEntryClosed("entry-b", fromReport = true))

        assertTrue("and the request is still there for entry-a", memory.onEntryClosed("entry-a", fromReport = true))
    }

    @Test
    fun `a find closing does not clear the entry request, so entry, Open find, Back, Back still reaches the map`() {
        memory.rememberEntryOpen("entry-a", findBubble)

        assertFalse("a find's page closing is not the entry", memory.onFindClosed("find-9"))

        assertTrue(memory.onEntryClosed("entry-a", fromReport = true))
        assertEquals(findBubble, memory.takeEntryBubble(sources))
    }

    @Test
    fun `an entry request does not disturb a pending find request, and the reverse`() {
        memory.rememberEntryOpen("entry-a", findBubble)
        memory.forget()

        assertFalse("forgetting forgets the entry request too", memory.onEntryClosed("entry-a", fromReport = true))
    }

    @Test
    fun `a record that is gone brings back no bubble, and says so`() {
        memory.rememberEntryOpen("entry-a", waypointBubble)
        assertTrue(memory.onEntryClosed("entry-a", fromReport = true))

        assertNull(memory.takeEntryBubble(MapRecordSources(waypoints = emptyList())))

        assertEquals("one line in the log", 1, warnings.size)
    }

    @Test
    fun `leaving the Maps tab without using the entry's restore drops it`() {
        memory.rememberEntryOpen("entry-a", findBubble)
        assertTrue(memory.onEntryClosed("entry-a", fromReport = true))

        memory.clearRestore()

        assertNull(memory.takeEntryBubble(sources))
    }
}
