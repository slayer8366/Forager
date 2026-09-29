package com.zynergylabs.forager.app.ui.map.fanout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The fan-out's arithmetic (dispatch 2026-09-28-197; the owner's rules 1 to 3): what is a stack,
 * where the ring and the spiral put each marker. Everything is derived from the 48 dp touch size, so
 * the checks are on the property that matters (no two touch areas overlap), not on hand-copied
 * coordinates. Pure Kotlin: no map, no Compose.
 */
class MarkerFanOutGeometryTest {

    /** Two touch areas are 48 dp squares, so they clear each other only when the centres are 48 dp apart on at least one axis. */
    private fun gap(a: FanOffset, b: FanOffset) = maxOf(abs(a.xDp - b.xDp), abs(a.yDp - b.yDp))

    private fun minPairwise(offsets: List<FanOffset>): Float =
        offsets.indices.flatMap { i -> (i + 1 until offsets.size).map { j -> gap(offsets[i], offsets[j]) } }.min()

    private fun marker(id: String, xPx: Float, yPx: Float) =
        ProbedMarker(FanKey("layer", id), lat = 0.0, lng = 0.0, xPx = xPx, yPx = yPx)

    private fun member(id: String, trueX: Float, trueY: Float, offset: FanOffset) =
        FanMember(FanKey("layer", id), 0.0, 0.0, trueX, trueY, offset)

    // Rule 2: the ring.

    @Test
    fun `a ring of two to eight leaves no two touch squares overlapping`() {
        for (n in 2..FAN_RING_MAX) {
            val ring = fanOffsets(n)
            assertEquals("count for $n", n, ring.size)
            assertTrue("ring of $n: the closest pair is ${minPairwise(ring)} dp apart on its wider axis, the squares overlap below $FAN_TOUCH_DP", minPairwise(ring) >= FAN_TOUCH_DP - 0.01f)
        }
    }

    @Test
    fun `a ring is a circle about the stack's own spot, a touch size or more from it`() {
        for (n in 2..FAN_RING_MAX) {
            val radii = fanOffsets(n).map { hypot(it.xDp, it.yDp) }
            assertTrue("ring of $n: radius ${radii.min()} is under a touch size", radii.min() >= FAN_TOUCH_DP - 0.01f)
            assertTrue("ring of $n is not one radius: ${radii.min()} to ${radii.max()}", radii.max() - radii.min() < 0.01f)
        }
    }

    @Test
    fun `the first marker of a ring is straight above the spot`() {
        val first = fanOffsets(4).first()
        assertEquals(0f, first.xDp, 0.01f)
        assertTrue("12 o'clock is negative y, was ${first.yDp}", first.yDp < 0f)
    }

    @Test
    fun `the ring of eight is as tight as square touch areas allow - its radius is derived, not a round number`() {
        // Adjacent markers of eight sit 45 degrees apart; their centres differ by r sin 45 across and r (1 - cos 45) along,
        // so the squares clear each other when r sin 45 = 48, r = 48 / sin 45 = 67.88.
        val radius = hypot(fanOffsets(8).first().xDp, fanOffsets(8).first().yDp)
        assertEquals(67.88f, radius, 0.05f)
    }

    // Rule 3: the spiral.

    @Test
    fun `more than eight are a spiral, not a ring of the same radius`() {
        val radii = fanOffsets(9).map { hypot(it.xDp, it.yDp) }
        assertTrue("nine markers sit on one circle (${radii.min()} to ${radii.max()}), so this is a ring", radii.max() - radii.min() > 1f)
    }

    @Test
    fun `a spiral of nine to sixty leaves no two touch squares overlapping and clears the spot`() {
        for (n in listOf(9, 12, 30, 60)) {
            val spiral = fanOffsets(n)
            assertEquals("count for $n", n, spiral.size)
            assertTrue("spiral of $n: the closest pair is ${minPairwise(spiral)} dp apart on its wider axis, the squares overlap below $FAN_TOUCH_DP", minPairwise(spiral) >= FAN_TOUCH_DP - 0.01f)
            assertTrue("spiral of $n: a marker at ${spiral.minOf { hypot(it.xDp, it.yDp) }} dp sits on the spot", spiral.all { hypot(it.xDp, it.yDp) >= FAN_TOUCH_DP - 0.01f })
        }
    }

