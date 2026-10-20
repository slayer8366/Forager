package com.zynergylabs.forager.app.ui.log

import android.util.Log
import com.zynergylabs.forager.app.domain.AddPhotoToLogEntryUseCase
import com.zynergylabs.forager.app.domain.PullPhotoIntoEntryUseCase
import com.zynergylabs.forager.app.domain.RemovePhotoFromLogEntryUseCase
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.Companion.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The open find's photos in [MushroomLogViewModel] (UI split Phase 2.8, PR 7, dispatch 2026-10-19-34,
 * RECORD -1460): attaching a new photo, removing one, and pulling one in from the album, moved out of
 * the ViewModel unchanged. It holds no state of its own. Every value it reads from the screen's state
 * comes through [uiState], every write goes through [updateState], and its coroutines launch in
 * [scope], the ViewModel's own `viewModelScope`, read each time rather than kept. It holds no job and
 * cancels none.
 *
 * **[editingEntryMutex] is the ViewModel's own lock, passed by reference.** Three critical sections live
 * here, unchanged, one per handler. Inside [onAddPhoto]'s, three calls leave this class, at the same
 * positions as before, and none acquires the lock on this coroutine:
 * [MushroomLogGallery.rescueCaptureWithNoEditingEntry] (the album save, lock-free), [reloadGalleryPhotos]
 * (a launch whose body is lock-free), and [MushroomLogCaptureLocation.patchCameraCaptureLocationIntoFind]
 * (only launches; the launched coroutine reaches `promoteCaptureFixToFind`'s lock later as a separate
 * coroutine nobody awaits, so it queues rather than nests: pre-existing, ruled acceptable by the
 * planner in 2.8's verify-first; reasoning, not a test).
 *
 * [gallery] and [captureLocation] are the ViewModel's own collaborators, passed as objects rather than
 * lambdas so the two calls keep their exact text, including the named arguments a function-type value
 * cannot take. Both are declared before this one in the ViewModel.
 */
internal class MushroomLogEntryPhotos(
    private val uiState: () -> MushroomLogUiState,
    private val updateState: ((MushroomLogUiState) -> MushroomLogUiState) -> Unit,
    private val scope: () -> CoroutineScope,
    private val editingEntryMutex: Mutex,
    private val addPhoto: AddPhotoToLogEntryUseCase,
    private val removePhoto: RemovePhotoFromLogEntryUseCase,
    private val pullPhotoIntoEntry: PullPhotoIntoEntryUseCase,
    private val gallery: MushroomLogGallery,
    private val captureLocation: MushroomLogCaptureLocation,
    /** The ViewModel's own `loadGalleryPhotos`. */
    private val reloadGalleryPhotos: () -> Unit,
) {

    fun onAddPhoto(source: PhotoSource) {
        scope().launch {
            updateState { it.copy(isSavingPhoto = true) }
            editingEntryMutex.withLock {
                // Read fresh, inside the critical section — not before acquiring it. Two photo
                // operations issued back to back must apply in the order issued: if this captured
                // editingEntry at call time instead, a second operation queued behind this one would
                // compute its own result from the *same* pre-this-operation snapshot, and its later
                // apply would silently discard this one's photo when it lands. See MushroomLogViewModel's own
                // "Serialized editing-entry mutations" doc comment — the same principle
                // onStartEditingEntry's guard applies, extended to every photo mutator.
                val entry = uiState().editingEntry ?: return@withLock gallery.rescueCaptureWithNoEditingEntry(source)
                addPhoto(entry, source).fold(
                    onSuccess = { updated ->
                        updateState { it.copy(editingEntry = updated, isSavingPhoto = false, saveErrorMessage = null) }
                        // A freshly added photo is a new gallery row the album's already-loaded
                        // state doesn't know about yet — without this, it wouldn't appear there until
                        // the ViewModel is recreated. Detach has no equivalent need: it never removes a
                        // gallery row, only a reference nothing in this screen currently displays.
                        reloadGalleryPhotos()
                        // Photo-geodata dispatch: see MushroomLogViewModel's own "Camera-capture location" doc
                        // comment. The newly persisted photo is always the last element of updated's
                        // photos list — AddPhotoToLogEntryUseCase's own entry.copy(photos = entry.photos + photo).
                        // Find-location-at-creation dispatch, Fix 2: this branch, and only this
                        // branch, also promotes the fix to the find's own foundAt when it has none.
                        // GalleryImportPhotoSource never enters here (its location is EXIF, read in
                        // FilePhotoStore, and can be from another place and year); onPullPhoto has
                        // no location path at all.
                        if (source is CameraCapturePhotoSource) {
                            captureLocation.patchCameraCaptureLocationIntoFind(photoId = updated.photos.last().id, findId = entry.id)
                        }
                    },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't attach a photo to entry '${entry.id}'.", error)
                        updateState {
                            it.copy(isSavingPhoto = false, saveErrorMessage = "Couldn't attach that photo.")
                        }
                    },
                )
            }
        }
    }

    fun onRemovePhoto(photo: LogPhoto) {
        scope().launch {
            editingEntryMutex.withLock {
                // See onAddPhoto's own comment on reading fresh inside the lock — same reasoning.
                val entry = uiState().editingEntry ?: return@withLock
                removePhoto(entry, photo).fold(
                    onSuccess = { updated -> updateState { it.copy(editingEntry = updated, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't remove a photo from entry '${entry.id}'.", error)
                        updateState { it.copy(saveErrorMessage = "Couldn't remove that photo.") }
                    },
                )
            }
        }
    }

    /** Workstream G3: references an existing gallery [photo] from the currently-editing entry — a reference only, never a new file (see [PullPhotoIntoEntryUseCase]'s own doc comment). */
    fun onPullPhoto(photo: LogPhoto) {
        scope().launch {
            editingEntryMutex.withLock {
                // See onAddPhoto's own comment on reading fresh inside the lock — same reasoning.
                val entry = uiState().editingEntry ?: return@withLock
                pullPhotoIntoEntry(entry, photo).fold(
                    onSuccess = { updated -> updateState { it.copy(editingEntry = updated, saveErrorMessage = null) } },
                    onFailure = { error ->
                        Log.w(TAG, "Couldn't pull photo '${photo.id}' into entry '${entry.id}'.", error)
                        updateState { it.copy(saveErrorMessage = "Couldn't add that photo.") }
                    },
                )
            }
        }
    }
}
