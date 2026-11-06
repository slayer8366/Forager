package com.zynergylabs.forager.app.ui.map

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-13-02 (the owner, of the map credit pill: "left as is"): the credit pill carries no outline, day or night.
 * Its edge pixels, just inside its top, bottom and left, are its own tint, the same as a point inside it clear of the text.
 *
 * A guard, not evidence for this dispatch: the dispatch does not touch [MapCreditCaption], so this passes identically before
 * and after it. It fails if the map chrome outline is ever put on the credit pill.
 */
abstract class MapCreditCaptionNoOutlineTests(private val dark: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private fun lum(c: Color) = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue

    @Test
    fun `the credit pill's edges are its own tint, no outline`() {
        composeRule.setContent {
            ForagerTheme(darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(Color(0xFFFFFFFF))) {
                    MapCreditCaption(text = "© OpenStreetMap, SRTM, OpenTopoMap (CC-BY-SA)", modifier = Modifier.align(Alignment.BottomStart))
                }
            }
        }
        composeRule.waitForIdle()
        val c = composeRule.onNodeWithTag(MAP_CREDIT_CAPTION_TAG).getUnclippedBoundsInRoot()
        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val d = composeRule.density.density
        fun px(v: Float) = (v * d).roundToInt()
        val midX = px((c.left.value + c.right.value) / 2)
        val midY = px((c.top.value + c.bottom.value) / 2)
        // Inside its left end, in the 6 dp padding before the text: the tint.
        val tint = image[px(c.left.value + 3f), midY]
        val edges = mapOf(
            "top" to image[midX, px(c.top.value) + 1],
            "bottom" to image[midX, px(c.bottom.value) - 2],
            "left" to image[px(c.left.value) + 1, midY],
        )
        for ((side, p) in edges) {
            assertTrue(
                "the credit pill's $side edge is its tint (lum %.3f), no outline: read lum %.3f".format(lum(tint), lum(p)),
                abs(lum(p) - lum(tint)) < 0.03f,
            )
        }
        assertTrue("positive control: the tint is not the white behind it (lum %.3f)".format(lum(tint)), lum(tint) < 0.8f)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCreditCaptionNoOutlineDayTest : MapCreditCaptionNoOutlineTests(dark = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapCreditCaptionNoOutlineNightTest : MapCreditCaptionNoOutlineTests(dark = true)
