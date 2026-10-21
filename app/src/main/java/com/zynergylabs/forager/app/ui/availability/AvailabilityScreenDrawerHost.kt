package com.zynergylabs.forager.app.ui.availability

// UI split Phase 2.6, PR 4 (dispatch 2026-10-19-40): the Tools drawer and the screen it slides over, moved out of
// AvailabilityScreen's body into one composable. The body calls it unconditionally, at the line where this tree was, so it
// is composed at the same point among the same siblings: after every effect and remember above it, and before
// InAppCameraHost, which stays in the body after it because composition order is what puts the camera on top.

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DrawerState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.log.LocalTrackDetailsFold
import com.zynergylabs.forager.app.ui.map.LocalMapCompass
import com.zynergylabs.forager.app.ui.map.LocalMapKeepOuts
import com.zynergylabs.forager.app.ui.map.LocalMapPosition
import com.zynergylabs.forager.app.ui.map.MapCompass
import com.zynergylabs.forager.app.ui.map.MapPosition
import com.zynergylabs.forager.app.ui.map.fanout.MapKeepOuts
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor

/**
 * The screen's composition locals around the Tools drawer and the frame. Everything it reads is computed or remembered in
 * the body and passed in as itself; the tab and the settings-only request are read through [state], inside the drawer's
 * content, where the body's own lambda read them, and closing writes the drawer flag through [state].
 */
@Composable
internal fun AvailabilityScreenDrawerHost(
    state: AvailabilityScreenState,
    drawerState: DrawerState,
    drawerDirection: LayoutDirection,
    ambientDirection: LayoutDirection,
    mapKeepOuts: MapKeepOuts,
    mapCompass: MapCompass,
    mapPosition: MapPosition,
    trackDetailsFoldOpen: MutableState<Boolean>,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    backup: BackupControls,
    openBackupRequest: Int,
    onSundownAlertsEnabledChanged: (Boolean) -> Unit,
    onDarknessMarginChanged: (Int) -> Unit,
    onOffTrackReminderChanged: (Boolean) -> Unit,
    openOfflineMapsFromTools: () -> Unit,
    compactMainScaffold: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides drawerDirection, LocalMapKeepOuts provides mapKeepOuts, LocalMapCompass provides mapCompass, LocalMapPosition provides mapPosition, LocalTrackDetailsFold provides trackDetailsFoldOpen) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe-to-open is off on purpose: the content behind the drawer is a full-screen
        // pannable map, and a horizontal drag there means "pan", not "open the drawer". Tools
        // is the way in. Gestures are on only while the drawer is open, which is what lets a
        // scrim tap close it: in material3 1.5.0-alpha26 both the scrim's dismiss and the
        // drag are gated on this flag (`if (gesturesEnabled && ...)` and
        // `anchoredDraggable(enabled = gesturesEnabled)`), so with it off neither a scrim tap
        // nor swipe-to-close worked. While open it also re-enables swipe-to-close; while
        // closed it stays false, so swipe-to-open stays off. See docs/audits/2026-09-27-landscape-b3-prebuild-report.md.
        gesturesEnabled = drawerState.isOpen,
        // Dispatch 2026-10-18-08 Amendment 1, RECORD -1313 (the owner: "When opening it, there should be no coloring of the
        // app", then "Portrait too" and "yes same behavior, just no added color"): no tint over the app behind the open drawer.
        // Transparent, not Color.Unspecified: material3 1.5.0-alpha26's Scrim (Scrim.kt) draws, and takes the tap that closes
        // the drawer, only `if (color.isSpecified)`, so Unspecified would also drop the tap-outside close. Transparent keeps the
        // scrim's full-screen tap target and its "Close drawer" semantics and draws nothing. Until -1313 the default,
        // DrawerDefaults.scrimColor (ScrimTokens' colour at ScrimTokens.ContainerOpacity).
        scrimColor = Color.Transparent,
        drawerContent = {
            // The sheet itself stays in drawerDirection, so its rounded edge faces the
            // content and its start-side inset padding lands on the window edge it sits on.
            // Only what is inside it goes back to the ambient direction.
            // Material3's own default container role for a modal drawer, passed explicitly, and
            // its content colour pinned to the role's own (`contentColorFor` matches a colour-scheme
            // role exactly; see `MapLayersSheet`).
            // Over the Maps tab at the map chrome's alpha, following the tab (owner: "80% over Maps
            // (Recommended)"; planner message -77, Q4); solid over the other tabs.
            val drawerColor = mapChromeFill(navigationBarContainerColor(), state.compactTab() == CompactTab.MAP)
            val drawerContentColor = contentColorFor(navigationBarContainerColor())
            ModalDrawerSheet(
                modifier = Modifier.testTag(TOOLS_DRAWER_SHEET_TAG).mapChromeContainerColor(drawerColor),
                drawerContainerColor = drawerColor,
                drawerContentColor = drawerContentColor,
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides ambientDirection) {
                Box(modifier = Modifier.mapChromeContentColor(LocalContentColor.current)) {
                CompactToolsDrawerContent(
                    uiState = uiState,
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = onDistanceUnitSelected,
                    onClose = { state.setIsDrawerOpen(false) },
                    onDeletePlannedTrip = onDeletePlannedTrip,
                    isNightMode = isNightMode,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    themeMode = uiState.themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    backup = backup,
                    openSettingsRequest = openBackupRequest,
                    openSettingsOnlyRequest = state.openSettingsOnlyRequest(),
                    sundown = SundownSettings(
                        alertsEnabled = uiState.sundownAlertsEnabled,
                        darknessMarginMinutes = uiState.darknessMarginMinutes,
                        onAlertsEnabledChanged = onSundownAlertsEnabledChanged,
                        onDarknessMarginChanged = onDarknessMarginChanged,
                    ),
                    offTrackReminder = OffTrackReminderSettings(
                        enabled = uiState.offTrackReminderEnabled,
                        onEnabledChanged = onOffTrackReminderChanged,
                    ),
                    onOpenOfflineMaps = openOfflineMapsFromTools,
                )
                }
                }
            }
        },
        content = {
            CompositionLocalProvider(LocalLayoutDirection provides ambientDirection) {
                compactMainScaffold()
            }
        },
    )
    }
}
