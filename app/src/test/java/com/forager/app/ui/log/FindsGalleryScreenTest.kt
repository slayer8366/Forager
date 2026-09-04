package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
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
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * [FindsGalleryScreen] — the Stage 2b follow-up dispatch's restored unify (point 1), replacing the
 * former, independently-diverged `LogGalleryScreenTest`/`LogEntryListScreenTest` this consolidates
 * (former grid-vs-list coverage folded into one set, since there's only one shape now). Also covers
 * [FindsGalleryScreen.onAddEntry]'s nullability (point 1's `null` branch — [LogPanel]'s own usage,
 * see that composable's own doc comment) and confirms no Album tab exists here (point 3 — Album
 * lives in [CartographyScreen] only now).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FindsGalleryScreenTest {

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

    @Test
    fun `an entry with a cover photo renders it`() {
        val entryWithPhoto = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(entryWithPhoto), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()
    }

    @Test
    fun `an entry with no photos shows the placeholder icon, not a broken image`() {
        val entryWithoutPhoto = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1))

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(entryWithoutPhoto), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }

        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Log photo").assertCountEquals(0)
    }

    /**
     * Workstream L4b-R's Log/Drafts toggle — the gate item "drafts do not appear in the entry list,
     * gallery cover thumbnails, or anywhere else the audit identifies" applies here too. Defaults to
     * the Log tab: the committed entry shows with its "+" tile, the draft is nowhere on screen.
     */
    @Test
    fun `by default the Log tab shows only committed entries, with the add tile, and no draft is visible`() {
        val committed = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)
        val draft = MushroomLogEntry.draft(id = "draft-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 2))

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(committed), draftEntries = listOf(draft), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }

        composeRule.onNodeWithText("Find on 2026-08-01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New log entry").assertIsDisplayed()
        composeRule.onNodeWithText("Find on 2026-08-02").assertDoesNotExist()
        composeRule.onNodeWithText("Draft").assertDoesNotExist()
    }

    @Test
    fun `tapping the Drafts tab shows only drafts, hides the committed entry and the add tile`() {
        val committed = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)
        val draft = MushroomLogEntry.draft(id = "draft-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 2))

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(committed), draftEntries = listOf(draft), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }

        composeRule.onNodeWithText("Drafts (1)").performClick()

        composeRule.onNodeWithText("Find on 2026-08-02").assertIsDisplayed()
        composeRule.onNodeWithText("Draft").assertIsDisplayed()
        composeRule.onNodeWithText("Find on 2026-08-01").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("New log entry").assertDoesNotExist()
    }

    /** [LogPanel]'s own usage — no "+" tile inside this list at all, matching the former `LogEntryListScreen`'s shape (the expanded window starts a find via the map's "Log a find" flow instead). */
    @Test
    fun `with onAddEntry null, no add tile is shown even on the Log tab`() {
        val committed = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(committed), isLoading = false, onOpenEntry = {}, onAddEntry = null)
        }

        composeRule.onNodeWithText("Find on 2026-08-01").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("New log entry").assertDoesNotExist()
    }

    @Test
    fun `a set loadErrorMessage shows the unavailable text when there are no entries to show`() {
        composeRule.setContent {
            FindsGalleryScreen(entries = emptyList(), isLoading = false, onOpenEntry = {}, loadErrorMessage = "Log entries unavailable.")
        }

        composeRule.onNodeWithText("Log entries unavailable.").assertIsDisplayed()
    }

    @Test
    fun `a set loadErrorMessage does not hide entries already showing`() {
        val committed = MushroomLogEntry.draft(id = "entry-1", location = LatLng(45.326, -122.634), date = LocalDate.of(2026, 8, 1)).copy(isDraft = false)

        composeRule.setContent {
            FindsGalleryScreen(entries = listOf(committed), isLoading = false, onOpenEntry = {}, loadErrorMessage = "Log entries unavailable.")
        }

        composeRule.onNodeWithText("Find on 2026-08-01").assertIsDisplayed()
    }

    /** Stage 2b follow-up dispatch, point 3 — Album lives in [CartographyScreen] only now. */
    @Test
    fun `there is no Album tab`() {
        composeRule.setContent {
            FindsGalleryScreen(entries = emptyList(), isLoading = false, onOpenEntry = {}, onAddEntry = {})
        }

        composeRule.onNodeWithText("Album").assertDoesNotExist()
    }
}
