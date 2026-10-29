package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.KeptLiveHeight
import com.zynergylabs.forager.app.domain.LastKnownLocationSource
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.PressureProvider
import com.zynergylabs.forager.app.domain.SeaLevelConverter
import com.zynergylabs.forager.app.domain.ShownHeight
import com.zynergylabs.forager.app.domain.acceptLiveFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The live fix of [AvailabilityViewModel] (UI split Phase 2.4, PR 9, dispatch 2026-10-19-33, RECORD -1453),
 * moved out of the ViewModel unchanged: the one collection of [LocationTracker.fixes] ([liveFixJob]), the
 * gate that writes the accepted fix and holds a refused one as the approximate reading, and the platform's
 * last known position read at each start. Every write to the screen's state goes through [updateState], and
 * the collection launches in [scope], the ViewModel's own `viewModelScope`, read each time rather than kept.
 *
 * **Whether the collection starts or stops is not decided here** (RECORD -1450's reading, applied by the
 * planner to this series). `onEnteredForeground`, `onLeftForeground` and `onLocationPermissionGranted` keep
 * the foreground flag and every condition, in their order, and call [begin], [isCollecting] and [cancel]
 * where they used the job before. Each is synchronous.
 *
 * **The live height** (dispatch 2026-10-28-13, RECORD -1792): each accepted fix's height is converted to sea level by
 * [seaLevelConverter] and, with the barometer's readings from [pressureProvider], steadied by [LiveHeight] into
 * [AvailabilityUiState.shownHeight]. A refused fix is not used for height, as it is not used for anything that acts.
 *
 * **When the barometer is read** (Amendment 3, RECORD -1797; the owner: "Map tab only (Recommended)"): here, only while
 * the Maps tab is showing ([onMapsTabShownChanged]) and the live fix is collected (the app in the foreground, the
 * permission held). The recording service reads its own while recording, on any tab, unchanged. Each time this
 * collection stops, the steadying starts afresh ([LiveHeight] has no barometer, so fixes are shown as they come): back
 * on the map, the first minute shows each fix's converted GPS height and then blends into the steadied one (N1's
 * rule), never an anchor or readings from before the tab was left.
 */
