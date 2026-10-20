package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.location.modes.CameraMode

/**
 * Dispatch 2026-10-19-36 (RECORD -1468), walk step 3. The owner, as a step path: "come back to Maps > the map is exactly
 * where you left it, same place and same zoom; only a new search or a tap on locate moves it."
 *
 * Each test runs a sequence through the real decisions, in the order the map makes them: the data+camera refresh's region
 * branch ([regionRefresh], which mirrors `MapDataRefresh.kt`'s `shouldMoveCameraToTarget` / `cameraTargetTakenAsApplied`
 * branch), the camera snapshot taken when the camera settles (built here by hand, as `MapListeners.kt` builds it inline),
 * the restore a returning map makes ([cameraRestoreFor]), and the cover decision ([coverDecision]).
 *
 * Device-only, and not shown by anything here: that `MapDataRefresh.kt` writes [cameraTargetTakenAsApplied]'s result (a
 * real MapLibre map does not load under Robolectric), that the camera then actually stays put, that the cover shows, and the
 * locate tap's own move (the puck's tracking owns that). The snapshot is copied here rather than run.
 *
 * Five tests are guards, labelled "not shown to bite": they pass with and without the fix, and are here so the fix is
 * seen not to take away a move the owner still wants.
 */
class MapKeepsViewOnReturnTest {

    /** The located region at app open, and the one about 67 m north after a walk (the S22 run in RECORD -1468). */
    private val atOpen: Pair<Region, LatLng?> = Region(lat = 45.3260, lng = -122.6340, radiusKm = 15) to null
    private val afterWalk: Pair<Region, LatLng?> = Region(lat = 45.3266, lng = -122.6340, radiusKm = 15) to null

    /** A region that genuinely changed while the map was away: a search cleared on another tab, back to the located one. */
    private val elsewhere: Pair<Region, LatLng?> = Region(lat = 45.4000, lng = -122.5000, radiusKm = 15) to null

    private data class Refresh(val moved: Boolean, val applied: Pair<Region, LatLng?>?)

    /** The refresh's region branch, as `MapDataRefresh.kt` takes it when no search frame or camera request is pending. */
    private fun regionRefresh(following: Boolean, target: Pair<Region, LatLng?>, applied: Pair<Region, LatLng?>?): Refresh =
        if (shouldMoveCameraToTarget(following, target, applied)) {
            Refresh(moved = true, applied = target)
        } else {
            Refresh(moved = false, applied = cameraTargetTakenAsApplied(following, target, applied))
        }

    /** The camera the user leaves, as the idle listener saves it. */
    private fun leftCamera(following: Boolean, applied: Pair<Region, LatLng?>?) = MapCameraSnapshot(
        target = LatLng(45.3270, -122.6330),
        zoom = 16.4,
        bearing = 0.0,
        tilt = 0.0,
        following = following,
        appliedTarget = applied,
    )

    private data class Return(val moved: Boolean, val cover: CoverDecision)

    /** Back on the Maps tab: the restore, then the first refresh on the restored style, and the cover decided as the map is made. */
    private fun comeBack(
        saved: MapCameraSnapshot,
        handed: Pair<Region, LatLng?>,
        searchFrameRequestId: Int = 0,
        appliedSearchFrameId: Int = 0,
    ): Return {
        val restore = cameraRestoreFor(saved, previousCameraMode = null)!!
        val following = restore.cameraMode != CameraMode.NONE
        val frame = searchFrameMove(following, searchFrameRequestId, appliedSearchFrameId)
        val moved = frame != SearchFrameMove.NONE || regionRefresh(following, handed, restore.appliedTarget).moved
        val cover = coverDecision(
            pictureCamera = saved,
            saved = saved,
            target = handed,
            searchFrameRequestId = searchFrameRequestId,
            appliedSearchFrameId = appliedSearchFrameId,
            cameraRequestPending = false,
        )
        return Return(moved, cover)
    }

    /**
     * The located region applied at open (what the still-phone run in RECORD -1468 implies: every return logged `SHOW`), then
     * following, the walk, and a locate tap: the fix changes the target while the puck leads. How the target came to be
     * applied at open with tracking on is undetermined from the code; the cold-start test below covers the other start.
     */
    private fun followedThroughALocateChange(): Pair<Region, LatLng?>? {
        val open = regionRefresh(following = false, target = atOpen, applied = null)
        val tapped = regionRefresh(following = true, target = afterWalk, applied = open.applied)
        assertFalse("the refresh moved the camera while the puck was following", tapped.moved)
        return tapped.applied
    }

    // ── Cases B and C: the bug ──

