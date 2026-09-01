package com.forager.app.ui.availability

import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.TripWindowReport
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
     * [AvailabilityViewModel.onReopenTaxonSuggestions][com.forager.app.ui.availability.AvailabilityViewModel.onReopenTaxonSuggestions].
     */
    val lastTaxonSearchQuery: String = "",
    val conditions: ConditionsSummary? = null,
    val isLoadingConditions: Boolean = false,
    val conditionsErrorMessage: String? = null,
    /**
     * The reference day's own forecast, shown alongside [conditions] in the Seasonal tab's
     * weather panel — see [com.forager.app.domain.GetTodaysForecastUseCase]. Gated to the current
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
     * [GetPlannedTripsUseCase][com.forager.app.domain.GetPlannedTripsUseCase] with any dated today
     * promoted to the front.
     */
    val plannedTrips: List<PlannedTrip> = emptyList(),
    val plannedTripsErrorMessage: String? = null,
    /**
     * The Seasonal tab's test of [com.forager.app.domain.FruitingPatternAssumptions.FRUITING_LAG_DAYS]
     * against real historical sightings and rainfall for the current search — see
     * [com.forager.app.domain.GetSeasonalPatternUseCase]. Fetched lazily on tab open, keyed on
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
     * [AvailabilitySearchResult][com.forager.app.domain.AvailabilitySearchResult], the type this
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
    val offlineMapRadiusKm: Int = 15,
    /**
     * A blank name defaults to "Region N" at download time — see
     * [AvailabilityViewModel.onDownloadOfflineMaps][com.forager.app.ui.availability.AvailabilityViewModel.onDownloadOfflineMaps] —
     * rather than requiring one, the same "default rather than require" pattern
     * [com.forager.app.domain.model.PlannedTrip.name] established for planned trips.
     */
    val offlineMapNameText: String = "",
    /** The picker's own last download attempt — see [OfflineMapStatus]'s doc comment. */
    val offlineDownloadStatus: OfflineMapStatus = OfflineMapStatus.Idle,
    /**
     * Every region currently on disk, per [com.forager.app.domain.OfflineMapRepository.listRegions]
     * — the persisted list the "Offline Maps" submenu renders, independent of
     * [offlineDownloadStatus]'s in-flight/last-attempt state.
     */
    val offlineRegions: List<OfflineRegionSummary> = emptyList(),
    /**
     * A region-*list-load* failure, not a download failure — see
     * [AvailabilityViewModel.loadOfflineRegions][com.forager.app.ui.availability.AvailabilityViewModel]'s
     * doc comment for the belief-changing distinction from [offlineDownloadStatus]. Also carries a
     * failed per-region delete, for the same reason: neither is something the user is mid-action on
     * the way a download is.
     */
    val offlineRegionsErrorMessage: String? = null,
    /**
     * The staleness badge threshold, in days, restored from
     * [com.forager.app.domain.MapPreferencesRepository.getStaleThresholdDays] — see
     * [DEFAULT_STALE_THRESHOLD_DAYS] until that load completes.
     */
    val offlineStaleThresholdDays: Int = DEFAULT_STALE_THRESHOLD_DAYS,
    /**
     * Whether the map renders in night mode — Settings' "Night Maps" checkbox, restored from
     * [com.forager.app.domain.MapPreferencesRepository.getNightModeMaps]. `false` (day) until that
     * load completes.
     */
    val nightModeMaps: Boolean = false,
    /**
     * The app's own theme choice — Settings' Light/Dark/System Default radio group, restored from
     * [com.forager.app.domain.AppThemePreferenceRepository.getThemeMode]. [AppThemeMode.LIGHT]
     * until that load completes. Independent of [nightModeMaps], which controls only the map's
     * basemap. [AppThemeMode.SYSTEM_DEFAULT] is resolved against the device's own theme in
     * `MainActivity`, not here — see [AppThemeMode]'s own doc comment.
     */
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    /**
     * The offline-region picker map's opening viewport before a region has been picked —
     * restored from [com.forager.app.domain.MapPreferencesRepository.getLastPickedRegion] at
     * startup, then overridden by the device's current location every time the picker is opened
     * (see [AvailabilityViewModel.onOfflineMapsOpened][com.forager.app.ui.availability.AvailabilityViewModel.onOfflineMapsOpened]),
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
     * The compass strip's own live position — continuously refreshed from
     * [com.forager.app.domain.LocationTracker.fixes] while this ViewModel is alive, distinct from
     * [locateMeStatus]'s one-shot fetch (still used for the map's own "center on me" action and its
     * permission-denied/unavailable messaging). `null` before a first fix arrives, or if location
     * permission isn't granted — the strip's own "Coordinates unavailable" text already covers that
     * state honestly; there is no separate denied/unavailable case duplicated here.
     */
    val liveLocation: LatLng? = null,
    /** See [liveLocation] — the same fix's altitude, `null` whenever the device didn't report one. */
    val liveAltitudeMeters: Double? = null,
    /**
     * The unit distances are displayed in, restored from
     * [com.forager.app.domain.DistanceUnitPreferenceRepository.getDistanceUnit] at startup and
     * persisted on every change — see [DistanceUnit]'s own doc comment for the bug this fixes
     * (a system theme switch, among other configuration changes, used to reset this to the
     * default because it was plain Compose state, not ViewModel/persisted state).
     */
    val distanceUnit: DistanceUnit = DistanceUnit.MILES,
) {
    val hasSearched: Boolean get() = region != null
}
