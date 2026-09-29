package com.zynergylabs.forager.app.ui.availability

// Split-AvailabilityScreen Stage F: the drawer rows, settings and compact tools drawer, moved
// verbatim out of AvailabilityScreen.kt. Two blocks, lines of the file as of 3bd0efe: 2493-2956
// (BuildIdentityFooter, DrawerHeader, MushroomLogEntryRow, SettingsEntryRow, PhotoGalleryEntryRow,
// SettingsHeader, PhotoGalleryHeader, CompactSettingsTab, SettingsContent, ThemeModeSection,
// NightModeMapsSection, PhotoLocationSection, CameraPortraitLockSection, LOCK_CAMERA_SETTING_LABEL,
// LOCK_CAMERA_SETTING_EXPLANATION, PHOTO_LOCATION_SETTING_LABEL,
// PHOTO_LOCATION_SETTING_EXPLANATION, DistanceUnitSection) and 2990-3074
// (CompactToolsDrawerContent). Same package as Stages A to E, so every same-package reference
// resolves unchanged. Pure move: no signature, name or body changed. Nine widenings, private ->
// internal: BuildIdentityFooter, DrawerHeader, MushroomLogEntryRow, SettingsEntryRow,
// PhotoGalleryEntryRow, SettingsHeader, PhotoGalleryHeader, SettingsContent and
// CompactToolsDrawerContent, whose callers stay in AvailabilityScreen.kt. No symbol left behind is
// reached from here. Seam F (the wide layout) was released by the owner for this split, as recorded
// in the Understory amendment merged in #130.
//
// J6 (2026-09-29): PhotoGalleryEntryRow and PhotoGalleryHeader, two of the rows moved here, were removed
// with the standalone Photo Gallery panel (the owner's ruling 2, 2026-09-28: "the old Photo Gallery panel
// is removed. Only the album remains, as on the phone"), and the list above records the move as it was.

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.crash.CrashFileStore
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.crash.CrashLogPanel
import com.zynergylabs.forager.app.ui.crash.CrashLogsEntryRow
import com.zynergylabs.forager.app.ui.diagnostics.DiagnosticsEntryRow
import com.zynergylabs.forager.app.ui.diagnostics.DiagnosticsPanel
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapModePicker
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.backup.BackupSection
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * Which build this is, at the bottom of the drawer.
 *
 * Debug APKs get handed to a tester several times a session and, before this, the app had no way
 * to say which one was running: two different builds both reported "1.0". The versionCode is here
 * because that is the number Android compares when deciding whether an install replaces or
 * no-ops, and the versionName because it carries the commit sha that names the exact build. A
 * versionName starting with "UNVERSIONED" means the build could not derive its identity from git
 * — see resolveBuildIdentity in app/build.gradle.kts — and its versionCode is not trustworthy.
 */
@Composable
internal fun BuildIdentityFooter() {
    HorizontalDivider()
    Text(
        text = "Build ${BuildConfig.VERSION_CODE} · ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            // The drawer sheet sits outside Scaffold's own automatic system-bar inset handling
            // (see AvailabilityScreen's enableEdgeToEdge() call in MainActivity), so this, the
            // bottom-most content in the sheet, needs its own bottom inset explicitly — otherwise
            // it would sit partly behind the now-transparent gesture/nav bar.
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    )
}

/**
 * A visible close affordance at the top of the drawer sheet.
 *
 * Gestures on this drawer are on only while it is open (see the comment on `gesturesEnabled` in
 * [AvailabilityScreen], landscape B3): the content behind it is a pannable map, so a swipe there
 * has to mean "pan", never "open". Tapping the scrim and swipe-to-close are the other ways out,
 * and both are easy to miss — this gives the drawer its own explicit, discoverable close control.
 *
 * The whole bar is the tap target, not just an icon: a bare [IconButton] here is a 48dp target in
 * the corner of an otherwise-empty full-width row, which is easy to miss the same way the scrim
 * tap was. Widening the target to the whole row is a bigger, easier-to-hit close affordance for
 * exactly the same action.
 *
 * No visible icon on it, on purpose: the app bar's tune icon that opens this drawer sits at the
 * same height as this row, so the row's position alone reads as "tap here to undo what the tune
 * icon did" without needing its own marker — an X here was one more thing competing for attention
 * at the top of a sheet whose whole point is fewer things demanding a look. [Role.Button] and the
 * `contentDescription` below keep it a real, labelled action for TalkBack even with nothing drawn.
 */
