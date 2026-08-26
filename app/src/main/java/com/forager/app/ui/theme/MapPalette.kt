package com.forager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.pow

/**
 * Understory step 3 / R2: the seven raw `Int` colours [SightingsMap] draws on the native MapLibre
 * canvas, which cannot read a Compose [ColorScheme] directly — see this file's own "what this does
 * not do" note. Before this, those seven were hardcoded constants with no dark-theme variant at
 * all (tag 04).
 *
 * **Derives from the scheme; is not the scheme (R2).** The first attempt at this fix promoted the
 * map's own blue and red to *be* `tertiary`/`error` and had this object read those roles back
 * verbatim. That re-creates tag 08's defect semantically: both colours are still drawn on the map,
 * so retuning `error` for on-screen text contrast would silently move the search-centre pin, and
 * the planned-trip marker would be bound to whatever `tertiary` becomes. So:
 * - [Colors.plannedTrip] derives from [ColorScheme.tertiary] — same hue family, its own tone and
 *   saturation — rather than returning it. [ThemeCompletenessTest]-adjacent: this is the one
 *   colour here that's allowed to read the promoted role at all.
 * - [Colors.searchCenter] does **not** read [ColorScheme.error]. Its hue is this object's own
 *   anchor, independent of any role; only its lightness varies with the scheme (via [ColorScheme]'s
 *   own surface luminance, not a separate dark-theme flag), so it still adapts between light and
 *   dark without being bound to a role a future contrast fix could move out from under it.
 *
 * The other five (connector, sighting dot, area marker, breadcrumb, waypoint) derive from whichever
 * existing role shares their hue family, each with its own hue/saturation/value shift so that two
 * markers deriving from the same role (planned-trip and breadcrumb both start from `tertiary`)
 * still land on visibly distinct colours — see [MapPaletteTest] for the derivation-not-identity
 * assertion this exists to satisfy.
 */
object MapPalette {

    /**
     * Provisional — see [MapPaletteTest], and README's "Not yet verified". This app's four
     * basemaps (light topo, dark aerial imagery, and two in between) were never sampled for an
     * actual luminance histogram; this is an estimate of the band they plausibly span, in the same
     * status as `MotionPrecedence.MARKER_CLUSTERING_THRESHOLD` — a starting point for a check to
     * bound regressions against, not a measured fact about real tiles.
     */
    val PROVISIONAL_BASEMAP_LUMINANCE_RANGE: ClosedFloatingPointRange<Float> = 0.20f..0.60f

    /** Lenient on purpose: a real basemap raster isn't flat, so this bounds a regression, not a real-tile guarantee. See [PROVISIONAL_BASEMAP_LUMINANCE_RANGE]. */
    const val PROVISIONAL_BASEMAP_CONTRAST_MINIMUM: Double = 1.5

    data class Colors(
        val plannedTrip: Int,
        val searchCenter: Int,
        val connector: Int,
        val sightingDot: Int,
        val areaMarkerBackground: Int,
        val breadcrumb: Int,
        val waypointMarker: Int,
    )

    /**
     * The anchor hue for [Colors.searchCenter] — the map's own long-standing search-centre red,
     * kept as this object's own constant rather than read from [ColorScheme.error] (see class doc
     * comment). Same hue/saturation the pin has always had; only value (lightness) is derived from
     * the scheme below.
     */
    private const val SEARCH_CENTER_HUE = 4f
    private const val SEARCH_CENTER_SATURATION = 0.62f

    fun derive(colorScheme: ColorScheme): Colors {
        // Not isSystemInDarkTheme(): a caller-supplied ColorScheme *is* "the scheme" this object is
        // meant to derive from, so darkness is read off it rather than from a second side-channel
        // that could disagree with the one actually driving these colours.
        val isDark = colorScheme.surface.luminance() < 0.5f

        return Colors(
            plannedTrip = shifted(colorScheme.tertiary, hueShift = 0f, saturationScale = 1.15f, valueScale = if (isDark) 0.9f else 1.05f),
            searchCenter = pushClearOfBasemapDeadZone(SEARCH_CENTER_HUE, SEARCH_CENTER_SATURATION, if (isDark) 0.85f else 0.55f),
            connector = shifted(colorScheme.secondary, hueShift = -6f, saturationScale = 1.0f, valueScale = if (isDark) 0.95f else 0.85f),
            // valueScale != 1.0 rather than a pure pass-through: onSurface itself must never come
            // back out unchanged, or this collapses to exactly the identity R2 forbids.
            sightingDot = shifted(colorScheme.onSurface, hueShift = 0f, saturationScale = 1.0f, valueScale = if (isDark) 0.9f else 0.85f),
            areaMarkerBackground = shifted(colorScheme.primary, hueShift = 0f, saturationScale = 1.05f, valueScale = if (isDark) 1.1f else 0.9f),
            // +14deg / higher saturation than plannedTrip's own tertiary-derived shift, so the two
            // never collide even though both start from the same role — see class doc comment.
            breadcrumb = shifted(colorScheme.tertiary, hueShift = 14f, saturationScale = 1.5f, valueScale = if (isDark) 1.05f else 0.95f),
            waypointMarker = shifted(colorScheme.secondary, hueShift = 22f, saturationScale = 1.1f, valueScale = if (isDark) 1.05f else 0.95f),
        )
    }

