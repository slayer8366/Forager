package com.zynergylabs.forager.app.ui.map.fanout

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.ENTRY_MAP_FRAME_MAX_ZOOM
import com.zynergylabs.forager.app.domain.ENTRY_MAP_FRAME_PADDING_DP
import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.GeoBoundingBox
import com.zynergylabs.forager.app.domain.model.LatLng

/*
 * A crowded group zooms in, then fans (dispatch 2026-10-27-07, RECORD -1736; the plan is option A of
 * docs/audits/2026-10-27-spiral-overflow-report.md). The owner: "Zoom in, then fan (Recommended)"; "Open by itself
 * (Recommended)"; "Back folds, then zooms out (Recommended)"; "Icon, name, date (Recommended)"; "Zoom 17, like the entry
 * map (Recommended)".
 *
 * A tapped stack whose fan does not fit ([FanPlan.fits]) is not fanned past the edge any more: the map zooms to the
 * stack's own area until its icons split or its fan fits, and a fan that fits then opens by itself. Still on one spot at
 * the closest zoom, the stack is offered as a list instead. Back folds the fan, then puts the camera back where it was
 * before the tap. Since dispatch 2026-11-04-02 (A6) both moves are shown, eased over 750 ms, not jumped, except under
 * Reduce Motion. No map SDK here: the camera is [StackCamera], which this project owns.
 */

/**
 * The closest the crowded-stack zoom goes before the list takes over: the entry map's cap, 17 (the owner: "Zoom 17, like the
 * entry map (Recommended)"). Every basemap reaches it: their operating limits are 17 to 19 (`operatingMaxZoom`).
 */
const val CROWDED_STACK_CLOSEST_ZOOM: Double = ENTRY_MAP_FRAME_MAX_ZOOM

/** How many zoom steps one tap may take before the list takes over; each step either splits the stack or reaches the closest zoom, so this is a guard. */
private const val MAX_ZOOM_STEPS = 8

/** A zoom within this of another is the same zoom. */
private const val ZOOM_EPS = 1e-3

/** Where the camera was, to go back to: its centre, zoom, bearing and tilt, and the location follower's mode then ([followMode], `null` when not following). */
data class CameraSpot(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val followMode: Int? = null,
)

/** The map's camera as the crowded-stack zoom needs it, behind an interface this project owns (`MapStackCamera` over MapLibre; a test's own). */
interface StackCamera {
    /** The camera now; `null` when the app is not to move it (navigating, or no camera yet). */
    fun spot(): CameraSpot?

    /** Moves the camera to [frame] at once, marked as the zoom that keeps the fan. `false`, logged by the camera, when it could not. */
    fun frame(frame: EntryMapFrame): Boolean

    /**
     * Puts the camera back at [spot], following again if it was: eased there when [animate] (dispatch 2026-11-04-02, A6),
     * at once when not (Reduce Motion).
     */
    fun restore(spot: CameraSpot, animate: Boolean)

    /**
     * Dispatch 2026-11-04-02, A6: the zoom's own steps have already put the camera where it ends, at once and in one turn of
     * the main thread, so nothing between was drawn. This shows the move to it instead of the jump: the camera goes back to
     * [from] at once and eases from there to where the steps put it, marked as the zoom's, so it ends exactly where the jump
     * did. [onArrived] once it is there; [onTakenOver] when the user's own touch stops the ease, and nothing is to open.
     */
    fun showMoveFrom(from: CameraSpot, onArrived: () -> Unit, onTakenOver: () -> Unit)
}

/** The stack offered as a list: its members, and where the tapped one is (for a row's bubble to point at). */
data class CrowdedList(val keys: List<FanKey>, val anchorXPx: Float, val anchorYPx: Float, val at: LatLng, val bearingDeg: Float)

/**
 * What the Maps tab holds for crowded stacks, observable: the cameras Back returns to, latest last, and the list showing.
 * The tab composes the list and its Back steps ([CrowdedStackBackHandlers]); the map writes it through [CrowdedStackZoom] and
 * tells it whether a fan is open ([fan]). A map with no host keeps the overflowing fan, logged.
 *
 * **Held above the tab switch** (Amendment 1, S2; CLAUDE.md's UX defaults), in the Maps tab's camera memory
 * (`MapCameraMemory.crowdedStacks`), so the list and the camera returns survive a tab change as the fan does. The map that
 * comes back is a new one, so the camera Back restores through is **the live map's** ([attachCamera], by the map as it is
 * built; [detachCamera] as it goes), not the one a return was taken with. The other choice, dropping the returns when the map
 * is rebuilt, was not taken: "Open in Journal" and Back rebuild the map, and that is the path S2 is for.
 */
