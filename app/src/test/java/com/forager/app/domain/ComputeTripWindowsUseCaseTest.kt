package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.SoilAvailability
import com.forager.app.domain.model.SoilDepthBand
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.domain.model.WeatherSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Trip-window search over synthetic series.
 *
 * The fixtures below are laid out the way a real response is: 14 observed days ending the day
 * before the reference day, then the reference day and six more forecast days. Days are addressed
 * by date throughout, never by index, because that is the property under test.
 */
class ComputeTripWindowsUseCaseTest {

    private val useCase = ComputeTripWindowsUseCase()
    private val region = Region(lat = 45.5, lng = -122.6, radiusKm = 10)

    private val referenceDay: LocalDate = LocalDate.of(2026, 8, 16)
    private val firstDay: LocalDate = referenceDay.minusDays(14) // 2026-08-02
    private val lastDay: LocalDate = referenceDay.plusDays(6) // 2026-08-22

    /**
     * Builds a 21-day series. [rain] maps a date to that day's precipitation in mm; every other
     * day is dry. [soilTemperature] and [soilMoisture] apply to every day when given, and
     * [evapotranspiration] defaults to a flat value so drying sums are predictable.
     */
    private fun series(
        rain: Map<LocalDate, Double> = emptyMap(),
        soilMoisture: Double? = null,
        soilTemperature: Double? = null,
        evapotranspiration: Double? = 3.0,
        referenceDay: LocalDate = this.referenceDay,
        firstDay: LocalDate = this.firstDay,
        lastDay: LocalDate = this.lastDay,
    ): WeatherSeries {
        val days = generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .map { date ->
                DailyWeather(
                    date = date,
                    isForecast = !date.isBefore(referenceDay),
                    precipitationMm = rain[date] ?: 0.0,
                    evapotranspirationMm = evapotranspiration,
                    shallowSoilMoistureM3M3 = soilMoisture,
                    deeperSoilMoistureM3M3 = soilMoisture?.plus(0.05),
                    soilTemperatureMeanC = soilTemperature,
                    soilTemperatureMinC = soilTemperature?.minus(3.0),
                    soilTemperatureMaxC = soilTemperature?.plus(3.0),
                )
            }
            .toList()
        return WeatherSeries(
            region = region,
            referenceDay = referenceDay,
            days = days,
            soilAvailability = SoilAvailability(
                shallowMoistureBand = soilMoisture?.let { SoilDepthBand(0, 10) },
                deeperMoistureBand = soilMoisture?.let { SoilDepthBand(10, 40) },
                temperatureBand = soilTemperature?.let { SoilDepthBand(0, 10) },
            ),
        )
    }

    // ---- a clear window after a rain event -------------------------------------------------

