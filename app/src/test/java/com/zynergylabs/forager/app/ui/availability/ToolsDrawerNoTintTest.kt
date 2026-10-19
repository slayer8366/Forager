package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
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
import com.zynergylabs.forager.app.ui.map.MapSlot
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
 * Dispatch 2026-10-18-08 Amendment 1, RECORD -1313 (the owner: "When opening it, there should be no coloring of the app", "Portrait
 * too", "yes same behavior, just no added color"): the Tools drawer opens with no tint over the rest of the app, and a tap outside
 * it and Back still close it. Driven through the real [AvailabilityScreen] on the Maps tab, its map a stand-in filled with one
 * known colour, in portrait and in landscape.
 *
 * - **No tint, read from the drawn pixels:** a column of points outside the drawer sheet, each first confirmed to show the map's
 *   exact colour with the drawer closed (the positive control: a point that lands on other chrome is dropped, and at least
 *   [MIN_SAMPLES] must remain), is read again with the drawer open and must be unchanged. Material's default scrim, its scrim
 *   token colour at 32%, darkens every one of them.
 * - **A tap outside closes it:** a real touch at screen coordinates on one of those points.
 * - **Back closes it:** the activity's back dispatcher, as the system Back gesture reaches it.
 */
abstract class ToolsDrawerNoTintTests(private val landscape: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.background(MAP_COLOUR)) }

    private val navTag get() = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG

    private fun setScreen() {
        val rotation = if (landscape) Surface.ROTATION_90 else Surface.ROTATION_0
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                isRecording = false,
                isReturning = false,
                navigationTarget = null,
                returnRoute = ReturnRoute.Ahead(LatLng(45.5135, -122.67), 245.0),
                compassProvider = FixedCompass(134f),
                computeTrueHeading = ComputeTrueHeadingUseCase(NoDeclination),
                currentTime = CurrentTimeProvider { t + 1_000L },
                mapSlot = map,
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
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
            )
        }
        settle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
        assertTrue("positive control: ${if (landscape) "the rail" else "the bottom bar"} is shown", composeRule.onAllNodes(hasTestTag(navTag)).fetchSemanticsNodes().isNotEmpty())
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun tools(): SemanticsNodeInteraction =
        composeRule.onNode(hasText("Tools") and hasClickAction() and hasAnyAncestor(hasTestTag(navTag)))

    private fun touchAt(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        settle()
    }

    /** Opens the drawer with a real touch on Tools, and confirms it is open. */
    private fun openDrawer() {
        val b = tools().getUnclippedBoundsInRoot()
        touchAt((b.left + b.right) / 2, (b.top + b.bottom) / 2)
        composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsDisplayed()
        tools().assertIsSelected()
    }

    private fun assertDrawerClosed(how: String) {
        composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).assertIsNotDisplayed()
        tools().assertIsNotSelected()
        println("MEASURED landscape=$landscape: $how closed the drawer")
    }

    private fun root(): DpRect = composeRule.onRoot().getUnclippedBoundsInRoot()

    private fun capture(): Bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()

    private fun pixelAt(bitmap: Bitmap, x: Dp, y: Dp): Int = with(composeRule.density) {
        bitmap.getPixel(x.toPx().toInt().coerceIn(0, bitmap.width - 1), y.toPx().toInt().coerceIn(0, bitmap.height - 1))
    }

    /**
     * Points down the middle of the widest strip of window the open sheet leaves uncovered, each showing the map's own colour with
     * the drawer closed. The sheet's bounds are read with it open; it is then closed with Back and the points read.
     */
    private fun mapPointsOutsideTheSheet(): List<Pair<Dp, Dp>> {
        openDrawer()
        val sheet = composeRule.onNodeWithTag(TOOLS_DRAWER_SHEET_TAG).getUnclippedBoundsInRoot()
        val window = root()
        back()
        assertDrawerClosed("Back (setting up)")
        val leftGap = sheet.left - window.left
        val rightGap = window.right - sheet.right
        val x = if (leftGap > rightGap) (window.left + sheet.left) / 2 else (sheet.right + window.right) / 2
        assertTrue("the sheet $sheet leaves part of the window $window uncovered", maxOf(leftGap, rightGap).value > 8f)
        val closed = capture()
        val height = window.bottom - window.top
        val candidates = (1 until SAMPLE_ROWS).map { i -> x to window.top + height * (i.toFloat() / SAMPLE_ROWS) }
        val onMap = candidates.filter { (px, py) -> pixelAt(closed, px, py) == MAP_COLOUR.toArgb() }
        println("MEASURED landscape=$landscape: sheet $sheet in window $window; column x=$x; ${onMap.size} of ${candidates.size} points show the map")
        assertTrue("at least $MIN_SAMPLES points outside the sheet show the map's own colour with the drawer closed (${onMap.size})", onMap.size >= MIN_SAMPLES)
        return onMap
    }

    private fun back() {
        composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    private fun hex(argb: Int) = "#%08X".format(argb)

    protected fun drawsNoTint() {
        setScreen()
        val points = mapPointsOutsideTheSheet()
        openDrawer()
        val open = capture()
        for ((x, y) in points) {
            val seen = pixelAt(open, x, y)
            assertEquals(
                "with the drawer open, the map at ($x, $y) keeps its colour ${hex(MAP_COLOUR.toArgb())}; it reads ${hex(seen)}, " +
                    "a tint over the app behind the drawer",
                hex(MAP_COLOUR.toArgb()), hex(seen),
            )
        }
        println("MEASURED landscape=$landscape: ${points.size} points outside the open drawer kept the map's colour")
    }

    protected fun tapOutsideCloses() {
        setScreen()
        val points = mapPointsOutsideTheSheet()
        openDrawer()
        val (x, y) = points[points.size / 2]
        touchAt(x, y)
        assertDrawerClosed("a real touch at ($x, $y) outside the sheet")
    }

    protected fun backCloses() {
        setScreen()
        openDrawer()
        back()
        assertDrawerClosed("Back")
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        val MAP_COLOUR = Color(0xFF2E7D32)
        const val SAMPLE_ROWS = 16
        const val MIN_SAMPLES = 4
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ToolsDrawerNoTintPortraitTest : ToolsDrawerNoTintTests(landscape = false) {
    @Test fun `in portrait the open Tools drawer leaves the app behind it untinted`() = drawsNoTint()
    @Test fun `in portrait a real touch outside the Tools drawer closes it`() = tapOutsideCloses()
    @Test fun `in portrait Back closes the Tools drawer`() = backCloses()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class ToolsDrawerNoTintLandscapeTest : ToolsDrawerNoTintTests(landscape = true) {
    @Test fun `in landscape the open Tools drawer leaves the app behind it untinted`() = drawsNoTint()
    @Test fun `in landscape a real touch outside the Tools drawer closes it`() = tapOutsideCloses()
    @Test fun `in landscape Back closes the Tools drawer`() = backCloses()
}
