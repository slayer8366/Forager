package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.SpeciesObservationCount
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * Landscape build B3, P11 (`docs/plans/landscape-phone-design.md`, as corrected by R7 and R9;
 * dispatch `prompts/preserved/2026-09-27-14.md`): in a short landscape window, on every tab but
 * Map, the content beside the opaque rail is capped at `READABLE_CONTENT_MAX_WIDTH` (640 dp) and
 * centred in the width the rail leaves.
 *
 * "The tab content" is measured per destination by an element that fills that destination's own
 * width, so what is measured is the width the destination was handed, not a caption that happens
 * to wrap short: List's species card (inside the tab's own 16 dp side padding), the Journal's top
 * tab row (Cartography's left edge to Records' right edge), the Records sub-tab row (Waypoint
 * Markers' left edge to Logged Finds' right edge), and Seasonal's own tagged column.
 *
 * Everything is driven through the real [AvailabilityScreen]; each rotation-specific test first
 * proves the screen read the pinned rotation, so the two port edges are really both sampled.
 * Robolectric reports zero insets, so "the area beside the rail" here is the window less the rail;
 * on a device it is also less the cut-out and system-bar insets, which is a device item (B4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class AvailabilityScreenLandscapeB3DestinationsTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private var rotationSeenByScreen: Int? = null

    private fun setScreen(rotation: Int) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeenByScreen = LocalView.current.display?.rotation
            B3ScreenUnderTest(uiState = B3_LIST_STATE, mapSlot = B3_STUB_MAP)
        }
        composeRule.waitForIdle()
        assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeenByScreen)
    }

    private fun bounds(text: String): DpRect = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
    private fun taggedBounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()
    private fun railBounds(): DpRect = taggedBounds(COMPACT_NAVIGATION_RAIL_TAG)
    private fun rootBounds(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    /** The width the rail leaves: the window less the rail, on whichever side the rail is. */
    private fun areaBesideRail(): DpRect {
        val root = rootBounds()
        val rail = railBounds()
        return if (rail.centreX() > root.centreX()) {
            DpRect(root.left, root.top, rail.left, root.bottom)
        } else {
            DpRect(rail.right, root.top, root.right, root.bottom)
        }
    }

    /** A rail item selected by a semantic click — navigation to the destination under test, not a claim about touch. */
    private fun openTab(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(label).assertIsSelected()
    }

    private fun assertCappedAndCentred(what: String, content: DpRect, allowance: Dp = 0.dp) {
        val area = areaBesideRail()
        assertTrue(
            "$what is at most $READABLE_WIDTH wide (plus $allowance of its own side padding): " +
                "it is ${content.width}, in an area ${area.width} wide beside the rail",
            content.width.value <= (READABLE_WIDTH + allowance).value + 0.5f,
        )
        assertEquals(
            "$what is centred in the area beside the rail ($area): content $content",
            area.centreX(), content.centreX(), 1f,
        )
    }

    private fun listContent(): DpRect = taggedBounds(SPECIES_ROW_TAG)

    /**
     * Journal redesign J5 (L1): in a short window the Entries | Records switch sits at the start of
     * the Journal's pinned 48 dp row, sized to its labels, so its segments no longer span the width
     * the Journal was handed. The row fills that width, so it is what is measured, as the Records
     * chip row is below. (Until J5 the switch's two segments spanned it and were measured.)
     */
    private fun journalTopTabs(): DpRect = taggedBounds(com.zynergylabs.forager.app.ui.log.SHORT_HEADER_TAG)

    /**
     * Journal redesign J1 (S3): the Records filter chip row, which replaced the sub-tab row, filled the width Records was
     * handed, so it was what was measured. Since RECORD -1243 (dispatch 2026-10-17-09) the chips sit on the switch's line in a
     * landscape window, centred and sized to their chips, so they no longer span that width; the line itself does, as on
     * Entries ([journalTopTabs]), so it is what is measured now. The requirement (Records capped at 640 and centred beside the
     * rail) is unchanged. Was `taggedBounds(RECORDS_FILTER_CHIP_ROW_TAG)`.
     */
    private fun recordsSubTabs(): DpRect = taggedBounds(com.zynergylabs.forager.app.ui.log.SHORT_HEADER_TAG)

    // ── T1: List, Journal (its default top tab), Records ──

    private fun checkList(rotation: Int) {
        setScreen(rotation)
        openTab("List")
        composeRule.onNodeWithText("artist's bracket").assertIsDisplayed()
        // The species card sits inside ListTab's own 16 dp side padding, so the capped content is
        // the card plus 32 dp; the card itself is at most 640 - 32.
        assertCappedAndCentred("List's species card", listContent(), allowance = (-32).dp)
    }

    private fun checkJournal(rotation: Int) {
        setScreen(rotation)
        openTab("Journal")
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.journalSwitchTestTag(com.zynergylabs.forager.app.ui.log.JournalTopTab.CARTOGRAPHY)).assertIsSelected()
        assertCappedAndCentred("the Journal's top tab row", journalTopTabs())
    }

    private fun checkRecords(rotation: Int) {
        setScreen(rotation)
        openTab("Journal")
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        // Records opens on Finds since RECORD -868; this asserted All selected before.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.FINDS)).assertIsSelected()
        assertCappedAndCentred("the Records filter chip row", recordsSubTabs())
    }

    @Test fun `T1 at ROTATION_90 List is capped at 640 and centred beside the rail`() = checkList(Surface.ROTATION_90)
    @Test fun `T1 at ROTATION_270 List is capped at 640 and centred beside the rail`() = checkList(Surface.ROTATION_270)
    @Test fun `T1 at ROTATION_90 Journal is capped at 640 and centred beside the rail`() = checkJournal(Surface.ROTATION_90)
    @Test fun `T1 at ROTATION_270 Journal is capped at 640 and centred beside the rail`() = checkJournal(Surface.ROTATION_270)
    @Test fun `T1 at ROTATION_90 Records is capped at 640 and centred beside the rail`() = checkRecords(Surface.ROTATION_90)
    @Test fun `T1 at ROTATION_270 Records is capped at 640 and centred beside the rail`() = checkRecords(Surface.ROTATION_270)

    // ── T2: Seasonal, which caps itself already; guards that the two caps compose to 640, not less ──

    private fun checkSeasonal(rotation: Int) {
        setScreen(rotation)
        openTab("Seasonal")
        val content = taggedBounds(SEASONAL_CONTENT_TAG)
        assertCappedAndCentred("Seasonal's column", content)
        assertEquals("the two caps compose to 640, not less", READABLE_WIDTH.value, content.width.value, 0.5f)
    }

    @Test fun `T2 at ROTATION_90 Seasonal is 640 wide and centred beside the rail`() = checkSeasonal(Surface.ROTATION_90)
    @Test fun `T2 at ROTATION_270 Seasonal is 640 wide and centred beside the rail`() = checkSeasonal(Surface.ROTATION_270)

    // ── The search bar above the tab content ──
    //
    // Was "in the capped column too (coder's reading, see the completion report)":
    // `the search bar above the tab content is capped and centred with it`, asserting
    // assertCappedAndCentred("the search bar", taggedBounds(SEARCH_ENTRY_BAR_TAG)) on List.
    // Superseded by the owner's ruling, RECORD -1227 (dispatch 2026-10-17-09): "Move to Maps'
    // spot". Its position was revised on the S22 (PR #252, build 1.0.3414: "have it centered"),
    // then again (build 1.0.3416, RECORD -1238: "Yes, at the left (Recommended)", to leave room
    // for a floating keyboard). So the bar is the Maps pill's size, outside the 640 dp cap, its
    // start just clear of the navigation bar: with zero insets, the Maps pill's own bounds. The content below it stays
    // capped (T1 and T2 above, unchanged). TabsMapChromeShapeTest checks this at both rotations,
    // on List, Seasonal and Journal, and with a navigation bar inset; this keeps the check in the
    // class that pins B3's cap, on its own screen and state.

    private fun openFromRail(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the search bar above the tab content is the Maps pill's size and place, outside the cap`() {
        setScreen(Surface.ROTATION_90)
        openFromRail("Maps")
        val mapsPill = taggedBounds(SEARCH_ENTRY_BAR_TAG)
        openTab("List")
        val listBar = taggedBounds(SEARCH_ENTRY_BAR_TAG)
        assertEquals("left, the Maps pill's", mapsPill.left.value, listBar.left.value, 0.5f)
        assertEquals("top, the Maps pill's", mapsPill.top.value, listBar.top.value, 0.5f)
        assertEquals("right, the Maps pill's", mapsPill.right.value, listBar.right.value, 0.5f)
        assertEquals("bottom, the Maps pill's", mapsPill.bottom.value, listBar.bottom.value, 0.5f)
    }

    // ── T4: the rail stays tappable beside the capped content ──

    /**
     * Real touches at five points across a rail item's own bounds, each from the capped List tab,
     * each switching to Seasonal. The item's bounds are checked to lie in the rail first, so the
     * points sampled are the rail's, not a same-labelled control elsewhere.
     */
    private fun checkRailTappable(rotation: Int) {
        setScreen(rotation)
        val rail = railBounds()
        val fractions = listOf(0.5f to 0.5f, 0.2f to 0.2f, 0.8f to 0.2f, 0.2f to 0.8f, 0.8f to 0.8f)
        var tapped = 0
        for ((fx, fy) in fractions) {
            openTab("List")
            composeRule.onNodeWithTag(SPECIES_ROW_TAG).assertIsDisplayed()
            val item = bounds("Seasonal")
            assertTrue("the Seasonal item $item lies in the rail $rail", item.left >= rail.left && item.right <= rail.right)
            val point = Offset(
                (item.left + item.width * fx).value,
                (item.top + (item.bottom - item.top) * fy).value,
            )
            composeRule.onRoot().performTouchInput { click(Offset(point.x * density, point.y * density)) }
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Seasonal").assertIsSelected()
            composeRule.onNodeWithTag(SEASONAL_CONTENT_TAG).assertIsDisplayed()
            tapped++
        }
        assertEquals("every sampled point was tapped", fractions.size, tapped)
    }

    @Test fun `T4 at ROTATION_90 real touches across a rail item switch tab beside capped content`() = checkRailTappable(Surface.ROTATION_90)
    @Test fun `T4 at ROTATION_270 real touches across a rail item switch tab beside capped content`() = checkRailTappable(Surface.ROTATION_270)


    // ── T3: portrait is unchanged. A pin: it passes before and after B3 by design ──

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun `T3 portrait List content fills the width, as today`() {
        composeRule.setContent { B3ScreenUnderTest(uiState = B3_LIST_STATE, mapSlot = B3_STUB_MAP) }
        composeRule.waitForIdle()
        assertTrue("portrait has no rail", composeRule.onAllNodesWithTag(COMPACT_NAVIGATION_RAIL_TAG).fetchSemanticsNodes().isEmpty())
        openTab("List")

        val root = rootBounds()
        val card = listContent()
        assertEquals("the card starts at the tab's 16 dp padding", (root.left + 16.dp).value, card.left.value, 0.5f)
        assertEquals("the card ends at the tab's 16 dp padding", (root.right - 16.dp).value, card.right.value, 0.5f)
    }
}

