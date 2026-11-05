package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material3's snackbar shadow (`SnackbarTokens.ContainerElevation`, `ElevationTokens.Level3`, read from the material3 1.5.0-alpha26
 * bytecode), which the notice keeps where it covers no map (dispatch 2026-11-05-06, A3).
 */
internal val MATERIAL_SNACKBAR_SHADOW_ELEVATION = 6.dp

/**
 * STUB for the tests-first commit of dispatch 2026-11-05-06 (RECORD -2155): Material's own snackbar, unchanged, so the parity tests
 * have both sides to compare and the edge test its red. The forward change replaces this body.
 */
@Composable
internal fun MapChromeSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    shape: Shape = SnackbarDefaults.shape,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    actionColor: Color = SnackbarDefaults.actionColor,
    actionContentColor: Color = SnackbarDefaults.actionContentColor,
    dismissActionContentColor: Color = SnackbarDefaults.dismissActionContentColor,
    @Suppress("UNUSED_PARAMETER") shadowElevation: Dp = MATERIAL_SNACKBAR_SHADOW_ELEVATION,
    @Suppress("UNUSED_PARAMETER") border: BorderStroke? = null,
) {
    Snackbar(
        snackbarData = snackbarData,
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        actionColor = actionColor,
        actionContentColor = actionContentColor,
        dismissActionContentColor = dismissActionContentColor,
    )
}
