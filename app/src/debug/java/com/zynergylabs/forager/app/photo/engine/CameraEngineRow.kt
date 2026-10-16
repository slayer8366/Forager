package com.zynergylabs.forager.app.photo.engine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * The Diagnostics page's "Camera engine A" row (dispatch 2026-10-16-02: "a Settings debug row"): off is today's CameraX
 * camera, on is the prototype, from the next camera open. Shaped like the page's other two toggles. The forced fallback is
 * adb only: it is a device-check step, not a setting.
 */
@Composable
internal fun CameraEngineRow() {
    val on = CameraEngineSwitch.choice == CameraEngineChoice.Platform
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = on, role = Role.Switch) { wanted ->
                CameraEngineSwitch.select(if (wanted) CameraEngineChoice.Platform else CameraEngineChoice.CameraX)
            }
            .testTag(CAMERA_ENGINE_ROW_TAG),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(CAMERA_ENGINE_ROW_LABEL, style = MaterialTheme.typography.bodyLarge)
            Text(CAMERA_ENGINE_ROW_DETAIL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null)
    }
    HorizontalDivider()
}

internal const val CAMERA_ENGINE_ROW_TAG = "diagnostics-camera-engine"
internal const val CAMERA_ENGINE_ROW_LABEL = "Camera engine A (prototype)"
internal const val CAMERA_ENGINE_ROW_DETAIL = "The camera through the platform interface, CameraX if it fails. From the next camera open; until the app restarts."
