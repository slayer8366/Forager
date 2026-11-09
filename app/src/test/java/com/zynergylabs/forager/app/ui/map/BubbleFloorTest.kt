package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * "Kept above the card" (RECORD -2187; BubbleFloor.kt): the route card's top as the bottom of the box a bubble is clamped into, where
 * the two meet sideways. The screen-level checks are RouteCardBubbleAboveCardTest; these pin the placement's two edges of it.
 */
class BubbleFloorTest {

    private fun place(anchor: Offset, floor: BubbleFloor?, height: Int = 250, minY: Int = 0) = bubblePlacement(
        anchor = anchor,
        arrowAngleDeg = 135f,
        bubbleWidth = 300,
        bubbleHeight = height,
        tailPx = 24f,
        maxWidth = 1000,
        maxHeight = 2000,
        minY = minY,
        floor = floor,
    )

    private fun assertTipOnAnchor(anchor: Offset, p: BubblePlacement) {
        val tip = Offset(p.topLeftX + p.tipInBubble.x, p.topLeftY + p.tipInBubble.y)
        assertTrue("the tip $tip lands on the anchor $anchor", abs(tip.x - anchor.x) < 0.5f && abs(tip.y - anchor.y) < 0.5f)
    }

    @Test
    fun `an anchor under the card puts the bubble at the card's top with its tail on to the anchor`() {
        val anchor = Offset(600f, 1500f)
        val p = place(anchor, BubbleFloor(top = 1400, left = 0, right = 1000), height = 150)
        assertEquals("the bubble's bottom is the card's top", 1400, p.topLeftY + 150)
        assertTipOnAnchor(anchor, p)
    }

    @Test
    fun `a bubble that would flip below onto the card does not, and stays above the card's top`() {
        val anchor = Offset(600f, 100f)
        assertTrue("precondition: with no card it flips below, past y 300", place(anchor, null).topLeftY + 250 > 300)
        val p = place(anchor, BubbleFloor(top = 300, left = 0, right = 1000))
        assertTrue("its bottom ${p.topLeftY + 250} is at or above the card's top 300", p.topLeftY + 250 <= 300)
    }

    @Test
    fun `a card beside the bubble, meeting none of its columns, does not limit it`() {
        val anchor = Offset(600f, 1500f)
        val free = place(anchor, null, height = 150)
        val beside = place(anchor, BubbleFloor(top = 1400, left = free.topLeftX + 300, right = 1000), height = 150)
        assertEquals("the same place as with no card", free, beside)
    }
    @Test
    fun `with no room between the strip and the card the bubble is placed as with no card, and over it`() {
        // The owner (RECORD -2187): "Bubble over the card (Recommended)". The S22 in landscape, card expanded: strip limit 52, card top 148, bubble 128.
        val anchor = Offset(600f, 200f)
        val free = place(anchor, null, height = 128, minY = 52)
        val over = place(anchor, BubbleFloor(top = 148, left = 0, right = 1000), height = 128, minY = 52)
        assertEquals("the same place as with no card", free.copy(overFloor = true), over)
        assertTrue("reported over the card", over.overFloor)
        assertTrue("precondition: with no card it is not over one", !free.overFloor)
    }

    @Test
    fun `with room above the card the bubble is not over it`() {
        val p = place(Offset(600f, 1500f), BubbleFloor(top = 1400, left = 0, right = 1000), height = 150, minY = 52)
        assertTrue("kept above, not over", !p.overFloor)
    }
}
