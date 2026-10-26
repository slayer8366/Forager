package com.zynergylabs.forager.app.domain.wayto

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Step 6 PR 3 (dispatch 2026-10-26-11), the step path's step 5: more than 50 m off the line re-routes once per stray, and
 * the next stray counts only after a fix back within 45 m (the way home's figures and rule, RECORD -1613, -1625).
 */
class WayToStrayTest {

    @Test
    fun `51 m from the line re-routes, 49 m does not`() {
        assertEquals(StrayVerdict.STAY, WayToStray().judge(49.0, searching = false))
        assertEquals(StrayVerdict.STAY, WayToStray().judge(50.0, searching = false))
        assertEquals(StrayVerdict.REROUTE, WayToStray().judge(51.0, searching = false))
    }

    @Test
    fun `once per stray, until a fix back within 45 m re-arms it`() {
        val stray = WayToStray()
        assertEquals(StrayVerdict.REROUTE, stray.judge(51.0, searching = false))
        assertEquals("the same stray: spent", StrayVerdict.ALREADY_REROUTED, stray.judge(80.0, searching = false))
        assertEquals("49 m does not re-arm", StrayVerdict.STAY, stray.judge(49.0, searching = false))
        assertEquals(StrayVerdict.ALREADY_REROUTED, stray.judge(51.0, searching = false))
        assertEquals("45 m is not within 45 m, as on the way home", StrayVerdict.STAY, stray.judge(45.0, searching = false))
        assertEquals(StrayVerdict.ALREADY_REROUTED, stray.judge(51.0, searching = false))
        assertEquals("under 45 m re-arms", StrayVerdict.STAY, stray.judge(44.9, searching = false))
        assertEquals("the next stray re-routes", StrayVerdict.REROUTE, stray.judge(51.0, searching = false))
    }

    @Test
    fun `a stray while a route is already being worked out starts nothing and does not spend the stray`() {
        val stray = WayToStray()
        assertEquals(StrayVerdict.SEARCHING, stray.judge(60.0, searching = true))
        assertEquals("still armed once the search ends", StrayVerdict.REROUTE, stray.judge(60.0, searching = false))
    }
}
