package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.zynergylabs.forager.app.R
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.domain.route.RouteStop
import com.zynergylabs.forager.app.ui.map.fanout.FanMember
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.MapLayerIds
import com.zynergylabs.forager.app.ui.map.layers.MapLayersState
import com.zynergylabs.forager.app.ui.map.layers.layerPaintFor
import com.zynergylabs.forager.app.ui.theme.MapPalette
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import kotlin.math.ceil

/*
 * Waypoint names on the map (dispatch 2026-11-02-04; RECORD -1964, -1965, -1974). The owner: "have waypoints be labeled on
 * the map", then "Option 1 plus an off switch": every name by default, crowded ones hidden until you zoom in, the route's
 * stops always named, and a setting to turn them off.
 *
 * Each name is a small bitmap drawn in the app's own font (Noto Sans Medium, as `Typography.kt`'s family) and registered
 * with `addImage`, the owner's "As small pictures (Recommended)" (-1974). No app layer draws text and no style the app
 * loads declares glyphs (`BasemapStyles.kt`, the 2026-10-18-04 "remove the fonts requests"; the offline style can carry none,
 * `MapLibreOfflineMapRepository.kt`), so a text-field would draw nothing on most maps. An icon still goes through MapLibre's
 * collision, so "only the ones that fit show; zooming in shows more" is the map's own placement, as CalTopo's
 * "Non-Overlapping" (-1964).
 *
 * Two layers, outside MAP_LAYER_REGISTRY as the fan's and the routes' are, and taking no taps:
 *  - [WaypointNameIds.LAYER], directly above the waypoint pins, no overlap: a name that would collide with one already
 *    placed is not drawn. Its features carry the waypoint's own [FEATURE_ID_PROPERTY], so the fan's hiding filter
 *    (`applyFanOutHiding`) hides a fanned pin's name with its pin; its features are pushed only while the waypoints layer
 *    itself is drawn and the setting is on ([waypointNamesDrawn]).
 *  - [WaypointNameIds.STOP_LAYER], the route's stops (plotting, or a saved route open on its card), above it, overlap
 *    allowed, so a stop's name always draws. MapLibre places the higher layer first and a symbol that allows overlap still
 *    takes its place in the collision index, so what yields to a stop's name is any waypoint name it would cover,
 *    the stop's own waypoint name included (the same picture at the same place), which is how a stop is not named twice.
 *
 * Names are map content, not chrome (the planner's call on A3): chrome drawn over them at 80% meets "nothing fully obstructs
 * the map". Device-only from here on: `Style` and its layers are native, so what the tests reach is what each source is
 * given ([nameLabelFeatures]), the layers' properties ([nameLayerProperties]) and the painted bitmap's size.
 */

internal object WaypointNameIds {
    const val SOURCE = "waypoint-names"
    const val LAYER = "waypoint-names-layer"
    const val STOP_SOURCE = "route-stop-names"
    const val STOP_LAYER = "route-stop-names-layer"

    /** The feature property naming the bitmap a name draws. */
    const val IMAGE_PROPERTY = "nameImage"

    /** Every name image's id starts with this, then the name: two waypoints of one name share one picture. */
    const val IMAGE_PREFIX = "waypoint-name:"
}

/** One name on the map: the text drawn, where, and the waypoint it names (`null` for a route stop, which keeps no id). */
internal data class NameLabel(val featureId: String?, val text: String, val lat: Double, val lng: Double) {
    val imageId: String get() = WaypointNameIds.IMAGE_PREFIX + text
}

/** The name as drawn: the user's own, with surrounding spaces dropped; `null` for a blank one, which gets no label. */
private fun drawnName(name: String): String? = name.trim().takeIf { it.isNotEmpty() }

/** Every waypoint's name label, in order, blank names left out; none at all when [drawn] is false ([waypointNamesDrawn]). */
internal fun waypointNameLabels(waypoints: List<Waypoint>, drawn: Boolean): List<NameLabel> =
    if (!drawn) emptyList() else waypoints.mapNotNull { w -> drawnName(w.name)?.let { NameLabel(w.id, it, w.lat, w.lng) } }

/** The route's stops' name labels, from [RouteStop.name], blank names left out. */
internal fun routeStopNameLabels(stops: List<RouteStop>): List<NameLabel> =
    stops.mapNotNull { s -> drawnName(s.name)?.let { NameLabel(null, it, s.position.lat, s.position.lng) } }

/**
 * Whether waypoint names are drawn: the setting ([MapOverlayContent.waypointNames]) and the waypoints layer itself drawn
 * under [layersState] (the Layers sheet's Waypoints switch), so a name is never drawn beside a pin that is not.
 */
