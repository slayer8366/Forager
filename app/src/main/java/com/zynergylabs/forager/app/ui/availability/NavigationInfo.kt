package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.animation.core.MutableTransitionState
import com.zynergylabs.forager.app.ui.motion.BouncingIconButton
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.DistanceKind
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
 * Since dispatch 2026-11-05-10 (RECORD -2181) the kind is on the distance's line, and since its Amendment 1 the turn line is the
 * degrees and the needle alone ("1° ▲"; G2 dropped the words, H1 gave the north arrow and the heading back to the strip), each in a
 * fixed space (G3). The bullets below describe the drawing as approved.
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
    /**
     * D42 (RECORD -2021, the owner: "Only where room is short"): the evening lines leave the card while the route card resting on it
     * would not otherwise fit, decided by the host from measured heights; [onEveningShown] is told whether they show.
     */
    eveningStepsAside: Boolean = false,
    onEveningShown: (Boolean) -> Unit = {},
    /** J7 ([nextTurnPlaceNow]): the evening line can't fit on the card even moved up, decided by the host ([NavigationCardLift.NoRoom]). */
    eveningOffForRoom: Boolean = false,
    /** The strip's live height above sea level (dispatch 2026-10-28-13), so the card's cannot differ from it. */
    heightMeters: Double? = null,
    /**
     * J3 step 3 and J4 ([navigationCardLift]): told the card's height with its evening line, as RECORD -955 reckons it (from parts the
     * line does not change), or NaN with no evening line; and whether this is landscape at font 1.5 or more.
     */
    onHeightWithEvening: (heightPx: Float, landscapeLargeText: Boolean) -> Unit = { _, _ -> },
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
    val liveReadout = hudReadout(reading, liveFix, approximateFix, lastKnownFix, target, distanceUnit, now, showDecimalDegrees, route, facing, heightMeters)
    val readout = if (arrivalHeld) heldArrivalReadout(liveReadout) else liveReadout
    // RECORD -955: the info's own bottom in the root, for a card anchored to the bottom edge (RECORD -953), whose top is its bottom
    // less its height. The height the decision uses is built from parts the evening stack does not change (the top padding, the left
    // part, the lines below, the bottom padding), never read back from the card: a first build read the card's measured height,
    // which mid-animation fed the decision back into itself and never settled (CI 38304300338, the T11 harness at font 2.0). Since
    // G3 and A3 the kind's step, and so the left part's height, depends on the evening line; H12 (below, `fit`) keeps the left part
    // as with the line wherever that would differ, so this holds again.
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
        // J7: where the next turn was when [leftHeightPx] was measured, so the left part's height with the evening line is reckoned
        // from what was drawn, whichever place that was.
        var leftMeasuredPlace by remember { mutableStateOf(NextTurnPlace.OWN_LINE) }
        var linesBelowHeightPx by remember { mutableFloatStateOf(0f) }
        // Amendment 1, H2 to H4: the status words are in the right part, under the evening lines; their measured height.
        var statusHeightPx by remember { mutableFloatStateOf(0f) }
        // Amendment 1, H5: whether the evening line breaks at the owner's ▪︎, and the right part's width with none showing.
        val evening = rememberCardEvening(partsPx, metrics, distanceUnit)
        val pieces = eveningPieces(sundownLine, backByLine, evening.breaks)
        val eveningStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
        val pieceHeightPx = rememberTextMeasurer().measure("Sunset", eveningStyle, maxLines = 1).size.height
        val bottomPaddingPx = with(LocalDensity.current) { Spacing.xs.toPx() }
        val topPaddingPx = with(LocalDensity.current) { CARD_TOP_PADDING.toPx() }
        // H12 (below, `fit`): the card laid out as with the evening line, read here for the right part's width with it.
        val withEveningFit = measuredNavigationInfoFit(readout, partsPx, metrics, sundownLine, backByLine, distanceUnit, evening)
        // J5, the defect (RECORD -2183): each piece as it wraps in the right part's width with the line, not one line each. Counted
        // as one line each, the card moved up at 640 x 360, font 2.0, with the S22's insets was drawn 366 px taller than decided,
        // its top off the screen (probe run 42421420016).
        val stackRightPx = maxOf(1, partsPx - metrics.dividerPx - leftPartWidthPx(partsPx, metrics.dividerPx, withEveningFit))
        val pieceMeasurer = rememberTextMeasurer()
        val stackPiecesPx = eveningStackHeightPx(pieces, pieceHeightPx) { pieceMeasurer.measure(it, eveningStyle, constraints = Constraints(maxWidth = stackRightPx)).size.height }
        val rightWithStackPx = stackPiecesPx + statusHeightPx
        // J7 (RECORD -2183, "it needs a check that this can't loop"): in landscape at font 1.5 and up the card with its evening line has
        // the next turn on its own line (J3 step 3), so its height with the line counts that line wherever the next turn is drawn now.
        // Read as drawn, the right part (J7, the line off for room) took a line off this height, which could make room for the line,
        // which put the next turn back on its own line, which took the room again.
        val ownLinePx = maxOf(with(LocalDensity.current) { COMPASS_ICON_SIZE.toPx() }, rememberTextMeasurer().measure("8", MaterialTheme.typography.labelMedium, maxLines = 1).size.height.toFloat())
        val leftWithEveningPx = leftHeightPx + if (evening.landscapeLargeText && leftMeasuredPlace != NextTurnPlace.OWN_LINE) ownLinePx else 0f
        val heightWithStackPx = topPaddingPx + maxOf(leftWithEveningPx, rightWithStackPx) + linesBelowHeightPx + bottomPaddingPx
        // A margin once shown, so a top a pixel either side of the limit cannot flip the decision back and forth.
        val marginPx = if (lastEveningShown) with(LocalDensity.current) { EVENING_LIMIT_MARGIN.toPx() } else 0f
        val eveningShown = !eveningStepsAside && pieces.isNotEmpty() && eveningLineFits(
            partsTopInRootPx + linesBelowHeightPx,
            maxOf(leftWithEveningPx, rightWithStackPx),
            bottomPaddingPx,
            eveningLineBottomLimitInRootPx,
        ) && eveningStackFitsBelow(infoBottomInRootPx, heightWithStackPx, eveningLineTopLimitInRootPx?.let { it - marginPx })
        val currentOnHeightWithEvening by rememberUpdatedState(onHeightWithEvening)
        SideEffect { currentOnHeightWithEvening(if (pieces.isNotEmpty() && leftHeightPx > 0f) heightWithStackPx else Float.NaN, evening.landscapeLargeText) }
        val currentOnEveningShown by rememberUpdatedState(onEveningShown)
        SideEffect {
            // D42: stepping aside leaves -955's memory as it was, so when the route card closes the lines come back on the same terms
            // they showed on before it opened (the margin once shown), rather than on a fresh decision.
            if (!eveningStepsAside) lastEveningShown = eveningShown
            currentOnEveningShown(eveningShown)
        }
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
        // Dispatch 2026-11-06-01, Amendment 1, J2: the next turn the route carries (worked out where the line and the walker's place
        // live), as the space draws and says it; blank in every state but a figure (E1) and on a way with no trail route (E2, E3).
        val nextTurnShown = nextTurnSpaceShown(readout, hasFix = liveFix != null, ahead = nextTurnOf(route), unit = distanceUnit)
        // J3: in landscape at font 2.0 the right part with no evening line, its own line with one; J2's decision everywhere else.
        // J7: the right part too where the line can't fit even moved up.
        val nextTurnPlace = nextTurnPlaceNow(evening.nextTurnPlace, evening.landscapeLargeText, eveningLine = pieces.isNotEmpty(), eveningStepsAside = eveningStepsAside, eveningOffForRoom = eveningOffForRoom)
        val heldFit = measuredNavigationInfoFit(readout, partsPx, metrics, holdSundown, holdBackBy, distanceUnit, evening)
        // Amendment 1, H12 (the owner: "(a) Leave out "Dark since"", the option reading "In that one case the evening line doesn't show;
        // "by trail" keeps its own line"; the planner's reading: "the decision must settle, never loop"): where the evening line would
        // change the kind's step and is left out for room (not stepping aside for the route card, D42), the card is laid out as with
        // it: the kind keeps its step and the right part the line's width. So the left part's height the decision above reads is the
        // same whether or not the line shows, and the decision cannot feed back into itself. Was the card as drawn without the line,
        // so at 823 x 384, font 2.0, with no compass, leaving the line out put the kind beside the distance, which made room for the
        // line again, every few frames (E13, stop 2, run 42278700471).
        val fit = if (!eveningStepsAside && pieces.isNotEmpty() && withEveningFit.topLine.kind != heldFit.topLine.kind) withEveningFit else heldFit
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
                    eveningBreaks = evening.breaks,
                    eveningHeld = eveningPieces(holdSundown, holdBackBy).isNotEmpty(),
                    statusInRight = !evening.statusBelow,
                    nextTurn = nextTurnShown,
                    nextTurnPlace = nextTurnPlace,
                    distanceUnit = distanceUnit,
                    onLeftHeight = { leftHeightPx = it; leftMeasuredPlace = nextTurnPlace },
                    onStatusHeight = { statusHeightPx = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { partsTopInRootPx = it.positionInRoot().y },
                )
                Column(modifier = Modifier.onGloballyPositioned { linesBelowHeightPx = it.size.height.toFloat() }) {
                    CardRouteLines(readout, partsPx, onRetryRoute)
                    // H8: where the right part is too narrow for the status words' widest word, they keep their own full-width line here;
                    // H10: one line, the more urgent ([oneStatusLine]).
                    if (evening.statusBelow) StatusSpace(readout, oneLine = true)
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
    distanceUnit: DistanceUnit,
    cardEvening: CardEvening,
): NavigationInfoFit {
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium
    val evening = small.copy(fontFeatureSettings = "tnum")
    val figureStyle = cardFigureStyle()
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val turn = readout.targetText
    // Amendment 1, G3 ("The turn line"): the degrees' fixed space, measured once per type; E18, as wide as the widest of "0°" to
    // "180°", "Target" and "No target", so the needle never moves ([widestTurnSpacePx]).
    val turnSpacePx = remember(measurer, small) { widestTurnSpacePx { widthOf(it, small) } }
    // G3: the left part is sized for the widest figure the walk could show in this unit, either kind, so where it is short of room it
    // keeps one width as the number changes ([longestCardFigurePx]).
    val longestFigurePx = remember(measurer, figureStyle, distanceUnit) { longestCardFigurePx(distanceUnit) { widthOf(it, figureStyle) } }
    return navigationInfoFit(
        NavigationInfoWidths(
            availablePx = partsPx,
            dividerPx = metrics.dividerPx,
            gapPx = metrics.gapPx,
            arrowPx = metrics.arrowPx,
            figurePx = widthOf(columnFigure(readout), figureStyle),
            figureShortPx = distanceShortForm(readout.distanceText)?.let { widthOf(it, figureStyle) },
            longestFigurePx = longestFigurePx,
            kindPx = readout.distanceKindText?.let { widthOf(it, small) },
            turnSpacePx = turnSpacePx,
            eveningPiecePx = eveningPieces(sundownLine, backByLine, cardEvening.breaks).maxOfOrNull { widthOf(it, evening) },
            rightFloorPx = cardEvening.rightFloorPx,
        ),
        turnText = turn,
    ).let { fit -> if (fit.overflows) fit.also { android.util.Log.w(CARD_LOG_TAG, "navigation info overflows at $partsPx px: $fit") } else fit }
        .let { fit ->
            // Dispatch 2026-11-05-10 (RECORD -2181): where the kind goes on the top line ([topLinePlace]).
            val place = topLinePlace(
                kindText = readout.distanceKindText,
                shownFigure = columnFigure(readout, fit.figureShort),
                unit = distanceUnit,
                fit = fit,
                totalPx = partsPx,
                dividerPx = metrics.dividerPx,
                gapPx = metrics.gapPx,
                figurePx = { widthOf(it, figureStyle) },
                kindPx = { widthOf(it, small) },
            )
            fit.copy(topLine = place)
        }
}

