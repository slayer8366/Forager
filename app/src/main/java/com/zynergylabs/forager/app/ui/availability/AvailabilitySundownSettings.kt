package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySettingsUi.kt (dispatch 2026-10-19-10, UI split Phase 1, PR 5).

/**
 * The "Sundown" settings (dispatch 2026-09-28-592, plan task T4; the owner's step path, RECORD -592):
 * what the sundown alerts and the line read, from
 * [com.zynergylabs.forager.app.domain.SundownPreferencesRepository]. Drawn in the map's ⋮ quick settings
 * ([MapQuickSettings]). They were Settings' section, then the Tools drawer's from dispatch 2026-09-28-707;
 * that section is gone since dispatch 2026-10-27-14 Amendment 1 (RECORD -1751; the owner: "remove the
 * Sundown and Dark Under Trees portion from the tools menu since it's redundant"), and with it the
 * explanation line ("Drop it").
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

/** The "Off-track reminder" checkbox (dispatch 2026-09-28-626, plan T14; Amendment 1, RECORD -627), from [com.zynergylabs.forager.app.domain.OffTrackReminderPreferenceRepository]; in the map's ⋮ quick settings since the Tools drawer's Sundown section went (dispatch 2026-10-27-14 Amendment 1). */
internal data class OffTrackReminderSettings(
    val enabled: Boolean = true,
    val onEnabledChanged: (Boolean) -> Unit = {},
)
