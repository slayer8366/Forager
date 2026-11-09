package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.ComputeFruitingLagDistributionUseCase
import com.zynergylabs.forager.app.domain.SoilMoistureLevel
import com.zynergylabs.forager.app.domain.model.DailyRain
import com.zynergylabs.forager.app.domain.model.NoTripWindowReason
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.SoilAvailability
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.domain.model.UnitSystem
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * What the Seasonal tab's charts, conditions table and soil scale show (data part C, dispatch -668),
 * headless: the scales, the axis labels, the band and the words TalkBack reads. The drawing itself is
 * device-only (Robolectric renders no Canvas).
 */
class SeasonalChartModelsTest {

    private val start = LocalDate.of(2026, 9, 23)

    @Test
    fun `the daily rain chart has one equal slot a day, scaled to the wettest day, with the dates at either end`() {
        val days = (0..13).map { DailyRain(start.plusDays(it.toLong()), if (it == 8) 6.0 else if (it == 11) 3.0 else 0.0) }

        val chart = dailyRainChart(days, UnitSystem.METRIC)!!

        assertEquals(14, chart.slotCount)
        assertEquals(days.map { it.mm }, chart.values)
        assertEquals(6.0, chart.yMax, 0.0)
        assertEquals("6.0mm", chart.yTopLabel)
        assertEquals("0", chart.yBottomLabel)
        assertEquals(listOf(AxisLabel(0f, "Sep 23, 2026"), AxisLabel(1f, "Oct 6, 2026")), chart.xLabels)
        assertEquals("Daily rain from Sep 23, 2026 to Oct 6, 2026. Wettest day Oct 1, 2026, 6.0mm.", chart.description)
        assertEquals(0.5f, barHeightFraction(3.0, chart.yMax), 0.0f)
    }

    @Test
    fun `a day missing from the response is a gap in the daily rain chart, not a zero and not a closed-up span`() {
        val days = listOf(DailyRain(start, 1.0), DailyRain(start.plusDays(2), 2.0))

        val chart = dailyRainChart(days, UnitSystem.METRIC)!!

        assertEquals(3, chart.slotCount)
        assertEquals(listOf(1.0, null, 2.0), chart.values)
    }

    @Test
    fun `the daily rain chart reads in inches under the imperial setting, and says when no day had rain`() {
        val dry = (0..13).map { DailyRain(start.plusDays(it.toLong()), 0.0) }

        val chart = dailyRainChart(dry, UnitSystem.IMPERIAL)!!

        assertEquals("0.0 in", chart.yTopLabel)
        assertEquals(0f, barHeightFraction(0.0, chart.yMax), 0.0f)
        assertEquals("Daily rain from Sep 23, 2026 to Oct 6, 2026. No rain on any day.", chart.description)
        assertNull(dailyRainChart(emptyList(), UnitSystem.METRIC))
    }

    @Test
    fun `the fruiting-lag chart ticks every 7 days to 42, shades day 7 to the end of day 21, and prints each span's count`() {
        val histogram = ComputeFruitingLagDistributionUseCase.equalSpanHistogram(listOf(0, 8, 9, 15, 22, 50), spanDays = 7, spanCount = 6)

        val chart = fruitingLagChart(histogram)

        assertEquals(listOf("0", "7", "14", "21", "28", "35", "42"), chart.xLabels.map { it.text })
        assertEquals(listOf(0f, 7 / 42f, 14 / 42f, 21 / 42f, 28 / 42f, 35 / 42f, 1f), chart.xLabels.map { it.fraction })
        assertEquals(7 / 42f, chart.band!!.start, 0.0001f)
        assertEquals(22 / 42f, chart.band!!.endInclusive, 0.0001f)
        assertEquals(listOf("1", "2", "1", "1", "0", "0"), chart.valueLabels)
        assertEquals(2.0, chart.yMax, 0.0)
        assertEquals("Sightings", chart.yTitle)
        assertEquals("Days after a soaking rain", chart.xTitle)
        assertEquals(
            "1 observation(s) came more than 41 days after a soaking rain and are past the end of the chart.",
            fruitingLagBeyondNote(histogram),
        )
        assertNull(fruitingLagBeyondNote(ComputeFruitingLagDistributionUseCase.equalSpanHistogram(listOf(41), 7, 6)))
    }

    /**
     * The days-since figure counts from the newest observed day, which is yesterday at the searched
     * location, so 0 is yesterday. The card used to print it as "Rain today.".
     */
    @Test
    fun `the last rainy day reads from yesterday, not today`() {
        assertEquals("Yesterday", lastRainyDayLabel(0))
        assertEquals("2 days ago", lastRainyDayLabel(1))
        assertEquals("3 days ago", lastRainyDayLabel(2))
        assertEquals("None in 14 days", lastRainyDayLabel(null))
    }

    @Test
    fun `the soil scale's words are the owner's, and the figure keeps two decimals`() {
        assertEquals(listOf("Dry", "Moist", "Wet"), SoilMoistureLevel.entries.map(::soilMoistureWord))
        assertEquals("0.27 m³/m³", soilMoistureFigure(0.2712))
    }

    @Test
    fun `dates read Oct 7, 2026, in the no-window message as well`() {
        assertEquals("Oct 7, 2026", displayDate(LocalDate.of(2026, 10, 7)))
        val report = TripWindowReport(
            region = Region(lat = 45.5, lng = -122.6, radiusKm = 10),
            referenceDay = LocalDate.of(2026, 10, 7),
            horizonEnd = LocalDate.of(2026, 10, 14),
            rainEvents = emptyList(),
            windows = emptyList(),
            noWindowReason = NoTripWindowReason.LagRangeOutsideHorizon(
                mostRecentEventEnd = LocalDate.of(2026, 10, 6),
                lagRangeStart = LocalDate.of(2026, 10, 13),
                lagRangeEnd = LocalDate.of(2026, 10, 27),
                horizonEnd = LocalDate.of(2026, 10, 14),
            ),
            soilAvailability = SoilAvailability(shallowMoistureBand = null, deeperMoistureBand = null, temperatureBand = null),
        )

        assertEquals(
            "The most recent qualifying rain ended Oct 6, 2026. The 7–21 day window it points to is " +
                "Oct 13, 2026 – Oct 27, 2026, past the Oct 14, 2026 horizon this search plans within.",
            noTripWindowMessage(report, UnitSystem.METRIC),
        )
    }
}
