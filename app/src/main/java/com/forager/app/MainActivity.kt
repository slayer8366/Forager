package com.forager.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forager.app.ui.availability.AvailabilityScreen
import com.forager.app.ui.availability.AvailabilityViewModel
import com.forager.app.ui.log.MushroomLogViewModel
import com.forager.app.ui.theme.ForagerTheme

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as ForagerApplication).container

    private val viewModel: AvailabilityViewModel by viewModels {
        viewModelFactory {
            initializer {
                AvailabilityViewModel(
                    container.locationProvider,
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
                )
            }
        }
    }
}
