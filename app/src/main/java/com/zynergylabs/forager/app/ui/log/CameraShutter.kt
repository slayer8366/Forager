package com.zynergylabs.forager.app.ui.log

// Moved unchanged from InAppCameraDialog.kt (dispatch 2026-10-19-23, UI split Phase 1, PR 10f), apart from private to internal on the declarations the dialog calls.

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.LocalIndication
import com.zynergylabs.forager.app.ui.motion.pressBounce
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The shutter band's contents: the shutter hard against the charger-port edge, the count (and a
 * failure) inboard of it. Along a horizontal edge — portrait, the port edge at the bottom — that is
 * a column with the shutter last; along a vertical edge it is a row with the shutter outermost,
 * mirrored by which side the port is on. One composable for all three, because the difference is
 * an axis and an order, and separate copies are how the halves drift apart.
 *
 * The shutter is always the outermost child, with the count inboard. That ordering is the point:
 * the shutter's distance from the edge the thumb wraps around is what the motor habit is built on.
 * Every glyph turns in place by [rotateWithDevice]; the shutter is a disc and has nothing to turn.
 */
@Composable
internal fun ShutterCluster(
    edge: ScreenEdge,
    deviceRotation: Int?,
    /** The dialog-level window rotation, not read here — see the CameraStrip call site for why. */
    displayRotation: Int,
    captureError: String?,
    /** A notice about a photo that was saved (a RAW shot kept as JPG): the normal colour, not the error colour (RECORD -1019). */
    captureNotice: String?,
    photosTaken: Int,
    shutterEnabled: Boolean,
    /** "Take photo", or "Cancel timer" while a countdown runs. */
    shutterDescription: String,
    onShutter: () -> Unit,
) {
    val readout: @Composable (horizontalAlignment: Alignment.Horizontal) -> Unit = { alignment ->
        Column(horizontalAlignment = alignment, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            captureNotice?.let { message ->
                OverlayText(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_NOTICE_TAG),
                )
            }
            captureError?.let { message ->
                OverlayText(
                    message,
                    fill = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_ERROR_TAG),
                )
            }
            OverlayText(
                photoCountLabel(photosTaken),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation).testTag(CAMERA_COUNT_TAG),
            )
        }
    }
    if (edge.isHorizontal) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            readout(Alignment.CenterHorizontally)
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                ShutterButton(enabled = shutterEnabled, description = shutterDescription, onClick = onShutter)
            }
        }
    } else {
        val portOnRight = edge == ScreenEdge.Right
        Row(
            modifier = Modifier.padding(Spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (portOnRight) {
                readout(Alignment.End)
                ShutterButton(enabled = shutterEnabled, description = shutterDescription, onClick = onShutter)
            } else {
                ShutterButton(enabled = shutterEnabled, description = shutterDescription, onClick = onShutter)
                readout(Alignment.Start)
            }
        }
    }
}

/**
 * The shutter: a plain ringed disc, the shape every camera app has trained people to recognise,
 * with the overlay rule's black ring at its outer edge — drawn inside the same 72 dp, so the disc
 * reads over a white scene without moving.
 */
@Composable
private fun ShutterButton(enabled: Boolean, description: String, onClick: () -> Unit) {
    // Amendment 2 to motion Part 1 (RECORD -659, the owner: "Shutter yes, rows no"): the disc dips on a press and springs back,
    // not under reduced motion. The touch box, its circular clip, the ripple, the tag and the description stay on the outer node,
    // as they were; only the drawn disc, on the inner node, bounces.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(SHUTTER_SIZE_DP.dp)
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(CAMERA_SHUTTER_TAG),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .pressBounce(interactionSource)
                .clip(CircleShape)
                .background(if (enabled) OverlayFill else OverlayFill.copy(alpha = DISABLED_SHUTTER_ALPHA))
                // Drawing only, at the full 72 dp: the black ring at the outer edge, then the white ring inset by it. The first
                // cut put the padding before the tag and the tagged node shrank 1.5 dp; the tag is on the outer node now, which
                // the padding here cannot touch.
                .overlayRing()
                .padding(OVERLAY_OUTLINE_WIDTH / 2)
                .border(SHUTTER_RING_DP.dp, OverlayFill.copy(alpha = SHUTTER_RING_ALPHA), CircleShape),
        )
    }
}

/**
 * "No photos yet" / "1 photo taken" / "4 photos taken". Singular is not decoration: the count is
 * the only feedback a session gives, so it should read like a sentence rather than a counter.
 */
internal fun photoCountLabel(count: Int): String = when (count) {
    0 -> "No photos yet"
    1 -> "1 photo taken"
    else -> "$count photos taken"
}

private const val SHUTTER_SIZE_DP = 72
private const val SHUTTER_RING_DP = 4
private const val SHUTTER_RING_ALPHA = 0.6f
private const val DISABLED_SHUTTER_ALPHA = 0.4f
