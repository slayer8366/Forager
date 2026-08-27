package com.forager.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forager.app.domain.ErrorLog
import com.forager.app.domain.model.LatLng
import com.forager.app.service.TrackRecordingService
import com.forager.app.ui.availability.AvailabilityScreen
import com.forager.app.ui.availability.AvailabilityViewModel
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
                    container.clusterForagingAreasUseCase,
                    container.getTripWindowsUseCase,
                    container.getPlannedTripsUseCase,
                    container.savePlannedTripUseCase,
                    container.deletePlannedTripUseCase,
                    container.getSeasonalPatternUseCase,
                    container.offlineMapRepository,
                    androidErrorLog,
                    container.mapPreferencesRepository,
                    container.distanceUnitPreferenceRepository,
                    container.appThemePreferenceRepository,
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
                    container.removePhotoFromLogEntryUseCase,
                    container.getGalleryPhotosUseCase,
                    container.pullPhotoIntoEntryUseCase,
                    container.deleteGalleryPhotoUseCase,
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
                    androidErrorLog,
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
        // Without this, the system nav bar stays whatever the platform default is — light on
        // most devices — regardless of this app's own theme, which is why it read as a stray
        // white bar under an otherwise dark screen. enableEdgeToEdge() makes it transparent and
        // switches its icon color to match light/dark automatically, and lets this app's own
        // background show through underneath it instead of a separately-colored system bar.
        enableEdgeToEdge()
        setContent {
            // Read before ForagerTheme wraps content, not inside it: darkTheme is this state's own
            // AvailabilityUiState.darkTheme (Settings' "Night Mode" checkbox), so ForagerTheme needs
            // it to pick a color scheme rather than the other way around.
            val uiState by viewModel.uiState.collectAsState()
            ForagerTheme(darkTheme = uiState.darkTheme) {
                val logUiState by mushroomLogViewModel.uiState.collectAsState()
                val trackUiState by trackRecordingViewModel.uiState.collectAsState()

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
                    onToggleForagingAreas = viewModel::onToggleForagingAreas,
                    onCategorySelected = viewModel::onCategorySelected,
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
                    onDarkThemeChanged = viewModel::onDarkThemeChanged,
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
                    onSaveLogErrorDismissed = mushroomLogViewModel::onSaveErrorDismissed,
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
                            trackRecordingViewModel.startRecording()
                        }
                    },
                    startRecordingErrorMessage = trackUiState.startRecordingErrorMessage,
                    breadcrumbPoints = trackUiState.breadcrumbPoints.map { LatLng(it.lat, it.lng) },
                    waypoints = trackUiState.waypoints,
                    waypointsErrorMessage = trackUiState.waypointsErrorMessage,
                    onDropWaypoint = { location, name -> trackRecordingViewModel.addWaypoint(location.lat, location.lng, name) },
                    onDeleteWaypoint = trackRecordingViewModel::removeWaypoint,
                    returnToStart = trackUiState.returnToStart,
                    isReturning = trackUiState.isReturning,
                    isOffTrack = trackUiState.isOffTrack,
                    onToggleReturning = {
                        if (trackUiState.isReturning) trackRecordingViewModel.stopReturn() else trackRecordingViewModel.startReturn()
                    },
                    compassProvider = container.compassProvider,
                    crashFileStore = container.crashFileStore,
                )
            }
        }
    }
}
