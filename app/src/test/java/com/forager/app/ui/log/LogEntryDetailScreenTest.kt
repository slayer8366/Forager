package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.photo.CameraCaptureFiles
import java.time.LocalDate
import org.junit.Assert.assertEquals
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