    @Test
    fun `B - a flick ends following, then Journal and back, and the map is where it was left`() {
        val applied = followedThroughALocateChange()
        // The flick: the SDK drops tracking; the idle after it saves the camera, not following.
        val back = comeBack(leftCamera(following = false, applied = applied), handed = afterWalk)
        assertFalse(
            "returning after a flick moved the camera to the located region: the target that changed while following " +
                "was not taken as applied",
            back.moved,
        )
        assertEquals("the cover should agree that nothing moves", CoverDecision.SHOW, back.cover)
    }

    @Test
    fun `C - a small nudge ends following, then Journal and back, and the map is where it was left`() {
        // Headless, a nudge and a flick are the same input (following ends); only the phone tells them apart.
        val applied = followedThroughALocateChange()
        val back = comeBack(leftCamera(following = false, applied = applied), handed = afterWalk)
        assertFalse(
            "returning after a nudge moved the camera to the located region: the target that changed while following " +
                "was not taken as applied",
            back.moved,
        )
        assertEquals("the cover should agree that nothing moves", CoverDecision.SHOW, back.cover)
    }

    // ── The same jump without a tab change ──

    @Test
    fun `no tab change - a drag ends following, and the next refresh (a trail point, a bubble, a style swap) leaves the map alone`() {
        val applied = followedThroughALocateChange()
        // The drag ends following. Any other key of the refresh effect re-runs it with the same target.
        val next = regionRefresh(following = false, target = afterWalk, applied = applied)
        assertFalse(
            "a refresh after the drag moved the camera to the located region: the target that changed while following " +
                "was not taken as applied",
            next.moved,
        )
    }

    @Test
    fun `cold start with permission - the puck leads from the first refresh, and a drag then a refresh leaves the map alone`() {
        // The style callback turns tracking on before the first refresh can run (MapStyleReload.kt), so nothing has been
        // applied yet when the first refresh skips its move.
        val first = regionRefresh(following = true, target = atOpen, applied = null)
        assertFalse("the first refresh fought the puck", first.moved)
        val afterDrag = regionRefresh(following = false, target = atOpen, applied = first.applied)
        assertFalse(
            "a refresh after the first drag since a cold start moved the camera to the located region: the target the " +
                "first refresh skipped while following was not taken as applied",
            afterDrag.moved,
        )
    }

    // ── Guards: the moves that stay (not shown to bite) ──

    @Test
    fun `A - following when leaving, the map comes back following, with nothing moved (guard, not shown to bite)`() {
        val applied = followedThroughALocateChange()
        val back = comeBack(leftCamera(following = true, applied = applied), handed = afterWalk)
        assertFalse("a map that came back following had its camera moved by the refresh", back.moved)
        assertEquals(CoverDecision.SHOW, back.cover)
    }

    @Test
    fun `a new search started while the map was away still frames on return (guard, not shown to bite)`() {
        val applied = followedThroughALocateChange()
        val back = comeBack(
            leftCamera(following = false, applied = applied),
            handed = elsewhere,
            searchFrameRequestId = 2,
            appliedSearchFrameId = 1,
        )
        assertTrue("a search's frame did not move the returning map", back.moved)
        assertEquals(CoverDecision.MOVE_PENDING, back.cover)
    }

    @Test
    fun `a region that genuinely changed while the map was away, not following, still moves on return (guard, not shown to bite)`() {
        val applied = followedThroughALocateChange()
        val back = comeBack(leftCamera(following = false, applied = applied), handed = elsewhere)
        assertTrue("a region that changed while the map was away was swallowed on return", back.moved)
        assertEquals(CoverDecision.MOVE_PENDING, back.cover)
    }

    @Test
    fun `cold start without permission - nothing follows, and the first refresh frames the region (guard, not shown to bite)`() {
        val first = regionRefresh(following = false, target = atOpen, applied = null)
        assertTrue("the first refresh without tracking did not frame the region", first.moved)
        assertEquals(atOpen, first.applied)
    }

    @Test
    fun `the cover decision agrees with the refresh on every return (guard, not shown to bite)`() {
        val applied = followedThroughALocateChange()
        val returns = listOf(
            comeBack(leftCamera(following = false, applied = applied), handed = afterWalk),
            comeBack(leftCamera(following = true, applied = applied), handed = afterWalk),
            comeBack(leftCamera(following = false, applied = applied), handed = elsewhere),
            comeBack(leftCamera(following = true, applied = applied), handed = elsewhere),
            comeBack(leftCamera(following = false, applied = atOpen), handed = afterWalk),
            comeBack(leftCamera(following = false, applied = null), handed = afterWalk),
        )
        returns.forEachIndexed { index, back ->
            assertEquals(
                "return $index: the cover said ${back.cover} while the refresh ${if (back.moved) "moved" else "did not move"}",
                back.moved,
                back.cover == CoverDecision.MOVE_PENDING,
            )
        }
    }
}
