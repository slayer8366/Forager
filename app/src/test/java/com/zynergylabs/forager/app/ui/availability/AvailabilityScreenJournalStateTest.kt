package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Journal redesign J1, S1 (`prompts/preserved/2026-09-27-16.md`; plan J10/L7; owner ruling 4 in
 * `docs/audits/2026-09-27-journal-j0-pulse.md`, "Hoisted, saveable"): the Journal's top tab and its
 * Records selection survive leaving the Journal bottom-nav tab and coming back, and survive a
 * saved-instance-state round trip.
 *
 * Driven through the real [AvailabilityScreen]: the bottom nav is what a user leaves the Journal
 * with, so a test on `JournalTab` alone could not see the reset this guards (the state used to live
 * inside the `when (compactTab)` branch that leaving the tab disposes).
 *
 * **Recreation.** The repository had no Activity-recreation test to copy (no `recreate()` and no
 * [StateRestorationTester] anywhere under `app/src/test` at `3df97d1`). [StateRestorationTester] is
 * Compose's own harness for this: it saves every `rememberSaveable` through the host's real
 * `SaveableStateRegistry`, disposes the whole composition and recomposes it from the saved values,
 * which is what an Activity recreation (night-mode toggle, fold, process death) does to composition
 * state. Plain `remember` state does not come back from it, which is what lets it tell a saveable
 * holder from a hoisted-but-unsaved one. `compactTab` itself was plain `remember` when this was
 * written (`AvailabilityScreen.kt:693` at `3df97d1`), so the test taps Journal again after the round
 * trip; journal redesign J2 (T5) made `compactTab` saveable, so that tap is now a no-op on the tab
 * already showing, and `AvailabilityScreenJournalEntriesStateTest` covers the tab surviving.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenJournalStateTest {

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

    /** Bottom-nav navigation by semantic click: which destination is shown, not a claim about touch. */
    private fun openBottomTab(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    /** Leaves the Journal on Records with a non-default Records selection. */
    private fun chooseRecordsAndANonDefaultSelection() {
        openBottomTab("Journal")
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        // J1 S3: the Records selection is a filter chip now (was the "Offline Maps" sub-tab in S1).
        composeRule.onNodeWithTag(OFFLINE_MAPS_CHIP_TAG).performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").assertIsSelected()
        composeRule.onNodeWithTag(OFFLINE_MAPS_CHIP_TAG).assertIsSelected()
    }

    private fun assertRecordsAndTheSelectionSurvived() {
        composeRule.onNodeWithText("Records").assertIsSelected()
        composeRule.onNodeWithTag(OFFLINE_MAPS_CHIP_TAG).assertIsSelected()
    }

    @Test
    fun `leaving the Journal tab and returning keeps the top tab and the Records selection`() {
        composeRule.setContent { JournalStateScreenUnderTest() }
        chooseRecordsAndANonDefaultSelection()

        openBottomTab("List")
        openBottomTab("Journal")

        assertRecordsAndTheSelectionSurvived()
    }

    @Test
    fun `the top tab and the Records selection survive a saved-instance-state round trip`() {
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { JournalStateScreenUnderTest() }
        chooseRecordsAndANonDefaultSelection()

        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()
        openBottomTab("Journal")

        assertRecordsAndTheSelectionSurvived()
    }
}

/** The Records "Offline maps" filter chip (J1 S3); a literal so this file compiles against a base without chips. */
private const val OFFLINE_MAPS_CHIP_TAG = "records-chip-offline-maps"

private val JOURNAL_STATE_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

@androidx.compose.runtime.Composable
private fun JournalStateScreenUnderTest() {
    AvailabilityScreen(
        uiState = AvailabilityUiState(),
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
        mapSlot = JOURNAL_STATE_STUB_MAP,
    )
}
