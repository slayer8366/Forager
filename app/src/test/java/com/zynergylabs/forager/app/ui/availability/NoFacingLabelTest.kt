package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -728, the owner: "Keep the the 330° NW metric, just remove the word "facing" and nothing else." At 384 dp, the S22's
 * portrait width, where the strip and the navigation display keep their labels (the positive control: "Alt" is drawn), the
 * heading reads alone and "Facing" is drawn nowhere. [AvailabilityScreenNavigationWordsTest] checks the same at 360 dp, where
 * the labels drop for width anyway, so it could not tell "Facing" removed from "Facing" dropped; this class can.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoFacingLabelTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.52 + 380.0 / 111_195.0, lng = -122.68, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)

    private fun setScreen(navigating: Boolean) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = navigating,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = navigating,
                    navigationTarget = start.takeIf { navigating },
                    returnRoute = ReturnRoute.Pending,
                ),
                compassProvider = object : CompassProvider {
                    override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(315f, HeadingUncertainty.Estimated(2f), 0L))
                },
                computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                }),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) },
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
            )
        }
        composeRule.waitForIdle()
    }

    private fun textOf(tag: String): String =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun labelsIn(parentTag: String): List<String> =
        composeRule.onAllNodes(hasTestTag(NAVIGATION_LABEL_TAG) and hasAnyAncestor(hasTestTag(parentTag)), useUnmergedTree = true)
            .fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    private fun assertNoFacing() {
        assertTrue(
            "\"Facing\" is drawn nowhere",
            composeRule.onAllNodes(hasText("Facing", substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun `on the strip the heading reads alone, Alt is still drawn, and Facing is not`() {
        setScreen(navigating = false)
        assertEquals("the heading stays", "315° NW", textOf(COMPASS_STRIP_HEADING_TAG))
        assertEquals("positive control: labels fit here, and Alt is the only one", listOf(ALTITUDE_LABEL), labelsIn("compass-elevation-strip"))
        assertNoFacing()
    }

    // Dispatch 2026-10-12-08 (RECORD -950): navigating, the height and its "Alt" are on the strip's line, which stays; the
    // navigation info has no label. Was "on the navigation display the heading reads alone, Alt is still drawn, and Facing is not",
    // asserting `listOf(ALTITUDE_LABEL)` in NAVIGATION_HUD_TAG.
    @Test
    fun `navigating, the heading reads alone in the navigation info, Alt is drawn on the strip, and Facing is not`() {
        setScreen(navigating = true)
        assertEquals("the heading stays", "315° NW", textOf(NAVIGATION_HUD_HEADING_TAG))
        assertEquals("no label in the navigation info", emptyList<String>(), labelsIn(NAVIGATION_HUD_TAG))
        assertEquals("positive control: labels fit on the strip's line, and Alt is the only one", listOf(ALTITUDE_LABEL), labelsIn("compass-elevation-strip"))
        assertNoFacing()
    }
}