// ── shared fixtures (file-private; named apart from AvailabilityScreenShortLandscapeTest's) ──

private val READABLE_WIDTH = 640.dp
private const val SPECIES_ROW_TAG = "species-row"

private fun DpRect.centreX(): Float = ((left + right) / 2).value

private val B3_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

private val B3_REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private val B3_LIST_STATE = AvailabilityUiState(
    region = B3_REGION,
    sightings = listOf(
        Sighting(
            observationId = 1L,
            taxonId = 48473L,
            scientificName = "Ganoderma applanatum",
            commonName = "artist's bracket",
            lat = B3_REGION.lat,
            lng = B3_REGION.lng,
            observedOn = LocalDate.of(2025, 8, 14),
            photoUrl = null,
        ),
    ),
    forecast = AvailabilityForecast(
        region = B3_REGION,
        month = 8,
        filter = TaxonFilter.FUNGI,
        entries = listOf(
            AvailabilityEntry(
                species = SpeciesObservationCount(
                    taxonId = 48473L,
                    scientificName = "Ganoderma applanatum",
                    commonName = "artist's bracket",
                    rank = "species",
                    observationCount = 14,
                    photoUrl = null,
                    wikipediaUrl = null,
                ),
                relativeLikelihood = 1.0f,
            ),
        ),
    ),
    selectedMonth = LocalDate.now().monthValue,
)

