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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.ui.track.RecordingNotice
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
 * The off-track reminder's background prompt (dispatch 2026-09-28-626, plan T14; Amendment 1,
 * RECORD -627) on the real [AvailabilityScreen]: the owner's words in the map's Snackbar, and real
 * touches across the whole of it opening Forager's own App info page, the no-permission route. A
 * touch on an ordinary notice in the same host opens nothing, so the tap belongs to the prompt and
 * not to the host.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class BackgroundRunPromptTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val words = "To make sure your off-track reminder can buzz, let Forager run in the background"
    private var prompt by mutableStateOf<RecordingNotice?>(null)
    private var warning by mutableStateOf<RecordingNotice?>(null)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("prompt-map")) }

    private fun setScreen() {
        val viewModel = mapLayersViewModel()
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
                ),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                    tripStartWarning = warning,
                    backgroundRunPrompt = prompt,
                ),
                mapSlot = map,
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { 1_700_000_000_000L },
            )
        }
        composeRule.waitForIdle()
    }

    private fun startedActivity(): Intent? = Shadows.shadowOf(composeRule.activity).nextStartedActivity

    /** A real touch on the Snackbar at [xFraction] across and [yFraction] down its own bounds. */
    private fun touchSnackbar(xFraction: Float, yFraction: Float) {
        composeRule.onNodeWithTag(COMPACT_SNACKBAR_TAG).performTouchInput { click(Offset(width * xFraction, height * yFraction)) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the prompt shows the owner's words once, and nothing else is opened by showing it`() {
        setScreen()
        composeRule.runOnIdle { prompt = RecordingNotice(id = 1, message = words) }
        composeRule.waitForIdle()
        assertEquals(1, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
        assertEquals(null, startedActivity())
    }

    @Test
    fun `real touches across the prompt each open Forager's App info page`() {
        setScreen()
        val samples = listOf(0.05f to 0.5f, 0.5f to 0.5f, 0.95f to 0.5f, 0.5f to 0.1f, 0.5f to 0.9f)
        samples.forEachIndexed { i, (x, y) ->
            composeRule.runOnIdle { prompt = RecordingNotice(id = i + 1, message = words) }
            composeRule.waitForIdle()
            assertEquals("the prompt is up for touch $i", 1, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
            touchSnackbar(x, y)
            val intent = startedActivity()
            assertEquals("touch at ($x, $y) opens App info", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent?.action)
            assertEquals(Uri.fromParts("package", composeRule.activity.packageName, null), intent?.data)
            assertEquals("no new task: Back returns to the recording", 0, intent!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
            assertEquals("the prompt goes once tapped", 0, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a touch on an ordinary notice in the same host opens nothing`() {
        setScreen()
        composeRule.runOnIdle { warning = RecordingNotice(id = 1, message = "Your phone is silenced. If you go off track, the alert may not be felt.") }
        composeRule.waitForIdle()
        touchSnackbar(0.5f, 0.5f)
        assertEquals(null, startedActivity())
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
