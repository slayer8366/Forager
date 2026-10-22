package com.zynergylabs.forager.app.ui.map

import com.zynergylabs.forager.app.map.STREET_SPRITE_ICON_NAMES

/**
 * Trail Map's fill-pattern images at night (dispatch 2026-10-22-03 and its Amendment 1, RECORD -1561). A `fill-pattern`
 * layer draws its sprite image's own pixels, so no paint property reaches it at night; like the icons
 * (`applyStreetSpriteNight`), the image is read back by name and put back under the same name in its night colours.
 *
 * Where the inversion gives the ruled night, the image is simply on the inversion list (`STREET_SPRITE_ICON_NAMES`):
 * the wetland tufts, whose inversion `#2485ad` is the owner's "Keep #2485ad". Where it does not, the image is painted
 * one colour here instead, and kept off that list: the tree glyph, whose inversion is `#9cd898` and whose ruled night is
 * `#5d804f` (Forager-cartography's night-colours record, the round-5 pair). Painting keeps each pixel's alpha, so the
 * drawing's own anti-aliased edges stay; the night PNG the owner approved was made exactly this way
 * (`tree-steps5.py`; every visible pixel `#5d804f`, alpha identical to the day image).
 *
 * The design's copy is `NIGHT_PATTERN_COLOURS` in `map-style/overrides/forager-character.mjs`, recorded in the style as
 * `forager:night-patterns`; `NightExceptionsTest` holds the two equal, and the glyph to 1.35:1 on the night forest.
 */
internal val STREET_NIGHT_PATTERN_COLOURS: Map<String, Int> = mapOf(
    "tree_dark_irregular" to 0x5D804F,
)

/**
 * [pixels] (ARGB, as `Bitmap.getPixels` gives them) painted [rgb], each pixel's alpha kept: a fully transparent pixel
 * stays fully transparent, and every other pixel takes exactly [rgb].
 */
internal fun nightPatternPixelsOf(pixels: IntArray, rgb: Int): IntArray {
    val colour = rgb and 0xFFFFFF
    return IntArray(pixels.size) { i ->
        val alpha = pixels[i] and 0xFF000000.toInt()
        if (alpha == 0) pixels[i] else alpha or colour
    }
}

/**
 * Every sprite image Trail Map's night redraws, in the order [applyStreetSpriteNight] walks them: the inverted icons
 * (`STREET_SPRITE_ICON_NAMES`, tufts included), then the painted patterns.
 */
internal val STREET_SPRITE_NIGHT_NAMES: List<String> = STREET_SPRITE_ICON_NAMES

/**
 * One sprite image's night pixels by its [name]: painted its [STREET_NIGHT_PATTERN_COLOURS] colour where it has one,
 * otherwise [nightIconPixelsOf] (the inversion, with the -1345 rings kept light).
 */
internal fun streetSpriteNightPixelsOf(name: String, pixels: IntArray): IntArray {
    val paint = STREET_NIGHT_PATTERN_COLOURS[name]
    return if (paint != null) nightPatternPixelsOf(pixels, paint) else nightIconPixelsOf(pixels, STREET_NIGHT_ICON_RINGS[name])
}
