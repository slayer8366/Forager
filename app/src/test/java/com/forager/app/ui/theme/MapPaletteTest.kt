package com.forager.app.ui.theme

import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Holds [MapPalette] to the properties a map overlay actually needs: each mark is legible against
 * the ground it is drawn on, and — for [MapPalette.DAY], which still differentiates its seven
 * marker roles by hue — no two marks a user must tell apart are hard to tell apart by colour
 * alone.
 *
 * [MapPalette.NIGHT] no longer makes that second claim: every marker field on it holds
 * [MapPalette.NIGHT_WARM] or [MapPalette.NIGHT_INK], and `SightingsMap.kt`'s icon shapes are what
 * keep night-mode markers apart now, not hue — see [MapPalette.NIGHT]'s own doc comment ("Fifth
 * pass") for why. This file has no way to check shape, so it checks what it can: that the shared
 * two colours themselves are legible.
 *
 * ## What this does not establish
 *
 * Legibility against *real tiles*, or that the night icon shapes actually read as distinct from
 * each other on a real screen. Both tile references are modelled, not sampled. These checks bound
 * regressions against a stated reference; the README's "Not yet verified" section is where the
 * hardware question lives. A green run here is not evidence that the map reads well, day or
 * night.
 */
class MapPaletteTest {

    /**
     * [MapPalette.NIGHT_WARM] was authored to a target, so it is held to one: 4.0:1, comfortably
     * above WCAG's 3:1 non-text bar, against the dimmed-tile reference it was designed for.
     */
    private val minimumNightMarkerContrast = 4.0

    /**
     * The day palette is held to a **ratchet, not a bar**, and the reason is worth stating rather
     * than burying in a constant.
     *
     * Measured against a pale topo reference, the day palette does not clear 4:1 — or 3:1. The
     * waypoint amber is 1.79:1, the connector 2.59:1, the breadcrumb 3.14:1. Those colours predate
     * this work, shipped, and were tuned against real tiles on real hardware (see `SightingsMap`'s
     * history on the sighting-dot stroke, where a dense-cluster legibility failure was found on a
     * Portland-metro topo basemap and fixed with a stroke rather than with more contrast).
     *
     * So one of two things is true, and arithmetic cannot say which: either luminance contrast
     * against an averaged tile is the wrong measure for a saturated mark on a desaturated,
     * texturally busy ground — which is plausible, since hue difference carries information that
     * a luminance ratio does not score — or the day palette has a real legibility weakness that
     * hardware review has not yet caught because nobody went looking for it.
     *
     * This test refuses to bless either reading. It pins the day palette at its measured minimum
     * so nothing can quietly make it worse, and the question goes to the device gate and the
     * README's "Not yet verified" section, where it belongs.
     */
    private val dayMarkerContrastRatchet = 1.75

    /** Marks drawn on top of other marks are held to the text bar, since one is literally a glyph. */
    private val minimumMarkOnMarkContrast = 4.5

    /**
     * Two marks closer than this in Oklab are treated as too similar. Set from the day palette's
     * own tightest pair (connector/waypoint, 0.106), which is the palette that has been on real
     * hardware — so the bar is "no worse than what has actually been used outdoors" rather than a
     * number chosen from nothing. Applies to [MapPalette.DAY] only — see the class doc comment for
     * why [MapPalette.NIGHT] no longer makes a colour-separation claim at all.
     */
    private val minimumMarkerSeparation = 0.099

    private fun markers(p: MapPalette) = mapOf(
        "sightingDot" to p.sightingDot,
        "connector" to p.connector,
        "areaMarkerBackground" to p.areaMarkerBackground,
        "plannedTrip" to p.plannedTrip,
        "searchCentre" to p.searchCentre,
        "breadcrumb" to p.breadcrumb,
        "waypoint" to p.waypoint,
    )

