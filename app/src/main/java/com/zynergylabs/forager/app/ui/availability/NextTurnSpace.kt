package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.nextturn.NextTurnArrow
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The next turn's own space (dispatch 2026-11-06-01, Amendment 1, A1 and J, 2b-2 step 1): the arrow, then the figure, "↰ 1.1 mi".
 * Not yet on the card: step 2 puts it beside the needle (A1) once #498's layout is merged in, and measures the fit there (G2, G4).
 *
 * **Its own window.** CLAUDE.md, "Changing info gets its own window", the owner: "Info that changes and shares a line with other
 * text should get its own window so it doesn't shift all text on the same line." The space is always as wide as the arrow, the gap
 * and the widest figure [unit] can show ([NEXT_TURN_WIDEST_FIGURES]), in [style], whatever it holds; blank (E1 to E3), it keeps
 * that width, empty, so nothing beside it moves. The text is never shrunk to fit ("Keep the text size the same size", RECORD -2181).
 *
 * **TalkBack** reads [NextTurnSpaceShown.spoken] alone, "turn left in 400 feet" (G3), in place of the drawn figure; blank, nothing.
 */
@Composable
internal fun NextTurnSpace(
    shown: NextTurnSpaceShown,
    unit: DistanceUnit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelMedium,
    arrowSize: Dp = COMPASS_ICON_SIZE,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val widestFigurePx = remember(measurer, style, unit, density) {
        NEXT_TURN_WIDEST_FIGURES.getValue(unit).maxOf { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width }
    }
    val spaceWidth = with(density) { arrowSize + Spacing.xs + widestFigurePx.toDp() }
    Box(
        modifier = modifier
            .width(spaceWidth)
            .heightIn(min = arrowSize)
            .clearAndSetSemantics { nextTurnSemantics(shown) },
        contentAlignment = Alignment.Center,
    ) {
        val arrow = shown.arrow ?: return@Box
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Icon(imageVector = nextTurnIcon(arrow), contentDescription = null, modifier = Modifier.size(arrowSize))
            if (shown.figure != null) Text(text = shown.figure, style = style, maxLines = 1, softWrap = false)
        }
    }
}

private fun SemanticsPropertyReceiver.nextTurnSemantics(shown: NextTurnSpaceShown) {
    testTag = NEXT_TURN_SPACE_TAG
    shown.spoken?.let { contentDescription = it }
    shown.arrow?.let { this[NextTurnArrowKey] = it }
    shown.figure?.let { this[NextTurnFigureKey] = it }
}

/**
 * The Material icon for [arrow] (C1 to C3): Material's TurnSlightLeft/Right, TurnLeft/Right and TurnSharpLeft/Right, ForkLeft/Right
 * and Straight, the Filled set, not mirrored (C1: "not mirrored"), so a left arrow points left in a right-to-left language too.
 */
internal fun nextTurnIcon(arrow: NextTurnArrow): ImageVector = when (arrow) {
    NextTurnArrow.SLIGHT_LEFT -> Icons.Filled.TurnSlightLeft
    NextTurnArrow.LEFT -> Icons.Filled.TurnLeft
    NextTurnArrow.SHARP_LEFT -> Icons.Filled.TurnSharpLeft
    NextTurnArrow.SLIGHT_RIGHT -> Icons.Filled.TurnSlightRight
    NextTurnArrow.RIGHT -> Icons.Filled.TurnRight
    NextTurnArrow.SHARP_RIGHT -> Icons.Filled.TurnSharpRight
    NextTurnArrow.FORK_LEFT -> Icons.Filled.ForkLeft
    NextTurnArrow.FORK_RIGHT -> Icons.Filled.ForkRight
    NextTurnArrow.STRAIGHT -> Icons.Filled.Straight
}

/**
 * The widest figures each unit can show, measured for the space's width. Phase 1's (section 6): "the widest arrow plus "88.8 mi" or
 * "8888 ft"", and the metric pair the same way. A figure of 100 mi or more is wider and would draw past the space's end; the space
 * itself keeps its width, so nothing beside it moves.
 */
internal val NEXT_TURN_WIDEST_FIGURES: Map<DistanceUnit, List<String>> = mapOf(
    DistanceUnit.MILES to listOf("88.8 mi", "8888 ft"),
    DistanceUnit.KILOMETERS to listOf("88.8 km", "8888 m"),
)

internal const val NEXT_TURN_SPACE_TAG = "next-turn-space"

/** The arrow the space draws, for tests: an icon's drawing cannot be read back from the semantics tree. */
internal val NextTurnArrowKey = SemanticsPropertyKey<NextTurnArrow>("NextTurnArrow")

/** The figure the space draws, for tests: TalkBack reads [NextTurnSpaceShown.spoken] in its place. */
internal val NextTurnFigureKey = SemanticsPropertyKey<String>("NextTurnFigure")
