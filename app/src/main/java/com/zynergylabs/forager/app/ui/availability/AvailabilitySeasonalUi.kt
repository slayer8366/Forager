package com.zynergylabs.forager.app.ui.availability

// Data part C (dispatch -668), under the owner's "Piece by piece (Recommended)" (RECORD -655): the
// Seasonal tab's code, moved out of AvailabilityResultsUi.kt before this part changed it. Same package,
// so every caller and test tag resolves unchanged. The move itself changed no line of it; the later
// commits on branch data-c-seasonal then changed the chart and the conditions card.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.FruitingPatternAssumptions
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatRainfall
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.ui.adaptive.WindowWidthClass
import com.zynergylabs.forager.app.ui.adaptive.currentWindowWidthClass
import com.zynergylabs.forager.app.ui.adaptive.isLandscapeWindow
import com.zynergylabs.forager.app.ui.adaptive.isLargeScreenWindow
import com.zynergylabs.forager.app.ui.credits.OpenMeteoCredit
import com.zynergylabs.forager.app.ui.theme.Spacing


/**
 * The Seasonal tab: the [ConditionsCard] weather panel (current conditions plus today's forecast),
 * above a test of [FruitingPatternAssumptions.FRUITING_LAG_DAYS] — the 7–21 day rain-to-fruiting-lag
 * rule of thumb [TripWindowsCard] and [ForagingWeatherGuidanceSection] already state as unmeasured
 * field lore — against real historical iNaturalist sightings and real historical Open-Meteo
 * rainfall for the current search.
 *
 * The two sit in one destination per PANEL-CONTENTS-DISPATCH.md item 2: Seasonal and weather are
 * both pre-trip checks (Seasonal rare, weather per-trip), so consolidating them puts everything
 * checked before leaving in one place. The weather panel is listed first — it's the more frequently
 * consulted of the two — and does not gate on or wait for the fruiting-lag fetch below it: they are
 * fetched independently (see [AvailabilityViewModel.refresh] for the conditions/forecast fetch,
 * [AvailabilityViewModel.onSeasonalTabSelected] for the lazily-fetched fruiting-lag pattern below).
 *
 * **The fruiting-lag section does not feed [AvailabilityEntry.relativeLikelihood] or the ranked
 * List tab.** It answers one narrow question — does the data support this one named lag range —
 * and nothing here changes how species are ranked. See [FruitingLagDistribution]'s own doc comment.
 */
@Composable
internal fun SeasonalTab(uiState: AvailabilityUiState, modifier: Modifier = Modifier) {
    // At medium+ width (a phone in landscape, a tablet) this tab content would otherwise stretch to the
    // window's full remaining width — a rule-of-thumb paragraph at 900+dp is well past M3's
    // ~40-60-character comfortable reading width. Centering the column and capping it at
    // READABLE_CONTENT_MAX_WIDTH is that constraint; COMPACT keeps fillMaxWidth exactly as
    // before, since a phone-width screen never approaches that cap anyway.
    val windowWidthClass = currentWindowWidthClass()
    // Amendment 4 to dispatch 2026-10-18-10 (RECORD -1355, the owner: "Same as the Journal (960)"): beside the rail on a large
    // screen the tab is the 960 dp column the scaffold gives it, in place of this 640 dp readable cap, whose reasoning above the
    // owner's choice overrides there. Portrait on a tablet, and phones, keep 640.
    val seasonalMaxWidth = if (isLargeScreenWindow() && isLandscapeWindow()) TABLET_JOURNAL_CONTENT_MAX_WIDTH else READABLE_CONTENT_MAX_WIDTH
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (windowWidthClass == WindowWidthClass.COMPACT) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(max = seasonalMaxWidth).fillMaxWidth()
                    },
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .testTag(SEASONAL_CONTENT_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))
            if (uiState.conditions != null || uiState.conditionsErrorMessage != null ||
                uiState.isLoadingTodaysForecast || uiState.todaysForecast != null || uiState.todaysForecastErrorMessage != null
            ) {
                ConditionsCard(
                    unitSystem = uiState.unitSystem,
                    conditions = uiState.conditions,
                    conditionsErrorMessage = uiState.conditionsErrorMessage,
                    isLoadingTodaysForecast = uiState.isLoadingTodaysForecast,
                    todaysForecast = uiState.todaysForecast,
                    todaysForecastErrorMessage = uiState.todaysForecastErrorMessage,
                )
            }
            when {
                !uiState.hasSearched -> Text(
                    "Choose a region in search options to test the rain-to-fruiting-lag rule of thumb " +
                        "against real data.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                uiState.isLoadingSeasonalPattern -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.seasonalPatternErrorMessage != null -> Text(
                    uiState.seasonalPatternErrorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.seasonalPattern != null -> SeasonalPatternContent(uiState.seasonalPattern)
            }
            // Open-Meteo's licence: a link next to any place their data are shown (dispatch 2026-10-13-05). Since dispatch
            // 2026-10-18-01 (the owner: "open meteo is cited twice in the seasonal tab. Once at the bottom is sufficient") the
            // tab carries one, the last thing in this column, in place of one in the Current Conditions card and one under the
            // fruiting-lag counts. Only while a figure of theirs is on the tab: a loading or failed fetch shows none.
            if (seasonalShowsOpenMeteoFigures(uiState)) {
                OpenMeteoCredit()
            }
        }
    }
}

