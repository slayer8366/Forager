package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-01, Amendment 1, J3 step 3 and J4 (RECORD -2183). The owner, J3: "Own line, card moves up" ("landscape at font
 * 2.0 with an evening line: its own line, and the card moves up just enough to clear the walker's dot; the evening line stays on the
 * card"); J4: "Follow the largest rule" (fonts 1.5 and 1.8 the same).
 *
 * Through the real [AvailabilityScreen] ([NextTurnCardRig]), in landscape at fonts 1.5, 1.8 and 2.0, with no evening line and with two
 * forms of it. Each case: the evening line is on the card; the card is clear of the walker's dot (where the navigation view puts it: the
 * map's top plus (h + 0.25 h) / 2, radius 12, as `cardTopLimitInRootPx`); its top is under the compass strip; it covers no part of the
 * L; and it is moved up exactly where it must be, its bottom then at the dot's top ("just enough"), and otherwise on the bottom edge.
 * Robolectric reports no insets (CLAUDE.md, "Known pitfalls"); the S22 steps cover the phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardMovesUpTest : NextTurnCardRig() {

    private val turn = NextTurn.Turn(TurnBand.SLIGHT, TurnSide.LEFT, metersAway = 14_200.0, sameSidePathsBefore = 0)

    private fun rects(tag: String, inCard: Boolean = false): List<DpRect> =
        composeRule.onAllNodes(if (inCard) hasTestTag(tag) and hasAnyAncestor(hasTestTag(NAVIGATION_HUD_TAG)) else hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().map { n -> with(composeRule.density) { DpRect(n.boundsInRoot.left.toDp(), n.boundsInRoot.top.toDp(), n.boundsInRoot.right.toDp(), n.boundsInRoot.bottom.toDp()) } }

    /** [movesUp]: whether, with an evening line, the card must leave the bottom edge here (from the probe's heights, run 42402017914). */
    private fun check(where: String, movesUp: Boolean) {
        val problems = mutableListOf<String>()
        for (e in listOf(Evening.NONE, Evening.AFTER_SUNSET, Evening.DARK_SINCE)) {
            show(turn, e)
            val c = card("$where $e")
            val hud = c.hud
            val root = with(composeRule.density) { composeRule.onRoot().fetchSemanticsNode().boundsInRoot.bottom.toDp() }
            val map = rects(NEXT_TURN_RIG_MAP_TAG).single()
            val strip = rects(STRIP_TAG).single()
            val cluster = rects(MAP_ICON_CLUSTER_TAG).singleOrNull()
            val pieces = rects(NAVIGATION_HUD_EVENING_PIECE_TAG, inCard = true).size
            val dot = map.top + (map.bottom - map.top) * 1.25f / 2f
            val dotTop = dot - WALKER_DOT_CLEARANCE
            val dotBottom = dot + WALKER_DOT_CLEARANCE
            val up = hud.bottom < root - 1.dp
            val sharesColumns = cluster != null && cluster.left < hud.right && hud.left < cluster.right
            val coversL = sharesColumns && cluster!!.top < hud.bottom && hud.top < cluster.bottom
            println(
                "MOVEDUP|$where|$e|place ${c.place}|card ${hud.left.value}..${hud.right.value} x ${hud.top.value}..${hud.bottom.value}|height ${(hud.bottom - hud.top).value}|" +
                    "pieces $pieces|root bottom ${root.value}|strip bottom ${strip.bottom.value}|dot ${dotTop.value}..${dotBottom.value}|up $up|" +
                    "top below strip ${(hud.top - strip.bottom).value}|bottom above dot top ${(dotTop - hud.bottom).value}|top below dot bottom ${(hud.top - dotBottom).value}|" +
                    "L $cluster shares columns $sharesColumns covers $coversL",
            )
            val moves = movesUp && e != Evening.NONE
            if (e != Evening.NONE && pieces == 0) problems += "$where $e: the evening line left the card"
            if (e == Evening.NONE && pieces != 0) problems += "$where $e: positive control: $pieces evening pieces with no evening line"
            if (hud.bottom > dotTop + 0.5.dp && hud.top < dotBottom - 0.5.dp) problems += "$where $e: the card $hud covers the walker's dot ($dotTop..$dotBottom)"
            if (hud.top < strip.bottom - 0.5.dp) problems += "$where $e: the card's top ${hud.top} is over the compass strip (its bottom ${strip.bottom})"
            if (coversL) problems += "$where $e: the card $hud covers the L $cluster"
            if (up != moves) problems += "$where $e: the card is ${if (up) "moved up" else "on the bottom edge"} ($hud, the screen's bottom $root), expected ${if (moves) "moved up" else "on the bottom edge"}"
            if (up && kotlin.math.abs((hud.bottom - dotTop).value) > 0.5f) problems += "$where $e: moved up, its bottom ${hud.bottom} is not at the dot's top $dotTop (just enough)"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 1.5f) fun `640 x 360, font 1,5 - clear of the dot from the bottom edge`() = check("640x360/1.5", movesUp = false)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 1.8f) fun `640 x 360, font 1,8 - up just enough with an evening line`() = check("640x360/1.8", movesUp = true)
    @Test @Config(qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f) fun `640 x 360, font 2,0 - up just enough with an evening line`() = check("640x360/2.0", movesUp = true)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.5f) fun `780 x 360, font 1,5 - clear of the dot from the bottom edge`() = check("780x360/1.5", movesUp = false)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.8f) fun `780 x 360, font 1,8 - up just enough with an evening line`() = check("780x360/1.8", movesUp = true)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f) fun `780 x 360, font 2,0 - up just enough with an evening line`() = check("780x360/2.0", movesUp = true)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.5f) fun `823 x 384, font 1,5 - clear of the dot from the bottom edge`() = check("823x384/1.5", movesUp = false)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.8f) fun `823 x 384, font 1,8 - up just enough with an evening line`() = check("823x384/1.8", movesUp = true)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f) fun `823 x 384, font 2,0 - up just enough with an evening line`() = check("823x384/2.0", movesUp = true)

    private companion object {
        const val STRIP_TAG = "compass-elevation-strip"
    }
}
