package com.zynergylabs.forager.app.ui.motion

import androidx.activity.ComponentActivity
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Motion Part 2, item 6 (dispatch 2026-09-28-666; the owner, RECORD -651: "Numbers instant, words fade"). [wordsOf] is checked
 * on the lines the strip and the navigation display really show; [WordSwap] is driven with the clock stopped, so a crossfade
 * shows as two lines on screen at once and a change in numbers alone as one line, already new.
 *
 * Nothing here has been run: written with the code, before the build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WordSwapTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(layoutFixesHostActivityRule()).around(composeRule)

    @After
    fun clockBack() {
        composeRule.mainClock.autoAdvance = true
    }

    @Test
    fun `numbers are one mark, words are kept`() {
        assertEquals("Fix # s ago", wordsOf("Fix 5 s ago"))
        assertEquals(wordsOf("Fix 5 s ago"), wordsOf("Fix 12 s ago"))
        assertEquals("a value gaining a digit is the same words", wordsOf("9° N"), wordsOf("10° N"))
        assertEquals("decimals are one number", wordsOf("0.4 mi"), wordsOf("12.75 mi"))
        assertEquals("thousands are one number", wordsOf("1,234 ft"), wordsOf("98 ft"))
        assertEquals("times keep their words", wordsOf("Start back by 7:42 PM"), wordsOf("Start back by 7:43 PM"))
        assertEquals("a changed compass point is a change of words", false, wordsOf("44° NE") == wordsOf("45° E"))
        assertEquals("a word in place of a number is a change of words", false, wordsOf("0.4 mi") == wordsOf("Arrived"))
    }

    private var text by mutableStateOf("Fix 5 s ago")

    private fun show() {
        composeRule.setContent {
            ForagerTheme {
                WordSwap(text = text) { shown -> Text(shown, modifier = Modifier.testTag(LINE_TAG)) }
            }
        }
        composeRule.waitForIdle()
    }

    private fun linesOnScreen(): List<String> =
        composeRule.onAllNodesWithTag(LINE_TAG).fetchSemanticsNodes().map { node -> node.config[SemanticsProperties.Text].joinToString { it.text } }

    private fun changeTo(next: String) {
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnUiThread { text = next; Snapshot.sendApplyNotifications() }
        repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
    }

    @Test
    fun `a change in numbers alone is drawn at once, with no crossfade`() {
        show()
        changeTo("Fix 6 s ago")
        assertEquals(listOf("Fix 6 s ago"), linesOnScreen())
    }

    @Test
    fun `a change in words crossfades, both lines on screen for a moment, then the new one alone`() {
        show()
        changeTo("No fix")
        assertEquals("mid-crossfade both lines are drawn", setOf("Fix 5 s ago", "No fix"), linesOnScreen().toSet())
        composeRule.mainClock.autoAdvance = true
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertEquals(listOf("No fix"), linesOnScreen())
    }

    private companion object {
        const val LINE_TAG = "word-swap-line"
    }
}
