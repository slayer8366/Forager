package com.forager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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

    // ForestGreen (used at this role before) is dark enough that as literal text color -- see the
    // "not a walking route" disclaimer, the one place this role is used as a text color rather
    // than a fill -- it read poorly against a background that's now neutral gray rather than warm
    // brown. That reasoning stands; what changed is that the fix is no longer "reuse MossGreen,"
    // which collided with `primary`. The cool third note comes from the map's own trip blue
    // instead, lightened for this theme the same way the green is.
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
)

/**
 * Light and dark variants both exist here already — [darkTheme] defaults to
 * [isSystemInDarkTheme], so the app follows the device's system theme setting rather than needing
 * its own in-app toggle, the same convention most Android apps use.
 *
 * A real [MaterialExpressiveTheme], not a [androidx.compose.material3.MaterialTheme] wrapped around
 * one — understory-design-system.md's step 2, REV 03. All four token axes are supplied explicitly:
 * [colorScheme] as before, [ForagerTypography] and [ForagerShapes] (see those files' own doc
 * comments for what they do and don't customize versus stock), and [MotionScheme.expressive] rather
 * than leaving `motionScheme` to default — `expressive()` is provisional pending the device gate
 * (§5S Gate G question 4), recorded here rather than left implicit so the swap to `standard()` stays
 * a one-line substitution if the gate asks for it.
 */
@Composable
fun ForagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = ForagerTypography,
        shapes = ForagerShapes,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
