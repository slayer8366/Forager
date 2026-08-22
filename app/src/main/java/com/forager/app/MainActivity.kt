package com.forager.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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
import com.forager.app.domain.model.LatLng
import com.forager.app.service.TrackRecordingService
import com.forager.app.ui.availability.AvailabilityScreen
import com.forager.app.ui.availability.AvailabilityViewModel
import com.forager.app.ui.log.MushroomLogViewModel
import com.forager.app.ui.theme.ForagerTheme
import com.forager.app.ui.track.TrackRecordingViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as ForagerApplication).container

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
                )
            }
        }
    }

    private val mushroomLogViewModel: MushroomLogViewModel by viewModels {
        viewModelFactory {
            initializer {
                MushroomLogViewModel(
                    container.getMushroomLogEntriesUseCase,
                    container.createMushroomLogEntryUseCase,
                    container.saveMushroomLogEntryUseCase,
                    container.deleteMushroomLogEntryUseCase,
                    container.addPhotoToLogEntryUseCase,
                    container.removePhotoFromLogEntryUseCase,
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Without this, the system nav bar stays whatever the platform default is — light on
        // most devices — regardless of this app's own theme, which is why it read as a stray
        // white bar under an otherwise dark screen. enableEdgeToEdge() makes it transparent and
        // switches its icon color to match light/dark automatically, and lets this app's own
        // background show through underneath it instead of a separately-colored system bar.
        enableEdgeToEdge()
        setContent {
            ForagerTheme {
                val uiState by viewModel.uiState.collectAsState()
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
                        hasStartedRecordingOnce = true
                        intent.action = TrackRecordingService.ACTION_START
                        intent.putExtra(TrackRecordingService.EXTRA_TRACK_ID, active.trackId)
                        intent.putExtra(TrackRecordingService.EXTRA_MODE, active.mode.name)
                        ContextCompat.startForegroundService(this@MainActivity, intent)
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
                    onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                    onDeleteOfflineMaps = viewModel::onDeleteOfflineMaps,
                    logUiState = logUiState,
                    cameraCaptureFiles = container.cameraCaptureFiles,
                    onStartLogEntry = mushroomLogViewModel::onStartNewEntry,
                    onOpenLogEntry = mushroomLogViewModel::onOpenEntry,
                    onCloseLogEntry = mushroomLogViewModel::onCloseEntry,
                    onLogEntryChanged = mushroomLogViewModel::onEntryEdited,
                    onAddLogPhoto = mushroomLogViewModel::onAddPhoto,
                    onRemoveLogPhoto = mushroomLogViewModel::onRemovePhoto,
                    onDeleteLogEntry = mushroomLogViewModel::onDeleteEntry,
                    isRecording = trackUiState.isRecording,
                    onToggleRecording = {
                        if (trackUiState.isRecording) {
                            trackRecordingViewModel.stopRecording()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            trackRecordingViewModel.startRecording()
                        }
                    },
                    breadcrumbPoints = trackUiState.breadcrumbPoints.map { LatLng(it.lat, it.lng) },
                    waypoints = trackUiState.waypoints,
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
