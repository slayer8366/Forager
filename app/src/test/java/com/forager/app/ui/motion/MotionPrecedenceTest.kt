package com.forager.app.ui.motion

import com.forager.app.ui.motion.MotionPrecedence.DegradationTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven checks for docs/motion-spec.md §1 and §3: precedence order, the animated-object
 * cap, degradation order, and marker clustering.
 */
class MotionPrecedenceTest {

    @Test
    fun `precedence order is legibility then performance then calm`() {
        assertEquals(
            listOf(
                MotionPrecedence.Principle.LEGIBILITY,
                MotionPrecedence.Principle.PERFORMANCE,
                MotionPrecedence.Principle.CALM,
            ),
            MotionPrecedence.PRECEDENCE_ORDER,
        )
    }

    @Test
    fun `degradation order drops decorative loops first and location indicator last`() {
        assertEquals(
            listOf(
                DegradationTier.DECORATIVE_CONTINUOUS_LOOPS,
                DegradationTier.ROUTE_PROGRESSIVE_DETAIL,
                DegradationTier.MARKER_ENTRANCE_ANIMATIONS,
                DegradationTier.LOCATION_INDICATOR,
            ),
            MotionPrecedence.DEGRADATION_ORDER,
        )
    }

    @Test
    fun `marker clustering threshold engages before the animated-object cap`() {
        assertTrue(MotionPrecedence.MARKER_CLUSTERING_THRESHOLD <= MotionPrecedence.MAX_CONTINUOUS_ANIMATED_OBJECTS)
    }

    @Test
    fun `clustering engages once visible marker count reaches the threshold`() {
        val threshold = MotionPrecedence.MARKER_CLUSTERING_THRESHOLD
        assertTrue(!MotionPrecedence.shouldClusterMarkers(threshold - 1))
        assertTrue(MotionPrecedence.shouldClusterMarkers(threshold))
        assertTrue(MotionPrecedence.shouldClusterMarkers(threshold + 1))
    }

    @Test
    fun `all tiers stay active when under the budget and not under load`() {
        val counts = mapOf(
            DegradationTier.DECORATIVE_CONTINUOUS_LOOPS to 2,
            DegradationTier.ROUTE_PROGRESSIVE_DETAIL to 1,
            DegradationTier.MARKER_ENTRANCE_ANIMATIONS to 2,
            DegradationTier.LOCATION_INDICATOR to 1,
        )
        assertEquals(DegradationTier.entries.toSet(), MotionPrecedence.activeTiers(counts, underLoad = false))
    }

    @Test
    fun `battery-saver drops decorative loops outright even under budget`() {
        val counts = mapOf(
            DegradationTier.DECORATIVE_CONTINUOUS_LOOPS to 1,
            DegradationTier.ROUTE_PROGRESSIVE_DETAIL to 1,
            DegradationTier.MARKER_ENTRANCE_ANIMATIONS to 1,
            DegradationTier.LOCATION_INDICATOR to 1,
        )
        val active = MotionPrecedence.activeTiers(counts, underLoad = true)
        assertTrue(DegradationTier.DECORATIVE_CONTINUOUS_LOOPS !in active)
        assertTrue(DegradationTier.LOCATION_INDICATOR in active)
    }

    @Test
    fun `a dense-map fixture over budget degrades tiers in order until the cap holds`() {
        // A dense-map fixture: far more simultaneously animated objects than the budget allows,
        // spread across every tier. This is the "animated-object cap holds under a dense-map
        // fixture" test docs/motion-spec.md §3 calls for.
        val denseFixtureCounts = mapOf(
            DegradationTier.DECORATIVE_CONTINUOUS_LOOPS to 20,
            DegradationTier.ROUTE_PROGRESSIVE_DETAIL to 15,
            DegradationTier.MARKER_ENTRANCE_ANIMATIONS to 30,
            DegradationTier.LOCATION_INDICATOR to 1,
        )

        val active = MotionPrecedence.activeTiers(denseFixtureCounts, underLoad = false)

        val activeTotal = active.sumOf { denseFixtureCounts.getValue(it) }
        assertTrue(
            "active tiers' total ($activeTotal) exceeds MAX_CONTINUOUS_ANIMATED_OBJECTS",
            activeTotal <= MotionPrecedence.MAX_CONTINUOUS_ANIMATED_OBJECTS,
        )
        assertEquals(setOf(DegradationTier.LOCATION_INDICATOR), active)
    }

    @Test
    fun `location indicator alone is never dropped even if it alone exceeds the cap`() {
        // Pathological input: the location indicator's own count is over budget on its own.
        // It must still never be dropped -- there is nothing left to degrade to.
        val counts = mapOf(DegradationTier.LOCATION_INDICATOR to MotionPrecedence.MAX_CONTINUOUS_ANIMATED_OBJECTS + 5)
        val active = MotionPrecedence.activeTiers(counts, underLoad = true)
        assertEquals(setOf(DegradationTier.LOCATION_INDICATOR), active)
    }
}
