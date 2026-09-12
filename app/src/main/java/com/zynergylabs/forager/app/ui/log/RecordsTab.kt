package com.zynergylabs.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.ui.availability.AvailabilityUiState
import com.zynergylabs.forager.app.ui.availability.OfflineMapsPanel
import com.zynergylabs.forager.app.ui.availability.WaypointsSection
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.track.TrackExportList

/**
 * The Journal's **Records** tab (journal restructure Stage 1) — "a logbook: raw, complete,
 * machine-generated data. It shows everything, it does not curate" (the project owner's own
 * framing). Four submenus: **Waypoints** (used to be a [WaypointsSection] inside
 * `SearchControls`, reachable from both window classes' own drawers), **Offline Maps** and
 * **Recorded Tracks** (used to be Settings submenus, reached via `DrawerPanel.OfflineMaps`/
 * `DrawerPanel.Tracks` on medium/expanded and `showOfflineMaps`/`showTracks` local state on
 * compact), and **Finds** — added Journal Stage 2b, `amendment-2b-finds-and-trash.md`: raw
 * `MushroomLogEntry` field records, moved here **unmodified, still working exactly as it did in
 * Cartography** — override text: "the override is for the move, not for Records generally." None
 * of the four screens' own content changed — only where they're reached from.
 *
 * **[findsContent] is a slot, not inlined logic.** Compact and expanded each render this same
 * fourth slot as a report-then-edit two-step ([JournalTab]) or straight-to-edit ([LogPanel]) when
 * an entry is open — that per-window difference is real and stays — but when nothing is open, both
 * now host the identical [FindsGalleryScreen] (Stage 2b follow-up dispatch, point 1, "restore the
 * unify" — see that composable's own doc comment for why the two window classes had briefly
 * diverged onto genuinely different browsing screens, and why this reunifies them). [JournalTab]/
 * [LogPanel] each still own their find-editing state (mode, pickers) exactly as before, just render
 * it into this tab's fourth slot now instead of directly into a "Cartography" tab. [onFindsTabLeft]
 * fires whenever [selectedTab] changes away from [RecordsSubTab.FINDS] to a sibling sub-tab — the
 * same "leaving mid-edit is an incidental exit" signal [JournalTab]'s own top-level tab switch
 * already sent before finds moved here; now that finds live *inside* Records, switching among
 * Records' own sub-tabs can interrupt an edit too, a scenario that didn't exist before this move.
 *
 * Follows this app's one existing nested-tab precedent, [FindsGalleryScreen]'s
 * `SecondaryTabRow`/`FindsGalleryTab` — this codebase has no navigation library (no `NavHost`, no
 * `NavController`), so, like every other "route" in this app, [RecordsSubTab] is a private enum
 * plus local `remember` state, not a real navigation destination.
 *
 * **No header, no back arrow, unlike the drill-in shape these screens used inside Settings.**
 * A flat `SecondaryTabRow` sub-tab is left by tapping another tab, not by a back affordance
 * embedded in the content — so [OfflineMapsPanel]/[TrackExportList] are called here without the
 * header rows (`OfflineMapsHeader`, `TrackExportHeader`) their old drill-in homes needed; both
 * were deleted as dead code once this became their only caller's shape.
 *
 * [onOfflineMapsOpened]/[onTracksOpened] fire once per entry into their own sub-tab (via
 * [LaunchedEffect] keyed on [RecordsSubTab]), the same "lazy-load on becoming visible" semantics
 * their old `onOpenOfflineMaps`/`onOpenTracks` callbacks had when tapping the old Settings entry
 * rows opened the drill-in submenu. Waypoints/Finds need no such callback: both already load
 * eagerly at their owning `ViewModel`'s init, unconditional on anything being opened.
 *
 * **`Modifier.weight(1f)` on every branch, [findsContent] included, is load-bearing** — see this
 * file's own git history (Stage 1's `WaypointsSection` regression) and
 * `amendment-2b-finds-and-trash.md`'s own reminder: a branch without it is measured as though the
 * tab row above took no space and can overflow.
 */