    @Test
    fun `every day marker clears its contrast ratchet against the day tile reference`() {
        val failures = mutableListOf<String>()
        for ((marker, argb) in markers(MapPalette.DAY)) {
            val ratio = contrastRatio(argb, MapPalette.DAY_TILE_REFERENCE)
            if (ratio < dayMarkerContrastRatchet) {
                failures += "DAY.%s is %.2f:1, below %.2f:1".format(marker, ratio, dayMarkerContrastRatchet)
            }
        }
        if (failures.isNotEmpty()) fail("Day marker contrast:\n  " + failures.joinToString("\n  "))
    }

    @Test
    fun `NIGHT_WARM clears the night contrast floor against the night tile reference`() {
        val ratio = contrastRatio(MapPalette.NIGHT_WARM, MapPalette.NIGHT_TILE_REFERENCE)
        if (ratio < minimumNightMarkerContrast) {
            fail("NIGHT_WARM is %.2f:1 against NIGHT_TILE_REFERENCE, below %.2f:1".format(ratio, minimumNightMarkerContrast))
        }
    }

    /**
     * Every one of [MapPalette.NIGHT]'s nine fields must resolve to exactly one of the two shared
     * values — the whole point of the fifth pass was collapsing nine independently-tuned colours
     * into two, and a future edit accidentally reintroducing a tenth would defeat it silently
     * (compiling and looking fine) rather than loudly.
     */
    @Test
    fun `every NIGHT field is NIGHT_WARM or NIGHT_INK, nothing else`() {
        val fields = mapOf(
            "sightingDot" to MapPalette.NIGHT.sightingDot,
            "sightingDotStroke" to MapPalette.NIGHT.sightingDotStroke,
            "connector" to MapPalette.NIGHT.connector,
            "areaMarkerBackground" to MapPalette.NIGHT.areaMarkerBackground,
            "areaMarkerForeground" to MapPalette.NIGHT.areaMarkerForeground,
            "plannedTrip" to MapPalette.NIGHT.plannedTrip,
            "searchCentre" to MapPalette.NIGHT.searchCentre,
            "breadcrumb" to MapPalette.NIGHT.breadcrumb,
            "waypoint" to MapPalette.NIGHT.waypoint,
        )
        val failures = fields.filterValues { it != MapPalette.NIGHT_WARM && it != MapPalette.NIGHT_INK }
        if (failures.isNotEmpty()) {
            fail("Fields not equal to NIGHT_WARM or NIGHT_INK: " + failures.keys.joinToString(", "))
        }
    }

    /**
     * Pins the gap between the two palettes so it cannot be closed by accident in the wrong
     * direction. [MapPalette.NIGHT_WARM] was designed to a contrast target; the day palette was
     * not, and measures worse. If a future edit ever makes night the weaker of the two against its
     * own ground, something has gone backwards.
     */
    @Test
    fun `night is not less legible against its ground than day is against its own`() {
        val worstDay = markers(MapPalette.DAY).values
            .minOf { contrastRatio(it, MapPalette.DAY_TILE_REFERENCE) }
        val nightRatio = contrastRatio(MapPalette.NIGHT_WARM, MapPalette.NIGHT_TILE_REFERENCE)
        if (nightRatio < worstDay) {
            fail("NIGHT_WARM (%.2f:1) is now worse than day's weakest marker (%.2f:1)".format(nightRatio, worstDay))
        }
    }

    /**
     * The stroke and the glyph are checked against the marker they sit on, not against the tile —
     * a hairline around a sighting dot is never seen against bare ground, so tile contrast says
     * nothing about it.
     */
    @Test
    fun `marks drawn on other marks are legible against those marks`() {
        val failures = mutableListOf<String>()
        val palettes = listOf("DAY" to MapPalette.DAY, "NIGHT" to MapPalette.NIGHT)
        for ((name, palette) in palettes) {
            val pairs = listOf(
                "sightingDotStroke on sightingDot" to (palette.sightingDotStroke to palette.sightingDot),
                "areaMarkerForeground on areaMarkerBackground" to
                    (palette.areaMarkerForeground to palette.areaMarkerBackground),
            )
            for ((label, pair) in pairs) {
                val ratio = contrastRatio(pair.first, pair.second)
                if (ratio < minimumMarkOnMarkContrast) {
                    failures += "%s: %s is %.2f:1, below %.1f:1".format(name, label, ratio, minimumMarkOnMarkContrast)
                }
            }
        }
        if (failures.isNotEmpty()) fail("Mark-on-mark contrast:\n  " + failures.joinToString("\n  "))
    }

