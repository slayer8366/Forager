package com.zynergylabs.forager.app.ui.map

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.abs

/**
 * Dispatch 2026-11-06-04, Amendment 4 (RECORD -2186): reading the map-chrome outline ([mapChromeOutline]) from drawn pixels.
 *
 * The outline is translucent, so what it draws depends on what lies under it. Each read takes the pixel just inside the line
 * on the same row or column ([under], the piece's own fill over the map, read from the screen) and composites the ruled
 * colour over it: [MAP_CHROME_OUTLINE_DAY] (Bark #3B2E24 at 40%) by day, [MAP_CHROME_OUTLINE_NIGHT] (white at 40%) at night.
 * The ruled colours are written out here, not read from the code under test, so an outline drawn any other colour fails.
 *
 * A solid 1 dp `onSurface` line (the outline Re-center and the locate ring drew before this amendment) does not read as this
 * one: its full pixels are the solid colour, far from the composite, and at most one anti-aliased pixel at its edge can fall
 * near it by chance, so the run lengths the callers require (at least ceil(1 dp) - 1) are not met.
 */
internal val MAP_CHROME_OUTLINE_DAY = Color(0x663B2E24)

/** See [MAP_CHROME_OUTLINE_DAY]. */
internal val MAP_CHROME_OUTLINE_NIGHT = Color(0x66FFFFFF)

/** How far, per channel, a drawn pixel may be from the composite and still read as it: 8-bit blending and rounding. */
internal const val OUTLINE_TOLERANCE = 4f / 255f

/** Whether [pixel] is within [OUTLINE_TOLERANCE] of [expected] in each of red, green and blue. */
internal fun near(pixel: Color, expected: Color): Boolean =
    abs(pixel.red - expected.red) <= OUTLINE_TOLERANCE &&
        abs(pixel.green - expected.green) <= OUTLINE_TOLERANCE &&
        abs(pixel.blue - expected.blue) <= OUTLINE_TOLERANCE

/** What [outline] draws over [under]. */
internal fun outlineOver(outline: Color, under: Color): Color = outline.compositeOver(under).copy(alpha = 1f)

/**
 * In [edge] (read inwards), between [from] and [to] inclusive, the first run of pixels that read as [outline] over [under];
 * null if none.
 */
internal fun outlineRunOf(edge: List<Color>, outline: Color, under: Color, from: Int = 0, to: Int = edge.lastIndex): IntRange? {
    val expected = outlineOver(outline, under)
    val start = (from..minOf(to, edge.lastIndex)).firstOrNull { near(edge[it], expected) } ?: return null
    var end = start
    while (end <= minOf(to, edge.lastIndex) && near(edge[end], expected)) end++
    return start until end
}

/**
 * How far, per channel, a pixel in the ring's place may be from the fill beside it and still read as no ring. Wider than
 * [OUTLINE_TOLERANCE] because the fill there is not always flat: a fading press highlight shades it by about 1/255 per pixel
 * (red run 42440600462, the tablet's touches beside the pill). The faintest ring, the map chrome's own by day, steps about
 * 70/255 from the fill, so any ring still fails this.
 */
internal const val NO_RING_TOLERANCE = 12f / 255f

/**
 * In [edge], between [from] and [to] inclusive, the first pixel that does not read as [under] itself (within
 * [NO_RING_TOLERANCE]); null if the whole band is [under]. "No ring" is a band that is only the fill, so a ring of any colour
 * fails it, not only the ruled one.
 */
internal fun firstNotUnder(edge: List<Color>, under: Color, from: Int, to: Int): Int? =
    (from..minOf(to, edge.lastIndex)).firstOrNull {
        abs(edge[it].red - under.red) > NO_RING_TOLERANCE ||
            abs(edge[it].green - under.green) > NO_RING_TOLERANCE ||
            abs(edge[it].blue - under.blue) > NO_RING_TOLERANCE
    }

/**
 * The locate row's ring, dispatch 2026-11-06-04 Amendments 2 and 4 (RECORD -2186): on the badge circle, 6 dp in from each side
 * of the 48 dp row, so a line from 6 dp to 7 dp in. [ringBand] is that place with a pixel either side; [ringUnder] is the
 * row's fill just inside it, two pixels past the band, clear of the glyph (from about 13 dp).
 */
internal fun ringBand(px: (Float) -> Int): IntRange = (px(6f) - 1)..(px(7f) + 1)

/** See [ringBand]. */
internal fun ringUnder(px: (Float) -> Int): Int = ringBand(px).last + 2
