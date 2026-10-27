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

    /** `false` stands for navigating: the camera is not the app's to move. */
    var available = true

    override fun spot(): CameraSpot? =
        if (!available) null else CameraSpot(scene.centre.lat, scene.centre.lng, scene.zoom, bearing = 0.0, tilt = 0.0)

    override fun frame(frame: EntryMapFrame): Boolean {
        classifier.markAppMove(keepsFan = true)
        frames += frame
        when (frame) {
            is EntryMapFrame.SinglePoint -> {
                scene.centre = frame.at
                scene.zoom = frame.zoom
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
                scene.zoom = min(frame.maxZoom, min(byWidth, byHeight))
            }
        }
        return true
    }

    override fun restore(spot: CameraSpot) {
        classifier.markAppMove()
        restores += spot
        scene.centre = LatLng(spot.lat, spot.lng)
        scene.zoom = spot.zoom
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
