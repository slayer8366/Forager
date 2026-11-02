package com.zynergylabs.forager.app.ui.map

import android.util.Log
import androidx.compose.foundation.background
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.PropertyValue
import org.maplibre.android.style.layers.SymbolLayer

/**
 * The offline style's night: every background, fill and line colour property of the loaded style set
 * to its V1 night value ([offlineNightRecolourOf]). The SDK calls were checked with `javap` against
 * the pinned `13.5.0` artifact before this was written: `Style.getLayers`, `Layer.setProperties`,
 * the `BackgroundLayer`/`FillLayer`/`LineLayer` colour getters (each a `PropertyValue<String>`), the
 * `PropertyFactory` colour setters for `String` and `Expression`, `PropertyValue.isExpression`/
 * `getExpression`/`isValue`/`getValue`, and `Expression.toString`/`Expression.raw`.
 *
 * A property whose value is unset (the style leaves it to the default) is not touched. One the pure
 * function cannot read is left in its day colour and logged by layer and property, never dropped
 * silently (CLAUDE.md); a summary line says how many were recoloured and how many left. Which form
 * MapLibre hands the colours back in (an `rgba()` string, `["rgba", ...]` arrays inside expressions)
 * is unverified until a device run; a form this does not read shows up in these log lines.
 *
 * [streetTables] are the Street night's tables for the Trail Map version loaded ([streetNightTablesFor]); they apply
 * only with [streetWater], the own Street.
 */
internal fun applyOfflineNightRecolour(style: Style, streetWater: Boolean = false, streetTables: StreetNightTables = STREET_NIGHT_TABLES) {
    var recoloured = 0
    var left = 0
    for (layer in style.layers) {
        for ((property, value) in dayColourPropertiesOf(layer)) {
            val colourValue = when {
                value.isNull -> continue
                value.isExpression -> StyleColourValue.Expression(value.expression.toString())
                value.value is String -> StyleColourValue.Literal(value.value as String)
                else -> null
            }
            val outcome = if (colourValue == null) {
                NightRecolour.Left(
                    LayerColourProperty(layer.id, property, StyleColourValue.Literal(value.toString())),
                    "value of type ${value.value?.javaClass?.name} is neither a colour string nor an expression",
                )
            } else if (streetWater) {
                streetNightRecolourOf(LayerColourProperty(layer.id, property, colourValue), sourceLayerOf(layer), streetTables)
            } else {
                offlineNightRecolourOf(LayerColourProperty(layer.id, property, colourValue))
            }
            when (outcome) {
                is NightRecolour.Recoloured -> try {
                    layer.setProperties(nightPropertyValue(property, outcome.night))
                    recoloured++
                } catch (e: RuntimeException) {
                    left++
                    Log.w(SIGHTINGS_MAP_TAG, "Offline night: could not set ${layer.id}/$property; left in its day colour.", e)
                }
                is NightRecolour.Left -> {
                    left++
                    Log.w(SIGHTINGS_MAP_TAG, "Offline night: ${layer.id}/$property left in its day colour: ${outcome.reason}")
                }
            }
        }
    }
    Log.i(SIGHTINGS_MAP_TAG, "Offline night: $recoloured colour properties recoloured, $left left in day colours.")
}

/** [layer]'s vector source layer, for the night water exception; `null` for a layer with none. */
private fun sourceLayerOf(layer: Layer): String? = when (layer) {
    is FillLayer -> layer.sourceLayer
    is LineLayer -> layer.sourceLayer
    else -> null
}

/** [layer]'s colour properties the offline night walks ([NIGHT_RECOLOURED_PROPERTIES]), with their day values. */
private fun dayColourPropertiesOf(layer: Layer): List<Pair<String, PropertyValue<*>>> = when (layer) {
    is BackgroundLayer -> listOf("background-color" to layer.backgroundColor)
    is FillLayer -> listOf("fill-color" to layer.fillColor, "fill-outline-color" to layer.fillOutlineColor)
    is LineLayer -> listOf("line-color" to layer.lineColor)
    is SymbolLayer -> listOf("text-color" to layer.textColor, "text-halo-color" to layer.textHaloColor, "icon-color" to layer.iconColor)
    else -> emptyList()
}

private fun nightPropertyValue(property: String, night: StyleColourValue): PropertyValue<*> = when (night) {
    is StyleColourValue.Literal -> when (property) {
        "background-color" -> PropertyFactory.backgroundColor(night.text)
        "fill-color" -> PropertyFactory.fillColor(night.text)
        "fill-outline-color" -> PropertyFactory.fillOutlineColor(night.text)
        "line-color" -> PropertyFactory.lineColor(night.text)
        "text-color" -> PropertyFactory.textColor(night.text)
        "text-halo-color" -> PropertyFactory.textHaloColor(night.text)
        "icon-color" -> PropertyFactory.iconColor(night.text)
        else -> error("offline night does not walk $property")
    }
    is StyleColourValue.Expression -> {
        val expression = Expression.raw(night.json)
        when (property) {
            "background-color" -> PropertyFactory.backgroundColor(expression)
            "fill-color" -> PropertyFactory.fillColor(expression)
            "fill-outline-color" -> PropertyFactory.fillOutlineColor(expression)
            "line-color" -> PropertyFactory.lineColor(expression)
            "text-color" -> PropertyFactory.textColor(expression)
            "text-halo-color" -> PropertyFactory.textHaloColor(expression)
            "icon-color" -> PropertyFactory.iconColor(expression)
            else -> error("offline night does not walk $property")
        }
    }
}
