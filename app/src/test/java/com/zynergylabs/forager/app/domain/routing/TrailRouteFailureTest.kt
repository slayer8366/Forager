package com.zynergylabs.forager.app.domain.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sorrel's S-412R-1 (dispatch 2026-10-26-11, the owner: "safer design in PR 3"): an end in a square with no route file
 * is told apart from one far from a trail by equality, and `copy` and `toString` carry the mark, so no later copy, log
 * or comparison can lose it silently.
 */
class TrailRouteFailureTest {

    @Test
    fun `a square with no route file is not equal to an end far from a trail`() {
        assertNotEquals(TrailRouteFailure.EndOutsideData(RouteEnd.END), TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true))
        assertEquals(TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true), TrailRouteFailure.EndOutsideData(RouteEnd.END, squareHasNoFile = true))
        assertEquals("unmarked by default", false, TrailRouteFailure.EndOutsideData(RouteEnd.START).squareHasNoFile)
    }

    @Test
    fun `copy keeps the mark and toString shows it`() {
        val marked = TrailRouteFailure.EndOutsideData(RouteEnd.START, squareHasNoFile = true)
        assertTrue("copy keeps it", marked.copy(end = RouteEnd.END).squareHasNoFile)
        assertTrue("toString shows it: $marked", "squareHasNoFile=true" in marked.toString())
    }
}
