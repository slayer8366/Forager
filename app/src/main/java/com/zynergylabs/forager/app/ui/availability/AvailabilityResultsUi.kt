package com.zynergylabs.forager.app.ui.availability

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
// (ListTab, called by the compact scaffold; SeasonalTab, two scaffold
// call sites; TripWindowsCard, called by TripPlannerSection). No symbol left behind is reached
// from here.
//
// Data part C (dispatch -668, RECORD -655's "Piece by piece"): the Seasonal tab (SeasonalTab through
// ConditionsCard) now lives in AvailabilitySeasonalUi.kt and the trip windows card (TRIP_WINDOW_DATE_FORMAT
// through ForagingWeatherGuidanceSection) in AvailabilityTripWindowsUi.kt; this file keeps the List tab.

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.ListRowMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.rememberListRows
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle


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
        // The tag goes first, so its bounds are the whole Column (the tab's column), not the content inside the 16 dp padding.
        modifier = modifier
            .testTag(LIST_CONTENT_TAG)
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
 * [ListTab]'s own Column, so a test can measure List's content (dispatch 2026-10-19-06, RECORD -1385): on a tablet beside the
 * rail it spans the 960 dp column (TabletJournalLayoutTest).
 */
const val LIST_CONTENT_TAG = "list-content"

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
    // The configuration's language, which Compose observes, not Locale.getDefault(), which it does not (lint NonObservableLocale; dispatch 2026-10-17-03).
    val locale = LocalConfiguration.current.locales[0]
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
                        Month.of(forecast.month).getDisplayName(TextStyle.FULL, locale) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(Spacing.sm))
                // Motion Part 3, item 2 (RECORD -651, Lists: "Slide and close up"; motion/ListMotion.kt; scout L4): when results
                // update, a species that drops out fades and shrinks, a new one fades and grows in, and one that changes place
                // glides there. A new search starts from the spinner, so its first results appear at once, as before.
                val rows = rememberListRows(forecast.entries, key = { it.species.taxonId })
                val glide = MotionTokens.listRowSpec<IntOffset>()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(rows, key = { it.key }) { row ->
                        ListRowMotion(row, Modifier.animateItem(fadeInSpec = null, placementSpec = glide, fadeOutSpec = null)) { entry ->
                            SpeciesRow(entry, onViewOnMap = onViewOnMap)
                        }
                    }
                }
            }
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
    // Motion Part 1 (dispatch 2026-09-28-652, item 3, scout L1; the owner, RECORD -651: "Yes, round them all"): the card's tap
    // stays where it was, on the Card's modifier and outside its clip (so it takes touches across the whole box, corners
    // included, as before), but its press is drawn by the Column inside, which fills the card and is clipped to its rounded
    // shape, instead of as a square over the corners.
    val tapInteraction = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("species-row")
            .clickable(interactionSource = tapInteraction, indication = null) { onViewOnMap(entry.species.taxonId) },
    ) {
        Column(
            modifier = Modifier
                .indication(tapInteraction, LocalIndication.current)
                .padding(Spacing.md),
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
                        .clickableWithShapedPress { onViewOnMap(entry.species.taxonId) }
                        .testTag("species-row-view-on-map"),
                )
            }
        }
    }
}
