package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.ui.map.HEADING_KEPT_FOR_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.assertEquals
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
 * RECORD -761 (the owner: "Keep last reading (Recommended)"), dispatch 2026-09-28-755 item 4: returning to Maps, the compass strip
 * showed "—" for about 200 ms on the S22 until the compass reported again. Through the real [AvailabilityScreen], every tab change
 * a real nav touch. The compass here reports only when the test says so, as a phone's would not for the first moments after a
 * return; the clock is the screen's own `currentTime`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HeadingKeptAcrossTabsTest {

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
    private var now = t
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 100.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val compass = MutableSharedFlow<CompassReading?>(extraBufferCapacity = 4)

    private fun setScreen() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                compassProvider = object : CompassProvider { override val heading: Flow<CompassReading?> = compass },
                computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                }),
                currentTime = CurrentTimeProvider { now },
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

    private fun heading(): String =
        composeRule.onNode(hasTestTag(COMPASS_STRIP_HEADING_TAG), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

    private fun report(degrees: Float) {
        composeRule.runOnIdle { check(compass.tryEmit(CompassReading(degrees, HeadingUncertainty.Estimated(2f), now))) }
        composeRule.waitForIdle()
    }

    private fun touchNav(label: String) {
        val b = composeRule.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_BOTTOM_NAV_TAG)), useUnmergedTree = true)
            .onFirst().fetchSemanticsNode().boundsInRoot
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(Offset(b.center.x, b.center.y)) }
        composeRule.waitForIdle()
    }

    private fun listAndBack(awayMillis: Long) {
        touchNav(CompactTab.LIST.label)
        now += awayMillis
        touchNav(CompactTab.MAP.label)
    }

    @Test
    fun `positive control - with no reading ever, the strip shows the dash, and a reading replaces it`() {
        setScreen()
        assertEquals(NO_HEADING_TEXT, heading())
        report(315f)
        assertEquals("315° NW", heading())
    }

    @Test
    fun `back on Maps after a short look at List, the strip shows the last reading until the compass reports, not the dash`() {
        setScreen()
        report(315f)
        listAndBack(awayMillis = 2_000L)
        assertEquals("the last reading, kept across the tab change", "315° NW", heading())
        report(90f)
        assertEquals("a new reading replaces it", "90° E", heading())
    }

    @Test
    fun `back on Maps after longer than the kept time, the strip shows the dash until the compass reports`() {
        setScreen()
        report(315f)
        listAndBack(awayMillis = HEADING_KEPT_FOR_MS + 1_000L)
        assertEquals("a stale reading is not shown", NO_HEADING_TEXT, heading())
        report(90f)
        assertEquals("90° E", heading())
    }
}
