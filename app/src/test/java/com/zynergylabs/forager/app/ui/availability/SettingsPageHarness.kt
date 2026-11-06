package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.MapSlot
import org.junit.rules.ExternalResource
import org.robolectric.Shadows

/**
 * The Settings page reached the way a person reaches it: [AvailabilityScreen], then "Tools", then
 * "Settings" (the same two taps `AvailabilityScreenSettingsPanelTest.openSettings` makes). The map
 * is a stub, as in that class: composing the real one starts the map library.
 */
internal object SettingsPageHarness {

    /** Robolectric needs the host activity declared before the compose rule launches it. */
    fun declareHostActivity(): ExternalResource = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    private val stubMap: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    fun openSettings(composeRule: AndroidComposeTestRule<*, ComponentActivity>) {
        setScreen(composeRule, AvailabilityUiState(region = Region(lat = 45.0, lng = -122.0, radiusKm = 15)))
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    /** The real screen showing [uiState], before any tap (dispatch 2026-10-13-05 added it, for the Open-Meteo credit's tests). */
    fun setScreen(composeRule: AndroidComposeTestRule<*, ComponentActivity>, uiState: AvailabilityUiState) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
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
                mapSlot = stubMap,
            )
        }
        composeRule.waitForIdle()
    }

    /** One of Settings' own rows: the check that the page showing is Settings. */
    const val SETTINGS_CONTROL_ROW = "Night Maps"
}
