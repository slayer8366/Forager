package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.LOST_AFTER_MILLIS
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationProvider
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.SaveMushroomLogEntryUseCase
import com.zynergylabs.forager.app.domain.UpdatePhotoLocationUseCase
import com.zynergylabs.forager.app.domain.ageMillis
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Where a find's and a photo's location comes from in [MushroomLogViewModel] (UI split Phase 2.8, PR 6,
 * dispatch 2026-10-19-34, RECORD -1460): the held fix a new find takes, and the camera-capture fix that
 * is patched onto a photo and, for a capture from inside a find, promoted to the find. Moved out of the
 * ViewModel unchanged; see [MushroomLogViewModel]'s "Camera-capture location" for the design. It holds
 * no state of its own. Every value it reads from the screen's state comes through [uiState], every write
 * goes through [updateState], and its coroutines launch in [scope], the ViewModel's own
 * `viewModelScope`, read each time rather than kept. It holds no job and cancels none.
 *
 * **[editingEntryMutex] is the ViewModel's own lock, passed by reference.** One critical section lives
 * here, unchanged: [promoteCaptureFixToFind]'s. Two of this class's functions are called while the
 * ViewModel holds that lock, and neither acquires it on the caller's coroutine:
 * - [freshDeviceLocation], from `onStartNewEntry`'s critical section, reads only
 *   [autoSaveLocationToPhotos], [currentFix] and [now].
 * - [patchCameraCaptureLocationIntoFind], from `onAddPhoto`'s critical section, only launches. The
 *   launched coroutine later reaches [promoteCaptureFixToFind]'s lock, as a separate coroutine nobody
 *   awaits, so it queues on the Mutex behind the holder rather than nesting inside it. This existed
 *   before the split, at the same position, and the planner ruled it acceptable (dispatch 2026-10-19-34,
 *   2.8's verify-first). That is reasoning, not a test.
 */
internal class MushroomLogCaptureLocation(
    private val uiState: () -> MushroomLogUiState,
    private val updateState: ((MushroomLogUiState) -> MushroomLogUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val editingEntryMutex: Mutex,
    private val locationProvider: LocationProvider,
    private val updatePhotoLocation: UpdatePhotoLocationUseCase,
    private val saveEntry: SaveMushroomLogEntryUseCase,
    private val currentFix: () -> LocationFix.Update?,
    private val now: () -> Long,
    private val autoSaveLocationToPhotos: suspend () -> Boolean,
    /** The ViewModel's own `loadGalleryPhotos`. */
    private val reloadGalleryPhotos: () -> Unit,
    /** The ViewModel's own `loadEntries`, which takes the lock inside its own launch. */
    private val reloadEntries: () -> Unit,
) {

    /**
     * The device's held fix as a [LatLng], or `null` — find-location-at-creation dispatch, Fix 1.
     * Takes what [currentFix] has in hand and never waits for one: under canopy the first fix can
     * arrive late or never, and a find must save either way (the dispatch's own constraint).
     *
     * **Age bound, a decision recorded here rather than inherited.** `AvailabilityViewModel` holds
     * its last accepted fix indefinitely once fixes stop, and its collector stops on every
     * background, so the held fix can be from a walk hours ago when the app is next opened on a
     * sofa with no fix yet. Stamping a find with that would produce a location that looks exactly
     * as authoritative as a real one, which is the failure this app exists to avoid. The bound
     * reused is the HUD's own [LOST_AFTER_MILLIS] (5 min): the one age at which this app already
     * says a fix is not a current position and withholds distance and bearing on it. Same judgement,
     * same number, by reference so the two cannot drift. Not tighter: the HUD's "stale" band
     * (30 s) is a display cue, and a find logged half a minute after the last accepted fix is at
     * the same spot for any practical purpose. Discarding a held fix for age is logged, so the
     * fallback never fires silently; no fix at all is the ordinary case and is not logged.
     *
     * What is not bounded here: accuracy. `AvailabilityViewModel`'s collector already refuses fixes
     * worse than 50 m before they are held (`LiveFixGate`), so every fix this reads passed that. It also
     * refuses every fix not from GPS (dispatch 2026-09-28-527), so a find never takes a network position
     * from here. The camera's own capture path does not come through here (the -527 report, stop 1).
     */
    internal suspend fun freshDeviceLocation(): LatLng? {
        // Owner ruling, 2026-09-14: the setting gates every automatic location capture, finds
        // included, not only the ones attached to a photo. A location the *caller* supplied (the
        // map's tapped or centred point) never reaches here — onStartNewEntry only falls back to
        // this when it was given none — so switching the setting off cannot override a point the
        // user actually chose.
        if (!autoSaveLocationToPhotos()) {
            Log.i(TAG, "Starting a find without a location: \"Automatically Save Location to Photos\" is off.")
            return null
        }
        val fix = currentFix() ?: return null
        val ageMillis = fix.ageMillis(now())
        if (ageMillis >= LOST_AFTER_MILLIS) {
            Log.i(TAG, "Starting a find without a location: the held fix is ${ageMillis / 1_000}s old, past the $LOST_AFTER_MILLIS ms bound.")
            return null
        }
        return LatLng(fix.lat, fix.lng)
    }

    /**
     * Fire-and-forget: requests a live GPS fix and, only if one resolves, patches it onto the
     * already-persisted gallery photo [photoId] — see [MushroomLogViewModel]'s own "Camera-capture location"
     * doc comment for the full reasoning. Launched from [MushroomLogViewModel.onAddPhoto]/[MushroomLogViewModel.onAddGalleryPhoto] without
     * being awaited, so this never delays the capture flow those functions already completed by the
     * time this runs. [LocationResult.PermissionDenied]/[LocationResult.LocationUnavailable] are
     * both silently accepted, same as [LogPhoto.latitude]/[LogPhoto.longitude] being `null` is the
     * ordinary case, not an error — nothing here surfaces a user-facing failure for "no fix came
     * back." Refreshes both galleries/entries on a successful patch, mirroring
     * [MushroomLogViewModel.onDeleteGalleryPhoto]'s own dual-refresh, so a photo already visible on screen picks up its
     * new coordinate without the user needing to leave and return.
     */
    internal fun patchCameraCaptureLocation(photoId: String) {
        scope().launch { requestAndPatchCaptureFix(photoId) }
    }

    /**
     * [patchCameraCaptureLocation] for a capture taken from inside a find — find-location-at-
     * creation dispatch, Fix 2. Same fire-and-forget fix request and photo patch, then one more
     * step: if the find [findId] still has no `foundAt`, the same fix becomes its location. A
     * separate function rather than a flag on [patchCameraCaptureLocation], so the Album's own
     * camera path (no find to promote into) keeps the function it always had. Only [MushroomLogViewModel.onAddPhoto]'s
     * `CameraCapturePhotoSource` branch calls this — see the comment there for why Import and From
     * Album never can.
     */
    internal fun patchCameraCaptureLocationIntoFind(photoId: String, findId: String) {
        scope().launch {
            val location = requestAndPatchCaptureFix(photoId) ?: return@launch
            promoteCaptureFixToFind(findId, LatLng(location.lat, location.lng))
        }
    }

    /** The shared half of both camera follow-ups: requests the fix and, if one resolves, patches it onto [photoId]. Returns the fix so a caller can use it further, `null` when none came back. */
    private suspend fun requestAndPatchCaptureFix(photoId: String): LocationResult.Success? {
        // The one choke point for both camera paths: gating here stops the coordinate reaching the
        // photo row *and*, because the find variant promotes what this returns, stops it reaching
        // the find's foundAt. Checked before the provider is asked at all, so switching the setting
        // off means no position is requested, not one requested and then discarded.
        if (!autoSaveLocationToPhotos()) {
            Log.i(TAG, "Not capturing a location for photo '$photoId': \"Automatically Save Location to Photos\" is off.")
            return null
        }
        val location = locationProvider.getCurrentLocation() as? LocationResult.Success ?: return null
        updatePhotoLocation(photoId, location.lat, location.lng).fold(
            onSuccess = {
                reloadGalleryPhotos()
                reloadEntries()
            },
            onFailure = { error ->
                Log.w(TAG, "Couldn't patch a location fix onto photo '$photoId'.", error)
            },
        )
        return location
    }

    /**
     * Writes [location] to find [findId]'s `foundAt` only if that find is the one still open for
     * editing and has no location yet — never over a location the user set or the map supplied.
     * Under [editingEntryMutex] like every other editing-entry mutation, and persisted the way
     * [MushroomLogViewModel.onEntryEdited] persists a keystroke. The fix can take up to the provider's own 20 s to
     * resolve; if the user has closed the find by then, nothing is written and that is logged.
     */
    private suspend fun promoteCaptureFixToFind(findId: String, location: LatLng) {
        editingEntryMutex.withLock {
            val entry = uiState().editingEntry
            if (entry == null || entry.id != findId) {
                Log.i(TAG, "Capture fix not promoted: find '$findId' is no longer open for editing.")
                return@withLock
            }
            if (entry.foundAt != null) return@withLock
            val located = entry.copy(foundAt = location)
            updateState { it.copy(editingEntry = located) }
            saveEntry(located).fold(
                onSuccess = { updateState { it.copy(saveErrorMessage = null) } },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't save the capture fix as find '$findId''s location.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't save your changes.") }
                },
            )
        }
    }
}
