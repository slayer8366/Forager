package com.forager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * `ForagerTheme`'s type scale (understory-design-system.md, step 2). `Theme.kt` previously passed
 * no `typography` to `MaterialTheme` at all — tag 01's read of "148 `MaterialTheme.typography`
 * reads across the app" resolved through whatever `MaterialTheme`'s own bare default was, not
 * anything Forager owned or could change in one place.
 *
 * **Roboto Flex, ruled out.** The design's original plan named Roboto Flex as the carrier for
 * per-role weight *and* width axes, on the claim that it ships with no new font asset. Checked
 * against the real Compose text API before writing this: genuine variation-axis control
 * (`FontVariation.Settings`, `FontVariation.width(...)`) needs the variable font file bundled as an
 * app resource — the platform's own `sans-serif` alias (what `FontFamily.SansSerif` resolves to,
 * and what M3's stock `Typography()` already uses for every role here) gives no documented,
 * asset-free way to reach Roboto Flex's width axis specifically. Owner decision: stay on
 * [FontFamily.SansSerif] — matches the app's current, already-shipped behaviour exactly — and drop
 * the width-axis half of the plan rather than add a font asset to reach it.
 *
 * **Weight-only emphasis was already M3's own default, before this file did anything.** Read
 * directly from `androidx.compose.material3:material3-android:1.5.0-alpha26`'s
 * `TypeScaleTokens.kt`: every `*Emphasized` role already steps one weight above its base
 * counterpart in the stock tokens (Regular→Medium for Body/Display/Headline, Medium→Bold for
 * Label, a mix for Title) — the exact "emphasis through weight, not a new family" mechanism the
 * design doc asked for. `ForagerTypography` below is bare `Typography()`: there is no
 * Forager-specific value to add on top of a scale that already does this, and inventing one with no
 * stated reason would be exactly the speculative addition CLAUDE.md warns against. What [Typography]
 * being wired in here at all still closes: without it, `MaterialTheme.typography` reads across the
 * app were never guaranteed to be *this* scale — `MaterialExpressiveTheme`'s own `typography = null`
 * fallback is bare `Typography()` too as of this version (see its own `// TODO: replace with calls
 * to Expressive typography default`), so today the two are value-identical, and only supplying it
 * explicitly makes that a decision this file owns rather than an accident of the null path.
 */
val ForagerTypography: Typography = Typography()

/**
 * The compass strip's coordinate segment, per the design doc's Type section: "the compass strip
 * gets tabular figures... makes the strip a fixed width for a given format." Built here, unwired —
 * swapping it into `CompassElevationStripContent` is step 5's finish pass, not this one, and that
 * call site has broken from touch-interception regressions twice already (see CLAUDE.md's own
 * pitfall entry); changing it is out of scope for a theme-wiring step.
 *
 * [FontFamily.Monospace], not a bundled Roboto Mono: same reasoning as [ForagerTypography] above —
 * the platform's generic monospace alias gives every character, digits included, equal width with
 * no new asset, which is what "fixed width for a given format" actually needs. Derived from
 * [ForagerTypography]'s own `bodyMedium` rather than a size invented here, so it stays in step with
 * the scale it belongs to.
 */
val CompassCoordinateTextStyle: TextStyle = ForagerTypography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
