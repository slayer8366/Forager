package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.ui.motion.MapPopUp
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.ForecastCell
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.availability.ObservationBubble
import com.zynergylabs.forager.app.ui.availability.launchDirections
import com.zynergylabs.forager.app.ui.log.PhotoViewerDialog
import com.zynergylabs.forager.app.ui.log.RecordDetailsSheet
import com.zynergylabs.forager.app.ui.log.RecordDetailsTarget
import com.zynergylabs.forager.app.ui.log.RecordDetailsTargetSaver
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.format.is24HourClock

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
    insetLeft: Dp = 0.dp,
    insetRight: Dp = 0.dp,
    /**
     * Dispatch 2026-09-28-502, Amendment 1: a details sheet to open again, once, because Back ended the
     * waypoint navigation started from it ("Back to where you tapped Navigate"); [onReopenDetailsConsumed]
     * is told once it is open. `null` (the default) asks for nothing.
     */
    reopenDetails: RecordDetailsTarget? = null,
    onReopenDetailsConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    var detailsTarget by rememberSaveable(stateSaver = RecordDetailsTargetSaver) { mutableStateOf<RecordDetailsTarget?>(null) }
    var viewerPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(reopenDetails) {
        reopenDetails?.let {
            detailsTarget = it
            onReopenDetailsConsumed()
        }
    }

    BackHandler(enabled = backEnabled && tapped != null) { onDismiss() }

    // Motion Part 2, item 4 (dispatch 2026-09-28-666, scout M5; the owner, RECORD -651: "Fade and grow", from where it belongs):
    // the bubble fades and grows from the glyph it is about, and shrinks back to it. Item 5 ("Let taps through at once"): from
    // the moment it starts to leave it takes no touch, so a tap on it then reaches the map. It goes on drawing what it showed
    // (`shown`) while it leaves. Another glyph tapped while it is open replaces it in place, as before (scout M6).
    // Amendment 1 (RECORD -672), item 6 (scout M7): what a tapped thing shows is resolved here, before the pop-up, and the
    // pop-up is shown only once there is something to show, so a forecast cell whose read comes late fades and grows when its
    // content arrives instead of appearing at once. A record or cell that is gone still closes the bubble with a logged line.
    val resolved: ShownBubble? = tapped?.let { thing ->
        when (val target = thing.target) {
            is MapBubbleTarget.SightingTarget -> ShownBubble(thing, content = null)
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
                    mapBubbleContentFor(target, sources, is24HourClock()).also { found ->
                        if (found == null) {
                            LaunchedEffect(target) {
                                Log.w(MAP_BUBBLE_LOG_TAG, "No ${target.kind} ${target.featureId} in the host's lists; its bubble was not shown.")
                                onDismiss()
                            }
                        }
                    }
                }
                content?.let { ShownBubble(thing, it) }
            }
        }
    }
    val shown = rememberLastShown(resolved)
    MapPopUp(
        visible = resolved != null,
        pivot = { shown?.tapped?.anchorPx ?: Offset.Zero },
        modifier = modifier.fillMaxSize(),
    ) {
        shown?.let { bubble ->
            when (val target = bubble.tapped.target) {
                is MapBubbleTarget.SightingTarget -> AnchoredAtScreenPoint(bubble.tapped.anchorPx, bubble.tapped.bearingDeg, modifier = Modifier.fillMaxSize(), minY = minY, insetLeft = insetLeft, insetRight = insetRight) { tip ->
                    ObservationBubble(
                        sighting = target.sighting,
                        onViewOnINaturalist = { onViewSightingOnINaturalist(target.sighting) },
                        onDismiss = onDismiss,
                        tipInBubble = tip,
                        unitSystem = UnitSystem.forDistanceUnit(sources.distanceUnit),
                    )
                }

                is MapBubbleTarget.FeatureTarget -> bubble.content?.let { content ->
                    AnchoredAtScreenPoint(bubble.tapped.anchorPx, bubble.tapped.bearingDeg, modifier = Modifier.fillMaxSize(), minY = minY, insetLeft = insetLeft, insetRight = insetRight) { tip ->
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
                            // Dispatch -502: where the bubble was, for Back to open it there again.
                            onNavigate = sources.onNavigateToWaypoint?.let { navigate ->
                                { id: String ->
                                    onDismiss()
                                    navigate(WaypointNavigationOrigin.MapBubble(id, bubble.tapped.anchorPx, bubble.tapped.bearingDeg))
                                }
                            },
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
            // Dispatch -616: a walk's waypoint opens in the sheet's place, and Back from it returns to the walk.
            onOpenDetails = { next -> detailsTarget = next },
            // Opened from a bubble, which only a map's own Box composes: always over a map.
            overMap = true,
            // Dispatch -502: the sheet closes, and Back opens it again (Amendment 1).
            onNavigateToWaypoint = sources.onNavigateToWaypoint?.let { navigate ->
                { id: String ->
                    detailsTarget = null
                    navigate(WaypointNavigationOrigin.MapDetails(id))
                }
            },
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

private const val MAP_BUBBLE_LOG_TAG = "MapBubble"

/** A tapped thing with what its bubble shows, resolved (motion Part 2, Amendment 1, item 6): `content` is null for a sighting. */
private data class ShownBubble(val tapped: TappedMapThing, val content: MapBubbleContent?)
