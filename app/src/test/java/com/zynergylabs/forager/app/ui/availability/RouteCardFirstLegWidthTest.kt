package com.zynergylabs.forager.app.ui.availability

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.zynergylabs.forager.app.ui.route.ROUTE_CARD_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A first row that is wider landed, with its distance, than in flight, with its mark, as on the S22; then one wider again. */
private val LEG_NAMES = listOf("Creek", "Upper Falls Trail", "Old Logging Road Junction Below", "Pond", "Bluff", "Spring")

/** More frames than the slide's 750 ms. */
private const val FRAMES = 60

/**
 * Dispatch 2026-10-30-08, Amendment 17 (RECORD -1937), the owner: "Change width once the leg lands (Recommended)". On the S22 the
 * first leg went 312 dp, about 212 dp, then 226.9 dp (docs/audits/2026-11-01-s22-card-beside-bar.md on tools/s22-combined). While a
 * leg is in flight the card keeps its width; once it lands it eases once to the final width, and never passes it. The real Maps tab
 * at the S22's size, under native graphics, with the leg held in flight by the router.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class RouteCardFirstLegWidthTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hostActivity()).around(composeRule)

    private fun RouteCardBarRig.cardWidth(): Float = bounds(ROUTE_CARD_TAG).let { it.right - it.left }.value

    private fun RouteCardBarRig.frames(): List<Float> = (1..FRAMES).map {
        composeRule.mainClock.advanceTimeByFrame()
        cardWidth()
    }

    /** The next leg, held in flight for [FRAMES] and then landed: the widths by frame, in flight and after, and where it settles. */
    private fun RouteCardBarRig.nextLeg(): Triple<List<Float>, List<Float>, Float> {
        composeRule.mainClock.autoAdvance = false
        holdLegs()
        tapNext()
        val inFlight = frames()
        landLeg()
        val landed = frames()
        composeRule.mainClock.autoAdvance = true
        settle()
        return Triple(inFlight, landed, cardWidth())
    }

    private fun assertEasesOnce(where: String, from: Float, landed: List<Float>, final: Float) {
        val all = listOf(from) + landed
        val direction = if (final < from) 1f else -1f
        all.forEach { assertTrue("$where: the width passed its final $final: $all", (it - final) * direction >= -0.5f) }
        all.zipWithNext().forEach { (a, b) -> assertTrue("$where: it moved back on the way, $a then $b: $all", (a - b) * direction >= -0.5f) }
        assertTrue("$where: it eases, the widths by frame: $all", all.distinct().size >= 5)
        assertEquals("$where: it arrives", final, landed.last(), 0.5f)
    }

    private fun firstLeg(barOnLeft: Boolean) {
        val rig = RouteCardBarRig(composeRule, LEG_NAMES)
        rig.setScreen()
        if (barOnLeft) rig.dragBarAcross(rightward = false)
        rig.plotTo(1)
        val start = rig.cardWidth()
        val (inFlight, landed, final) = rig.nextLeg()
        println("A17 first leg, bar on left $barOnLeft: from $start to $final, in flight $inFlight, landed $landed")
        assertTrue("precondition: the hint's width $start is above the final width $final", start > final + 16f)
        inFlight.forEach { assertEquals("in flight the width stays, by frame: $inFlight", start, it, 0.5f) }
        assertEasesOnce("the first leg", inFlight.last(), landed, final)
    }

    @Test
    fun `the bar on the right - the first leg keeps the width in flight, then eases once to its final width`() = firstLeg(barOnLeft = false)

    @Test
    fun `the bar on the left - the first leg keeps the width in flight, then eases once to its final width`() = firstLeg(barOnLeft = true)

    @Test
    fun `a wider leg keeps the width in flight, then eases once wider`() {
        val rig = RouteCardBarRig(composeRule, LEG_NAMES)
        rig.setScreen()
        rig.plotTo(2)
        val start = rig.cardWidth()
        val (inFlight, landed, final) = rig.nextLeg()
        println("A17 wider leg: from $start to $final, in flight $inFlight, landed $landed")
        assertTrue("precondition: this leg widens the card, $start to $final", final > start + 16f)
        inFlight.forEach { assertEquals("in flight the width stays, by frame: $inFlight", start, it, 0.5f) }
        assertEasesOnce("the wider leg", inFlight.last(), landed, final)
    }
}
