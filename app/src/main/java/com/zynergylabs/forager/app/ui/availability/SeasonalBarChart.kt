package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.SoilMoistureLevel
import com.zynergylabs.forager.app.domain.SoilMoistureScale
import com.zynergylabs.forager.app.ui.theme.Spacing

// Data part C (dispatch -668): the drawing for the Seasonal tab's charts, the labelled tables and the
// soil moisture scale. What they show is decided in SeasonalChartModels.kt and tested there; this file
// only lays it out. Hand-drawn on a Compose Canvas, as the one chart before it was: the app has no
// chart library, and adding one is a stop in the dispatch.
//
// Device-only by construction: Robolectric does not render Canvas content, so the bars, the band and
// the figures above the bars are seen only on a phone. The axis labels and titles are real Text, and
// the chart's own content description carries every value, so both are asserted under Robolectric.

/** The tag on a chart's drawing, for tests that read its description. */
internal const val SEASONAL_BAR_CHART_TAG = "seasonal-bar-chart"

private val CHART_PLOT_HEIGHT: Dp = 120.dp

/** A bar chart with a y-axis (its title, top and zero values), an x-axis with labels, and an optional shaded band. */
@Composable
internal fun SeasonalBarChart(model: BarChartModel, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.secondary
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val valueStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val textMeasurer = rememberTextMeasurer()
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(model.yTitle, style = MaterialTheme.typography.labelSmall, color = axisColor)
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.height(CHART_PLOT_HEIGHT).padding(end = Spacing.xs),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(model.yTopLabel, style = MaterialTheme.typography.labelSmall, color = axisColor)
                Text(model.yBottomLabel, style = MaterialTheme.typography.labelSmall, color = axisColor)
            }
            Column(modifier = Modifier.weight(1f)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CHART_PLOT_HEIGHT)
                        .testTag(SEASONAL_BAR_CHART_TAG)
                        .semantics { contentDescription = model.description },
                ) {
                    model.band?.let { band ->
                        drawRect(
                            color = bandColor,
                            topLeft = Offset(band.start * size.width, 0f),
                            size = Size((band.endInclusive - band.start) * size.width, size.height),
                        )
                    }
                    if (model.slotCount > 0) {
                        val slot = size.width / model.slotCount
                        val gap = (slot * 0.2f).coerceAtMost(8.dp.toPx())
                        // Room above the tallest bar for its figure, when the chart prints figures.
                        val headroom = if (model.valueLabels != null) 14.dp.toPx() else 0f
                        val plotHeight = size.height - headroom
                        model.values.forEachIndexed { index, value ->
                            if (value == null) return@forEachIndexed
                            val barHeight = plotHeight * barHeightFraction(value, model.yMax)
                            val left = index * slot + gap / 2
                            drawRect(
                                color = barColor,
                                topLeft = Offset(left, size.height - barHeight),
                                size = Size(slot - gap, barHeight),
                            )
                            model.valueLabels?.getOrNull(index)?.let { label ->
                                val measured = textMeasurer.measure(label, valueStyle)
                                drawText(
                                    measured,
                                    topLeft = Offset(
                                        x = left + (slot - gap - measured.size.width) / 2,
                                        y = (size.height - barHeight - measured.size.height).coerceAtLeast(0f),
                                    ),
                                )
                            }
                        }
                    }
                    // The axes: the zero line along the bottom and the value axis up the left.
                    val stroke = 1.dp.toPx()
                    drawLine(axisColor, Offset(0f, size.height), Offset(size.width, size.height), stroke)
                    drawLine(axisColor, Offset(0f, 0f), Offset(0f, size.height), stroke)
                }
                XAxisLabels(model.xLabels)
            }
        }
        model.xTitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = axisColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Each label centred on its fraction of the width, and pushed back inside at either end. */
@Composable
private fun XAxisLabels(labels: List<AxisLabel>) {
    Layout(
        content = {
            labels.forEach { Text(it.text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        modifier = Modifier.fillMaxWidth(),
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0)) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width, height) {
            placeables.forEachIndexed { index, placeable ->
                val centre = labels[index].fraction * width
                val x = (centre - placeable.width / 2f).toInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}

/**
 * A small table of labels and values, label column on the left (the owner's "conditions are a small
 * table"). Each row reads as one item to TalkBack: "Rain, last 14 days, 12.4mm".
 */
@Composable
internal fun LabelledTable(rows: List<TableRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) { row.value() }
            }
        }
    }
}

/** One row of [LabelledTable]: its label, and the composable that draws its value. */
internal class TableRow(val label: String, val value: @Composable () -> Unit)

/** A plain text value cell for [LabelledTable]. */
@Composable
internal fun TableValue(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

/**
 * The soil moisture scale: Dry, Moist and Wet side by side with the reading's own step filled, and
 * the figure under it (the owner's "Soil moisture as Dry, Moist or Wet with the figure under it").
 * Read to TalkBack as one phrase, "Moist, 0.27 m³/m³".
 */
@Composable
internal fun SoilMoistureScaleValue(m3m3: Double) {
    val level = SoilMoistureScale.levelOf(m3m3)
    val word = soilMoistureWord(level)
    val figure = soilMoistureFigure(m3m3)
    Column(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$word, $figure" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SoilMoistureLevel.entries.forEach { step ->
                val current = step == level
                val shape = RoundedCornerShape(Spacing.xs)
                Text(
                    soilMoistureWord(step),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
                    color = if (current) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .widthIn(min = SOIL_SCALE_STEP_MIN_WIDTH)
                        .then(
                            if (current) {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer, shape)
                            } else {
                                Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                            },
                        )
                        .padding(vertical = 2.dp),
                )
            }
        }
        Text(figure, style = MaterialTheme.typography.bodySmall)
    }
}

/** A floor, not a fixed width, so a step's word is never cut at a large font scale. */
private val SOIL_SCALE_STEP_MIN_WIDTH: Dp = 44.dp
