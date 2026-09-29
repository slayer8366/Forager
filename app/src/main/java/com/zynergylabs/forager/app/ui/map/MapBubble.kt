package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.ui.availability.ObservationBubble
import com.zynergylabs.forager.app.ui.availability.launchDirections
import com.zynergylabs.forager.app.ui.availability.rectEdgeIntersection
import com.zynergylabs.forager.app.ui.log.DecodedPhoto
import com.zynergylabs.forager.app.ui.log.PhotoViewerDialog
import com.zynergylabs.forager.app.ui.log.RecordDetailsSheet
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import com.zynergylabs.forager.app.ui.log.RecordDetailsTargetSaver
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlin.math.abs
import kotlin.math.atan2

/**
 * M1's bubble shell and the layer every map host puts over its map (`prompts/preserved/2026-09-28-29.md`
 * B2 to B4, with continuation `2026-09-28-30`, which widened the scope so the shell could be extracted
 * from the sighting bubble and its tail fixed). What each bubble says is `MapBubbles.kt`.
 *
 * - [AnchoredAtScreenPoint] places a bubble so its tail's tip is on the tapped point, moved here from
 *   `AvailabilityMapOverlaysUi.kt` with the one fix the planner ruled: the tip is on the anchor even
 *   when the clamp moves the card, because the tail is drawn from the card's edge to the anchor
 *   itself ([bubblePlacement]) instead of from where the card would have been.
 * - [MapBubbleShell] is the card: the map chrome's 80% fill, the tail, a close button and a content
 *   slot. It swallows taps on itself, so a touch on a bubble never reaches the map beneath.
 *   `ObservationBubble` (the sighting bubble, content unchanged) is one kind of it; [MapFeatureBubble]
 *   is every other kind.
 * - [MapBubbleLayer] is one bubble at a time for one host, with its targets opened in place (owner's
 *   M1 ruling 2, "Open in place"): the J5c details sheet, the photo viewer, Directions, and the find
 *   route the host passes in [MapRecordSources.onOpenFind].
 */

/**
 * The direction a bubble sits from the point it names, measured clockwise from up, in the map's own
 * north-up frame, not screen space (unchanged from the sighting bubble): [AnchoredAtScreenPoint]
 * rotates it by the camera bearing, so up-and-left of the point at bearing 0 stays up-and-left of the
 * point as the map turns.
 */
private const val BUBBLE_BASE_DIRECTION_DEG = 315f

/** How far the tail's tip reaches past the card toward the point, unclamped; also the margin the shell reserves on every side. */
internal val BUBBLE_TAIL_LENGTH = Spacing.md

/** Half the width of the tail's base. */
private val BUBBLE_TAIL_BASE_HALF_WIDTH = Spacing.xs

/**
 * Places [content] so its tail's tip lands on [anchorPx], the tapped point in the map slot's own
 * coordinates, at [BUBBLE_BASE_DIRECTION_DEG] rotated by [bearingDeg], clamped to the map box and no
 * higher than [minY]. [content] gets the tip, in its own coordinates, as a [State] written during
 * placement, and draws its tail to it: so when the clamp moves the card near an edge, the tail moves
 * with the anchor, not with the card (planner's M1 ruling), and the tip still touches the point.
 *
 * A custom [Layout] reporting the whole incoming size, with the child placed freely inside it, for
 * the reason the sighting bubble's placement always had: placing a child outside its parent's own
 * bounds would leave it undependably hit-testable. The layout itself takes no touches; only the
 * shell's card does.
 */
@Composable
internal fun AnchoredAtScreenPoint(
    anchorPx: Offset,
    bearingDeg: Float,
    minY: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable (tipInBubble: State<Offset?>) -> Unit,
) {
    val screenDirectionFromPointDeg = (BUBBLE_BASE_DIRECTION_DEG - bearingDeg).mod(360f)
    val arrowAngleDeg = (screenDirectionFromPointDeg + 180f).mod(360f)
    val tip: MutableState<Offset?> = remember { mutableStateOf(null) }
    Layout(content = { content(tip) }, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(Constraints())
        val placement = bubblePlacement(
            anchor = anchorPx,
            arrowAngleDeg = arrowAngleDeg,
            bubbleWidth = placeable.width,
            bubbleHeight = placeable.height,
            tailPx = BUBBLE_TAIL_LENGTH.toPx(),
            maxWidth = constraints.maxWidth,
            maxHeight = constraints.maxHeight,
            minY = minY.roundToPx(),
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            // Read by the shell's tail at draw time, after this placement.
            tip.value = placement.tipInBubble
            placeable.placeRelative(placement.topLeftX, placement.topLeftY)
        }
    }
}

