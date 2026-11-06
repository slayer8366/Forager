@file:OptIn(ExperimentalMaterial3Api::class)

package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.onAllNodesWithTag
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.WaypointDecision
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.ENTRY_DELETE_DIALOG_TAG
import com.zynergylabs.forager.app.ui.log.ENTRY_OVERFLOW_MENU_TAG
import com.zynergylabs.forager.app.ui.log.RECORD_DETAILS_SHEET_TAG
import com.zynergylabs.forager.app.ui.log.trackTileTag
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag
import com.zynergylabs.forager.app.ui.map.CENTRE_PIN_CONFIRM_ROW_TAG
import com.zynergylabs.forager.app.ui.map.MAP_BUBBLE_DETAILS_TAG
import com.zynergylabs.forager.app.ui.map.MAP_CHROME_OVER_MAP_ALPHA
import com.zynergylabs.forager.app.ui.map.MapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.MapChromeContentColor
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.theme.ForagerTheme
import com.zynergylabs.forager.app.ui.track.RecordingNotice
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/*
 * Map chrome at 80% where it covers a map, solid elsewhere: dispatch 2026-09-28-56 as amended by
 * continuation -58, with planner message -77's rulings.
 *
 * - Every surface is reached through the real screen (`AvailabilityScreen`, in the app's own theme,
 *   dark) and its real entry point: a real touch on a glyph, a bubble's Details, the + disc and its
 *   chooser, the bottom bar or rail, the search field, a Records row, the report's overflow button, a
 *   Back press. Getting to a screen (a tab, the Records switch, a chip) may be a semantic click.
 * - Each surface exposes the container colour it was given and the content colour inside it through
 *   semantics (`MapChromeContainerColor`, `MapChromeContentColor`), as the Layers sheet does; the
 *   expected colours are the theme's own roles, read in the same composition.
 * - Over a map: the container is its default role at `MAP_CHROME_OVER_MAP_ALPHA` and the content is
 *   the role's own content colour, opaque. Elsewhere: the container is the role, solid.
 * - "Covers a map" is -77's Q2: a map is drawn on screen beneath the surface. Q4: on the compact Maps
 *   tab, surfaces follow the tab. Q1, as the owner's "1 A" in -104 changed it: the Records details
 *   sheet is at 0.8 from the Offline maps sub-tab only in a short landscape window. Q3: the species suggestions and the Month menu are at 0.8 on the Maps tab, stacking
 *   over the 0.8 search panel.
 */

private typealias ChromeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** The theme's own roles, read inside the screen's composition, that each surface's colours are checked against. */
internal class MapChromeRoles {
    var sheet: Color = Color.Unspecified
    var surface: Color = Color.Unspecified
    var onSurface: Color = Color.Unspecified
    var errorContainer: Color = Color.Unspecified
    var onErrorContainer: Color = Color.Unspecified
    var menu: Color = Color.Unspecified
    var dialog: Color = Color.Unspecified
    var dialogText: Color = Color.Unspecified
    var datePicker: Color = Color.Unspecified
    var snackbar: Color = Color.Unspecified
    var snackbarContent: Color = Color.Unspecified
    var drawer: Color = Color.Unspecified
    /** C1: the navigation bar's container colour, the one token every role below now equals. */
    var token: Color = Color.Unspecified

    @Composable
    fun Capture() {
        // C1 (dispatch 2026-09-28-210). The owner, verbatim: "The map chrome isn't aligned. The search panel and map icon
        // bar are the wrong color. Have them be the same color as the bottom app navigation bar. Make sure any other pop
        // up or bubble, or the tool panel, is the same color as the app navigation bar also please". So the roles below
        // that were the sheet, the surface, the dialog, the date picker, the snackbar and the drawer roles are now the
        // token, `surfaceContainer`; each was `BottomSheetDefaults.ContainerColor` (surfaceContainerLow),
        // `colorScheme.surface`, `AlertDialogDefaults.containerColor` (surfaceContainerHigh), `DatePickerDefaults`'
        // container (surfaceContainerHigh), `SnackbarDefaults.color` (inverseSurface) and `DrawerDefaults.modalContainerColor`
        // (surfaceContainerLow). The snackbar's content colour becomes the navigation bar's own (`onSurfaceVariant`),
        // because `inverseOnSurface` on the token fails contrast (ChromeContrastTest). No alpha assertion changes.
        token = MaterialTheme.colorScheme.surfaceContainer
        sheet = token
        surface = token
        onSurface = MaterialTheme.colorScheme.onSurface
        errorContainer = MaterialTheme.colorScheme.errorContainer
        onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
        menu = MenuDefaults.containerColor
        dialog = token
        dialogText = AlertDialogDefaults.textContentColor
        datePicker = token
        snackbar = token
        snackbarContent = MaterialTheme.colorScheme.onSurfaceVariant
        drawer = token
    }
}

