package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Dispatch 2026-11-06-05, Amendment 1, step 1 (RECORD -2187, the owner: "(b) Card leaves room"): the rule `RouteCardReCenterRoom.kt`
 * decides by, on its own. The screen's claims are `RouteCardReCenterUnderCardTest`'s.
 */
class RouteCardReCenterRoomTest {

    @Test
    fun `the portrait card is flush to its edge at the reference width, capped by the room beside the bar's column`() {
        assertEquals(0f..300f, routeCardPortraitSpanPx(boxWidthPx = 1000f, leftPadPx = 0f, rightPadPx = 0f, barColumnPx = 80f, referenceWidthPx = 300f, onLeft = true))
        assertEquals(700f..1000f, routeCardPortraitSpanPx(1000f, 0f, 0f, 80f, 300f, onLeft = false))
        assertEquals(10f..330f, routeCardPortraitSpanPx(400f, 10f, 0f, 70f, 600f, onLeft = true))
        assertEquals(70f..390f, routeCardPortraitSpanPx(400f, 0f, 10f, 70f, 600f, onLeft = false))
    }

    @Test
    fun `Re-center's own place is bottom centre inside the controls' padding`() {
        assertEquals(150f..250f, reCenterSpanAtBottomCentrePx(boxWidthPx = 400f, leftPadPx = 0f, rightPadPx = 0f, wordWidthPx = 100f))
        assertEquals(170f..270f, reCenterSpanAtBottomCentrePx(400f, 40f, 0f, 100f))
    }

    @Test
    fun `room is left only navigating, in portrait, where Re-center shares the card's columns, or before its word is measured`() {
        val card = 0f..300f
        assertTrue("sharing the column", routeCardLeavesReCenterRoom(isNavigating = true, portrait = true, card = card, reCenter = 150f..250f))
        assertTrue("not yet measured", routeCardLeavesReCenterRoom(true, true, card, null))
        assertFalse("not navigating: Re-center never shows", routeCardLeavesReCenterRoom(false, true, card, 150f..250f))
        assertFalse("landscape: placed by #506's fit test instead", routeCardLeavesReCenterRoom(true, false, card, 150f..250f))
        assertFalse("the other column", routeCardLeavesReCenterRoom(true, true, card, 300f..400f))
    }

    @Test
    fun `the room is Re-center's height, at least the 48 dp target, and an 8 dp gap`() {
        assertEquals(56f, routeCardReCenterRoom(20.dp).value, 0.01f)
        assertEquals(56f, routeCardReCenterRoom(48.dp).value, 0.01f)
        assertEquals(76f, routeCardReCenterRoom(68.dp).value, 0.01f)
    }
}
