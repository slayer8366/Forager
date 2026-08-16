package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting

/**
 * Fetches individual mapped observations for a region and month, most recent first.
 * Pure and Android-framework-free so it's unit-testable headless.
 */
class GetSightingsUseCase(
    private val repository: MushroomRepository,
) {
    suspend operator fun invoke(region: Region, month: Int): Result<List<Sighting>> {
        require(month in 1..12) { "month must be 1-12, was $month" }
        return repository.getSightings(region, month).map { sightings ->
            // nullsFirst + descending puts the most recent date first and null dates last.
            sightings.sortedWith(compareByDescending(nullsFirst()) { it.observedOn })
        }
    }
}
