package com.zynergylabs.forager.app.ui.availability

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GpxImportFailure
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.importgpx.GpxImportNotice
import com.zynergylabs.forager.app.ui.map.MapSlot
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
import org.robolectric.shadows.ShadowToast

/**
 * Plan T16: where Forager lands after an import, from the in-app button or from Open with or Share (both
 * reach the screen as a [GpxImportNotice]). Driven through the real [AvailabilityScreen] from the Maps tab,
 * where the app opens: the Journal comes up on Records, the Tracks chip is selected, and the first new
 * track's details are open; several tracks add the note "Imported N tracks"; a failure says the owner's
 * words and leaves the person on the Tracks list, where "Import GPX" is. The notice is handed back once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenGpxImportTest {

    private val composeRule = createComposeRule()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(declareHostActivity).around(composeRule)

    private val points = listOf(TrackPoint(45.5, -122.6, null, null, 1_000_000L), TrackPoint(45.6, -122.6, null, null, 1_060_000L))
    private val imported = listOf(
        Track("imp-1", "Day one", 1_000_000L, 1_060_000L, points, importedAtEpochMillis = 2_000_000L),
        Track("imp-2", "Day two", 1_000_000L, 1_060_000L, points, importedAtEpochMillis = 2_000_000L),
        Track("imp-3", "Day three", 1_000_000L, 1_060_000L, points, importedAtEpochMillis = 2_000_000L),
    )

    private val shownSeqs = mutableListOf<Long>()
    private var notice by mutableStateOf<GpxImportNotice?>(null)

    private fun setScreen() {
        composeRule.setContent { GpxImportScreenUnderTest(imported, notice) { seq -> shownSeqs += seq; if (notice?.seq == seq) notice = null } }
        composeRule.waitForIdle()
    }

    @Test
    fun `an import lands on Journal, Records, Tracks with the new track's details open`() {
        setScreen()
        notice = GpxImportNotice(seq = 1, outcome = GpxImportOutcome.Imported(listOf("imp-2")))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Records").assertIsSelected()
        composeRule.onNodeWithTag("records-chip-tracks").assertIsSelected()
        composeRule.onNodeWithTag("record-details-title").assertTextEquals("Day two")
        assertNull("one track simply opens, with no note", ShadowToast.getTextOfLatestToast())
        assertEquals(listOf(1L), shownSeqs)
    }

    @Test
    fun `a file with three tracks says Imported 3 tracks and opens the first`() {
        setScreen()
        notice = GpxImportNotice(seq = 1, outcome = GpxImportOutcome.Imported(listOf("imp-1", "imp-2", "imp-3")))
        composeRule.waitForIdle()

        assertEquals("Imported 3 tracks", ShadowToast.getTextOfLatestToast())
        composeRule.onNodeWithTag("record-details-title").assertTextEquals("Day one")
    }

    @Test
    fun `a failed import says why and leaves the Tracks list showing`() {
        setScreen()
        notice = GpxImportNotice(seq = 1, outcome = GpxImportOutcome.Failed(GpxImportFailure.ROUTE_ONLY))
        composeRule.waitForIdle()

        assertEquals("This file has a route but no track; routes aren't imported yet", ShadowToast.getTextOfLatestToast())
        composeRule.onNodeWithTag("records-chip-tracks").assertIsSelected()
        composeRule.onNodeWithTag("record-details-sheet").assertDoesNotExist()
        assertEquals(listOf(1L), shownSeqs)
    }
}

private val GPX_IMPORT_STUB_MAP: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

@Composable
private fun GpxImportScreenUnderTest(tracks: List<Track>, notice: GpxImportNotice?, onShown: (Long) -> Unit) {
    AvailabilityScreen(
        uiState = AvailabilityUiState(),
        searchInputs = AvailabilityScreenSearchInputs(
            onUseCurrentLocation = {},
            onManualLatChanged = {},
            onManualLngChanged = {},
            onSearchManualCoordinates = {},
            onRadiusChanged = {},
            onMonthSelected = {},
            onMapTabSelected = {},
            onSeasonalTabSelected = {},
            onTaxonSearchQueryChanged = {},
            onTaxonSearchResultSelected = {},
            onDismissTaxonSuggestions = {},
            onReopenTaxonSuggestions = {},
            onPlaceTripPin = { _, _, _ -> },
            onDeletePlannedTrip = {},
            onRecentSearchSelected = {},
        ),
        offlineMaps = AvailabilityScreenOfflineMaps(
            onOfflineMapLatChanged = {},
            onOfflineMapLngChanged = {},
            onOfflineMapRadiusChanged = {},
            onOfflineMapNameChanged = {},
            onOfflineMapsOpened = {},
            onDownloadOfflineMaps = {},
            onDeleteOfflineRegion = {},
        ),
        settings = AvailabilityScreenSettings(
            onNightModeMapsChanged = {},
            onThemeModeChanged = {},
        ),
        mapSlot = GPX_IMPORT_STUB_MAP,
        records = AvailabilityScreenRecords(
            tracks = tracks,
        ),
        backupAndImport = AvailabilityScreenBackupAndImport(
            gpxImportNotice = notice,
            onGpxImportNoticeShown = onShown,
            onGpxFilePicked = {},
        ),
    )
}
