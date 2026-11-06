package com.zynergylabs.forager.app.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The merge behind [rememberListRows] (motion Part 3, item 2; the owner, RECORD -651: Lists "Slide and close up": "Removed rows
 * fade and shrink, the rest glide up, Undo reverses, and new or reordered rows glide into place"): where a removed row stays while
 * it leaves, that Undo turns the same row round, and that nothing is kept when nothing should play.
 *
 * Nothing here has been run: written with the code, before the build.
 */
class ListRowsMergeTest {

    private fun rows(vararg keys: String, animate: Boolean = true, previous: List<ListRow<String>> = emptyList()) =
        mergeListRows(previous = previous, items = keys.toList(), key = { it }, animate = animate)

    private fun List<ListRow<String>>.keys() = map { it.key }
    private fun List<ListRow<String>>.leavingKeys() = filter { it.leaving }.map { it.key }

    @Test
    fun `a removed row stays where it was, leaving, and the rest keep their order`() {
        val first = rows("a", "b", "c", "d", animate = false)
        val after = rows("a", "c", "d", previous = first)
        assertEquals(listOf("a", "b", "c", "d"), after.keys())
        assertEquals(listOf("b"), after.leavingKeys())
    }

    @Test
    fun `a removed first row stays first, and two removed together keep their own order`() {
        val first = rows("a", "b", "c", "d", animate = false)
        assertEquals(listOf("a", "b", "c", "d"), rows("b", "c", "d", previous = first).keys())
        val both = rows("a", "d", previous = first)
        assertEquals(listOf("a", "b", "c", "d"), both.keys())
        assertEquals(listOf("b", "c"), both.leavingKeys())
    }

    @Test
    fun `Undo turns the same row round instead of starting a new one`() {
        val first = rows("a", "b", "c", animate = false)
        val leaving = rows("a", "c", previous = first)
        val leavingState = leaving.single { it.key == "b" }.visible
        assertFalse(leavingState.targetState)

        val back = rows("a", "b", "c", previous = leaving)
        val returned = back.single { it.key == "b" }
        assertSame("the row that was leaving, reversed, not a new one", leavingState, returned.visible)
        assertTrue(returned.visible.targetState)
        assertFalse(returned.leaving)
        assertEquals(listOf("a", "b", "c"), back.keys())
    }

    @Test
    fun `a new row starts hidden and grows in, and a row already there is not restarted`() {
        val first = rows("a", "c", animate = false)
        val added = rows("a", "b", "c", previous = first)
        val b = added.single { it.key == "b" }
        assertFalse("a new row starts hidden", b.visible.currentState)
        assertTrue("and is on its way in", b.visible.targetState)
        assertSame(first.single { it.key == "a" }.visible, added.single { it.key == "a" }.visible)
        assertTrue(added.single { it.key == "a" }.visible.currentState)
    }

    @Test
    fun `without animating, a removed row goes at once and a new one is simply there`() {
        val first = rows("a", "b", "c", animate = false)
        val after = rows("a", "c", "d", animate = false, previous = first)
        assertEquals(listOf("a", "c", "d"), after.keys())
        assertTrue(after.leavingKeys().isEmpty())
        assertTrue("a new row is shown at once", after.single { it.key == "d" }.visible.currentState)
    }

    @Test
    fun `a reordered list keeps each row's own state under its new place`() {
        val first = rows("a", "b", "c", animate = false)
        val reordered = rows("c", "a", "b", previous = first)
        assertEquals(listOf("c", "a", "b"), reordered.keys())
        for (key in listOf("a", "b", "c")) {
            assertSame(first.single { it.key == key }.visible, reordered.single { it.key == key }.visible)
        }
    }
}
