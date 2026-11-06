package com.zynergylabs.forager.app.ui.log

import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Timer10
import androidx.compose.material.icons.outlined.Timer3
import androidx.compose.material.icons.outlined.TimerOff
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.ui.motion.IconSwap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
 * **Glyphs (the Outlined style since dispatch 2026-10-13-03, RECORD -970):** `TimerOff`, `Timer3` and `Timer10` from `material-icons-extended`. Whether each
 * reads at a glance over a scene is the owner's verdict on the device.
 */
@Composable
internal fun TimerChip(mode: TimerMode, onTimerModeChanged: (TimerMode) -> Unit, deviceRotation: Int?, displayRotation: Int) {
    val glyph = timerGlyph(mode)
    BouncingIconButton(
        onClick = { onTimerModeChanged(mode.next()) },
        modifier = Modifier
            .rotateWithDevice(deviceRotation, displayRotation)
            .testTag(CAMERA_TIMER_CHIP_TAG)
            .semantics { contentDescription = glyph.label },
    ) {
        // Motion Part 1 (dispatch 2026-09-28-652, item 5): the glyph crossfades with a slight grow when the mode changes. The
        // label sits on the button, not the icon, because both glyphs are composed for the length of the swap.
        IconSwap(targetState = glyph.icon) { icon -> OverlayIcon(icon, contentDescription = null) }
    }
}

internal data class TimerGlyph(val icon: ImageVector, val label: String)

internal fun timerGlyph(mode: TimerMode): TimerGlyph = when (mode) {
    TimerMode.Off -> TimerGlyph(Icons.Outlined.TimerOff, TIMER_OFF_LABEL)
    TimerMode.ThreeSeconds -> TimerGlyph(Icons.Outlined.Timer3, TIMER_3_LABEL)
    TimerMode.TenSeconds -> TimerGlyph(Icons.Outlined.Timer10, TIMER_10_LABEL)
}

internal const val CAMERA_TIMER_CHIP_TAG = "in-app-camera-timer-chip"
internal const val TIMER_OFF_LABEL = "Timer off"
internal const val TIMER_3_LABEL = "Timer 3 seconds"
internal const val TIMER_10_LABEL = "Timer 10 seconds"