    @Test
    fun `no two day markers a user must distinguish are too similar`() {
        val failures = mutableListOf<String>()
        val entries = markers(MapPalette.DAY).toList()
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val (nameA, a) = entries[i]
                val (nameB, b) = entries[j]
                val d = oklabDistance(a, b)
                if (d < minimumMarkerSeparation) {
                    failures += "%s and %s are %.4f apart, below %.4f".format(nameA, nameB, d, minimumMarkerSeparation)
                }
            }
        }
        if (failures.isNotEmpty()) fail("Marker separation:\n  " + failures.joinToString("\n  "))
    }

    /**
     * The night palette exists to be read on a dimmed ground, so [MapPalette.NIGHT_WARM] must be
     * lighter than every day marker, and [MapPalette.NIGHT_INK] darker than every day mark-on-mark
     * colour it replaces — the same "lift marks off a dimmed ground, invert the marks-on-marks"
     * property the earlier per-marker night palette held, now checked against the two shared
     * values instead of nine individually-tuned ones.
     */
    @Test
    fun `NIGHT_WARM is lighter and NIGHT_INK is darker than every day counterpart`() {
        val nightWarmLuminance = relativeLuminance(MapPalette.NIGHT_WARM)
        for ((marker, day) in markers(MapPalette.DAY)) {
            if (nightWarmLuminance <= relativeLuminance(day)) {
                fail("NIGHT_WARM is not lighter than DAY.$marker — night mode must lift marks off a dimmed ground")
            }
        }
        val nightInkLuminance = relativeLuminance(MapPalette.NIGHT_INK)
        for ((label, day) in listOf(
            "sightingDotStroke" to MapPalette.DAY.sightingDotStroke,
            "areaMarkerForeground" to MapPalette.DAY.areaMarkerForeground,
        )) {
            if (nightInkLuminance >= relativeLuminance(day)) {
                fail("NIGHT_INK should invert to a dark mark on a light marker, but is not darker than DAY.$label")
            }
        }
    }

    @Test
    fun `forMode selects the palettes, and the two are not the same object`() {
        assertNotEquals(MapPalette.DAY, MapPalette.NIGHT)
        assertNotEquals(MapPalette.forMode(night = false), MapPalette.forMode(night = true))
        if (MapPalette.forMode(night = false) != MapPalette.DAY) fail("forMode(false) must be DAY")
        if (MapPalette.forMode(night = true) != MapPalette.NIGHT) fail("forMode(true) must be NIGHT")
    }

    /**
     * A guard on the guards. Every real ratio and distance above sits well clear of its threshold,
     * so a broken calculation returning a large constant would pass all of them silently.
     */
    @Test
    fun `the contrast and distance calculations are correct`() {
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val extremes = contrastRatio(black, white)
        if (kotlin.math.abs(extremes - 21.0) > 0.01) fail("black on white should be 21.00:1, got %.2f".format(extremes))
        if (kotlin.math.abs(contrastRatio(white, white) - 1.0) > 0.0001) fail("a colour against itself should be 1:1")
        if (oklabDistance(white, white) > 1e-9) fail("a colour's distance from itself should be 0")
        if (oklabDistance(black, white) < 0.9) fail("black and white should be about 1.0 apart in Oklab L")
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return 0.2126 * channel(((argb shr 16) and 0xFF) / 255.0) +
            0.7152 * channel(((argb shr 8) and 0xFF) / 255.0) +
            0.0722 * channel((argb and 0xFF) / 255.0)
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun oklabDistance(a: Int, b: Int): Double {
        val x = oklab(a)
        val y = oklab(b)
        return sqrt((0..2).sumOf { (x[it] - y[it]).pow(2) })
    }

    private fun oklab(argb: Int): DoubleArray {
        fun toLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val r = toLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = toLinear(((argb shr 8) and 0xFF) / 255.0)
        val b = toLinear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }
}
