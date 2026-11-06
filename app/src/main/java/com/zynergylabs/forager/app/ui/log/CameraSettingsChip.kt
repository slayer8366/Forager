package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton

/**
 * The strip's gear chip (dispatch 2026-09-28-707; the owner: "Move the two camera options at the
 * bottom to a settings menu inside the camera itself", then "Gear chip with the others
 * (Recommended)"). A tap opens [CameraSettingsPanel]. It held "Automatically Save Location to
 * Photos" and "Lock camera to portrait", the two rows that were at the bottom of Settings; since
 * dispatch 2026-10-13-03 it holds neither and says so, and the gear stays for the settings the owner
 * expects to add (RECORD -971, -972).
 *
 * Built like [GridChip] and [LocationChip]: [BouncingIconButton] for the bounce, [OverlayIcon] for
 * the outline rule, [rotateWithDevice] to turn in place. Always present, last in the strip, after
 * the Location chip. One glyph, no state of its own.
 *
 * **Glyph:** `Icons.Outlined.Settings` (material-icons-core), the thin-line style of the gear the
 * Tools drawer's own Settings row uses (filled there), so the two still read as the same kind of
 * thing. Filled here until dispatch 2026-10-13-03 (RECORD -970).
 */
@Composable
internal fun CameraSettingsChip(onClick: () -> Unit, deviceRotation: Int?, displayRotation: Int) {
    BouncingIconButton(
        onClick = onClick,
        modifier = Modifier
            .rotateWithDevice(deviceRotation, displayRotation)
            .testTag(CAMERA_SETTINGS_CHIP_TAG)
            .semantics { contentDescription = CAMERA_SETTINGS_LABEL },
    ) {
        OverlayIcon(CAMERA_SETTINGS_GLYPH, contentDescription = null)
    }
}

/** The gear's glyph, named so a test can hold it (RECORD -970: the thin-line style). */
internal val CAMERA_SETTINGS_GLYPH: ImageVector get() = Icons.Outlined.Settings

internal const val CAMERA_SETTINGS_CHIP_TAG = "in-app-camera-settings-chip"
internal const val CAMERA_SETTINGS_LABEL = "Camera settings"
