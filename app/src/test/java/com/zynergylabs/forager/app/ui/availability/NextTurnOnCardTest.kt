package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.NextTurnBlank
import com.zynergylabs.forager.app.domain.nextturn.TurnBand
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-01, Amendment 1, A1, J2 and G5 (RECORD -2183), on the S22's window (384 dp) at normal text, where the next turn
 * fits beside the needle: what the card draws and says, from the route the card is given, through the real screen.
 *
 * - A1, the owner: "Beside the needle (Recommended)": "The degrees and needle keep their fixed spaces, and the next turn gets its own
 *   fixed space beside them."
 * - J2: "beside the needle where it fits (the card keeps its height)"; "no turn ahead → blank (E1-E3)".
 * - G5: ""keep straight for 0.4 miles"" for the straight-ahead arrow; turns keep "in <distance>".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnOnCardTest : NextTurnCardRig() {

    private val leftIn120m = NextTurn.Turn(TurnBand.PLAIN, TurnSide.LEFT, metersAway = 120.0, sameSidePathsBefore = 0)

    @Test
    fun `A1 J2 - at 384 dp and normal text the next turn is beside the needle, a gap right of it, on the turn line`() {
        show(leftIn120m)
        val c = card("384/1.0, a left turn in 120 m")
        assertNotNull(c.describe(), c.space)
        val space = c.space!!
        assertEquals(c.describe(), NextTurnPlace.BESIDE_NEEDLE, c.place)
        assertEquals("the line's own gap right of the needle: ${c.describe()}", (c.needle.right + 4.dp).value, space.left.value, 0.5f)
        val middle = (space.top + space.bottom) / 2
        assertTrue("on the needle's row: ${c.describe()}", middle >= c.needle.top && middle <= c.needle.bottom)
        assertTrue("inside the left part: ${c.describe()}", space.right <= c.leftPartRight + 0.5.dp)
        assertEquals(c.describe(), NextTurnArrow.LEFT, c.arrow)
        assertEquals(c.describe(), "394 ft", c.figure)
        assertEquals(c.describe(), "turn left in 394 feet", c.spoken)
    }

    @Test
    fun `E1 - with no turn known the space is blank and keeps its place, so nothing on the turn line moves`() {
        show(leftIn120m)
        val withTurn = card("a left turn")
        show(NextTurn.Blank(NextTurnBlank.NOT_YET_KNOWN))
        val blank = card("not yet known")
        assertNull(blank.describe(), blank.arrow)
        assertNull(blank.describe(), blank.figure)
        assertNull(blank.describe(), blank.spoken)
        assertEquals("the space's place: ${blank.describe()}", withTurn.space, blank.space)
        assertEquals("the needle's place: ${blank.describe()}", withTurn.needle, blank.needle)
    }

    @Test
    fun `A3 G5 - with no turn left the straight-ahead arrow carries the top line's own figure and says keep straight for`() {
        show(NextTurn.NoTurnLeft)
        val c = card("no turn left")
        assertEquals(c.describe(), NextTurnArrow.STRAIGHT, c.arrow)
        assertEquals("the top line's own string: ${c.describe()}", c.distanceText, c.figure)
        assertEquals(c.describe(), "keep straight for 1280 feet", c.spoken)
    }

    @Test
    fun `E3 - the straight line to a waypoint leaves the space blank`() {
        show(NextTurn.NoTurnLeft, straight = true)
        val c = card("the straight line")
        assertTrue("the straight line's figure: ${c.describe()}", c.distanceText.startsWith("≈"))
        assertEquals("the space keeps its place: ${c.describe()}", 1, c.spaceCount)
        assertNull(c.describe(), c.arrow)
        assertNull(c.describe(), c.spoken)
    }
}
