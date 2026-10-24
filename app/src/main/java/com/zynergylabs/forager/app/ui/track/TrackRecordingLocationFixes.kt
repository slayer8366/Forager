package com.zynergylabs.forager.app.ui.track

import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationSampler
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.mayAct
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.TrackPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The live-fix collection of [TrackRecordingViewModel] (dispatch 2026-10-23-09 Amendment 1, part 0: the split before the
 * way home is added, RECORD -1490), moved out of the ViewModel unchanged, in the pattern of UI split Phase 2.7 (RECORD
 * -1439, the poll and the route tick). It holds the collection's one job and the last accuracy-gated GPS fix, and nothing
 * else. The ViewModel decides when the collection starts and stops, from the same lines as before: [begin] from
 * `beginLocationTracking` (called by `startRecording` and `takeUpRunningRecording`), [cancel] from `clearRecordingState`
 * and `onCleared`, each synchronous. The collection launches in [scope], the ViewModel's own `viewModelScope`, read each
 * time rather than kept, so it gets no scope, dispatcher or lifetime of its own. What it calls back into the ViewModel
 * ([isTakenUp], [originCreationInFlight], [createOriginWaypoint], [returnToStart]) is called from the same place in the
 * collector body, in the same order, as before.
 *
 * ## Why return-to-start is fed by [locationTracker], not a one-shot fetch
 *
 * The first pass of the return-to-vehicle screen recomputed [ReturnToStartInfo] from
 * `AvailabilityViewModel`'s `locateMeStatus` — a one-shot "where am I right now" fetch, refreshed
 * only when the user taps the locate-me icon. That made the bearing/distance shown stale between
 * taps. This ViewModel now collects [LocationTracker.fixes] itself, the same continuous stream
 * [com.zynergylabs.forager.app.service.TrackRecordingService] collects for the track's own points, whenever a
 * recording is active — so [TrackRecordingUiState.returnToStart] updates on every fix, not just on
 * demand. This does mean two independent OS location-listener
 * registrations while recording (the service's and this one) rather than one shared stream — a
 * real, accepted duplication, not a hidden one, in exchange for not re-plumbing the service to
 * publish its fixes back out to the UI layer for this one reader.
 *
 * ## Why the collection keeps running with the screen off
 *
 * (Moved here from `TrackRecordingViewModel.onLeftForeground`'s doc, which resyncs on `ON_STOP`, by part 0.)
 * **Why this and not the foreground gate `AvailabilityViewModel` uses.** That gate releases the
 * subscription on `ON_STOP` unconditionally, which is right there and would be a regression
 * here: two things in this collector must keep running with the screen off
 * during a *legitimate* recording.
 *
 * 1. The origin waypoint. It is seeded from the first fix that clears the mode's accuracy gate,
 *    with no timeout, and [com.zynergylabs.forager.app.domain.pathHome]'s own doc records that under
 *    canopy that fix may arrive very late or never. A pocketed phone is the normal way to walk
 *    a track. Gate the collector off and a canopy recording acquires no origin at all, and the
 *    navigation HUD then has no target to point at — the return-to-vehicle safety feature.
 * 2. The off-track alert. `returnToStart` was fed from that collector and ran the decision.
 *    **No longer true since dispatch 2026-09-28-400, Amendment 2:** the decision is
 *    [com.zynergylabs.forager.app.domain.ReturnWatch]'s and is fed by the recording service, so it does not depend on this
 *    collector at all. Reason 1 still stands on its own, so the collector is left as it was.
 *
 * So the collector stays bounded by the recording, as it always was. What changes is that
 * "the recording" now means the track's own row rather than a field nothing repopulates.
 *
 * **What this does not close, recorded rather than fixed.** During a legitimate recording,
 * backgrounding still does not release this ViewModel's subscription, and it is a *second*
 * platform registration on top of the service's own — `LocationTracker.fixes` is a cold
 * `callbackFlow`, so every collector runs `requestLocationUpdates` independently. Routing this
 * ViewModel off the service's fixes instead is the structural change and is out of this scope.
 */
internal class TrackRecordingLocationFixes(
    private val uiState: () -> TrackRecordingUiState,
    private val scope: () -> CoroutineScope,
    private val locationTracker: LocationTracker,
    private val isTakenUp: () -> Boolean,
    private val originCreationInFlight: () -> Boolean,
    private val createOriginWaypoint: (ActiveTrack, TrackPoint) -> Unit,
    private val returnToStart: (TrackPoint) -> ReturnToStartInfo?,
) {

    private var locationJob: Job? = null

    // Navigation HUD stage one — the auto-created origin/end waypoints. lastGatedFix is the most
    // recent fix that passed the active mode's own accuracy gate (LocationSampler's first-fix
    // rule), what stopRecording() seeds the end waypoint from.
    //
    // Dispatch 2026-09-28-527 split it in two. lastGatedGpsFix is the same rule for GPS fixes only,
    // and is what everything that acts reads: the origin and end waypoints and the route home (RECORD
    // -558, and the dispatch's rule 2 for the end). lastGatedFix, any provider, was read only by the
    // sundown countdown, and went with it (dispatch 2026-09-28-592): the line is the watch's now, which
    // takes its sunset from any fix as that countdown did.
    var lastGatedGpsFix: TrackPoint? = null

    /**
     * See this class's own doc comment for why [returnToStart] is driven from here rather than a
     * one-shot fetch. Navigation HUD stage one: this same stream is what seeds the track's origin
     * waypoint — the **first fix that passes the active mode's accuracy gate**, no timeout (owner
     * decision): the origin lands where the recorder actually stood within seconds in the open,
     * and under canopy it may never, in which case the track validly has no origin. The last-known
     * fix from elsewhere was rejected as a seed because it may be minutes old and somewhere the
     * user has already left; the first breadcrumb because it arrives 30–45 s late via the
     * service's flush and this ViewModel's poll.
     */
    fun begin() {
        locationJob?.cancel()
        locationJob = scope().launch {
            locationTracker.fixes.collect { fix ->
                if (fix is LocationFix.Update) {
                    val point = TrackPoint(
                        lat = fix.lat,
                        lng = fix.lng,
                        altitude = fix.altitude,
                        accuracyMeters = fix.accuracyMeters,
                        timestampEpochMillis = fix.timestampEpochMillis,
                    )
                    val active = uiState().activeTrack
                    // LocationSampler's own first-fix rule *is* the accuracy gate: with no
                    // lastAccepted it accepts exactly the fixes whose reported accuracy clears the
                    // mode's ceiling — reused rather than restated.
                    if (active != null && LocationSampler(active.mode).shouldAccept(lastAccepted = null, candidate = point)) {
                        // Dispatch 2026-09-28-527: only a GPS fix acts.
                        if (fix.provider.mayAct) {
                            lastGatedGpsFix = point
                            // Not for a recording that was taken up: its marker is the one it already
                            // has, or its first recorded point, never this fix. See settleTakenUpOrigin.
                            if (!isTakenUp() && uiState().originWaypoint == null && !originCreationInFlight()) createOriginWaypoint(active, point)
                        }
                    }
                }
            }
        }
    }

    /**
     * Stops the collection. Cancelling [locationJob] is what releases the platform location listener, not merely
     * what stops this collector reading it: `LocationTracker.fixes` is a `callbackFlow` whose
     * `awaitClose { removeUpdates(listener) }` runs on cancellation and at no other time. See
     * [com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onLeftForeground], where this
     * project recorded the same mechanism after finding a subscription that outlived every
     * backgrounding.
     */
    fun cancel() {
        locationJob?.cancel()
        locationJob = null
    }
}
