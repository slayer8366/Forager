package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * RECORD -951, item 4 (the planner's call, after the owner's "Just rearrange it"): the compass strip's own sundown and Back by
 * lines, when not navigating, break at their " · " joins into stacked pieces only when the line does not fit whole; nothing is
 * ellipsised. Native graphics, so the text is measured at real widths: a short line at 320 dp and font 1.0 is one whole line; the
 * recording's long line at 320 dp and font 2.0 is a stack, every piece whole. Before this, the line was one line ending in "…".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h780dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StripEveningLineStackTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    private fun show(line: String) {
        composeRule.setContent {
            ForagerTheme {
                val heading = remember { mutableStateOf<TrueHeadingReading>(TrueHeadingReading.Available(80f)) }
                CompassElevationStrip(
                    heading = heading,
                    elevationMeters = 123.0,
                    unitSystem = UnitSystem.IMPERIAL,
                    location = LatLng(45.33, -122.64),
                    showDecimalDegrees = false,
                    onToggleCoordinateFormat = {},
                    modifier = Modifier.fillMaxWidth(),
                    sundownLine = line,
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.waitForIdle()
    }

    private fun layouts(tag: String): List<TextLayoutResult> = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().mapNotNull { node ->
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        results.firstOrNull()
    }

    private fun ellipsised(layout: TextLayoutResult) = (0 until layout.lineCount).any { layout.isLineEllipsized(it) }

    @Test
    fun `a line that fits is one whole line, not stacked`() {
        show("Dark since 7:01 PM")
        val line = layouts(STRIP_SUNDOWN_LINE_TAG).single()
        assertEquals("Dark since 7:01 PM", line.layoutInput.text.text)
        assertEquals("one line", 1, line.lineCount)
        assertFalse("not ellipsised", ellipsised(line))
        assertEquals("no stacked pieces", 0, composeRule.onAllNodesWithTag(STRIP_EVENING_PIECE_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    @Config(fontScale = 2.0f)
    fun `at font 2,0 a line too long for the strip stacks at its joins, every piece whole, nothing ellipsised`() {
        val whole = "Sunset 10:58 PM · start back by 10:48 · dark 11:31"
        show(whole)
        val node = composeRule.onAllNodesWithTag(STRIP_SUNDOWN_LINE_TAG, useUnmergedTree = true).fetchSemanticsNodes().single()
        assertEquals("the line's node still reads the whole line", whole, node.config[SemanticsProperties.Text].joinToString("") { it.text })
        val pieces = layouts(STRIP_EVENING_PIECE_TAG)
        println("MEASURED strip stack at 320 dp, font 2.0: ${pieces.map { it.layoutInput.text.text to it.lineCount }}")
        assertEquals("three pieces, one per join", listOf("Sunset 10:58 PM", "start back by 10:48", "dark 11:31"), pieces.map { it.layoutInput.text.text })
        pieces.forEach { piece ->
            assertFalse("piece <${piece.layoutInput.text.text}> is not ellipsised", ellipsised(piece))
            assertTrue("piece <${piece.layoutInput.text.text}> shows whole", piece.getLineEnd(piece.lineCount - 1, visibleEnd = true) >= piece.layoutInput.text.text.length)
        }
    }
}
