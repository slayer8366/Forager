package com.zynergylabs.forager.app.ui.availability

// Data part C (dispatch -668), under the owner's "Piece by piece (Recommended)" (RECORD -655): the
// trip windows card, moved out of AvailabilityResultsUi.kt before this part changed it. Same package,
// so its caller (TripPlannerSection) and TRIP_WINDOW_DATE_FORMAT's other users (the planned-trips row,
// its map bubble) resolve unchanged. The move itself changed no line of it; the later commits on branch data-c-seasonal then
// changed TripWindowRow. Data part D (RECORD -702) then removed TRIP_WINDOW_DATE_FORMAT ("MMM d"): its users show the shared
// "Oct 7, 2026" (ui/format/DisplayDates.kt).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatRainfall
import com.zynergylabs.forager.app.domain.model.formatSoilTemperature
import com.zynergylabs.forager.app.domain.model.TripWindow
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.ui.credits.OpenMeteoCredit
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.format.displayDate


/**
 * Upcoming days that sit inside the stated post-rain lag range.
 *
 * [TripWindowReport] is measurements and date arithmetic only (see its own doc comment for why it
 * must never grow a score). The group's weather guidance text that used to follow it, under a
 * divider, was removed by RECORD -727.
 *
 * Unlike [ConditionsCard], not gated to the browsed month: the days ahead of today are relevant
 * to planning a trip this week regardless of which month's species ranking is on screen.
 */
@Composable
internal fun TripWindowsCard(uiState: AvailabilityUiState) {
    Card(modifier = Modifier.fillMaxWidth().testTag(TRIP_WINDOWS_CARD_TAG)) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Trip Windows", style = MaterialTheme.typography.titleSmall)

            when {
                uiState.isLoadingTripWindows -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                uiState.tripWindowsErrorMessage != null -> Text(
                    uiState.tripWindowsErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.tripWindowReport != null -> {
                    TripWindowReportContent(uiState.tripWindowReport, uiState.unitSystem)
                    // Every figure in a report, its "no window" message included, is Open-Meteo's rain, soil or
                    // evaporation, or arithmetic on them: their credit sits beside them (dispatch 2026-10-13-05).
                    OpenMeteoCredit()
                }
            }

            // RECORD -727, the owner: "remove the block of text here labeled Rain and Fungi: the general pattern",
            // then "Remove both" for the plants block. The divider and the guidance section that sat here are gone,
            // for every group: the card ends at its measurements.
        }
    }
}

/** The Trip Planner's Trip Windows card, for tests. */
internal const val TRIP_WINDOWS_CARD_TAG = "trip-windows-card"

@Composable
private fun TripWindowReportContent(report: TripWindowReport, unitSystem: UnitSystem) {
    if (report.windows.isEmpty()) {
        Text(noTripWindowMessage(report, unitSystem), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        report.windows.forEach { window -> TripWindowRow(window, unitSystem) }
    }
}

/**
 * One trip window as a dated heading over a small labelled table (data part C, dispatch -668: the
 * owner's "conditions are a small table", and dates as "Oct 7, 2026"). Each row is one measurement
 * the window carries; a measurement the location's weather model did not serve has no row, as before.
 * Soil moisture is the plain Dry / Moist / Wet scale with the figure under it
 * ([SoilMoistureScaleValue]).
 */
@Composable
private fun TripWindowRow(window: TripWindow, unitSystem: UnitSystem) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${displayDate(window.startDate)} – ${displayDate(window.endDate)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val mostRecentRain = window.precedingRainEvents.first()
        val rows = buildList<TableRow> {
            add(TableRow("Days after rain") { TableValue("${window.daysAfterMostRecentRainAtStart}–${window.daysAfterMostRecentRainAtEnd}") })
            add(
                TableRow("Last soaking rain") {
                    TableValue(
                        "${formatRainfall(mostRecentRain.totalMm, unitSystem, metricDecimals = 0)}, ended " +
                            displayDate(mostRecentRain.endDate) +
                            if (mostRecentRain.isForecast) " (forecast)" else "",
                    )
                },
            )
            if (window.precipitationDuringWindowMm > 0.0) {
                add(TableRow("More rain forecast") { TableValue(formatRainfall(window.precipitationDuringWindowMm, unitSystem)) })
            }
            window.meanShallowSoilMoistureM3M3?.let { moisture ->
                add(TableRow("Soil moisture") { SoilMoistureScaleValue(moisture) })
            }
            window.meanSoilTemperatureC?.let { temp ->
                add(TableRow("Soil temperature") { TableValue(formatSoilTemperature(temp, unitSystem)) })
            }
            window.evapotranspirationSinceRainMm?.let { et0 ->
                add(TableRow("Evaporated since rain") { TableValue(formatRainfall(et0, unitSystem)) })
            }
        }
        LabelledTable(rows)
    }
}