/**
 * Whether the Seasonal tab is drawing any figure of Open-Meteo's, and so carries their credit at its foot: recent rain, today's
 * forecast (Current Conditions), or the fruiting-lag pattern, whose lags are counted from their historical rainfall (dispatch
 * 2026-10-13-05, the planner's answer Q3). The pattern is drawn only once a search has run and nothing is loading or failed, as
 * [SeasonalTab]'s `when` decides.
 */
private fun seasonalShowsOpenMeteoFigures(uiState: AvailabilityUiState): Boolean {
    val patternDrawn = uiState.hasSearched && !uiState.isLoadingSeasonalPattern &&
        uiState.seasonalPatternErrorMessage == null && uiState.seasonalPattern != null
    return uiState.conditions != null || uiState.todaysForecast != null || patternDrawn
}

/**
 * Same readable-width reasoning as [SeasonalTab].
 * `internal` since landscape B3 (R9): the compact scaffold caps every tab beside the landscape rail with it.
 */
internal val READABLE_CONTENT_MAX_WIDTH = 640.dp

/**
 * Dispatch 2026-10-18-10 (Forager RECORD -1340, the owner's "A: wider column (Recommended)": the same columns in a wider centred
 * column of about 960 dp): the Journal's content cap beside the landscape rail on a large screen (smallest width 600 dp or more),
 * in place of [READABLE_CONTENT_MAX_WIDTH]. Rejected by the owner: more columns across the full width, and stretched cards.
 */
internal val TABLET_JOURNAL_CONTENT_MAX_WIDTH = 960.dp

/** Lets [AvailabilityScreenAdaptiveLayoutTest] measure the readable-width column directly. */
const val SEASONAL_CONTENT_TAG = "seasonal-content"

@Composable
private fun SeasonalPatternContent(distribution: FruitingLagDistribution) {
    Column(modifier = Modifier.testTag(FRUITING_LAG_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Does rain predict fruiting?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Testing whether ${distribution.filter.label} sightings actually cluster in the " +
                "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
                "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} days after a soaking rain — the " +
                "widely-repeated foraging rule of thumb — against real historical observations and " +
                "real historical rainfall.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SeasonalSampleSizeSummary(distribution)
        FruitingLagChart(distribution, modifier = Modifier.fillMaxWidth())
        FruitingLagBucketCounts(distribution.buckets)

        HorizontalDivider()
        // The observer-effort caveat: not polish, per this feature's own honesty requirement —
        // raw counts conflate "more people were out looking" with "the species was more present."
        Text(
            "Raw iNaturalist counts reflect how many people were out looking that day, not only " +
                "whether ${distribution.filter.label} was actually there — more observers means more " +
                "sightings regardless of the rain.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
    }
}

/**
 * The sample size, on screen and prominent rather than in a tooltip — this feature's whole
 * honesty mechanism is that nobody can read a bar off [FruitingLagChart] without also seeing what
 * it's an estimate from.
 */
@Composable
private fun SeasonalSampleSizeSummary(distribution: FruitingLagDistribution) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Estimate from ${distribution.sampleSize} observations with a known date, not a guarantee.",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Based on ${distribution.sightingsConsidered} of ${distribution.totalResultsOnServer} " +
                "total observations iNaturalist reports for this search.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (distribution.observationsExcludedForMissingDate > 0) {
            Text(
                "${distribution.observationsExcludedForMissingDate} observation(s) have no recorded " +
                    "date and are excluded from this estimate.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (distribution.observationsWithNoPrecedingEvent > 0) {
            Text(
                "${distribution.observationsWithNoPrecedingEvent} observation(s) had no qualifying " +
                    "rain event in the fetched history before them.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The fruiting-lag chart (data part C, dispatch -668, the owner's "the chart gets axes, labels and
 * equal spans"): sightings per week-long span of days after the nearest soaking rain, with a count
 * axis, a days axis, each bar's count above it, and the rule of thumb's own days shaded behind the
 * bars. What it shows is [fruitingLagChart], tested headless; the drawing is [SeasonalBarChart].
 *
 * Before this the chart drew [FruitingLagDistribution.buckets] directly, whose spans are 7, 15 and 14
 * days wide plus an open "36+", so the 15-day rule-of-thumb bucket looked taller for being wider.
 * Those buckets stay, as the counts listed under the chart ([FruitingLagBucketCounts]), because the
 * second of them is exactly the range being tested; the bars now come from
 * [FruitingLagDistribution.histogram], whose spans are equal.
 *
 * Still hand-rolled on a Canvas, with no chart library, for the reason the earlier one gave.
 */
@Composable
private fun FruitingLagChart(distribution: FruitingLagDistribution, modifier: Modifier = Modifier) {
    val histogram = distribution.histogram
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SeasonalBarChart(fruitingLagChart(histogram))
        Text(fruitingLagBandKey(), style = MaterialTheme.typography.bodySmall)
        fruitingLagBeyondNote(histogram)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

/**
 * The count in each of [FruitingLagDistribution.buckets], as real on-screen text. (Since data part C
 * the bars above are equal week-long spans and print their own counts, and these rows keep the
 * rule-of-thumb bucket's own count.) Originally: the exact count behind every bar of [FruitingLagChart], as real on-screen text — the canvas
 * above is unmeasurable in the Robolectric layout tests this project relies on (no rendering
 * happens under Robolectric; see [AvailabilityScreenLayoutTest]'s own doc comment for the same
 * limitation on the map), so the numbers this feature's honesty rests on live here, not only in
 * pixels.
 */
@Composable
private fun FruitingLagBucketCounts(buckets: List<FruitingLagBucket>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        buckets.forEach { bucket ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (bucket.isFruitingLagRule) "${bucket.label} (the rule of thumb)" else bucket.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (bucket.isFruitingLagRule) FontWeight.Bold else FontWeight.Normal,
                )
                Text("${bucket.count}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Recent rainfall plus today's own forecast, shown as a standalone fact at the top of the Seasonal
 * tab — never described as having factored into the ranked List tab. See
 * [com.zynergylabs.forager.app.domain.GetConditionsUseCase] and [com.zynergylabs.forager.app.domain.GetTodaysForecastUseCase]'s
 * own doc comments for why the two halves are separate fetches from separate provider methods, and
 * [SeasonalTab]'s own doc comment for why this card lives here rather than next to the ranking.
 *
 * [conditionsErrorMessage]/[todaysForecastErrorMessage] are the non-belief-changing empty state for
 * a failed fetch — the user wanted weather data, not a report on the network, so they render with
 * the same neutral (no `color` argument) treatment [WaypointsSection]'s empty state and
 * [MapMessage]'s default use — never `colorScheme.error` — per
 * docs/error-presentation-spec.md's per-field table. Exactly one of
 * [conditions]/[conditionsErrorMessage] is non-null at any call site, and independently the same
 * for [todaysForecast]/[todaysForecastErrorMessage]/[isLoadingTodaysForecast] — the two halves load
 * from separate fetches and can be in different states at once (see [SeasonalTab]).
 */
@Composable
private fun ConditionsCard(
    unitSystem: UnitSystem,
    conditions: ConditionsSummary? = null,
    conditionsErrorMessage: String? = null,
    isLoadingTodaysForecast: Boolean = false,
    todaysForecast: DailyWeather? = null,
    todaysForecastErrorMessage: String? = null,
) {
    // Data part C (dispatch -668, the owner's "conditions are a small table"): the three facts are
    // labelled rows rather than sentences, and the 14 observed days are drawn as a bar chart under
    // them. The "Today's Forecast" heading went: its one figure is now the table's last row, labelled.
    Card(modifier = Modifier.fillMaxWidth().testTag(CONDITIONS_CARD_TAG)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Current Conditions", style = MaterialTheme.typography.titleSmall)
            val rows = buildList<TableRow> {
                if (conditions != null) {
                    add(TableRow("Rain, last ${FruitingPatternAssumptions.OBSERVED_HISTORY_DAYS} days") { TableValue(formatRainfall(conditions.totalPrecipitationMm, unitSystem)) })
                    add(TableRow("Last rainy day") { TableValue(lastRainyDayLabel(conditions.daysSinceSignificantRain)) })
                }
                add(
                    TableRow("Rain forecast today") {
                        when {
                            isLoadingTodaysForecast -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )

                            todaysForecastErrorMessage != null -> TableValue(todaysForecastErrorMessage)
                            todaysForecast != null -> TableValue(formatRainfall(todaysForecast.precipitationMm, unitSystem))
                            else -> TableValue("No forecast available for today.")
                        }
                    },
                )
                // Amendment 1 to -668 (RECORD -669), the owner: "Yes, add it (Recommended)". Today's
                // shallow soil moisture, already fetched with today's forecast, on the same plain scale
                // as the trip windows. No row when the location's model served none.
                todaysForecast?.shallowSoilMoistureM3M3?.let { moisture ->
                    add(TableRow("Soil moisture") { SoilMoistureScaleValue(moisture) })
                }
            }
            if (conditions == null && conditionsErrorMessage != null) {
                Text(conditionsErrorMessage, style = MaterialTheme.typography.bodyMedium)
            }
            LabelledTable(rows)
            conditions?.let { dailyRainChart(it.dailyRain, unitSystem) }?.let { chart ->
                HorizontalDivider()
                Text(
                    "Daily rain, last ${FruitingPatternAssumptions.OBSERVED_HISTORY_DAYS} days",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SeasonalBarChart(chart)
            }
        }
    }
}

/** The Seasonal tab's Current Conditions card, and its fruiting-lag section, for tests. */
internal const val CONDITIONS_CARD_TAG = "conditions-card"
internal const val FRUITING_LAG_SECTION_TAG = "fruiting-lag-section"
