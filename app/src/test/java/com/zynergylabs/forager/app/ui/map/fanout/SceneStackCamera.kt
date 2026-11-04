package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.EntryMapFrame
import com.zynergylabs.forager.app.domain.model.LatLng
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.tan

/**
 * The crowded-stack zoom's camera over a [FanOutTestScene], owned by these tests: a frame moves the scene's centre and zoom
 * the way MapLibre's fit to bounds does (the bounds, less the frame's padding each side, fill a view [viewWidthDp] by
 * [viewHeightDp] dp, capped at the frame's zoom), so where the markers land after the zoom is computed by the scene's own
 * projection. Each move is marked on [classifier] as `MapStackCamera`'s are, so a test can deliver the move start MapLibre
 * queues after the move and see what it does to the fan.
 */
internal class SceneStackCamera(
    private val scene: FanOutTestScene,
    private val viewWidthDp: Float,
    private val viewHeightDp: Float,
    private val classifier: CameraMoveClassifier,
) : StackCamera {
    val frames = mutableListOf<EntryMapFrame>()
    val restores = mutableListOf<CameraSpot>()

    /** Each restore's [restore] `animate` (dispatch 2026-11-04-02, A6), in order with [restores]. */
    val restoresAnimated = mutableListOf<Boolean>()

    /** A move shown by [showMoveFrom]: from where, to where the zoom's steps had put the camera. */
    data class Ease(val from: CameraSpot, val to: CameraSpot)

    /** Every move [showMoveFrom] was asked to show, in order. */
    val eases = mutableListOf<Ease>()

    /**
     * While `true`, a shown move waits for [finishEase] or [takeOver], as MapLibre's ease runs over 750 ms; `false` (the default,
     * as before A6) it arrives at once, so a test that is not about the move reads the outcome straight after the tap.
     */
    var holdEases = false

    private var pending: Triple<Ease, () -> Unit, () -> Unit>? = null

    /** Whether a held move is under way. */
    val easing: Boolean get() = pending != null

    /** `false` stands for navigating: the camera is not the app's to move. */
    var available = true

    /** The basemap's own zoom limit (`setMaxZoomPreference`): a frame closer than this stops here. */
    var maxZoom = Double.POSITIVE_INFINITY

    override fun spot(): CameraSpot? =
        if (!available) null else CameraSpot(scene.centre.lat, scene.centre.lng, scene.zoom, bearing = 0.0, tilt = 0.0)

    override fun frame(frame: EntryMapFrame): Boolean {
        classifier.markStackZoomMove()
        frames += frame
        when (frame) {
            is EntryMapFrame.SinglePoint -> {
                scene.centre = frame.at
                scene.zoom = min(maxZoom, frame.zoom)
            }
            is EntryMapFrame.Fit -> {
                val b = frame.bounds
                scene.centre = LatLng((b.north + b.south) / 2, (b.east + b.west) / 2)
                // Dp across at zoom 0, then the zoom at which they fill the view less the padding.
                val widthAtZero = (b.east - b.west) / 360.0 * TILE_DP
                val heightAtZero = abs(mercatorY(b.north) - mercatorY(b.south)) * TILE_DP
                val pad = 2.0 * frame.paddingDp
                val byWidth = if (widthAtZero > 0) log2((viewWidthDp - pad) / widthAtZero) else Double.POSITIVE_INFINITY
                val byHeight = if (heightAtZero > 0) log2((viewHeightDp - pad) / heightAtZero) else Double.POSITIVE_INFINITY
                scene.zoom = min(maxZoom, min(frame.maxZoom, min(byWidth, byHeight)))
            }
            // Dispatch 2026-10-30-08: a saved route's fit; a crowded stack never asks for one, so this scene does not model it.
            is EntryMapFrame.FitInsets -> error("A crowded stack's camera never frames a FitInsets: $frame")
        }
        return true
    }

    override fun restore(spot: CameraSpot, animate: Boolean) {
        classifier.markAppMove()
        restores += spot
        restoresAnimated += animate
        scene.centre = LatLng(spot.lat, spot.lng)
        scene.zoom = spot.zoom
    }

    override fun showMoveFrom(from: CameraSpot, onArrived: () -> Unit, onTakenOver: () -> Unit) {
        val to = checkNotNull(spot()) { "a move shown with no camera" }
        val ease = Ease(from, to)
        eases += ease
        // As MapStackCamera: back to the start at once, then the ease, both marked as the zoom's.
        classifier.markStackZoomMove()
        scene.centre = LatLng(from.lat, from.lng)
        scene.zoom = from.zoom
        classifier.markStackZoomMove()
        if (holdEases) {
            pending = Triple(ease, onArrived, onTakenOver)
        } else {
            land(ease)
            onArrived()
        }
    }

    /** The held move lands where the zoom's steps put the camera, and its arrival runs. */
    fun finishEase() {
        val (ease, arrived, _) = checkNotNull(pending) { "no move is under way" }
        pending = null
        land(ease)
        arrived()
    }

    /** The user's touch stops the held move where it is, part way, as MapLibre's ease is cancelled by a gesture. */
    fun takeOver() {
        val (ease, _, takenOver) = checkNotNull(pending) { "no move is under way" }
        pending = null
        scene.centre = LatLng((ease.from.lat + ease.to.lat) / 2, (ease.from.lng + ease.to.lng) / 2)
        scene.zoom = (ease.from.zoom + ease.to.zoom) / 2
        takenOver()
    }

    private fun land(ease: Ease) {
        scene.centre = LatLng(ease.to.lat, ease.to.lng)
        scene.zoom = ease.to.zoom
    }

    private fun mercatorY(lat: Double): Double {
        val rad = Math.toRadians(lat)
        return 0.5 - ln(tan(PI / 4 + rad / 2)) / (2 * PI)
    }

    private companion object {
        /** The scene's tile, in dp: 512, as [FanOutTestScene]'s projection. */
        const val TILE_DP = 512.0
    }
}
