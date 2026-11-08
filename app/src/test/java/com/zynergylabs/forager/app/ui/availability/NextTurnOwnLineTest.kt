package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.nextturn.NextTurn
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.domain.nextturn.TurnSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-06-01, Amendment 1, J2 (RECORD -2183), on the S22's window (384 dp) at the next text size up (1.3), where the fit
 * probe found the next turn 9 to 33 dp short beside the needle (run 42389607585): "its own line under the turn line where it doesn't
 * (one line taller)", the needle where #498 put it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NextTurnOwnLineTest : NextTurnCardRig() {

    @Test
    fun `J2 - at 384 dp and font 1,3 the next turn has its own line under the turn line, centred in the left part, inside the card`() {
        show(NextTurn.Fork(TurnSide.RIGHT, metersAway = 800.0))
        val c = card("384/1.3, keep right in 800 m")
        assertNotNull(c.describe(), c.space)
        val space = c.space!!
        assertEquals(c.describe(), NextTurnPlace.OWN_LINE, c.place)
        assertTrue("under the turn line: ${c.describe()}", space.top >= c.needle.bottom - 0.5.dp)
        assertEquals("centred in the left part: ${c.describe()}", c.leftPartMiddle.value, ((space.left + space.right) / 2).value, 1f)
        assertTrue("inside the card, which is taller for it: ${c.describe()}", space.bottom <= c.hud.bottom)
        assertEquals(c.describe(), NextTurnArrow.FORK_RIGHT, c.arrow)
        assertEquals(c.describe(), "0.5 mi", c.figure)
        assertEquals(c.describe(), "keep right in 0.5 miles", c.spoken)
    }
}
