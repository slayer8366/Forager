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

import com.zynergylabs.forager.app.ui.motion.clickableWithShapedPress
import com.zynergylabs.forager.app.ui.motion.PageSlide
import com.zynergylabs.forager.app.ui.motion.PageSlideStyle
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Map
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.MapModePicker
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.backup.BackupSection
import com.zynergylabs.forager.app.ui.credits.CreditsNavigationRow
import com.zynergylabs.forager.app.ui.credits.OpenSourceSoftwarePage
import com.zynergylabs.forager.app.ui.credits.SOURCES_AND_CREDITS_TITLE
import com.zynergylabs.forager.app.ui.credits.SourcesAndCreditsPage
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
private fun CompactSettingsTab(
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    /** Night mode for the map, and Settings' own checkbox value. */
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    backup: BackupControls,
    showBackupRequest: Int = 0,
    /** Counts up when the settings-reset snackbar's "Settings" is tapped (RECORD -661): Settings' own first page, at the top. */
    showSettingsOnlyRequest: Int = 0,
    modifier: Modifier = Modifier,
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
 * values, their DataStore keys and the ViewModel functions they call are unchanged.
 *
 * **No longer has a "Choose Maps Service" section.** That section picked between OpenStreetMap and
 * USGS as the tile provider for the map's topo/regular modes — superseded outright once [MapMode]
 * pinned Street/Topographical to OpenStreetMap and added Satellite (USGS) as a third option
 * reachable only from the map's own [MapModePicker] (Satellite itself removed since, dispatch
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
private fun NightModeMapsSection(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
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
 * [SettingsCheckboxRow] with a supporting line under it: "Automatically Save Location to Photos" and
 * "Lock camera to portrait" share it. They were Settings' two explained rows; since dispatch
 * 2026-09-28-707 they are drawn by the camera's gear panel (`ui/log/CameraSettingsPanel.kt`), which is
 * why this and the row are `internal`. [tag] goes on the row, as [SettingsCheckboxRow] places it.
 */
@Composable
internal fun ExplainedSettingsCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, label: String, explanation: String, tag: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SettingsCheckboxRow(checked, onCheckedChange, label, tag = tag)
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One Settings radio row: the whole row selects, with the radio button and its label in it. The one
 * copy (dispatch 2026-09-28-658, F6) of the row this panel had written out three times. Touch
 * target and [tag] as [SettingsCheckboxRow].
 */
@Composable
private fun SettingsRadioRow(selected: Boolean, onSelect: () -> Unit, label: String, tag: String? = null) {
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
            SettingsRadioRow(
                selected = system.distanceUnit == distanceUnit,
                onSelect = { onDistanceUnitSelected(system.distanceUnit) },
                label = system.label,
            )
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
