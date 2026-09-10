package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The read-seam rule and its thresholds, with every expected value written by hand (timestamp-filter
 * dispatch). The evidence tracks' own proportions — A 17/8, B 32/10, C 8/2, D 33/0 — are pinned as
 * the cases the "large exclusion" threshold must *not* fire on.
 */
class NetworkProviderFixTest {
    private fun point(t: Long, lat: Double = 45.0) = TrackPoint(lat = lat, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = t)
    private fun track(points: List<TrackPoint>, excluded: Int) =
        Track(id = "t", name = null, startedAtEpochMillis = 0L, endedAtEpochMillis = null, points = points, excludedPointCount = excluded)

    @Test
    fun `millis exactly zero is kept - the boundary is the whole rule`() {
        assertFalse(point(1_757_162_407_000L).isNetworkProviderFix())
        assertFalse(point(0L).isNetworkProviderFix())
    }

    @Test
    fun `any non-zero millisecond marks a network-provider fix`() {
        assertTrue(point(1_757_162_407_001L).isNetworkProviderFix())
        assertTrue(point(1_757_162_407_999L).isNetworkProviderFix())
        assertTrue(point(1_757_162_407_500L).isNetworkProviderFix())
    }

    @Test
    fun `a mixed track loses exactly its sub-second points, in stored order`() {
        val whole1 = point(1_000L, lat = 45.000)
        val sub1 = point(3_500L, lat = 45.030)
        val whole2 = point(6_000L, lat = 45.001)
        val sub2 = point(8_250L, lat = 45.030)
        val whole3 = point(11_000L, lat = 45.002)

        assertEquals(listOf(whole1, whole2, whole3), excludeNetworkProviderFixes(listOf(whole1, sub1, whole2, sub2, whole3)))
    }

    @Test
    fun `an all-whole-second track loses nothing - the honest-track case`() {
        val honest = listOf(point(1_000L), point(6_000L), point(11_000L), point(16_000L))
        assertEquals(honest, excludeNetworkProviderFixes(honest))
    }

    @Test
    fun `an all-sub-second track comes back empty`() {
        assertEquals(emptyList<TrackPoint>(), excludeNetworkProviderFixes(listOf(point(1_001L), point(6_250L), point(11_999L))))
    }

    @Test
    fun `the evidence tracks' own proportions do not read as a large exclusion`() {
        // A: 17 stored, 8 excluded (47 %); B: 32 / 10 (31 %); C: 8 / 2 (25 %); D: 33 / 0.
        assertFalse(track(List(9) { point(1_000L * it) }, excluded = 8).isMostlyNetworkFixes())
        assertFalse(track(List(22) { point(1_000L * it) }, excluded = 10).isMostlyNetworkFixes())
        assertFalse(track(List(6) { point(1_000L * it) }, excluded = 2).isMostlyNetworkFixes())
        assertFalse(track(List(33) { point(1_000L * it) }, excluded = 0).isMostlyNetworkFixes())
        assertNull(networkFixExclusionNote(track(List(9) { point(1_000L * it) }, excluded = 8)))
    }

    @Test
    fun `more than three quarters of at least ten stored points is a large exclusion`() {
        assertTrue(track(List(6) { point(1_000L * it) }, excluded = 19).isMostlyNetworkFixes()) // 19 of 25 = 76 %
        assertFalse(track(List(7) { point(1_000L * it) }, excluded = 18).isMostlyNetworkFixes()) // 18 of 25 = 72 %
        assertTrue(track(List(2) { point(1_000L * it) }, excluded = 8).isMostlyNetworkFixes()) // 8 of 10 = 80 %
        assertFalse(track(List(1) { point(1_000L * it) }, excluded = 8).isMostlyNetworkFixes()) // 8 of 9: under the ten-point minimum
    }

    @Test
    fun `one or no survivors from at least two stored points has no usable points`() {
        assertTrue(track(listOf(point(1_000L)), excluded = 1).hasNoUsablePoints())
        assertTrue(track(emptyList(), excluded = 5).hasNoUsablePoints())
        assertFalse(track(listOf(point(1_000L)), excluded = 0).hasNoUsablePoints())
        assertFalse(track(emptyList(), excluded = 0).hasNoUsablePoints())
    }

    @Test
    fun `the row note names what was left out, and only when the exclusion is large`() {
        assertEquals("No usable points — all 12 fixes were from the network provider", networkFixExclusionNote(track(emptyList(), excluded = 12)))
        assertEquals("12 more not shown (network fixes)", networkFixExclusionNote(track(listOf(point(1_000L)), excluded = 12)))
        assertEquals("19 more not shown (network fixes)", networkFixExclusionNote(track(List(6) { point(1_000L * it) }, excluded = 19)))
        assertNull(networkFixExclusionNote(track(List(3) { point(1_000L * it) }, excluded = 2)))
    }
}
