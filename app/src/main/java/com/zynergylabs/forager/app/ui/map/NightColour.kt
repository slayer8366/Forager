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

/**
 * The own Street's night water: a lighter blue than the inversion gives, on every water fill and line (rivers, creeks,
 * lakes, the sea). RECORD -1343, the owner: "I like the blue of the water, but looking at creeks is a bit dark against
 * the other dark colors. Can the water be a little bit lighter in blue color?", then "Night only, lighter
 * (Recommended)". It is the one named exception to -1288's pure inversion; -1344 allows such exceptions where an
 * inverted colour reads poorly ("we can go outside the purity for the UX").
 *
 * The inversion gave `#005864` (the day `#80deea`), 1.35:1 on the night forest; this is 3.28:1 on the night forest
 * `#436d35`, 6.68:1 on the night grass `#2d3911` and 8.70:1 on the night ground `#24211c` (`NightWaterTest`, and
 * `map-style/tools/check-street-night.mjs`, which reads the same colour from the style's design file). The day water
 * colour is unchanged. Only the own Street takes it: the offline style keeps its inverted water, so today's app is
 * unchanged.
 */
internal const val STREET_NIGHT_WATER = "#8ec5f0"

/** The vector source layer the own Street's water is drawn from (Protomaps' `water`). */
internal const val WATER_SOURCE_LAYER = "water"

/** The colour properties [streetNightRecolourOf] gives [STREET_NIGHT_WATER]: fills and lines, never label text. */
private val WATER_COLOUR_PROPERTIES = setOf("fill-color", "fill-outline-color", "line-color")

/**
 * The own Street's night line colours by layer (RECORD -1345, the owner: "Roads (Recommended), Railways + boundaries
 * (Recommended)"): the inversion left these at 1.0 to 1.5:1 on the night forest (Part 1 report, round 1). Tunnels keep
 * the inversion. The design's copy is `NIGHT_LAYER_COLOURS` in `map-style/overrides/forager-street.mjs`, recorded in
 * the style's metadata; `NightExceptionsTest` holds the two equal and each colour to 2.5:1 on the night forest, grass
 * and ground, except where the design gives a layer its own band on one ground (`NIGHT_LAYER_BANDS`: informal paths on
 * the night forest).
 */
internal val STREET_NIGHT_LAYER_COLOURS: Map<String, String> = mapOf(
    "roads_minor_casing" to "#c4bcae", // minor road edge, 3.21:1 on the night forest
    "roads_bridges_minor_casing" to "#c4bcae",
    "roads_minor_service_casing" to "#b3ab9d", // service road edge, 2.65:1
    "roads_major" to "#e0ad4f", // major road centre, 2.95:1; links take the major palette
    "roads_bridges_major" to "#e0ad4f",
    "roads_link" to "#e0ad4f",
    "roads_bridges_link" to "#e0ad4f",
    "roads_highway" to "#f0c060", // highway centre, 3.57:1
    "roads_bridges_highway" to "#f0c060",
    "roads_rail" to "#b4bec0", // railways, 3.18:1
    "boundaries" to "#bdbdbd", // 3.21:1
    "boundaries_country" to "#bdbdbd",
    // Informal paths' own night colour (dispatch 2026-10-19-41; the owner: "Own night colour (Recommended)"), in place of
    // the inversion #9b694d, which nearly vanished on the night forest. 2.01:1 there: below the 2.5 the others hold, by
    // the owner's band for this layer ("Accept the range (Recommended)", 1.9 to 2.3), which NightExceptionsTest holds.
    "roads_path_informal_z13" to "#bb8a6f",
    "roads_path_informal" to "#9b694d",
)

/**
 * The own Street's night for one colour property: [STREET_NIGHT_WATER] for a water fill or line (a layer drawn from
 * [sourceLayer] `water`), a [STREET_NIGHT_LAYER_COLOURS] colour for those layers' lines, and [offlineNightRecolourOf]'s
 * pure inversion for everything else, labels included.
 */
