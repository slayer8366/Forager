package com.zynergylabs.forager.app.ui.map

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.zynergylabs.forager.app.domain.JournalEntryHighlights
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.RecordPoint
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.fanout.FanOutHideGate
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The marker fan's effects on the loaded style, in their order: the content fold, the fan's hide gate and its drawing,
 * the open fan's keys for the return memory, the fade of the markers outside it, and the circles' colour. Moved whole
 * from [SightingsMap] by UI split Phase 2.2 (dispatch 2026-10-19-29), called from the place they stood there and keyed
 * exactly as they were. The hide gate is still remembered under the loaded style, here, just ahead of the drawing
 * effect, as it was. Every parameter but [state] is a value [SightingsMap] already had, captured when an effect
 * launches, as before; the drawn layers' latest state is read through [SightingsMapState.layersState], as
 * `currentLayersState` was. The comments inside are the effects' own.
 */
@Composable
internal fun MapFan(
    state: SightingsMapState,
    sightings: List<Sighting>,
    plannedTrips: List<PlannedTrip>,
    waypoints: List<Waypoint>,
    findMarkers: List<RecordPoint>,
    photoMarkers: List<RecordPoint>,
    drawnLayersState: MapLayersState,
    journalHighlights: JournalEntryHighlights,
    focusedObservationId: Long?,
    returnMemory: MapReturnMemory?,
    chromeColour: Int,
    resources: Resources,
) {
    // Layer visibility and opacity (map layers L0a, A3): each native layer's `visibility` layout
    // property and its opacity paint properties set from layersState through layerPaintFor, on the
    // loaded style's own layers — no setStyle, so nothing is rebuilt. Keyed on loadedStyle as well,
    // so a freshly loaded style gets the current state; initializeOverlayLayers has already built
    // each layer with it, so for that case this re-sets the same values.
    // A fanned stack folds only when what the map draws changes a member (a record gone or moved, its layer switched off); a change
    // that leaves its members alone keeps it (intent 2026-09-28-274, "Fold only if members change"), and so does a replaced style
    // (dispatch 2026-09-28-279): the effects below draw the fan's layers, frame, circle colour and fade again on the new one.
    // Not when a bubble opens (focusedObservationId, focusedFeature): tapping a fanned marker keeps the fan up.
    LaunchedEffect(state.loadedStyle, sightings, plannedTrips, waypoints, findMarkers, photoMarkers, drawnLayersState, journalHighlights) {
        state.fanReopen.onContentEffect(state.loadedStyle != null, state.loadedStyle)
    }

    // Draws the fan: pushes the copies and their legs at every step of its progress (fanFrameCollections), and hides the
    // originals of the fanned markers while it is up, but not before the renderer reports the copies drawn: hiding first
    // left a frame or two with neither on screen, the blink at the open's start (dispatch 2026-09-28-369, amendment -371).
    // The order is FanOutHideGate's; the signal is FanOutRenderSignal's. Device-only: see FanOutLayers.kt.
    // The gate outlives a restart of this effect (it is keyed on focusedObservationId too, so tapping a fanned sighting restarts it with
    // the fan up) and is new only with a new style, whose layers start unfiltered: a restart then asks it again for the same members and
    // changes nothing, and the originals stay hidden (the planner's review of a5185a2f).
    val fanHideGate = remember(state.loadedStyle) { FanOutHideGate() }
    LaunchedEffect(state.loadedStyle, state.mapLibreMap, focusedObservationId) {
        val style = state.loadedStyle ?: return@LaunchedEffect
        val map = state.mapLibreMap ?: return@LaunchedEffect
        val density = resources.displayMetrics.density
        val effectScope = this
        val gate = fanHideGate
        var waiting: Job? = null
        var clearing: Job? = null
        var hiddenFor: List<FanMember>? = null
        // True to start with, so a restart that finds the fan folding drops a wait that was still pending (gate.onFold) on its first pass.
        var wasOpen = true
        snapshotFlow { Triple(state.fanOut.members, state.fanOut.progress, state.fanOut.wantOpen) }.collect { (members, progress, wantOpen) ->
            if (hiddenFor !== members) {
                hiddenFor = members
                waiting?.cancel()
                clearing?.cancel()
                val step = gate.onMembers(members, spread = progress > 0f)
                applyFanOutHiding(style, step.hide)
                if (step.reveal.isNotEmpty()) {
                    // A release: the originals are shown again, but the copies stay (they stand on them at progress 0) until the
                    // renderer reports the originals drawn, so the stack is never empty for a frame at the fold's end.
                    clearing = effectScope.launch {
                        clearCopiesWhenOriginalsDrawn(map, state.mapView, step.reveal) {
                            pushFanFrame(style, fanFrameCollections(emptyList(), { fanMemberLatLng(map, it, 0f, density) }, focusedObservationId, 0f, drawOrder = orderedLayers(MAP_LAYER_REGISTRY, state.layersState)))
                        }
                    }
                }
                if (step.awaiting) {
                    waiting = effectScope.launch {
                        hideWhenCopiesDrawn(style, map, state.mapView, gate, members, step.generation) { state.fanOut.progress >= 1f }
                    }
                }
            }
            if (wasOpen && !wantOpen) {
                // Folding: a hide still waiting is dropped, so the originals stay shown until the release shows everything.
                gate.onFold()
                waiting?.cancel()
            }
            wasOpen = wantOpen
            if (members.isEmpty() && clearing?.isActive == true) return@collect // the copies stay until the originals are drawn
            pushFanFrame(
                style,
                fanFrameCollections(
                    members, { fanMemberLatLng(map, it, progress, density) }, focusedObservationId, progress,
                    drawOrder = orderedLayers(MAP_LAYER_REGISTRY, state.layersState),
                ),
            )
        }
    }

    // Item 8: the open fan's member keys, written where "Open in Journal" reads them (a closed fan writes an empty list).
    // A map that is left (a tab switch) writes nothing on the way out, so the last list survives until the tap that read it.
    LaunchedEffect(returnMemory) {
        val memory = returnMemory ?: return@LaunchedEffect
        snapshotFlow { if (state.fanOut.isOpen) state.fanOut.members.map { it.key } else emptyList() }.collect { keys -> memory.openFanKeys = keys }
    }

    // Item 2 (dispatch 2026-09-28-265): while a fan is open the marker icons outside it draw at 80% of
    // what they drew. The one writer of these opacities, so the fade composes with the Layers sheet's
    // state (fanFadedPaint multiplies the resolved paint) and the restore on folding is the state's own
    // value. Device-only: see FanClarity.kt.
    LaunchedEffect(state.loadedStyle, drawnLayersState) {
        val style = state.loadedStyle ?: return@LaunchedEffect
        snapshotFlow { state.fanOut.isOpen }.collect { fanOpen ->
            MAP_LAYER_REGISTRY.forEach { spec -> applyLayerPaint(style, fanFadedPaint(spec, layerPaintFor(spec, drawnLayersState), fanOpen)) }
        }
    }

    // Item 3: the circles' colour is the map chrome's, which follows the app's theme, so a theme change
    // recolours them on the loaded style (the layer was built with the colour current at style load).
    LaunchedEffect(state.loadedStyle, chromeColour) {
        val style = state.loadedStyle ?: return@LaunchedEffect
        if (fanCircleNeedsRecolour(state.fanCircleColour[0], chromeColour)) {
            applyFanCircleStyle(style, chromeColour, resources.displayMetrics.density)
            state.fanCircleColour[0] = chromeColour
        }
    }
}
