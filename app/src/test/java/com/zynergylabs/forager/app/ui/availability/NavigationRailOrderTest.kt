package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
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
import org.robolectric.shadows.ShadowDisplay

/**
 * Dispatch 2026-10-18-08, RECORD -1312 (the owner: "List is on the left/bottom always"): in landscape the navigation rail reads
 * List at the bottom up to Tools at the top, whichever way the phone is turned, on a phone and on a tablet. Driven through the real
 * [AvailabilityScreen] at a pinned display rotation, which each test first proves the screen saw.
 *
 * - **Order on screen:** on Maps (the rail over the map) and on List, Seasonal and Journal (the rail beside the content), each
 *   item's bounds lie wholly above the one before it in [BOTTOM_TO_TOP]. Tools opens a drawer over the tab it was chosen from,
 *   not a screen of its own, so it has no case here.
 * - **A finger on List at the bottom opens List:** real touches at screen coordinates, five points across List's own bounds
 *   (a finger is not a point, CLAUDE.md), each from the Maps tab, each selecting List and taking the map off screen. A semantic
 *   click would pass even if something covered List (CLAUDE.md, Testing).
 * - **The semantics tree runs in the same order as the screen:** the rail's five clickable items, read in tree order, are Tools
 *   first and List last. That is the order a screen reader is given; what TalkBack actually speaks is a device item.
 *
 * Every expected order here is a literal, not [RAIL_ORDER_TOP_TO_BOTTOM], so a change to that list fails these tests.
 */
