package com.forager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forager.app.domain.ErrorLog
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackRecordingMode
import com.forager.app.service.TrackRecordingService
import com.forager.app.ui.availability.AvailabilityScreen
import com.forager.app.ui.availability.AvailabilityViewModel
import com.forager.app.ui.log.CartographyViewModel
import com.forager.app.ui.log.MushroomLogViewModel
import com.forager.app.ui.theme.ForagerTheme
import com.forager.app.ui.track.TrackRecordingViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as ForagerApplication).container

    /**
     * The real [ErrorLog] production wires in — see that interface's own doc comment for why
     * [AvailabilityViewModel]/[TrackRecordingViewModel] don't call [Log] directly.
     */
    private val androidErrorLog = ErrorLog { tag, message, error -> Log.w(tag, message, error) }

    private val viewModel: AvailabilityViewModel by viewModels {
        viewModelFactory {
            initializer {
                AvailabilityViewModel(
                    container.locationProvider,
                    container.locationTracker,
                    container.getAvailabilityUseCase,
                    container.getRecentSearchesUseCase,
                    container.getSightingsUseCase,
                    container.searchTaxaUseCase,
                    container.getConditionsUseCase,
                    container.getTripWindowsUseCase,
                    container.getPlannedTripsUseCase,
                    container.savePlannedTripUseCase,
                    container.deletePlannedTripUseCase,
                    container.getSeasonalPatternUseCase,
                    container.offlineMapRepository,
                    androidErrorLog,
                    container.mapPreferencesRepository,
                    container.unitSystemPreferenceRepository,
                    container.appThemePreferenceRepository,
                    container.getTodaysForecastUseCase,
                    getOfflineRegionReferenceCount = { id -> container.getEntryReferenceCountUseCase.forOfflineRegion(id).getOrDefault(0) },
                )
            }
        }
    }

    private val mushroomLogViewModel: MushroomLogViewModel by viewModels {
        viewModelFactory {
            initializer {
                MushroomLogViewModel(
                    container.getMushroomLogEntriesUseCase,
                    container.getDraftEntriesUseCase,
                    container.createMushroomLogEntryUseCase,
                    container.startEditingLogEntryUseCase,
                    container.saveMushroomLogEntryUseCase,
                    container.commitDraftEntryUseCase,
                    container.deleteMushroomLogEntryUseCase,
                    container.addPhotoToLogEntryUseCase,
                    container.addPhotoToGalleryUseCase,
                    container.removePhotoFromLogEntryUseCase,
                    container.getGalleryPhotosUseCase,
                    container.pullPhotoIntoEntryUseCase,
                    container.deleteGalleryPhotoUseCase,
                    container.locationProvider,
                    container.updatePhotoLocationUseCase,
                    getPhotoEntryReferenceCount = { id -> container.getEntryReferenceCountUseCase.forPhoto(id).getOrDefault(0) },
                )
            }
        }
    }

    private val cartographyViewModel: CartographyViewModel by viewModels {
        viewModelFactory {
            initializer {
                CartographyViewModel(
                    container.getCartographyEntriesUseCase,
                    container.getCartographyDraftEntriesUseCase,
                    container.createCartographyEntryUseCase,
                    container.saveCartographyEntryUseCase,
                    container.getCartographyEntryUseCase,
                    container.commitCartographyEntryUseCase,
                    container.deleteCartographyEntryUseCase,
                    container.getDerivedTripUseCase,
                    container.getTripReportOfflineRegionsUseCase,
                    container.computeTrackStatisticsUseCase,
                )
            }
        }
    }

    private val trackRecordingViewModel: TrackRecordingViewModel by viewModels {
        viewModelFactory {
            initializer {
                TrackRecordingViewModel(
                    container.trackRepository,
                    container.startTrackUseCase,
                    container.getWaypointsUseCase,
                    container.createWaypointUseCase,
                    container.deleteWaypointUseCase,
                    container.computeReturnToStartUseCase,
                    container.detectOffTrackUseCase,
                    container.locationTracker,
                    container.getTracksUseCase,
                    container.alertDelivery,
                    container.alertAudibility,
                    androidErrorLog,
                    getWaypointReferenceCount = { id -> container.getEntryReferenceCountUseCase.forWaypoint(id).getOrDefault(0) },
                )
            }
        }
    }

    /**
     * Android 13+ only shows a foreground service's notification with this permission granted;
     * without it the service still runs (recording isn't blocked), it just runs silently. Requested
     * once, right when a recording actually starts, rather than at app launch — there's nothing to
     * show a notification for until then. A denial doesn't stop the recording: see
     * [TrackRecordingService]'s own notification, which the OS simply won't display if this was
     * never granted.
     */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* No follow-up either way — see this property's own doc comment. */ }

    /**
     * Which action the OS permission dialog is standing in front of — [requestLocationPermission]
     * is one shared launcher (Android only allows one in-flight request per contract per Activity),
     * so this is how its grant/deny callback below routes to the right [viewModel] method. Set
     * immediately before every `launch` call site.
     */
    private var pendingLocationAction = PendingLocationAction.USE_CURRENT_LOCATION

    private enum class PendingLocationAction { USE_CURRENT_LOCATION, LOCATE_ME }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        // First-launch dispatch: the grant is the event the compass strip's live-fix collection
        // has to restart on — see AvailabilityViewModel.onLocationPermissionGranted's own doc
        // comment. Reported before the per-action call so the stream is live by the time the
        // one-shot below resolves; a no-op when the collection is already running. The denied
        // branches are exactly what they were.
        if (granted) viewModel.onLocationPermissionGranted()
        when (pendingLocationAction) {
            PendingLocationAction.USE_CURRENT_LOCATION ->
                if (granted) viewModel.useCurrentLocation() else viewModel.onPermissionDenied()
            PendingLocationAction.LOCATE_ME ->
                if (granted) viewModel.locateMe() else viewModel.onLocateMePermissionDenied()
        }
    }

    /**
     * Same check, same two permissions, as
     * [com.forager.app.location.AndroidLocationProvider.hasLocationPermission] — not shared code
     * across an Activity/domain-layer boundary that owns neither Context nor Manifest, matching
     * that class's own doc comment on why (see also [TrackRecordingService]'s own copy, and
     * `com.forager.app.ui.map.SightingsMap.kt`'s).
     *
     * Two call sites below both gate on this rather than trusting a single check: this one, right
     * before [TrackRecordingViewModel.startRecording] is called at all (the confirmed crash's
     * fix — recording never begins without permission, so [TrackRecordingUiState.activeTrack]
     * never gets set), and a second inside the `LaunchedEffect` that actually issues
     * `startForegroundService` (defence against permission being revoked in the narrow window
     * between the two — that path also rolls the ViewModel's state back if it fires, so
     * `isRecording` can never report true for a service that didn't actually start).
     */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The off-track alert's channel is created by AndroidAlertDelivery when AppContainer
        // builds it (alert-delivery dispatch) — nothing alert-related lives in this Activity now.
        setContent {
            // Read before ForagerTheme wraps content, not inside it: themeMode is this state's own
            // AvailabilityUiState.themeMode (Settings' Light/Dark/System Default choice), so
            // ForagerTheme needs the resolved boolean below rather than the other way around.
            val uiState by viewModel.uiState.collectAsState()
            // AppThemeMode.SYSTEM_DEFAULT is the one choice this app doesn't store as an explicit
            // light/dark value — it means "follow the device" — and isSystemInDarkTheme() is a
            // @Composable-only signal (backed by LocalConfiguration), so this resolution has to
            // happen here, not in the ViewModel or domain/. AppThemeMode.LIGHT/DARK stay direct,
            // device-independent choices either way.
            val systemInDarkTheme = isSystemInDarkTheme()
            val effectiveDarkTheme = when (uiState.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM_DEFAULT -> systemInDarkTheme
            }
            // enableEdgeToEdge() makes the system bars transparent and lets this app's own
            // background show through underneath them instead of a separately-colored system bar —
            // called here, keyed on effectiveDarkTheme via SideEffect (a synchronous per-recomposition
            // effect, not LaunchedEffect: this call isn't suspending), rather than once with no
            // arguments in onCreate() as before.
            //
            // The no-arg call this replaces picks each bar's icon appearance from the *device's*
            // system dark-mode resource qualifier at the single moment it runs (SystemBarStyle.auto's
            // own default detectDarkMode) — not from this app's own resolved theme, which can also
            // change at runtime (a Light/Dark/System Default switch, or the device's own theme
            // changing while System Default is selected) without recreating this Activity.
            // LocalForagerDarkTheme's own doc comment already names this exact mistake for a
            // different set of call sites (map screen colors reading the device theme instead of
            // this state) as a real, previously-hit bug; this is the same mistake in the system bar
            // icon appearance. A device in system dark mode with this app's own theme set to Light
            // produces exactly the "status bar is white and washes out the notifications" hardware
            // report this fixes: a light (effectiveDarkTheme == false) app background under status
            // bar icons chosen for a dark background (light/white icons, from the device's dark
            // system setting) — invisible against white.
            SideEffect {
                val statusBarStyle = if (effectiveDarkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = statusBarStyle)
            }
            ForagerTheme(darkTheme = effectiveDarkTheme) {
                val logUiState by mushroomLogViewModel.uiState.collectAsState()
                val trackUiState by trackRecordingViewModel.uiState.collectAsState()
                val cartographyUiState by cartographyViewModel.uiState.collectAsState()

                // Starts/stops the actual foreground service as a side effect of
                // TrackRecordingViewModel's own state, mirroring the locateMeStatus LaunchedEffect
                // below — the ViewModel owns the Track row (via StartTrackUseCase), this Activity
                // owns the Context-level action of running the service. hasStartedRecordingOnce
                // guards against firing a spurious ACTION_STOP on first composition, when
                // activeTrack is null simply because nothing has ever started yet.
                var hasStartedRecordingOnce by remember { mutableStateOf(false) }
                LaunchedEffect(trackUiState.activeTrack) {
                    val active = trackUiState.activeTrack
                    val intent = Intent(this@MainActivity, TrackRecordingService::class.java)
                    if (active != null) {
                        // Re-checked here, not just in onToggleRecording below: this is the exact
                        // call that would otherwise reproduce the confirmed FGS-location-type
                        // crash, and it runs asynchronously after that first check — see
                        // hasLocationPermission()'s own doc comment on why both exist. Rolling
                        // back through the ViewModel (rather than only skipping the service start)
                        // is what keeps isRecording from reporting true for a service that never
                        // actually started.
                        if (hasLocationPermission()) {
                            hasStartedRecordingOnce = true
                            intent.action = TrackRecordingService.ACTION_START
                            intent.putExtra(TrackRecordingService.EXTRA_TRACK_ID, active.trackId)
                            intent.putExtra(TrackRecordingService.EXTRA_MODE, active.mode.name)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else {
                            trackRecordingViewModel.onStartRecordingPermissionDenied(
                                getString(R.string.track_recording_needs_location),
                            )
                            trackRecordingViewModel.stopRecording()
                        }
                    } else if (hasStartedRecordingOnce) {
                        intent.action = TrackRecordingService.ACTION_STOP
                        startService(intent)
                    }
                }

                // The off-track alert is no longer delivered from here (alert-delivery dispatch):
                // a LaunchedEffect keyed on a ViewModel counter only ran while this Activity was
                // STARTED, so a pocketed phone got the alert when its screen came on, not when it
                // strayed. TrackRecordingViewModel.returnToStart now calls AlertDelivery directly.

                AvailabilityScreen(
                    uiState = uiState,
                    onUseCurrentLocation = {
                        pendingLocationAction = PendingLocationAction.USE_CURRENT_LOCATION
                        requestLocationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onLocateMe = {
                        pendingLocationAction = PendingLocationAction.LOCATE_ME
                        requestLocationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onManualLatChanged = viewModel::onManualLatChanged,
                    onManualLngChanged = viewModel::onManualLngChanged,
                    onSearchManualCoordinates = viewModel::searchManualCoordinates,
                    onRadiusChanged = viewModel::onRadiusChanged,
                    onMonthSelected = viewModel::onMonthSelected,
                    onMapTabSelected = viewModel::onMapTabSelected,
                    onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
                    onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
                    onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
                    onPlaceTripPin = viewModel::onPlaceTripPin,
                    onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                    onRecentSearchSelected = viewModel::onRecentSearchSelected,
                    currentTime = container.currentTimeProvider,
                    onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
                    onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                    onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                    onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                    onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                    onDistanceUnitSelected = viewModel::onDistanceUnitSelected,
                    onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                    onThemeModeChanged = viewModel::onThemeModeChanged,
                    onMapFullscreenChanged = viewModel::onMapFullscreenChanged,
                    logUiState = logUiState,
                    cameraCaptureFiles = container.cameraCaptureFiles,
                    onStartLogEntry = mushroomLogViewModel::onStartNewEntry,
                    onOpenLogEntry = mushroomLogViewModel::onOpenEntry,
                    onCloseLogEntry = mushroomLogViewModel::onCloseEntry,
                    onLogEntryChanged = mushroomLogViewModel::onEntryEdited,
                    onStartEditingLogEntry = mushroomLogViewModel::onStartEditingEntry,
                    onOpenLogEntryForEditing = mushroomLogViewModel::onOpenEntryForEditing,
                    onSaveLogEntry = mushroomLogViewModel::onSaveEntry,
                    onCancelLogEntryEditing = mushroomLogViewModel::onCancelEditing,
                    onLeaveLogEntryEditingIncidentally = mushroomLogViewModel::onLeaveEditingIncidentally,
                    onDiscardLogDraft = mushroomLogViewModel::onDeleteEntry,
                    onAddLogPhoto = mushroomLogViewModel::onAddPhoto,
                    onRemoveLogPhoto = mushroomLogViewModel::onRemovePhoto,
                    onPullLogPhoto = mushroomLogViewModel::onPullPhoto,
                    onDeleteLogEntry = mushroomLogViewModel::onDeleteEntry,
                    onDeleteGalleryPhoto = mushroomLogViewModel::onDeleteGalleryPhoto,
                    onAddGalleryPhoto = mushroomLogViewModel::onAddGalleryPhoto,
                    onSaveLogErrorDismissed = mushroomLogViewModel::onSaveErrorDismissed,
                    cartographyUiState = cartographyUiState,
                    onOpenCartographyEntry = cartographyViewModel::onOpenEntry,
                    onStartCartographyEntry = cartographyViewModel::onStartEntry,
                    onCloseCartographyEntry = cartographyViewModel::onCloseEntry,
                    onCartographyTextChanged = cartographyViewModel::onTextChanged,
                    onCartographyTagsChanged = cartographyViewModel::onTagsChanged,
                    onSetFindDecision = cartographyViewModel::onSetFindDecision,
                    onSetTrackDecision = cartographyViewModel::onSetTrackDecision,
                    onSetWaypointDecision = cartographyViewModel::onSetWaypointDecision,
                    onSetOfflineRegionDecision = cartographyViewModel::onSetOfflineRegionDecision,
                    onToggleKeptPhoto = cartographyViewModel::onToggleKeptPhoto,
                    // Entry-photo-acquisition dispatch, Item 2: composed here, the one place both
                    // ViewModels are already visible, rather than giving CartographyViewModel its
                    // own copy of AddPhotoToGalleryUseCase — see MushroomLogViewModel.onAddGalleryPhoto's
                    // own doc comment on `onPersisted` for why that would leave
                    // MushroomLogUiState.galleryPhotos stale. Persist (and, for a camera capture,
                    // the GPS patch) is entirely MushroomLogViewModel's own existing, unmodified
                    // pipeline; only the id comes back here, to attach it to whichever entry
                    // CartographyViewModel currently has open.
                    onAcquirePhotoForCartographyEntry = { source ->
                        mushroomLogViewModel.onAddGalleryPhoto(source) { id -> cartographyViewModel.onToggleKeptPhoto(id) }
                    },
                    onFinishCartographyEntry = cartographyViewModel::onFinishEntry,
                    onSaveCartographyEntry = cartographyViewModel::onSaveEntry,
                    onDiscardCartographyEntryChanges = cartographyViewModel::onDiscardEntryChanges,
                    onSaveCartographyEntryAsDraft = cartographyViewModel::onSaveEntryAsDraft,
                    onDeleteCartographyEntry = cartographyViewModel::onDeleteEntry,
                    getCartographyEntryMapData = { entry, photos -> container.getCartographyEntryMapDataUseCase(entry, photos) },
                    getCartographyEntryOfflineRegion = { entry, points -> container.getCartographyEntryOfflineRegionUseCase(entry, points) },
                    getCartographyEntryCurrentLocation = { container.locationProvider.getCurrentLocation() },
                    isRecording = trackUiState.isRecording,
                    onToggleRecording = {
                        if (trackUiState.isRecording) {
                            trackRecordingViewModel.stopRecording()
                        } else if (!hasLocationPermission()) {
                            // Confirmed crash's primary fix: never even ask the ViewModel to start
                            // (never creates the Track row, never sets activeTrack) when the
                            // foreground service could not possibly start without crashing — see
                            // hasLocationPermission()'s own doc comment.
                            trackRecordingViewModel.onStartRecordingPermissionDenied(
                                getString(R.string.track_recording_needs_location),
                            )
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            // Field-test dispatch item 3: BALANCED's 15s/15m gate produced tracks
                            // too sparse to evaluate against Gaia's own recording. Testers already
                            // run Gaia concurrently, so the extra GPS draw is a cost already
                            // committed either way — see startRecording()'s own default-parameter
                            // doc comment for why this is an explicit override here rather than a
                            // changed default: a future non-tester-build caller should still get
                            // BALANCED unless it deliberately asks otherwise.
                            trackRecordingViewModel.startRecording(TrackRecordingMode.HIGH_ACCURACY)
                        }
                    },
                    startRecordingErrorMessage = trackUiState.startRecordingErrorMessage,
                    tripStartWarning = trackUiState.tripStartWarning,
                    networkFixesNotice = trackUiState.networkFixesNotice,
                    breadcrumbPoints = trackUiState.breadcrumbPoints.map { LatLng(it.lat, it.lng) },
                    waypoints = trackUiState.waypoints,
                    waypointsErrorMessage = trackUiState.waypointsErrorMessage,
                    waypointEntryReferenceCounts = trackUiState.waypointEntryReferenceCounts,
                    onDropWaypoint = { location, name -> trackRecordingViewModel.addWaypoint(location.lat, location.lng, name) },
                    onDeleteWaypoint = trackRecordingViewModel::removeWaypoint,
                    returnToStart = trackUiState.returnToStart,
                    isReturning = trackUiState.isReturning,
                    isOffTrack = trackUiState.isOffTrack,
                    onToggleReturning = {
                        if (trackUiState.isReturning) trackRecordingViewModel.stopReturn() else trackRecordingViewModel.startReturn()
                    },
                    compassProvider = container.compassProvider,
                    computeTrueHeading = container.computeTrueHeadingUseCase,
                    navigationTarget = trackUiState.originWaypoint,
                    crashFileStore = container.crashFileStore,
                    tracks = trackUiState.tracks,
                    onTracksOpened = trackRecordingViewModel::loadTracks,
                    getFullRecord = trackRecordingViewModel::getFullRecord,
                )
            }
        }
    }
}
