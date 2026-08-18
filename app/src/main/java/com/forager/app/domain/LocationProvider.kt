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
    /**
     * [altitude] is `null` whenever the underlying fix didn't report one — genuinely "not
     * provided" (e.g. `Location.hasAltitude()` is false on network-based fixes), never defaulted
     * to a guessed value, per CLAUDE.md's rule against fabricating a plausible-looking value for
     * an unsupported/absent capability.
     */
    data class Success(val lat: Double, val lng: Double, val altitude: Double? = null) : LocationResult
    data object PermissionDenied : LocationResult
    data object LocationUnavailable : LocationResult
}
