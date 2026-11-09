package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.AddPhotoToGalleryUseCase
import com.zynergylabs.forager.app.domain.DeleteGalleryPhotoUseCase
import com.zynergylabs.forager.app.domain.GetGalleryPhotosUseCase
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The photo album of [MushroomLogViewModel] (UI split Phase 2.8, PR 4, dispatch 2026-10-19-34, RECORD
 * -1460): loading the gallery, adding a photo to it, deleting one at once, and saving a capture that
 * came back with no find open, moved out of the ViewModel unchanged. It holds no state of its own. Every
 * write to the screen's state goes through [updateState], and its coroutines launch in [scope], the
 * ViewModel's own `viewModelScope`, read each time rather than kept. It holds no job and cancels none.
 *
 * **It never takes [MushroomLogViewModel.editingEntryMutex], and is not given it.** Nothing here reads
 * or writes [MushroomLogUiState.editingEntry]. [rescueCaptureWithNoEditingEntry] is called by
 * [MushroomLogViewModel.onAddPhoto] while that lock is held, which is safe only because nothing it
 * reaches acquires the lock: [addPhotoToGallery], [updateState] and [loadGalleryPhotos] (a launch whose
 * body takes no lock). [reloadEntries] does take the lock, inside its own launch; it is called only from
 * [onDeleteGalleryPhoto], which never holds the lock.
 */