internal fun streetNightRecolourOf(property: LayerColourProperty, sourceLayer: String?): NightRecolour {
    if (sourceLayer == WATER_SOURCE_LAYER && property.property in WATER_COLOUR_PROPERTIES) {
        return NightRecolour.Recoloured(property, StyleColourValue.Literal(STREET_NIGHT_WATER))
    }
    val layerNight = STREET_NIGHT_LAYER_COLOURS[property.layerId]
    if (layerNight != null && property.property == "line-color") {
        return NightRecolour.Recoloured(property, StyleColourValue.Literal(layerNight))
    }
    return offlineNightRecolourOf(property)
}

/**
 * An icon whose ring takes its own night colour (RECORD -1345, "Place dots + icon rings (Recommended)"): [dayRing] and
 * [dayFill] are the colours the sprite draws the ring and its inside with, [nightRing] the ring's night colour. As
 * RGB, no alpha.
 */
internal data class IconRingNight(val dayRing: Int, val dayFill: Int, val nightRing: Int)

/**
 * The own Street's icons whose rings read too dark inverted (1.0 to 1.4:1 on the night forest): the place dot, and the
 * toilets and drinking-water icons. The design's copy is `NIGHT_ICON_RINGS` in `map-style/overrides/forager-street.mjs`.
 */
internal val STREET_NIGHT_ICON_RINGS: Map<String, IconRingNight> = mapOf(
    "townspot" to IconRingNight(dayRing = 0x868686, dayFill = 0xF3F3F3, nightRing = 0xD6D6D6),
    "toilets" to IconRingNight(dayRing = 0x8780AB, dayFill = 0xDCEBFA, nightRing = 0xC6BFEA),
    "drinking_water" to IconRingNight(dayRing = 0x8780AB, dayFill = 0xDCEBFA, nightRing = 0xC6BFEA),
)

/**
 * How far, in RGB levels, a pixel may sit from the line between an icon's ring and fill colours and still count as part
 * of that ring or its edge. The sprite's own anti-aliasing sits on that line (its edge pixels are mixes of the two);
 * a symbol drawn inside the icon in another colour does not, and keeps the plain inversion.
 */
private const val RING_TOLERANCE = 16.0

/**
 * One icon's night pixels with its ring kept light ([ring]); without one, [nightPixelsOf]. A pixel on the ring-to-fill
 * line at fraction `t` from the ring becomes the same mix of [IconRingNight.nightRing] and the fill's inversion, so the
 * ring's anti-aliased edge stays smooth; every other pixel is the plain inversion. Alpha is kept.
 */
internal fun nightIconPixelsOf(pixels: IntArray, ring: IconRingNight?): IntArray {
    if (ring == null) return nightPixelsOf(pixels)
    fun ch(c: Int, shift: Int) = (c shr shift) and 0xFF
    val ringRgb = intArrayOf(ch(ring.dayRing, 16), ch(ring.dayRing, 8), ch(ring.dayRing, 0))
    val fillRgb = intArrayOf(ch(ring.dayFill, 16), ch(ring.dayFill, 8), ch(ring.dayFill, 0))
    val nightFill = nightColorOf((0xFF shl 24) or ring.dayFill)
    val nightFillRgb = intArrayOf(ch(nightFill, 16), ch(nightFill, 8), ch(nightFill, 0))
    val nightRingRgb = intArrayOf(ch(ring.nightRing, 16), ch(ring.nightRing, 8), ch(ring.nightRing, 0))
    val axis = DoubleArray(3) { (fillRgb[it] - ringRgb[it]).toDouble() }
    val axisLength2 = axis.sumOf { it * it }
    return IntArray(pixels.size) { i ->
        val p = pixels[i]
        val rgb = intArrayOf(ch(p, 16), ch(p, 8), ch(p, 0))
        val t = (DoubleArray(3) { (rgb[it] - ringRgb[it]) * axis[it] }.sum() / axisLength2).coerceIn(0.0, 1.0)
        val distance2 = (0 until 3).sumOf { val d = rgb[it] - (ringRgb[it] + t * axis[it]); d * d }
        if (distance2 > RING_TOLERANCE * RING_TOLERANCE) {
            nightColorOf(p)
        } else {
            val out = IntArray(3) { (nightRingRgb[it] + t * (nightFillRgb[it] - nightRingRgb[it])).roundToInt().coerceIn(0, 255) }
            (p and 0xFF000000.toInt()) or (out[0] shl 16) or (out[1] shl 8) or out[2]
        }
    }
}

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
