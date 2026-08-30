package com.forager.app.ui.availability

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The point where a ray from a rectangle's own center exits its boundary — the one piece of
 * geometry [AnchoredAtScreenPoint] and [ObservationBubble] both need to agree on for the bubble's
 * arrow tip to actually land on the tapped dot; see [rectEdgeIntersection]'s own doc comment.
 *
 * A plain JVM test with no Compose UI in it: this is pure trigonometry over [Offset], the same
 * "test the geometry directly, no rendering needed" shape [RelativeTimeLabelTest] already uses for
 * a different pure function in this package. Expected values are independently computed (not by
 * re-deriving the same formula) — a Python trig check kept alongside this file's own history —
 * rather than asserted against whatever the function under test happens to already return.
 */
class RectEdgeIntersectionTest {

    private val delta = 0.001f

    @Test
    fun `straight up exits through the top edge, at the rectangle's own horizontal center`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 0f)
        assertEquals(0f, point.x, delta)
        assertEquals(-20f, point.y, delta)
    }

    @Test
    fun `straight right exits through the right edge, at the rectangle's own vertical center`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 90f)
        assertEquals(100f, point.x, delta)
        assertEquals(0f, point.y, delta)
    }

    @Test
    fun `straight down exits through the bottom edge`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 180f)
        assertEquals(0f, point.x, delta)
        assertEquals(20f, point.y, delta)
    }

    @Test
    fun `straight left exits through the left edge`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 270f)
        assertEquals(-100f, point.x, delta)
        assertEquals(0f, point.y, delta)
    }

    /**
     * A wide, short rectangle (halfWidth 100, halfHeight 20): a shallow-from-vertical ray (10°,
     * mostly "up") is close enough to the top edge to hit it before either side, but a shallow-
     * from-horizontal one (80°, mostly "right") reaches the right edge first — the two branches
     * [rectEdgeIntersection] has to choose between, both exercised against the same rectangle so
     * only the angle differs.
     */
    @Test
    fun `a steep angle on a wide rectangle exits through the top edge, not a side`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 10f)
        assertEquals(3.5265f, point.x, delta)
        assertEquals(-20f, point.y, delta)
    }

    @Test
    fun `a shallow angle on a wide rectangle exits through a side edge, not the top`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 80f)
        assertEquals(100f, point.x, delta)
        assertEquals(-17.6327f, point.y, delta)
    }

    @Test
    fun `a 45 degree ray from a wide rectangle's center still respects its own aspect ratio`() {
        val point = rectEdgeIntersection(halfWidth = 100f, halfHeight = 20f, angleDeg = 45f)
        assertEquals(20f, point.x, delta)
        assertEquals(-20f, point.y, delta)
    }

    /** The returned [Offset] is relative to the rectangle's own center, not an absolute point. */
    @Test
    fun `a square rectangle's own corner sits exactly at the halfway angle between two edges`() {
        val point = rectEdgeIntersection(halfWidth = 50f, halfHeight = 50f, angleDeg = 45f)
        assertEquals(50f, point.x, delta)
        assertEquals(-50f, point.y, delta)
    }
}
