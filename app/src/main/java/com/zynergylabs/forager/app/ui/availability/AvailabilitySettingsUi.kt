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
//
// Dispatch 2026-09-28-707: PhotoLocationSection and CameraPortraitLockSection, two more of the moved names, were removed when
// the two camera settings moved into the camera's gear panel (ui/log/CameraSettingsPanel.kt); the list above is still history.
// Dispatch 2026-10-13-03: LOCK_CAMERA_SETTING_LABEL, LOCK_CAMERA_SETTING_EXPLANATION, PHOTO_LOCATION_SETTING_LABEL and
// PHOTO_LOCATION_SETTING_EXPLANATION were removed with the gear panel's two rows, and ExplainedSettingsCheckbox, which drew
// only those rows, with them (docs/archive/camera-portrait-lock.md).

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.PageSlideStyle
import androidx.compose.ui.graphics.Color
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.ui.backup.BackupControls
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
            .clickableWithShapedPress(role = Role.Button, onClick = onClose)
            .semantics { contentDescription = "Close search options" },
    ) {}
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
            .clickableWithShapedPress(role = Role.Button, onClick = onClick)
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
 * The Tools drawer's "Offline maps" row (RECORD -868, the owner: "Add a quick button to the Tools drawer for Offline
 * Maps. Tapping it brings you to the Offline Maps page"; "Records, on the Offline maps chip (Recommended)"). Fixed
 * above [SettingsEntryRow], in the same shape (the planner's call, RECORD -871): a divider, then a labelled row with
 * the Offline maps chip's map icon. Unlike Settings it leaves the drawer: the drawer closes and Journal > Records
 * opens on the Offline maps chip, and Back reopens Tools and then returns to where the user was
 * (`AvailabilityScreen`'s Tools return step, RECORD -871).
 */
@Composable
internal fun OfflineMapsEntryRow(onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TOOLS_OFFLINE_MAPS_ROW_TAG)
            .clickableWithShapedPress(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Map, contentDescription = null)
        Text("Offline maps", style = MaterialTheme.typography.titleSmall)
    }
}

/** The Tools drawer's "Offline maps" row, for tests. */
internal const val TOOLS_OFFLINE_MAPS_ROW_TAG = "tools-offline-maps-row"
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
 * 1. **[SearchControls]** — Trip Planner only. Waypoints moved
 *    out (Journal restructure Stage 1) into the Journal's own Records tab. Since dispatch
 *    2026-09-28-707 the **Sundown** section ([ToolsSundownSection]) sits under it, in the same
 *    scroll, moved here from Settings so it is one tap away instead of two.
 * 1a. **Offline maps** ([OfflineMapsEntryRow], RECORD -868), a fixed row above Settings: the one
 *    entry here that leaves the drawer, a shortcut to Journal > Records on the Offline maps chip.
 * 2. **Settings** ([showSettings]) — new as of the map redesign's Dispatch B, per the owner's own
 *    call: this drawer *is* the Tools destination now, so Settings (which had its own bottom-nav
 *    tab before that dispatch) lives here instead, reached one tap deeper via its own entry row —
 *    the "drill in, own back step" shape.
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
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    backup: BackupControls = BackupControls(),
    /** Counts up when a backup notification is tapped: open Settings, at the Backup section. */
    openSettingsRequest: Int = 0,
    /** Counts up when the settings-reset snackbar's "Settings" is tapped (RECORD -661): open Settings, at the top. */
    openSettingsOnlyRequest: Int = 0,
    /** The Tools page's Sundown section (dispatch 2026-09-28-707), drawn by [ToolsSundownSection]. */
    sundown: SundownSettings = SundownSettings(),
    /** The "Off-track reminder", the last row of that section. */
    offTrackReminder: OffTrackReminderSettings = OffTrackReminderSettings(),
    /**
     * RECORD -868: the "Offline maps" row's tap, which opens Journal > Records on the Offline maps chip. `null` (the
     * default) draws no row, for a caller with nowhere to send it.
     */
    onOpenOfflineMaps: (() -> Unit)? = null,
) {
    // Own drill-in step — see this composable's own doc comment, item 2. Composed inside this drawer sheet (which the
    // ModalNavigationDrawer keeps mounted even while visually closed, same as CollapsibleSection's
    // own expand state), so its own BackHandler below takes priority over the top-level
    // isDrawerOpen one — the same "most-recently-composed enabled callback wins" precedence
    // AvailabilityScreen's own top-level BackHandler chain already documents.
    var showSettings by remember { mutableStateOf(false) }
    LaunchedEffect(openSettingsRequest) {
        if (openSettingsRequest > 0) showSettings = true
    }
    LaunchedEffect(openSettingsOnlyRequest) {
        if (openSettingsOnlyRequest > 0) showSettings = true
    }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    // Motion Part 3, Amendment 2 (RECORD -682; the owner: "Push slide from the left (Recommended)"; scout T1): Settings slides in
    // from the drawer's left edge while the Tools list slides out to the right, side by side; Back reverses it. The pages draw no
    // fill: the drawer's own is the one 80% layer over the map (MapChromeAlphaTest's rule).
    PageSlide(
        targetState = showSettings,
        depthOf = { if (it) 1 else 0 },
        modifier = Modifier.fillMaxSize(),
        pageColor = Color.Transparent,
        style = PageSlideStyle.PUSH_FROM_LEFT,
    ) { settingsShown ->
    if (settingsShown) {
        CompactSettingsTab(
            distanceUnit = distanceUnit,
            onDistanceUnitSelected = onDistanceUnitSelected,
            isNightMode = isNightMode,
            onNightModeMapsChanged = onNightModeMapsChanged,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            backup = backup,
            showBackupRequest = openSettingsRequest,
            showSettingsOnlyRequest = openSettingsOnlyRequest,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(onClose = onClose)
        SearchControls(
            modifier = Modifier.weight(1f),
            uiState = uiState,
            onDeletePlannedTrip = onDeletePlannedTrip,
            following = {
                HorizontalDivider()
                ToolsSundownSection(sundown = sundown, offTrackReminder = offTrackReminder)
            },
        )
        onOpenOfflineMaps?.let { open -> OfflineMapsEntryRow(onClick = open) }
        SettingsEntryRow(onClick = { showSettings = true })
    }
    }
    }
}
