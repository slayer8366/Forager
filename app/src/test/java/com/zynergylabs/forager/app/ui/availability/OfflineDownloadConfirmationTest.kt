package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.estimateServedOfflineTileCount
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.PanRecordingMapSlot
import com.zynergylabs.forager.app.domain.model.LatLng
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
 * "Download Maps" asks first (owner, 2026-09-29: "Approve the Download Maps wording as is";
 * `docs/plans/journal-redesign.md`, "'Download Maps' asks first: the approved copy"). Driven through the
 * real [OfflineMapsPanel] with a real tap on the button. The wording under test is the owner's, quoted:
 * title "Download this area?"; body "<name> · <radius> around the pin · about <N> tiles", without the
 * name part when there is none, the radius in the units setting; buttons "Cancel" and "Download".
 * Part 2 finding (c): before this, one tap on Download Maps started a real download.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineDownloadConfirmationTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val picked = LatLng(45.7, -122.9)
    private val uiState = mutableStateOf(AvailabilityUiState(offlineMapLatText = "45.7", offlineMapLngText = "-122.9", offlineMapRadiusKm = 16))
    private var downloads = 0

    private fun setPanel(unit: DistanceUnit = DistanceUnit.KILOMETERS) {
        composeRule.setContent {
            OfflineMapsPanel(
                uiState = uiState.value,
                distanceUnit = unit,
                currentTime = CurrentTimeProvider { 0L },
                mapSlot = PanRecordingMapSlot(panTo = picked).slot,
                isNightMode = false,
                onRegionPicked = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onDownloadOfflineMaps = { downloads++ },
                onDeleteOfflineRegion = {},
            )
        }
    }

    private fun tapDownloadMaps() {
        composeRule.onNodeWithText("Download Maps").performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /** The picker has its own Cancel under the map, so the confirmation's buttons are found inside the dialog. */
    private fun dialogButton(label: String) = composeRule.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    private fun tiles() = estimateServedOfflineTileCount(Region(picked.lat, picked.lng, 16))

    @Test
    fun `tapping Download Maps opens the confirmation and starts nothing`() {
        setPanel()

        tapDownloadMaps()

        composeRule.onNodeWithText("Download this area?").assertIsDisplayed()
        dialogButton("Cancel").assertIsDisplayed()
        dialogButton("Download").assertIsDisplayed()
        assertEquals("the tap itself downloads nothing", 0, downloads)
    }

    @Test
    fun `the body reads name, radius around the pin and the tile count, with the radius in kilometres`() {
        uiState.value = uiState.value.copy(offlineMapNameText = "Creek loop")
        setPanel(DistanceUnit.KILOMETERS)

        tapDownloadMaps()

        composeRule.onNodeWithText("Creek loop · 16 km around the pin · about ${tiles()} tiles").assertIsDisplayed()
    }

    @Test
    fun `the radius follows the units setting`() {
        uiState.value = uiState.value.copy(offlineMapNameText = "Creek loop")
        setPanel(DistanceUnit.MILES)

        tapDownloadMaps()

        // 16 km is 9.94 mi, shown as "10 mi" by formatDistanceKm (the 8 km default reads "5 mi").
        composeRule.onNodeWithText("Creek loop · 10 mi around the pin · about ${tiles()} tiles").assertIsDisplayed()
    }

    @Test
    fun `with no name the body starts at the radius`() {
        setPanel()

        tapDownloadMaps()

        composeRule.onNodeWithText("16 km around the pin · about ${tiles()} tiles").assertIsDisplayed()
    }

    @Test
    fun `a name of only spaces counts as no name`() {
        uiState.value = uiState.value.copy(offlineMapNameText = "   ")
        setPanel()

        tapDownloadMaps()

        composeRule.onNodeWithText("16 km around the pin · about ${tiles()} tiles").assertIsDisplayed()
    }

    @Test
    fun `Cancel closes the confirmation and downloads nothing`() {
        setPanel()
        tapDownloadMaps()

        dialogButton("Cancel").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Download this area?").assertDoesNotExist()
        assertEquals(0, downloads)
    }

    @Test
    fun `Download starts the existing download once and closes the confirmation`() {
        setPanel()
        tapDownloadMaps()

        dialogButton("Download").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Download this area?").assertDoesNotExist()
        assertEquals(1, downloads)
    }
}
