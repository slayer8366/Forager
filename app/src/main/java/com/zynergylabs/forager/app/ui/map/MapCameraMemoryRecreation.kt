package com.zynergylabs.forager.app.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import java.io.Serializable
import java.util.UUID

/**
 * RECORD -2192: the Maps tab's [MapCameraMemory], kept across an activity recreation (MainActivity does not handle font-scale or
 * night-mode changes itself, so either recreates it). Three of its fields are kept:
 * - [MapCameraMemory.appliedCameraRequestId] (the owner: "Fix: remember framing (Recommended)", "After the restart the map carries
 *   on following you; nothing else changes"): without it the recreated map took an open saved route's fit (whose id stays the
 *   same while the route shows) as new, ended following and framed the route, and the navigation view saved "follow broken";
 * - [MapCameraMemory.appliedSearchFrameId] (the same defect after a search, under -2190): the search frame's serial survives in
 *   the ViewModel, so without it the recreated map framed the search again (MapDataRefresh.kt:88-105) and ended following;
 * - [MapCameraMemory.saved] (the owner: "Exactly as I left it (Recommended)", "The map remembers its position and zoom across the
 *   restart, so nothing moves. Also applies with no route open"): the recreated map restores it on its first style load, as a map
 *   returning from a tab change does (MapStyleReload.kt:114), target, zoom, bearing, tilt and whether it was following, so
 *   following carries on when it was on.
 * The cover picture, the heading and the crowded stacks stay session-only, lost on a recreation as before. A memory with none of
 * the three set saves nothing, and a fresh memory is made, as before.
 *
 * **Same process only.** Saved instance state also comes back after the process is killed in the background, but the ids it
 * would bring back do not mean anything then: a saved route's id counts its opens in `RoutePlottingViewModel.openCount`, which
 * starts again at 0 in a new process, so the first route opened after a process restore could carry the restored id and not be
 * framed. The saved value therefore carries [PROCESS_TOKEN], and a value saved by another process restores nothing (a fresh
 * memory). The camera goes with the ids, so a process restore opens a fresh map, as a restart does.
 */
@Composable
internal fun rememberMapCameraMemory(): MapCameraMemory = rememberSaveable(saver = MapCameraMemoryAcrossRecreation) { MapCameraMemory() }

/** Made once per process: a value saved with another process's token is from before a process restore. */
private val PROCESS_TOKEN: String = UUID.randomUUID().toString()

internal val MapCameraMemoryAcrossRecreation: Saver<MapCameraMemory, SavedMapCameraMemory> = mapCameraMemorySaver(PROCESS_TOKEN)

/** What crosses the recreation, in plain values, so the saved-state Bundle can hold it. */
internal data class SavedMapCameraMemory(
    val token: String,
    val appliedCameraRequestId: String?,
    val appliedSearchFrameId: Int,
    val camera: SavedCamera?,
) : Serializable

/** [MapCameraSnapshot] in plain values; the applied target's region and focus are null when the snapshot had none. */
internal data class SavedCamera(
    val lat: Double,
    val lng: Double,
    val zoom: Double,
    val bearing: Double,
    val tilt: Double,
    val following: Boolean,
    val appliedRegion: Triple<Double, Double, Int>?,
    val appliedFocus: Pair<Double, Double>?,
) : Serializable

/** The saver for a process whose token is [token]; separate so a test can stand in a value saved by another process. */
internal fun mapCameraMemorySaver(token: String): Saver<MapCameraMemory, SavedMapCameraMemory> = Saver(
    save = { memory ->
        val camera = memory.saved?.toSaved()
        if (memory.appliedCameraRequestId == null && memory.appliedSearchFrameId == 0 && camera == null) {
            null
        } else {
            SavedMapCameraMemory(token, memory.appliedCameraRequestId, memory.appliedSearchFrameId, camera)
        }
    },
    restore = { value ->
        if (value.token != token) {
            null
        } else {
            MapCameraMemory().apply {
                appliedCameraRequestId = value.appliedCameraRequestId
                appliedSearchFrameId = value.appliedSearchFrameId
                saved = value.camera?.toSnapshot()
            }
        }
    },
)

private fun MapCameraSnapshot.toSaved(): SavedCamera = SavedCamera(
    target.lat, target.lng, zoom, bearing, tilt, following,
    appliedRegion = appliedTarget?.first?.let { Triple(it.lat, it.lng, it.radiusKm) },
    appliedFocus = appliedTarget?.second?.let { it.lat to it.lng },
)

private fun SavedCamera.toSnapshot(): MapCameraSnapshot = MapCameraSnapshot(
    target = LatLng(lat, lng),
    zoom = zoom,
    bearing = bearing,
    tilt = tilt,
    following = following,
    appliedTarget = appliedRegion?.let { (regionLat, regionLng, radiusKm) ->
        Region(regionLat, regionLng, radiusKm) to appliedFocus?.let { (focusLat, focusLng) -> LatLng(focusLat, focusLng) }
    },
)
