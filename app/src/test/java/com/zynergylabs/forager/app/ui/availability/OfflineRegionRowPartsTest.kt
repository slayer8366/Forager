package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The offline-region row's one plain line (RECORD -870; the wording is the owner's, RECORD -871: "Region 5 · 10 mi
 * radius · 18.8 MB · downloaded 11 hours ago"). Pure: [offlineRegionRowParts] is what the row draws, part by part.
 */
class OfflineRegionRowPartsTest {

    private val now = 1_800_000_000_000L
    private val elevenHours = 11 * 60 * 60 * 1_000L

    // 16 km is 9.94 mi, which the app's miles formatter rounds to 10.
    private val region = OfflineRegionSummary(
        id = 5L,
        name = "Region 5",
        region = Region(lat = 45.3261, lng = -122.6345, radiusKm = 16),
        minZoom = 10.0,
        maxZoom = 15.0,
        tileCount = 4321,
        sizeBytes = 18_800_000L,
        createdAtEpochMillis = now - elevenHours,
    )

    @Test
    fun `a downloaded region reads name, radius, size and when it downloaded, in the owner's wording`() {
        val parts = offlineRegionRowParts(region, DistanceUnit.MILES, now)

        assertEquals(listOf("Region 5", "10 mi radius", "18.8 MB", "downloaded 11 hours ago"), parts)
        assertEquals("Region 5 · 10 mi radius · 18.8 MB · downloaded 11 hours ago", parts.joinToString(" · "))
    }

    @Test
    fun `the radius follows the unit setting`() {
        assertEquals("16 km radius", offlineRegionRowParts(region, DistanceUnit.KILOMETERS, now)[1])
    }

    @Test
    fun `a region not yet downloaded takes the same short form, ending Not downloaded, with no size or time`() {
        val restored = region.copy(tileCount = 0, sizeBytes = 0L, isDownloaded = false)

        assertEquals(listOf("Region 5", "10 mi radius", "Not downloaded"), offlineRegionRowParts(restored, DistanceUnit.MILES, now))
    }
}
