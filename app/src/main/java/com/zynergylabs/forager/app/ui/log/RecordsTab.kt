package com.zynergylabs.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
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
 * **Filter chips, not sub-tabs, as of journal redesign J1 (S3; plan J4).** The four sub-tabs
 * became a [RecordsFilterChipRow] of five chips, All · Finds · Tracks · Waypoints · Offline maps,
 * All the default. This codebase has no navigation library (no `NavHost`, no `NavController`), so
 * [RecordsSubTab] is still an enum plus state, not a real navigation destination; that state is
 * now [selectedTabState], hoisted by [JournalTab] into [JournalScreenState] (S1).
 *
 * **No header, no back arrow, unlike the drill-in shape these screens used inside Settings.**
 * A flat chip filter is left by tapping another chip, not by a back affordance
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
 * chip row above took no space and can overflow.
 */
@Composable
internal fun RecordsTab(
    waypoints: List<Waypoint>,
    waypointsErrorMessage: String?,
    /**
     * A swipe (or the rows' "Delete" accessibility action) on a waypoint row: asks for a pending
     * delete with Undo (journal redesign J4); `MainActivity` wires it to
     * `TrackRecordingViewModel.requestRemoveWaypoint`. It used to be the delete itself, behind a
     * confirm dialog.
     */
    onDeleteWaypoint: (String) -> Unit,
    /**
     * How many journal entries keep each waypoint (`TrackRecordingUiState.waypointEntryReferenceCounts`).
     * Unread from journal redesign J4 (its warning moved into the Undo snackbar) until J5c, whose
     * waypoint details sheet shows it as "Used in N journal entries". A waypoint with no entry here
     * has no count to show and the sheet leaves the line out, rather than print a zero it was not
     * given (the default map is empty, for callers with no counts).
     */
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
    onDownloadAgain: (Long) -> Unit = {},
    tracks: List<Track>,
    onTracksOpened: () -> Unit,
    /**
     * A swipe on a finished track's row, or the Delete on its details (sheet or pane): asks for a pending
     * delete with Undo (Part 2 follow-ups F1 item 5, owner "Option A"); `MainActivity` wires it to
     * `TrackRecordingViewModel.requestRemoveTrack`. `null`, the default, leaves tracks without a delete.
     * A track that is still recording is offered neither the swipe nor the Delete.
     */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back ([TrackRecordingUiState.tracksErrorMessage]); shown above the Tracks chip's list. */
    tracksErrorMessage: String? = null,
    /** GPX full-record export dispatch — see [TrackExportList]'s own doc comment. Defaults empty/no-op so no other caller of this tab changes. */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    findsContent: @Composable ColumnScope.() -> Unit,
    /**
     * J6a (ruling 6.1): the wide tree's detail slot. With it, a tapped record's details open in the
     * right side ([RecordDetailsPane], registered at [JournalDetailPriority.RECORD_DETAILS]) instead of
     * as [RecordDetailsSheet]'s modal sheet, and Back closes them. `null` (the default; compact, tests)
     * is the sheet, as before.
     */
    detailSlot: JournalDetailSlot? = null,
    /**
     * The committed logged finds (`MushroomLogUiState.entries`, what the Finds gallery's own first
     * "Log" tab lists; owner's answer 3 in `prompts/preserved/2026-09-27-17.md`, "Committed finds
     * only"): the Finds chip counts them and the All logbook lists them (journal redesign J1, S3/S4).
     * `null` means the caller has none to give — [LogPanel], out of scope until J6 — and then the
     * Finds and All chips show no count and the All logbook says finds are not listed, rather than
     * showing a made-up number or passing the rest off as everything.
     */
    finds: List<MushroomLogEntry>? = null,
    /**
     * Opens a find's report — the All logbook's find tap (J1 S4). `RecordsTab` selects the Finds chip
     * first, so the report opens in the Finds slot and Back goes report, Finds gallery, All (owner's
     * answer 2). The default does nothing beyond that chip switch ([LogPanel] passes none).
     */
    onOpenFind: (String) -> Unit = {},
    /** J4b L1: the All logbook find tile's long-press Delete (pending, with Undo); `null` leaves it tap-only. */
    onDeleteFind: ((String) -> Unit)? = null,
    /**
     * J4b L1: the All logbook find tile's long-press Edit. `RecordsTab` selects the Finds chip first,
     * as for [onOpenFind], so the edit form opens in the Finds slot.
     */
    onEditFind: ((String) -> Unit)? = null,
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
    /**
     * Where the selection lives. [JournalTab] passes [JournalScreenState.recordsFilterState], hoisted
     * and saveable (journal redesign J1, S1), so the selection survives leaving the Journal tab and
     * an Activity recreation. The default is local, plain `remember` state, which is what [LogPanel]
     * (the wide tree, out of scope until plan stage J6) still gets, unchanged.
     */
    selectedTabState: MutableState<RecordsSubTab> = remember { mutableStateOf(DEFAULT_RECORDS_FILTER) },
    /**
     * Journal redesign J5, L3: in a short window the filter chip row is the second row under the
     * Journal's L1 row, and hides while the content below it scrolls down, returning on a scroll up
     * ([HideOnScrollState]); and (L5a) the chips take the tighter [RecordsChipRowMetrics.ShortWindow]
     * so all five fit one line at 640 dp. `false` (the default: portrait, and `LogPanel`) keeps the
     * row fixed and J1's spacing, as before.
     */
    shortWindow: Boolean = false,
    /** Off while the Tools drawer is open over the Journal, so Back closes the drawer (intent 2026-09-28-28); see [JournalTab]'s parameter of the same name. `true` (the default) is every other caller, unchanged. */
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var selectedTab by selectedTabState

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
            RecordsSubTab.ALL, RecordsSubTab.WAYPOINTS, RecordsSubTab.FINDS -> Unit
        }
    }

    fun selectTab(tab: RecordsSubTab) {
        if (selectedTab == RecordsSubTab.FINDS && tab != RecordsSubTab.FINDS) onFindsTabLeft()
        selectedTab = tab
    }

    // Journal redesign J5c: the record whose details sheet is open, if any. Saveable, so the sheet
    // survives a rotation and an Activity recreation (RecordDetailsTarget's doc comment). Every row
    // type that opens a sheet sets it; the sheet's own dismissal (Back, a scrim tap) clears it.
    var detailsTarget by rememberSaveable(stateSaver = RecordDetailsTargetSaver) { mutableStateOf<RecordDetailsTarget?>(null) }
    val openDetails: (RecordDetailsTarget) -> Unit = { target -> detailsTarget = target }

    // Back-nav-and-save-flow dispatch, Item 1, retargeted by journal redesign J1 (S3, the planner's
    // call in prompts/preserved/2026-09-27-16.md; the owner may overrule): step back to All — the
    // fixed default, not whichever chip was last selected. It used to step back to Waypoints, the
    // old default sub-tab; the rule is the same, only the default moved. A fixed target keeps back
    // deterministic regardless of navigation history (the same press always does the same thing);
    // tracking "last selected" as a second piece of state to reason about was considered and
    // rejected for exactly that reason. Disabled while findsEditingInProgress — see that parameter's
    // own doc comment for why this can't just rely on Compose's usual nested-handler-wins ordering
    // here. From All this handler is off, so Back falls to JournalTab's Records -> Cartography step,
    // exactly what Back did from Waypoints before.
    BackHandler(enabled = backEnabled && selectedTab != RecordsSubTab.ALL && !findsEditingInProgress) {
        selectTab(RecordsSubTab.ALL)
    }

    // J5, L3: a nested-scroll parent over the whole tab, so whichever list is showing (the All
    // logbook, a single-type list, the Finds gallery) reports its scroll to the chip row's state.
    val chipRowScroll = rememberHideOnScrollState()
    Column(modifier = modifier.fillMaxSize().then(if (shortWindow) Modifier.nestedScroll(chipRowScroll.connection) else Modifier)) {
        // Journal redesign J1, S3 (plan J4): one horizontally scrolling row of filter chips replaced
        // the four-tab SecondaryTabRow ("Waypoint Markers" / "Offline Maps" / "Recorded Tracks" /
        // "Logged Finds"). That row's fixed 90 dp tabs are why its labels had to be two words and
        // wrapped to two lines on a phone (see this file's history, and the plan's evidence
        // section); a scrolling chip row sizes each chip to its label, so the short names fit on one
        // line and the row scrolls sideways when it overflows.
        val chipRow: @Composable () -> Unit = {
            RecordsFilterChipRow(
                selected = selectedTab,
                counts = RecordsFilterCounts(
                    finds = finds?.size,
                    tracks = tracks.size,
                    waypoints = waypoints.size,
                    // J4: a region whose delete is pending is not counted.
                    offlineMaps = availabilityUiState.visibleOfflineRegions.size,
                ),
                onSelect = ::selectTab,
                metrics = if (shortWindow) RecordsChipRowMetrics.ShortWindow else RecordsChipRowMetrics.Default,
            )
        }
        if (shortWindow) ShortWindowSecondRow(chipRowScroll) { chipRow() } else chipRow()

        when (selectedTab) {
            // J1 S4: the All logbook — see RecordsLogbookList.
            RecordsSubTab.ALL -> RecordsLogbookList(
                finds = finds,
                tracks = tracks,
                waypoints = waypoints,
                availabilityUiState = availabilityUiState,
                distanceUnit = distanceUnit,
                currentTime = currentTime,
                getFullRecord = getFullRecord,
                onDeleteWaypoint = onDeleteWaypoint,
                onDeleteOfflineRegion = onDeleteOfflineRegion,
                onDeleteTrack = onDeleteTrack,
                onDownloadAgain = onDownloadAgain,
                onOpenFind = { id ->
                    selectTab(RecordsSubTab.FINDS)
                    onOpenFind(id)
                },
                modifier = Modifier.weight(1f).testTag(RECORDS_LOGBOOK_LIST_TAG),
                onDeleteFind = onDeleteFind,
                onEditFind = onEditFind?.let { edit ->
                    { id ->
                        selectTab(RecordsSubTab.FINDS)
                        edit(id)
                    }
                },
                onOpenDetails = openDetails,
            )

            RecordsSubTab.WAYPOINTS -> WaypointsSection(
                waypoints = waypoints,
                errorMessage = waypointsErrorMessage,
                onDeleteWaypoint = onDeleteWaypoint,
                modifier = Modifier.weight(1f),
                onOpenWaypointDetails = { id -> openDetails(RecordDetailsTarget.WaypointDetails(id)) },
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
                onDownloadAgain = onDownloadAgain,
                onOpenRegionDetails = { id -> openDetails(RecordDetailsTarget.OfflineRegionDetails(id)) },
            )

            RecordsSubTab.RECORDED_TRACKS -> TrackExportList(
                tracks = tracks,
                waypoints = waypoints,
                getFullRecord = getFullRecord,
                modifier = Modifier.weight(1f),
                onOpenTrackDetails = { id -> openDetails(RecordDetailsTarget.TrackDetails(id)) },
                onDeleteTrack = onDeleteTrack,
                errorMessage = tracksErrorMessage,
            )

            // Column, not Box: the relocated find-editing composables (CentrePinLocationPicker,
            // LogEntryDetailScreen, etc.) each pass themselves Modifier.weight(1f), which only
            // resolves inside a ColumnScope — see this file's own doc comment on why they were left
            // exactly as-is rather than unified.
            RecordsSubTab.FINDS -> Column(modifier = Modifier.weight(1f).fillMaxSize()) { findsContent() }
        }
    }

    // J5c: the details sheet, over whichever list the row was tapped in. It reads the same lists the
    // rows were drawn from (waypoints and regions already leave out a pending delete).
    //
    // J6a: with a slot the same details are a pane in the right side instead (ruling 6.1), closed by
    // Back as well as by the pane's own back row. The handler is composed after this tab's other
    // handlers (JournalTab's, above it), so it is the first to take Back while the details are open.
    if (detailSlot != null) {
        BackHandler(enabled = backEnabled && detailsTarget != null) { detailsTarget = null }
        JournalDetail(detailSlot, active = detailsTarget != null, priority = JournalDetailPriority.RECORD_DETAILS) {
            detailsTarget?.let { target ->
                RecordDetailsPane(
                    target = target,
                    waypoints = waypoints,
                    tracks = tracks,
                    offlineRegions = availabilityUiState.visibleOfflineRegions,
                    waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                    distanceUnit = distanceUnit,
                    nowEpochMillis = currentTime.nowEpochMillis(),
                    staleThresholdDays = availabilityUiState.offlineStaleThresholdDays,
                    getFullRecord = getFullRecord,
                    onDeleteTrack = onDeleteTrack,
                    onDismiss = { detailsTarget = null },
                )
            }
        }
    } else {
        detailsTarget?.let { target ->
            RecordDetailsSheet(
                target = target,
                waypoints = waypoints,
                tracks = tracks,
                offlineRegions = availabilityUiState.visibleOfflineRegions,
                waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                distanceUnit = distanceUnit,
                nowEpochMillis = currentTime.nowEpochMillis(),
                staleThresholdDays = availabilityUiState.offlineStaleThresholdDays,
                getFullRecord = getFullRecord,
                onDeleteTrack = onDeleteTrack,
                onDismiss = { detailsTarget = null },
                // Owner "1 A" (dispatch 2026-09-28-104, superseding planner message -77's Q1 (b)): over a map
                // only from the Offline maps sub-tab AND only in a short landscape window, where that panel's
                // picker map is beside the list (`OfflineMapsPanel`'s own test, AvailabilityOfflineMapsUi.kt:264,
                // which is `isShortLandscapeJournal`). In portrait the panel is stacked and the sheet lies over
                // the region list, not a map, so it stays solid. (The wide tree no longer reaches this branch:
                // it passes a slot, J6a.)
                overMap = selectedTab == RecordsSubTab.OFFLINE_MAPS && isShortLandscapeJournal(),
            )
        }
    }
}

/**
 * Which of [RecordsTab]'s filter chips is selected — declared in chip display order (journal
 * redesign J1, S3: [ALL] added and made the default, the rest reordered to the plan's All · Finds ·
 * Tracks · Waypoints · Offline maps). Nothing reads the ordinal: [JournalScreenState]'s saver stores
 * names. `internal`, not `private`, as of Stage 2d: [JournalTab]/[LogPanel] hold a pending value of
 * this type to request [FINDS] externally — see [RecordsTab]'s own `pendingSubTab` doc comment.
 */
internal enum class RecordsSubTab { ALL, FINDS, RECORDED_TRACKS, WAYPOINTS, OFFLINE_MAPS }

/** The All logbook's list, as `RecordsTab` places it (J5: the short-window tests scroll and measure it). */
internal const val RECORDS_LOGBOOK_LIST_TAG = "records-logbook-list"