/**
 * Amendment 1, H5: whether the evening line breaks at the ▪︎ ([eveningBreaks]), and the right part's width with no evening line; H8:
 * whether the status words take their own line under the parts ([statusBelowTheParts]), where the right part then keeps no daytime
 * width.
 */
internal class CardEvening(
    val breaks: Boolean,
    val rightFloorPx: Int,
    val statusBelow: Boolean = false,
    /** J2: beside the needle or on its own line, for this window and font ([nextTurnPlace]). */
    val nextTurnPlace: NextTurnPlace = NextTurnPlace.OWN_LINE,
    /** J3: landscape at font 2.0, where the evening line decides between the right part and the own line ([nextTurnPlaceNow]). */
    val landscapeLargeText: Boolean = false,
)

/**
 * H5 and the planner's reading of Q3: decided once for the card's width, font and the phone's clock, never from today's times, so it
 * does not change as the evening comes. The line breaks unless it fits whole beside the left part's widest need with the next-turn
 * space reserved ([nextTurnReservePx]); the right part is then as wide as the broken form's wider line (or the whole line), evening
 * line or none.
 */
@Composable
private fun rememberCardEvening(partsPx: Int, metrics: InfoMetrics, distanceUnit: DistanceUnit): CardEvening {
    val measurer = rememberTextMeasurer()
    val small = MaterialTheme.typography.labelMedium
    val evening = small.copy(fontFeatureSettings = "tnum")
    val figureStyle = cardFigureStyle()
    val clock = rememberSundownClock()
    // J3: landscape at font 2.0, read once per configuration like the rest of this decision.
    val landscapeLargeText = isLandscapeLargeText(
        landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
        fontScale = LocalDensity.current.fontScale,
    )
    return remember(measurer, small, figureStyle, clock, partsPx, metrics.gapPx, metrics.arrowPx, metrics.dividerPx, distanceUnit, landscapeLargeText) {
        fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val widths = cardEveningWidths(clock) { widthOf(it, evening) }
        val turnLinePx = widestTurnSpacePx { widthOf(it, small) } + metrics.gapPx + metrics.arrowPx
        val reservePx = nextTurnReservePx(metrics.gapPx, metrics.arrowPx, widthOf(longestTurnDistance(distanceUnit), evening))
        val leftNeedPx = maxOf(longestCardFigurePx(distanceUnit) { widthOf(it, figureStyle) }, turnLinePx + reservePx)
        val breaks = eveningBreaks(partsPx, metrics.dividerPx, leftNeedPx, widths)
        val floorPx = if (breaks) widths.brokenPx else widths.oneLinePx
        // H8: the left part's widest need with no reserve (the widest figure, the turn line, the wider kind), as the fit counts it.
        val leftPartNeedPx = maxOf(longestCardFigurePx(distanceUnit) { widthOf(it, figureStyle) }, turnLinePx, DistanceKind.entries.maxOf { widthOf(it.words, small) })
        val statusBelow = statusBelowTheParts(partsPx, metrics.dividerPx, leftPartNeedPx, floorPx, widestStatusWordPx { widthOf(it, small) })
        // J2: beside the needle only where it fits the left part at its narrowest, every evening piece counted, so it never flips as the
        // evening comes. The next turn's space as NextTurnSpace sizes it: its arrow (the needle's size), a gap, the widest figure.
        val nextTurnSpacePx = metrics.arrowPx + metrics.gapPx + NEXT_TURN_WIDEST_FIGURES.getValue(distanceUnit).maxOf { widthOf(it, small) }
        // J3 step 2: where the next turn can be in the right part, that part is never narrower than its space, evening line or none, so
        // the left part keeps one width as the evening comes and goes.
        val rightFloorPx = maxOf(if (statusBelow) 0 else floorPx, if (landscapeLargeText) nextTurnSpacePx else 0)
        val widestEveningPiecePx = cardEveningPiecesAtWidest(clock, breaks).maxOf { widthOf(it, evening) }
        val place = nextTurnPlace(partsPx, metrics.dividerPx, leftPartNeedPx, rightFloorPx, widestEveningPiecePx, turnLinePx, metrics.gapPx, nextTurnSpacePx)
        CardEvening(breaks = breaks, rightFloorPx = rightFloorPx, statusBelow = statusBelow, nextTurnPlace = place, landscapeLargeText = landscapeLargeText)
    }
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
