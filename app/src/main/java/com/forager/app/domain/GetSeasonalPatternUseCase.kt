package com.forager.app.domain

import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.TaxonFilter

/**
 * Fetches what [ComputeFruitingLagDistributionUseCase] needs and runs it: the region's dated
 * sightings (via [GetSightingsUseCase], already fetching all-years observations for
 * region+month+filter) and the historical rainfall behind them (via [HistoricalWeatherProvider]),
 * then composes the two into a [FruitingLagDistribution].
 *
 * Shaped like [GetTripWindowsUseCase]: the fetch and the pure computation are separate types, and
 * this class is the thin seam that joins them, one API round trip and one deterministic
 * computation per call.
 *
 * **Fetch range.** One historical-weather request per call, covering from the earliest dated
 * sighting back-padded by [FruitingPatternAssumptions.FRUITING_LAG_DAYS]`.last` days — far enough
 * that a soaking event just before the earliest sighting still falls inside the fetched window and
 * isn't invisibly excluded — through the latest dated sighting. Padding by that existing constant
 * rather than a new one keeps the fetch range and the lag range it needs to detect from being able
 * to drift apart.
 */
class GetSeasonalPatternUseCase(
    private val getSightings: GetSightingsUseCase,
    private val historicalWeatherProvider: HistoricalWeatherProvider,
    private val computeFruitingLagDistribution: ComputeFruitingLagDistributionUseCase,
) {
    suspend operator fun invoke(region: Region, month: Int, filter: TaxonFilter): Result<FruitingLagDistribution> {
        val page = getSightings(region, month, filter).getOrElse { return Result.failure(it) }
        val datedDates = page.sightings.mapNotNull { it.observedOn }

        if (datedDates.isEmpty()) {
            return Result.success(
                computeFruitingLagDistribution(
                    region = region,
                    month = month,
                    filter = filter,
                    sightings = page.sightings,
                    historicalDays = emptyList(),
                    totalResultsOnServer = page.totalResults,
                ),
            )
        }

        val from = datedDates.min().minusDays(FruitingPatternAssumptions.FRUITING_LAG_DAYS.last.toLong())
        val through = datedDates.max()

        return historicalWeatherProvider.getHistoricalPrecipitation(region, from, through).map { historicalDays ->
            computeFruitingLagDistribution(
                region = region,
                month = month,
                filter = filter,
                sightings = page.sightings,
                historicalDays = historicalDays,
                totalResultsOnServer = page.totalResults,
            )
        }
    }
}
