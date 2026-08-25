package com.forager.app.domain.model

/**
 * One photo as the gallery shows it — [photo] plus every entry currently referencing it.
 * [referencingEntryIds] can be empty: [AddPhotoToLogEntryUseCase]'s own doc comment names the one
 * reachable path (a gallery-add that succeeds followed by an attach that fails) that leaves a
 * photo with no reference at all, so the gallery has to render this case sensibly rather than
 * assume every photo it shows belongs to at least one entry.
 *
 * Kept as a wrapper around [LogPhoto] rather than widening that type itself — which entries
 * reference a photo is a read-time join over `log_entry_photos` (see
 * [com.forager.app.domain.MushroomLogRepository.getAllPhotos]), not an intrinsic property of the
 * photo the way [LogPhoto.relativePath]/[LogPhoto.createdAtEpochMillis] are; the set of
 * referencing entries changes as entries attach/detach it without the photo itself changing at
 * all.
 */
data class GalleryPhoto(
    val photo: LogPhoto,
    val referencingEntryIds: List<String>,
)
