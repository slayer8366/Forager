package com.forager.app.domain

/**
 * Every labelled, adjustable assumption the trip-window computation rests on, in one place.
 *
 * None of these are data-derived. Nothing in this codebase has yet measured a correlation between
 * weather and observation frequency — that is a separate, planned piece of work — so these are
 * rules of thumb chosen to bound a search, not fitted parameters. They exist as named constants
 * with stated reasoning for the same reason
 * [com.forager.app.data.repository.OpenMeteoWeatherProvider.SIGNIFICANT_RAIN_MM] does (CLAUDE.md:
 * record why a non-obvious decision was made).
 *
 * They are deliberately *not* combined into a score. A window is reported with the measurements
 * that identified it and the rule of thumb stated as a rule of thumb; the reader draws the
 * conclusion. Multiplying these numbers together into a percentage or a star rating would launder
 * a rule of thumb into a prediction the data does not support.
 */
object FruitingPatternAssumptions {

    /**
     * Labelled, adjustable assumption: the daily rain total, in mm, that counts as a day of real
     * rain rather than drizzle.
     *
     * Single source of truth — [com.forager.app.data.repository.OpenMeteoWeatherProvider]'s
     * shipped `SIGNIFICANT_RAIN_MM` is defined as this value, so the "days since significant rain"
     * line on the conditions card and the rain events found here can never drift apart. Declared
     * in the domain rather than the data layer because the domain must not depend on the data
     * layer; the dependency runs the other way.
     */
    const val RAIN_DAY_MIN_MM = 2.0

    /**
     * Labelled, adjustable assumption: how much rain, in mm, a run of consecutive rain days has to
     * add up to before it is treated as a soaking event worth planning a trip around.
     *
     * The point of a total-over-the-run threshold rather than a single-day threshold is that three
     * days of 4mm and one day of 12mm both wet the soil profile, while one 3mm day does not. The
     * number is a judgement call, not a measurement: set it lower and every damp week becomes an
     * "event", set it higher and only storms qualify.
     */
    const val SOAKING_EVENT_MIN_TOTAL_MM = 10.0

    /**
     * Labelled, adjustable assumption: the lag, in days after a soaking event ends, that the
     * trip-window search looks at.
     *
     * This is the well-known and widely-repeated foraging rule of thumb that many fleshy fungi
     * fruit roughly one to three weeks after sustained rain — it is field lore and general
     * mycological writing, not a figure this app has measured, and it is reported to the user
     * with that hedge attached (see [ForagingWeatherGuidance]). Fruiting lag genuinely varies by
     * species, substrate and temperature, so a range this wide is the honest shape for it; a
     * precise "11 days" would be invented precision.
     */
    val FRUITING_LAG_DAYS = 7..21

    /**
     * Labelled, adjustable assumption: how many days ahead the trip planner looks.
     *
     * Open-Meteo serves up to 16 forecast days on this endpoint, and the free tier will return
     * them. Seven is an operating limit chosen for the task rather than the API's full capability
     * (CLAUDE.md: a reported capability range is not an operating envelope): a foraging trip gets
     * planned a week out at most, and day-14 precipitation forecasts are not worth acting on.
     */
    const val FORECAST_HORIZON_DAYS = 7

    /**
     * Labelled, adjustable assumption: how many days of observed history to fetch.
     *
     * Matches the lookback the shipped conditions card already uses, and has to be at least
     * [FRUITING_LAG_DAYS]`.last` minus [FORECAST_HORIZON_DAYS] for the search to be able to see
     * the rain event behind a window at the far end of the horizon.
     */
    const val OBSERVED_HISTORY_DAYS = 14

    /**
     * Labelled, adjustable assumption: the soil-temperature range, in °C, quoted alongside a
     * window as broadly typical for fleshy fungal fruiting in temperate regions.
     *
     * Reported as context with the actual measured temperature next to it, never used to include
     * or exclude a window and never scored. The band is deliberately wide and stated as a rough
     * one: fruiting temperature is strongly species-specific and this app has no per-species data.
     */
    val TEMPERATE_FRUITING_SOIL_TEMPERATURE_C = 10.0..20.0

    /**
     * Labelled, adjustable assumption: the fewest non-null hourly readings a local day must have
     * before it gets a daily soil value at all.
     *
     * Twenty rather than twenty-four because a daylight-saving transition genuinely produces a
     * 23-hour local day, and dropping one day a year for that would be a silent data gap. A day
     * with fewer readings than this reports no soil value rather than a mean of whatever happened
     * to arrive (CLAUDE.md: partial results are reported as such).
     */
    const val MIN_HOURLY_READINGS_PER_DAY = 20
}
