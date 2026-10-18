package com.zynergylabs.forager.app.ui.map

import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Night mode's colour transform for colours this project sets itself, and for the offline vector
 * style (colour build C1 (d)). Pure Kotlin, no Android or MapLibre types, so every case is asserted
 * headless (`NightColourTest`, `OfflineNightRecolourTest`); `SightingsMap.kt`'s
 * `applyOfflineNightRecolour` is the thin adapter that reads a live style through the SDK and
 * writes the results back.
 *
 * **Why a recolour at all.** The raster basemaps get night from `NIGHT_RASTER_PAINT`, a raster paint
 * block. The offline style has no raster layer (1 background, 15 fill, 41 line layers), so that
 * block has nothing to apply to; before C1, Night Maps showed the offline style in its day colours.
 * The offline style is loaded by URI, the one URL every region was downloaded against
 * (`OfflineStyle.kt`), so a second, night style URL would need its own download. The planner's call
 * is to keep the one URL and recolour the loaded style's own layers after it loads, with the same V1
 * transform the raster paint applies, so the offline map's night matches the online one.
 */

/**
 * The V1 night transform for one colour, as ARGB: `night = clamp(c + 255 - 2 * mean(r, g, b))` per
 * channel, alpha unchanged.
 *
 * This is what MapLibre Native's raster shader computes for `raster-brightness-min 1`,
 * `raster-brightness-max 0`, `raster-hue-rotate 180` (cited from source at the pinned `13.5.0`, with
 * its residual against the S22 captures, in `docs/audits/2026-09-27-marker-swatch-board.md`
 * section 2): the hue rotate spins the colour half way round the grey axis, which is
 * `2 * mean - c` per channel, and the swapped brightness range then gives `1 - that`. Adding one
 * amount to every channel keeps HSV hue exactly wherever nothing clamps, while lightness inverts.
 * Values are gamma-encoded, as the shader's are; rounded to the nearest level.
 */
internal fun nightColorOf(dayColor: Int): Int {
    val alpha = (dayColor ushr 24) and 0xFF
    val r = (dayColor shr 16) and 0xFF
    val g = (dayColor shr 8) and 0xFF
    val b = dayColor and 0xFF
    val shift = 255.0 - 2.0 * (r + g + b) / 3.0
    fun night(c: Int): Int = (c + shift).roundToInt().coerceIn(0, 255)
    return (alpha shl 24) or (night(r) shl 16) or (night(g) shl 8) or night(b)
}

/** A colour property's value as a style hands it over: a literal colour string, or an expression as JSON text. */
internal sealed interface StyleColourValue {
    data class Literal(val text: String) : StyleColourValue
    data class Expression(val json: String) : StyleColourValue
}

/** One colour property of one layer, e.g. `landcover`'s `fill-color`. */
internal data class LayerColourProperty(val layerId: String, val property: String, val value: StyleColourValue)

/** What the offline night recolour does with one [LayerColourProperty]. */
internal sealed interface NightRecolour {
    val property: LayerColourProperty

    /** Set [property] to [night]. */
    data class Recoloured(override val property: LayerColourProperty, val night: StyleColourValue) : NightRecolour

    /** Leave [property] in its day colour; [reason] is what gets logged and listed. */
    data class Left(override val property: LayerColourProperty, val reason: String) : NightRecolour
}

/**
 * The colour paint properties the night recolour walks, by layer type: background, fill and line (the three types
 * `offline-style.json` has), and since dispatch 2026-10-18-05 Part 2 the symbol layers' text, halo and icon colours,
 * which the own Street style has and the offline style does not (-1288: "Labels are inverted too"). `icon-color` acts
 * only on SDF icons; the Street sprite's are not SDF, so its icons are inverted as images instead ([nightPixelsOf]).
 * The overlay layers `SightingsMap` adds after the recolour are not walked.
 */
internal val NIGHT_RECOLOURED_PROPERTIES: Map<String, List<String>> = mapOf(
    "background" to listOf("background-color"),
    "fill" to listOf("fill-color", "fill-outline-color"),
    "line" to listOf("line-color"),
    "symbol" to listOf("text-color", "text-halo-color", "icon-color"),
)

/** [pixels] (ARGB, as `Bitmap.getPixels` gives them) through [nightColorOf], alpha kept: an icon's night look. */
internal fun nightPixelsOf(pixels: IntArray): IntArray = IntArray(pixels.size) { nightColorOf(pixels[it]) }

/**
 * The night value for one layer colour property: [nightColorOf] of a literal, or of every colour
 * literal inside an expression of any kind (`match`, `case`, `interpolate`, `step` or any other,
 * planner ruling 2), leaving labels, conditions and zoom stops as they are.
 *
 * A colour literal is a `#rgb`/`#rgba`/`#rrggbb`/`#rrggbbaa` or `rgb()`/`rgba()` string, or an
 * `["rgb", r, g, b]`/`["rgba", r, g, b, a]` array with number arguments (the form MapLibre Native is
 * expected to serialise expression colours in; unverified until a device run). Anything else is
 * [NightRecolour.Left] with a reason: a literal in another form (a CSS colour name, `hsl()`), an
 * expression with no colour literal in it (a data-driven `["get", ...]`), or text that is not JSON.
 * CSS colour names inside an expression are not recognised and stay as they are; the repository's
 * offline style uses none.
 */
