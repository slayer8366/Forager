package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.photo.CameraCaptureFiles
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
 * [LogEntryDetailScreen]'s own location line — L3 (`docs/plans/pr26-rework.md`'s Workstream L)
 * makes [MushroomLogEntry.foundAt] nullable, so this composable's "Found at ..." coordinate line
 * has a second state to prove: the owner-decided "No location set." text for an entry with none.
 * No dedicated test file existed for this screen before this addition.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogEntryDetailScreenTest {

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

    /** Generous on purpose — this proves the strip is centered, not that it's centered to the pixel. */
    private val centeringToleranceDp = 4f

    private fun setScreen(
        entry: MushroomLogEntry,
        onAddLocation: () -> Unit = {},
        onPullPhoto: () -> Unit = {},
        onSave: () -> Unit = {},
        onCancel: () -> Unit = {},
    ) {
        composeRule.setContent {
            LogEntryDetailScreen(
                entry = entry,
                cameraCaptureFiles = CameraCaptureFiles(ApplicationProvider.getApplicationContext()),
                onEntryChanged = {},
                onAddPhoto = {},
                onRemovePhoto = {},
                onPullPhoto = onPullPhoto,
                onAddLocation = onAddLocation,
                onSave = onSave,
                onCancel = onCancel,
                onDeleteEntry = {},
                onBack = {},
            )
        }
    }

    @Test
    fun `an entry with a location shows its coordinates`() {
        val entry = MushroomLogEntry.draft(id = "with-location-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

        setScreen(entry)

        composeRule.onNodeWithText("Found at 45.3260, -122.6340").assertIsDisplayed()
    }

    @Test
    fun `an entry with no location shows the no-location text instead of coordinates`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))

        setScreen(locationLessEntry)

        composeRule.onNodeWithText("No location set.").assertIsDisplayed()
    }

    /**
     * Workstream L4: [entry] routinely arrives with [MushroomLogEntry.foundAt] `null` now that
     * entry creation routes straight here — the button reads "Add Location" for that case and
     * "Change Location" once one is set, joining the Camera/Gallery row (see this screen's own
     * doc comment on why it lives there).
     */
    @Test
    fun `the location button reads Add Location when the entry has none set`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))

        setScreen(locationLessEntry)

        composeRule.onNodeWithText("Add Location").assertIsDisplayed()
    }

    @Test
    fun `the location button reads Change Location once the entry has one set`() {
        val locatedEntry = MushroomLogEntry.draft(id = "with-location-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

        setScreen(locatedEntry)

        composeRule.onNodeWithText("Change Location").assertIsDisplayed()
    }

    @Test
    fun `tapping the location button invokes onAddLocation`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))
        var invoked = false
        setScreen(locationLessEntry, onAddLocation = { invoked = true })

        composeRule.onNodeWithText("Add Location").performClick()

        assertEquals(true, invoked)
    }

    /**
     * Workstream G2: [LogPhotoThumbnail] now delegates to the shared [DecodedPhoto] rather than
     * its own hand-rolled decode — this proves the converted call site still renders a photo
     * (rather than asserting anything G2-specific, which [DecodedPhotoTest]/[PhotoGalleryScreenTest]
     * already own).
     */
    @Test
    fun `an entry with a photo still renders it`() {
        val entryWithPhoto = MushroomLogEntry.draft(id = "with-photo-1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))

        setScreen(entryWithPhoto)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()
    }

    /**
     * Workstream L4c §4: the photo strip must sit at the top of the Photos section (immediately
     * under the Camera/Gallery/From Album row — already true positionally, nothing else appears
     * between them) with the strip itself rendered as a centered group rather than flush left. Read
     * via real measured bounds (`getUnclippedBoundsInRoot`, the same primitive
     * [AvailabilityScreenLayoutTest] already uses for its own layout regression tests) rather than a
     * screenshot, since this environment has no Android emulator to render one: Robolectric's
     * Compose test rule performs real layout, so the leftover space on each side of the strip is
     * genuine measured evidence, not a stand-in.
     */
    @Test
    fun `a single photo renders centered horizontally`() {
        val entryWithPhoto = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))
        setScreen(entryWithPhoto)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val photoBounds = composeRule.onNodeWithContentDescription("Log photo").getUnclippedBoundsInRoot()
        val leftGap = photoBounds.left
        val rightGap = rootWidth - photoBounds.right

        assertTrue(
            "a single photo must be centered — left gap ($leftGap) and right gap ($rightGap) should match",
            kotlin.math.abs(leftGap.value - rightGap.value) <= centeringToleranceDp,
        )
    }

    @Test
    fun `two photos render left-to-right as a centered group`() {
        val entryWithPhotos = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(
                photos = listOf(
                    LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L),
                    LogPhoto(id = "p2", relativePath = "photos/p2.jpg", createdAtEpochMillis = 2_000L),
                ),
            )
        setScreen(entryWithPhotos)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().size == 2
        }

        assertGroupIsCenteredLeftToRight(count = 2)
    }

    @Test
    fun `four photos render left-to-right as a centered group`() {
        val entryWithPhotos = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(
                photos = (1..4).map { i ->
                    LogPhoto(id = "p$i", relativePath = "photos/p$i.jpg", createdAtEpochMillis = i * 1_000L)
                },
            )
        setScreen(entryWithPhotos)
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().size == 4
        }

        // Verified (not assumed): at this test's 320dp-wide root, four 88dp thumbnails at 8dp
        // spacing (376dp needed) don't fit one row (288dp available inside the form's 16dp
        // horizontal padding), so this wraps 3-then-1 — the FlowRow's own wrap behavior, not a bug.
        // Most real phones (360-412dp) wrap the same way; only a much wider window fits four on one
        // row. assertGroupIsCenteredLeftToRight groups by row (top) precisely because of this.
        assertGroupIsCenteredLeftToRight(count = 4)
    }

    /**
     * Groups the matched nodes by their row (`top`) rather than assuming a single row, since
     * [FlowRow] wraps once a row can't fit the next thumbnail — real behavior at four photos on
     * this test's 320dp-wide root (see the test above), and on most real phone widths too. Within
     * each row, thumbnails must run left to right with no overlap; each row, independently, must be
     * centered as its own group — [FlowRow]'s `horizontalArrangement` centers per row, not across
     * the whole wrapped strip, so that is the correct unit to assert centering on.
     */
    private fun assertGroupIsCenteredLeftToRight(count: Int) {
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val rootWidth = root.right - root.left
        val nodes = composeRule.onAllNodesWithContentDescription("Log photo")
        val actualCount = nodes.fetchSemanticsNodes().size
        assertEquals(count, actualCount)
        val bounds = (0 until actualCount).map { i -> nodes[i].getUnclippedBoundsInRoot() }

        val rows = bounds.groupBy { it.top }.toSortedMap(compareBy { it.value })
        for (row in rows.values) {
            val sorted = row.sortedBy { it.left }
            for (i in 1 until sorted.size) {
                assertTrue("within a row, thumbnails must run left to right, not overlap or stack", sorted[i].left >= sorted[i - 1].right)
            }
            val leftGap = sorted.first().left
            val rightGap = rootWidth - sorted.last().right
            assertTrue(
                "each row of $count photos must be centered on its own — left gap ($leftGap) and right gap ($rightGap) should match",
                kotlin.math.abs(leftGap.value - rightGap.value) <= centeringToleranceDp,
            )
        }
    }

    /**
     * Workstream G3: the third Photos-row button, distinct from "Camera" and "Gallery" (the
     * system picker) and from the bottom nav's own "Album" tab label — see this screen's own
     * inline comment on the wording check.
     */
    @Test
    fun `tapping From Album invokes onPullPhoto`() {
        val entry = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
        var invoked = false
        setScreen(entry, onPullPhoto = { invoked = true })

        composeRule.onNodeWithText("From Album").performClick()

        assertEquals(true, invoked)
    }

    /** Workstream L4b: Save and Cancel are new, explicit affordances this form previously had no equivalent of at all. */
    @Test
    fun `tapping Save invokes onSave`() {
        val entry = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
        var invoked = false
        setScreen(entry, onSave = { invoked = true })

        composeRule.onNodeWithText("Save").performClick()

        assertEquals(true, invoked)
    }

    @Test
    fun `tapping Cancel invokes onCancel`() {
        val entry = MushroomLogEntry.draft(id = "e1", location = null, date = LocalDate.of(2026, 8, 1))
        var invoked = false
        setScreen(entry, onCancel = { invoked = true })

        composeRule.onNodeWithText("Cancel").performClick()

        assertEquals(true, invoked)
    }
}