/** What the screen is handed; each field is state, so a test can change it after the screen is up. */
internal class MapChromeScreenState {
    var ui by mutableStateOf(AvailabilityUiState(offlineRegions = listOf(BUBBLE_REGION)))
    var isReturning by mutableStateOf(false)
    var tripStartWarning by mutableStateOf<RecordingNotice?>(null)
    var cartography by mutableStateOf(CartographyUiState())
    var entryMapData: CartographyEntryMapData = NO_ENTRY_MAP
    var returnToMapRequest by mutableStateOf(0)
    var openBackupRequest by mutableStateOf(0)
    val downloadedAgain = mutableListOf<Long>()
    var backup by mutableStateOf(com.zynergylabs.forager.app.ui.backup.BackupControls())
}

private val NO_ENTRY_MAP = CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())

/**
 * The real screen in the app's own theme (dark, the theme of the owner's screenshots), with a search
 * field that answers any query of three or more letters with "no matches" (so the suggestions open),
 * and the roles captured beside it.
 */
@Composable
internal fun MapChromeTestScreen(
    state: MapChromeScreenState,
    map: BubbleMapSlot,
    roles: MapChromeRoles,
    // Continuation 2026-09-29-12: a test that records the render modes of every map brings its own slot.
    mapSlotOverride: com.zynergylabs.forager.app.ui.map.MapSlot? = null,
    // Dispatch 2026-09-28-137: something drawn over the screen, as MainActivity draws the restore's loading page.
    overlay: @Composable () -> Unit = {},
    // C1 (dispatch 2026-09-28-210): the colour tests run both themes; every earlier caller is dark.
    darkTheme: Boolean = true,
) {
    ForagerTheme(darkTheme = darkTheme) {
        roles.Capture()
        AvailabilityScreen(
            uiState = state.ui,
            searchInputs = AvailabilityScreenSearchInputs(
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onTaxonSearchQueryChanged = { query ->
                    state.ui = state.ui.copy(taxonSearchQuery = query, taxonSearchHasNoResults = query.trim().length >= 3)
                },
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = { state.ui = state.ui.copy(taxonSearchHasNoResults = false) },
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
            ),
            settings = AvailabilityScreenSettings(
                onNightModeMapsChanged = {},
                onThemeModeChanged = {},
            ),
            mapSlot = mapSlotOverride ?: map.slot,
            records = AvailabilityScreenRecords(
                waypoints = listOf(BUBBLE_WAYPOINT),
                tracks = listOf(BUBBLE_TRACK),
            ),
            navigation = AvailabilityScreenNavigation(
                isReturning = state.isReturning,
            ),
            recording = AvailabilityScreenRecording(
                tripStartWarning = state.tripStartWarning,
            ),
            journalEntries = AvailabilityScreenJournalEntries(
                cartographyUiState = state.cartography,
            ),
            entryReportData = AvailabilityScreenEntryReportData(
                getCartographyEntryMapData = { _, _ -> state.entryMapData },
            ),
            backupAndImport = AvailabilityScreenBackupAndImport(
                backup = state.backup,
                returnToMapRequest = state.returnToMapRequest,
                openBackupRequest = state.openBackupRequest,
            ),
            offlineMaps = AvailabilityScreenOfflineMaps(
                onOfflineMapLatChanged = {},
                onOfflineMapLngChanged = {},
                onOfflineMapRadiusChanged = {},
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onDownloadAgain = { state.downloadedAgain += it },
            ),
        )
        overlay()
    }
}

/** The track sheet's five tiles (dispatch -677), by label. */
private val TRACK_SHEET_TILE_LABELS = listOf("Distance", "Time", "Climb", "Descent", "Moving speed")

internal fun mapChromeHostActivityRule() = object : ExternalResource() {
    override fun before() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
    }
}

private fun ChromeRule.touchCentreOf(tag: String) {
    onNodeWithTag(tag, useUnmergedTree = true).performTouchInput { click(center) }
    waitForIdle()
}

