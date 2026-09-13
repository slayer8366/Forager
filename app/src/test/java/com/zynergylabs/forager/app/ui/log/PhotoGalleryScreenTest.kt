package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Workstream G2 (`docs/plans/pr26-rework.md`): [PhotoGalleryScreen] against the real read path's
 * [GalleryPhoto] shape, per that dispatch's own "Tests" section — the gallery renders photos
 * including one with zero references, and shows the empty state when there are none.
 *
 * Standalone-photos dispatch: also covers the screen's own Camera/Import buttons (present
 * regardless of loading/empty/populated state) and the reworded empty-state copy. Tapping Camera/
 * Import itself is not exercised here — same precedent as [LogEntryDetailScreenTest], which tests
 * only its own "From Album" button's click (a pure Compose callback) and leaves Camera/Import
 * untested at the click level, since both launch a real system activity/permission dialog Robolectric
 * cannot meaningfully drive. Button label updated from "Gallery" to "Import" (entry-photo-acquisition
 * dispatch, Item 1) — see [PhotoGalleryScreen]'s own doc comment on that button.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PhotoGalleryScreenTest {

    private val composeRule = createComposeRule()
    private val cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext())

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    @Test
    fun `renders a photo with a known date`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_700_000_000_000L),
            referencingEntryIds = listOf("entry-1"),
        )

        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(photo), isLoading = false, onDeletePhoto = {}, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        // 1_700_000_000_000ms -> 2023-11-14 UTC; asserted against LocalDate's own toString() (the
        // same "just show it" convention this screen's own doc comment cites) rather than a
        // hand-picked fixed date, so this doesn't depend on this machine's default time zone.
        val expected = java.time.Instant.ofEpochMilli(1_700_000_000_000L)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        composeRule.onNodeWithText(expected).assertIsDisplayed()
    }

    /**
     * The dispatch's own required case: a [GalleryPhoto] with no referencing entries at all — a
     * real, reachable state (see that type's own doc comment) — must render sensibly, not crash
     * or be filtered out.
     */
    @Test
    fun `renders a photo with zero referencing entries`() {
        val orphaned = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )

        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(orphaned), isLoading = false, onDeletePhoto = {}, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
    }

    @Test
    fun `a null createdAtEpochMillis reads Date unknown, never a fabricated date`() {
        val migrated = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = listOf("entry-1"),
        )

        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(migrated), isLoading = false, onDeletePhoto = {}, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
    }

    @Test
    fun `the empty state shows when there are no photos`() {
        composeRule.setContent {
            PhotoGalleryScreen(photos = emptyList(), isLoading = false, onDeletePhoto = {}, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithText("No photos yet. Use Camera or Import above to add one.").assertIsDisplayed()
    }

    @Test
    fun `a load error message shows instead of the empty state when there are no photos`() {
        composeRule.setContent {
            PhotoGalleryScreen(
                photos = emptyList(),
                isLoading = false,
                onDeletePhoto = {},
                cameraCaptureFiles = cameraCaptureFiles,
                onAddGalleryPhoto = {},
                loadErrorMessage = "Photo gallery unavailable.",
            )
        }

        composeRule.onNodeWithText("Photo gallery unavailable.").assertIsDisplayed()
        composeRule.onNodeWithText("No photos yet. Use Camera or Import above to add one.").assertDoesNotExist()
    }

    /** Not belief-changing — mirrors [LogGalleryScreen]'s identical rule: a failed refresh never hides photos already showing. */
    @Test
    fun `a load error message does not hide photos already showing`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )

        composeRule.setContent {
            PhotoGalleryScreen(
                photos = listOf(photo),
                isLoading = false,
                onDeletePhoto = {},
                cameraCaptureFiles = cameraCaptureFiles,
                onAddGalleryPhoto = {},
                loadErrorMessage = "Photo gallery unavailable.",
            )
        }

        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
        composeRule.onNodeWithText("Photo gallery unavailable.").assertDoesNotExist()
    }

    /** Standalone-photos dispatch: Camera and Import are always available, not just when the gallery is empty. */
    @Test
    fun `Camera and Import buttons are shown alongside a populated gallery`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )

        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(photo), isLoading = false, onDeletePhoto = {}, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Import").assertIsDisplayed()
    }

    /**
     * Workstream G3: deleting a referenced photo warns with the correct count before calling
     * [PhotoGalleryScreen.onDeletePhoto] — the count comes straight from
     * [GalleryPhoto.referencingEntryIds], the whole reason G2 built the richer read path.
     */
    @Test
    fun `deleting a referenced photo warns with the correct count, and confirming calls onDeletePhoto`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = listOf("entry-1", "entry-2"),
        )
        var deleted: GalleryPhoto? = null
        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(photo), isLoading = false, onDeletePhoto = { deleted = it }, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithContentDescription("Delete this photo").performClick()

        composeRule.onNodeWithText("This photo is used in 2 entries. Deleting it will remove it from all of them too.").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(photo, deleted)
    }

    /**
     * Owner decision, 2026-08-22: "if nothing references it, no warning is needed" — there is
     * nothing for an entries-count line to warn about at zero, so this dialog omits it, but still
     * confirms before deleting (deletion is irreversible either way).
     */
    @Test
    fun `deleting an unreferenced photo shows a plain confirmation, no entries-count warning`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = emptyList(),
        )
        var deleted: GalleryPhoto? = null
        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(photo), isLoading = false, onDeletePhoto = { deleted = it }, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithContentDescription("Delete this photo").performClick()

        composeRule.onNodeWithText("This photo isn't used in any entry.").assertIsDisplayed()
        composeRule.onNodeWithText("entries", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Delete").performClick()

        assertEquals(photo, deleted)
    }

    @Test
    fun `cancelling the delete confirmation changes nothing`() {
        val photo = GalleryPhoto(
            photo = LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = null),
            referencingEntryIds = listOf("entry-1"),
        )
        var deleted: GalleryPhoto? = null
        composeRule.setContent {
            PhotoGalleryScreen(photos = listOf(photo), isLoading = false, onDeletePhoto = { deleted = it }, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }

        composeRule.onNodeWithContentDescription("Delete this photo").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        assertNull(deleted)
        // The dialog itself is gone, and the photo is still shown, unaffected.
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.onNodeWithText("Date unknown").assertIsDisplayed()
    }

    // ── Full-screen viewer from the Album (owner follow-up to the full-screen-photo-viewer dispatch) ──
    //
    // Coordinate touches, not semantic clicks: the claim is which of the tile's two targets a
    // finger reaches (CLAUDE.md, Testing).

    private fun albumOf(vararg ids: String): List<GalleryPhoto> = ids.map { id ->
        GalleryPhoto(photo = LogPhoto(id = id, relativePath = "photos/$id.jpg", createdAtEpochMillis = null), referencingEntryIds = emptyList())
    }

    private fun setAlbum(photos: List<GalleryPhoto>, onDeletePhoto: (GalleryPhoto) -> Unit = {}) {
        composeRule.setContent {
            PhotoGalleryScreen(photos = photos, isLoading = false, onDeletePhoto = onDeletePhoto, cameraCaptureFiles = cameraCaptureFiles, onAddGalleryPhoto = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().size == photos.size
        }
    }

    private fun viewerIsOpen(): Boolean = composeRule.onAllNodesWithTag(PHOTO_VIEWER_TAG).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `touching a tile's photo opens the viewer on that photo, with the whole album to step through`() {
        setAlbum(albumOf("p1", "p2", "p3"))

        composeRule.onAllNodesWithContentDescription("Log photo")[1].performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(true, viewerIsOpen())
        composeRule.onNodeWithTag(PHOTO_VIEWER_COUNTER_TAG).assertTextEquals("2 / 3")
        composeRule.onNodeWithContentDescription("Close photo").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals(false, viewerIsOpen())
    }

    @Test
    fun `touching the delete control asks to delete, and does not open the viewer`() {
        var deleted: GalleryPhoto? = null
        setAlbum(albumOf("p1"), onDeletePhoto = { deleted = it })

        composeRule.onNodeWithContentDescription("Delete this photo").performTouchInput { click(center) }
        composeRule.waitForIdle()

        assertEquals(false, viewerIsOpen())
        composeRule.onNodeWithText("Delete this photo?").assertIsDisplayed()
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals("p1", deleted?.photo?.id)
    }

    /** A finger is not a point: the tile's four corners away from the delete control, and a point just outside the control's own box, all open. */
    @Test
    fun `touches across the tile away from the delete control all open the viewer`() {
        var deletes = 0
        setAlbum(albumOf("p1"), onDeletePhoto = { deletes++ })
        val tile = composeRule.onNodeWithContentDescription("Log photo")
        val bounds = tile.getUnclippedBoundsInRoot()
        val inset = 6.dp
        val samples = listOf(
            Pair(inset, inset),
            Pair(inset, bounds.height - inset),
            Pair(bounds.width - inset, bounds.height - inset),
            Pair(bounds.width / 2, bounds.height / 2),
            // Just below the IconButton's 48dp corner box, at its horizontal centre.
            Pair(bounds.width - 24.dp, 52.dp),
        )
        samples.forEach { (x, y) ->
            val point = with(composeRule.density) { Offset(x.toPx(), y.toPx()) }
            tile.performTouchInput { click(point) }
            composeRule.waitForIdle()
            assertEquals("a touch at ($x, $y) must open the viewer", true, viewerIsOpen())
            composeRule.onNodeWithContentDescription("Close photo").performTouchInput { click() }
            composeRule.waitForIdle()
        }
        assertEquals(0, deletes)
        composeRule.onNodeWithText("Delete this photo?").assertDoesNotExist()
    }
}