internal class AvailabilityLiveLocation(
    private val updateState: ((AvailabilityUiState) -> AvailabilityUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val locationTracker: LocationTracker,
    private val lastKnownLocation: LastKnownLocationSource,
    private val pressureProvider: PressureProvider,
    private val seaLevelConverter: SeaLevelConverter,
    private val errorLog: ErrorLog,
) {

    /**
     * The one live collection of [LocationTracker.fixes] feeding [AvailabilityUiState.liveFix] —
     * see [begin]. Tracked so [AvailabilityViewModel.onLocationPermissionGranted] can tell "still collecting"
     * from "collected once, completed, nothing listening any more", which is the first-launch bug.
     */
    private var liveFixJob: Job? = null

    /** The live steadying, kept across the barometer's stops ([KeptLiveHeight]). */
    private val height = KeptLiveHeight(errorLog)

    /** The barometer's collection, a child of [liveFixJob], running only while [mapsTabShown]. */
    private var barometerJob: Job? = null
    private var mapsTabShown = false
    private var fixesEnded = false

    /** Whether the live collection is running: the ViewModel's former `liveFixJob?.isActive == true`. */
    val isCollecting: Boolean
        get() = liveFixJob?.isActive == true

    /** Stops the live collection: [AvailabilityViewModel.onLeftForeground]'s former two lines, moved unchanged. */
    fun cancel() {
        liveFixJob?.cancel()
        liveFixJob = null
        stopBarometer()
    }

    /**
     * Whether the Maps tab is showing, from the screen ([AvailabilityScreenMapControls.onMapsTabShownChanged]). Starts or
     * stops the barometer to match (Amendment 3).
     */
    fun onMapsTabShownChanged(shown: Boolean) {
        mapsTabShown = shown
        syncBarometer()
    }

    private fun syncBarometer() {
        val parent = liveFixJob
        val wanted = mapsTabShown && parent != null && parent.isActive && !fixesEnded
        if (wanted && barometerJob == null) {
            height.onBarometerStarted()
            // A child of the live-fix job: cancelled with it, as before.
            barometerJob = scope().launch(parent!!) {
                pressureProvider.readings.collect { reading ->
                    val shown = if (reading == null) height.onNoBarometer() else height.onPressure(reading)
                    if (shown != null) showHeight(shown)
                }
            }
        } else if (!wanted && barometerJob != null) {
            stopBarometer()
        }
    }

    private fun stopBarometer() {
        barometerJob?.cancel()
        barometerJob = null
        height.onBarometerStopped()
    }

    /**
     * Starts collecting [LocationTracker.fixes] into [AvailabilityUiState.liveFix].
     *
     * **First-launch dispatch (owner-found on device):** on a fresh install the app's very first
     * collection of `fixes` happens here, at construction, *before* the OS permission dialog has
     * been shown — and [LocationTracker.fixes]' own contract is to emit
     * [LocationFix.PermissionDenied] once and **complete** when permission isn't held at
     * collection start. The flow didn't go silent; it terminated. `collect` returned, this
     * coroutine ended, and nothing collected again for the life of the process — so the compass
     * strip read "Elevation unavailable · Coordinates unavailable" until a restart, whose
     * construction-time collection then found the permission already held. Heading was fine
     * (a different sensor, no permission), and the map's own puck was fine (MapLibre's activation
     * is re-invoked from events, not subscribed once in an init block) — which is exactly the shape
     * this ViewModel lacked. A denied/unsupported permission still just never emits an Update here;
     * the strip's own "unavailable" text covers that honestly, unchanged.
     */
    fun begin() {
        // A fresh steadying each start: the anchor is not carried across a time in the background.
        height.startAfresh()
        fixesEnded = false
        liveFixJob = scope().launch {
            readLastKnownLocation()
            locationTracker.fixes.collect { fix ->
                // Location-accuracy dispatch, item 1: the one place the live fix is gated. A fix
                // worse than LIVE_FIX_MAX_ACCURACY_METERS is dropped and the previous one held —
                // and a held fix ages into the HUD's stale display on purpose; see acceptLiveFix's
                // own doc comment for the threshold, the null rule, and the alternative refused.
                if (fix is LocationFix.Update && acceptLiveFix(fix)) {
                    // The whole fix, accuracy and timestamp included — see
                    // AvailabilityUiState.liveFix's own doc comment (HUD-foundations dispatch, Item 1).
                    updateState { it.copy(liveFix = fix) }
                    // Converted first, then handed to the steadying current at that moment ([height] may have been
                    // replaced while the conversion ran).
                    val seaLevel = seaLevelConverter.seaLevel(fix.lat, fix.lng, fix.altitude)
                    height.onFix(seaLevel, fix.timestampEpochMillis)?.let(::showHeight)
                } else if (fix is LocationFix.Update) {
                    onRefusedFix(fix)
                }
            }
            // The fixes ended (a missing permission): the barometer stops too, so it is never read with no fixes to steady.
            fixesEnded = true
            stopBarometer()
        }
        // The barometer, if the Maps tab is showing ([syncBarometer]).
        syncBarometer()
    }

    private fun showHeight(shown: ShownHeight) {
        updateState { if (it.shownHeight == shown) it else it.copy(shownHeight = shown) }
    }

    /**
     * Dispatch 2026-09-28-510: a fix the gate refused is still held, as the approximate reading, beside
     * [AvailabilityUiState.liveFix] and never in it. The gate itself, and what "held" means for the
     * gated fix, are unchanged: everything that acts reads [AvailabilityUiState.liveFix] alone, and the
     * screen decides what to show from both ([com.zynergylabs.forager.app.domain.shownPosition]).
     */
    private fun onRefusedFix(fix: LocationFix.Update) {
        updateState { it.copy(approximateFix = fix) }
    }

    /**
     * Dispatch 2026-09-28-510: the platform's own last known position, read each time collection starts
     * (each foreground, and after a permission grant, when it may first be readable). Shown greyed until
     * anything newer arrives; never moved into [AvailabilityUiState.liveFix]. None held is the ordinary
     * case on a fresh phone and changes nothing.
     */
    private fun readLastKnownLocation() {
        val known = lastKnownLocation.lastKnown() ?: return
        updateState { it.copy(lastKnownFix = known) }
    }
}
