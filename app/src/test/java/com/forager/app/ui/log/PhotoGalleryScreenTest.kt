package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LogPhoto
import com.forager.app.photo.CameraCaptureFiles
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
}
