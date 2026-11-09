package com.zynergylabs.forager.app.domain

import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.LagSpan
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The fruiting-lag chart's equal spans (data part C, dispatch -668, the owner's "the chart gets axes,
 * labels and equal spans"), headless. The edges are the subject: day 6 and day 7 land in different
 * spans, and day 41 is the chart's last day while day 42 is past it and counted, not dropped.
 */
class FruitingLagHistogramTest {

    @Test
    fun `lags fall into week-long spans from day 0, edges included, and past the last span they are counted beyond it`() {
        val histogram = ComputeFruitingLagDistributionUseCase.equalSpanHistogram(
            lags = listOf(0, 6, 7, 13, 14, 21, 41, 42, 100),
            spanDays = 7,
            spanCount = 6,
        )

        assertEquals(
            listOf(
                LagSpan(0, 6, 2),
                LagSpan(7, 13, 2),
                LagSpan(14, 20, 1),
                LagSpan(21, 27, 1),
                LagSpan(28, 34, 0),
                LagSpan(35, 41, 1),
            ),
            histogram.spans,
        )
        assertEquals(2, histogram.beyondCount)
        assertEquals(42, histogram.axisEndDay)
    }

    @Test
    fun `every span is the same width`() {
        val histogram = ComputeFruitingLagDistributionUseCase.equalSpanHistogram(emptyList(), spanDays = 7, spanCount = 6)

        assertEquals(setOf(7), histogram.spans.map { it.lastDay - it.firstDay + 1 }.toSet())
        assertEquals(0, histogram.beyondCount)
    }

    @Test
    fun `a negative lag is refused rather than counted`() {
        assertThrows(IllegalArgumentException::class.java) {
            ComputeFruitingLagDistributionUseCase.equalSpanHistogram(listOf(-1), spanDays = 7, spanCount = 6)
        }
    }

    /**
     * Through the use case's real entry point: one soaking event ending 1 Aug 2025, and sightings 0,
     * 6, 7, 21, 41, 42 and 100 days after it. The histogram and the four labelled buckets are two
     * views of the same lags, so they hold the same total, and the chart's spans are the shipped
     * constants (7 days, 6 spans).
     */
    @Test
    fun `the use case fills the histogram from the same lags as its buckets`() {
        val eventEnd = LocalDate.of(2025, 8, 1)
        val days = generateSequence(LocalDate.of(2025, 7, 20)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(LocalDate.of(2025, 11, 30)) }
            .map { date -> dryDay(date, if (date == eventEnd) 12.0 else 0.0) }
            .toList()
        val lags = listOf(0L, 6L, 7L, 21L, 41L, 42L, 100L)
        val sightings = lags.mapIndexed { index, lag -> sighting(index.toLong(), eventEnd.plusDays(lag)) }

        val distribution = ComputeFruitingLagDistributionUseCase()(
            REGION, month = 8, TaxonFilter.FUNGI, sightings, days, totalResultsOnServer = sightings.size,
        )

        assertEquals(listOf(2, 1, 0, 1, 0, 1), distribution.histogram.spans.map { it.count })
        assertEquals(2, distribution.histogram.beyondCount)
        assertEquals(ComputeFruitingLagDistributionUseCase.HISTOGRAM_SPAN_DAYS, distribution.histogram.spanDays)
        val bucketedLags = distribution.buckets.filter { it.lagDaysRange != null }.sumOf { it.count }
        assertEquals(bucketedLags, distribution.histogram.spans.sumOf { it.count } + distribution.histogram.beyondCount)
        assertEquals(lags.size, bucketedLags)
    }

    private fun dryDay(date: LocalDate, mm: Double) = DailyWeather(
        date = date,
        isForecast = false,
        precipitationMm = mm,
        evapotranspirationMm = null,
        shallowSoilMoistureM3M3 = null,
        deeperSoilMoistureM3M3 = null,
        soilTemperatureMeanC = null,
        soilTemperatureMinC = null,
        soilTemperatureMaxC = null,
    )

    private fun sighting(id: Long, observedOn: LocalDate) = Sighting(
        observationId = id,
        taxonId = 1L,
        scientificName = "Species",
        commonName = null,
        lat = REGION.lat,
        lng = REGION.lng,
        observedOn = observedOn,
        photoUrl = null,
    )

    private companion object {
        val REGION = Region(lat = 45.5, lng = -122.6, radiusKm = 10)
    }
}
