@file:OptIn(ExperimentalMaterial3Api::class)

package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.test.onAllNodesWithTag
import com.zynergylabs.forager.app.ui.log.JOURNAL_DETAIL_PANE_TAG
import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarDefaults
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

    @Composable
    fun Capture() {
        sheet = BottomSheetDefaults.ContainerColor
        surface = MaterialTheme.colorScheme.surface
        onSurface = MaterialTheme.colorScheme.onSurface
        errorContainer = MaterialTheme.colorScheme.errorContainer
        onErrorContainer = MaterialTheme.colorScheme.onErrorContainer
        menu = MenuDefaults.containerColor
        dialog = AlertDialogDefaults.containerColor
        dialogText = AlertDialogDefaults.textContentColor
        datePicker = DatePickerDefaults.colors().containerColor
        snackbar = SnackbarDefaults.color
        snackbarContent = SnackbarDefaults.contentColor
        drawer = DrawerDefaults.modalContainerColor
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
) {
    ForagerTheme(darkTheme = true) {
        roles.Capture()
        AvailabilityScreen(
            uiState = state.ui,
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
            onOfflineMapLatChanged = {},
            onOfflineMapLngChanged = {},
            onOfflineMapRadiusChanged = {},
            onOfflineMapNameChanged = {},
            onOfflineMapsOpened = {},
            onDownloadOfflineMaps = {},
            onDeleteOfflineRegion = {},
            onNightModeMapsChanged = {},
            onThemeModeChanged = {},
            mapSlot = mapSlotOverride ?: map.slot,
            waypoints = listOf(BUBBLE_WAYPOINT),
            tracks = listOf(BUBBLE_TRACK),
            isReturning = state.isReturning,
            tripStartWarning = state.tripStartWarning,
            cartographyUiState = state.cartography,
            getCartographyEntryMapData = { _, _ -> state.entryMapData },
            backup = state.backup,
            returnToMapRequest = state.returnToMapRequest,
            openBackupRequest = state.openBackupRequest,
            onDownloadAgain = { state.downloadedAgain += it },
        )
        overlay()
    }
}

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
// from every other sub-tab and window, in the compact Journal and the wide drawer.
// ---------------------------------------------------------------------------------------------------

abstract class MapChromeRecordsTests(private val wide: Boolean, private val pickerMapBesideList: Boolean) {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(emptyList())
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    /** Records, in whichever tree the window gives, on the chip for [subTab]. */
    private fun openRecords(subTab: RecordsSubTab) {
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(if (wide) "Mushroom Log" else "Journal").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Records").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(recordsFilterChipTestTag(subTab)).performScrollTo().performClick()
        composeRule.waitForIdle()
    }

    /**
     * The details are solid, not at the map chrome's alpha. In the compact Journal they are a sheet; in
     * the wide tree they are the right side's detail pane (J6a, ruling 6.1: "The record details open in
     * the right side, not as a sheet"), whose container is `surface`.
     */
    private fun assertDetailsSolid() {
        if (wide) {
            composeRule.assertSolid(JOURNAL_DETAIL_PANE_TAG, roles.surface)
            assertEquals("the wide tree has no details sheet", 0, composeRule.onAllNodesWithTag(RECORD_DETAILS_SHEET_TAG).fetchSemanticsNodes().size)
        } else {
            composeRule.assertSolid(RECORD_DETAILS_SHEET_TAG, roles.sheet)
        }
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
    // lies over the region list (portrait, and the wide tree, whose panel is stacked as in portrait).
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
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w384dp-h823dp-xxhdpi")
class MapChromeRecordsPortraitTest : MapChromeRecordsTests(wide = false, pickerMapBesideList = false)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w823dp-h384dp-land-xxhdpi")
class MapChromeRecordsShortLandscapeTest : MapChromeRecordsTests(wide = false, pickerMapBesideList = true)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w840dp-h1024dp-mdpi")
class MapChromeRecordsWideTest : MapChromeRecordsTests(wide = true, pickerMapBesideList = false)

// ---------------------------------------------------------------------------------------------------
// The wide Maps results (M2), which show a map only once a region is searched. At w1280dp, as the M1
// bubble tests use: the drawer and the list pane take 720 dp.
// ---------------------------------------------------------------------------------------------------

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w1280dp-h900dp-mdpi")
class MapChromeWideTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(mapChromeHostActivityRule()).around(composeRule)

    private val map = BubbleMapSlot(chromeGlyphs(60.dp, 300.dp, 40.dp))
    private val state = MapChromeScreenState()
    private val roles = MapChromeRoles()

    /** The screen, with a searched region (so the results pane draws its map) when [searched]. */
    private fun setScreen(searched: Boolean = true) {
        if (searched) state.ui = state.ui.copy(region = Region(45.5, -122.6, 10))
        composeRule.setContent { MapChromeTestScreen(state, map, roles) }
        composeRule.waitForIdle()
    }

    private fun openChooserAndPick(option: String) {
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.touchText(option)
    }

    @Test
    fun `on the wide layout a waypoint's details sheet opened from its bubble is at the map chrome's alpha`() {
        setScreen()
        composeRule.touchCentreOf(glyphTag(BUBBLE_WAYPOINT.id))
        composeRule.touchCentreOf(MAP_BUBBLE_DETAILS_TAG)
        composeRule.assertOverMap(RECORD_DETAILS_SHEET_TAG, roles.sheet, roles.onSurface)
    }

    @Test
    fun `on the wide layout the three-way dialog over the map is at the map chrome's alpha`() {
        setScreen()
        composeRule.onNodeWithContentDescription("Plan a trip or log a find here").performTouchInput { click(center) }
        composeRule.waitForIdle()
        composeRule.assertOverMap(THREE_WAY_ACTION_DIALOG_TAG, roles.dialog, roles.dialogText)
    }

    @Test
    fun `on the wide layout the centre-pin confirm row over the map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Drop a waypoint")
        composeRule.assertOverMap(CENTRE_PIN_CONFIRM_ROW_TAG, roles.surface, roles.onSurface)
    }

    @Test
    fun `on the wide layout the waypoint name dialog over the map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Drop a waypoint")
        composeRule.touchText("OK")
        composeRule.assertOverMap(WAYPOINT_NAME_DIALOG_TAG, roles.surface, roles.onSurface)
    }

