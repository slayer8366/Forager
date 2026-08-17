package com.forager.app.domain

import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
import java.util.UUID

/**
 * Plans a new trip at [location] for [date] named [name], assigning it an identity and
 * persisting it.
 *
 * Rejects a [date] before today: planning a trip in the past makes no sense, per the user's own
 * framing of this feature. The map's date picker already restricts selection to today-or-later,
 * but that is a UI convenience, not the invariant itself — this is the one place that actually
 * enforces it, the same "domain owns the invariant, UI can't be the only thing enforcing it"
 * pattern as [com.forager.app.domain.model.Region.clampRadiusKm].
 *
 * Rejects a blank [name] for the same reason: the date picker dialog pre-fills and disables its
 * confirm button on a blank name as a UI convenience, but this is the invariant's real home — see
 * [PlannedTrip.name].
 *
 * [today] and [idGenerator] are injected for the same reason as in [GetPlannedTripsUseCase]: a
 * test can fix both instead of racing the clock or asserting against a random id.
 */
class SavePlannedTripUseCase(
    private val repository: PlannedTripRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(location: LatLng, date: LocalDate, name: String): Result<PlannedTrip> {
        require(!date.isBefore(today())) {
            "Cannot plan a trip in the past: $date is before ${today()}."
        }
        require(name.isNotBlank()) { "Trip name must not be blank." }
        val trip = PlannedTrip(id = idGenerator(), name = name, location = location, date = date)
        return repository.save(trip).map { trip }
    }
}
