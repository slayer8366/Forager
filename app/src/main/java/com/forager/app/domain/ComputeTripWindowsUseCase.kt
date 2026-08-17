package com.forager.app.domain

import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.RainEvent
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.domain.model.WeatherSeries
import java.time.LocalDate

/**
 * Finds the upcoming stretches of days that sit inside the stated post-rain lag range, and reports
 * the measurements that put them there.
 *
 * **What this deliberately does not do:** score, rank, rate or otherwise reduce a window to a
 * number. It reports inputs — the rain that fell, how long ago, what the soil is doing — next to a
 * separately-stated general pattern, and stops there. There is no measured relationship in this
 * codebase between weather and observation frequency, so a "73%" or a "best day" would be an
 * invented formula wearing the clothes of a prediction. Deriving that relationship from real
 * observation data is planned separate work; see [PredictAvailabilityUseCase] for the same refusal
 * applied to the ranked list.
 *
 * Pure and Android-framework-free so it is unit-testable headless.
 */
class ComputeTripWindowsUseCase {

    operator fun invoke(series: WeatherSeries): TripWindowReport {
        val events = findSoakingEvents(series.days)
        val plannable = series.forecastDays
        val horizonEnd = plannable.lastOrNull()?.date ?: series.referenceDay

        if (plannable.isEmpty()) {
            return report(series, horizonEnd, events, emptyList(), NoTripWindowReason.NoForecastDays)
        }
        if (events.isEmpty()) {
            return report(
                series,
                horizonEnd,
                events,
                emptyList(),
                NoTripWindowReason.NoQualifyingRainEvent(
                    daysExamined = series.days.size,
                    largestRunTotalMm = largestConsecutiveRainRunTotalMm(series.days),
                    requiredTotalMm = FruitingPatternAssumptions.SOAKING_EVENT_MIN_TOTAL_MM,
                ),
            )
        }

        // For each plannable day, which events' lag ranges cover it. A day with none is not part
        // of any window; a run of days that each have at least one becomes a window.
        val eventsCoveringDay: Map<LocalDate, List<RainEvent>> = plannable.associate { day ->
            day.date to events.filter { event -> lagDays(event, day.date) in FruitingPatternAssumptions.FRUITING_LAG_DAYS }
        }

        val windows = consecutiveRuns(plannable) { day -> eventsCoveringDay.getValue(day.date).isNotEmpty() }
            .map { run -> buildWindow(run, eventsCoveringDay, series.days) }

        val reason = if (windows.isNotEmpty()) {
            null
        } else {
            // Events exist but none of their lag ranges landed in the plannable days. Report the
            // window the most recent event actually points at, so the empty result is explained
            // rather than just empty.
            val mostRecent = events.first()
            NoTripWindowReason.LagRangeOutsideHorizon(
                mostRecentEventEnd = mostRecent.endDate,
                lagRangeStart = mostRecent.endDate.plusDays(FruitingPatternAssumptions.FRUITING_LAG_DAYS.first.toLong()),
                lagRangeEnd = mostRecent.endDate.plusDays(FruitingPatternAssumptions.FRUITING_LAG_DAYS.last.toLong()),
                horizonEnd = horizonEnd,
            )
        }
        return report(series, horizonEnd, events, windows, reason)
    }

    private fun report(
        series: WeatherSeries,
        horizonEnd: LocalDate,
        events: List<RainEvent>,
        windows: List<TripWindow>,
        reason: NoTripWindowReason?,
    ) = TripWindowReport(
        region = series.region,
        referenceDay = series.referenceDay,
        horizonEnd = horizonEnd,
        rainEvents = events,
        windows = windows,
        noWindowReason = reason,
        soilAvailability = series.soilAvailability,
    )

