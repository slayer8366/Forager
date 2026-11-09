package com.zynergylabs.forager.app.ui.availability

// Moved unchanged from AvailabilitySettingsUi.kt (dispatch 2026-10-19-10, UI split Phase 1, PR 5).

import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.PageSlideStyle
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.backup.BackupSection
import com.zynergylabs.forager.app.ui.credits.CreditsNavigationRow
import com.zynergylabs.forager.app.ui.credits.OpenSourceSoftwarePage
import com.zynergylabs.forager.app.ui.credits.SOURCES_AND_CREDITS_TITLE
import com.zynergylabs.forager.app.ui.credits.SourcesAndCreditsPage
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Settings' body, reached by tapping the Settings entry at the bottom of [CompactToolsDrawerContent]
 * (the compact "Tools" drawer, map/navigation redesign dispatch B) — [SettingsContent] hosted as a
 * `showSettings`-gated state within that drawer instead of a drawer panel of its own. Reuses
 * [SettingsContent]/[BuildIdentityFooter] unmodified — only the navigation host around them changed,
 * from a drawer panel switch to a nested-state one. No header for the main Settings state, unlike
 * the drawer panel's [SettingsHeader]: there is nothing to go "back" to here via an in-content
 * affordance — [CompactToolsDrawerContent]'s own `BackHandler` unwinds `showSettings` back to the
 * rest of the Tools drawer, the same most-recently-composed-callback-wins pattern [JournalTab]'s
 * nested states use.
 *
 * **Was** [CompactTab.SETTINGS]'s body — a standalone sixth bottom-nav destination — before dispatch
 * B collapsed the bottom nav to five destinations and folded Settings one level deeper, behind Tools.
 *
 * Journal restructure Stage 1 moved Offline Maps and Recorded Tracks out of Settings entirely, into
 * the Journal's Records tab — see [com.zynergylabs.forager.app.ui.log.RecordsTab]. This tab's own `showOfflineMaps`/
 * `showTracks` submenu state is gone with them.
 */
@Composable
internal fun CompactSettingsTab(
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    /** Night mode for the map, and Settings' own checkbox value. */
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    backup: BackupControls,
    modifier: Modifier = Modifier,
    showBackupRequest: Int = 0,
    /** Counts up when the settings-reset snackbar's "Settings" is tapped (RECORD -661): Settings' own first page, at the top. */
    showSettingsOnlyRequest: Int = 0,
) {
    // Dispatch 2026-10-13-05: Sources & credits, and Open-source software under it, are pages of Settings, one step
    // deeper each. Back retraces the way in: Software to Sources, Sources to Settings, each its own handler, composed after
    // the Tools drawer's own showSettings handler so the deepest page unwinds first.
    var page by remember { mutableStateOf(SettingsDrawerPage.SETTINGS) }
    BackHandler(enabled = page == SettingsDrawerPage.SOFTWARE) { page = SettingsDrawerPage.SOURCES }
    BackHandler(enabled = page == SettingsDrawerPage.SOURCES) { page = SettingsDrawerPage.SETTINGS }
    // A notification's "open Backup" and the snackbar's "open Settings" both mean Settings' own page (the planner's answer Q8).
    LaunchedEffect(showBackupRequest) { if (showBackupRequest > 0) page = SettingsDrawerPage.SETTINGS }
    LaunchedEffect(showSettingsOnlyRequest) { if (showSettingsOnlyRequest > 0) page = SettingsDrawerPage.SETTINGS }
    // Hoisted out of SettingsContent so that Back from Sources & credits returns to Settings where it was left, scrolled
    // to the row that was tapped, rather than at its top: the page itself is disposed while a deeper one shows.
    val settingsScroll = rememberScrollState()

    // The same push from the left as the Tools drawer's own step into Settings (RECORD -682), and no fill of their own.
    PageSlide(
        targetState = page,
        depthOf = { it.ordinal },
        modifier = modifier.fillMaxSize(),
        pageColor = Color.Transparent,
        style = PageSlideStyle.PUSH_FROM_LEFT,
    ) { shownPage ->
        when (shownPage) {
            SettingsDrawerPage.SETTINGS -> Column(modifier = Modifier.fillMaxSize()) {
                SettingsContent(
                    modifier = Modifier.weight(1f),
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = onDistanceUnitSelected,
                    nightModeMaps = isNightMode,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    backup = backup,
                    showBackupRequest = showBackupRequest,
                    onOpenSourcesAndCredits = { page = SettingsDrawerPage.SOURCES },
                    scrollState = settingsScroll,
                )
                BuildIdentityFooter()
            }

            SettingsDrawerPage.SOURCES -> SourcesAndCreditsPage(
                onOpenSoftware = { page = SettingsDrawerPage.SOFTWARE },
                modifier = Modifier.fillMaxSize(),
            )

            SettingsDrawerPage.SOFTWARE -> OpenSourceSoftwarePage(modifier = Modifier.fillMaxSize())
        }
    }
}

