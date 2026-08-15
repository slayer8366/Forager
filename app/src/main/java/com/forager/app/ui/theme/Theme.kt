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
    background = Cream,
    surface = Cream,
    onPrimary = Cream,
    onBackground = Bark,
    onSurface = Bark,
)

private val DarkColors = darkColorScheme(
    primary = MossGreen,
    secondary = Mushroom,
    tertiary = ForestGreen,
    background = Bark,
    surface = Bark,
    onPrimary = Cream,
    onBackground = Cream,
    onSurface = Cream,
)

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
