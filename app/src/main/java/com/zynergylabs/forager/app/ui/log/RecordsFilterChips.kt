package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * How many records each Records filter chip counts. [finds] is `null` when the caller has no count
 * to give (see `RecordsTab`'s `findsCount`); [all] is then `null` too, since a total missing one type
 * would be a wrong number shown as a right one.
 */
internal data class RecordsFilterCounts(
    val finds: Int?,
    val tracks: Int,
    val waypoints: Int,
    val offlineMaps: Int,
) {
    val all: Int? get() = finds?.let { it + tracks + waypoints + offlineMaps }

    fun of(filter: RecordsSubTab): Int? = when (filter) {
        RecordsSubTab.ALL -> all
        RecordsSubTab.FINDS -> finds
        RecordsSubTab.RECORDED_TRACKS -> tracks
        RecordsSubTab.WAYPOINTS -> waypoints
        RecordsSubTab.OFFLINE_MAPS -> offlineMaps
    }
}

/**
 * Records' filter chips — journal redesign J1, S3 (plan J4): All · Finds · Tracks · Waypoints ·
 * Offline maps, each with an icon and a count, in one row that scrolls sideways when it overflows.
 * Single choice: exactly one chip is selected, and tapping the selected one keeps it selected (a
 * filter row with nothing selected has no meaning here; All is the "no filter" choice).
 *
 * A single-type chip takes its record type's colours from [RecordTypeStyle] (plan J6): the type's
 * container behind a selected chip, its accent on the icon (always) and on the selected label. All
 * has no record type and keeps [FilterChipDefaults]' own colours.
 */
@Composable
internal fun RecordsFilterChipRow(
    selected: RecordsSubTab,
    counts: RecordsFilterCounts,
    onSelect: (RecordsSubTab) -> Unit,
    modifier: Modifier = Modifier,
    /** The row's spacing and icon size: [RecordsChipRowMetrics.Default], or the short window's (J5, L5a). */
    metrics: RecordsChipRowMetrics = RecordsChipRowMetrics.Default,
    /**
     * Dispatch 2026-10-17-09, RECORD -1243: in a landscape window the row sits on the Journal's switch line, centred in the space
     * between the switch and the search icon, so it takes its chips' width (up to that space, then scrolls) rather than the
     * whole width; and the selected chip is scrolled into view when the row is shown ("scrollable with offline maps showing and
     * highlighted"). Both off by default: portrait and the wide tree are as before.
     */
    onSwitchLine: Boolean = false,
) {
    Row(
        modifier = modifier
            .then(if (onSwitchLine) Modifier else Modifier.fillMaxWidth())
            .testTag(RECORDS_FILTER_CHIP_ROW_TAG)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = metrics.rowPadding, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(metrics.chipGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecordsSubTab.entries.forEach { filter ->
            RecordsFilterChip(
                filter = filter,
                count = counts.of(filter),
                selected = filter == selected,
                onClick = { onSelect(filter) },
                iconSize = metrics.iconSize,
                bringIntoViewWhenSelected = onSwitchLine,
            )
        }
    }
}

@Composable
private fun RecordsFilterChip(filter: RecordsSubTab, count: Int?, selected: Boolean, onClick: () -> Unit, iconSize: Dp?, bringIntoViewWhenSelected: Boolean = false) {
    // RECORD -1243: on the switch line, the selected chip is brought into the row's view when it is shown or chosen.
    val bringIntoView = remember { BringIntoViewRequester() }
    if (bringIntoViewWhenSelected) {
        LaunchedEffect(selected) { if (selected) bringIntoView.bringIntoView() }
    }
    val type = filter.recordType()
    val colors = if (type != null) {
        val typeColors = RecordTypeStyle.colors(type)
        FilterChipDefaults.filterChipColors(
            iconColor = typeColors.accent,
            selectedContainerColor = typeColors.container,
            selectedLabelColor = typeColors.accent,
            selectedLeadingIconColor = typeColors.accent,
        )
    } else {
        FilterChipDefaults.filterChipColors()
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(filter.chipLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (count != null) Text(count.toString(), style = MaterialTheme.typography.labelMedium)
            }
        },
        leadingIcon = { Icon(filter.chipIcon(), contentDescription = null, modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier) },
        colors = colors,
        modifier = Modifier
            .then(if (bringIntoViewWhenSelected) Modifier.bringIntoViewRequester(bringIntoView) else Modifier)
            .testTag(recordsFilterChipTestTag(filter)),
    )
}

/**
 * The chip row's spacing and leading-icon size. [Default] is J1's row, unchanged in portrait and in
 * the wide tree. [ShortWindow] is journal redesign J5's L5a (continuation 2026-09-27-27; the owner's
 * "Tighter chips (fits by ~6 dp)", option (a) in the J5 report): Material's own 18 dp chip icon
 * ([FilterChipDefaults.IconSize]) in place of the unsized `Icon`'s 24 dp, 4 dp between chips and
 * 12 dp at the row's ends, so All, Finds, Tracks, Waypoints and Offline maps, each with its icon and
 * count, fit one line at the 640 dp cap. Measured in native graphics with two-digit counts, the row
 * fits with 6 dp to spare, so a guard test (`AvailabilityScreenJournalShortWindowTest`, "L5a guard")
 * fails if it stops fitting. Larger counts or font scales can still overflow; the row still scrolls
 * sideways then, as J1 built it.
 */
internal data class RecordsChipRowMetrics(
    /** `null` leaves the chip's `Icon` at its own size (24 dp), as J1 built it. */
    val iconSize: Dp?,
    val chipGap: Dp,
    val rowPadding: Dp,
) {
    companion object {
        val Default = RecordsChipRowMetrics(iconSize = null, chipGap = Spacing.sm, rowPadding = Spacing.lg)
        val ShortWindow = RecordsChipRowMetrics(iconSize = FilterChipDefaults.IconSize, chipGap = Spacing.xs, rowPadding = Spacing.md)
    }
}

/** The record type a single-type chip filters to; `null` for [RecordsSubTab.ALL]. */
internal fun RecordsSubTab.recordType(): RecordType? = when (this) {
    RecordsSubTab.ALL -> null
    RecordsSubTab.FINDS -> RecordType.FINDS
    RecordsSubTab.RECORDED_TRACKS -> RecordType.TRACKS
    RecordsSubTab.WAYPOINTS -> RecordType.WAYPOINTS
    RecordsSubTab.OFFLINE_MAPS -> RecordType.OFFLINE_MAPS
}

internal fun RecordsSubTab.chipLabel(): String = when (this) {
    RecordsSubTab.ALL -> "All"
    RecordsSubTab.FINDS -> "Finds"
    RecordsSubTab.RECORDED_TRACKS -> "Tracks"
    RecordsSubTab.WAYPOINTS -> "Waypoints"
    RecordsSubTab.OFFLINE_MAPS -> "Offline maps"
}

/**
 * The chip icons. The plan's mockup draws them as emoji (🍄 〰 📍 🗺); these are the nearest glyphs
 * in the Material icon set the app already depends on (`material-icons-extended`), which has no
 * mushroom: a leaf for finds, a line graph for tracks, a pin for waypoints, a map for offline maps,
 * a list for All.
 */
internal fun RecordsSubTab.chipIcon(): ImageVector = when (this) {
    RecordsSubTab.ALL -> Icons.AutoMirrored.Filled.ViewList
    RecordsSubTab.FINDS -> Icons.Filled.Eco
    RecordsSubTab.RECORDED_TRACKS -> Icons.Filled.Timeline
    RecordsSubTab.WAYPOINTS -> Icons.Filled.Place
    RecordsSubTab.OFFLINE_MAPS -> Icons.Filled.Map
}

/** The chip row itself: it fills the width Records is given, so a layout test can measure that width through it. */
internal const val RECORDS_FILTER_CHIP_ROW_TAG = "records-filter-chip-row"

/** Test tag of each filter chip — stable strings, not enum names, so a rename of [RecordsSubTab] does not move them. */
internal fun recordsFilterChipTestTag(filter: RecordsSubTab): String = when (filter) {
    RecordsSubTab.ALL -> "records-chip-all"
    RecordsSubTab.FINDS -> "records-chip-finds"
    RecordsSubTab.RECORDED_TRACKS -> "records-chip-tracks"
    RecordsSubTab.WAYPOINTS -> "records-chip-waypoints"
    RecordsSubTab.OFFLINE_MAPS -> "records-chip-offline-maps"
}
