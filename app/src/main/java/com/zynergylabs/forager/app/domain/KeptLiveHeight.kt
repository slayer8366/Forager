package com.zynergylabs.forager.app.domain

/**
 * The live steadying across the barometer's stops (dispatch 2026-10-28-13, Amendment 4, RECORD -1808; the owner: "Keep
 * the steadying for 10 min (Recommended)"). The barometer stops on every pause, screen-off and tab change (Amendment 3,
 * "Map tab only"); the S22 read of the 10-29 walk found nearly every map window under 75 s, so a fresh first minute at
 * each return left the strip almost always on plain GPS. Pure, and holds no clock: every time comes with a fix or a
 * reading. A new class rather than a branch threaded through [LiveHeight] (CLAUDE.md, Building).
 *
 * - **What is kept** at a stop: a settled anchor, by [LiveHeight.keep]. An anchor still in its first minute is not
 *   kept, and the return starts fresh.
 * - **The absence** is measured from the last barometer reading before the stop to the first one after, both on the
 *   sensor's own clock (elapsedRealtime, which keeps counting with the screen off). Up to [LIVE_HEIGHT_KEEP_MILLIS], the
 *   steadying carries on from the kept anchor, steadied at once ([LiveHeight.resumedFrom]). Longer, or a clock that went
 *   backwards, starts fresh, as Amendment 3 had it for every return.
 * - **The pressure gap.** The barometer is off for the absence, so its old readings are dropped and the median restarts
 *   on new ones. The anchor is an offset (sea-level GPS over the barometer's height), not a height, so a climb while
 *   away is picked up in full by the new readings; only a weather change during the absence is missed.
 * - **A kept anchor that disagrees with the new GPS heights** is moved by the existing pull alone, nothing new tuned:
 *   dt / 60 min toward each fix, counted from the anchor's last pull, so across the gap; a fix more than 30 m off is
 *   skipped. After 9 minutes away the first fix moves it at most 15% of the disagreement.
 * - **Before the first reading back** a fix does not change the shown height (`null`): showing it would put plain GPS
 *   on the strip for a moment and then step when the reading decides. The hold ends after
 *   [LIVE_HEIGHT_RETURN_HOLD_MILLIS] of fixes; with no reading by then the steadying starts fresh, logged.
 * - While the barometer is stopped (another tab, where nothing shows the height) fixes are held too, the latest one
 *   kept and handed on when the return decides, and the 10 s count does not run.
 */
class KeptLiveHeight(private val errorLog: ErrorLog) {
    private var height = LiveHeight()
    private var kept: LiveHeight.Kept? = null
    private var barometerRunning = false
    private var heldFix: Pair<SeaLevelHeight, Long>? = null
    private var firstHeldFixAt: Long? = null

    /** The barometer started reading. */
    fun onBarometerStarted() {
        barometerRunning = true
        firstHeldFixAt = null
    }

    /**
     * The barometer stopped: a pause, the app leaving the foreground, or the Maps tab left. A second stop before a
     * return has decided keeps the first stop's anchor, so the absence is still measured from its last reading.
     */
    fun onBarometerStopped() {
        barometerRunning = false
        if (kept == null) kept = height.keep()
        height = LiveHeight()
    }

    /** Drops anything kept and begins a fresh steadying, with the latest held fix in it. Returns the height to show. */
    fun startAfresh(): ShownHeight? {
        kept = null
        height = LiveHeight()
        return handOnHeldFix()
    }

    fun onPressure(reading: PressureReading): ShownHeight? {
        kept?.let { k ->
            kept = null
            val absence = reading.elapsedRealtimeNanos - k.lastReadingNanos
            height = if (absence in 0..LIVE_HEIGHT_KEEP_MILLIS * NANOS_PER_MILLI) LiveHeight.resumedFrom(k) else LiveHeight()
            handOnHeldFix()
        }
        return height.onPressure(reading)
    }

    fun onNoBarometer(): ShownHeight? {
        if (kept != null) startAfresh()
        return height.onNoBarometer()
    }

    /** A fix's sea-level height, at the fix's own time. `null` holds the height shown now (see the class comment). */
    fun onFix(seaLevel: SeaLevelHeight, atEpochMillis: Long): ShownHeight? {
        if (kept == null) return height.onFix(seaLevel, atEpochMillis)
        heldFix = seaLevel to atEpochMillis
        if (!barometerRunning) return null
        val first = firstHeldFixAt ?: atEpochMillis.also { firstHeldFixAt = it }
        if (atEpochMillis - first <= LIVE_HEIGHT_RETURN_HOLD_MILLIS) return null
        errorLog.w(TAG, "no barometer reading within ${LIVE_HEIGHT_RETURN_HOLD_MILLIS / 1_000} s of the return; the kept anchor is dropped and plain GPS shown", IllegalStateException("no barometer reading after the return"))
        return startAfresh()
    }

    private fun handOnHeldFix(): ShownHeight? {
        val held = heldFix
        heldFix = null
        firstHeldFixAt = null
        return held?.let { (seaLevel, at) -> height.onFix(seaLevel, at) }
    }

    internal companion object {
        const val TAG = "ForagerLiveHeight"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}

/**
 * How long a settled steadying is kept after the barometer stops (Amendment 4, RECORD -1808). The owner's figure: "Keep
 * the steadying for 10 min (Recommended)". Past it the return starts fresh.
 */
const val LIVE_HEIGHT_KEEP_MILLIS: Long = 10 * 60_000L

/**
 * How long, back on the map with a kept anchor, the strip holds its height waiting for the barometer's first reading,
 * timed from the first fix back (RECORD -1809; the owner: "10 seconds (Recommended)"). Why a hold: the sensor batches
 * its readings up to 5 s, so the first fix can come before the first reading, and showing that fix would put plain GPS
 * on the strip and then step to the steadied height when the reading lands. Why a cap: a barometer that never speaks
 * must not freeze the strip; past it the steadying starts fresh and shows plain GPS, as with no barometer.
 */
const val LIVE_HEIGHT_RETURN_HOLD_MILLIS: Long = 10_000L
