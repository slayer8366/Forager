package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplay

// Scratch diagnostic for dispatch 2026-10-17-09: prints bounds, asserts nothing. Never merged.
abstract class TabsChromeBoundsReport(private val name: String, private val landscape: Boolean, private val rotation: Int?) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private fun b(tag: String): String {
        val n = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()
        if (n.isEmpty()) return "absent"
        val r = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true)[0].getUnclippedBoundsInRoot()
        return "l=%.1f t=%.1f r=%.1f b=%.1f w=%.1f h=%.1f".format(r.left.value, r.top.value, r.right.value, r.bottom.value, (r.right - r.left).value, (r.bottom - r.top).value)
    }
    private fun content(label: String): String = when (label) {
        "List" -> composeRule.onAllNodes(hasText("Choose a region", substring = true), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { val r = it.boundsInRoot; "text t=%.1f l=%.1f r=%.1f".format(r.top / it.layoutInfo.density.density, r.left / it.layoutInfo.density.density, r.right / it.layoutInfo.density.density) } ?: "absent"
        "Seasonal" -> "seasonal " + b(SEASONAL_CONTENT_TAG)
        "Journal" -> "short-header " + b(com.zynergylabs.forager.app.ui.log.SHORT_HEADER_TAG) + " / switch " + b(com.zynergylabs.forager.app.ui.log.journalSwitchTestTag(com.zynergylabs.forager.app.ui.log.JournalTopTab.CARTOGRAPHY))
        else -> ""
    }

    @Test fun report() {
        if (rotation != null) Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(rotation)
        val state = MapChromeScreenState(); val roles = MapChromeRoles()
        composeRule.setContent { MapChromeTestScreen(state, BubbleMapSlot(emptyList()), roles) }
        composeRule.waitForIdle()
        val nav = if (landscape) COMPACT_NAVIGATION_RAIL_TAG else COMPACT_BOTTOM_NAV_TAG
        for (label in listOf("Maps", "List", "Seasonal", "Journal", "Maps")) {
            composeRule.onNode(hasText(label) and hasAnyAncestor(hasTestTag(nav)), useUnmergedTree = true).performClick()
            composeRule.waitForIdle()
            if (landscape && label == "Journal") {
                composeRule.onNodeWithTag("journal-short-search", useUnmergedTree = true).performTouchInput { click(center) }
                composeRule.waitForIdle()
            }
            println("BOUNDS $name $label pill: ${b(SEARCH_ENTRY_BAR_TAG)}")
            println("BOUNDS $name $label nav: ${b(nav)}")
            if (label != "Maps") println("BOUNDS $name $label content: ${content(label)}")
        }
    }
}

@RunWith(RobolectricTestRunner::class) @Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class TabsChromeBoundsPortraitReport : TabsChromeBoundsReport("portrait", false, null)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsChromeBoundsLand90Report : TabsChromeBoundsReport("land90", true, Surface.ROTATION_90)
@RunWith(RobolectricTestRunner::class) @Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class TabsChromeBoundsLand270Report : TabsChromeBoundsReport("land270", true, Surface.ROTATION_270)
