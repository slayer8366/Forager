package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.geometry.Offset
import com.zynergylabs.forager.app.domain.ForecastBlock
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellsResult
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.DEFAULT_STALE_THRESHOLD_DAYS
import com.zynergylabs.forager.app.domain.HighlightedRecord
import com.zynergylabs.forager.app.domain.HighlightedRecordKind
import com.zynergylabs.forager.app.domain.JournalEntryOnMap
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackDecision
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.ComputeTrackStatisticsUseCase
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.ui.availability.decimalDegreesLabel
import com.zynergylabs.forager.app.ui.availability.offlineRegionSizeLabel
import com.zynergylabs.forager.app.ui.availability.rectEdgeIntersection
import com.zynergylabs.forager.app.ui.log.formatTrackDuration
import com.zynergylabs.forager.app.ui.log.trackDurationLabel
import com.zynergylabs.forager.app.ui.log.journalEntryCountLabel
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.LEGEND_REFERENCE_CLASS
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.legendDatesLine
import com.zynergylabs.forager.app.ui.track.trackTitle
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import com.zynergylabs.forager.app.ui.format.displayDate
import com.zynergylabs.forager.app.ui.format.displayDateTime
import com.zynergylabs.forager.app.ui.log.findTitle
import com.zynergylabs.forager.app.ui.log.findBlankTitleLabel

/**
 * M1, tap a map glyph for a bubble (`prompts/preserved/2026-09-28-29.md`, continuation
 * `2026-09-28-30`; the plan's "M1 rulings"): the plain-data half of the bubbles. What a tap names,
 * the one thing a map's bubble shows, what each kind's bubble says, and how the forecast cell is
 * looked up. Nothing here names a Compose UI node or a MapLibre type (an [Offset] is a value), so it
 * all runs headless (`MapBubblesTest`). The shell that draws a bubble is `MapBubble.kt`.
 */

/** The record kinds a map glyph can be (owner: "Finds and photos, Waypoints, Tracks, Planned trips & offline maps"), plus a forecast cell. */
enum class MapBubbleKind(
    /** Whether the kind is drawn as a point, so its bubble re-anchors on the point on every camera idle (planner's M1 ruling). */
    val isPoint: Boolean,
) {
    FIND(isPoint = true),
    PHOTO(isPoint = true),
    WAYPOINT(isPoint = true),
    PLANNED_TRIP(isPoint = true),
    TRACK(isPoint = false),
    OFFLINE_REGION(isPoint = false),
    FORECAST_CELL(isPoint = false),
}

/** The kind a tap on [layerId] names, or `null` for a layer no bubble is built for. */
fun mapBubbleKindOf(layerId: String): MapBubbleKind? = when (layerId) {
    MapLayerIds.FINDS -> MapBubbleKind.FIND
    MapLayerIds.PHOTOS -> MapBubbleKind.PHOTO
    MapLayerIds.WAYPOINTS -> MapBubbleKind.WAYPOINT
    MapLayerIds.PLANNED_TRIPS -> MapBubbleKind.PLANNED_TRIP
    MapLayerIds.KEPT_TRACKS -> MapBubbleKind.TRACK
    // The outline is what a finger hits; the fill takes no taps (L0a, owner's ruling 4).
    MapLayerIds.OFFLINE_REGION_OUTLINE -> MapBubbleKind.OFFLINE_REGION
    else -> if (COLOUR_FIELDS.any { it.layerId == layerId }) MapBubbleKind.FORECAST_CELL else null
}

/**
 * The one thing a map is showing a bubble for (planner's M1 ruling: one bubble at a time, a single
 * tapped-thing state that includes sightings), with where its tail points: [anchorPx] in the map
 * slot's own coordinates and the camera bearing, as `onSightingTap` and [MapFeatureTap] report them.
 */
data class TappedMapThing(val target: MapBubbleTarget, val anchorPx: Offset, val bearingDeg: Float)

