package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.domain.ErrorLog

/**
 * A count read for a badge or a warning that shows 0 when the read fails, and logs that it did
 * (CLAUDE.md, "no default fallback that isn't logged when it fires"). The one copy (dispatch
 * 2026-09-28-658, D7) of the shape the four entry-reference counts below each wrote out by hand
 * inside `MainActivity.kt`; moved out of the Activity's file with them. [what] completes "Couldn't
 * count journal entries keeping ...", and the log line ends "showing 0." as each copy's did.
 */
internal suspend fun <K> countOrZero(
    id: K,
    count: suspend (K) -> Result<Int>,
    errorLog: ErrorLog,
    tag: String,
    what: String,
): Int = count(id).getOrElse { error ->
    errorLog.w(tag, "Couldn't count journal entries keeping $what; showing 0.", error)
    0
}

/**
 * How many journal (Cartography) entries keep photo [photoId], for the album's entry badge and the
 * photo delete dialog (`MushroomLogViewModel.loadGalleryPhotos`).
 *
 * A failed count still shows as 0, as it always has, but the fallback is now logged when it fires
 * (journal redesign J3, C6; CLAUDE.md, "no default fallback that isn't logged when it fires"),
 * through the same [ErrorLog] seam and `getOrElse { log; fallback }` shape the photo-location
 * preference read uses in `MainActivity`'s ViewModel factory. Shown as 0, a failed read means no
 * entry badge and no "appears in N journal entries" warning in the delete dialog; the log is what
 * tells that apart from a real 0.
 */
internal suspend fun photoEntryReferenceCountOrZero(
    photoId: String,
    countEntriesReferencingPhoto: suspend (String) -> Result<Int>,
    errorLog: ErrorLog,
): Int = countOrZero(photoId, countEntriesReferencingPhoto, errorLog, "PhotoReferenceCount", "photo $photoId")

/**
 * How many journal entries keep waypoint [waypointId], for the Records Undo snackbar's warning
 * (`TrackRecordingViewModel.loadWaypoints`, read by `requestRemoveWaypoint`). A failed count still
 * shows as 0, as it always has, but the fallback is now logged when it fires (journal redesign J4,
 * D6; owner ruling "Fix in J4 (Recommended)"), the same way [photoEntryReferenceCountOrZero] was fixed
 * in J3. Shown as 0, a failed read means the snackbar says only "Waypoint deleted" for a waypoint
 * entries do use; the log is what tells that apart from a real 0.
 */
internal suspend fun waypointEntryReferenceCountOrZero(
    waypointId: String,
    countEntriesReferencingWaypoint: suspend (String) -> Result<Int>,
    errorLog: ErrorLog,
): Int = countOrZero(waypointId, countEntriesReferencingWaypoint, errorLog, "WaypointReferenceCount", "waypoint $waypointId")

/**
 * How many journal entries keep track [trackId], for the Records Undo snackbar's warning
 * (`TrackRecordingViewModel.loadTracks`, read by `requestRemoveTrack`). Logged when the 0 fallback
 * fires, as [waypointEntryReferenceCountOrZero] (Part 2 follow-ups F1 item 5).
 */
internal suspend fun trackEntryReferenceCountOrZero(
    trackId: String,
    countEntriesReferencingTrack: suspend (String) -> Result<Int>,
    errorLog: ErrorLog,
): Int = countOrZero(trackId, countEntriesReferencingTrack, errorLog, "TrackReferenceCount", "track $trackId")

/**
 * How many journal entries keep offline region [offlineRegionId], for the Records Undo snackbar's
 * warning (`AvailabilityViewModel.loadOfflineRegions`, read by `requestDeleteOfflineRegion`). Logged
 * when the 0 fallback fires, as [waypointEntryReferenceCountOrZero] (J4, D6).
 */
internal suspend fun offlineRegionEntryReferenceCountOrZero(
    offlineRegionId: Long,
    countEntriesReferencingOfflineRegion: suspend (Long) -> Result<Int>,
    errorLog: ErrorLog,
): Int = countOrZero(offlineRegionId, countEntriesReferencingOfflineRegion, errorLog, "OfflineRegionReferenceCount", "offline region $offlineRegionId")
