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
 * - The distance is about twice the other text (24 sp against the turn's 12), and each line of the left part is centred in it:
 *   the figure, the turn line and the kind word share a centre.
 * - The evening pieces are centred in the right part, between the divider and the X.
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

    @Test
    fun `the divider runs the parts' full height, with the evening stack taller than the left part`() {
        setScreen()
        val divider = one(NAVIGATION_HUD_DIVIDER_TAG)
        val figure = one(NAVIGATION_HUD_DISTANCE_TAG)
        val kind = one(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        val pieces = all(NAVIGATION_HUD_EVENING_PIECE_TAG)
        assertEquals("positive control: five evening pieces", 5, pieces.size)
        val partsTop = minOf(figure.top, pieces.minOf { it.top })
        val partsBottom = maxOf(kind.bottom, pieces.maxOf { it.bottom })
        println("MEASURED divider $divider, figure $figure, kind $kind, pieces ${pieces.first()}..${pieces.last()}")
        assertTrue("positive control: the stack ${pieces.maxOf { it.bottom }} ends below the left part ${kind.bottom}", pieces.maxOf { it.bottom } > kind.bottom + 0.5.dp)
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
        val kind = one(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        assertTrue("the divider $divider starts at the figure's top $figure", divider.top <= figure.top + 1.dp)
        assertTrue("the divider $divider ends at the kind's bottom $kind", divider.bottom >= kind.bottom - 1.dp)
        val exit = one(NAVIGATION_HUD_EXIT_TAG)
        assertTrue("the divider $divider is left of the X $exit", divider.right <= exit.left + 0.5.dp)
    }

    @Test
    fun `the distance is twice the other text, and the left part's lines share one centre`() {
        setScreen()
        val figureSp = fontSizeSp(NAVIGATION_HUD_DISTANCE_TAG)
        val turnSp = fontSizeSp(NAVIGATION_HUD_TARGET_TAG)
        assertEquals("the distance is twice the turn's size ($figureSp sp against $turnSp sp)", 2f * turnSp, figureSp, 0.5f)
        val figure = one(NAVIGATION_HUD_DISTANCE_TAG)
        val turn = one(NAVIGATION_HUD_TARGET_TAG)
        val heading = one(NAVIGATION_HUD_HEADING_TAG)
        val kind = one(NAVIGATION_HUD_DISTANCE_KIND_TAG)
        val lineCentre = (turn.left + heading.right) / 2
        println("MEASURED centres: figure ${centreX(figure)}, turn line $lineCentre, kind ${centreX(kind)}")
        assertTrue("the figure ${centreX(figure)} and the turn line $lineCentre share a centre", abs((centreX(figure) - lineCentre).value) <= 1.5f)
        assertTrue("the kind ${centreX(kind)} and the turn line $lineCentre share a centre", abs((centreX(kind) - lineCentre).value) <= 1.5f)
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
