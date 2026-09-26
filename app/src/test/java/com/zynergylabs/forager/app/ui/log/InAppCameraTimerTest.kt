package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.photo.TimerMode
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The self-timer (decision B8 / Closed decision C, 2026-09-26), driven through the real
 * [InAppCameraDialog]: the Timer chip, then the shutter, against [FakeCameraCaptureSession], whose
 * `captureCalls` is the count of actual captures.
 *
 * **Time is the test's.** Each countdown test stops the main clock's auto-advance and moves it by
 * hand, so "nothing at 2.9 s, one at 3 s" is read at exactly those instants of virtual time.
 *
 * **Every test that starts a countdown ends it in a `finally`** (CLAUDE.md, the unstopped poll
 * loop): it removes the dialog, which cancels the dialog's coroutine scope and the countdown with
 * it, and gives the clock back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InAppCameraTimerTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(FileProviderCacheReset()).around(composeRule)

    private val captured = mutableListOf<PhotoSource>()
    private var dismissals = 0
    private var shown by mutableStateOf(true)

    /** The dialog as its host shows it: Back reaches onDismiss, and the host removes the camera. */
    private fun setDialog(session: FakeCameraCaptureSession) {
        composeRule.setContent {
            if (shown) {
                InAppCameraDialog(
                    session = session,
                    cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                    lockToPortrait = false,
                    onPhotoCaptured = { captured += it },
                    onDismiss = { dismissals += 1; shown = false },
                    gridMode = GridMode.Off,
                    onGridModeChanged = {},
                    levelProvider = FakeLevelProvider(),
                    viewfinder = { modifier -> Box(modifier.fillMaxSize()) },
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun timerChip() = composeRule.onNodeWithTag(CAMERA_TIMER_CHIP_TAG)
    private fun shutter() = composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG)

    /** Taps the Timer chip [taps] times, with the clock still running. */
    private fun setTimer(taps: Int) {
        repeat(taps) {
            timerChip().performClick()
            composeRule.waitForIdle()
        }
    }

    /** Stops auto-advance, runs [body], and always ends the countdown by removing the dialog. */
    private fun withHandClock(body: () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        try {
            body()
        } finally {
            shown = false
            composeRule.mainClock.autoAdvance = true
            composeRule.waitForIdle()
        }
    }

    private fun advance(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.waitForIdle()
    }

    // ── The chip ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the timer starts Off and a tap cycles it Off, 3 s, 10 s, Off`() {
        setDialog(FakeCameraCaptureSession())
        timerChip().assertContentDescriptionEquals(TIMER_OFF_LABEL)

        listOf(TIMER_3_LABEL, TIMER_10_LABEL, TIMER_OFF_LABEL).forEach { label ->
            timerChip().performClick()
            composeRule.waitForIdle()
            timerChip().assertContentDescriptionEquals(label)
        }
    }

    @Test
    fun `each timer setting has its own glyph and label`() {
        assertEquals(TimerGlyph(Icons.Filled.TimerOff, "Timer off"), timerGlyph(TimerMode.Off))
        assertEquals(TimerGlyph(Icons.Filled.Timer3, "Timer 3 seconds"), timerGlyph(TimerMode.ThreeSeconds))
        assertEquals(TimerGlyph(Icons.Filled.Timer10, "Timer 10 seconds"), timerGlyph(TimerMode.TenSeconds))
    }

    /**
     * **A finger on the Timer chip reaches it**, over the viewfinder: real touches at screen
     * coordinates, five across the chip's own bounds (CLAUDE.md: a semantic click asserts wiring,
     * not routing, and a finger is not a point). Each touch moves the timer one step.
     */
    @Test
    fun `a real touch anywhere on the timer chip reaches it, over the viewfinder`() {
        setDialog(FakeCameraCaptureSession())
        val chip = timerChip().fetchSemanticsNode().boundsInRoot
        val inset = 0.2f
        val points = listOf(
            chip.center,
            Offset(chip.left + chip.width * inset, chip.top + chip.height * inset),
            Offset(chip.right - chip.width * inset, chip.top + chip.height * inset),
            Offset(chip.left + chip.width * inset, chip.bottom - chip.height * inset),
            Offset(chip.right - chip.width * inset, chip.bottom - chip.height * inset),
        )
        val expected = listOf(TIMER_3_LABEL, TIMER_10_LABEL, TIMER_OFF_LABEL, TIMER_3_LABEL, TIMER_10_LABEL)

        points.forEachIndexed { i, point ->
            composeRule.onRoot().performTouchInput { click(point) }
            composeRule.waitForIdle()
            timerChip().assertContentDescriptionEquals(expected[i])
        }
    }

    // ── The countdown ────────────────────────────────────────────────────────────────────────

    @Test
    fun `with the timer off, the shutter captures at once, as before`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)

        shutter().performClick()
        composeRule.waitForIdle()

        assertEquals(1, session.captureCalls)
        composeRule.onAllNodesWithTag(CAMERA_COUNTDOWN_TAG).assertCountEquals(0)
    }

    @Test
    fun `at 3 s, no capture at 2_9 s and exactly one at 3 s`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(2_900)
            assertEquals("nothing at 2.9 s", 0, session.captureCalls)
            advance(100)
            assertEquals("exactly one at 3 s", 1, session.captureCalls)
            advance(30_000)
            assertEquals("and no second", 1, session.captureCalls)
            assertEquals("handed up once", 1, captured.size)
        }
    }

    @Test
    fun `at 10 s, no capture at 9_9 s and exactly one at 10 s`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(2)
        withHandClock {
            shutter().performClick()
            advance(9_900)
            assertEquals("nothing at 9.9 s", 0, session.captureCalls)
            advance(100)
            assertEquals("exactly one at 10 s", 1, session.captureCalls)
            advance(30_000)
            assertEquals("and no second", 1, session.captureCalls)
        }
    }

    @Test
    fun `the countdown shows the remaining whole seconds, centred, and goes at zero`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(0)
            composeRule.onNodeWithTag(CAMERA_COUNTDOWN_TAG).assertTextEquals("3")
            val frame = composeRule.onNodeWithTag(IN_APP_CAMERA_TAG).fetchSemanticsNode().boundsInRoot
            val numerals = composeRule.onNodeWithTag(CAMERA_COUNTDOWN_TAG).fetchSemanticsNode().boundsInRoot
            assertEquals("centred across", frame.center.x, numerals.center.x, 1f)
            assertEquals("centred down", frame.center.y, numerals.center.y, 1f)
            advance(1_000)
            composeRule.onNodeWithTag(CAMERA_COUNTDOWN_TAG).assertTextEquals("2")
            advance(1_000)
            composeRule.onNodeWithTag(CAMERA_COUNTDOWN_TAG).assertTextEquals("1")
            advance(1_000)
            composeRule.onAllNodesWithTag(CAMERA_COUNTDOWN_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun `during a countdown the shutter says Cancel timer, and Take photo again after`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        shutter().assertContentDescriptionEquals(SHUTTER_DESCRIPTION)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(500)
            shutter().assertContentDescriptionEquals("Cancel timer")
            advance(2_500)
            assertEquals("precondition: the capture happened", 1, session.captureCalls)
            shutter().assertContentDescriptionEquals(SHUTTER_DESCRIPTION)
        }
    }

    @Test
    fun `pressing the shutter during a countdown cancels it, with no capture, ever`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(1_500)
            shutter().performClick()
            advance(30_000)
            assertEquals("no capture, ever", 0, session.captureCalls)
            composeRule.onAllNodesWithTag(CAMERA_COUNTDOWN_TAG).assertCountEquals(0)
            shutter().assertContentDescriptionEquals(SHUTTER_DESCRIPTION)
        }
    }

    @Test
    fun `Back during a countdown closes the camera, and nothing is captured after`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(1_000)
            composeRule.pressBackOnCamera()
            assertEquals("Back closed the camera as today", 1, dismissals)
            composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
            advance(30_000)
            assertEquals("no capture after close", 0, session.captureCalls)
            assertEquals(0, captured.size)
        }
    }

    @Test
    fun `the photo count moves only when the capture happens`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(2_000)
            composeRule.onNodeWithText(photoCountLabel(0)).assertTextEquals(photoCountLabel(0))
            advance(1_000)
            composeRule.onNodeWithText(photoCountLabel(1)).assertTextEquals(photoCountLabel(1))
        }
    }

    @Test
    fun `changing the timer during a countdown keeps the running countdown's length and applies to the next press`() {
        val session = FakeCameraCaptureSession()
        setDialog(session)
        setTimer(1)
        withHandClock {
            shutter().performClick()
            advance(1_000)
            timerChip().performClick() // 3 s to 10 s, mid-countdown
            advance(0)
            timerChip().assertContentDescriptionEquals(TIMER_10_LABEL)
            advance(1_900)
            assertEquals("nothing at 2.9 s", 0, session.captureCalls)
            advance(100)
            assertEquals("the running countdown kept its 3 s", 1, session.captureCalls)

            shutter().performClick()
            advance(9_900)
            assertEquals("the next press counts 10 s: nothing at 9.9 s", 1, session.captureCalls)
            advance(100)
            assertEquals("one at 10 s", 2, session.captureCalls)
        }
    }
}
