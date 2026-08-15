package com.forager.app.ui.availability

import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import java.time.LocalDate

data class AvailabilityUiState(
    val region: Region? = null,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val radiusKm: Int = 15,
    val manualLatText: String = "",
    val manualLngText: String = "",
    val forecast: AvailabilityForecast? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val locationPermissionDenied: Boolean = false,
) {
    val hasSearched: Boolean get() = region != null
}