    /**
     * [source] moved through HSV space by the given shifts, guaranteeing the result is a genuine
     * derivation rather than the role's own value passed through — every call site below applies a
     * non-zero saturation or value scale (or both), so no [Colors] field can silently collapse back
     * to `source.toArgb()`. [MapPaletteTest] asserts that property directly rather than trusting it.
     *
     * Hand-rolled RGB<->HSV rather than `android.graphics.Color.colorToHSV`/`HSVToColor`:
     * [MapPalette] is meant to be headlessly testable like [ThemeContrastTest] (pure Kotlin, no
     * device, no Robolectric), and the `android.graphics.Color` conversion methods are native/stub
     * calls that throw under a plain JVM unit test with no Robolectric shadow registered. Compose's
     * own [Color]/[toArgb]/[luminance] are pure Kotlin already; this keeps the whole derivation
     * that way rather than pulling in the one Android-runtime-dependent piece for an HSV shift that
     * plain arithmetic does just as well.
     */
    private fun shifted(source: Color, hueShift: Float, saturationScale: Float, valueScale: Float): Int {
        val (h, s, v) = rgbToHsv(source)
        val newHue = ((h + hueShift) % 360f).let { if (it < 0f) it + 360f else it }
        val newSat = (s * saturationScale).coerceIn(0f, 1f)
        val newValue = (v * valueScale).coerceIn(0f, 1f)
        return pushClearOfBasemapDeadZone(newHue, newSat, newValue)
    }

    /**
     * A marker painted at a luminance close to a typical basemap's own is the actual failure
     * [MapPaletteTest] exists to catch — a hue/saturation shift alone (the derivations above) can
     * still land in that band, since neither axis is luminance. Rather than hand-tune each of the
     * seven role-derived shifts against a threshold that is itself provisional (see
     * [PROVISIONAL_BASEMAP_LUMINANCE_RANGE]'s own doc comment) and re-tune them every time that
     * range is revised, value is nudged toward whichever extreme (black or white) the colour is
     * already closer to, in fixed steps, until it clears [PROVISIONAL_BASEMAP_CONTRAST_MINIMUM]
     * against both ends of the range or hits the extreme — so the property holds by construction
     * for any hue/saturation this file derives, not by having gotten seven magic numbers right.
     */
    private fun pushClearOfBasemapDeadZone(hue: Float, saturation: Float, initialValue: Float): Int {
        val rangeMid = (PROVISIONAL_BASEMAP_LUMINANCE_RANGE.start + PROVISIONAL_BASEMAP_LUMINANCE_RANGE.endInclusive) / 2.0

        var value = initialValue
        var saturationRemaining = saturation
        var argb = hsvToArgb(hue, saturationRemaining, value)
        var stepsRemaining = 40

        while (contrastAgainstBasemapRange(argb) < PROVISIONAL_BASEMAP_CONTRAST_MINIMUM && stepsRemaining > 0) {
            // Direction is read off actual relative luminance each iteration, not the raw HSV
            // value the loop started from -- luminance weights the RGB channels unevenly (see
            // relativeLuminance), so "value" and luminance can disagree about which side of the
            // range's midpoint a colour is actually on.
            val goingLighter = relativeLuminance(argb) >= rangeMid
            if (goingLighter) {
                if (value < 1f) {
                    value = (value + 0.05f).coerceAtMost(1f)
                } else {
                    // value is already maxed: a fully saturated hue at value=1 is not white, so
                    // the only way left to get lighter is desaturating toward it.
                    saturationRemaining = (saturationRemaining - 0.1f).coerceAtLeast(0f)
                }
            } else {
                value = (value - 0.05f).coerceAtLeast(0f)
            }
            argb = hsvToArgb(hue, saturationRemaining, value)
            stepsRemaining--
        }
        return argb
    }

    private fun relativeLuminance(argb: Int): Double {
        fun channel(c: Int): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    private fun contrastAgainstBasemapRange(argb: Int): Double {
        val markerLuminance = relativeLuminance(argb)
        fun ratio(other: Double): Double {
            val lighter = maxOf(markerLuminance, other)
            val darker = minOf(markerLuminance, other)
            return (lighter + 0.05) / (darker + 0.05)
        }
        return minOf(
            ratio(PROVISIONAL_BASEMAP_LUMINANCE_RANGE.start.toDouble()),
            ratio(PROVISIONAL_BASEMAP_LUMINANCE_RANGE.endInclusive.toDouble()),
        )
    }

    private data class Hsv(val h: Float, val s: Float, val v: Float)

    private fun rgbToHsv(color: Color): Hsv {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val saturation = if (max == 0f) 0f else delta / max
        return Hsv(hue, saturation, max)
    }

    private fun hsvToArgb(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val hPrime = hue / 60f
        val x = c * (1f - abs(hPrime % 2f - 1f))
        val (r1, g1, b1) = when {
            hPrime < 1f -> Triple(c, x, 0f)
            hPrime < 2f -> Triple(x, c, 0f)
            hPrime < 3f -> Triple(0f, c, x)
            hPrime < 4f -> Triple(0f, x, c)
            hPrime < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = value - c
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
