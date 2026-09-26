package com.zynergylabs.forager.app.ui.log

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.photo.TimerMode
import com.zynergylabs.forager.app.photo.next

/** STUB. */
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

/** STUB: one glyph for every setting. */
internal fun timerGlyph(mode: TimerMode): TimerGlyph = TimerGlyph(Icons.Filled.TimerOff, TIMER_OFF_LABEL)

internal const val CAMERA_TIMER_CHIP_TAG = "in-app-camera-timer-chip"
internal const val TIMER_OFF_LABEL = "Timer off"
internal const val TIMER_3_LABEL = "Timer 3 seconds"
internal const val TIMER_10_LABEL = "Timer 10 seconds"
