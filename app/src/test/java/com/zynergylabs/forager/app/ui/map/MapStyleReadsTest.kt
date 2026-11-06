package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.maps.Style
import org.maplibre.android.maps.StyleLifecycleFixture
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

/**
 * Dispatch 2026-10-25-02 (RECORD -1634, Amendment 1): no map layer reads a style MapLibre has replaced. The style is a
 * real MapLibre [Style], cleared by MapLibre's own `clear()` as `setStyle` clears the old one (#397's fixture).
 *
 * What this can prove: the way-home route's and the overlay data's pushes, the two `MapDataRefresh` effects' style
 * reads, skip a replaced style, logged, instead of throwing; and a locate tap in a style change is remembered and
 * carried out once the new style is current. What it cannot prove: that the effects run in the gap on a phone, that
 * the fan's collectors and the dot's effects take the same path (they read the style inside a composition Robolectric
 * cannot give a real map), or that the remembered locate moves MapLibre's camera. A real MapView cannot be built under
 * Robolectric, so those are the S22 run's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MapStyleReadsTest {

    @Before
    fun clearLog() {
        ShadowLog.clear()
    }

    private fun mapLines() = ShadowLog.getLogsForTag(SIGHTINGS_MAP_TAG).map { it.msg }

    private fun overlay(loaded: Style?) = overlayDataOnCurrentStyle(
        loaded, Region(45.7, -123.2, 5), emptyList(), emptyList(), emptyList(), emptyList(), null,
        emptyList(), emptyList(), emptyList(), emptyList(), showSearchCentre = false, journalHighlights = JournalEntryHighlights.NONE,
    )

    @Test
    fun `the way-home route on a replaced style is skipped, logged, instead of crashing`() {
        val pushed = routeHomeOnCurrentStyle(StyleLifecycleFixture.replacedStyle(), route = null)
        assertFalse(pushed)
        assertEquals(listOf("The way-home route skipped: a newer style is loading. It runs again when that style has loaded."), mapLines())
    }

    @Test
    fun `the overlay data on a replaced style is skipped, logged, instead of crashing`() {
        assertNull(overlay(StyleLifecycleFixture.replacedStyle()))
        assertEquals(listOf("The map's overlay data skipped: a newer style is loading. It runs again when that style has loaded."), mapLines())
    }

    @Test
    fun `a loaded style is read as before, with nothing logged`() {
        val loaded = StyleLifecycleFixture.loadedStyle()
        assertTrue(routeHomeOnCurrentStyle(loaded, route = null))
        assertSame(loaded, overlay(loaded))
        assertSame(loaded, currentStyle(loaded, "A reader"))
        assertNull(currentStyle(null, "A reader"))
        assertEquals(emptyList<String>(), mapLines())
    }

    @Test
    fun `a locate tap in a style change is remembered and carried out once, when the new style is current`() {
        val locate = PendingLocate()
        assertFalse("not now: the style is changing", locate.onTap(styleCurrent = false))
        assertFalse("still changing: not yet", locate.onStyle(styleCurrent = false))
        assertTrue("the new style is current: carried out", locate.onStyle(styleCurrent = true))
        assertFalse("once only", locate.onStyle(styleCurrent = true))
    }

    @Test
    fun `a locate tap on a current style goes at once and leaves nothing remembered`() {
        val locate = PendingLocate()
        assertTrue(locate.onTap(styleCurrent = true))
        assertFalse(locate.onStyle(styleCurrent = true))
    }
}
