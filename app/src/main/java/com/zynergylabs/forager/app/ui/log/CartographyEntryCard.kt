package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPoint
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * One Entries timeline card — journal redesign J3, C1 (plan J5; `prompts/preserved/2026-09-27-20.md`).
 * Replaces the old tile, which showed an ISO date, the whole text, the tags and one summed "N kept
 * items" count, so two entries on one day looked identical.
 *
 * What it shows, all from data [CartographyEntry] already holds (no new read):
 * - a large day numeral with the weekday ([entryDayNumeral], [entryWeekday]) instead of the ISO date;
 * - the first line of [CartographyEntry.text] as the title, the rest as a short preview
 *   ([entryTitleAndBody]);
 * - kept finds' [com.zynergylabs.forager.app.domain.model.FindDecision.ownIdentification]s as species
 *   chips, each species once ([entrySpecies]), in [RecordTypeStyle]'s Finds colours;
 * - a stats row by type ([entryStats]), each in its type's [RecordTypeStyle] colours;
 * - the tags, as before (the plan's J5 list does not name them; the card keeps what it already showed).
 *
 * Kept decisions only, throughout: a withheld find, track, waypoint or region is not part of the
 * entry (see [CartographyEntry]'s doc comment on the three states), exactly as the old tile's count
 * read only `kept` ones.
 *
 * An entry with nothing to draw large (no hero photo, no text, no kept track) is not a card at all
 * but [CollapsedEntryRow] (plan J5, "collapses to one short row"); [isCollapsedEntry] decides.
 */
@Composable
internal fun CartographyEntryCard(
    entry: CartographyEntry,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Drawn on top of the card when non-null (C2). */
    hero: (@Composable () -> Unit)? = null,
    /** Drawn at the card's end when non-null (C3). */
    thumbnail: (@Composable () -> Unit)? = null,
) {
    val (title, body) = entryTitleAndBody(entry.text)
    val species = entrySpecies(entry)
    val stats = entryStats(entry, distanceUnit)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(entryCardTestTag(entry.id)),
        shape = RoundedCornerShape(Spacing.sm),
    ) {
        Column {
            hero?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                EntryDay(entry.date, large = true)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (title != null) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (body != null) {
                        Text(
                            body,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (species.isNotEmpty()) {
                        val finds = RecordTypeStyle.colors(RecordType.FINDS)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            species.forEach { name ->
                                Text(
                                    name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = finds.accent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .background(finds.container, RoundedCornerShape(Spacing.sm))
                                        .padding(horizontal = Spacing.sm, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (stats.isNotEmpty()) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
                thumbnail?.invoke()
            }
        }
    }
}

/**
 * The short row an entry with no hero photo, no text and no kept track collapses to (plan J5): its
 * day and weekday and its stats on one line, or "Nothing kept" when it keeps nothing. Tags are left
 * out to keep it one line; the entry's report shows them.
 */
@Composable
internal fun CollapsedEntryRow(
    entry: CartographyEntry,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = entryStats(entry, distanceUnit)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().testTag(entryRowTestTag(entry.id)),
        shape = RoundedCornerShape(Spacing.sm),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            EntryDay(entry.date, large = false)
            if (stats.isEmpty()) {
                Text("Nothing kept", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    stats.forEach { EntryStat(it) }
                }
            }
        }
    }
}

/** The sticky month header over a run of entries (plan J5), e.g. "SEPTEMBER 2026". */
@Composable
internal fun EntryMonthHeader(month: YearMonth, modifier: Modifier = Modifier) {
    Text(
        entryMonthLabel(month),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = Spacing.sm)
            .testTag(entryMonthTestTag(month)),
    )
}

@Composable
internal fun EntryDay(date: LocalDate, large: Boolean) {
    Column(modifier = Modifier.widthIn(min = if (large) 44.dp else 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(entryDayNumeral(date), style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium)
        Text(entryWeekday(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun EntryStat(stat: EntryStat) {
    val colors = RecordTypeStyle.colors(stat.type)
    Row(
        modifier = Modifier.background(colors.container, RoundedCornerShape(Spacing.sm)).padding(horizontal = Spacing.xs, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(entryStatIcon(stat.type), contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
        Text(stat.text, style = MaterialTheme.typography.labelMedium, color = colors.accent, maxLines = 1)
    }
}

/** The Records chips' icons (J1, `RecordsFilterChips.kt`), so a stat reads as the same kind of thing its chip does. */
internal fun entryStatIcon(type: RecordType): ImageVector = when (type) {
    RecordType.FINDS -> Icons.Filled.Eco
    RecordType.TRACKS -> Icons.Filled.Timeline
    RecordType.WAYPOINTS -> Icons.Filled.Place
    RecordType.OFFLINE_MAPS -> Icons.Filled.Map
}

// ── Pure helpers (no Compose), so what a card says is decidable from the entry alone ──

/** One figure in a card's stats row: its [type] (for colour and icon) and its [text], unit included. */
internal data class EntryStat(val type: RecordType, val text: String)

/** The day of the month, e.g. "26". */
internal fun entryDayNumeral(date: LocalDate): String = date.dayOfMonth.toString()

/** The short weekday in capitals, e.g. "SAT", in the device's locale (as the app's other date labels). */
internal fun entryWeekday(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(Locale.getDefault())

/** The month header's label, e.g. "SEPTEMBER 2026". */
internal fun entryMonthLabel(month: YearMonth): String = MONTH_HEADER_FORMAT.format(month).uppercase(Locale.getDefault())

private val MONTH_HEADER_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * The first line of [text] as the title and what follows as the preview body, each trimmed; `null`
 * for a part that is empty. Leading blank lines are skipped, so the title is the first line with
 * something on it.
 */
internal fun entryTitleAndBody(text: String): Pair<String?, String?> {
    val lines = text.trim().lines()
    val title = lines.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    val body = lines.drop(1).joinToString("\n").trim().takeIf { it.isNotEmpty() }
    return title to body
}

/**
 * Kept finds' identifications, each species once, in the order the finds are listed. A kept find
 * with no identification (`ownIdentification` null or blank) gets no chip, as the entry report
 * shows none for it either (`CartographyEntryReportScreen`'s kept-finds rows); it still counts in
 * the finds stat.
 */
internal fun entrySpecies(entry: CartographyEntry): List<String> =
    entry.findDecisions.filter { it.kept }.mapNotNull { it.ownIdentification?.trim()?.takeIf(String::isNotEmpty) }.distinct()

/**
 * The stats row, one figure per type with anything kept, in the Records chips' order (finds,
 * tracks, waypoints, offline maps). A type with nothing kept has no figure.
 *
 * Tracks: the kept tracks' stored [com.zynergylabs.forager.app.domain.model.TrackDecision.distanceMeters]
 * and [com.zynergylabs.forager.app.domain.model.TrackDecision.durationMillis], summed (one figure for
 * the type, as the other three types' counts are), through the app's existing track formatter
 * [trackSubtitle] in the user's [distanceUnit] — never `formatDistanceKm`, whose rounding that
 * formatter's doc comment warns against.
 */
internal fun entryStats(entry: CartographyEntry, distanceUnit: DistanceUnit): List<EntryStat> = buildList {
    val finds = entry.findDecisions.count { it.kept }
    if (finds > 0) add(EntryStat(RecordType.FINDS, countLabel(finds, "find", "finds")))
    val tracks = entry.trackDecisions.filter { it.kept }
    if (tracks.isNotEmpty()) {
        val total = trackSubtitle(tracks.sumOf { it.distanceMeters }, tracks.sumOf { it.durationMillis }, distanceUnit)
        // J4, D5 (owner ruling "Sum, with a count (Recommended)"): two or more kept tracks show their
        // total labelled with the count, "2 tracks · 5.4 km · 2h 10m"; one track keeps its plain figure.
        add(EntryStat(RecordType.TRACKS, if (tracks.size >= 2) "${tracks.size} tracks · $total" else total))
    }
    val waypoints = entry.waypointDecisions.count { it.kept }
    if (waypoints > 0) add(EntryStat(RecordType.WAYPOINTS, countLabel(waypoints, "waypoint", "waypoints")))
    val regions = entry.offlineRegionDecisions.count { it.kept }
    if (regions > 0) add(EntryStat(RecordType.OFFLINE_MAPS, countLabel(regions, "offline map", "offline maps")))
}

private fun countLabel(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"

/**
 * Whether [entry] collapses to [CollapsedEntryRow] (plan J5: "no photo, no text and no track").
 * "No photo" means no hero to draw ([hasHero] false: nothing attached, or every attached photo gone
 * from the gallery), since a card with a deleted photo has nothing more to show than one with none;
 * "no track" means no kept track.
 */
internal fun isCollapsedEntry(entry: CartographyEntry, hasHero: Boolean): Boolean =
    !hasHero && entry.text.isBlank() && entry.trackDecisions.none { it.kept }

/**
 * Consecutive runs of [entries] that share a calendar month, in list order. The timeline's list is
 * newest date first (`GetCartographyEntriesUseCase`), so each month is one run; the drafts list is
 * ordered by last update, so a month there can recur as a second run, and gets a second header.
 */
internal fun groupEntriesByMonth(entries: List<CartographyEntry>): List<Pair<YearMonth, List<CartographyEntry>>> {
    val runs = mutableListOf<Pair<YearMonth, MutableList<CartographyEntry>>>()
    for (entry in entries) {
        val month = YearMonth.from(entry.date)
        val last = runs.lastOrNull()
        if (last != null && last.first == month) last.second += entry else runs += month to mutableListOf(entry)
    }
    return runs
}

/**
 * The card's hero photo (J3, C2; owner ruling "Direct photos only (Recommended)"): the earliest
 * *directly attached* photo that still exists. [CartographyEntry.photos] sorted by
 * [com.zynergylabs.forager.app.domain.model.PhotoAttachment.attachedAtEpochMillis] (their stored
 * order is undefined: `getPhotoRefs` has no `ORDER BY`, J0 B2), each looked up in the gallery the
 * screen already holds ([photosById]); the first that resolves is the hero. A photo deleted from the
 * gallery leaves its reference behind, so it is skipped rather than drawn as a blank. None resolves,
 * no hero. Photos of the entry's kept finds are not considered (the owner's ruling; the entry holds
 * no id for them). Ties keep the list's order (a stable sort). No query, column or migration.
 */
internal fun entryHeroPhoto(entry: CartographyEntry, photosById: Map<String, GalleryPhoto>): GalleryPhoto? =
    entry.photos.sortedBy { it.attachedAtEpochMillis }.firstNotNullOfOrNull { photosById[it.photoId] }

/** The hero drawn on top of a card: the existing [DecodedPhoto], full width, a fixed height, cropped. */
@Composable
internal fun EntryHeroPhoto(entryId: String, photo: GalleryPhoto) {
    DecodedPhoto(
        relativePath = photo.photo.relativePath,
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().height(ENTRY_HERO_HEIGHT).testTag(entryHeroTestTag(entryId, photo.photo.id)),
    )
}

private val ENTRY_HERO_HEIGHT = 140.dp

/**
 * The recorded track a card draws as its thumbnail (J3, C3; owner ruling "Join in memory
 * (Recommended)"): the entry's kept track, looked up by id in the track list already loaded
 * (`TrackRecordingUiState.tracks`, passed down; [tracksById]). No database read per card. A track
 * not in the list (deleted, or not loaded yet) gives no thumbnail.
 *
 * **Every kept track, in one box** (journal redesign J4, D5; owner ruling "All in one box
 * (Recommended)", answering the J3 report's open question): an entry keeping two or more tracks draws
 * them all together on one projection. A kept track not in the loaded list is left out and the rest
 * still draw; none found means no thumbnail, as with one track.
 */
internal fun entryThumbnailTracks(entry: CartographyEntry, tracksById: Map<String, Track>): List<Track> =
    entry.trackDecisions.filter { it.kept }.mapNotNull { tracksById[it.trackId] }

/**
 * The entries whose thumbnail needs a saved path (F3, dispatch 2026-09-28-195 item 5; owner, "C: list screen loads
 * lazily"): those with a **kept** track that is not in the loaded list. The list asks for these entries' saved
 * paths and no others, so an entry whose tracks all exist never reads the table, and a withheld track's path is
 * never asked for.
 */
internal fun entriesNeedingSavedPaths(entries: List<CartographyEntry>, tracksById: Map<String, Track>): List<String> =
    entries.filter { entry -> entry.trackDecisions.any { it.kept && it.trackId !in tracksById } }.map { it.id }

/**
 * [entryThumbnailTracks] with the path saved when a kept track was deleted: for each kept decision, in decision
 * order, the live track if it is loaded, else its entry in [savedPaths] (this entry's, by track id), else nothing.
 * The live track wins whenever it exists. A withheld decision draws nothing either way.
 *
 * A saved path becomes a [Track] carrying only what a path holds: its id, the decision's name, and points with the
 * saved lat/lng. **Its timestamps are 0 and its accuracy and altitude null, not measured values**: the thumbnail
 * reads only `id` and each point's lat/lng ([TracksThumbnail]), and nothing that consumes this list may read
 * anything else from a track it draws from a saved path. Rejected: changing [TracksThumbnail] and
 * `projectTracksToBox` to take bare lat/lng, which would touch their own tests for one more caller.
 */
internal fun entryThumbnailTracksOrSaved(entry: CartographyEntry, tracksById: Map<String, Track>, savedPaths: Map<String, List<LatLng>>): List<Track> =
    entry.trackDecisions.filter { it.kept }.mapNotNull { decision ->
        tracksById[decision.trackId] ?: savedPaths[decision.trackId]?.let { path ->
            Track(
                id = decision.trackId,
                name = decision.name,
                startedAtEpochMillis = 0L,
                endedAtEpochMillis = 0L,
                points = path.map { TrackPoint(lat = it.lat, lng = it.lng, altitude = null, accuracyMeters = null, timestampEpochMillis = 0L) },
            )
        }
    }

/** A card's track thumbnail: [TracksThumbnail] in a small square at the card's end, every kept track found drawn in it. */
@Composable
internal fun EntryTrackThumbnail(entryId: String, tracks: List<Track>) {
    TracksThumbnail(
        trackIds = tracks.map { it.id },
        tracks = tracks.map { it.points },
        modifier = Modifier.size(ENTRY_THUMBNAIL_SIZE).testTag(entryTrackThumbnailTestTag(entryId)),
    )
}

private val ENTRY_THUMBNAIL_SIZE = 56.dp

internal fun entryTrackThumbnailTestTag(entryId: String): String = "entry-track-thumbnail-$entryId"

internal fun entryHeroTestTag(entryId: String, photoId: String): String = "entry-hero-$entryId-$photoId"

internal fun entryCardTestTag(entryId: String): String = "entry-card-$entryId"
internal fun entryRowTestTag(entryId: String): String = "entry-row-$entryId"
internal fun entryMonthTestTag(month: YearMonth): String = "entries-month-$month"