/** What a bubble is about. */
sealed interface MapBubbleTarget {
    data class SightingTarget(val sighting: Sighting) : MapBubbleTarget

    /** A record or a cell, by the layer it was tapped on and its feature id; [at] is the tapped map position. */
    data class FeatureTarget(val kind: MapBubbleKind, val layerId: String, val featureId: String, val at: LatLng) : MapBubbleTarget
}

/** The [TappedMapThing] a feature tap makes, or `null` for a layer no bubble is built for. */
fun tappedThingOf(tap: MapFeatureTap): TappedMapThing? {
    val kind = mapBubbleKindOf(tap.layerId) ?: return null
    return TappedMapThing(MapBubbleTarget.FeatureTarget(kind, tap.layerId, tap.featureId, tap.at), tap.screenPoint, tap.bearingDeg)
}

/** The sighting [MapOverlayContent.focusedObservationId] names while this thing's bubble shows. */
val TappedMapThing?.focusedObservationId: Long?
    get() = ((this?.target) as? MapBubbleTarget.SightingTarget)?.sighting?.observationId

/** The point feature [MapOverlayContent.focusedFeature] names while this thing's bubble shows; `null` for lines, regions and cells, which keep the tap point. */
val TappedMapThing?.focusedFeature: FocusedMapFeature?
    get() = ((this?.target) as? MapBubbleTarget.FeatureTarget)
        ?.takeIf { it.kind.isPoint }
        ?.let { FocusedMapFeature(it.layerId, it.featureId) }

/**
 * Where [focus]'s glyph is now, from the lists the map draws: the position a point bubble is
 * re-anchored on at each camera idle (F2, generalising `focusedObservationId`). `null` when the
 * record is no longer drawn, so the map stops re-anchoring rather than reviving a stale bubble.
 */
fun focusedFeaturePosition(
    focus: FocusedMapFeature,
    plannedTrips: List<PlannedTrip>,
    waypoints: List<Waypoint>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
): LatLng? = when (focus.layerId) {
    MapLayerIds.PLANNED_TRIPS -> plannedTrips.firstOrNull { it.id == focus.featureId }?.location
    MapLayerIds.WAYPOINTS -> waypoints.firstOrNull { it.id == focus.featureId }?.let { LatLng(it.lat, it.lng) }
    MapLayerIds.FINDS -> findMarkers.firstOrNull { it.recordId == focus.featureId }?.at
    MapLayerIds.PHOTOS -> photoMarkers.firstOrNull { it.recordId == focus.featureId }?.at
    else -> null
}

/**
 * Everything a map host looks a tapped record up in, and the J5c details sheet's inputs (B3, B4):
 * the lists the host already holds, not a new read. [onOpenFind] is the find bubble's action,
 * labelled [openFindLabel] ("Open in Journal" on the Maps tab, "Open find" on an entry's map); `null`
 * leaves the find bubble with no action.
 */
