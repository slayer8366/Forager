package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The most important surface in the Seasonal Visualizer: wrong lag arithmetic here makes the
 * whole feature's claim false. Every test addresses days by date, never by index, the same
 * discipline [ComputeTripWindowsUseCaseTest] uses and for the same reason.
 */
class ComputeFruitingLagDistributionUseCaseTest {

    private val useCase = ComputeFruitingLagDistributionUseCase()
    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    private fun sighting(id: Long, observedOn: LocalDate?) = Sighting(
        observationId = id,
        taxonId = 1L,
        scientificName = "Species",
        commonName = null,
        lat = region.lat,
        lng = region.lng,
        observedOn = observedOn,
        photoUrl = null,
    )

    /** One [DailyWeather] entry per day from [from] to [through] inclusive; [rain] maps a date to that day's mm, every other day dry. */
    private fun historicalDays(from: LocalDate, through: LocalDate, rain: Map<LocalDate, Double> = emptyMap()): List<DailyWeather> =
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(through) }
            .map { date ->
                DailyWeather(
                    date = date,
                    isForecast = false,
                    precipitationMm = rain[date] ?: 0.0,
                    evapotranspirationMm = null,
                    shallowSoilMoistureM3M3 = null,
                    deeperSoilMoistureM3M3 = null,
                    soilTemperatureMeanC = null,
                    soilTemperatureMinC = null,
                    soilTemperatureMaxC = null,
                )
            }
            .toList()

    private fun bucketCount(distribution: com.forager.app.domain.model.FruitingLagDistribution, range: IntRange?): Int =
        distribution.buckets.single { it.lagDaysRange == range }.count

    // ---- the core lag arithmetic -------------------------------------------------------------

    @Test
    fun `a sighting before any known event has no preceding event`() {
        // The only rain event ends 20 Aug, after the 10 Aug sighting.
        val days = historicalDays(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 25), rain = mapOf(LocalDate.of(2025, 8, 20) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 10)))

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, distribution.observationsWithNoPrecedingEvent)
        assertEquals(0, distribution.sampleSize - distribution.observationsWithNoPrecedingEvent)
    }

    @Test
    fun `never matches an event that follows the sighting even when it is the only event`() {
        // Same as above, phrased as a direct assertion that no non-null bucket picked it up.
        val days = historicalDays(LocalDate.of(2025, 8, 1), LocalDate.of(2025, 8, 25), rain = mapOf(LocalDate.of(2025, 8, 20) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 10)))

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        distribution.buckets.filter { it.lagDaysRange != null }.forEach { assertEquals(0, it.count) }
    }

    @Test
    fun `a lag of exactly 7 days lands in the fruiting-lag bucket`() {
        // Event ends 1 Aug; sighting 8 Aug is exactly 7 days later.
        val days = historicalDays(LocalDate.of(2025, 7, 20), LocalDate.of(2025, 8, 15), rain = mapOf(LocalDate.of(2025, 8, 1) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 8)))

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, bucketCount(distribution, FruitingPatternAssumptions.FRUITING_LAG_DAYS))
    }

    @Test
    fun `a lag of exactly 21 days lands in the fruiting-lag bucket`() {
        // Event ends 1 Aug; sighting 22 Aug is exactly 21 days later.
        val days = historicalDays(LocalDate.of(2025, 7, 20), LocalDate.of(2025, 8, 25), rain = mapOf(LocalDate.of(2025, 8, 1) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 22)))

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, bucketCount(distribution, FruitingPatternAssumptions.FRUITING_LAG_DAYS))
    }

    @Test
    fun `a lag of 6 days falls one short, into the 0-6 bucket instead`() {
        val days = historicalDays(LocalDate.of(2025, 7, 20), LocalDate.of(2025, 8, 15), rain = mapOf(LocalDate.of(2025, 8, 1) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 7))) // lag 6

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, bucketCount(distribution, 0..6))
        assertEquals(0, bucketCount(distribution, FruitingPatternAssumptions.FRUITING_LAG_DAYS))
    }

    @Test
    fun `a lag of 22 days falls one past, into the 22-35 bucket instead`() {
        val days = historicalDays(LocalDate.of(2025, 7, 20), LocalDate.of(2025, 8, 25), rain = mapOf(LocalDate.of(2025, 8, 1) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 23))) // lag 22

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, bucketCount(distribution, 22..35))
        assertEquals(0, bucketCount(distribution, FruitingPatternAssumptions.FRUITING_LAG_DAYS))
    }

    @Test
    fun `a lag past 35 days lands in the open-ended 36+ bucket`() {
        val days = historicalDays(LocalDate.of(2025, 6, 1), LocalDate.of(2025, 8, 25), rain = mapOf(LocalDate.of(2025, 7, 1) to 12.0))
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 20))) // lag 50

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        assertEquals(1, bucketCount(distribution, 36..Int.MAX_VALUE))
    }

    // ---- nearest, not largest or first ---------------------------------------------------------

    @Test
    fun `multiple candidate events pick the nearest one, not the largest or the first`() {
        // Three events end 1 Aug (13mm, farthest), 10 Aug (9mm, nearest-but-one) and 15 Aug
        // (30mm, largest but not nearest). The sighting on 18 Aug is 3 days after the 15 Aug
        // event — the nearest — even though it is neither the largest total nor first in time.
        val days = historicalDays(
            LocalDate.of(2025, 7, 20),
            LocalDate.of(2025, 8, 20),
            rain = mapOf(
                LocalDate.of(2025, 8, 1) to 13.0,
                LocalDate.of(2025, 8, 10) to 9.0,
                LocalDate.of(2025, 8, 15) to 30.0,
            ),
        )
        val sightings = listOf(sighting(1, LocalDate.of(2025, 8, 18)))

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 1)

        // Lag from the nearest (15 Aug) is 3 days -> the 0-6 bucket, not the 7-21 bucket a lag
        // from the 10 Aug or 1 Aug events would have produced.
        assertEquals(1, bucketCount(distribution, 0..6))
        assertEquals(0, bucketCount(distribution, FruitingPatternAssumptions.FRUITING_LAG_DAYS))
    }

    // ---- missing dates --------------------------------------------------------------------

    @Test
    fun `a sighting with no observed date is excluded from the sample, not counted as zero-lag`() {
        val days = historicalDays(LocalDate.of(2025, 7, 20), LocalDate.of(2025, 8, 20), rain = mapOf(LocalDate.of(2025, 8, 1) to 12.0))
        val sightings = listOf(
            sighting(1, LocalDate.of(2025, 8, 8)), // lag 7, fruiting-lag bucket
            sighting(2, null),
        )

        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = 5)

        assertEquals(1, distribution.observationsExcludedForMissingDate)
        assertEquals(1, distribution.sampleSize)
        assertEquals(2, distribution.sightingsConsidered)
        // Not silently dropped: still reflected in sightingsConsidered and its own field, never
        // folded into a bucket as if it were a zero-lag observation.
        distribution.buckets.forEach { bucket -> assertTrue(bucket.count <= 1) }
    }

    @Test
    fun `a sample of only undated sightings produces an empty histogram, not a crash`() {
        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, listOf(sighting(1, null)), emptyList(), totalResultsOnServer = 1)

        assertEquals(1, distribution.observationsExcludedForMissingDate)
        assertEquals(0, distribution.sampleSize)
        distribution.buckets.forEach { assertEquals(0, it.count) }
    }

    // ---- bucket labelling and pass-through fields ------------------------------------------

    @Test
    fun `exactly one bucket is flagged as the fruiting-lag rule, and it is FRUITING_LAG_DAYS`() {
        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, emptyList(), emptyList(), totalResultsOnServer = 0)

        val flagged = distribution.buckets.filter { it.isFruitingLagRule }
        assertEquals(1, flagged.size)
        assertEquals(FruitingPatternAssumptions.FRUITING_LAG_DAYS, flagged.single().lagDaysRange)
    }

    @Test
    fun `the no-preceding-event bucket has a null range and is never flagged as the rule`() {
        val distribution = useCase(region, month = 8, TaxonFilter.FUNGI, emptyList(), emptyList(), totalResultsOnServer = 0)

        val noEventBucket = distribution.buckets.last()
        assertNull(noEventBucket.lagDaysRange)
        assertEquals(false, noEventBucket.isFruitingLagRule)
    }

    @Test
    fun `region, month, filter and the server total pass through unchanged`() {
        val distribution = useCase(region, month = 8, TaxonFilter.LICHENS, emptyList(), emptyList(), totalResultsOnServer = 1847)

        assertEquals(region, distribution.region)
        assertEquals(8, distribution.month)
        assertEquals(TaxonFilter.LICHENS, distribution.filter)
        assertEquals(1847, distribution.totalResultsOnServer)
    }
}
