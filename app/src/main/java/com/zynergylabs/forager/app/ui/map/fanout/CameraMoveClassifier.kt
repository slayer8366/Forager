package com.zynergylabs.forager.app.ui.map.fanout

/** What started a camera move, as far as an open fan is concerned (dispatch 2026-09-28-380). */
enum class CameraMoveCause {
    /** The user's touch: a pan, a zoom, a rotation. The fan closes. */
    USER_GESTURE,

    /** The app moved the camera because the user just asked for something (a control, a search result, a style change). The fan closes, as before. */
    APP_REQUESTED,

    /** The map re-centring itself while it follows the location. The user did not mean it. The fan stays open. */
    LOCATION_FOLLOW,

    /**
     * The app zoomed in on a crowded stack to open its fan (dispatch 2026-10-27-07, RECORD -1736): the fan this move is for
     * opens as the camera arrives, and the move must not fold it (the dispatch's step 5: "The app's own zoom must not fold the
     * fan it is about to open"). Marked by [CameraMoveClassifier.markStackZoomMove].
     */
    APP_STACK_ZOOM,

    /**
     * The app moved the camera because the user pressed a control that moves it: Locate, a search result, Return to Route,
     * north-up (dispatch 2026-10-27-07 Amendment 1, S1; RECORD -1753, the owner: "Treat it like your own pan (Recommended)").
     * It folds the fan, as [APP_REQUESTED] does, and it also counts as the user's own pan for a crowded stack: the list closes
     * and Back's camera return is dropped. Marked by [CameraMoveClassifier.markUserAskedMove]. The app's other moves (a
     * style load, the return to a saved camera, the crowded stack's own camera return) stay [APP_REQUESTED].
     */
    APP_USER_ASKED,

    /** Not a touch, not marked as the app's, and not following the location. Nothing in the app is known to make one; it closes the fan, as before, and is logged. */
    UNKNOWN,
}

/**
 * Tells a location-following move from the others. MapLibre reports every programmatic move, the location follower's included, with the
 * same reason (API_ANIMATION, `javap` of `Transform`), so the reason cannot tell them apart. What can: the app marks each camera move it
 * makes itself ([markAppMove], before making it). A move that is not a touch, not marked, while the map follows the location is the follower's.
 *
 * A mark covers only the move it was set for. It is ARMED from the mark until the SDK reports a move start, then IN FLIGHT until the camera is idle
 * ([onCameraIdle]); the follower's moves during an in-flight move are classed with it. A mark whose move never starts (a frame the app could not
 * apply, a camera already where it was asked to go, a locate tap with no fix yet) is dropped, so it cannot catch a later move of the follower's and
 * close a fan the user opened in between (planner review of dispatch 2026-09-28-380; the owner: "If a small jump will close the fan then we
 * shouldn't let that pass").
 *
 * How "never starts" is known without a time limit: the SDK does not call the app's listener inside the camera call. `Transform.moveCamera`,
 * `easeCamera` and `animateCamera` store the reason and `CameraChangeDispatcher.onCameraMoveStarted` queues a message on the main looper
 * (`javap -c` of both, SDK 13.5.0), so the listener runs on a later turn. The dispatcher also removes a pending message and queues a new one at the
 * back, so a single hop after the mark could run before the message that belongs to it. The settle step therefore hops twice through
 * [afterQueuedMessages]: by the second hop every message the mark's own camera call queued has been delivered. An ARMED mark still there then
 * had no move. The call that makes the move must be made synchronously after the mark; a site that moves the camera later than two looper turns
 * after marking would be classed as the follower's.
 *
 * Held where the map is built and used on the main thread only.
 */
class CameraMoveClassifier(
    /** Runs a task on the main looper behind the messages already queued (a `Handler.post`). */
    private val afterQueuedMessages: (() -> Unit) -> Unit,
    /** Where a mark that loses its kind by joining another is reported (Sorrel's #430 review, N1); the map passes `Log.w`. */
    private val warn: (String) -> Unit = {},
) {
    private enum class Mark { NONE, ARMED, IN_FLIGHT }

    /** Which of the app's moves a mark is for: what [classify] reports for it. */
    private enum class Kind(val cause: CameraMoveCause) {
        PLAIN(CameraMoveCause.APP_REQUESTED),
        USER_ASKED(CameraMoveCause.APP_USER_ASKED),
        STACK_ZOOM(CameraMoveCause.APP_STACK_ZOOM),
    }

    private var mark = Mark.NONE

    /** The current mark's kind. */
    private var kind = Kind.PLAIN

    /** Which mark is current, so a settle step queued by an earlier mark, running late, cannot drop a newer one (seen on the S22 at launch, main thread busy). */
    private var generation = 0

    /** The app is about to move the camera itself (a style change, a return to a saved camera). Call just before the move: [CameraMoveCause.APP_REQUESTED]. */
    fun markAppMove() = mark(Kind.PLAIN)

    /**
     * The app is about to move the camera because the user pressed a control that moves it (Locate, a search result, Return to
     * Route, north-up): [CameraMoveCause.APP_USER_ASKED], which also counts as the user's own pan for a crowded stack.
     */
    fun markUserAskedMove() = mark(Kind.USER_ASKED)

    /** The crowded-stack zoom is about to move the camera to open a fan as it lands: [CameraMoveCause.APP_STACK_ZOOM], which keeps it. */
    fun markStackZoomMove() = mark(Kind.STACK_ZOOM)

    /**
     * A mark that joins another (a move already armed or in flight) takes one kind for the flight: the user's ask if either was
     * one, so the user's control still ends a crowded stack's way back; the zoom's only if both were, so another move of the
     * app's in the same flight still folds the fan, as before. The zoom's kind lost that way is logged.
     */
    private fun mark(new: Kind) {
        if (mark == Mark.NONE) {
            mark = Mark.ARMED
            kind = new
        } else {
            val joined = when {
                kind == Kind.USER_ASKED || new == Kind.USER_ASKED -> Kind.USER_ASKED
                kind == Kind.STACK_ZOOM && new == Kind.STACK_ZOOM -> Kind.STACK_ZOOM
                else -> Kind.PLAIN
            }
            if ((kind == Kind.STACK_ZOOM || new == Kind.STACK_ZOOM) && joined != Kind.STACK_ZOOM) {
                // revert RN1: no log
            }
            kind = joined
        }
        val thisMark = ++generation
        afterQueuedMessages { afterQueuedMessages { settle(thisMark) } }
    }

    /** Two looper turns after a mark: a mark no move has started under was for a move that did not happen. Only the latest mark's own step may drop it. */
    private fun settle(forMark: Int) {
        if (forMark == generation && mark == Mark.ARMED) {
            mark = Mark.NONE
            kind = Kind.PLAIN
        }
    }

    /** The camera is idle: the marked move, if any, is over. */
    fun onCameraIdle() {
        mark = Mark.NONE
        kind = Kind.PLAIN
    }

    /** What started a camera move that has just begun. [isGesture] is the SDK's gesture reason; [followingLocation] is whether the location component is tracking. */
    fun classify(isGesture: Boolean, followingLocation: Boolean): CameraMoveCause {
        if (isGesture) return CameraMoveCause.USER_GESTURE
        if (mark != Mark.NONE) {
            mark = Mark.IN_FLIGHT
            return kind.cause
        }
        return if (followingLocation) CameraMoveCause.LOCATION_FOLLOW else CameraMoveCause.UNKNOWN
    }
}