/**
 * The bubble's card (B2): the same theme-aware 80% fill as every control over the map, a tail from
 * the card's edge to [tipInBubble], [content] beside a close button, at most 280 dp wide. [cardTag]
 * is the card's test tag and [closeTag] the close button's.
 *
 * The card consumes its own taps with a plain `pointerInput` (no ripple, no click action to fake),
 * so a touch anywhere on it is never also the map's "tap elsewhere" dismiss; the buttons inside it
 * take their own taps first.
 */
@Composable
internal fun MapBubbleShell(
    tipInBubble: State<Offset?>,
    onDismiss: () -> Unit,
    cardTag: String,
    closeTag: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val fillColor = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    Box(
        modifier = modifier
            .drawBehind {
                val tip = tipInBubble.value ?: return@drawBehind
                val tailPx = BUBBLE_TAIL_LENGTH.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val toTip = tip - center
                val cardHalfWidth = center.x - tailPx
                val cardHalfHeight = center.y - tailPx
                // A tip inside the card (the clamp pushed the card over the point) needs no tail.
                if (abs(toTip.x) <= cardHalfWidth && abs(toTip.y) <= cardHalfHeight) return@drawBehind
                val angleDeg = Math.toDegrees(atan2(toTip.x.toDouble(), -toTip.y.toDouble())).toFloat()
                val base = center + rectEdgeIntersection(cardHalfWidth, cardHalfHeight, angleDeg)
                val length = (tip - base).getDistance().takeIf { it > 0f } ?: return@drawBehind
                val dir = (tip - base) / length
                val half = BUBBLE_TAIL_BASE_HALF_WIDTH.toPx()
                val base1 = Offset(base.x - dir.y * half, base.y + dir.x * half)
                val base2 = Offset(base.x + dir.y * half, base.y - dir.x * half)
                drawPath(
                    Path().apply {
                        moveTo(base1.x, base1.y)
                        lineTo(tip.x, tip.y)
                        lineTo(base2.x, base2.y)
                        close()
                    },
                    color = fillColor,
                )
            }
            .padding(BUBBLE_TAIL_LENGTH),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag(cardTag)
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(20.dp),
            color = fillColor,
            contentColor = if (isDarkTheme) Color.White else Bark,
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
        ) {
            Row(
                // fillMaxWidth so the Column's weight(1f) has a bounded width to share (the sighting
                // bubble's own finding: without it the text wrapped a character per line).
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp).testTag(closeTag)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * One map host's bubble and its targets (B2 to B4). [tapped] is the host's single tapped thing,
 * sightings included; [onDismiss] clears it. The bubble closes by its close button, by a tap on
 * empty map (the host's `onTap`), by Back while it shows ([backEnabled] off while something above the
 * map owns Back, such as the Tools drawer), and by opening a target.
 *
 * A record no longer in [sources] (a delete landed) and a cell the store no longer has close the
 * bubble with a logged line, never a made-up card. A cell is re-read from [forecast]'s store by group,
 * week and block while its bubble opens ([lookUpForecastCell]), and nothing shows until it is.
 */
@Composable
internal fun MapBubbleLayer(
    tapped: TappedMapThing?,
    onDismiss: () -> Unit,
    sources: MapRecordSources,
    forecast: MapForecastFeed?,
    onViewSightingOnINaturalist: (Sighting) -> Unit,
    modifier: Modifier = Modifier,
    minY: Dp = 0.dp,
    backEnabled: Boolean = true,
) {
    val context = LocalContext.current
    var detailsTarget by rememberSaveable(stateSaver = RecordDetailsTargetSaver) { mutableStateOf<RecordDetailsTarget?>(null) }
    var viewerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = backEnabled && tapped != null) { onDismiss() }

    if (tapped != null) {
        when (val target = tapped.target) {
            is MapBubbleTarget.SightingTarget -> AnchoredAtScreenPoint(tapped.anchorPx, tapped.bearingDeg, minY, modifier.fillMaxSize()) { tip ->
                ObservationBubble(
                    sighting = target.sighting,
                    onViewOnINaturalist = { onViewSightingOnINaturalist(target.sighting) },
                    onDismiss = onDismiss,
                    tipInBubble = tip,
                )
            }

            is MapBubbleTarget.FeatureTarget -> {
                val content: MapBubbleContent? = if (target.kind == MapBubbleKind.FORECAST_CELL) {
                    when (val cell = rememberForecastCell(target, forecast).value) {
                        CellLookup.Loading -> null
                        CellLookup.Missing -> {
                            LaunchedEffect(target) {
                                Log.w(MAP_BUBBLE_LOG_TAG, "No stored cell for ${target.featureId} on ${target.layerId}; its bubble was not shown.")
                                onDismiss()
                            }
                            null
                        }
                        is CellLookup.Found -> forecastCellBubble(
                            COLOUR_FIELDS.firstOrNull { it.layerId == target.layerId }?.label ?: target.layerId,
                            cell.cell,
                        )
                    }
                } else {
                    mapBubbleContentFor(target, sources).also { found ->
                        if (found == null) {
                            LaunchedEffect(target) {
                                Log.w(MAP_BUBBLE_LOG_TAG, "No ${target.kind} ${target.featureId} in the host's lists; its bubble was not shown.")
                                onDismiss()
                            }
                        }
                    }
                }
                if (content != null) {
                    AnchoredAtScreenPoint(tapped.anchorPx, tapped.bearingDeg, minY, modifier.fillMaxSize()) { tip ->
                        MapFeatureBubble(
                            content = content,
                            tipInBubble = tip,
                            onDismiss = onDismiss,
                            openFindLabel = sources.openFindLabel,
                            onOpenFind = sources.onOpenFind?.let { open -> { id: String -> onDismiss(); open(id) } },
                            onViewPhoto = { id -> onDismiss(); viewerPhotoId = id },
                            onDirections = { name, at -> onDismiss(); launchDirections(context, name, at) },
                            onDetails = { details -> onDismiss(); detailsTarget = details },
                            onOpenEntry = sources.onOpenEntry?.let { open -> { id: String -> onDismiss(); open(id) } },
                        )
                    }
                }
            }
        }
    }

    detailsTarget?.let { target ->
        RecordDetailsSheet(
            target = target,
            waypoints = sources.waypoints,
            tracks = sources.tracks,
            offlineRegions = sources.offlineRegions,
            waypointEntryReferenceCounts = sources.waypointEntryReferenceCounts,
            distanceUnit = sources.distanceUnit,
            nowEpochMillis = sources.nowEpochMillis(),
            staleThresholdDays = sources.staleThresholdDays,
            getFullRecord = sources.getFullRecord,
            onDismiss = { detailsTarget = null },
            // Opened from a bubble, which only a map's own Box composes: always over a map.
            overMap = true,
        )
    }
    viewerPhotoId?.let { id ->
        // The photo's own viewer, in place (owner's M1 ruling 2); it closes itself if the photo is gone.
        PhotoViewerDialog(
            photos = sources.galleryPhotos.filter { it.photo.id == id }.map { it.photo },
            initialIndex = 0,
            onDismiss = { viewerPhotoId = null },
        )
    }
}

private sealed interface CellLookup {
    data object Loading : CellLookup
    data object Missing : CellLookup
    data class Found(val cell: ForecastCell) : CellLookup
}

@Composable
private fun rememberForecastCell(target: MapBubbleTarget.FeatureTarget, forecast: MapForecastFeed?): State<CellLookup> =
    produceState<CellLookup>(CellLookup.Loading, target, forecast?.store, forecast?.week, forecast?.groupsByLayer) {
        val group = forecast?.groupsByLayer?.get(target.layerId)
        value = if (forecast == null || group == null) {
            CellLookup.Missing
        } else {
            lookUpForecastCell(forecast.store, group, forecast.week, target.at, target.featureId)?.let { CellLookup.Found(it) } ?: CellLookup.Missing
        }
    }

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
) {
    MapBubbleShell(tipInBubble = tipInBubble, onDismiss = onDismiss, cardTag = MAP_BUBBLE_TAG, closeTag = MAP_BUBBLE_CLOSE_TAG) {
        when (content) {
            is MapBubbleContent.Find -> {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    content.coverPhotoPath?.let { DecodedPhoto(relativePath = it, modifier = Modifier.size(BUBBLE_THUMBNAIL_SIZE), contentDescription = "Cover photo") }
                    Column {
                        BubbleTitle(content.title)
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
                KeptInEntries(content.keptIn, onOpenEntry)
                BubbleActions {
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
 * entry, labelled "Open entry <date>" for TalkBack; past three, one line, "Kept in N journal entries",
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
internal const val MAP_BUBBLE_DETAILS_TAG = "map-bubble-details"
internal const val MAP_BUBBLE_STALE_TAG = "map-bubble-stale"
internal const val MAP_BUBBLE_CHANCE_TAG = "map-bubble-chance"

/** J8: a highlighted record's bubble line for one keeping entry, by its date; a tap opens that entry. */
internal fun mapBubbleEntryLineTag(entryId: String) = "map-bubble-entry-$entryId"

/** J8: the one line, "Kept in N journal entries", that stands for more than three keeping entries. */
internal const val MAP_BUBBLE_ENTRY_COUNT_TAG = "map-bubble-entry-count"

/** J8: the untitled list of those entries' dates, opened from [MAP_BUBBLE_ENTRY_COUNT_TAG]. */
internal const val MAP_BUBBLE_ENTRY_LIST_TAG = "map-bubble-entry-list"
private const val MAP_BUBBLE_LOG_TAG = "MapBubble"
