package com.forager.app.sensor

import android.hardware.GeomagneticField
import com.forager.app.domain.DeclinationProvider

/**
 * [DeclinationProvider] over the platform's own [GeomagneticField] — the World Magnetic Model
 * shipped inside the Android framework, evaluated on-device with no network and no sensor. The
 * only thing this class does beyond calling it is the `null`-altitude rule
 * [DeclinationProvider.declinationDegrees] documents (sea level), and unit conversion: the platform
 * takes `float` degrees and metres, this project's domain carries `Double`s.
 *
 * `getDeclination()` is positive east, exactly the convention [DeclinationProvider] documents —
 * no sign adjustment here, and `AndroidDeclinationProviderTest` pins that against two real sites
 * of opposite sign. That test runs under Robolectric, which executes the framework's real
 * `GeomagneticField` (the `android-all` jar carries the full class, and Robolectric ships no shadow
 * for it — checked against the pinned jars, not assumed), so it exercises this exact code path.
 */
class AndroidDeclinationProvider : DeclinationProvider {
    override fun declinationDegrees(latitude: Double, longitude: Double, altitudeMeters: Double?, epochMillis: Long): Float =
        GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            (altitudeMeters ?: SEA_LEVEL_METERS).toFloat(),
            epochMillis,
        ).declination

    private companion object {
        /** See [DeclinationProvider.declinationDegrees] on why a missing altitude evaluates at sea level. */
        const val SEA_LEVEL_METERS = 0.0
    }
}