    private fun buildWindow(
        run: List<DailyWeather>,
        eventsCoveringDay: Map<LocalDate, List<RainEvent>>,
        allDays: List<DailyWeather>,
    ): TripWindow {
        val start = run.first()
        val end = run.last()
        // Every event covering any day of the run is a reason this run exists; the run is a single
        // stretch of days, so they are reported together rather than split into overlapping
        // windows per event.
        val events = run.flatMap { eventsCoveringDay.getValue(it.date) }
            .distinct()
            .sortedByDescending { it.endDate }
        val mostRecent = events.first()

        return TripWindow(
            startDate = start.date,
            endDate = end.date,
            precedingRainEvents = events,
            daysAfterMostRecentRainAtStart = lagDays(mostRecent, start.date),
            daysAfterMostRecentRainAtEnd = lagDays(mostRecent, end.date),
            meanShallowSoilMoistureM3M3 = run.meanOrNull { it.shallowSoilMoistureM3M3 },
            meanDeeperSoilMoistureM3M3 = run.meanOrNull { it.deeperSoilMoistureM3M3 },
            meanSoilTemperatureC = run.meanOrNull { it.soilTemperatureMeanC },
            minSoilTemperatureC = run.mapNotNull { it.soilTemperatureMinC }.minOrNull(),
            maxSoilTemperatureC = run.mapNotNull { it.soilTemperatureMaxC }.maxOrNull(),
            evapotranspirationSinceRainMm = evapotranspirationBetween(
                allDays,
                from = mostRecent.endDate.plusDays(1),
                through = start.date,
            ),
            precipitationDuringWindowMm = run.sumOf { it.precipitationMm },
        )
    }

    /**
     * Sums reference evapotranspiration over [from]..[through], or returns null if any day in that
     * span is missing from the series or has no value. A partial sum would read as a smaller
     * amount of drying than actually happened, which is worse than saying nothing.
     */
    private fun evapotranspirationBetween(
        days: List<DailyWeather>,
        from: LocalDate,
        through: LocalDate,
    ): Double? {
        if (from.isAfter(through)) return 0.0
        val byDate = days.associateBy { it.date }
        var total = 0.0
        var date = from
        while (!date.isAfter(through)) {
            val value = byDate[date]?.evapotranspirationMm ?: return null
            total += value
            date = date.plusDays(1)
        }
        return total
    }

    /** Days from the end of [event] to [date]. Negative when [date] precedes the event's end. */
    private fun lagDays(event: RainEvent, date: LocalDate): Int =
        (date.toEpochDay() - event.endDate.toEpochDay()).toInt()

    companion object {

        /**
         * Maximal runs of consecutive days that each cleared
         * [FruitingPatternAssumptions.RAIN_DAY_MIN_MM], kept only when the run's total cleared
         * [FruitingPatternAssumptions.SOAKING_EVENT_MIN_TOTAL_MM]. Most recent first.
         *
         * "Consecutive" is strict — a dry day splits a run in two. Bridging one-day gaps was
         * considered and rejected: it is an extra rule with nothing behind it, and two soakings
         * either side of a dry day are reported as two events, which is the more honest shape and
         * the one this codebase can defend.
         */
        internal fun findSoakingEvents(days: List<DailyWeather>): List<RainEvent> =
            consecutiveRuns(days) { it.precipitationMm >= FruitingPatternAssumptions.RAIN_DAY_MIN_MM }
                .map { run ->
                    RainEvent(
                        startDate = run.first().date,
                        endDate = run.last().date,
                        totalMm = run.sumOf { it.precipitationMm },
                        isForecast = run.any { it.isForecast },
                    )
                }
                .filter { it.totalMm >= FruitingPatternAssumptions.SOAKING_EVENT_MIN_TOTAL_MM }
                .sortedByDescending { it.endDate }

        /** The biggest total any consecutive rain-day run reached, for explaining an empty result. */
        internal fun largestConsecutiveRainRunTotalMm(days: List<DailyWeather>): Double =
            consecutiveRuns(days) { it.precipitationMm >= FruitingPatternAssumptions.RAIN_DAY_MIN_MM }
                .maxOfOrNull { run -> run.sumOf { it.precipitationMm } } ?: 0.0

        /**
         * Splits [days] into maximal runs of entries satisfying [predicate].
         *
         * Runs are broken both by a failing entry and by a gap in the dates themselves, so a
         * series with a missing day cannot silently produce a run that spans it.
         */
        private fun consecutiveRuns(
            days: List<DailyWeather>,
            predicate: (DailyWeather) -> Boolean,
        ): List<List<DailyWeather>> {
            val runs = mutableListOf<List<DailyWeather>>()
            var current = mutableListOf<DailyWeather>()
            for (day in days) {
                val contiguous = current.isEmpty() ||
                    current.last().date.plusDays(1) == day.date
                if (predicate(day) && contiguous) {
                    current.add(day)
                } else {
                    if (current.isNotEmpty()) runs.add(current.toList())
                    current = if (predicate(day)) mutableListOf(day) else mutableListOf()
                }
            }
            if (current.isNotEmpty()) runs.add(current.toList())
            return runs
        }

        private fun List<DailyWeather>.meanOrNull(select: (DailyWeather) -> Double?): Double? {
            val values = mapNotNull(select)
            return if (values.isEmpty()) null else values.average()
        }
    }
}
