package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.DpRect
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
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-13-02, RECORD -987 (the owner: "in landscape: tapping the coordinates in the strip changes it as expected,
 * but the change causes strip to change size, and the navigation HUD at the bottom changes along with it. When changing heading
 * coordinates to and from lat/long, can the strip stay the same size? It's currently shorter in lat/long"), through the real
 * [AvailabilityScreen] at 780 and 823 dp, font scale 1.0 and 2.0, navigating and not: two real touches on the coordinates switch
 * them to the other form and back, the text really changes form, and the strip's, the search bar's and (navigating) the
 * navigation card's bounds stay within 0.5 dp of where they were.
 */
abstract class LandscapeStripCoordinatesToggleTests(private val name: String) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val start = Waypoint(id = "origin", lat = 45.5135, lng = -122.67, altitude = null, name = "Start", note = "", createdAtEpochMillis = t, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private fun setScreen(navigating: Boolean) {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = navigating,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = navigating,
                    navigationTarget = if (navigating) start else null,
                    returnRoute = ReturnRoute.Ahead(LatLng(45.5135, -122.67), 245.0),
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
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private fun capture(navigating: Boolean, shot: String) {
        val bitmap = composeRule.onRoot().captureToImage()
        val dir = File("build/test-results/testDebugUnitTest/strip-coordinates-toggle-screens").apply { mkdirs() }
        val file = File(dir, "$name-${if (navigating) "navigating" else "strip"}-$shot.png")
        file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT|${file.absolutePath}|${bitmap.width}x${bitmap.height}")
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun coordinates(): String =
        composeRule.onNodeWithTag(COMPASS_STRIP_COORDINATES_TAG, useUnmergedTree = true).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)!!.joinToString()

    /** A real touch at the coordinates' centre, where a finger toggles them. */
    private fun tapCoordinates() {
        val c = bounds(COMPASS_STRIP_COORDINATES_TAG)
        val p = with(composeRule.density) { Offset(((c.left + c.right) / 2).toPx(), ((c.top + c.bottom) / 2).toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
    }

    private fun assertSame(what: String, before: DpRect, after: DpRect) {
        for ((edge, a, b) in listOf(
            Triple("left", before.left, after.left), Triple("top", before.top, after.top),
            Triple("right", before.right, after.right), Triple("bottom", before.bottom, after.bottom),
        )) {
            assertEquals("$what's $edge stays put when the coordinates switch form: before $before, after $after", a.value, b.value, 0.5f)
        }
    }

    protected fun toggle(navigating: Boolean) {
        setScreen(navigating)
        val tags = listOfNotNull(STRIP_TAG, SEARCH_ENTRY_BAR_TAG, if (navigating) NAVIGATION_HUD_TAG else null)
        val first = coordinates()
        val boundsFirst = tags.associateWith { bounds(it) }
        capture(navigating, "first")
        tapCoordinates()
        val second = coordinates()
        val boundsSecond = tags.associateWith { bounds(it) }
        capture(navigating, "second")
        tapCoordinates()
        val third = coordinates()
        val boundsThird = tags.associateWith { bounds(it) }
        println("MEASURED $name navigating=$navigating: '$first' -> '$second' -> '$third'; ${tags.map { "$it ${boundsFirst[it]} / ${boundsSecond[it]}" }}")
        val decimal = Regex("""^-?\d+\.\d{4}, -?\d+\.\d{4}$""")
        assertTrue("the text actually changes form: '$first' then '$second'", decimal.matches(first) != decimal.matches(second))
        assertEquals("a second tap switches back", first, third)
        for (tag in tags) {
            assertSame(tag, boundsFirst.getValue(tag), boundsSecond.getValue(tag))
            assertSame(tag, boundsFirst.getValue(tag), boundsThird.getValue(tag))
        }
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}

abstract class LandscapeStripCoordinatesToggleCases(name: String) : LandscapeStripCoordinatesToggleTests(name) {
    @Test fun `tapping the coordinates leaves the strip and the search bar where they were`() = toggle(navigating = false)

    @Test fun `navigating, tapping the coordinates leaves the strip, the search bar and the navigation card where they were`() = toggle(navigating = true)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripCoordinatesToggle780Font1Test : LandscapeStripCoordinatesToggleCases("780-font1")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripCoordinatesToggle780Font2Test : LandscapeStripCoordinatesToggleCases("780-font2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripCoordinatesToggle823Font1Test : LandscapeStripCoordinatesToggleCases("823-font1")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeStripCoordinatesToggle823Font2Test : LandscapeStripCoordinatesToggleCases("823-font2")