class CrowdedStackHost(
    /** Where a camera step that cannot move the camera is reported; the map's memory passes `Log.w`. */
    private val warn: (String) -> Unit = {},
) {
    private val returns = mutableStateListOf<CameraSpot>()

    /** Whether Back has a camera to return to. */
    val cameraReturnPending: Boolean get() = returns.isNotEmpty()

    /** How many camera returns are waiting (for tests and logs). */
    val cameraReturnCount: Int get() = returns.size

    var list by mutableStateOf<CrowdedList?>(null)
        private set

    private var modeShown by mutableStateOf(CrowdedIconsMode.DEFAULT)

    /**
     * Settings > Map > "Crowded icons" (dispatch 2026-10-27-14), written by the Maps tab from the stored setting. In
     * [CrowdedIconsMode.LIST] a tapped stack of two or more opens the list at once ([CrowdedStackZoom.listNow]), fits or not.
     *
     * **A change of mode drops the camera returns** (decided beyond the dispatch's scope, reported): returns kept by a zoom in
     * "Zoom in, then fan" would otherwise wait under a List-mode map, where nothing is zoomed, and a later Back would jump the
     * camera back to before a tap the user has since moved on from. The map stays where it is; an open list stays open.
     */
    var mode: CrowdedIconsMode
        get() = modeShown
        set(value) {
            if (value == modeShown) return
            returns.clear()
            modeShown = value
        }

    /** The map's fan, so the tab's Back steps wait while it is open (the fan's own Back folds it first). */
    var fan by mutableStateOf<MarkerFanOutState?>(null)

    /** The live map's camera, which Back's camera step restores through; `null` while no map is built. */
    private var camera: StackCamera? = null

    internal fun pushReturn(spot: CameraSpot, from: StackCamera) {
        returns += spot
        camera = from
    }

    /** A map was built over this host: Back's camera step moves its camera from now on. */
    fun attachCamera(live: StackCamera) { camera = live }

    /** [gone]'s map is going away; a newer map's camera, already attached, is kept. */
    fun detachCamera(gone: StackCamera) { if (camera === gone) camera = null }

    /** The user moved the map: what they see is theirs now, and Back no longer moves the camera (see [CrowdedStackZoom.onUserPan] and [CrowdedStackZoom.onUserAskedMove]). */
    fun clearReturns() = returns.clear()

    /**
     * Locate was pressed (dispatch 2026-10-27-14 Amendment 2, Q1; RECORD -1757): the press is the user's own pan, whether or
     * not the camera moves. With no fix yet nothing moves, and the follow that comes when the fix does is
     * [CameraMoveCause.LOCATION_FOLLOW], which ends nothing, so the returns and the list end here, at the press.
     */
    fun onLocatePressed() {
        clearReturns()
        closeList()
    }

    /**
     * A navigation view was asked for (dispatch 2026-10-27-14 Amendment 2, Q2; RECORD -1757, -1758): starting navigation is the
     * user's ask whether or not the camera moves, so Back never restores the pre-zoom camera over the navigation view. The
     * list stays: List mode lists while navigating, and the view's effect also re-runs on a style or layout change.
     */
    fun onNavigationViewRequested() = clearReturns()

    /**
     * Back's camera step: the latest return, restored, eased there when [animate] (dispatch 2026-11-04-02, A6; off under
     * Reduce Motion). `false` when there was none.
     */
    fun stepBack(animate: Boolean = true): Boolean {
        val spot = returns.removeLastOrNull() ?: return false
        val live = camera
        if (live == null) {
            warn("Crowded stack: Back's camera step found no map to move; the return to zoom ${spot.zoom} is dropped.")
            return true
        }
        live.restore(spot, animate)
        return true
    }

    internal fun showList(shown: CrowdedList) { list = shown }

    /**
     * Dispatch 2026-11-04-02, A1: the list's bubble follows its stack as the map moves, as a record's bubble follows its glyph
     * (`SightingsMapState.reanchorFocusedBubble`): [screenPointOf] is where a place is on the map now, in the map's pixels.
     */
    fun followList(screenPointOf: (LatLng) -> Pair<Float, Float>?, bearingDeg: Float) {
        val shown = list ?: return
        val at = screenPointOf(shown.at)
        if (at == null) {
            warn("Crowded stack: the list's spot ${shown.at} could not be placed on the map; its bubble stays where it was.")
            return
        }
        val moved = shown.copy(anchorXPx = at.first, anchorYPx = at.second, bearingDeg = bearingDeg)
        if (moved != shown) list = moved
    }

    fun closeList() { list = null }
}

/** A fan worked out but not opened: its members and where [fanShift] put it. */
data class FanPlan(val members: List<FanMember>, val placement: FanShift) {
    /** The report's "fits": every touch square on screen and every control cleared, read from the live bounds and the measured controls. */
    val fits: Boolean get() = placement.allOnScreen && placement.controlsCleared == placement.controlsTotal
}

