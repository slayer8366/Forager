package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.MutableTransitionState
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.NavigationFacing
import com.zynergylabs.forager.app.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Dispatch 2026-10-12-08 (RECORD -945, -948, -950): the navigation info, without a surface of its own: inside the compass strip's
 * card under a thin line in portrait ("have the card just extend down rather than a dual tuck"), and inside [NavigationHud]'s card
 * in landscape. As the owner's approved drawing has it:
 *
 * ```
 * |        804 ft         | Sunset 12:41 AM X|
 * | ahead 1° ▲ ▲ 134° SE  | in 2 h 28 min    |
 * |       by trail        | dark 1:13        |
 * ```
 * - **Left part**, each line centred (RECORD -945, "Both (Recommended)"): the distance, about twice the other text; the turn words
 *   with the needle after them and the north arrow and heading right after that, the two arrows neighbours ("The arrows being
 *   side by side is a compass visual"); the kind word, "by trail" or "straight", a small line of its own (RECORD -945, "Full
 *   dividers + by trail under figure").
 * - **A divider** the parts' full height (RECORD -945), kept when there is no evening stack (RECORD -951).
 * - **Right part**: the evening stack (RECORD -941), centred; then the X at the right edge.
 * - Under them, each only while it has something to say: the route message with Try again, and the status line. The status line
 *   leaves out what the strip already says about the position (no fix, approximate, last seen: RECORD -951, shown once, on the
 *   strip).
 * Height, the grid reference and the three-dot button are the strip's, so nothing shows twice (RECORD -950).
 */
@Composable
internal fun NavigationInfo(
    heading: State<TrueHeadingReading>,
    liveFix: LocationFix.Update?,
    target: Waypoint?,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    showDecimalDegrees: Boolean,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    route: ReturnRoute? = null,
    onRetryRoute: () -> Unit = {},
    facing: NavigationFacing = NavigationFacing.FACING_UP,
    approximateFix: LocationFix.Update? = null,
    lastKnownFix: LocationFix.Update? = null,
    sundownLine: String? = null,
    backByLine: String? = null,
    eveningLineBottomLimitInRootPx: Float? = null,
    eveningLineTopLimitInRootPx: Float? = null,
    arrivalHeld: Boolean = false,
) {
    // Read here, in this leaf, never higher — see rememberTrueHeading's own doc comment.
    val reading by heading
    // The age ticker: the fix's timestamp is fixed, the clock moves. Lives in this composable so
    // only this panel recomposes each second.
    val now by produceState(initialValue = currentTime.nowEpochMillis(), currentTime) {
        while (true) {
            delay(AGE_TICK_MILLIS)
            value = currentTime.nowEpochMillis()
        }
    }
    val liveReadout = hudReadout(reading, liveFix, approximateFix, lastKnownFix, target, distanceUnit, now, showDecimalDegrees, route, facing)
    val readout = if (arrivalHeld) heldArrivalReadout(liveReadout) else liveReadout
    // RECORD -955: the info's own bottom in the root, for a card anchored to the bottom edge (RECORD -953), whose top is its bottom
    // less its height. The height the decision uses is built from parts the evening stack does not change (the top padding, the left
    // part, the lines below, the bottom padding), never read back from the card: a first build read the card's measured height,
    // which mid-animation fed the decision back into itself and never settled (CI 38304300338, the T11 harness at font 2.0).
    var infoBottomInRootPx by remember { mutableFloatStateOf(Float.NaN) }
    var lastEveningShown by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { infoBottomInRootPx = it.positionInRoot().y + it.size.height },
    ) {
        val metrics = rememberInfoMetrics()
        val partsPx = constraints.maxWidth - metrics.fixedPx
        // RECORD -770: where the parts and the lines under them end without the evening stack, which the stack does not change, so
        // the decision to leave it out cannot feed back into what it is decided on.
        var partsTopInRootPx by remember { mutableFloatStateOf(Float.NaN) }
        var leftHeightPx by remember { mutableFloatStateOf(0f) }
        var linesBelowHeightPx by remember { mutableFloatStateOf(0f) }
        val pieces = eveningPieces(sundownLine, backByLine)
        val eveningStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
        val pieceHeightPx = rememberTextMeasurer().measure("Sunset", eveningStyle, maxLines = 1).size.height
        val bottomPaddingPx = with(LocalDensity.current) { Spacing.xs.toPx() }
        val topPaddingPx = with(LocalDensity.current) { CARD_TOP_PADDING.toPx() }
        val heightWithStackPx = topPaddingPx + maxOf(leftHeightPx, (pieces.size * pieceHeightPx).toFloat()) + linesBelowHeightPx + bottomPaddingPx
        // A margin once shown, so a top a pixel either side of the limit cannot flip the decision back and forth.
        val marginPx = if (lastEveningShown) with(LocalDensity.current) { EVENING_LIMIT_MARGIN.toPx() } else 0f
        val eveningShown = pieces.isNotEmpty() && eveningLineFits(
            partsTopInRootPx + linesBelowHeightPx,
            maxOf(leftHeightPx, (pieces.size * pieceHeightPx).toFloat()),
            bottomPaddingPx,
            eveningLineBottomLimitInRootPx,
        ) && eveningStackFitsBelow(infoBottomInRootPx, heightWithStackPx, eveningLineTopLimitInRootPx?.let { it - marginPx })
        SideEffect { lastEveningShown = eveningShown }
        val shownSundown = sundownLine.takeIf { eveningShown }
        val shownBackBy = backByLine.takeIf { eveningShown }
        // Each evening line's own visibility, so the right part keeps a leaving line's width until its exit has run. Without it the
        // part dropped to no width the moment the lines went, and the pieces still animating out wrapped a character a line,
        // spiking the card's height for the length of the exit (caught by HudSundownLineGrowTest, CI run 38259665051).
        val sundownVisibility = remember { MutableTransitionState(shownSundown != null) }
        val backByVisibility = remember { MutableTransitionState(shownBackBy != null) }
        SideEffect {
            sundownVisibility.targetState = shownSundown != null
            backByVisibility.targetState = shownBackBy != null
        }
        val lastSundown = rememberLastShown(shownSundown)
        val lastBackBy = rememberLastShown(shownBackBy)
        val holdSundown = shownSundown ?: lastSundown.takeIf { sundownVisibility.currentState || !sundownVisibility.isIdle }
        val holdBackBy = shownBackBy ?: lastBackBy.takeIf { backByVisibility.currentState || !backByVisibility.isIdle }
        val fit = measuredNavigationInfoFit(readout, partsPx, metrics, holdSundown, holdBackBy)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // RECORD -936 (the owner: "Yes, slightly tighter (Recommended)"): 4 dp at the sides; RECORD -943: 2 dp at the top.
                .padding(start = CARD_SIDE_PADDING, end = CARD_SIDE_PADDING, top = CARD_TOP_PADDING, bottom = Spacing.xs),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                InfoParts(
                    readout = readout,
                    fit = fit,
                    metrics = metrics,
                    sundownLine = shownSundown,
                    backByLine = shownBackBy,
                    sundownVisibility = sundownVisibility,
                    backByVisibility = backByVisibility,
                    reserveRight = eveningPieces(holdSundown, holdBackBy).isNotEmpty(),
                    onLeftHeight = { leftHeightPx = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { partsTopInRootPx = it.positionInRoot().y },
                )
                Column(modifier = Modifier.onGloballyPositioned { linesBelowHeightPx = it.size.height.toFloat() }) {
                    CardRouteAndStatusLines(readout, partsPx, onRetryRoute)
                }
            }
            // The X at the card's right edge, top-aligned beside the parts and the lines under them, its own 48 dp touch box.
            BouncingIconButton(
                onClick = onExit,
                modifier = Modifier.testTag(NAVIGATION_HUD_EXIT_TAG),
            ) {
                Icon(imageVector = Icons.Filled.Close, contentDescription = "Stop navigating")
            }
        }
    }
}