private fun ChromeRule.touchText(text: String) {
    onNodeWithText(text).performTouchInput { click(center) }
    waitForIdle()
}

private fun ChromeRule.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

/** The one value of [key] on the node tagged [tag] (the key is set beside the tag). */
private fun ChromeRule.colourOn(tag: String, key: SemanticsPropertyKey<Color>): Color {
    val nodes = onAllNodes(hasTestTag(tag) and SemanticsMatcher.keyIsDefined(key), useUnmergedTree = true).fetchSemanticsNodes()
    assertEquals("one node tagged $tag carries ${key.name}", 1, nodes.size)
    return nodes.single().config[key]
}

/** The content colour on [tag]'s node or on the one descendant of it that carries one. */
private fun ChromeRule.contentColourIn(tag: String): Color {
    val nodes = onAllNodes(
        (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))) and SemanticsMatcher.keyIsDefined(MapChromeContentColor),
        useUnmergedTree = true,
    ).fetchSemanticsNodes()
    assertEquals("one node in $tag carries its content colour", 1, nodes.size)
    return nodes.single().config[MapChromeContentColor]
}

/** Over a map: [tag]'s container is [role] at the map chrome's alpha, and its content is [content], opaque. */
private fun ChromeRule.assertOverMap(tag: String, role: Color, content: Color) {
    val container = colourOn(tag, MapChromeContainerColor)
    assertEquals("$tag: container alpha", MAP_CHROME_OVER_MAP_ALPHA, container.alpha, 0.002f)
    assertEquals("$tag: container colour aside from alpha", role.copy(alpha = 1f), container.copy(alpha = 1f))
    val inside = contentColourIn(tag)
    assertEquals("$tag: content alpha", 1f, inside.alpha, 0.002f)
    assertEquals("$tag: content colour", content, inside)
}

/** Where no map is drawn beneath it: [tag]'s container is [role], solid. */
private fun ChromeRule.assertSolid(tag: String, role: Color) {
    val container = colourOn(tag, MapChromeContainerColor)
    assertEquals("$tag: container alpha", 1f, container.alpha, 0.002f)
    assertEquals("$tag: container colour", role, container)
}

/** A waypoint, a track and a region glyph, one below the other from ([x], [firstY]), [step] apart. */
private fun chromeGlyphs(x: Dp, firstY: Dp, step: Dp) = listOf(
    StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, x, firstY, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng)),
    StubGlyph(MapLayerIds.KEPT_TRACKS, BUBBLE_TRACK.id, x, firstY + step, LatLng(45.505, -122.6)),
    StubGlyph(MapLayerIds.OFFLINE_REGION_OUTLINE, BUBBLE_REGION.id.toString(), x, firstY + step * 2, LatLng(45.6, -122.67)),
)

// ---------------------------------------------------------------------------------------------------
// The compact Maps tab (M1) and the tabs beside it, in portrait and in a short landscape window.
// ---------------------------------------------------------------------------------------------------

