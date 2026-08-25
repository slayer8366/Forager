package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import java.util.UUID

/**
 * Starts a new, entirely-unrecorded log entry — at [location] if one is already known, or with
 * none at all (Workstream L4: journal "+" routes straight to the entry page rather than through a
 * location-placement step first; see [MushroomLogEntry.foundAt]'s own doc comment) — on [date], and
 * persists it immediately, as an uncommitted draft ([MushroomLogEntry.isDraft] `true` — Workstream
 * L4b, owner decision #6: nothing this creates is visible via [GetMushroomLogEntriesUseCase] until
 * the user has put something there). Unlike [com.forager.app.domain.SavePlannedTripUseCase], there's
 * no required field to validate first, since every section starts as
 * [com.forager.app.domain.model.Observed.NotObserved]/[com.forager.app.domain.model.Feature.NotObserved]
 * by design (see [MushroomLogEntry.draft]).
 *
 * The id is assigned here, before the entry is ever committed — Workstream L4b's own "draft
 * identity" decision — so `log_entry_photos` cross-references work unchanged for a photo pulled
 * into a still-uncommitted entry: the id a photo attaches to today is the same id the entry
 * commits under later, never reassigned.
 *
 * Persisting immediately, rather than only once the forager finishes filling it in, is what makes
 * the deferred-observation edit flow possible: the entry exists in storage from the moment it's
 * started, ready to be reopened and completed later, surviving a crash the same way any other
 * open edit session does (see [MushroomLogViewModel]'s own doc comment).
 *
 * [today] and [idGenerator] are injected for the same reason as [GetPlannedTripsUseCase]/
 * [SavePlannedTripUseCase]: a test can fix both instead of racing the clock or asserting against a
 * random id.
 */
class CreateMushroomLogEntryUseCase(
    private val repository: MushroomLogRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(location: LatLng?, date: LocalDate = today()): Result<MushroomLogEntry> {
        val entry = MushroomLogEntry.draft(id = idGenerator(), location = location, date = date)
        return repository.save(entry).map { entry }
    }
}
