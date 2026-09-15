package com.zynergylabs.forager.app.ui.log

import android.view.Surface
import org.junit.Assert.assertEquals
import org.junit.Test

/** The two pure functions behind `Modifier.rotateWithDevice`: the angle for a surface rotation, and the short way round to it. Plain JVM: `Surface.ROTATION_*` are constants. */
class RotateWithDeviceTest {

    @Test
    fun `the device turned a quarter counter-clockwise needs the control turned a quarter clockwise, and the reverse`() {
        assertEquals(0f, uprightRotationDegrees(Surface.ROTATION_0))
        assertEquals(90f, uprightRotationDegrees(Surface.ROTATION_90))
        assertEquals(180f, uprightRotationDegrees(Surface.ROTATION_180))
        assertEquals(-90f, uprightRotationDegrees(Surface.ROTATION_270))
    }

    @Test
    fun `no reading yet means upright, which is where the controls already are`() {
        assertEquals(0f, uprightRotationDegrees(null))
    }

    @Test
    fun `an unknown rotation value is treated as upright rather than guessed`() {
        assertEquals(0f, uprightRotationDegrees(7))
    }

    @Test
    fun `the short way round is a quarter back, never three quarters forward`() {
        assertEquals(-90f, shortestRotationTarget(current = 0f, targetDegrees = -90f))
        assertEquals(-90f, shortestRotationTarget(current = 0f, targetDegrees = 270f))
        assertEquals(270f, shortestRotationTarget(current = 180f, targetDegrees = -90f)) // 180 → 270 is a quarter on; 180 → -90 would be three back
        assertEquals(90f, shortestRotationTarget(current = 0f, targetDegrees = 90f))
        assertEquals(0f, shortestRotationTarget(current = 90f, targetDegrees = 0f))
    }

    @Test
    fun `a half turn goes clockwise, and the same angle is no turn at all`() {
        assertEquals(180f, shortestRotationTarget(current = 0f, targetDegrees = 180f))
        // Exactly a half turn back is the boundary; the rule sends it forward, so both spellings agree.
        assertEquals(180f, shortestRotationTarget(current = 0f, targetDegrees = -180f))
        assertEquals(90f, shortestRotationTarget(current = 90f, targetDegrees = 90f))
    }

    @Test
    fun `the result is always within a half turn of where it started`() {
        for (current in listOf(-720f, -270f, -90f, 0f, 45f, 90f, 180f, 270f, 1000f)) {
            for (target in listOf(0f, 90f, 180f, -90f)) {
                val result = shortestRotationTarget(current, target)
                val delta = result - current
                assert(delta > -180f && delta <= 180f) { "from $current to $target moved $delta" }
                assertEquals("lands on the target mod 360", ((target % 360f) + 360f) % 360f, ((result % 360f) + 360f) % 360f, 0.0001f)
            }
        }
    }
}