@androidx.compose.runtime.Composable
private fun B3ScreenUnderTest(uiState: AvailabilityUiState, mapSlot: MapSlot) {
    AvailabilityScreen(
        uiState = uiState,
        searchInputs = AvailabilityScreenSearchInputs(
            onUseCurrentLocation = {},
            onManualLatChanged = {},
            onManualLngChanged = {},
            onSearchManualCoordinates = {},
            onRadiusChanged = {},
            onMonthSelected = {},
            onMapTabSelected = {},
            onSeasonalTabSelected = {},
            onTaxonSearchQueryChanged = {},
            onTaxonSearchResultSelected = {},
            onDismissTaxonSuggestions = {},
            onReopenTaxonSuggestions = {},
            onPlaceTripPin = { _, _, _ -> },
            onDeletePlannedTrip = {},
            onRecentSearchSelected = {},
        ),
        offlineMaps = AvailabilityScreenOfflineMaps(
            onOfflineMapLatChanged = {},
            onOfflineMapLngChanged = {},
            onOfflineMapRadiusChanged = {},
            onOfflineMapNameChanged = {},
            onOfflineMapsOpened = {},
            onDownloadOfflineMaps = {},
            onDeleteOfflineRegion = {},
        ),
        settings = AvailabilityScreenSettings(
            onNightModeMapsChanged = {},
            onThemeModeChanged = {},
        ),
        mapSlot = mapSlot,
    )
}
