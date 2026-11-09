package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.EntryContents
import com.zynergylabs.forager.app.domain.EntryFindItem
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.EntryGroupState
import com.zynergylabs.forager.app.domain.EntryIncludedCounts
import com.zynergylabs.forager.app.domain.EntryItem
import com.zynergylabs.forager.app.domain.EntryOfflineMapItem
import com.zynergylabs.forager.app.domain.EntryTrackItem
import com.zynergylabs.forager.app.domain.EntryWaypointItem
import com.zynergylabs.forager.app.domain.Inclusion
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.formatTimeSpan
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.format.displayDate

/**
 * The editor's "In this entry" panel (dispatch 2026-09-28-667, data part A; the owner in RECORD -656:
 * "Summary first, open to adjust (Recommended)"), in place of the one-card-per-item Withhold list.
 *
 * From the top: a summary line of what is included; then anything new since the entry was last saved,
 * each with Include and Leave out (a new item is neither yet, so it gets both, as before); then one row
 * per group with a switch. A tap on a group row, anywhere but its switch, opens or closes it to show its
 * items, each with its own switch; a track's waypoints (its Start, its End, and any dropped while it
 * recorded) sit indented under it. Which items each group holds, and which its switch changes, is
 * [EntryContents]'s rule, so the two can not disagree.
 *
 * The words are the owner's ("'Leave out' (Recommended)": "Withhold" becomes "Leave out", and its
 * opposite "Include"). A switch on means included. A group's switch is on while anything in it is
 * included, with its line saying whether that is all of it; turning it on includes everything in the
 * group, turning it off leaves everything out.
 *
 * Which groups are open is hoisted ([openGroups]), so it survives a tab change as the editor itself
 * does (CLAUDE.md, UX defaults: what the user set survives navigating away and back).
 */
