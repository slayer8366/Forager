package com.zynergylabs.forager.app.ui.availability

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.unit.Dp

/**
 * The sizes the frame ([CompactMainScaffold]) measures or is told, carried as one object so the Maps tab's call
 * ([ScaffoldMapTabCall]) writes and reads them (UI split Phase 2.5, dispatch 2026-10-19-39; survey
 * `docs/audits/2026-10-18-ui-split-survey.md` on branch `ui-split-survey`, section 5.4).
 *
 * **A view over State objects, never a copy** (the pattern of [CompactMapLayoutState] and [ScaffoldSearchDropdownState]).
 * [CompactMainScaffold] still remembers all seven States where it always did, with the same start values and the same keys
 * (`lastMapsStripHeightDp` still by `rememberSaveable`, so it still survives a process restart), and builds this holder from them
 * straight after the last, on every composition, without a remember of its own: the frame's slots are unchanged, and every
 * instance wraps the same States. Each read here is a getter on that State and each write a setter on it. A value copied in would
 * be stale the moment the State changed: the trap the frame's extraction found
 * (`docs/audits/2026-09-26-split-stage-g-completion-report.md`). Each property's meaning is documented at its State in the frame.
 */
internal class ScaffoldMeasurements(
    private val landscapeStripHeightState: MutableState<Dp>,
    private val landscapeStripWidthState: MutableState<Dp>,
    private val lastMapsStripHeightDpState: MutableFloatState,
    private val mapCompassStripHeightState: MutableState<Dp>,
    private val searchChromeHeightPxState: MutableIntState,
    private val bottomNavHeightPxState: MutableFloatState,
    private val mapRailWidthPxState: MutableFloatState,
) {
    /** RECORD -729: the landscape compass strip's measured height (the frame's `landscapeStripHeight`). */
    var landscapeStripHeight: Dp
        get() = landscapeStripHeightState.value
        set(value) { landscapeStripHeightState.value = value }

    /** RECORD -732: the landscape compass strip's measured width (the frame's `landscapeStripWidth`). */
    var landscapeStripWidth: Dp
        get() = landscapeStripWidthState.value
        set(value) { landscapeStripWidthState.value = value }

    /** RECORD -1269: the strip's last measured height on Maps in this landscape window, in dp (the frame's `lastMapsStripHeightDp`). */
    var lastMapsStripHeightDp: Float
        get() = lastMapsStripHeightDpState.floatValue
        set(value) { lastMapsStripHeightDpState.floatValue = value }

    /** RECORD -709: the portrait compass strip's measured height (the frame's `mapCompassStripHeight`). */
    var mapCompassStripHeight: Dp
        get() = mapCompassStripHeightState.value
        set(value) { mapCompassStripHeightState.value = value }

    /** Dispatch 2026-09-28-104, item 2: the Maps search column's measured height (the frame's `searchChromeHeightPx`). */
    var searchChromeHeightPx: Int
        get() = searchChromeHeightPxState.intValue
        set(value) { searchChromeHeightPxState.intValue = value }

    /** The Maps tab's floating bottom bar's measured height, in px (the frame's `bottomNavHeightPx`). */
    var bottomNavHeightPx: Float
        get() = bottomNavHeightPxState.floatValue
        set(value) { bottomNavHeightPxState.floatValue = value }

    /** Landscape B1: the Maps tab's overlaid rail's measured width, in px (the frame's `mapRailWidthPx`). */
    var mapRailWidthPx: Float
        get() = mapRailWidthPxState.floatValue
        set(value) { mapRailWidthPxState.floatValue = value }
}
