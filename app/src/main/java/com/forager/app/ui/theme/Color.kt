package com.forager.app.ui.theme

import androidx.compose.ui.graphics.Color

val ForestGreen = Color(0xFF2E5339)
val MossGreen = Color(0xFF5C8A5E)
val Mushroom = Color(0xFFC97B3D)
val Cream = Color(0xFFEDE3D0)
val Bark = Color(0xFF3B2E24)

/**
 * Dark theme's background/surface. Replaces [Bark] there: a warm brown reads as a colored tint,
 * not the neutral gray Android's own dark theme convention uses, which is what dark mode was
 * asked to match. [Bark] stays in use for light-theme text/containers, where a warm dark tone is
 * the point.
 */
val NeutralGray900 = Color(0xFF1B1B1B)

/** Dark theme's surfaceVariant — one step lighter than [NeutralGray900], same gray family. */
val NeutralGray800 = Color(0xFF2C2C2C)

/** Dark theme's onSurfaceVariant: dimmer than [Cream], for secondary text on [NeutralGray800]. */
val NeutralGray200 = Color(0xFFCFC9BE)

/**
 * primaryContainer/surfaceVariant roles below exist because Compose fills any role a
 * [androidx.compose.material3.lightColorScheme]/[androidx.compose.material3.darkColorScheme] call
 * doesn't name with Material3's own baseline (purple-tinted) default — which several roles this
 * app actually renders with (`surfaceVariant`, `onSurfaceVariant`, `primaryContainer`) were
 * silently doing, clashing with the brown/green/orange palette everywhere else. These are muted
 * steps of the brand hues instead, not new colors: a tonal-container convention Material3 itself
 * follows, applied to this app's own palette rather than the default one.
 */
val MossGreenContainerDark = Color(0xFF3D5A40)
val MossGreenContainerLight = Color(0xFFD9E8D2)
val WarmOnSurfaceVariantLight = Color(0xFF5B5347)

/**
 * Light theme's background/surface. [Cream] used to fill this role directly — a saturated warm
 * tan is fine as an accent but, spread across the entire screen with nothing lighter above it,
 * reads as a flat, dated wash rather than the near-white neutral modern Material 3 light theme
 * actually uses ("cigarette stain yellow", reported against the previous version). [Cream] moved
 * to `surfaceVariant` instead — a tonal step, not the dominant surface.
 */
val NearWhite = Color(0xFFFAF8F3)

// ---------------------------------------------------------------------------
// Step 1 of docs/plans/understory-design-system.md: the roles the UI reads but
// the theme never set.
//
// Before this block, ForagerTheme named 12 roles while the UI read 15. The seven
// it never named -- error, errorContainer, onErrorContainer, outline,
// surfaceContainer, tertiaryContainer, onTertiaryContainer -- fell through to
// Material3's own baseline (purple/red) palette, which is the same leak the
// primaryContainer/surfaceVariant comment above records catching once already.
// `error` alone had 13 read sites rendering Material's red rather than anything
// this app chose.
//
// Every value below clears WCAG AA against the role it is paired with; the
// ratios are asserted by ThemeContrastTest rather than trusted here.
// ---------------------------------------------------------------------------

/** Pure white. Only ever a foreground, on the three accents dark enough to carry it. */
val PureWhite = Color(0xFFFFFFFF)

/**
 * Dark theme's `primary`. [MossGreen] used to hold both `primary` and `tertiary` there, so the
 * two roles resolved to one colour and any component pairing them had no contrast at all --
 * "tag 08" in the design doc. This is the same brand hue lightened, so dark `primary` can move
 * off [MossGreen] without introducing a fourth green.
 */
val Lichen = Color(0xFFA8CBA0)

/** The `onPrimary`/`onPrimaryContainer` counterpart to [Lichen] and [MossGreenContainerLight]. */
val ForestDeep = Color(0xFF16301D)

/**
 * `secondary` in light theme. [Mushroom] itself is too light to carry white text (3.0:1, below
 * AA for body copy), so the role gets a deepened step of the same hue rather than a new colour;
 * [Mushroom] stays the brand accent it always was.
 */
