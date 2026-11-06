package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashAuto
import androidx.compose.material.icons.outlined.FlashOff
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.ui.motion.IconSwap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
 * **Glyphs:** `Icons.Outlined.FlashOff` (Filled until dispatch 2026-10-13-03, RECORD -970) for Off, `FlashAuto` for Auto, `FlashOn` for On and
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
    BouncingIconButton(
        onClick = { session.setFlashMode(mode.next()) },
        modifier = Modifier
            .rotateWithDevice(deviceRotation, displayRotation)
            .testTag(CAMERA_FLASH_CHIP_TAG)
            .semantics { contentDescription = glyph.label },
    ) {
        // Motion Part 1 (dispatch 2026-09-28-652, item 5): the glyph crossfades with a slight grow when the mode changes. The
        // label sits on the button, not the icon, because both glyphs are composed for the length of the swap.
        IconSwap(targetState = glyph.icon) { icon -> OverlayIcon(icon, contentDescription = null) }
    }
}

/** One mapping from mode to icon and label, so the two cannot disagree. */
internal data class FlashGlyph(val icon: ImageVector, val label: String)

internal fun flashGlyph(mode: FlashMode): FlashGlyph = when (mode) {
    FlashMode.Off -> FlashGlyph(Icons.Outlined.FlashOff, FLASH_OFF_LABEL)
    FlashMode.Auto -> FlashGlyph(Icons.Outlined.FlashAuto, FLASH_AUTO_LABEL)
    FlashMode.On -> FlashGlyph(Icons.Outlined.FlashOn, FLASH_ON_LABEL)
    FlashMode.Torch -> FlashGlyph(Icons.Outlined.FlashlightOn, TORCH_ON_LABEL)
}

internal const val CAMERA_FLASH_CHIP_TAG = "in-app-camera-flash-chip"
internal const val FLASH_OFF_LABEL = "Flash off"
internal const val TORCH_ON_LABEL = "Torch on"
internal const val FLASH_AUTO_LABEL = "Flash auto"
internal const val FLASH_ON_LABEL = "Flash on"
