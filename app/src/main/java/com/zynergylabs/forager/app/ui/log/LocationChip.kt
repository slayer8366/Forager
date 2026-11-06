package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.IconSwap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * The strip's Location chip (decision B8 / Closed decision D, 2026-09-26): shows and sets Settings'
 * "Automatically Save Location to Photos" — the one stored value, not a per-shot override. The B3
 * mirror: the camera and Settings are two views of the same setting. Since dispatch 2026-10-13-03
 * the chip is the only one: Settings' row moved to the camera's gear panel (dispatch 2026-09-28-707),
 * and the panel's row was removed (RECORD -972).
 *
 * **[autoSaveLocationToPhotos] is the stored value**, handed down from `AvailabilityScreen`'s
 * `uiState` the way "Lock camera to portrait" was until its removal. A tap calls [onAutoSaveLocationToPhotosChanged]
 * with the toggled value — the same handler Settings' checkbox calls — and changes nothing here;
 * the chip shows the new value when the screen's state has it. It never writes the repository.
 *
 * Built like [GridChip]: [OverlayIcon] for the outline rule, [rotateWithDevice] to turn in place,
 * one mapping so icon and label cannot disagree. Always present, last in the strip.
 *
 * **Glyphs (the Outlined style since dispatch 2026-10-13-03, RECORD -970):** `LocationOn` (material-icons-core) and `LocationOff` (material-icons-extended), both
 * already on the app's classpath. Whether each reads at a glance over a scene is the owner's
 * verdict on the device.
 */
@Composable
internal fun LocationChip(
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    deviceRotation: Int?,
    displayRotation: Int,
) {
    val glyph = locationGlyph(autoSaveLocationToPhotos)
    BouncingIconButton(
        onClick = { onAutoSaveLocationToPhotosChanged(!autoSaveLocationToPhotos) },
        modifier = Modifier
            .rotateWithDevice(deviceRotation, displayRotation)
            .testTag(CAMERA_LOCATION_CHIP_TAG)
            .semantics { contentDescription = glyph.label },
    ) {
        // Motion Part 1 (dispatch 2026-09-28-652 item 5, with Amendment 1, RECORD -657): the glyph crossfades with a slight grow.
        // The label is on the button, because both glyphs are composed for the length of the swap.
        IconSwap(targetState = glyph.icon) { icon -> OverlayIcon(icon, contentDescription = null) }
    }
}

internal data class LocationGlyph(val icon: ImageVector, val label: String)

internal fun locationGlyph(autoSaveLocationToPhotos: Boolean): LocationGlyph =
    if (autoSaveLocationToPhotos) LocationGlyph(Icons.Outlined.LocationOn, LOCATION_ON_LABEL)
    else LocationGlyph(Icons.Outlined.LocationOff, LOCATION_OFF_LABEL)

internal const val CAMERA_LOCATION_CHIP_TAG = "in-app-camera-location-chip"
internal const val LOCATION_ON_LABEL = "Save location: On"
internal const val LOCATION_OFF_LABEL = "Save location: Off"