internal class MushroomLogGallery(
    private val updateState: ((MushroomLogUiState) -> MushroomLogUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val getGalleryPhotos: GetGalleryPhotosUseCase,
    private val getPhotoEntryReferenceCount: suspend (String) -> Int,
    private val addPhotoToGallery: AddPhotoToGalleryUseCase,
    private val deleteGalleryPhoto: DeleteGalleryPhotoUseCase,
    private val onPhotoDeleted: (String) -> Unit,
    /** The ViewModel's own `loadEntries`, which takes the lock inside its own launch. */
    private val reloadEntries: () -> Unit,
    /** The ViewModel's camera-capture location follow-up, fire-and-forget. */
    private val patchCameraCaptureLocation: (String) -> Unit,
) {

    /** Loads [MushroomLogUiState.galleryPhotos] for the photo album — Workstream G2, independent of [MushroomLogViewModel.loadEntries] (see [MushroomLogUiState]'s own doc comment on why the two get separate loading/error fields). */
    fun loadGalleryPhotos(): Job {
        return scope().launch {
            updateState { it.copy(isLoadingGalleryPhotos = true, galleryLoadErrorMessage = null) }
            getGalleryPhotos().fold(
                onSuccess = { photos ->
                    updateState { it.copy(galleryPhotos = photos, isLoadingGalleryPhotos = false) }
                    // One query per photo — see TrackRecordingViewModel.loadWaypoints' identical
                    // choice for why this scale doesn't need a batched read.
                    val counts = photos.associate { it.photo.id to getPhotoEntryReferenceCount(it.photo.id) }
                    updateState { it.copy(cartographyEntryPhotoReferenceCounts = counts) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't load the photo gallery.", error)
                    updateState {
                        // Same "not belief-changing" reasoning as loadEntries' own failure path —
                        // the photos are on disk; only the read failed.
                        it.copy(isLoadingGalleryPhotos = false, galleryLoadErrorMessage = "Photo gallery unavailable.")
                    }
                },
            )
        }
    }

    /**
     * A capture came back for a find that is no longer being edited. **It is saved to the album
     * rather than dropped**, which is this app's rule everywhere else: a capture that succeeds is
     * never lost, and the album is where it goes when there is nowhere better — also the first
     * place a user looks for a photo they cannot find.
     *
     * ## Do not delete this as dead code: the state is reachable, and here is the sequence
     *
     * **This handling is defensive, and it is also on a path a user can walk.** Both, and the
     * second is the one a future reader is likely to doubt, so it is written out: edit a find in
     * the Journal, open the in-app camera, background the app, come back inside four minutes — so
     * the camera is still open, see [CameraAbsenceWatcher] — and press the shutter.
     *
     * What clears the entry on the way out is `AvailabilityScreen`'s own `ON_STOP` observer, which
     * runs the incidental-exit auto-save when a find is open and no photo acquisition is in
     * flight. The in-app camera does not set that in-flight flag: it was narrowed to the gallery
     * picker and the permission dialog when the camera became a `Dialog` that never leaves the
     * Activity (`PhotoAcquisitionLaunchers`). Correct for *opening* the camera, which no longer
     * causes an `ON_STOP` at all — but a genuine backgrounding while the camera is open is not
     * guarded, and it clears `editingEntry` while the dialog, which lives above it in
     * `InAppCameraViewModel`, survives untouched. The shutter is then live over a find that is no
     * longer open.
     *
     * The commissioning dispatch described this state as "not reachable by normal means", reached
     * only by sudden process death. The sequence above was read out of the tree rather than
     * reproduced on a device, and is recorded here because the doc's purpose — stopping someone
     * removing handling they cannot reproduce — is served better by a reproduction than by an
     * assurance. It covers the process-death case too; that case simply is not the only one.
     *
     * Until 2026-09-17 this path dropped the source with no persist, no `release()` and no log —
     * the photo lost, the scratch file leaking into `captures/` until the next startup sweep, and
     * nothing anywhere recording it. Found while building the absence timeout. **This is the only
     * path in the app that drops an unpersisted capture**: `onRemovePhoto` and `onPullPhoto` guard
     * the same way but act on an already-persisted `LogPhoto`, and the Cartography camera persists
     * through [onAddGalleryPhoto] first, so its own no-entry guard can skip an attach but never
     * loses a photo.
     *
     * ## Two things
     *
     * The album save is the user's remedy. The Toast is what stops them looking on the find where
     * they expected the photo — the only thing that closes that gap at the moment it opens.
     *
     * The message goes out through [MushroomLogUiState.saveErrorMessage], which is this screen's
     * one transient-message channel — `JournalTab` and `LogPanel` both show it as a Toast and clear
     * it. The field is named for errors and this message is not one; reusing it is deliberate,
     * because the alternative was a parallel field threaded through five files to reach the same
     * `Toast.makeText`. Recorded as a naming wart rather than hidden.
     *
     * `release()` is not called here directly: [AddPhotoToGalleryUseCase] reaches
     * `FilePhotoStore.persist`, which releases the source in a `finally` whether the copy succeeds
     * or fails. That is the same path the ordinary album save uses, so the scratch file goes by the
     * mechanism that already exists rather than a second one written for this case.
     */
    internal suspend fun rescueCaptureWithNoEditingEntry(source: PhotoSource) {
        addPhotoToGallery(source).fold(
            onSuccess = { _ ->
                updateState { it.copy(isSavingPhoto = false, saveErrorMessage = PHOTO_SAVED_TO_ALBUM_MESSAGE) }
                loadGalleryPhotos()
            },
            onFailure = { _ ->
                updateState { it.copy(isSavingPhoto = false, saveErrorMessage = "Couldn't save that photo.") }
            },
        )
    }

    /**
     * Standalone-photos dispatch: acquires [source] via the album's own Camera/Import
     * buttons — persisted and added to the gallery only, never attached to anything (see
     * [AddPhotoToGalleryUseCase]'s own doc comment for why this stops one step short of
     * [MushroomLogViewModel.onAddPhoto]). No [MushroomLogViewModel.editingEntryMutex] needed: unlike [MushroomLogViewModel.onAddPhoto], this never reads or writes
     * [MushroomLogUiState.editingEntry] at all.
     *
     * [onPersisted] — entry-photo-acquisition dispatch, Item 2: fired with the new photo's id on
     * success, before this function's own `loadGalleryPhotos()` refresh but after the write has
     * landed. Defaults to a no-op, so every call site before this dispatch is unaffected. Exists so
     * `MainActivity` can compose "persist to the standalone gallery" (owned here) with "attach to
     * the Cartography entry the user is standing in" (`CartographyViewModel.onToggleKeptPhoto`,
     * owned there) at the one place both ViewModels are already visible, without
     * `CartographyViewModel` growing its own copy of [AddPhotoToGalleryUseCase] — which would not
     * only duplicate this function's own persist logic, but leave [MushroomLogUiState.galleryPhotos]
     * stale: [GetGalleryPhotosUseCase] is a one-shot suspend call, not a reactive `Flow`, so a write
     * from a use case this ViewModel never called would never be reflected here on its own. Reusing
     * this function whole — persist, refresh, and (for a camera capture) the GPS patch below — keeps
     * a photo acquired from inside an entry indistinguishable, everywhere else in the app, from one
     * acquired from the Album or a find.
     */
    fun onAddGalleryPhoto(source: PhotoSource, onPersisted: (String) -> Unit = {}) {
        scope().launch {
            addPhotoToGallery(source).fold(
                onSuccess = { photo ->
                    updateState { it.copy(saveErrorMessage = null) }
                    onPersisted(photo.id)
                    loadGalleryPhotos()
                    // Photo-geodata dispatch: see this class's own "Camera-capture location" doc comment.
                    if (source is CameraCapturePhotoSource) {
                        patchCameraCaptureLocation(photo.id)
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't add a photo to the gallery.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't add that photo.") }
                },
            )
        }
    }

    /**
     * Workstream G3: deletes [photo] from the gallery — the user has already confirmed, including
     * seeing how many entries reference it (the removed Photo Gallery screen's confirmation flow; since J4b the
     * album's long-press Delete is a pending delete that names the same count in its Undo snackbar). Refreshes both the gallery and the entry list on success: an entry left
     * open in the background (e.g. across a tab switch — nothing closes [MushroomLogUiState.editingEntry]
     * on its own) must not keep showing a reference to a photo that no longer exists. See this
     * class's own doc comment on the [MushroomLogViewModel.loadEntries] hazard for why that refresh no longer risks
     * discarding in-progress typing.
     */
    fun onDeleteGalleryPhoto(photo: GalleryPhoto) {
        scope().launch {
            deleteGalleryPhoto(photo.photo) { error ->
                Log.w(TAG, "Couldn't delete the file for photo '${photo.photo.id}'.", error)
            }.fold(
                onSuccess = {
                    // Committed from the moment the row is gone, so a map-records load that read it before cannot draw it.
                    updateState { it.copy(saveErrorMessage = null, committedPhotoDeleteIds = it.committedPhotoDeleteIds + photo.photo.id) }
                    onPhotoDeleted(photo.photo.id)
                    loadGalleryPhotos()
                    // Deliberately not acquiring editingEntryMutex here first — loadEntries()
                    // acquires and releases its own below. Calling into it is not a nested
                    // acquisition, since this function never holds the lock itself.
                    reloadEntries()
                },
                onFailure = { error ->
                    Log.w(TAG, "Couldn't delete photo '${photo.photo.id}' from the gallery.", error)
                    updateState { it.copy(saveErrorMessage = "Couldn't delete that photo.") }
                },
            )
        }
    }
}
