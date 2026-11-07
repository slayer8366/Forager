package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Dispatch 2026-11-05-10, Amendment 1, H7, the pure half ([squeezeStripWindows]): the owner's "have them squeeze together to make room",
 * read by the planner as each window giving up its slack toward its own text, the shortfall shared and the gaps tightening too, never
 * below the text. Pinned on round numbers: a dot of 4 and gaps of 8 (at least 4), so a line of three windows has two dots and four gaps.
 */
class CompassStripWindowsSqueezeTest {

    // The heading (widest 150 with the compass words, 60 its widest value, 50 shown), the altitude (106, 82, 65), the grid reference (140).
    private val windows = listOf(StripWindow(150, 60, 50), StripWindow(106, 82, 65), StripWindow(140, 140, 140))
    private fun squeeze(available: Int) = squeezeStripWindows(available, windows, dotPx = 4, gapPx = 8, minGapPx = 4)

    @Test
    fun `with room every window is at its widest and the gaps are whole`() {
        // 396 + 2 × (4 + 16) = 436.
        assertEquals(StripRowLayout(listOf(150, 106, 140), 8), squeeze(436))
        assertEquals("an unbounded line never squeezes", StripRowLayout(listOf(150, 106, 140), 8), squeeze(Int.MAX_VALUE))
    }

    @Test
    fun `first the statuses' room and the gaps give way, in proportion, down to the widest values`() {
        // At the widest values with the gaps at 4: 282 + 2 × (4 + 8) = 306. 436 - 306 = 130 to give; 371 wants 65, half of it.
        val half = squeeze(371)
        assertEquals(listOf(150 - 45, 106 - 12, 140), half.windowsPx)
        assertEquals(6, half.gapPx)
        assertEquals("the line fits", true, half.windowsPx.sum() + 2 * (4 + 2 * half.gapPx) <= 371)
        assertEquals("all the way", StripRowLayout(listOf(60, 82, 140), 4), squeeze(306))
    }

    @Test
    fun `then the windows give the rest toward the values shown, never below them`() {
        // At the values shown: 255 + 24 = 279. 306 - 279 = 27 to give; 293 wants 13.
        val part = squeeze(293)
        assertEquals(4, part.gapPx)
        assertEquals("the heading's 10 and the altitude's 17, about half each", listOf(60 - 5, 82 - 9, 140), part.windowsPx)
        assertEquals("no window below its text", StripRowLayout(listOf(50, 65, 140), 4), squeeze(279))
        assertEquals("shorter still, the texts and nothing less", StripRowLayout(listOf(50, 65, 140), 4), squeeze(200))
    }

    @Test
    fun `a status shown keeps its window at least as wide as itself`() {
        // "Alt unavailable" shown: its text, 106, is wider than the widest value, 82, so the window keeps 106 at the second step.
        val status = listOf(StripWindow(150, 60, 50), StripWindow(106, 82, 106), StripWindow(140, 140, 140))
        assertEquals(StripRowLayout(listOf(60, 106, 140), 4), squeezeStripWindows(330, status, dotPx = 4, gapPx = 8, minGapPx = 4))
    }

    @Test
    fun `one window alone squeezes toward its text`() {
        assertEquals(StripRowLayout(listOf(140), 8), squeezeStripWindows(200, listOf(StripWindow(140, 140, 120)), dotPx = 4, gapPx = 8, minGapPx = 4))
        // Squeezed, the window gives up half its 20 toward its text; the gap, with no dot to sit beside, is moot.
        assertEquals(listOf(130), squeezeStripWindows(130, listOf(StripWindow(140, 140, 120)), dotPx = 4, gapPx = 8, minGapPx = 4).windowsPx)
    }
}