val MushroomDeep = Color(0xFFA2612C)

/** `secondary` in dark theme -- [Mushroom] lightened the way [Lichen] lightens the green. */
val MushroomLight = Color(0xFFE5A76B)

val MushroomContainerLight = Color(0xFFF6DFC8)
val MushroomContainerDark = Color(0xFF6B421A)
val MushroomOnContainerLight = Color(0xFF3A2008)
val MushroomOnContainerDark = Color(0xFF452507)

/**
 * `tertiary`. Not a new hue: this is the planned-trip marker's existing blue, promoted out of
 * `SightingsMap.kt` into the theme so the palette gains a cool third note and dark theme stops
 * having `primary` and `tertiary` set to the same green.
 *
 * **The map does not read this role back.** `MapPalette` *derives* its blue from `tertiary`
 * rather than returning it, so retuning this value for on-screen contrast cannot move what the
 * map draws -- see that object's doc comment, and "R2" in the design doc for why identity here
 * would re-create tag 08's defect in semantic form.
 */
val TrailBlue = Color(0xFF3B6EA5)

val TrailBlueLight = Color(0xFFA8C4E4)
val TrailBlueContainerLight = Color(0xFFD7E3F2)
val TrailBlueContainerDark = Color(0xFF2B4763)
val TrailBlueOnContainerLight = Color(0xFF12283F)

/**
 * `error`. Sourced from the search-centre pin's red the same way [TrailBlue] is sourced from the
 * trip marker's blue -- and, the same way, **not** bound to it: the search centre gets its own
 * derivation in `MapPalette`. A pin meaning "you searched here" and text meaning "something
 * failed" have no reason to move together.
 */
val SignalRed = Color(0xFFB33B3B)

val SignalRedLight = Color(0xFFE89A9A)
val SignalRedContainerLight = Color(0xFFF5DCDC)
val SignalRedContainerDark = Color(0xFF6B2222)
val SignalRedOnContainerLight = Color(0xFF3A0F0F)
val SignalRedOnContainerDark = Color(0xFF4A1212)

/**
 * The `surfaceContainer*` ramp. Material3 defines five tonal steps between `surface` and the
 * highest container; this app read exactly one of them (`surfaceContainer`, the bottom
 * navigation bar's own container, `AvailabilityScreen.kt`) and got Material's purple-tinted
 * baseline for it. The ramp is warm neutral in light, matching [Cream]/[NearWhite], and plain
 * grey in dark, matching the convention [NeutralGray900]'s comment records.
 */
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFFAF6EC)
val SurfaceContainerLight = Color(0xFFF4EFE2)
val SurfaceContainerHighLight = Color(0xFFEDE7D7)
val SurfaceContainerHighestLight = Color(0xFFE2DCCC)

val SurfaceContainerLowestDark = Color(0xFF101010)
val SurfaceContainerLowDark = Color(0xFF1B1B1B)
val SurfaceContainerDark = Color(0xFF202020)
val SurfaceContainerHighDark = Color(0xFF2B2B2B)
val SurfaceContainerHighestDark = Color(0xFF373737)

/**
 * `outline`/`outlineVariant`. Both are non-text roles, so they are held to WCAG 1.4.11's 3:1
 * non-text bar rather than the 4.5:1 body-text bar -- see ThemeContrastTest, which asserts them
 * against that threshold explicitly rather than exempting them.
 */
/**
 * 0xFF847D70, not the 0xFF8C8577 this started as. That value cleared 3:1 against `surface`
 * (3.45:1) but landed at 2.88:1 against `surfaceVariant`, which is the darker of the two
 * backgrounds an outline actually sits on -- below WCAG 1.4.11 and caught by ThemeContrastTest
 * rather than by eye. Three percent darker at the same hue and saturation; 3.20:1 and 3.84:1
 * respectively, so it clears the bar with margin rather than sitting on it.
 */
val OutlineLight = Color(0xFF847D70)
val OutlineVariantLight = Color(0xFFD5CEBF)
val OutlineDark = Color(0xFF8E8E8E)
val OutlineVariantDark = Color(0xFF444444)
