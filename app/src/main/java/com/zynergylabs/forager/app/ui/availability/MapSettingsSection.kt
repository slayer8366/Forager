package com.zynergylabs.forager.app.ui.availability

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.zynergylabs.forager.app.domain.CrowdedIconsMode
import com.zynergylabs.forager.app.ui.theme.Spacing

/** Settings > Map > "Crowded icons": the stored choice and its change (dispatch 2026-10-27-14). */
internal data class CrowdedIconsControls(
    val mode: CrowdedIconsMode = CrowdedIconsMode.DEFAULT,
    val onModeChanged: (CrowdedIconsMode) -> Unit = {},
)

/** Settings > Map > "Waypoint names": the stored choice and its change (dispatch 2026-11-02-04, B2), on by default. */
internal data class WaypointNamesControls(
    val shown: Boolean = true,
    val onChanged: (Boolean) -> Unit = {},
)

internal const val MAP_SETTINGS_TITLE = "Map"
internal const val WAYPOINT_NAMES_LABEL = "Waypoint names"
internal const val WAYPOINT_NAMES_TAG = "settings-waypoint-names"
internal const val CROWDED_ICONS_LABEL = "Crowded icons"
internal fun crowdedIconsTag(mode: CrowdedIconsMode) = "settings-crowded-icons-${mode.name.lowercase()}"

/**
 * Settings' "Map" section (dispatch 2026-10-27-14 Amendment 3, RECORD -1758): "Crowded icons", a choice of two, as radio
 * rows, the shape [DistanceUnitSection] and [ThemeModeSection] give a choice. "Zoom in, then fan" is #430's zoom; "List"
 * opens the list for any stack of two or more, at once, on the Maps tab. Under it, "Waypoint names" (dispatch 2026-11-02-04,
 * B2), a checkbox, on by default.
 */
@Composable
internal fun MapSettingsSection(crowdedIcons: CrowdedIconsControls, waypointNames: WaypointNamesControls = WaypointNamesControls()) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(MAP_SETTINGS_TITLE, style = MaterialTheme.typography.titleMedium)
        Text(CROWDED_ICONS_LABEL, style = MaterialTheme.typography.bodyLarge)
        CrowdedIconsMode.entries.forEach { mode ->
            SettingsRadioRow(
                selected = mode == crowdedIcons.mode,
                onSelect = { crowdedIcons.onModeChanged(mode) },
                label = mode.label,
                tag = crowdedIconsTag(mode),
            )
        }
        // Dispatch 2026-11-02-04, B2 (the owner: "Settings > Map"): a checkbox under "Crowded icons", the shape Night Maps has.
        SettingsCheckboxRow(waypointNames.shown, waypointNames.onChanged, WAYPOINT_NAMES_LABEL, tag = WAYPOINT_NAMES_TAG)
    }
}
