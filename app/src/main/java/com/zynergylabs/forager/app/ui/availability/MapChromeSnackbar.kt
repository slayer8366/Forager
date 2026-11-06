package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Material3's snackbar shadow (`SnackbarTokens.ContainerElevation`, `ElevationTokens.Level3`, read from the material3 1.5.0-alpha26
 * bytecode), which the notice keeps where it covers no map (dispatch 2026-11-05-06, A3: "The same component shown where it covers no
 * map stays as it is").
 */
internal val MATERIAL_SNACKBAR_SHADOW_ELEVATION = 6.dp

/** Material's dismiss button's description (its `Strings.SnackbarDismiss`), kept word for word. */
internal const val NOTICE_DISMISS_DESCRIPTION = "Dismiss"

/**
 * Dispatch 2026-11-05-06 (RECORD -2155, the owner: "App's own notice (Recommended)", whose option read "The app gets its own version:
 * the same layout, words and buttons, with no shadow and the thin outline, so it reads an even 80% like every other window"). The
 * one host, `ScaffoldSnackbarHost`, used Material3's `Snackbar(snackbarData)`, which draws a 6 dp shadow and takes neither an
 * elevation nor a border, so under its 0.8 fill over the map the shadow showed as a band (Phase 1 report, sections 1 and 2).
 *
 * This is that snackbar with two parameters added, [shadowElevation] and [border]; everything else is Material's, from its source
 * (androidx-main `Snackbar.kt`) checked against the 1.5.0-alpha26 bytecode the app ships (its constants: 600, 30, 16, 8, 6 and 8 dp):
 * - the 12 dp margin inside [modifier] (`SNACKBAR_SURFACE_MARGIN`, which the host's tap box already matches);
 * - the message in `bodyMedium`, the action as a `TextButton` coloured [actionColor] in `labelLarge`, the dismiss as an `IconButton`
 *   with Material's tooltip and its "Dismiss" description;
 * - `LegacyOneRowSnackbar`'s layout, which is what 1.5.0-alpha26 runs (`ComposeMaterial3Flags.isSnackbarStylingFixEnabled` is false;
 *   its static initialiser never sets it): see [NoticeOneRow].
 *
 * Not built: Material's layout with the action on a line of its own, which it uses only for `actionOnNewLine = true`. The host never
 * passes it, so nothing could reach it (RECORD -2156, item 2). `MapChromeSnackbarParityTest` holds this to Material's own, side by
 * side.
 */
@Composable
internal fun MapChromeSnackbar(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
    shape: Shape = SnackbarDefaults.shape,
    containerColor: Color = SnackbarDefaults.color,
    contentColor: Color = SnackbarDefaults.contentColor,
    actionColor: Color = SnackbarDefaults.actionColor,
    actionContentColor: Color = SnackbarDefaults.actionContentColor,
    dismissActionContentColor: Color = SnackbarDefaults.dismissActionContentColor,
    /** Material's 6 dp by default; the host passes none over the map. */
    shadowElevation: Dp = MATERIAL_SNACKBAR_SHADOW_ELEVATION,
    /** None by default; the host passes the map-chrome hairline over the map. */
    border: BorderStroke? = null,
) {
    val actionLabel = snackbarData.visuals.actionLabel
    val action: (@Composable () -> Unit)? = actionLabel?.let { label ->
        @Composable {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                onClick = { snackbarData.performAction() },
                content = { Text(label) },
            )
        }
    }
    val dismissAction: (@Composable () -> Unit)? = if (snackbarData.visuals.withDismissAction) {
        @Composable { NoticeDismissButton(onDismiss = { snackbarData.dismiss() }) }
    } else {
        null
    }
    Surface(
        modifier = modifier.padding(SNACKBAR_SURFACE_MARGIN),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = shadowElevation,
        border = border,
    ) {
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
            NoticeOneRow(
                text = { Text(snackbarData.visuals.message) },
                action = action,
                dismissAction = dismissAction,
                actionTextStyle = MaterialTheme.typography.labelLarge,
                actionTextColor = actionContentColor,
                dismissActionColor = dismissActionContentColor,
            )
        }
    }
}

/** Material's dismiss button: a close icon with its tooltip above it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoticeDismissButton(onDismiss: () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(NOTICE_DISMISS_DESCRIPTION) } },
        state = rememberTooltipState(),
    ) {
        IconButton(onClick = onDismiss) {
            Icon(Icons.Filled.Close, contentDescription = NOTICE_DISMISS_DESCRIPTION)
        }
    }
}

/** Material's `HorizontalSpacing`, `HorizontalSpacingButtonSide`, `TextEndExtraSpacing`, `LegacySnackbarVerticalPadding`, `HeightToFirstLine`, `ContainerMaxWidth`. */
private val NOTICE_START_PADDING = 16.dp
private val NOTICE_END_PADDING = 8.dp
private val NOTICE_TEXT_END_SPACING = 8.dp
private val NOTICE_TEXT_VERTICAL_PADDING = 6.dp
private val NOTICE_HEIGHT_TO_FIRST_LINE = 30.dp
private val NOTICE_MAX_WIDTH = 600.dp

