package com.forager.app.domain

import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate

/**
 * Loads every planned trip, with any dated today moved to the front and the rest ordered
 * soonest-first — the "auto-promoted display, no notification" behaviour the user asked for: a
 * trip whose day has arrived should be the first thing seen on opening the Trip Planner section,
 * with nothing pushed to the user proactively.
 *
 * [today] is injected rather than called inline so a test can fix "today" instead of racing the
 * clock, the same reason [com.forager.app.domain.model.WeatherSeries] resolves its own reference
 * day rather than trusting the device clock.
 */
class GetPlannedTripsUseCase(
    private val repository: PlannedTripRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend operator fun invoke(): Result<List<PlannedTrip>> = repository.getAll().map { trips ->
        val referenceDay = today()
        // false sorts before true, so today's trips (predicate false) land first; everything
        // else keeps its natural date order behind them.
        trips.sortedWith(compareBy({ it.date != referenceDay }, PlannedTrip::date))
    }
}
