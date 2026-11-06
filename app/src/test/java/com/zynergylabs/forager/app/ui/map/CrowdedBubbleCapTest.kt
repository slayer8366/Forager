package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.testing.MadeUpPlace
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate

/**
 * Dispatch 2026-11-04-02, S1 (RECORD -2040): the crowded stack's bubble is no taller than "the tallest standard bubble, measured
 * and kept as a constant". The standard bubble has no height limit of its own (MapBubble.kt measures it unbounded), so the
 * tallest is measured here: the Maps tab's waypoint bubble with every line and action it can carry there (its name, its MGRS,
 * its elevation, three keeping entries, Navigate, Plot route and Details), at font 1.0, on the S22's portrait window, under
 * native graphics so its text is measured as on the phone, and in the app's own theme. The constant is that height; a change to
 * the waypoint bubble that makes it taller or shorter fails here, and the constant is measured again. The other kinds are not
 * measured: the ruling names the waypoint bubble (the verify-first report's option (a)), not whichever kind is tallest.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class CrowdedBubbleCapTest {

    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val waypoint = Waypoint("wp-1", 45.32, -122.63, null, "Waypoint 1", "", 1_700_000_000_000L)

    private fun cardHeightDp(content: MapBubbleContent): Float {
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                Box {
                    MapFeatureBubble(
                        content = content,
                        tipInBubble = remember { mutableStateOf<Offset?>(null) },
                        onDismiss = {},
                        openFindLabel = "Open in Journal",
                        onOpenFind = {},
                        onViewPhoto = {},
                        onDirections = { _, _ -> },
                        onDetails = {},
                        onOpenEntry = {},
                        onNavigate = {},
                        onPlotRoute = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithTag(MAP_BUBBLE_TAG).getUnclippedBoundsInRoot()
        return (bounds.bottom - bounds.top).value
    }

    @Test
    fun `the crowded bubble's cap is the Maps tab's waypoint bubble with every line and action, at font 1_0`() {
        val tallest = MapBubbleContent.WaypointContent(
            waypoint = waypoint,
            mgrs = MadeUpPlace.MGRS,
            hasDetails = true,
            keptIn = listOf(
                JournalEntryOnMap("e1", LocalDate.of(2026, 10, 29)),
                JournalEntryOnMap("e2", LocalDate.of(2026, 10, 30)),
                JournalEntryOnMap("e3", LocalDate.of(2026, 10, 31)),
            ),
            elevation = "Elevation 1352 ft",
        )
        val measured = cardHeightDp(tallest)
        println("CrowdedBubbleCapTest: the waypoint bubble's card, every line and action, is $measured dp tall at font 1.0")
        assertEquals(
            "CROWDED_BUBBLE_MAX_CARD_HEIGHT is the measured waypoint bubble's card height, $measured dp",
            measured,
            CROWDED_BUBBLE_MAX_CARD_HEIGHT.value,
            0.5f,
        )
    }
}
