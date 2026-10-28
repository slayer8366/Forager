package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.ForagingSelection
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.MapRecords
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.PendingDelete
import com.zynergylabs.forager.app.domain.WaypointNavigation
import com.zynergylabs.forager.app.domain.withoutPending
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.AvailabilityForecast
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.defaultOfflineMapRadiusKm
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import java.time.LocalDate

data class AvailabilityUiState(
    val region: Region? = null,
    val selectedMonth: Int = LocalDate.now().monthValue,
    // 8, not a rounder-looking 5: formatDistanceKm's mi label rounds to the nearest whole mile
    // (DistanceUnit.kt), and 8km * MILES_PER_KM = 4.97, the km value that actually displays as
    // "5 mi" -- the unit the default is chosen against, since MILES is this app's own default unit.
    val radiusKm: Int = 8,
    val manualLatText: String = "",
    val manualLngText: String = "",
    val forecast: AvailabilityForecast? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationPermissionDenied: Boolean = false,
    val sightings: List<Sighting> = emptyList(),
    /**
     * RECORD -723: counts every search the ViewModel starts, the same search run again included. Read by
     * AvailabilityScreen's lazy-fetch effect, which is keyed on it: a search clears [sightings] and the
     * seasonal pattern and expects the visible tab to fetch them again, but re-running the search already
     * showing (a recent search tapped, the same coordinates searched twice) changes none of region, month
     * or filter, so without this the effect never re-ran and the map was left empty ("Dropdown closes,
     * nothing new").
     */
    val searchSerial: Int = 0,
    /**
     * RECORD -750, item 5 (the owner: "Yes, fly to the search (Recommended)"): counts the location searches the user ran (a
     * recent search, coordinates, Set on map, Search with the current location), each bumped in the same update that sets
     * [region]. The Maps tab hands it to the map as [com.zynergylabs.forager.app.ui.map.MapOverlayContent.searchFrameRequestId],
     * and each new value stops the camera following the GPS fix and frames the searched region. Not [searchSerial]: a month
     * change re-runs the search for the same place, and that does not move the camera the user may have moved.
     */
    val searchFrameSerial: Int = 0,
    val isLoadingSightings: Boolean = false,
    val sightingsErrorMessage: String? = null,
    val taxonFilter: TaxonFilter = TaxonFilter.FUNGI,
    val taxonSearchQuery: String = "",
    val taxonSearchResults: List<TaxonSearchResult> = emptyList(),
    val isSearchingTaxa: Boolean = false,
    val taxonSearchErrorMessage: String? = null,
    /**
     * True only right after a completed search answered zero matches for the query still in
     * [taxonSearchQuery] — distinct from [taxonSearchResults] simply being empty, which is also
     * true before any search has run and right after [AvailabilityViewModel.onDismissTaxonSuggestions]
     * clears it. Reset to false by every one of those (a query edit, a dismiss, a pick), so it
     * can drive the search dropdown's "no matches" row without that row reappearing on its own
     * right after being dismissed. The local fungi index (unlike the old unfiltered remote
     * autocomplete this replaced) legitimately answers nothing for some queries, so this is a
     * real state to render, not an edge case to leave silent.
     */
    val taxonSearchHasNoResults: Boolean = false,
    /**
     * The query text behind [taxonSearchResults] at the moment a result was last picked — kept
     * around after [taxonSearchQuery] itself is cleared back to blank on selection, so tapping the
     * summary strip can restore and re-search it without the user retyping. See
     * [AvailabilityViewModel.onReopenTaxonSuggestions][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onReopenTaxonSuggestions].
     */
    val lastTaxonSearchQuery: String = "",
    val conditions: ConditionsSummary? = null,
    val isLoadingConditions: Boolean = false,
    val conditionsErrorMessage: String? = null,
    /**
     * The reference day's own forecast, shown alongside [conditions] in the Seasonal tab's
     * weather panel — see [com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase]. Gated to the current
     * month exactly like [conditions]: a forecast is only "today's" when the browsed month
     * actually is this one.
     */
    val todaysForecast: DailyWeather? = null,
    val isLoadingTodaysForecast: Boolean = false,
    val todaysForecastErrorMessage: String? = null,
    /**
     * Which group's weather guidance text applies to [taxonFilter], carried alongside it because
     * [TaxonFilter] alone cannot answer that — see [ForagingSelection]'s doc comment.
     */
    val foragingSelection: ForagingSelection = ForagingSelection.forChip(TaxonFilter.FUNGI),
    val tripWindowReport: TripWindowReport? = null,
    val isLoadingTripWindows: Boolean = false,
    val tripWindowsErrorMessage: String? = null,
    /**
     * Trips the user has placed on the map for a future date, independent of any region search —
     * these are absolute map points, not tied to species/category/search state. Sorted by
     * [GetPlannedTripsUseCase][com.zynergylabs.forager.app.domain.GetPlannedTripsUseCase] with any dated today
     * promoted to the front.
     */
    val plannedTrips: List<PlannedTrip> = emptyList(),
    val plannedTripsErrorMessage: String? = null,
    /**
     * The Seasonal tab's test of [com.zynergylabs.forager.app.domain.FruitingPatternAssumptions.FRUITING_LAG_DAYS]
     * against real historical sightings and rainfall for the current search — see
     * [com.zynergylabs.forager.app.domain.GetSeasonalPatternUseCase]. Fetched lazily on tab open, keyed on
     * region+month+filter, the same pattern [sightings] already uses for the Map tab.
     */
    val seasonalPattern: FruitingLagDistribution? = null,
    val isLoadingSeasonalPattern: Boolean = false,
    val seasonalPatternErrorMessage: String? = null,
    /**
     * Whether [forecast] came out of the offline cache rather than off the network.
     *
     * The List tab must say so out loud when this is true — CLAUDE.md: a fallback result is
     * reported as a fallback, never rendered identically to a live one. See
     * [AvailabilitySearchResult][com.zynergylabs.forager.app.domain.AvailabilitySearchResult], the type this
     * and [cachedResultsAsOfEpochMillis] are set from together.
     */
    val isShowingCachedResults: Boolean = false,
    /**
     * When the cached [forecast] was originally fetched, for the banner's "saved 3 hours ago".
     *
     * Non-null whenever [isShowingCachedResults] is true, because both are written from the same
     * `Cached` result in one update. The screen still handles the impossible combination rather
     * than asserting it away, since a banner that claims an age it doesn't have would be the exact
     * dishonesty the banner exists to prevent.
     */
    val cachedResultsAsOfEpochMillis: Long? = null,
    /**
     * The offline cache's recent searches, most recently used first, for the drawer's picker.
     * Independent of the current search — like [plannedTrips], it is loaded once at start-up and
     * refreshed after each search rather than being derived from the search in progress.
     */
    val recentSearches: List<CachedSearchSummary> = emptyList(),
    /**
     * The standalone region picker in the "Offline Maps" submenu — independent of [region], per
     * this project's own decision: a downloaded region has nothing to do with whatever's currently
     * searched in the List/Map tabs. Set by panning the picker map there to the centre pin and
     * confirming with OK (see `OfflineMapsPanel` in `AvailabilityScreen.kt`), not by typing — `String`, same representation
     * [manualLatText]/[manualLngText] use, rather than a nullable `Double`, so "nothing picked yet"
     * and "picked" are both representable without a separate flag.
     */
    val offlineMapLatText: String = "",
    val offlineMapLngText: String = "",
    /**
     * Starts at the per-unit default for [distanceUnit]'s own initial value (miles → 8 km, which
     * displays as "5 mi") and is re-applied by the ViewModel whenever the unit changes while
     * [offlineMapRadiusTouched] is still false — see `defaultOfflineMapRadiusKm` and
     * `AvailabilityViewModel.applyDistanceUnit`. Radius-default dispatch, Item 2: the old single
     * default of 15 km read as "9 mi" in this app's default unit.
     */
    val offlineMapRadiusKm: Int = defaultOfflineMapRadiusKm(DistanceUnit.MILES),
    /**
     * `true` once the user has set the radius themselves (the slider) or a last-picked region has
     * restored it — from then on the per-unit default never moves it. Session state; the persisted
     * last-picked region is what carries a chosen radius across restarts.
     */
    val offlineMapRadiusTouched: Boolean = false,
    /**
     * A blank name defaults to "Region N" at download time — see
     * [AvailabilityViewModel.onDownloadOfflineMaps][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onDownloadOfflineMaps] —
     * rather than requiring one, the same "default rather than require" pattern
     * [com.zynergylabs.forager.app.domain.model.PlannedTrip.name] established for planned trips.
     */
    val offlineMapNameText: String = "",
    /** The picker's own last download attempt — see [OfflineMapStatus]'s doc comment. */
    val offlineDownloadStatus: OfflineMapStatus = OfflineMapStatus.Idle,
    /**
     * Every region currently on disk, per [com.zynergylabs.forager.app.domain.OfflineMapRepository.listRegions]
     * — the persisted list the "Offline Maps" submenu renders, independent of
     * [offlineDownloadStatus]'s in-flight/last-attempt state.
     */
    val offlineRegions: List<OfflineRegionSummary> = emptyList(),
    /** How many Cartography entries currently keep a reference to each offline region (by id) — Journal Stage 2b's 4b deletion warning. Loaded alongside [offlineRegions]; a region missing from this map has never been counted, treated as zero the same as an explicit zero. */
    val offlineRegionEntryReferenceCounts: Map<Long, Int> = emptyMap(),
    /**
     * The offline region whose delete was asked for (a swipe on its Records row, journal redesign J4)
     * and has not run yet: the Undo snackbar is still up, and its tiles are still on disk. See
     * [AvailabilityViewModel.requestDeleteOfflineRegion].
     */
    val pendingOfflineRegionDelete: PendingDelete<OfflineRegionSummary>? = null,
    /**
     * The ids of offline regions whose delete has left its Undo window and was started (or has finished):
     * [MushroomLogUiState.committedFindDeleteIds]'s rule, for the region circles (dispatch 2026-09-28-312,
     * item 9). In the same state update that clears [pendingOfflineRegionDelete]; kept after the delete
     * succeeds; removed only when it fails; cleared by a restore.
     */
    val committedOfflineRegionDeleteIds: Set<Long> = emptySet(),
    /**
     * A region-*list-load* failure, not a download failure — see
     * [AvailabilityViewModel.loadOfflineRegions][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel]'s
     * doc comment for the belief-changing distinction from [offlineDownloadStatus]. Also carries a
     * failed per-region delete, for the same reason: neither is something the user is mid-action on
     * the way a download is.
     */
    val offlineRegionsErrorMessage: String? = null,
    /**
     * The staleness badge threshold, in days, restored from
     * [com.zynergylabs.forager.app.domain.MapPreferencesRepository.getStaleThresholdDays] — see
     * [DEFAULT_STALE_THRESHOLD_DAYS] until that load completes.
     */
    val offlineStaleThresholdDays: Int = DEFAULT_STALE_THRESHOLD_DAYS,
    /**
     * Whether the map renders in night mode — Settings' "Night Maps" checkbox, restored from
     * [com.zynergylabs.forager.app.domain.MapPreferencesRepository.getNightModeMaps]. `false` (day) until that
     * load completes.
     */
    val nightModeMaps: Boolean = false,
    /**
     * Whether the Night Maps preference read has completed, successfully or not — colour build C1's
     * cold-launch gate. [nightModeMaps] is read asynchronously from `init`, and the map's style
     * effect only waits for the map itself, so without this a night user could get a day style that
     * never switches. `SightingsMap` loads no style while this is `false`. Set `true` by
     * `AvailabilityViewModel.loadNightModePreferences` on success (together with the value) and on
     * failure (logged, [nightModeMaps] left `false`), so it can never hold the map blank forever.
     */
    val nightModeMapsLoaded: Boolean = false,
    /** Settings > Map > "Crowded icons" (dispatch 2026-10-27-14); the Maps tab writes it into its crowded-stack host. */
    val crowdedIconsMode: CrowdedIconsMode = CrowdedIconsMode.DEFAULT,
    /**
     * The Maps tab's basemap (dispatch 2026-09-28-708): restored at start from
     * [com.zynergylabs.forager.app.domain.BasemapPreferenceRepository] and kept in step with every pick
     * ([AvailabilityViewModel.onMapModeSelected]). `null` until that read has finished, successfully or
     * not, which is the second half of the cold-launch gate: `AvailabilityScreen` tells the map no style
     * may load until this and [nightModeMapsLoaded] are both in, so a phone that left Street never draws
     * a first frame of Topographical. A failed read, or a stored key naming no map type (Satellite's,
     * after its removal), is logged and lands here as a real mode, so it can never hold the map blank.
     */
    val mapMode: MapMode? = null,
    /**
     * The Maps tab's icon cluster's side and height (RECORD -711), as stored: restored at start from
     * [com.zynergylabs.forager.app.domain.MapIconClusterPlacementRepository] and kept in step with every
     * drag that ends ([AvailabilityViewModel.onMapIconClusterPlacementChanged]). `null` while the read is
     * pending, and while it is `null` `AvailabilityScreen` does not draw the cluster, so a phone that left
     * it on the left never shows a frame of it on the right. Every way the read ends (stored, nothing
     * stored, failed and logged) lands a real placement here, so it cannot keep the cluster hidden.
     *
     * The default here is [MapIconClusterPlacement.DEFAULT], not `null`: the ViewModel starts its state
     * with `null` explicitly (it has a read to wait for), while a host with no ViewModel (the screen tests
     * that build this state directly) has nothing to wait for and shows the cluster where it always opened.
     */
    val mapIconClusterPlacement: MapIconClusterPlacement? = MapIconClusterPlacement.DEFAULT,

    /**
     * Settings' "Automatically Save Location to Photos" checkbox — see
     * [com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository] for what the one flag
     * gates, which is wider than the label. `true` until the stored preference loads, matching that
     * repository's own default so the checkbox does not visibly flip on itself at startup.
     */
    val autoSaveLocationToPhotos: Boolean = true,
    /** Settings' "Sundown alerts" (dispatch 2026-09-28-592): on until the stored value loads, the repository's default. */
    val sundownAlertsEnabled: Boolean = true,
    /** Settings' "Off-track reminder" (dispatch 2026-09-28-626): on until the stored value loads, the repository's default. */
    val offTrackReminderEnabled: Boolean = true,
    /** The quick menu's "Battery saver" (dispatch 2026-09-28-767): off until the stored value loads, the repository's default. */
    val batterySaverEnabled: Boolean = false,
    /** Settings' "Dark under trees", in minutes (dispatch 2026-09-28-592): one hour until the stored value loads, the repository's default. */
    val darknessMarginMinutes: Int = com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES,
    /**
     * Whether the Maps tab was left in fullscreen on the last run, restored from
     * [com.zynergylabs.forager.app.domain.MapPreferencesRepository.getMapFullscreen] — the one cluster/map
     * UI preference that persists across restarts (see that method's own doc comment for the
     * precedent it sets). `null` until the load completes or when the read failed; the screen
     * applies a non-null value once, on arrival, and never writes it back from an effect — see
     * `AvailabilityScreen`'s own `isMapFullscreen` for why that ordering is load-bearing.
     */
    val persistedMapFullscreen: Boolean? = null,
    /**
     * The app's own theme choice — Settings' Light/Dark/System Default radio group, restored from
     * [com.zynergylabs.forager.app.domain.AppThemePreferenceRepository.getThemeMode]. [AppThemeMode.LIGHT]
     * until that load completes. Independent of [nightModeMaps], which controls only the map's
     * basemap. [AppThemeMode.SYSTEM_DEFAULT] is resolved against the device's own theme in
     * `MainActivity`, not here — see [AppThemeMode]'s own doc comment.
     */
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    /**
     * The offline-region picker map's opening viewport before a region has been picked —
     * restored from [com.zynergylabs.forager.app.domain.MapPreferencesRepository.getLastPickedRegion] at
     * startup, then overridden by the device's current location every time the picker is opened
     * (see [AvailabilityViewModel.onOfflineMapsOpened][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onOfflineMapsOpened]),
     * or if nothing has ever been picked, in which case the picker falls back to its own fixed
     * continental-US-centre default — see `OFFLINE_MAP_PICKER_DEFAULT_CENTER` in
     * `AvailabilityScreen.kt`. Distinct from [offlineMapLatText]/[offlineMapLngText], which mean
     * "picked in this session"; this is never itself submitted as a region.
     */
    val offlineMapPickerDefaultCenter: LatLng? = null,
    /**
     * The map's GPS/locate-me icon stack button — see [LocateMeStatus]'s doc comment for why this
     * is a separate field from [locationPermissionDenied], which belongs to the unrelated "use
     * current location for search region" control.
     */
    val locateMeStatus: LocateMeStatus = LocateMeStatus.Idle,
    /**
     * The device's own live position — the last fix received from
     * [com.zynergylabs.forager.app.domain.LocationTracker.fixes], continuously refreshed while this ViewModel is
     * alive, distinct from [locateMeStatus]'s one-shot fetch (still used for the map's own "center
     * on me" action and its permission-denied/unavailable messaging). `null` before a first fix
     * arrives, or if location permission isn't granted — the compass strip's own "Coordinates
     * unavailable" text already covers that state honestly; there is no separate
     * denied/unavailable case duplicated here.
     *
     * HUD-foundations dispatch, Item 1: the whole [com.zynergylabs.forager.app.domain.LocationFix.Update] is
     * held now, not just its lat/lng/altitude, so a consumer can ask how accurate the fix is
     * (`accuracyMeters`, where `null` — "not reported" — stays distinct from `0f`) and, via
     * [com.zynergylabs.forager.app.domain.ageMillis], how old it is. Both used to be dropped at this boundary,
     * leaving the last position to read as current forever once fixes stopped. Held as the domain
     * type itself rather than a parallel UI-side copy: it already carries exactly these fields, and
     * `TrackRecordingViewModel`'s own path (fix → `TrackPoint`, the persisted shape) stays separate
     * by decision — neither consumer needs the other's type. No staleness threshold or "GPS lost"
     * state is derived here; that policy belongs to the HUD, not to this state.
     */
    val liveFix: LocationFix.Update? = null,
    /**
     * The newest fix the live gate refused ([com.zynergylabs.forager.app.domain.acceptLiveFix]: over 50 m, or
     * not from GPS, dispatch 2026-09-28-527):
     * an approximate reading, shown and never acted on (dispatch 2026-09-28-510; see
     * [com.zynergylabs.forager.app.domain.ShownPosition]). Held **beside** [liveFix], never in it, so
     * everything that reads [liveFix] to act ("Arrived", the waypoint's line, a new find's location) can
     * never see it. `null` until a reading is refused.
     */
    val approximateFix: LocationFix.Update? = null,
    /**
     * The platform's own last known location, read when this screen starts collecting fixes
     * ([com.zynergylabs.forager.app.domain.LastKnownLocationSource]); shown greyed with its age while
     * nothing live has arrived (dispatch 2026-09-28-510). Never acted on, never moved into [liveFix].
     */
    val lastKnownFix: LocationFix.Update? = null,
    /**
     * The waypoint being navigated to, if any (dispatch 2026-09-28-502, plan tasks T8 and T9): set by
     * [AvailabilityViewModel.onNavigateToWaypoint], cleared by [AvailabilityViewModel.onStopWaypointNavigation],
     * and picked back up from [com.zynergylabs.forager.app.domain.WaypointNavigationRepository] when the
     * app starts. Held here, not in the screen, so it outlives a tab change and a recreation, and needs
     * no recording: the live fix it measures from is this ViewModel's own.
     */
    val waypointNavigation: WaypointNavigation? = null,
    /**
     * The dashed line to the waypoint being navigated to (dispatch -502), worked out by [AvailabilityWaypointLine]
     * and held here, so it outlives a tab change (step 6 PR 1, dispatch 2026-10-26-06: moved from the screen's own
     * `remember`). `null` with no navigation, before the first fix, and once arrived. The screen draws it only while
     * its own resolved target is found: the one rule for a deleted or Undo-pending waypoint (RECORD -1657).
     */
    val waypointStraightLine: StraightLine? = null,
    /**
     * The way to that waypoint along trails (step 6 PR 3, dispatch 2026-10-26-11), worked out by [AvailabilityWaypointRoute]
     * from this ViewModel's own fixes, in the foreground only (RECORD -1652 Q5). `null` with no navigation and while the
     * route is worked out, when the HUD and the map are today's straight ones; see [WaypointRouteShown].
     */
    val waypointRoute: WaypointRouteShown? = null,
    /**
     * The system of units this person reads, restored from
     * [com.zynergylabs.forager.app.domain.UnitSystemPreferenceRepository.getUnitSystem] at startup and
     * persisted on every change — see [DistanceUnit]'s own doc comment for the bug the persisted
     * form fixes (a system theme switch, among other configuration changes, used to reset it to
     * the default because it was plain Compose state, not ViewModel/persisted state), and
     * [UnitSystem]'s for why this is a system rather than the distance unit it used to be.
     */
    val unitSystem: UnitSystem = UnitSystem.IMPERIAL,
    /**
     * Every saved record the Maps tab draws (map layers L0b, B2), reloaded whenever the Maps tab is
     * shown ([AvailabilityViewModel.onMapShown]). A kind whose read failed is empty here, and logged.
     */
    val mapRecords: MapRecords = MapRecords.NONE,
    /**
     * The map's layer choices as the user left them (map layers L0b, B3): restored from
     * [com.zynergylabs.forager.app.domain.MapLayerPreferencesRepository] at start and kept in step with
     * every change the Layers sheet makes. Not the state a map draws with: `AvailabilityScreen` hides the
     * colour fields with no data first (`withUnavailableColourFieldsHidden`).
     */
    val mapLayers: MapLayersState = MapLayersState.DEFAULT,
    /** The ISO week the forecast store was last asked about, or `null` before the Maps tab was first shown (L0b, B5). */
    val forecastWeek: LocalDate? = null,
    /** The forecast groups the store has data for in [forecastWeek]; empty for "no forecast data" (L0b, B5). */
    val forecastGroups: Set<String> = emptySet(),
    /** Regions whose trail routes are being fetched now (dispatch 2026-10-23-03); their rows say "Getting trail routes". */
    val offlineRouteFetchingIds: Set<Long> = emptySet(),
    /** Regions whose route fetch failed this session, at download or automatically; only these offer "Try again". */
    val offlineRouteFailedIds: Set<Long> = emptySet(),
) {
    /**
     * [offlineRegions] without [pendingOfflineRegionDelete]: what the Journal's region rows, the All
     * logbook and the Offline maps chip count show (J4). The tile budget and the download gate keep
     * reading [offlineRegions], because a pending region's tiles are on disk until its delete runs.
     */
    val visibleOfflineRegions: List<OfflineRegionSummary>
        get() = offlineRegions.withoutPending(pendingOfflineRegionDelete) { it.id }

    /** Derived from [unitSystem], never set on its own — every distance display reads this exactly as before. */
    val distanceUnit: DistanceUnit get() = unitSystem.distanceUnit

    val hasSearched: Boolean get() = region != null

    /**
     * The compass strip's live coordinates — derived from [liveFix] so the strip's call site reads
     * exactly the lat/lng it always did (HUD-foundations dispatch, Item 1: "nothing that reads
     * `liveLocation` today changes behaviour").
     */
    val liveLocation: LatLng? get() = liveFix?.let { LatLng(it.lat, it.lng) }

    /** See [liveLocation] — the same fix's altitude, `null` whenever the device didn't report one. */
    val liveAltitudeMeters: Double? get() = liveFix?.altitude

    /**
     * The position true north is worked out at (declination), dispatch 2026-09-28-510: the gated fix,
     * else the approximate reading, else the last known one. Declination changes by a fraction of a
     * degree over tens of kilometres, so a position good to a few hundred metres, or hours old, gives
     * the same true north; without it the heading would read "needs a fix" exactly while the HUD shows
     * an approximate position and its needle. Read only by the true heading, which only displays.
     */
    val headingFix: LocationFix.Update? get() = liveFix ?: approximateFix ?: lastKnownFix
}
