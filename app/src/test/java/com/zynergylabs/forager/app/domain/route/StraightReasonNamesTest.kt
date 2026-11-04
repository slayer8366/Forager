package com.zynergylabs.forager.app.domain.route

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * N8 of Sorrel's #445/#446 review: a straight leg's reason is stored by its name (RoomPlannedTripRepository), so a renamed or
 * reordered constant would make a saved route unreadable. The names are written out here as literals, so a change to one fails
 * this test by name. Step 3 writes the first stored routes (dispatch 2026-10-30-08).
 */
class StraightReasonNamesTest {

    @Test
    fun `the stored reason names are the shipped ones, in order`() {
        assertEquals(
            listOf(
                "NO_REGION_COVERS",
                "IN_DIFFERENT_AREAS",
                "ROUTES_NOT_DOWNLOADED",
                "ROUTES_FETCHING",
                "ROUTES_NEED_APP_UPDATE",
                "ROUTES_UNREADABLE",
                "NO_TRAIL_ROUTES",
                "START_FAR_FROM_TRAIL",
                "END_FAR_FROM_TRAIL",
                "NO_ROUTE_FOUND",
                "LEAVES_AREA",
            ),
            StraightReason.entries.map { it.name },
        )
    }
}