    @Test
    fun `a spiral's markers are all distinct`() {
        assertEquals(30, fanOffsets(30).toSet().size)
    }

    @Test
    fun `a single marker still gets a real offset, never NaN`() {
        val one = fanOffsets(1).single()
        assertFalse(one.xDp.isNaN() || one.yDp.isNaN())
        assertTrue(hypot(one.xDp, one.yDp) >= FAN_TOUCH_DP - 0.01f)
    }

    // Rule 1: what is a stack.

    @Test
    fun `two markers under a touch size apart are a stack, and the tapped one comes first`() {
        val a = marker("a", 100f, 100f)
        val b = marker("b", 100f + 2 * 47f, 100f) // 47 dp at density 2
        assertEquals(listOf(a, b), stackOf(a, listOf(b), density = 2f))
        assertEquals(listOf(b, a), stackOf(b, listOf(a), density = 2f))
    }

    @Test
    fun `at exactly a touch size apart the areas only touch, so it is not a stack`() {
        val a = marker("a", 0f, 0f)
        val b = marker("b", 2 * FAN_TOUCH_DP, 0f)
        assertEquals(listOf(a), stackOf(a, listOf(b), density = 2f))
    }

    @Test
    fun `overlap needs both axes - a marker far above is not in the stack`() {
        val a = marker("a", 0f, 0f)
        val above = marker("above", 0f, -2 * 60f)
        assertEquals(listOf(a), stackOf(a, listOf(above), density = 2f))
    }

    @Test
    fun `the tapped marker is not counted twice when the query returned it too`() {
        val a = marker("a", 0f, 0f)
        val b = marker("b", 10f, 10f)
        assertEquals(listOf(a, b), stackOf(a, listOf(a, b), density = 2f))
    }

    @Test
    fun `a chain is not a stack - a marker that overlaps only a neighbour of the tapped one is left out`() {
        val a = marker("a", 0f, 0f)
        val b = marker("b", 2 * 40f, 0f)
        val c = marker("c", 2 * 80f, 0f) // 80 dp from a, 40 from b
        assertEquals(listOf(a, b), stackOf(a, listOf(b, c), density = 2f))
    }

    // The positions while it opens.

    @Test
    fun `a member sits on its true spot at progress 0 and on its ring place at 1`() {
        val m = member("m", 100f, 200f, FanOffset(30f, -40f))
        assertEquals(FanOffset(100f, 200f), memberPositionDp(m, 0f))
        assertEquals(FanOffset(130f, 160f), memberPositionDp(m, 1f))
        assertEquals(FanOffset(115f, 180f), memberPositionDp(m, 0.5f))
    }

    // The tap on a fanned marker.

    @Test
    fun `a touch anywhere in a fanned marker's own 48 dp square finds it, and outside does not`() {
        val members = fanOffsets(3).mapIndexed { i, off -> member("m$i", 200f, 300f, off) }
        for (m in members) {
            val at = memberPositionDp(m, 1f)
            assertEquals(m, fanMemberAt(members, 1f, at.xDp, at.yDp))
            for ((dx, dy) in listOf(-20f to -20f, 20f to -20f, -20f to 20f, 20f to 20f)) {
                assertEquals("corner $dx,$dy of ${m.key.featureId}", m, fanMemberAt(members, 1f, at.xDp + dx, at.yDp + dy))
            }
        }
        assertNull(fanMemberAt(members, 1f, 200f + 400f, 300f))
    }

    @Test
    fun `where two squares would both contain a touch the nearer marker wins`() {
        val a = member("a", 0f, 0f, FanOffset(0f, 0f))
        val b = member("b", 0f, 0f, FanOffset(30f, 0f))
        assertEquals(b, fanMemberAt(listOf(a, b), 1f, 20f, 0f))
        assertEquals(a, fanMemberAt(listOf(a, b), 1f, 5f, 0f))
    }

    @Test
    fun `progress moves the places a touch is tested against`() {
        val m = member("m", 0f, 0f, FanOffset(0f, -60f))
        assertNotEquals(null, fanMemberAt(listOf(m), 0f, 0f, 0f))
        assertNull(fanMemberAt(listOf(m), 1f, 0f, 0f))
        assertEquals(m, fanMemberAt(listOf(m), 1f, 0f, -60f))
        assertTrue(abs(memberPositionDp(m, 1f).yDp + 60f) < 0.001f)
    }
}
