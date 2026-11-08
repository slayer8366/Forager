package com.zynergylabs.forager.app.ui.map

import androidx.compose.animation.core.FastOutSlowInEasing
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.testing.MadeUpPlace
import com.zynergylabs.forager.app.ui.map.fanout.FAN_DURATION_MS
import com.zynergylabs.forager.app.ui.map.fanout.FanKey
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOffset
import com.zynergylabs.forager.app.ui.map.fanout.FanOutHideGate
import com.zynergylabs.forager.app.ui.map.fanout.FanOutHideStep
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.geojson.Feature
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-11-06-05, Amendment 6, "the fan's opening, after the S22 measurement" (RECORD -2187, -2190): with the selected stop in a
 * stack, the highlighted original at the stack's centre stayed 216 ms after the pins started leaving, then went in one frame. The
 * fix's claim: it is hidden by the frame the first copy is drawn away from its spot, and the plain originals with it.
 *
 * Frame by frame through an opening at 120 fps on the fan's own clock ([FAN_DURATION_MS], fast-out-slow-in): each frame the fan pushes
 * [fanFrameCollections] with the selected stop's [accent], as MapFan does; the renderer reports the frame pushed [RENDER_LAG_FRAMES]
 * earlier (the copies are re-tiled off the main thread, "about three frames from the open", FanOutRenderSignal.kt); each rendered frame
 * is checked by [copiesReported], the check `awaitCopiesRendered` makes, and when it passes the real [FanOutHideGate] hides. The
 * accent layer hides what [fanHiddenAccentKeys] names for the hidden members, the filter `applyFanOutHiding` sets. The `MapView` frame
 * listener itself is device-only (it cannot be built under Robolectric), and so is the hand-over of the accent in MapFan; the S22
 * steps are the evidence for those.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FanOpeningCentreAccentTest {

    private val creek = FanKey(MapLayerIds.WAYPOINTS, "wp-creek")
    private val ridge = FanKey(MapLayerIds.WAYPOINTS, "wp-ridge")
    private val saddle = FanKey(MapLayerIds.PLANNED_TRIPS, "trip-saddle")

    private fun member(key: FanKey, offset: FanOffset) = FanMember(key, MadeUpPlace.LAT, MadeUpPlace.LNG, 200f, 300f, offset)

    private val members = listOf(
        member(creek, FanOffset(0f, -48f)),
        member(ridge, FanOffset(41.6f, 24f)),
        member(saddle, FanOffset(-41.6f, 24f)),
    )

    /** Where a member's copy is drawn at [progress]: its spot plus the offset times the progress, as a tiny step in degrees per dp. */
    private fun at(member: FanMember, progress: Float) =
        LatLng(member.lat - member.offset.yDp * progress * DEGREES_PER_DP, member.lng + member.offset.xDp * progress * DEGREES_PER_DP)

    private class Opening(val pinsLeaveAtFrame: Int, val hiddenByThen: List<FanMember>, val hiddenAtFrame: Int?)

    /** Runs the opening with [accent] selected and says what was hidden by the first rendered frame whose copies are off their spots. */
    private fun open(accent: FanKey?): Opening {
        val gate = FanOutHideGate()
        val first: FanOutHideStep = gate.onMembers(members, spread = false)
        assertTrue("the fan opens folded, so its hide waits for the copies", first.awaiting)
        var hidden = first.hide
        var hiddenAtFrame: Int? = null
        val pushed = mutableListOf<Pair<Float, List<Feature>>>()
        val frames = FAN_DURATION_MS * FPS / 1000
        for (frame in 0..frames + RENDER_LAG_FRAMES) {
            val progress = FastOutSlowInEasing.transform((frame.toFloat() / frames).coerceAtMost(1f))
            val icons = fanFrameCollections(members, { at(it, progress) }, null, progress, accent = accent).icons.features().orEmpty()
            pushed += progress to icons
            val rendered = frame - RENDER_LAG_FRAMES
            if (rendered < 0) continue
            val (renderedProgress, renderedIcons) = pushed[rendered]
            if (hiddenAtFrame == null && copiesReported(members, accent, renderedIcons, emptyList())) {
                gate.onCopiesDrawn(first.generation)?.let { hidden = it.hide; hiddenAtFrame = frame }
            }
            if (renderedProgress > 0f) return Opening(frame, hidden, hiddenAtFrame)
        }
        error("no rendered frame had the copies off their spots")
    }

    @Test
    fun `the highlighted original at the centre is hidden by the frame the pins start leaving`() {
        val opening = open(accent = creek)

        assertNotNull(
            "the copies were reported drawn by frame ${opening.pinsLeaveAtFrame}, the first whose copies are off their spots",
            opening.hiddenAtFrame,
        )
        assertTrue(
            "no accent left at the stack's centre once the pins leave",
            fanAccentKeyValue(creek) in fanHiddenAccentKeys(opening.hiddenByThen),
        )
        assertTrue("every fanned original hidden once the pins leave", opening.hiddenByThen.map { it.key }.toSet() == members.map { it.key }.toSet())
    }

    @Test
    fun `a selected trip flag in the stack is hidden by the frame the pins start leaving`() {
        val opening = open(accent = saddle)

        assertNotNull("the copies were reported drawn before the flags leave", opening.hiddenAtFrame)
        assertTrue("no accent flag left at the stack's centre", fanAccentKeyValue(saddle) in fanHiddenAccentKeys(opening.hiddenByThen))
    }

    @Test
    fun `with no stop selected the originals are hidden by the frame the pins start leaving, as before`() {
        val opening = open(accent = null)

        assertNotNull("the copies were reported drawn before the pins leave", opening.hiddenAtFrame)
        assertTrue(opening.hiddenByThen.map { it.key }.toSet() == members.map { it.key }.toSet())
    }

    private companion object {
        const val FPS = 120
        const val RENDER_LAG_FRAMES = 3
        const val DEGREES_PER_DP = 0.00001
    }
}
