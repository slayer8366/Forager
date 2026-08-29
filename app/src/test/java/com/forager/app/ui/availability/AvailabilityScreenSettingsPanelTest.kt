package com.forager.app.ui.availability

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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TrackPoint
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.MapSlot
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
 *    [MapModePicker], and that tapping a mode chip there actually changes which [Basemap] the map
 *    slot receives — not just that an icon is somewhere on screen. Settings' old "Choose Maps
 *    Service" section is gone entirely — see [com.forager.app.ui.map.MapMode]'s own doc comment for
 *    what superseded it — so this file no longer tests it.
 * 2. The "Offline Maps" submenu: reachable regardless of the selected [com.forager.app.ui.map.MapMode]
 *    (offline downloads always target a fixed source internally, so nothing about reaching the
 *    submenu depends on the live mode selection — see `com.forager.app.domain.OfflineMapRepository`'s
 *    doc comment), its navigation (entry row in → back arrow out), and picking a region by panning
 *    its [com.forager.app.ui.map.CentrePinLocationPicker] map and confirming with OK, instead of
 *    typing coordinates.
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
    private var capturedThemeMode: AppThemeMode? = null

    /** See this class's doc comment for why the two map instances are told apart by content. */
    private val CapturingMapSlot: MapSlot = { _, content, renderMode, _, _, _, _, onCameraIdle, modifier ->
        if (content.sightings.isEmpty() && content.areas.isEmpty() && content.plannedTrips.isEmpty()) {
            capturedOfflinePickerBasemap = renderMode.basemap
            Column(modifier.testTag(OFFLINE_PICKER_MAP_TAG)) {
                Button(onClick = { onCameraIdle(PICKED_LOCATION) }) { Text("Simulate pan to test location") }
            }
        } else {
            capturedBasemap = renderMode.basemap
            capturedNightMode = renderMode.night
            Box(modifier.testTag(MAP_SLOT_TAG))
        }
    }

    private fun setScreen(tracks: List<Track> = emptyList()) {
        composeRule.setContent {
            AvailabilityScreen(
                uiState = SEARCHED_STATE,
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
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
                mapSlot = CapturingMapSlot,
                tracks = tracks,
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
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
                onReopenTaxonSuggestions = {},
                onPlaceTripPin = { _, _, _ -> },
                onDeletePlannedTrip = {},
                onRecentSearchSelected = {},
                onOfflineMapLatChanged = { text -> current = current.copy(offlineMapLatText = text) },
                onOfflineMapLngChanged = { text -> current = current.copy(offlineMapLngText = text) },
                onOfflineMapRadiusChanged = { radius -> current = current.copy(offlineMapRadiusKm = radius) },
                onOfflineMapNameChanged = {},
                onOfflineMapsOpened = {},
                onDownloadOfflineMaps = {},
                onDeleteOfflineRegion = {},
                onNightModeMapsChanged = { night -> current = current.copy(nightModeMaps = night) },
                onThemeModeChanged = { mode ->
                    current = current.copy(themeMode = mode)
                    capturedThemeMode = mode
                },
                mapSlot = CapturingMapSlot,
            )
        }
    }

    private fun openSettings() {
        composeRule.onNodeWithText("Settings").performClick()
    }

    private fun openOfflineMaps() {
        openSettings()
        composeRule.onNodeWithText("Offline Maps").performClick()
    }

    @Test
    fun `the map opens on the OpenTopoMap basemap, MapMode's default`() {
        setScreen()

        // Basemap.DEFAULT resolves through MapMode.DEFAULT (Topographical, via OpenStreetMap) —
        // see MapMode's own doc comment for why this changed from PR #13's USGS Topo default.
        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)
    }

    /**
     * Settings' "Night Maps" checkbox — a direct, persistent toggle
     * ([AvailabilityUiState.nightModeMaps]), replacing the map's earlier civil-twilight-automatic/
     * long-press-hold control (`MapNightMode`, deleted). Driven through the real checkbox row
     * rather than calling `onNightModeMapsChanged` directly, and asserts the map slot's own
     * [com.forager.app.ui.map.MapRenderMode.night] actually flips, not just local Settings state —
     * read back via the Maps tab, since [CompactMapTab] (and so [CapturingMapSlot]'s "else" branch)
     * isn't composed at all while the Settings tab is showing, per the bottom-nav's own one-tab-
     * at-a-time model.
     */
    @Test
    fun `the Night Maps checkbox toggles night mode on the map`() {
        setScreenWithOfflineMapsState()

        assertEquals(false, capturedNightMode)

        openSettings()
        composeRule.onNodeWithText("Night Maps").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()
        assertEquals(true, capturedNightMode)

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Night Maps").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()
        assertEquals(false, capturedNightMode)
    }

    /**
     * Settings' Night Mode radio group (Light/Dark/System Default) — the app-wide theme
     * [com.forager.app.ui.theme.ForagerTheme] renders, sitting directly above
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

    private val mapModeContentDescription = "Map mode: Topographical. Choose Street, Topographical, or Satellite. Night mode off."

    @Test
    fun `the quick-fire icon renders over the map's own top-right corner`() {
        setScreen()

        val iconBounds = composeRule
            .onNodeWithContentDescription(mapModeContentDescription)
            .assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val mapBounds = composeRule.onNodeWithTag(MAP_SLOT_TAG).getUnclippedBoundsInRoot()

        // Over the map, not beside it: the icon's horizontal center must fall within the map's own
        // width, and it sits in the map's top half and right half — top-right, not merely "on top of".
        val iconCenterX = iconBounds.left + iconBounds.width / 2
        assertTrue(
            "expected the icon's horizontal center ($iconCenterX) to fall inside the map's bounds " +
                "(${mapBounds.left}..${mapBounds.right})",
            iconCenterX in mapBounds.left..mapBounds.right,
        )
        assertTrue(
            "iconBounds.top=${iconBounds.top} mapBounds.top=${mapBounds.top} mapBounds.height=${mapBounds.height} half=${mapBounds.top + mapBounds.height / 2}",
            iconBounds.top < mapBounds.top + mapBounds.height / 2,
        )
        assertTrue(iconCenterX > mapBounds.left + mapBounds.width / 2)
    }

    @Test
    fun `tapping the quick-fire icon opens the map mode picker, and a chip there changes the basemap`() {
        setScreen()
        assertEquals(Basemap.OPEN_TOPO_MAP, capturedBasemap)

        composeRule.onNodeWithContentDescription(mapModeContentDescription).performClick()
        composeRule.onNodeWithText("Street").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertEquals(Basemap.OSM_STANDARD, capturedBasemap)
        // The picker dismisses itself on selection, so a second chip isn't on screen until the icon
        // (now reflecting Street) is tapped again.
        composeRule.onAllNodesWithText("Satellite").assertCountEquals(0)

        composeRule.onNodeWithContentDescription(
            "Map mode: Street. Choose Street, Topographical, or Satellite. Night mode off.",
        ).performClick()
        composeRule.onNodeWithText("Satellite").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        assertNotEquals(Basemap.OSM_STANDARD, capturedBasemap)
        assertEquals(Basemap.USGS_IMAGERY_ONLY, capturedBasemap)
    }

    @Test
    fun `Settings shows an Offline Maps entry row`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Offline Maps").assertIsDisplayed()
    }

    /**
     * Field-test dispatch item 1: `GpxCodec` was fully implemented and tested but called from
     * nowhere. Settings' existing crash-log list-then-share pattern is the surface this reuses —
     * see `TrackExportPanel`'s own doc comment.
     */
    @Test
    fun `Settings shows a Recorded Tracks entry row`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Recorded Tracks").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `Recorded Tracks shows an empty state with nothing recorded yet`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Recorded Tracks").performScrollTo().performClick()

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
        openSettings()

        composeRule.onNodeWithText("Recorded Tracks").performScrollTo().performClick()

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
        openSettings()
        composeRule.onNodeWithText("Recorded Tracks").performScrollTo().performClick()

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
        openSettings()

        composeRule.onNodeWithText("Recorded Tracks").performScrollTo().performClick()
        composeRule.onNodeWithText("0 points · recording").assertIsDisplayed()

        assertNull(Shadows.shadowOf(composeRule.activity).nextStartedActivity)
    }

    /**
     * The behavior this project's owner explicitly asked for: offline downloads always use a fixed
     * source internally, so reaching the submenu never depends on which map mode is selected for
     * live browsing. Topographical is the default (untouched) mode for this test.
     */
    @Test
    fun `Offline Maps is reachable under the default map mode, with no gating message`() {
        setScreen()
        openOfflineMaps()

        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        composeRule.onAllNodesWithText(
            "Offline downloads are only available for the USGS map service",
            substring = true,
        ).assertCountEquals(0)
    }

    @Test
    fun `the Offline Maps submenu always resolves the picker map to OpenTopoMap`() {
        setScreen()
        openOfflineMaps()
        // A synchronizing node query (as every other test in this file that reads capture state
        // right after a click already has, via assertIsDisplayed/assertIsNotEnabled) is what
        // actually forces recomposition to settle before a plain var read; performClick() alone
        // doesn't guarantee it here.
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()

        assertEquals(Basemap.OPEN_TOPO_MAP, capturedOfflinePickerBasemap)
    }

    @Test
    fun `the Offline Maps entry row navigates into the submenu, and its back arrow returns to Settings`() {
        setScreen()
        openSettings()

        composeRule.onNodeWithText("Offline Maps").performClick()
        composeRule.onNodeWithTag(OFFLINE_PICKER_MAP_TAG).assertIsDisplayed()
        // Settings' own content — the distance-unit picker — is no longer on screen once inside the submenu.
        composeRule.onAllNodesWithText("Distance Unit").assertCountEquals(0)

        composeRule.onNodeWithContentDescription("Back to Settings").performClick()

        composeRule.onNodeWithText("Distance Unit").assertIsDisplayed()
        composeRule.onAllNodesWithTag(OFFLINE_PICKER_MAP_TAG).assertCountEquals(0)
    }

    @Test
    fun `Download Maps is disabled with no region picked, and no regions or delete buttons show with nothing downloaded`() {
        setScreen()
        openOfflineMaps()

        composeRule.onNodeWithText("Download Maps").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        // Delete is per-region now (OfflineRegionRow), not a standalone always-present button — with
        // nothing downloaded there is no row to show one on.
        composeRule.onNodeWithText("No regions downloaded yet.").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Delete").assertCountEquals(0)
    }

    @Test
    fun `panning the picker map and confirming with OK sets the region and enables Download Maps`() {
        setScreenWithOfflineMapsState()
        openOfflineMaps()
        composeRule.onNodeWithText("Download Maps").assertIsNotEnabled()

        composeRule.onNodeWithText("Simulate pan to test location").performClick()
        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText(
            "Download region: ${"%.4f".format(PICKED_LOCATION.lat)}, ${"%.4f".format(PICKED_LOCATION.lng)}",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Download Maps").assertIsEnabled()
    }

    @Test
    fun `the Offline Maps submenu states it covers the continental US with vector map data`() {
        setScreen()
        openOfflineMaps()

        composeRule.onAllNodesWithText("continental United States", substring = true).assertCountEquals(1)
    }

    /**
     * The offline-readiness text a downloaded region shows — the z0-14 local-archive vs. z15
     * live-fetched-at-download-time distinction `docs/plans/forager-navigator-plan.md`'s Phase 1c
     * item asks this submenu to surface, per the project owner's own call to extend this existing
     * panel rather than build a separate live-position readiness check (deferred).
     *
     * This text moved with Workstream B from the picker's own transient download status (main's
     * single-region `OfflineMapStatusContent`) to each region's own row in `OfflineRegionsSection` —
     * see that composable's doc comment for why: [OfflineMapStatus.Succeeded] is a bare marker with
     * no region data left to attach the text to, once a region can no longer replace whatever was
     * downloaded before it.
     */
    @Test
    fun `a downloaded region states it is ready to zoom 15, with the z14-archive vs z15-live distinction`() {
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
                onUseCurrentLocation = {},
                onManualLatChanged = {},
                onManualLngChanged = {},
                onSearchManualCoordinates = {},
                onRadiusChanged = {},
                onMonthSelected = {},
                onMapTabSelected = {},
                onSeasonalTabSelected = {},
                onToggleForagingAreas = {},
                onCategorySelected = {},
                onTaxonSearchQueryChanged = {},
                onTaxonSearchResultSelected = {},
                onDismissTaxonSuggestions = {},
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
                mapSlot = CapturingMapSlot,
            )
        }
        openOfflineMaps()

        composeRule.onAllNodesWithText("Ready to zoom 15", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("zoom 10–14 from the archive", substring = true).assertCountEquals(1)
        composeRule.onAllNodesWithText("zoom 15 detail fetched live from Protomaps", substring = true).assertCountEquals(1)
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
    foragingAreas = ForagingAreas.None(ForagingAreas.Reason.TOO_FEW_OBSERVATIONS, observationsConsidered = 4),
    showForagingAreas = false,
)
