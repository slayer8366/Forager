package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.next

/**
 * The strip's grid chip: Off, Grid, Grid + Level, a tap asking for the next (decision record B7).
 * Built like [FlashChip]: [OverlayIcon] for the outline rule, [rotateWithDevice] to turn in place,
 * and a glyph from one mapping so icon and label cannot disagree. Unlike the flash chip it is
 * always present — every camera can draw a grid — and it sits after the flash chip.
 *
 * **[mode] is the stored mode.** A tap calls [onGridModeChanged] and changes nothing here; the chip
 * shows the next mode when `CameraGridModeViewModel` has stored it.
 *
 * **Glyphs:** `GridOff`, `GridOn` and `Straighten` (a ruler, for the level) from
 * `material-icons-extended`. Material has no grid-with-level glyph; `Straighten` is the closest
 * this session found, chosen by name and not compared against any reference app's icon.
 */
@Composable
internal fun GridChip(mode: GridMode, onGridModeChanged: (GridMode) -> Unit, deviceRotation: Int?, displayRotation: Int) {
    val glyph = gridGlyph(mode)
    IconButton(
        onClick = { onGridModeChanged(mode.next()) },
        modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_GRID_CHIP_TAG),
    ) {
        OverlayIcon(glyph.icon, contentDescription = glyph.label)
    }
}

internal data class GridGlyph(val icon: ImageVector, val label: String)

internal fun gridGlyph(mode: GridMode): GridGlyph = when (mode) {
    GridMode.Off -> GridGlyph(Icons.Filled.GridOff, GRID_OFF_LABEL)
    GridMode.Grid -> GridGlyph(Icons.Filled.GridOn, GRID_ON_LABEL)
    GridMode.GridLevel -> GridGlyph(Icons.Filled.Straighten, GRID_AND_LEVEL_LABEL)
}

internal const val CAMERA_GRID_CHIP_TAG = "in-app-camera-grid-chip"
internal const val GRID_OFF_LABEL = "Grid off"
internal const val GRID_ON_LABEL = "Grid on"
internal const val GRID_AND_LEVEL_LABEL = "Grid and level on"
