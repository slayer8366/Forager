package com.zynergylabs.forager.app.ui.availability

import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.map.hex
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDisplay
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Dispatch 2026-11-06-05, Amendment 6, A2 (RECORD -2187). The owner: "The route card is one size, and its tucked drag bar is another
 * size. Have the drag bar be the same size as the card." and "When tucking the route card, the bar appears at the start of the
 * animation. It should animate to the edge and meet the bar that appears, so it doesn't show the bar appearing, it just shows the card
 * moving to the edge and staying there, with the no size change as mentioned before." The strip: "24 dp, as today (Recommended)"; its
 * words and buttons: "Fade (Recommended)".
 *
 * Read from drawn frames on the real Maps tab ([RouteCardBarRig]), the clock stepped by hand: tucked by a real sideways swipe on the
 * card, brought back by a real tap on the strip. What counts as drawn by the card is every pixel that differs from the same screen with
 * no card open, so a separate tab, the card leaving, or a card of another height shows in the reading whatever drew it. At every frame,
 * both ways:
 * - **One continuous movement, no other bar**: along a row a quarter of the way down the card, exactly one run of drawn pixels, from
 *   the card's edge, and nothing else drawn across the card's resting width; the run never grows while tucking or shrinks coming back.
 * - **No size change**: down a column 12 dp in from the edge (the strip's middle once tucked), the card is drawn from its resting top
 *   to its resting bottom, and nothing above or below.
 * - **The card itself does not fade**: its fill 12 dp in from the edge and 6 dp down (clear of the handle, the ✕ and the words, which
 *   pass under it) reads as at rest.
 * - **Its words and buttons fade**: the strongest ink on what shows of the card (its corners, edges and the grip's place left out) never
 *   rises while tucking or falls coming back, passes through a frame part way, and is gone once tucked.
 * Tucked: the run is [ROUTE_CARD_STRIP_DP] wide and the column the card's full height. Back: the run is the card's whole width.
 */
abstract class RouteCardStripSlideTests {

    protected val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    internal val rig = RouteCardBarRig(composeRule)

    private val density get() = composeRule.density.density
    private fun px(dp: Dp): Int = with(composeRule.density) { dp.roundToPx() }
    private fun capture(): PixelMap = composeRule.onRoot().captureToImage().toPixelMap()

    /** One frame's reading. */
    private data class Frame(val runPx: Int, val stray: String?, val topPx: Int?, val bottomPx: Int?, val fill: Color, val ink: Float)

    /**
     * Reads [now] against [alone] (the same screen with no card) for a card resting at [card] against its [onRight] edge.
     */
    private fun read(now: PixelMap, alone: PixelMap, card: DpRect, onRight: Boolean): Frame {
        val left = ceil(card.left.value * density).toInt()
        val right = floor(card.right.value * density).toInt() - 1
        val top = ceil(card.top.value * density).toInt()
        val bottom = floor(card.bottom.value * density).toInt() - 1
        fun drawn(x: Int, y: Int) = !now[x, y].near(alone[x, y])
        // The row a quarter of the way down: one run from the edge.
        val row = top + (bottom - top) / 4
        val xs = if (onRight) (right downTo left).toList() else (left..right).toList()
        val run = xs.takeWhile { drawn(it, row) }.size
        val stray = xs.drop(run).firstOrNull { drawn(it, row) }?.let { "drawn at ${"%.1f".format(abs(it - xs.first()) / density)} dp from the edge, past the card's run of ${"%.1f".format(run / density)} dp" }
        // The column 12 dp in from the edge: drawn from the resting top to the resting bottom.
        val column = xs[px(12.dp)]
        val reach = max(0, top - px(4.dp))..minOf(now.height - 1, bottom + px(4.dp))
        val drawnRows = reach.filter { drawn(column, it) }
        val fill = now[column, top + px(6.dp)]
        // Ink: the strongest difference from the plain fill on what shows of the card, past its round corners (16 dp) top and bottom,
        // 3 dp in from both ends of the run, and not where the grip sits (the card's inner 24 dp, 16 dp either side of its middle).
        var ink = 0f
        if (run > px(6.dp)) {
            val inner = run - px(ROUTE_CARD_STRIP_DP.dp)
            val middle = (top + bottom) / 2
            for (y in (top + px(16.dp))..(bottom - px(16.dp))) {
                for (i in px(3.dp) until run - px(3.dp)) {
                    if (i >= inner && abs(y - middle) < px(16.dp)) continue
                    val p = now[xs[i], y]
                    ink = max(ink, max(abs(p.red - fill.red), max(abs(p.green - fill.green), abs(p.blue - fill.blue))))
                }
            }
        }
        return Frame(run, stray, drawnRows.firstOrNull(), drawnRows.lastOrNull(), fill, ink)
    }

    private fun onRight(card: DpRect): Boolean = rig.screen().right - card.right < card.left - rig.screen().left

    /**
     * From the card open and settled: captures the screen with no card ([alone], taken by the caller before opening it), then tucks by a
     * real swipe and reads frames through the slide, then brings it back by a real tap on the strip and reads frames through that.
     */
    protected fun assertSlides(name: String, alone: PixelMap) {
        val card = rig.bounds(ROUTE_CARD_TAG)
        val onRight = onRight(card)
        val resting = read(capture(), alone, card, onRight)
        val cardPx = floor(card.right.value * density).toInt() - ceil(card.left.value * density).toInt()
        println("MEASURED $name: card ${card.left.value}..${card.right.value} x ${card.top.value}..${card.bottom.value}, on the ${if (onRight) "right" else "left"}; at rest run ${resting.runPx} px, rows ${resting.topPx}..${resting.bottomPx}, fill ${resting.fill.hex()}, ink ${"%.3f".format(resting.ink)}")
        val off = mutableListOf<String>()
        assertTrue("$name, precondition: at rest the card is drawn across its width (${resting.runPx} of $cardPx px) and its words show (ink ${resting.ink})", resting.runPx >= cardPx - 2 && resting.ink > 0.3f)

        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag(ROUTE_CARD_TAG).performTouchInput {
            val y = height * 0.5f
            val from = if (onRight) width * 0.2f else width * 0.8f
            val to = if (onRight) width * 0.8f else width * 0.2f
            swipe(Offset(from, y), Offset(to, y), durationMillis = 300)
        }
        val tucking = frames(card, onRight)
        assertTrue("$name, precondition: the swipe tucked the card", rig.plotting.uiState.value.tucked)
        off += check("tucking", tucking, resting, shrinking = true)
        val tucked = tucking.last()
        if (abs(tucked.runPx - px(ROUTE_CARD_STRIP_DP.dp)) > 2) off += "tucked: the strip is ${"%.1f".format(tucked.runPx / density)} dp wide, not $ROUTE_CARD_STRIP_DP"
        if (tucked.ink > INK_GONE) off += "tucked: words or buttons still show on the strip (ink ${"%.3f".format(tucked.ink)})"

        composeRule.onNodeWithTag(ROUTE_CARD_TAB_TAG).performTouchInput { click(Offset(width * 0.25f, height * 0.5f)) }
        val back = frames(card, onRight)
        assertTrue("$name, precondition: the tap brought the card back", !rig.plotting.uiState.value.tucked)
        off += check("coming back", back, resting, shrinking = false)
        val out = back.last()
        if (out.runPx < cardPx - 2) off += "back: the card is drawn ${"%.1f".format(out.runPx / density)} dp wide, not its ${"%.1f".format(cardPx / density)} dp"
        if (out.ink < resting.ink - 0.05f) off += "back: its words show with ink ${"%.3f".format(out.ink)}, not ${"%.3f".format(resting.ink)} as before"
        composeRule.mainClock.autoAdvance = true
        assertTrue("$name: the card slides to its edge and back as one, the same height, its words fading, no other bar; off: $off", off.isEmpty())
    }

    /** Frames every [FRAME_MS] for [FRAMES_MS] after the touch, past the slide's 750 ms (NAVIGATION_VIEW_TRANSITION_MILLIS). */
    /** Read against the card's resting bounds, [card], taken before the touch: a card that leaves the screen is read as gone, not skipped. */
    private fun frames(card: DpRect, onRight: Boolean): List<Frame> {
        return (0 until FRAMES_MS / FRAME_MS).map {
            composeRule.mainClock.advanceTimeBy(FRAME_MS.toLong())
            composeRule.waitForIdle()
            read(capture(), aloneShot!!, card, onRight)
        }
    }

    private fun check(way: String, frames: List<Frame>, resting: Frame, shrinking: Boolean): List<String> {
        val off = mutableListOf<String>()
        println("MEASURED $way: runs ${frames.map { it.runPx }}, ink ${frames.map { "%.2f".format(it.ink) }}")
        frames.forEachIndexed { i, f ->
            val at = "$way, frame $i (${(i + 1) * FRAME_MS} ms)"
            f.stray?.let { off += "$at: $it" }
            if (f.topPx == null || abs(f.topPx - resting.topPx!!) > 1 || abs(f.bottomPx!! - resting.bottomPx!!) > 1) off += "$at: drawn rows ${f.topPx}..${f.bottomPx} 12 dp in from the edge, not the card's ${resting.topPx}..${resting.bottomPx}"
            if (!f.fill.near(resting.fill)) off += "$at: the card's fill reads ${f.fill.hex()}, not ${resting.fill.hex()} as at rest (the card itself fades)"
            if (i > 0) {
                val prev = frames[i - 1]
                if (shrinking && f.runPx > prev.runPx + 1 || !shrinking && f.runPx < prev.runPx - 1) off += "$at: the run went ${prev.runPx} to ${f.runPx} px"
                if (shrinking && f.ink > prev.ink + INK_STEP || !shrinking && f.ink < prev.ink - INK_STEP) off += "$at: the ink went ${"%.3f".format(prev.ink)} to ${"%.3f".format(f.ink)}"
            }
        }
        if (frames.none { it.ink > resting.ink * 0.1f && it.ink < resting.ink * 0.9f }) off += "$way: no frame part way through the fade (ink ${frames.map { "%.2f".format(it.ink) }})"
        return off
    }

    /** The screen with no card, read before the card opens; set by [setUp]. */
    protected var aloneShot: PixelMap? = null

    protected fun setUp() {
        aloneShot = capture()
    }

    protected companion object {
        const val ROUTE_CARD_STRIP_DP = 24
        const val FRAMES_MS = 960
        const val FRAME_MS = 32
        const val INK_GONE = 0.03f
        const val INK_STEP = 0.02f
    }
}

/** The S22's portrait window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardStripSlideTest : RouteCardStripSlideTests() {
    @Test
    fun `portrait, a saved route - the card slides to its edge and back, the same height, its words fading, no other bar`() {
        val trip = rig.savedTrip("Creek loop", 3)
        rig.setScreen(trips = listOf(trip))
        setUp()
        rig.open(trip)
        assertSlides("portrait, a saved route", aloneShot!!)
    }

    @Test
    fun `portrait, plotting - the card slides to its edge and back, the same height, its words fading, no other bar`() {
        rig.setScreen()
        setUp()
        rig.plotTo(2)
        assertSlides("portrait, plotting", aloneShot!!)
    }
}

/** The S22's landscape window. */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class RouteCardStripSlideLandscapeTest : RouteCardStripSlideTests() {
    @Test
    fun `landscape, plotting - the card slides to its edge and back, the same height, its words fading, no other bar`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen()
        setUp()
        rig.plotTo(2)
        assertSlides("landscape, plotting", aloneShot!!)
    }

    @Test
    fun `landscape navigating, on the navigation card - the card slides to its edge and back, the same height, its words fading, no other bar`() {
        Shadows.shadowOf(ShadowDisplay.getDefaultDisplay()).setRotation(Surface.ROTATION_90)
        rig.setScreen(navigating = true)
        setUp()
        rig.plotTo(2)
        assertSlides("landscape navigating", aloneShot!!)
    }
}
