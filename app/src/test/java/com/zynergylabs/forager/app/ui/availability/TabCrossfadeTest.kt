package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import androidx.compose.runtime.snapshots.Snapshot
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.motion.TabChromeAlphaKey
import com.zynergylabs.forager.app.ui.motion.TabLeavingKey
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 2, items 1 and 2 (dispatch 2026-09-28-666; the owner, RECORD -651: tabs "Quick crossfade", the bottom bar "Fade
 * with the tab"), through [AvailabilityScreen] and real touches on the bottom bar.
 *
 * The map stand-in measures through a remembered measure policy that records every set of constraints it is measured with, so
 * "the live map must not re-measure" is read off the map itself: while it fades out it must not be measured again (the bar
 * that comes back would make the content area shorter), and while it fades in it must be measured at its final size from its
 * first measure (the solid bar that leaves must not hold its room). The cost of keeping the map alive through the fade on the
 * phone is not something this can show; the S22 judges it (RECORD -651).
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TabCrossfadeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    /** Every set of constraints the map was measured with, in order. */
    private val mapMeasures = mutableListOf<Constraints>()
    private var mapTaps = 0

    /** How many map stand-ins are composed: the leaving tab's has no semantics any more, so it is counted from the inside. */
    private var mapsComposed = 0

    private val mapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier ->
        DisposableEffect(Unit) {
            mapsComposed++
            onDispose { mapsComposed-- }
        }
        val policy = remember {
            MeasurePolicy { _, constraints ->
                mapMeasures += constraints
                layout(constraints.maxWidth, constraints.maxHeight) {}
            }
        }
        Layout(
            content = {},
            modifier = modifier
                .testTag(LAYOUT_FIXES_MAP_TAG)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { mapTaps++ },
            measurePolicy = policy,
        )
    }

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private fun setScreen() {
        // Under the app's theme, so the reduce-motion setting is provided (ForagerTheme's ProvideReduceMotion).
        composeRule.setContent { ForagerTheme { LayoutFixesScreen(uiState = LAYOUT_FIXES_FIX_STATE, mapSlot = mapSlot) } }
        settle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun root() = composeRule.onAllNodes(isRoot()).onFirst()

    private fun tapAt(p: DpOffset) {
        val at = with(composeRule.density) { Offset(p.x.toPx(), p.y.toPx()) }
        root().performTouchInput { click(at) }
    }

    /** A real touch on the bar's [label], the one bar showing (so taken at rest, never mid-fade). */
    private fun tapTab(label: String, paused: Boolean) {
        val b = composeRule.onAllNodesWithText(label).onFirst().getUnclippedBoundsInRoot()
        val centre = DpOffset((b.left + b.right) / 2, (b.top + b.bottom) / 2)
        if (paused) {
            composeRule.mainClock.autoAdvance = false
            tapAt(centre)
            // With the clock stopped, the tap's state write reaches the next frame only once applied (motion Part 1's report).
            composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
            repeat(FRAMES_INTO_FADE) { composeRule.mainClock.advanceTimeByFrame() }
        } else {
            tapAt(centre)
            settle()
        }
    }

    /** A map stand-in is composed (arriving, settled or leaving). */
    private fun mapShown() = mapsComposed > 0

    /** How many tabs are marked as leaving: the leaving tab keeps that one property and nothing else. */
    private fun tabsLeaving(): Int =
        composeRule.onAllNodes(SemanticsMatcher.expectValue(TabLeavingKey, true), useUnmergedTree = true).fetchSemanticsNodes().size

    /** The fade alphas of the solid bar (the one in the Scaffold's slot), leaving or arriving; portrait, so no rail. */
    private fun solidBarAlphas(): List<Float> =
        composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(TabChromeAlphaKey), useUnmergedTree = true)
            .fetchSemanticsNodes().map { it.config[TabChromeAlphaKey] }

    /** What a screen reader finds: the selected tabs named [label]. */
    private fun selectedTabsNamed(label: String): Int =
        composeRule.onAllNodes(hasText(label) and isSelected()).fetchSemanticsNodes().size

    @Test
    fun `leaving Maps, the map fades out without being measured again, while the solid bar fades in`() {
        setScreen()
        assertTrue(mapShown())
        val measuredBefore = mapMeasures.toList()
        val settledConstraints = measuredBefore.last()

        tapTab("List", paused = true)
        assertTrue("the map is still on screen, fading out", mapShown())
        assertEquals("one tab marked as leaving", 1, tabsLeaving())
        val duringFade = mapMeasures.drop(measuredBefore.size)
        assertTrue(
            "while it fades the map is measured with nothing but its own constraints ($settledConstraints), saw $duringFade",
            duringFade.all { it == settledConstraints },
        )
        val alphas = solidBarAlphas()
        assertEquals("the solid bar is there, arriving", 1, alphas.size)
        assertTrue("mid-fade it is part way in: ${alphas.single()}", alphas.single() > 0f && alphas.single() < 1f)

        settle()
        assertFalse("the map left with its tab", mapShown())
        assertEquals("and the bar is in, solid", 1f, solidBarAlphas().single(), 0.0001f)
    }

    @Test
    fun `mid-fade a touch on the arriving tab's bare background does not reach the leaving map`() {
        setScreen()
        val map = composeRule.onAllNodesWithTag(LAYOUT_FIXES_MAP_TAG).onFirst().getUnclippedBoundsInRoot()
        tapTab("List", paused = true)
        assertTrue(mapShown())
        val before = mapTaps
        // The middle of the window: the List tab draws its text there and takes no touch of its own.
        tapAt(DpOffset((map.left + map.right) / 2, (map.top + map.bottom) / 2))
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
        settle()
        assertEquals("the leaving map took no touch", before, mapTaps)
    }

    @Test
    fun `coming to Maps, the map is measured at its final size from its first measure while the solid bar fades out`() {
        setScreen()
        tapTab("List", paused = false)
        assertFalse(mapShown())
        mapMeasures.clear()

        tapTab("Maps", paused = true)
        assertTrue("the map is on screen, arriving", mapShown())
        val leavingBar = solidBarAlphas()
        assertEquals("the solid bar is still drawn, leaving", 1, leavingBar.size)
        assertTrue("part way out: ${leavingBar.single()}", leavingBar.single() > 0f && leavingBar.single() < 1f)

        settle()
        assertTrue("the solid bar is gone", solidBarAlphas().isEmpty())
        assertTrue("measured at all", mapMeasures.isNotEmpty())
        assertEquals("one size from first to last: the leaving bar held no room (${mapMeasures.distinct()})", 1, mapMeasures.distinct().size)
    }

    @Test
    fun `under reduced motion the tab changes at once and no tab is kept`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        setScreen()
        tapTab("List", paused = true)
        assertFalse("the map went at once", mapShown())
        assertEquals("the solid bar is in at once", listOf(1f), solidBarAlphas())
    }

    /**
     * Amendment 2: mid-fade, a screen reader finds one selected tab of each name, the arriving bar's, never the leaving bar or
     * the leaving tab's own bar. Both directions: to List (the Maps tab, with its own bar, leaving) and to Maps (the solid bar
     * leaving).
     */
    @Test
    fun `mid-fade a screen-reader query finds exactly one selected tab, the arriving one`() {
        setScreen()
        tapTab("List", paused = true)
        assertTrue("the Maps tab is still composed, fading out", mapShown())
        assertEquals("one selected List tab mid-fade", 1, selectedTabsNamed("List"))
        assertEquals("and no selected Maps tab left behind", 0, selectedTabsNamed("Maps"))
        settle()

        tapTab("Maps", paused = true)
        assertEquals("the solid bar is still drawn, leaving", 1, solidBarAlphas().count { it < 1f })
        assertEquals("one selected Maps tab mid-fade", 1, selectedTabsNamed("Maps"))
        assertEquals("and no selected List tab left behind", 0, selectedTabsNamed("List"))
    }

    private companion object {
        /** Frames let through after a tap with the clock stopped: enough for a fade to have started, not to have ended. */
        const val FRAMES_INTO_FADE = 4
    }
}
