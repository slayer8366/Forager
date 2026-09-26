package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
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
 * The strip's flash chip: **Off, Auto, On, Torch**, a tap asking for the next (decision B8,
 * 2026-09-26, extending B2). Torch came first (owner, 2026-09-21) and lives inside the flash chip
 * rather than on a chip of its own, as in Samsung Camera and Open Camera (decision B2,
 * `docs/audits/2026-09-21-camera-strip-basics-decisions.md`); flash on capture, Auto and On,
 * joined it in the same chip.
 *
 * - **Hidden on a camera with no flash unit**, rather than shown dead: none of the four reference
 *   apps shows a control that cannot work. [CameraCaptureSession.hasFlashUnit] is false until a
 *   camera is bound, so the chip appears when the bind lands.
 * - **The glyph is the session's mode**, read from [CameraCaptureSession.flashMode] on every
 *   composition. The chip keeps no copy, so a mode the session refused or reset (a failed
 *   `enableTorch`, a close) is what it shows.
 * - **Nothing is stored.** The mode resets to Off when the camera closes; torch did so in all four reference apps.
 * - Built from [OverlayIcon], so it takes the outline rule, and turned in place by
 *   [rotateWithDevice], the same rule as every other camera glyph.
 *
 * **Glyphs:** `Icons.Filled.FlashOff` for Off, `FlashAuto` for Auto, `FlashOn` for On and
 * `FlashlightOn` for Torch, from `material-icons-extended`, which the app already ships. Whether
 * each reads at a glance over a scene is the owner's verdict on the device. Open Camera's own icons for these two
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
    FlashMode.Auto -> FlashGlyph(Icons.Filled.FlashAuto, FLASH_AUTO_LABEL)
    FlashMode.On -> FlashGlyph(Icons.Filled.FlashOn, FLASH_ON_LABEL)
    FlashMode.Torch -> FlashGlyph(Icons.Filled.FlashlightOn, TORCH_ON_LABEL)
}

internal const val CAMERA_FLASH_CHIP_TAG = "in-app-camera-flash-chip"
internal const val FLASH_OFF_LABEL = "Flash off"
internal const val TORCH_ON_LABEL = "Torch on"
internal const val FLASH_AUTO_LABEL = "Flash auto"
internal const val FLASH_ON_LABEL = "Flash on"
