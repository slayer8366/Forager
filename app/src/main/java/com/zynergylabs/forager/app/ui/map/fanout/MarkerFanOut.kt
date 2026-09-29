package com.zynergylabs.forager.app.ui.map.fanout

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * Stacked map markers fan out on tap (dispatch 2026-09-28-197; the owner's rules in
 * docs/plans/journal-redesign.md, "Fan-out: the behaviour"). This file is the arithmetic and nothing
 * else: no map SDK, no Compose, so it is unit-testable headless. Every distance below comes from
 * one number, the touch size, so nothing here is a taste constant.
 */

/**
 * A marker's touch area is a square this many dp across, centred on the marker's own coordinate: the
 * owner's floor ("at least 48 dp") and the same size as the tap box `resolveTap` already queries
 * (`TAP_BOX_DP`). It is a hit area, not the glyph's drawn extent, which differs by glyph.
 */
const val FAN_TOUCH_DP = 48f

/** Up to this many markers fan out in a ring; more go in a spiral (the owner's rule 3). */
const val FAN_RING_MAX = 8

/** The fan-out, and the fold-back, take this long (the owner's rule 6: "Give it a .4s animation speed"). */
const val FAN_DURATION_MS = 400

/** A marker by the layer that draws it and its own id, the pair a tap resolves to. */
data class FanKey(val layerId: String, val featureId: String)

/** A marker the map reports near a tap: its true position, and where that is on the screen, in px. */
data class ProbedMarker(val key: FanKey, val lat: Double, val lng: Double, val xPx: Float, val yPx: Float)

/** Where a fanned marker sits relative to its true spot, in dp, y down. */
data class FanOffset(val xDp: Float, val yDp: Float)

/**
 * One marker of an open fan: its true position (lat/lng, and on screen in dp when the fan opened)
 * and the [offset] from it that it fans out to.
 */
data class FanMember(
    val key: FanKey,
    val lat: Double,
    val lng: Double,
    val trueXDp: Float,
    val trueYDp: Float,
    val offset: FanOffset,
)

/**
 * [tapped] and every marker in [nearby] whose touch area overlaps [tapped]'s, [tapped] first, each once.
 * Two touch areas overlap when both the horizontal and the vertical distance between the markers are
 * under [FAN_TOUCH_DP], so squares that only touch do not. Not transitive: a marker that overlaps only
 * a neighbour of [tapped] is not in its stack, which keeps a stack to what is under the finger.
 * [density] converts the px positions to dp.
 */
fun stackOf(tapped: ProbedMarker, nearby: List<ProbedMarker>, density: Float): List<ProbedMarker> {
    val overlapping = nearby.filter {
        it.key != tapped.key &&
            abs(it.xPx - tapped.xPx) / density < FAN_TOUCH_DP &&
            abs(it.yPx - tapped.yPx) / density < FAN_TOUCH_DP
    }
    return (listOf(tapped) + overlapping).distinctBy { it.key }
}

/**
 * The [count] offsets a stack fans out to, first marker first: a ring up to [FAN_RING_MAX], a spiral
 * beyond. Either way no two touch squares overlap (their centres are [FAN_TOUCH_DP] apart on at least
 * one axis, which is a stronger condition than 48 dp apart as the crow flies: two centres 48 dp apart on
 * the diagonal have squares that overlap) and none sits on the stack's own spot.
 *
 * **Ring:** the smallest radius, from one touch size up, at which every pair of markers clears that
 * condition. The first marker is straight above, the rest clockwise.
 *
 * **Spiral:** an Archimedean spiral from one touch size out, growing one touch size per turn; each
 * marker is the first point along it that clears every marker already placed. That rule is the whole
 * spacing: there is no fudge factor to keep the turns apart.
 */
fun fanOffsets(count: Int): List<FanOffset> = if (count <= FAN_RING_MAX) ring(count) else spiral(count)

/** True when squares [FAN_TOUCH_DP] across, centred on [a] and [b], do not overlap (touching edges do not count). */
private fun clear(a: FanOffset, b: FanOffset): Boolean = maxOf(abs(a.xDp - b.xDp), abs(a.yDp - b.yDp)) >= FAN_TOUCH_DP

private fun ring(count: Int): List<FanOffset> {
    if (count <= 0) return emptyList()
    fun at(radius: Float) = List(count) { i ->
        val angle = -PI / 2 + 2 * PI * i / count
        FanOffset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
    }
    var radius = FAN_TOUCH_DP
    var placed = at(radius)
    while (placed.indices.any { i -> (i + 1 until placed.size).any { j -> !clear(placed[i], placed[j]) } }) {
        radius += RING_RADIUS_STEP_DP
        placed = at(radius)
    }
    return placed
}

/** How much the ring's radius grows while looking for room; fine enough that the gap it leaves is under 0.1 dp. */
private const val RING_RADIUS_STEP_DP = 0.05f

private fun spiral(count: Int): List<FanOffset> {
    val growthPerRadian = FAN_TOUCH_DP / (2 * PI)
    val placed = ArrayList<FanOffset>(count)
    var angle = -PI / 2
    repeat(count) {
        var candidate = pointAt(angle, growthPerRadian)
        while (placed.any { !clear(candidate, it) }) {
            angle += SPIRAL_STEP_RADIANS
            candidate = pointAt(angle, growthPerRadian)
        }
        placed += candidate
    }
    return placed
}

/** The spiral's point at [angle], whose radius is one touch size plus [growthPerRadian] for each radian since 12 o'clock. */
private fun pointAt(angle: Double, growthPerRadian: Double): FanOffset {
    val radius = FAN_TOUCH_DP + growthPerRadian * (angle + PI / 2)
    return FanOffset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
}

/** The angular step the spiral advances by while looking for room; fine enough that the gap it leaves is under 1 dp. */
private const val SPIRAL_STEP_RADIANS = 0.01

/** Where [member] is drawn at [progress] (0 on its true spot, 1 at its fanned place), in dp. */
fun memberPositionDp(member: FanMember, progress: Float): FanOffset = FanOffset(
    member.trueXDp + member.offset.xDp * progress,
    member.trueYDp + member.offset.yDp * progress,
)

/**
 * The member a touch at ([xDp], [yDp]) lands on at [progress]: the one whose own [FAN_TOUCH_DP]
 * square, centred where that member is drawn, contains the touch; the nearest, where two squares do.
 * `null` when the touch is on none.
 */
fun fanMemberAt(members: List<FanMember>, progress: Float, xDp: Float, yDp: Float): FanMember? {
    val half = FAN_TOUCH_DP / 2
    return members
        .map { it to memberPositionDp(it, progress) }
        .filter { (_, at) -> abs(at.xDp - xDp) < half && abs(at.yDp - yDp) < half }
        .minByOrNull { (_, at) -> hypot(at.xDp - xDp, at.yDp - yDp) }
        ?.first
}