@Composable
internal fun RecordsTab(
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    availabilityUiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    night: Boolean,
    onOfflineMapRegionPicked: (LatLng) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onOfflineMapsOpened: () -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    tracks: List<Track>,
    onTracksOpened: () -> Unit,
    /** GPX full-record export dispatch — see [TrackExportList]'s own doc comment. Defaults empty/no-op so no other caller of this tab changes. */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    findsContent: @Composable ColumnScope.() -> Unit,
    onFindsTabLeft: () -> Unit = {},
    /**
     * Whether [JournalTab]/[LogPanel]'s own find-editing `BackHandler` is currently live —
     * back-nav-and-save-flow dispatch, Item 1. Gates this tab's own sub-tab-stepping `BackHandler`
     * (below) out of the way while it is: that handler is registered at a *shallower* structural
     * point (the caller's own composable, this tab's parent) than this one even though it covers a
     * conceptually *deeper* state (editing a specific find, inside the Finds sub-tab, inside
     * Records), so this can't rely on Compose's usual "more nested wins" ordering the way the
     * Records→Cartography step safely does — see this tab's own new `BackHandler` for the full
     * reasoning, and `AvailabilityScreen`'s outer four for the same caution stated there first.
     * Defaults to `false` so every other caller of this composable is unaffected.
     */
    findsEditingInProgress: Boolean = false,
    /**
     * A one-shot external request to switch to a specific sub-tab — Stage 2d's routing fix for the
     * map "+" icon bar's "Log a find" flow, which used to leave this tab's own [selectedTab]
     * (defaults to [RecordsSubTab.WAYPOINTS]) untouched even after [JournalTab]/[LogPanel] switched
     * to Records, landing on Waypoints instead of Finds. `null` (the default) means nothing pending;
     * every other caller of this composable passes nothing, so its own behavior is unchanged.
     */
    pendingSubTab: RecordsSubTab? = null,
    /** Fires once [pendingSubTab] has been applied — the caller clears its own copy so the same request doesn't reapply after the user has since navigated elsewhere. */
    onPendingSubTabConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableStateOf(RecordsSubTab.WAYPOINTS) }

    LaunchedEffect(pendingSubTab) {
        if (pendingSubTab != null) {
            selectedTab = pendingSubTab
            onPendingSubTabConsumed()
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            RecordsSubTab.OFFLINE_MAPS -> onOfflineMapsOpened()
            RecordsSubTab.RECORDED_TRACKS -> onTracksOpened()
            RecordsSubTab.WAYPOINTS, RecordsSubTab.FINDS -> Unit
        }
    }

    fun selectTab(tab: RecordsSubTab) {
        if (selectedTab == RecordsSubTab.FINDS && tab != RecordsSubTab.FINDS) onFindsTabLeft()
        selectedTab = tab
    }

    // Back-nav-and-save-flow dispatch, Item 1: step back to Waypoints — the fixed default, not
    // whichever sub-tab was last selected. A fixed target keeps back deterministic regardless of
    // navigation history (the same press always does the same thing); tracking "last selected" as
    // a second piece of state to reason about was considered and rejected for exactly that reason.
    // Disabled while findsEditingInProgress — see that parameter's own doc comment for why this
    // can't just rely on Compose's usual nested-handler-wins ordering here.
    BackHandler(enabled = selectedTab != RecordsSubTab.WAYPOINTS && !findsEditingInProgress) {
        selectTab(RecordsSubTab.WAYPOINTS)
    }

    Column(modifier = modifier.fillMaxSize()) {
        // SecondaryTabRow is the fixed-width kind: at 360dp each of these four tabs measures
        // exactly 90dp (measured, a plain 360/4, not sized to content), so each label gets the
        // same narrow column and the only question is where its text breaks.
        //
        // Every label is two words, deliberately — owner's call, from a device screenshot.
        // "Waypoints" and "Finds" used to be one word each, and a single word too wide for 90dp
        // has nowhere to break but inside itself: "Waypoints" rendered as "Waypoint" / "s" on
        // hardware. Shrinking the type was tried first (titleSmall 14sp -> labelMedium 12sp) and
        // the owner's next screenshot showed the same broken word, which is the answer to
        // whether a smaller font buys enough headroom at this width: it does not, and any margin
        // it does buy is one long word away from being spent again. Naming each tab with two
        // words removes the failure mode rather than narrowing it — the longest single word left
        // is "Waypoint"/"Recorded" at eight characters, and all four labels take two lines, so
        // the row is uniform instead of one odd tab out.
        //
        // No style override, so these are Tab's own default label style again. The 12sp override
        // was only ever load-bearing while a nine-character word had to fit one line; two-word
        // names carry their own headroom, and the owner's call is that the row reads better at
        // the normal size. Nothing here depends on the smaller type — if a future label does,
        // that is the signal to rename it rather than to shrink the row again.
        //
        // Not a rename for brevity's sake: "Waypoint Markers" and "Logged Finds" are what these
        // screens hold. The earlier instruction was not to *shorten* the word ("Points"), which
        // this does not do.
        //
        // textAlign = Center because a wrapped label is not centred by default: a single-line
        // label's text box wraps to its content and the Tab centres the box, but a label that
        // wraps fills the tab's full width and its lines then sit start-aligned inside it — the
        // second line visibly hanging left, which the same screenshot showed.
        //
        // Robolectric cannot check any of this: its text-layout measurement in this project
        // reports implausible glyph widths (single digits of px for a whole word, at any font
        // size), so nothing here ever measures as wrapping and a line-count assertion would pass
        // whatever the labels say. The device is the only authority on this row.
        SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == RecordsSubTab.WAYPOINTS,
                onClick = { selectTab(RecordsSubTab.WAYPOINTS) },
                text = { Text("Waypoint Markers", textAlign = TextAlign.Center) },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.OFFLINE_MAPS,
                onClick = { selectTab(RecordsSubTab.OFFLINE_MAPS) },
                text = { Text("Offline Maps", textAlign = TextAlign.Center) },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.RECORDED_TRACKS,
                onClick = { selectTab(RecordsSubTab.RECORDED_TRACKS) },
                text = { Text("Recorded Tracks", textAlign = TextAlign.Center) },
            )
            Tab(
                selected = selectedTab == RecordsSubTab.FINDS,
                onClick = { selectTab(RecordsSubTab.FINDS) },
                text = { Text("Logged Finds", textAlign = TextAlign.Center) },
            )
        }

        when (selectedTab) {
            RecordsSubTab.WAYPOINTS -> WaypointsSection(
                waypoints = waypoints,
                errorMessage = waypointsErrorMessage,
                onDeleteWaypoint = onDeleteWaypoint,
                entryReferenceCounts = waypointEntryReferenceCounts,
                modifier = Modifier.weight(1f),
            )

            RecordsSubTab.OFFLINE_MAPS -> OfflineMapsPanel(
                modifier = Modifier.weight(1f),
                uiState = availabilityUiState,
                distanceUnit = distanceUnit,
                currentTime = currentTime,
                mapSlot = mapSlot,
                isNightMode = night,
                onRegionPicked = onOfflineMapRegionPicked,
                onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                onOfflineMapNameChanged = onOfflineMapNameChanged,
                onDownloadOfflineMaps = onDownloadOfflineMaps,
                onDeleteOfflineRegion = onDeleteOfflineRegion,
            )

            RecordsSubTab.RECORDED_TRACKS -> TrackExportList(
                tracks = tracks,
                waypoints = waypoints,
                getFullRecord = getFullRecord,
                modifier = Modifier.weight(1f),
            )

            // Column, not Box: the relocated find-editing composables (CentrePinLocationPicker,
            // LogEntryDetailScreen, etc.) each pass themselves Modifier.weight(1f), which only
            // resolves inside a ColumnScope — see this file's own doc comment on why they were left
            // exactly as-is rather than unified.
            RecordsSubTab.FINDS -> Column(modifier = Modifier.weight(1f).fillMaxSize()) { findsContent() }
        }
    }
}

/**
 * Which of [RecordsTab]'s four sub-tabs is selected — ordinal order matches display order.
 * `internal`, not `private`, as of Stage 2d: [JournalTab]/[LogPanel] hold a pending value of this
 * type to request [FINDS] externally — see [RecordsTab]'s own `pendingSubTab` doc comment.
 */
internal enum class RecordsSubTab { WAYPOINTS, OFFLINE_MAPS, RECORDED_TRACKS, FINDS }