@Composable
internal fun DrawerHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = "Close search options" },
    ) {}
}

/**
 * The Search panel's sticky-footer entry into the mushroom log, right above [SettingsEntryRow] —
 * see [DrawerPanel]'s doc comment for the two sticky rows this drawer now has. No
 * `navigationBarsPadding()` here: [SettingsEntryRow] below is still the last row in the sheet and
 * carries that inset, so both rows don't independently pad for the same nav-bar gap.
 */
@Composable
internal fun MushroomLogEntryRow(onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MenuBook, contentDescription = null)
        Text("Mushroom Log", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The Search panel's sticky-footer entry into Settings — the exact slot [BuildIdentityFooter] used
 * to occupy, same divider-plus-navigation-bar-padding treatment, so the footer's move to the bottom
 * of the Settings panel doesn't leave this slot looking or behaving any differently to a user who
 * never opens Settings at all.
 *
 * Unlike [DrawerHeader], this one is a real, visible, labelled row: it isn't undoing anything the
 * app bar already drew (there is no "Settings" icon anywhere else on screen this could echo), so it
 * has to carry its own label to be discoverable at all.
 */
@Composable
internal fun SettingsEntryRow(onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Settings, contentDescription = null)
        Text("Settings", style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * The Settings panel's header: unlike [DrawerHeader] this carries a visible back arrow and title,
 * because — unlike closing the drawer entirely, which the app bar's tune icon already visually
 * "undoes" — there is nothing else on screen suggesting how to get back from Settings to Search.
 */
@Composable
internal fun SettingsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to search options")
        Text("Settings", style = MaterialTheme.typography.titleMedium)
    }
}

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
 * `showTracks` submenu state is gone with them; only [showCrashLogs] remains.
 */
@Composable
private fun CompactSettingsTab(
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    /** Night mode for the map, and Settings' own checkbox value. */
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    /** Settings' "Automatically Save Location to Photos" checkbox value — see [AvailabilityUiState.autoSaveLocationToPhotos]. */
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    lockCameraToPortrait: Boolean,
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    crashFileStore: CrashFileStore,
    backup: BackupControls,
    showBackupRequest: Int = 0,
    modifier: Modifier = Modifier,
) {
    var showCrashLogs by remember { mutableStateOf(false) }
    // Debug builds only — the row that sets this composes nothing in release. Same drill-in shape
    // as showCrashLogs, one flag per submenu rather than an enum, matching what was here.
    var showDiagnostics by remember { mutableStateOf(false) }

    // Unwinds this tab's own nested submenu before AvailabilityScreen's top-level "switch away
    // from a non-Maps tab" handler ever sees it — same reasoning as JournalTab's own BackHandler.
    BackHandler(enabled = showCrashLogs) {
        showCrashLogs = false
    }
    BackHandler(enabled = showDiagnostics) {
        showDiagnostics = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        when {
            showCrashLogs -> {
                CrashLogPanel(
                    modifier = Modifier.weight(1f),
                    files = crashFileStore.list(),
                    onBack = { showCrashLogs = false },
                )
            }

            showDiagnostics -> {
                DiagnosticsPanel(
                    modifier = Modifier.weight(1f),
                    onBack = { showDiagnostics = false },
                )
            }

            else -> {
                SettingsContent(
                    modifier = Modifier.weight(1f),
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = onDistanceUnitSelected,
                    nightModeMaps = isNightMode,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    autoSaveLocationToPhotos = autoSaveLocationToPhotos,
                    onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
                    lockCameraToPortrait = lockCameraToPortrait,
                    onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onOpenCrashLogs = { showCrashLogs = true },
                    onOpenDiagnostics = { showDiagnostics = true },
                    backup = backup,
                    showBackupRequest = showBackupRequest,
                )
                BuildIdentityFooter()
            }
        }
    }
}

