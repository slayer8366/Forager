package com.forager.app.ui.log

import androidx.compose.ui.graphics.Color
import com.forager.app.ui.theme.Bark
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * B3 (2026-08-27): the remove-photo glyph on [LogPhotoThumbnail] was near-invisible against pale
 * photo content, because its color tracked the theme rather than anything guaranteed to contrast
 * against an arbitrary photo. The fix draws a [androidx.compose.material3.MaterialTheme.colorScheme.scrim]
 * circle behind a fixed white glyph, at [REMOVE_GLYPH_SCRIM_ALPHA] — imported from the production
 * file itself, not copied, so this fails if that value ever drifts from what's actually drawn.
 *
 * Pure arithmetic, no Robolectric or composition — same shape as [com.forager.app.ui.theme.ThemeContrastTest],
 * which this duplicates the small WCAG contrast formulas from rather than sharing (that class keeps
 * them private). What a rendered pixel-capture test can't cheaply give here that this can: the exact
 * worst-case backdrop is unknowable (any real photo), so this checks the two extremes analytically —
 * a pure white backdrop (the owner's own reported case, "light map tiles") and a pure black one —
 * rather than picking one arbitrary test photo and calling it representative.
 */
class LogPhotoThumbnailRemoveAffordanceContrastTest {

    /** [Bark] — this theme's own explicit `scrim` value in both color schemes, per [com.forager.app.ui.theme.ThemeCompletenessTest]'s requirement that every role the UI reads be intentional, not a Material baseline passthrough. */
    private val scrim = Bark
    private val glyph = Color.White

    /** WCAG 2.2 SC 1.4.11, non-text contrast — the same bar [com.forager.app.ui.theme.ThemeContrastTest] applies to icon/control-shaped elements. */
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

    /** A flat alpha composite of the scrim over an opaque backdrop, per sRGB channel — matching how this circle is actually drawn (a semi-transparent fill over whatever the photo already rendered). */
    private fun compositeOverBackdrop(backdrop: Color): Color = Color(
        red = scrim.red * REMOVE_GLYPH_SCRIM_ALPHA + backdrop.red * (1 - REMOVE_GLYPH_SCRIM_ALPHA),
        green = scrim.green * REMOVE_GLYPH_SCRIM_ALPHA + backdrop.green * (1 - REMOVE_GLYPH_SCRIM_ALPHA),
        blue = scrim.blue * REMOVE_GLYPH_SCRIM_ALPHA + backdrop.blue * (1 - REMOVE_GLYPH_SCRIM_ALPHA),
    )

    @Test
    fun `the white glyph on the scrim clears non-text contrast over a pale backdrop`() {
        val composited = compositeOverBackdrop(Color.White)
        val ratio = contrastRatio(glyph, composited)
        assertTrue("contrast over a pale backdrop was $ratio, need >= $nonTextMinimum", ratio >= nonTextMinimum)
    }

    @Test
    fun `the white glyph on the scrim clears non-text contrast over a dark backdrop`() {
        val composited = compositeOverBackdrop(Color.Black)
        val ratio = contrastRatio(glyph, composited)
        assertTrue("contrast over a dark backdrop was $ratio, need >= $nonTextMinimum", ratio >= nonTextMinimum)
    }

    @Test
    fun `mutation check — a much lower scrim alpha fails against a pale backdrop`() {
        // Not this file's real REMOVE_GLYPH_SCRIM_ALPHA — a stand-in for "someone weakens the
        // scrim later," proving these two tests above would actually catch that rather than
        // passing regardless of what the constant says.
        val weakAlpha = 0.15f
        val composited = Color(
            red = scrim.red * weakAlpha + 1f * (1 - weakAlpha),
            green = scrim.green * weakAlpha + 1f * (1 - weakAlpha),
            blue = scrim.blue * weakAlpha + 1f * (1 - weakAlpha),
        )
        val ratio = contrastRatio(glyph, composited)
        assertTrue(
            "expected a much weaker scrim to fail against a pale backdrop, but it scored $ratio",
            ratio < nonTextMinimum,
        )
    }
}
