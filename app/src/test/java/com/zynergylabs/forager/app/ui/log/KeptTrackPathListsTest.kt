package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.TrackDecision
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
 * F3 (dispatch 2026-09-28-195, item 5; owner, 2026-09-29, "C: list screen loads lazily"): the saved path
 * reaches the two list surfaces [JournalEntryCardsTest] does not drive through the Journal tab: the drafts list
 * ([DraftsListScreen], where the plan's "Drafts get the saved path too" shows) and the sideways cards of a short
 * window ([CartographyEntryListScreen] with `sideways = true`, whose slot is [EntrySlotView]). Each is composed
 * directly, since each is the composable a list is drawn by, with no track loaded and a lambda standing for the read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KeptTrackPathListsTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val saved = listOf(LatLng(45.0, -122.0), LatLng(45.001, -122.002), LatLng(45.002, -122.004))

    private val keptGone = TrackDecision(trackId = "tr-gone", name = "Old loop", distanceMeters = 1_200.0, durationMillis = 1_800_000L, pointCount = 3, kept = true)

    private val draft = CartographyEntry.draft(id = "draft-1", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1L)
        .copy(text = "Half a loop", trackDecisions = listOf(keptGone))

    private val committed = CartographyEntry.draft(id = "entry-1", date = LocalDate.of(2026, 9, 12), updatedAtEpochMillis = 1L)
        .copy(isDraft = false, text = "A loop", trackDecisions = listOf(keptGone))

    @Test
    fun `a draft card draws the path saved for its kept track that is gone`() {
        val asked = mutableListOf<String>()
        composeRule.setContent {
            DraftsListScreen(
                drafts = listOf(draft),
                isLoading = false,
                onOpenDraft = {},
                onBack = {},
                distanceUnit = DistanceUnit.KILOMETERS,
                columns = 2,
                getSavedTrackPaths = { id -> asked += id; mapOf("tr-gone" to saved) },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(entryTrackThumbnailTestTag(draft.id), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf(draft.id), asked.distinct())
    }

    @Test
    fun `a sideways card's slot is the track thumbnail for a kept track that is gone, drawn from the saved path`() {
        composeRule.setContent {
            CartographyEntryListScreen(
                entries = listOf(committed),
                isLoading = false,
                onOpenEntry = {},
                emptyMessage = "none",
                distanceUnit = DistanceUnit.KILOMETERS,
                sideways = true,
                getSavedTrackPaths = { mapOf("tr-gone" to saved) },
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(entryTrackThumbnailTestTag(committed.id), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a sideways card with no saved path and no loaded track has the type-icon slot, not the thumbnail`() {
        composeRule.setContent {
            CartographyEntryListScreen(
                entries = listOf(committed),
                isLoading = false,
                onOpenEntry = {},
                emptyMessage = "none",
                distanceUnit = DistanceUnit.KILOMETERS,
                sideways = true,
            )
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(entryTrackThumbnailTestTag(committed.id), useUnmergedTree = true).assertDoesNotExist()
    }
}
