package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.photo.CameraCaptureSession
import com.zynergylabs.forager.app.photo.FlashMode
import com.zynergylabs.forager.app.photo.next

/**
 * The strip's flash chip. Its only working mode today is torch: **Off and Torch**, a tap cycling
 * one to the other (owner, 2026-09-21). Torch lives inside the flash chip rather than on a chip of
 * its own, as in Samsung Camera and Open Camera (decision B2,
 * `docs/audits/2026-09-21-camera-strip-basics-decisions.md`).
 *
 * - **Hidden on a camera with no flash unit**, rather than shown dead: none of the four reference
 *   apps shows a control that cannot work. [CameraCaptureSession.hasFlashUnit] is false until a
 *   camera is bound, so the chip appears when the bind lands.
 * - **The glyph is the session's mode**, read from [CameraCaptureSession.flashMode] on every
 *   composition. The chip keeps no copy, so a mode the session refused or reset (a failed
 *   `enableTorch`, a close) is what it shows.
 * - **Nothing is stored.** Torch resets when the camera closes, as in all four reference apps.
 * - Built from [OverlayIcon], so it takes the outline rule, and turned in place by
 *   [rotateWithDevice], the same rule as every other camera glyph.
 *
 * **Glyphs:** `Icons.Filled.FlashOff` for Off and `Icons.Filled.FlashlightOn` for Torch, from
 * `material-icons-extended`, which the app already ships. Open Camera's own icons for these two
 * states are a crossed-out bolt and a flashlight; that mapping is from memory and was not checked
 * against Open Camera's source.
 */
@Composable
internal fun FlashChip(session: CameraCaptureSession, deviceRotation: Int?, displayRotation: Int) {
    if (!session.hasFlashUnit) return
    val mode = session.flashMode
    val glyph = flashGlyph(mode)
    IconButton(
        onClick = { session.setFlashMode(mode.next()) },
        modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_FLASH_CHIP_TAG),
    ) {
        OverlayIcon(glyph.icon, contentDescription = glyph.label)
    }
}

/** One mapping from mode to icon and label, so the two cannot disagree. */
internal data class FlashGlyph(val icon: ImageVector, val label: String)

internal fun flashGlyph(mode: FlashMode): FlashGlyph = when (mode) {
    FlashMode.Off -> FlashGlyph(Icons.Filled.FlashOff, FLASH_OFF_LABEL)
    FlashMode.Torch -> FlashGlyph(Icons.Filled.FlashlightOn, TORCH_ON_LABEL)
    FlashMode.Auto, FlashMode.On -> FlashGlyph(Icons.Filled.FlashOff, FLASH_OFF_LABEL) // STUB
}

internal const val CAMERA_FLASH_CHIP_TAG = "in-app-camera-flash-chip"
internal const val FLASH_OFF_LABEL = "Flash off"
internal const val TORCH_ON_LABEL = "Torch on"
internal const val FLASH_AUTO_LABEL = "Flash auto"
internal const val FLASH_ON_LABEL = "Flash on"
