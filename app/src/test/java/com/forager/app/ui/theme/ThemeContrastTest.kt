package com.forager.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.pow

/**
 * WCAG contrast for every foreground/background pair [ForagerTheme] defines. Pure arithmetic over
 * two data objects -- no device, no Robolectric, no composition.
 *
 * This exists because the palette is the one part of the design that is both fully checkable
 * headlessly *and* the part where a plausible-looking edit does real harm: this app is read
 * outdoors in direct sun, and a token nudged for aesthetics can drop body text below legible
 * without anything failing to compile or render.
 *
 * What it does **not** establish: anything about the map. Overlay colours are drawn over a basemap
 * raster rather than a theme surface role, so contrast-against-surface does not reach them -- see
 * `MapPaletteTest` and the README's "Not yet verified" section.
 */
class ThemeContrastTest {

    /** WCAG 2.2 SC 1.4.3, normal-size body text. */
    private val bodyTextMinimum = 4.5

    /**
     * WCAG 2.2 SC 1.4.11, non-text contrast. [OutlineLight]/[OutlineDark] are borders and
     * dividers, never text, so 3:1 is the correct bar for them -- asserted at that threshold
     * rather than exempted, so a regression below it still fails.
     */
    private val nonTextMinimum = 3.0

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    private fun contrastRatio(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun textPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("onPrimary on primary", s.onPrimary, s.primary),
        Triple("onPrimaryContainer on primaryContainer", s.onPrimaryContainer, s.primaryContainer),
        Triple("onSecondary on secondary", s.onSecondary, s.secondary),
        Triple("onSecondaryContainer on secondaryContainer", s.onSecondaryContainer, s.secondaryContainer),
        Triple("onTertiary on tertiary", s.onTertiary, s.tertiary),
        Triple("onTertiaryContainer on tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer),
        Triple("onError on error", s.onError, s.error),
        Triple("onErrorContainer on errorContainer", s.onErrorContainer, s.errorContainer),
        Triple("onBackground on background", s.onBackground, s.background),
        Triple("onSurface on surface", s.onSurface, s.surface),
        Triple("onSurfaceVariant on surfaceVariant", s.onSurfaceVariant, s.surfaceVariant),
        // The bottom navigation bar's own container reads this step; its labels are onSurface.
        Triple("onSurface on surfaceContainer", s.onSurface, s.surfaceContainer),
        Triple("onSurface on surfaceContainerHighest", s.onSurface, s.surfaceContainerHighest),
        Triple("onSurface on surfaceContainerLowest", s.onSurface, s.surfaceContainerLowest),
    )

    private fun nonTextPairs(s: ColorScheme): List<Triple<String, Color, Color>> = listOf(
        Triple("outline on surface", s.outline, s.surface),
        Triple("outline on surfaceVariant", s.outline, s.surfaceVariant),
    )

    private fun check(schemeName: String, scheme: ColorScheme): List<String> {
        val failures = mutableListOf<String>()
        for ((label, fg, bg) in textPairs(scheme)) {
            val ratio = contrastRatio(fg, bg)
            if (ratio < bodyTextMinimum) {
                failures += "%s: %s is %.2f:1, below AA body text (%.1f:1)"
                    .format(schemeName, label, ratio, bodyTextMinimum)
            }
        }
        for ((label, fg, bg) in nonTextPairs(scheme)) {
            val ratio = contrastRatio(fg, bg)
            if (ratio < nonTextMinimum) {
                failures += "%s: %s is %.2f:1, below the non-text bar (%.1f:1)"
                    .format(schemeName, label, ratio, nonTextMinimum)
            }
        }
        return failures
    }

    @Test
    fun `every on-role pair clears WCAG AA in both themes`() {
        val failures = check("LightColors", LightColors) + check("DarkColors", DarkColors)
        if (failures.isNotEmpty()) {
            fail("Contrast failures:\n  " + failures.joinToString("\n  "))
        }
    }

    /**
     * A guard on the guard. Every ratio above is well clear of its threshold, which means the test
     * would still pass if [contrastRatio] were quietly broken to return a large constant. This
     * pins the arithmetic to two values whose answer is fixed by the spec: black on white is
     * exactly 21:1, and any colour against itself is exactly 1:1.
     */
    @Test
    fun `the contrast calculation itself is correct`() {
        val black = Color(0xFF000000)
        val white = Color(0xFFFFFFFF)
        val extremes = contrastRatio(black, white)
        if (kotlin.math.abs(extremes - 21.0) > 0.01) {
            fail("black-on-white should be 21.00:1, got %.2f:1".format(extremes))
        }
        val identical = contrastRatio(ForestGreen, ForestGreen)
        if (kotlin.math.abs(identical - 1.0) > 0.0001) {
            fail("a colour against itself should be 1.00:1, got %.2f:1".format(identical))
        }
    }
}
