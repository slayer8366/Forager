package com.zynergylabs.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.approximateShotBytes
import com.zynergylabs.forager.app.domain.approximateSizeLabel
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The format chip (RAW pipeline stage 2, dispatch 2026-10-15-01; RECORD -1002: "Show it now"). It reads the format the
 * camera is taking shots in, **JPG** or **RAW**, as outlined text like the strip's other glyphs (the overlay rule in
 * `CameraOverlay.kt`, and -970's outline), and turns with the device like every control. A tap opens [FormatMenuLayer].
 *
 * Composed only where the bound camera's probe offers RAW within the operating limit (`rawCaptureSize`); elsewhere there
 * is no chip and capture is as it was (RECORD -974).
 */
@Composable
internal fun FormatChip(format: CameraFormat, onClick: () -> Unit, deviceRotation: Int?, displayRotation: Int) {
    val label = formatLabel(format)
    BouncingIconButton(
        onClick = onClick,
        modifier = Modifier
            .rotateWithDevice(deviceRotation, displayRotation)
            .testTag(CAMERA_FORMAT_CHIP_TAG)
            .semantics { contentDescription = "$CAMERA_FORMAT_CHIP_LABEL: $label" },
    ) {
        OverlayText(label, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * The format menu: JPG and RAW, the one in use ticked, each with its approximate size (RECORD -975), as the owner's mockup
 * draws it. Built as the gear panel is (`CameraSettingsPanelLayer`, Amendment 1, item 7): just inboard of the strip, turned
 * with the device, closed by Back or a tap outside (which does nothing else), and on the dark scheme at the 80% fill the
 * camera borrows from the map chrome. Choosing a row asks for that format and closes the menu.
 */
@Composable
internal fun BoxScope.FormatMenuLayer(
    stripEdge: ScreenEdge,
    deviceRotation: Int?,
    displayRotation: Int,
    current: CameraFormat,
    rawSize: PixelSize,
    onChoose: (CameraFormat) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val currentOnClose by rememberUpdatedState(onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CAMERA_FORMAT_DISMISS_TAG)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        currentOnClose()
                    }
                }
            },
    )
    CameraBand(edge = stripEdge) {
        val inboard = STRIP_ROW_HEIGHT + Spacing.sm
        val clearOfStrip = when (stripEdge) {
            ScreenEdge.Top -> Modifier.absolutePadding(top = inboard)
            ScreenEdge.Bottom -> Modifier.absolutePadding(bottom = inboard)
            ScreenEdge.Left -> Modifier.absolutePadding(left = inboard)
            ScreenEdge.Right -> Modifier.absolutePadding(right = inboard)
        }
        Box(modifier = clearOfStrip.padding(Spacing.sm)) {
            FormatMenu(
                current = current,
                rawSize = rawSize,
                onChoose = onChoose,
                modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation),
            )
        }
    }
}

@Composable
internal fun FormatMenu(current: CameraFormat, rawSize: PixelSize, onChoose: (CameraFormat) -> Unit, modifier: Modifier = Modifier) {
    MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(
            // Sized to its rows, not stretched (CLAUDE.md's Surface pitfall: a stretched Surface takes touches where nothing is drawn).
            modifier = modifier
                .width(IntrinsicSize.Max)
                .testTag(CAMERA_FORMAT_MENU_TAG)
                .semantics { paneTitle = CAMERA_FORMAT_CHIP_LABEL },
            shape = RoundedCornerShape(Spacing.md),
            color = DarkColors.surfaceContainer.copy(alpha = CAMERA_SETTINGS_PANEL_ALPHA),
            contentColor = DarkColors.onSurface,
        ) {
            Column(modifier = Modifier.padding(vertical = Spacing.sm)) {
                for (format in CameraFormat.entries) {
                    FormatRow(format, chosen = format == current, size = approximateSizeLabel(approximateShotBytes(format, rawSize))) { onChoose(format) }
                }
            }
        }
    }
}

@Composable
private fun FormatRow(format: CameraFormat, chosen: Boolean, size: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = chosen }
            .testTag(formatRowTag(format))
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(formatLabel(format), style = MaterialTheme.typography.bodyLarge)
        Box(modifier = Modifier.size(20.dp)) {
            if (chosen) Icon(Icons.Filled.Check, contentDescription = CAMERA_FORMAT_CHOSEN_LABEL, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(size, style = MaterialTheme.typography.bodyMedium, color = DarkColors.onSurfaceVariant)
    }
}

internal fun formatLabel(format: CameraFormat): String = when (format) {
    CameraFormat.Jpg -> "JPG"
    CameraFormat.Raw -> "RAW"
}

internal fun formatRowTag(format: CameraFormat): String = "in-app-camera-format-row-${format.name.lowercase()}"

internal const val CAMERA_FORMAT_CHIP_TAG = "in-app-camera-format-chip"
internal const val CAMERA_FORMAT_MENU_TAG = "in-app-camera-format-menu"
internal const val CAMERA_FORMAT_DISMISS_TAG = "in-app-camera-format-dismiss"
internal const val CAMERA_FORMAT_CHIP_LABEL = "Photo format"
internal const val CAMERA_FORMAT_CHOSEN_LABEL = "In use"
