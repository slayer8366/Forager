package com.zynergylabs.forager.app.ui.availability

import androidx.compose.animation.core.MutableTransitionState
import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.WordSwap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.ui.unit.IntSize
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.motion.rememberLastShown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The evening lines broken at their " · " joins into short pieces, the sundown line's first, then Back by's (RECORD -941: "Sunset
 * 12:41 AM", "in 2 h 28 min", "dark 1:13", "Back by …").
 */
internal fun eveningPieces(sundownLine: String?, backByLine: String?): List<String> =
    listOfNotNull(sundownLine, backByLine).flatMap { line -> line.split(" · ").map(String::trim).filter(String::isNotEmpty) }

/**
 * RECORD -941: the evening lines stacked in the right part, one piece per line, centred (RECORD -945), never ending in "…" (a piece
 * wider than the part wraps; the fit reserves the widest piece's width, so it only wraps where the fit reports an overflow). Each
 * line keeps its own tag on a column whose text is the whole line, so it reads as it did; each piece carries
 * [NAVIGATION_HUD_EVENING_PIECE_TAG]. Each line fades and grows as before (RECORD -672), or only fades under reduced motion.
 */
@Composable
internal fun EveningStack(
    sundownLine: String?,
    backByLine: String?,
    sundownVisibility: MutableTransitionState<Boolean>,
    backByVisibility: MutableTransitionState<Boolean>,
) {
    val reduceMotion = LocalReduceMotion.current
    val lineFade = MotionTokens.mapPopUpFadeSpec<Float>()
    val lineGrow = MotionTokens.mapPopUpGrowSpec<IntSize>()
    val enter = if (reduceMotion) fadeIn(animationSpec = lineFade) else fadeIn(animationSpec = lineFade) + expandVertically(animationSpec = lineGrow, expandFrom = Alignment.Top)
    val exit = if (reduceMotion) fadeOut(animationSpec = lineFade) else fadeOut(animationSpec = lineFade) + shrinkVertically(animationSpec = lineGrow, shrinkTowards = Alignment.Top)
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        for ((line, tag, visibility) in listOf(
            Triple(sundownLine, NAVIGATION_HUD_SUNDOWN_LINE_TAG, sundownVisibility),
            Triple(backByLine, NAVIGATION_HUD_BACK_BY_LINE_TAG, backByVisibility),
        )) {
            val shownLine = rememberLastShown(line)
            AnimatedVisibility(visibleState = visibility, enter = enter, exit = exit, label = "hudEveningStack") {
                shownLine?.let { whole ->
                    Column(
                        modifier = Modifier.fillMaxWidth().semantics { text = AnnotatedString(whole) }.testTag(tag),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        eveningPieces(whole, null).forEach { piece ->
                            WordSwap(text = piece) { shown ->
                                Text(text = shown, style = style, modifier = Modifier.testTag(NAVIGATION_HUD_EVENING_PIECE_TAG))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The lines under the parts that say what is wrong or what else is true (RECORD -935: everything today's status line said, shown
 * only when it has something to say), in [widthPx]:
 * - the route message, when the route home cannot be calculated, with "Try again" beside it when recomputing could change that
 *   (RECORD -932, step 3; in landscape too, RECORD -934). It carries [NAVIGATION_HUD_DISTANCE_TAG], the large slot's tag, since
 *   it is the large slot's words moved here; the left part shows a dash meanwhile. Where even "No route" cannot sit beside
 *   "Try again" (font 2.0 in a narrow window), "Try again" takes its own line under it, as before;
 * - the status line. Since dispatch 2026-10-12-08 the kind word is a line of its own in the left part (RECORD -945), so it no
 *   longer leads this line; and what the compass strip already says about the position (no fix, approximate, last seen) is left
 *   out here, so it shows once, on the strip (RECORD -951).
 */
@Composable
internal fun CardRouteAndStatusLines(
    readout: NavigationHudReadout,
    widthPx: Int,
    onRetryRoute: () -> Unit,
) {
    val statusStyle = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    val gapPx = with(density) { Spacing.sm.roundToPx() }
    if (readout.distanceText == ROUTE_UNAVAILABLE_TEXT) {
        val retryPx = with(density) { (RETRY_ICON_SIZE + Spacing.xs).roundToPx() } + widthOf(ROUTE_RETRY_TEXT, retryTextStyle())
        val besideRoom = if (readout.routeRetryOffered) widthPx - retryPx - gapPx else widthPx
        val message = if (widthOf(ROUTE_UNAVAILABLE_TEXT, statusStyle) <= besideRoom) ROUTE_UNAVAILABLE_TEXT else ROUTE_UNAVAILABLE_SHORT_TEXT
        val beside = !readout.routeRetryOffered || retryFitsBeside(widthPx, retryPx, gapPx, widthOf(ROUTE_UNAVAILABLE_SHORT_TEXT, statusStyle))
        val messageText: @Composable () -> Unit = {
            WordSwap(text = if (beside) message else ROUTE_UNAVAILABLE_TEXT) { shown ->
                Text(
                    text = shown,
                    style = statusStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(NAVIGATION_HUD_DISTANCE_TAG),
                )
            }
        }
        if (beside) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(modifier = Modifier.weight(1f, fill = false)) { messageText() }
                if (readout.routeRetryOffered) RouteRetryRow(onRetryRoute)
            }
        } else {
            messageText()
            RouteRetryRow(onRetryRoute)
        }
    }
    val separatorPx = widthOf("·", statusStyle) + 2 * with(density) { Spacing.xs.roundToPx() }
    val statusFit = statusLineFit(readout, kindInStatus = false, maxPx = widthPx, leadSeparatorPx = separatorPx) { widthOf(it, statusStyle) }
    val status = if (readout.statusIsPositionStatus) "" else statusFit.status
    if (status.isEmpty()) {
        // Nothing to say: no line (RECORD -935, "only when it has something to say"). An empty, zero-height node keeps the
        // status's tag, reading "", so "the status line is empty" stays a statement a test can check.
        Text(text = "", style = statusStyle, modifier = Modifier.height(0.dp).testTag(NAVIGATION_HUD_STATUS_TAG))
    } else {
        WordSwap(text = status) { shown ->
            Text(
                text = shown,
                style = statusStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NAVIGATION_HUD_STATUS_TAG),
            )
        }
    }
}

/**
 * A short label and its reading on one line, a space apart (dispatch 2026-09-28-677; the owner, RECORD -656: "heading and
 * altitude labelled"): "Facing 123° SE", "Alt 1352 ft". [label] `null` draws the reading alone, for a status that names
 * itself. Shared by the navigation display's second row and the compass strip, so the two label alike. The label is its own
 * text node ([NAVIGATION_LABEL_TAG]), so the reading's node, and every test that reads it, holds the reading alone.
 */
@Composable
internal fun LabelledReadout(
    label: String?,
    /** The label's style: labelMedium on the navigation display; the strip's 14 sp readout style there (RECORD -728). */
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    reading: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (label != null) {
            Text(
                text = label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(NAVIGATION_LABEL_TAG),
            )
        }
        reading()
    }
}

/**
 * "⟳ Try again", on its own line under "Unable to calculate route" (dispatch 2026-09-28-423; the
 * owner chose "Own line under it", shown that on one line beside the message the owner's sentence
 * was cut to "Unable to ca…" on a 360 dp phone and to 16 of its 25 characters on the S22). The
 * message keeps the large slot's line whole, and this row makes the HUD one row taller **only while
 * a route is withheld and recomputing could change that**; with no usable points it is not drawn,
 * and that state keeps the HUD's usual height.
 *
 * At least 48 dp tall, the minimum touch target, laid out at that height rather than relying on
 * hit testing to extend a shorter row, so what a finger can reach is what is drawn. "Try again"
 * reads as something to tap, not as more text: the primary colour, underlined, behind a refresh
 * icon. A screen reader announces the row as a button whose action is "Try again".
 */
@Composable
private fun RouteRetryRow(onRetryRoute: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .heightIn(min = RETRY_ROW_MIN_HEIGHT)
            .clickableWithShapedPress(role = Role.Button, onClickLabel = ROUTE_RETRY_TEXT, onClick = onRetryRoute)
            .testTag(NAVIGATION_HUD_RETRY_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, tint = primary, modifier = Modifier.size(RETRY_ICON_SIZE))
        Text(
            text = ROUTE_RETRY_TEXT,
            style = retryTextStyle(),
            color = primary,
            maxLines = 1,
        )
    }
}

/** "Try again"'s type: the primary colour comes from the row; measured with this by the landscape layout (T11 fixes). */
@Composable
private fun retryTextStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(textDecoration = TextDecoration.Underline)

private val RETRY_ICON_SIZE = 18.dp
private val RETRY_ROW_MIN_HEIGHT = 48.dp
