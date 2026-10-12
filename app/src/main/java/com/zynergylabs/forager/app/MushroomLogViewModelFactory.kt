package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel

/**
 * The one place the Journal's [MushroomLogViewModel] is built and told about the Maps tab's
 * [AvailabilityViewModel]: `MainActivity` calls this, and so does the test that proves the hooks
 * below are connected (dispatch 2026-09-28-312, item 10). The hooks were two lines inside
 * `MainActivity`'s ViewModel factory, and removing either left the suite green because nothing could
 * reach them without launching the Activity. A function that takes the real [AppContainer] and the real
 * [AvailabilityViewModel] changes no dependency-injection shape: it is the same construction, moved.
 */
internal fun createMushroomLogViewModel(
    container: AppContainer,
    availability: AvailabilityViewModel,
    errorLog: ErrorLog,
    recordCaptureWithoutEditingEntry: (String?, Throwable?) -> Unit,
): MushroomLogViewModel = MushroomLogViewModel(
    container.getMushroomLogEntriesUseCase,
    container.getDraftEntriesUseCase,
    container.createMushroomLogEntryUseCase,
    container.startEditingLogEntryUseCase,
    container.saveMushroomLogEntryUseCase,
    container.commitDraftEntryUseCase,
    container.deleteMushroomLogEntryUseCase,
    container.addPhotoToLogEntryUseCase,
    container.addPhotoToGalleryUseCase,
    container.removePhotoFromLogEntryUseCase,
    container.getGalleryPhotosUseCase,
    container.pullPhotoIntoEntryUseCase,
    container.deleteGalleryPhotoUseCase,
    container.locationProvider,
    container.updatePhotoLocationUseCase,
    getPhotoEntryReferenceCount = { id -> photoEntryReferenceCountOrZero(id, container.getEntryReferenceCountUseCase::forPhoto, errorLog) },
    // Find-location-at-creation dispatch, Fix 1: the held live fix, read at the
    // moment a find is started. AvailabilityViewModel is the one live collector.
    currentFix = { availability.uiState.value.liveFix },
    // A failed read fails *closed*, unlike the never-set default: the preference
    // defaults to on for an install that predates the setting, but an unreadable
    // one must not capture a position the user may have switched off. Logged, never
    // silent (CLAUDE.md: no default fallback that isn't logged when it fires).
    autoSaveLocationToPhotos = {
        container.photoLocationPreferenceRepository.getAutoSaveLocationToPhotos().getOrElse { error ->
            errorLog.w("PhotoLocation", "Couldn't read the photo-location preference; not capturing a location.", error)
            false
        }
    },
    // A capture that lands with no find open is saved to the album and recorded where the
    // device check can read it — see MushroomLogViewModel.rescueCaptureWithNoEditingEntry.
    recordCaptureWithoutEditingEntry = recordCaptureWithoutEditingEntry,
    // A find whose delete has finished leaves the Maps tab's records (dispatch 2026-09-28-291).
    onFindDeleted = { id -> availability.onFindDeleted(id) },
    // An album photo whose delete has finished leaves them too (dispatch 2026-09-28-297).
    onPhotoDeleted = { id -> availability.onPhotoDeleted(id) },
)
