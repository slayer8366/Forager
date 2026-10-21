package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * RECORD -1525: the Maps tab pans to a locate-me only for the search it arrived under ([compactMapLocateFocus]), so a fix
 * from before a search is never replayed when the tab is composed again (the owner's rule from -1468: "only a new search or a
 * tap on locate moves it"). The tab's composition itself is the real-screen test's ([SearchClearAndFrameTest]).
 */
class CompactMapLocateFocusTest {

    private val gps = LatLng(45.3263, -122.6339)
    private val searched = Region(47.55, -122.30, 8)

    private fun state(region: Region?, frame: Int, status: LocateMeStatus) =
        AvailabilityUiState(region = region, searchFrameSerial = frame, locateMeStatus = status)

    @Test
    fun `a fix from before the search is not the search's locate-me`() {
        // The app-open locate (frame 0), then a search (frame 1): Sorrel's case, and the S22's step 3.
        assertNull(compactMapLocateFocus(state(searched, frame = 1, status = LocateMeStatus.Located(gps, null, searchFrameSerial = 0))))
    }

    @Test
    fun `a locate tap after the search is its locate-me, every time the tab is composed`() {
        val afterSearch = state(searched, frame = 1, status = LocateMeStatus.Located(gps, null, searchFrameSerial = 1))
        assertEquals(gps, compactMapLocateFocus(afterSearch))
        assertEquals("the same answer when the tab comes back", gps, compactMapLocateFocus(afterSearch.copy()))
    }

    @Test
    fun `a new search drops an earlier locate-me`() {
        assertNull(compactMapLocateFocus(state(searched, frame = 2, status = LocateMeStatus.Located(gps, null, searchFrameSerial = 1))))
    }

    @Test
    fun `before a search, and without a fix, there is no locate-me pan`() {
        assertNull("no search: displayRegion follows the fix instead", compactMapLocateFocus(state(null, frame = 0, status = LocateMeStatus.Located(gps, null, 0))))
        assertNull(compactMapLocateFocus(state(searched, frame = 1, status = LocateMeStatus.Loading)))
        assertNull(compactMapLocateFocus(state(searched, frame = 1, status = LocateMeStatus.Idle)))
    }
}