abstract class MapChromeCompactTests(glyphX: Dp, glyphY: Dp, glyphStep: Dp) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(chromeGlyphs(glyphX, glyphY, glyphStep))
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    private fun setScreen() {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
    }

    private fun openDetailsFrom(glyphId: String) {
        composeRule.touchCentreOf(glyphTag(glyphId))
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
    }

    private fun openTab(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.waitForIdle()
    }

    private fun openChooserAndPick(option: String) {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.touchText(option)
    }

    @Test
    fun `a waypoint's details sheet opened from its bubble on the Maps tab is at the map chrome's alpha`() {
        setScreen()
        openDetailsFrom(BUBBLE_WAYPOINT.id)
        composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
    }

    @Test
    fun `a track's details sheet opened from its bubble on the Maps tab is at the map chrome's alpha`() {
        setScreen()
        openDetailsFrom(BUBBLE_TRACK.id)
        composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
    }

    /**
     * Dispatch 2026-09-28-677 (data part B): the track sheet's tiles, over a map, are outlined with no fill of their own, so
     * the sheet's container stays the one fill between the map and the eye (layered fills composite; CLAUDE.md, "Nothing
     * fully obstructs the map view"). Every tile is checked, so one left filled cannot pass.
     */
    @Test
    fun `a track's details sheet opened from its bubble draws its tiles with no fill of their own`() {
        setScreen()
        openDetailsFrom(BUBBLE_TRACK.id)
        TRACK_SHEET_TILE_LABELS.forEach { label ->
            assertEquals("$label tile: container", Color.Transparent, composeRule.colourOn(trackTileTag(label), MapChromeContainerColor))
        }
    }

    @Test
    fun `an offline region's details sheet opened from its bubble on the Maps tab is at the map chrome's alpha`() {
        setScreen()
        openDetailsFrom(BUBBLE_REGION.id.toString())
        composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
    }

    @Test
    fun `the centre-pin confirm row over the Maps tab's map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Waypoint")
        composeRule.assertOverMap(CENTRE_PIN_CONFIRM_ROW_TAG, roles.surface, roles.onSurface)
    }

    @Test
    fun `the waypoint name dialog over the Maps tab's map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Waypoint")
        composeRule.touchText("OK")
        composeRule.assertOverMap(WAYPOINT_NAME_DIALOG_TAG, roles.surface, roles.onSurface)
    }

    @Test
    fun `the trip date dialog over the Maps tab's map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Trip")
        composeRule.touchText("OK")
        composeRule.assertOverMap(TRIP_DATE_DIALOG_TAG, roles.datePicker, roles.onSurface)
    }

    /**
     * The picker inside the dialog paints its own container (material3 1.5.0-alpha26), which would stack
     * on the dialog's to about 0.96; over a map it is clear, so the dialog's one fill is what the map
     * shows through (CLAUDE.md, UX defaults: layered fills composite). Added after the forward change,
     * with the picker's seam; its evidence is a revert check, not a tests-first run.
     */
    @Test
    fun `the trip date dialog's picker draws no fill of its own over the dialog's`() {
        setScreen()
        openChooserAndPick("Trip")
        composeRule.touchText("OK")
        assertEquals("trip-date-picker: container", Color.Transparent, composeRule.colourOn(TRIP_DATE_PICKER_TAG, MapChromeContainerColor))
    }

    @Test
    fun `the search notice over the Maps tab's map is at the map chrome's alpha`() {
        state.ui = state.ui.copy(errorMessage = "Test notice")
        setScreen()
        composeRule.assertOverMap(SEARCH_NOTICE_TAG, roles.errorContainer, roles.onErrorContainer)
    }

    @Test
    fun `the search notice on the List tab stays solid`() {
        state.ui = state.ui.copy(errorMessage = "Test notice")
        setScreen()
        openTab("List")
        composeRule.assertSolid(SEARCH_NOTICE_TAG, roles.errorContainer)
    }

    @Test
    fun `the species suggestions typed on the Maps tab are at the map chrome's alpha`() {
        setScreen()
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performTextInput("zzz")
        composeRule.waitForIdle()
        composeRule.assertOverMap(TAXON_SUGGESTIONS_MENU_TAG, roles.menu, roles.onSurface)
    }

    @Test
    fun `the species suggestions typed on the List tab stay solid`() {
        setScreen()
        openTab("List")
        composeRule.onNodeWithTag(ACTIVE_SEARCH_SUMMARY_TAG).performTextInput("zzz")
        composeRule.waitForIdle()
        composeRule.assertSolid(TAXON_SUGGESTIONS_MENU_TAG, roles.menu)
    }

    private fun openMonthMenu() {
        composeRule.touchCentreOf(ACTIVE_SEARCH_SUMMARY_TAG)
        composeRule.onNodeWithText("Month").performScrollTo()
        composeRule.waitForIdle()
        composeRule.touchText("Month")
    }

    @Test
    fun `the Month menu from the search panel on the Maps tab is at the map chrome's alpha`() {
        setScreen()
        openMonthMenu()
        composeRule.assertOverMap(MONTH_MENU_TAG, roles.menu, roles.onSurface)
    }

    @Test
    fun `the Month menu from the search panel on the List tab stays solid`() {
        setScreen()
        openTab("List")
        openMonthMenu()
        composeRule.assertSolid(MONTH_MENU_TAG, roles.menu)
    }

    @Test
    fun `a snackbar over the Maps tab is at the map chrome's alpha`() {
        setScreen()
        state.tripStartWarning = RecordingNotice(1, "Test warning")
        composeRule.waitForIdle()
        composeRule.assertOverMap(COMPACT_SNACKBAR_TAG, roles.snackbar, roles.snackbarContent)
    }

    @Test
    fun `a snackbar over the List tab stays solid`() {
        setScreen()
        openTab("List")
        state.tripStartWarning = RecordingNotice(1, "Test warning")
        composeRule.waitForIdle()
        composeRule.assertSolid(COMPACT_SNACKBAR_TAG, roles.snackbar)
    }

    @Test
    fun `the Tools drawer over the Maps tab is at the map chrome's alpha`() {
        setScreen()
        openTab("Tools")
        composeRule.assertOverMap(TOOLS_DRAWER_SHEET_TAG, roles.drawer, roles.onSurface)
    }

    @Test
    fun `the Tools drawer over the List tab stays solid`() {
        setScreen()
        openTab("List")
        openTab("Tools")
        composeRule.assertSolid(TOOLS_DRAWER_SHEET_TAG, roles.drawer)
    }

    /**
     * -77, Q4: on the Maps tab with no map drawn yet (sightings loading), a surface follows the tab. A
     * snackbar, because the Maps tab's own bottom bar or rail, and so Tools, is composed inside the map's
     * Box and is not there while sightings load (first run of this test, through the drawer, at b019080).
     */
    @Test
    fun `a snackbar over the Maps tab while sightings load follows the tab, at the map chrome's alpha`() {
        state.ui = state.ui.copy(isLoadingSightings = true)
        setScreen()
        state.tripStartWarning = RecordingNotice(1, "Test warning")
        composeRule.waitForIdle()
        composeRule.assertOverMap(COMPACT_SNACKBAR_TAG, roles.snackbar, roles.snackbarContent)
    }

    @Test
    fun `the exit-navigation prompt raised by Back on the Maps tab is at the map chrome's alpha`() {
        state.isReturning = true
        setScreen()
        composeRule.back()
        composeRule.assertOverMap(EXIT_NAVIGATION_PROMPT_TAG, roles.dialog, roles.dialogText)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeCompactPortraitTest : MapChromeCompactTests(60.dp, 380.dp, 120.dp)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeCompactShortLandscapeTest : MapChromeCompactTests(420.dp, 150.dp, 50.dp)

// ---------------------------------------------------------------------------------------------------
// A day entry's report (M3) on the compact Journal tab: its overflow menu and delete dialog cover the
// entry's map when it has one, and its map's bubbles open the details sheet over it.
// ---------------------------------------------------------------------------------------------------

private val CHROME_ENTRY: CartographyEntry = CartographyEntry.draft(id = "entry-1", date = BUBBLE_DAY, updatedAtEpochMillis = 1_000L).copy(
    isDraft = false,
    text = "A wet morning on the ridge.",
    waypointDecisions = listOf(WaypointDecision(BUBBLE_WAYPOINT.id, BUBBLE_WAYPOINT.name, BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng, kept = true)),
)

/** The entry's map data: its kept waypoint, so the entry has a map. */
private val CHROME_ENTRY_MAP = CartographyEntryMapData(
    trackPolylines = emptyList(),
    findMarkers = emptyList(),
    waypointMarkers = listOf(RecordPoint(BUBBLE_WAYPOINT.id, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))),
    photoMarkers = emptyList(),
    offlineRegionCircles = emptyList(),
)

