package com.zynergylabs.forager.app.domain

/**
 * The live steadying across the barometer's stops (dispatch 2026-10-28-13, Amendment 4, RECORD -1808). Test-first seam:
 * for now every stop and every start begins a fresh [LiveHeight], exactly as before this class existed.
 */
class KeptLiveHeight(@Suppress("unused") private val errorLog: ErrorLog) {
    private var height = LiveHeight()

    /** The barometer started reading. */
    fun onBarometerStarted() = Unit

    /** The barometer stopped: a pause, the app leaving the foreground, or the Maps tab left. */
    fun onBarometerStopped() {
        height = LiveHeight()
    }

    /** Drops anything kept and begins a fresh steadying. */
    fun startAfresh(): ShownHeight? {
        height = LiveHeight()
        return null
    }

    fun onPressure(reading: PressureReading): ShownHeight? = height.onPressure(reading)

    fun onNoBarometer(): ShownHeight? = height.onNoBarometer()

    /** A fix's sea-level height. `null` holds the height shown now. */
    fun onFix(seaLevel: SeaLevelHeight, atEpochMillis: Long): ShownHeight? = height.onFix(seaLevel, atEpochMillis)
}
