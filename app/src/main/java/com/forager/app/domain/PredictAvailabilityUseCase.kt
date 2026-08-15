package com.forager.app.domain

import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.AvailabilityForecast
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SpeciesObservationCount

/**
 * Ranks mushroom species by how often they've historically been observed in a region during
 * a given month. This is deliberately a frequency ranking over real iNaturalist observations,
 * not a fitted or speculative model: CLAUDE.md's evidence-based-feature-addition rule is to
 * not build correction/prediction logic beyond what the available data actually supports.
 *
 * Pure and Android-framework-free so it's unit-testable headless.
 */
class PredictAvailabilityUseCase(
    private val repository: MushroomRepository,
) {
    suspend operator fun invoke(region: Region, month: Int): Result<AvailabilityForecast> {
        require(month in 1..12) { "month must be 1-12, was $month" }
        return repository.getSpeciesCounts(region, month).map { counts -> rank(counts, region, month) }
    }

    internal fun rank(counts: List<SpeciesObservationCount>, region: Region, month: Int): AvailabilityForecast {
        val sorted = counts.sortedByDescending { it.observationCount }
        val maxCount = sorted.firstOrNull()?.observationCount ?: 0
        val entries = sorted.map { count ->
            AvailabilityEntry(
                species = count,
                relativeLikelihood = if (maxCount == 0) 0f else count.observationCount.toFloat() / maxCount,
            )
        }
        return AvailabilityForecast(region = region, month = month, entries = entries)
    }
}