    @Test
    fun `on the wide layout the trip date dialog over the map is at the map chrome's alpha`() {
        setScreen()
        openChooserAndPick("Plan a trip")
        composeRule.touchText("OK")
        composeRule.assertOverMap(TRIP_DATE_DIALOG_TAG, roles.datePicker, roles.onSurface)
    }

    @Test
    fun `on the wide layout the trip date dialog's picker draws no fill of its own over the dialog's`() {
        setScreen()
        openChooserAndPick("Plan a trip")
        composeRule.touchText("OK")
        assertEquals("trip-date-picker: container", Color.Transparent, composeRule.colourOn(TRIP_DATE_PICKER_TAG, MapChromeContainerColor))
    }

    /**
     * J6c (the owner's item 5: "The tablet's separate Layers button and "+" are replaced by the bar's Layers
     * and "+" rows"): the wide map's own Layers button, which this test read at the map chrome's alpha, no
     * longer exists. Its replacement is a row of the icon cluster, the same `MapIconBar` and container the phone's
     * Maps tab draws, whose fill the compact tests guard. What is asserted here is what is left to say about the
     * wide map: the separate button is gone and the Layers control is the cluster's row. A direct alpha
     * assertion on that row is not made: the cluster's fill is not marked with `MapChromeContainerColor`.
     */
    @Test
    fun `on the wide layout the Layers control is the icon cluster's row, and the separate Layers button is gone`() {
        setScreen()
        assertEquals("the cluster is on the wide map", 1, composeRule.onAllNodesWithTag(MAP_ICON_CLUSTER_TAG).fetchSemanticsNodes().size)
        assertEquals("the separate Layers button is gone", 0, composeRule.onAllNodesWithTag("wide-layers-button").fetchSemanticsNodes().size)
    }

    @Test
    fun `on the wide layout the species suggestions over the searched map are at the map chrome's alpha`() {
        state.ui = state.ui.copy(taxonSearchQuery = "zzz", taxonSearchHasNoResults = true)
        setScreen(searched = true)
        composeRule.assertOverMap(TAXON_SUGGESTIONS_MENU_TAG, roles.menu, roles.onSurface)
    }

    @Test
    fun `on the wide layout the species suggestions before any search, with no map drawn, stay solid`() {
        state.ui = state.ui.copy(taxonSearchQuery = "zzz", taxonSearchHasNoResults = true)
        setScreen(searched = false)
        composeRule.assertSolid(TAXON_SUGGESTIONS_MENU_TAG, roles.menu)
    }

    @Test
    fun `on the wide layout the exit-navigation prompt over the searched map is at the map chrome's alpha`() {
        state.isReturning = true
        setScreen(searched = true)
        composeRule.back()
        composeRule.assertOverMap(EXIT_NAVIGATION_PROMPT_TAG, roles.dialog, roles.dialogText)
    }

    @Test
    fun `on the wide layout the exit-navigation prompt with no map drawn stays solid`() {
        state.isReturning = true
        setScreen(searched = false)
        composeRule.back()
        composeRule.assertSolid(EXIT_NAVIGATION_PROMPT_TAG, roles.dialog)
    }
}
