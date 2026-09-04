package com.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.forager.app.domain.ComputeTripWindowsUseCase
import com.forager.app.domain.DeletePlannedTripUseCase
import com.forager.app.domain.AppThemePreferenceRepository
import com.forager.app.domain.DistanceUnitPreferenceRepository
import com.forager.app.domain.GetAvailabilityUseCase
import com.forager.app.domain.GetConditionsUseCase
import com.forager.app.domain.GetPlannedTripsUseCase
import com.forager.app.domain.GetRecentSearchesUseCase
import com.forager.app.domain.GetSeasonalPatternUseCase
import com.forager.app.domain.GetSightingsUseCase
import com.forager.app.domain.GetTodaysForecastUseCase
import com.forager.app.domain.GetTripWindowsUseCase
import com.forager.app.domain.HistoricalWeatherProvider
import com.forager.app.domain.InMemorySearchCacheRepository
import com.forager.app.domain.LocationFix
import com.forager.app.domain.LocationProvider
import com.forager.app.domain.LocationResult
import com.forager.app.domain.LocationTracker
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.MapPreferencesRepository
import com.forager.app.domain.MushroomRepository
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.PlannedTripRepository
import com.forager.app.domain.PredictAvailabilityUseCase
import com.forager.app.domain.SavePlannedTripUseCase
import com.forager.app.domain.SearchTaxaUseCase
import com.forager.app.domain.TaxonSearchRepository
import com.forager.app.domain.TripPlanningWeatherProvider
import com.forager.app.domain.WeatherProvider
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.SpeciesObservationCount
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.WeatherSeries
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.ui.log.CartographyUiState
import com.forager.app.ui.log.MushroomLogUiState
import com.forager.app.ui.map.MapSlot
import androidx.lifecycle.Lifecycle
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * System back on the compact layout, driven through the real
 * [androidx.activity.ComponentActivity.getOnBackPressedDispatcher] rather than by calling a
 * callback directly — the whole point of this suite is verifying the *priority* several
 * independently-declared [androidx.activity.compose.BackHandler]s resolve to when more than one is
 * enabled at once (e.g. the drawer open while the map is also fullscreen), which only the real
 * dispatcher's own stack can settle. Reasoning about `BackHandler` registration order is exactly
 * the kind of thing CLAUDE.md's "see the failure... don't describe code that wasn't actually read"
 * warns against trusting unverified.
 *
 * The rule: back always unwinds one step toward "home" (Maps tab, chrome visible, drawer closed)
 * before the exit-confirmation handler ever sees it. See `AvailabilityScreen`'s own back-handling
 * block, `CompactMapTab`'s `AddActionTile` handler, `JournalTab`'s, and `CompactSettingsTab`'s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenBackNavigationTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val searchCache = InMemorySearchCacheRepository()
    private lateinit var viewModel: AvailabilityViewModel

    private fun pressBack() {
        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.waitForIdle()
    }

    /**
     * Simulates the app backgrounding and the user returning to it — real `ON_STOP`/`ON_RESUME`
     * events driven through the actual Activity lifecycle (`ActivityScenario.moveToState`), the
     * same "the real dispatcher/lifecycle is the only thing that can settle this" reasoning this
     * file's own class doc comment gives for [pressBack] over calling a callback directly.
     * `CREATED` (not `DESTROYED`) is backgrounding, not a real Activity recreation — the View
     * hierarchy and Compose composition stay alive, matching what actually happens when a user
     * presses home or switches apps, as opposed to a config change or process death.
     */
    private fun backgroundThenResume() {
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        composeRule.waitForIdle()
    }

    private fun setScreen(logUiState: MushroomLogUiState = MushroomLogUiState(), cartographyUiState: CartographyUiState = CartographyUiState()) {
        val plannedTripRepository = BackNavInMemoryPlannedTripRepository()
        viewModel = AvailabilityViewModel(
            locationProvider = BackNavUnusedLocationProvider,
            locationTracker = BackNavNoOpLocationTracker,
            getAvailability = GetAvailabilityUseCase(PredictAvailabilityUseCase(BackNavEmptyRepository), searchCache),
            getRecentSearches = GetRecentSearchesUseCase(searchCache),
            getSightings = GetSightingsUseCase(BackNavEmptyRepository),
            searchTaxa = SearchTaxaUseCase(BackNavEmptyRepository),
            getConditions = GetConditionsUseCase(BackNavStubWeatherProvider),
            getTripWindows = GetTripWindowsUseCase(BackNavStubTripPlanningWeatherProvider, ComputeTripWindowsUseCase()),
            getPlannedTrips = GetPlannedTripsUseCase(plannedTripRepository),
            savePlannedTrip = SavePlannedTripUseCase(plannedTripRepository),
            deletePlannedTrip = DeletePlannedTripUseCase(plannedTripRepository),
            getSeasonalPattern = GetSeasonalPatternUseCase(
                GetSightingsUseCase(BackNavEmptyRepository),
                BackNavStubHistoricalWeatherProvider,
                ComputeFruitingLagDistributionUseCase(),
            ),
            offlineMapRepository = BackNavStubOfflineMapRepository,
            mapPreferencesRepository = BackNavStubMapPreferencesRepository,
            distanceUnitPreferenceRepository = BackNavStubDistanceUnitPreferenceRepository,
            appThemePreferenceRepository = BackNavStubAppThemePreferenceRepository,
            getTodaysForecast = GetTodaysForecastUseCase(BackNavStubTripPlanningWeatherProvider),
        )
        composeRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            var logState by remember { mutableStateOf(logUiState) }
            // Back-nav-and-save-flow dispatch: plain local state standing in for
            // CartographyViewModel, mirroring logState's own fixture shape immediately above and
            // CartographyScreenTest's identical fixture — see that file's own setScreen for the
            // fuller reasoning on why a fixture, not a fake/mock, is right for this.
            var cartographyState by remember { mutableStateOf(cartographyUiState) }
            AvailabilityScreen(
                uiState = uiState,
                onUseCurrentLocation = viewModel::useCurrentLocation,
                onManualLatChanged = viewModel::onManualLatChanged,
                onManualLngChanged = viewModel::onManualLngChanged,
                onSearchManualCoordinates = viewModel::searchManualCoordinates,
                onRadiusChanged = viewModel::onRadiusChanged,
                onMonthSelected = viewModel::onMonthSelected,
                onMapTabSelected = viewModel::onMapTabSelected,
                onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
                onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
                onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
                onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
                onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
                onPlaceTripPin = viewModel::onPlaceTripPin,
                onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                onRecentSearchSelected = viewModel::onRecentSearchSelected,
                onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
                onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
                onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                onThemeModeChanged = viewModel::onThemeModeChanged,
                logUiState = logState,
                onStartLogEntry = { location, date ->
                    // Workstream L4b: a brand-new entry is a draft, never added to entries at
                    // creation (owner decision #6) — see MushroomLogViewModel.onStartNewEntry's own
                    // doc comment.
                    logState = logState.copy(
                        editingEntry = com.forager.app.domain.model.MushroomLogEntry.draft(
                            id = "started-entry",
                            location = location,
                            date = date,
                        ),
                    )
                },
                onOpenLogEntry = { id -> logState = logState.copy(editingEntry = logState.entries.first { it.id == id }) },
                onCloseLogEntry = { logState = logState.copy(editingEntry = null) },
                onLeaveLogEntryEditingIncidentally = {
                    // Workstream L4b: leaving without answering (the back arrow, here) auto-saves
                    // and closes the entry — see MushroomLogViewModel.onLeaveEditingIncidentally's
                    // own doc comment.
                    logState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        logState = logState.copy(
                            entries = if (logState.entries.any { it.id == committed.id }) {
                                logState.entries.map { if (it.id == committed.id) committed else it }
                            } else {
                                logState.entries + committed
                            },
                            editingEntry = null,
                        )
                    }
                },
                cartographyUiState = cartographyState,
                onOpenCartographyEntry = { id ->
                    cartographyState = cartographyState.copy(
                        editingEntry = cartographyState.entries.firstOrNull { it.id == id } ?: cartographyState.draftEntries.firstOrNull { it.id == id },
                    )
                },
                onStartCartographyEntry = { date ->
                    val started = CartographyEntry.draft(id = "new-cartography-entry", date = date, updatedAtEpochMillis = 0L)
                    cartographyState = cartographyState.copy(editingEntry = started, draftEntries = cartographyState.draftEntries + started)
                },
                onCloseCartographyEntry = {
                    cartographyState.editingEntry?.let { current ->
                        cartographyState = cartographyState.copy(
                            entries = if (!current.isDraft && cartographyState.entries.any { it.id == current.id }) {
                                cartographyState.entries.map { if (it.id == current.id) current else it }
                            } else {
                                cartographyState.entries
                            },
                        )
                    }
                    cartographyState = cartographyState.copy(editingEntry = null, hasUnsavedChanges = false)
                },
                onCartographyTextChanged = { text ->
                    cartographyState.editingEntry?.let { current ->
                        val edited = current.copy(text = text)
                        cartographyState = cartographyState.copy(editingEntry = edited, hasUnsavedChanges = cartographyState.hasUnsavedChanges || !edited.isDraft)
                    }
                },
                onFinishCartographyEntry = {
                    cartographyState.editingEntry?.let { current ->
                        val committed = current.copy(isDraft = false)
                        cartographyState = cartographyState.copy(
                            entries = cartographyState.entries + committed,
                            draftEntries = cartographyState.draftEntries.filterNot { it.id == committed.id },
                            editingEntry = committed,
                        )
                    }
                },
                onSaveCartographyEntry = {
                    cartographyState.editingEntry?.let { current ->
                        cartographyState = cartographyState.copy(
                            entries = if (cartographyState.entries.any { it.id == current.id }) {
                                cartographyState.entries.map { if (it.id == current.id) current else it }
                            } else {
                                cartographyState.entries + current
                            },
                            hasUnsavedChanges = false,
                        )
                    }
                },
                onDiscardCartographyEntryChanges = { cartographyState = cartographyState.copy(editingEntry = null, hasUnsavedChanges = false) },
                onSaveCartographyEntryAsDraft = {
                    cartographyState.editingEntry?.let { current ->
                        val demoted = current.copy(isDraft = true)
                        cartographyState = cartographyState.copy(
                            entries = cartographyState.entries.filterNot { it.id == demoted.id },
                            draftEntries = cartographyState.draftEntries + demoted,
                        )
                    }
                    cartographyState = cartographyState.copy(editingEntry = null, hasUnsavedChanges = false)
                },
                onDeleteCartographyEntry = { id ->
                    cartographyState = cartographyState.copy(
                        entries = cartographyState.entries.filterNot { it.id == id },
                        draftEntries = cartographyState.draftEntries.filterNot { it.id == id },
                        editingEntry = null,
                    )
                },
                compassProvider = BackNavFakeCompassProvider,
                mapSlot = BackNavStubMapSlot,
            )
        }
    }

    /**
     * Opens [AdvancedSearchDropdown] via the search summary bar and expands its "Enter coordinates
     * manually" section — map/navigation redesign dispatch C, item 1 moved advanced search out of
     * the Tools drawer entirely, to float over the map from where quick species search used to sit.
     * Most of these were true `performScrollTo()`-free semantic actions before dispatch D promoted
     * radius and month to this dropdown's own top level — that addition pushed "Search this
     * location" below [SearchDropdown]'s own bounded, scrolled viewport on real device
     * configurations, so a `performClick()` against that node's own (correct but now off-screen)
     * bounds reaches nothing actually rendered there; `performScrollTo()` first is what makes that
     * one call reach the real, live button again.
     */
    private fun searchAReferenceRegion() {
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.onNodeWithText("Advanced search").performClick()
        composeRule.onNodeWithText("Enter coordinates manually").performClick()
        composeRule.onNodeWithText("Latitude").performTextReplacement("45.326")
        composeRule.onNodeWithText("Longitude").performTextReplacement("-122.634")
        composeRule.onNodeWithText("Search this location").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `back closes the open drawer instead of warning to exit`() {
        setScreen()
        composeRule.onNodeWithText("Tools").performClick()
        // "Trip Planner" (the drawer's own first section header) stands in for "the drawer is
        // open" — "Recent searches" doesn't work for this any more: dispatch C moved it into
        // SearchDropdown, over the map, not behind this drawer at all.
        composeRule.onNodeWithText("Trip Planner").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Trip Planner").assertIsNotDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    /**
     * The top [AdvancedSearchDropdown] (map/navigation redesign dispatch C, item 1 — replaces the
     * old quick species-search panel this test used to cover, per [ActiveSearchSummary]'s own doc
     * comment) had no `BackHandler` at all before the original fix this test guards — a hardware
     * report that back didn't close it, unlike every other nested UI this suite already covers.
     * Same "one step toward home, no exit warning" shape as the drawer test above.
     */
    @Test
    fun `back closes the advanced search dropdown instead of warning to exit`() {
        setScreen()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performClick()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).assertDoesNotExist()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `back exits fullscreen instead of warning to exit`() {
        setScreen()
        searchAReferenceRegion()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        // The bottom nav is conditionally composed away while fullscreen, not merely hidden.
        composeRule.onNodeWithText("Tools").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("Tools").assertIsDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    /**
     * Was driven through "Settings" before map/navigation redesign dispatch B collapsed it into a
     * nested state inside the "Tools" drawer (see [CompactToolsDrawerContent]'s `showSettings`) —
     * Settings is no longer its own `compactTab`, so it can't stand in for "another tab" here.
     * "List" is a real, still-standalone `compactTab` this handler chain treats identically.
     */
    @Test
    fun `back returns to the Maps tab from another tab instead of warning to exit`() {
        setScreen()
        composeRule.onNodeWithText("List").performClick()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    // The "drawer open while fullscreen" back-priority test that used to live here is gone, not
    // relocated: it depended on the icon stack's own "Search" button, which stayed up while
    // fullscreen and so could open the drawer in that state. Map/navigation redesign dispatch B
    // removed that button — Tools now lives only on the bottom nav, which itself is conditionally
    // composed away while fullscreen (see the "back exits fullscreen" test's own assertion above) —
    // and the drawer's scrim blocks MapIconBar's Fullscreen button while the drawer is open, so
    // there is no path left to reach *and* leave fullscreen with the drawer open at the same time.
    // "Drawer open and fullscreen at once" is accordingly no longer a reachable state to assert
    // back-priority over, not merely an untested one — this is the same accepted reachability
    // tradeoff as removing the Search icon itself.

    /**
     * A Journal entry's own edit form is more nested than "which bottom-nav tab is selected" — back
     * must unwind the entry before it ever switches tabs. Proves `JournalTab`'s own `BackHandler`,
     * composed as part of this tab's content, takes priority over `AvailabilityScreen`'s top-level
     * "switch away from a non-Maps tab" handler.
     *
     * Workstream L4b: back out of the edit form is an incidental exit (auto-save + close), not a
     * toggle to the report the way it worked before drafts existed — see
     * `MushroomLogViewModel.onLeaveEditingIncidentally`'s own doc comment, and
     * [com.forager.app.ui.log.JournalTabTest]'s identical flip for the same reason. Ends on the
     * gallery, not the report.
     */
    @Test
    fun `back backs out of a Journal entry before switching away from the Journal tab`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        // Journal Stage 2b: finds relocated from Cartography into Records' fourth Finds submenu.
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Finds").performClick()
        composeRule.onNodeWithContentDescription("New log entry").performClick()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()

        pressBack()

        // Back on the gallery (JournalTab's `when` mounts only one at a time, so the edit form is
        // fully unmounted, not merely hidden), and still on the Journal tab — not bounced to Maps.
        composeRule.onNodeWithText("Photos").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
    }

    // --- Back-nav-and-save-flow dispatch, Items 1-3: Cartography's own back-navigation gap -------
    // Before this dispatch, none of CartographyScreen/JournalTab's selectedTopTab/RecordsTab's
    // selectedTab had any BackHandler reaching them at all — back on any of them, draft or
    // committed, fell straight through to AvailabilityScreen's own go-home handler in one step,
    // exiting the Journal entirely. These are the real onBackPressedDispatcher tests that gap
    // needed and never had — see this file's own class doc comment on why only the real dispatcher
    // can settle a BackHandler-priority question.

    private val committedCartographyEntry = CartographyEntry.draft(id = "committed-1", date = LocalDate.of(2026, 8, 1), updatedAtEpochMillis = 1_000L)
        .copy(isDraft = false)

    /** The entry-level layer: a draft opens straight into the editor, and back on it must step back to the Drafts list — never exit the Journal, and never prompt (drafts autosave silently, unchanged by this dispatch). */
    @Test
    fun `back on an open Cartography draft steps back to the drafts list, not out of the Journal, and the draft persists`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithContentDescription("New Cartography entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()

        pressBack()

        // Still inside the Journal (not bounced to Maps), and the draft is visible, not lost.
        composeRule.onNodeWithText("Your own account (optional)").assertDoesNotExist()
        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Cartography").assertIsDisplayed()
        composeRule.onNodeWithText("Drafts (1)").assertIsDisplayed()
    }

    /**
     * The Records sub-tab layer: back from a non-default sub-tab steps to Waypoints, the fixed
     * default — not out to Cartography in the same press. Recorded Tracks, not Offline Maps: that
     * sub-tab's own `onOfflineMapsOpened` calls the real `AvailabilityViewModel`, which reaches a
     * real `LocationProvider` this fixture deliberately stubs to error (see
     * [BackNavUnusedLocationProvider]) — unrelated to what this test is proving, so it picks the
     * sub-tab that doesn't touch it.
     */
    @Test
    fun `back on a non-default Records sub-tab steps to Waypoints before leaving Records`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Recorded Tracks").performClick()
        composeRule.onNodeWithText("No recorded tracks yet.").assertIsDisplayed()

        pressBack()

        // Still on Records, now showing Waypoints' own content — not bounced out to Cartography or Maps.
        composeRule.onNodeWithText("No recorded tracks yet.").assertDoesNotExist()
        composeRule.onNodeWithText("No waypoints dropped yet. Tap the add button on the map to drop one.").assertIsDisplayed()
    }

    /** The top-tab layer: back from Records' own default sub-tab (nothing left within Records to unwind) steps to Cartography — still inside the Journal, not out to Maps. */
    @Test
    fun `back on Records' default sub-tab steps to Cartography before leaving the Journal`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Waypoints").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithTag("map-slot").assertDoesNotExist()
    }

    /**
     * The caution from this dispatch: adding steps must never cost the go-home fallback itself —
     * back from Cartography's own top level (nothing open, nothing left within the Journal to
     * unwind) must still reach it, exactly as it did before this dispatch.
     */
    @Test
    fun `back on Cartography's own top level still falls through to the go-home handler`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithTag("map-slot").assertIsDisplayed()
        assertEquals(null, ShadowToast.getTextOfLatestToast())
    }

    /** Item 2: the prompt only fires once system back actually reaches CartographyEntryEditScreen's own decision — and only for a *dirty* committed entry, never a clean one. */
    @Test
    fun `back on a clean committed Cartography entry closes without any prompt`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
    }

    /** Item 2: a dirty committed entry's system back shows Save/Discard/Cancel — the exact prompt the on-screen arrow already showed, now reachable by the nav-bar button too. Cancel keeps editing with the change intact. */
    @Test
    fun `back on a dirty committed Cartography entry shows the leave prompt, and Cancel keeps editing`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Unsaved via system back.")
        composeRule.waitForIdle()

        pressBack()

        composeRule.onNodeWithText("Save your changes?").assertIsDisplayed()

        composeRule.onNodeWithTag(com.forager.app.ui.log.LEAVE_PROMPT_CANCEL_TEST_TAG).performClick()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Unsaved via system back.").assertIsDisplayed()
    }

    /** Item 2/3: Discard via system back's own prompt reloads from the database and leaves — no second dialog, no lingering edit. */
    @Test
    fun `Discard from the system-back leave prompt discards the edit and leaves`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Should not be saved.")
        composeRule.waitForIdle()
        pressBack()
        composeRule.onNodeWithText("Save your changes?").assertIsDisplayed()

        composeRule.onNodeWithTag(com.forager.app.ui.log.LEAVE_PROMPT_DISCARD_TEST_TAG).performClick()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
    }

    /** Item 2/3: Save via system back's own prompt saves and leaves in one step — no second dialog. */
    @Test
    fun `Save from the system-back leave prompt saves the edit and leaves, with no second dialog`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Saved via system back.")
        composeRule.waitForIdle()
        pressBack()
        composeRule.onNodeWithText("Save your changes?").assertIsDisplayed()

        composeRule.onNodeWithTag(com.forager.app.ui.log.LEAVE_PROMPT_SAVE_TEST_TAG).performClick()

        composeRule.onNodeWithText("Save your changes?").assertDoesNotExist()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithText("Saved via system back.").assertIsDisplayed()
    }

    // --- Pending-edit-and-fixes dispatch, Item 1: backgrounding must not commit ---------------------
    // Real ON_STOP/ON_RESUME coverage, via backgroundThenResume() — this exact lifecycle transition
    // (not just the ViewModel methods it can trigger) had no test anywhere in this suite before this
    // dispatch, the same kind of gap the back-nav dispatch's own report called out and closed for
    // system back specifically.

    @Test
    fun `backgrounding a dirty committed entry commits nothing, and resuming shows the return prompt`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Pending, not yet approved.")
        composeRule.waitForIdle()

        backgroundThenResume()

        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()
        // Still showing the pending edit, right where it was — commit never happened.
        composeRule.onNodeWithText("Pending, not yet approved.").assertIsDisplayed()
    }

    @Test
    fun `backgrounding a clean committed entry shows no return prompt on resume`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        backgroundThenResume()

        composeRule.onNodeWithText("Welcome back").assertDoesNotExist()
    }

    @Test
    fun `backgrounding an open draft shows no return prompt on resume — drafts autosave, unchanged`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithContentDescription("New Cartography entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Draft text.")
        composeRule.waitForIdle()

        backgroundThenResume()

        composeRule.onNodeWithText("Welcome back").assertDoesNotExist()
        composeRule.onNodeWithText("Draft text.").assertIsDisplayed()
    }

    @Test
    fun `Continue editing on the return prompt dismisses it and leaves the pending edit exactly in place`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Pending, not yet approved.")
        composeRule.waitForIdle()
        backgroundThenResume()
        composeRule.onNodeWithText("Welcome back").assertIsDisplayed()

        composeRule.onNodeWithTag(com.forager.app.ui.log.RETURN_PROMPT_CONTINUE_EDITING_TEST_TAG).performClick()

        composeRule.onNodeWithText("Welcome back").assertDoesNotExist()
        composeRule.onNodeWithText("Pending, not yet approved.").assertIsDisplayed()
    }

    /**
     * The final assertion below used to read `onNodeWithText("Committed on return.").assertIsDisplayed()`
     * with nothing checking that the preceding tap on the tile actually opened it — that text is
     * also the tile's own accessibility text (`CartographyEntryTile` renders `entry.text` as its
     * preview line), so the assertion passed whether or not the tap landed on anything. It didn't,
     * for a real reason: after "Back to Cartography" closed the editor, the app's top search field
     * silently regained focus and popped its "Advanced search" dropdown open — scrim included — over
     * the tab row and grid beneath, confirmed on a physical device (search-focus-and-hide dispatch)
     * after this session first found it via `composeRule.onRoot().printToLog()`. Strengthened to
     * assert `"Entry options"` (only present on the opened report screen, never on the tile) is
     * displayed — a real assertion of navigation, not a coincidence of shared text — which now
     * passes because the bar is hidden for as long as an entry is open (`AvailabilityScreen.kt`'s own
     * `isEditingJournalEntry`-gated `SearchEntryBar` call sites) rather than because focus was
     * explicitly cleared: see that gate's own doc comment for why a `LaunchedEffect(isEditingJournalEntry)
     * { focusManager.clearFocus() }` was tried first and found to reintroduce this exact bug instead
     * of fixing it.
     */
    @Test
    fun `Commit on the return prompt persists the pending edit and stays on the entry`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Committed on return.")
        composeRule.waitForIdle()
        backgroundThenResume()

        // Search-focus-and-hide dispatch, Item 3: "Submit," not "Commit" — the tag/callback
        // (RETURN_PROMPT_COMMIT_TEST_TAG, onCommit) are unchanged, only this label.
        composeRule.onNodeWithText("Submit").assertIsDisplayed()
        composeRule.onNodeWithTag(com.forager.app.ui.log.RETURN_PROMPT_COMMIT_TEST_TAG).performClick()

        composeRule.onNodeWithText("Welcome back").assertDoesNotExist()
        // Still on the entry (Commit doesn't leave), and it landed in Entries.
        composeRule.onNodeWithText("Committed on return.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithText("2026-08-01").performClick()
        // Proves the tap actually opened the entry — see this test's own doc comment.
        composeRule.onNodeWithContentDescription("Entry options").assertIsDisplayed()
        composeRule.onNodeWithText("Committed on return.").assertIsDisplayed()
    }

    /**
     * The owner's own correction to the reported plan: demoting closes the screen rather than
     * staying open in draft mode, so the change is visible, not discovered later.
     *
     * Closing the edit screen here removes a composable holding text-field focus; after that,
     * [ACTIVE_SEARCH_SUMMARY_TAG] (the top-level search field) used to show `Focused = true` and pop
     * its "Advanced search" dropdown open, scrim included, over the whole screen — confirmed via
     * `composeRule.onRoot().printToLog()`, not guessed, then confirmed again on a physical device
     * (search-focus-and-hide dispatch). The scrim ate the next tap (switching to the Drafts tab), so
     * the tab never switched and the demoted entry's tile was never found. The *same* focus hand-off
     * and open dropdown were present after the "Commit on the return prompt..." test's own "Back to
     * Cartography" click too — that one only read green because its final assertion had a blind spot
     * of its own (see that test's own doc comment, same investigation).
     *
     * Fixed by hiding the bar for as long as an entry is open — `AvailabilityScreen.kt`'s own
     * `isEditingJournalEntry`-gated `SearchEntryBar` call sites — not by clearing focus on this exact
     * transition: that was tried first (`LaunchedEffect(isEditingJournalEntry) { focusManager.clearFocus() }`,
     * the natural generalization of this file's own established `LaunchedEffect(showSearchDropdown)`
     * pattern) and made this test fail the *same way*, because clearing focus at the instant the hide
     * condition also flips the bar back into existence left it as the only focusable candidate with
     * nothing else claiming focus — precisely what triggers this codebase's own default-focus
     * reassignment onto it. See that gate's own doc comment for the full account.
     */
    @Test
    fun `Save as draft on the return prompt closes the screen and moves the entry to Drafts with the edit in place`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Saved as draft on return.")
        composeRule.waitForIdle()
        backgroundThenResume()

        composeRule.onNodeWithTag(com.forager.app.ui.log.RETURN_PROMPT_SAVE_AS_DRAFT_TEST_TAG).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Welcome back").assertDoesNotExist()
        // Closed — visible under Drafts now, not left open silently rendering as a draft.
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithText("Drafts (1)").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithText("Saved as draft on return.").assertIsDisplayed()
        // And it's really gone from Entries, not just still showing there too.
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithText("Entries").performClick()
        composeRule.onNodeWithText("2026-08-01").assertDoesNotExist()
    }

    // --- Search-focus-and-hide dispatch, Item 2: the top search bar hides for as long as an entry
    // (Cartography or a find) is open, present everywhere else. See AvailabilityScreen.kt's own
    // `isEditingJournalEntry` gate, right above its SearchEntryBar call sites, for the fix and the
    // race this session found and ruled out while building it.

    @Test
    fun `the top search bar hides while viewing or editing a Cartography entry, and reappears once it closes`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()

        // Viewing (the report screen) counts as "open," not just editing — see the gate's own doc
        // comment on why this scope was the reachable one, not a lifted VIEW/EDIT distinction.
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertDoesNotExist()

        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()
    }

    @Test
    fun `the top search bar hides while editing a find, and reappears once it closes`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()

        composeRule.onNodeWithText("Records").performClick()
        composeRule.onNodeWithText("Finds").performClick()
        composeRule.onNodeWithContentDescription("New log entry").performClick()
        composeRule.onNodeWithText("Photos").assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertDoesNotExist()

        pressBack()

        composeRule.onNodeWithText("Photos").assertDoesNotExist()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()
    }

    /**
     * The specific scenario this dispatch's own owner flagged as unexercised while the fix was being
     * built: backgrounding and resuming *while still editing* (not backgrounding then immediately
     * exiting, which the return-prompt tests above already cover on their own), then closing the
     * editor normally afterward — the exact moment [ACTIVE_SEARCH_SUMMARY_TAG] reappears, racing
     * against CartographyScreen's own ON_RESUME `focusManager.clearFocus()`. Passing here is what
     * rules out that race, not an assumption that it can't happen.
     */
    @Test
    fun `backgrounding and resuming mid-edit, then closing normally, leaves the search bar visible with no dropdown open`() {
        setScreen(cartographyUiState = CartographyUiState(entries = listOf(committedCartographyEntry)))
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("2026-08-01").performClick()
        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()
        composeRule.onNodeWithText("Your own account (optional)").performTextReplacement("Resumed then closed.")
        composeRule.waitForIdle()
        backgroundThenResume()
        composeRule.onNodeWithTag(com.forager.app.ui.log.RETURN_PROMPT_CONTINUE_EDITING_TEST_TAG).performClick()

        // Still dirty (Continue editing never saved) — closes through the leave-prompt's own Discard,
        // the same as any other unsaved exit, not a direct close.
        composeRule.onNodeWithContentDescription("Back to Cartography").performClick()
        composeRule.onNodeWithTag(com.forager.app.ui.log.LEAVE_PROMPT_DISCARD_TEST_TAG).performClick()

        composeRule.onNodeWithText("Entries").assertIsDisplayed()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Set on map").assertDoesNotExist()
    }

    /**
     * Workstream G2 (`docs/plans/pr26-rework.md`) made the gallery a top-level destination on both
     * window classes; map/navigation redesign dispatch B folded the compact half back in as
     * [com.forager.app.ui.log.LogGalleryScreen]'s own third tab (Log/Drafts/Album), reached through
     * Journal rather than standing alone on the bottom nav, to make room for a fifth bottom-nav slot
     * — see that composable's own doc comment. The medium/expanded half (a drawer entry) is
     * untouched and stays `AvailabilityScreenAdaptiveLayoutTest`'s own equivalent test. Labelled
     * "Album" rather than "Photos" — see `CompactTab`'s own doc comment for why that exact string
     * collides with existing on-screen text elsewhere in this same feature.
     */
    @Test
    fun `the Album tab shows the photo gallery`() {
        val photo = com.forager.app.domain.model.GalleryPhoto(
            photo = com.forager.app.domain.model.LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )
        setScreen(logUiState = MushroomLogUiState(galleryPhotos = listOf(photo)))

        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Album").performClick()

        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
    }

    /**
     * The warning is a real [android.widget.Toast], not Compose UI, so it's read back via
     * [ShadowToast] — the same pattern `DirectionsIntentTest` uses — rather than
     * `onNodeWithText`, which only sees the Compose semantics tree.
     *
     * Doesn't use [pressBack]/`waitForIdle()` between the two presses: idling the compose test
     * clock fast-forwards through the exit-window's own `delay(DOUBLE_BACK_EXIT_WINDOW_MS)`
     * coroutine, resetting `backPressedOnce` back to `false` before the second press ever lands —
     * which would make this test fail for a reason that has nothing to do with whether back
     * navigation actually works, only with how the test clock settles a real elapsed-time window.
     * Advancing by a single frame at a time keeps that coroutine suspended across both presses,
     * the same way a real double-tap arrives well inside the window.
     */
    @Test
    fun `back on the home screen warns once, then exits on a second press`() {
        setScreen()
        composeRule.mainClock.autoAdvance = false

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("Tap Back Button Again to Exit", ShadowToast.getTextOfLatestToast())
        assertFalse(composeRule.activity.isFinishing)

        composeRule.activity.onBackPressedDispatcher.onBackPressed()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(composeRule.activity.isFinishing)
    }
}

