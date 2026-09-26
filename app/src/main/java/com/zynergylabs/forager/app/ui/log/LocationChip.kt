package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/** STUB. */
@Composable
internal fun LocationChip(autoSaveLocationToPhotos: Boolean, onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit, deviceRotation: Int?, displayRotation: Int) {
    val glyph = locationGlyph(autoSaveLocationToPhotos)
    IconButton(onClick = {}, modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_LOCATION_CHIP_TAG)) {
        OverlayIcon(glyph.icon, contentDescription = glyph.label)
    }
}

internal data class LocationGlyph(val icon: ImageVector, val label: String)

/** STUB: one glyph for both states. */
internal fun locationGlyph(autoSaveLocationToPhotos: Boolean): LocationGlyph = LocationGlyph(Icons.Filled.LocationOff, LOCATION_OFF_LABEL)

internal const val CAMERA_LOCATION_CHIP_TAG = "in-app-camera-location-chip"
internal const val LOCATION_ON_LABEL = "Save location: On"
internal const val LOCATION_OFF_LABEL = "Save location: Off"
