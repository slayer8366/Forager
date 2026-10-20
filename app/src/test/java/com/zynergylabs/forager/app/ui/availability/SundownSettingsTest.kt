package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.unit.height
import com.zynergylabs.forager.app.data.repository.DataStoreSundownPreferencesRepository
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tools > "Sundown" (dispatch 2026-09-28-592, plan task T4; the owner's step path, RECORD -592; in
 * Settings until dispatch 2026-09-28-707 moved it to the Tools drawer), on the real
 * [AvailabilityScreen] and the real [AvailabilityViewModel] over the real DataStore repository:
 * "Sundown alerts" on by default, "Dark under trees" at 1 h with the owner's sentence, each changed
 * by real touches across its row, and both read back by a recreated repository and a new ViewModel,
 * the way a restart reads them. Since -707 the rows are reached with one tap, Tools, and Settings
 * no longer shows them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SundownSettingsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun context() = ApplicationProvider.getApplicationContext<Application>()
    private fun dataStoreFile() = File(context().filesDir, "datastore/sundown_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreSundownPreferencesRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreSundownPreferencesRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    private val stored = mutableListOf<Int>()
    private val errors = mutableListOf<String>()
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("sundown-settings-map")) }

    private fun viewModelOver(repository: DataStoreSundownPreferencesRepository) = mapLayersViewModel(
        getSundownAlertsEnabled = repository::getAlertsEnabled,
        setSundownAlertsEnabled = repository::setAlertsEnabled,
        getDarknessMarginMinutes = repository::getDarknessMarginMinutes,
        setDarknessMarginMinutes = repository::setDarknessMarginMinutes,
        onDarknessMarginStored = { stored += it },
        errorLog = { _, message, error -> errors += "$message $error" },
    )

    private fun setScreen(viewModel: AvailabilityViewModel) {
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            AvailabilityScreen(
                uiState = state,
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
                    onSundownAlertsEnabledChanged = viewModel::onSundownAlertsEnabledChanged,
                    onDarknessMarginChanged = viewModel::onDarknessMarginChanged,
                ),
                mapSlot = map,
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { 1_700_000_000_000L },
            )
        }
        composeRule.waitForIdle()
        // Opening Tools is not the claim here; the touches on the Sundown rows are. One tap since
        // dispatch 2026-09-28-707, where it was Tools then Settings.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
    }

    private fun checked(tag: String): Boolean {
        // The row merges the checkbox's state into its own semantics.
        val node = composeRule.onNodeWithTag(tag, useUnmergedTree = false).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On ||
            node.children.any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
    }

    private fun selected(minutes: Int): Boolean {
        val node = composeRule.onNodeWithTag(darknessMarginTag(minutes)).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.Selected) == true ||
            node.children.any { it.config.getOrNull(SemanticsProperties.Selected) == true }
    }

    /** A real touch at [fraction] across the row's width, at its vertical centre. */
    private fun touchRow(tag: String, fraction: Float) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTouchInput { click(Offset(width * fraction, height / 2f)) }
        composeRule.waitForIdle()
    }

    /**
     * Waits for [condition], running Robolectric's main looper on every pass.
     *
     * Not `composeRule.waitUntil`: in this harness that only advances Compose's own test clock and
     * sleeps; it never runs the paused main looper. The ViewModel stores a setting in
     * `viewModelScope` (Dispatchers.Main), DataStore writes it on its IO scope, and the rest of the
     * coroutine, which tells the watch, is posted back to the main looper. When the disk write
     * finishes after the last `waitForIdle`, that post sits unrun and `waitUntil` times out however
     * long it is given (CI run 37498429829; reproduced 17 of 40 with the test's files on a real
     * disk, 0 of 80 on tmpfs, the local default; see the sundown-line report, "The CI timeout").
     * Here the only thing waited for is the disk write itself.
     */
    private fun awaitOnMainLooper(timeoutMillis: Long, what: String, condition: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            mainLooper.idle()
            if (condition()) return
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("$what: not within $timeoutMillis ms (stored $stored, errors $errors)")
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun `the section reads as confirmed, alerts on and an hour selected by default`() {
        val (repository, _) = repository()
        setScreen(viewModelOver(repository))
        composeRule.onNodeWithTag(SUNDOWN_ALERTS_TAG).performScrollTo()
        assertTrue(composeRule.onAllNodesWithText("Sundown").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Sundown alerts").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Dark under trees").fetchSemanticsNodes().isNotEmpty())
        assertTrue(composeRule.onAllNodesWithText("Woods get dark before sunset. Alerts allow this much extra.").fetchSemanticsNodes().isNotEmpty())
        for (label in listOf("30 min", "45 min", "1 h", "1 h 30")) {
            assertTrue(label, composeRule.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty())
        }
        assertEquals(true, checked(SUNDOWN_ALERTS_TAG))
        assertEquals(listOf(false, false, true, false), listOf(30, 45, 60, 90).map(::selected))
    }

    @Test
    fun `real touches across each row change it, and both settings survive a recreated repository`() = runBlocking {
        val (repository, scope) = repository()
        setScreen(viewModelOver(repository))

        for (fraction in listOf(0.1f, 0.5f, 0.9f)) {
            val before = checked(SUNDOWN_ALERTS_TAG)
            touchRow(SUNDOWN_ALERTS_TAG, fraction)
            assertEquals("a touch at $fraction across the alerts row flips it", !before, checked(SUNDOWN_ALERTS_TAG))
        }
        assertEquals("three flips from on is off", false, checked(SUNDOWN_ALERTS_TAG))

        touchRow(darknessMarginTag(30), 0.9f)
        assertEquals(listOf(true, false, false, false), listOf(30, 45, 60, 90).map(::selected))
        touchRow(darknessMarginTag(45), 0.1f)
        assertEquals(listOf(false, true, false, false), listOf(30, 45, 60, 90).map(::selected))
        awaitOnMainLooper(5_000, "the watch told 45") { stored.lastOrNull() == 45 }
        assertEquals("the watch is told each stored margin", listOf(30, 45), stored)

        assertEquals(false, repository.getAlertsEnabled().getOrThrow())
        assertEquals(45, repository.getDarknessMarginMinutes().getOrThrow())
        scope.cancelAndJoin()

        val (recreated, _) = repository()
        assertEquals("alerts off survives", false, recreated.getAlertsEnabled().getOrThrow())
        assertEquals("45 min survives", 45, recreated.getDarknessMarginMinutes().getOrThrow())
        val next = viewModelOver(recreated)
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !(next.uiState.value.darknessMarginMinutes == 45 && !next.uiState.value.sundownAlertsEnabled)) {
            composeRule.waitForIdle()
            Thread.sleep(20)
        }
        assertEquals("a new ViewModel shows 45 min (state ${next.uiState.value.darknessMarginMinutes}, errors $errors)", 45, next.uiState.value.darknessMarginMinutes)
        assertEquals("and alerts off", false, next.uiState.value.sundownAlertsEnabled)
    }

    /**
     * Dispatch 2026-09-28-707, step 1: the section is near the top of the Tools page, the next thing
     * under the Trip Planner's one-line header (collapsed when the drawer opens), inside the open
     * drawer sheet and in its upper third, with the Off-track reminder as its last row.
     */
    @Test
    fun `Tools shows the Sundown section near the top, under the Trip Planner's header, with the off-track reminder in it`() {
        val (repository, _) = repository()
        setScreen(viewModelOver(repository))

        val sheet = composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).getBoundsInRoot()
        val section = composeRule.onNodeWithTag(TOOLS_SUNDOWN_SECTION_TAG).getBoundsInRoot()
        val tripPlanner = composeRule.onNodeWithText("Trip Planner").getBoundsInRoot()
        assertTrue("the section is inside the open sheet: $section in $sheet", section.left >= sheet.left && section.right <= sheet.right && section.top >= sheet.top)
        assertTrue("the section is under the Trip Planner's header: $section, $tripPlanner", section.top >= tripPlanner.bottom)
        assertTrue("and near the top, in the sheet's upper third: ${section.top} of $sheet", section.top - sheet.top <= sheet.height / 3)
        val offTrack = composeRule.onNodeWithTag(OFF_TRACK_REMINDER_TAG).getBoundsInRoot()
        assertTrue("the off-track reminder is in the section: $offTrack in $section", offTrack.top >= section.top && offTrack.bottom <= section.bottom)
    }

    /** Dispatch 2026-09-28-707, step 2: Settings no longer shows the section, its explanation or the off-track reminder. */
    @Test
    fun `Settings no longer shows the Sundown section or the off-track reminder`() {
        val (repository, _) = repository()
        setScreen(viewModelOver(repository))
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()

        // Settings is the page now showing: one of its own rows is there.
        assertTrue(composeRule.onAllNodesWithText("Night Maps").fetchSemanticsNodes().isNotEmpty())
        assertEquals(0, composeRule.onAllNodesWithTag(SUNDOWN_ALERTS_TAG).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithTag(OFF_TRACK_REMINDER_TAG).fetchSemanticsNodes().size)
        assertEquals(0, composeRule.onAllNodesWithText(DARK_UNDER_TREES_EXPLANATION).fetchSemanticsNodes().size)
        for (minutes in listOf(30, 45, 60, 90)) {
            assertEquals(0, composeRule.onAllNodesWithTag(darknessMarginTag(minutes)).fetchSemanticsNodes().size)
        }
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
