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
import androidx.compose.ui.unit.DpRect
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
import kotlin.math.abs

/**
 * Dispatch 2026-10-12-08 (RECORD -945, the owner's design review: "Both (Recommended)", "Full dividers + by trail under figure
 * (Recommended)"; RECORD -951 item 7): the navigation info's own layout, measured on native graphics at 360 dp.
 * - The divider runs the parts' full height: from the top of the taller part to the bottom of the taller part, with the evening
 *   stack (taller than the left part) and without it (the left part alone), where it stays.
 * - The distance is about twice the other text (24 sp against the turn's 12). Since dispatch 2026-11-05-10 (RECORD -2181, the
 *   owner: "Meet at the middle (Recommended)") the distance ends at the left part's middle and the kind starts there, on one
 *   baseline, and the turn line stays centred, so the two meet at its centre. Was: the figure, the turn line and the kind word share
 *   a centre.
 * - The evening pieces are centred in the right part, between the divider and the X.
 *
 * Since dispatch 2026-11-05-10 the left part's bottom is the turn row's: nothing is drawn under it. Was the kind word's bottom.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationInfoLayoutTest {

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

    /** RECORD -941's own example with a start-back piece: five pieces, taller than the left part's three lines (about 70 dp). */
    private var sundown by mutableStateOf<String?>("Sunset 12:41 AM · in 2 h 28 min · dark 1:13 · start back by 12:31")
    private var backBy by mutableStateOf<String?>("Back by 1:30 AM")

    private fun setScreen() {
        composeRule.setContent {
            NavigationHud(
                heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(182f)) },
                liveFix = fix,
                target = start,
                distanceUnit = DistanceUnit.MILES,
                currentTime = CurrentTimeProvider { now },
                showDecimalDegrees = false,
                onExit = {},
                route = ReturnRoute.Ahead(LatLng(start.lat, start.lng), 390.0),
                sundownLine = sundown,
                backByLine = backBy,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun all(tag: String): List<DpRect> = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().map { n ->
        with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) }
    }

    private fun one(tag: String): DpRect = all(tag).single()

    private fun fontSizeSp(tag: String): Float {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single().layoutInput.style.fontSize.value
    }

    private fun centreX(r: DpRect) = (r.left + r.right) / 2

    /**
     * The turn row's bottom: the needle's square or the degrees, whichever is lower. Since Amendment 1, H1, the card has no heading.
     * Was `maxOf(one(NAVIGATION_HUD_NEEDLE_TAG).bottom, one(NAVIGATION_HUD_HEADING_TAG).bottom)` (dispatch 2026-11-05-10).
     */
    private fun turnRowBottom(): androidx.compose.ui.unit.Dp = maxOf(one(NAVIGATION_HUD_NEEDLE_TAG).bottom, one(NAVIGATION_HUD_TARGET_TAG).bottom)

    private fun baseline(tag: String): androidx.compose.ui.unit.Dp {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().single().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return one(tag).top + with(composeRule.density) { results.single().firstBaseline.toDp() }
    }

    @Test
    fun `the divider runs the parts' full height, with the evening stack taller than the left part`() {
        setScreen()
        val divider = one(NAVIGATION_HUD_DIVIDER_TAG)
        val figure = one(NAVIGATION_HUD_DISTANCE_TAG)
        // Dispatch 2026-11-05-10: the left part ends with the turn row. Was `val kind = one(NAVIGATION_HUD_DISTANCE_KIND_TAG)`, the
        // kind word's line under it being the left part's last.
        val leftBottom = turnRowBottom()
        val pieces = all(NAVIGATION_HUD_EVENING_PIECE_TAG)
        assertEquals("positive control: five evening pieces", 5, pieces.size)
        val partsTop = minOf(figure.top, pieces.minOf { it.top })
        val partsBottom = maxOf(leftBottom, pieces.maxOf { it.bottom })
        println("MEASURED divider $divider, figure $figure, left part's bottom $leftBottom, pieces ${pieces.first()}..${pieces.last()}")
        assertTrue("positive control: the stack ${pieces.maxOf { it.bottom }} ends below the left part $leftBottom", pieces.maxOf { it.bottom } > leftBottom + 0.5.dp)
        assertTrue("the divider $divider starts at the parts' top $partsTop", abs((divider.top - partsTop).value) <= 1f)
        assertTrue("the divider $divider ends at the taller part's bottom $partsBottom", divider.bottom >= partsBottom - 1.dp)
    }

    @Test
    fun `with no evening stack the divider stays, the left part's full height`() {
        sundown = null
        backBy = null
        setScreen()
        assertEquals("positive control: no evening pieces", 0, all(NAVIGATION_HUD_EVENING_PIECE_TAG).size)
        val dividers = all(NAVIGATION_HUD_DIVIDER_TAG)
        assertEquals("the divider stays (RECORD -951)", 1, dividers.size)
        val divider = dividers.single()
        val figure = one(NAVIGATION_HUD_DISTANCE_TAG)
        // Dispatch 2026-11-05-10: the left part is the top line and the turn row, so its height ends with the turn row, exactly. Was
        // `assertTrue("the divider $divider ends at the kind's bottom $kind", divider.bottom >= kind.bottom - 1.dp)`, the kind word
        // the left part's last line.
        val leftBottom = turnRowBottom()
        assertTrue("the divider $divider starts at the figure's top $figure", divider.top <= figure.top + 1.dp)
        assertTrue("the divider $divider ends at the turn row's bottom $leftBottom: nothing under it", abs((divider.bottom - leftBottom).value) <= 1f)
        val exit = one(NAVIGATION_HUD_EXIT_TAG)
        assertTrue("the divider $divider is left of the X $exit", divider.right <= exit.left + 0.5.dp)
    }

    /**
     * Dispatch 2026-11-05-10 (RECORD -2181). Was "the distance is twice the other text, and the left part's lines share one centre",
     * asserting `abs(centreX(figure) - lineCentre) <= 1.5` and `abs(centreX(kind) - lineCentre) <= 1.5`; then (Phase 2, "Meet at the
     * middle") "… and the distance and its kind meet at the turn line's centre on one baseline", asserting `abs(figure.right -
     * (lineCentre - 2.dp)) <= 1` and `abs(kind.left - (lineCentre + 2.dp)) <= 1`. Amendment 1, G3, replaced the meeting: each is centred
     * in its own space, either side of the left part's middle (exactly where, from the longest figure, NavigationCardTopLineRules
     * checks through the real screen). The left part's middle is the top line's, the left part's width.
     */
    @Test
    fun `the distance is twice the other text, and the distance and its kind share one baseline either side of the left part's middle`() {
        setScreen()
        val figureSp = fontSizeSp(NAVIGATION_HUD_DISTANCE_TAG)
        val turnSp = fontSizeSp(NAVIGATION_HUD_TARGET_TAG)
        assertEquals("the distance is twice the turn's size ($figureSp sp against $turnSp sp)", 2f * turnSp, figureSp, 0.5f)
        val figure = one(NAVIGATION_HUD_DISTANCE_TAG)
        val kind = one(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        val topLine = one(NAVIGATION_HUD_TOP_LINE_TAG)
        val middle = centreX(topLine)
        println("MEASURED top line: figure $figure, kind $kind, the left part's middle $middle")
        assertTrue("the figure $figure is left of the left part's middle $middle", figure.right <= middle - 2.dp + 0.5.dp)
        assertTrue("the kind $kind is right of the left part's middle $middle", kind.left >= middle + 2.dp - 0.5.dp)
        assertEquals("the kind is on the figure's baseline", baseline(NAVIGATION_HUD_DISTANCE_TAG).value, baseline(NAVIGATION_HUD_DISTANCE_KIND_TAG).value, 0.5f)
    }

    @Test
    fun `the evening pieces are centred in the right part, between the divider and the X`() {
        setScreen()
        val divider = one(NAVIGATION_HUD_DIVIDER_TAG)
        val exit = one(NAVIGATION_HUD_EXIT_TAG)
        val pieces = all(NAVIGATION_HUD_EVENING_PIECE_TAG)
        // The right part starts after the divider's 6 dp gap (CARD_DIVIDER_GAP) and ends at the X.
        val partCentre = (divider.right + 6.dp + exit.left) / 2
        pieces.forEach { piece ->
            assertTrue("the piece $piece lies between the divider $divider and the X $exit", piece.left >= divider.right - 0.5.dp && piece.right <= exit.left + 0.5.dp)
            assertTrue("the piece $piece is centred on $partCentre", abs((centreX(piece) - partCentre).value) <= 2f)
        }
    }
}
