package com.zynergylabs.forager.app.ui.availability

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Dispatch 2026-11-05-10 (RECORD -2181): the owner's first step where "by trail" doesn't fit right of the meeting point, "stack by
 * on top of trail first", and how it lines up, "On the distance's line (Recommended)": both words start at the meeting point,
 * "trail" on the distance's baseline and "by" just above it. Reached at normal text size on a 320 dp phone with an evening time a
 * digit wider than the owner's screenshot's, "Dark since 10:23 PM"; straight, which cannot stack, takes its own line there.
 *
 * Then the two moments in a walk when the evening time changes its words, sunset and dusk (the planner: report what moves, don't
 * chase it): each phase's line is read in turn on one card, and every reading is held to the rulings. What moves between them is
 * in the readings printed (RULES lines), for the completion report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationCardKindLineStackTest : NavigationCardTopLineRules() {

    private fun assertClean(readings: List<Reading>) {
        val problems = readings.flatMap { it.problems }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, Dark since 10 23 PM - by stacked over trail on the distance's line, straight on its own line`() = assertClean(
        listOf(
            topLine("320/1.0", State.TRAIL, Evening.DARK_SINCE_LONG, step = Step.STACKED, shifted = true),
            topLine("320/1.0", State.STRAIGHT, Evening.DARK_SINCE_LONG, step = Step.OWN_LINE, shifted = true),
        ),
    )

    @Test @Config(qualifiers = "w780dp-h360dp-land-xxhdpi")
    fun `780 x 360, font 1,0, Dark since 10 23 PM - both beside, moved right`() = assertClean(
        listOf(
            topLine("780x360/1.0", State.TRAIL, Evening.DARK_SINCE_LONG, step = Step.BESIDE, shifted = true),
            topLine("780x360/1.0", State.STRAIGHT, Evening.DARK_SINCE_LONG, step = Step.BESIDE, shifted = true),
        ),
    )

    private val phases = listOf(Evening.NONE, Evening.BEFORE_SUNSET, Evening.AFTER_SUNSET, Evening.DARK_SINCE)

    @Test @Config(qualifiers = "w320dp-h780dp-xxhdpi")
    fun `320 dp, font 1,0, through sunset and dusk - every phase follows the rulings`() = assertClean(
        listOf(State.TRAIL, State.STRAIGHT).flatMap { s -> phases.map { e -> topLine("320/1.0 phases", s, e) } },
    )

    @Test @Config(qualifiers = "w823dp-h384dp-land-xxhdpi")
    fun `823 x 384, font 1,0, through sunset and dusk - every phase follows the rulings`() = assertClean(
        listOf(State.TRAIL, State.STRAIGHT).flatMap { s -> phases.map { e -> topLine("823x384/1.0 phases", s, e) } },
    )
}
