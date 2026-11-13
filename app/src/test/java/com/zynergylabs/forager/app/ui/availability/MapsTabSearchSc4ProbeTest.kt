package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** SC4 probe (not for merge): measurements printed, lines starting "S ". Nothing asserted. */
abstract class Sc4Probe(private val where: String, private val navigating: Boolean, private val fullscreen: Boolean) {
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(RealSearchScreenRig.touchModeHost()).around(composeRule)

    private val rig = if (navigating) {
        RealSearchScreenRig(composeRule, recording = AvailabilityScreenRecording(isRecording = true), navigation = AvailabilityScreenNavigation(isReturning = true))
    } else {
        RealSearchScreenRig(composeRule)
    }

    private fun all(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
    private fun Rect.d() = "[%.0f,%.0f][%.0f,%.0f]".format(left, top, right, bottom)

    private fun layoutOfTag(tag: String): TextLayoutResult? {
        val node = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        val out = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.firstOrNull()
    }

    private fun layoutOfText(text: String): TextLayoutResult? {
        val node = composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        val out = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(out)
        return out.firstOrNull()
    }

    @Test
    fun measure() {
        rig.setScreen()
        rig.sightingsFailure = IOException("no signal (test)")
        rig.searchCoordinates("45.3736", "-121.6959")
        if (fullscreen) {
            rig.touch(composeRule.onAllNodesWithContentDescription("Fullscreen").onFirst())
            rig.settle()
        }
        val root = composeRule.onAllNodes(isRoot()).onFirst().fetchSemanticsNode().boundsInRoot
        val parts = listOf("bar" to SEARCH_ENTRY_BAR_TAG, "tryAgain" to SEARCH_FAILED_TRY_AGAIN_TAG, "notice" to SEARCH_FAILED_NOTICE_TAG,
            "strip" to "compass-elevation-strip", "card" to NAVIGATION_HUD_TAG, "cluster" to MAP_ICON_CLUSTER_TAG, "rail" to COMPACT_NAVIGATION_RAIL_TAG,
            "coords" to COMPASS_STRIP_COORDINATES_TAG)
        println("S $where: root ${root.d()}; " + parts.joinToString("; ") { (n, t) -> "$n ${all(t).joinToString { it.d() }}" })
        layoutOfTag(COMPASS_STRIP_COORDINATES_TAG)?.let { println("S $where: coordinates <${it.layoutInput.text}> ellipsized ${it.isLineEllipsized(0)}, lines ${it.lineCount}") }
        layoutOfText(SEARCH_BAR_FAILED_TEXT)?.let { println("S $where: bar line ellipsized ${it.isLineEllipsized(0)}, lines ${it.lineCount}") }
        layoutOfText(SEARCH_FAILED_NOTICE_TEXT)?.let { l ->
            val t = SEARCH_FAILED_NOTICE_TEXT
            val broken = (0 until l.lineCount - 1).filter { i -> val e = l.getLineEnd(i); e in 1 until t.length && t[e - 1].isLetter() && t[e].isLetter() }
            val m = TextMeasurer(l.layoutInput.fontFamilyResolver, l.layoutInput.density, l.layoutInput.layoutDirection)
            val longestWord = t.split(" ").maxOf { m.measure(it, l.layoutInput.style, maxLines = 1, softWrap = false).size.width }
            val whole = m.measure(t, l.layoutInput.style, maxLines = 1, softWrap = false).size.width
            println("S $where: notice lines ${l.lineCount}, broken inside words $broken; text box width ${l.size.width}; longest word $longestWord px; whole line $whole px")
        }
        layoutOfText(SEARCH_FAILED_TRY_AGAIN_TEXT)?.let { println("S $where: Try again text width ${it.size.width}") }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4BarShortLandscape2Test : Sc4Probe("i: bar, short landscape font 2, floor grown", navigating = false, fullscreen = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4BarLandscape780Test : Sc4Probe("i: bar, 780x360 landscape font 2, floor grown", navigating = false, fullscreen = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4BarShortLandscape2NavTest : Sc4Probe("i: bar, short landscape font 2, navigating, floor grown", navigating = true, fullscreen = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4FsPortraitNavTest : Sc4Probe("ii: fullscreen portrait 360x780 font 2, navigating", navigating = true, fullscreen = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4FsPortraitTest : Sc4Probe("ii: fullscreen portrait 360x780 font 2, not navigating", navigating = false, fullscreen = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4FsLandscapeNavTest : Sc4Probe("ii: fullscreen short landscape font 2, navigating", navigating = true, fullscreen = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4FsLandscapeTest : Sc4Probe("ii: fullscreen short landscape font 2, not navigating", navigating = false, fullscreen = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Sc4FsLandscape780NavTest : Sc4Probe("ii: fullscreen 780x360 landscape font 2, navigating", navigating = true, fullscreen = true)
