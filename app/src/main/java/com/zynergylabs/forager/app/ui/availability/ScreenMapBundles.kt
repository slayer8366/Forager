package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin

// Nav step 2's small tidy (RECORD -1537, the owner: "Stop here + small tidy"): two value bundles that AvailabilityScreen built
// inside its CompactMainScaffold call, moved out unchanged and called at the same place, in the same argument position. Both
// are plain data built from values the screen already had, with no State read here: every value is read when the builder is
// called, where the screen read it, and the two "consumed" lambdas write the same States through [AvailabilityScreenState]'s
// setters (`setMapWaypointReopen`, `setRecordsWaypointReopen`), which are `{ mapWaypointReopen = it }` and
// `{ recordsWaypointReopen = it }` in the screen.

/** The map's quick settings (dispatch 2026-09-28-645), from the screen's recording and settings bundles. */
internal fun mapQuickSettingsFor(
    uiState: AvailabilityUiState,
    recording: AvailabilityScreenRecording,
    settings: AvailabilityScreenSettings,
    currentTime: CurrentTimeProvider,
): MapQuickSettings =
    MapQuickSettings(
        isRecording = recording.isRecording,
        backBy = recording.recordingBackBy,
        onBackByChoice = recording.onBackByChoice,
        onClearBackBy = recording.onClearBackBy,
        sundown = SundownSettings(
            alertsEnabled = uiState.sundownAlertsEnabled,
            darknessMarginMinutes = uiState.darknessMarginMinutes,
            onAlertsEnabledChanged = settings.onSundownAlertsEnabledChanged,
            onDarknessMarginChanged = settings.onDarknessMarginChanged,
        ),
        offTrackReminder = OffTrackReminderSettings(
            enabled = uiState.offTrackReminderEnabled,
            onEnabledChanged = settings.onOffTrackReminderChanged,
        ),
        batterySaver = BatterySaverSettings(
            enabled = uiState.batterySaverEnabled,
            onEnabledChanged = settings.onBatterySaverChanged,
        ),
        currentTime = currentTime,
    )

/** The waypoint navigation's controls for the Maps tab (dispatch -502). */
internal fun waypointNavigateControlsFor(
    navigatingToWaypoint: Waypoint?,
    nextWaypointLine: StraightLine?,
    onNavigateToWaypoint: ((String) -> Unit)?,
    startWaypointNavigation: (WaypointNavigationOrigin) -> Unit,
    mapWaypointReopen: WaypointNavigationOrigin?,
    recordsWaypointReopen: String?,
    screenState: AvailabilityScreenState,
): WaypointNavigateControls =
    WaypointNavigateControls(
        isNavigatingToWaypoint = navigatingToWaypoint != null,
        straightLine = nextWaypointLine,
        onNavigate = onNavigateToWaypoint?.let { startWaypointNavigation },
        mapReopen = mapWaypointReopen,
        onMapReopenConsumed = { screenState.setMapWaypointReopen(null) },
        recordsReopenDetails = recordsWaypointReopen,
        onRecordsReopenConsumed = { screenState.setRecordsWaypointReopen(null) },
    )
