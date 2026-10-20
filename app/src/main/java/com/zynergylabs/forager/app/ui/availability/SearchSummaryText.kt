package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySearchUi.kt (dispatch 2026-10-19-17, UI split Phase 1, PR 10a).

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale


/**
 * RECORD -732: the landscape search bar's floor, in pixels: never narrower than its field and Clear need. Measured as the bar
 * lays them out: the search icon's start padding (Spacing.lg) and 18 dp, the field's own content padding (16 dp each side,
 * OutlinedTextFieldDefaults), its resting text (the search summary, in the field's text style, bodyLarge) whole, and, while a
 * search shows, Clear (its word and Spacing.md each side, at least Material's 58 dp button width, then Spacing.xs). The strip
 * narrows before the bar goes under this (landscapeStripFit), down to its coordinates.
 */
@Composable
internal fun rememberLandscapeBarFloorPx(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): Int {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fieldStyle = MaterialTheme.typography.bodyLarge
    val clearStyle = MaterialTheme.typography.labelLarge
    fun widthOf(text: String, style: androidx.compose.ui.text.TextStyle) = measurer.measure(text, style, maxLines = 1, softWrap = false).size.width
    return with(density) {
        val field = Spacing.lg.roundToPx() + 18.dp.roundToPx() + 2 * LANDSCAPE_BAR_FIELD_SIDE_PADDING.roundToPx() + widthOf(activeSearchSummary(uiState, distanceUnit), fieldStyle)
        val clear = if (uiState.region != null) {
            maxOf(LANDSCAPE_BAR_CLEAR_MIN_WIDTH.roundToPx(), widthOf("Clear", clearStyle) + 2 * Spacing.md.roundToPx()) + Spacing.xs.roundToPx()
        } else {
            0
        }
        field + clear
    }
}

/** OutlinedTextFieldDefaults' horizontal content padding, which the bar's field keeps (it sets only top and bottom). */
private val LANDSCAPE_BAR_FIELD_SIDE_PADDING = 16.dp

/** Material3's minimum button width (ButtonDefaults.MinWidth), which Clear's TextButton keeps. */
private val LANDSCAPE_BAR_CLEAR_MIN_WIDTH = 58.dp

internal fun activeSearchSummary(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    // Before any search there is no region, and this says so as what the user can do about it
    // (owner, 2026-09-28: "Change it to 'September · Search a location'", so the line gives "a spark
    // or motivation to action"). Tapping it focuses
    // SearchEntryBar's field, which opens SearchDropdown.
    val where = uiState.region?.let { formatDistanceKm(it.radiusKm, distanceUnit) } ?: "Search a location"
    // Fungi is the only category now (owner decision) — leading with its name on every search
    // would be a label with nothing left to distinguish it from. A specific searched species is
    // still worth naming up front; nothing selected leads with the month instead of a blank
    // segment (a blank search still means "all fungi in this area and month" — see
    // AvailabilityUiState.taxonFilter's default).
    val species = (uiState.taxonFilter as? TaxonFilter.SpecificTaxon)?.label
    return listOfNotNull(species, month, where).joinToString(" · ")
}
