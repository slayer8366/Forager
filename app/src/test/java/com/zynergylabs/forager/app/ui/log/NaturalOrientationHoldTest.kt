package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pure halves of the large-screen hold (RECORD -981, -985): when it turns the camera's content, what shape the natural
 * frame is, and where the window's insets land in that frame.
 *
 * The inset mapping is written as device anatomy, not as a table copied from the code. At `ROTATION_90` a portrait-natural
 * device has been turned a quarter counter-clockwise, so its natural top edge is the window's **left**, its natural right
 * is the window's top, its natural bottom (the charger port, `CameraArrangement`) the window's right, and its natural left
 * the window's bottom. `ROTATION_270` is the mirror, and `ROTATION_180` swaps opposite edges. Each window edge carries a
 * different value, so an edge sent to the wrong place cannot pass.
 */
class NaturalOrientationHoldTest {

    @Test
    fun `full screen, the content turns back by the display's rotation`() {
        assertEquals(0, naturalHoldQuarterTurns(Surface.ROTATION_0, fullScreen = true))
        assertEquals(1, naturalHoldQuarterTurns(Surface.ROTATION_90, fullScreen = true))
        assertEquals(2, naturalHoldQuarterTurns(Surface.ROTATION_180, fullScreen = true))
        assertEquals(3, naturalHoldQuarterTurns(Surface.ROTATION_270, fullScreen = true))
    }

    @Test
    fun `in split screen or a freeform window there is no hold, at any rotation`() {
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            assertEquals("rotation $rotation", 0, naturalHoldQuarterTurns(rotation, fullScreen = false))
        }
    }

    @Test
    fun `the natural frame's shape is the window's after an even turn and swapped after an odd one`() {
        assertEquals("portrait window, no turn: portrait-natural", false, naturalFrameIsLandscape(windowIsLandscape = false, quarterTurns = 0))
        assertEquals("landscape window turned a quarter: portrait-natural", false, naturalFrameIsLandscape(windowIsLandscape = true, quarterTurns = 1))
        assertEquals("landscape window turned three quarters: portrait-natural", false, naturalFrameIsLandscape(windowIsLandscape = true, quarterTurns = 3))
        assertEquals("portrait window turned a quarter: landscape-natural (a tablet)", true, naturalFrameIsLandscape(windowIsLandscape = false, quarterTurns = 1))
        assertEquals("landscape window, no turn: landscape-natural", true, naturalFrameIsLandscape(windowIsLandscape = true, quarterTurns = 0))
        assertEquals("landscape window, half turn: landscape-natural", true, naturalFrameIsLandscape(windowIsLandscape = true, quarterTurns = 2))
    }

    private val window = EdgeInsets(left = 1.dp, top = 2.dp, right = 3.dp, bottom = 4.dp)

    @Test
    fun `no turn, the insets are the window's`() {
        assertEquals(window, windowEdgesInNaturalFrame(window, quarterTurns = 0))
    }

    @Test
    fun `a quarter turn (ROTATION_90), natural top is the window's left, natural bottom its right`() {
        assertEquals(EdgeInsets(left = 4.dp, top = 1.dp, right = 2.dp, bottom = 3.dp), windowEdgesInNaturalFrame(window, quarterTurns = 1))
    }

    @Test
    fun `a half turn (ROTATION_180), opposite edges swap`() {
        assertEquals(EdgeInsets(left = 3.dp, top = 4.dp, right = 1.dp, bottom = 2.dp), windowEdgesInNaturalFrame(window, quarterTurns = 2))
    }

    @Test
    fun `three quarters (ROTATION_270), natural top is the window's right, natural bottom its left`() {
        assertEquals(EdgeInsets(left = 2.dp, top = 3.dp, right = 4.dp, bottom = 1.dp), windowEdgesInNaturalFrame(window, quarterTurns = 3))
    }
}
