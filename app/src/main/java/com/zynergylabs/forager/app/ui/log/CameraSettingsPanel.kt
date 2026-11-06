package com.zynergylabs.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The camera's gear panel (dispatch 2026-09-28-707), opened by [CameraSettingsChip], and everything
 * that closes it: Back, or a tap anywhere outside it. Composed by [InAppCameraDialog] after the
 * strip and the shutter band, only while open, so it draws and hit-tests above both.
 *
 * ## What it holds: nothing yet, and it says so
 *
 * Dispatch 2026-10-13-03 emptied it. "Lock camera to portrait" was removed (RECORD -968; the owner:
 * "Seems pretty useless to be honest", then "Go ahead and remove it"), and the photo-location row
 * with it (RECORD -972; the owner: "Remove save location from the settings, empty it out
 * completely"); the strip's Location chip still shows and sets that value. The gear and this panel
 * stay (RECORD -971, "Don't remove the settings yet"): the owner expects to fill it before the
 * update ships. Until then it shows one plain line, [CAMERA_SETTINGS_EMPTY_TEXT], rather than
 * opening blank, which could read as broken (the planner's call, -972). Both removed rows, and
 * how to put either back, are in `docs/archive/camera-portrait-lock.md`.
 *
 * Toggling the lock row with the camera open froze the picture: the capture session was keyed on
 * the setting and rebuilt, and the viewfinder stayed attached to the closed one. A row added here
 * later whose value the session or the viewfinder reads must not repeat that; the archive document
 * says what to do instead.
 *
 * ## Where it sits, and what it covers
 *
 * Just inboard of the strip, on the punch-hole edge: under it in portrait, beside it in landscape,
 * inside the same band so it clears the cut-out the way the strip does ([CameraBand]). The shutter
 * band is on the opposite edge ([portEdge]), so the panel never covers the shutter, the count or a
 * capture error; it covers part of the preview while open, which is what a panel is. It turns with
 * the device like every control ([rotateWithDevice], owner, 2026-09-17), so its words read in the
 * current hold, and its square footprint is why its width is capped at [CAMERA_SETTINGS_PANEL_MAX_WIDTH]:
 * turned a quarter it must still fit across a portrait window. Its rows scroll if a large font
 * makes it taller than the window.
 *
 * Opening and closing are instant, like the rest of the camera (motion scout's deliberately-instant
 * list; the camera's rotation stays the system's, RECORD -681).
 *
 * ## Its colours
 *
 * The camera is always black behind its controls, whatever the app theme, so the panel is drawn in
 * the app's own dark scheme ([DarkColors]) in both themes; in the light scheme Settings' supporting
 * line (`onSurfaceVariant`) would be dark grey on black. Its fill is that scheme's container at
 * [CAMERA_SETTINGS_PANEL_ALPHA], 80%, the alpha the owner set for chrome over the map ("nothing
 * should fully obstruct the map view"), borrowed for the same reason over the preview. The camera's
 * overlay rule (`CameraOverlay.kt`: outlined glyphs, no panel of their own) is for the strip's
 * glyphs; a panel of checkbox rows and sentences is not readable that way, and the owner chose a
 * panel. **The fill and its alpha are this dispatch's choice, not the owner's**, and are the first
 * thing to judge on the device.
 */
@Composable
internal fun BoxScope.CameraSettingsPanelLayer(
    /** The strip's edge, the punch-hole edge: the panel sits just inboard of it. */
    stripEdge: ScreenEdge,
    deviceRotation: Int?,
    displayRotation: Int,
    onClose: () -> Unit,
) {
    // Composed after the camera's own BackHandler, so this one wins while the panel is open: Back
    // closes the panel, and the next Back closes the camera as before.
    BackHandler(onBack = onClose)
    val currentOnClose by rememberUpdatedState(onClose)

    // A tap anywhere outside the panel closes it and does nothing else: this layer is above the
    // shutter, the chips and the preview, and takes the touch. That is the convention for a popup
    // dismissed by a tap outside; a shutter tap while the panel is open closes the panel and takes
    // no photo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CAMERA_SETTINGS_DISMISS_TAG)
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
            CameraSettingsPanel(
                modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation),
            )
        }
    }
}

/** The panel itself, on the dark scheme's container, see [CameraSettingsPanelLayer]: empty, so one line saying so. */
@Composable
internal fun CameraSettingsPanel(
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(
            modifier = modifier
                .widthIn(max = CAMERA_SETTINGS_PANEL_MAX_WIDTH)
                .testTag(CAMERA_SETTINGS_PANEL_TAG)
                .semantics { paneTitle = CAMERA_SETTINGS_LABEL },
            shape = RoundedCornerShape(Spacing.md),
            color = DarkColors.surfaceContainer.copy(alpha = CAMERA_SETTINGS_PANEL_ALPHA),
            contentColor = DarkColors.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(CAMERA_SETTINGS_EMPTY_TEXT, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Narrow enough that, turned a quarter by [rotateWithDevice], the panel's square still fits across a portrait phone. */
internal val CAMERA_SETTINGS_PANEL_MAX_WIDTH: Dp = 300.dp

/** The panel's fill alpha: the map chrome's 80% (`MAP_CHROME_OVER_MAP_ALPHA`), borrowed; see [CameraSettingsPanelLayer]. */
internal const val CAMERA_SETTINGS_PANEL_ALPHA = 0.8f

internal const val CAMERA_SETTINGS_PANEL_TAG = "in-app-camera-settings-panel"
internal const val CAMERA_SETTINGS_DISMISS_TAG = "in-app-camera-settings-dismiss"

/** What the empty panel says (the planner's wording, RECORD -972). */
internal const val CAMERA_SETTINGS_EMPTY_TEXT = "No camera settings yet"
