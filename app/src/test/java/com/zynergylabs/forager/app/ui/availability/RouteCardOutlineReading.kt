package com.zynergylabs.forager.app.ui.availability

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.zynergylabs.forager.app.ui.map.hex
import kotlin.math.abs

/*
 * Dispatch 2026-11-06-05, Amendment 5 (RECORD -2187): reading the route card's outline from drawn pixels. The owner: "I just want a
 * regular outline like every other map chrome has", "Plain line all the way round, like a bubble". The ruled line is the map chrome's
 * (`MapChromeOutline.kt`): 1 dp, Bark #3B2E24 at 40% by day, white at 40% at night, written out here as the ruling gives it, so a line
 * in any other colour or alpha, or none, fails.
 *
 * A 40% line shows what is under it, so the colour expected at the card's edge is that line over the card's own pixel a few dp in on
 * the same row or column, where nothing else is drawn: [ROUTE_CARD_LINE_REFERENCE_DP] in, checked level with the pixel a dp further
 * in, so a reference that lands on a glyph or a gradient fails as a bad sample rather than passing.
 */

/** The ruled outline: Bark at 40% by day, white at 40% at night. */
internal fun routeCardRuledLine(dark: Boolean): Color = if (dark) Color(0x66FFFFFF) else Color(0x663B2E24)

/** How far in the reference pixel is read, in dp: past the 1 dp line, before any of the card's content. */
internal const val ROUTE_CARD_LINE_REFERENCE_DP = 4

/** Per channel, how far a read pixel may be from the expected one: compositing an 80% fill and a 40% line rounds twice. */
internal const val ROUTE_CARD_LINE_TOLERANCE = 4f / 255f

internal fun Color.near(other: Color, tolerance: Float = ROUTE_CARD_LINE_TOLERANCE): Boolean =
    abs(red - other.red) <= tolerance && abs(green - other.green) <= tolerance && abs(blue - other.blue) <= tolerance

/**
 * One side of the card, read from its edge inwards ([edge][0] is the outermost pixel). [px] is pixels per dp. Returns null if the
 * side carries the ruled line (the pixel 1 px in, inside the 1 dp stroke at any sub-pixel offset, is [routeCardRuledLine] over the
 * reference), else why not.
 */
internal fun whyNotRuledLine(edge: List<Color>, dark: Boolean, px: Float): String? {
    val (reference, bad) = lineReference(edge, px)
    if (bad != null) return bad
    val expected = routeCardRuledLine(dark).compositeOver(reference)
    val read = edge[1]
    return if (read.near(expected)) null else "1 px in reads ${read.hex()}, not the ruled line over the card (${expected.hex()} = ${routeCardRuledLine(dark).alpha}-alpha line over ${reference.hex()}); the edge reads ${edge.take((px * 2).toInt()).map { it.hex() }}"
}

/** Null if the side carries no line: its pixel 1 px in is level with the reference. Else what it reads. */
internal fun whyNotBare(edge: List<Color>, px: Float): String? {
    val (reference, bad) = lineReference(edge, px)
    if (bad != null) return bad
    val read = edge[1]
    return if (read.near(reference)) null else "1 px in reads ${read.hex()}, not the card's own ${reference.hex()} a few dp in; the edge reads ${edge.take((px * 2).toInt()).map { it.hex() }}"
}

private fun lineReference(edge: List<Color>, px: Float): Pair<Color, String?> {
    val at = (ROUTE_CARD_LINE_REFERENCE_DP * px).toInt()
    val further = ((ROUTE_CARD_LINE_REFERENCE_DP + 1) * px).toInt()
    check(edge.size > further) { "the edge read is ${edge.size} px, shorter than the reference ${ROUTE_CARD_LINE_REFERENCE_DP + 1} dp in" }
    val reference = edge[at]
    return reference to if (reference.near(edge[further])) null else "bad sample: the reference ${ROUTE_CARD_LINE_REFERENCE_DP} dp in (${reference.hex()}) is not level with ${ROUTE_CARD_LINE_REFERENCE_DP + 1} dp in (${edge[further].hex()})"
}
