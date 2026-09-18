package com.zynergylabs.forager.app.ui.log

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The camera's region model: a viewfinder region and two bands. Owner's decision, 2026-09-18
 * ("option A, build the bands now").
 *
 * A control's position depends on two things, not one — the device's rotation **and** the
 * viewfinder's extent — and until this file the code named only the first. Positioning against
 * screen edges is right exactly as long as the viewfinder fills the screen; the moment a 4:3
 * ratio leaves black bands on a tall screen, "the screen edge" stops being where a control
 * belongs. So the three regions are named now, while there is still one ratio:
 *
 * - **the viewfinder region** — whatever the current ratio produces; today, the whole window;
 * - **the strip band** — on the punch-hole side of it ([punchHoleEdge]);
 * - **the shutter band** — on the charger-port side of it ([portEdge]).
 *
 * Controls are anchored **inside their band** against its outer edge, and overlap onto the image
 * when the band is thinner than they are. **Built with exactly one ratio, full-bleed**: the
 * viewfinder region is the window and both bands are zero-thick at its edges, so every control
 * sits where it did before this file existed. A later ratio is "the viewfinder region shrank and
 * the bands grew" — [CameraBand.thickness] becomes non-zero — and no control's placement logic is
 * touched. There is no ratio setting and no second ratio here, on purpose.
 *
 * **Said plainly, because the structure could otherwise look verified:** with the bands zero-thick
 * at a single ratio there is no second case to test them against. Their thickness never exceeds
 * their content, so "inside the band" and "at the edge" are the same place, and every band test
 * is a test of edge anchoring. The band model is correct by construction and by review, not by a
 * test that could fail. The tests that *can* fail — the shutter on the port edge, the strip on the
 * punch-hole edge, at every rotation — are in `InAppCameraDialogTest` and
 * `InAppCameraDialogLandscapeTest`, and those are the evidence.
 *
 * ## Insets: the safe area collapses, each band clears only its own edge
 *
 * The status bar is hidden while the camera is open ([HideStatusBarOnThisWindow]), and the
 * controls are **not** laid out as if it were still there. The dialog no longer pads by
 * `safeDrawing`; instead each band pads by the display cut-out and the navigation bar **on its own
 * edge only**. That does three things at once: the strip sits *inboard of the punch-hole cut-out*
 * (the answer to "how do you clear it"), the shutter clears the navigation bar where that bar is
 * on the port edge (portrait), and in landscape the shutter is centred on the **true centre of the
 * screen** rather than the centre of what remains after a top or bottom inset — because a
 * left/right band applies no top or bottom inset at all.
 *
 * Robolectric reports zero for every inset (CLAUDE.md, known pitfalls), so cut-out clearance is
 * emulator- and device-only by construction; nothing in the test suite claims it.
 */
@Composable
internal fun BoxScope.CameraBand(
    edge: ScreenEdge,
    modifier: Modifier = Modifier,
    /** The band's own extent inward from [edge]. Zero at full-bleed; the one value a ratio feature changes. */
    thickness: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val sides = when (edge) {
        ScreenEdge.Top -> WindowInsetsSides.Top
        ScreenEdge.Bottom -> WindowInsetsSides.Bottom
        ScreenEdge.Left -> WindowInsetsSides.Left
        ScreenEdge.Right -> WindowInsetsSides.Right
    }
    val alignment = when (edge) {
        ScreenEdge.Top -> Alignment.TopCenter
        ScreenEdge.Bottom -> Alignment.BottomCenter
        ScreenEdge.Left -> Alignment.CenterStart
        ScreenEdge.Right -> Alignment.CenterEnd
    }
    Box(
        modifier = modifier
            .align(alignment)
            .windowInsetsPadding(WindowInsets.displayCutout.union(WindowInsets.navigationBars).only(sides))
            .then(if (edge.isHorizontal) Modifier.fillMaxWidth().heightIn(min = thickness) else Modifier.fillMaxHeight().widthIn(min = thickness)),
        contentAlignment = alignment,
        content = content,
    )
}

