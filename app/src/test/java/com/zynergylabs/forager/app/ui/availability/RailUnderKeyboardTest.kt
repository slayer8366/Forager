package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.zynergylabs.forager.app.ui.log.ENTRIES_HOME_TAG
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
 * Dispatch 2026-10-17-09, RECORD -1242 (the owner: "A: Ignore the keyboard, like Maps (Recommended)"; "Still do A
 * (Recommended)"). In a landscape window on a tab other than Maps, the docked rail keeps its full height while the keyboard is
 * up, and the keyboard covers its lower part, as on Maps. The page beside it still ends above the keyboard. On build 1.0.3416
 * the owner's S22 showed the rail squashed by a full keyboard: "Maps" clipped and the lower items hidden.
 *
 * Robolectric reports no keyboard and zero insets (CLAUDE.md, "Known pitfalls"). So this dispatches a keyboard inset (`ime`,
 * bottom) to the screen as real window insets once it is up. The positive control is the page shrinking by it: if the
 * injection did not reach the screen, the page's bottom would not move and the test fails on that, not on the rail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RailUnderKeyboardTest {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun open(label: String) {
        composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(COMPACT_NAVIGATION_RAIL_TAG)), useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
    }

    private fun raiseKeyboard(heightDp: Float) {
        val px = (heightDp * composeRule.activity.resources.displayMetrics.density).toInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        composeRule.runOnUiThread {
            ViewCompat.dispatchApplyWindowInsets(composeRule.activity.window.decorView.findViewById(android.R.id.content), insets)
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `with the keyboard up on the Journal, the rail keeps its full height and the page ends above the keyboard`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        val state = MapChromeScreenState()
        composeRule.setContent { MapChromeTestScreen(state, BubbleMapSlot(emptyList()), MapChromeRoles()) }
        composeRule.waitForIdle()
        open("Journal")
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val railBefore = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        val pageBefore = bounds(ENTRIES_HOME_TAG)
        val keyboard = 200f

        raiseKeyboard(keyboard)

        val page = bounds(ENTRIES_HOME_TAG)
        // Positive control: the injected keyboard reached the screen, so the page beside the rail ends above it.
        assertEquals(
            "the page ends above the keyboard (before: ${pageBefore.bottom}, window ${root.bottom})",
            (root.bottom.value - keyboard), page.bottom.value, 0.5f,
        )
        val rail = bounds(COMPACT_NAVIGATION_RAIL_TAG)
        assertEquals("the rail's top is unchanged by the keyboard", railBefore.top.value, rail.top.value, 0.5f)
        assertEquals("the rail's bottom is unchanged by the keyboard (it runs under it, as on Maps)", railBefore.bottom.value, rail.bottom.value, 0.5f)
        assertTrue("the rail still runs to the window's bottom (${rail.bottom} of ${root.bottom})", rail.bottom >= root.bottom - 0.5.dp)
    }
}
