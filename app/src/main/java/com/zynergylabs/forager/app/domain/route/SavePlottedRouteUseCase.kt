package com.zynergylabs.forager.app.domain.route

import com.zynergylabs.forager.app.domain.PlannedTripRepository
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
import java.util.UUID

/**
 * Saves a plotted route to the Trip Planner (dispatch 2026-10-30-08, step 3): a [PlannedTrip] named [invoke]'s `name`, its pin at
 * the route's first stop (`PlannedTrip`'s own rule), dated only when the user added a date (RECORD -1817: "Date optional").
 *
 * A new path, not `SavePlannedTripUseCase`, which requires a date and builds a trip with no route (the first stop, (e)).
 * A blank name, or a date before today, is refused as a failed [Result], not a throw, so the save dialog keeps the route and
 * says it could not be saved.
 */
class SavePlottedRouteUseCase(
    private val repository: PlannedTripRepository,
    private val today: () -> LocalDate = LocalDate::now,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(route: PlottedRoute, name: String, date: LocalDate?): Result<PlannedTrip> = Result.failure(NotImplementedError("STUB"))
}
