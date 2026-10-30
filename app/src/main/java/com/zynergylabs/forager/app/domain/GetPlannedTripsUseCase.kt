package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.PlannedTrip
import java.time.LocalDate

/**
 * Loads every planned trip, with any dated today moved to the front and the rest ordered
 * soonest-first — the "auto-promoted display, no notification" behaviour the user asked for: a
 * trip whose day has arrived should be the first thing seen on opening the Trip Planner section,
 * with nothing pushed to the user proactively.
 *
 * [today] is injected rather than called inline so a test can fix "today" instead of racing the
 * clock, the same reason [com.zynergylabs.forager.app.domain.model.WeatherSeries] resolves its own reference
 * day rather than trusting the device clock.
 */
class GetPlannedTripsUseCase(
    private val repository: PlannedTripRepository,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend operator fun invoke(): Result<List<PlannedTrip>> = repository.getAll().map { trips ->
        val referenceDay = today()
        // false sorts before true, so today's trips (predicate false) land first; everything
        // else keeps its natural date order behind them. Undated trips (schema 23, dispatch 2026-10-30-02) go after
        // every dated one, by name and then id (Amendment 1, Q7): the store returns them in no particular order, so
        // without a key of their own they would move about between loads.
        trips.sortedWith(
            compareBy<PlannedTrip>({ it.date != referenceDay }, { it.date }), // STUB (tests first)
        )
    }
}
