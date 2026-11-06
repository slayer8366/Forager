package com.zynergylabs.forager.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.zynergylabs.forager.app.R

/**
 * Noto Sans, bundled — `app/src/main/res/font/notosans_{regular,medium,bold}.ttf`, fetched from
 * Google's own font-serving CDN (`fonts.gstatic.com`, the same files `fonts.googleapis.com/css2`
 * serves), the three weights [androidx.compose.material3.tokens.TypeScaleTokens] actually uses
 * across the 30 base/emphasized roles. A bundled static font, not the downloadable-fonts API
 * (`GoogleFont.Provider`): that path resolves through Google Play Services' Font Provider at
 * runtime, which this project's Robolectric-driven test suite has no access to, and a family
 * central to *every* screen isn't the place to carry that uncertainty. ~1.6MB across the three
 * files — accepted; a distinct, deliberately-chosen family for the whole app was worth it.
 */
private val NotoSans = FontFamily(
    Font(R.font.notosans_regular, FontWeight.Normal),
    Font(R.font.notosans_medium, FontWeight.Medium),
    Font(R.font.notosans_bold, FontWeight.Bold),
)

/**
 * `ForagerTheme`'s type scale (understory-design-system.md, step 2). `Theme.kt` previously passed
 * no `typography` to `MaterialTheme` at all — tag 01's read of "148 `MaterialTheme.typography`
 * reads across the app" resolved through whatever `MaterialTheme`'s own bare default was, not
 * anything Forager owned or could change in one place.
 *
 * **Roboto Flex, ruled out; Noto Sans, chosen instead.** The design's original plan named Roboto
 * Flex for per-role weight *and* width axes, on the claim that it ships with no new font asset.
 * Checked against the real Compose text API before writing anything: genuine variation-axis control
 * needs the variable font bundled as an app resource — there is no documented, asset-free way to
 * reach Roboto Flex's width axis through the platform's generic `sans-serif` alias. Once bundling a
 * font asset was back on the table, Noto Sans (and, for long-form reading content, Noto Serif)
 * was the owner's specific choice: a deliberately-picked family for the whole app, not the
 * width-axis mechanism the original plan wanted. Noto Sans is `Typography`'s `fontFamily`
 * constructor parameter, which — verified against `TypographyTokens.kt`'s real
 * implementation — swaps only the family per role; each role's own weight, size, line height and
 * letter spacing still come from the stock tokens.
 *
 * **Weight-only emphasis was already M3's own default, before this file did anything.** Read
 * directly from `androidx.compose.material3:material3-android:1.5.0-alpha26`'s
 * `TypeScaleTokens.kt`: every `*Emphasized` role already steps one weight above its base
 * counterpart in the stock tokens (Regular→Medium for Body/Display/Headline, Medium→Bold for
 * Label, a mix for Title) — the exact "emphasis through weight, not a new family" mechanism the
 * design doc asked for, and unaffected by swapping in [NotoSans].
 */
val ForagerTypography: Typography = Typography(fontFamily = NotoSans)

/**
 * The compass strip's coordinate segment, per the design doc's Type section: "the compass strip
 * gets tabular figures... makes the strip a fixed width for a given format." Built here, unwired —
 * swapping it into `CompassElevationStripContent` is step 5's finish pass, not this one, and that
 * call site has broken from touch-interception regressions twice already (see CLAUDE.md's own
 * pitfall entry); changing it is out of scope for a theme-wiring step.
 *
 * [FontFamily.Monospace], not a Noto monospace cut: no monospace member of the Noto family was part
 * of the owner's request, and the platform's generic monospace alias already gives every character,
 * digits included, equal width with no further asset — which is what "fixed width for a given
 * format" actually needs. Derived from [ForagerTypography]'s own `bodyMedium` rather than a size
 * invented here, so it stays in step with the scale it belongs to.
 *
 * Kept, unused, for the design system (dispatch 2026-09-28-658; the owner: "Remove what costs, keep
 * plans"): it costs nothing in the APK.
 */
val CompassCoordinateTextStyle: TextStyle = ForagerTypography.bodyMedium.copy(fontFamily = FontFamily.Monospace)

// Removed (dispatch 2026-09-28-658; the owner: "Remove what costs, keep plans"): LongFormSerifTextStyle
// and its Noto Serif family. Unwired since it was written, and the only reason
// res/font/notoserif_regular.ttf (444,064 bytes) shipped in every APK, release builds being unshrunk.
// A long-form reading surface that wants a serif brings the font back with its first call site.
