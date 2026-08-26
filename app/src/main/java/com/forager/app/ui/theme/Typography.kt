package com.forager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.forager.app.R

/**
 * Understory step 2 / tag 02: the app had no [Typography] before this, so every
 * `MaterialTheme.typography` read resolved to Material's stock Roboto scale, and the fifteen
 * `*Emphasized` roles the 1.5.0 line adds were all unclaimed.
 *
 * Roboto Flex (OFL, `google/fonts` — `ofl/robotoflex`) is the carrier, not a hand-rolled weight
 * bump: Expressive's typographic argument is emphasis through weight *and width* against a
 * neighbour in the same role, not a heavier typeface everywhere. Material's own baseline already
 * bakes a heavier weight into every `*Emphasized` token relative to its base
 * (`TypeScaleTokens.kt`'s `*EmphasizedWeight` constants) — the [Typography] convenience
 * constructor below that takes only a [FontFamily] keeps that relationship and simply supplies
 * this family as the default for every role, base and emphasized alike, rather than overriding
 * thirty `TextStyle`s by hand to get the same weights back.
 *
 * The variable font exposes four fixed instances, matched by nominal [FontWeight] the way any
 * family with multiple statically-hinted weights would be:
 * - regular / medium / bold, all at width 100 (the "normal" run of text at any weight Material
 *   asks for).
 * - [FontWeight.W900] is reused as a *slot key*, not a literal black weight: it holds wght 700 at
 *   width 90, the narrow-and-bold instance the design calls for on the three `display*Emphasized`
 *   roles specifically (`docs/plans/understory-design-system.md` §2S's spec sheet). Compose
 *   selects a [FontFamily] entry by declared weight+style only, not by variation axis, so getting
 *   a distinct width for just those three roles means claiming an otherwise-unused weight bucket
 *   for it and asking for that bucket explicitly on those roles below — reusing W700 with a
 *   different width would collide with every other bold role in the scale.
 */
private val RobotoFlexFamily = FontFamily(
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
        ),
    ),
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
        ),
    ),
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
        ),
    ),
    // The "display, emphasized" slot -- see the class doc comment above.
    Font(
        resId = R.font.roboto_flex,
        weight = FontWeight.W900,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(90f),
        ),
    ),
)

val ForagerTypography = Typography(fontFamily = RobotoFlexFamily).let { base ->
    base.copy(
        displayLargeEmphasized = base.displayLargeEmphasized.copy(fontWeight = FontWeight.W900),
        displayMediumEmphasized = base.displayMediumEmphasized.copy(fontWeight = FontWeight.W900),
        displaySmallEmphasized = base.displaySmallEmphasized.copy(fontWeight = FontWeight.W900),
    )
}

/**
 * The one exception to [RobotoFlexFamily]: the compass strip's coordinate segment (heading,
 * elevation, MGRS grid reference, decimal lat/long on one line). `CompassElevationStripContent`'s
 * own comments record two hardware rounds fighting that line's width -- a two-line wrap, then an
 * ellipsis fix, then a touch-interception regression caused by the first attempt. Proportional
 * digits are part of that: the line's rendered width changes as the digits change, so it fits
 * today and may not tomorrow.
 *
 * [FontFamily.Monospace] rather than a second bundled variable font (Roboto Mono): a monospace
 * family gives every glyph, digits included, the same advance width *by construction* — that is
 * what "tabular figures" means for this line, and it doesn't need Roboto Mono's own variable axes
 * to get it. One embedded font is enough for this app; a second one earns its place only if this
 * generic family turns out not to, which is a device-gate question, not one this file can answer.
 */
val CoordinateMonospace: FontFamily = FontFamily.Monospace
