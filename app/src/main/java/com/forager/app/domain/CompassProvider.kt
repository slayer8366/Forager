package com.forager.app.domain

import kotlinx.coroutines.flow.Flow

/**
 * Owned abstraction over the device compass. Domain and UI code depend on this interface, never on
 * android.hardware.SensorManager directly, so the compass+elevation strip can be exercised without
 * a real sensor (CLAUDE.md: isolate hardware/integration layers behind a driver interface this
 * project owns).
 */
interface CompassProvider {
    /**
     * Heading in degrees clockwise from magnetic north, in `[0, 360)`, emitted on every sensor
     * update while collected. `null` when this device has no usable rotation sensor (neither a
     * rotation-vector sensor nor an accelerometer+magnetometer pair) — an explicit "unsupported"
     * per CLAUDE.md, never a fabricated or stale last-known value.
     */
    val heading: Flow<Float?>
}
