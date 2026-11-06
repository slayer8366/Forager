package com.zynergylabs.forager.app.ui.format

import androidx.compose.runtime.staticCompositionLocalOf
import com.zynergylabs.forager.app.domain.NoSeaLevelConverter
import com.zynergylabs.forager.app.domain.SeaLevelConverter

/**
 * The sea-level converter for the screens that show or write a stored height (dispatch 2026-10-28-13, item 5: the
 * journal's height labels and GPX export), provided by `MainActivity` from `AppContainer.seaLevelConverter`. Defaults
 * to [NoSeaLevelConverter], so a screen composed without one shows no height number rather than a raw one.
 */
val LocalSeaLevelConverter = staticCompositionLocalOf<SeaLevelConverter> { NoSeaLevelConverter }
