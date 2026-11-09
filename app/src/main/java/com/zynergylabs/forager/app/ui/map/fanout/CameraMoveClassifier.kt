package com.zynergylabs.forager.app.ui.map.fanout

/** What started a camera move, as far as an open fan is concerned (dispatch 2026-09-28-380). */
enum class CameraMoveCause {
    /** The user's touch: a pan, a zoom, a rotation. The fan closes. */
    USER_GESTURE,

    /** The app moved the camera because the user just asked for something (a control, a search result, a style change). The fan closes, as before. */
    APP_REQUESTED,

    /** The map re-centring itself while it follows the location. The user did not mean it. The fan stays open. */
    LOCATION_FOLLOW,

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
) {
    private enum class Mark { NONE, ARMED, IN_FLIGHT }

    private var mark = Mark.NONE

    /** Which mark is current, so a settle step queued by an earlier mark, running late, cannot drop a newer one (seen on the S22 at launch, main thread busy). */
    private var generation = 0

    /** The app is about to move the camera itself (a control the user pressed, a search result, a style change). Call just before the move. */
    fun markAppMove() {
        if (mark == Mark.NONE) mark = Mark.ARMED
        val thisMark = ++generation
        afterQueuedMessages { afterQueuedMessages { settle(thisMark) } }
    }

    /** Two looper turns after a mark: a mark no move has started under was for a move that did not happen. Only the latest mark's own step may drop it. */
    private fun settle(forMark: Int) {
        if (forMark == generation && mark == Mark.ARMED) mark = Mark.NONE
    }

    /** The camera is idle: the marked move, if any, is over. */
    fun onCameraIdle() {
        mark = Mark.NONE
    }

    /** What started a camera move that has just begun. [isGesture] is the SDK's gesture reason; [followingLocation] is whether the location component is tracking. */
    fun classify(isGesture: Boolean, followingLocation: Boolean): CameraMoveCause {
        if (isGesture) return CameraMoveCause.USER_GESTURE
        if (mark != Mark.NONE) {
            mark = Mark.IN_FLIGHT
            return CameraMoveCause.APP_REQUESTED
        }
        return if (followingLocation) CameraMoveCause.LOCATION_FOLLOW else CameraMoveCause.UNKNOWN
    }
}
