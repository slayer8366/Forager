package com.zynergylabs.forager.app.ui.availability

import android.graphics.Bitmap
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.SemanticsNodeInteraction
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
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.io.File
import kotlin.math.abs
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
 * Dispatch 2026-10-13-02, RECORD -976 (the owner: "Landscape: the side navigation buttons (list, seasonal, maps, journal, tools)
 * is crammed on the bar, unevenly stacked between the top and the bottom. Can the buttons be evenly spaced?"): the rail's five
 * destinations spread evenly over its height, through the real [AvailabilityScreen], on the Maps tab (the rail over the map)
 * and on the List tab (the rail docked beside the content).
 *
 * - **Even:** the gaps between consecutive items' centres are equal within 1 dp. That alone held before the change too (the
 *   items were stacked a fixed gap apart from the top), so it is not the claim on its own.
 * - **Spread over the height:** the margin above the first item and the margin below the last are equal within 1 dp, and each
 *   at least [MIN_END_MARGIN]. Before the change the free height was all below the last item; this is the check that tells the
 *   two apart. (The end margins include the rail's own 4 dp padding, so they are that much more than the gaps between items.)
 * - **Still touchable:** real touches at screen coordinates, several per item across its own bounds (a finger is not a
 *   point), each selecting that item.
 *
 * Each state is also written as a PNG under the build's test-results directory, which CI uploads; nothing goes into the
 * repository.
 */
abstract class NavigationRailEvenSpacingTests(private val widthDp: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
    /** The rail's items top to bottom: Tools highest, List lowest (RECORD -1312; until then List to Tools). */
    private val labels = listOf("Tools", "Journal", "Maps", "Seasonal", "List")

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        composeRule.setContent {
            AvailabilityScreen(
                uiState = AvailabilityUiState(liveFix = fix),
                recording = AvailabilityScreenRecording(
                    isRecording = false,
                ),
                navigation = AvailabilityScreenNavigation(
                    isReturning = false,
                    navigationTarget = null,
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

    private fun item(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)))

    private fun itemBounds(label: String): DpRect = item(label).getUnclippedBoundsInRoot()

    private fun rail(): DpRect = composeRule.onNodeWithTag(COMPACT_NAVIGATION_RAIL_TAG).getUnclippedBoundsInRoot()

    private fun capture(shot: String) {
        val bitmap = composeRule.onRoot().captureToImage()
        val dir = File("build/test-results/testDebugUnitTest/navigation-rail-spacing-screens").apply { mkdirs() }
        val file = File(dir, "landscape-$widthDp-$shot.png")
        file.outputStream().use { bitmap.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT|${file.absolutePath}|${bitmap.width}x${bitmap.height}")
    }

    /** A real touch at screen coordinates, so what is tested is what a finger at that point reaches. */
    private fun touchAt(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun assertEvenlySpaced(where: String) {
        val rail = rail()
        val items = labels.map { itemBounds(it) }
        val centres = items.map { (it.top + it.bottom) / 2 }
        val gaps = centres.zipWithNext { a, b -> b - a }
        val topMargin = items.first().top - rail.top
        val bottomMargin = rail.bottom - items.last().bottom
        println(
            "MEASURED $widthDp $where: rail $rail; item tops ${items.map { it.top.value }}, heights ${items.map { (it.bottom - it.top).value }}; " +
                "centre gaps ${gaps.map { it.value }}; margin above the first ${topMargin.value}, below the last ${bottomMargin.value}",
        )
        for ((i, gap) in gaps.withIndex()) {
            assertEquals(
                "$where: the gap between ${labels[i]} and ${labels[i + 1]}'s centres equals the first gap, within 1 dp (gaps ${gaps.map { it.value }})",
                gaps.first().value, gap.value, 1f,
            )
        }
        assertTrue(
            "$where: the margin above ${labels.first()} (${topMargin.value} dp) equals the margin below ${labels.last()} " +
                "(${bottomMargin.value} dp) within 1 dp, so the items are spread over the rail's height, not packed toward one end",
            abs(topMargin.value - bottomMargin.value) <= 1f,
        )
        assertTrue("$where: ${labels.first()} keeps at least $MIN_END_MARGIN from the rail's top: ${topMargin.value} dp", topMargin >= MIN_END_MARGIN)
        assertTrue("$where: ${labels.last()} keeps at least $MIN_END_MARGIN from the rail's bottom: ${bottomMargin.value} dp", bottomMargin >= MIN_END_MARGIN)
    }

    /** The centre and four points 30% of the way out toward the item's corners. */
    private fun samples(b: DpRect): List<Pair<Dp, Dp>> {
        val cx = (b.left + b.right) / 2
        val cy = (b.top + b.bottom) / 2
        val dx = (b.right - b.left) * 0.3f
        val dy = (b.bottom - b.top) * 0.3f
        return listOf(cx to cy, cx - dx to cy - dy, cx + dx to cy - dy, cx - dx to cy + dy, cx + dx to cy + dy)
    }

    protected fun overTheMap() {
        setScreen()
        item("Maps").assertIsSelected()
        capture("maps")
        assertEvenlySpaced("over the map")
    }

    protected fun besideTheListTab() {
        setScreen()
        val list = itemBounds("List")
        touchAt((list.left + list.right) / 2, (list.top + list.bottom) / 2)
        item("List").assertIsSelected()
        capture("list")
        assertEvenlySpaced("beside the List tab")
    }

    /**
     * Each point of each item, in turn, moves the selection to that item: List, Seasonal, Journal and Maps alternate, so every
     * touch changes what is selected and a touch that missed its item cannot pass on an earlier one. Tools opens the drawer
     * (its highlight is the drawer being open), which then lies over the screen, so it is touched last, once, at its centre.
     */
    protected fun itemsTakeRealTouches() {
        setScreen()
        var touched = 0
        for (k in 0 until 5) {
            for (label in listOf("List", "Seasonal", "Journal", "Maps")) {
                val (x, y) = samples(itemBounds(label))[k]
                touchAt(x, y)
                item(label).assertIsSelected()
                touched++
            }
        }
        val (x, y) = samples(itemBounds("Tools"))[0]
        touchAt(x, y)
        item("Tools").assertIsSelected()
        touched++
        println("MEASURED $widthDp: $touched real touches, each selected its item")
        assertEquals("every sampled touch ran", 21, touched)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        val MIN_END_MARGIN = 8.dp
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationRailEvenSpacing780Test : NavigationRailEvenSpacingTests(widthDp = 780) {
    @Test fun `over the map the rail's five items are evenly spaced over its height`() = overTheMap()

    @Test fun `beside the List tab the rail's five items are evenly spaced over its height`() = besideTheListTab()

    @Test fun `each rail item takes real touches across its bounds`() = itemsTakeRealTouches()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationRailEvenSpacing823Test : NavigationRailEvenSpacingTests(widthDp = 823) {
    @Test fun `over the map the rail's five items are evenly spaced over its height`() = overTheMap()

    @Test fun `beside the List tab the rail's five items are evenly spaced over its height`() = besideTheListTab()

    @Test fun `each rail item takes real touches across its bounds`() = itemsTakeRealTouches()
}