data class MapRecordSources(
    val finds: List<MushroomLogEntry> = emptyList(),
    val galleryPhotos: List<GalleryPhoto> = emptyList(),
    /** How many journal entries keep each photo (`MushroomLogUiState.cartographyEntryPhotoReferenceCounts`). */
    val photoEntryReferenceCounts: Map<String, Int> = emptyMap(),
    val waypoints: List<Waypoint> = emptyList(),
    /**
     * An entry map's kept waypoints as the entry snapshotted them (real names, snapshot positions),
     * for a waypoint whose record has since left [waypoints]: its bubble still names it, with no
     * details action (the J5c sheet reads records, and there is none).
     */
    val snapshotWaypoints: List<Waypoint> = emptyList(),
    /**
     * An entry map's kept track decisions, for a track whose record has since left [tracks] (F3, dispatch
     * 2026-09-28-195, item 5: "mirroring `snapshotWaypoints`"). The line is drawn from the path saved at delete
     * time; its bubble is named from the decision's own snapshot (name, distance, duration), with no details
     * action (there is no record to open) and no date (the snapshot never held one).
     */
    val snapshotTracks: List<TrackDecision> = emptyList(),
    /**
     * An entry map's waypoints' distance walked from the start of their own track, in metres, by waypoint id
     * (RECORD -867, the owner: "Add it to the icon's details"; the entry report's waypoint table, which had it,
     * is gone). A waypoint not in it gets no such line; every other map passes none.
     */
    val waypointDistancesFromStart: Map<String, Double> = emptyMap(),
    val waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    val tracks: List<Track> = emptyList(),
    val plannedTrips: List<PlannedTrip> = emptyList(),
    val offlineRegions: List<OfflineRegionSummary> = emptyList(),
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val staleThresholdDays: Int = DEFAULT_STALE_THRESHOLD_DAYS,
    val nowEpochMillis: () -> Long = System::currentTimeMillis,
    val getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    val onOpenFind: ((String) -> Unit)? = null,
    val openFindLabel: String = OPEN_IN_JOURNAL_LABEL,
    /**
     * J8: for each record highlighted on this map, the shown entries that keep it
     * (`JournalEntryHighlights.keptIn`), while the "Journal entries" switch shows the highlights;
     * empty on a map with no highlights (the entry map) and while the switch is off. A bubble names
     * these entries and opens one through [onOpenEntry].
     */
    val journalEntriesKeeping: Map<HighlightedRecord, List<JournalEntryOnMap>> = emptyMap(),
    /** J8: opens a keeping entry in its report (owner's Q1 ruling, "Open in Journal, prompt first"); `null` offers no entry lines. */
    val onOpenEntry: ((String) -> Unit)? = null,
    /**
     * Dispatch 2026-09-28-502: a waypoint's "Navigate", in its bubble and its details sheet, given where it
     * was tapped. Only the Maps tab passes one (the owner: "only Maps, the sheet and Records"); `null`, an
     * entry map's, offers Directions only.
     */
    val onNavigateToWaypoint: ((WaypointNavigationOrigin) -> Unit)? = null,
)

/** The Maps tab's find action (owner's M1 ruling 2). */
const val OPEN_IN_JOURNAL_LABEL = "Open in Journal"

/** An entry map's find action (owner, Q4: "Open find, Back returns"). */
const val OPEN_FIND_LABEL = "Open find"

/** What one bubble says, per kind (B3): each starts from what that record's row, sheet or card already shows, kept short. */
sealed interface MapBubbleContent {
    /** A find: its title by the tile's rule (or none), its date line, and its cover photo (data part D, RECORD -703). */
    data class Find(
        val findId: String,
        /** The tile's title ([findTitle]): the given name, "Found 2:14 PM", or `null` for none (RECORD -703, "Match the tile"). */
        val title: String?,
        /** "Find on Oct 7, 2026", always: the bubble has no day heading above it, as the tile has. */
        val date: String?,
        val coverPhotoPath: String?,
        val keptIn: List<JournalEntryOnMap> = emptyList(),
        /** What a screen reader says for a blank [title], the tile's own label ([findBlankTitleLabel]); `null` when titled. */
        val blankTitleLabel: String? = null,
    ) : MapBubbleContent

    /**
     * A photo: the photo, its date, and what it is attached to; [attachedTo] is `null` when the bubble's
     * keeping-entry lines ([keptIn]) leave that line nothing to say (see `photoAttachmentLine`).
     */
    data class Photo(val photo: LogPhoto, val date: String, val attachedTo: String?, val keptIn: List<JournalEntryOnMap> = emptyList()) : MapBubbleContent

