package com.zynergylabs.forager.app.ui.availability

import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Amendment 1 to motion Part 1 (RECORD -657; the owner: "Trim to its visible edge"): the tappable background-run prompt takes
 * touches on its drawn surface only. Material's `Snackbar(snackbarData)` draws its surface 12 dp inside the modifier it is given,
 * and the tap used to sit on that modifier, so it also caught a 12 dp band around the drawn snackbar, over the map. A touch-area
 * change, so it is proved both ways with real coordinate touches: 6 dp outside each of the four drawn edges (inside the old band)
 * a touch reaches the map and opens nothing; 4 dp inside each edge it opens App info, as before.
 *
 * Before the change the outside touches opened App info: that is the failure this test is built to show on a revert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SnackbarTrimmedTouchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val words = "To make sure your off-track reminder can buzz, let Forager run in the background"
    private var prompt by mutableStateOf<RecordingNotice?>(null)
    private var mapTaps = 0
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag("prompt-map").clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { mapTaps++ })
    }

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

    /** The drawn surface: the tagged box now lies exactly over it. */
    private fun surface(): DpRect = with(composeRule.density) {
        composeRule.onNodeWithTag(COMPACT_SNACKBAR_TAG).fetchSemanticsNode().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
    }

    private fun show(id: Int) {
        composeRule.runOnIdle { prompt = RecordingNotice(id = id, message = words) }
        composeRule.waitForIdle()
        assertEquals("the prompt is up", 1, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
    }

    private fun edgePoints(s: DpRect, offset: Dp): List<Pair<String, Pair<Dp, Dp>>> {
        val cx = (s.left + s.right) / 2
        val cy = (s.top + s.bottom) / 2
        return listOf(
            "left" to (s.left + offset to cy),
            "right" to (s.right - offset to cy),
            "top" to (cx to s.top + offset),
            "bottom" to (cx to s.bottom - offset),
        )
    }

    @Test
    fun `a touch 6 dp outside each drawn edge reaches the map and opens nothing`() {
        setScreen()
        show(1)
        val s = surface()
        val root = with(composeRule.density) {
            composeRule.onAllNodes(androidx.compose.ui.test.isRoot()).fetchSemanticsNodes().first().boundsInRoot.let { DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp()) }
        }
        // An edge the snackbar shares with the screen has no "beside" to touch: a point off the screen is no sample.
        val points = edgePoints(s, (-6).dp).filter { (_, at) -> at.first >= root.left && at.first <= root.right && at.second >= root.top && at.second <= root.bottom }
        assertTrue("at least the top and bottom edges are sampled (${points.map { it.first }})", points.size >= 2)
        points.forEach { (edge, at) ->
            val before = mapTaps
            composeRule.touchAt(at.first, at.second)
            assertEquals("6 dp outside the $edge edge (${at.first.value}, ${at.second.value}) of ${s.describe()} the map took the touch", before + 1, mapTaps)
            assertEquals("and nothing was opened", null, startedActivity())
            assertEquals("the prompt is still up", 1, composeRule.onAllNodesWithText(words).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a touch 4 dp inside each drawn edge opens App info`() {
        setScreen()
        edgePoints(DpRect(0.dp, 0.dp, 0.dp, 0.dp), 0.dp).indices.forEach { i ->
            show(i + 1)
            val (edge, at) = edgePoints(surface(), 4.dp)[i]
            val before = mapTaps
            composeRule.touchAt(at.first, at.second)
            assertEquals("4 dp inside the $edge edge opens App info", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, startedActivity()?.action)
            assertEquals("and the map did not take it", before, mapTaps)
        }
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
