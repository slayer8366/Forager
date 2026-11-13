package com.zynergylabs.forager.app.ui.availability

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/*
 * Dispatch 2026-11-11-01, Amendment Q, step 1 (a probe, not for merge): each of QC1 to QC4 seen failing, or measured, through
 * the real screen, before anything is built. Measurements are printed (lines starting "Q ").
 */

private fun Rect.d() = "[%.1f, %.1f][%.1f, %.1f]".format(left, top, right, bottom)

/** Lines of [text]'s node that end inside a word: the letter before the break and the one after it are both letters. */
private fun brokenInsideWords(rule: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>, text: String): List<Int> {
    val layouts = mutableListOf<TextLayoutResult>()
    rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
    val layout = layouts.single()
    return (0 until layout.lineCount - 1).filter { i ->
        val end = layout.getLineEnd(i)
        end in 1 until text.length && text[end - 1].isLetter() && text[end].isLetter()
    }
}

abstract class QProbeBase {
    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    protected fun shown(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    protected fun all(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
    protected fun bounds(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    protected fun root() = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot

    internal fun navigatingRig() = RealSearchScreenRig(
        composeRule,
        recording = AvailabilityScreenRecording(isRecording = true),
        navigation = AvailabilityScreenNavigation(isReturning = true),
    )

    /** Every piece of chrome's bounds, printed. */
    protected fun printLayout(where: String) {
        val parts = listOf(
            "notice" to SEARCH_FAILED_NOTICE_TAG, "tryAgain" to SEARCH_FAILED_TRY_AGAIN_TAG, "banner" to SEARCH_NOTICE_TAG,
            "bar" to SEARCH_ENTRY_BAR_TAG, "strip" to "compass-elevation-strip", "card" to NAVIGATION_HUD_TAG,
            "cluster" to MAP_ICON_CLUSTER_TAG, "rail" to COMPACT_NAVIGATION_RAIL_TAG,
        )
        println("Q $where: root ${root().d()}; " + parts.joinToString("; ") { (n, t) -> "$n ${all(t).joinToString { it.d() }}" })
    }
}

/** QC1: a failed automatic try is followed by another 3 s later, then 10 s after that, then none. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class QProbeAutomaticTriesTest : QProbeBase() {

    private fun signal(rig: RealSearchScreenRig, v: Boolean) {
        rig.networkValidated.value = v
        composeRule.waitForIdle()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.waitForIdle()
    }

    private fun pass(ms: Long) {
        composeRule.mainClock.advanceTimeBy(ms)
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))
        composeRule.waitForIdle()
    }

    @Test
    fun `a failed automatic try is followed by a second 3 s later and a third 10 s after that`() {
        val rig = RealSearchScreenRig(composeRule)
        rig.setScreen()
        signal(rig, false)
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertEquals("positive control: one fetch, failed", 1, rig.sightingsFetches.size)
        signal(rig, true)
        assertEquals("positive control: the first automatic try ran at once", 2, rig.sightingsFetches.size)
        pass(3_500)
        println("Q QC1: fetches 3.5 s after the first automatic try failed: ${rig.sightingsFetches.size}")
        assertEquals("QC1: no second automatic try 3 s after the first failed", 3, rig.sightingsFetches.size)
        pass(10_500)
        assertEquals("QC1: no third automatic try 10 s after the second failed", 4, rig.sightingsFetches.size)
        pass(60_000)
        assertEquals("QC1: a fourth automatic try ran", 4, rig.sightingsFetches.size)
    }
}

/** QC4: in fullscreen while navigating, a both-halves failure retried: "Request failed…" must not show over "Trying again…". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class QProbeFlashTest : QProbeBase() {

    @Test
    fun `fullscreen while navigating, a retry of both halves never shows the list's failure line above Trying again`() {
        val rig = navigatingRig()
        rig.setScreen()
        rig.availabilityFailure = IOException("no signal (test)")
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
        rig.settle()
        assertTrue("positive control: the notice shows", shown(SEARCH_FAILED_NOTICE_TAG))
        val gate = CompletableDeferred<Unit>()
        rig.sightingsGate = gate
        try {
            rig.touch(composeRule.onNodeWithTag(SEARCH_FAILED_TRY_AGAIN_TAG, useUnmergedTree = true))
            val retrying = composeRule.onAllNodesWithText(SEARCH_RETRYING_NOTICE_TEXT).fetchSemanticsNodes().isNotEmpty()
            val listLine = composeRule.onAllNodesWithText(LIST_SEARCH_FAILED_MESSAGE).fetchSemanticsNodes().isNotEmpty()
            val s = rig.viewModel.uiState.value
            println("Q QC4: retrying shown $retrying, list line shown $listLine; state errorMessage=${s.errorMessage}, sightingsError=${s.sightingsErrorMessage}, isLoadingSightings=${s.isLoadingSightings}, isRetrying=${s.isRetryingSearch}, sightingsFetches=${rig.sightingsFetches.size}")
            printLayout("QC4")
            assertTrue("positive control: the notice reads Trying again…", retrying)
            assertTrue("QC4: the list's failure line shows above \"Trying again…\"", !listLine)
        } finally {
            rig.sightingsGate = null
            gate.complete(Unit)
            rig.settle()
        }
    }
}

/** QC2 and QC3: font scale 2.0, navigating, not fullscreen. */
abstract class QProbeLargeFontTests(private val where: String) : QProbeBase() {

    @Test
    fun `at font 2 while navigating the notice keeps clear of the cluster, words whole, on the screen`() {
        val rig = navigatingRig()
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        assertTrue("positive control: the notice shows", shown(SEARCH_FAILED_NOTICE_TAG))
        printLayout(where)
        val notice = bounds(SEARCH_FAILED_NOTICE_TAG)
        val tryAgain = bounds(SEARCH_FAILED_TRY_AGAIN_TAG)
        val broken = brokenInsideWords(composeRule, SEARCH_FAILED_NOTICE_TEXT)
        println("Q $where: notice width ${notice.width}, lines broken inside a word: $broken")
        val problems = mutableListOf<String>()
        for (c in all(MAP_ICON_CLUSTER_TAG)) if (tryAgain.overlaps(c) || notice.overlaps(c)) problems += "covered by the cluster ${c.d()}"
        for (c in all(NAVIGATION_HUD_TAG) + all("compass-elevation-strip")) if (notice.overlaps(c)) problems += "overlaps ${c.d()}"
        if (broken.isNotEmpty()) problems += "words broken inside on lines $broken"
        if (notice.bottom > root().bottom + 1f) problems += "runs off the screen (bottom ${notice.bottom} > ${root().bottom})"
        assertEquals("$where: the notice ${notice.d()}, Try again ${tryAgain.d()}", emptyList<String>(), problems)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeLargeFontPortraitTest : QProbeLargeFontTests("QC2 portrait font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeLargeFontShortLandscapeTest : QProbeLargeFontTests("QC3 short landscape font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1318dp-h824dp-land", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeLargeFontLandscapeTabletTest : QProbeLargeFontTests("QC3 landscape tablet font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeFont1ShortLandscapeTest : QProbeLargeFontTests("reference short landscape font 1")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeLargeFontPortrait360Test : QProbeLargeFontTests("QC2 portrait 360x780 font 2")

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class QProbeLargeFontLandscape780Test : QProbeLargeFontTests("QC3 landscape 780x360 font 2")
