package com.zynergylabs.forager.app.ui.theme

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Motion Part 3, item 4 (the owner, RECORD -651: Night mode "Fade the colours", and RECORD -652: "fast and smooth, and not
 * ceremonial and boring"), as rebuilt for RECORD -752 (dispatch 2026-09-28-755, item 3: on the S22 the switch went light, one
 * muddy grey middle frame, dark), through [ForagerTheme], the app's one theme root, which `MainActivity` hands the Night mode
 * setting as `darkTheme`.
 *
 * Two things are measured. What a screen under the theme is composed with ([seen], every composition), which shows whether the
 * change recomposes the app once or on every frame (the cost that left the S22 one middle frame). And what is drawn: the window's
 * pixels, frame by frame, read from a capture of the root, which shows the blend itself (never darker than the darker end nor
 * lighter than the lighter, several frames between the ends, over in well under half a second). How it looks on the phone is a
 * device item.
 */
@RunWith(RobolectricTestRunner::class)
// A small, low-density window: every frame is read back as a whole-window capture, and the colour is the same everywhere.
@Config(sdk = [36], qualifiers = "w240dp-h320dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NightModeFadeTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private val resolver get() = ApplicationProvider.getApplicationContext<Application>().contentResolver

    @After
    fun restore() {
        composeRule.mainClock.autoAdvance = true
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    }

    private val dark = mutableStateOf(false)

    /** Every background colour a screen under the theme was composed with, in order. */
    private val seen = mutableListOf<Color>()

    private fun setScreen() {
        org.robolectric.shadows.ShadowLog.stream = System.out
        composeRule.setContent {
            ForagerTheme(darkTheme = dark.value) {
                val background = MaterialTheme.colorScheme.background
                SideEffect { seen += background }
                Box(Modifier.fillMaxSize().background(background))
            }
        }
        composeRule.waitForIdle()
        // Robolectric draws the window only when something reads it back (a capture), unlike a phone, which draws every
        // frame. One capture here is the frame the phone would have drawn before the change, which the fade keeps as its picture.
        drawn()
    }

    private fun flipToDarkWithTheClockStopped() {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread {
            dark.value = true
            Snapshot.sendApplyNotifications()
        }
    }

    /** The colour drawn at the window's centre now. */
    private fun drawn(): Color {
        val map = composeRule.onRoot().captureToImage().toPixelMap()
        return map[map.width / 2, map.height / 2]
    }

    /** Within one 8-bit step per channel, which is what a capture can hold. */
    private fun Color.near(other: Color) =
        kotlin.math.abs(red - other.red) <= 1.5f / 255 && kotlin.math.abs(green - other.green) <= 1.5f / 255 && kotlin.math.abs(blue - other.blue) <= 1.5f / 255

    /** The drawn colour on each of [frames] frames after the change. */
    private fun drawnPerFrame(frames: Int): List<Color> {
        flipToDarkWithTheClockStopped()
        val perFrame = mutableListOf<Color>()
        repeat(frames) {
            composeRule.mainClock.advanceTimeByFrame()
            perFrame += drawn()
        }
        return perFrame
    }

    @Test
    fun `positive control - the capture reads the light scheme's background before the change`() {
        setScreen()
        assertTrue("the light background is drawn: ${drawn()} vs ${LightColors.background}", drawn().near(LightColors.background))
    }

    @Test
    fun `turning night mode on recomposes the app once, not on every frame of the fade`() {
        setScreen()
        assertEquals(LightColors.background, seen.last())
        seen.clear()
        val perFrame = drawnPerFrame(30)
        println("MEASURED -752 compositions during the fade: $seen; drawn $perFrame")
        assertTrue("positive control: composed again after the change", seen.isNotEmpty())
        assertTrue("every composition is at one end or the other, never a scheme part-way: $seen", seen.all { it == LightColors.background || it == DarkColors.background })
        assertTrue("at most two compositions over the fade's 30 frames (the switch, once), not one a frame: ${seen.size}", seen.size <= 2)
    }

    @Test
    fun `the drawn blend goes from light to dark over several frames, never past either end, and is over in half a second`() {
        setScreen()
        val light = LightColors.background
        val darkEnd = DarkColors.background
        val perFrame = drawnPerFrame(30)
        println("MEASURED -752 drawn per frame (luminance): ${perFrame.map { "%.3f".format(it.luminance()) }}")
        val lo = minOf(light.luminance(), darkEnd.luminance())
        val hi = maxOf(light.luminance(), darkEnd.luminance())
        perFrame.forEachIndexed { i, c ->
            assertTrue("frame $i: luminance ${c.luminance()} is not below the darker end $lo (no grey dip)", c.luminance() >= lo - 0.005f)
            assertTrue("frame $i: luminance ${c.luminance()} is not above the lighter end $hi", c.luminance() <= hi + 0.005f)
        }
        val between = perFrame.filter { !it.near(light) && !it.near(darkEnd) }
        assertTrue("at least three frames are drawn part-way between the ends, not one: ${between.size} of ${perFrame.map { it.luminance() }}", between.size >= 3)
        perFrame.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue("frame ${i + 1} is no lighter than frame $i (${b.luminance()} vs ${a.luminance()})", b.luminance() <= a.luminance() + 0.005f)
        }
        assertTrue("settled on the dark background within half a second: ${perFrame.last()}", perFrame.last().near(darkEnd))
        assertEquals(DarkColors.background, seen.last())
    }

    @Test
    fun `under reduced motion night mode changes every colour at once, with no frame in between`() {
        Settings.Global.putFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 0f)
        setScreen()
        seen.clear()
        val perFrame = drawnPerFrame(10)
        assertTrue("composed again after the change", seen.isNotEmpty())
        assertTrue("only the two ends were ever composed: $seen", seen.all { it == LightColors.background || it == DarkColors.background })
        assertTrue("only the two ends were ever drawn: $perFrame", perFrame.all { it.near(LightColors.background) || it.near(DarkColors.background) })
        assertTrue("dark from the first frame after the change: ${perFrame.first()}", perFrame.first().near(DarkColors.background))
    }
}