private val BackNavStubMapSlot: MapSlot = { _, _, _, _, _, _, _, onCameraIdle, modifier ->
    Column(modifier.testTag("map-slot")) {
        Button(onClick = { onCameraIdle(LatLng(45.326, -122.634)) }) { Text("Simulate pan to test location") }
    }
}

private class BackNavFakeCompassProviderImpl : CompassProvider {
    override val heading: Flow<Float?> = MutableStateFlow(null)
}
private val BackNavFakeCompassProvider = BackNavFakeCompassProviderImpl()

private object BackNavUnusedLocationProvider : LocationProvider {
    override suspend fun getCurrentLocation(): LocationResult =
        error("getCurrentLocation() is not part of this test's path and must not be called")
}

private object BackNavNoOpLocationTracker : LocationTracker {
    override val fixes: Flow<LocationFix> = emptyFlow()
}

private object BackNavEmptyRepository : MushroomRepository, TaxonSearchRepository {
    override suspend fun getSpeciesCounts(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(emptyList<SpeciesObservationCount>())
    override suspend fun getSightings(region: Region, month: Int, filter: TaxonFilter) =
        Result.success(SightingsPage(sightings = emptyList<Sighting>(), totalResults = 0))
    override suspend fun searchTaxa(query: String) = Result.success(emptyList<TaxonSearchResult>())
}

private object BackNavStubWeatherProvider : WeatherProvider {
    override suspend fun getRecentPrecipitation(region: Region) =
        Result.success(ConditionsSummary(region = region, totalPrecipitationMm = 0.0, daysSinceSignificantRain = null))
}

private object BackNavStubTripPlanningWeatherProvider : TripPlanningWeatherProvider {
    override suspend fun getWeatherSeries(region: Region): Result<WeatherSeries> =
        Result.failure(UnsupportedOperationException("trip windows not exercised by this test"))
}

private object BackNavStubHistoricalWeatherProvider : HistoricalWeatherProvider {
    override suspend fun getHistoricalPrecipitation(region: Region, from: LocalDate, through: LocalDate): Result<List<DailyWeather>> =
        Result.failure(UnsupportedOperationException("seasonal pattern not exercised by this test"))
}

private class BackNavInMemoryPlannedTripRepository : PlannedTripRepository {
    private val trips = mutableMapOf<String, PlannedTrip>()