/**
 * Dispatch 2026-10-12-08: the navigation info's fixed parts, in pixels at this density and font. [fixedPx] is what the info takes
 * from its width before the two parts: its side padding and the X.
 */
internal class InfoMetrics(
    val fixedPx: Int,
    val dividerPx: Int,
    val gapPx: Int,
    val arrowPx: Int,
)

@Composable
private fun rememberInfoMetrics(): InfoMetrics = with(LocalDensity.current) {
    val padding = 2 * CARD_SIDE_PADDING.roundToPx()
    InfoMetrics(
        fixedPx = padding + HUD_EXIT_TOUCH_SIZE.roundToPx(),
        dividerPx = CARD_DIVIDER_WIDTH.roundToPx() + 2 * CARD_DIVIDER_GAP.roundToPx(),
        gapPx = Spacing.xs.roundToPx(),
        arrowPx = COMPASS_ICON_SIZE.roundToPx(),
    )
}

/**
 * RECORD -945 (the owner: "Both (Recommended)", a bigger distance): about twice the size of the other text, as in the mockup:
 * headlineSmall's 24 sp against labelMedium's 12, bold, tabular figures. Was titleMedium bold, one step up.
 */
@Composable
internal fun cardFigureStyle(): TextStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/**
 * The figure the left part draws: the route message moves to its own line under the parts (see [CardRouteAndStatusLines]);
 * with [short], "within 16 ft" reads "≤ 16 ft" (RECORD -936).
 */
