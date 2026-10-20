package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySettingsUi.kt (dispatch 2026-10-19-10, UI split Phase 1, PR 5).

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The app's own theme — a direct, persistent preference ([AvailabilityUiState.themeMode]), a
 * three-way choice rather than a single on/off checkbox now that [AppThemeMode.SYSTEM_DEFAULT]
 * exists alongside the two explicit choices this setting started as
 * ([AppThemeMode.LIGHT]/[AppThemeMode.DARK]) — only [AppThemeMode.SYSTEM_DEFAULT] is derived from
 * the device's own theme ([androidx.compose.foundation.isSystemInDarkTheme], resolved in
 * `MainActivity`); [AppThemeMode.LIGHT]/[AppThemeMode.DARK] stay direct choices independent of it.
 * Same radio-group shape as [DistanceUnitSection] above, for the same reason: more than two mutually
 * exclusive choices reads as a choice, not a toggle. [NightModeMapsSection] sits directly beneath
 * this one: that checkbox controls only the map's own basemap styling, independent of this app-wide
 * choice — see [AvailabilityUiState.nightModeMaps]'s own doc comment, and
 * [com.zynergylabs.forager.app.domain.AppThemePreferenceRepository]'s own doc comment for why that independence
 * held even once this setting grew a third option.
 */
@Composable
internal fun ThemeModeSection(themeMode: AppThemeMode, onThemeModeSelected: (AppThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Night Mode", style = MaterialTheme.typography.titleMedium)
        AppThemeMode.entries.forEach { mode ->
            SettingsRadioRow(selected = mode == themeMode, onSelect = { onThemeModeSelected(mode) }, label = mode.label)
        }
    }
}

/**
 * Whether the map renders in night mode — a direct, persistent preference
 * ([AvailabilityUiState.nightModeMaps]), not derived from time of day. Replaces the map's earlier
 * civil-twilight-automatic/long-press-hold control per the project owner's own request to move
 * this to a plain Settings checkbox instead. Sits directly beneath [ThemeModeSection] — see that
 * composable's own doc comment.
 */
@Composable
internal fun NightModeMapsSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    SettingsCheckboxRow(checked, onCheckedChange, "Night Maps")
}

/**
 * One Settings checkbox row: the whole row toggles, with the checkbox and its label in it. The one
 * copy (dispatch 2026-09-28-658, F6) of the row this panel had written out five times. The row is
 * the touch target, full width, exactly as each copy built it, and carries [tag] as its test tag when given,
 * after the click as before.
 */
@Composable
internal fun SettingsCheckboxRow(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, tag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * One Settings radio row: the whole row selects, with the radio button and its label in it. The one
 * copy (dispatch 2026-09-28-658, F6) of the row this panel had written out three times. Touch
 * target and [tag] as [SettingsCheckboxRow].
 */
@Composable
internal fun SettingsRadioRow(selected: Boolean, onSelect: () -> Unit, label: String, tag: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton) { onSelect() }
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Metric or imperial for everything this app displays with a unit — distances (search radius,
 * offline-download radius, recent-search cards, the HUD) and, since the return-estimate dispatch,
 * rainfall; see [UnitSystem]'s own doc comment for why this is a system rather than the distance
 * unit it used to be, and which displays still wait on it. A display preference only, never a
 * change to what's actually searched or downloaded ([DistanceUnit]'s own doc comment).
 *
 * Still parameterised by [DistanceUnit] and still calling `onDistanceUnitSelected`: the two enums
 * are in bijection, so the existing callback carries the chosen system exactly, and this file's
 * plumbing keeps its shape while its split is held — see `AvailabilityViewModel.onDistanceUnitSelected`.
 */
@Composable
internal fun DistanceUnitSection(distanceUnit: DistanceUnit, onDistanceUnitSelected: (DistanceUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Units", style = MaterialTheme.typography.titleMedium)
        UnitSystem.entries.forEach { system ->
            SettingsRadioRow(
                selected = system.distanceUnit == distanceUnit,
                onSelect = { onDistanceUnitSelected(system.distanceUnit) },
                label = system.label,
            )
        }
    }
}
