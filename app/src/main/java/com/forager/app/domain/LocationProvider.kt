package com.forager.app.domain

/**
 * Owned abstraction over device location. Domain and UI code depend on this interface, never
 * on android.location directly, so the availability prediction flow can be exercised without
 * a real device (CLAUDE.md: isolate hardware/integration layers behind a driver interface).
 */
interface LocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}

sealed interface LocationResult {
    data class Success(val lat: Double, val lng: Double) : LocationResult
    data object PermissionDenied : LocationResult
    data object LocationUnavailable : LocationResult
}