/**
 * The strip: the band on the punch-hole edge and what lives in it. Geometry and structure only —
 * what goes in it is PR #103's, and no working control lives here.
 *
 * **There is no Done control** (owner, 2026-09-18). The overlay build first moved Done in here
 * from the top-left corner, as an outlined ✕; the owner then removed it, because the navigation
 * bar's Back was already wired to the same close — Done's `onClick` and the `Dialog`'s
 * `onDismissRequest` were the one `onDismiss` lambda, reaching `InAppCameraViewModel.close()` —
 * so Done was a second control for one function. Back carries everything Done did: the camera
 * closes, the photos already handed over stand, and the status bar returns with the dialog's
 * window. `InAppCameraDialogTest` presses Back through the dialog's own dispatcher to prove it.
 *
 * With nothing resident, **the empty strip is a production state**, not a test-only one: gate
 * the placeholder off and the strip composes nothing and takes no space (rule 9).
 *
 * Along a horizontal edge the strip is a row; along a vertical one, a column. Every glyph turns in
 * place by [rotateWithDevice], the same rule as the shutter band's.
 *
 * **The placeholder** shows the strip at the size a real control row occupies, outlined and with
 * no background, so the owner can judge size, position and legibility before a real control
 * exists. It is **gated by one constant**, [SHOW_STRIP_PLACEHOLDER]: flipping it to `false` is the
 * one edit that removes it from the build. Whether it ships is the owner's decision after seeing
 * it; the default here is on so that it can be seen.
 */
@Composable
internal fun BoxScope.CameraStrip(
    edge: ScreenEdge,
    deviceRotation: Int?,
    /** The strip's slot for what PR #103 adds, given the edge it runs along and the device reading for [rotateWithDevice]; null composes nothing there. */
    content: (@Composable (edge: ScreenEdge, deviceRotation: Int?) -> Unit)? = defaultStripContent(),
) {
    if (content == null) return
    CameraBand(edge = edge, modifier = Modifier.testTag(CAMERA_STRIP_TAG)) {
        if (edge.isHorizontal) {
            Row(
                modifier = Modifier.fillMaxWidth().height(STRIP_ROW_HEIGHT),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(Modifier.weight(1f).fillMaxHeight()) { content(edge, deviceRotation) }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxHeight().width(STRIP_ROW_HEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) { content(edge, deviceRotation) }
            }
        }
    }
}

/** What the strip holds by default: the placeholder while [SHOW_STRIP_PLACEHOLDER] is on, nothing otherwise. */
internal fun defaultStripContent(): (@Composable (ScreenEdge, Int?) -> Unit)? =
    if (SHOW_STRIP_PLACEHOLDER) ({ edge, rotation -> StripPlaceholder(edge, rotation) }) else null

/** An outlined, transparent slot the size of a control row, so the strip can be judged before it has controls. */
@Composable
internal fun StripPlaceholder(edge: ScreenEdge, deviceRotation: Int?) {
    val shape = RoundedCornerShape(Spacing.sm)
    Box(
        modifier = Modifier
            .padding(Spacing.xs)
            .then(if (edge.isHorizontal) Modifier.fillMaxWidth().height(STRIP_ROW_HEIGHT - Spacing.sm) else Modifier.fillMaxHeight().width(STRIP_ROW_HEIGHT - Spacing.sm))
            .border(OVERLAY_OUTLINE_WIDTH, OverlayOutline, shape)
            .padding(OVERLAY_OUTLINE_WIDTH / 2)
            .border(OVERLAY_OUTLINE_WIDTH / 2, OverlayFill, shape)
            .testTag(CAMERA_STRIP_PLACEHOLDER_TAG),
        contentAlignment = Alignment.Center,
    ) {
        OverlayText(
            STRIP_PLACEHOLDER_LABEL,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.rotateWithDevice(deviceRotation),
        )
    }
}

/** One edit gates the placeholder: `false` here and it is not composed. Owner's decision after seeing it. */
internal const val SHOW_STRIP_PLACEHOLDER = true

/** A control row: Material's minimum touch target. */
internal val STRIP_ROW_HEIGHT: Dp = 48.dp

internal const val CAMERA_STRIP_TAG = "in-app-camera-strip"
internal const val CAMERA_STRIP_PLACEHOLDER_TAG = "in-app-camera-strip-placeholder"
/** Short enough to sit upright inside a one-row-deep strip along a vertical edge without wrapping — "Controls" broke into "Contr/ols" there on the emulator. */
internal const val STRIP_PLACEHOLDER_LABEL = "Strip"
