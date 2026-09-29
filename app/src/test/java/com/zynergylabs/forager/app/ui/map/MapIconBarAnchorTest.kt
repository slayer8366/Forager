package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [mapIconBarRowAnchorOffset] (dispatch 2026-09-28-160): the anchor for a row of the bar, from the bar's centre. The portrait and
 * Cartography callers keep the 4 dp row spacing they always had (52 dp pitch, the add row 104 dp below the centre); the landscape L's
 * bar has none (48 dp pitch, 96 dp).
 */
class MapIconBarAnchorTest {

    @Test
    fun `the default spacing keeps the add row 104 dp below the bar's centre, as before`() {
        assertEquals(104f, mapIconBarRowAnchorOffset(rowIndexFromTop = 5).value, 0.001f)
        assertEquals(0f, mapIconBarRowAnchorOffset(rowIndexFromTop = 3).value, 0.001f)
    }

    @Test
    fun `with the landscape L's no spacing the add row is 96 dp below the bar's centre`() {
        assertEquals(96f, mapIconBarRowAnchorOffset(rowIndexFromTop = 5, rowSpacing = MAP_ICON_BAR_LANDSCAPE_ROW_SPACING).value, 0.001f)
        assertEquals(0f, mapIconBarRowAnchorOffset(rowIndexFromTop = 3, rowSpacing = 0.dp).value, 0.001f)
        assertEquals(-96f, mapIconBarRowAnchorOffset(rowIndexFromTop = 1, rowSpacing = 0.dp).value, 0.001f)
    }
}
