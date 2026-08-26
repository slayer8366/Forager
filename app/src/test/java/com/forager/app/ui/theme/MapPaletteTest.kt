package com.forager.app.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.pow

/**
 * R5 in `docs/plans/understory-design-system.md`: the earlier draft of this test asserted only
 * that light and dark produced different ints, which passes if both are illegible. Three things
 * instead, none of which "differ" alone would catch:
 *
 * 1. **Derivation, not identity (R2).** Every returned colour that names a source role must not
 *    equal that role's own value — the check that fails if [MapPalette] is ever "simplified" back
 *    into returning `colorScheme.tertiary` (etc.) verbatim.
 * 2. **[MapPalette.Colors.searchCenter] is independent of `error`.** Two schemes differing only in
 *    `error` must still produce the same search-centre colour — a stronger property than "doesn't
 *    equal error today," which a coincidence could satisfy without the independence actually
 *    holding.
 * 3. **Contrast against a stated, provisional basemap luminance range**, not against a theme
 *    surface role — [ThemeContrastTest] can't reach these colours (they're drawn over a raster,
 *    not a Compose surface), so this is the one check standing between a marker colour and going
 *    illegible against every basemap this app ships. The range itself is a provisional constant,
 *    same status as `MotionPrecedence.MARKER_CLUSTERING_THRESHOLD` — not a measurement.
 *
 * Light vs dark differing is still asserted, but demoted to a necessary condition, not the
 * property under test.
 */
class MapPaletteTest {

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

    private fun contrastRatio(argb: Int, luminance: Double): Double {
        val la = relativeLuminance(argb)
        val lighter = maxOf(la, luminance)
        val darker = minOf(la, luminance)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `each derived colour differs from the role it derives from`() {
        for ((schemeName, scheme) in listOf("LightColors" to LightColors, "DarkColors" to DarkColors)) {
            val colors = MapPalette.derive(scheme)
            assertNotEquals("$schemeName: plannedTrip must not equal tertiary verbatim (R2)", scheme.tertiary.toArgb(), colors.plannedTrip)
            assertNotEquals("$schemeName: breadcrumb must not equal tertiary verbatim", scheme.tertiary.toArgb(), colors.breadcrumb)
            assertNotEquals("$schemeName: connector must not equal secondary verbatim", scheme.secondary.toArgb(), colors.connector)
            assertNotEquals("$schemeName: waypointMarker must not equal secondary verbatim", scheme.secondary.toArgb(), colors.waypointMarker)
            assertNotEquals("$schemeName: sightingDot must not equal onSurface verbatim", scheme.onSurface.toArgb(), colors.sightingDot)
            assertNotEquals("$schemeName: areaMarkerBackground must not equal primary verbatim", scheme.primary.toArgb(), colors.areaMarkerBackground)
        }
    }

    @Test
    fun `searchCenter does not move when error changes -- it is not bound to that role`() {
        val schemeWithRedError = lightColorScheme(error = Color(0xFFB33B3B))
        val schemeWithBlueError = lightColorScheme(error = Color(0xFF3B6EA5))

        val searchCenterA = MapPalette.derive(schemeWithRedError).searchCenter
        val searchCenterB = MapPalette.derive(schemeWithBlueError).searchCenter

        assertEqualsInt(
            "changing `error` alone must not move MapPalette.Colors.searchCenter (R2) -- a pin " +
                "meaning \"you searched here\" has no reason to move when text-failure red is retuned",
            searchCenterA,
            searchCenterB,
        )
    }

    private fun assertEqualsInt(message: String, expected: Int, actual: Int) {
        if (expected != actual) fail("$message: expected 0x${expected.toUInt().toString(16)}, got 0x${actual.toUInt().toString(16)}")
    }

    @Test
    fun `light and dark produce different colours -- necessary, not sufficient, on its own`() {
        val light = MapPalette.derive(LightColors)
        val dark = MapPalette.derive(DarkColors)

        val fields = listOf(
            "plannedTrip" to (light.plannedTrip to dark.plannedTrip),
            "searchCenter" to (light.searchCenter to dark.searchCenter),
            "connector" to (light.connector to dark.connector),
            "sightingDot" to (light.sightingDot to dark.sightingDot),
            "areaMarkerBackground" to (light.areaMarkerBackground to dark.areaMarkerBackground),
            "breadcrumb" to (light.breadcrumb to dark.breadcrumb),
            "waypointMarker" to (light.waypointMarker to dark.waypointMarker),
        )
        for ((name, pair) in fields) {
            assertNotEquals("$name must differ between light and dark", pair.first, pair.second)
        }
    }

    @Test
    fun `every marker colour clears a lenient contrast bar against the provisional basemap luminance range`() {
        val failures = mutableListOf<String>()
        for ((schemeName, scheme) in listOf("LightColors" to LightColors, "DarkColors" to DarkColors)) {
            val colors = MapPalette.derive(scheme)
            val named = listOf(
                "plannedTrip" to colors.plannedTrip,
                "searchCenter" to colors.searchCenter,
                "connector" to colors.connector,
                "sightingDot" to colors.sightingDot,
                "areaMarkerBackground" to colors.areaMarkerBackground,
                "breadcrumb" to colors.breadcrumb,
                "waypointMarker" to colors.waypointMarker,
            )
            for ((name, argb) in named) {
                // Against both ends of the range, not just one -- a colour only has to be
                // distinguishable from *whichever* basemap luminance it actually lands on, and a
                // real basemap raster isn't flat, so "clears one end" alone would pass a colour
                // that only reads against half of what it's actually drawn over.
                val worstCase = minOf(
                    contrastRatio(argb, MapPalette.PROVISIONAL_BASEMAP_LUMINANCE_RANGE.start.toDouble()),
                    contrastRatio(argb, MapPalette.PROVISIONAL_BASEMAP_LUMINANCE_RANGE.endInclusive.toDouble()),
                )
                if (worstCase < MapPalette.PROVISIONAL_BASEMAP_CONTRAST_MINIMUM) {
                    failures += "%s/%s: %.2f:1, below the provisional %.1f:1 bar"
                        .format(schemeName, name, worstCase, MapPalette.PROVISIONAL_BASEMAP_CONTRAST_MINIMUM)
                }
            }
        }
        assertTrue(
            "Marker colours below the provisional basemap contrast bar (see MapPalette's own doc " +
                "comment -- this bounds a regression, it does not establish real-basemap legibility, " +
                "which is a hardware question):\n  " + failures.joinToString("\n  "),
            failures.isEmpty(),
        )
    }
}
