package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilityMapControlsUi.kt (dispatch 2026-10-19-12, UI split Phase 1, PR 8).

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.semantics
import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatWholeLength
import com.zynergylabs.forager.app.ui.motion.WordSwap
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Dispatch 2026-10-12-08 (RECORD -948, -950): the strip's line while navigating, "344 ft · 10T ER 24991 40768": the height and
 * the grid reference, the grid reference still toggling MGRS and decimal. The north arrow and the heading are the navigation
 * info's, so they show once. A path of its own beside the strip's readout row rather than a flag threaded through it; it gives way
 * by the same rule (readoutsFitBeside: the label first, then the height; the grid reference whole).
 */
@Composable
internal fun StripNavigatingReadout(
    /** RECORD -987: give way as if the wider coordinate form were showing (landscape); see [coordinatesBothFormsWidthPx]. */
    coordinatesSizedForBothForms: Boolean,
    elevationMeters: Double?,
    unitSystem: UnitSystem,
    location: LatLng,
    showDecimalDegrees: Boolean,
    onToggleCoordinateFormat: () -> Unit,
) {
    val readoutStyle = stripReadoutStyle().copy(fontFeatureSettings = "tnum")
    val labelStyle = stripReadoutStyle()
    val measurer = rememberTextMeasurer()
    val elevationLabel = ALTITUDE_LABEL.takeIf { elevationMeters != null }
    val elevationText = elevationMeters?.let { formatWholeLength(it, unitSystem) } ?: ELEVATION_UNAVAILABLE_TEXT
    val coordinatesText = coordinatesStripText(location, showDecimalDegrees)
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        fun widthOf(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
        val fit = readoutsFitBeside(
            availablePx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE,
            coordinatesPx = if (coordinatesSizedForBothForms) coordinatesBothFormsWidthPx(location, ::widthOf, readoutStyle) else widthOf(coordinatesText, readoutStyle),
            labelsPx = listOf(elevationLabel?.let { widthOf(it, labelStyle) + with(density) { Spacing.xs.roundToPx() } } ?: 0),
            valuesPx = listOf(widthOf(elevationText, readoutStyle)),
            separatorPx = widthOf("·", labelStyle) + with(density) { 2 * Spacing.sm.roundToPx() },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
        ) {
            if (fit.shown[0]) {
                LabelledReadout(label = elevationLabel.takeIf { fit.labels }, labelStyle = labelStyle) {
                    WordSwap(text = elevationText) { shownText ->
                        Text(
                            text = shownText,
                            style = readoutStyle,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag(COMPASS_STRIP_ELEVATION_TAG),
                        )
                    }
                }
                Text("·", style = labelStyle)
            }
            Text(
                text = coordinatesText,
                style = readoutStyle,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clickableWithShapedPress(onClick = onToggleCoordinateFormat)
                    .testTag(COMPASS_STRIP_COORDINATES_TAG),
            )
        }
    }
}

/**
 * RECORD -951 (the planner's call, item 4): the strip's own sundown or Back by line, whole on one line where it fits, and
 * otherwise broken at its " · " joins into short pieces stacked one per line, as the navigation card's evening stack is (RECORD
 * -941). Nothing is ellipsised; a piece wider than the strip wraps. The line keeps [tag], on a node whose text is the whole line,
 * so it reads as it did; each stacked piece carries [STRIP_EVENING_PIECE_TAG].
 */
@Composable
internal fun StripEveningLine(line: String, tag: String, contentWidth: Boolean) {
    val style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = if (contentWidth) Modifier else Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val wholePx = measurer.measure(line, style, maxLines = 1, softWrap = false).size.width
        val fits = !constraints.hasBoundedWidth || wholePx <= constraints.maxWidth
        if (fits) {
            Text(
                text = line,
                style = style,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .then(if (contentWidth) Modifier else Modifier.fillMaxWidth())
                    .testTag(tag),
            )
        } else {
            Column(
                modifier = Modifier
                    .then(if (contentWidth) Modifier else Modifier.fillMaxWidth())
                    .semantics { text = AnnotatedString(line) }
                    .testTag(tag),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                eveningPieces(line, null).forEach { piece ->
                    Text(text = piece, style = style, textAlign = TextAlign.Center, modifier = Modifier.testTag(STRIP_EVENING_PIECE_TAG))
                }
            }
        }
    }
}

/** RECORD -951: each stacked piece of the strip's sundown or Back by line, where the whole line does not fit. */
internal const val STRIP_EVENING_PIECE_TAG = "strip-evening-piece"