    /** A waypoint: its name and MGRS; Directions, and details while its record exists ([hasDetails]). */
    data class WaypointContent(
        val waypoint: Waypoint,
        val mgrs: String?,
        val hasDetails: Boolean = true,
        val keptIn: List<JournalEntryOnMap> = emptyList(),
        /** "0.3 mi walked from the start" on an entry map (RECORD -867); `null` everywhere else and when it is not known. */
        val fromStart: String? = null,
    ) : MapBubbleContent

    /** A track: its title, date, distance and duration; details. */
    data class TrackContent(val trackId: String, val title: String, val date: String?, val distance: String, val duration: String, val hasDetails: Boolean = true, val keptIn: List<JournalEntryOnMap> = emptyList()) : MapBubbleContent

    /** A planned trip: what its Trip Planner row shows; Directions. */
    data class Trip(val trip: PlannedTrip, val date: String, val mgrs: String?, val coordinates: String) : MapBubbleContent

    /** An offline region: its name, radius, size, and whether it is stale; details. */
    data class Region(val regionId: Long, val name: String, val radius: String, val size: String, val stale: Boolean, val keptIn: List<JournalEntryOnMap> = emptyList()) : MapBubbleContent

    /**
     * A forecast cell (the L0b forecast-facing rulings: chance, uncertainty, top drivers and data
     * dates, beside the reference class). [layerName] is the layer's own name, so a synthetic layer
     * is never labelled with the fixed term for the real number.
     */
    data class Cell(
        val layerName: String,
        val chance: String,
        val range: String,
        val drivers: List<String>,
        val dates: String,
        val referenceClass: String,
    ) : MapBubbleContent
}

/**
 * What [target]'s bubble says, looked up by its feature id in [sources]; `null` when the record is
 * not there any more (a delete landed, a list reloaded), which the host treats as nothing to show.
 * Not for cells, which are looked up in the store ([lookUpForecastCell]).
 */
