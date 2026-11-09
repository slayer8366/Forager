package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.robolectric.Shadows
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-10-12-06, step 2 (RECORD -934, the owner: "Trim extras when tight (Recommended)"): the real widths of the
 * navigation card's readouts, in their own types, at fonts 1.0, 1.3 and 2.0, measured before the three-column fit is written.
 *
 * A measurement harness, not a guard: each width is printed as an `RMS|` line and read off the JUnit XML's system-out into
 * the report. Text width does not depend on the window's width, only on the font scale, so one window per font serves every
 * width; the room each width leaves is worked out in the report from the card's fixed parts. The one assertion is a positive
 * control: every string measured a width above zero, so an empty measurement cannot be read as "fits".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoundedStripWidthMeasurementTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The host activity declared, as the other Robolectric Compose tests here do; the first run failed without it.
    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val small = listOf(
        "by trail", "straight",
        "sharp right 169°", "slight left 10°", "ahead 5°", "behind 175°", "169°", "5°",
        "Alt", "9843 ft", "344 ft", "Elevation unavailable",
        "315° NW", "134° SE", "—",
        "Compass unavailable", "Compass unreliable", "Compass calibrating…", "Compass unavailable · north up",
        "10T ER 24991 40768", "-45.5234, -122.6876",
        "Dark since 7:01 PM", "Sunset 10:58 PM · start back by 10:48", "Back by 11:30 PM", "Sun stays down today",
        "Location services unavailable", "Last fix 45 s ago", "No fix for 6 min", "Approximate, finding GPS…",
        "No origin waypoint for this track", "Unable to calculate route", "Try again", "Approaching", "≈ 1250 ft straight",
    )
    private val figures = listOf("1280 ft", "≈ 800 ft", "≈ 1250 ft", "within 16 ft", "0.4 mi", "12.3 mi", "Arrived", "—")

    private fun measure(font: String) {
        val lines = mutableListOf<String>()
        composeRule.setContent {
            ForagerTheme(darkTheme = false) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val label = MaterialTheme.typography.labelMedium
                val figure = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")
                val strip = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
                fun line(styleName: String, style: TextStyle, text: String) {
                    val r = measurer.measure(text, style, maxLines = 1, softWrap = false)
                    val w = with(density) { r.size.width.toDp().value }
                    val h = with(density) { r.size.height.toDp().value }
                    lines += "RMS|font=$font|style=$styleName|w=%.2f|h=%.2f|text=$text".format(w, h)
                }
                small.forEach { line("labelMedium", label, it) }
                small.forEach { line("strip14", strip, it) }
                figures.forEach { line("figure", figure, it) }
                line("labelLargeRetry", MaterialTheme.typography.labelLarge, "Try again")
            }
        }
        composeRule.waitForIdle()
        lines.forEach { println(it) }
        assertTrue("positive control: every string measured, ${lines.size}", lines.size == small.size * 2 + figures.size + 1)
        assertTrue("positive control: no zero width", lines.none { it.contains("|w=0.00|") })
    }

    @Test fun `font 1,0 widths`() = measure("1.0")

    @Test @Config(fontScale = 1.3f) fun `font 1,3 widths`() = measure("1.3")

    @Test @Config(fontScale = 2.0f) fun `font 2,0 widths`() = measure("2.0")
}
