package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-658 (F6): Settings' checkbox and radio rows are now one shared row each, and
 * a touch anywhere across a row still reaches its setting. The tagged rows (Sundown alerts, Dark
 * under trees, Off-track reminder) keep their own coordinate-touch tests in `SundownSettingsTest`
 * and `OffTrackReminderSettingsTest`, in the Tools drawer since dispatch 2026-09-28-707; this covers
 * the untagged ones Settings still has: Night Mode's three choices, Night Maps, and Units' choices.
 * The photo-location and camera-lock checkboxes were here until -707 moved them into the camera's
 * gear panel; their touches across the row are `InAppCameraSettingsPanelTest`'s now.
 *
 * Real touches at three points across each row's width (a finger is not a point, CLAUDE.md), on
 * the real [AvailabilityScreen] reached through Tools > Settings. The reading is the callback the
 * screen's caller receives, with its value.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SettingsRowsTouchTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val calls = mutableListOf<String>()
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("settings-rows-map")) }

    @Test
    fun `every untagged settings row takes a touch anywhere across it`() {
        setScreen()

        AppThemeMode.entries.forEach { mode -> touchAcross(mode.label, "theme $mode") }
        touchAcross("Night Maps", "night maps ${!STATE.nightModeMaps}")
        UnitSystem.entries.forEach { system -> touchAcross(system.label, "units ${system.distanceUnit}") }
    }

    /** Three real touches across the row labelled [label]; each must reach the caller as [expected]. */
    private fun touchAcross(label: String, expected: String) {
        FRACTIONS.forEach { fraction ->
            calls.clear()
            row(label).performScrollTo().performTouchInput { click(Offset(width * fraction, height / 2f)) }
            composeRule.waitForIdle()
            assertEquals("a touch at $fraction across '$label'", listOf(expected), calls)
        }
    }

    /** The row itself: its clickable merges the label into its own semantics. */
    private fun row(label: String): SemanticsNodeInteraction = composeRule.onNode(hasText(label) and hasClickAction())

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = STATE,
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
                    onNightModeMapsChanged = { calls += "night maps $it" },
                    onAutoSaveLocationToPhotosChanged = { calls += "photo location $it" },
                    onThemeModeChanged = { calls += "theme $it" },
                    onDistanceUnitSelected = { unit: DistanceUnit -> calls += "units $unit" },
                ),
                mapSlot = map,
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { 1_700_000_000_000L },
            )
        }
        composeRule.waitForIdle()
        // Navigation to Settings is not the claim here; the touches on the rows are.
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        val STATE = AvailabilityUiState()
        val FRACTIONS = listOf(0.05f, 0.5f, 0.95f)
    }
}
