package com.zynergylabs.forager.app.ui.motion

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.zynergylabs.forager.app.ui.availability.layoutFixesHostActivityRule
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

/**
 * Dispatch 2026-10-31-04 (RECORD -1907; docs/audits/2026-10-31-recording-screen-off-recomposition.md, fix A): a number changing
 * while the activity is stopped must leave composition quiet. Compose keeps composing a stopped activity with its frame clock
 * paused, so a transition started then cannot finish.
 *
 * In its own class, with no compose rule, on purpose. The rule installs its own Recomposer on a test clock through
 * WindowRecomposerPolicy (ui-test 1.12.0, ComposeUiTest.android.kt:608, :771), so stopping the activity under it pauses nothing
 * and this failure cannot happen; the "numbers alone" test above passed with the fault in place for that reason. Here a plain
 * ActivityScenario and setContent get the real lifecycle-aware recomposer, and the test confirms it did: [clockFrames] stops at
 * the stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WordSwapStoppedActivityTest {

    @get:Rule
    val host = layoutFixesHostActivityRule()

    private var text by mutableStateOf("Sunset 5:59 PM · in 1 h 9 min · dark 6:29")
    private var shown: String? = null
    private var clockFrames = 0
    private var outerText: String? = null
    private var choreoFrames = 0

    private fun frames(count: Int) = repeat(count) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(FRAME_MILLIS)) }

    private fun recomposerOf(activity: ComponentActivity): Recomposer {
        val content = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val composeView: View = content.getChildAt(0)
        return composeView.findViewTreeCompositionContext() as Recomposer
    }

    private fun onScreen(activity: ComponentActivity) {
        activity.setContent {
            ForagerTheme {
                WordSwap(text = text) { line ->
                    shown = line
                    Text(line)
                }
            }
            outerText = text
            LaunchedEffect(Unit) {
                fun tick() { android.view.Choreographer.getInstance().postFrameCallback { choreoFrames++; tick() } }
                tick()
            }
            LaunchedEffect(Unit) { while (true) withFrameNanos { clockFrames++ } }
        }
    }

    @Test
    fun `a number changing while the activity is stopped leaves composition quiet, and the latest number shows`() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            lateinit var recomposer: Recomposer
            scenario.onActivity(::onScreen)
            frames(SETTLE_FRAMES)
            scenario.onActivity { recomposer = recomposerOf(it) }
            assertEquals("the frame clock ran while on screen", true, clockFrames > 0)

            scenario.moveToState(Lifecycle.State.CREATED)
            frames(SETTLE_FRAMES)
            val clockAtStop = clockFrames

            scenario.onActivity {
                text = "Sunset 5:59 PM · in 1 h 8 min · dark 6:29"
                Snapshot.sendApplyNotifications()
            }
            repeat(12) { i ->
                frames(5)
                scenario.onActivity { println("PROBE $i state=${recomposer.currentState.value} pending=${recomposer.hasPendingWork} changes=${recomposer.changeCount} shown=$shown outer=$outerText choreo=$choreoFrames attached=${it.window.decorView.isAttachedToWindow} life=${it.lifecycle.currentState}") }
            }

            var applied = 0
            val observer = Snapshot.registerApplyObserver { changed, _ -> if (changed.isNotEmpty()) applied++ }
            val changesBefore = recomposer.changeCount
            frames(COUNTED_FRAMES)
            val changes = recomposer.changeCount - changesBefore
            observer.dispose()

            assertEquals("the real lifecycle paused the frame clock, or this test shows nothing", clockAtStop, clockFrames)
            assertEquals("snapshots applied over $COUNTED_FRAMES frames, with $changes recompositions", 0, applied)
            assertEquals("recompositions over $COUNTED_FRAMES frames", 0L, changes)
            assertEquals("Sunset 5:59 PM · in 1 h 8 min · dark 6:29", shown)

            scenario.moveToState(Lifecycle.State.RESUMED)
            frames(SETTLE_FRAMES)
            assertEquals("Sunset 5:59 PM · in 1 h 8 min · dark 6:29", shown)
        }
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val SETTLE_FRAMES = 20
        const val COUNTED_FRAMES = 30
    }
}
