package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * An Entries card turned sideways for a short window — journal redesign J5, L4
 * (`prompts/preserved/2026-09-27-26.md`; plan L4, owner's rulings 2 and 3). Two sit side by side
 * (`CartographyEntryListScreen`'s two columns in a short window), so the portrait card's 140 dp hero
 * on top would leave room for barely one row in about 290 dp of content height.
 *
 * - **The slot** ([slot], 72 dp square, at the start): the hero photo if there is one (J3's rule,
 *   [entryHeroPhoto]); else the track thumbnail, every kept track in one box (J3's [TracksThumbnail]
 *   as J4 extended it); else a panel tinted in one record type's colours with that type's icon
 *   ([EntryTypePanel]). Ruling 3, "Photo, then track, then icon (Recommended)". [entrySlotContent]
 *   decides, from data the list already holds.
 * - **The text** beside it is J3's card content, arranged for the width: the day numeral, weekday and
 *   title on one line; the preview; the species chips (one line); the stats by type; the tags.
 * - **The gesture** (ruling 2, "Long-press, like grids (Recommended)"): the card is a grid tile now,
 *   so it takes J4b's long-press menu (Edit, Delete) through [tileClickable] when [options] is given,
 *   instead of the portrait card's two-stage swipe. A plain tap still opens the entry.
 *
 * A new composable rather than a flag on [CartographyEntryCard] (CLAUDE.md, Building): portrait's
 * card is unchanged.
 */
@Composable
internal fun SidewaysEntryCard(
    entry: CartographyEntry,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    slot: @Composable () -> Unit,
    options: TileOptions?,
    modifier: Modifier = Modifier,
) {
    val (title, body) = entryTitleAndBody(entry.text)
    val species = entrySpecies(entry)
    val stats = entryStats(entry, distanceUnit)
    val shape = RoundedCornerShape(Spacing.sm)
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .testTag(entryCardTestTag(entry.id))
            .clip(shape)
            .entryTileClickable(onClick, options),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            slot()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text(entryDayNumeral(entry.date), style = MaterialTheme.typography.titleMedium)
                    Text(entryWeekday(entry.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    }
                }
                if (body != null) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (species.isNotEmpty()) {
                    val finds = RecordTypeStyle.colors(RecordType.FINDS)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), maxLines = 1) {
                        species.forEach { name ->
                            Text(
                                name,
                                style = MaterialTheme.typography.labelMedium,
                                color = finds.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .background(finds.container, RoundedCornerShape(Spacing.sm))
                                    .padding(horizontal = Spacing.sm, vertical = 1.dp),
                            )
                        }
                    }
                }
                if (stats.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        stats.forEach { EntryStat(it) }
                    }
                }
                if (entry.tags.isNotEmpty()) {
                    Text(
                        entry.tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * J3's collapsed row (an entry with no hero, no text and no kept track, [isCollapsedEntry]) in a
 * short window: the same one line, with the long-press menu of ruling 2 instead of the swipe. It has
 * no slot: J3's rule already decided it has nothing to draw large, and the slot is drawing large.
 */
@Composable
internal fun SidewaysCollapsedEntryRow(
    entry: CartographyEntry,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    options: TileOptions?,
    modifier: Modifier = Modifier,
) {
    val stats = entryStats(entry, distanceUnit)
    val shape = RoundedCornerShape(Spacing.sm)
    Card(
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .testTag(entryRowTestTag(entry.id))
            .clip(shape)
            .entryTileClickable(onClick, options),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryDay(entry.date, large = false)
            if (stats.isEmpty()) {
                Text("Nothing included", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    stats.forEach { EntryStat(it) }
                }
            }
        }
    }
}

/** The tap, and with [options] J4b's long-press and its Edit/Delete actions, on the card's one node. */
private fun Modifier.entryTileClickable(onClick: () -> Unit, options: TileOptions?): Modifier =
    if (options != null) tileClickable(onClick = onClick, options = options, onClickLabel = "Open") else clickable(onClickLabel = "Open", onClick = onClick)

/** What fills a sideways card's slot (ruling 3), decided in this order. */
internal sealed interface EntrySlot {
    data class Photo(val photo: GalleryPhoto) : EntrySlot
    data class Tracks(val tracks: List<Track>) : EntrySlot

    /** [type] null: the entry keeps nothing (it has writing, or it would have collapsed). */
    data class TypeIcon(val type: RecordType?) : EntrySlot
}

/**
 * Ruling 3: the hero photo ([entryHeroPhoto]) if one resolves; else every kept track found
 * ([entryThumbnailTracks]); else the icon of the entry's first kept type in the Records chips' order
 * (finds, tracks, waypoints, offline maps, [entryStats]' order), or none. Pure, so the order is
 * testable without Compose.
 */
internal fun entrySlotContent(hero: GalleryPhoto?, thumbnailTracks: List<Track>, stats: List<EntryStat>): EntrySlot = when {
    hero != null -> EntrySlot.Photo(hero)
    thumbnailTracks.isNotEmpty() -> EntrySlot.Tracks(thumbnailTracks)
    else -> EntrySlot.TypeIcon(stats.firstOrNull()?.type)
}

/** Draws [slot] at the 72 dp size, tagged as the portrait card tags its hero and thumbnail. */
@Composable
internal fun EntrySlotView(entryId: String, slot: EntrySlot) {
    val shape = RoundedCornerShape(Spacing.xs)
    when (slot) {
        is EntrySlot.Photo -> DecodedPhoto(
            relativePath = slot.photo.photo.relativePath,
            contentDescription = null,
            modifier = Modifier.size(ENTRY_SLOT_SIZE).clip(shape).testTag(entryHeroTestTag(entryId, slot.photo.photo.id)),
        )
        is EntrySlot.Tracks -> Box(
            modifier = Modifier
                .size(ENTRY_SLOT_SIZE)
                .clip(shape)
                .background(RecordTypeStyle.colors(RecordType.TRACKS).container)
                .testTag(entryTrackThumbnailTestTag(entryId)),
        ) {
            TracksThumbnail(
                trackIds = slot.tracks.map { it.id },
                tracks = slot.tracks.map { it.points },
                modifier = Modifier.size(ENTRY_SLOT_SIZE).padding(Spacing.xs),
            )
        }
        is EntrySlot.TypeIcon -> EntryTypePanel(entryId, slot.type)
    }
}

/**
 * The tinted type-icon panel: the type's container colour behind its chip icon in the type's accent
 * (plan J6's roles, [RecordTypeStyle]). An entry that keeps nothing gets a neutral panel with the
 * plan's ✎ (`Edit`) glyph, since it has writing and no record to stand for.
 */
@Composable
private fun EntryTypePanel(entryId: String, type: RecordType?) {
    val container = if (type != null) RecordTypeStyle.colors(type).container else MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = if (type != null) RecordTypeStyle.colors(type).accent else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .size(ENTRY_SLOT_SIZE)
            .clip(RoundedCornerShape(Spacing.xs))
            .background(container)
            .testTag(entryTypePanelTestTag(entryId)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type?.let(::entryStatIcon) ?: Icons.Filled.Edit, contentDescription = null, tint = accent, modifier = Modifier.size(ENTRY_SLOT_ICON_SIZE))
    }
}

/** L4's thumbnail size (plan L4: 72 dp). */
internal val ENTRY_SLOT_SIZE = 72.dp
private val ENTRY_SLOT_ICON_SIZE = 32.dp

internal fun entryTypePanelTestTag(entryId: String): String = "entry-type-panel-$entryId"
