package com.zynergylabs.forager.app.ui.map.fanout

import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.ui.map.layers.TapHit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/**
 * A stand-in for the map SDK, owned by these tests: a real Web-Mercator projection at [zoom] (512 dp
 * tiles, the vector-tile convention MapLibre uses) around [centre], so "the same two markers are a
 * stack at one zoom and not at another" is computed, not asserted by hand-placed pixels. Icon extent
 * for a point hit is [ICON_HALF_DP] each way from the anchor. Hidden features (the originals of a
 * fanned stack) are not returned, as the real map does not draw them.
 */
internal class FanOutTestScene(override val density: Float = 2f) : MapProbe {
    data class Placed(val layerId: String, val featureId: String, val lat: Double, val lng: Double, val screenXPx: Float? = null, val screenYPx: Float? = null)

    var zoom = 10.0
    val centre = LatLng(45.0, -122.0)
    val markers = mutableListOf<Placed>()
    var hidden: () -> Set<FanKey> = { emptySet() }

    private val viewportCentre = 500f to 1000f

    private fun worldPx(): Double = 512.0 * density * 2.0.pow(zoom)

    private fun worldY(lat: Double): Double {
        val rad = Math.toRadians(lat)
        return worldPx() * (0.5 - ln(tan(PI / 4 + rad / 2)) / (2 * PI))
    }

    fun xPx(lng: Double): Float = (viewportCentre.first + (lng - centre.lng) / 360.0 * worldPx()).toFloat()

    fun yPx(lat: Double): Float = (viewportCentre.second + (worldY(lat) - worldY(centre.lat))).toFloat()

    fun add(layerId: String, featureId: String, lat: Double = centre.lat, lng: Double = centre.lng) {
        markers += Placed(layerId, featureId, lat, lng)
    }

    /** A marker at a given screen position, px of the map view, whatever the projection says (for layouts read off real chrome). */
    fun addAtScreen(layerId: String, featureId: String, xPx: Float, yPx: Float) {
        markers += Placed(layerId, featureId, centre.lat, centre.lng, xPx, yPx)
    }

    private fun px(p: Placed) = (p.screenXPx ?: xPx(p.lng))

    private fun py(p: Placed) = (p.screenYPx ?: yPx(p.lat))

    private fun visible(layerIds: List<String>) =
        markers.filter { it.layerId in layerIds && FanKey(it.layerId, it.featureId) !in hidden() }

    override fun hitsAt(xPx: Float, yPx: Float, layerIds: List<String>): List<TapHit> =
        hitsInBox(xPx, yPx, 0f, layerIds)

    override fun hitsInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<TapHit> {
        val reach = halfPx + ICON_HALF_DP * density
        return visible(layerIds)
            .filter { abs(px(it) - xPx) <= reach && abs(py(it) - yPx) <= reach }
            .map { TapHit(it.layerId, it.featureId) }
    }

    override fun markersInBox(xPx: Float, yPx: Float, halfPx: Float, layerIds: List<String>): List<ProbedMarker> =
        visible(layerIds)
            .filter { abs(px(it) - xPx) <= halfPx && abs(py(it) - yPx) <= halfPx }
            .map { ProbedMarker(FanKey(it.layerId, it.featureId), it.lat, it.lng, px(it), py(it)) }

    companion object {
        const val ICON_HALF_DP = 12f
    }
}

/** What the host was told, in order, as strings a test can compare. */
internal class RecordingSinks : MapTapSinks {
    val events = mutableListOf<String>()

    override fun onPlainTap() { events += "plain" }

    override fun onSightingTap(observationId: Long?, xPx: Float, yPx: Float) { events += "sighting:$observationId" }

    override fun onFeatureTap(layerId: String, featureId: String, xPx: Float, yPx: Float, at: LatLng) {
        events += "feature:$layerId:$featureId"
    }

    override fun onUnidentifiedFeature(layerId: String) { events += "unidentified:$layerId" }
}
