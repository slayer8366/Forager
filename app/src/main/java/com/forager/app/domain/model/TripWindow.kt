package com.forager.app.domain.model

import java.time.LocalDate

/**
 * A run of consecutive days that each delivered enough rain to count, taken together.
 *
 * @param totalMm the summed precipitation across [startDate]..[endDate].
 * @param isForecast true when any day of the run has not happened yet, so callers can say "18mm
 *   fell" versus "18mm is forecast" rather than blurring the two.
 */
data class RainEvent(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val totalMm: Double,
    val isForecast: Boolean,
) {
    val dayCount: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
}

/**
 * A stretch of upcoming days that sits inside the stated post-rain lag range, with the
 * measurements that put it there.
 *
 * **This type carries no score, rank, probability or rating, and must not grow one.** Every field
 * is either a measured quantity or a date arithmetic result. The rule of thumb that makes the
 * timing interesting is stated separately and as a rule of thumb — see [ForagingWeatherGuidance]
 * — so the reader can weigh it. There is no measured correlation in this codebase between weather
 * and observation frequency; a number combining these fields would imply one exists. That is the
 * same reason [com.forager.app.domain.PredictAvailabilityUseCase] refuses to be a fitted model.
 */
data class TripWindow(
    val startDate: LocalDate,
    val endDate: LocalDate,
    /**
     * Every qualifying soaking event whose lag range covers at least one day of this window,
     * most recent first. More than one is common and is worth showing: a window can be eight days
     * after one soaking and nineteen days after an earlier one.
     */
    val precedingRainEvents: List<RainEvent>,
    /** Days from the end of [precedingRainEvents]`.first()` to [startDate]. */
    val daysAfterMostRecentRainAtStart: Int,
    /** Days from the end of [precedingRainEvents]`.first()` to [endDate]. */
    val daysAfterMostRecentRainAtEnd: Int,
    /** Mean shallow soil moisture across the window's days, m³/m³. Null when not served. */
    val meanShallowSoilMoistureM3M3: Double?,
    /** Mean deeper soil moisture across the window's days, m³/m³. Null when not served. */
    val meanDeeperSoilMoistureM3M3: Double?,
    /** Mean of the days' mean soil temperatures, °C. Null when not served. */
    val meanSoilTemperatureC: Double?,
    /** Coldest hourly soil temperature across the window's days, °C. Null when not served. */
    val minSoilTemperatureC: Double?,
    /** Warmest hourly soil temperature across the window's days, °C. Null when not served. */
    val maxSoilTemperatureC: Double?,
    /**
     * Reference evapotranspiration summed from the day after the most recent qualifying rain event
     * through [startDate] — how much drying has happened since the soaking. Null when any day in
     * that span had no value, rather than a partial sum presented as a whole one.
     */
    val evapotranspirationSinceRainMm: Double?,
    /** Rain forecast during the window itself, mm, summed across its days. */
    val precipitationDuringWindowMm: Double,
) {
    val dayCount: Int get() = (endDate.toEpochDay() - startDate.toEpochDay()).toInt() + 1
}

/** Why no window was identified. Each case carries the numbers behind it. */
sealed interface NoTripWindowReason {

    /** No run of rain days in the whole series cleared the soaking-event total. */
    data class NoQualifyingRainEvent(
        val daysExamined: Int,
        val largestRunTotalMm: Double,
        val requiredTotalMm: Double,
    ) : NoTripWindowReason

    /**
     * Soaking events exist, but the lag range they point at falls outside the days we can plan
     * for. The dates are given so the caller can say "that points at 24 Aug – 7 Sep, past the
     * 7-day horizon" instead of an unexplained empty list.
     */
    data class LagRangeOutsideHorizon(
        val mostRecentEventEnd: LocalDate,
        val lagRangeStart: LocalDate,
        val lagRangeEnd: LocalDate,
        val horizonEnd: LocalDate,
    ) : NoTripWindowReason

    /** The series had no forecast days at all, so there was nothing to plan against. */
    data object NoForecastDays : NoTripWindowReason
}

/**
 * The whole result of the trip-window search: what was found, what was searched, and — when
 * nothing was found — why.
 *
 * An empty [windows] with a populated [noWindowReason] is a reported result, not a failure and not
 * an absence dressed up as one.
 */
data class TripWindowReport(
    val region: Region,
    val referenceDay: LocalDate,
    /** Last day the search could consider; [referenceDay] plus the forecast horizon. */
    val horizonEnd: LocalDate,
    /** Every qualifying soaking event found across the series, most recent first. */
    val rainEvents: List<RainEvent>,
    val windows: List<TripWindow>,
    /** Non-null exactly when [windows] is empty. */
    val noWindowReason: NoTripWindowReason?,
    /** Which soil variables this location's weather model served, and what it did not. */
    val soilAvailability: SoilAvailability,
)