internal fun offlineNightRecolourOf(property: LayerColourProperty): NightRecolour =
    when (val value = property.value) {
        is StyleColourValue.Literal -> {
            val colour = parseStyleColour(value.text)
            if (colour == null) {
                NightRecolour.Left(property, "literal colour \"${value.text}\" is not a form this recolour reads")
            } else {
                NightRecolour.Recoloured(property, StyleColourValue.Literal(colour.night().format()))
            }
        }
        is StyleColourValue.Expression -> {
            val parsed = try {
                Json.parseToJsonElement(value.json)
            } catch (e: IllegalArgumentException) {
                null
            }
            if (parsed == null) {
                NightRecolour.Left(property, "expression is not JSON: ${value.json.take(120)}")
            } else {
                var found = 0
                val night = nightOfColourLiterals(parsed) { found++ }
                if (found == 0) {
                    NightRecolour.Left(property, "expression has no colour literal: ${value.json.take(120)}")
                } else {
                    NightRecolour.Recoloured(property, StyleColourValue.Expression(night.toString()))
                }
            }
        }
    }

private fun nightOfColourLiterals(element: JsonElement, onColour: () -> Unit): JsonElement = when (element) {
    is JsonPrimitive -> {
        val colour = if (element.isString) parseStyleColour(element.content) else null
        if (colour == null) {
            element
        } else {
            onColour()
            JsonPrimitive(colour.night().format())
        }
    }
    is JsonArray -> {
        val operator = (element.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
        val channels = element.drop(1).take(3).map { (it as? JsonPrimitive)?.takeUnless { p -> p.isString }?.doubleOrNull }
        val isColourArray = (operator == "rgba" && element.size == 5 || operator == "rgb" && element.size == 4) &&
            channels.all { it != null } &&
            (operator == "rgb" || (element[4] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull != null)
        if (isColourArray) {
            onColour()
            val day = RgbaColour(channels[0]!!.toChannel(), channels[1]!!.toChannel(), channels[2]!!.toChannel(), 1.0)
            val night = day.night()
            JsonArray(listOf(element[0], JsonPrimitive(night.r), JsonPrimitive(night.g), JsonPrimitive(night.b)) + element.drop(4))
        } else {
            JsonArray(element.map { nightOfColourLiterals(it, onColour) })
        }
    }
    is JsonObject -> JsonObject(element.mapValues { (_, v) -> nightOfColourLiterals(v, onColour) })
}

/** A style colour with its alpha kept as the style's own 0-1 value, so a recolour never re-rounds it. */
private data class RgbaColour(val r: Int, val g: Int, val b: Int, val alpha: Double) {
    fun night(): RgbaColour {
        val n = nightColorOf((0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        return RgbaColour((n shr 16) and 0xFF, (n shr 8) and 0xFF, n and 0xFF, alpha)
    }

    fun format(): String = "rgba($r, $g, $b, ${alpha.formatAlpha()})"
}

private fun Double.toChannel(): Int = roundToInt().coerceIn(0, 255)

private fun Double.formatAlpha(): String =
    if (this % 1.0 == 0.0) toLong().toString() else toString()

private val RGB_FUNCTION = Regex("""^rgba?\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*,\s*([-\d.]+)\s*(?:,\s*([-\d.]+)\s*)?\)$""")

/** Parses the colour forms [offlineNightRecolourOf] reads; `null` for anything else. */
private fun parseStyleColour(text: String): RgbaColour? {
    val t = text.trim().lowercase()
    if (t.startsWith("#")) {
        val hex = t.drop(1)
        if (hex.any { it !in "0123456789abcdef" }) return null
        val expanded = when (hex.length) {
            3, 4 -> hex.map { "$it$it" }.joinToString("")
            6, 8 -> hex
            else -> return null
        }
        val r = expanded.substring(0, 2).toInt(16)
        val g = expanded.substring(2, 4).toInt(16)
        val b = expanded.substring(4, 6).toInt(16)
        val alpha = if (expanded.length == 8) expanded.substring(6, 8).toInt(16) / 255.0 else 1.0
        return RgbaColour(r, g, b, alpha)
    }
    val match = RGB_FUNCTION.matchEntire(t) ?: return null
    val (r, g, b) = (1..3).map { match.groupValues[it].toDoubleOrNull() ?: return null }
    val alphaText = match.groupValues[4]
    val alpha = if (alphaText.isEmpty()) 1.0 else alphaText.toDoubleOrNull() ?: return null
    if (t.startsWith("rgba(") != alphaText.isNotEmpty()) return null
    return RgbaColour(r.toChannel(), g.toChannel(), b.toChannel(), alpha)
}
