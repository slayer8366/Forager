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
import com.forager.app.ui.theme.ForagerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AvailabilityViewModel by viewModels {
        val container = (application as ForagerApplication).container
        viewModelFactory {
            initializer {
                AvailabilityViewModel(
                    container.locationProvider,
                    container.predictAvailabilityUseCase,
                    container.getSightingsUseCase,
                    container.searchTaxaUseCase,
                    container.getConditionsUseCase,
                    container.clusterForagingAreasUseCase,
                    container.getTripWindowsUseCase,
                    container.getPlannedTripsUseCase,
                    container.savePlannedTripUseCase,
                    container.deletePlannedTripUseCase,
                    container.getSeasonalPatternUseCase,
                )
            }
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.useCurrentLocation()
        } else {
            viewModel.onPermissionDenied()
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
                AvailabilityScreen(
                    uiState = uiState,
                    onUseCurrentLocation = {
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
                )
            }
        }
    }
}
