package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.PAN_RECORDING_MAP_TAG
import com.zynergylabs.forager.app.ui.map.PanRecordingMapSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-18-03, item 1 (Forager RECORD -1269; the owner: "Remove both OK and Cancel and just have the Download Maps
 * option", then, to the step path, "Yes, exactly (Recommended)"). Elder's top-up audit, item 1: Download Maps was greyed until
 * OK, and nothing on screen said so.
 *
 * The step path: Records → Offline maps → pan the map to place the pin → tap Download Maps → the existing "Download this area?"
 * confirmation → it downloads, at wherever the pin sits. No OK and no Cancel on this screen, in either orientation.
 *
 * Driven through the real [AvailabilityScreen] (Journal, Records, the Offline maps chip, by semantic clicks: navigation, not a
 * claim about touch), its state held here as the ViewModel holds it, the map a [PanRecordingMapSlot] a real drag pans. Download
 * Maps is reached by real touches at window coordinates across its own bounds. Robolectric reports zero insets.
 */
abstract class OfflineDownloadMapsOnlyTests(private val rotation: Int?) {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val uiState = mutableStateOf(AvailabilityUiState())
    private val map = PanRecordingMapSlot(panTo = PANNED)
    private var rotationSeenByScreen: Int? = null

    /** What the download read when it started: the ViewModel reads the two text fields (AvailabilityViewModel.onDownloadOfflineMaps). */
    private val downloadsFrom = mutableListOf<Pair<String, String>>()

    private fun setScreen() {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            rotationSeenByScreen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = uiState.value,
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
                    // As AvailabilityViewModel does: synchronous updates of the two text fields.
                    onOfflineMapLatChanged = { uiState.value = uiState.value.copy(offlineMapLatText = it) },
                    onOfflineMapLngChanged = { uiState.value = uiState.value.copy(offlineMapLngText = it) },
                    onOfflineMapRadiusChanged = { uiState.value = uiState.value.copy(offlineMapRadiusKm = it, offlineMapRadiusTouched = true) },
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = { downloadsFrom += uiState.value.offlineMapLatText to uiState.value.offlineMapLngText },
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                ),
                mapSlot = map.slot,
            )
        }
        composeRule.waitForIdle()
        if (rotation != null) assertEquals("the screen must actually see the rotation this test is about", rotation, rotationSeenByScreen)
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(RecordsSubTab.OFFLINE_MAPS)).performClick()
        composeRule.waitForIdle()
        // Positive control: this is the offline-maps screen.
        composeRule.onNodeWithText("Download Maps").assertExists()
    }

    private fun bounds(node: SemanticsNodeInteraction): DpRect = node.getUnclippedBoundsInRoot()
    private fun download() = composeRule.onNodeWithText("Download Maps")
    private fun pinLine() = composeRule.onNodeWithText("Pin at:", substring = true)
    private fun dialogButton(label: String) = composeRule.onNode(hasText(label) and hasAnyAncestor(isDialog()))

    private fun touch(rect: DpRect, fx: Float, fy: Float) {
        val x = rect.left + (rect.right - rect.left) * fx
        val y = rect.top + (rect.bottom - rect.top) * fy
        composeRule.onRoot().performTouchInput { click(Offset(x.value * density, y.value * density)) }
        composeRule.waitForIdle()
    }

    /** Download Maps in view (portrait: it is under the controls, below the map), then its bounds. */
    private fun downloadRect(): DpRect {
        if (rotation == null) download().performScrollTo()
        composeRule.waitForIdle()
        return bounds(download())
    }

    private val fractions = listOf(0.5f to 0.5f, 0.15f to 0.2f, 0.85f to 0.2f, 0.15f to 0.8f, 0.85f to 0.8f)

    /**
     * Landscape's pinned part (the dispatch's item 1: "In landscape, the pinned bottom becomes 'Pin at: …', then Download Maps
     * (full width)"): under the scrolling part, the Pin at line, then Download Maps across the controls side, inside the row's
     * own padding (Spacing.lg, 16 dp, each side).
     */
    protected fun checkPinnedPart() {
        setScreen()
        val scroll = bounds(composeRule.onNodeWithTag(OFFLINE_PICKER_CONTROLS_SCROLL_TAG))
        val pin = bounds(pinLine())
        val button = bounds(download())
        assertTrue("the Pin at line $pin is under the scrolling part $scroll", pin.top >= scroll.bottom - 0.5.dp)
        assertTrue("Download Maps $button is under the Pin at line $pin", button.top >= pin.bottom - 0.5.dp)
        assertEquals("Download Maps starts at the controls side's padding", (scroll.left + 16.dp).value, button.left.value, 0.5f)
        assertEquals("Download Maps ends at the controls side's padding", (scroll.right - 16.dp).value, button.right.value, 0.5f)
        assertTrue("Download Maps lies inside the window", button.bottom <= bounds(composeRule.onRoot()).bottom)
    }

    @Test
    fun `no OK and no Cancel on the offline-maps screen`() {
        setScreen()
        composeRule.onAllNodes(hasText("OK"), useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("no OK on the offline-maps screen (found ${it.size})", it.isEmpty())
        }
        composeRule.onAllNodes(hasText("Cancel"), useUnmergedTree = true).fetchSemanticsNodes().let {
            assertTrue("no Cancel on the offline-maps screen (found ${it.size})", it.isEmpty())
        }
        // The pin's line stays: it is where the user reads where the download will be.
        pinLine().assertExists()
    }

    @Test
    fun `Download Maps is enabled with only a position, before anything is confirmed`() {
        setScreen()
        assertTrue("positive control: nothing has been confirmed", uiState.value.offlineMapLatText.isEmpty())
        download().assertIsEnabled()
    }

    @Test
    fun `after a pan, real touches on Download Maps open the confirmation, and its Download downloads where the pin sits`() {
        setScreen()
        composeRule.onNodeWithTag(PAN_RECORDING_MAP_TAG).performTouchInput { swipe(center, center - Offset(120f, 60f), 300) }
        composeRule.waitForIdle()
        pinLine().assertExists()
        composeRule.onNodeWithText(pinText(PANNED)).assertExists()

        val rect = downloadRect()
        for ((fx, fy) in fractions) {
            touch(rect, fx, fy)
            composeRule.onNodeWithText("Download this area?").assertIsDisplayed()
            dialogButton("Cancel").performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("Download this area?").assertDoesNotExist()
        }
        assertEquals("the confirmation comes first: no touch on Download Maps started a download", 0, downloadsFrom.size)

        touch(downloadRect(), 0.5f, 0.5f)
        dialogButton("Download").performClick()
        composeRule.waitForIdle()
        assertEquals("the dialog's Download started one download", 1, downloadsFrom.size)
        val (lat, lng) = downloadsFrom.single()
        assertEquals("the download read the pin's latitude", PANNED.lat, lat.toDouble(), 1e-9)
        assertEquals("the download read the pin's longitude", PANNED.lng, lng.toDouble(), 1e-9)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class OfflineDownloadMapsOnlyPortraitTest : OfflineDownloadMapsOnlyTests(rotation = null)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class OfflineDownloadMapsOnlyLandscape90Test : OfflineDownloadMapsOnlyTests(rotation = Surface.ROTATION_90) {
    @Test
    fun `in landscape the pinned part is the Pin at line, then Download Maps full width under it`() = checkPinnedPart()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land")
class OfflineDownloadMapsOnlyLandscape270Test : OfflineDownloadMapsOnlyTests(rotation = Surface.ROTATION_270) {
    @Test
    fun `in landscape the pinned part is the Pin at line, then Download Maps full width under it`() = checkPinnedPart()
}

private val PANNED = LatLng(45.7, -122.9)
private fun pinText(at: LatLng) = "Pin at: ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"
