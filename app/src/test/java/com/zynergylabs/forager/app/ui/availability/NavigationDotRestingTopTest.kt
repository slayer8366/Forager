package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.ui.map.NavigationSideInsets
import com.zynergylabs.forager.app.ui.map.navigationViewPaddingPx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Sorrel's J8 review on PR #500, item 1.1 (RECORD -2190). J8 (dispatch 2026-11-06-01, Amendment 1, RECORD -2183) keeps the walker's dot
 * above the navigation card's *resting* top, the place it is going (its bottom edge less its whole move, less its height), not its
 * top as drawn mid-glide, so the dot goes to its place once rather than chasing the card frame by frame
 * (`CompactMapLandscapeNavigationCard.kt`, `navigationCardRestingBoundsInRoot`). Settled, the two agree, so no settled test can tell
 * them apart.
 *
 * Through the real [AvailabilityScreen] ([NextTurnCardRig]) at 640 x 360, font 2.0, where the evening line moves the card up
 * ([NavigationCardMovesUpTest], [NavigationCardGlidesTest]). The clock is stepped a frame at a time through the glide up. On the
 * bottom edge with its evening line, the card covers the dot, which is why it moves; moved up, its bottom is at the dot's top, so its
 * resting place clears the dot and the view should ask for no bottom padding. Mid-glide its drawn top is still over the dot. So at
 * every frame part-way through the glide, the padding the navigation view is asked for (the cluster's resting bounds turned into the
 * camera's padding, `navigationInsetsWithDotAboveCard`) must keep the dot for the final top; a request for bottom padding there is the
 * dot kept above the drawn top. The map is a stub (MapLibre cannot run under Robolectric), so this reads the request, not the camera.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationDotRestingTopTest : NextTurnCardRig() {

    private val turn = NextTurn.Turn(TurnBand.SLIGHT, TurnSide.LEFT, metersAway = 14_200.0, sameSidePathsBefore = 0)

    private class Frame(val index: Int, val drawn: DpRect, val side: NavigationSideInsets)

    private fun map(): DpRect = with(composeRule.density) {
        val b = composeRule.onAllNodes(hasTestTag(NEXT_TURN_RIG_MAP_TAG), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot
        DpRect(b.left.toDp(), b.top.toDp(), b.right.toDp(), b.bottom.toDp())
    }

    private fun side(): NavigationSideInsets =
        mapRenderMode?.navigationView?.sideInsetsPx ?: throw AssertionError("navigating, so the map was asked for its navigation view")

    /** Where the camera puts the dot for [side], across and down, read off the padding it is sent, as NavigationDotAboveCardTest. */
    private fun dotFor(map: DpRect, side: NavigationSideInsets): Pair<Dp, Dp> = with(composeRule.density) {
        val widthPx = (map.right - map.left).toPx()
        val heightPx = (map.bottom - map.top).toPx()
        val (left, top, right, bottom) = navigationViewPaddingPx(heightPx.toInt(), side).toList()
        (map.left + (left + (widthPx - left - right) / 2).toFloat().toDp()) to (map.top + (top + (heightPx - top - bottom) / 2).toFloat().toDp())
    }

    private fun covers(card: DpRect, dot: Pair<Dp, Dp>): Boolean {
        val (x, y) = dot
        val r = WALKER_DOT_CLEARANCE
        return card.left < x + r && card.right > x - r && card.top < y + r && card.bottom > y - r
    }

    @Test
    fun `J8 review 1_1 - part-way through the card's glide up, the dot is kept for its final top, not its drawn top`() {
        show(turn, Evening.NONE)
        val edge = card("on the edge").hud.bottom
        val map = map()
        val home = dotFor(map, NavigationSideInsets.NONE)
        composeRule.mainClock.autoAdvance = false
        setEveningUnsettled(Evening.AFTER_SUNSET)
        val frames = (1..MAX_FRAMES).map { i ->
            composeRule.mainClock.advanceTimeByFrame()
            Frame(i, card("frame $i").hud, side())
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        val settled = card("settled").hud
        val settledSide = side()
        frames.forEach { f ->
            println("RESTINGTOP frame ${f.index}: drawn ${f.drawn.top.value}..${f.drawn.bottom.value} x ${f.drawn.left.value}..${f.drawn.right.value}, side ${f.side}")
        }
        println("RESTINGTOP edge ${edge.value}, settled ${settled.top.value}..${settled.bottom.value}, dot home ${home.first.value}, ${home.second.value}, settled side $settledSide")

        assertTrue("positive control: the evening line moves the card up here (edge $edge, settled bottom ${settled.bottom})", settled.bottom < edge - 20.dp)
        assertTrue("precondition: settled, the card's final place (top ${settled.top}) clears the dot at ${home.second}", !covers(settled, home))
        assertEquals("precondition: settled, the view asks for no bottom padding", NavigationSideInsets.NONE, settledSide)
        val midGlide = frames.filter { it.drawn.bottom < edge - 2.dp && it.drawn.bottom > settled.bottom + 2.dp }
        val drawnCovers = midGlide.filter { covers(it.drawn, home) }
        assertTrue(
            "positive control: part-way through the glide the drawn card still covers the dot, so its drawn top and final top disagree there " +
                "(mid-glide frames ${midGlide.map { it.index }}, covering ${drawnCovers.map { it.index }})",
            drawnCovers.isNotEmpty(),
        )
        val problems = midGlide.filter { it.side != NavigationSideInsets.NONE }.map { f ->
            val keptAbove = dotFor(map, f.side).second + WALKER_DOT_CLEARANCE
            "frame ${f.index}: the view asks for ${f.side}, keeping the dot above a card top at $keptAbove; the card's final top is " +
                "${settled.top} (bottom edge less the full move less its height), which clears the dot, and its drawn top at that frame is ${f.drawn.top}"
        }
        assertTrue("J8: the dot is kept for the card's resting top, not its drawn top, part-way through the glide:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        /** About three seconds of frames, longer than any of the card's motion (NavigationCardGlidesTest). */
        const val MAX_FRAMES = 180
    }
}
