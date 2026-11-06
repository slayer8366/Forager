package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
 * RECORD -735 (the owner: "Same crossed-out icon (Recommended)"): the landscape strip keeps its heading slot at the value's
 * widest form ("000°" and the widest compass point, 14 sp tabular), and the crossed-out compass that stands for no compass or an
 * unreliable one is the readout line's square ([stripHeadingIconSize]); it must fit within that width at fonts 1.0 and 2.0.
 * Native graphics, so the text widths are real (Robolectric's legacy measurement would pass any fit).
 */
abstract class LandscapeHeadingIconFitTests(private val expectedFontScale: Float) {
    private val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(rule)

    @Test
    fun `the crossed-out compass fits within the heading's value width`() {
        var valuePx = -1
        var iconPx = -1
        var fontScale = 0f
        var density = 0f
        rule.setContent {
            MaterialTheme {
                val measurer = rememberTextMeasurer()
                val d = LocalDensity.current
                fontScale = d.fontScale
                density = d.density
                val style = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
                val widthOf: (String, TextStyle) -> Int = { text, s -> measurer.measure(text, s, maxLines = 1, softWrap = false).size.width }
                valuePx = landscapeHeadingValueWidthPx(widthOf, style)
                iconPx = with(d) { stripHeadingIconSize().roundToPx() }
            }
        }
        rule.waitForIdle()
        println("MEASURED font $fontScale: heading value width ${valuePx / density} dp, icon ${iconPx / density} dp")
        assertEquals("the font scale this test is about", expectedFontScale, fontScale, 0.01f)
        assertTrue("positive control: both were measured ($valuePx, $iconPx)", valuePx > 0 && iconPx > 0)
        assertTrue("the icon ($iconPx px) fits within the value's $valuePx px", iconPx <= valuePx)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeHeadingIconFitFont1Test : LandscapeHeadingIconFitTests(1.0f)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LandscapeHeadingIconFitFont2Test : LandscapeHeadingIconFitTests(2.0f)
