package com.zynergylabs.forager.app.ui.map.fanout

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Which camera moves are the location follower's (dispatch 2026-09-28-380). MapLibre gives every programmatic move the same reason, so the app marks
 * its own moves and the mark lasts until the camera is idle. Pure; that the app marks every one of its moves is read from the code and recorded in
 * the report, and what a real follower does is the recordings'.
 */
class CameraMoveClassifierTest {

    private val looper = FakeLooper()
    private val classifier = CameraMoveClassifier(looper::post)

    @Test
    fun `a touch is the user's, marked or not, following or not`() {
        assertEquals(CameraMoveCause.USER_GESTURE, classifier.classify(isGesture = true, followingLocation = true))
        classifier.markAppMove()
        assertEquals(CameraMoveCause.USER_GESTURE, classifier.classify(isGesture = true, followingLocation = true))
        assertEquals(CameraMoveCause.USER_GESTURE, classifier.classify(isGesture = true, followingLocation = false))
    }

    @Test
    fun `an unmarked move that is not a touch, while following the location, is the follower's`() {
        assertEquals(CameraMoveCause.LOCATION_FOLLOW, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `a move the app marked is the app's, following or not`() {
        classifier.markAppMove()
        assertEquals(CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = false))
        assertEquals(CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `the locate tap's own move is the app's, because the mark is set before the mode changes`() {
        // The locate button marks, then sets the camera mode; the follower's first re-centre then arrives while following.
        classifier.markAppMove()
        assertEquals(
            "the user pressed locate, so the move it causes is the user's",
            CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true),
        )
    }

    @Test
    fun `a follower's move that arrives while a marked move is still in flight is classed with it, and does not clear the mark`() {
        classifier.markAppMove()
        assertEquals(CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true))
        assertEquals("still in flight: the next move is not the follower's yet", CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true))
        assertEquals(CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `the mark does not outlive the idle that ends its move`() {
        classifier.markAppMove()
        classifier.onCameraIdle()

        assertEquals(CameraMoveCause.LOCATION_FOLLOW, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `a mark set after an idle works again`() {
        classifier.markAppMove()
        classifier.onCameraIdle()
        classifier.markAppMove()

        assertEquals(CameraMoveCause.APP_REQUESTED, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `an unmarked move that is not a touch while not following is unknown`() {
        assertEquals(CameraMoveCause.UNKNOWN, classifier.classify(isGesture = false, followingLocation = false))
    }

    // A mark covers only the move it was set for (dispatch -380, planner review). MapLibre does not call the app's listener inside the camera call: it
    // queues a message on the main looper. So the settle step is queued behind that message, not run when the call returns.

    /** The SDK starting a move: its listener message is queued when the app's camera call runs, behind whatever is already queued. */
    private fun sdkStartsAMove(causes: MutableList<CameraMoveCause>, following: Boolean = true) {
        looper.post { causes += classifier.classify(isGesture = false, followingLocation = following) }
    }

    @Test
    fun `a mark whose move never starts does not catch the follower's next move`() {
        classifier.markAppMove() // the app asked for a move (a frame it could not apply, a camera already there); the SDK starts none
        looper.runAll()

        val causes = mutableListOf<CameraMoveCause>()
        sdkStartsAMove(causes) // much later: the follower re-centres
        looper.runAll()

        assertEquals("the follower's, not the app's", listOf(CameraMoveCause.LOCATION_FOLLOW), causes)
    }

    @Test
    fun `a mark whose move starts is the app's, though the settle step was queued before the move`() {
        val causes = mutableListOf<CameraMoveCause>()
        classifier.markAppMove() // the mark is set before the camera call, so the settle step is queued first
        sdkStartsAMove(causes) // the camera call queues the SDK's listener message behind it
        looper.runAll()

        assertEquals(listOf(CameraMoveCause.APP_REQUESTED), causes)
    }

    @Test
    fun `the follower's moves during a marked move that did start are still classed with it`() {
        val causes = mutableListOf<CameraMoveCause>()
        classifier.markAppMove()
        sdkStartsAMove(causes)
        looper.runAll() // the app's move has started and the settle step has run
        sdkStartsAMove(causes) // the follower ticks while the app's move is still in flight
        sdkStartsAMove(causes)
        looper.runAll()

        assertEquals(List(3) { CameraMoveCause.APP_REQUESTED }, causes)

        classifier.onCameraIdle()
        sdkStartsAMove(causes)
        looper.runAll()
        assertEquals("after the idle it is the follower's again", CameraMoveCause.LOCATION_FOLLOW, causes.last())
    }

    @Test
    fun `the locate tap with no move to make leaves the follower's next move the follower's`() {
        // The locate button marks, then sets the camera mode; with no fix yet, or already centred, nothing moves.
        classifier.markAppMove()
        looper.runAll()

        assertEquals(CameraMoveCause.LOCATION_FOLLOW, classifier.classify(isGesture = false, followingLocation = true))
    }

    @Test
    fun `an earlier mark's settle step, running late, does not drop a newer mark whose move is still queued`() {
        // Seen on the S22 at launch, when the main thread was busy: mark 1's second hop ran after mark 1's move had gone idle and mark 2 had been set,
        // dropped mark 2, and mark 2's own move then started unmarked.
        val first = mutableListOf<CameraMoveCause>()
        classifier.markAppMove() // queue: settle 1a
        sdkStartsAMove(first) // queue: settle 1a, move 1
        looper.runNext() // settle 1a runs and queues settle 1b: move 1, settle 1b
        looper.runNext() // move 1 starts
        classifier.onCameraIdle() // move 1 is over

        val second = mutableListOf<CameraMoveCause>()
        classifier.markAppMove() // queue: settle 1b, settle 2a
        sdkStartsAMove(second) // queue: settle 1b, settle 2a, move 2
        looper.runAll() // settle 1b is mark 1's; it must not touch mark 2

        assertEquals(listOf(CameraMoveCause.APP_REQUESTED), first)
        assertEquals("mark 2's move is the app's whichever settle step runs first", listOf(CameraMoveCause.APP_REQUESTED), second)
    }
}