/** Which page of [CompactSettingsTab] shows, in order of depth: one value, so the pages slide between each other. */
private enum class SettingsDrawerPage { SETTINGS, SOURCES, SOFTWARE }

/** Settings' last row (dispatch 2026-10-13-05; the owner: "Bottom of settings page makes sense"), for tests. */
internal const val SETTINGS_SOURCES_ROW_TAG = "settings-sources-and-credits-row"

/**
 * The Settings panel's body: [DistanceUnitSection], theme, night maps, Backup, and the "Sources & credits" row.
 *
 * **No longer has the Sundown section, the Off-track reminder, or the two camera settings**
 * (dispatch 2026-09-28-707; the owner: "move the Sundown area to the Tools so that it's one button
 * away instead of two. Move the two camera options at the bottom to a settings menu inside the
 * camera itself"). Sundown and Off-track are drawn by [ToolsSundownSection] at the top of the Tools
 * drawer; "Automatically Save Location to Photos" and "Lock camera to portrait" by the camera's
 * gear panel (`ui/log/CameraSettingsPanel.kt`). Only where the controls are drawn moved: the
 * values, their DataStore keys and the ViewModel functions they call are unchanged. Dispatch
 * 2026-10-13-03 then removed both rows from that panel (RECORD -968, -972): the lock is gone, and
 * photo location is set from the camera strip's Location chip alone. The rows' code, with
 * `ExplainedSettingsCheckbox` and their strings, is in `docs/archive/camera-portrait-lock.md`.
 *
 * **No longer has a "Choose Maps Service" section.** That section picked between OpenStreetMap and
 * USGS as the tile provider for the map's topo/regular modes — superseded outright once [MapMode]
 * pinned Street/Topographical to OpenStreetMap and added Satellite (USGS) as a third option
 * reachable only from the map's own `MapModePicker` (removed 2026-10-19, RECORD -1392) (Satellite itself removed since, dispatch
 * 2026-09-28-708). See [MapMode]'s own doc comment for the
 * full account of what this removed and why.
 *
 * **No longer has Offline Maps or Recorded Tracks entries.** Journal restructure Stage 1 moved
 * both into the Journal's own Records tab ([RecordsTab] in `ui/log/`) — see that composable's own
 * doc comment.
 *
 * Scrolls for the same reason [SearchControls] does — a drawer sheet is a fixed-height container,
 * so a tall stack of controls needs its own scroll rather than relying on the sheet to grow.
 */
@Composable
internal fun SettingsContent(
    modifier: Modifier = Modifier,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    nightModeMaps: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    /** The Backup section's state and callbacks (journal backup and restore, dispatch 2026-09-28-127). */
    backup: BackupControls = BackupControls(),
    /** Counts up when a backup notification is tapped: scroll the Backup section into view. */
    showBackupRequest: Int = 0,
    /** The "Sources & credits" row's tap (dispatch 2026-10-13-05). */
    onOpenSourcesAndCredits: () -> Unit = {},
    /** Hoisted by [CompactSettingsTab], so a return from Sources & credits lands where Settings was left. */
    scrollState: ScrollState = rememberScrollState(),
) {
    // Scrolls to the Backup section when a notification's tap asks (dispatch 2026-09-28-153): its top is measured as it is
    // laid out, and the scroll waits for that measurement, so a section that is not yet laid out (the drawer still opening)
    // is not scrolled to a position it does not have yet.
    var backupTop by remember { mutableIntStateOf(-1) }
    LaunchedEffect(showBackupRequest) {
        if (showBackupRequest > 0) {
            val top = snapshotFlow { backupTop }.first { it >= 0 }
            scrollState.animateScrollTo(top)
        }
    }
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DistanceUnitSection(distanceUnit = distanceUnit, onDistanceUnitSelected = onDistanceUnitSelected)
        HorizontalDivider()
        ThemeModeSection(themeMode = themeMode, onThemeModeSelected = onThemeModeChanged)
        NightModeMapsSection(checked = nightModeMaps, onCheckedChange = onNightModeMapsChanged)
        HorizontalDivider()
        BackupSection(controls = backup, modifier = Modifier.onGloballyPositioned { backupTop = it.positionInParent().y.toInt() })
        // Settings ends at Sources & credits, the very bottom (dispatch 2026-10-13-05; the owner: "Bottom of settings page
        // makes sense"), with no divider under it (dispatch 2026-10-11, RECORD -830, when Backup was last).
        HorizontalDivider()
        CreditsNavigationRow(SOURCES_AND_CREDITS_TITLE, tag = SETTINGS_SOURCES_ROW_TAG, onClick = onOpenSourcesAndCredits)
    }
}
