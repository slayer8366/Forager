package com.zynergylabs.forager.app.ui.log

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.availability.commitDeleteOfflineRegion
import com.zynergylabs.forager.app.ui.availability.offlineRegionDeleteButtonTag
import com.zynergylabs.forager.app.ui.availability.requestDeleteOfflineRegion
import com.zynergylabs.forager.app.ui.availability.undoDeleteOfflineRegion
import com.zynergylabs.forager.app.ui.map.MapSlot
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
 * Dispatch 2026-10-29-10 (RECORD -1817, -1818; the owner: "Visible Delete button (Recommended)", "Each row in Offline
 * maps shows a trash button. The swipe still works and Undo stays."): every offline-map row shows a trash button that
 * asks for the swipe's own pending delete, with the same Undo and no confirm dialog.
 *
 * The two screens that list offline-map rows are both covered: Records' Offline maps chip (the Offline maps panel,
 * `OfflineRegionsSection`) and Records' All logbook (`RecordsLogbookList`). Driven through the real [RecordsTab], the real
 * [AvailabilityViewModel] over a fake repository that records every tile delete, a real [SnackbarHost] and
 * [PendingDeleteSnackbarEffects] fed by the notice builder `MainActivity` uses, with `MainActivity`'s own callback
 * (`requestDeleteOfflineRegion`). The button is touched with real coordinate touches at several points across its own
 * bounds (CLAUDE.md: a semantic click asserts wiring, not routing). Every delete assertion reads the fake repository.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class OfflineMapDeleteButtonTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val offlineMapRepository = OmdOfflineMapRepository(OMD_REGIONS)
    private lateinit var availabilityViewModel: AvailabilityViewModel

    private fun setScreen(chip: RecordsSubTab) {
        availabilityViewModel = omdAvailabilityViewModel(offlineMapRepository, regionReferenceCounts = mapOf(7L to 2, 8L to 0))
        composeRule.setContent {
            val hostState = remember { SnackbarHostState() }
            val availability by availabilityViewModel.uiState.collectAsState()
            Box(modifier = Modifier.fillMaxSize()) {
                RecordsTab(
                    waypoints = emptyList(),
                    waypointsErrorMessage = null,
                    onDeleteWaypoint = {},
                    availabilityUiState = availability,
                    distanceUnit = DistanceUnit.MILES,
                    currentTime = CurrentTimeProvider { 0L },
                    mapSlot = OMD_STUB_MAP,
                    night = false,
                    onOfflineMapRegionPicked = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    // What MainActivity passes: the pending-delete request, the swipe's and now the button's.
                    onDeleteOfflineRegion = availabilityViewModel::requestDeleteOfflineRegion,
                    tracks = emptyList(),
                    onTracksOpened = {},
                    findsContent = {},
                )
                SnackbarHost(hostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
            PendingDeleteSnackbarEffects(
                notices = listOfNotNull(
                    offlineRegionDeleteNotice(
                        availability.pendingOfflineRegionDelete,
                        availabilityViewModel::undoDeleteOfflineRegion,
                        availabilityViewModel::commitDeleteOfflineRegion,
                    ),
                ),
                hostState = hostState,
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(chip)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    private fun button(regionId: Long) = composeRule.onNodeWithTag(offlineRegionDeleteButtonTag(regionId))

    private fun exists(tag: String): Boolean = composeRule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    private fun letSnackbarTimeOut() {
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS + 1_000L)
        composeRule.waitForIdle()
    }

    private fun touchUndo() {
        composeRule.onNodeWithText("Undo").assert(hasClickAction()).performTouchInput { click() }
        composeRule.waitForIdle()
    }

    /**
     * Three real touches across region [regionId]'s trash button, each a fresh attempt: the row leaves, [snackbar] shows,
     * nothing is deleted while Undo is up, the details sheet does not open (the touch went to the button, not the row),
     * and Undo brings the row back. Then one more touch, left to time out: exactly that region's tiles are deleted.
     */
    private fun assertButtonPendsWithUndoAndCommitsOnTimeout(regionId: Long, name: String, snackbar: String, rowTag: String) {
        listOf(0.2f to 0.3f, 0.5f to 0.5f, 0.8f to 0.7f).forEach { (fx, fy) ->
            button(regionId).performScrollTo().assertIsDisplayed().performTouchInput { click(Offset(width * fx, height * fy)) }
            composeRule.waitForIdle()

            composeRule.onNodeWithText(snackbar).assertIsDisplayed()
            assertEquals("the $name row leaves (touch at $fx, $fy)", false, exists(rowTag))
            composeRule.onNodeWithText(name).assertDoesNotExist()
            assertEquals("nothing is deleted while Undo is up", emptyList<Long>(), offlineMapRepository.deletedIds)
            assertEquals("the touch reached the button, not the row's details tap", false, exists(RECORD_DETAILS_SHEET_TAG))

            touchUndo()
            assertEquals("Undo brings the $name row back", true, exists(rowTag))
            composeRule.onNodeWithText(name).assertExists()
        }

        button(regionId).performScrollTo().performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(SNACKBAR_LONG_MILLIS / 2)
        composeRule.waitForIdle()
        assertEquals("not before the snackbar ends", emptyList<Long>(), offlineMapRepository.deletedIds)
        letSnackbarTimeOut()
        assertEquals("the ended snackbar deletes that map, once", listOf(regionId), offlineMapRepository.deletedIds)
        composeRule.onNodeWithText(name).assertDoesNotExist()
    }

    // ── Records > Offline maps (the Offline maps panel) ──

    @Test
    fun `on the Offline maps chip, touching a row's trash button anywhere on it pends the delete with Undo, and an ended snackbar deletes the map`() {
        setScreen(RecordsSubTab.OFFLINE_MAPS)
        assertButtonPendsWithUndoAndCommitsOnTimeout(
            regionId = 7L,
            name = "Ridge",
            snackbar = "Offline map deleted · used in 2 journal entries",
            rowTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, "7"),
        )
        composeRule.onNodeWithText("Saddle").assertExists()
    }

    @Test
    fun `on the Offline maps chip, the second row's trash button deletes the second map, not the first`() {
        setScreen(RecordsSubTab.OFFLINE_MAPS)
        assertButtonPendsWithUndoAndCommitsOnTimeout(
            regionId = 8L,
            name = "Saddle",
            snackbar = "Offline map deleted",
            rowTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, "8"),
        )
        composeRule.onNodeWithText("Ridge").assertExists()
    }

    // ── Records > All (the logbook) ──

    @Test
    fun `in All, touching an offline map row's trash button anywhere on it pends the delete with Undo, and an ended snackbar deletes the map`() {
        setScreen(RecordsSubTab.ALL)
        assertButtonPendsWithUndoAndCommitsOnTimeout(
            regionId = 7L,
            name = "Ridge",
            snackbar = "Offline map deleted · used in 2 journal entries",
            rowTag = logbookRowTag(RecordType.OFFLINE_MAPS, "7"),
        )
        assertEquals(true, exists(logbookRowTag(RecordType.OFFLINE_MAPS, "8")))
    }

    @Test
    fun `in All, the second offline map row's trash button deletes the second map, not the first`() {
        setScreen(RecordsSubTab.ALL)
        assertButtonPendsWithUndoAndCommitsOnTimeout(
            regionId = 8L,
            name = "Saddle",
            snackbar = "Offline map deleted",
            rowTag = logbookRowTag(RecordType.OFFLINE_MAPS, "8"),
        )
        assertEquals(true, exists(logbookRowTag(RecordType.OFFLINE_MAPS, "7")))
    }

    // ── A swiped-open row (Amendment 3, RECORD -1828; the owner: "Hide trash while open (Recommended)") ──

    /**
     * A real short swipe opens region [regionId]'s row: its red Delete shows and its trash button is gone. A real touch on the
     * row's body closes it: the trash comes back, the red Delete goes, and nothing is pending or deleted.
     */
    private fun assertTrashHiddenWhileOpen(regionId: Long, name: String) {
        val rowTag = swipeToDeleteTag(RecordType.OFFLINE_MAPS, regionId.toString())
        repeat(2) { attempt ->
            composeRule.onNodeWithTag(rowTag).performScrollTo().performTouchInput {
                val startX = right - 8f
                swipe(start = Offset(startX, centerY), end = Offset(startX - 64.dp.toPx(), centerY), durationMillis = 600)
            }
            composeRule.waitForIdle()
            composeRule.onNodeWithTag(twoStageSwipeDeleteTag(rowTag)).assertIsDisplayed()
            assertEquals("the $name row's trash is hidden while it is open (attempt $attempt)", false, exists(offlineRegionDeleteButtonTag(regionId)))

            composeRule.onNodeWithTag(rowTag).performTouchInput { click(Offset(width * 0.2f, centerY)) }
            composeRule.waitForIdle()
            assertEquals("the red Delete goes when the $name row closes", false, exists(twoStageSwipeDeleteTag(rowTag)))
            button(regionId).assertIsDisplayed()
            composeRule.onNodeWithText(name).assertExists()
        }
        composeRule.onNodeWithText("Offline map deleted", substring = true).assertDoesNotExist()
        letSnackbarTimeOut()
        assertEquals("opening and closing a row deletes nothing", emptyList<Long>(), offlineMapRepository.deletedIds)
    }

    @Test
    fun `on the Offline maps chip, a swiped-open row shows only the red Delete, and its trash comes back when it closes`() {
        setScreen(RecordsSubTab.OFFLINE_MAPS)
        assertTrashHiddenWhileOpen(regionId = 7L, name = "Ridge")
        assertTrashHiddenWhileOpen(regionId = 8L, name = "Saddle")
    }

    @Test
    fun `in All, a swiped-open offline map row shows only the red Delete, and its trash comes back when it closes`() {
        setScreen(RecordsSubTab.ALL)
        assertTrashHiddenWhileOpen(regionId = 7L, name = "Ridge")
        assertTrashHiddenWhileOpen(regionId = 8L, name = "Saddle")
    }

    // ── The screen-reader label (proposed; the owner approves the wording before merge) ──

    @Test
    fun `each row's trash button is announced as Delete and the map's name, on both screens`() {
        for (chip in listOf(RecordsSubTab.OFFLINE_MAPS, RecordsSubTab.ALL)) {
            if (chip == RecordsSubTab.ALL) {
                composeRule.onNodeWithTag(recordsFilterChipTestTag(chip)).performScrollTo().performClick()
                composeRule.waitForIdle()
            } else {
                setScreen(chip)
            }
            for ((id, name) in listOf(7L to "Ridge", 8L to "Saddle")) {
                button(id).performScrollTo().assert(hasContentDescription("Delete $name")).assert(hasClickAction())
            }
        }
    }
}

private const val SNACKBAR_LONG_MILLIS = 10_000L
private val OMD_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }
