package com.forager.app.ui.availability

// Split-AvailabilityScreen Stage D: list, seasonal and results, moved verbatim out of
// AvailabilityScreen.kt. Two blocks that were not adjacent there — lines 2928-3276 (ListTab,
// OfflineResultsBanner, ResultsSection, SeasonalTab, READABLE_CONTENT_MAX_WIDTH,
// SEASONAL_CONTENT_TAG, SeasonalPatternContent, SeasonalSampleSizeSummary, FruitingLagChart,
// FruitingLagBucketCounts) and the file's tail, lines 5824-6074 (ConditionsCard,
// TRIP_WINDOW_DATE_FORMAT, TripWindowsCard, TripWindowReportContent, TripWindowRow,
// ForagingWeatherGuidanceSection, SpeciesRow). The tail block belongs with the first because
// ListTab and ResultsSection compose those cards; that is not obvious from position alone, so
// it is said here. Same package as Stages A and C, for the same reason: the tests reach this set
// only by its tags (SEASONAL_CONTENT_TAG, "species-row") and AvailabilityPureFunctions.kt's use
// of TRIP_WINDOW_DATE_FORMAT resolves unchanged. Pure move: no signature, name or body changed.
// Three composables went private -> internal because their callers stay in AvailabilityScreen.kt
// (ListTab, called by the compact scaffold and CombinedResultsPane; SeasonalTab, two scaffold
// call sites; TripWindowsCard, called by TripPlannerSection). No symbol left behind is reached
// from here. CombinedResultsPane stays deliberately: it composes MapTab, which is seam F, held.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.ForagingWeatherGuidance
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.MgrsConverter
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.FruitingLagBucket
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.ui.adaptive.WindowWidthClass
import com.forager.app.ui.adaptive.currentWindowWidthClass
import com.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale


/**
 * The ranked list.
 *
 * The Current Conditions/forecast weather panel used to sit here, above the list; it now lives in
 * the Seasonal tab instead — see [ConditionsCard] and [SeasonalTab]'s own doc comment for why
 * (PANEL-CONTENTS-DISPATCH.md item 2: Seasonal and weather are both pre-trip checks, so they were
 * consolidated into one destination).
 *
 * Trip windows are shown in the drawer's Trip Planner section — see [TripPlannerSection] — because
 * "what's likely nearby this month" and "when in the next few days is worth going" are different
 * questions with different lifetimes (the ranking depends on the browsed month, trip windows only
 * on the next several days), and fusing them into one scrolling column was one more step to reach
 * whichever one wasn't currently showing.
 */
@Composable
internal fun ListTab(
    uiState: AvailabilityUiState,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    onViewOnMap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        // Above the ranking: it changes what everything below it means, so it cannot be something
        // the user meets after reading the list.
        if (uiState.isShowingCachedResults) {
            OfflineResultsBanner(
                cachedAtEpochMillis = uiState.cachedResultsAsOfEpochMillis,
                nowEpochMillis = currentTime.nowEpochMillis(),
            )
        }
        // Weighted so the ranked list scrolls within a bounded height.
        ResultsSection(uiState = uiState, distanceUnit = distanceUnit, onViewOnMap = onViewOnMap, modifier = Modifier.weight(1f))
    }
}

/**
 * Says out loud that the ranking below came out of the offline cache rather than off the network,
 * and how old it is.
 *
 * **Not optional polish.** CLAUDE.md requires a partial or fallback result to be reported as such
 * and never presented as a success; a cached ranking rendered identically to a live one is exactly
 * that failure, and the user would have no way to tell that iNaturalist was never reached.
 *
 * Tertiary rather than the error palette: nothing failed in a way that cost the user their answer
 * — the answer is right there, it is simply older than it looks. Reusing the error color would
 * make a real failure read as no more urgent than this.
 *
 * [cachedAtEpochMillis] is non-null in every state the ViewModel produces (both fields are written
 * from one `Cached` result), but a null is rendered as an explicit "when isn't known" rather than
 * being hidden or filled in with a guess — a banner that invented an age would undo the honesty it
 * exists for.
 */
@Composable
private fun OfflineResultsBanner(cachedAtEpochMillis: Long?, nowEpochMillis: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                if (cachedAtEpochMillis == null) {
                    "Offline — showing saved results; when they were saved isn't known."
                } else {
                    "Offline — showing results saved ${relativeTimeLabel(cachedAtEpochMillis, nowEpochMillis)}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "iNaturalist couldn't be reached, so this is the last ranking saved for this " +
                    "region, month and category.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ResultsSection(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onViewOnMap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            !uiState.hasSearched -> Text(
                "Choose a region to see what's historically been found nearby this month.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null && uiState.forecast.entries.isEmpty() -> Text(
                "No verifiable observations of ${uiState.forecast.filter.label} found for this region and month. " +
                    "Try a wider radius or a different category.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null -> {
                val forecast = uiState.forecast
                Text(
                    "Based on ${forecast.totalObservationsConsidered} historical iNaturalist observations " +
                        "of ${forecast.filter.label} within ${formatDistanceKm(forecast.region.radiusKm, distanceUnit)} for " +
                        Month.of(forecast.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(forecast.entries, key = { it.species.taxonId }) { entry ->
                        SpeciesRow(entry, onViewOnMap = onViewOnMap)
                    }
                }
            }
        }
    }
}

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
    // Unlike List/Map, Seasonal isn't paired into CombinedResultsPane (see AvailabilityScreen's
    // doc comment on why), so at medium+ width it's the one tab content still stretching to the
    // window's full remaining width — a rule-of-thumb paragraph at 900+dp is well past M3's
    // ~40-60-character comfortable reading width. Centering the column and capping it at
    // READABLE_CONTENT_MAX_WIDTH is that constraint; COMPACT keeps fillMaxWidth exactly as
    // before, since a phone-width screen never approaches that cap anyway.
    val windowWidthClass = currentWindowWidthClass()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (windowWidthClass == WindowWidthClass.COMPACT) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(max = READABLE_CONTENT_MAX_WIDTH).fillMaxWidth()
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
        }
    }
}

/** Same readable-width reasoning as [SeasonalTab]; see [CombinedResultsPane] for the drawer/pane analogs. */
private val READABLE_CONTENT_MAX_WIDTH = 640.dp

/** Lets [AvailabilityScreenAdaptiveLayoutTest] measure the readable-width column directly. */
const val SEASONAL_CONTENT_TAG = "seasonal-content"

@Composable
private fun SeasonalPatternContent(distribution: FruitingLagDistribution) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
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
        FruitingLagChart(distribution.buckets, modifier = Modifier.fillMaxWidth())
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
 * A hand-rolled Compose `Canvas` bar chart — no charting dependency, consistent with
 * [com.forager.app.domain.GeoDistance]/[com.forager.app.domain.MgrsConverter] being hand-built
 * rather than pulled from a library for a single use.
 *
 * The bucket whose [FruitingLagBucket.isFruitingLagRule] is true — the range this whole feature
 * exists to test — is drawn in the theme's primary color; every other bucket, including "no
 * preceding event", shares a second, unhighlighted color. That is the entire visual claim this
 * chart makes: whether the data's tallest bar (or not) lines up with the rule of thumb. The exact
 * counts behind each bar are [FruitingLagBucketCounts], not this canvas — pixel heights are for
 * the shape of the distribution, not for reading an exact number off a screen.
 */
@Composable
private fun FruitingLagChart(buckets: List<FruitingLagBucket>, modifier: Modifier = Modifier) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.secondary
    val maxCount = buckets.maxOfOrNull { it.count } ?: 0

    Canvas(modifier = modifier.height(160.dp)) {
        if (buckets.isEmpty()) return@Canvas
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (buckets.size - 1)) / buckets.size).coerceAtLeast(0f)
        buckets.forEachIndexed { index, bucket ->
            val heightFraction = if (maxCount == 0) 0f else bucket.count.toFloat() / maxCount
            val barHeight = size.height * heightFraction
            drawRect(
                color = if (bucket.isFruitingLagRule) highlightColor else barColor,
                topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
            )
        }
    }
}

/**
 * The exact count behind every bar of [FruitingLagChart], as real on-screen text — the canvas
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
 * [com.forager.app.domain.GetConditionsUseCase] and [com.forager.app.domain.GetTodaysForecastUseCase]'s
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
    conditions: ConditionsSummary? = null,
    conditionsErrorMessage: String? = null,
    isLoadingTodaysForecast: Boolean = false,
    todaysForecast: DailyWeather? = null,
    todaysForecastErrorMessage: String? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Current Conditions", style = MaterialTheme.typography.titleSmall)
            if (conditions != null) {
                val totalMm = conditions.totalPrecipitationMm
                Text(
                    "${"%.1f".format(totalMm)}mm of rain in the last 14 days",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val daysSince = conditions.daysSinceSignificantRain
                Text(
                    when {
                        daysSince == null -> "No significant rain in the last 14 days."
                        daysSince == 0 -> "Rain today."
                        daysSince == 1 -> "1 day since last rain."
                        else -> "$daysSince days since last rain."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (conditionsErrorMessage != null) {
                Text(conditionsErrorMessage, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text("Today's Forecast", style = MaterialTheme.typography.titleSmall)
            when {
                isLoadingTodaysForecast -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                todaysForecastErrorMessage != null -> Text(
                    todaysForecastErrorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )

                todaysForecast != null -> Text(
                    "${"%.1f".format(todaysForecast.precipitationMm)}mm of rain forecast today.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> Text("No forecast available for today.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

internal val TRIP_WINDOW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/**
 * Upcoming days that sit inside the stated post-rain lag range, next to the group's general
 * weather pattern.
 *
 * Two owned domain objects meet here and stay visually distinct: [TripWindowReport] is
 * measurements and date arithmetic only (see its own doc comment for why it must never grow a
 * score), and [ForagingWeatherGuidance] is the separately-stated rule of thumb that makes those
 * measurements interesting. The card shows both but never blends them into one sentence.
 *
 * Unlike [ConditionsCard], not gated to the browsed month: the days ahead of today are relevant
 * to planning a trip this week regardless of which month's species ranking is on screen.
 */
@Composable
internal fun TripWindowsCard(uiState: AvailabilityUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Trip Windows", style = MaterialTheme.typography.titleSmall)

            when {
                uiState.isLoadingTripWindows -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                uiState.tripWindowsErrorMessage != null -> Text(
                    uiState.tripWindowsErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.tripWindowReport != null -> TripWindowReportContent(uiState.tripWindowReport)
            }

            HorizontalDivider()
            ForagingWeatherGuidanceSection(uiState.foragingSelection)
        }
    }
}

@Composable
private fun TripWindowReportContent(report: TripWindowReport) {
    if (report.windows.isEmpty()) {
        Text(noTripWindowMessage(report), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        report.windows.forEach { window -> TripWindowRow(window) }
    }
}

@Composable
private fun TripWindowRow(window: TripWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${TRIP_WINDOW_DATE_FORMAT.format(window.startDate)} – ${TRIP_WINDOW_DATE_FORMAT.format(window.endDate)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val mostRecentRain = window.precedingRainEvents.first()
        Text(
            "${window.daysAfterMostRecentRainAtStart}–${window.daysAfterMostRecentRainAtEnd} days after " +
                "${"%.0f".format(mostRecentRain.totalMm)}mm of rain ending " +
                TRIP_WINDOW_DATE_FORMAT.format(mostRecentRain.endDate) +
                if (mostRecentRain.isForecast) " (forecast)" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (window.precipitationDuringWindowMm > 0.0) {
            Text(
                "${"%.1f".format(window.precipitationDuringWindowMm)}mm more rain forecast during the window",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanShallowSoilMoistureM3M3?.let { moisture ->
            Text(
                "Shallow soil moisture: ${"%.2f".format(moisture)} m³/m³",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanSoilTemperatureC?.let { temp ->
            Text("Soil temperature: ${"%.1f".format(temp)}°C", style = MaterialTheme.typography.bodySmall)
        }
        window.evapotranspirationSinceRainMm?.let { et0 ->
            Text(
                "${"%.1f".format(et0)}mm evapotranspiration since the rain",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The general weather pattern for the current selection, stated as a rule of thumb next to the
 * measurements above it — never combined with them into a score. See
 * [ForagingWeatherGuidance]'s doc comment for the rules this enforces.
 */
@Composable
private fun ForagingWeatherGuidanceSection(selection: ForagingSelection) {
    val guidance = ForagingWeatherGuidance.forSelection(selection)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // labelMedium + a muted color, not titleSmall/labelLarge: Material3 sizes titleSmall and
        // labelLarge identically (14sp/500), so this heading and the card's own "Trip Windows"
        // title above it were reading as the same weight despite one being nested inside the
        // other. This is deliberately a step down from the card title, not a second one beside it.
        Text(
            guidance.heading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        guidance.paragraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodySmall)
        }
        guidance.speciesDataCaveat?.let { caveat ->
            Text(caveat, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
    }
}

/**
 * Tapping the row (or its own explicit "View on Map" text) hands [entry]'s own
 * [SpeciesObservationCount.taxonId] up to [AvailabilityScreen]'s `onViewSpeciesOnMap`, which both
 * switches to whichever map surface the current window class shows and sets the taxon filter
 * [MapTab]/[CompactMapTab] read to limit their sightings to this one species — see that filter's
 * own doc comment on [AvailabilityScreen] for why it lives there rather than in either tab. The
 * text is a second, explicit affordance on the same line as the observation count (not the row's
 * only way to trigger it) so the action reads as discoverable rather than a hidden tap-anywhere
 * gesture.
 *
 * An earlier revision also offered "View on iNaturalist" here, opening the species' taxon page —
 * removed at the project owner's own request ("the view on iNaturalist idea was a bad one"), not
 * merely hidden: [launchINaturalistTaxon]/[inaturalistTaxonIntent] were deleted outright rather
 * than left unreferenced, since nothing else in this file used them (the map's own observation
 * bubble still uses [launchINaturalistObservation], a distinct per-observation link this removal
 * doesn't touch).
 */
@Composable
private fun SpeciesRow(entry: AvailabilityEntry, onViewOnMap: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("species-row")
            .clickable { onViewOnMap(entry.species.taxonId) },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                entry.species.commonName ?: entry.species.scientificName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                entry.species.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
            LinearProgressIndicator(
                progress = { entry.relativeLikelihood },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${entry.species.observationCount} observations",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "View on Map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onViewOnMap(entry.species.taxonId) }
                        .testTag("species-row-view-on-map"),
                )
            }
        }
    }
}
