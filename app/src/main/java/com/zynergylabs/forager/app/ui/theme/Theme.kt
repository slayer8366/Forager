package com.zynergylabs.forager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.ui.motion.ProvideReduceMotion
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

/**
 * `internal`, not `private`: `ThemeCompletenessTest` and `ThemeContrastTest` assert against these
 * two schemes directly. Reaching them through a composed `MaterialTheme` instead would need a
 * Robolectric runtime to answer questions that are pure data, and would test the composition
 * rather than the palette.
 */
internal val LightColors = lightColorScheme(
    primary = ForestGreen,
    onPrimary = NearWhite,
    primaryContainer = MossGreenContainerLight,
    onPrimaryContainer = ForestDeep,

    // Deepened from Mushroom so the role can carry text -- see MushroomDeep's own comment.
    secondary = MushroomDeep,
    onSecondary = PureWhite,
    secondaryContainer = MushroomContainerLight,
    onSecondaryContainer = MushroomOnContainerLight,

    tertiary = TrailBlue,
    onTertiary = PureWhite,
    tertiaryContainer = TrailBlueContainerLight,
    onTertiaryContainer = TrailBlueOnContainerLight,

    error = SignalRed,
    onError = PureWhite,
    errorContainer = SignalRedContainerLight,
    onErrorContainer = SignalRedOnContainerLight,

    background = NearWhite,
    onBackground = Bark,
    surface = NearWhite,
    onSurface = Bark,
    surfaceVariant = Cream,
    onSurfaceVariant = WarmOnSurfaceVariantLight,

    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,

    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,

    // Bark, not Material's baseline black -- ThemeCompletenessTest ("tag 01") requires every role
    // the UI actually reads to be an intentional choice, not a passthrough that happens to render
    // the same. Scrim backs arbitrary content (a photo, a map, anything under it), not this
    // theme's own surfaces, so it stays the same warm dark tone in both schemes rather than
    // flipping with light/dark mode -- the same reasoning Material's own baseline scrim already
    // follows (pure black in both), and the same tone MapIconStackButtonColorDark/
    // CompassStripBackgroundColorDark already use for the identical job elsewhere in this app.
    scrim = Bark,
)

internal val DarkColors = darkColorScheme(
    // Lichen, not MossGreen. MossGreen held both `primary` and `tertiary` here, so the two roles
    // were the same colour and any component pairing them had no contrast at all. See Lichen.
    primary = Lichen,
    onPrimary = ForestDeep,
    primaryContainer = MossGreenContainerDark,
    onPrimaryContainer = MossGreenContainerLight,

    secondary = MushroomLight,
    onSecondary = MushroomOnContainerDark,
    secondaryContainer = MushroomContainerDark,
    onSecondaryContainer = MushroomContainerLight,

    // ForestGreen (used at this role before) is dark enough that as literal text color it read
    // poorly against a background that's now neutral gray rather than warm brown. It was judged on
    // a "not a walking route" disclaimer that is no longer in the app (T19, RECORD -765; this
    // comment corrected by T11 fixes, RECORD -766). The role is still read as text: it is the
    // Tracks accent in RecordTypeStyle, drawn as the Journal's stat text and chip labels. That
    // reasoning stands; what changed is that the fix is no longer "reuse MossGreen," which
    // collided with `primary`. The cool third note comes from the map's own trip blue instead,
    // lightened for this theme the same way the green is.
    tertiary = TrailBlueLight,
    onTertiary = TrailBlueOnContainerLight,
    tertiaryContainer = TrailBlueContainerDark,
    onTertiaryContainer = TrailBlueContainerLight,

    error = SignalRedLight,
    onError = SignalRedOnContainerDark,
    errorContainer = SignalRedContainerDark,
    onErrorContainer = SignalRedContainerLight,

    background = NeutralGray900,
    onBackground = Cream,
    surface = NeutralGray900,
    onSurface = Cream,
    surfaceVariant = NeutralGray800,
    onSurfaceVariant = NeutralGray200,

    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,

    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,

    // Same value and same reasoning as LightColors' own scrim above -- deliberately identical
    // across both schemes, not a dark-mode variant of it.
    scrim = Bark,
)

/**
 * `expressive()` rather than `standard()` — provisional pending the device gate (§5S Gate G
 * question 4). Extracted as its own value, alongside [ForagerTypography] and [ForagerShapes], for
 * the same reason: `ExpressiveThemeTest` can assert against it directly, and the swap to
 * `standard()` stays a one-line substitution here rather than a change inside [ForagerTheme]'s own
 * body if the gate asks for it.
 */
val ForagerMotionScheme: MotionScheme = MotionScheme.expressive()

/**
 * Light and dark variants both exist here already. [darkTheme] defaults to [isSystemInDarkTheme]
 * only as this parameter's own fallback — every real caller (`MainActivity`) passes
 * `AvailabilityUiState.darkTheme` explicitly instead, Settings' own "Night Mode" checkbox (see
 * that field's doc comment and [com.zynergylabs.forager.app.domain.AppThemePreferenceRepository]), a direct
 * persistent choice rather than the device's system theme setting.
 *
 * A real [MaterialExpressiveTheme], not a [androidx.compose.material3.MaterialTheme] wrapped around
 * one — understory-design-system.md's step 2, REV 03. All four token axes are supplied explicitly:
 * [colorScheme] as before, [ForagerTypography], [ForagerShapes] and [ForagerMotionScheme] (see
 * those values' own doc comments for what they do and don't customize versus stock).
 */
@Composable
fun ForagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Dispatch 2026-09-28-652, item 1: the phone's reduce-motion setting, provided here because this is the app's one
    // theme root (MainActivity's only one), so every screen reads it without asking. See ProvideReduceMotion.
    ProvideReduceMotion {
        // Motion Part 3, item 4 (RECORD -651, "Fade the colours"; RECORD -652, "fast and smooth"): a change of night mode fades
        // the screen quickly from the old colours to the new, or changes at once under reduced motion. Since RECORD -752 the
        // scheme switches once, under a fading picture of the old screen, rather than blending every frame (ColorSchemeFade.kt).
        NightModeBlend(darkTheme) { shownDark ->
            CompositionLocalProvider(LocalForagerDarkTheme provides shownDark) {
                MaterialExpressiveTheme(
                    colorScheme = if (shownDark) DarkColors else LightColors,
                    typography = ForagerTypography,
                    shapes = ForagerShapes,
                    motionScheme = ForagerMotionScheme,
                    content = content,
                )
            }
        }
    }
}

/**
 * The [darkTheme] a [ForagerTheme] ancestor actually resolved to — for the handful of call sites
 * across the map screen (`MapIconBar`'s accent colors, the map icon stack's own surface color, the
 * compass strip, `MapModePicker` (removed 2026-10-19, RECORD -1392), `MapFloatingIconButton` (removed 2026-10-19, RECORD -1392), `AddActionTile`) that pick a color by
 * light/dark theme but sit too far below [AvailabilityUiState.darkTheme] in the composable tree —
 * several calls deep through `MapIconBar`/`CompactMapTab`/`MapTab` — to take it as a parameter
 * without threading it through every intermediate signature. Calling [isSystemInDarkTheme] directly
 * at those sites was the bug this local fixes: it reads the *device's* system setting, not this
 * app's own "Night Mode" checkbox, so those elements stayed dark on a dark-system device even with
 * the checkbox unchecked. Defaults to `false` only for a composable previewed or tested outside any
 * [ForagerTheme] ancestor; every real call path has one.
 */
val LocalForagerDarkTheme = compositionLocalOf { false }