    @Test
    fun `a soaking event produces a window at the stated lag`() {
        // 12mm on 12 Aug. The 7-21 day lag range from it is 19 Aug - 2 Sep, so of the seven
        // plannable days (16-22 Aug) only 19-22 Aug fall inside.
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0)))

        assertEquals(1, report.windows.size)
        val window = report.windows.single()
        assertEquals(LocalDate.of(2026, 8, 19), window.startDate)
        assertEquals(LocalDate.of(2026, 8, 22), window.endDate)
        assertEquals(4, window.dayCount)
        assertEquals(7, window.daysAfterMostRecentRainAtStart)
        assertEquals(10, window.daysAfterMostRecentRainAtEnd)
        assertNull(report.noWindowReason)
    }

    @Test
    fun `the window carries the rain event that produced it`() {
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 11) to 6.0,
                    LocalDate.of(2026, 8, 12) to 7.0,
                ),
            ),
        )

        val event = report.windows.single().precedingRainEvents.single()
        assertEquals(LocalDate.of(2026, 8, 11), event.startDate)
        assertEquals(LocalDate.of(2026, 8, 12), event.endDate)
        assertEquals(13.0, event.totalMm, 0.0001)
        assertEquals(2, event.dayCount)
        assertEquals(false, event.isForecast)
    }

    @Test
    fun `a rain event still in the forecast is marked as not yet fallen`() {
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 18) to 14.0)))

        assertEquals(1, report.rainEvents.size)
        assertTrue(report.rainEvents.single().isForecast)
    }

    // ---- no window during a dry spell ------------------------------------------------------

    @Test
    fun `a completely dry series identifies no window and says why`() {
        val report = useCase(series())

        assertTrue(report.windows.isEmpty())
        val reason = report.noWindowReason as NoTripWindowReason.NoQualifyingRainEvent
        assertEquals(21, reason.daysExamined)
        assertEquals(0.0, reason.largestRunTotalMm, 0.0001)
        assertEquals(FruitingPatternAssumptions.SOAKING_EVENT_MIN_TOTAL_MM, reason.requiredTotalMm, 0.0001)
    }

    @Test
    fun `drizzle below the per-day threshold never starts an event`() {
        // 1.9mm every day for three weeks is 39.9mm of rain, comfortably over the event total,
        // but no single day clears the per-day threshold so there is no run to total up.
        val everyDay = generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .associateWith { 1.9 }

        val report = useCase(series(rain = everyDay))

        assertTrue(report.rainEvents.isEmpty())
        val reason = report.noWindowReason as NoTripWindowReason.NoQualifyingRainEvent
        assertEquals(0.0, reason.largestRunTotalMm, 0.0001)
    }

    // ---- boundaries around the thresholds --------------------------------------------------

    @Test
    fun `a run totalling exactly the soaking threshold qualifies`() {
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 11) to 5.0,
                    LocalDate.of(2026, 8, 12) to 5.0,
                ),
            ),
        )

        assertEquals(10.0, report.rainEvents.single().totalMm, 0.0001)
        assertEquals(1, report.windows.size)
    }

    @Test
    fun `a run just under the soaking threshold does not qualify`() {
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 11) to 5.0,
                    LocalDate.of(2026, 8, 12) to 4.9,
                ),
            ),
        )

        assertTrue(report.rainEvents.isEmpty())
        val reason = report.noWindowReason as NoTripWindowReason.NoQualifyingRainEvent
        assertEquals(9.9, reason.largestRunTotalMm, 0.0001)
    }

    @Test
    fun `a day exactly at the per-day rain threshold counts toward a run`() {
        // 2.0mm is the threshold exactly; without it the run is 8.0mm and does not qualify.
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 11) to 2.0,
                    LocalDate.of(2026, 8, 12) to 8.0,
                ),
            ),
        )

        val event = report.rainEvents.single()
        assertEquals(LocalDate.of(2026, 8, 11), event.startDate)
        assertEquals(10.0, event.totalMm, 0.0001)
    }

    @Test
    fun `the window starts exactly at the lower lag bound`() {
        // Event ends 9 Aug; 16 Aug is exactly 7 days later, the first day in the lag range.
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 9) to 12.0)))

        assertEquals(LocalDate.of(2026, 8, 16), report.windows.single().startDate)
        assertEquals(FruitingPatternAssumptions.FRUITING_LAG_DAYS.first, report.windows.single().daysAfterMostRecentRainAtStart)
    }

    @Test
    fun `a day one short of the lower lag bound is excluded`() {
        // Event ends 10 Aug, so 16 Aug is 6 days later — one short — and the window starts on
        // 17 Aug instead.
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 10) to 12.0)))

        assertEquals(LocalDate.of(2026, 8, 17), report.windows.single().startDate)
    }

    @Test
    fun `the window ends exactly at the upper lag bound`() {
        // Reference day moved to 21 Aug so the 21-day bound lands inside the plannable range:
        // an event ending 2 Aug covers 9-23 Aug, and 24 Aug (lag 22) must be excluded.
        val laterReference = LocalDate.of(2026, 8, 21)
        val report = useCase(
            series(
                rain = mapOf(LocalDate.of(2026, 8, 2) to 12.0),
                referenceDay = laterReference,
                firstDay = laterReference.minusDays(19),
                lastDay = laterReference.plusDays(6), // 27 Aug
            ),
        )

        val window = report.windows.single()
        assertEquals(LocalDate.of(2026, 8, 21), window.startDate)
        assertEquals(LocalDate.of(2026, 8, 23), window.endDate)
        assertEquals(FruitingPatternAssumptions.FRUITING_LAG_DAYS.last, window.daysAfterMostRecentRainAtEnd)
    }

    // ---- events, runs and gaps -------------------------------------------------------------

    @Test
    fun `a dry day splits one wet stretch into two events`() {
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 4) to 11.0,
                    // 5 Aug dry
                    LocalDate.of(2026, 8, 6) to 12.0,
                ),
            ),
        )

        assertEquals(2, report.rainEvents.size)
        // Most recent first.
        assertEquals(LocalDate.of(2026, 8, 6), report.rainEvents[0].endDate)
        assertEquals(LocalDate.of(2026, 8, 4), report.rainEvents[1].endDate)
    }

    @Test
    fun `a window covered by two events carries both, most recent first`() {
        // Events ending 4 Aug (lag range 11-25 Aug) and 11 Aug (lag range 18 Aug - 1 Sep).
        // 18-22 Aug is inside both.
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 4) to 11.0,
                    LocalDate.of(2026, 8, 11) to 12.0,
                ),
            ),
        )

        val window = report.windows.single()
        assertEquals(LocalDate.of(2026, 8, 16), window.startDate)
        assertEquals(LocalDate.of(2026, 8, 22), window.endDate)
        assertEquals(2, window.precedingRainEvents.size)
        assertEquals(LocalDate.of(2026, 8, 11), window.precedingRainEvents[0].endDate)
        assertEquals(LocalDate.of(2026, 8, 4), window.precedingRainEvents[1].endDate)
        // Gaps are measured from the most recent of them.
        assertEquals(5, window.daysAfterMostRecentRainAtStart)
        assertEquals(11, window.daysAfterMostRecentRainAtEnd)
    }

    @Test
    fun `rain too recent for the lag range reports the range it points at instead`() {
        // 14mm forecast for the reference day itself. Its lag range is 23 Aug - 6 Sep, entirely
        // past the 22 Aug horizon, so there is no window and the report says where it is looking.
        val report = useCase(series(rain = mapOf(referenceDay to 14.0)))

        assertTrue(report.windows.isEmpty())
        val reason = report.noWindowReason as NoTripWindowReason.LagRangeOutsideHorizon
        assertEquals(referenceDay, reason.mostRecentEventEnd)
        assertEquals(LocalDate.of(2026, 8, 23), reason.lagRangeStart)
        assertEquals(LocalDate.of(2026, 9, 6), reason.lagRangeEnd)
        assertEquals(lastDay, reason.horizonEnd)
    }

    @Test
    fun `a series with no forecast days says so rather than reporting an empty search`() {
        val report = useCase(
            series(
                rain = mapOf(LocalDate.of(2026, 8, 5) to 12.0),
                lastDay = referenceDay.minusDays(1),
            ),
        )

        assertTrue(report.windows.isEmpty())
        assertEquals(NoTripWindowReason.NoForecastDays, report.noWindowReason)
    }

    // ---- what a window reports -------------------------------------------------------------

    @Test
    fun `a window carries the soil state measured across its days`() {
        val report = useCase(
            series(
                rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0),
                soilMoisture = 0.24,
                soilTemperature = 14.0,
            ),
        )

        val window = report.windows.single()
        assertEquals(0.24, window.meanShallowSoilMoistureM3M3!!, 0.0001)
        assertEquals(0.29, window.meanDeeperSoilMoistureM3M3!!, 0.0001)
        assertEquals(14.0, window.meanSoilTemperatureC!!, 0.0001)
        assertEquals(11.0, window.minSoilTemperatureC!!, 0.0001)
        assertEquals(17.0, window.maxSoilTemperatureC!!, 0.0001)
    }

    @Test
    fun `soil fields stay null when the location served no soil data`() {
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0)))

        val window = report.windows.single()
        assertNull(window.meanShallowSoilMoistureM3M3)
        assertNull(window.meanSoilTemperatureC)
        assertNull(report.soilAvailability.shallowMoistureBand)
    }

    @Test
    fun `drying since the rain is summed from the day after the event through the window start`() {
        // Event ends 12 Aug, window starts 19 Aug: 13-19 Aug inclusive is seven days at 3.0mm.
        val report = useCase(
            series(rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0), evapotranspiration = 3.0),
        )

        assertEquals(21.0, report.windows.single().evapotranspirationSinceRainMm!!, 0.0001)
    }

    @Test
    fun `drying is reported as unknown rather than as a partial sum`() {
        val report = useCase(
            series(rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0), evapotranspiration = null),
        )

        assertNull(report.windows.single().evapotranspirationSinceRainMm)
    }

    @Test
    fun `rain forecast during the window itself is reported separately from the preceding event`() {
        val report = useCase(
            series(
                rain = mapOf(
                    LocalDate.of(2026, 8, 12) to 12.0,
                    LocalDate.of(2026, 8, 20) to 4.0,
                ),
            ),
        )

        val window = report.windows.single()
        assertEquals(4.0, window.precipitationDuringWindowMm, 0.0001)
        // The 4mm day is its own run of 4mm, under the soaking total, so it is not an event.
        assertEquals(1, report.rainEvents.size)
        assertEquals(LocalDate.of(2026, 8, 12), report.rainEvents.single().endDate)
    }

    @Test
    fun `the report echoes the region, reference day and horizon it searched`() {
        val report = useCase(series(rain = mapOf(LocalDate.of(2026, 8, 12) to 12.0)))

        assertEquals(region, report.region)
        assertEquals(referenceDay, report.referenceDay)
        assertEquals(lastDay, report.horizonEnd)
    }

    // ---- the hard constraint ---------------------------------------------------------------

    /**
     * The one rule this feature must not break by accident: it reports inputs, never a verdict.
     *
     * Asserted against the types' actual field and accessor schemas rather than against a sample
     * of output, so a scoring field added later fails here even if no existing assertion covers
     * it. There is no measured correlation in this codebase between weather and observation
     * frequency, so a number combining these fields would state one exists.
     *
     * Java reflection, not `KClass.members`: kotlin-reflect is not on the test classpath and
     * adding it would be a new dependency. `declaredFields` plus `declaredMethods` covers both
     * constructor properties and computed `val`s, which is the whole surface a score could hide in.
     */
    @Test
    fun `no trip-planning type exposes a score, rank, probability or rating`() {
        val forbidden = listOf(
            "score", "rank", "probability", "percent", "rating", "stars", "likelihood",
            "confidence", "best", "recommend", "quality",
        )
        val types = listOf(
            com.forager.app.domain.model.TripWindow::class.java,
            com.forager.app.domain.model.RainEvent::class.java,
            TripWindowReport::class.java,
        )

        val allNames = types.flatMap { type ->
            type.declaredFields.map { it.name } + type.declaredMethods.map { it.name }
        }
        // Guards against a vacuous pass: if reflection returned nothing, the scan below would
        // find no offenders no matter what the types actually declare.
        assertTrue(
            "reflection surfaced no member names, so the scan proves nothing",
            allNames.contains("precedingRainEvents") && allNames.contains("windows"),
        )

        val offenders = types.flatMap { type ->
            val names = type.declaredFields.map { it.name } + type.declaredMethods.map { it.name }
            names.flatMap { name ->
                forbidden.filter { name.lowercase().contains(it) }.map { "${type.simpleName}.$name" }
            }
        }

        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun `every window is reported as a plain interval, never ordered by preference`() {
        // Two separate windows from two events far enough apart not to merge. They must come back
        // in chronological order — the order the days happen in — not "best first".
        val laterReference = LocalDate.of(2026, 8, 21)
        val report = useCase(
            series(
                rain = mapOf(
                    // lag range 9-23 Aug
                    LocalDate.of(2026, 8, 2) to 30.0,
                    // lag range 25 Aug - 8 Sep
                    LocalDate.of(2026, 8, 18) to 12.0,
                ),
                referenceDay = laterReference,
                firstDay = laterReference.minusDays(19),
                lastDay = laterReference.plusDays(6), // 27 Aug
            ),
        )

        assertEquals(2, report.windows.size)
        assertEquals(LocalDate.of(2026, 8, 21), report.windows[0].startDate)
        assertEquals(LocalDate.of(2026, 8, 23), report.windows[0].endDate)
        assertEquals(LocalDate.of(2026, 8, 25), report.windows[1].startDate)
        assertEquals(LocalDate.of(2026, 8, 27), report.windows[1].endDate)
        assertNotNull(report.windows[0])
    }
}
