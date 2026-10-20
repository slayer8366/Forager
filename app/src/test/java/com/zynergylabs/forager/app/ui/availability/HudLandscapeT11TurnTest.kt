package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-09-01 (RECORD -764, T11): the HUD after turns, with RECORD -759/-760's fix in place. -760's bug was the
 * strip keeping its landscape size through portrait and being half the window on the **second** turn to landscape. The HUD is
 * placed by a different path (`besideLandscapeSearchBar`, from constants, not a measured size), so this measures it on the first
 * and second turn from portrait while navigating, and once more after leaving navigation in landscape, to see whether any of
 * its bounds depend on the path taken. A measurement harness like [HudLandscapeT11Measurement]: its assertions are positive
 * controls only (the turn happened, the HUD is up). ROTATION_90 only: Robolectric sets it on every turn to `land`, and the
 * extra configuration dispatch a 270 turn needs (LandscapeSearchFixesTest) adds nothing the HUD's placement reads.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HudLandscapeT11TurnTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** The turn reaches the same Activity, as on the phone (MainActivity's configChanges; the LandscapeSearchFixesTest rig). */
    private val rotatesInPlace = object : org.junit.rules.ExternalResource() {
        override fun before() {
            val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
            val info = android.content.pm.ActivityInfo().apply {
                packageName = app.packageName
                name = ComponentActivity::class.java.name
                configChanges = android.content.pm.ActivityInfo.CONFIG_ORIENTATION or android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_SCREEN_LAYOUT or android.content.pm.ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                    android.content.pm.ActivityInfo.CONFIG_KEYBOARD_HIDDEN
            }
            Shadows.shadowOf(app.packageManager).addOrUpdateActivity(info)
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(rotatesInPlace).around(composeRule)

    private val t = 1_791_050_400_000L // 2026-10-03T18:00:00Z: no sundown line
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 3_000.0, accuracyMeters = 12.5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val origin = Waypoint(
        id = "origin", lat = 45.52 + 380.0 / 111_195.0, lng = -122.68, altitude = null, name = "Start", note = "",
        createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN,
    )

    private var returning = androidx.compose.runtime.mutableStateOf(true)

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_0)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = true,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = returning.value,
                    onToggleReturning = { returning.value = !returning.value },
                    navigationTarget = origin,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.52, -122.679), 390.0),
                ),
                compassProvider = object : CompassProvider {
                    override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(281f, HeadingUncertainty.Estimated(2f), 0L))
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
        settle()
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(3_000L)
        composeRule.waitForIdle()
    }

    private fun turnTo(qualifiers: String) {
        composeRule.activityRule.scenario.onActivity { RuntimeEnvironment.setQualifiers(qualifiers) }
        settle()
    }

    private fun boundsOf(tag: String): DpRect? {
        val found = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().takeIf { it.isNotEmpty() } ?: return null
        val d = composeRule.density.density
        return DpRect(
            (found.minOf { it.boundsInRoot.left } / d).dp, (found.minOf { it.boundsInRoot.top } / d).dp,
            (found.maxOf { it.boundsInRoot.right } / d).dp, (found.maxOf { it.boundsInRoot.bottom } / d).dp,
        )
    }

    private fun DpRect?.d() = if (this == null) "absent" else "[%.1f,%.1f %.1f,%.1f] %.1fx%.1f".format(left.value, top.value, right.value, bottom.value, (right - left).value, (bottom - top).value)

    private fun measure(step: String) {
        val root = composeRule.onAllNodes(isRoot()).fetchSemanticsNodes().first().boundsInRoot
        val d = composeRule.density.density
        println("T11|turn|font=${composeRule.density.fontScale}|$step|window|[${root.width / d}x${root.height / d}] rotation=${ShadowDisplay.getDefaultDisplay().rotation}")
        for (tag in listOf(NAVIGATION_HUD_TAG, SEARCH_ENTRY_BAR_TAG, "compass-elevation-strip", LANDSCAPE_BAR_STRIP_LINE_TAG, MAP_ICON_CLUSTER_TAG)) {
            println("T11|turn|font=${composeRule.density.fontScale}|$step|$tag|${boundsOf(tag).d()}")
        }
    }

    private fun turns() {
        setScreen()
        measure("0-portrait-navigating")
        turnTo(LANDSCAPE)
        assertEquals("positive control: the first turn reached ROTATION_90", Surface.ROTATION_90, ShadowDisplay.getDefaultDisplay().rotation)
        assertTrue("positive control: the HUD is up after the first turn", boundsOf(NAVIGATION_HUD_TAG) != null)
        measure("1-first-turn-to-landscape")
        turnTo(PORTRAIT)
        measure("2-back-to-portrait")
        turnTo(LANDSCAPE)
        assertTrue("positive control: the HUD is up after the second turn", boundsOf(NAVIGATION_HUD_TAG) != null)
        measure("3-second-turn-to-landscape")
        composeRule.runOnIdle { returning.value = false }
        settle()
        measure("4-landscape-return-stopped")
        composeRule.runOnIdle { returning.value = true }
        settle()
        measure("5-landscape-return-again")
    }

    @Test fun `font 1,0 the HUD after turns`() = turns()

    @Test @Config(fontScale = 2.0f) fun `font 2,0 the HUD after turns`() = turns()

    private companion object {
        const val PORTRAIT = "w384dp-h823dp-port-xxhdpi"
        const val LANDSCAPE = "w823dp-h384dp-land-xxhdpi"
    }
}
