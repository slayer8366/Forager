package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_ACTION_LABEL
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_MESSAGE
import com.zynergylabs.forager.app.domain.SettingsResetNotice
import com.zynergylabs.forager.app.ui.map.MapSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * RECORD -660/-661: after a corrupt settings file was reset, the real [AvailabilityScreen] shows the
 * one-time snackbar, which stays until dismissed and whose "Settings" opens Settings. Driven by a real
 * [SettingsResetNotice], collected the way `MainActivity` collects the container's. `MainActivity`
 * itself is not composed by any test in this repository, so its two lines of wiring are not reached.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SettingsResetSnackbarTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val notice = SettingsResetNotice()
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag("settings-reset-map")) }

    @Test
    fun `the snackbar shows once, stays, and its Settings opens Settings`() {
        notice.onSettingsReset("app_theme_preferences")
        setScreen()

        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().isNotEmpty() }
        assertFalse("cleared as it is shown, so it is shown once", notice.pending.value)
        composeRule.mainClock.advanceTimeBy(30_000L)
        assertEquals("still there after 30 s: it stays until dismissed", 1, composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().size)

        // The closed Tools drawer stays composed off screen with its own "Settings" row, so the action is
        // told apart by sitting beside the snackbar's message.
        composeRule.onNode(hasText(SETTINGS_RESET_ACTION_LABEL) and hasClickAction() and hasAnySibling(hasText(SETTINGS_RESET_MESSAGE)))
            .performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals("the snackbar is gone", 0, composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().size)
        // Settings' own row shows: "Night Maps", since "Lock camera to portrait", which this waited for, moved into the
        // camera's gear panel (dispatch 2026-09-28-707).
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("Night Maps").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `dismissed, it does not come back`() {
        notice.onSettingsReset("app_theme_preferences")
        setScreen()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithContentDescription("Dismiss").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(5_000L)

        assertEquals(0, composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().size)
        assertFalse(notice.pending.value)
    }

    @Test
    fun `nothing owed, nothing shown`() {
        setScreen()
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText(SETTINGS_RESET_MESSAGE).fetchSemanticsNodes().size)
    }

    private fun setScreen() {
        composeRule.setContent {
            val pending by notice.pending.collectAsState()
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
                    settingsResetNoticePending = pending,
                    onSettingsResetNoticeShown = notice::shown,
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = map,
                compassProvider = FixedCompass,
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { 1_700_000_000_000L },
            )
        }
        composeRule.waitForIdle()
    }

    private object FixedCompass : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(0f, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
