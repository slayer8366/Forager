package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Data part D (RECORD -702, carrying the owner's -656: "group the entries by date and then keep the name they gave on the tile
 * instead of the date"; unnamed, "Time it was found (Recommended)", for example "Found 2:14 PM"; times follow the phone's 12- or
 * 24-hour setting).
 *
 * Through [FindsGalleryScreen], the Finds grid JournalTab hosts, with its own `onOpenEntry` callback, and real touches at screen
 * coordinates on the grouped tiles. Positions are read from laid-out bounds, not from tree order.
 *
 * The time is the one saved when the find was created, else its earliest photo taken on its day; with neither the tile has no
 * title text and says "Find, <date>" to a screen reader (RECORD -703, the owner: "keep it blank"; see [findTitle]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class FindsGroupedByDayTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val opened = mutableListOf<String>()

    private fun setClock(hours: String) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Settings.System.putString(context.contentResolver, Settings.System.TIME_12_24, hours)
    }

    private fun setScreen(entries: List<MushroomLogEntry>) {
        composeRule.setContent {
            // No "+" tile, so every tile here is laid out on this screen (a lazy grid composes only what is in view).
            FindsGalleryScreen(entries = entries, isLoading = false, onOpenEntry = { opened += it }, onAddEntry = null)
        }
        composeRule.waitForIdle()
    }

    /** Epoch millis for [day] at [hour]:[minute] in the JVM's zone, the zone the tile reads in. */
    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        day.atTime(hour, minute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun find(id: String, day: LocalDate, name: String? = null, photoTimes: List<Long> = emptyList(), foundAt: Long? = null) =
        MushroomLogEntry.draft(id = id, location = null, date = day).copy(
            isDraft = false,
            ownIdentification = name,
            foundAtEpochMillis = foundAt,
            photos = photoTimes.mapIndexed { i, t -> LogPhoto(id = "$id-p$i", relativePath = "photos/$id-$i.jpg", createdAtEpochMillis = t) },
        )

    @Test
    fun `finds sit under one heading per day, newest day first, each tile under its own day`() {
        val older = LocalDate.of(2026, 9, 20)
        val newer = LocalDate.of(2026, 10, 7)
        // Stored oldest first, as the table returns rows: the grid orders the days itself.
        setScreen(listOf(find("a", older, "Morel"), find("b", newer, "Chanterelle"), find("c", older, "Oyster")))

        val newerHeading = composeRule.onNodeWithTag(findsDayHeaderTag(newer)).getUnclippedBoundsInRoot()
        val olderHeading = composeRule.onNodeWithTag(findsDayHeaderTag(older)).getUnclippedBoundsInRoot()
        composeRule.onNodeWithText("Oct 7, 2026").assertExists()
        composeRule.onNodeWithText("Sep 20, 2026").assertExists()
        assertTrue("the newer day ($newerHeading) is above the older ($olderHeading)", newerHeading.bottom <= olderHeading.top)

        val chanterelle = composeRule.onNodeWithText("Chanterelle").getUnclippedBoundsInRoot()
        val morel = composeRule.onNodeWithText("Morel").getUnclippedBoundsInRoot()
        val oyster = composeRule.onNodeWithText("Oyster").getUnclippedBoundsInRoot()
        assertTrue("Chanterelle is under its day's heading", chanterelle.top >= newerHeading.bottom && chanterelle.bottom <= olderHeading.top)
        assertTrue("Morel is under its day's heading", morel.top >= olderHeading.bottom)
        assertTrue("Oyster is under its day's heading", oyster.top >= olderHeading.bottom)
        // No tile carries a date any more: the heading does.
        assertEquals(0, composeRule.onAllNodesWithText("Find on", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a named find's tile shows its name, and an unnamed one never shows a species`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("12")
        setScreen(listOf(find("named", day, "Golden chanterelle"), find("unnamed", day, photoTimes = listOf(at(day, 14, 14)))))

        composeRule.onNodeWithText("Golden chanterelle").assertExists()
        composeRule.onNodeWithText("Found 2:14 PM").assertExists()
        assertEquals("the only name on screen is the one the user gave", 1, composeRule.onAllNodesWithText("chanterelle", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `an unnamed find shows the time it was found in 12-hour form on a 12-hour phone`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("12")
        // Two photos on the day: the earlier is when it was found.
        setScreen(listOf(find("f", day, photoTimes = listOf(at(day, 15, 2), at(day, 14, 14)))))

        composeRule.onNodeWithText("Found 2:14 PM").assertExists()
    }

    @Test
    fun `an unnamed find shows the time it was found in 24-hour form on a 24-hour phone`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("24")
        setScreen(listOf(find("f", day, photoTimes = listOf(at(day, 14, 14)))))

        composeRule.onNodeWithText("Found 14:14").assertExists()
    }

    @Test
    fun `the time saved when the find was created comes before its photos`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("12")
        setScreen(listOf(find("f", day, photoTimes = listOf(at(day, 9, 5)), foundAt = at(day, 14, 14))))

        composeRule.onNodeWithText("Found 2:14 PM").assertExists()
    }

    /** RECORD -703: "keep it blank instead of showing "Unnamed find"". A photo from another day is not the time it was found. */
    @Test
    fun `with no name, no saved time and no photo from its day, the tile has no title and says Find with its date`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("12")
        setScreen(listOf(find("f", day, photoTimes = listOf(at(day.minusDays(3), 9, 30)))))

        composeRule.onNodeWithContentDescription("Find, Oct 7, 2026").assertExists()
        assertEquals(0, composeRule.onAllNodesWithText("Found", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText("Unnamed", substring = true).fetchSemanticsNodes().size)
    }

    /** Real touches at several points across a grouped tile each open that find, and only that find. */
    @Test
    fun `real touches across a grouped tile open that find`() {
        val older = LocalDate.of(2026, 9, 20)
        val newer = LocalDate.of(2026, 10, 7)
        setScreen(listOf(find("a", older, "Morel"), find("b", newer, "Chanterelle")))

        val samples = listOf(0.5f to 0.5f, 0.2f to 0.2f, 0.8f to 0.2f, 0.2f to 0.8f, 0.8f to 0.8f)
        for ((name, id) in listOf("Morel" to "a", "Chanterelle" to "b")) {
            // The tile is the card holding the name: its bounds are the merged node the name's text belongs to.
            val tile = composeRule.onNodeWithText(name).getUnclippedBoundsInRoot()
            samples.forEach { (fx, fy) ->
                val before = opened.size
                val point = with(composeRule.density) {
                    Offset((tile.left + (tile.right - tile.left) * fx).toPx(), (tile.top + (tile.bottom - tile.top) * fy).toPx())
                }
                composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(point) }
                composeRule.waitForIdle()
                assertEquals("a touch at ($fx, $fy) of $name opened it", listOf(id), opened.drop(before))
            }
        }
    }

    /**
     * RECORD -703, "Match the tile": a find's own report is titled by the tile's rule, a blank title included, with the tile's
     * label. Through [LogEntryReportScreen], the page JournalTab shows when a tile is tapped.
     */
    @Test
    fun `a find's report is titled by the tile's rule, blank included`() {
        val day = LocalDate.of(2026, 10, 7)
        setClock("12")
        var shown by mutableStateOf(find("f", day, foundAt = at(day, 14, 14)))
        composeRule.setContent { LogEntryReportScreen(entry = shown, onEdit = {}, onDeleteEntry = {}, onBack = {}) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FIND_PAGE_TITLE_TAG).assertTextEquals("Found 2:14 PM")

        shown = find("f", day, name = "Morel")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FIND_PAGE_TITLE_TAG).assertTextEquals("Morel")

        shown = find("f", day)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FIND_PAGE_TITLE_TAG).assertTextEquals("").assertContentDescriptionEquals("Find, Oct 7, 2026")
        assertEquals("no Find on title any more", 0, composeRule.onAllNodesWithText("Find on", substring = true).fetchSemanticsNodes().size)
    }

    // Headless: the title rule and the grouping, with the zone fixed.

    @Test
    fun `the title is the given name, else the saved time, else the earliest photo of the day, else nothing`() {
        val day = LocalDate.of(2026, 10, 7)
        val nineFive = day.atTime(9, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        val twoFourteen = day.atTime(14, 14).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("Morel", findTitle(find("a", day, "Morel", listOf(twoFourteen), foundAt = nineFive), is24HourClock = false, zone = ZoneOffset.UTC))
        assertEquals("the saved time first", "Found 9:05 AM", findTitle(find("s", day, null, listOf(twoFourteen), foundAt = nineFive), is24HourClock = false, zone = ZoneOffset.UTC))
        assertEquals("a blank name is no name", "Found 2:14 PM", findTitle(find("b", day, "  ", listOf(twoFourteen)), is24HourClock = false, zone = ZoneOffset.UTC))
        assertEquals("Found 14:14", findTitle(find("c", day, null, listOf(twoFourteen)), is24HourClock = true, zone = ZoneOffset.UTC))
        assertNull(findTitle(find("d", day), is24HourClock = false, zone = ZoneOffset.UTC))
        assertEquals("Find, Oct 7, 2026", findBlankTitleLabel(find("d", day)))
    }

    @Test
    fun `days are grouped newest first, each day's items in their given order`() {
        val d1 = LocalDate.of(2026, 9, 1)
        val d2 = LocalDate.of(2026, 9, 2)
        val grouped = groupByDayNewestFirst(listOf("x1" to d1, "y2" to d2, "z1" to d1)) { it.second }
        assertEquals(listOf(d2 to listOf("y2" to d2), d1 to listOf("x1" to d1, "z1" to d1)), grouped)
    }
}
