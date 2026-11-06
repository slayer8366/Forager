package com.zynergylabs.forager.app.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The find-over-the-Journal state (M1, continuation `2026-09-28-30`): a find opened from a map bubble
 * shows once the ViewModel has opened it, stays while a find is open, and goes when none is, leaving
 * the view under it as it was.
 */
class FindOverViewTest {

    private val waiting = FindOverView("find-1")

    @Test
    fun `waiting, it shows once the asked-for find is open, and not for another find`() {
        assertEquals(waiting, nextFindOverView(waiting, openFindId = null))
        assertEquals(FindOverView("find-1", shown = true), nextFindOverView(waiting, openFindId = "find-1"))
        // Another find being open does not show it.
        assertEquals(waiting, nextFindOverView(waiting, openFindId = "find-0"))
    }

    /** RECORD -876: a find opened straight into its form opens as its draft row; that draft of the asked-for find shows it. */
    @Test
    fun `waiting, it shows once a draft of the asked-for find is open, and not for a draft of another`() {
        assertEquals(FindOverView("find-1", shown = true), nextFindOverView(waiting, openFindId = "draft-x", openFindDraftOf = "find-1"))
        assertEquals(waiting, nextFindOverView(waiting, openFindId = "draft-y", openFindDraftOf = "find-0"))
    }

    @Test
    fun `shown, it stays while a find is open, including the draft an edit swaps in, and goes when none is`() {
        val shown = FindOverView("find-1", shown = true)
        assertEquals(shown, nextFindOverView(shown, openFindId = "find-1"))
        assertEquals(shown, nextFindOverView(shown, openFindId = "draft-of-find-1"))
        assertNull(nextFindOverView(shown, openFindId = null))
        // Nothing asked for stays nothing.
        assertNull(nextFindOverView(null, openFindId = "find-1"))
    }
}