internal fun waypointNamesDrawn(setting: Boolean, layersState: MapLayersState): Boolean {
    if (!setting) return false
    val spec = MAP_LAYER_REGISTRY.firstOrNull { it.id == MapLayerIds.WAYPOINTS } ?: return false
    return layerPaintFor(spec, layersState).visible
}

/** What a name source receives for [labels]: a point each, with its image and, for a waypoint, its id. */
internal fun nameLabelFeatures(labels: List<NameLabel>): FeatureCollection = FeatureCollection.fromFeatures(
    labels.map { label ->
        Feature.fromGeometry(Point.fromLngLat(label.lng, label.lat)).apply {
            label.featureId?.let { addStringProperty(FEATURE_ID_PROPERTY, it) }
            addStringProperty(WaypointNameIds.IMAGE_PROPERTY, label.imageId)
        }
    },
)

/**
 * Where a name sits: its left edge [NAME_GAP_DP] right of the pin's body ([MarkerGlyph.WAYPOINT] is 22 dp wide, centred on
 * its tip), its middle level with the middle of the pin's body (28 dp tall, above its tip). In dp, as `icon-offset` takes it.
 */
internal val NAME_OFFSET_DP: List<Float> = listOf(MarkerGlyph.WAYPOINT.widthDp / 2 + NAME_GAP_DP, -MarkerGlyph.WAYPOINT.heightDp / 2)
private const val NAME_GAP_DP = 2f

/**
 * A name layer's properties, a function so a headless test reads them (a `SymbolLayer` cannot be built under Robolectric, a
 * `PropertyValue` can). [allowOverlap]: false for both since A5 (a name that would collide is not drawn); the stops
 * still beat waypoint names by their layer's place above them (stopNameLayerProperties). Neither ignores placement, so each still takes its room from the names placed after it.
 */
internal fun nameLayerProperties(allowOverlap: Boolean): Array<PropertyValue<*>> = arrayOf(
    PropertyFactory.iconImage(Expression.get(WaypointNameIds.IMAGE_PROPERTY)),
    PropertyFactory.iconAllowOverlap(allowOverlap),
    PropertyFactory.iconIgnorePlacement(false),
    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_LEFT),
    PropertyFactory.iconOffset(NAME_OFFSET_DP.toTypedArray()),
)

/** The waypoint names' layer: a name that would collide with one placed before it is not drawn. */
internal fun waypointNameLayerProperties(): Array<PropertyValue<*>> = nameLayerProperties(allowOverlap = false)

/**
 * The route's stops' layer. A5 (dispatch 2026-11-02-04, RECORD -1979; the owner: "Stops avoid each other (Recommended)", whose option
 * read "Stop names still always beat ordinary waypoint names, but two stop names don't print over each other: the one that would
 * collide hides until you zoom in, like waypoint names."): no overlap. It sits above the waypoint names' layer, and MapLibre places
 * symbol layers from the top down, so the stops still take their room first and the waypoint names yield to them.
 */
internal fun stopNameLayerProperties(): Array<PropertyValue<*>> = nameLayerProperties(allowOverlap = false)

/** Adds both name sources and layers, empty: waypoint names directly above [aboveLayerId] (the pins), the stops' above them. */
internal fun addWaypointNameLayers(style: Style, aboveLayerId: String) {
    val empty = FeatureCollection.fromFeatures(emptyList())
    style.addSource(GeoJsonSource(WaypointNameIds.SOURCE, empty))
    style.addSource(GeoJsonSource(WaypointNameIds.STOP_SOURCE, empty))
    style.addLayerAbove(SymbolLayer(WaypointNameIds.LAYER, WaypointNameIds.SOURCE).withProperties(*waypointNameLayerProperties()), aboveLayerId)
    style.addLayerAbove(SymbolLayer(WaypointNameIds.STOP_LAYER, WaypointNameIds.STOP_SOURCE).withProperties(*stopNameLayerProperties()), WaypointNameIds.LAYER)
}

/** A name's text size and the most of its width drawn before it ends in an ellipsis, in dp; its halo's width each side. */
internal const val NAME_TEXT_DP = 12f
internal const val NAME_MAX_WIDTH_DP = 160f
internal const val NAME_HALO_DP = 1.5f

/**
 * Draws a name as a bitmap: the text in [MapPalette.waypoint] over a halo in [MapPalette.casing] (white by day, black at
 * night, the outline every marker carries), in device pixels as the marker glyphs are, so it draws at its dp size.
 */
