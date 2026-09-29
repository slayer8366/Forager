package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.ui.map.layers.MapLayerSpec
import com.zynergylabs.forager.app.ui.map.layers.PaletteRole
import com.zynergylabs.forager.app.ui.map.layers.TapGroup
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 * Where a fan goes (dispatch 2026-09-28-197, continuation -208; the owner's "1 A / 2 A"): which layers
 * fan at all, and where the fan's centre is moved so every marker's touch area stays on screen and off
 * the controls. Pure Kotlin, in the unit its caller says.
 */

/** A rectangle, in whatever unit its user says (dp in [fanShift], px in [FanSpace]). */
data class FanRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/**
 * The room a fan has, in px of the map view: the map's visible bounds and the measured bounds of the
 * controls over it. `null` bounds means no edge to stay inside.
 */
interface FanSpace {
    fun boundsPx(): FanRect?
    fun keepOutsPx(): List<FanRect>

    companion object {
        val Unbounded: FanSpace = object : FanSpace {
            override fun boundsPx(): FanRect? = null
            override fun keepOutsPx(): List<FanRect> = emptyList()
        }
    }
}

/**
 * Where [fanShift] put the fan, and how well it did: [allOnScreen] is false only when the fan is larger
 * than the window (it is then centred on it); [controlsCleared] of [controlsTotal] controls have no touch
 * square on them.
 */
data class FanShift(val shift: FanOffset, val allOnScreen: Boolean, val controlsCleared: Int, val controlsTotal: Int)

/** The controls' measured bounds, by name, in root px, written by `mapKeepOut` as each control is laid out. */
class MapKeepOuts {
    private val rects = LinkedHashMap<String, FanRect>()

    fun set(id: String, rect: FanRect) { rects[id] = rect }

    fun remove(id: String) { rects.remove(id) }

    fun snapshot(): Map<String, FanRect> = LinkedHashMap(rects)
}

/**
 * The layers that fan: the owner's own records, that is every tap-target marker layer but the sighting
 * dot (finds, photos, waypoints, planned trips). iNaturalist sightings never join a fan (the owner's
 * "1 A"). Derived from the registry rather than listed, and pinned by a test, so a marker layer added
 * later fails that test and is ruled on instead of fanning by default.
 */
fun fanOutLayerIds(drawOrder: List<MapLayerSpec>): List<String> =
    drawOrder.filter { it.tapGroup == TapGroup.MARKER && it.paletteRole != PaletteRole.SIGHTING_DOT }.map { it.id }

/** A fan larger than this many markers is placed by its bounding box rather than marker by marker, to bound the search. */
private const val PER_MARKER_LIMIT = 60

private const val EPS = 1e-3f

/**
 * The smallest move of a fan of [ring] about ([centreX], [centreY]) that puts every marker's
 * [FAN_TOUCH_DP] square inside [bounds] and off every one of [keepOuts] (all in dp). Only the centre
 * moves; the markers' true positions are the caller's and are not touched.
 *
 *  1. The moves that keep every square inside [bounds] are a box. If the fan is wider or taller than
 *     the bounds there is none on that axis, and the fan is centred on it instead ([FanShift.allOnScreen]
 *     false): staying on screen comes before clearing controls.
 *  2. Within that box, every marker/control pair forbids a rectangle of moves (the control grown by half
 *     a touch square, less the marker's place). The best move is the one nearest no move that is in no
 *     forbidden rectangle: the answer, if there is one, is the clamped origin or lies on the edges of the
 *     forbidden rectangles, so those coordinates are the candidates, tried nearest first.
 *  3. If no candidate clears every control, subsets of controls are tried, largest first, so as many
 *     are cleared as can be; the nearest move that clears the largest subset wins.
 */
fun fanShift(centreX: Float, centreY: Float, ring: List<FanOffset>, bounds: FanRect?, keepOuts: List<FanRect>): FanShift {
    if (ring.isEmpty()) return FanShift(FanOffset(0f, 0f), true, keepOuts.size, keepOuts.size)
    val half = FAN_TOUCH_DP / 2
    val xs = ring.map { centreX + it.xDp }
    val ys = ring.map { centreY + it.yDp }

    // 1. The box of moves that keep everything on screen, or its centre on an axis with no room.
    var allOnScreen = true
    var xLo = Float.NEGATIVE_INFINITY
    var xHi = Float.POSITIVE_INFINITY
    var yLo = Float.NEGATIVE_INFINITY
    var yHi = Float.POSITIVE_INFINITY
    if (bounds != null) {
        xLo = bounds.left + half - xs.min()
        xHi = bounds.right - half - xs.max()
        yLo = bounds.top + half - ys.min()
        yHi = bounds.bottom - half - ys.max()
        if (xLo > xHi) { val mid = (xLo + xHi) / 2; xLo = mid; xHi = mid; allOnScreen = false }
        if (yLo > yHi) { val mid = (yLo + yHi) / 2; yLo = mid; yHi = mid; allOnScreen = false }
    }
    fun clamp(v: Float, lo: Float, hi: Float) = min(max(v, lo), hi)

    // Each control's forbidden moves: one rectangle per marker, or one for the whole fan when it is large.
    fun forbidden(control: FanRect): List<FanRect> {
        val positions = if (ring.size > PER_MARKER_LIMIT) {
            listOf(FanRect(xs.min(), ys.min(), xs.max(), ys.max()))
        } else {
            xs.indices.map { FanRect(xs[it], ys[it], xs[it], ys[it]) }
        }
        return positions.map {
            FanRect(control.left - half - it.right, control.top - half - it.bottom, control.right + half - it.left, control.bottom + half - it.top)
        }
    }
    val forbiddenByControl = keepOuts.map(::forbidden)

    fun blockedBy(control: Int, sx: Float, sy: Float) =
        forbiddenByControl[control].any { sx > it.left + EPS && sx < it.right - EPS && sy > it.top + EPS && sy < it.bottom - EPS }

    fun cleared(sx: Float, sy: Float) = keepOuts.indices.count { !blockedBy(it, sx, sy) }

    // 2 and 3. Candidates nearest first; the first that clears the largest subset.
    val candidatesX = (listOf(clamp(0f, xLo, xHi), xLo, xHi) + forbiddenByControl.flatten().flatMap { listOf(it.left, it.right) }).map { clamp(it, xLo, xHi) }.distinct()
    val candidatesY = (listOf(clamp(0f, yLo, yHi), yLo, yHi) + forbiddenByControl.flatten().flatMap { listOf(it.top, it.bottom) }).map { clamp(it, yLo, yHi) }.distinct()
    val candidates = candidatesX.flatMap { x -> candidatesY.map { y -> x to y } }.sortedBy { hypot(it.first, it.second) }

    var best: Pair<Float, Float> = candidates.first()
    var bestCleared = -1
    for ((sx, sy) in candidates) {
        val c = cleared(sx, sy)
        if (c > bestCleared) { best = sx to sy; bestCleared = c }
        if (bestCleared == keepOuts.size) break
    }
    return FanShift(FanOffset(best.first, best.second), allOnScreen, max(bestCleared, 0), keepOuts.size)
}
