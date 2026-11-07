package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Dispatch 2026-11-05-10, Amendment 1, H14 (RECORD -2181), the owner, verbatim: "There's a space between the 1.5 mi and the by trail.
 * It used to not be there before 498 to 500. Please put it back to where they're next to each other"; to "How should "by trail" sit
 * next to the distance on the navigation card?", "(a) Fixed, distance pushed right" (the option read: "The distance hugs the right
 * edge of its own window and "by trail" sits right after it. They're always side by side, and "by trail" never moves; a short
 * distance leaves room on the left instead"); and for "straight", "Yes, same".
 *
 * Through the real screen, as the distance counts down ("1.5 mi", "1280 ft", "98 ft" by trail; "≈ 1250 ft", "≈ 100 ft" straight),
 * in portrait and landscape, at fonts 1.0 and 2.0, where the kind is beside the distance:
 * - side by side: the kind starts the word gap (Spacing.xs, 4 dp) after the distance ends, whatever the distance's width;
 * - the distance ends at one place and the kind starts at one place for every figure, so neither moves as the number changes;
 * - and each reading is checked by [NavigationCardTopLineRules] (the window's end, the kind's space's start, the baseline, the step).
 * The kind's start not moving is a control, not evidence of H14: under G3 the kind was centred in a space of fixed width, so it did
 * not move then either. What H14 changes is the gap, and the distance's right edge.
 *
 * Amendment 1, H15, the owner, to "With the correct figures, where should "98 ft by trail" sit?": "Centre them together" (the option
 * read: "The widest distance plus "by trail" centred as one block: "by trail" moves about 20 dp right (5 to 26 dp depending on screen
 * and font), the distance starts further in from the left"). So the block, from the widest distance's left edge (the distance's right
 * edge less the widest distance's width, worked out here from the measured type) to the kind's right edge, has its centre at the left
 * part's middle, within 0.5 dp, for "by trail" and "straight" each. Under H14 the block started at the left part's left edge, so this
 * is the claim H15 turns red.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardDistanceBesideKindTest : NavigationCardTopLineRules() {

    private fun Dp.px(): Int = (value * densityPx).roundToInt()

    private fun assertSideBySide(where: String, states: List<State>) {
        val readings = states.map { topLine(where, it, Evening.NONE, step = Step.BESIDE) }
        val problems = readings.flatMap { it.problems }.toMutableList()
        val gapDp = 4f
        readings.zip(states).forEach { (r, s) ->
            val figure = checkNotNull(r.figure)
            val kind = r.kind
            if (kind == null) {
                problems += "$where $s: no kind drawn"
                return@forEach
            }
            val gap = kind.left - figure.right
            if (abs(gap.value - gapDp) > 0.5f) problems += "$where $s: <${s.figure}> ${figure} and <${s.kind}> ${kind} are $gap apart, not the word gap ($gapDp dp)"
            val (left, right) = checkNotNull(r.leftPart)
            val windowEnd = checkNotNull(r.windowEnd)
            // H15: the block (the widest distance's window, the gap, the kind) centred in the left part.
            val longest = (checkNotNull(r.longestPx) / densityPx).dp
            val blockLeft = figure.right - longest
            val blockCentre = (blockLeft + kind.right) / 2
            val middle = (left + right) / 2
            if (abs((blockCentre - middle).value) > 0.5f) {
                problems += "$where $s: the block ${blockLeft}..${kind.right} (the widest distance ${longest}, the gap, <${s.kind}>) is not centred in the left part ${left}..${right}: its centre $blockCentre, the middle $middle"
            }
            println(
                "PLACEMENT|$where|$s|left part $left..$right (${right - left})|window $left..$windowEnd (${windowEnd - left})|" +
                    "distance ${figure.left}..${figure.right} (${figure.right - figure.left})|gap $gap|kind ${kind.left}..${kind.right}|" +
                    "pair ${figure.left - left}..${kind.right - left} in the left part|room left of it ${figure.left - left}, right of it ${right - kind.right}|" +
                    "block ${blockLeft - left}..${kind.right - left} (${kind.right - blockLeft}), widest $longest|room left of the block ${blockLeft - left}, right ${right - kind.right}",
            )
        }
        // Positive control: the countdown changes the distance's width, so a fixed place is a claim that could fail.
        val widths = readings.mapNotNull { it.figure }.map { (it.right - it.left).px() }.distinct()
        if (widths.size < 2) problems += "$where: positive control: the distances are all ${widths.single()} px wide"
        val kindLefts = readings.mapNotNull { it.kind?.left?.px() }.distinct()
        if (kindLefts.size != 1) problems += "$where: the kind's left edge moved as the distance counted down: $kindLefts px"
        val figureRights = readings.mapNotNull { it.figure?.right?.px() }.distinct()
        if (figureRights.size != 1) problems += "$where: the distance's right edge moved as it counted down: $figureRights px"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private val byTrail = listOf(State.TRAIL_MILES, State.TRAIL, State.TRAIL_SHORT)
    private val straight = listOf(State.STRAIGHT, State.STRAIGHT_SHORT)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - by trail sits right after the distance as it counts down`() = assertSideBySide("384/1.0", byTrail)

    @Test @Config(qualifiers = "w384dp-h823dp-xxhdpi")
    fun `384 dp, font 1,0 - straight sits right after the distance as it counts down`() = assertSideBySide("384/1.0", straight)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - by trail sits right after the distance as it counts down`() = assertSideBySide("823x384/1.0", byTrail)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0 - straight sits right after the distance as it counts down`() = assertSideBySide("823x384/1.0", straight)

    // At 384 dp and font 2.0 both kinds take their own line in daytime (H8 does not apply there), so 360 dp is the portrait case at 2.0.
    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0 - by trail sits right after the distance as it counts down`() = assertSideBySide("360/2.0", byTrail)

    @Test @Config(qualifiers = "w360dp-h780dp-xxhdpi", fontScale = 2.0f)
    fun `360 dp, font 2,0 - straight sits right after the distance as it counts down`() = assertSideBySide("360/2.0", straight)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0 - by trail sits right after the distance as it counts down`() = assertSideBySide("823x384/2.0", byTrail)

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi", fontScale = 2.0f)
    fun `823 x 384, font 2,0 - straight sits right after the distance as it counts down`() = assertSideBySide("823x384/2.0", straight)
}
