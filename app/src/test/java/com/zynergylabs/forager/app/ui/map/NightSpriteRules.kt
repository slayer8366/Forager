package com.zynergylabs.forager.app.ui.map

import kotlin.math.roundToInt

// The night sprite's pixel rules (dispatch 2026-10-22-06 Amendment 1, RECORD -1566). They ran on the phone, in the
// setStyle callback, until that dispatch; MapLibre Native had not loaded the sprite by then (dispatch 2026-10-22-05,
// docs/audits/2026-10-22-night-icon-set-verify.md), so they never reached a pixel on a device. They now run here, at
// build time, where NightSpriteTest applies them to sprite v5's day images and holds the app's bundled night sheets
// (app/src/main/assets/night-sprite/v5) pixel-equal to the result. Moved out of NightColour.kt and NightPattern.kt
// unchanged, except that tufts is painted rather than inverted (STREET_NIGHT_PATTERN_PAINTS).

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
 * Trail Map's fill-pattern images at night (dispatch 2026-10-22-03 and its Amendment 1, RECORD -1561): the tree glyph
 * painted its ruled night colour, `#5d804f` (Forager-cartography's night-colours record, the round-5 pair), in place of
 * its inversion `#9cd898`. The design's copy is `NIGHT_PATTERN_COLOURS` in `map-style/overrides/forager-character.mjs`,
 * recorded in the style as `forager:night-patterns`; `NightExceptionsTest` holds the two equal.
 */
internal val STREET_NIGHT_PATTERN_COLOURS: Map<String, Int> = mapOf(
    "tree_dark_irregular" to 0x5D804F,
)

/**
 * Every pattern painted one colour at night: [STREET_NIGHT_PATTERN_COLOURS], and the wetland tufts at `#2485ad`. The
 * owner, RECORD -1566: "The tree glyph is #5d804f, and tufts #2485ad"; the planner, relaying the owner's "Only for the
 * white icons I mentioned. Nothing else": tufts "takes #2485ad, which is its ruled colour, set as a colour and not by
 * inverting". At @2x every visible tufts pixel is `#3fa0c8`, whose inversion is exactly `#2485ad`, so painting changes
 * nothing there; the 1x sheet's tufts has mixed edge colours, which painting sets to `#2485ad` too.
 */
internal val STREET_NIGHT_PATTERN_PAINTS: Map<String, Int> = STREET_NIGHT_PATTERN_COLOURS + ("tufts" to 0x2485AD)

/**
 * [pixels] (ARGB) painted [rgb], each pixel's alpha kept: a fully transparent pixel stays as it is, and every other
 * pixel takes exactly [rgb]. Painting keeps the drawing's own anti-aliased edges; the night tree PNG the owner approved
 * was made this way (`tree-steps5.py`).
 */
internal fun nightPatternPixelsOf(pixels: IntArray, rgb: Int): IntArray {
    val colour = rgb and 0xFFFFFF
    return IntArray(pixels.size) { i ->
        val alpha = pixels[i] and 0xFF000000.toInt()
        if (alpha == 0) pixels[i] else alpha or colour
    }
}

/**
 * One sprite image's night pixels by its [name] (the 61-row table in docs/audits/2026-10-22-night-icon-set-verify.md):
 * a pattern in [STREET_NIGHT_PATTERN_PAINTS] painted its colour; an icon with a -1345 ring inverted with its ring kept
 * light; every other image, point icons and road shields alike, inverted ([nightPixelsOf]).
 */
internal fun nightSpritePixelsOf(name: String, pixels: IntArray): IntArray {
    val paint = STREET_NIGHT_PATTERN_PAINTS[name]
    return if (paint != null) nightPatternPixelsOf(pixels, paint) else nightIconPixelsOf(pixels, null)
}
