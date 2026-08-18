package com.forager.app.domain.model

import java.time.Instant

/**
 * An entry's iNaturalist upload state. Modeled from the start per the project owner's decision
 * that upload sync state should not be retrofitted later — but Phase 1 (this codebase, today) only
 * ever constructs [Draft]: uploading is a separate, not-yet-built feature blocked on the app owner
 * registering an iNaturalist OAuth application. See `docs/plans/mushroom-log.md`'s iNaturalist
 * section for the upload design these other three states exist for.
 *
 * The nullable `remoteObservationId` on [Failed] is what will make upload resumption safe once
 * built: a failure after the remote observation was already created must resume against it, never
 * re-POST and create a duplicate.
 */
sealed interface LogSyncState {
    data object Draft : LogSyncState
    data class Uploading(val progress: Float) : LogSyncState
    data class Uploaded(val remoteObservationId: String, val uploadedAt: Instant) : LogSyncState
    data class Failed(val reason: String, val remoteObservationId: String?) : LogSyncState
}
