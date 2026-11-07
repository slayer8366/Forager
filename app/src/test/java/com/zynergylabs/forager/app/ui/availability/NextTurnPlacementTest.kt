package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import com.zynergylabs.forager.app.ui.availability.NextTurnPlace.BESIDE_NEEDLE
import com.zynergylabs.forager.app.ui.availability.NextTurnPlace.OWN_LINE
import com.zynergylabs.forager.app.ui.availability.NextTurnPlace.RIGHT_PART
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** J2's placement at 823 x 384, normal text, shared with [NextTurnCardLongPressTest]. */
internal val LANDSCAPE_823_AT_1_0 = OWN_LINE

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183), the owner: "Own line where needed (Recommended)"; the step path, "Yes, that's
 * it": "beside the needle where it fits (the card keeps its height); its own line under the turn line where it doesn't (one line
 * taller); decided once per screen and text size, never flipping while walking (as H8 decides the status words)".
 *
 * The 21 windows and fonts the fit probe measured (run 42389607585), each read in its five evening states, through the real screen.
 * Each case pins where the next turn goes there, and checks it is the same in every evening state (never flipping as the evening
 * comes) and drawn inside the left part, clear of the needle. The expected places come from the fit probe's widths with every evening
 * piece the card can show counted (the measured placements are in the dispatch report's "2b-2 step 2, built" section).
 *
 * J3 (RECORD -2183), the owner: "Arrow in the right part", "Own line, card moves up", "Centre the line"; the step path, "Yes, that's it".
 * Portrait at every font and landscape at 1.0 and 1.3 stay as J2 (step 1). In landscape at font 2.0 the next turn is in the card's
 * right part with no evening line (step 2) and on its own line with one (step 3), so there, and only there, the place follows the
 * evening line by the owner's own steps.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnPlacementTest : NextTurnCardRig() {

    private val turn = NextTurn.Turn(TurnBand.SLIGHT, TurnSide.LEFT, metersAway = 14_200.0, sameSidePathsBefore = 0)

    private fun placed(where: String, expected: NextTurnPlace) = placed(where) { expected }

    /** [expected] by evening state: the same in all five but in landscape at font 2.0, where J3 decides by the evening line. */
    private fun placed(where: String, expected: (Evening) -> NextTurnPlace) {
        for (e in Evening.entries) {
            show(turn, e)
            val c = card("$where, $e")
            assertNotNull(c.describe(), c.space)
            val space = c.space!!
            println("NEXTTURN|$where|$e|${c.place}|card height ${c.hud.bottom - c.hud.top}|${c.describe()}")
            assertEquals("$where, $e: ${c.describe()}", expected(e), c.place)
            if (expected(e) == RIGHT_PART) {
                // J3 step 2: in the right part, where the evening line goes, adding no line: it ends within the turn line's row.
                assertTrue("$where, $e: inside the right part: ${c.describe()}", space.left >= c.dividerLeft && space.right <= c.hud.right)
                assertTrue("$where, $e: no taller than the turn line's row: ${c.describe()}", space.bottom <= c.needle.bottom + 0.5.dp)
            } else {
                assertTrue("$where, $e: inside the left part: ${c.describe()}", space.left >= c.topLine.left - 0.5.dp && space.right <= c.leftPartRight + 0.5.dp)
            }
            if (expected(e) == BESIDE_NEEDLE) assertTrue("$where, $e: clear of the needle: ${c.describe()}", space.left >= c.needle.right)
            assertTrue("$where, $e: inside the card: ${c.describe()}", space.bottom <= c.hud.bottom)
        }
    }

    /**
     * J3 steps 2 and 3 (the owner: "Arrow in the right part", "Own line, card moves up"): in landscape at font 2.0, the right part with
     * no evening line; its own line with one. Step 3's card moving up is not built (stopped, the dispatch report's "J3, built").
     */
    private val landscapeLargeText: (Evening) -> NextTurnPlace = { e -> if (e == Evening.NONE) RIGHT_PART else OWN_LINE }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi") fun `320 dp, font 1,0`() = placed("320/1.0", OWN_LINE)
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 1.3f) fun `320 dp, font 1,3`() = placed("320/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f) fun `320 dp, font 2,0`() = placed("320/2.0", OWN_LINE)
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi") fun `360 dp, font 1,0`() = placed("360/1.0", OWN_LINE)
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f) fun `360 dp, font 1,3`() = placed("360/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f) fun `360 dp, font 2,0`() = placed("360/2.0", OWN_LINE)
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi") fun `384 dp (the S22), font 1,0`() = placed("384/1.0", BESIDE_NEEDLE)
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f) fun `384 dp (the S22), font 1,3`() = placed("384/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f) fun `384 dp (the S22), font 2,0`() = placed("384/2.0", OWN_LINE)
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi") fun `412 dp, font 1,0`() = placed("412/1.0", BESIDE_NEEDLE)
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f) fun `412 dp, font 1,3 - never flipping - own line in daytime too`() = placed("412/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 2.0f) fun `412 dp, font 2,0`() = placed("412/2.0", OWN_LINE)
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi") fun `480 dp, font 1,0`() = placed("480/1.0", BESIDE_NEEDLE)
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 1.3f) fun `480 dp, font 1,3`() = placed("480/1.3", BESIDE_NEEDLE)
    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 2.0f) fun `480 dp, font 2,0`() = placed("480/2.0", OWN_LINE)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi") fun `780 x 360, font 1,0 - never flipping - own line in daytime too`() = placed("780x360/1.0", OWN_LINE)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.3f) fun `780 x 360, font 1,3`() = placed("780x360/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f) fun `780 x 360, font 2,0 - J3 - the right part with no evening line, its own line with one`() = placed("780x360/2.0", landscapeLargeText)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi") fun `823 x 384, font 1,0 - never flipping - own line in daytime too`() = placed("823x384/1.0", LANDSCAPE_823_AT_1_0)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f) fun `823 x 384, font 1,3`() = placed("823x384/1.3", OWN_LINE)
    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f) fun `823 x 384, font 2,0 - J3 - the right part with no evening line, its own line with one`() = placed("823x384/2.0", landscapeLargeText)
}
