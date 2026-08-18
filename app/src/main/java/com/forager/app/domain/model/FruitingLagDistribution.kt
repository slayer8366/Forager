package com.forager.app.domain.model

/**
 * One bucket of the fruiting-lag histogram: how many sightings fell a given number of days after
 * the nearest preceding soaking event (per
 * [com.forager.app.domain.ComputeTripWindowsUseCase.findSoakingEvents]), or had no qualifying
 * preceding event at all in the fetched historical window.
 */
data class FruitingLagBucket(
    /** The lag range this bucket covers in days, or null for the "no preceding event" bucket. */
    val lagDaysRange: IntRange?,
    val label: String,
    val count: Int,
    /**
     * True for the one bucket whose range is
     * [com.forager.app.domain.FruitingPatternAssumptions.FRUITING_LAG_DAYS] itself — the range
     * this whole feature exists to test. Carried on the bucket, computed once by
     * [com.forager.app.domain.ComputeFruitingLagDistributionUseCase], rather than recomputed by
     * the UI, so the chart's highlight can never drift from the range it is testing.
     */
    val isFruitingLagRule: Boolean,
)

/**
 * The result of testing [com.forager.app.domain.FruitingPatternAssumptions.FRUITING_LAG_DAYS]
 * against real historical iNaturalist sightings and real historical Open-Meteo rainfall, for one
 * region, month and taxon filter.
 *
 * This is a labelled estimate, not a validated model. [totalResultsOnServer] and
 * [sightingsConsidered] are on this type specifically so a caller can never show [buckets] without
 * also being able to say what they are an estimate from — see CLAUDE.md's rule against a
 * fabricated plausible value, and this feature's own requirement that every screen showing this
 * type states the sample size and an "estimate from N observations, not a guarantee" caveat.
 *
 * **What this deliberately does not do:** feed into [AvailabilityEntry.relativeLikelihood] or any
 * ranking, and it makes no claim about weather and observation frequency beyond the one named
 * hypothesis it tests — same restraint [TripWindow] and
 * [com.forager.app.domain.PredictAvailabilityUseCase] already apply, for the same reason.
 */
data class FruitingLagDistribution(
    val region: Region,
    val month: Int,
    val filter: TaxonFilter,
    /** One entry per [com.forager.app.domain.ComputeFruitingLagDistributionUseCase.BUCKET_RANGES], plus the "no preceding event" bucket last. */
    val buckets: List<FruitingLagBucket>,
    /** Sightings with no `observedOn` date: excluded from every bucket rather than counted as zero-lag or silently dropped. */
    val observationsExcludedForMissingDate: Int,
    /**
     * How many mapped sightings for this query were actually fetched and considered here — always
     * <= [totalResultsOnServer]. This is `sightingsConsidered`, not [sampleSize]: it also counts
     * the [observationsExcludedForMissingDate] sightings that couldn't be bucketed.
     */
    val sightingsConsidered: Int,
    /**
     * iNaturalist's own `total_results` for the query, before this app's per-page cap and
     * mappable-position filtering (see [SightingsPage]). Lets the UI say "based on 170 of 1,847
     * observations" instead of presenting [sightingsConsidered] as if it were exhaustive.
     */
    val totalResultsOnServer: Int,
) {
    /** The histogram's actual sample size: bucketed sightings, i.e. [sightingsConsidered] minus [observationsExcludedForMissingDate]. */
    val sampleSize: Int get() = buckets.sumOf { it.count }

    /** The bucket count for sightings with no qualifying preceding soaking event in the fetched window. */
    val observationsWithNoPrecedingEvent: Int get() = buckets.firstOrNull { it.lagDaysRange == null }?.count ?: 0
}
