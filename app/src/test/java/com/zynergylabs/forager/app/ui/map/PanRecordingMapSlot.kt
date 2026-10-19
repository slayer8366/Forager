package com.zynergylabs.forager.app.ui.map

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region

/**
 * A stub [MapSlot] for the picker-fixes dispatch (F1 to F3) that stands in for what a finger does
 * to the real map: a real drag on it (`performTouchInput { swipe... }`) fires
 * [MapRenderMode.onUserCameraGesture] when the drag starts, as `SightingsMap` does for
 * `REASON_API_GESTURE`, and `onCameraIdle(`[panTo]`)` when it ends, as the camera settling does.
 *
 * It also records every distinct `region` it is handed, in order ([regions]), which is what the
 * real map's camera effect keys on (`SightingsMap`'s data and camera `LaunchedEffect`): a region
 * that changes after a pan is a camera jump on a device, whatever the pin says.
 *
 * Why a drag and not a button (the older `StubPickerMapSlot`s): a button calls `onCameraIdle`
 * without any gesture, so a picker that must tell a pan from a programmatic move could not be
 * tested with it. The drag reaches the picker only through the [MapSlot] parameters a real map
 * would use.
 *
 * Dispatch 2026-10-18-07 (Forager RECORD -1309): it also records every distinct basemap it is handed
 * ([basemaps]), the `renderMode.basemap` the real map draws, so a picker's basemap is read from what the
 * map was actually given.
 */
internal class PanRecordingMapSlot(private val panTo: LatLng) {
    val regions = mutableListOf<Region>()
    val basemaps = mutableListOf<Basemap>()

    /** Whether it has been handed a night render mode, so a night check knows the night frame was composed. */
    var nightSeen = false

    val slot: MapSlot = { region, _, renderMode, _, _, _, _, onCameraIdle, modifier ->
        val currentGesture by rememberUpdatedState(renderMode.onUserCameraGesture)
        val currentIdle by rememberUpdatedState(onCameraIdle)
        SideEffect { if (regions.lastOrNull() != region) regions += region }
        SideEffect { if (basemaps.lastOrNull() != renderMode.basemap) basemaps += renderMode.basemap }
        SideEffect { if (renderMode.night) nightSeen = true }
        Box(
            modifier
                .fillMaxSize()
                .testTag(PAN_RECORDING_MAP_TAG)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { currentGesture() },
                        onDragEnd = { currentIdle(panTo) },
                    ) { change, _ -> change.consume() }
                },
        )
    }
}

internal const val PAN_RECORDING_MAP_TAG = "pan-recording-map"
