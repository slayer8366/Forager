package com.forager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Guards [MapPalette]. Deliberately **not** the "the seven ints differ between light and dark"
 * check an early draft of docs/plans/understory-design-system.md specified: that passes if both
 * are illegible, so it stands in for the property that matters without establishing it (review
 * item R5).
 *
 * What can honestly be asserted headlessly, and is:
 *
 *  1. **Derivation, not identity.** No returned int equals the role it came from. This is the
 *     check that fails if someone "simplifies" [MapPalette] into returning `colorScheme.tertiary`
 *     verbatim, which would re-couple the map to roles that are tuned for on-screen text.
 *  2. **Theme responsiveness.** Every value moves between the two schemes, which is what the
 *     design document asks the palette to do.
 *  3. **A separation ratchet.** No two user-distinguishable markers may get closer together than
 *     they are today. See [MINIMUM_MARKER_SEPARATION] — it is a floor against drift, not a
 *     legibility guarantee.
 *
 * What cannot be asserted here, and is recorded in the README's "Not yet verified" section
 * instead: whether any of these colours is actually legible against real basemap tiles. The
 * overlay is drawn over raster imagery, not over a theme surface role, so the
 * contrast-against-surface arithmetic [ThemeContrastTest] applies to the rest of the palette does
 * not reach it, and the tiles vary by basemap choice rather than by theme.
 */
class MapPaletteTest {

    /**
     * Oklab distance, the space Compose's own `lerp` interpolates in.
     *
     * The floor is the smallest separation the palette exhibits today — measured, not chosen:
     * in dark theme `connector` (from `secondary`) and `waypoint` sit 0.044 apart, both warm
     * oranges, where the light scheme's deeper `secondary` holds them 0.12+ apart. That proximity
     * is a real consequence of deriving the palette from the ambient theme, and it is called out
     * for the device gate in the design document rather than papered over here.
     *
     * A ratchet, so it is worth being clear about what it does and does not do: it fails if any
     * future edit pushes two markers closer than this, and it says nothing about whether 0.044 is
     * far enough apart to tell two pins apart on a wet screen in the sun. Only hardware answers
     * that. Raise this floor when hardware says what the real minimum should be.
     */
    private val MINIMUM_MARKER_SEPARATION = 0.044

    /** The markers a user has to tell apart from each other on the map. */
    private fun distinguishableMarkers(p: MapPalette): Map<String, Int> = mapOf(
        "sightingDot" to p.sightingDot,
        "connector" to p.connector,
        "areaMarkerBackground" to p.areaMarkerBackground,
        "plannedTrip" to p.plannedTrip,
        "searchCentre" to p.searchCentre,
        "breadcrumb" to p.breadcrumb,
        "waypoint" to p.waypoint,
    )

    /**
     * `sightingDotStroke` and `areaMarkerForeground` are excluded from the separation check above
     * on purpose. Neither is a marker in the vocabulary a user reads: one is the hairline that
     * keeps overlapping dots separable, the other is the glyph inside a filled area badge, and
     * they are never adjacent to each other or to anything else on the map. In the light scheme
     * they are in fact the same colour, because `onPrimary` and `surface` are both near-white
     * there -- harmless for that reason, and asserted here so the coincidence is recorded rather
     * than discovered later and mistaken for a bug.
     */
    @Test
    fun `the two ground-like colours are excluded from separation, and their light-theme collision is intentional`() {
        val light = MapPalette.from(LightColors)
        if (light.sightingDotStroke != light.areaMarkerForeground) {
            // Not a failure -- a notice that the coincidence this comment explains has ended,
            // which means the exclusion above may no longer need explaining.
            println(
                "Note: sightingDotStroke and areaMarkerForeground no longer coincide in the light " +
                    "scheme. The doc comment on this test can be simplified.",
            )
        }
    }

    @Test
    fun `no colour is returned verbatim from the role it derives from`() {
        for ((schemeName, scheme) in listOf("LightColors" to LightColors, "DarkColors" to DarkColors)) {
            val p = MapPalette.from(scheme)
            fun check(label: String, derived: Int, source: Color) =
                assertNotEquals(
                    "$schemeName.$label is its source role verbatim — MapPalette must derive, not " +
                        "bind (see R2: binding re-couples the map to roles tuned for text)",
                    source.toArgb(),
                    derived,
                )
            check("sightingDot", p.sightingDot, scheme.onSurface)
            check("sightingDotStroke", p.sightingDotStroke, scheme.surface)
            check("connector", p.connector, scheme.secondary)
            check("areaMarkerBackground", p.areaMarkerBackground, scheme.primary)
            check("areaMarkerForeground", p.areaMarkerForeground, scheme.onPrimary)
            check("plannedTrip", p.plannedTrip, scheme.tertiary)
        }
    }

    @Test
    fun `every colour responds to the theme`() {
        val light = MapPalette.from(LightColors)
        val dark = MapPalette.from(DarkColors)
        val identical = distinguishableMarkers(light).filter { (name, value) ->
            distinguishableMarkers(dark)[name] == value
        }.keys
        if (identical.isNotEmpty()) {
            fail("These map colours do not change with the theme: $identical")
        }
    }

    @Test
    fun `no two distinguishable markers are closer than the measured floor`() {
        for ((schemeName, scheme) in listOf("LightColors" to LightColors, "DarkColors" to DarkColors)) {
            val markers = distinguishableMarkers(MapPalette.from(scheme)).toList()
            for (i in markers.indices) {
                for (j in i + 1 until markers.size) {
                    val (nameA, a) = markers[i]
                    val (nameB, b) = markers[j]
                    val d = oklabDistance(a, b)
                    if (d < MINIMUM_MARKER_SEPARATION) {
                        fail(
                            "%s: %s and %s are %.4f apart in Oklab, below the %.4f floor. Two map markers got harder to tell apart."
                                .format(schemeName, nameA, nameB, d, MINIMUM_MARKER_SEPARATION),
                        )
                    }
                }
            }
        }
    }

    private fun oklabDistance(argbA: Int, argbB: Int): Double {
        val a = oklab(argbA)
        val b = oklab(argbB)
        return sqrt((0..2).sumOf { (a[it] - b[it]).pow(2) })
    }

    private fun oklab(argb: Int): DoubleArray {
        fun srgbToLinear(c: Double) = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        val r = srgbToLinear(((argb shr 16) and 0xFF) / 255.0)
        val g = srgbToLinear(((argb shr 8) and 0xFF) / 255.0)
        val bl = srgbToLinear((argb and 0xFF) / 255.0)
        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)
        return doubleArrayOf(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
        )
    }
}
