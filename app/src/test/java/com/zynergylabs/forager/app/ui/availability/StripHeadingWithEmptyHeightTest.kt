package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.CompassReading
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DeclinationProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.HeadingUncertainty
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.ShownHeight
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
 * RECORD -2022 (the owner: "Today's GPS height"): where the phone cannot convert (Unsupported, below Android 14), or until
 * the first conversion arrives, the strip shows the fix's GPS height as it did before #440, never "Alt unavailable". The
 * reason, RECORD -1885 and the 2026-11-03 heading check: on a 360 or 384 dp screen "Alt unavailable" is long enough that
 * RECORD -713's fit rule drops the heading from the strip.
 *
 * Through the real [AvailabilityScreen] and its real heading producer, fed a compass reading. The state is built by hand in
 * the two shapes the live path produces: `shownHeight` still null between the first fix and its conversion
 * ([AvailabilityLiveLocation.begin]), and [ShownHeight.Unavailable] from a converter that cannot convert.
 * AvailabilityScreenSeaLevelHeightTest drives both through the real ViewModel. The known-height case is the control: a
 * sea-level height different from the fix's own, so the strip is seen showing the shown height, not the GPS one.
 *
 * Native graphics, so the text is really measured: without it Robolectric measures "315° NW" as 8 px and "Alt unavailable"
 * as 16 px, the fit rule never drops anything, and every case here passes whatever the strip shows (runs 41731511319 and
 * 41734265220 on scratch/altitude-heading-probe*, against diagnostic run 41739744089 with native graphics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripHeadingWithEmptyHeightTest {

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
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 100.0, accuracyMeters = 8f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val compass = MutableSharedFlow<CompassReading?>(extraBufferCapacity = 4)

    private fun setScreen(uiState: AvailabilityUiState) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
                compassProvider = object : CompassProvider { override val heading: Flow<CompassReading?> = compass },
                computeTrueHeading = ComputeTrueHeadingUseCase(object : DeclinationProvider {
                    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
                }),
                currentTime = CurrentTimeProvider { t },
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
        composeRule.runOnIdle { check(compass.tryEmit(CompassReading(315f, HeadingUncertainty.Estimated(2f), t))) }
        composeRule.waitForIdle()
    }

    private fun texts(tag: String): List<String> =
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }

    /** The strip shows the heading beside [expectedHeight]; the failure message names everything the strip holds. */
    private fun assertHeadingShownBeside(expectedHeight: String) {
        val heading = texts(COMPASS_STRIP_HEADING_TAG)
        val height = texts(COMPASS_STRIP_ELEVATION_TAG)
        val state = "heading=$heading height=$height"
        assertEquals("the strip's height ($state)", listOf(expectedHeight), height)
        assertEquals("the strip's heading ($state)", listOf("315° NW"), heading)
    }

    /** 100 m: the fix's own GPS height. */
    private val gpsHeight = "328 ft"

    /** 121.6 m above sea level: what a conversion would show, kept apart from the GPS height. */
    private val seaLevelHeight = "399 ft"

    private val known = AvailabilityUiState(liveFix = fix, shownHeight = ShownHeight.Known(121.6, aboveRawMeters = 21.6))
    private val beforeConversion = AvailabilityUiState(liveFix = fix)
    private val cannotConvert = AvailabilityUiState(liveFix = fix, shownHeight = ShownHeight.Unavailable)

    @Test
    fun `control, 360 dp - with a sea-level height the strip shows it, and the heading`() {
        setScreen(known)
        assertHeadingShownBeside(seaLevelHeight)
    }

    @Test
    fun `360 dp - before the first conversion, the strip shows the GPS height, and the heading`() {
        setScreen(beforeConversion)
        assertHeadingShownBeside(gpsHeight)
    }

    @Test
    fun `360 dp - on a phone that cannot convert, the strip shows the GPS height, and the heading`() {
        setScreen(cannotConvert)
        assertHeadingShownBeside(gpsHeight)
    }

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `control, 384 dp - with a sea-level height the strip shows it, and the heading`() {
        setScreen(known)
        assertHeadingShownBeside(seaLevelHeight)
    }

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp - before the first conversion, the strip shows the GPS height, and the heading`() {
        setScreen(beforeConversion)
        assertHeadingShownBeside(gpsHeight)
    }

    @Test
    @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp - on a phone that cannot convert, the strip shows the GPS height, and the heading`() {
        setScreen(cannotConvert)
        assertHeadingShownBeside(gpsHeight)
    }
}
