package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.remember
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.model.WaypointDesignation
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import org.junit.Assert.assertEquals
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

import org.junit.Assert.assertFalse

/**
 * Dispatch 2026-10-12-08: the navigation card has no three-dot button (RECORD -950, -951); see the test's own comment. Before:
 * RECORD -714 (the owner: "Button moves to second row (Recommended)"): where the navigation display's three-dot button sits,
 * and that real touches across its 36 dp square, corners included, open the quick menu there. At 360 dp the first row cannot
 * give data part B's longest route line its full width beside the button (firstRowHoldsTheButton), so the button ends the
 * second row; at 384 dp (the S22) it stays left of the X. The display is composed afresh between touches, because under
 * Robolectric neither Back nor a touch elsewhere reaches the menu's popup window.
 */
abstract class NavigationHudButtonPlacementTests {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }
    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val now = 1_791_050_400_000L
    private val start = Waypoint("origin", 45.53, -122.68, null, "Start", "", now - 3_600_000L, trackId = "t1", designation = WaypointDesignation.ORIGIN)
    private val fix = LocationFix.Update(lat = start.lat + 380.0 / 111_195.08, lng = start.lng, altitude = 120.0, accuracyMeters = 3.79f, timestampEpochMillis = now, provider = FixProvider.GPS)
    private var generation by mutableStateOf(0)

    private fun bounds(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single().let { n ->
        with(composeRule.density) { androidx.compose.ui.unit.DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    private fun shown(tag: String) = composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    /**
     * Dispatch 2026-10-12-08 (RECORD -947, the owner: "Yes, only on the strip (Recommended)"; RECORD -950, -951 item 5): the
     * three-dot button is on the compass strip only, above the X, so the card has none, and the X stands alone at its top right.
     * Where the button sits on the strip, and real touches across both, are held by `MapTopCardScreenTest`. Was "the button sits
     * under the X at the card's right edge, and real touches across it open the menu" (RECORD -932's order, which -951 item 5
     * supersedes): `assertTrue("under the X: ...", button.top >= exit.bottom - 0.5.dp)`, the button at the card's right edge, and
     * five real touches across the button opening the menu.
     */
    @Test
    fun `the card has no three-dot button, the X is at its top right, and real touches across the X stop navigating`() {
        var exits = 0
        composeRule.setContent {
            androidx.compose.runtime.key(generation) {
                NavigationHud(
                    heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(11f)) },
                    liveFix = fix,
                    target = start,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { now },
                    showDecimalDegrees = false,
                    onExit = { exits++ },
                    route = ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        composeRule.waitForIdle()
        assertFalse("no three-dot button in the card", shown(MAP_QUICK_SETTINGS_BUTTON_TAG))
        val exit = bounds(NAVIGATION_HUD_EXIT_TAG)
        val hud = bounds(NAVIGATION_HUD_TAG)
        assertTrue("the X at the card's top right: $exit in $hud", hud.right - exit.right <= 8.dp && exit.top - hud.top <= 8.dp)
        // Material's icon button: laid out at 40 dp, its touch target reaching 48 dp by expansion (not in its bounds).
        assertEquals("the X is Material's 40 dp icon button: $exit", 40f, (exit.right - exit.left).value, 0.5f)
        val inset = 4.dp
        val points = listOf(
            (exit.left + exit.right) / 2 to (exit.top + exit.bottom) / 2,
            exit.left + inset to exit.top + inset,
            exit.right - inset to exit.top + inset,
            exit.left + inset to exit.bottom - inset,
            exit.right - inset to exit.bottom - inset,
        )
        for ((i, point) in points.withIndex()) {
            composeRule.touchAt(point.first, point.second)
            composeRule.waitForIdle()
            assertEquals("a real touch at (${point.first.value}, ${point.second.value}) in $exit stopped navigating", i + 1, exits)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationHudButtonPlacement360Test : NavigationHudButtonPlacementTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationHudButtonPlacement384Test : NavigationHudButtonPlacementTests()
