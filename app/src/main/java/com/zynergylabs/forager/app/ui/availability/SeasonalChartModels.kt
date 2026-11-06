package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.FruitingPatternAssumptions
import com.zynergylabs.forager.app.domain.SoilMoistureLevel
import com.zynergylabs.forager.app.domain.model.DailyRain
import com.zynergylabs.forager.app.domain.model.FruitingLagHistogram
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatRainfall
import java.util.Locale
import com.zynergylabs.forager.app.ui.format.displayDate

// Data part C (dispatch -668, the owner's "Plain scales + real charts (Recommended)", RECORD -656):
// what the Seasonal tab's two bar charts and the soil moisture scale show, as plain values with no
// Compose in them, so the scales, labels and screen-reader text are tested headless and the
// composables in SeasonalBarChart.kt only draw them.

/** A label on a chart's x-axis, centred at [fraction] of the plot's width (kept inside the plot at either end). */
internal data class AxisLabel(val fraction: Float, val text: String)

/**
 * One bar chart, ready to draw.
 *
 * @param values one per slot, in the chart's units; [slotCount] slots of equal width span the x-axis.
 *   A null value is a slot with no data: no bar, and it is not drawn as zero.
 * @param yMax the value at the plot's top; every bar's height is its value over this.
 * @param band a stretch of the x-axis to shade behind the bars, as fractions of the plot's width.
 * @param valueLabels a figure to print above each bar, or null for none.
 * @param description the whole chart in words, for TalkBack (the canvas itself says nothing).
 */
internal data class BarChartModel(
    val values: List<Double?>,
    val slotCount: Int,
    val yMax: Double,
    val yTitle: String,
    val yTopLabel: String,
    val yBottomLabel: String,
    val xLabels: List<AxisLabel>,
    val xTitle: String?,
    val band: ClosedFloatingPointRange<Float>?,
    val valueLabels: List<String>?,
    val description: String,
)

/** A bar's height as a share of the plot, 0 to 1: its value over [yMax], and 0 for an empty chart. */
internal fun barHeightFraction(value: Double, yMax: Double): Float =
    if (yMax <= 0.0) 0f else (value / yMax).coerceIn(0.0, 1.0).toFloat()

/**
 * The 14 observed days of rain as one bar a day (the owner's "daily rain is kept and drawn as a
 * 14-day bar chart"). A day is placed by its date, not its position in the list, so a day the
 * response left out shows as a gap rather than closing up and making the span unequal. Null when
 * there is no day to draw.
 */
internal fun dailyRainChart(days: List<DailyRain>, unitSystem: UnitSystem): BarChartModel? {
    if (days.isEmpty()) return null
    val first = days.first().date
    val last = days.last().date
    val slotCount = (last.toEpochDay() - first.toEpochDay()).toInt() + 1
    val values = MutableList<Double?>(slotCount) { null }
    days.forEach { values[(it.date.toEpochDay() - first.toEpochDay()).toInt()] = it.mm }
    val wettest = days.maxBy { it.mm }
    val yMax = wettest.mm
    val description = buildString {
        append("Daily rain from ${displayDate(first)} to ${displayDate(last)}. ")
        if (yMax <= 0.0) {
            append("No rain on any day.")
        } else {
            append("Wettest day ${displayDate(wettest.date)}, ${formatRainfall(wettest.mm, unitSystem)}.")
        }
    }
    return BarChartModel(
        values = values,
        slotCount = slotCount,
        yMax = yMax,
        yTitle = "Rain",
        yTopLabel = formatRainfall(yMax, unitSystem),
        yBottomLabel = "0",
        xLabels = if (slotCount == 1) {
            listOf(AxisLabel(0.5f, displayDate(first)))
        } else {
            listOf(AxisLabel(0f, displayDate(first)), AxisLabel(1f, displayDate(last)))
        },
        xTitle = null,
        band = null,
        valueLabels = null,
        description = description,
    )
}

/**
 * The fruiting-lag histogram in equal spans (the owner's "the chart gets axes, labels and equal
 * spans"), with the rule of thumb's own days shaded behind the bars. The band is drawn from
 * [FruitingPatternAssumptions.FRUITING_LAG_DAYS] by reference, so it cannot drift from the range
 * being tested; it covers day 7 up to the end of day 21, which is part-way into the 21–27 span,
 * and that is drawn as it is rather than rounded to a span edge.
 */
internal fun fruitingLagChart(histogram: FruitingLagHistogram): BarChartModel {
    val axisEnd = histogram.axisEndDay.toFloat()
    val rule = FruitingPatternAssumptions.FRUITING_LAG_DAYS
    val counts = histogram.spans.map { it.count }
    val yMax = counts.maxOrNull() ?: 0
    val ticks = (0..histogram.spans.size).map { it * histogram.spanDays }
    return BarChartModel(
        values = counts.map { it.toDouble() },
        slotCount = histogram.spans.size,
        yMax = yMax.toDouble(),
        yTitle = "Sightings",
        yTopLabel = "$yMax",
        yBottomLabel = "0",
        xLabels = ticks.map { AxisLabel(if (axisEnd == 0f) 0f else it / axisEnd, "$it") },
        xTitle = "Days after a soaking rain",
        band = if (axisEnd == 0f) {
            null
        } else {
            (rule.first / axisEnd).coerceIn(0f, 1f)..((rule.last + 1) / axisEnd).coerceIn(0f, 1f)
        },
        valueLabels = counts.map { "$it" },
        description = "Sightings by days after a soaking rain, in ${histogram.spanDays}-day spans: " +
            histogram.spans.joinToString(", ") { "${it.firstDay}–${it.lastDay} days, ${it.count}" } +
            ". Shaded: ${rule.first}–${rule.last} days, the rule of thumb.",
    )
}

/** The key under the fruiting-lag chart, saying what the shading is. */
internal fun fruitingLagBandKey(): String =
    "Shaded: ${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
        "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} days, the rule of thumb"

/** Said under the chart when some lags fall past its last span, so none is silently left out. */
internal fun fruitingLagBeyondNote(histogram: FruitingLagHistogram): String? =
    if (histogram.beyondCount == 0) {
        null
    } else {
        "${histogram.beyondCount} observation(s) came more than ${histogram.axisEndDay - 1} days after a " +
            "soaking rain and are past the end of the chart."
    }

/**
 * The conditions table's "Last rainy day" value, from [daysSinceSignificantRain].
 *
 * That figure counts from the newest *observed* day, which is yesterday at the searched location
 * (`OpenMeteoWeatherProvider.summariseObservedConditions`): 0 means it rained yesterday. The card
 * used to print 0 as "Rain today.", which was a day early; this reads it as the day it was.
 */
internal fun lastRainyDayLabel(daysSinceSignificantRain: Int?): String = when (daysSinceSignificantRain) {
    null -> "None in ${FruitingPatternAssumptions.OBSERVED_HISTORY_DAYS} days"
    0 -> "Yesterday"
    else -> "${daysSinceSignificantRain + 1} days ago"
}

/** The word for a soil moisture level, as the owner named the scale: "Dry, Moist or Wet". */
internal fun soilMoistureWord(level: SoilMoistureLevel): String = when (level) {
    SoilMoistureLevel.DRY -> "Dry"
    SoilMoistureLevel.MOIST -> "Moist"
    SoilMoistureLevel.WET -> "Wet"
}

/** The figure under the scale word, as the card printed it before (two decimals, m³/m³). */
internal fun soilMoistureFigure(m3m3: Double): String = String.format(Locale.US, "%.2f m³/m³", m3m3)