/** What a crowded tap came to. */
enum class CrowdedOutcome {
    /** The zoom made the stack's fan fit, and it opened. */
    FANNED,

    /** The zoom split the stack: the tapped icon no longer shares its spot, and nothing opens. */
    SPLIT,

    /** Still one spot at the closest zoom: the list opened. */
    LISTED,

    /** Nothing this can do (no host, no camera, the camera would not move): the caller fans the stack it is given past the edge, logged. */
    NOT_HANDLED,
}

/**
 * The zoom for a stack whose fan does not fit. [host] is read at each tap (the tab hands it to the map; `null` on a map with
 * none). Reports every fallback through [warn].
 */
class CrowdedStackZoom(
    private val camera: StackCamera,
    private val host: () -> CrowdedStackHost?,
    private val warn: (String) -> Unit,
    /** Dispatch 2026-11-04-02, A6: the phone's Reduce Motion, read at each tap; under it the zoom jumps, as before. */
    private val reduceMotion: () -> Boolean = { false },
) {
    /**
     * [stack] (the tapped marker [tapped] first) does not fit. Zooms to it, step by step, until its icons split or its fan fits
     * ([open], once the camera has landed: the move is marked so it does not fold it, [CameraMoveCause.APP_STACK_ZOOM]), or
     * offers it as a list at the closest zoom. The outcome is returned at the tap; what it opens waits for the shown move
     * ([StackCamera.showMoveFrom]) and is not opened if a touch takes the move over. [locate] is where the named markers are now; [plan] works out a fan without opening it. When the
     * outcome is [CrowdedOutcome.NOT_HANDLED], [overflow] is the stack the caller fans past the edge.
     */
    fun onStackDoesNotFit(
        stack: List<ProbedMarker>,
        tapped: FanKey,
        density: Float,
        locate: (List<FanKey>) -> List<ProbedMarker>,
        plan: (List<ProbedMarker>) -> FanPlan,
        open: (FanPlan) -> Unit,
        overflow: (List<ProbedMarker>) -> Unit,
    ): CrowdedOutcome {
        val host = host() ?: return notHandled(stack, overflow, "this map has no crowded-stack host")
        val start = camera.spot() ?: return notHandled(stack, overflow, "the camera may not be moved now (navigating, or not ready)")
        val steps = zoomSteps(host, start, stack, tapped, density, locate, plan, open, overflow)
        // Dispatch 2026-11-04-02, A6 (S5, RECORD -2040: approach (i)): the steps above moved the camera at once, in this one turn
        // of the main thread, so none of them was drawn. The camera now goes back to where it was and eases to where they put it,
        // so it ends exactly where the jump did, and what the zoom opens waits for it to land. Under Reduce Motion it jumps, as
        // before. A touch during the move takes over: the user's pan ends the way back (onUserPan) and nothing opens.
        if (!steps.moved || reduceMotion()) {
            steps.onLanded()
        } else {
            camera.showMoveFrom(
                from = start,
                onArrived = steps.onLanded,
                onTakenOver = { warn("Crowded stack: the zoom's move was taken over by a touch; its ${steps.outcome} is not opened.") },
            )
        }
        return steps.outcome
    }

    /** What the zoom's steps came to: its [outcome], what opens once the camera is where they put it, and whether they moved it. */
    private class ZoomSteps(val outcome: CrowdedOutcome, val moved: Boolean, val onLanded: () -> Unit)

    /**
     * The zoom's steps, as before A6, each moving the camera at once and reading where the markers are then: until [stack]'s
     * icons split, its fan fits, or the closest zoom is reached and it is a list. Opens nothing itself: the fan or the list is
     * [ZoomSteps.onLanded]'s, worked out here from where the markers are at the steps' end, which is where the camera lands.
     */
    private fun zoomSteps(
        host: CrowdedStackHost,
        start: CameraSpot,
        stack: List<ProbedMarker>,
        tapped: FanKey,
        density: Float,
        locate: (List<FanKey>) -> List<ProbedMarker>,
        plan: (List<ProbedMarker>) -> FanPlan,
        open: (FanPlan) -> Unit,
        overflow: (List<ProbedMarker>) -> Unit,
    ): ZoomSteps {
        var current = stack
        var zoomBefore = start.zoom
        var pushed = false
        fun listed(at: List<ProbedMarker>) = ZoomSteps(CrowdedOutcome.LISTED, pushed) { list(host, at, tapped) }
        fun split() = ZoomSteps(CrowdedOutcome.SPLIT, pushed) {}
        repeat(MAX_ZOOM_STEPS) {
            if (zoomBefore >= CROWDED_STACK_CLOSEST_ZOOM - ZOOM_EPS) return listed(current)
            if (!camera.frame(frameOf(current))) {
                if (pushed) return listed(current)
                val unframed = current
                return ZoomSteps(CrowdedOutcome.NOT_HANDLED, moved = false) { notHandled(unframed, overflow, "the camera could not frame the stack") }
            }
            if (!pushed) {
                host.pushReturn(start, camera)
                pushed = true
            }
            val zoomAfter = camera.spot()?.zoom ?: zoomBefore
            val located = locate(current.map { it.key })
            val tappedNow = located.firstOrNull { it.key == tapped }
            if (tappedNow == null) {
                warn("Crowded stack: the tapped marker ${tapped.layerId}/${tapped.featureId} is not located after the zoom; nothing is opened.")
                return split()
            }
            val now = stackOf(tappedNow, located, density)
            if (now.size < 2) return split()
            val fanned = plan(now)
            if (fanned.fits) return ZoomSteps(CrowdedOutcome.FANNED, moved = true) { open(fanned) }
            current = now
            // A camera that did not get closer (the basemap's own limit) is as close as it goes.
            if (zoomAfter <= zoomBefore + ZOOM_EPS) {
                warn("Crowded stack: the camera did not get closer than zoom $zoomAfter; ${current.size} markers are offered as a list there.")
                return listed(current)
            }
            zoomBefore = zoomAfter
        }
        warn("Crowded stack: ${current.size} markers did not split or fit in $MAX_ZOOM_STEPS zoom steps; offered as a list.")
        return listed(current)
    }

    /**
     * List mode (dispatch 2026-10-27-14): [stack] (two or more, [tapped] among them) is offered as the list now, with no zoom
     * and no fan, so no camera return is kept. `false` when this map has no host or the host is not in list mode, and the
     * caller goes on as before. Needs no camera, so it lists while navigating too (RECORD -1758).
     */
    fun listNow(stack: List<ProbedMarker>, tapped: FanKey): Boolean {
        val host = host()?.takeIf { it.mode == CrowdedIconsMode.LIST } ?: return false
        list(host, stack, tapped)
        return true
    }

    /** A tap on the map with no bubble showing: the list closes, as an open fan folds at one. */
    fun onMapTap() {
        host()?.takeIf { it.list != null }?.closeList()
    }

    /**
     * Dispatch 2026-11-04-02, S2 (RECORD -2040): the user panned or zoomed the map with a touch ([CameraMoveCause.USER_GESTURE]).
     * No return is kept: what they see is theirs.
     */
    fun onUserPan() {
        host()?.clearReturns()
    }

    /**
     * The user moved the camera by a control that moves it ([CameraMoveCause.APP_USER_ASKED]: Locate, a search result, Return
     * to Route): no return is kept and the list closes, as the fan folds.
     */
    fun onUserAskedMove() {
        host()?.let { h ->
            h.clearReturns()
            h.closeList()
        }
    }

    private fun list(host: CrowdedStackHost, stack: List<ProbedMarker>, tapped: FanKey): CrowdedOutcome {
        val anchor = stack.firstOrNull { it.key == tapped } ?: stack.first()
        host.showList(
            CrowdedList(
                keys = stack.map { it.key },
                anchorXPx = anchor.xPx,
                anchorYPx = anchor.yPx,
                at = LatLng(anchor.lat, anchor.lng),
                bearingDeg = (camera.spot()?.bearing ?: 0.0).toFloat(),
            ),
        )
        return CrowdedOutcome.LISTED
    }

    private fun notHandled(stack: List<ProbedMarker>, overflow: (List<ProbedMarker>) -> Unit, why: String): CrowdedOutcome {
        warn("Crowded stack of ${stack.size} markers is fanned past the edge: $why.")
        overflow(stack)
        return CrowdedOutcome.NOT_HANDLED
    }
}

/**
 * The frame that shows [stack]'s own area: its records' bounds with the entry map's padding, no closer than
 * [CROWDED_STACK_CLOSEST_ZOOM]; a stack whose records are all at one coordinate is centred at that zoom instead, since
 * bounds with no area have no fit.
 */
fun frameOf(stack: List<ProbedMarker>): EntryMapFrame {
    val north = stack.maxOf { it.lat }
    val south = stack.minOf { it.lat }
    val east = stack.maxOf { it.lng }
    val west = stack.minOf { it.lng }
    if (north == south && east == west) return EntryMapFrame.SinglePoint(LatLng(north, east), CROWDED_STACK_CLOSEST_ZOOM)
    return EntryMapFrame.Fit(GeoBoundingBox(north = north, south = south, east = east, west = west), ENTRY_MAP_FRAME_PADDING_DP, CROWDED_STACK_CLOSEST_ZOOM)
}
