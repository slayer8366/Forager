package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer10
import androidx.compose.material.icons.filled.Timer3
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.photo.TimerMode
import com.zynergylabs.forager.app.photo.next

/**
 * The strip's Timer chip: Off, 3 s, 10 s, a tap asking for the next (decision B8 / Closed
 * decision C, 2026-09-26). Built like [GridChip]: [OverlayIcon] for the outline rule,
 * [rotateWithDevice] to turn in place, one mapping so icon and label cannot disagree. Always
 * present, after the flash chip.
 *
 * **[mode] is the camera screen's.** It lives for one camera session and resets when the camera
 * closes; a tap calls [onTimerModeChanged]. Changing it during a countdown affects the next
 * shutter press only: a running countdown keeps the length it started with (`CaptureCountdown`).
 *
 * **Glyphs:** `TimerOff`, `Timer3` and `Timer10` from `material-icons-extended`. Whether each
 * reads at a glance over a scene is the owner's verdict on the device.
 */
@Composable
internal fun TimerChip(mode: TimerMode, onTimerModeChanged: (TimerMode) -> Unit, deviceRotation: Int?, displayRotation: Int) {
    val glyph = timerGlyph(mode)
    IconButton(
        onClick = { onTimerModeChanged(mode.next()) },
        modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_TIMER_CHIP_TAG),
    ) {
        OverlayIcon(glyph.icon, contentDescription = glyph.label)
    }
}

internal data class TimerGlyph(val icon: ImageVector, val label: String)

internal fun timerGlyph(mode: TimerMode): TimerGlyph = when (mode) {
    TimerMode.Off -> TimerGlyph(Icons.Filled.TimerOff, TIMER_OFF_LABEL)
    TimerMode.ThreeSeconds -> TimerGlyph(Icons.Filled.Timer3, TIMER_3_LABEL)
    TimerMode.TenSeconds -> TimerGlyph(Icons.Filled.Timer10, TIMER_10_LABEL)
}

internal const val CAMERA_TIMER_CHIP_TAG = "in-app-camera-timer-chip"
internal const val TIMER_OFF_LABEL = "Timer off"
internal const val TIMER_3_LABEL = "Timer 3 seconds"
internal const val TIMER_10_LABEL = "Timer 10 seconds"
