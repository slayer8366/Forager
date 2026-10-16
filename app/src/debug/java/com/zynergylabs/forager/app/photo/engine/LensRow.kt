package com.zynergylabs.forager.app.photo.engine

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.log.OverlayFill
import com.zynergylabs.forager.app.ui.log.OverlayLevelFill
import com.zynergylabs.forager.app.ui.log.OverlayText
import com.zynergylabs.forager.app.ui.log.rotateWithDevice
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The lens row (dispatch 2026-10-16-04; RECORD -1103 to -1107): one button per real back lens, labelled by its measured
 * zoom, shortest first. The dialog places it directly above the shutter, centred, with the message line above it, so it
 * never moves (-1106); each label turns with the phone like the strip's chips, and the row itself does not (-980, -981).
 *
 * **Styling** follows the camera's overlay rule (`CameraOverlay.kt`, the owner, 2026-09-18: "Fill colour carries
 * meaning, and white is the default fill"): outlined numerals, white, and the lit lens filled with the camera's one
 * meaning colour, [OverlayLevelFill]. That colour is a call made by the coder and reported, not an owner ruling.
 *
 * **Touch.** The row wraps its buttons: no fill-width container, so the viewfinder beside it still takes a pinch
 * (CLAUDE.md, the Surface pitfall). Each button is a 48 dp circle, Material's minimum target.
 */
@Composable
internal fun LensRow(engine: PlatformCameraEngine, vertical: Boolean, deviceRotation: Int?, displayRotation: Int) {
    val set = engine.lensSet ?: return
    val lit = engine.litLens?.physicalId
    val buttons: @Composable () -> Unit = {
        set.lenses.forEach { lens ->
            LensButton(
                lens = lens,
                lit = lens.physicalId == lit,
                deviceRotation = deviceRotation,
                displayRotation = displayRotation,
                onClick = { engine.onLensTapped(lens.physicalId) },
            )
        }
    }
    if (vertical) {
        Column(
            modifier = Modifier.testTag(LENS_ROW_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { buttons() }
    } else {
        Row(
            modifier = Modifier.testTag(LENS_ROW_TAG),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) { buttons() }
    }
}

@Composable
private fun LensButton(lens: Lens, lit: Boolean, deviceRotation: Int?, displayRotation: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(LENS_BUTTON_SIZE)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { selected = lit }
            .testTag(lensButtonTag(lens.physicalId)),
        contentAlignment = Alignment.Center,
    ) {
        OverlayText(
            lens.label,
            fill = if (lit) OverlayLevelFill else OverlayFill,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation),
        )
    }
}

/**
 * The pinch on the picture: each event's zoom change goes to the engine, which zooms smoothly within the lens and moves to
 * another lens at its boundary. One finger does nothing (a pan's zoom change is 1). Applied by `PlatformViewfinder` to the
 * viewfinder and by the tests to their stand-in viewfinder, so a test pinch goes through this exact gesture.
 */
internal fun Modifier.lensPinch(engine: PlatformCameraEngine): Modifier = pointerInput(engine) {
    detectTransformGestures { _, _, zoomChange, _ ->
        if (zoomChange != 1f) engine.onPinch(1f)
    }
}

internal val LENS_BUTTON_SIZE = 48.dp

internal const val LENS_ROW_TAG = "in-app-camera-lens-row"

internal fun lensButtonTag(physicalId: String): String = "in-app-camera-lens-$physicalId"