    override suspend fun getAll(): Result<List<PlannedTrip>> = Result.success(trips.values.toList())
    override suspend fun save(trip: PlannedTrip): Result<Unit> {
        trips[trip.id] = trip
        return Result.success(Unit)
    }
    override suspend fun delete(id: String): Result<Unit> {
        trips.remove(id)
        return Result.success(Unit)
    }
}

private object BackNavStubOfflineMapRepository : OfflineMapRepository {
    override suspend fun download(name: String, region: Region, onProgress: (Int, Int) -> Unit): Result<OfflineRegionSummary> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun deleteRegion(id: Long): Result<Unit> =
        Result.failure(UnsupportedOperationException("offline maps not exercised by this test"))
    override suspend fun listRegions(): Result<List<OfflineRegionSummary>> = Result.success(emptyList())
}

private object BackNavStubMapPreferencesRepository : MapPreferencesRepository {
    override suspend fun getLastPickedRegion(): Result<Region?> = Result.success(null)
    override suspend fun setLastPickedRegion(region: Region): Result<Unit> = Result.success(Unit)
    override suspend fun getStaleThresholdDays(): Result<Int> = Result.success(DEFAULT_STALE_THRESHOLD_DAYS)
    override suspend fun setStaleThresholdDays(days: Int): Result<Unit> = Result.success(Unit)
    override suspend fun getNightModeMaps(): Result<Boolean> = Result.success(false)
    override suspend fun setNightModeMaps(night: Boolean): Result<Unit> = Result.success(Unit)
}

private object BackNavStubDistanceUnitPreferenceRepository : DistanceUnitPreferenceRepository {
    override suspend fun getDistanceUnit(): Result<DistanceUnit> = Result.success(DistanceUnit.MILES)
    override suspend fun setDistanceUnit(unit: DistanceUnit): Result<Unit> = Result.success(Unit)
}

private object BackNavStubAppThemePreferenceRepository : AppThemePreferenceRepository {
    override suspend fun getThemeMode(): Result<AppThemeMode> = Result.success(AppThemeMode.LIGHT)
    override suspend fun setThemeMode(mode: AppThemeMode): Result<Unit> = Result.success(Unit)
}
