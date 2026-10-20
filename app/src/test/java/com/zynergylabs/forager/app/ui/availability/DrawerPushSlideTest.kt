package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.motion.PageLeavingKey
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 3, Amendment 2 (dispatch 2026-09-28-676; RECORD -682, the owner: "Push slide from the left (Recommended)"): the Tools
 * drawer's pages, through the real [AvailabilityScreen] and real coordinate touches. Settings comes in from the drawer's left edge
 * while the Tools list goes out to the right, edge to edge, never overlapping; Back reverses it; the page going takes no touch.
 *
 * "Never overlapping" is read from the pages' own bounds mid-slide: the arriving page's right edge is where the leaving page's left
 * edge is. The pages' fill (none of their own, so the drawer's stays the one 80% layer) is not read here; MapChromeAlphaTest owns
 * the drawer's fill.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class DrawerPushSlideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private val mapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private fun setScreen() {
        composeRule.setContent {
            ForagerTheme {
                AvailabilityScreen(
                    uiState = AvailabilityUiState(),
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
                    mapSlot = mapSlot,
                )
            }
        }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun pause() {
        composeRule.mainClock.autoAdvance = false
    }

    private fun applyWrites() = composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }

    private fun frame() = composeRule.mainClock.advanceTimeByFrame()

    private fun stepUntil(what: String, maxFrames: Int = 40, condition: () -> Boolean) {
        repeat(maxFrames) {
            frame()
            if (condition()) return
        }
        throw AssertionError("never saw: $what")
    }

    private fun tapAt(x: Dp, y: Dp) {
        val at = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
    }

    private fun centreOf(b: DpRect) = Pair((b.left + b.right) / 2, (b.top + b.bottom) / 2)

    private fun tap(node: SemanticsNodeInteraction) {
        val (x, y) = centreOf(node.getUnclippedBoundsInRoot())
        tapAt(x, y)
    }

    private fun back() = composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }

    /** Every page frame's unclipped bounds, leaving or not. */
    private fun frames(leaving: Boolean): List<DpRect> {
        val all = composeRule.onAllNodes(SemanticsMatcher.expectValue(PageLeavingKey, leaving))
        return List(all.fetchSemanticsNodes().size) { i -> all[i].getUnclippedBoundsInRoot() }
    }

    private fun openTools(): DpRect {
        tap(composeRule.onNodeWithText("Tools"))
        settle()
        // The Tools page is the one settled page frame; its bounds are the drawer's page area.
        return frames(leaving = false).first()
    }

    @Test
    fun `Settings pushes in from the left edge while Tools goes out to the right, edge to edge, and Tools takes no touch`() {
        setScreen()
        val area = openTools()
        val close = composeRule.onNodeWithContentDescription("Close search options").getUnclippedBoundsInRoot()

        pause()
        tap(composeRule.onNodeWithText("Settings"))
        applyWrites()
        val (cx, cy) = centreOf(close)
        stepUntil("Tools part-way out to the right, the close row's centre still inside the drawer") {
            val leaving = frames(leaving = true)
            leaving.isNotEmpty() && leaving.first().left > area.left + 8.dp && cx + (leaving.first().left - area.left) < area.right - 4.dp
        }
        val leaving = frames(leaving = true).first()
        val arriving = frames(leaving = false).first { it.left < area.left - 1.dp }
        assertEquals("the pages meet edge to edge, never overlapping", leaving.left.value, arriving.right.value, 1f)

        // A real touch on the Tools page's close row, where it is drawn now. It is leaving, so it takes no touch and the drawer
        // stays open; had it taken the touch, the drawer would close and slide its pages off the left of the screen.
        tapAt(cx + (leaving.left - area.left), cy)
        applyWrites()
        frame()
        settle()
        val settled = frames(leaving = false).first()
        assertEquals("the drawer is still open, its page where Tools was", area.left.value, settled.left.value, 1f)
    }

    @Test
    fun `Back reverses it, Settings going out to the left while Tools comes back from the right`() {
        setScreen()
        val area = openTools()
        tap(composeRule.onNodeWithText("Settings"))
        settle()

        pause()
        back()
        applyWrites()
        stepUntil("Settings part-way out to the left") {
            val leaving = frames(leaving = true)
            leaving.isNotEmpty() && leaving.first().left < area.left - 8.dp && leaving.first().right > area.left + 8.dp
        }
        val leaving = frames(leaving = true).first()
        val arriving = frames(leaving = false).first { it.left > area.left + 1.dp }
        assertEquals("edge to edge on the way back too", leaving.right.value, arriving.left.value, 1f)
        settle()
        composeRule.onNodeWithContentDescription("Close search options").assertExists()
    }

    @Test
    fun `under reduced motion the drawer's pages change at once`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        setScreen()
        openTools()

        pause()
        tap(composeRule.onNodeWithText("Settings"))
        applyWrites()
        frame()
        frame()
        assertTrue("nothing leaving", frames(leaving = true).isEmpty())
        composeRule.onNodeWithContentDescription("Close search options").assertDoesNotExist()
    }
}
