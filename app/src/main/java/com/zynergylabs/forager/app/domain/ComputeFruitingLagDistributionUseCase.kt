package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.model.FruitingLagHistogram
import com.zynergylabs.forager.app.domain.model.LagSpan
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.TaxonFilter

/**
 * Tests [FruitingPatternAssumptions.FRUITING_LAG_DAYS] against real data: for each sighting with a
 * known observed date, finds the nearest soaking event that ended on or before that date (reusing
 * [ComputeTripWindowsUseCase.findSoakingEvents] — never a second copy of that detection, per
 * CLAUDE.md's rule against drift between duplicated logic) and buckets the lag between them.
 *
 * Pure and Android-framework-free so it's unit-testable headless, same as
 * [ComputeTripWindowsUseCase]. Like that class, this one reports measurements — a count per
 * labelled bucket — and never a score, rank or probability: see [FruitingLagDistribution]'s own
 * doc comment.
 */
class ComputeFruitingLagDistributionUseCase {

    operator fun invoke(
        region: Region,
        month: Int,
        filter: TaxonFilter,
        sightings: List<Sighting>,
        historicalDays: List<DailyWeather>,
        totalResultsOnServer: Int,
    ): FruitingLagDistribution {
        val events = ComputeTripWindowsUseCase.findSoakingEvents(historicalDays)
        val dated = sightings.filter { it.observedOn != null }
        val excludedForMissingDate = sightings.size - dated.size

        val rangeCounts = IntArray(BUCKET_RANGES.size)
        var noPrecedingEventCount = 0
        val lags = mutableListOf<Int>()

        for (sighting in dated) {
            val date = requireNotNull(sighting.observedOn)
            // Never an event that follows the sighting: only endDate <= date is a candidate.
            val nearest = events
                .filter { !it.endDate.isAfter(date) }
                .minByOrNull { date.toEpochDay() - it.endDate.toEpochDay() }
            if (nearest == null) {
                noPrecedingEventCount++
                continue
            }
            val lag = (date.toEpochDay() - nearest.endDate.toEpochDay()).toInt()
            val bucketIndex = BUCKET_RANGES.indexOfFirst { lag in it }
            // BUCKET_RANGES' last range ends at Int.MAX_VALUE, so a non-negative lag always
            // matches one of them; lag is non-negative by construction (nearest.endDate <= date).
            rangeCounts[bucketIndex]++
            lags += lag
        }

        val buckets = BUCKET_RANGES.mapIndexed { index, range ->
            FruitingLagBucket(
                lagDaysRange = range,
                label = labelFor(range),
                count = rangeCounts[index],
                isFruitingLagRule = range == FruitingPatternAssumptions.FRUITING_LAG_DAYS,
            )
        } + FruitingLagBucket(
            lagDaysRange = null,
            label = "No preceding rain event",
            count = noPrecedingEventCount,
            isFruitingLagRule = false,
        )

        return FruitingLagDistribution(
            region = region,
            month = month,
            filter = filter,
            buckets = buckets,
            observationsExcludedForMissingDate = excludedForMissingDate,
            sightingsConsidered = sightings.size,
            totalResultsOnServer = totalResultsOnServer,
            histogram = equalSpanHistogram(lags, HISTOGRAM_SPAN_DAYS, HISTOGRAM_SPAN_COUNT),
        )
    }

    companion object {
        /**
         * Labelled, adjustable bucket edges for the lag histogram.
         * [FruitingPatternAssumptions.FRUITING_LAG_DAYS] is reused *by reference*, not copied, as
         * the second bucket, so the chart's highlighted range can never drift from the rule of
         * thumb it is testing. The other three edges (0–6, 22–35, 36+) exist only to give that
         * bucket a "before" and an "after" to be compared against.
         */
        val BUCKET_RANGES: List<IntRange> = listOf(
            0..6,
            FruitingPatternAssumptions.FRUITING_LAG_DAYS,
            22..35,
            36..Int.MAX_VALUE,
        )

        /**
         * Labelled, adjustable: the chart's span width, a week, and how many spans it draws (six, so
         * days 0 to 41). A proposal awaiting the owner (data part C report, RECORD -668): the rule
         * of thumb's 7–21 days is 15 days wide, so no equal width other than 1, 3, 5 or 15 days lines
         * its edges up with span edges; the chart shades the rule's own days behind the bars instead.
         */
        const val HISTOGRAM_SPAN_DAYS = 7
        const val HISTOGRAM_SPAN_COUNT = 6

        /**
         * Counts [lags] into [spanCount] spans of [spanDays] days from day 0; a lag past the last span
         * goes to [FruitingLagHistogram.beyondCount]. Pure, so the bucketing is tested headless.
         */
        fun equalSpanHistogram(lags: List<Int>, spanDays: Int, spanCount: Int): FruitingLagHistogram {
            require(spanDays > 0 && spanCount > 0) { "spanDays and spanCount must be positive: $spanDays, $spanCount" }
            val counts = IntArray(spanCount)
            var beyond = 0
            lags.forEach { lag ->
                require(lag >= 0) { "A lag is days after a preceding event, never negative: $lag" }
                val index = lag / spanDays
                if (index < spanCount) counts[index]++ else beyond++
            }
            val spans = counts.mapIndexed { index, count ->
                LagSpan(firstDay = index * spanDays, lastDay = index * spanDays + spanDays - 1, count = count)
            }
            return FruitingLagHistogram(spanDays = spanDays, spans = spans, beyondCount = beyond)
        }

        private fun labelFor(range: IntRange): String =
            if (range.last == Int.MAX_VALUE) "${range.first}+ days" else "${range.first}–${range.last} days"
    }
}
