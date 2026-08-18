package com.forager.app.domain

import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SightingsPage
import com.forager.app.domain.model.TaxonFilter

/**
 * Fetches individual mapped observations matching [TaxonFilter] for a region and month, most
 * recent first, alongside iNaturalist's own total for the query (see [SightingsPage]). Pure and
 * Android-framework-free so it's unit-testable headless.
 */
class GetSightingsUseCase(
    private val repository: MushroomRepository,
) {
    suspend operator fun invoke(region: Region, month: Int, filter: TaxonFilter): Result<SightingsPage> {
        require(month in 1..12) { "month must be 1-12, was $month" }
        return repository.getSightings(region, month, filter).map { page ->
            // nullsFirst + descending puts the most recent date first and null dates last.
            page.copy(sightings = page.sightings.sortedWith(compareByDescending(nullsFirst()) { it.observedOn }))
        }
    }
}
