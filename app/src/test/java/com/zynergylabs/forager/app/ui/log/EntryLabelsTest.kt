package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.domain.EntryIncludedCounts
import com.zynergylabs.forager.app.domain.EntryReport
import com.zynergylabs.forager.app.domain.HeightProfile
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import java.time.LocalDate
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * The exact words and figures data part A (dispatch 2026-09-28-667) puts on the entry's editor and
 * report: every number with its label and unit (the owner in RECORD -656), so no more "0 ft · 0m".
 * Plain JUnit: these are the strings the composables show, read directly.
 */
class EntryLabelsTest {

    private var savedLocale: Locale = Locale.getDefault()

    @Before
    fun pinLocale() {
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() = Locale.setDefault(savedLocale)

    private fun report(trackCount: Int = 1, distance: Double = 1_931.0, duration: Long = 72 * 60_000L, climb: Double? = 20.0, finds: Int = 3) = EntryReport(
        trackCount = trackCount,
        distanceMeters = distance,
        durationMillis = duration,
        climbMeters = climb,
        findCount = finds,
        heightProfile = HeightProfile.NoTrack,
        waypoints = emptyList(),
    )

    @Test
    fun `the summary line is the owner's example, singular and plural`() {
        assertEquals("In this entry: 1 track, 5 waypoints, 6 finds, 1 offline map", entrySummaryLine(EntryIncludedCounts(1, 5, 6, 1), anythingToChoose = true))
        assertEquals("In this entry: 2 tracks, 1 waypoint, 1 find, 2 offline maps", entrySummaryLine(EntryIncludedCounts(2, 1, 1, 2), anythingToChoose = true))
        assertEquals("In this entry: 3 finds", entrySummaryLine(EntryIncludedCounts(0, 0, 3, 0), anythingToChoose = true))
        assertEquals(NOTHING_INCLUDED_LINE, entrySummaryLine(EntryIncludedCounts(0, 0, 0, 0), anythingToChoose = true))
        assertEquals(NOTHING_TO_INCLUDE_LINE, entrySummaryLine(EntryIncludedCounts(0, 0, 0, 0), anythingToChoose = false))
    }

    /** 1 931 m is 1.2 mi; 20 m of climb is 66 ft; 72 minutes is "1 h 12 min". */
    @Test
    fun `the tiles carry a label and a unit, in miles`() {
        assertEquals(
            listOf("Distance" to "1.2 mi", "Time out" to "1 h 12 min", "Climb" to "66 ft", "Finds" to "3"),
            entrySummaryTiles(report(), DistanceUnit.MILES),
        )
    }

    @Test
    fun `the tiles follow the unit setting, and an unknown climb says so`() {
        assertEquals(
            listOf("Distance" to "1.9 km", "Time out" to "1 h 12 min", "Climb" to "Not recorded", "Finds" to "3"),
            entrySummaryTiles(report(climb = null), DistanceUnit.KILOMETERS),
        )
    }

    @Test
    fun `with no track only the Finds tile shows, and with neither none do`() {
        assertEquals(listOf("Finds" to "2"), entrySummaryTiles(report(trackCount = 0, finds = 2), DistanceUnit.MILES))
        assertEquals(emptyList<Pair<String, String>>(), entrySummaryTiles(report(trackCount = 0, finds = 0), DistanceUnit.MILES))
    }

    /** The owner's "0 ft · 0m": the minutes no longer read as metres. */
    @Test
    fun `a track's line writes its time out in full`() {
        assertEquals("0 ft · Under 1 min", labelledTrackLine(0.0, 0L, DistanceUnit.MILES))
        assertEquals("412 m · 48 min", labelledTrackLine(412.0, 48 * 60_000L, DistanceUnit.KILOMETERS))
    }

    @Test
    fun `dates read Oct 7, 2026`() {
        assertEquals("Oct 7, 2026", displayDate(LocalDate.of(2026, 10, 7)))
    }
}