/** `SnackbarTokens.SingleLineContainerHeight` and `TwoLinesContainerHeight`. */
private val NOTICE_ONE_LINE_MIN_HEIGHT = 48.dp
private val NOTICE_TWO_LINES_MIN_HEIGHT = 68.dp

private const val NOTICE_TEXT = "text"
private const val NOTICE_ACTION = "action"
private const val NOTICE_DISMISS = "dismissAction"

/**
 * Material3's `LegacyOneRowSnackbar`, rule for rule: the message beside its buttons, at most 600 dp wide. One line: at least 48 dp
 * tall, the message centred and the action on the message's baseline. More than one: at least 68 dp tall, the first baseline 30 dp
 * from the top and the action centred. The dismiss button is centred either way, at the end.
 */
@Composable
private fun NoticeOneRow(
    text: @Composable () -> Unit,
    action: @Composable (() -> Unit)?,
    dismissAction: @Composable (() -> Unit)?,
    actionTextStyle: TextStyle,
    actionTextColor: Color,
    dismissActionColor: Color,
) {
    Layout(
        {
            Box(Modifier.layoutId(NOTICE_TEXT).padding(vertical = NOTICE_TEXT_VERTICAL_PADDING)) { text() }
            if (action != null) {
                Box(Modifier.layoutId(NOTICE_ACTION)) {
                    CompositionLocalProvider(LocalContentColor provides actionTextColor, LocalTextStyle provides actionTextStyle, content = action)
                }
            }
            if (dismissAction != null) {
                Box(Modifier.layoutId(NOTICE_DISMISS)) {
                    CompositionLocalProvider(LocalContentColor provides dismissActionColor, content = dismissAction)
                }
            }
        },
        modifier = Modifier.padding(start = NOTICE_START_PADDING, end = if (dismissAction == null) NOTICE_END_PADDING else 0.dp),
    ) { measurables, constraints ->
        val containerWidth = min(constraints.maxWidth, NOTICE_MAX_WIDTH.roundToPx())
        val actionPlaceable = measurables.firstOrNull { it.layoutId == NOTICE_ACTION }?.measure(constraints)
        val dismissPlaceable = measurables.firstOrNull { it.layoutId == NOTICE_DISMISS }?.measure(constraints)
        val actionWidth = actionPlaceable?.width ?: 0
        val actionHeight = actionPlaceable?.height ?: 0
        val dismissWidth = dismissPlaceable?.width ?: 0
        val dismissHeight = dismissPlaceable?.height ?: 0
        val extraSpacingWidth = if (dismissWidth == 0) NOTICE_TEXT_END_SPACING.roundToPx() else 0
        val textMaxWidth = (containerWidth - actionWidth - dismissWidth - extraSpacingWidth).coerceAtLeast(constraints.minWidth)
        val textPlaceable = measurables.first { it.layoutId == NOTICE_TEXT }.measure(constraints.copy(minHeight = 0, maxWidth = textMaxWidth))

        val firstTextBaseline = textPlaceable[FirstBaseline]
        val lastTextBaseline = textPlaceable[LastBaseline]
        val hasText = firstTextBaseline != AlignmentLine.Unspecified && lastTextBaseline != AlignmentLine.Unspecified
        val isOneLine = firstTextBaseline == lastTextBaseline || !hasText
        val dismissX = containerWidth - dismissWidth
        val actionX = dismissX - actionWidth

        val textY: Int
        val containerHeight: Int
        val actionY: Int
        if (isOneLine) {
            containerHeight = max(NOTICE_ONE_LINE_MIN_HEIGHT.roundToPx(), max(actionHeight, dismissHeight))
            textY = (containerHeight - textPlaceable.height) / 2
            actionY = if (actionPlaceable != null) {
                actionPlaceable[FirstBaseline].let { if (it != AlignmentLine.Unspecified) textY + firstTextBaseline - it else 0 }
            } else {
                0
            }
        } else {
            textY = NOTICE_HEIGHT_TO_FIRST_LINE.roundToPx() - firstTextBaseline
            containerHeight = max(NOTICE_TWO_LINES_MIN_HEIGHT.roundToPx(), textY + textPlaceable.height)
            actionY = if (actionPlaceable != null) (containerHeight - actionPlaceable.height) / 2 else 0
        }
        val dismissY = if (dismissPlaceable != null) (containerHeight - dismissPlaceable.height) / 2 else 0

        layout(containerWidth, containerHeight) {
            textPlaceable.placeRelative(0, textY)
            actionPlaceable?.placeRelative(actionX, actionY)
            dismissPlaceable?.placeRelative(dismissX, dismissY)
        }
    }
}
