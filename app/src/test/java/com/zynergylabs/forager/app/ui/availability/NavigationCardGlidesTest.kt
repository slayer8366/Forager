package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-01, Amendment 1, J5 (RECORD -2183), the owner: "Animate the move": the landscape navigation card glides up off
 * the bottom edge and back down again rather than jumping (J3 step 3's move, [navigationCardLift]).
 *
 * Through the real [AvailabilityScreen] ([NextTurnCardRig]) at 640 x 360, font 2.0, where the evening line moves the card up
 * ([NavigationCardMovesUpTest]). The evening line comes and goes with the clock stepped a frame at a time, and the card's bottom is
 * read each frame: the card's own height changes with the line while its bottom stays on the edge, so only the move shows in its
 * bottom. A jump puts no frame between the two places; a glide puts several. The settled places are read after the clock runs on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w640dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardGlidesTest : NextTurnCardRig() {

    private val turn = NextTurn.Turn(TurnBand.SLIGHT, TurnSide.LEFT, metersAway = 14_200.0, sameSidePathsBefore = 0)

    private fun bottom(): Dp = card("glide").hud.bottom

    /** The card's bottom at each frame for [MAX_FRAMES] after the evening line becomes [e], then where it settles. */
    private fun framesTo(e: Evening): Pair<List<Dp>, Dp> {
        composeRule.mainClock.autoAdvance = false
        setEveningUnsettled(e)
        val frames = (0 until MAX_FRAMES).map {
            composeRule.mainClock.advanceTimeByFrame()
            bottom()
        }
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(3_000)
        composeRule.waitForIdle()
        return frames to bottom()
    }

    private fun between(frames: List<Dp>, a: Dp, b: Dp): List<Dp> {
        val lo = minOf(a, b) + 2.dp
        val hi = maxOf(a, b) - 2.dp
        return frames.filter { it > lo && it < hi }
    }

    @Test
    fun `J5 - the card glides up when the evening line moves it, and glides back down when the line goes`() {
        show(turn, Evening.NONE)
        val edge = bottom()
        val (up, upSettled) = framesTo(Evening.AFTER_SUNSET)
        println("GLIDE up: edge ${edge.value}, settled ${upSettled.value}, frames ${up.map { it.value }}")
        assertTrue("positive control: the evening line moves the card up here (edge $edge, settled $upSettled)", upSettled < edge - 20.dp)
        val upBetween = between(up, edge, upSettled)
        assertTrue("J5: the card glides up, so several frames show it between the edge $edge and its place $upSettled; frames between: $upBetween", upBetween.size >= MIN_GLIDE_FRAMES)
        assertTrue("J5: the glide only goes up, never past its place: $up", up.zipWithNext().all { (a, b) -> b <= a + 0.5.dp } && up.all { it >= upSettled - 0.5.dp })
        val (down, downSettled) = framesTo(Evening.NONE)
        println("GLIDE down: settled ${downSettled.value}, frames ${down.map { it.value }}")
        assertEquals("settled back on the bottom edge", edge.value, downSettled.value, 0.5f)
        val downBetween = between(down, upSettled, edge)
        assertTrue("J5: the card glides back down, so several frames show it between its place $upSettled and the edge $edge; frames between: $downBetween", downBetween.size >= MIN_GLIDE_FRAMES)
    }

    private companion object {
        /** About three seconds of frames, longer than any of the card's motion. */
        const val MAX_FRAMES = 180

        /** A jump has none; the shortest glide worth the name has a few. */
        const val MIN_GLIDE_FRAMES = 4
    }
}
