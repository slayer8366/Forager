package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.PAN_RECORDING_MAP_TAG
import com.zynergylabs.forager.app.ui.map.PanRecordingMapSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The Offline maps region picker ([OfflineMapsPanel]'s [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]),
 * picker-fixes dispatch F2 (the diagnosis's M1) and F3 (M2). Driven through the real panel with its
 * [AvailabilityUiState] held in state, the real radius `Slider` moved by a real swipe, and the map a
 * [PanRecordingMapSlot] a real drag pans.
 *
 * F2 is recorded as **consistency, not an observed failure**: the owner, "Offline maps doesn't do
 * this currently, but it may as well echo the same behavior". The late centre is
 * `AvailabilityViewModel.onOfflineMapsOpened`'s location fetch, which can land up to 20 s after the
 * panel opens (`docs/audits/2026-09-27-offline-picker-recenter-diagnosis.md`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineMapsPanelPickerTest {

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

    private val uiState = mutableStateOf(AvailabilityUiState())
    private var picked: LatLng? = null
    private val map = PanRecordingMapSlot(panTo = PANNED)

    private fun setPanel() {
        composeRule.setContent {
            OfflineMapsPanel(
                uiState = uiState.value,
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
                mapSlot = map.slot,
                isNightMode = false,
                // As JournalTab/LogPanel wire it: the pick becomes the lat/lng text fields.
                onRegionPicked = { location ->
                    picked = location
                    uiState.value = uiState.value.copy(offlineMapLatText = location.lat.toString(), offlineMapLngText = location.lng.toString())
                },
                // As AvailabilityViewModel.onOfflineMapRadiusChanged does, less the clamp the Slider's range already holds.
                onOfflineMapRadiusChanged = { uiState.value = uiState.value.copy(offlineMapRadiusKm = it, offlineMapRadiusTouched = true) },
                onOfflineMapNameChanged = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
            )
        }
    }

    private fun panTheMap() {
        composeRule.onNodeWithTag(PAN_RECORDING_MAP_TAG).performTouchInput { swipe(center, center - Offset(120f, 60f), 300) }
        composeRule.onNodeWithText(pinText(PANNED)).assertExists()
    }

    private fun dragTheRadiusSlider() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .performScrollTo()
            .performTouchInput { swipe(Offset(width * 0.1f, centerY), Offset(width * 0.9f, centerY), 500) }
        composeRule.waitForIdle()
    }

    /**
     * Dispatch 2026-10-18-03 (Forager RECORD -1269): this screen has no OK; Download Maps picks the pin's point and asks. Was
     * `onNodeWithText("OK").performScrollTo().performClick()`. The confirmation is closed again by its own Cancel.
     */
    private fun confirm() {
        composeRule.onNodeWithText("Download Maps").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()
    }

    /** F2 (M1): after a pan, the device location arriving late moves neither the pin, nor the map, nor what Download Maps picks. */
    @Test
    fun `after a pan, a late device centre moves neither the pin, nor the map's region, nor what Download Maps picks`() {
        setPanel()
        composeRule.onNodeWithText(pinText(BUILT_IN_CENTRE)).assertExists()
        panTheMap()
        val regionsAtPan = map.regions.toList()

        composeRule.runOnIdle { uiState.value = uiState.value.copy(offlineMapPickerDefaultCenter = DEVICE) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(DEVICE)).assertDoesNotExist()
        composeRule.onNodeWithText(pinText(PANNED)).assertExists()
        assertEquals("no new region may reach the map after the pan", regionsAtPan, map.regions.toList())
        confirm()
        assertEquals(PANNED, picked)
    }

    /**
     * Dispatch 2026-10-18-07 (Forager RECORD -1309; the owner: "Offline maps displays topo maps. Have it display the street
     * map", then "Always be street."): the picker's map is handed Street, and only Street, from its first frame and after a
     * pan, by day and by night. Read from what the map slot was given, not from the panel's source.
     */
    @Test
    fun `the picker's map is always Street, by day and by night, before and after a pan`() {
        val night = mutableStateOf(false)
        composeRule.setContent {
            OfflineMapsPanel(
                uiState = uiState.value,
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { 0L },
                mapSlot = map.slot,
                isNightMode = night.value,
                onRegionPicked = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
            )
        }
        composeRule.waitForIdle()
        assertEquals(listOf(Basemap.OSM_STANDARD), map.basemaps.toList())
        panTheMap()
        composeRule.runOnIdle { night.value = true }
        composeRule.waitForIdle()
        assertTrue("the map must have been composed again at night", map.nightSeen)
        assertEquals(listOf(Basemap.OSM_STANDARD), map.basemaps.toList())
    }

    /** F2's other half: before any pan, the late device centre does move the picker (today's behaviour, kept). */
    @Test
    fun `before any pan, a late device centre moves the picker there`() {
        setPanel()
        composeRule.onNodeWithText(pinText(BUILT_IN_CENTRE)).assertExists()

        composeRule.runOnIdle { uiState.value = uiState.value.copy(offlineMapPickerDefaultCenter = DEVICE) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(pinText(DEVICE)).assertExists()
        assertEquals(Region(DEVICE.lat, DEVICE.lng, uiState.value.offlineMapRadiusKm), map.regions.last())
    }

    /**
     * F3 (M2): moving the radius after a pan keeps the picker on the panned point: the pin and OK keep
     * it, and the map is handed a region centred on it, at the new radius (so the zoom still follows
     * the radius, as it did before this fix).
     */
    @Test
    fun `after a pan, the radius slider keeps the panned centre for the pin, the map and Download Maps`() {
        setPanel()
        val radiusBefore = uiState.value.offlineMapRadiusKm
        panTheMap()

        dragTheRadiusSlider()
        val radiusAfter = uiState.value.offlineMapRadiusKm
        assertNotEquals("the slider must actually have moved", radiusBefore, radiusAfter)

        composeRule.onNodeWithText(pinText(BUILT_IN_CENTRE)).assertDoesNotExist()
        composeRule.onNodeWithText(pinText(PANNED)).assertExists()
        assertEquals(Region(PANNED.lat, PANNED.lng, radiusAfter), map.regions.last())
        confirm()
        assertEquals(PANNED, picked)
    }

    /** F3's other half: before any pan, the slider changes the zoom around the same centre, as today. */
    @Test
    fun `before any pan, the radius slider keeps today's centre`() {
        setPanel()
        val radiusBefore = uiState.value.offlineMapRadiusKm
        dragTheRadiusSlider()
        val radiusAfter = uiState.value.offlineMapRadiusKm
        assertNotEquals("the slider must actually have moved", radiusBefore, radiusAfter)

        composeRule.onNodeWithText(pinText(BUILT_IN_CENTRE)).assertExists()
        assertEquals(Region(BUILT_IN_CENTRE.lat, BUILT_IN_CENTRE.lng, radiusAfter), map.regions.last())
    }
}

/** `OFFLINE_MAP_PICKER_DEFAULT_CENTER`, the panel's built-in centre before any location (private there). */
private val BUILT_IN_CENTRE = LatLng(39.8283, -98.5795)
private val DEVICE = LatLng(45.6, -122.7)
private val PANNED = LatLng(45.7, -122.9)

private fun pinText(at: LatLng) = "Pin at: ${"%.4f".format(at.lat)}, ${"%.4f".format(at.lng)}"