internal fun columnFigure(readout: NavigationHudReadout, short: Boolean = false): String = when {
    readout.distanceText == ROUTE_UNAVAILABLE_TEXT -> NO_HEADING_TEXT
    short -> distanceShortForm(readout.distanceText) ?: readout.distanceText
    else -> readout.distanceText
}

@Composable
private fun measuredNavigationInfoFit(
    readout: NavigationHudReadout,
    partsPx: Int,
    metrics: InfoMetrics,
    sundownLine: String?,
    backByLine: String?,
): NavigationInfoFit {
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium
    val evening = small.copy(fontFeatureSettings = "tnum")
    val figureStyle = cardFigureStyle()
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val turn = readout.targetText
    return navigationInfoFit(
        NavigationInfoWidths(
            availablePx = partsPx,
            dividerPx = metrics.dividerPx,
            gapPx = metrics.gapPx,
            arrowPx = metrics.arrowPx,
            figurePx = widthOf(columnFigure(readout), figureStyle),
            figureShortPx = distanceShortForm(readout.distanceText)?.let { widthOf(it, figureStyle) },
            kindPx = readout.distanceKindText?.let { widthOf(it, small) },
            turnWholePx = turn.takeIf { it.isNotEmpty() }?.let { widthOf(it, small) },
            turnShortPx = turnShortForm(turn)?.let { widthOf(it, small) },
            headingPx = if (readout.secondRowShown) widthOf(readout.headingText, small.copy(fontFeatureSettings = "tnum")) else 0,
            headingIsStatus = !readout.headingIsReading,
            headingIconPx = metrics.arrowPx,
            eveningPiecePx = eveningPieces(sundownLine, backByLine).maxOfOrNull { widthOf(it, evening) },
        ),
        turnWhole = turn,
        turnShort = turnShortForm(turn),
    ).let { fit -> if (fit.overflows) fit.also { android.util.Log.w(CARD_LOG_TAG, "navigation info overflows at $partsPx px: $fit") } else fit }
}

/** The X's touch box: Material's icon button at its 48 dp minimum interactive size. */
private val HUD_EXIT_TOUCH_SIZE = 48.dp

/** RECORD -955: the evening lines, once shown, stay while the card's top is within this of its limit (no flip-flop). */
private val EVENING_LIMIT_MARGIN = 4.dp

/** RECORD -936 (the owner: "Yes, slightly tighter (Recommended)"): the card's own side padding. */
private val CARD_SIDE_PADDING = Spacing.xs
/** RECORD -943: the card's top padding, under the pill. */
private val CARD_TOP_PADDING = 2.dp
private const val CARD_LOG_TAG = "NavigationCard"

private const val AGE_TICK_MILLIS = 1_000L
