package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.MushroomLogEntry
import java.time.LocalDate
import java.util.UUID

/**
 * Starts a new, entirely-unrecorded log entry at [location] on [date] and persists it immediately
 * — unlike [com.forager.app.domain.SavePlannedTripUseCase], there's no required field to validate
 * first, since every section starts as [com.forager.app.domain.model.Observed.NotObserved]/
 * [com.forager.app.domain.model.Feature.NotObserved] by design (see [MushroomLogEntry.draft]).
 * Persisting immediately, rather than only once the forager finishes filling it in, is what makes
 * the deferred-observation edit flow possible: the entry exists in storage from the moment it's
 * started, ready to be reopened and completed later.
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
    suspend operator fun invoke(location: LatLng, date: LocalDate = today()): Result<MushroomLogEntry> {
        val entry = MushroomLogEntry.draft(id = idGenerator(), location = location, date = date)
        return repository.save(entry).map { entry }
    }
}
