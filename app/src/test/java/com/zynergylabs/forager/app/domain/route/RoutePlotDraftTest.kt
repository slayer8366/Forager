package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** The plotting rules of [RoutePlotDraft] and the default name (dispatch 2026-10-30-08, step 3). */
class RoutePlotDraftTest {

    private val creek = RouteStop("Creek", LatLng(45.2, -122.5))
    private val ridge = RouteStop("Ridge", LatLng(45.22, -122.52))
    private val falls = RouteStop("Falls", LatLng(45.24, -122.55))

    private fun straight(from: RouteStop, to: RouteStop, meters: Double) =
        RouteLeg.Straight(listOf(from.position, to.position), meters, StraightReason.NO_REGION_COVERS)

    @Test
    fun `a stop is added at once, and the leg to route next runs from the last routed stop`() {
        val draft = RoutePlotDraft(listOf(creek)).withStop(ridge)!!.withStop(falls)!!

        assertEquals(listOf(creek, ridge, falls), draft.stops)
        assertEquals(creek to ridge, draft.nextLeg)
        assertEquals(ridge to falls, draft.withLeg(straight(creek, ridge, 100.0)).nextLeg)
    }

    @Test
    fun `the last stop tapped again is ignored, and A to B to A is allowed`() {
        val ab = RoutePlotDraft(listOf(creek)).withStop(ridge)!!

        assertNull(ab.withStop(RouteStop("Ridge copy", ridge.position)))
        assertEquals(listOf(creek, ridge, creek), ab.withStop(creek)!!.stops)
    }

    @Test
    fun `a leg that is not the next one is refused`() {
        val draft = RoutePlotDraft(listOf(creek)).withStop(ridge)!!

        assertThrows(IllegalArgumentException::class.java) { draft.withLeg(straight(ridge, creek, 1.0)) }
        assertThrows(IllegalArgumentException::class.java) { draft.withLeg(straight(creek, ridge, 1.0)).withLeg(straight(ridge, falls, 1.0)) }
    }

    @Test
    fun `undo removes the last stop and its leg`() {
        val two = RoutePlotDraft(listOf(creek)).withStop(ridge)!!.withLeg(straight(creek, ridge, 100.0))
        val three = two.withStop(falls)!!.withLeg(straight(ridge, falls, 50.0))

        assertEquals(two, three.undo())
        assertEquals(RoutePlotDraft(listOf(creek)), two.undo())
    }

    @Test
    fun `undo removes a stop whose leg is still being routed, keeping the routed legs`() {
        val routed = RoutePlotDraft(listOf(creek)).withStop(ridge)!!.withLeg(straight(creek, ridge, 100.0))

        assertEquals(routed, routed.withStop(falls)!!.undo())
    }

    @Test
    fun `the route exists only from two stops with every leg in, and the total counts routed legs only`() {
        val waiting = RoutePlotDraft(listOf(creek)).withStop(ridge)!!
        val done = waiting.withLeg(straight(creek, ridge, 120.5))

        assertNull(RoutePlotDraft(listOf(creek)).toRoute())
        assertNull(waiting.toRoute())
        assertEquals(0.0, waiting.routedMeters, 1e-9)
        assertEquals(PlottedRoute(listOf(creek, ridge), listOf(straight(creek, ridge, 120.5))), done.toRoute())
        assertEquals(120.5, done.withStop(falls)!!.routedMeters, 1e-9)
    }

    @Test
    fun `the default name is first to last, or first and back when the last stop is at the first stop's position`() {
        assertEquals("Creek to Falls", defaultRouteName(listOf(creek, ridge, falls)))
        assertEquals("Creek and back", defaultRouteName(listOf(creek, ridge, RouteStop("Other name", creek.position))))
        // By position, not by name (the planner's ruling on Q10): two places both named Creek.
        assertEquals("Creek to Creek", defaultRouteName(listOf(creek, ridge, RouteStop("Creek", falls.position))))
    }
}
