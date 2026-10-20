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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.data.repository.DataStoreOffTrackReminderPreferenceRepository
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
 * Tools > Sundown > "Off-track reminder" (in Settings until dispatch 2026-09-28-707) (dispatch 2026-09-28-626, plan T14; Amendment 1, RECORD -627), on
 * the real [AvailabilityScreen] and [AvailabilityViewModel] over the real DataStore repository: the
 * owner's words, on by default, flipped by real touches across the row, and read back by a
 * recreated repository, including the synchronous read the off-track rule uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class OffTrackReminderSettingsTest {

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
    private fun dataStoreFile() = File(context().filesDir, "datastore/off_track_reminder_preferences.preferences_pb")
    private val scopes = mutableListOf<Job>()

    private fun repository(): Pair<DataStoreOffTrackReminderPreferenceRepository, Job> {
        val job = SupervisorJob()
        scopes += job
        return DataStoreOffTrackReminderPreferenceRepository(context(), CoroutineScope(Dispatchers.IO + job)) to job
    }

    @Before fun setUp() { dataStoreFile().delete() }

    @After fun tearDown() {
        scopes.forEach { it.cancel() }
        dataStoreFile().delete()
    }

    private val errors = mutableListOf<String>()
    private val writes = java.util.concurrent.atomic.AtomicInteger()
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("off-track-settings-map")) }

    private fun viewModelOver(repository: DataStoreOffTrackReminderPreferenceRepository) = mapLayersViewModel(
        getOffTrackReminderEnabled = repository::getEnabled,
        setOffTrackReminderEnabled = { enabled -> repository.setEnabled(enabled).also { writes.incrementAndGet() } },
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
                    onOffTrackReminderChanged = viewModel::onOffTrackReminderChanged,
                ),
                mapSlot = map,
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { 1_700_000_000_000L },
            )
        }
        composeRule.waitForIdle()
        // Opening Tools is not the claim here; the touches on the row are. One tap since dispatch
        // 2026-09-28-707, where it was Tools then Settings.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.waitForIdle()
    }

    private fun checked(): Boolean {
        val node = composeRule.onNodeWithTag(OFF_TRACK_REMINDER_TAG).fetchSemanticsNode()
        return node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On ||
            node.children.any { it.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On }
    }

    /** A real touch at [fraction] across the row's width, at its vertical centre. */
    private fun touchRow(fraction: Float) {
        composeRule.onNodeWithTag(OFF_TRACK_REMINDER_TAG).performScrollTo().performTouchInput { click(Offset(width * fraction, height / 2f)) }
        composeRule.waitForIdle()
    }

    /** Runs the paused main looper while waiting, for the reason SundownSettingsTest.awaitOnMainLooper records. */
    private fun awaitOnMainLooper(timeoutMillis: Long, what: String, condition: () -> Boolean) {
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (true) {
            mainLooper.idle()
            if (condition()) return
            if (System.currentTimeMillis() >= deadline) throw AssertionError("$what: not within $timeoutMillis ms (errors $errors)")
            Thread.sleep(10)
        }
    }

    @Test
    fun `the row reads the owner's words, and is on by default`() {
        val (repository, _) = repository()
        setScreen(viewModelOver(repository))
        composeRule.onNodeWithTag(OFF_TRACK_REMINDER_TAG).performScrollTo()
        assertEquals(
            1,
            composeRule.onAllNodesWithText("Off-track reminder: your phone buzzes if you head away from your start.").fetchSemanticsNodes().size,
        )
        assertEquals(true, checked())
    }

    @Test
    fun `real touches across the row flip it, and off survives a recreated repository`() = runBlocking {
        val (repository, scope) = repository()
        setScreen(viewModelOver(repository))

        for (fraction in listOf(0.1f, 0.5f, 0.9f)) {
            val before = checked()
            touchRow(fraction)
            assertEquals("a touch at $fraction across the row flips it", !before, checked())
        }
        assertEquals("three flips from on is off", false, checked())
        // All three stores finished, not just the first: the first is already an off.
        awaitOnMainLooper(5_000, "all three stores finished") { writes.get() == 3 }
        assertEquals(false, repository.getEnabled().getOrThrow())
        scope.cancelAndJoin()

        val (recreated, _) = repository()
        assertEquals("off survives", false, recreated.getEnabled().getOrThrow())
        assertEquals("and the off-track rule's own read sees it", false, recreated.enabledNow())
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
