package com.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.model.CapDecoration
import com.forager.app.domain.model.CapSection
import com.forager.app.domain.model.CapShape
import com.forager.app.domain.model.Feature
import com.forager.app.domain.model.HymenophoreDetails
import com.forager.app.domain.model.HymenophoreSection
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.Observed
import com.forager.app.domain.model.VeilSection
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
 * [LogEntryReportScreen]'s compiled report — only [Observed.Recorded]/[Feature.Present] values are
 * expected to render as lines, per that composable's own doc comment on why an unrecorded field is
 * left out rather than printed as "Not recorded" for every one of the fields a real entry
 * accumulates gradually; a section with nothing recorded at all is expected to say so explicitly
 * instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LogEntryReportScreenTest {

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

    private val partiallyRecordedEntry = MushroomLogEntry.draft(
        id = "entry-1",
        location = LatLng(45.326, -122.634),
        date = LocalDate.of(2026, 8, 1),
    ).copy(
        ownIdentification = "Possible chanterelle",
        cap = CapSection(
            shape = Observed.Recorded(CapShape.CONVEX),
            surface = Observed.NotObserved,
            decorations = Feature.Absent,
            margin = Observed.NotObserved,
            notes = "",
        ),
        hymenophore = HymenophoreSection(details = Observed.Recorded(HymenophoreDetails.Pores), notes = ""),
        veil = VeilSection(annulus = Feature.NotObserved, volva = Feature.Absent, notes = ""),
        notes = "Found near a large oak.",
    )

    @Test
    fun `recorded fields compile into readable lines, grouped under their section`() {
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithText("Your own identification: Possible chanterelle").assertIsDisplayed()
        composeRule.onNodeWithText("Shape: Convex").assertIsDisplayed()
        composeRule.onNodeWithText("No decorations observed").assertIsDisplayed()
        composeRule.onNodeWithText("Pores").assertIsDisplayed()
        composeRule.onNodeWithText("No volva").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Found near a large oak.").performScrollTo().assertIsDisplayed()
    }

    /** [CapSection.EMPTY]'s CapShape/surface/margin are all [Observed.NotObserved] — none of them should print a line. */
    @Test
    fun `an unrecorded field is left out of its section, not printed as a Not-recorded line`() {
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithText("Surface:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Margin:", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Annulus:", substring = true).assertDoesNotExist()
    }

    /**
     * Stage 2d: [StipeSection.EMPTY]/[ContextFleshSection.EMPTY]/[SporePrintSection.EMPTY]/
     * [HostSubstrateSection.EMPTY] carry nothing at all — omitted entirely now, never a "Not
     * recorded yet." placeholder (the owner's own reasoning: that text asserts an absence that may
     * be false). [partiallyRecordedEntry] has real content elsewhere (identification, cap, notes),
     * so this is the "partially filled — omit silently, no message" case, not the fully-empty one.
     */
    @Test
    fun `a section with nothing recorded is omitted, not shown with a placeholder`() {
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        // Stipe/Context-flesh/Spore-print/Host-substrate headings are gone along with their bodies.
        composeRule.onNodeWithText("Stipe").assertDoesNotExist()
        composeRule.onNodeWithText("Context / flesh").assertDoesNotExist()
        composeRule.onNodeWithText("Spore print").assertDoesNotExist()
        composeRule.onNodeWithText("Host & substrate").assertDoesNotExist()
        composeRule.onNodeWithText("Not recorded yet.").assertDoesNotExist()
        // A partially filled find shows no explanatory message either — only a fully empty one does.
        composeRule.onNodeWithText("There's nothing in here!", substring = true).assertDoesNotExist()
    }

    /** Stage 2d: literally nothing recorded anywhere — no location, identification, photos, notes, or taxonomic content — shows the owner's exact wording rather than a title and nothing else. */
    @Test
    fun `a find with nothing recorded at all shows the empty-find message, exactly as worded`() {
        val entirelyEmptyEntry = MushroomLogEntry.draft(id = "empty-1", location = null, date = LocalDate.of(2026, 8, 1))

        composeRule.setContent {
            LogEntryReportScreen(entry = entirelyEmptyEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithText("There's nothing in here!  You can change this by tapping the three dot menu > tap edit.").assertIsDisplayed()
        // The ordinary body content this message replaces is not also showing.
        composeRule.onNodeWithText("No location set.").assertDoesNotExist()
    }

    /** Confirms the instruction in the message is true — Edit really is one tap away in the overflow menu, per the dispatch's own "confirm the overflow menu genuinely offers Edit" requirement. */
    @Test
    fun `the empty-find message's own overflow menu route to Edit actually works`() {
        val entirelyEmptyEntry = MushroomLogEntry.draft(id = "empty-2", location = null, date = LocalDate.of(2026, 8, 1))
        var editCalls = 0
        composeRule.setContent {
            LogEntryReportScreen(entry = entirelyEmptyEntry, onEdit = { editCalls++ }, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        assertEquals(1, editCalls)
    }

    /** A find with only a photo and nothing else is not "nothing in here" — the message must not show alongside real content. */
    @Test
    fun `a find with only a photo shows no empty-find message`() {
        val photoOnlyEntry = MushroomLogEntry.draft(id = "photo-only-1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))

        composeRule.setContent {
            LogEntryReportScreen(entry = photoOnlyEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithText("There's nothing in here!", substring = true).assertDoesNotExist()
    }

    /**
     * L3 (`docs/plans/pr26-rework.md`'s Workstream L) makes [MushroomLogEntry.foundAt] nullable —
     * this is the display side of that: a location-less entry shows the owner-decided "No location
     * set." text rather than the "Found at ..." coordinate line. Given real content elsewhere
     * (own identification) so this exercises the no-location line specifically, distinct from
     * Stage 2d's own fully-empty case above, which a bare no-location draft would otherwise also be.
     */
    @Test
    fun `an entry with no location shows the no-location text instead of coordinates`() {
        val locationLessEntry = MushroomLogEntry.draft(id = "no-location-1", location = null, date = LocalDate.of(2026, 8, 1))
            .copy(ownIdentification = "Possible chanterelle")

        composeRule.setContent {
            LogEntryReportScreen(entry = locationLessEntry, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithText("No location set.").assertIsDisplayed()
    }

    @Test
    fun `the overflow menu's Edit entry option calls onEdit`() {
        var editCalls = 0
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = { editCalls++ }, onDeleteEntry = {}, onBack = {})
        }

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Edit entry").performClick()

        assertEquals(1, editCalls)
    }

    @Test
    fun `the overflow menu's Delete entry option calls onDeleteEntry`() {
        var deleteCalls = 0
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = {}, onDeleteEntry = { deleteCalls++ }, onBack = {})
        }

        composeRule.onNodeWithContentDescription("Entry options").performClick()
        composeRule.onNodeWithText("Delete entry").performClick()

        assertEquals(1, deleteCalls)
    }

    /** Workstream G2: [ReportPhotoThumbnail] now delegates to the shared [DecodedPhoto] — proves the converted call site still renders a photo. */
    @Test
    fun `an entry with a photo still renders it`() {
        val entryWithPhoto = partiallyRecordedEntry.copy(photos = listOf(LogPhoto(id = "p1", relativePath = "photos/p1.jpg", createdAtEpochMillis = 1_000L)))

        composeRule.setContent {
            LogEntryReportScreen(entry = entryWithPhoto, onEdit = {}, onDeleteEntry = {}, onBack = {})
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithContentDescription("Log photo").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Log photo").assertIsDisplayed()
    }

    @Test
    fun `the back arrow calls onBack`() {
        var backCalls = 0
        composeRule.setContent {
            LogEntryReportScreen(entry = partiallyRecordedEntry, onEdit = {}, onDeleteEntry = {}, onBack = { backCalls++ })
        }

        composeRule.onNodeWithContentDescription("Back to your log").performClick()

        assertEquals(1, backCalls)
    }
}