fun mapBubbleContentFor(target: MapBubbleTarget.FeatureTarget, sources: MapRecordSources, is24HourClock: Boolean): MapBubbleContent? {
    val id = target.featureId
    // J8: the shown entries keeping this record, when it is highlighted; empty otherwise.
    fun keptIn(kind: HighlightedRecordKind) = sources.journalEntriesKeeping[HighlightedRecord(kind, id)].orEmpty()
    return when (target.kind) {
        MapBubbleKind.FIND -> sources.finds.firstOrNull { it.id == id }?.let { find ->
            // RECORD -703, "Match the tile": the tile's title rule, blank included; the date line is always there.
            val title = findTitle(find, is24HourClock)
            MapBubbleContent.Find(
                findId = find.id,
                title = title,
                date = findDateLabel(find),
                coverPhotoPath = find.photos.firstOrNull()?.relativePath,
                keptIn = keptIn(HighlightedRecordKind.FIND),
                blankTitleLabel = if (title == null) findBlankTitleLabel(find) else null,
            )
        }
        MapBubbleKind.PHOTO -> sources.galleryPhotos.firstOrNull { it.photo.id == id }?.let { gallery ->
            val keepingEntries = keptIn(HighlightedRecordKind.PHOTO)
            MapBubbleContent.Photo(
                photo = gallery.photo,
                date = gallery.photo.createdAtEpochMillis?.let { displayDateTime(it, is24HourClock) } ?: PHOTO_DATE_UNKNOWN,
                attachedTo = photoAttachmentLine(gallery, sources, keepingEntriesShown = keepingEntries.isNotEmpty()),
                keptIn = keepingEntries,
            )
        }
        MapBubbleKind.WAYPOINT -> {
            val fromStart = sources.waypointDistancesFromStart[id]?.let { waypointFromStartLine(it, sources.distanceUnit) }
            sources.waypoints.firstOrNull { it.id == id }
                ?.let { waypoint -> MapBubbleContent.WaypointContent(waypoint, mgrsOf(LatLng(waypoint.lat, waypoint.lng)), keptIn = keptIn(HighlightedRecordKind.WAYPOINT), fromStart = fromStart) }
                ?: sources.snapshotWaypoints.firstOrNull { it.id == id }
                    ?.let { kept -> MapBubbleContent.WaypointContent(kept, mgrsOf(LatLng(kept.lat, kept.lng)), hasDetails = false, fromStart = fromStart) }
        }
        MapBubbleKind.PLANNED_TRIP -> sources.plannedTrips.firstOrNull { it.id == id }?.let { trip ->
            // The Trip Planner row's lines (AvailabilityTripsWaypointsUi's PlannedTripRow): name,
            // date, MGRS when there is one, decimal degrees.
            MapBubbleContent.Trip(
                trip = trip,
                date = displayDate(trip.date),
                mgrs = mgrsOf(trip.location),
                coordinates = decimalDegreesLabel(trip.location.lat, trip.location.lng),
            )
        }
        MapBubbleKind.TRACK -> (sources.tracks.firstOrNull { it.id == id }?.let { track ->
            // The J5c sheet's derivation (RecordDetailsSheet's TrackDetails), from the points in memory.
            val stats = ComputeTrackStatisticsUseCase()(track.points)
            MapBubbleContent.TrackContent(
                trackId = track.id,
                title = trackTitle(track, is24HourClock),
                date = displayDateTime(track.startedAtEpochMillis, is24HourClock),
                distance = formatDistanceMeters(stats.distanceMeters, sources.distanceUnit),
                // Plan T16: "No times in file" for an imported track whose file had none.
                duration = trackDurationLabel(track, stats.durationMillis),
                keptIn = keptIn(HighlightedRecordKind.TRACK),
            )
        } ?: sources.snapshotTracks.firstOrNull { it.trackId == id }?.let { kept ->
            MapBubbleContent.TrackContent(
                trackId = kept.trackId,
                // The entry report's own title for an unnamed kept track (CartographyEntryReportScreen, "Recorded track").
                title = kept.name ?: "Recorded track",
                date = null,
                distance = formatDistanceMeters(kept.distanceMeters, sources.distanceUnit),
                duration = formatTrackDuration(kept.durationMillis),
                hasDetails = false,
            )
        })
        MapBubbleKind.OFFLINE_REGION -> id.toLongOrNull()?.let { regionId -> sources.offlineRegions.firstOrNull { it.id == regionId } }?.let { region ->
            MapBubbleContent.Region(
                regionId = region.id,
                name = region.name,
                radius = formatDistanceKm(region.region.radiusKm, sources.distanceUnit),
                size = offlineRegionSizeLabel(region),
                stale = isOfflineRegionStale(region.createdAtEpochMillis, sources.nowEpochMillis(), sources.staleThresholdDays),
                keptIn = keptIn(HighlightedRecordKind.OFFLINE_REGION),
            )
        }
        MapBubbleKind.FORECAST_CELL -> null
    }
}

/** A waypoint's distance from the start as its entry-map bubble writes it (RECORD -867): "0.3 mi walked from the start". */
internal fun waypointFromStartLine(meters: Double, distanceUnit: DistanceUnit): String =
    "${formatDistanceMeters(meters, distanceUnit)} walked from the start"

/** A find's date as its gallery tile captions it ("Find on <date>", `FindsGalleryScreen`). */
private fun findDateLabel(find: MushroomLogEntry): String = "Find on ${displayDate(find.foundOn)}"

/** No line rather than a wrong one, as the rows do (`MgrsCoordinate`'s doc comment). */
private fun mgrsOf(location: LatLng): String? = (MgrsConverter.convert(location) as? MgrsCoordinate.Grid)?.value

