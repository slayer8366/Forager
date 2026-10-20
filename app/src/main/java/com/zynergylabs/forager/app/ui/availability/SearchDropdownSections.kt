package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.mapChromeContentColor
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle


/**
 * The offline cache's recent searches, most recently used first, each re-runnable in one tap.
 *
 * The list is short by construction — the cache keeps five (see
 * [RoomSearchCacheRepository][com.zynergylabs.forager.app.data.repository.RoomSearchCacheRepository]) — so
 * these are plain [Column] children inside the drawer's existing scroll rather than a nested
 * [LazyColumn], which inside a scrolling parent would need a height of its own and would scroll
 * independently of everything around it.
 *
 * Tapping an entry does **not** mean "show me the saved copy": it re-runs the search through the
 * ordinary flow, which tries live first — see
 * [AvailabilityViewModel.onRecentSearchSelected][com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel.onRecentSearchSelected].
 *
 * An empty list says so rather than rendering nothing, the same way [PlannedTripsList] does: a
 * section that expands to blank space is indistinguishable from one that failed to load.
 */
@Composable
internal fun RecentSearchesSection(
    recentSearches: List<CachedSearchSummary>,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (recentSearches.isEmpty()) {
            Text(
                "No searches saved yet. Each search you run is saved here, and the last five can " +
                    "be reopened without a connection.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Read once for the whole list rather than per row, so every "cached ..." label in one
            // rendering is measured from the same instant.
            val now = currentTime.nowEpochMillis()
            recentSearches.forEach { summary ->
                RecentSearchRow(
                    summary = summary,
                    nowEpochMillis = now,
                    distanceUnit = distanceUnit,
                    onClick = { onRecentSearchSelected(summary) },
                )
            }
        }
    }
}

/** One recent search: what was searched for, where and when, and how old the saved copy is. */
@Composable
private fun RecentSearchRow(
    summary: CachedSearchSummary,
    nowEpochMillis: Long,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The whole card is the target, not a button inside it: the row is one action, and
                // a small control in a card the user has already aimed at is a smaller target for
                // no reason — the same widening [DrawerHeader] does for its close affordance.
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // The configuration's language, which Compose observes, not Locale.getDefault(), which it does not (lint NonObservableLocale; dispatch 2026-10-17-03).
            val month = Month.of(summary.month).getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
            Text("${summary.filter.label} · $month", style = MaterialTheme.typography.titleSmall)
            Text(
                "${"%.4f".format(summary.region.lat)}, ${"%.4f".format(summary.region.lng)} · " +
                    formatDistanceKm(summary.region.radiusKm, distanceUnit),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "cached ${relativeTimeLabel(summary.cachedAtEpochMillis, nowEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MonthSelector(selectedMonth: Int, onMonthSelected: (Int) -> Unit, overMap: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    // The configuration's language, which Compose observes, not Locale.getDefault(), which it does not (lint NonObservableLocale; dispatch 2026-10-17-03).
    val locale = LocalConfiguration.current.locales[0]
    val monthName = Month.of(selectedMonth).getDisplayName(TextStyle.FULL, locale)

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = monthName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        // As the species suggestions above: the default role, passed explicitly, and its content colour.
        // As the species suggestions (owner, "1 A"): 0.8 on its own over the Maps tab, stacking.
        val monthMenuColor = mapChromeFill(navigationBarContainerColor(), overMap)
        val monthMenuContentColor = contentColorFor(navigationBarContainerColor())
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = monthMenuColor,
            modifier = Modifier
                .testTag(MONTH_MENU_TAG)
                .mapChromeContainerColor(monthMenuColor),
        ) {
            CompositionLocalProvider(LocalContentColor provides monthMenuContentColor) {
                // The content colour as read inside the menu, on its first row, for tests.
                val readInside = Modifier.mapChromeContentColor(LocalContentColor.current)
                (1..12).forEach { month ->
                    DropdownMenuItem(
                        text = { Text(Month.of(month).getDisplayName(TextStyle.FULL, locale)) },
                        onClick = {
                            onMonthSelected(month)
                            expanded = false
                        },
                        modifier = if (month == 1) readInside else Modifier,
                    )
                }
            }
        }
    }
}

/** The Month menu ([MonthSelector]), for tests. */
internal const val MONTH_MENU_TAG = "month-menu"
