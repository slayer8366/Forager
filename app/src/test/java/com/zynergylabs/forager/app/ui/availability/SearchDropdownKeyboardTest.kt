package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.zynergylabs.forager.app.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Part 1 layout fixes, item 3 (Part 1's device check, check 8; planner message `2026-09-28-98`), and dispatch
 * 2026-09-28-722 (RECORD intent -722): the keyboard coming up shrinks the dropdown's viewport, and the panel
 * scrolls so its bottom row stays in view just above it.
 *
 * Before dispatch 2026-09-28-697 the coordinates were the dropdown's last row, and T3a/T3b tested that the
 * end of the panel was kept in view as the viewport changed, and that a user drag stopped it. -697 put the
 * coordinates first and removed that keep-in-view with the scroll-to-the-end on open, so nothing lifted the
 * panel's end above the keyboard any more; its end is now Set on map and Search. The owner, verbatim: "the
 * search menu doesn't bounce back up when the keyboard hits it. It used to do that. The search button is
 * still hidden as a result." -722 restores the keep-in-view on a shrink only, and these tests restore the
 * old T3a/T3b claims for the new layout: after a shrink the bottom row is in view and takes real touches
 * (T3a); after a user drag a later shrink leaves the scroll alone (T3b); with no shrink the panel opens at
 * its top, coordinates first, as -697 made it (T3c).
 *
 * Robolectric reports no keyboard, so the keyboard's own inset cannot be seen here: the viewport is shrunk
 * by the test, as the keyboard shrinks it on the phone. Whether the dropdown's cap follows the real
 * keyboard is a device item. A component test, not the real screen, for that reason: the screen's height
 * cannot change under Robolectric without a keyboard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class SearchDropdownKeyboardTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private var viewport by mutableStateOf(800.dp)
    private var searches = 0
    private var setOnMaps = 0

    private fun setDropdown() {
        composeRule.setContent {
            Box(Modifier.height(viewport)) {
                SearchDropdown(
                    uiState = AvailabilityUiState(),
                    distanceUnit = DistanceUnit.KILOMETERS,
                    // Search's own callback since RECORD -700 ("Use current location" renamed and moved).
                    onUseCurrentLocation = { searches++ },
                    onRecentSearchSelected = {},
                    currentTime = CurrentTimeProvider { 0L },
                    onManualLatChanged = {},
                    onManualLngChanged = {},
                    onSearchManualCoordinates = {},
                    onRadiusChanged = {},
                    onMonthSelected = {},
                    onSetOnMap = { setOnMaps++ },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    private fun shrinkTo(heightDp: Int) {
        viewport = heightDp.dp
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
    }

    /** How far the Latitude field's top sits below the dropdown's top: the panel's own padding when it is not scrolled. */
    private fun latitudeOffsetFromTop(): Dp {
        val panelTop = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot().top
        val latitudeTop = composeRule.onNodeWithTag(SEARCH_DROPDOWN_LATITUDE_TAG).getUnclippedBoundsInRoot().top
        return latitudeTop - panelTop
    }

    /**
     * How far the bottom row's bottom sits above the dropdown's bottom. Scrolled to its end, that is the panel's own padding
     * (16 dp) plus the 4 dp a 40 dp button leaves under itself in its 48 dp touch target: 20 dp, measured at the build.
     */
    private fun bottomRowGapToBottom(tag: String): Dp {
        val panelBottom = composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).getUnclippedBoundsInRoot().bottom
        val rowBottom = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot().bottom
        return panelBottom - rowBottom
    }

    /** The panel's padding plus the button's touch-target margin below it; see [bottomRowGapToBottom]. */
    private val SCROLLED_TO_END_GAP = Spacing.lg + 4.dp

    /** Samples across a control's own bounds, as fractions of its width and height: a finger is not a point. */
    private val touchSamples = listOf(0.5f to 0.5f, 0.15f to 0.25f, 0.85f to 0.25f, 0.15f to 0.75f, 0.85f to 0.75f)

    /** A real touch at screen coordinates, at ([fx], [fy]) of the tagged node's bounds, with no scroll first. */
    private fun touchTagged(tag: String, fx: Float, fy: Float) {
        val b = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val at = Offset(b.left + b.width * fx, b.top + b.height * fy)
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput { click(at) }
        composeRule.waitForIdle()
    }

    /** Two drags back up to the panel's top, as the old T3b's user did. */
    private fun userDragsBackToTop() {
        repeat(2) {
            composeRule.onNodeWithTag(SEARCH_DROPDOWN_TAG).performTouchInput { swipeDown(startY = top + 40f, endY = bottom - 40f, durationMillis = 400) }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun `T3a when the viewport shrinks, the bottom row is lifted into view and takes real touches`() {
        setDropdown()
        assertEquals("opened at its top", Spacing.lg.value, latitudeOffsetFromTop().value, 0.5f)

        shrinkTo(360)

        // The panel does not fit in 360 dp, so showing the bottom row needed a scroll: Latitude has gone up past the top.
        assertTrue("the panel scrolled (Latitude ${latitudeOffsetFromTop()} from its top)", latitudeOffsetFromTop() < Spacing.lg)
        assertEquals("Search sits just above the panel's bottom", SCROLLED_TO_END_GAP.value, bottomRowGapToBottom(SEARCH_DROPDOWN_SEARCH_TAG).value, 0.5f)
        assertEquals("Set on map sits just above the panel's bottom", SCROLLED_TO_END_GAP.value, bottomRowGapToBottom(SEARCH_DROPDOWN_SET_ON_MAP_TAG).value, 0.5f)
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_SEARCH_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SEARCH_DROPDOWN_SET_ON_MAP_TAG).assertIsDisplayed()

        touchSamples.forEachIndexed { i, (fx, fy) ->
            touchTagged(SEARCH_DROPDOWN_SEARCH_TAG, fx, fy)
            assertEquals("a real touch at ($fx, $fy) of Search reached it", i + 1, searches)
        }
        touchSamples.forEachIndexed { i, (fx, fy) ->
            touchTagged(SEARCH_DROPDOWN_SET_ON_MAP_TAG, fx, fy)
            assertEquals("a real touch at ($fx, $fy) of Set on map reached it", i + 1, setOnMaps)
        }
        assertEquals("no touch on Set on map ran Search", touchSamples.size, searches)
    }

    @Test
    fun `T3b guard once the user drags the dropdown back up, later shrinks leave it where they put it`() {
        setDropdown()
        shrinkTo(360)
        assertEquals("the shrink lifted the bottom row first", SCROLLED_TO_END_GAP.value, bottomRowGapToBottom(SEARCH_DROPDOWN_SEARCH_TAG).value, 0.5f)

        userDragsBackToTop()
        assertEquals("the user's drag took it back to its top", Spacing.lg.value, latitudeOffsetFromTop().value, 0.5f)

        shrinkTo(420)
        shrinkTo(360)

        assertEquals("still where the user put it after a later shrink", Spacing.lg.value, latitudeOffsetFromTop().value, 0.5f)
        composeRule.onNodeWithText("Latitude").assertIsDisplayed()
    }

    @Test
    fun `T3c with no shrink the dropdown opens at its top, coordinates first`() {
        setDropdown()
        assertEquals("opened at its top at 800 dp", Spacing.lg.value, latitudeOffsetFromTop().value, 0.5f)
        composeRule.onNodeWithText("Latitude").assertIsDisplayed()
        composeRule.onNodeWithText("Longitude").assertIsDisplayed()
    }

    @Test
    fun `T3c a panel that opens short, with no shrink after, still opens at its top`() {
        viewport = 360.dp
        setDropdown()
        assertEquals("opened at its top at 360 dp: a first size is not a shrink", Spacing.lg.value, latitudeOffsetFromTop().value, 0.5f)
        composeRule.onNodeWithText("Latitude").assertIsDisplayed()
    }
}