/**
 * The Settings panel's body: [DistanceUnitSection], theme, night maps, and Crash Logs.
 *
 * **No longer has a "Choose Maps Service" section.** That section picked between OpenStreetMap and
 * USGS as the tile provider for the map's topo/regular modes — superseded outright once [MapMode]
 * pinned Street/Topographical to OpenStreetMap and added Satellite (USGS) as a third, always-on
 * option reachable only from the map's own [MapModePicker]. See [MapMode]'s own doc comment for the
 * full account of what this removed and why.
 *
 * **No longer has Offline Maps or Recorded Tracks entries.** Journal restructure Stage 1 moved
 * both into the Journal's own Records tab ([RecordsTab] in `ui/log/`) — see that composable's own
 * doc comment. `CrashLogs` stays here since it isn't a Records concept.
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
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    lockCameraToPortrait: Boolean,
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    onOpenCrashLogs: () -> Unit,
    /** Debug builds only: the row this opens composes nothing in release — see [DiagnosticsEntryRow]'s two source-set versions. */
    onOpenDiagnostics: () -> Unit,
    /** The Backup section's state and callbacks (journal backup and restore, dispatch 2026-09-28-127). */
    backup: BackupControls = BackupControls(),
    /** Counts up when a backup notification is tapped: scroll the Backup section into view. */
    showBackupRequest: Int = 0,
) {
    // Scrolls to the Backup section when a notification's tap asks (dispatch 2026-09-28-153): its top is measured as it is
    // laid out, and the scroll waits for that measurement, so a section that is not yet laid out (the drawer still opening)
    // is not scrolled to a position it does not have yet.
    val scrollState = rememberScrollState()
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
        PhotoLocationSection(checked = autoSaveLocationToPhotos, onCheckedChange = onAutoSaveLocationToPhotosChanged)
        CameraPortraitLockSection(checked = lockCameraToPortrait, onCheckedChange = onLockCameraToPortraitChanged)
        HorizontalDivider()
        BackupSection(controls = backup, modifier = Modifier.onGloballyPositioned { backupTop = it.positionInParent().y.toInt() })
        HorizontalDivider()
        CrashLogsEntryRow(onClick = onOpenCrashLogs)
        DiagnosticsEntryRow(onClick = onOpenDiagnostics)
    }
}

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
private fun ThemeModeSection(themeMode: AppThemeMode, onThemeModeSelected: (AppThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Night Mode", style = MaterialTheme.typography.titleMedium)
        AppThemeMode.entries.forEach { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onThemeModeSelected(mode) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(selected = mode == themeMode, onClick = { onThemeModeSelected(mode) })
                Text(mode.label, style = MaterialTheme.typography.bodyLarge)
            }
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
private fun NightModeMapsSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("Night Maps", style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Whether the app captures the device's current position for a photo and its find (owner request,
 * 2026-09-14) — see [com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository] for what
 * the one flag actually gates, which is wider than the label says, and for why it defaults to on.
 *
 * Carries explanatory text, unlike every other control in this panel. That is not decoration: the
 * other settings announce their effect the moment they are flipped (the map recolours, units
 * change), while this one changes what is *written to a record the user cannot see from here*, and
 * whose point is what happens to a photo after it leaves the app. The wording is the owner's own,
 * kept verbatim.
 */
@Composable
private fun PhotoLocationSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(PHOTO_LOCATION_SETTING_LABEL, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            PHOTO_LOCATION_SETTING_EXPLANATION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Settings' "Lock camera to portrait" (owner request, 2026-09-15). Same shape as
 * [PhotoLocationSection] above. The supporting line is there because the consequence is not
 * obvious from the label: a sideways photo is saved portrait. What it gates, and why it is one
 * gate, is on [com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository].
 */
@Composable
private fun CameraPortraitLockSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Checkbox(checked = checked, onCheckedChange = onCheckedChange)
            Text(LOCK_CAMERA_SETTING_LABEL, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            LOCK_CAMERA_SETTING_EXPLANATION,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal const val LOCK_CAMERA_SETTING_LABEL = "Lock camera to portrait"

internal const val LOCK_CAMERA_SETTING_EXPLANATION =
    "Keeps the camera and its controls still when you turn the phone. Photos are always saved in " +
        "portrait, even when you hold the phone sideways."

/** Exposed at file scope so [com.zynergylabs.forager.app.ui.availability.AvailabilityScreenSettingsPanelTest] asserts the exact strings this panel draws, not a copy that can drift from them. */
internal const val PHOTO_LOCATION_SETTING_LABEL = "Automatically Save Location to Photos"

internal const val PHOTO_LOCATION_SETTING_EXPLANATION =
    "When saving photos, metadata is stripped of the location data. Enabling this option captures " +
        "your current position, and saves it to the Journal entry instead. This allows you to share " +
        "your photos outside the app without the location being revealed."

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
private fun DistanceUnitSection(distanceUnit: DistanceUnit, onDistanceUnitSelected: (DistanceUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Units", style = MaterialTheme.typography.titleMedium)
        UnitSystem.entries.forEach { system ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onDistanceUnitSelected(system.distanceUnit) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(selected = system.distanceUnit == distanceUnit, onClick = { onDistanceUnitSelected(system.distanceUnit) })
                Text(system.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * The compact "Tools" drawer's entire content. Named for what it actually holds, not what it used
 * to: the composable itself and this file's own doc comments called it the "search drawer" through
 * two rounds of the map/navigation redesign — first because "the whole side panel is the search
 * feature" (the project owner's own original framing, when species search and Recent Searches both
 * lived here), then again once dispatch C moved both out into [SearchDropdown] over the map (see
 * that composable's own doc comment). The owner's own later call, on seeing that move land: stop
 * calling this the search drawer at all, "so it doesn't get lumped together in the future by some
 * ambitious planner" — species search is gone from here for good, and the name should say so. Two
 * things live here now, none of them search:
 *
 * 1. **[SearchControls]**, `includeRecentSearches = false` — Trip Planner only. Waypoints moved
 *    out (Journal restructure Stage 1) into the Journal's own Records tab.
 * 2. **Settings** ([showSettings]) — new as of the map redesign's Dispatch B, per the owner's own
 *    call: this drawer *is* the Tools destination now, so Settings (which had its own bottom-nav
 *    tab before that dispatch) lives here instead, reached one tap deeper via its own entry row —
 *    the same "drill in, own back step" shape [CompactSettingsTab] already uses for its own
 *    CrashLogs submenu.
 *
 * [DrawerHeader] stays the one visible way to close this drawer, same as before. **No sticky Log
 * row** — that stayed on the bottom nav (Journal) — but Settings is sticky here again.
 */
@Composable
internal fun CompactToolsDrawerContent(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onClose: () -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    currentTime: CurrentTimeProvider,
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    lockCameraToPortrait: Boolean,
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    crashFileStore: CrashFileStore,
    backup: BackupControls = BackupControls(),
    /** Counts up when a backup notification is tapped: open Settings, at the Backup section. */
    openSettingsRequest: Int = 0,
) {
    // Own drill-in step, same shape as CompactSettingsTab's own CrashLogs submenu — see this
    // composable's own doc comment, item 2. Composed inside this drawer sheet (which the
    // ModalNavigationDrawer keeps mounted even while visually closed, same as CollapsibleSection's
    // own expand state), so its own BackHandler below takes priority over the top-level
    // isDrawerOpen one — the same "most-recently-composed enabled callback wins" precedence
    // AvailabilityScreen's own top-level BackHandler chain already documents.
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) showSettings = true
    }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    if (showSettings) {
        CompactSettingsTab(
            distanceUnit = distanceUnit,
            onDistanceUnitSelected = onDistanceUnitSelected,
            isNightMode = isNightMode,
            onNightModeMapsChanged = onNightModeMapsChanged,
            autoSaveLocationToPhotos = autoSaveLocationToPhotos,
            onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
            lockCameraToPortrait = lockCameraToPortrait,
            onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            crashFileStore = crashFileStore,
            backup = backup,
            showBackupRequest = openSettingsRequest,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(onClose = onClose)
        SearchControls(
            modifier = Modifier.weight(1f),
            uiState = uiState,
            distanceUnit = distanceUnit,
            onDeletePlannedTrip = onDeletePlannedTrip,
            currentTime = currentTime,
            // See SearchControls' own doc comment on these params: species search, Recent
            // searches, and Advanced search all now live in SearchDropdown, over the map, not here.
            includeAdvancedSearch = false,
            includeRecentSearches = false,
        )
        SettingsEntryRow(onClick = { showSettings = true })
    }
}
