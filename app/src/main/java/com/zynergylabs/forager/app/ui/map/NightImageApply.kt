package com.zynergylabs.forager.app.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.DisplayMetrics
import android.util.Log
import java.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * The bundled night sheets: sprite v5's index and its images in their night colours, generated from the day sheets by
 * the night rules at build time and held pixel-equal to them by `NightSpriteTest` (dispatch 2026-10-22-06 Amendment 1).
 * 1x and @2x, as the day sprite comes.
 */
internal const val NIGHT_SPRITE_ASSET_DIR = "night-sprite/v5"

/** Sprite v6's night sheets: v5's, plus the orchard, vineyard and nursery patterns (dispatch 2026-10-31-coder-07 Part 3). */
internal const val NIGHT_SPRITE_V6_ASSET_DIR = "night-sprite/v6"

private val SPRITE_V6 = Regex("""/sprites/v6/light$""")

/**
 * The night sheets for the style the map loaded, by the sprite it names: v6's for a style naming sprite v6 (Street v4),
 * v5's for every other (v1 to v3 and anything unreadable), as before v6.
 */
internal fun nightSpriteAssetDirFor(styleJson: String): String {
    val sprite = try {
        (Json.parseToJsonElement(styleJson).jsonObject["sprite"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    } catch (e: RuntimeException) {
        null
    }
    return NIGHT_SPRITE_ASSET_DIR // revert check: v5 for every style
}

/**
 * The @2x sheet on any screen whose pixel ratio is above 1, the 1x sheet otherwise: the same choice MapLibre Native
 * makes for the day sprite (a `@2x` URL when the map's pixel ratio is above 1), so each night copy has its day image's
 * size. The map's pixel ratio is the display density (MapLibre's default, which this app does not change).
 */
internal fun nightSpriteSuffixFor(density: Float): String = if (density > 1f) "@2x" else ""

/**
 * The night copies by their day names, decoded once per sheet (the last one read is kept): the sheet is cut into one
 * bitmap per index entry, each given the density MapLibre reads its pixel ratio from (`Style.toImage`:
 * `getDensity() / DENSITY_DEFAULT`, checked against the `13.5.0` source).
 */
private object NightSpriteCache {
    @Volatile
    private var images: Pair<String, Map<String, Bitmap>>? = null

    fun images(context: Context, dir: String): Map<String, Bitmap> {
        val suffix = nightSpriteSuffixFor(context.resources.displayMetrics.density)
        val key = "$dir/light$suffix"
        images?.let { (cachedKey, cached) -> if (cachedKey == key) return cached }
        val index = context.assets.open("$dir/light$suffix.json").use {
            Json.parseToJsonElement(it.reader().readText()).jsonObject
        }
        val sheet = context.assets.open("$dir/light$suffix.png").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        } ?: throw IOException("the night sheet light$suffix.png did not decode")
        val cut = index.mapValues { (_, entry) ->
            val e = entry.jsonObject
            fun field(name: String) = e.getValue(name).jsonPrimitive.int
            Bitmap.createBitmap(sheet, field("x"), field("y"), field("width"), field("height")).apply {
                density = DisplayMetrics.DENSITY_DEFAULT * field("pixelRatio")
            }
        }
        images = key to cut
        return cut
    }
}

/** The live style as the night step's target. */
private class StyleNightImageTarget(private val style: Style) : NightImageTarget<Bitmap> {
    override val styleJson: String get() = style.json

    override fun addImages(images: Map<String, Bitmap>) = style.addImages(HashMap(images))

    override fun setImageReference(layerId: String, property: String, value: ImageReference): Boolean {
        val layer = style.getLayer(layerId) ?: return false
        val propertyValue = when (property) {
            "icon-image" -> when (value) {
                is ImageReference.Name -> PropertyFactory.iconImage(value.name)
                is ImageReference.Expr -> PropertyFactory.iconImage(Expression.raw(value.json))
            }
            "fill-pattern" -> when (value) {
                is ImageReference.Name -> PropertyFactory.fillPattern(value.name)
                is ImageReference.Expr -> PropertyFactory.fillPattern(Expression.raw(value.json))
            }
            else -> return false
        }
        layer.setProperties(propertyValue)
        return true
    }

    override fun readImageReference(layerId: String, property: String): String? {
        val value = when (val layer = style.getLayer(layerId)) {
            is SymbolLayer -> if (property == "icon-image") layer.iconImage else return null
            is FillLayer -> if (property == "fill-pattern") layer.fillPattern else return null
            else -> return null
        }
        return if (value.isExpression) value.expression.toString() else value.value?.toString()
    }
}

/**
 * Trail Map's night icons and patterns on the loaded [style] ([applyNightImages]), with one log line saying how many
 * night copies were added, how many references switched, and anything left in day images. If the bundled sheets cannot
 * be read, nothing is added, every reference keeps its day image, and the line says so: never a crash, never silent.
 */
internal fun applyStreetNightImages(style: Style, context: Context) {
    val images = try {
        NightSpriteCache.images(context, nightSpriteAssetDirFor(style.json))
    } catch (e: IOException) {
        Log.w(SIGHTINGS_MAP_TAG, "Street night: the night sheets could not be read; every icon and pattern stays in day colours.", e)
        emptyMap()
    } catch (e: RuntimeException) {
        Log.w(SIGHTINGS_MAP_TAG, "Street night: the night sheets could not be read; every icon and pattern stays in day colours.", e)
        emptyMap()
    }
    Log.i(SIGHTINGS_MAP_TAG, applyNightImages(StyleNightImageTarget(style), images).logLine())
}
