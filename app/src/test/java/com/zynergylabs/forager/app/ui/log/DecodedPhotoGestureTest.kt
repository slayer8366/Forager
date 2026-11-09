package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-317: a tap or long-press on a photo that uses [DecodedPhoto] reaches its
 * handler whether or not the decode lands during the gesture.
 *
 * The bug (`docs/audits/2026-09-30-ci-flake-diagnosis.md`, -296): [DecodedPhoto] swapped a
 * placeholder `Box` for an `Image` when its `Dispatchers.IO` decode finished, and the four
 * gesture-carrying call sites put their `clickable` on that swapped node, so a gesture in flight
 * when the swap landed was lost. Every test here places the swap **between `down` and `up`**
 * (or between `down` and the long-press timeout) on purpose, deterministically, through
 * [GatedDecode]'s test-only shadow of `BitmapFactory.decodeFile` — no production hook. The
 * other orderings (swap before `down`, after `up`) passed before the fix and are not tested here;
 * -296 measured them.
 *
 * Each test first checks **reachability**: no "Log photo" node exists at `down` (the placeholder is
 * showing), and one exists after the swap. A test whose swap never reached the gesture would pass
 * on the unfixed build for the wrong reason.
 *
 * The four sites are the ones -317 audited (`docs/audits/2026-09-30-album-gesture-completion-report.md`):
 * the album tile with and without delete wired, a find's thumbnail in the editor, and in the report.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], shadows = [GatedBitmapFactoryShadow::class])
class DecodedPhotoGestureTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @After
    fun releaseAnyHeldDecode() {
        composeRule.mainClock.autoAdvance = true
        GatedDecode.reset()
    }

    private val photo = LogPhoto(id = "gesture-photo", relativePath = "photos/none-gesture.jpg", createdAtEpochMillis = 1_758_300_000_000L)

    private fun photoNodes() = composeRule.onAllNodesWithContentDescription("Log photo")

    private fun placeholderShowing() = photoNodes().fetchSemanticsNodes().isEmpty()

    /**
     * Holds the decode, runs [startGesture] (a `down`), lets the decode finish and the swap apply in
     * whole frames (the clock stays manual, so no long-press timeout can expire meanwhile), and
     * checks the swap really happened before handing back to [finishGesture].
     */
    private fun gestureAcrossTheSwap(startGesture: () -> Unit, finishGesture: () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.waitForIdle()
        assertTrue("reachability: the placeholder must be showing before the gesture starts", placeholderShowing())
        startGesture()
        GatedDecode.release()
        assertTrue("the gated decode never finished on its IO thread", GatedDecode.awaitFinished())
        // The decode returning on its IO thread and its result reaching the main thread are two steps;
        // the second is a post to the main looper. The test clock is manual here (a long-press timeout
        // must not run), so idle the looper, without advancing any clock, for a short real interval so
        // the bitmap is published; the frames below then apply it.
        val settleUntil = System.nanoTime() + SETTLE_NANOS
        while (System.nanoTime() < settleUntil) Shadows.shadowOf(Looper.getMainLooper()).idle()
        var frames = 0
        while (placeholderShowing() && frames < MAX_SWAP_FRAMES) {
            composeRule.mainClock.advanceTimeByFrame()
            frames++
        }
        assertTrue("reachability: the swap to the image did not apply within $MAX_SWAP_FRAMES frames", !placeholderShowing())
        finishGesture()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    // ── the album tile (EntriesAlbum.kt AlbumPhotoTile) ──

    private fun setAlbum(wiredDelete: Boolean) {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            EntriesAlbum(
                photos = listOf(GalleryPhoto(photo = photo, referencingEntryIds = emptyList())),
                isLoading = false,
                onDeletePhoto = {},
                photoAcquisition = rememberPhotoAcquisitionLaunchers({}, {}),
                onRequestDeletePhoto = if (wiredDelete) ({ _: String -> }) else null,
            )
        }
    }

    private fun albumTile() = composeRule.onNodeWithTag(albumPhotoTestTag(photo.id))

    @Test
    fun `album tile with delete wired - a tap whose decode lands between down and up opens the viewer`() {
        setAlbum(wiredDelete = true)

        gestureAcrossTheSwap(
            startGesture = { albumTile().performTouchInput { down(center) } },
            finishGesture = { albumTile().performTouchInput { up() } },
        )

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
    }

    @Test
    fun `album tile with delete wired - a long-press whose decode lands before the timeout opens the Delete menu`() {
        setAlbum(wiredDelete = true)

        gestureAcrossTheSwap(
            startGesture = { albumTile().performTouchInput { down(center) } },
            finishGesture = {
                // Past the long-press timeout, with the pointer still down: the long-press fires here or not
                // at all. The clock is manual, so the timeout is advanced on the main clock, where the
                // pointer-input coroutine's delay lives; advancing only the event time would not fire it.
                var timeoutMillis = 0L
                albumTile().performTouchInput { timeoutMillis = viewConfiguration.longPressTimeoutMillis }
                composeRule.mainClock.advanceTimeBy(timeoutMillis + 200)
                albumTile().performTouchInput { up() }
            },
        )

        composeRule.onNodeWithTag(TILE_OPTIONS_DELETE_TAG).assertIsDisplayed()
    }

    @Test
    fun `album tile with no delete wired - a tap whose decode lands between down and up opens the viewer`() {
        setAlbum(wiredDelete = false)

        gestureAcrossTheSwap(
            startGesture = { albumTile().performTouchInput { down(center) } },
            finishGesture = { albumTile().performTouchInput { up() } },
        )

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
    }

    // ── a find's thumbnail in the editor (LogEntryDetailScreen.kt LogPhotoThumbnail) ──

    private val openFullScreen = SemanticsMatcher("has click label 'Open full screen'") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Open full screen"
    }

    private fun entryWithPhoto() = MushroomLogEntry
        .draft(id = "gesture-entry", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))
        .copy(photos = listOf(photo))

    @Test
    fun `find editor thumbnail - a tap whose decode lands between down and up opens the viewer`() {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            LogEntryDetailScreen(
                entry = entryWithPhoto(),
                onOpenCamera = {},
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = {},
                onAddLocation = {},
                onSave = {},
                onCancel = {},
                onDeleteEntry = {},
                onBack = {},
            )
        }

        gestureAcrossTheSwap(
            startGesture = { composeRule.onNode(openFullScreen).performTouchInput { down(center) } },
            finishGesture = { composeRule.onNode(openFullScreen).performTouchInput { up() } },
        )

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
    }

    // ── a find's thumbnail in the report (LogEntryReportScreen.kt ReportPhotoThumbnail) ──

    @Test
    fun `find report thumbnail - a tap whose decode lands between down and up opens the viewer`() {
        GatedDecode.holdDecodes(decodes = 1)
        composeRule.setContent {
            LogEntryReportScreen(entry = entryWithPhoto(), onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        gestureAcrossTheSwap(
            startGesture = { composeRule.onNode(openFullScreen).performTouchInput { down(center) } },
            finishGesture = { composeRule.onNode(openFullScreen).performTouchInput { up() } },
        )

        composeRule.onNodeWithTag(PHOTO_VIEWER_TAG).assertIsDisplayed()
    }

    private companion object {
        /** Whole frames allowed for the swap to apply; far under any gesture timeout (16 ms each against a 500 ms long-press). */
        const val MAX_SWAP_FRAMES = 8

        /** Real time the main looper is idled after the IO decode returns, so its result is published before the frames. */
        const val SETTLE_NANOS = 150_000_000L
    }
}