abstract class NavigationRailOrderTests(private val rotation: Int) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val t = 1_700_000_000_000L
    private val fix = LocationFix.Update(lat = 45.52, lng = -122.68, altitude = 104.9, accuracyMeters = 5f, timestampEpochMillis = t, provider = FixProvider.GPS)
    private val map: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier.testTag(ORDER_MAP_TAG).background(Color(0xFF2E7D32))) }

    private fun setScreen() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        var rotationSeen: Int? = null
        composeRule.setContent {
            rotationSeen = LocalView.current.display?.rotation
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
        settle()
        assertEquals("the screen must see the pinned rotation", rotation, rotationSeen)
    }

    private fun settle() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun item(label: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(label) and hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)))

    private fun itemBounds(label: String): DpRect = item(label).getUnclippedBoundsInRoot()

    private fun mapShown(): Boolean = composeRule.onAllNodes(hasTestTag(ORDER_MAP_TAG)).fetchSemanticsNodes().isNotEmpty()

    /** A real touch at screen coordinates. */
    private fun touchAt(x: Dp, y: Dp) {
        val p = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(p) }
        settle()
    }

    private fun touchCentre(label: String) {
        val b = itemBounds(label)
        touchAt((b.left + b.right) / 2, (b.top + b.bottom) / 2)
    }

    private fun assertBottomToTop(where: String) {
        val bounds = BOTTOM_TO_TOP.map { it to itemBounds(it) }
        println("MEASURED rotation=$rotation $where: " + bounds.joinToString { (l, b) -> "$l top=${b.top.value} bottom=${b.bottom.value}" })
        for ((lower, upper) in bounds.zipWithNext()) {
            assertTrue(
                "$where: ${upper.first} (bottom ${upper.second.bottom}) lies wholly above ${lower.first} (top ${lower.second.top}); " +
                    "the rail reads List at the bottom up to Tools at the top",
                upper.second.bottom <= lower.second.top,
            )
        }
    }

    protected fun orderOverTheMap() {
        setScreen()
        item("Maps").assertIsSelected()
        assertTrue("positive control: the Maps tab shows the map", mapShown())
        assertBottomToTop("over the map")
    }

    protected fun orderBeside(tab: String) {
        setScreen()
        touchCentre(tab)
        item(tab).assertIsSelected()
        assertBottomToTop("beside the $tab tab")
    }

    /** The centre and four points 30% of the way out toward the item's corners. */
    private fun samples(b: DpRect): List<Pair<Dp, Dp>> {
        val cx = (b.left + b.right) / 2
        val cy = (b.top + b.bottom) / 2
        val dx = (b.right - b.left) * 0.3f
        val dy = (b.bottom - b.top) * 0.3f
        return listOf(cx to cy, cx - dx to cy - dy, cx + dx to cy - dy, cx - dx to cy + dy, cx + dx to cy + dy)
    }

    protected fun realTouchOnListAtTheBottomOpensList() {
        setScreen()
        val others = listOf("Seasonal", "Maps", "Journal", "Tools").map { itemBounds(it) }
        val list = itemBounds("List")
        assertTrue("List ($list) is the lowest item on the rail (others $others)", others.all { it.bottom <= list.top })
        var touched = 0
        for ((x, y) in samples(list)) {
            if (touched > 0) {
                touchCentre("Maps")
                item("Maps").assertIsSelected()
                assertTrue("back on Maps the map shows again", mapShown())
            }
            touchAt(x, y)
            item("List").assertIsSelected()
            assertTrue("a touch at ($x, $y) on List opened List: the map is off screen", !mapShown())
            touched++
        }
        assertEquals("every sampled touch ran", 5, touched)
    }

    protected fun semanticsOrderMatchesTheScreen() {
        setScreen()
        val inTreeOrder = composeRule.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)))
            .fetchSemanticsNodes()
            .map { node -> node.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text } }
        assertEquals("the rail's items in semantics tree order, top to bottom", BOTTOM_TO_TOP.reversed(), inTreeOrder)
    }

    private class FixedCompass(degrees: Float) : CompassProvider {
        override val heading: Flow<CompassReading?> = MutableStateFlow(CompassReading(degrees, HeadingUncertainty.Estimated(2f), 0L))
    }

    private object NoDeclination : DeclinationProvider {
        override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float = 0f
    }

    private companion object {
        /** RECORD -1312: the rail from the bottom up, the bar's left-to-right order. */
        val BOTTOM_TO_TOP = listOf("List", "Seasonal", "Maps", "Journal", "Tools")
        const val ORDER_MAP_TAG = "rail-order-map"
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class NavigationRailOrderPhoneRotation90Test : NavigationRailOrderTests(Surface.ROTATION_90) {
    @Test fun `over the map the rail reads List at the bottom up to Tools at the top`() = orderOverTheMap()
    @Test fun `beside List the rail reads List at the bottom up to Tools at the top`() = orderBeside("List")
    @Test fun `beside Seasonal the rail reads List at the bottom up to Tools at the top`() = orderBeside("Seasonal")
    @Test fun `beside Journal the rail reads List at the bottom up to Tools at the top`() = orderBeside("Journal")
    @Test fun `real touches on List at the bottom of the rail open List`() = realTouchOnListAtTheBottomOpensList()
    @Test fun `the rail's semantics order is the screen's, Tools first and List last`() = semanticsOrderMatchesTheScreen()
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class NavigationRailOrderPhoneRotation270Test : NavigationRailOrderTests(Surface.ROTATION_270) {
    @Test fun `over the map the rail reads List at the bottom up to Tools at the top`() = orderOverTheMap()
    @Test fun `beside List the rail reads List at the bottom up to Tools at the top`() = orderBeside("List")
    @Test fun `beside Seasonal the rail reads List at the bottom up to Tools at the top`() = orderBeside("Seasonal")
    @Test fun `beside Journal the rail reads List at the bottom up to Tools at the top`() = orderBeside("Journal")
    @Test fun `real touches on List at the bottom of the rail open List`() = realTouchOnListAtTheBottomOpensList()
    @Test fun `the rail's semantics order is the screen's, Tools first and List last`() = semanticsOrderMatchesTheScreen()
}

/** A landscape tablet (the size AvailabilityScreenTabletAsPhoneTest uses): the same rail, so the same order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land")
class NavigationRailOrderTabletTest : NavigationRailOrderTests(Surface.ROTATION_90) {
    @Test fun `over the map the rail reads List at the bottom up to Tools at the top`() = orderOverTheMap()
    @Test fun `beside List the rail reads List at the bottom up to Tools at the top`() = orderBeside("List")
    @Test fun `real touches on List at the bottom of the rail open List`() = realTouchOnListAtTheBottomOpensList()
}
