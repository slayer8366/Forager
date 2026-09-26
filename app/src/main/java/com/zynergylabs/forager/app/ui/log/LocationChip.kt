package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * The strip's Location chip (decision B8 / Closed decision D, 2026-09-26): shows and sets Settings'
 * "Automatically Save Location to Photos" — the one stored value, not a per-shot override. The B3
 * mirror: the camera and Settings are two views of the same setting.
 *
 * **[autoSaveLocationToPhotos] is the stored value**, handed down from `AvailabilityScreen`'s
 * `uiState` the way "Lock camera to portrait" is. A tap calls [onAutoSaveLocationToPhotosChanged]
 * with the toggled value — the same handler Settings' checkbox calls — and changes nothing here;
 * the chip shows the new value when the screen's state has it. It never writes the repository.
 *
 * Built like [GridChip]: [OverlayIcon] for the outline rule, [rotateWithDevice] to turn in place,
 * one mapping so icon and label cannot disagree. Always present, last in the strip.
 *
 * **Glyphs:** `LocationOn` (material-icons-core) and `LocationOff` (material-icons-extended), both
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
    IconButton(
        onClick = { onAutoSaveLocationToPhotosChanged(!autoSaveLocationToPhotos) },
        modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_LOCATION_CHIP_TAG),
    ) {
        OverlayIcon(glyph.icon, contentDescription = glyph.label)
    }
}

internal data class LocationGlyph(val icon: ImageVector, val label: String)

internal fun locationGlyph(autoSaveLocationToPhotos: Boolean): LocationGlyph =
    if (autoSaveLocationToPhotos) LocationGlyph(Icons.Filled.LocationOn, LOCATION_ON_LABEL)
    else LocationGlyph(Icons.Filled.LocationOff, LOCATION_OFF_LABEL)

internal const val CAMERA_LOCATION_CHIP_TAG = "in-app-camera-location-chip"
internal const val LOCATION_ON_LABEL = "Save location: On"
internal const val LOCATION_OFF_LABEL = "Save location: Off"
