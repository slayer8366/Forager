package com.forager.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ForestGreen,
    secondary = Mushroom,
    tertiary = MossGreen,
    background = NearWhite,
    surface = NearWhite,
    surfaceVariant = Cream,
    onSurfaceVariant = WarmOnSurfaceVariantLight,
    primaryContainer = MossGreenContainerLight,
    onPrimaryContainer = Bark,
    onPrimary = Cream,
    onBackground = Bark,
    onSurface = Bark,
)

private val DarkColors = darkColorScheme(
    primary = MossGreen,
    secondary = Mushroom,
    // ForestGreen (used here before) is dark enough that as literal text color — see the
    // "not a walking route" disclaimer, the one place this role is used as a text color rather
    // than a fill — it read poorly against a background that's now neutral gray rather than warm
    // brown. MossGreen is the same brand hue, lighter, and already this scheme's primary, so it
    // doesn't introduce a fourth color into a three-color brand palette.
    tertiary = MossGreen,
    background = NeutralGray900,
    surface = NeutralGray900,
    surfaceVariant = NeutralGray800,
    onSurfaceVariant = NeutralGray200,
    primaryContainer = MossGreenContainerDark,
    onPrimaryContainer = Cream,
    onPrimary = Cream,
    onBackground = Cream,
    onSurface = Cream,
)

/**
 * Light and dark variants both exist here already — [darkTheme] defaults to
 * [isSystemInDarkTheme], so the app follows the device's system theme setting rather than needing
 * its own in-app toggle, the same convention most Android apps use.
 */
@Composable
fun ForagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
