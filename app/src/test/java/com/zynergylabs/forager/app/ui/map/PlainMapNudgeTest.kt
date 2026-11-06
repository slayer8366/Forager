package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.location.modes.CameraMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * Dispatch 2026-10-19-37: the plain map's nudge through its real entry point, [PlainMapNudge.onTouch],
 * which the map view's touch listener calls with each MotionEvent (SightingsMapLayout.kt). The map's side
 * is a recording [PlainNudgeMap]: what MapLibre does with the options, a finger, the padding and the
 * picture-keeping clear is device-only, since a real MapView cannot run under Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "xxhdpi")
class PlainMapNudgeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val events = mutableListOf<MotionEvent>()

    private var mode: Int? = CameraMode.TRACKING
    private var springBack = true
    private var navigating = false
    private val shown = mutableListOf<DoubleArray>()
    private var clears = 0
    private val flingWrites = mutableListOf<Boolean>()

    /** The padding the camera holds: what was last shown, until cleared. */
    private var cameraPadding = doubleArrayOf(0.0, 0.0, 0.0, 0.0)

    /** Following ends once this many paddings have been shown: a leave at a set frame, however the frames are timed. */
    private var followingEndsAfter = Int.MAX_VALUE
    private val map = object : PlainNudgeMap {
        override fun cameraMode() = mode

        override fun showPadding(padding: DoubleArray) {
            shown += padding
            cameraPadding = padding
            if (shown.size >= followingEndsAfter) mode = CameraMode.NONE
        }

        override fun clearPaddingKeepingPicture() {
            clears++
            cameraPadding = doubleArrayOf(0.0, 0.0, 0.0, 0.0)
        }

        override fun paddingShown() = cameraPadding.any { it != 0.0 }

        override var flingEnabled: Boolean = true
            set(value) {
                field = value
                flingWrites += value
            }
    }
    private val nudge = PlainMapNudge(map, springBack = { springBack }, navigating = { navigating }, thresholdPx = 96f)

    @After
    fun recycle() {
        nudge.cancel()
        events.forEach(MotionEvent::recycle)
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        nudge.onTouch(MotionEvent.obtain(now, now, action, x, y, 0).also { events += it }, density = 3f)
    }

    private fun springFor(millis: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    // The options.

    @Test
    fun `the plain nudge's options manage tracking gestures at navigation's thresholds`() {
        val density = context.resources.displayMetrics.density
        val options = plainMapNudgeOptions(context, PositionLook.PRECISE)
        assertTrue(options.trackingGesturesManagement())
        assertEquals(NAVIGATION_NUDGE_THRESHOLD_DP * density, options.trackingInitialMoveThreshold(), 0.5f)
        assertEquals(NAVIGATION_MULTI_FINGER_MOVE_THRESHOLD_DP * density, options.trackingMultiFingerMoveThreshold(), 0.5f)
    }

    @Test
    fun `a map with the spring-back holds the nudge's options outside navigation, and one without holds the ordinary ones`() {
        assertTrue("Maps tab", ordinaryLocationOptions(context, PositionLook.PRECISE, nudgeSpringBack = true).trackingGesturesManagement())
        val ordinary = ordinaryLocationOptions(context, PositionLook.PRECISE, nudgeSpringBack = false)
        assertFalse("a picker", ordinary.trackingGesturesManagement())
        assertEquals("exactly the ordinary options", liveLocationComponentOptions(context), ordinary)
    }

    @Test
    fun `the nudge's options keep the dot's look`() {
        val options = plainMapNudgeOptions(context, PositionLook.APPROXIMATE)
        assertEquals(liveLocationComponentOptions(context, look = PositionLook.APPROXIMATE).foregroundName(), options.foregroundName())
    }

    // When the plain map follows for the nudge.

    @Test
    fun `the plain map follows for the nudge only with the spring-back, outside navigation, and following`() {
        assertTrue(plainMapFollowing(springBack = true, navigating = false, cameraMode = CameraMode.TRACKING))
        assertFalse("a picker", plainMapFollowing(springBack = false, navigating = false, cameraMode = CameraMode.TRACKING))
        assertFalse("navigating: navigation's own path", plainMapFollowing(springBack = true, navigating = true, cameraMode = CameraMode.TRACKING))
        assertFalse("not following", plainMapFollowing(springBack = true, navigating = false, cameraMode = CameraMode.NONE))
        assertFalse("no location component", plainMapFollowing(springBack = true, navigating = false, cameraMode = null))
    }

    // Steps 1 and 2, through the touch listener's call.

    @Test
    fun `a nudge gives half the finger's travel, springs back past rest, settles with no padding, and the release has no fling`() {
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 40f, 0f)
        assertArrayEquals("half of 40 px, shown by twice that on the left, no top padding", doubleArrayOf(40.0, 0.0, 0.0, 0.0), shown.last(), 1e-6)
        touch(MotionEvent.ACTION_UP, 40f, 0f)
        assertEquals("the release reaches MapLibre with no fling", false, map.flingEnabled)
        springFor(NAVIGATION_NUDGE_SPRING_MILLIS + 100)
        assertTrue("the spring passes rest: the right side opens", shown.any { it[2] > 0.0 })
        assertArrayEquals("settled", doubleArrayOf(0.0, 0.0, 0.0, 0.0), shown.last(), 1e-6)
        assertEquals("following never ended: nothing to clear", 0, clears)
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        assertEquals("the fling is back before MapLibre sees the next touch", true, map.flingEnabled)
    }

    @Test
    fun `while navigating the plain nudge does nothing`() {
        navigating = true
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 40f, 0f)
        touch(MotionEvent.ACTION_UP, 40f, 0f)
        springFor(NAVIGATION_NUDGE_SPRING_MILLIS + 100)
        assertTrue("no padding", shown.isEmpty())
        assertTrue("fling untouched", flingWrites.isEmpty())
        assertEquals(0, clears)
    }

    @Test
    fun `a map without the spring-back does nothing`() {
        springBack = false
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 40f, 0f)
        touch(MotionEvent.ACTION_UP, 40f, 0f)
        springFor(NAVIGATION_NUDGE_SPRING_MILLIS + 100)
        assertTrue("no padding", shown.isEmpty())
        assertTrue("fling untouched", flingWrites.isEmpty())
        assertEquals(0, clears)
    }

    // Step 3, and the give it leaves on screen.

    @Test
    fun `a drag that leaves following gets no spring, keeps its fling, and has its give cleared once at the release`() {
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 90f, 0f)
        mode = CameraMode.NONE // MapLibre ended tracking past the threshold
        touch(MotionEvent.ACTION_MOVE, 150f, 0f)
        val before = shown.size
        assertEquals("not cleared mid-drag", 0, clears)
        touch(MotionEvent.ACTION_UP, 150f, 0f)
        springFor(NAVIGATION_NUDGE_SPRING_MILLIS + 100)
        assertEquals("nothing sent once following ended", before, shown.size)
        assertTrue("fling untouched", flingWrites.isEmpty())
        assertEquals("the give left on screen is cleared, once", 1, clears)
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_UP, 0f, 0f)
        assertEquals("and not again", 1, clears)
    }

    @Test
    fun `a cancelled gesture that left following clears its give too`() {
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 60f, 0f)
        mode = CameraMode.NONE
        touch(MotionEvent.ACTION_CANCEL, 60f, 0f)
        assertEquals(1, clears)
    }

    @Test
    fun `a tap that never gave leaves nothing to clear when following has ended`() {
        mode = CameraMode.NONE
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_UP, 0f, 0f)
        assertEquals(0, clears)
    }

    @Test
    fun `the leftover forgets a give once the map is navigating, so navigation's padding is never cleared`() {
        val leftover = PlainNudgeLeftover()
        leftover.noteShown(doubleArrayOf(40.0, 0.0, 0.0, 0.0))
        assertFalse(leftover.onRelease(MotionEvent.ACTION_UP, plainMap = false, stillFollowing = false))
        assertFalse("forgotten", leftover.onRelease(MotionEvent.ACTION_UP, plainMap = true, stillFollowing = false))
    }

    // A spring cut short (the owner's ruling "Fix it in #337 first"): following ends mid-spring with no finger down.

    @Test
    fun `a give left by a spring cut short is cleared when locate is tapped, before following resumes`() {
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_MOVE, 40f, 0f)
        // Under Robolectric the whole spring runs within the first idle, so the leave is set by frame: following ends at
        // the spring's second frame (a search frame or the reset-orientation tap, with no finger down).
        followingEndsAfter = shown.size + 2
        touch(MotionEvent.ACTION_UP, 40f, 0f)
        springFor(NAVIGATION_NUDGE_SPRING_MILLIS + 100)
        assertEquals("the spring stopped where following ended", followingEndsAfter, shown.size)
        assertTrue("part of the give is still on screen", map.paddingShown())
        assertEquals("no release came after the leave, so nothing cleared it yet", 0, clears)
        nudge.beforeLocate()
        assertEquals("cleared before locate sets following", 1, clears)
        assertFalse(map.paddingShown())
        touch(MotionEvent.ACTION_DOWN, 0f, 0f)
        touch(MotionEvent.ACTION_UP, 0f, 0f)
        assertEquals("and the leftover's record is gone too", 1, clears)
    }

    @Test
    fun `locate while still following leaves the give to the spring`() {
        // Locate tapped mid-spring, or while a finger holds a give: following is on and padding is shown.
        cameraPadding = doubleArrayOf(40.0, 0.0, 0.0, 0.0)
        mode = CameraMode.TRACKING
        nudge.beforeLocate()
        assertEquals("no camera move, which would end following", 0, clears)
    }

    @Test
    fun `locate with no padding on screen clears nothing`() {
        nudge.beforeLocate()
        assertEquals(0, clears)
    }

    @Test
    fun `locate while navigating leaves the camera's padding to navigation`() {
        cameraPadding = doubleArrayOf(0.0, 282.0, 0.0, 0.0) // navigation's own top padding
        navigating = true
        nudge.beforeLocate()
        assertEquals(0, clears)
    }

    @Test
    fun `locate on a map without the spring-back clears nothing`() {
        cameraPadding = doubleArrayOf(40.0, 0.0, 0.0, 0.0)
        springBack = false
        nudge.beforeLocate()
        assertEquals(0, clears)
    }
}
