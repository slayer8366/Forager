package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.referencetrails.ReferenceTrailRecord
import com.zynergylabs.forager.app.ui.map.MapFeatureTap
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.TapHandlerRef
import com.zynergylabs.forager.app.ui.map.fanout.CameraMoveClassifier
import com.zynergylabs.forager.app.ui.map.fanout.CrowdedStackZoom
import com.zynergylabs.forager.app.ui.map.fanout.FakeLooper
import com.zynergylabs.forager.app.ui.map.fanout.FanOutTestScene
import com.zynergylabs.forager.app.ui.map.fanout.FanRect
import com.zynergylabs.forager.app.ui.map.fanout.FanSpace
import com.zynergylabs.forager.app.ui.map.fanout.MapTapHandler
import com.zynergylabs.forager.app.ui.map.fanout.MapTapSinks
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutBackHandler
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutHost
import com.zynergylabs.forager.app.ui.map.fanout.MarkerFanOutState
import com.zynergylabs.forager.app.ui.map.fanout.SceneStackCamera
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.orderedLayers

/**
 * A stub map for the crowded-stack screen tests of dispatch 2026-10-27-07 Amendment 1 (a `MapView` cannot be built under
 * Robolectric), built as `SightingsMap` is: **each composition is a new map**, with its own fan, tap handler, camera move
 * classifier and camera, so a tab change rebuilds it as it does on the phone ([builds] counts them). What outlives the map
 * stands for what outlives it on the phone: the [scene] (the records, which the database keeps, and the camera, which the
 * camera memory restores).
 *
 * Its production lines are the real ones where they can be: the camera is attached to the host through the real
 * [TapHandlerRef] (`MapListeners` sets [TapHandlerRef.stackCamera] as the map is built and clears it as it goes; `SightingsMap`
 * sets [TapHandlerRef.crowdedHost]). The rest are copies: the fan's Back (`SightingsMap`), the open fan's keys for "Open in
 * Journal" (`MapFan.kt`), and the fan reopened on the new map from those keys (`FanReopen.kt`, at the first idle on the phone,
 * here at once).
 */
internal class CrowdedStackStubMap(
    /** Where the stack is drawn, dp of the map. */
    private val spotXDp: Float,
    private val spotYDp: Float,
    /** The fan's room and the camera's view about the spot, dp. */
    private val viewWDp: Float,
    private val viewHDp: Float,
) {
    var scene: FanOutTestScene? = null
        private set

    /** The current map's fan and camera; a rebuilt map has new ones. */
    var fan: MarkerFanOutState? = null
        private set
    val cameras = mutableListOf<SceneStackCamera>()

    /** How many maps have been built. */
    var builds = 0
        private set

    var mapLongPresses = 0
        private set

    val slot: MapSlot = { _, content, renderMode, _, _, onTap, _, _, modifier ->
        val density = LocalDensity.current.density
        val currentOnTap by rememberUpdatedState(onTap)
        val currentOnFeatureTap by rememberUpdatedState(renderMode.onFeatureTap)
        val currentOnCloseBubble by rememberUpdatedState(renderMode.onCloseBubble)
        val bubbleShown = content.focusedFeature != null || content.focusedObservationId != null
        val currentBubbleShown by rememberUpdatedState(bubbleShown)
        val mapFan = remember { MarkerFanOutState().also { fan = it } }
        val probe = scene ?: FanOutTestScene(density, spotXDp * density to spotYDp * density).also { s ->
            scene = s
            s.hidden = { fan?.members?.map { m -> m.key }?.toSet() ?: emptySet() }
        }
        val ref = remember { TapHandlerRef() }
        val sceneCamera = remember {
            builds++
            SceneStackCamera(probe, viewWDp, viewHDp, CameraMoveClassifier(FakeLooper()::post)).also { cameras += it }
        }
        val handler = remember {
            val space = object : FanSpace {
                override fun boundsPx() = FanRect(
                    (spotXDp - viewWDp / 2) * density, (spotYDp - viewHDp / 2) * density,
                    (spotXDp + viewWDp / 2) * density, (spotYDp + viewHDp / 2) * density,
                )
                override fun keepOutsPx(): List<FanRect> = emptyList()
            }
            MapTapHandler(
                fan = mapFan,
                probe = probe,
                drawOrder = { orderedLayers(MAP_LAYER_REGISTRY, MapLayersState.DEFAULT) },
                sinks = object : MapTapSinks {
                    override fun onPlainTap() = currentOnTap()
                    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) = currentOnTap()
                    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap(layerId, featureId, Offset(xPx, yPx), 0f, at))
                    override fun onUnidentifiedFeature(layerId: String) = currentOnTap()
                    override fun onCloseBubble() = currentOnCloseBubble()
                    override fun onReferenceTrailTap(layerId: String, record: ReferenceTrailRecord, xPx: Float, yPx: Float, at: LatLng) =
                        currentOnFeatureTap(MapFeatureTap.forReferenceTrail(layerId, record, Offset(xPx, yPx), 0f, at))
                },
                space = space,
                bubbleOpen = { currentBubbleShown },
                crowded = CrowdedStackZoom(sceneCamera, { ref.crowdedHost }, {}),
            )
        }
        // SightingsMap's lines: the host, and the fan it is told of.
        SideEffect {
            ref.crowdedHost = renderMode.crowdedStacks
            renderMode.crowdedStacks?.fan = mapFan
        }
        // MapListeners' lines: this map's camera is the host's while the map lives.
        DisposableEffect(ref) {
            ref.stackCamera = sceneCamera
            onDispose { ref.stackCamera = null }
        }
        // MapFan.kt's: the open fan's keys, where "Open in Journal" reads them. FanReopen's: the fan reopened on a new map.
        LaunchedEffect(renderMode.returnMemory) {
            val memory = renderMode.returnMemory ?: return@LaunchedEffect
            memory.takeFanKeys()?.let { keys -> handler.openFanFor(keys) }
            snapshotFlow { if (mapFan.isOpen) mapFan.members.map { it.key } else emptyList() }.collect { keys -> memory.openFanKeys = keys }
        }
        MarkerFanOutHost(mapFan)
        MarkerFanOutBackHandler(mapFan, bubbleOpen = bubbleShown, backEnabled = renderMode.backEnabled)
        Box(
            modifier
                .testTag(CROWDED_STUB_MAP_TAG)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { handler.onMapTap(LatLng(0.0, 0.0), it.x, it.y) },
                        onLongPress = { mapLongPresses++ },
                    )
                },
        )
    }
}

internal const val CROWDED_STUB_MAP_TAG = "crowded-stack-stub-map"
