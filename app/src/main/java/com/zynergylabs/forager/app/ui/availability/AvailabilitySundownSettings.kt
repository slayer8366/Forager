package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySettingsUi.kt (dispatch 2026-10-19-10, UI split Phase 1, PR 5).

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The "Sundown" section's two settings (dispatch 2026-09-28-592, plan task T4; the owner's step path,
 * RECORD -592): what the sundown alerts and the line read, from
 * [com.zynergylabs.forager.app.domain.SundownPreferencesRepository]. Drawn in the Tools drawer since
 * dispatch 2026-09-28-707 ([ToolsSundownSection]); in Settings before that.
 */
internal data class SundownSettings(
    val alertsEnabled: Boolean = true,
    val darknessMarginMinutes: Int = com.zynergylabs.forager.app.domain.DEFAULT_DARKNESS_MARGIN_MINUTES,
    val onAlertsEnabledChanged: (Boolean) -> Unit = {},
    val onDarknessMarginChanged: (Int) -> Unit = {},
)

/** The margin choices the owner chose, "30 min, 45 min, 1 h, 1 h 30 (Recommended)", in minutes with their labels. */
internal val DARKNESS_MARGIN_CHOICES: List<Pair<Int, String>> = listOf(30 to "30 min", 45 to "45 min", 60 to "1 h", 90 to "1 h 30")

internal const val SUNDOWN_ALERTS_LABEL = "Sundown alerts"
internal const val DARK_UNDER_TREES_LABEL = "Dark under trees"
internal const val DARK_UNDER_TREES_EXPLANATION = "Woods get dark before sunset. Alerts allow this much extra."
internal const val SUNDOWN_ALERTS_TAG = "settings-sundown-alerts"
internal fun darknessMarginTag(minutes: Int) = "settings-darkness-margin-$minutes"

/**
 * The Tools drawer's "Sundown" section (dispatch 2026-09-28-707; the owner: "move the Sundown area to
 * the Tools so that it's one button away instead of two", then "Section in the Tools drawer
 * (Recommended)"): "Sundown alerts", "Dark under trees" with its explanation and four choices, and
 * the "Off-track reminder". Drawn near the top of the Tools page, under the Trip Planner's one-line
 * header (collapsed by default), inside [SearchControls]' scroll so a short screen still reaches
 * everything; why under it and not above it is on [SearchControls]' `following`. It was Settings' section,
 * moved whole: same rows, same tags, same values and callbacks.
 *
 * **No fill of its own**: the drawer's container is the one 80% layer over the map
 * (MapChromeAlphaTest's rule), and this section sits on the Tools page, which slides with motion
 * Part 3's drawer pages. Its rows are Settings' own shared rows ([SettingsCheckboxRow],
 * [SettingsRadioRow], dispatch 2026-09-28-658) with their press feedback unchanged.
 *
 * "Sundown alerts" is a checkbox row, the shape of Settings' other on/off settings
 * ([NightModeMapsSection]); the owner said "switch", and the planner chose the
 * screen's own control and told the owner (RECORD -593). Off stops the notifications only; the line
 * stays. "Dark under trees" is a radio group, the shape [DistanceUnitSection] and [ThemeModeSection]
 * give a choice of more than two, so every choice shows without a tap. A stored value that is not one
 * of the four (none is offered anywhere) selects none rather than pretending to be one.
 */
@Composable
internal fun ToolsSundownSection(sundown: SundownSettings, offTrackReminder: OffTrackReminderSettings) {
    Column(modifier = Modifier.testTag(TOOLS_SUNDOWN_SECTION_TAG), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(SUNDOWN_SECTION_TITLE, style = MaterialTheme.typography.titleMedium)
        SettingsCheckboxRow(sundown.alertsEnabled, sundown.onAlertsEnabledChanged, SUNDOWN_ALERTS_LABEL, tag = SUNDOWN_ALERTS_TAG)
        Text(DARK_UNDER_TREES_LABEL, style = MaterialTheme.typography.bodyLarge)
        Text(DARK_UNDER_TREES_EXPLANATION, style = MaterialTheme.typography.bodySmall)
        DARKNESS_MARGIN_CHOICES.forEach { (minutes, label) ->
            SettingsRadioRow(
                selected = sundown.darknessMarginMinutes == minutes,
                onSelect = { sundown.onDarknessMarginChanged(minutes) },
                label = label,
                tag = darknessMarginTag(minutes),
            )
        }
        OffTrackReminderSection(offTrackReminder)
    }
}

internal const val SUNDOWN_SECTION_TITLE = "Sundown"

/** [ToolsSundownSection]'s column, for tests. */
internal const val TOOLS_SUNDOWN_SECTION_TAG = "tools-sundown-section"

/** The "Off-track reminder" checkbox (dispatch 2026-09-28-626, plan T14; Amendment 1, RECORD -627), from [com.zynergylabs.forager.app.domain.OffTrackReminderPreferenceRepository]; in the Tools drawer's Sundown section since dispatch 2026-09-28-707. */
internal data class OffTrackReminderSettings(
    val enabled: Boolean = true,
    val onEnabledChanged: (Boolean) -> Unit = {},
)

/** The owner's words, verbatim (dispatch 2026-09-28-626): one line, no hedging. */
internal const val OFF_TRACK_REMINDER_LABEL = "Off-track reminder: your phone buzzes if you head away from your start."
internal const val OFF_TRACK_REMINDER_TAG = "settings-off-track-reminder"

/**
 * One checkbox row, the shape of "Sundown alerts" above it, the other walk alert. Unticked, the
 * off-track alert does not fire; nothing else changes.
 */
@Composable
private fun OffTrackReminderSection(reminder: OffTrackReminderSettings) {
    SettingsCheckboxRow(reminder.enabled, reminder.onEnabledChanged, OFF_TRACK_REMINDER_LABEL, tag = OFF_TRACK_REMINDER_TAG)
}
