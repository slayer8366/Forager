package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dispatch 2026-11-05-10, Amendment 1, E18 (RECORD -2181). E17 moved the needle 6 to 32 dp right when "Target" or "No target" replaced
 * the degrees. The owner, to "Keep that, or size the space for "No target" all the time so the needle never moves?": "Never moves"
 * ("The degrees' space is always as wide as "No target", so the needle stays put; the card's left part is a little tighter in every
 * case").
 *
 * Through the real screen, in the screens and fonts [NavigationCardNeedleClearOfWordTest] covers, the needle's left edge in four
 * states: the degrees ("169°", the way home facing the turn), no compass (nothing in the degrees' space), "Target" ([State.LOST]) and
 * "No target" ([State.NO_TARGET]). The claims:
 * - the needle's left edge is the same in all four, within 0.05 dp;
 * - it is where a turn line centred in the left part puts it when the degrees' space is the widest of its contents ("0°" to "180°",
 *   "Target", "No target"), 4 dp and the 22 dp needle: half that space less 9 dp right of the left part's middle, within 1 dp (as
 *   [NavigationCardTopLineRules] reads the turn line); since dispatch 2026-11-06-01, J3 step 1 ("Centre the line"), where the next
 *   turn is beside the needle the centred line includes it, and the needle sits half of what it adds further left;
 * - and it stays inside the left part.
 * E17's claim (the word clear of the needle) is [NavigationCardNeedleClearOfWordTest]'s, unchanged.
 *
 * Each case prints a ROOM line: the left part's width, the turn line's width with the degrees-only space and with E18's, the needle's
 * spare room to the left part's right edge, and what the left part's widest need decides with each (the evening line broken at the ▪︎,
 * H6; the status words under the parts, H8), worked out with the card's own functions on the rig's clock, so a decision E18 would
 * change shows in the run before it is built.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardNeedleNeverMovesTest : NavigationCardKindLineScreen() {

    /** The rig's own clock (12-hour, "h:mm a", UTC), for the evening widths the card decides on. */
    private val clock = object : SundownClock {
        private val utc = TimeZone.getTimeZone("UTC")
        override fun full(epochMillis: Long): String = SimpleDateFormat("h:mm a", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
        override fun short(epochMillis: Long): String = SimpleDateFormat("h:mm", Locale.US).apply { timeZone = utc }.format(Date(epochMillis))
    }

    private fun stays(where: String) {
        val problems = mutableListOf<String>()
        show(State.TRAIL, Evening.NONE, Facing.TURN)
        val turning = card("$where TRAIL")
        val like = turning.turn?.takeIf { it.layout != null }
        if (like == null || like.text != "169°") {
            assertTrue("positive control: $where TRAIL: the turn line reads <${like?.text}>, not <169°>", false)
            return
        }
        val d = densityPx
        fun dpOf(p: Int) = (p / d).dp
        val degreesSpacePx = (0..180).maxOf { widthPx(like, "$it°") }
        val spacePx = maxOf(degreesSpacePx, widthPx(like, "Target"), widthPx(like, "No target"))
        // Dispatch 2026-11-06-01, J3 step 1 ("Centre the line"): where the next turn is beside the needle the centred turn line
        // includes it, so the needle sits half of what it adds further left, in every state alike ([besideNextTurnDp]). Was (E18)
        // the degrees' space and the needle alone, which still holds wherever the next turn is not beside.
        val expected = turning.middle + dpOf(spacePx) / 2 - 9.dp - besideNextTurnDp(turning, like) / 2
        val readings = mutableListOf("TRAIL <169°>" to turning)
        show(State.TRAIL, Evening.NONE, Facing.NO_COMPASS)
        readings += "TRAIL no compass" to card("$where TRAIL no compass")
        for ((s, word) in listOf(State.LOST to "Target", State.NO_TARGET to "No target")) {
            show(s, Evening.NONE, Facing.TURN)
            val c = card("$where $s")
            if (c.turn?.text != word) problems += "positive control: $where $s: the turn line reads <${c.turn?.text}>, not <$word>"
            readings += "$s <$word>" to c
        }
        val (_, first) = readings.first()
        for ((name, c) in readings) {
            if (abs((c.needle.left - first.needle.left).value) > 0.05f) {
                problems += "$where $name: the needle's left edge ${c.needle.left} is not where it is beside the degrees, ${first.needle.left} (moved ${c.needle.left - first.needle.left})"
            }
            if (abs((c.needle.left - expected).value) > 1f) {
                problems += "$where $name: the needle's left edge ${c.needle.left} is not where a degrees' space sized for \"No target\" puts it, $expected"
            }
            if (c.needle.right.value > c.leftPartRight.value + 0.05f) {
                problems += "$where $name: the needle ${c.needle.left}..${c.needle.right} leaves the left part (its right edge ${c.leftPartRight})"
            }
        }
        // The room, worked out as the card works it out (NavigationInfo.kt, rememberCardEvening and the fit), with each space.
        val gapPx = (4 * d).roundToInt()
        val arrowPx = (22 * d).roundToInt()
        val dividerPx = d.roundToInt() + 2 * (6 * d).roundToInt()
        val partsPx = ((((first.exit.left + first.exit.right) / 2 - 24.dp - first.topLine.left).value) * d).roundToInt()
        val longestPx = longestCardFigurePx(DistanceUnit.MILES) { widthPx(first.figure, it) }
        val kindsPx = listOf("by trail", "straight").maxOf { widthPx(like, it) }
        val reservePx = nextTurnReservePx(gapPx, arrowPx, widthPx(like, longestTurnDistance(DistanceUnit.MILES), tabular = true))
        val evening = cardEveningWidths(clock) { widthPx(like, it, tabular = true) }
        val widestWordPx = widestStatusWordPx { widthPx(like, it) }
        fun decided(space: Int): String {
            val linePx = space + gapPx + arrowPx
            val breaks = eveningBreaks(partsPx, dividerPx, maxOf(longestPx, linePx + reservePx), evening)
            val floorPx = if (breaks) evening.brokenPx else evening.oneLinePx
            val below = statusBelowTheParts(partsPx, dividerPx, maxOf(longestPx, linePx, kindsPx), floorPx, widestWordPx)
            return "turn line ${dpOf(linePx)}, left need ${dpOf(maxOf(longestPx, linePx, kindsPx))}, with next-turn reserve ${dpOf(maxOf(longestPx, linePx + reservePx))}, evening breaks $breaks, status below $below"
        }
        println(
            "ROOM|$where|left part ${first.topLine.left}..${first.leftPartRight} (${first.leftPartRight - first.topLine.left})|" +
                "longest figure ${dpOf(longestPx)}|kinds ${dpOf(kindsPx)}|parts ${dpOf(partsPx)}|evening one line ${dpOf(evening.oneLinePx)}, broken ${dpOf(evening.brokenPx)}|" +
                "degrees' space ${dpOf(degreesSpacePx)}: ${decided(degreesSpacePx)}|E18 space ${dpOf(spacePx)}: ${decided(spacePx)}|" +
                "needle ${readings.joinToString { (n, c) -> "$n ${c.needle.left}..${c.needle.right}" }}|spare right of the needle ${readings.minOf { (_, c) -> (c.leftPartRight - c.needle.right).value }} dp",
        )
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0 - the needle never moves`() = stays("320/1.0")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun `320 dp, font 1,3 - the needle never moves`() = stays("320/1.3")

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `320 dp, font 2,0 - the needle never moves`() = stays("320/2.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `360 dp, font 1,0 - the needle never moves`() = stays("360/1.0")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 1.3f)
    fun `360 dp, font 1,3 - the needle never moves`() = stays("360/1.3")

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0 - the needle never moves`() = stays("360/2.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - the needle never moves`() = stays("384/1.0")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 1.3f)
    fun `384 dp, font 1,3 - the needle never moves`() = stays("384/1.3")

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi", fontScale = 2.0f)
    fun `384 dp, font 2,0 - the needle never moves`() = stays("384/2.0")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `412 dp, font 1,0 - the needle never moves`() = stays("412/1.0")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 1.3f)
    fun `412 dp, font 1,3 - the needle never moves`() = stays("412/1.3")

    @Test @Config(qualifiers = "w412dp-h915dp-xxhdpi", fontScale = 2.0f)
    fun `412 dp, font 2,0 - the needle never moves`() = stays("412/2.0")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi")
    fun `480 dp, font 1,0 - the needle never moves`() = stays("480/1.0")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 1.3f)
    fun `480 dp, font 1,3 - the needle never moves`() = stays("480/1.3")

    @Test @Config(qualifiers = "w480dp-h960dp-xxhdpi", fontScale = 2.0f)
    fun `480 dp, font 2,0 - the needle never moves`() = stays("480/2.0")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0 - the needle never moves`() = stays("780x360/1.0")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 1.3f)
    fun `780 x 360, font 1,3 - the needle never moves`() = stays("780x360/1.3")

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi", fontScale = 2.0f)
    fun `780 x 360, font 2,0 - the needle never moves`() = stays("780x360/2.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - the needle never moves`() = stays("823x384/1.0")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 1.3f)
    fun `823 x 384, font 1,3 - the needle never moves`() = stays("823x384/1.3")

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0 - the needle never moves`() = stays("823x384/2.0")
}
