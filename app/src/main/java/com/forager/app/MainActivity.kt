package com.forager.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                )
            }
        }
    }
}