/**
 * What a photo is attached to, from the facts the album's two badges read: the saved finds that use
 * it ([GalleryPhoto.referencingEntryIds], kept to the finds in [MapRecordSources.finds], so a draft
 * find does not count, as the find badge does not) and how many journal entries keep it.
 *
 * While the bubble shows J8's keeping-entry lines ([keepingEntriesShown]), the line leaves out its
 * "In N journal entries" part (it said "Kept in" until the owner's RECORD -671), so it is said once, by J8's lines (dispatch `2026-09-28-70`; the owner:
 * "Option B"). The two counts differ: this one is every entry keeping the photo, J8's only the entries
 * shown on the map. With nothing else left the line is left out (`null`), so a photo a shown entry
 * keeps is never called "Not in a find or a journal entry".
 */
private fun photoAttachmentLine(gallery: GalleryPhoto, sources: MapRecordSources, keepingEntriesShown: Boolean): String? {
    val finds = gallery.referencingEntryIds.mapNotNull { id -> sources.finds.firstOrNull { it.id == id } }
    val entries = sources.photoEntryReferenceCounts[gallery.photo.id] ?: 0
    // The owner, RECORD -673 ("'Chanterelle · 2 journal entries' (Recommended)"): a line naming both a
    // find and journal entries says "In" for neither; a line naming only one keeps its "In".
    val parts = buildList {
        when (finds.size) {
            0 -> Unit
            1 -> add(finds.single().ownIdentification?.takeIf { it.isNotBlank() } ?: findDateLabel(finds.single()))
            else -> add("${finds.size} finds")
        }
        if (entries > 0 && !keepingEntriesShown) add(journalEntryCountLabel(entries))
    }
    return when {
        parts.size == 1 -> "In ${parts.single()}"
        parts.isNotEmpty() -> parts.joinToString(" · ")
        keepingEntriesShown -> null
        else -> PHOTO_NOT_ATTACHED
    }
}

private const val PHOTO_DATE_UNKNOWN = "Date unknown"
private const val PHOTO_NOT_ATTACHED = "Not in a find or a journal entry"

/** A forecast cell's bubble, from the stored cell and its layer's name. */
fun forecastCellBubble(layerName: String, cell: ForecastCell): MapBubbleContent.Cell = MapBubbleContent.Cell(
    layerName = layerName,
    // A percent is shown only on a chance layer (R8); every colour field today is one (ColourFieldSpec).
    chance = percent(cell.chance),
    range = "Uncertainty ${percent(cell.uncertaintyLow)} to ${percent(cell.uncertaintyHigh)}",
    drivers = cell.drivers.map { "${it.label}: ${it.value}" },
    dates = legendDatesLine(ForecastCellsShown(cell.week, cell.weatherThrough)),
    referenceClass = LEGEND_REFERENCE_CLASS,
)

private fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"

/**
 * The stored cell a tap on a colour field named, re-read from [store] by [group], [week] and block
 * (planner's M1 ruling: re-queried, not parsed from the id). The blocks asked for are every block a
 * cell under [at] can belong to; the cell is the one whose feature id is [featureId]. `null` when
 * the store no longer has it.
 */
suspend fun lookUpForecastCell(
    store: ForecastCellStore,
    group: String,
    week: LocalDate,
    at: LatLng,
    featureId: String,
): ForecastCell? {
    // A cell under [at] has its centre within half a cell of it, so its block is one of the blocks
    // that box touches: up to four near a block corner (a cell belongs to the block holding its
    // centre, and the centre can be across a whole-degree line from the finger).
    val blocks = ForecastBlock.touching(
        south = at.lat - CELL_HALF_DEGREES,
        west = at.lng - CELL_HALF_DEGREES,
        north = at.lat + CELL_HALF_DEGREES,
        east = at.lng + CELL_HALF_DEGREES,
    )
    return when (val result = store.cells(group, week, blocks)) {
        ForecastCellsResult.NoForecastData -> null
        is ForecastCellsResult.Cells -> result.cells.firstOrNull { it.applicable && forecastCellFeatureId(it) == featureId }
    }
}

private const val CELL_HALF_DEGREES = 0.05