internal class NameLabelPainter(private val typeface: Typeface, private val density: Float, val palette: MapPalette) {
    // Each dp figure is in the painter's own density, named in full: inside this `apply`, a bare `density` is TextPaint's own
    // public field (1 by default), which drew every name at a third of its size at 3x (CI run 41556900171).
    private fun paint(colour: Int, halo: Boolean) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = this@NameLabelPainter.typeface
        textSize = NAME_TEXT_DP * this@NameLabelPainter.density
        color = colour
        if (halo) {
            style = Paint.Style.STROKE
            strokeWidth = 2 * NAME_HALO_DP * this@NameLabelPainter.density
            strokeJoin = Paint.Join.ROUND
        }
    }

    /** [text] as drawn, cut with an ellipsis at [NAME_MAX_WIDTH_DP]. */
    fun fitted(text: String): String =
        TextUtils.ellipsize(text, paint(palette.waypoint, halo = false), NAME_MAX_WIDTH_DP * density, TextUtils.TruncateAt.END).toString()

    fun bitmap(text: String): Bitmap {
        val fill = paint(palette.waypoint, halo = false)
        val halo = paint(palette.casing, halo = true)
        val shown = fitted(text)
        val pad = NAME_HALO_DP * density
        val metrics = fill.fontMetrics
        val width = ceil(fill.measureText(shown) + 2 * pad).toInt().coerceAtLeast(1)
        val height = ceil(metrics.descent - metrics.ascent + 2 * pad).toInt().coerceAtLeast(1)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val baseline = pad - metrics.ascent
        canvas.drawText(shown, pad, baseline, halo)
        canvas.drawText(shown, pad, baseline, fill)
        return bitmap
    }
}

/** The app's own font for a name, Noto Sans Medium; a font that cannot be read is logged and the platform's default drawn. */
internal fun nameLabelTypeface(context: Context): Typeface =
    runCatching { ResourcesCompat.getFont(context, R.font.notosans_medium) }.fold(
        onSuccess = { it ?: Typeface.DEFAULT.also { Log.w(SIGHTINGS_MAP_TAG, "Noto Sans Medium read as null; names drawn in the default font.") } },
        onFailure = { error -> Typeface.DEFAULT.also { Log.w(SIGHTINGS_MAP_TAG, "Noto Sans Medium could not be read; names drawn in the default font.", error) } },
    )

/**
 * The name images a style holds, so each is registered once per style and palette, not at every push (a second
 * registration of one image is what moved the fan's glyph sampling, `fanCircleNeedsRecolour`). A new style, which starts
 * with no images, or a new palette starts it again.
 */
internal class NameImageRegistry {
    private var style: Any? = null
    private var palette: MapPalette? = null
    private val ids = mutableSetOf<String>()

    /** The labels of [labels] whose image is not yet registered on [style] (compared by identity) in [palette], now counted as registered. */
    fun toRegister(style: Any, palette: MapPalette, labels: List<NameLabel>): List<NameLabel> {
        if (style !== this.style || palette != this.palette) {
            this.style = style
            this.palette = palette
            ids.clear()
        }
        return labels.distinctBy { it.imageId }.filter { ids.add(it.imageId) }
    }
}

/** The waypoint ids whose names a fan of [members] hides: the fanned waypoints', as their pins are hidden (`applyFanOutHiding`). */
internal fun fanHiddenNameIds(members: List<FanMember>): List<String> =
    members.filter { it.key.layerId == MapLayerIds.WAYPOINTS }.map { it.key.featureId }

/** Pushes both name sources to the current style, registering any image not yet there; nothing in a style change (logged). */
internal fun nameLabelsOnCurrentStyle(
    loaded: Style?,
    waypointLabels: List<NameLabel>,
    stopLabels: List<NameLabel>,
    painter: NameLabelPainter,
    registry: NameImageRegistry,
) {
    val style = currentStyle(loaded, "The waypoint names") ?: return
    registry.toRegister(style, painter.palette, waypointLabels + stopLabels).forEach { style.addImage(it.imageId, painter.bitmap(it.text)) }
    listOf(WaypointNameIds.SOURCE to waypointLabels, WaypointNameIds.STOP_SOURCE to stopLabels).forEach { (id, labels) ->
        val source = style.getSourceAs<GeoJsonSource>(id)
        if (source == null) Log.w(SIGHTINGS_MAP_TAG, "The name source $id is missing; its names are not drawn.") else source.setGeoJson(nameLabelFeatures(labels))
    }
}