abstract class MapChromeEntryReportTests {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(listOf(StubGlyph(MapLayerIds.WAYPOINTS, BUBBLE_WAYPOINT.id, 60.dp, 60.dp, LatLng(BUBBLE_WAYPOINT.lat, BUBBLE_WAYPOINT.lng))))
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    /** The Journal tab with the entry open in its report, with or without a map. */
    private fun setScreen(withMap: Boolean) {
        state.cartography = CartographyUiState(entries = listOf(CHROME_ENTRY), editingEntry = CHROME_ENTRY)
        state.entryMapData = if (withMap) CHROME_ENTRY_MAP else NO_ENTRY_MAP
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("A wet morning on the ridge.").assertExists()
    }

    private fun openOverflow() {
        composeRule.onNodeWithContentDescription("Entry options").performTouchInput { click(center) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the report's overflow menu over the entry's map is at the map chrome's alpha`() {
        setScreen(withMap = true)
        openOverflow()
        composeRule.assertOverMap(ENTRY_OVERFLOW_MENU_TAG, roles.menu, roles.onSurface)
    }

    @Test
    fun `the report's overflow menu on an entry with no map stays solid`() {
        setScreen(withMap = false)
        openOverflow()
        composeRule.assertSolid(ENTRY_OVERFLOW_MENU_TAG, roles.menu)
    }

    @Test
    fun `the delete dialog over the entry's map is at the map chrome's alpha`() {
        setScreen(withMap = true)
        openOverflow()
        composeRule.touchText("Delete entry")
        composeRule.assertOverMap(ENTRY_DELETE_DIALOG_TAG, roles.dialog, roles.dialogText)
    }

    @Test
    fun `the delete dialog on an entry with no map stays solid`() {
        setScreen(withMap = false)
        openOverflow()
        composeRule.touchText("Delete entry")
        composeRule.assertSolid(ENTRY_DELETE_DIALOG_TAG, roles.dialog)
    }

    @Test
    fun `a waypoint's details sheet opened from its bubble on the entry's map is at the map chrome's alpha`() {
        setScreen(withMap = true)
        composeRule.touchCentreOf(glyphTag(BUBBLE_WAYPOINT.id))
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeEntryReportPortraitTest : MapChromeEntryReportTests()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeEntryReportShortLandscapeTest : MapChromeEntryReportTests()

// ---------------------------------------------------------------------------------------------------
// The Records details sheet (-77 Q1 (b), superseded by the owner's "1 A" in -104): at 0.8 from the Offline
// maps sub-tab only in a short landscape window, where the panel's picker map is beside the list, and solid
// from every other sub-tab and window, in the Journal.
// ---------------------------------------------------------------------------------------------------

abstract class MapChromeRecordsTests(private val pickerMapBesideList: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    /** Records, on the chip for [subTab]. */
    private fun openRecords(subTab: RecordsSubTab) {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(subTab)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /** The details are solid, not at the map chrome's alpha: in the Journal they are a sheet. */
    private fun assertDetailsSolid() {
        composeRule.assertSolid(RECORD_DETAILS_SHEET_TAG, roles.sheet)
    }

    /** A real touch on [rowTag], upper middle, after scrolling it into view. */
    private fun touchRow(rowTag: String) {
        composeRule.onNodeWithTag(rowTag).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(rowTag).performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.4f, height * 0.25f)) }
        composeRule.waitForIdle()
    }

    // Owner "1 A" (dispatch 2026-09-28-104, superseding -77's Q1 (b)): the sheet opened from the Offline
    // maps panel is at 0.8 only where the picker map is beside it (short landscape) and solid where it
    // lies over the region list (portrait, where the panel is stacked).
    @Test
    fun `an offline region's details sheet from the Offline maps sub-tab is at the map chrome's alpha only where the picker map is beside it`() {
        openRecords(RecordsSubTab.OFFLINE_MAPS)
        touchRow("records-swipe-offline-maps-${BUBBLE_REGION.id}")
        if (pickerMapBesideList) {
            composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
        } else {
            assertDetailsSolid()
        }
    }

    @Test
    fun `an offline region's details sheet from All stays solid`() {
        openRecords(RecordsSubTab.ALL)
        touchRow("records-swipe-offline-maps-${BUBBLE_REGION.id}")
        assertDetailsSolid()
    }

    @Test
    fun `a waypoint's details sheet from the Waypoints sub-tab stays solid`() {
        openRecords(RecordsSubTab.WAYPOINTS)
        touchRow("records-swipe-waypoints-${BUBBLE_WAYPOINT.id}")
        assertDetailsSolid()
    }

    @Test
    fun `a track's details sheet from the Tracks sub-tab stays solid`() {
        openRecords(RecordsSubTab.RECORDED_TRACKS)
        touchRow("track-row-${BUBBLE_TRACK.id}")
        assertDetailsSolid()
    }

    /** Dispatch -677: off a map the track sheet's tiles are filled, as the entry report's are; the outline is for over a map only. */
    @Test
    fun `a track's details sheet from the Tracks sub-tab fills its tiles`() {
        openRecords(RecordsSubTab.RECORDED_TRACKS)
        touchRow("track-row-${BUBBLE_TRACK.id}")
        TRACK_SHEET_TILE_LABELS.forEach { label ->
            val fill = composeRule.colourOn(trackTileTag(label), MapChromeContainerColor)
            assertEquals("$label tile: filled, solid", 1f, fill.alpha, 0.002f)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeRecordsPortraitTest : MapChromeRecordsTests(pickerMapBesideList = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeRecordsShortLandscapeTest : MapChromeRecordsTests(pickerMapBesideList = true)
