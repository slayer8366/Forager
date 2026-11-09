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

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onAllNodesWithTag

import androidx.compose.runtime.key
import androidx.compose.ui.test.onAllNodesWithTag

/**
 * RECORD -709 (the planner): in a landscape window the strip's three-dot button always gets its 36 dp, with a live fix
 * and the longest readouts (facing 315 degrees, 9843 ft, an MGRS reference), the state in which the first back-by build
 * measured it 0 dp wide at 780 x 360 dp: the readings column took all of the strip's capped width before the button was
 * measured. Through the real [AvailabilityScreen], data part B's own setup; real touches across the button's square,
 * corners included, each open the quick menu. The screen is composed afresh between touches, because under Robolectric
 * neither Back nor a map touch reaches the menu's popup window.
 */
abstract class LandscapeStripButtonTests {
    val composeRule = createComposeRule()
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
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
    private var route by mutableStateOf<ReturnRoute?>(null)
    private var generation by mutableStateOf(0)

    private fun setScreen(facing: Float, route: ReturnRoute?, navigating: Boolean = true) {
        this.route = route
        composeRule.setContent {
            key(generation) {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = navigating,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = navigating,
                    navigationTarget = start.takeIf { navigating },
                    returnRoute = this.route ?: ReturnRoute.Pending,
                ),
                compassProvider = FixedCompass(facing),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = map,
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
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `with the longest readouts the strip's button keeps its 36 dp and real touches across it open the menu`() {
        setScreen(facing = 315f, route = null, navigating = false)
        val strip = bounds("compass-elevation-strip")
        val button = bounds(MAP_QUICK_SETTINGS_BUTTON_TAG)
        assertEquals("36 dp wide: $button", 36f, (button.right - button.left).value, 0.5f)
        assertEquals("36 dp tall: $button", 36f, (button.bottom - button.top).value, 0.5f)
        assertTrue("at the strip's far right, inside it: $button in $strip", strip.right - button.right < 1.dp && button.left >= strip.left)
        val inset = 3.dp
        val points = listOf(
            (button.left + button.right) / 2 to (button.top + button.bottom) / 2,
            button.left + inset to button.top + inset,
            button.right - inset to button.top + inset,
            button.left + inset to button.bottom - inset,
            button.right - inset to button.bottom - inset,
        )
        for ((x, y) in points) {
            composeRule.touchAt(x, y)
            assertTrue("a real touch at (${x.value}, ${y.value}) in $button opened the menu", shown(MAP_QUICK_SETTINGS_MENU_TAG))
            composeRule.runOnIdle { generation++ }
            composeRule.waitForIdle()
            assertFalse("a fresh screen before the next touch", shown(MAP_QUICK_SETTINGS_MENU_TAG))
        }
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }
    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripButton780Test : LandscapeStripButtonTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripButton823Test : LandscapeStripButtonTests()
