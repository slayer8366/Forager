package com.zynergylabs.forager.app.ui.availability

/*
 * The live fix's lifecycle functions, moved out of AvailabilityViewModel.kt unchanged as extension functions on
 * [AvailabilityViewModel] (dispatch 2026-11-03-03, A5; RECORD -1490's rule that a file over 800 lines is split before a
 * feature goes in, here before route navigation). A pure move, as AvailabilityViewModelWaypointNavigation.kt's was: each
 * function's body and doc comment are as they were, they are still ViewModel functions (RECORD -1450), and every caller
 * reaches them by the same name. The two members they read, `isForegrounded` and `liveLocation`, were made `internal` for
 * this, and nothing else changed.
 */

/**
 * The hosting Activity reached `ON_START`. Acquires the live-fix subscription.
 *
 * **Why lifecycle and not construction.** `viewModelScope` cancels at `onCleared()` — Activity
 * *destruction*, not stop. A backgrounded Activity is stopped, not destroyed, so a
 * construction-time collection outlived every backgrounding: the `callbackFlow` was never
 * closed, its `awaitClose { locationManager.removeUpdates(listener) }` never ran, and the OS
 * listener stayed registered on both GPS and network providers with no screen showing. What
 * limited delivery was the platform withholding background location from an app that does not
 * declare `ACCESS_BACKGROUND_LOCATION` — not anything this app did.
 *
 * Idempotent: a start while already collecting is a no-op, so a re-delivered `ON_START` cannot
 * stack a second OS registration on top of the ones this app already runs while recording.
 *
 * **Deliberately not need-gating.** This still subscribes on every tab, at the same 1-second
 * floor, whether or not anything is consuming fixes. Narrowing *when* the app subscribes while
 * foregrounded is a separate, larger change; releasing on background is orthogonal to it and
 * survives it.
 */
fun AvailabilityViewModel.onEnteredForeground() {
    isForegrounded = true
    if (!liveLocation.isCollecting) liveLocation.begin()
}

/**
 * The hosting Activity reached `ON_STOP`. Releases the live-fix subscription.
 *
 * Cancelling the collection completes the `callbackFlow`, which is what actually runs
 * `removeUpdates(listener)` — the OS registration goes away rather than merely being ignored.
 *
 * **This does not touch a running recording.** `TrackRecordingService` collects
 * [LocationTracker.fixes] on its own (`TrackRecordingService.kt`), as a foreground service, and
 * `TrackRecordingViewModel` collects its own stream. Neither is this job, and neither is
 * affected by this call.
 *
 * **Corrected (resync dispatch).** This paragraph used to add that the recording ViewModel's
 * stream is "bounded by the recording itself — started when recording starts and cancelled when
 * it stops". That held only for a stop made through the ViewModel. A stop from the notification
 * shade calls `TrackRecordingService.stopRecording()`, a different method of the same name on a
 * different class, so its `locationJob` outlived the recording and kept a platform registration
 * alive. `TrackRecordingViewModel` now has its own `onEnteredForeground`/`onLeftForeground`,
 * which resynchronize against the track's row rather than releasing unconditionally the way
 * this one does — see that class for why an unconditional release would regress the origin
 * waypoint and the off-track alert.
 */
fun AvailabilityViewModel.onLeftForeground() {
    isForegrounded = false
    liveLocation.cancel()
}

/**
 * The OS just granted location permission — `MainActivity`'s one permission launcher reports
 * this from its result callback, for either action it was launched for. Starts a fresh
 * collection of [LocationTracker.fixes] if the construction-time one has already completed
 * (see [AvailabilityLiveLocation.begin]: it completes, rather than idling, when permission wasn't held at
 * its start). The tracker re-checks permission at every collection start, so the new
 * collection is the one that actually registers OS listeners.
 *
 * A no-op while a collection is still live — so a grant reported while fixes are already
 * flowing (the launcher answers immediately for an already-held permission, on every locate
 * tap) never stacks a second OS listener on the four this app already runs while recording.
 * Deliberately *not* driven from [locateMe]/[useCurrentLocation] succeeding: "a one-shot fix
 * resolved" and "the permission was just granted" are different facts that happen to coincide
 * on first launch, and the next reader should not have to know that. Not driven from
 * `ON_RESUME` either — see the dispatch's own constraint: re-registering on every resume would
 * mask this bug and add churn. Never requests the permission itself; it only reacts to the
 * grant the existing launcher already delivers.
 */
fun AvailabilityViewModel.onLocationPermissionGranted() {
    // Gated on the foreground flag as well as the job: a grant delivered while the Activity is
    // stopped must not re-acquire the subscription behind a backgrounded app. On the ordinary
    // path the grant arrives from a launcher callback while the app is visible, so this is
    // true; ON_START would start it anyway if it were not.
    if (!isForegrounded) return
    if (liveLocation.isCollecting) return
    liveLocation.begin()
}