/** The feature id a cell's map feature carries: the one place it is written ([forecastCellsFeatureCollection]) and matched ([lookUpForecastCell]). */
internal fun forecastCellFeatureId(cell: ForecastCell): String = "${cell.centre.lat},${cell.centre.lng}"

/**
 * Where a bubble goes and where its tail's tip is (B2, planner's M1 ruling: the tail's tip always
 * lands on the tapped point; when the clamp moves the card, the tail moves with the anchor, not
 * with the card). [topLeft] is the bubble's placed corner in the map box; [tipInBubble] is the
 * anchor in the bubble's own coordinates, so `topLeft + tipInBubble == anchor` wherever the clamp
 * put the card.
 */
data class BubblePlacement(val topLeftX: Int, val topLeftY: Int, val tipInBubble: Offset)

/**
 * Places a bubble of [bubbleWidth] by [bubbleHeight] px (the card plus [tailPx] of margin on every
 * side) so that, unclamped, its tail of [tailPx] ends on [anchor] along [arrowAngleDeg] (screen
 * space, clockwise from up, pointing from the card back to the anchor); then clamps it into the box
 * [maxWidth] by [maxHeight], no higher than [minY] and no further left than [minX] ([maxWidth] is the
 * box's right limit, so a right-hand inset is passed by lowering it). The tip stays on [anchor] either way. A card that
 * does not fit above its glyph opens below it when it fits there (amendment -262, "Flip below").
 */
fun bubblePlacement(
    anchor: Offset,
    arrowAngleDeg: Float,
    bubbleWidth: Int,
    bubbleHeight: Int,
    tailPx: Float,
    maxWidth: Int,
    maxHeight: Int,
    minY: Int,
    minX: Int = 0,
): BubblePlacement {
    // The card's own half-extents: the placeable less the tail's margin on every side (the shell's
    // padding), so the tail's base sits on the visible card's edge.
    val halfWidth = bubbleWidth / 2f
    val halfHeight = bubbleHeight / 2f
    /** The card's unclamped top-left along [angleDeg]: where it would sit if the box did not limit it. */
    fun idealTopLeft(angleDeg: Float): Offset {
        val edge = rectEdgeIntersection(halfWidth - tailPx, halfHeight - tailPx, angleDeg)
        val radians = Math.toRadians(angleDeg.toDouble())
        val tipFromCentre = Offset(edge.x + sin(radians).toFloat() * tailPx, edge.y - cos(radians).toFloat() * tailPx)
        val idealCentre = anchor - tipFromCentre
        return Offset(idealCentre.x - halfWidth, idealCentre.y - halfHeight)
    }
    var ideal = idealTopLeft(arrowAngleDeg)
    // Flip below (dispatch 2026-09-29-57, amendment -262, the owner's "Flip below"): a card whose top would be
    // above [minY] does not fit above its glyph, and clamped down it would sit over the glyph it names. It opens
    // below instead, the arrow mirrored top to bottom so the tail points up from the card's top edge to the glyph
    // (this supersedes M1's "no tail when the clamp puts the card over the point" where below fits). Where it fits
    // neither above nor below, M1's clamp stands.
    if (ideal.y < minY) {
        val below = idealTopLeft((180f - arrowAngleDeg).mod(360f))
        if (below.y >= minY && below.y + bubbleHeight <= maxHeight) ideal = below
    }
    val x = ideal.x.roundToInt().coerceIn(minX, (maxWidth - bubbleWidth).coerceAtLeast(minX))
    val y = ideal.y.roundToInt().coerceIn(minY, (maxHeight - bubbleHeight).coerceAtLeast(minY))
    // The tip is the anchor itself, in the bubble's coordinates: wherever the clamp put the card, the
    // tail is drawn from the card's edge to here (planner's M1 ruling).
    return BubblePlacement(x, y, Offset(anchor.x - x, anchor.y - y))
}
