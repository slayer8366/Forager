package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import com.zynergylabs.forager.app.ui.log.RecordType
import com.zynergylabs.forager.app.ui.log.swipeToDeleteTag
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.MapMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
 * Two pieces of [AvailabilityScreen], measured headlessly:
 *
 * 1. The quick-fire mode icon over the map's own top-right corner, and that tapping it opens
 *    `MapModePicker` (removed 2026-10-19, RECORD -1392), and that tapping a mode chip there actually changes which [Basemap] the map
 *    slot receives — not just that an icon is somewhere on screen. Settings' old "Choose Maps
 *    Service" section is gone entirely — see [com.zynergylabs.forager.app.ui.map.MapMode]'s own doc comment for
 *    what superseded it — so this file no longer tests it.
 * 2. The "Offline Maps" sub-tab, reached through the Journal's Records tab (Journal restructure
 *    Stage 1 moved Offline Maps and Recorded Tracks out of Settings — see [com.zynergylabs.forager.app.ui.log.RecordsTab]):
 *    reachable regardless of the selected [com.zynergylabs.forager.app.ui.map.MapMode] (offline downloads always
 *    target a fixed source internally, so nothing about reaching it depends on the live mode
 *    selection — see `com.zynergylabs.forager.app.domain.OfflineMapRepository`'s doc comment), its navigation
 *    (a flat tab among Waypoints/Offline Maps/Recorded Tracks, left by tapping another tab rather
 *    than a back arrow), and picking a region by panning its
 *    [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker] map and confirming with OK, instead of typing
 *    coordinates.
 *
 * The map is stubbed, same reasoning as [AvailabilityScreenLayoutTest]: composing the real one
 * starts osmdroid. [CapturingMapSlot] backs *both* the main Map tab's map and the Offline Maps
 * submenu's picker map — both are mounted through the same [AvailabilityScreen.mapSlot] parameter,
 * and both can be composed at once (the main tab stays mounted behind the drawer while a submenu is
 * open), so the stub tells them apart by content: the picker always gets empty sightings/areas/
 * planned-trips, the main tab's ([SEARCHED_STATE]) doesn't.
 *
 * Not covered here, and not verifiable headlessly: the icon's and picker map's actual pixel
 * position/legibility over real map tiles — see README's "Not yet verified".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenSettingsPanelTest {

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

    private var capturedBasemap: Basemap? = null
    private var capturedOfflinePickerBasemap: Basemap? = null
    private var capturedNightMode: Boolean? = null
    private var capturedNightModeLoaded: Boolean? = null
    private var capturedOfflinePickerNightModeLoaded: Boolean? = null
    private var capturedThemeMode: AppThemeMode? = null
    private var capturedAutoSaveLocation: Boolean? = null

    /** See this class's doc comment for why the two map instances are told apart by content. */
    private val CapturingMapSlot: MapSlot = { _, content, renderMode, _, _, _, _, onCameraIdle, modifier ->
        if (content.sightings.isEmpty() && content.plannedTrips.isEmpty()) {
            capturedOfflinePickerBasemap = renderMode.basemap
            capturedOfflinePickerNightModeLoaded = renderMode.nightModeLoaded
            Column(modifier.testTag(OFFLINE_PICKER_MAP_TAG)) {
                Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
            }
        } else {
            capturedBasemap = renderMode.basemap
            capturedNightMode = renderMode.night
            capturedNightModeLoaded = renderMode.nightModeLoaded
            Box(modifier.testTag(MAP_SLOT_TAG))
        }
    }

    private fun setScreen(tracks: List<Track> = emptyList(), uiState: AvailabilityUiState = SEARCHED_STATE) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = uiState,
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
                mapSlot = CapturingMapSlot,
                records = AvailabilityScreenRecords(
                    tracks = tracks,
                ),
            )
        }
    }

    /**
     * Unlike [setScreen], wires the offline-map callbacks to real local state so panning and
     * confirming the picker map (see [CapturingMapSlot]) actually round-trips into
     * [AvailabilityUiState] the same way [AvailabilityViewModel]'s real
     * `onOfflineMapLatChanged`/`onOfflineMapLngChanged` do — needed for the "picking a region sets
     * it" test, which otherwise has nothing to observe.
     */
    private fun setScreenWithOfflineMapsState(initial: AvailabilityUiState = SEARCHED_STATE) {
        composeRule.setContent {
            var current by remember { mutableStateOf(initial) }
            AvailabilityScreen(
                uiState = current,
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
                    onOfflineMapLatChanged = { text -> current = current.copy(offlineMapLatText = text) },
                    onOfflineMapLngChanged = { text -> current = current.copy(offlineMapLngText = text) },
                    onOfflineMapRadiusChanged = { radius -> current = current.copy(offlineMapRadiusKm = radius) },
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                ),
                settings = AvailabilityScreenSettings(
                    onNightModeMapsChanged = { night -> current = current.copy(nightModeMaps = night) },
                    onAutoSaveLocationToPhotosChanged = { enabled ->
                        current = current.copy(autoSaveLocationToPhotos = enabled)
                        capturedAutoSaveLocation = enabled
                    },
                    onThemeModeChanged = { mode ->
                        current = current.copy(themeMode = mode)
                        capturedThemeMode = mode
                    },
                ),
                mapSlot = CapturingMapSlot,
            )
        }
    }

    /**
     * Settings moved one level deeper (map/navigation redesign dispatch B): no longer its own
     * bottom-nav tab, it's a sticky entry at the bottom of the "Tools" drawer's content (see
     * [CompactToolsDrawerContent]'s `showSettings` state and [SettingsEntryRow]). No
     * `performScrollTo()` needed: [SettingsEntryRow] sits outside [SearchControls]'s own internal
     * `verticalScroll` region, as a fixed sibling below it in the drawer's outer `Column` — that
     * outer Column has no scroll of its own, and `SearchControls`'s `Modifier.weight(1f)` sizes it
     * to exactly the remaining space, so the row is always laid out on screen at the Column's
     * bottom rather than scrolled out of view.
     */
    private fun openSettings() {
        composeRule.onNodeWithText("Tools").performClick()
        composeRule.onNodeWithText("Settings").performClick()
    }

    /**
     * Journal restructure Stage 1: Offline Maps and Recorded Tracks moved out of Settings into the
     * Journal's Records tab, alongside Waypoints — see [com.zynergylabs.forager.app.ui.log.RecordsTab]. Records
     * opens on its Waypoints sub-tab by default, so reaching either of the other two needs one more
     * tap on its own tab.
     */
    private fun openRecordsTab() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithText("Records").performClick()
    }

    // Journal redesign J1 (S3): the Offline maps and Tracks filter chips replaced the "Offline Maps"
    // and "Recorded Tracks" sub-tabs; Records opens on All. Offline maps is the last chip and can sit
    // past a phone's edge in the scrolling row, so it is scrolled into view first.
    private fun openOfflineMapsSubTab() {
        openRecordsTab()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.OFFLINE_MAPS)).performScrollTo().performClick()
    }

    private fun openRecordedTracksSubTab() {
        openRecordsTab()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
    }

    @Test
    // Was "…opens on the OpenTopoMap basemap…", asserting Basemap.OPEN_TOPO_MAP; the default became Street (dispatch
    // 2026-10-18-04, Amendment 2; RECORD -1297).
    fun `the map opens on the OpenStreetMap Street basemap, MapMode's default`() {
        setScreen()

        // Basemap.DEFAULT resolves through MapMode.DEFAULT (Street, via OpenStreetMap) —
        // see MapMode's own doc comment for why this changed from PR #13's USGS Topo default.
        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)
    }

    /**
     * Settings' "Night Maps" checkbox — a direct, persistent toggle
     * ([AvailabilityUiState.nightModeMaps]), replacing the map's earlier civil-twilight-automatic/
     * long-press-hold control (`MapNightMode`, deleted). Driven through the real checkbox row
     * rather than calling `onNightModeMapsChanged` directly, and asserts the map slot's own
     * [com.zynergylabs.forager.app.ui.map.MapRenderMode.night] actually flips, not just local Settings state.
     *
     * No trip through the Maps tab needed to read it back (unlike before map/navigation redesign
     * dispatch B, when Settings was its own bottom-nav tab and hid the Map tab's content entirely):
     * Settings now opens as a nested state inside the "Tools" drawer, which overlays whatever
     * `compactTab` is already showing rather than replacing it — [CompactMapTab] (and so
     * [CapturingMapSlot]'s "else" branch) stays composed underneath the whole time, since this test
     * never switches `compactTab` away from its own MAP default.
     */
    @Test
    fun `the Night Maps checkbox toggles night mode on the map`() {
        setScreenWithOfflineMapsState()

        assertEquals(false, capturedNightMode)

        openSettings()
        composeRule.onNodeWithText("Night Maps").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(true, capturedNightMode)

        composeRule.onNodeWithText("Night Maps").performClick()
        composeRule.waitForIdle()
        assertEquals(false, capturedNightMode)
    }

    /**
     * Colour build C1's cold-launch gate, through the real screen: the main map's [MapRenderMode]
     * carries [AvailabilityUiState.nightModeMapsLoaded] (the one hop the planner's ruling routes it
     * through, `AvailabilityScreen`'s `mapRenderMode`), so its style waits for the preference.
     */
    @Test
    fun `the main map is told the Night Maps preference has not loaded yet`() {
        setScreenWithOfflineMapsState(initial = SEARCHED_STATE.copy(nightModeMapsLoaded = false))
        composeRule.onNodeWithTag(MAP_SLOT_TAG).assertExists()

        assertEquals(false, capturedNightModeLoaded)
    }

    /**
     * Since dispatch 2026-09-28-708 the gate also waits for the stored basemap
     * ([AvailabilityUiState.mapMode]), so "loaded" here is both reads landed. The state gained
     * `mapMode` for that; the assertion is unchanged.
     */
    @Test
    fun `the main map is told once the Night Maps preference has loaded`() {
        setScreenWithOfflineMapsState(initial = SEARCHED_STATE.copy(nightModeMapsLoaded = true, mapMode = MapMode.DEFAULT))
        composeRule.onNodeWithTag(MAP_SLOT_TAG).assertExists()

        assertEquals(true, capturedNightModeLoaded)
    }

    /**
     * By the planner's ruling the centre-pin picker keeps [MapRenderMode.nightModeLoaded]'s default
     * `true`: it sits behind user navigation, so the preference is assumed (unverified) to have
     * loaded by the time it is reached. Asserted with the screen's own flag still `false`, so the
     * picker's `true` can only be the default, not the screen's value passed through.
     */
    @Test
    fun `the offline picker map keeps the default, not the gate`() {
        setScreen()
        openOfflineMapsSubTab()
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()

        assertEquals(false, SEARCHED_STATE.nightModeMapsLoaded)
        assertEquals(true, capturedOfflinePickerNightModeLoaded)
    }

    /**
     * Settings' Night Mode radio group (Light/Dark/System Default) — the app-wide theme
     * [com.zynergylabs.forager.app.ui.theme.ForagerTheme] renders, sitting directly above
     * [NightModeMapsSection]'s own checkbox (see that composable's doc comment) rather than the
     * other way around. Driven through the real radio rows, same reasoning as the Night Maps
     * checkbox test above. Unlike that one, this preference has no map-slot side channel to read
     * back through — [AvailabilityUiState.themeMode] only ever reaches [MainActivity] for
     * resolution against the device theme — so it's captured directly from
     * [AvailabilityScreen.onThemeModeChanged] instead.
     */
    @Test
    fun `the Night Mode radio group is above the Night Maps checkbox and selects the app theme`() {
        setScreenWithOfflineMapsState()
        openSettings()

        assertEquals(null, capturedThemeMode)

        composeRule.onNodeWithText("Dark").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.DARK, capturedThemeMode)

        composeRule.onNodeWithText("System Default").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.SYSTEM_DEFAULT, capturedThemeMode)

        composeRule.onNodeWithText("Light").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.LIGHT, capturedThemeMode)
    }

    private val mapModeContentDescription = "Layers: Street map. Choose the map type and overlays."

    /**
     * Was "...renders over the map's own top-right corner", asserting `iconBounds.top` strictly
     * above the map's vertical center. Re-derived, not re-run as-is: a mapicon-pill dispatch
     * dropped [MapIconBar] from 8 rows to 6 (record start/stop moved into `ControlPill`), and that
     * bar is `Alignment.CenterEnd` — shrinking its own row count moves every row's position closer
     * to true center, since [mapIconBarRowAnchorOffset] centers the whole row stack on the bar's
     * own midpoint. Measured directly against the tree (not assumed): row 4 of 6 (map mode)'s own
     * top edge now lands 2dp *below* the map's vertical center — a real flip from comfortably above
     * it at 8 rows, but only by 2dp out of a 512dp-tall map, too close to the line for a strict
     * above/below comparison in either direction to be a meaningful, stable assertion (a font-scale
     * or density difference could flip it back). The icon's own vertical *center*, not its top edge,
     * landing in the map's middle third is the invariant that actually survives this: "top-right
     * corner" no longer describes this icon's position, but "over the map, right side, roughly
     * centered rather than pinned to an edge" still does, and is what actually matters for a popover
     * anchor not to open jammed against the top or bottom edge. Re-derive again if the row count
     * changes further (the search row leaves next, per that same dispatch's own follow-up).
     */
    @Test
    fun `the quick-fire icon renders over the map's own right side, roughly vertically centered`() {
        setScreen()

        val iconBounds = composeRule
            .onNodeWithContentDescription(mapModeContentDescription)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val mapBounds = composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

        // Over the map, not beside it: the icon's horizontal center must fall within the map's own
        // width, and it sits in the map's middle third (vertically) and right half — see this
        // test's own doc comment for why "top-right corner" no longer holds exactly.
        val iconCenterX = iconBounds.left + iconBounds.width / 2
        assertTrue(
            "expected the icon's horizontal center ($iconCenterX) to fall inside the map's bounds " +
                "(${mapBounds.left}..${mapBounds.right})",
            iconCenterX in mapBounds.left..mapBounds.right,
        )
        val iconCenterY = iconBounds.top + iconBounds.height / 2
        val middleThirdStart = mapBounds.top + mapBounds.height / 3
        val middleThirdEnd = mapBounds.top + mapBounds.height * 2 / 3
        assertTrue(
            "expected the icon's vertical center ($iconCenterY) inside the map's own middle third " +
                "($middleThirdStart..$middleThirdEnd) — see this test's own doc comment",
            iconCenterY in middleThirdStart..middleThirdEnd,
        )
        assertTrue(iconCenterX > mapBounds.left + mapBounds.width / 2)
    }

    @Test
    fun `tapping the quick-fire icon opens the Layers sheet, and a chip there changes the basemap while the sheet stays open`() {
        // The map now opens on Street (dispatch 2026-10-18-04, Amendment 2), so the two chips are taken the other way
        // round: Topographical first, then back to Street. It was Street first, then Topographical.
        setScreen()
        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)

        composeRule.onNodeWithContentDescription(mapModeContentDescription).performClick()
        composeRule.onNodeWithText("Topographical").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)
        // Map layers L0b (owner's ruling on Q3, "Stays open"): the sheet stays open on a map-type
        // tap, so the other chip is still on screen. Before L0b the picker closed itself here and
        // this asserted Satellite's count was 0; until dispatch 2026-09-28-708 it then went on to
        // Satellite, which that dispatch removed, so it now goes back to Topographical and checks
        // that no Satellite chip is offered.
        composeRule.onAllNodesWithText("Satellite").assertCountEquals(0)
        composeRule.onNodeWithText("Street").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertNotEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)
        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)
    }

    @Test
    fun `Records shows an Offline Maps tab`() {
        setScreen()
        openRecordsTab()

        // J1 S3: an "Offline maps" filter chip now, scrolled into view in the chip row.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.OFFLINE_MAPS)).performScrollTo().assertIsDisplayed().assert(hasText("Offline maps"))
    }

    /**
     * Field-test dispatch item 1: `GpxCodec` was fully implemented and tested but called from
     * nowhere. The Records tab's list-then-share pattern (moved here from Settings by the Journal
     * restructure) is the surface this reuses — see `TrackExportList`'s own doc comment.
     */
    @Test
    fun `Records shows a Recorded Tracks tab`() {
        setScreen()
        openRecordsTab()

        // J1 S3: a "Tracks" filter chip now.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.RECORDED_TRACKS)).performScrollTo().assertIsDisplayed().assert(hasText("Tracks"))
    }

    @Test
    fun `Recorded Tracks shows an empty state with nothing recorded yet`() {
        setScreen()
        openRecordedTracksSubTab()

        composeRule.onNodeWithText("No recorded tracks yet.").assertIsDisplayed()
    }

    /**
     * The exact failure shape item 2 of this dispatch documents for return-to-vehicle: a value
     * wired only to `contentDescription` passes every existing test while showing a sighted user
     * nothing. This asserts the track's timestamp and its share affordance are found by
     * [onNodeWithText]/[onNodeWithTag] — proof they're actually visible, not merely
     * TalkBack-reachable.
     */
    @Test
    fun `Recorded Tracks lists a track by visible text and a taggable share action, not contentDescription alone`() {
        val track = Track(
            id = "track-1",
            name = null,
            startedAtEpochMillis = TRACK_STARTED_AT,
            endedAtEpochMillis = TRACK_STARTED_AT + 60_000L,
            points = listOf(
                TrackPoint(lat = 45.0, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = TRACK_STARTED_AT),
                TrackPoint(lat = 45.001, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = TRACK_STARTED_AT + 15_000L),
            ),
        )
        setScreen(tracks = listOf(track))
        openRecordedTracksSubTab()

        composeRule.onNodeWithText(expectedTrackTimestampText(TRACK_STARTED_AT)).assertIsDisplayed()
        composeRule.onNodeWithText("2 points").assertIsDisplayed()
        composeRule.onNodeWithTag("share-track-track-1").assertIsDisplayed()
    }

    @Suppress("DEPRECATION")
    @Test
    fun `tapping a track's share action starts a real ACTION_SEND chooser for a GPX file`() {
        val track = Track(
            id = "track-1",
            name = null,
            startedAtEpochMillis = TRACK_STARTED_AT,
            endedAtEpochMillis = TRACK_STARTED_AT + 60_000L,
            points = listOf(
                TrackPoint(lat = 45.0, lng = -122.0, altitude = null, accuracyMeters = null, timestampEpochMillis = TRACK_STARTED_AT),
            ),
        )
        setScreen(tracks = listOf(track))
        openRecordedTracksSubTab()

        composeRule.onNodeWithTag("share-track-track-1").performClick()
        // The share action writes the GPX file on Dispatchers.IO (a real thread pool) before
        // starting the chooser — waitForIdle() only synchronizes Compose's own recomposition/
        // animation clock, not that background hop, so this polls for the real effect instead.
        // Robolectric's nextStartedActivity is a consuming (dequeuing) getter, not a peek — it's
        // captured into `started` the first time the predicate finds it, rather than queried again
        // afterward, which would otherwise find the queue already drained and read back null.
        var started: Intent? = null
        composeRule.waitUntil(timeoutMillis = 5_000) {
            started = started ?: Shadows.shadowOf(composeRule.activity).nextStartedActivity
            started != null
        }

        assertEquals(Intent.ACTION_CHOOSER, started?.action)
        val inner = started?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, inner?.action)
        assertEquals("application/gpx+xml", inner?.type)
        assertTrue(inner?.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) != null)
    }

    /**
     * Timestamp-filter dispatch, Item 3: a track the read seam emptied is never a silent "0 points"
     * row — it says what happened. Literal copy. Fails with the note removed from trackSubtitle.
     */
    @Test
    fun `Recorded Tracks names an emptied track instead of showing zero points`() {
        val emptied = Track(
            id = "track-1",
            name = null,
            startedAtEpochMillis = TRACK_STARTED_AT,
            endedAtEpochMillis = TRACK_STARTED_AT + 60_000L,
            points = emptyList(),
            excludedPointCount = 12,
        )
        setScreen(tracks = listOf(emptied))
        openRecordedTracksSubTab()

        composeRule.onNodeWithText("No usable points — all 12 fixes were from the network provider").assertIsDisplayed()
        composeRule.onAllNodesWithText("0 points", substring = true).assertCountEquals(0)
    }

    /** No recording in progress, no tap yet — nothing should have started an activity. */
    @Test
    fun `Recorded Tracks starts nothing until the share action is actually tapped`() {
        val track = Track(
            id = "track-1",
            name = null,
            startedAtEpochMillis = TRACK_STARTED_AT,
            endedAtEpochMillis = null,
            points = emptyList(),
        )
        setScreen(tracks = listOf(track))
        openRecordedTracksSubTab()

        composeRule.onNodeWithText("0 points · recording").assertIsDisplayed()

        assertNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
    }

    /**
     * The behavior this project's owner explicitly asked for: offline downloads always use a fixed
     * source internally, so reaching the submenu never depends on which map mode is selected for
     * live browsing. The default (untouched) mode, Street since Amendment 2 of dispatch 2026-10-18-04, is used here.
     */
    @Test
    fun `Offline Maps is reachable under the default map mode, with no gating message`() {
        setScreen()
        openOfflineMapsSubTab()

        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Offline downloads are only available for the USGS map service",
            substring = true,
        ).assertCountEquals(0)
    }

    /**
     * Dispatch 2026-10-18-07 (Forager RECORD -1309; the owner: "Offline maps displays topo maps. Have it display the street
     * map", then, asked whether it should always be Street or follow the Layers choice, "Always be street."). Until then this
     * test was `the Offline Maps submenu always resolves the picker map to OpenTopoMap`, ending
     * `assertEquals(Basemap.OPEN_TOPO_MAP, capturedOfflinePickerBasemap)`: the behaviour that ruling replaces. The Maps tab is
     * set to Topographical here, so a picker that followed the Layers choice would fail this, not only one pinned to Topo.
     */
    @Test
    fun `the Offline Maps submenu always resolves the picker map to Street, even with the Maps tab on Topographical`() {
        setScreen(uiState = SEARCHED_STATE.copy(mapMode = MapMode.TOPOGRAPHIC))
        composeRule.waitForIdle()
        assertEquals("premise: the Maps tab's map is on Topographical", Basemap.OPEN_TOPO_MAP, capturedBasemap)
        openOfflineMapsSubTab()
        // A synchronizing node query (as every other test in this file that reads capture state
        // right after a click already has, via assertIsDisplayed/assertIsNotEnabled) is what
        // actually forces recomposition to settle before a plain var read; performClick() alone
        // doesn't guarantee it here.
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()

        assertEquals(Basemap.OSM_STANDARD, capturedOfflinePickerBasemap)
    }

    /**
     * Records is flat tabs (Waypoints/Offline Maps/Recorded Tracks), not a drill-in submenu — see
     * [com.zynergylabs.forager.app.ui.log.RecordsTab]'s own doc comment. There is no back arrow to leave one; a
     * sub-tab is left by tapping another sub-tab, and each swap fully replaces the shown content.
     */
    @Test
    fun `tapping between Waypoints, Offline Maps, and Recorded Tracks tabs switches Records content`() {
        setScreen()
        openRecordsTab()

        // J1 S3: Records opens on All now; the Waypoints chip shows Waypoints' content.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.WAYPOINTS)).performScrollTo().performClick()
        composeRule.onNodeWithText("No waypoints dropped yet. Tap the add button on the map to drop one.")
            .assertIsDisplayed()

        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.OFFLINE_MAPS)).performScrollTo().performClick()
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText("No waypoints dropped yet. Tap the add button on the map to drop one.")
            .assertCountEquals(0)

        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.recordsFilterChipTestTag(com.zynergylabs.forager.app.ui.log.RecordsSubTab.RECORDED_TRACKS)).performScrollTo().performClick()
        composeRule.onNodeWithText("No recorded tracks yet.").assertIsDisplayed()
        composeRule.onAllNodesWithTag(OFFLINE_PICKER_MAP_TAG).assertCountEquals(0)
    }

    // Journal redesign J4b L5: this test used to end with `onAllNodesWithText("Delete")
    // .assertCountEquals(0)` on an empty region list, guarding "no standalone Delete button". J4
    // removed every "Delete" text from the app, so that line passed whatever it checked, and with
    // no region there was no row for a delete control to be on anyway. The empty-list half stays
    // here; what the line was guarding moved to the next test, which has a row to look at.
    // Dispatch 2026-10-18-03 (Forager RECORD -1269; the owner: "Remove both OK and Cancel and just have the Download Maps option";
    // Download Maps "is enabled whenever the map has a position and no download is running, and is never greyed waiting for OK").
    // Was `Download Maps is disabled with no region picked, ...`, asserting `.assertIsNotEnabled()` here.
    @Test
    fun `Download Maps is enabled with no region picked, and no regions show with nothing downloaded`() {
        setScreen()
        openOfflineMapsSubTab()

        composeRule.onNodeWithText("Download Maps").performScrollTo().assertIsDisplayed().assertIsEnabled()
        composeRule.onNodeWithText("No regions downloaded yet.").performScrollTo().assertIsDisplayed()
    }

    /**
     * J4b L5: what the old "no delete buttons" line was guarding, asserted where it can fail. A
     * downloaded region's row, at rest, carries **no** delete control of its own (no "Delete" text,
     * no clickable node described as a delete), and **does** carry J4's swipe (its tag) and the
     * "Delete" accessibility action that stands in for the button (J4 D3). The row's name showing is
     * the positive control: the row is really there to be looked at.
     */
    @Test
    fun `a downloaded region's row has no Delete control at rest, and carries the swipe and its Delete action`() {
        setScreen(uiState = SEARCHED_STATE.copy(offlineRegions = listOf(L5_REGION)))
        openOfflineMapsSubTab()

        composeRule.onNodeWithText(L5_REGION.name).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(swipeToDeleteTag(RecordType.OFFLINE_MAPS, L5_REGION.id.toString()))
            .assertExists()
            .assert(
                SemanticsMatcher("has a custom accessibility action labelled Delete") { node ->
                    node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Delete" }
                },
            )
        composeRule.onAllNodesWithText("Delete", ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodes(hasContentDescription("Delete", substring = true, ignoreCase = true) and hasClickAction())
            .assertCountEquals(0)
    }

    // Dispatch 2026-10-18-03 (Forager RECORD -1269): this screen has no OK; Download Maps picks the pin's point and asks first. Was
    // `panning the picker map and confirming with OK sets the region and enables Download Maps`: Download Maps not enabled on
    // arrival, then `onNodeWithText("OK").performClick()`, then enabled.
    @Test
    fun `panning the picker map and tapping Download Maps picks the panned point and asks first`() {
        setScreenWithOfflineMapsState()
        openOfflineMapsSubTab()
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("Download Maps").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Download this area?").assertIsDisplayed()
        composeRule.onNode(hasText("Cancel") and hasAnyAncestor(isDialog())).performClick()
        composeRule.waitForIdle()

        // Dispatch 2026-10-17-09, RECORD -1245 (the owner: "Option 1, but do the same for portrait"): the panel's
        // "Download region: <lat>, <lng>" line is gone, both forms of it; this read it as the evidence that OK set the region
        // (`onNodeWithText("Download region: …").performScrollTo().assertIsDisplayed()`). The evidence is now the pinned "Pin at"
        // line showing the picked point, and Download Maps enabled (since dispatch 2026-10-18-03 it is enabled whenever the pin is
        // on a real place and no download runs).
        composeRule.onNodeWithText(
            "Pin at: ${"%.4f".format(PICKED_LOCATION.lat)}, ${"%.4f".format(PICKED_LOCATION.lng)}",
        ).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Download Maps").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `the Offline Maps submenu states it covers the continental US with vector map data`() {
        setScreen()
        openOfflineMapsSubTab()

        composeRule.onAllNodesWithText("continental United States", substring = true).assertCountEquals(1)
    }

    /**
     * The offline-readiness text a downloaded region shows — the z0-14 local-archive vs. z15
     * live-fetched-at-download-time distinction `docs/navigation/forager-navigator-plan.md`'s Phase 1c
     * item asks this submenu to surface, per the project owner's own call to extend this existing
     * panel rather than build a separate live-position readiness check (deferred).
     *
     * This text moved with Workstream B from the picker's own transient download status (main's
     * single-region `OfflineMapStatusContent`) to each region's own row in `OfflineRegionsSection` —
     * see that composable's doc comment for why: [OfflineMapStatus.Succeeded] is a bare marker with
     * no region data left to attach the text to, once a region can no longer replace whatever was
     * downloaded before it.
     *
     * Moved again by RECORD -870 (the owner: "Yes please. Good catch"): the row became one plain line and the
     * paragraph lives on the region's own page, its details sheet, which a tap on the row opens. Rewritten for that:
     * the three counts were 1 on the Offline maps chip; they are now 0 on the chip and 1 on the region's page.
     */
    @Test
    fun `a downloaded region's page states it is ready to zoom 15, with the z14-archive vs z15-live distinction, and its row does not`() {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE.copy(
                    offlineRegions = listOf(
                        OfflineRegionSummary(
                            id = 1L,
                            name = "Chanterelle Ridge",
                            region = REGION,
                            minZoom = OfflineMapRepository.MIN_ZOOM,
                            maxZoom = OfflineMapRepository.MAX_ZOOM,
                            tileCount = 4200,
                            sizeBytes = 18_500_000L,
                            createdAtEpochMillis = 0L,
                        ),
                    ),
                ),
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
                mapSlot = CapturingMapSlot,
            )
        }
        openOfflineMapsSubTab()
        val zoomFragments = listOf("Ready to zoom 15", "zoom 10–14 from the archive", "zoom 15 detail fetched live from Protomaps")
        for (fragment in zoomFragments) composeRule.onAllNodesWithText(fragment, substring = true).assertCountEquals(0)

        composeRule.onNodeWithText("Chanterelle Ridge").performScrollTo().performClick()
        composeRule.waitForIdle()

        for (fragment in zoomFragments) composeRule.onAllNodesWithText(fragment, substring = true).assertCountEquals(1)
    }

    /**
     * Two-data-corrections dispatch, Part B: the offline radius slider's own range ends at the
     * offline ceiling (24 km), so a radius one step above it is not reachable from the UI — asserted
     * on the slider's semantics (`ProgressBarRangeInfo` is what Material3's Slider publishes for its
     * value range and step count), the closest thing to "what the thumb can reach" a Robolectric
     * test can read. Literals: range 1–24, 22 steps (24 − 1 − 1), current 8 (the miles default the
     * state starts on). Fails with the slider's `valueRange` reverted to `Region.MAX_RADIUS_KM`
     * (1–50, 48 steps).
     */
    @Test
    fun `the offline radius slider cannot reach one step above the offline ceiling`() {
        setScreenWithOfflineMapsState()
        openOfflineMapsSubTab()

        val sliders = composeRule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.ProgressBarRangeInfo] }
        val offlineRadiusSlider = sliders.single { it.range.start == 1f && it.range.endInclusive != 50f }
        assertEquals(24f, offlineRadiusSlider.range.endInclusive)
        assertEquals(22, offlineRadiusSlider.steps)
        // Material3 snaps the thumb to its step grid in float, which lands on 7.9999995 for 8.
        assertEquals(8f, offlineRadiusSlider.current, 0.001f)
    }

    /**
     * UI-defects dispatch, §2: the picker's own label and the panel's "No location picked yet"
     * line read two different pieces of state — the pin's live map position
     * ([com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]'s own `cameraCenter`) and whether OK has ever
     * been pressed ([AvailabilityUiState.offlineMapLatText]/`offlineMapLngText`, set only from
     * [OfflineMapsPanel]'s own `onRegionPicked`) — and both are legitimately true before any pick,
     * which is not a state bug: it's the exact moment the dispatch's screenshot caught. The fix is
     * wording, not state — the picker no longer claims a "Selected" that has not happened.
     */
    @Test
    fun `the offline map picker's pin label and the panel's not-yet-picked line do not contradict each other`() {
        setScreen()
        openOfflineMapsSubTab()

        composeRule.onAllNodesWithText("Pin at:", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("Selected:", substring = true).assertCountEquals(0)
        // Dispatch 2026-10-17-09, RECORD -1245: the panel's not-yet-picked line is gone (the owner: "Option 1, but do the same
        // for portrait"), so nothing can contradict the pin's label; this asserted it was displayed
        // (`onNodeWithText("No location picked yet — pan the map above and tap OK.").performScrollTo().assertIsDisplayed()`).
        composeRule.onAllNodesWithText("No location picked yet", substring = true).assertCountEquals(0)
    }

    /**
     * Dispatch 2026-09-28-707 (the owner: "Move the two camera options at the bottom to a settings
     * menu inside the camera itself"): Settings no longer shows "Automatically Save Location to
     * Photos" or "Lock camera to portrait", nor their explanations. They were the camera's gear panel's
     * until dispatch 2026-10-13-03 removed both (RECORD -968, -972); photo location is now set from the
     * camera strip's Location chip alone, and the lock is gone. This test's own check is unchanged.
     *
     * Replaces `the photo-location checkbox starts on, explains itself, and toggles` and `the
     * lock-camera checkbox starts off, explains that sideways photos save portrait, and toggles`,
     * which found both rows in Settings.
     */
    @Test
    fun `Settings no longer shows the two camera settings`() {
        setScreenWithOfflineMapsState()
        openSettings()
        composeRule.waitForIdle()

        // Settings is the page showing: its own rows are there.
        composeRule.onNodeWithText("Night Maps").assertIsDisplayed()
        // Dispatch 2026-10-13-03 removed the four string constants with the gear panel's rows; the strings are written out.
        for (text in listOf(
            "Automatically Save Location to Photos",
            "When saving photos, metadata is stripped of the location data. Enabling this option captures your current position, and saves it to the Journal entry instead. This allows you to share your photos outside the app without the location being revealed.",
            "Lock camera to portrait",
            "Keeps the camera and its controls still when you turn the phone. Photos are always saved in portrait, even when you hold the phone sideways.",
        )) {
            assertEquals("'$text' is not in Settings", 0, composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size)
        }
        assertEquals("nothing is written just by opening Settings", null, capturedAutoSaveLocation)
    }
}

private const val MAP_SLOT_TAG = "settings-panel-test-map-slot"
private const val OFFLINE_PICKER_MAP_TAG = "settings-panel-test-offline-picker-map-slot"

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)
private val PICKED_LOCATION = LatLng(lat = 44.5, lng = -121.5)

private const val TRACK_STARTED_AT = 1_756_400_000_000L

/** Mirrors `TrackExportPanel`'s own private `formatTrackTimestamp` exactly, against the JVM's own default zone, so this stays correct under whatever timezone the test runs in. */
private fun expectedTrackTimestampText(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a").format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 100L + index,
    scientificName = "Species $index",
    commonName = "Species $index",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 1),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(
    region = REGION,
    sightings = List(4) { sighting(it) },
)

/** J4b L5's one downloaded region. */
private val L5_REGION = OfflineRegionSummary(
    id = 5L,
    name = "Molalla Ridge",
    region = Region(lat = 45.1, lng = -122.5, radiusKm = 5),
    minZoom = OfflineMapRepository.MIN_ZOOM,
    maxZoom = OfflineMapRepository.MAX_ZOOM,
    tileCount = 1200,
    sizeBytes = 5_000_000L,
    createdAtEpochMillis = 0L,
)
