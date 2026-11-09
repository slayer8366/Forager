package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.log.DecodedPhoto
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import com.zynergylabs.forager.app.ui.theme.Spacing

/** Every kind's bubble but a sighting's (B3), on [MapBubbleShell], with its actions (B4). */
@Composable
internal fun MapFeatureBubble(
    content: MapBubbleContent,
    tipInBubble: State<Offset?>,
    onDismiss: () -> Unit,
    openFindLabel: String,
    onOpenFind: ((String) -> Unit)?,
    onViewPhoto: (String) -> Unit,
    onDirections: (name: String, at: LatLng) -> Unit,
    onDetails: (RecordDetailsTarget) -> Unit,
    /**
     * J8-4: opens a keeping entry (owner's Q1 ruling, "Open in Journal, prompt first"). With it, a
     * highlighted record's bubble draws its keeping entries ([KeptInEntries]); `null` draws none.
     */
    onOpenEntry: ((String) -> Unit)? = null,
    /**
     * Dispatch 2026-09-28-502: a waypoint's "Navigate", given its id, first among its actions, beside
     * Directions (T9). `null` offers none: an entry map's bubble keeps Directions only (Amendment 1).
     */
    onNavigate: ((String) -> Unit)? = null,
) {
    MapBubbleShell(tipInBubble = tipInBubble, onDismiss = onDismiss, cardTag = MAP_BUBBLE_TAG, closeTag = MAP_BUBBLE_CLOSE_TAG) {
        when (content) {
            is MapBubbleContent.Find -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    content.coverPhotoPath?.let { DecodedPhoto(relativePath = it, modifier = Modifier.size(BUBBLE_THUMBNAIL_SIZE), contentDescription = "Cover photo") }
                    Column {
                        // A blank title (RECORD -703) draws nothing and says the tile's label to a screen reader.
                        content.title?.let { BubbleTitle(it) }
                            ?: content.blankTitleLabel?.let { label -> Box(Modifier.semantics { contentDescription = label }) }
                        content.date?.let { BubbleLine(it) }
                    }
                }
                KeptInEntries(content.keptIn, onOpenEntry)
                onOpenFind?.let { open -> BubbleActions { BubbleAction(openFindLabel, MAP_BUBBLE_OPEN_FIND_TAG) { open(content.findId) } } }
            }
            is MapBubbleContent.Photo -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    DecodedPhoto(relativePath = content.photo.relativePath, modifier = Modifier.size(BUBBLE_THUMBNAIL_SIZE), contentDescription = "Photo")
                    Column {
                        BubbleTitle(content.date)
                        content.attachedTo?.let { BubbleLine(it) }
                    }
                }
                KeptInEntries(content.keptIn, onOpenEntry)
                BubbleActions { BubbleAction("View photo", MAP_BUBBLE_VIEW_PHOTO_TAG) { onViewPhoto(content.photo.id) } }
            }
            is MapBubbleContent.WaypointContent -> {
                BubbleTitle(content.waypoint.name)
                content.mgrs?.let { BubbleLine(it) }
                content.fromStart?.let { BubbleLine(it) }
                KeptInEntries(content.keptIn, onOpenEntry)
                BubbleActions {
                    // Only for a waypoint still in Records: one an entry knows only from its snapshot has nothing to navigate by.
                    onNavigate?.takeIf { content.hasDetails }?.let { navigate -> BubbleAction("Navigate", MAP_BUBBLE_NAVIGATE_TAG) { navigate(content.waypoint.id) } }
                    BubbleAction("Directions", MAP_BUBBLE_DIRECTIONS_TAG) { onDirections(content.waypoint.name, LatLng(content.waypoint.lat, content.waypoint.lng)) }
                    if (content.hasDetails) {
                        BubbleAction("Details", MAP_BUBBLE_DETAILS_TAG) { onDetails(RecordDetailsTarget.WaypointDetails(content.waypoint.id)) }
                    }
                }
            }
            is MapBubbleContent.TrackContent -> {
                BubbleTitle(content.title)
                content.date?.let { BubbleLine(it) }
                BubbleLine("${content.distance} · ${content.duration}")
                KeptInEntries(content.keptIn, onOpenEntry)
                if (content.hasDetails) {
                    BubbleActions { BubbleAction("Details", MAP_BUBBLE_DETAILS_TAG) { onDetails(RecordDetailsTarget.TrackDetails(content.trackId)) } }
                }
            }
            is MapBubbleContent.Trip -> {
                BubbleTitle(content.trip.name)
                BubbleLine(content.date)
                content.mgrs?.let { BubbleLine(it) }
                BubbleLine(content.coordinates)
                // No further target: a planned trip has no sheet (planner's M1 ruling).
                BubbleActions { BubbleAction("Directions", MAP_BUBBLE_DIRECTIONS_TAG) { onDirections(content.trip.name, content.trip.location) } }
            }
            is MapBubbleContent.Region -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    BubbleTitle(content.name)
                    if (content.stale) {
                        Text("Stale", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag(MAP_BUBBLE_STALE_TAG))
                    }
                }
                BubbleLine("${content.radius} · ${content.size}")
                KeptInEntries(content.keptIn, onOpenEntry)
                BubbleActions { BubbleAction("Details", MAP_BUBBLE_DETAILS_TAG) { onDetails(RecordDetailsTarget.OfflineRegionDetails(content.regionId)) } }
            }
            is MapBubbleContent.Cell -> {
                BubbleTitle(content.layerName)
                Text(content.chance, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag(MAP_BUBBLE_CHANCE_TAG))
                BubbleLine(content.range)
                content.drivers.forEach { BubbleLine(it) }
                BubbleLine(content.dates)
                Text(
                    content.referenceClass,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * J8-4, a highlighted record's keeping entries (owner's Q2 ruling, "Tap a date line"; planner's Q3,
 * the report header's date form): up to three, one line each showing its date, whose tap opens that
 * entry, labelled "Open entry <date>" for TalkBack; past three, one line, "In N journal entries" (was "Kept in"; RECORD -671),
 * whose tap opens an untitled list of their dates, each opening its entry. Nothing for a record no
 * shown entry keeps, or without [onOpenEntry] (every map but the Maps tab). The list is a menu over the
 * map at the map chrome's opacity ([journalMenuContainerColor]).
 */
@Composable
private fun KeptInEntries(keptIn: List<JournalEntryOnMap>, onOpenEntry: ((String) -> Unit)?) {
    val open = onOpenEntry ?: return
    when (val lines = keptInEntriesLines(keptIn)) {
        null -> Unit
        is KeptInEntriesLines.Dates -> lines.lines.forEach { line ->
            TextButton(
                onClick = { open(line.entryId) },
                contentPadding = PaddingValues(horizontal = Spacing.sm),
                modifier = Modifier
                    .testTag(mapBubbleEntryLineTag(line.entryId))
                    .semantics { contentDescription = line.accessibilityLabel },
            ) {
                Text(line.date, style = MaterialTheme.typography.labelMedium)
            }
        }
        is KeptInEntriesLines.Count -> {
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = Spacing.sm),
                    modifier = Modifier.testTag(MAP_BUBBLE_ENTRY_COUNT_TAG),
                ) {
                    Text(lines.label, style = MaterialTheme.typography.labelMedium)
                }
                val container = journalMenuContainerColor()
                val content = journalMenuContentColor()
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = container,
                    modifier = Modifier.testTag(MAP_BUBBLE_ENTRY_LIST_TAG).journalMenuColours(container, content),
                ) {
                    lines.entries.forEach { line ->
                        DropdownMenuItem(
                            text = { Text(line.date) },
                            onClick = {
                                expanded = false
                                open(line.entryId)
                            },
                            colors = MenuDefaults.itemColors(textColor = content),
                            modifier = Modifier
                                .testTag(mapBubbleEntryLineTag(line.entryId))
                                .semantics { contentDescription = line.accessibilityLabel },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BubbleTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun BubbleLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun BubbleActions(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
}

@Composable
private fun BubbleAction(label: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = Spacing.sm), modifier = Modifier.testTag(tag)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private val BUBBLE_THUMBNAIL_SIZE = 56.dp

internal const val MAP_BUBBLE_TAG = "map-bubble"
internal const val MAP_BUBBLE_CLOSE_TAG = "map-bubble-close"
internal const val MAP_BUBBLE_OPEN_FIND_TAG = "map-bubble-open-find"
internal const val MAP_BUBBLE_VIEW_PHOTO_TAG = "map-bubble-view-photo"
internal const val MAP_BUBBLE_DIRECTIONS_TAG = "map-bubble-directions"
internal const val MAP_BUBBLE_NAVIGATE_TAG = "map-bubble-navigate"
internal const val MAP_BUBBLE_DETAILS_TAG = "map-bubble-details"
internal const val MAP_BUBBLE_STALE_TAG = "map-bubble-stale"
internal const val MAP_BUBBLE_CHANCE_TAG = "map-bubble-chance"

/** J8: a highlighted record's bubble line for one keeping entry, by its date; a tap opens that entry. */
internal fun mapBubbleEntryLineTag(entryId: String) = "map-bubble-entry-$entryId"

/** J8: the one line, "In N journal entries" (was "Kept in"; RECORD -671), that stands for more than three keeping entries. */
internal const val MAP_BUBBLE_ENTRY_COUNT_TAG = "map-bubble-entry-count"

/** J8: the untitled list of those entries' dates, opened from [MAP_BUBBLE_ENTRY_COUNT_TAG]. */
internal const val MAP_BUBBLE_ENTRY_LIST_TAG = "map-bubble-entry-list"