@Composable
internal fun EntryContentsPanel(
    contents: EntryContents,
    distanceUnit: DistanceUnit,
    openGroups: Set<EntryGroup>,
    onToggleGroupOpen: (EntryGroup) -> Unit,
    onSetGroupIncluded: (EntryGroup, Boolean) -> Unit,
    onSetFindIncluded: (String, Boolean) -> Unit,
    onSetTrackIncluded: (String, Boolean) -> Unit,
    onSetWaypointIncluded: (String, Boolean) -> Unit,
    onSetOfflineMapIncluded: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val setIncluded: (EntryItem, Boolean) -> Unit = { item, included ->
        when (item) {
            is EntryTrackItem -> onSetTrackIncluded(item.id, included)
            is EntryWaypointItem -> onSetWaypointIncluded(item.id, included)
            is EntryFindItem -> onSetFindIncluded(item.id, included)
            is EntryOfflineMapItem -> onSetOfflineMapIncluded(item.regionId, included)
        }
    }
    val groups = EntryGroup.entries.filter { contents.groupState(it) != EntryGroupState.EMPTY }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("In this entry", style = MaterialTheme.typography.titleSmall)
        Text(
            entrySummaryLine(contents.included, anythingToChoose = groups.isNotEmpty() || contents.newItems.isNotEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(ENTRY_SUMMARY_LINE_TAG),
        )

        if (contents.newItems.isNotEmpty()) {
            Text(
                NEW_ITEMS_HEADING,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            contents.newItems.forEach { item ->
                NewItemRow(item = item, distanceUnit = distanceUnit, onSetIncluded = { setIncluded(item, it) })
            }
        }

        groups.forEach { group ->
            HorizontalDivider()
            GroupRow(
                group = group,
                state = contents.groupState(group),
                open = group in openGroups,
                onToggleOpen = { onToggleGroupOpen(group) },
                onSetIncluded = { onSetGroupIncluded(group, it) },
            )
            if (group in openGroups) {
                when (group) {
                    EntryGroup.TRACKS -> contents.tracks.forEach { (track, waypoints) ->
                        ItemRow(item = track, distanceUnit = distanceUnit, indent = 1, onSetIncluded = { setIncluded(track, it) })
                        waypoints.forEach { waypoint ->
                            ItemRow(item = waypoint, distanceUnit = distanceUnit, indent = 2, onSetIncluded = { setIncluded(waypoint, it) })
                        }
                    }
                    EntryGroup.WAYPOINTS -> contents.waypoints.forEach { item ->
                        ItemRow(item = item, distanceUnit = distanceUnit, indent = 1, onSetIncluded = { setIncluded(item, it) })
                    }
                    EntryGroup.FINDS -> contents.finds.forEach { item ->
                        ItemRow(item = item, distanceUnit = distanceUnit, indent = 1, onSetIncluded = { setIncluded(item, it) })
                    }
                    EntryGroup.OFFLINE_MAPS -> contents.offlineMaps.forEach { item ->
                        ItemRow(item = item, distanceUnit = distanceUnit, indent = 1, onSetIncluded = { setIncluded(item, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupRow(
    group: EntryGroup,
    state: EntryGroupState,
    open: Boolean,
    onToggleOpen: () -> Unit,
    onSetIncluded: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = if (open) "Hide ${group.title.lowercase()}" else "Show ${group.title.lowercase()}", onClick = onToggleOpen)
            .padding(vertical = Spacing.xs)
            .testTag(entryGroupRowTag(group)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(group.title, style = MaterialTheme.typography.bodyLarge)
            Text(state.line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = state == EntryGroupState.ALL_INCLUDED || state == EntryGroupState.SOME_INCLUDED,
            onCheckedChange = onSetIncluded,
            modifier = Modifier.testTag(entryGroupSwitchTag(group)).semantics { contentDescription = "Include all ${group.title.lowercase()}" },
        )
    }
}

@Composable
private fun ItemRow(item: EntryItem, distanceUnit: DistanceUnit, indent: Int, onSetIncluded: (Boolean) -> Unit) {
    val included = item.inclusion == Inclusion.INCLUDED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (INDENT_STEP_DP * indent).dp)
            .testTag(entryItemRowTag(item.tagKey)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(modifier = Modifier.weight(1f).alpha(if (included) 1f else LEFT_OUT_ALPHA)) {
            Text(item.title(), style = MaterialTheme.typography.bodyMedium)
            item.detail(distanceUnit)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(
            checked = included,
            onCheckedChange = onSetIncluded,
            modifier = Modifier.testTag(entryItemSwitchTag(item.tagKey)).semantics { contentDescription = "Include ${item.title()}" },
        )
    }
}

/** A new item has no decision, so it gets both actions and neither is chosen for it (as the editor's "New" rows always did). */
@Composable
private fun NewItemRow(item: EntryItem, distanceUnit: DistanceUnit, onSetIncluded: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(entryItemRowTag(item.tagKey)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title(), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(item.kindLabel, item.detail(distanceUnit)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { onSetIncluded(true) }, modifier = Modifier.testTag(entryNewIncludeTag(item.tagKey))) { Text(INCLUDE_LABEL) }
        TextButton(onClick = { onSetIncluded(false) }, modifier = Modifier.testTag(entryNewLeaveOutTag(item.tagKey))) { Text(LEAVE_OUT_LABEL) }
    }
}

/** "In this entry: 1 track, 5 waypoints, 6 finds, 1 offline map", the owner's example, or what to do when nothing is included. */
internal fun entrySummaryLine(counts: EntryIncludedCounts, anythingToChoose: Boolean): String {
    if (counts.isEmpty) return if (anythingToChoose) NOTHING_INCLUDED_LINE else NOTHING_TO_INCLUDE_LINE
    val parts = listOfNotNull(
        counted(counts.tracks, "track", "tracks"),
        counted(counts.waypoints, "waypoint", "waypoints"),
        counted(counts.finds, "find", "finds"),
        counted(counts.offlineMaps, "offline map", "offline maps"),
    )
    return "In this entry: ${parts.joinToString(", ")}"
}

private fun counted(count: Int, one: String, many: String): String? = when (count) {
    0 -> null
    1 -> "1 $one"
    else -> "$count $many"
}

private val EntryGroup.title: String
    get() = when (this) {
        EntryGroup.TRACKS -> "Tracks"
        EntryGroup.WAYPOINTS -> "Waypoints"
        EntryGroup.FINDS -> "Finds"
        EntryGroup.OFFLINE_MAPS -> "Offline maps"
    }

private val EntryGroupState.line: String
    get() = when (this) {
        EntryGroupState.ALL_INCLUDED -> "All included"
        EntryGroupState.SOME_INCLUDED -> "Some left out"
        EntryGroupState.ALL_LEFT_OUT -> "All left out"
        EntryGroupState.NOT_CHOSEN -> "New, not chosen yet"
        EntryGroupState.EMPTY -> ""
    }

private val EntryItem.kindLabel: String
    get() = when (this) {
        is EntryTrackItem -> "Track"
        is EntryWaypointItem -> "Waypoint"
        is EntryFindItem -> "Find"
        is EntryOfflineMapItem -> "Offline map"
    }

private fun EntryItem.title(): String = when (this) {
    is EntryTrackItem -> name ?: "Recorded track"
    is EntryWaypointItem -> name
    is EntryFindItem -> "Find on ${displayDate(foundOn)}"
    is EntryOfflineMapItem -> name
}

/**
 * The item's one line of figures, each with its unit: a track's distance and time (and, as before, a
 * note when the read seam left it with few or no usable points); an offline map's radius, named as one.
 * A waypoint shows none: its coordinates were the raw line this replaces, and its name already says
 * what it is ("Start · Oct 7, 12:08 PM").
 */
private fun EntryItem.detail(distanceUnit: DistanceUnit): String? = when (this) {
    is EntryTrackItem -> labelledTrackLine(distanceMeters, durationMillis, distanceUnit) +
        trackExclusionSuffix(liveTrack, snapshotPointCount = if (inclusion == Inclusion.NEW) null else pointCount)
    is EntryWaypointItem -> null
    is EntryFindItem -> ownIdentification
    is EntryOfflineMapItem -> "${formatDistanceKm(radiusKm, distanceUnit)} radius"
}

/**
 * A track's length and time on this part's screens: "2.3 mi · 1 h 12 min". Distance through
 * [formatDistanceMeters] for the reason `trackSubtitle`'s doc comment records (never the kilometre
 * formatter); time through [formatTimeSpan], so no "m" for minutes sits beside a distance.
 */
internal fun labelledTrackLine(distanceMeters: Double, durationMillis: Long, distanceUnit: DistanceUnit): String =
    "${formatDistanceMeters(distanceMeters, distanceUnit)} · ${formatTimeSpan(durationMillis)}"

internal const val INCLUDE_LABEL = "Include"
internal const val LEAVE_OUT_LABEL = "Leave out"
internal const val NEW_ITEMS_HEADING = "New since you last saved"
internal const val NOTHING_INCLUDED_LINE = "Nothing in this entry yet. Turn on a group below to include it."
internal const val NOTHING_TO_INCLUDE_LINE = "Nothing from this day to include yet."

private const val INDENT_STEP_DP = 16
private const val LEFT_OUT_ALPHA = 0.6f

internal const val ENTRY_SUMMARY_LINE_TAG = "entry-contents-summary"
internal fun entryGroupRowTag(group: EntryGroup) = "entry-group-row-${group.name}"
internal fun entryGroupSwitchTag(group: EntryGroup) = "entry-group-switch-${group.name}"

/** An item's key in the tags below: its kind and id, since an offline map's id is a small number another kind's could equal. */
internal fun trackItemKey(id: String) = "track-$id"
internal fun waypointItemKey(id: String) = "waypoint-$id"
internal fun findItemKey(id: String) = "find-$id"
internal fun offlineMapItemKey(id: Long) = "map-$id"

private val EntryItem.tagKey: String
    get() = when (this) {
        is EntryTrackItem -> trackItemKey(id)
        is EntryWaypointItem -> waypointItemKey(id)
        is EntryFindItem -> findItemKey(id)
        is EntryOfflineMapItem -> offlineMapItemKey(regionId)
    }

internal fun entryItemRowTag(key: String) = "entry-item-row-$key"
internal fun entryItemSwitchTag(key: String) = "entry-item-switch-$key"
internal fun entryNewIncludeTag(key: String) = "entry-new-include-$key"
internal fun entryNewLeaveOutTag(key: String) = "entry-new-leave-out-$key"
