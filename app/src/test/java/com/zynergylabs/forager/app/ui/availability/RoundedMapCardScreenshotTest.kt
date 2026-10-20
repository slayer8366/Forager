package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-12-06, "Robolectric screenshots of the card in portrait and landscape, navigating and not". Not a guard:
 * each test renders the real screen over a striped stand-in map (so the 80% fill shows the map through it) and writes a PNG
 * under the build's test-results directory, which CI uploads with the JUnit XML; the coder copies them out of the artifact to
 * the owner's evidence folder outside any repository. The one assertion is that the file was written and is not empty.
 * Robolectric reports zero window insets, so the status bar's place is not shown; the fit against it is a device item.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoundedMapCardScreenshotTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.5135, lng = -122.67, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private var route by mutableStateOf<ReturnRoute>(ReturnRoute.Ahead(LatLng(45.5135, -122.67), 245.0))

    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        Box(modifier.testTag("screenshot-map")) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Color(0xFFD8D4C8))
                var x = -size.height
                while (x < size.width) {
                    drawLine(Color(0xFF7A3FB0), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 18f)
                    x += 90f
                }
            }
        }
    }

    private fun shoot(name: String, rotation: Int?, returning: Boolean, dark: Boolean) {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        composeRule.setContent {
            ForagerTheme(darkTheme = dark) {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(liveFix = fix),
                    recording = AvailabilityScreenRecording(
                        isRecording = true,
                    ),
                    navigation = AvailabilityScreenNavigation(
                        isReturning = returning,
                        navigationTarget = start,
                        returnRoute = route,
                    ),
                    compassProvider = FixedCompass(134f),
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
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/test-results/testDebugUnitTest/rounded-map-strip-screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT|${file.absolutePath}|${bitmap.width}x${bitmap.height}")
        assertTrue("the screenshot $name was written", file.length() > 0)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `portrait 360, navigating, day`() = shoot("portrait-360-navigating-day", null, returning = true, dark = false)

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `portrait 360, navigating, night`() = shoot("portrait-360-navigating-night", null, returning = true, dark = true)

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `portrait 360, route unavailable with Try again, day`() {
        route = ReturnRoute.Unavailable(canRetry = true)
        shoot("portrait-360-try-again-day", null, returning = true, dark = false)
    }

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `portrait 360, not navigating, day`() = shoot("portrait-360-strip-day", null, returning = false, dark = false)

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `portrait 412, navigating, day`() = shoot("portrait-412-navigating-day", null, returning = true, dark = false)

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun `portrait 412, font 1,3, navigating, day`() = shoot("portrait-412-font13-navigating-day", null, returning = true, dark = false)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `landscape 780, navigating, day`() = shoot("landscape-780-navigating-day", Surface.ROTATION_90, returning = true, dark = false)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `landscape 780, not navigating, day`() = shoot("landscape-780-strip-day", Surface.ROTATION_90, returning = false, dark = false)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `landscape 780, navigating, night`() = shoot("landscape-780-navigating-night", Surface.ROTATION_90, returning = true, dark = true)

    // Dispatch 2026-10-12-08: "portrait and landscape, navigating and not, day and night". The two states the set lacked, at night.
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `portrait 360, not navigating, night`() = shoot("portrait-360-strip-night", null, returning = false, dark = true)

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `landscape 780, not navigating, night`() = shoot("landscape-780-strip-night", Surface.ROTATION_90, returning = false, dark = true)

    // The S22's landscape window, both states, for the owner's "Keep it exactly like this size" check (RECORD -951).
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `landscape 823, not navigating, day`() = shoot("landscape-823-strip-day", Surface.ROTATION_90, returning = false, dark = false)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `landscape 823, navigating, day`() = shoot("landscape-823-navigating-day", Surface.ROTATION_90, returning = true, dark = false)

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }
}
