package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.PanRecordingMapSlot
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

/*
 * Dispatch 2026-10-17-09, RECORD -1243 and -1245: the offline-maps download screen, in landscape and in portrait. The owner,
 * on landscape: "Trim the top text out, the design is intuitive to use without explaining (3 times). Add at the bottom the
 * note on the Continental US vector map. Have the user be faced with the optional name box at top with the radius slider
 * shown." To the three lines as listed: "Option 1, but do the same for portrait". To the tile budget line: "Keep the figure,
 * drop the explanation (Recommended)".
 *
 * Driven through the real screen (`AvailabilityScreen`), reaching the screen through Journal, Records and its Offline maps
 * chip, as `LandscapeOfflinePickerTest` does.
 */

/** The continental-US sentence, word for word from the instruction it was the first sentence of (RECORD -1245). */
private const val US_NOTE = "Offline downloads cover the continental United States with vector map data."

abstract class OfflineMapsPanelTextTests(private val landscape: Boolean) {
    protected val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val uiState = mutableStateOf(AvailabilityUiState())
    private val map = PanRecordingMapSlot(panTo = LatLng(45.7, -122.9))

    protected fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(if (landscape) Surface.ROTATION_90 else Surface.ROTATION_0)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState.value,
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
                    onOfflineMapLatChanged = { uiState.value = uiState.value.copy(offlineMapLatText = it) },
                    onOfflineMapLngChanged = { uiState.value = uiState.value.copy(offlineMapLngText = it) },
                    onOfflineMapRadiusChanged = { uiState.value = uiState.value.copy(offlineMapRadiusKm = it, offlineMapRadiusTouched = true) },
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = map.slot,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performClick()
        composeRule.waitForIdle()
    }

    private fun absent(text: String, substring: Boolean = true): Boolean =
        composeRule.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()

    private fun textBounds(text: String): DpRect = composeRule.onNode(hasText(text), useUnmergedTree = true).getUnclippedBoundsInRoot()

    @Test
    fun `the three explanatory lines at the top are gone, before and after a pick`() {
        setScreen()
        assertTrue("the instruction (\"Pan the map below…\") is gone", absent("Pan the map below"))
        assertTrue("the centre-pin instruction is gone from this screen", absent("Pan the map to position the pin, then confirm."))
        assertTrue("\"No location picked yet…\" is gone", absent("No location picked yet"))
        // A pick: the picked form of the same line is gone too. Since dispatch 2026-10-18-03 the pick is Download Maps' (this
        // screen has no OK); was `onNodeWithText("OK").performClick()`. Its confirmation is closed again by its own Cancel.
        // A semantic click, as OK's was: in landscape Download Maps is pinned outside the scrolling part (no scroll to it).
        composeRule.onNodeWithText("Download Maps").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()
        assertTrue("a pick was confirmed (the screen has a region to download)", uiState.value.offlineMapLatText.isNotEmpty())
        assertTrue("\"Download region: …\" is gone", absent("Download region:"))
    }

    @Test
    fun `the tile budget line keeps the figure and drops the explanation`() {
        setScreen()
        assertTrue("the figure stays", !absent("Tile budget: 0 / "))
        assertTrue("the explanation is gone", absent("Sizes don't add up"))
        assertTrue("and its second half", absent("overlapping regions share tiles"))
    }

    @Test
    fun `the continental-US note is the last line, under the downloaded-maps section`() {
        setScreen()
        val note = textBounds(US_NOTE)
        val emptyState = textBounds("No regions downloaded yet.")
        val budget = composeRule.onNode(hasText("Tile budget:", substring = true), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the note $note is under the downloaded-maps section's last line $emptyState", note.top >= emptyState.bottom - 0.5.dp)
        assertTrue("and under the budget line $budget", note.top >= budget.bottom - 0.5.dp)
        assertEquals("the note appears once, as its own line", 1, composeRule.onAllNodes(hasText(US_NOTE), useUnmergedTree = true).fetchSemanticsNodes().size)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class OfflineMapsPanelTextLandscapeTest : OfflineMapsPanelTextTests(landscape = true) {
    /**
     * RECORD -1243 (the owner: "Have the user be faced with the optional name box at top with the radius slider shown"): the
     * controls side opens scrolled to its top, the Name box first in it and the slider wholly in view, with no drag.
     */
    @Test
    fun `landscape - the controls open with the Name box first and the radius slider in view`() {
        setScreen()
        val area = composeRule.onNodeWithTag(OFFLINE_PICKER_CONTROLS_SCROLL_TAG).getUnclippedBoundsInRoot()
        val name = composeRule.onNode(hasSetTextAction(), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).getUnclippedBoundsInRoot()
        assertTrue("the Name box $name is the first thing in the controls $area (within 16 dp of its top)", name.top >= area.top - 0.5.dp && name.top <= area.top + 16.dp)
        assertTrue("the slider $slider is wholly in view in the controls $area without a drag", slider.top >= area.top - 0.5.dp && slider.bottom <= area.bottom + 0.5.dp)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class OfflineMapsPanelTextPortraitTest : OfflineMapsPanelTextTests(landscape = false)
