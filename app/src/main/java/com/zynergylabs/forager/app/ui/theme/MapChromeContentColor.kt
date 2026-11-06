package com.zynergylabs.forager.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The content colour of the map's search chrome (the species search bar and the search panel):
 * white over night chrome, [Bark] over day chrome, keyed off the map's own night/day
 * ([LocalForagerDarkTheme]) as that chrome's fill is. An owned accessor in the theme package
 * (dispatch 2026-09-28-658, RECORD -660 item 7), so the feature files that drew it stop importing
 * the palette constant [Bark] themselves; the colours are exactly the ones they used.
 */
fun mapChromeContentColor(isDarkTheme: Boolean): Color = if (isDarkTheme) Color.White else Bark
