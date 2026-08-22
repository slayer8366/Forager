package com.forager.app.ui.availability

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forager.app.BuildConfig
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.ClusterForagingAreasUseCase
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.ForagingWeatherGuidance
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.MgrsConverter
import com.forager.app.domain.SystemCurrentTimeProvider
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.ForagingArea
import com.forager.app.domain.model.ForagingAreas
import com.forager.app.domain.model.FruitingLagBucket
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MgrsCoordinate
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PhotoSource
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.domain.model.Waypoint
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.sensor.AndroidCompassProvider
import com.forager.app.ui.adaptive.WindowWidthClass
import com.forager.app.ui.adaptive.currentWindowWidthClass
import com.forager.app.ui.log.JournalTab
import com.forager.app.ui.log.LogPanel
import com.forager.app.ui.log.MushroomLogUiState
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.BasemapCoverage
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapService
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.map.SightingsMapSlot
import com.forager.app.ui.map.VISITING_ORDER_DISCLAIMER
import com.forager.app.ui.map.foragingAreaSummary
import com.forager.app.ui.motion.MotionTokens
import com.forager.app.ui.theme.Bark
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private enum class ResultsTab(val label: String) {
    LIST("List"),
    MAP("Maps"),
    SEASONAL("Seasonal"),
}

/**
 * The compact bottom nav's five destinations — List/Maps/Seasonal (the same three [ResultsTab]
 * values the medium/expanded window's top tab row switches between, kept in sync with
 * [ResultsTab]'s own `selectedTab` state — see [AvailabilityScreen]'s `ForagerBottomNav` call site)
 * plus Journal and Settings, both moved here from the drawer per the project owner's own framing:
 * the compact bottom nav is where a user reaches every top-level destination now, and the drawer
 * (see [CompactSearchDrawerContent]) is search-only. [ResultsTab] itself stays a 3-way enum,
 * unchanged, since the medium/expanded window's tab row still switches only between those three.
 */
private enum class CompactTab(val label: String) {
    LIST("List"),
    MAP("Maps"),
    SEASONAL("Seasonal"),
    JOURNAL("Journal"),
    SETTINGS("Settings"),
}

/** The compact bottom nav's icon per destination — see [ForagerBottomNav]. */
private fun CompactTab.icon(): ImageVector = when (this) {
    CompactTab.LIST -> Icons.AutoMirrored.Filled.List
    CompactTab.MAP -> Icons.Filled.Map
    CompactTab.SEASONAL -> Icons.Filled.WbSunny
    CompactTab.JOURNAL -> Icons.Filled.MenuBook
    CompactTab.SETTINGS -> Icons.Filled.Settings
}

/**
 * The medium/expanded window's drawer panels — see [AvailabilityScreen]'s doc comment on
 * `drawerPanel` for how they're switched between. [Settings] and [Log] are both reached from
 * sticky entries at the bottom of [Search] (see [SettingsEntryRow]/`MushroomLogEntryRow`);
 * [OfflineMaps] is reached from a row inside [Settings], one level deeper — its own back arrow
 * returns to [Settings], not all the way to [Search]. Closing the drawer entirely resets all the
 * way back to [Search] regardless of which panel was showing.
 *
 * [Log] additionally opens directly — bypassing [Search] — from the map's long-press "Log a find"
 * option; see [MapTab]'s `onLogFindHere` call site in [AvailabilityScreen].
 *
 * **Compact windows no longer use this at all.** [Settings] and [Log] moved to the bottom nav
 * ([CompactTab.SETTINGS]/[CompactTab.JOURNAL] — see [ForagerBottomNav]'s doc comment) per the
 * project owner's own framing; the compact drawer ([CompactSearchDrawerContent]) is search-only
 * and reaches its own Offline Maps-equivalent nowhere, since Settings itself is a bottom-nav
 * destination there (see [CompactSettingsTab]). This enum, [drawerSheetContent], and the
 * `PermanentNavigationDrawer` it feeds stay exactly as they were before any of that — untouched,
 * medium/expanded-only — see docs/plans/map-redesign.md's "Scope decision" section.
 */
private enum class DrawerPanel {
    Search,
    Settings,
    OfflineMaps,
    Log,
}

/** How long a first back press keeps "exit on the next one" armed — see [AvailabilityScreen]. */
private const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L

/**
 * The screen's spacing scale. Padding and gap values across this file used to be picked ad hoc
 * (2dp here, 10dp there) with no relationship to each other, which is why visually-similar things
 * — card internal padding, the gap between a card's rows — didn't quite line up card to card.
 * Four steps, each used for a stated kind of gap, rather than a value invented per call site.
 */
private object Spacing {
    /** Within a tightly related group — a line and its own subtext, one card's internal rows. */
    val xs = 4.dp

    /** Between related but distinct items — chips in a row, a card's own sub-sections. */
    val sm = 8.dp

    /** A card's outer padding, and the standard gap between sibling cards. */
    val md = 12.dp

    /** Screen-level padding, and the gap between major regions of a tab. */
    val lg = 16.dp
}

/**
 * Map-first layout: the results (map or ranked list) own the content area, and everything set far
 * less than once per search — location, radius, month, the foraging-areas layer, and trip
 * planning — lives in a navigation drawer behind the app bar's tune icon, as two independently
 * collapsible sections; see [SearchControls]. Species/category, the one control used on nearly
 * every search, lives in the app bar itself; see [AvailabilitySearchTopBar].
 *
 * **Why the rest is still in a drawer.** The controls used to be stacked above the results in one
 * unscrolled [Column]. A Column measures its non-weighted children in order against the height
 * still unclaimed, so the eight-odd wrap-content controls took the viewport and whatever came
 * after them — the tab row, the conditions card, the map — was measured against what was left,
 * which on a ~780dp-tall screen is zero. Nothing scrolled, because the Column had no
 * `verticalScroll`, so the starved children were simply unreachable. Making that Column
 * scrollable was the other option and was rejected: a scrollable parent passes an infinite
 * height constraint down, which a map cannot be measured against at all, so the map would still
 * have needed a hard-coded height and would still not have been the primary thing on screen.
 *
 * The drawer is what makes the real fix possible: with most controls gone, the content area has
 * a small, bounded set of wrap-content siblings above the results (the top bar, the summary
 * strip, the tab row), and the results take the rest via [Modifier.weight], which is a definite
 * bounded height rather than a remainder.
 *
 * **The app bar is the one exception**, and the one place a change here can still reintroduce the
 * squeeze this file's whole layout exists to avoid — see [AvailabilitySearchTopBar]'s own doc
 * comment for why it's a fixed two-row bar rather than a single Material3 row, and
 * [AvailabilityScreenLayoutTest] for the measurement that verifies it hasn't.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvailabilityScreen(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onMapTabSelected: () -> Unit,
    onSeasonalTabSelected: () -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onReopenTaxonSuggestions: () -> Unit,
    /** Called when a date and name are confirmed for a trip pin dropped via the map's long-press gesture. */
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    /** Called when one of the drawer's recent searches is tapped; see [RecentSearchesSection]. */
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    /**
     * Where "now" comes from for the relative times this screen renders — the offline banner's
     * "saved 3 hours ago" and the recent-searches picker's "cached 2 days ago".
     *
     * Injected rather than read off [System.currentTimeMillis] at the two call sites, and it is
     * the reason [CurrentTimeProvider] exists: a Robolectric test asserts the banner's actual
     * rendered text is on screen, and text derived from the real wall clock is not something a
     * test can name. Defaults to the real clock the same way [mapSlot] defaults to the real map,
     * so this stays optional for callers that render neither.
     */
    currentTime: CurrentTimeProvider = SystemCurrentTimeProvider,
    /** Set by long-pressing the picker map in the Offline Maps submenu — see `OfflineMapsPanel`. */
    onOfflineMapLatChanged: (String) -> Unit,
    onOfflineMapLngChanged: (String) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineMaps: () -> Unit,
    /**
     * The mushroom log drawer destination's own state — see [com.forager.app.ui.log.LogPanel].
     * Defaulted, like [mapSlot] below, so the many existing tests of this screen that have nothing
     * to do with the log don't need to pass log-specific state and callbacks just to compile.
     */
    logUiState: MushroomLogUiState = MushroomLogUiState(),
    cameraCaptureFiles: CameraCaptureFiles = CameraCaptureFiles(LocalContext.current),
    /** Starts and immediately opens a new log entry — the map's long-press "Log a find" option is the only production caller; entries have no other creation path (see `docs/plans/mushroom-log.md`'s Navigation section). */
    onStartLogEntry: (LatLng, LocalDate) -> Unit = { _, _ -> },
    onOpenLogEntry: (String) -> Unit = {},
    onCloseLogEntry: () -> Unit = {},
    onLogEntryChanged: (MushroomLogEntry) -> Unit = {},
    onAddLogPhoto: (PhotoSource) -> Unit = {},
    onRemoveLogPhoto: (LogPhoto) -> Unit = {},
    onDeleteLogEntry: (String) -> Unit = {},
    /**
     * The compact map icon stack's GPS/locate-me button. Distinct from [onUseCurrentLocation] —
     * see [LocateMeStatus]'s doc comment — and, like it, defers the OS permission dialog to the
     * Activity (see `MainActivity`'s `pendingLocationAction`) rather than requesting it here.
     */
    onLocateMe: () -> Unit = {},
    /**
     * Whether a track is currently being recorded, and the compact map's start/stop toggle for it.
     * Rendered inside the same compass/elevation/MGRS strip box, on its right-hand side, rather than
     * as a sixth [MapIconStack] icon — `map-redesign.md` §3 fixes that stack at exactly five, a
     * settled owner decision this doesn't re-litigate. See `MainActivity`'s `LaunchedEffect` on
     * [isRecording] for what actually starts/stops [com.forager.app.service.TrackRecordingService].
     */
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    /** Set when the most recent [onToggleRecording]-triggered start failed — see [CompactMapTab]'s own doc comment on how this reaches the user. */
    startRecordingErrorMessage: String? = null,
    /**
     * The active track's recorded points, oldest first — see [com.forager.app.ui.map.MapSlot]'s own
     * doc comment on this same parameter for how it's drawn. Empty whenever [isRecording] is false.
     */
    breadcrumbPoints: List<LatLng> = emptyList(),
    /**
     * Saved waypoints — independent of any track recording, per [Waypoint]'s own doc comment.
     * Rendered as pins on the map ([com.forager.app.ui.map.MapSlot]) and listed, with delete, in
     * the search drawer's "Waypoints" section ([WaypointsSection]).
     */
    waypoints: List<Waypoint> = emptyList(),
    /** Called with the dropped location and the confirmed name when "Drop a waypoint" is chosen from the map's long-press menu — see [WaypointNameDialog]. */
    onDropWaypoint: (LatLng, String) -> Unit = { _, _ -> },
    onDeleteWaypoint: (String) -> Unit = {},
    /** Set when the most recent waypoint load/add/delete failed — rendered in [WaypointsSection]. */
    waypointsErrorMessage: String? = null,
    /**
     * Bearing/distance/elevation difference back to the active track's start point, from the
     * device's current position — `null` whenever nothing is being recorded, no fix has come in
     * yet, or no breadcrumb has landed to compute a start from. See [ReturnToStartInfo]'s own doc
     * comment for why there's no ETA, and [CompassElevationStripContent] for where this renders.
     */
    returnToStart: ReturnToStartInfo? = null,
    /** Whether the walker has said they're heading back — see [com.forager.app.ui.track.TrackRecordingViewModel.startReturn]'s own doc comment. */
    isReturning: Boolean = false,
    /** Set once [isReturning] and the live distance back has been trending up rather than down — see `DetectOffTrackUseCase`. */
    isOffTrack: Boolean = false,
    onToggleReturning: () -> Unit = {},
    /**
     * What reads the device compass for the compact map's top strip. Defaults to the real sensor,
     * so no production caller passes it — same [mapSlot]/[cameraCaptureFiles] pattern below.
     */
    compassProvider: CompassProvider = AndroidCompassProvider(LocalContext.current),
    /**
     * What fills the map's box. Defaults to the real map, so no production caller passes it; see
     * [MapSlot] for why the map is reached through a slot rather than named directly here.
     */
    mapSlot: MapSlot = SightingsMapSlot,
) {
    // Map up front. The list is one tap away; the map is the thing this screen is arranged around.
    var selectedTab by remember { mutableStateOf(ResultsTab.MAP) }

    // The compact bottom nav's own 5-way selection — see [CompactTab]'s doc comment for why this
    // is separate from selectedTab rather than extending ResultsTab itself (which the medium/
    // expanded window's tab row also reads and must stay 3-way). Kept in sync with selectedTab
    // below whenever the tapped destination is one of the three they share, so onMapTabSelected/
    // onSeasonalTabSelected's LaunchedEffect keeps firing correctly and a later resize to a wider
    // window lands on the same List/Maps/Seasonal tab compact was just showing.
    var compactTab by remember { mutableStateOf(CompactTab.MAP) }

    // Local remembered state, same reasoning as selectedTab/isTopoMode below: purely a display
    // decision the ViewModel has no part in. The compact map icon stack's fullscreen toggle sets
    // this — see CompactMapTab's call site. Compact-only: WindowWidthClass.MEDIUM/EXPANDED never
    // render the icon stack that can set it, so it stays false there. Only reachable while on the
    // Maps tab (the toggle icon lives in that tab's own icon stack), so this being true while
    // selectedTab != MAP cannot happen in practice.
    var isMapFullscreen by remember { mutableStateOf(false) }

    // Local remembered state, alongside selectedTab and for the same reason: which basemap is under
    // the overlays changes nothing the ViewModel owns. It triggers no fetch, filters no result, and
    // no domain type depends on it — it is purely what the tiles look like. Putting it in
    // AvailabilityUiState would make the ViewModel the authority on a decision it has no part in.
    // Two pieces now instead of one [Basemap] — see [MapService]'s doc comment for why the choice
    // split into "which service" (Settings, occasional) and "which mode" (the map's own quick-fire
    // icon, frequent) — but the reasoning above still applies to both: this task's own notes ask for
    // this state "wherever basemap/onBasemapSelected currently live", and that is here, in Compose
    // state local to this screen, not the ViewModel — PR #13 never put it there despite the task's
    // phrasing suggesting otherwise, and moving it now would be scope this task didn't ask for.
    //
    // The cost, stated rather than hidden: like selectedTab, both reset to their defaults on process
    // death. Persisting them needs somewhere to persist *to*, and adding a Room table or a DataStore
    // for two small pieces of display-only state is the speculative build CLAUDE.md warns against.
    var selectedMapService by remember { mutableStateOf(MapService.DEFAULT) }
    var isTopoMode by remember { mutableStateOf(true) }
    val basemap = selectedMapService.basemapFor(isTopoMode)
    // Same reasoning and cost as selectedMapService above — see DistanceUnit's own doc comment for
    // why this stays session-local display state rather than a persisted preference.
    var distanceUnit by remember { mutableStateOf(DistanceUnit.KILOMETERS) }
    var drawerPanel by remember { mutableStateOf(DrawerPanel.Search) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current

    // The authority for "should the drawer be open" — not drawerState.isOpen. DrawerState is an
    // animated slide-in/out; its currentValue only settles to Open/Closed once that animation
    // finishes, so routing back-press decisions off it left a real window, mid-animation, where a
    // rapid open-then-back could still read as closed and fall through to the exit-warning
    // handler below instead of the close-drawer one — a real bug, not a hypothetical, reported
    // against the previous version. isDrawerOpen flips the instant an open/close is requested,
    // synchronously, so both BackHandlers below always route correctly on the very next press no
    // matter where the slide animation currently is. The LaunchedEffect drives the actual
    // animated drawer as a side effect of this flag, and Compose's animateTo cancels and reverses
    // cleanly if the flag flips again before a prior animation finished, so rapid open/close
    // toggling stays responsive rather than queuing up stale animations.
    var isDrawerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
            // Reset to the Search panel on every close — scrim tap, back button, or a search action
            // that closes the drawer itself — rather than leaving Settings showing the next time the
            // drawer opens. A minor, easily-revisited default: see this task's own notes.
            drawerPanel = DrawerPanel.Search
        }
    }

    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth, uiState.taxonFilter) {
        if (selectedTab == ResultsTab.MAP) onMapTabSelected()
        if (selectedTab == ResultsTab.SEASONAL) onSeasonalTabSelected()
    }

    // System back navigates one step toward "home" — the Maps tab, chrome visible, drawer closed
    // — before falling through to the exit-confirmation handler at the bottom, rather than exiting
    // from wherever the user happens to be. Nested UI further down the tree (a Journal entry, the
    // Settings offline-maps submenu, the map's own add-action tile) unwinds itself first via its
    // own local BackHandler — see JournalTab's, CompactSettingsTab's, and CompactMapTab's — since
    // Compose's OnBackPressedDispatcher tries the most-recently-composed enabled callback first, so
    // a nested composable's own handler naturally takes priority over these four.
    //
    // Each condition below explicitly excludes the states "more nested" than it, so at most one is
    // ever enabled at once — declaration order isn't what decides precedence, in case that ever
    // stops being true. Verified end to end (not just reasoned about) in
    // AvailabilityScreenBackNavigationTest, including the case only compact width can reach where
    // isDrawerOpen and isMapFullscreen are both true (the drawer's own Search entry point is still
    // reachable while fullscreen — see MapIconStack).
    //
    // Only isDrawerOpen/isMapFullscreen/compactTab drive this — all three are compact-only state
    // that a medium/expanded window never changes away from its own defaults (isDrawerOpen stays
    // false there; a PermanentNavigationDrawer is never "closed"), so none of this fires on that
    // width class.
    BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }
    BackHandler(enabled = !isDrawerOpen && isMapFullscreen) {
        isMapFullscreen = false
    }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab != CompactTab.MAP) {
        compactTab = CompactTab.MAP
        // Keeps the shared ResultsTab-driven state in sync, same as ForagerBottomNav's own tap
        // handler does — see compactTab's own doc comment for why the two exist side by side.
        selectedTab = ResultsTab.MAP
    }

    // "Home": drawer closed, chrome visible, Maps tab selected. A second back press within the
    // window actually exits; a lone press just warns. This is a single-Activity app with nothing
    // else back could sensibly navigate to once nothing is nested, so an un-warned single back
    // press (which a pan gesture can graze) would otherwise dump the user straight out.
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == CompactTab.MAP) {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Tap Back Button Again to Exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            delay(DOUBLE_BACK_EXIT_WINDOW_MS)
            backPressedOnce = false
        }
    }

    val windowWidthClass = currentWindowWidthClass()

    // The drawer's panel content, shared between the compact `ModalNavigationDrawer` below and
    // the `PermanentNavigationDrawer` medium+ windows get instead — see [windowWidthClass]. A
    // local composable lambda rather than a top-level one so it closes over this function's ~25
    // params and local state directly instead of re-threading all of it through an explicit
    // parameter list a second time. [showCloseButton] is the only behavioral difference the two
    // hosts need: a permanent drawer is never "closed", so it gets no close affordance.
    // ColumnScope receiver, not a plain function type: both hosts' drawer sheets hand this a
    // ColumnScope (that's what lets the `Modifier.weight(1f)` calls inside resolve at all), and a
    // lambda assigned to a receiver-typed val keeps that receiver rather than losing it.
    val drawerSheetContent: @Composable ColumnScope.(showCloseButton: Boolean) -> Unit = { showCloseButton ->
        when (drawerPanel) {
            DrawerPanel.Search -> {
                if (showCloseButton) {
                    // The one visible way to close this drawer other than tapping the scrim:
                    // gestures are off (see gesturesEnabled below), and the scrim alone is
                    // undiscoverable.
                    DrawerHeader(onClose = { isDrawerOpen = false })
                }
                SearchControls(
                    // The controls take whatever height is left over so the settings entry
                    // row stays pinned to the bottom of the sheet rather than sitting past
                    // the end of the controls' own scroll, where nobody would find it.
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    distanceUnit = distanceUnit,
                    onUseCurrentLocation = {
                        isDrawerOpen = false
                        onUseCurrentLocation()
                    },
                    onManualLatChanged = onManualLatChanged,
                    onManualLngChanged = onManualLngChanged,
                    onSearchManualCoordinates = {
                        isDrawerOpen = false
                        onSearchManualCoordinates()
                    },
                    onRadiusChanged = onRadiusChanged,
                    onMonthSelected = onMonthSelected,
                    onDeletePlannedTrip = onDeletePlannedTrip,
                    waypoints = waypoints,
                    onDeleteWaypoint = onDeleteWaypoint,
                    waypointsErrorMessage = waypointsErrorMessage,
                    onRecentSearchSelected = { summary ->
                        // Closed for the same reason searching from this drawer closes it:
                        // the tap starts a search, and the results are behind the sheet.
                        isDrawerOpen = false
                        onRecentSearchSelected(summary)
                    },
                    currentTime = currentTime,
                )
                // Both sticky footer rows: the log is the newer of the two, placed above
                // Settings so it isn't the last thing in the sheet — see MushroomLogEntryRow.
                MushroomLogEntryRow(onClick = { drawerPanel = DrawerPanel.Log })
                // Occupies the search panel's old sticky-footer slot — BuildIdentityFooter
                // moved to the bottom of the Settings panel below.
                SettingsEntryRow(onClick = { drawerPanel = DrawerPanel.Settings })
            }

            DrawerPanel.Settings -> {
                SettingsHeader(onBack = { drawerPanel = DrawerPanel.Search })
                SettingsContent(
                    modifier = Modifier.weight(1f),
                    selectedMapService = selectedMapService,
                    onMapServiceSelected = { selectedMapService = it },
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = { distanceUnit = it },
                    onOpenOfflineMaps = { drawerPanel = DrawerPanel.OfflineMaps },
                )
                BuildIdentityFooter()
            }

            DrawerPanel.OfflineMaps -> {
                // Back returns to Settings, one level up — not all the way to Search. See
                // DrawerPanel's own doc comment.
                OfflineMapsHeader(onBack = { drawerPanel = DrawerPanel.Settings })
                OfflineMapsPanel(
                    modifier = Modifier.weight(1f),
                    uiState = uiState,
                    distanceUnit = distanceUnit,
                    mapSlot = mapSlot,
                    onRegionPicked = { location ->
                        onOfflineMapLatChanged(location.lat.toString())
                        onOfflineMapLngChanged(location.lng.toString())
                    },
                    onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                    onDownloadOfflineMaps = onDownloadOfflineMaps,
                    onDeleteOfflineMaps = onDeleteOfflineMaps,
                )
            }

            DrawerPanel.Log -> {
                LogPanel(
                    modifier = Modifier.weight(1f),
                    uiState = logUiState,
                    cameraCaptureFiles = cameraCaptureFiles,
                    onOpenEntry = onOpenLogEntry,
                    onCloseEntry = onCloseLogEntry,
                    onEntryChanged = onLogEntryChanged,
                    onAddPhoto = onAddLogPhoto,
                    onRemovePhoto = onRemoveLogPhoto,
                    onDeleteEntry = onDeleteLogEntry,
                    onBackToSearch = { drawerPanel = DrawerPanel.Search },
                )
            }
        }
    }

    // The Scaffold + tab content for MEDIUM/EXPANDED windows only — shared with drawerSheetContent
    // for the same reason: it closes over this function's state rather than re-threading it.
    // COMPACT windows use [compactMainScaffold] below instead, a separate composable rather than a
    // conditional threaded through this one, per CLAUDE.md — the map redesign (full-bleed map,
    // bottom nav, icon stack, fullscreen toggle) is compact-only (see docs/plans/map-redesign.md's
    // "Scope decision" section), and this scaffold is what stays byte-for-byte what MEDIUM/EXPANDED
    // already had before that redesign, untouched by any of it.
    val mainScaffold: @Composable () -> Unit = {
        Scaffold(
            topBar = {
                AvailabilitySearchTopBar(
                    uiState = uiState,
                    onOpenDrawer = {
                        // Dismissed here, not just left to whatever state the drawer's own
                        // content happens to leave it in: the suggestion popup is anchored to
                        // this bar and the drawer opens as an overlay above it, so without this
                        // the popup stayed visible underneath/behind the drawer instead of
                        // collapsing along with it.
                        onDismissTaxonSuggestions()
                        // Medium+ windows show the drawer's panel permanently (see
                        // PermanentNavigationDrawer below) — there is nothing to open, so this
                        // icon instead jumps the always-visible panel back to Search, the same
                        // "get back to search options" job it does on compact.
                        drawerPanel = DrawerPanel.Search
                    },
                    onUseCurrentLocation = onUseCurrentLocation,
                    onCategorySelected = onCategorySelected,
                    onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Scaffold's padding carries the system bar insets, so nothing here is laid
                    // out under the status or navigation bar.
                    .padding(padding),
            ) {
                ActiveSearchSummary(uiState, distanceUnit, onReopenTaxonSuggestions = onReopenTaxonSuggestions)
                SearchNotice(uiState)

                SecondaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ResultsTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }

                val onLogFindHere: (LatLng) -> Unit = { location ->
                    drawerPanel = DrawerPanel.Log
                    isDrawerOpen = true
                    onStartLogEntry(location, LocalDate.now())
                }

                // weight(1f) states the intent: the results get whatever is left after the
                // wrap-content siblings above, and Compose measures weighted children last, so
                // that height is definite and bounded instead of a remainder that can reach zero.
                //
                // MEDIUM/EXPANDED windows reveal List and Map together in [CombinedResultsPane] —
                // the M3 "reveal" pattern: a wider window shows list and detail/map side by side
                // rather than making the user switch between them. Seasonal isn't part of that
                // pairing (it's not a view onto the same sightings), so it stays its own tab.
                when (selectedTab) {
                    ResultsTab.LIST, ResultsTab.MAP -> CombinedResultsPane(
                        uiState = uiState,
                        currentTime = currentTime,
                        distanceUnit = distanceUnit,
                        mapSlot = mapSlot,
                        basemap = basemap,
                        isTopoMode = isTopoMode,
                        onToggleMapMode = { isTopoMode = !isTopoMode },
                        onPlaceTripPin = onPlaceTripPin,
                        onLogFindHere = onLogFindHere,
                        onToggleForagingAreas = onToggleForagingAreas,
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        onDropWaypoint = onDropWaypoint,
                        modifier = Modifier.weight(1f),
                    )
                    ResultsTab.SEASONAL -> SeasonalTab(uiState = uiState, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    // The compact-only Scaffold: full-bleed map, bottom nav instead of a top tab row, and — while
    // the Maps tab's fullscreen icon is toggled on — the top app bar and bottom nav both hidden,
    // leaving only the map and its floating icon stack. See docs/plans/map-redesign.md decisions
    // #2, #4, #5.
    //
    // No top app bar at all any more: the species/category search bar that used to live there
    // moved into the drawer (see [CompactSearchDrawerContent]) per the project owner's own framing
    // — "the whole side panel is the search feature" — and its own tune icon is redundant with the
    // map icon stack's search icon, which opens the identical drawer. [ActiveSearchSummary] is what
    // remains visible up top: a read-only "what am I currently searching" strip, deliberately
    // non-interactive here (`onReopenTaxonSuggestions = {}`) since the field it used to reopen no
    // longer lives where this strip is — its whole point now is answering that question *without*
    // opening the drawer, not being a second way to open it.
    val compactMainScaffold: @Composable () -> Unit = {
        // Reused by both the drawer content's own close-and-search flow and the map icon stack's
        // search icon — decision #3.4: the stack's search icon is a second entry point into this
        // same drawer panel, not a new search feature, so it calls this identical lambda rather
        // than a parallel one.
        val openSearchDrawer = {
            onDismissTaxonSuggestions()
            isDrawerOpen = true
        }

        // The quick species-search panel under ActiveSearchSummary — see that composable's own
        // onOpenQuickSearch doc comment. Local to this scaffold, not AvailabilityUiState: which
        // panel is showing is a display decision the ViewModel has no part in, same reasoning as
        // isTopoMode/drawerPanel above.
        var showQuickSearch by remember { mutableStateOf(false) }

        // Pings the device's live location once, as soon as the compact Maps experience is shown,
        // so the map opens already centred on it rather than waiting for an explicit locate-me tap
        // — see CompactMapTab's own doc comment on the pre-search display region this feeds. Fires
        // once per this scaffold's own composition lifetime (i.e. once per app open on a compact
        // window), not once per Maps-tab visit, since it's hoisted here rather than into
        // CompactMapTab itself, which enters and leaves composition on every bottom-nav switch.
        LaunchedEffect(Unit) { onLocateMe() }

        Scaffold(
            bottomBar = {
                if (!isMapFullscreen) {
                    ForagerBottomNav(
                        selectedTab = compactTab,
                        onTabSelected = { tab ->
                            compactTab = tab
                            // Keep the shared ResultsTab-driven state in sync for the three
                            // destinations both it and CompactTab describe — see compactTab's own
                            // doc comment for why.
                            when (tab) {
                                CompactTab.LIST -> selectedTab = ResultsTab.LIST
                                CompactTab.MAP -> selectedTab = ResultsTab.MAP
                                CompactTab.SEASONAL -> selectedTab = ResultsTab.SEASONAL
                                CompactTab.JOURNAL, CompactTab.SETTINGS -> Unit
                            }
                        },
                    )
                }
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Scaffold's padding carries the system bar insets, so nothing here is laid
                    // out under the status or navigation bar.
                    .padding(padding),
            ) {
                if (!isMapFullscreen) {
                    ActiveSearchSummary(
                        uiState,
                        distanceUnit,
                        onReopenTaxonSuggestions = {},
                        onOpenQuickSearch = { showQuickSearch = !showQuickSearch },
                    )
                    if (showQuickSearch) {
                        QuickSearchPanel(
                            uiState = uiState,
                            onUseCurrentLocation = onUseCurrentLocation,
                            onCategorySelected = onCategorySelected,
                            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                            onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                            onClose = { showQuickSearch = false },
                        )
                    }
                    SearchNotice(uiState)
                }

                // Switches to the Journal tab and starts the entry there — the gallery's own edit
                // form ([JournalTab]'s `editingEntry` branch) is what shows it next, the same
                // "just-created entry opens for editing" behavior the drawer used to give this
                // exact call. No drawer to open any more; Journal is a bottom-nav destination now.
                val onLogFindHere: (LatLng) -> Unit = { location ->
                    compactTab = CompactTab.JOURNAL
                    onStartLogEntry(location, LocalDate.now())
                }

                // weight(1f) states the intent: the results get whatever is left after the
                // wrap-content siblings above (empty in fullscreen, so the map then gets the
                // entire padded area) — see mainScaffold's own doc comment on this same pattern.
                when (compactTab) {
                    CompactTab.LIST -> ListTab(
                        uiState = uiState,
                        currentTime = currentTime,
                        distanceUnit = distanceUnit,
                        modifier = Modifier.weight(1f),
                    )
                    CompactTab.MAP -> CompactMapTab(
                        uiState = uiState,
                        mapSlot = mapSlot,
                        basemap = basemap,
                        isTopoMode = isTopoMode,
                        onToggleMapMode = { isTopoMode = !isTopoMode },
                        onPlaceTripPin = onPlaceTripPin,
                        // Opens straight to the log's edit form for the new entry, bypassing
                        // Search — see DrawerPanel's own doc comment on why Log is reachable
                        // both ways.
                        onLogFindHere = onLogFindHere,
                        isFullscreen = isMapFullscreen,
                        onToggleFullscreen = { isMapFullscreen = !isMapFullscreen },
                        onLocateMe = onLocateMe,
                        onOpenSearchDrawer = openSearchDrawer,
                        isRecording = isRecording,
                        onToggleRecording = onToggleRecording,
                        startRecordingErrorMessage = startRecordingErrorMessage,
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        onDropWaypoint = onDropWaypoint,
                        returnToStart = returnToStart,
                        isReturning = isReturning,
                        isOffTrack = isOffTrack,
                        onToggleReturning = onToggleReturning,
                        compassProvider = compassProvider,
                        modifier = Modifier.weight(1f),
                    )
                    CompactTab.SEASONAL -> SeasonalTab(uiState = uiState, modifier = Modifier.weight(1f))
                    CompactTab.JOURNAL -> JournalTab(
                        uiState = logUiState,
                        cameraCaptureFiles = cameraCaptureFiles,
                        mapSlot = mapSlot,
                        pickerRegion = uiState.region ?: JOURNAL_PICKER_DEFAULT_REGION,
                        basemap = basemap,
                        onOpenEntry = onOpenLogEntry,
                        onCloseEntry = onCloseLogEntry,
                        onStartEntry = onStartLogEntry,
                        onEntryChanged = onLogEntryChanged,
                        onAddPhoto = onAddLogPhoto,
                        onRemovePhoto = onRemoveLogPhoto,
                        onDeleteEntry = onDeleteLogEntry,
                        modifier = Modifier.weight(1f),
                    )
                    CompactTab.SETTINGS -> CompactSettingsTab(
                        uiState = uiState,
                        mapSlot = mapSlot,
                        selectedMapService = selectedMapService,
                        onMapServiceSelected = { selectedMapService = it },
                        distanceUnit = distanceUnit,
                        onDistanceUnitSelected = { distanceUnit = it },
                        onOfflineMapLatChanged = onOfflineMapLatChanged,
                        onOfflineMapLngChanged = onOfflineMapLngChanged,
                        onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                        onDownloadOfflineMaps = onDownloadOfflineMaps,
                        onDeleteOfflineMaps = onDeleteOfflineMaps,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    if (windowWidthClass == WindowWidthClass.COMPACT) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Swipe-to-open is off on purpose: the content behind the drawer is a full-screen
            // pannable map, and a horizontal drag there means "pan", not "open the drawer". The
            // app-bar icon is the way in. Swipe-to-close still works — Material3 enables the drag
            // whenever the drawer is open regardless of this flag.
            gesturesEnabled = false,
            drawerContent = {
                ModalDrawerSheet {
                    CompactSearchDrawerContent(
                        uiState = uiState,
                        distanceUnit = distanceUnit,
                        onClose = { isDrawerOpen = false },
                        onUseCurrentLocation = {
                            isDrawerOpen = false
                            onUseCurrentLocation()
                        },
                        onCategorySelected = onCategorySelected,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        onManualLatChanged = onManualLatChanged,
                        onManualLngChanged = onManualLngChanged,
                        onSearchManualCoordinates = {
                            isDrawerOpen = false
                            onSearchManualCoordinates()
                        },
                        onRadiusChanged = onRadiusChanged,
                        onMonthSelected = onMonthSelected,
                        onDeletePlannedTrip = onDeletePlannedTrip,
                        waypoints = waypoints,
                        onDeleteWaypoint = onDeleteWaypoint,
                        waypointsErrorMessage = waypointsErrorMessage,
                        onRecentSearchSelected = { summary ->
                            isDrawerOpen = false
                            onRecentSearchSelected(summary)
                        },
                        currentTime = currentTime,
                        onToggleForagingAreas = onToggleForagingAreas,
                    )
                }
            },
            content = compactMainScaffold,
        )
    } else {
        // M3's "swapped" adaptive pattern: the same drawer panel, but always on screen and never
        // covering the content — see drawerSheetContent's own doc comment for what's shared.
        // PERMANENT_DRAWER_WIDTH keeps the panel's text at a readable line length rather than
        // stretching it as the window grows past the medium breakpoint.
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet(modifier = Modifier.width(PERMANENT_DRAWER_WIDTH)) {
                    drawerSheetContent(false)
                }
            },
            content = mainScaffold,
        )
    }
}

/**
 * Replaces the compact-only top [SecondaryTabRow] — decision #4 in `docs/plans/map-redesign.md`,
 * extended by the project owner from 3 destinations to [CompactTab]'s 5: List/Maps/Seasonal (the
 * original three), Journal, and Settings, the latter two moved here from the drawer (see
 * [CompactSearchDrawerContent]'s own doc comment). MEDIUM/EXPANDED windows still use
 * [SecondaryTabRow] via [mainScaffold], untouched.
 *
 * Colored entirely from [MaterialTheme.colorScheme] rather than the fixed [Bark]/[Color.White] an
 * earlier revision used — that hardcoding was a real bug, not a style choice: it left this bar the
 * same dark brown regardless of system light/dark theme, while every other surface in the app (and
 * this same bar's own active-tab color, already `MaterialTheme.colorScheme.primary`) switched with
 * it. [NavigationBarItemDefaults.colors]' own defaults already give the unselected/indicator roles
 * sensible theme-following values, so this only overrides `selectedIconColor`/`selectedTextColor`
 * to keep the app's own forest green (`colorScheme.primary` —
 * [com.forager.app.ui.theme.ForestGreen] in light theme, [com.forager.app.ui.theme.MossGreen] in
 * dark, per that theme's own doc comment) as the active-tab accent, matching this file's other
 * hand-picked accents rather than leaving it at M3's default secondary-container tint.
 */
@Composable
private fun ForagerBottomNav(selectedTab: CompactTab, onTabSelected: (CompactTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        CompactTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

/**
 * Width of the always-visible drawer panel on medium+ windows — see [PermanentNavigationDrawer]'s
 * call site in [AvailabilityScreen]. 360dp is M3's standard navigation-drawer width and keeps this
 * panel's text (species chips, radius slider, trip list) around a comfortable line length rather
 * than stretching to fill whatever the window happens to be.
 */
private val PERMANENT_DRAWER_WIDTH = 360.dp

/**
 * List and Map shown together rather than tab-switched — the M3 "reveal" pattern for medium+
 * windows (see [AvailabilityScreen]'s call site). [ListTab] keeps a fixed, readable width so it
 * doesn't stretch as the window grows; [MapTab] takes the rest, same as it does full-bleed at
 * compact width.
 */
@Composable
private fun CombinedResultsPane(
    uiState: AvailabilityUiState,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    basemap: Basemap,
    isTopoMode: Boolean,
    onToggleMapMode: () -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        ListTab(
            uiState = uiState,
            currentTime = currentTime,
            distanceUnit = distanceUnit,
            modifier = Modifier.width(COMBINED_PANE_LIST_WIDTH).fillMaxHeight(),
        )
        VerticalDivider()
        MapTab(
            uiState = uiState,
            mapSlot = mapSlot,
            basemap = basemap,
            isTopoMode = isTopoMode,
            onToggleMapMode = onToggleMapMode,
            onPlaceTripPin = onPlaceTripPin,
            onLogFindHere = onLogFindHere,
            onToggleForagingAreas = onToggleForagingAreas,
            breadcrumbPoints = breadcrumbPoints,
            waypoints = waypoints,
            onDropWaypoint = onDropWaypoint,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Same readable-width reasoning as [PERMANENT_DRAWER_WIDTH]; see [CombinedResultsPane]. */
private val COMBINED_PANE_LIST_WIDTH = 360.dp

/**
 * What the screen is currently showing, in one line — "Fungi · August · 15 km".
 *
 * With the controls behind a drawer the user can no longer read their own filter settings off
 * the screen, so this replaces that. It is deliberately outside the drawer and outside the tab
 * content: it has to be true of both tabs and visible at all times.
 *
 * Also a shortcut back into the species search that produced [AvailabilityUiState.taxonFilter]
 * when that came from a picked result: tapping it calls [onReopenTaxonSuggestions], which
 * restores and re-runs [AvailabilityUiState.lastTaxonSearchQuery] rather than making the user
 * retype it to change species. A no-op tap when nothing was ever searched (the callback itself
 * handles that), so this is unconditionally clickable rather than needing its own enabled state.
 */
@Composable
private fun ActiveSearchSummary(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onReopenTaxonSuggestions: () -> Unit,
    /**
     * Compact-only: opens the quick species-search panel instead of [onReopenTaxonSuggestions]
     * when tapped, and draws a search icon at the far left so the bar reads as tappable-for-search.
     * `null` on the medium/expanded call site, which already shows [SpeciesSearchControls] directly
     * in its app bar — a quick-search panel underneath this bar would be a second, redundant way to
     * do the same thing there.
     */
    onOpenQuickSearch: (() -> Unit)? = null,
) {
    Surface(
        onClick = onOpenQuickSearch ?: onReopenTaxonSuggestions,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
            if (onOpenQuickSearch != null) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = "Quick species search",
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = activeSearchSummary(uiState, distanceUnit),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Compact-only quick species search — [SpeciesSearchControls] itself, in a small panel under
 * [ActiveSearchSummary] rather than behind the full search drawer ("Advanced search" stays where
 * it is, reachable the same way it always was). Closes itself once a result is picked, so tapping
 * the bar, picking a species, and being done reads as one action rather than needing an explicit
 * close step too.
 *
 * [ModalNavigationDrawer] keeps its content composed even while closed (translated off-screen, not
 * removed), so while this panel is open there are briefly two [SpeciesSearchControls] instances in
 * the tree — this one, and the drawer's own closed-and-off-screen copy. Both read the same
 * [AvailabilityUiState.taxonSearchQuery], so they never disagree, and the drawer's copy is neither
 * visible nor reachable while closed — [QUICK_SEARCH_PANEL_TAG] exists so tests can address this
 * one specifically rather than tripping over that duplication.
 */
@Composable
private fun QuickSearchPanel(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.testTag(QUICK_SEARCH_PANEL_TAG)) {
        SpeciesSearchControls(
            uiState = uiState,
            onUseCurrentLocation = onUseCurrentLocation,
            onCategorySelected = onCategorySelected,
            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
            onTaxonSearchResultSelected = { result ->
                onTaxonSearchResultSelected(result)
                onClose()
            },
            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
            chipRowModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm),
        )
    }
}

/** See [QuickSearchPanel]'s own doc comment. */
internal const val QUICK_SEARCH_PANEL_TAG = "quick-search-panel"

private fun activeSearchSummary(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    val where = uiState.region?.let { formatDistanceKm(it.radiusKm, distanceUnit) } ?: "no location set"
    return "${uiState.taxonFilter.label} · $month · $where"
}

/**
 * Failures that come from the drawer's controls or the app bar's search field, surfaced outside
 * both.
 *
 * The coordinate-validation and search-failure messages used to be rendered inside the ranked
 * list, which was the default tab. The Map tab is the default now and the controls that raise
 * these live behind a drawer that closes on search, so without this strip both messages could be
 * raised and never seen (CLAUDE.md: failures are reported, not swallowed). The taxon-search error
 * joined this strip rather than staying inline in [AvailabilitySearchTopBar]: that bar is a fixed
 * two-row sibling above the weighted tab content (see its own doc comment), so an inline error
 * line there would grow the app bar's height exactly when the map's share of the screen is being
 * protected — this strip already exists and already scrolls with nothing beneath it.
 */
@Composable
private fun SearchNotice(uiState: AvailabilityUiState) {
    val message = uiState.errorMessage
        ?: uiState.taxonSearchErrorMessage
        ?: uiState.plannedTripsErrorMessage
        ?: if (uiState.locationPermissionDenied) {
            "Location permission was denied. Open search options and enter coordinates manually."
        } else {
            null
        }
    if (message == null) return

    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )
    }
}

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
private fun BuildIdentityFooter() {
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
 * Gestures are deliberately disabled on this drawer (see the comment on `gesturesEnabled = false`
 * in [AvailabilityScreen]) because the content behind it is a pannable map, so a swipe there has
 * to mean "pan", not "close". That leaves tapping the scrim as the only other way out, which is
 * easy to miss — this gives the drawer its own explicit, discoverable close control.
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
private fun DrawerHeader(onClose: () -> Unit) {
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
private fun MushroomLogEntryRow(onClick: () -> Unit) {
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
private fun SettingsEntryRow(onClick: () -> Unit) {
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
private fun SettingsHeader(onBack: () -> Unit) {
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
 * [CompactTab.SETTINGS]'s body — [SettingsContent] hosted as a bottom-nav tab's own content
 * instead of a drawer panel, with the Offline Maps submenu as local `showOfflineMaps` state instead
 * of [DrawerPanel.OfflineMaps]. Reuses [SettingsContent]/[OfflineMapsPanel]/[OfflineMapsHeader]/
 * [BuildIdentityFooter] unmodified — only the navigation host around them changed, from a drawer
 * panel switch to a tab-local one. No header for the main Settings state, unlike the drawer
 * panel's [SettingsHeader]: there is nothing to go "back" to here — Settings is a top-level
 * destination now, and the bottom nav is how you leave it, the same reason [CompactMapTab]/
 * [ListTab]/[SeasonalTab] have no such header either.
 */
@Composable
private fun CompactSettingsTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    selectedMapService: MapService,
    onMapServiceSelected: (MapService) -> Unit,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onOfflineMapLatChanged: (String) -> Unit,
    onOfflineMapLngChanged: (String) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineMaps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showOfflineMaps by remember { mutableStateOf(false) }

    // Unwinds this tab's own nested submenu before AvailabilityScreen's top-level "switch away
    // from a non-Maps tab" handler ever sees it — same reasoning as JournalTab's own BackHandler.
    BackHandler(enabled = showOfflineMaps) {
        showOfflineMaps = false
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showOfflineMaps) {
            OfflineMapsHeader(onBack = { showOfflineMaps = false })
            OfflineMapsPanel(
                modifier = Modifier.weight(1f),
                uiState = uiState,
                distanceUnit = distanceUnit,
                mapSlot = mapSlot,
                onRegionPicked = { location ->
                    onOfflineMapLatChanged(location.lat.toString())
                    onOfflineMapLngChanged(location.lng.toString())
                },
                onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                onDownloadOfflineMaps = onDownloadOfflineMaps,
                onDeleteOfflineMaps = onDeleteOfflineMaps,
            )
        } else {
            SettingsContent(
                modifier = Modifier.weight(1f),
                selectedMapService = selectedMapService,
                onMapServiceSelected = onMapServiceSelected,
                distanceUnit = distanceUnit,
                onDistanceUnitSelected = onDistanceUnitSelected,
                onOpenOfflineMaps = { showOfflineMaps = true },
            )
            BuildIdentityFooter()
        }
    }
}

/**
 * The Settings panel's body: "Choose Maps Service" plus a row into the "Offline Maps" submenu,
 * built as a real menu with headroom for more sections later per this task's own framing.
 *
 * Offline Maps is a full submenu of its own ([DrawerPanel.OfflineMaps] for medium/expanded,
 * `showOfflineMaps` local state for compact's [CompactSettingsTab]) rather than a section inline
 * here, because it now holds an interactive map (see [OfflineMapsPanel]) that needs real screen
 * space to be usable — a map squeezed into one scrolling section among several would be too small
 * to long-press accurately.
 *
 * Scrolls for the same reason [SearchControls] does — a drawer sheet is a fixed-height container,
 * so a tall stack of controls needs its own scroll rather than relying on the sheet to grow.
 */
@Composable
private fun SettingsContent(
    modifier: Modifier = Modifier,
    selectedMapService: MapService,
    onMapServiceSelected: (MapService) -> Unit,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onOpenOfflineMaps: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ChooseMapsServiceSection(selectedMapService = selectedMapService, onMapServiceSelected = onMapServiceSelected)
        HorizontalDivider()
        DistanceUnitSection(distanceUnit = distanceUnit, onDistanceUnitSelected = onDistanceUnitSelected)
        HorizontalDivider()
        OfflineMapsEntryRow(onClick = onOpenOfflineMaps)
    }
}

/**
 * Kilometers or miles for every distance this app displays (search radius, offline-download
 * radius, recent-search cards) — see [DistanceUnit]'s own doc comment for why this is a display
 * preference only, never a change to what's actually searched or downloaded.
 */
@Composable
private fun DistanceUnitSection(distanceUnit: DistanceUnit, onDistanceUnitSelected: (DistanceUnit) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Distance Unit", style = MaterialTheme.typography.titleMedium)
        DistanceUnit.entries.forEach { unit ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onDistanceUnitSelected(unit) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(selected = unit == distanceUnit, onClick = { onDistanceUnitSelected(unit) })
                Text(unit.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/**
 * Which tile provider the map draws from — occasional, unlike the topo/regular mode toggled from
 * the map itself (see [MapModeToggle]), which is why this lives in Settings rather than as a
 * quick-fire control. A plain two-option radio choice rather than restating each service's four
 * [Basemap]s individually: [MapService] already is that grouping, and PR #13's old selector rows
 * — one per [Basemap], with its own coverage/zoom/attribution block — are no longer what a user
 * picks between here. The detail those rows carried survives as a short caption per service
 * ([mapServiceCaption]) rather than being dropped outright.
 */
@Composable
private fun ChooseMapsServiceSection(selectedMapService: MapService, onMapServiceSelected: (MapService) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Choose Maps Service", style = MaterialTheme.typography.titleMedium)
        MapService.entries.forEach { service ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onMapServiceSelected(service) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                RadioButton(selected = service == selectedMapService, onClick = { onMapServiceSelected(service) })
                Column {
                    Text(service.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        mapServiceCaption(service),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** The coverage-and-zoom summary [ChooseMapsServiceSection] shows under each [MapService] option. */
private fun mapServiceCaption(service: MapService): String {
    val coverage = if (service.topoBasemap.coverage == BasemapCoverage.WORLDWIDE) "Worldwide" else "United States only"
    return "$coverage · zooms to ${service.topoBasemap.maxZoom} (topo) / ${service.regularBasemap.maxZoom} (regular)"
}

/**
 * The Settings panel's row into the [DrawerPanel.OfflineMaps] submenu. A plain row rather than a
 * sticky one like [SettingsEntryRow]: this lives inside Settings' own scrolling content, it isn't a
 * second drawer-wide sticky slot.
 *
 * Unconditionally reachable regardless of [MapService] — offline downloads always target this
 * repository's one fixed source regardless of which service is selected for live browsing, so
 * there is nothing to gate this row on; see `com.forager.app.domain.OfflineMapRepository`'s doc
 * comment for why that coupling was removed.
 */
@Composable
private fun OfflineMapsEntryRow(onClick: () -> Unit) {
    // heightIn(min = 48.dp), not just the vertical padding [MushroomLogEntryRow]/[SettingsEntryRow]
    // use: those rows' padding(vertical = Spacing.md) around a 24dp icon already clears 48dp, but
    // this row's original padding(vertical = Spacing.sm) only reached ~40dp — under M3's 48x48dp
    // minimum touch target, a real miss found by auditing this file's tap targets against that
    // rule, not a hypothetical one.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Offline Maps", style = MaterialTheme.typography.titleMedium)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

/** Mirrors [SettingsHeader]'s style; its back arrow returns to Settings, one level up, not to Search. */
@Composable
private fun OfflineMapsHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onBack)
            .padding(horizontal = Spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Settings")
        Text("Offline Maps", style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The "Offline Maps" submenu: an interactive topo map to pick a download region by long-press, the
 * region's radius, current status, and the Download/Delete actions.
 *
 * Always downloads from the same one fixed source, unconditionally — see
 * `com.forager.app.domain.OfflineMapRepository`'s doc comment for why this is no longer gated on,
 * or reactive to, [MapService]/the quick-fire map mode: the project owner's own call was that
 * offline downloads should "assume [a fixed source] and [be] ready to function" regardless of
 * either. That fixed source is `com.forager.app.map.MapLibreOfflineMapRepository`'s Cloudflare
 * Worker now, not USGS — this panel's own picker map below is unrelated to that choice, see the
 * next paragraph.
 *
 * ## Picking a region by long-press instead of typing coordinates
 *
 * Reuses [MapSlot]/`SightingsMap` rather than a new map composable — empty `sightings`/`areas`/
 * `plannedTrips`, [Basemap.USGS_TOPO] always, and [onRegionPicked] wired to the same
 * `onLongPress` mechanism [MapTab] already uses for planning a trip, except there is no
 * name-and-date dialog in between: a picked point becomes the region's center immediately, since
 * there is nothing else to ask the user for. `SightingsMap` already draws a center marker whose
 * snippet states the radius, so nothing new needs to be drawn for feedback — see that composable's
 * own doc comment. [Basemap.USGS_TOPO] here is only terrain context for choosing *where* to
 * download — this picker map is still osmdroid/live-browsing-shaped, unrelated to which source the
 * download itself actually reads from underneath.
 *
 * Before anything is long-pressed, [uiState]'s `offlineMapLatText`/`offlineMapLngText` are blank,
 * so the map centres on [OFFLINE_MAP_PICKER_DEFAULT_CENTER] purely so there is a map to navigate
 * and long-press on — not a claim about where the user is or wants to download. "Download Maps"
 * stays disabled until a real point has been picked (see `hasValidRegion` below), so that default
 * viewport can never itself be submitted as a region.
 *
 * The map is weighted to fill the space the fixed controls below it don't need, the same
 * map-gets-the-remainder pattern [MapTab] already uses, rather than a fixed dp height: a picker map
 * too small to long-press accurately would defeat the reason this replaced the lat/lng text fields.
 */
@Composable
private fun OfflineMapsPanel(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    mapSlot: MapSlot,
    onRegionPicked: (LatLng) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineMaps: () -> Unit,
) {
    val pickedLat = uiState.offlineMapLatText.toDoubleOrNull()
    val pickedLng = uiState.offlineMapLngText.toDoubleOrNull()
    val hasValidRegion = pickedLat != null && pickedLat in -90.0..90.0 && pickedLng != null && pickedLng in -180.0..180.0

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Offline downloads cover the continental United States with vector map data. " +
                "Long-press the map below to choose where to download.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        val pickerRegion = Region(
            lat = pickedLat ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER.lat,
            lng = pickedLng ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER.lng,
            radiusKm = uiState.offlineMapRadiusKm,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            mapSlot(
                pickerRegion,
                MapOverlayContent(),
                Basemap.USGS_TOPO,
                null,
                onRegionPicked,
                {},
                Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                if (hasValidRegion) {
                    "Selected: ${"%.4f".format(pickedLat)}, ${"%.4f".format(pickedLng)}"
                } else {
                    "No location picked yet — long-press the map above."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Radius: ${formatDistanceKm(uiState.offlineMapRadiusKm, distanceUnit)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = uiState.offlineMapRadiusKm.toFloat(),
                onValueChange = { onOfflineMapRadiusChanged(it.toInt()) },
                valueRange = Region.MIN_RADIUS_KM.toFloat()..Region.MAX_RADIUS_KM.toFloat(),
                steps = Region.MAX_RADIUS_KM - Region.MIN_RADIUS_KM - 1,
            )

            OfflineMapStatusContent(uiState.offlineMapStatus, distanceUnit)

            val isDownloading = uiState.offlineMapStatus is OfflineMapStatus.Downloading
            val isDownloaded = uiState.offlineMapStatus is OfflineMapStatus.Downloaded
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Button(
                    onClick = onDownloadOfflineMaps,
                    enabled = hasValidRegion && !isDownloading,
                    modifier = Modifier.weight(1f),
                ) { Text("Download Maps") }
                OutlinedButton(
                    onClick = onDeleteOfflineMaps,
                    enabled = isDownloaded && !isDownloading,
                    modifier = Modifier.weight(1f),
                ) { Text("Delete Offline Maps") }
            }
        }
    }
}

/**
 * An arbitrary opening viewport for [OfflineMapsPanel]'s picker map before anything has been
 * long-pressed — the geographic center of the contiguous United States (near Lebanon, Kansas),
 * since offline downloads only ever cover the continental-US PMTiles archive
 * `com.forager.app.map.MapLibreOfflineMapRepository` reads from. Not a default region and never
 * submitted as one: "Download Maps" stays disabled until a real long-press sets a region.
 */
private val OFFLINE_MAP_PICKER_DEFAULT_CENTER = LatLng(39.8283, -98.5795)

/**
 * [JournalTab]'s location-picker fallback viewport, for whenever no region has ever been searched
 * — reuses [OFFLINE_MAP_PICKER_DEFAULT_CENTER] rather than inventing a second "nowhere in
 * particular to start from" default. `radiusKm` here only sets the picker's opening zoom level
 * ([SightingsMap] derives zoom from it); it is never submitted anywhere, the same way the offline
 * picker's own default centre never is.
 */
private val JOURNAL_PICKER_DEFAULT_REGION =
    Region(lat = OFFLINE_MAP_PICKER_DEFAULT_CENTER.lat, lng = OFFLINE_MAP_PICKER_DEFAULT_CENTER.lng, radiusKm = 15)

/** What [OfflineMapsPanel] shows for each [OfflineMapStatus] — every branch says something, per CLAUDE.md. */
@Composable
private fun OfflineMapStatusContent(status: OfflineMapStatus, distanceUnit: DistanceUnit) {
    when (status) {
        OfflineMapStatus.NotDownloaded -> Text(
            "No offline region downloaded yet.",
            style = MaterialTheme.typography.bodySmall,
        )

        is OfflineMapStatus.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (status.total > 0) {
                LinearProgressIndicator(
                    progress = { status.downloaded.toFloat() / status.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${status.downloaded} / ${status.total} tiles", style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Starting download…", style = MaterialTheme.typography.bodySmall)
            }
        }

        is OfflineMapStatus.Downloaded -> Text(
            "Downloaded: ${formatDistanceKm(status.region.radiusKm, distanceUnit)} around " +
                "${"%.4f".format(status.region.lat)}, ${"%.4f".format(status.region.lng)} — " +
                "${status.tileCount} tiles, ${"%.1f".format(status.sizeBytes / 1_000_000.0)} MB. " +
                "Ready to zoom 15: zoom 10–14 from the archive, zoom 15 detail fetched live from " +
                "Protomaps at download time — a download that reports as finished has both, since a " +
                "zoom-15 fetch failure fails the download rather than silently completing without it.",
            style = MaterialTheme.typography.bodySmall,
        )

        is OfflineMapStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * The quick-fire icon overlaid on the map's own top-right corner — not the app bar, not Settings —
 * because which mode a service is in ("if a map has two modes, toggle the two") is a during-the-walk
 * decision made often, unlike which [MapService] to use at all, which is occasional and lives in
 * Settings. See [MapService]'s doc comment and [MapTab]'s call site for the full picture.
 */
@Composable
private fun MapModeToggle(isTopoMode: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    // size(MIN_TOUCH_TARGET) rather than sizing this off the icon-plus-padding it draws: the icon
    // is 24dp and Spacing.sm padding is 8dp a side, which wrapped to a 40dp circle — under M3's
    // 48x48dp minimum touch target, a real miss found by auditing this file's tap targets against
    // that rule, not a hypothetical one.
    Surface(
        onClick = onToggle,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp,
        modifier = modifier.size(MIN_TOUCH_TARGET),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Filled.Layers,
                contentDescription = if (isTopoMode) {
                    "Showing topo mode. Switch to regular mode."
                } else {
                    "Showing regular mode. Switch to topo mode."
                },
            )
        }
    }
}

/** M3's minimum touch target size — see [MapModeToggle]'s doc comment for where this was missed. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * The compact drawer's entire content — "the whole side panel is the search feature," per the
 * project owner's own framing once the redesign's icon stack gave the Maps tab a second entry
 * point into this same drawer. Three things moved here from where they used to live once that
 * framing landed:
 *
 * 1. **Species/category search** ([SpeciesSearchControls]) — used to be [AvailabilitySearchTopBar],
 *    a bar of its own above the (now-removed, see [ForagerBottomNav]) compact top tab row. With
 *    the whole drawer now dedicated to search, keeping a second, separate search surface in the
 *    app bar was redundant with the icon stack's own search icon, which opens this exact drawer —
 *    so that bar (and its own "Advanced search options" tune icon) is gone for compact, and this
 *    is the one place species/category search lives now. Fixed at the top, not scrolling with
 *    [SearchControls] below it: it's the control reached for on nearly every search, the same
 *    reason it was promoted out of a drawer section and into the app bar in the first place.
 * 2. **[SearchControls]** itself, reused unmodified — Recent searches / Advanced search / Trip
 *    Planner, identical to what medium/expanded's drawer shows.
 * 3. **Foraging areas** ([ForagingAreasToggle]/[ForagingAreasPanel]) — used to float as an overlay
 *    on the map itself ([CompactMapTab]'s own doc comment has that history), then moved here per
 *    the project owner's later call: "move the foraging areas to the side search panel." Fixed at
 *    the bottom, not scrolling with [SearchControls]: [ForagingAreasPanel]'s own
 *    [FORAGING_AREAS_PANEL_MAX_HEIGHT] cap already bounds it to a footnote-sized block, the same
 *    fixed-height treatment [MapTab] gave it before this redesign, just relocated.
 *
 * [DrawerHeader] stays the one visible way to close this drawer, same as before. No sticky
 * Settings/Log rows any more — both left the drawer entirely for the bottom nav (decision made
 * alongside the above), so this content is Search and only Search now, matching its new sole job.
 */
@Composable
private fun CompactSearchDrawerContent(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onClose: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    waypoints: List<Waypoint>,
    onDeleteWaypoint: (String) -> Unit,
    waypointsErrorMessage: String?,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    currentTime: CurrentTimeProvider,
    onToggleForagingAreas: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DrawerHeader(onClose = onClose)
        SpeciesSearchControls(
            uiState = uiState,
            onUseCurrentLocation = onUseCurrentLocation,
            onCategorySelected = onCategorySelected,
            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
            onTaxonSearchResultSelected = onTaxonSearchResultSelected,
            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
            chipRowModifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg),
        )
        HorizontalDivider()
        SearchControls(
            modifier = Modifier.weight(1f),
            uiState = uiState,
            distanceUnit = distanceUnit,
            onUseCurrentLocation = onUseCurrentLocation,
            onManualLatChanged = onManualLatChanged,
            onManualLngChanged = onManualLngChanged,
            onSearchManualCoordinates = onSearchManualCoordinates,
            onRadiusChanged = onRadiusChanged,
            onMonthSelected = onMonthSelected,
            onDeletePlannedTrip = onDeletePlannedTrip,
            waypoints = waypoints,
            onDeleteWaypoint = onDeleteWaypoint,
            waypointsErrorMessage = waypointsErrorMessage,
            onRecentSearchSelected = onRecentSearchSelected,
            currentTime = currentTime,
        )
        HorizontalDivider()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            ForagingAreasToggle(checked = uiState.showForagingAreas, onCheckedChange = onToggleForagingAreas)
            if (uiState.showForagingAreas) {
                Box(modifier = Modifier.height(FORAGING_AREAS_PANEL_MAX_HEIGHT)) {
                    ForagingAreasPanel(foragingAreas = uiState.foragingAreas)
                }
            }
        }
    }
}

/**
 * Everything set far less than once per search, as three independently collapsible sections:
 * **Recent searches** (the offline cache's picker), **Advanced search** (location, radius, month)
 * and **Trip Planner** (rain-driven trip windows plus the planned-trips list). Each is a single
 * tappable header row when collapsed and expands on tap — see [CollapsibleSection] — rather than a
 * flat stack, per the user's own framing of this drawer ("single line until you tap it, then it
 * drops down").
 *
 * Shared by both window classes' drawers, unlike most of this file's compact-vs-medium/expanded
 * split: [AvailabilitySearchTopBar] hosts species/category search above these three sections for
 * medium/expanded, while [CompactSearchDrawerContent] hosts the identical
 * [SpeciesSearchControls]/foraging-areas pieces around this same composable for compact — see that
 * composable's own doc comment for why species search and foraging areas moved there instead.
 *
 * The scroll modifier on the outer [Column] is not optional. This is the same tall stack of
 * controls that starved the map when it lived in the main column; a drawer sheet is a
 * fixed-height container too, so without it the later controls would simply be unreachable on a
 * short screen or at a large font scale. Collapsing all sections by default shortens that stack
 * further, but doesn't remove the need for scroll — a large font scale with all sections expanded
 * still needs it.
 */
@Composable
private fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    waypoints: List<Waypoint>,
    onDeleteWaypoint: (String) -> Unit,
    waypointsErrorMessage: String?,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    currentTime: CurrentTimeProvider,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        // First in the column, and a section of its own rather than a control inside "Advanced
        // search". Two reasons, both about what this list is: one tap on an entry here *is* a
        // whole search, so burying it under a header about the individual pieces of a search would
        // put the shortest route to results two taps deeper than the long route; and it is the
        // only control in this drawer that still does something useful with no connection, which
        // is exactly when nobody wants to go hunting for it. It keeps the drawer's established
        // one-line-until-tapped behaviour rather than being the one section that starts expanded.
        CollapsibleSection(title = "Recent searches") {
            RecentSearchesSection(
                recentSearches = uiState.recentSearches,
                currentTime = currentTime,
                distanceUnit = distanceUnit,
                onRecentSearchSelected = onRecentSearchSelected,
            )
        }
        HorizontalDivider()
        CollapsibleSection(title = "Advanced search") {
            RegionControls(
                uiState = uiState,
                distanceUnit = distanceUnit,
                onUseCurrentLocation = onUseCurrentLocation,
                onManualLatChanged = onManualLatChanged,
                onManualLngChanged = onManualLngChanged,
                onSearchManualCoordinates = onSearchManualCoordinates,
                onRadiusChanged = onRadiusChanged,
            )
            HorizontalDivider()
            MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)
        }
        HorizontalDivider()
        CollapsibleSection(title = "Trip Planner") {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
        }
        HorizontalDivider()
        CollapsibleSection(title = "Waypoints") {
            WaypointsSection(waypoints = waypoints, onDeleteWaypoint = onDeleteWaypoint, waypointsErrorMessage = waypointsErrorMessage)
        }
    }
}

/**
 * A single tappable header row that expands on tap to reveal [content], collapsing back on a
 * second tap. Collapsed by default: both drawer sections start as one line, per the user's
 * request, rather than remembering whichever state they were last left in.
 *
 * Lives in this file rather than becoming a generic shared component, because both call sites
 * (see [SearchControls]) are this drawer's two sections and nothing else in the app has asked for
 * this pattern yet — CLAUDE.md: no speculative generality ahead of a second real caller.
 */
@Composable
// internal rather than private: com.forager.app.ui.log's own drawer panel reuses this exact
// "tap header to expand" shape for its record sections, rather than re-implementing the same
// molecule a second time.
internal fun CollapsibleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                content()
            }
        }
    }
}

/**
 * The offline cache's recent searches, most recently used first, each re-runnable in one tap.
 *
 * The list is short by construction — the cache keeps five (see
 * [RoomSearchCacheRepository][com.forager.app.data.repository.RoomSearchCacheRepository]) — so
 * these are plain [Column] children inside the drawer's existing scroll rather than a nested
 * [LazyColumn], which inside a scrolling parent would need a height of its own and would scroll
 * independently of everything around it.
 *
 * Tapping an entry does **not** mean "show me the saved copy": it re-runs the search through the
 * ordinary flow, which tries live first — see
 * [AvailabilityViewModel.onRecentSearchSelected][com.forager.app.ui.availability.AvailabilityViewModel.onRecentSearchSelected].
 *
 * An empty list says so rather than rendering nothing, the same way [PlannedTripsList] does: a
 * section that expands to blank space is indistinguishable from one that failed to load.
 */
@Composable
private fun RecentSearchesSection(
    recentSearches: List<CachedSearchSummary>,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (recentSearches.isEmpty()) {
            Text(
                "No searches saved yet. Each search you run is saved here, and the last five can " +
                    "be reopened without a connection.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            // Read once for the whole list rather than per row, so every "cached ..." label in one
            // rendering is measured from the same instant.
            val now = currentTime.nowEpochMillis()
            recentSearches.forEach { summary ->
                RecentSearchRow(
                    summary = summary,
                    nowEpochMillis = now,
                    distanceUnit = distanceUnit,
                    onClick = { onRecentSearchSelected(summary) },
                )
            }
        }
    }
}

/** One recent search: what was searched for, where and when, and how old the saved copy is. */
@Composable
private fun RecentSearchRow(
    summary: CachedSearchSummary,
    nowEpochMillis: Long,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // The whole card is the target, not a button inside it: the row is one action, and
                // a small control in a card the user has already aimed at is a smaller target for
                // no reason — the same widening [DrawerHeader] does for its close affordance.
                .clickable(role = Role.Button, onClick = onClick)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            val month = Month.of(summary.month).getDisplayName(TextStyle.FULL, Locale.getDefault())
            Text("${summary.filter.label} · $month", style = MaterialTheme.typography.titleSmall)
            Text(
                "${"%.4f".format(summary.region.lat)}, ${"%.4f".format(summary.region.lng)} · " +
                    formatDistanceKm(summary.region.radiusKm, distanceUnit),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "cached ${relativeTimeLabel(summary.cachedAtEpochMillis, nowEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RegionControls(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = onUseCurrentLocation, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(Spacing.sm))
            Text("Use current location")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = uiState.manualLatText,
                onValueChange = onManualLatChanged,
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.manualLngText,
                onValueChange = onManualLngChanged,
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }
        OutlinedButton(onClick = onSearchManualCoordinates, modifier = Modifier.fillMaxWidth()) {
            Text("Search this location")
        }

        Text("Search radius: ${formatDistanceKm(uiState.radiusKm, distanceUnit)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = uiState.radiusKm.toFloat(),
            onValueChange = { onRadiusChanged(it.toInt()) },
            valueRange = 1f..50f,
            steps = 48,
        )
    }
}

/**
 * The app bar: species/category search in place of a static "Forager" title, because it's the
 * control used on nearly every search rather than once a session — see [AvailabilityScreen]'s doc
 * comment. This was the most-buried control in the previous layout, at the same depth as location
 * and radius; putting it in the app bar itself is one step further than the prior promotion out
 * of the drawer, per the user's explicit request.
 *
 * **A deliberate two-row bar, not Material3's single-row [androidx.compose.material3.TopAppBar].**
 * That component's title slot is sized for a line of text; fitting category chips *and* a text
 * field into it would either clip one of them or force the chips and the field onto the same row,
 * where neither has enough width to be usable next to a navigation icon. A taller, custom bar is
 * the defensible choice here — flagged rather than picked silently, per CLAUDE.md — and its cost
 * is a fixed, known quantity: two rows have a fixed height regardless of query length or category
 * count, so [AvailabilityScreenLayoutTest]'s `MIN_MAP_SHARE_OF_SCREEN` floor is a one-time,
 * re-measurable cost rather than one that grows with what the user types.
 *
 * The category row scrolls rather than wraps for the same reason it did inside the old
 * [Row]-in-a-drawer version: "Lichens (approx.)" is long enough to threaten clipping in a
 * non-scrolling row on a narrow phone, and scrolling keeps the row's height fixed at one chip
 * tall regardless of device width or category count.
 *
 * The suggestion list is the one part of the old species bar whose height wasn't fixed by its own
 * content — it grew with however many matches the search returned, which is exactly the kind of
 * wrap-content sibling this bar cannot afford to have. [ExposedDropdownMenuBox] (the same
 * mechanism [MonthSelector] already uses) renders it as a popup anchored below the text field
 * instead of a layout child, so a long result list draws over the map rather than pushing this
 * bar — and everything below it — taller.
 *
 * The location icon is the trailing icon of the species text field, at the field's far right —
 * a shortcut to the same "Use current location" action the drawer's [RegionControls] already has
 * ([onUseCurrentLocation] is the identical callback, not a second location-fetch path), placed
 * here because location is, with species/category, the other control reached for on nearly every
 * search. It uses [Icons.Filled.MyLocation] rather than the drawer button's
 * [Icons.Filled.LocationOn] so the two don't read as the same icon in two places. It used to sit
 * at the end of the category-chip row instead, which crowded that row's scrollable chips against
 * a fixed icon at a variable-width boundary; the text field's trailing-icon slot is a stable,
 * purpose-built spot for exactly this kind of field-scoped action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AvailabilitySearchTopBar(
    uiState: AvailabilityUiState,
    onOpenDrawer: () -> Unit,
    onUseCurrentLocation: () -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(Icons.Filled.Tune, contentDescription = "Advanced search options")
                }
                Box(modifier = Modifier.weight(1f)) {
                    SpeciesSearchControls(
                        uiState = uiState,
                        onUseCurrentLocation = onUseCurrentLocation,
                        onCategorySelected = onCategorySelected,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        chipRowModifier = Modifier,
                    )
                }
            }
        }
    }
}

/**
 * The species/category search controls themselves — the category chip row plus the species text
 * field and its suggestion dropdown — factored out of [AvailabilitySearchTopBar] so
 * [CompactSearchDrawerContent] can host the identical control inside the drawer instead of the app
 * bar (per the project owner's own framing: "the whole side panel is the search feature"), rather
 * than a second copy of the [ExposedDropdownMenuBox]/chip-row logic. [AvailabilitySearchTopBar]'s
 * own external shape (the Surface, the tune icon, the two-row layout) is unchanged by this
 * extraction — only where the chips+field piece itself is called from moved.
 *
 * The oddly-placed [chipRowModifier] parameter is the one real difference between the two call
 * sites: the app bar puts the chip row in a shared `Row` alongside the tune icon
 * (`Modifier.weight(1f)` applied by the caller around this whole composable there), while the
 * drawer has no icon to share a row with, so its chip row can simply fill the width itself.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSearchControls(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onCategorySelected: (TaxonFilter) -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    chipRowModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(
            modifier = chipRowModifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            TaxonFilter.DEFAULT_CATEGORIES.forEach { category ->
                FilterChip(
                    selected = uiState.taxonFilter == category,
                    onClick = { onCategorySelected(category) },
                    label = { Text(category.label) },
                )
            }
        }

        val suggestionsOpen = uiState.taxonSearchResults.isNotEmpty()
        ExposedDropdownMenuBox(
            expanded = suggestionsOpen,
            onExpandedChange = {},
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            OutlinedTextField(
                value = uiState.taxonSearchQuery,
                onValueChange = onTaxonSearchQueryChanged,
                placeholder = {
                    Text("Or search a species", maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isSearchingTaxa) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                        IconButton(onClick = onUseCurrentLocation) {
                            Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
                        }
                    }
                },
            )
            // A no-op onDismissRequest here before this fix meant the standard "tap outside
            // the popup" dismissal ExposedDropdownMenu already implements never actually
            // closed anything — the only way to get rid of the list was to pick a result or
            // clear the query back below MIN_QUERY_LENGTH. Wiring the real dismiss action in
            // is the fix, not new behavior invented on top of the component.
            ExposedDropdownMenu(expanded = suggestionsOpen, onDismissRequest = onDismissTaxonSuggestions) {
                uiState.taxonSearchResults.forEach { result ->
                    DropdownMenuItem(
                        text = { TaxonSuggestionContent(result) },
                        onClick = { onTaxonSearchResultSelected(result) },
                    )
                }
            }
        }
    }
}

/** One suggestion's content inside a [DropdownMenuItem], which supplies the click and padding. */
@Composable
private fun TaxonSuggestionContent(result: TaxonSearchResult) {
    Column {
        Text(result.commonName ?: result.scientificName, style = MaterialTheme.typography.bodyMedium)
        val subtitle = result.scientificName + (result.iconicTaxonName?.let { " · $it" } ?: "")
        Text(subtitle, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthSelector(selectedMonth: Int, onMonthSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val monthName = Month.of(selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = monthName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Month") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..12).forEach { month ->
                DropdownMenuItem(
                    text = { Text(Month.of(month).getDisplayName(TextStyle.FULL, Locale.getDefault())) },
                    onClick = {
                        onMonthSelected(month)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * The ranked list, with the conditions card above it.
 *
 * The card sits here rather than in the drawer or over the map because rainfall is context for
 * the ranking and belongs next to it — but *next to*, not fused into it: it keeps its own card,
 * its own heading, and says nothing about the ranking below. See [ConditionsCard].
 *
 * Trip windows used to be shown here too; they now live in the drawer's Trip Planner section —
 * see [TripPlannerSection] — because "what's likely nearby this month" and "when in the next few
 * days is worth going" are different questions with different lifetimes (the ranking depends on
 * the browsed month, trip windows only on the next several days), and fusing them into one
 * scrolling column was one more step to reach whichever one wasn't currently showing.
 */
@Composable
private fun ListTab(
    uiState: AvailabilityUiState,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        // Above the conditions card and the ranking alike: it changes what everything below it
        // means, so it cannot be something the user meets after reading the list.
        if (uiState.isShowingCachedResults) {
            OfflineResultsBanner(
                cachedAtEpochMillis = uiState.cachedResultsAsOfEpochMillis,
                nowEpochMillis = currentTime.nowEpochMillis(),
            )
        }
        when {
            uiState.conditionsErrorMessage != null -> Text(
                uiState.conditionsErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            uiState.conditions != null -> ConditionsCard(conditions = uiState.conditions)
        }
        // Weighted for the same reason the tab content is: the ranked list scrolls, so it needs a
        // bounded height rather than whatever the card above it happens to leave.
        ResultsSection(uiState = uiState, distanceUnit = distanceUnit, modifier = Modifier.weight(1f))
    }
}

/**
 * Says out loud that the ranking below came out of the offline cache rather than off the network,
 * and how old it is.
 *
 * **Not optional polish.** CLAUDE.md requires a partial or fallback result to be reported as such
 * and never presented as a success; a cached ranking rendered identically to a live one is exactly
 * that failure, and the user would have no way to tell that iNaturalist was never reached.
 *
 * Tertiary rather than the error palette, for the same reason the visiting-order disclaimer is
 * (see [ForagingAreasPanel]): nothing failed in a way that cost the user their answer — the answer
 * is right there, it is simply older than it looks. Reusing the error color would make a real
 * failure read as no more urgent than this.
 *
 * [cachedAtEpochMillis] is non-null in every state the ViewModel produces (both fields are written
 * from one `Cached` result), but a null is rendered as an explicit "when isn't known" rather than
 * being hidden or filled in with a guess — a banner that invented an age would undo the honesty it
 * exists for.
 */
@Composable
private fun OfflineResultsBanner(cachedAtEpochMillis: Long?, nowEpochMillis: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                if (cachedAtEpochMillis == null) {
                    "Offline — showing saved results; when they were saved isn't known."
                } else {
                    "Offline — showing results saved ${relativeTimeLabel(cachedAtEpochMillis, nowEpochMillis)}"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                "iNaturalist couldn't be reached, so this is the last ranking saved for this " +
                    "region, month and category.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * How long ago [thenEpochMillis] was, in the coarsest unit that still says something useful —
 * "just now", "5 minutes ago", "3 hours ago", "2 days ago".
 *
 * Coarse on purpose: this labels a cached search, and the difference between 181 and 184 minutes
 * changes nothing about whether somebody wants to re-run it. Both callers pass a clock-provided
 * [nowEpochMillis] rather than reading [System.currentTimeMillis] here, so the output is a pure
 * function of its arguments and can be asserted on directly — see [CurrentTimeProvider].
 *
 * A [thenEpochMillis] in the future (a device clock moved backwards, or a row written under a
 * clock that was ahead) reads as "just now" rather than as a negative age. It is not a state this
 * app can produce on its own, and inventing a phrase for it would be a claim about a clock this
 * code cannot check.
 */
internal fun relativeTimeLabel(thenEpochMillis: Long, nowEpochMillis: Long): String {
    val elapsedMillis = nowEpochMillis - thenEpochMillis
    val minutes = elapsedMillis / MILLIS_PER_MINUTE
    val hours = elapsedMillis / MILLIS_PER_HOUR
    val days = elapsedMillis / MILLIS_PER_DAY
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes ${plural(minutes, "minute")} ago"
        hours < 24 -> "$hours ${plural(hours, "hour")} ago"
        else -> "$days ${plural(days, "day")} ago"
    }
}

private fun plural(count: Long, singular: String) = if (count == 1L) singular else "${singular}s"

private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 60 * MILLIS_PER_MINUTE
private const val MILLIS_PER_DAY = 24 * MILLIS_PER_HOUR

@Composable
private fun ResultsSection(uiState: AvailabilityUiState, distanceUnit: DistanceUnit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        when {
            uiState.isLoading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }

            !uiState.hasSearched -> Text(
                "Choose a region to see what's historically been found nearby this month.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null && uiState.forecast.entries.isEmpty() -> Text(
                "No verifiable observations of ${uiState.forecast.filter.label} found for this region and month. " +
                    "Try a wider radius or a different category.",
                style = MaterialTheme.typography.bodyMedium,
            )

            uiState.forecast != null -> {
                val forecast = uiState.forecast
                Text(
                    "Based on ${forecast.totalObservationsConsidered} historical iNaturalist observations " +
                        "of ${forecast.filter.label} within ${formatDistanceKm(forecast.region.radiusKm, distanceUnit)} for " +
                        Month.of(forecast.month).getDisplayName(TextStyle.FULL, Locale.getDefault()) + ".",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                )
                Spacer(Modifier.height(Spacing.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(forecast.entries, key = { it.species.taxonId }) { entry ->
                        SpeciesRow(entry)
                    }
                }
            }
        }
    }
}

/**
 * The Seasonal tab: tests [FruitingPatternAssumptions.FRUITING_LAG_DAYS] — the 7–21 day
 * rain-to-fruiting-lag rule of thumb [TripWindowsCard] and [ForagingWeatherGuidanceSection]
 * already state as unmeasured field lore — against real historical iNaturalist sightings and real
 * historical Open-Meteo rainfall for the current search, and reports what it finds.
 *
 * **This tab does not feed [AvailabilityEntry.relativeLikelihood] or the ranked List tab.** It
 * answers one narrow question — does the data support this one named lag range — and nothing here
 * changes how species are ranked. See [FruitingLagDistribution]'s own doc comment.
 *
 * Fetched lazily, keyed on region+month+filter, the same pattern [MapTab] already uses for
 * sightings — see [AvailabilityViewModel.onSeasonalTabSelected].
 */
@Composable
private fun SeasonalTab(uiState: AvailabilityUiState, modifier: Modifier = Modifier) {
    // Unlike List/Map, Seasonal isn't paired into CombinedResultsPane (see AvailabilityScreen's
    // doc comment on why), so at medium+ width it's the one tab content still stretching to the
    // window's full remaining width — a rule-of-thumb paragraph at 900+dp is well past M3's
    // ~40-60-character comfortable reading width. Centering the column and capping it at
    // READABLE_CONTENT_MAX_WIDTH is that constraint; COMPACT keeps fillMaxWidth exactly as
    // before, since a phone-width screen never approaches that cap anyway.
    val windowWidthClass = currentWindowWidthClass()
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(
                    if (windowWidthClass == WindowWidthClass.COMPACT) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier.widthIn(max = READABLE_CONTENT_MAX_WIDTH).fillMaxWidth()
                    },
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
                .testTag(SEASONAL_CONTENT_TAG),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Spacer(Modifier.height(Spacing.xs))
            when {
                !uiState.hasSearched -> Text(
                    "Choose a region in search options to test the rain-to-fruiting-lag rule of thumb " +
                        "against real data.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                uiState.isLoadingSeasonalPattern -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }

                uiState.seasonalPatternErrorMessage != null -> Text(
                    uiState.seasonalPatternErrorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.seasonalPattern != null -> SeasonalPatternContent(uiState.seasonalPattern)
            }
        }
    }
}

/** Same readable-width reasoning as [SeasonalTab]; see [CombinedResultsPane] for the drawer/pane analogs. */
private val READABLE_CONTENT_MAX_WIDTH = 640.dp

/** Lets [AvailabilityScreenAdaptiveLayoutTest] measure the readable-width column directly. */
const val SEASONAL_CONTENT_TAG = "seasonal-content"

@Composable
private fun SeasonalPatternContent(distribution: FruitingLagDistribution) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Text("Does rain predict fruiting?", style = MaterialTheme.typography.titleMedium)
        Text(
            "Testing whether ${distribution.filter.label} sightings actually cluster in the " +
                "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
                "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} days after a soaking rain — the " +
                "widely-repeated foraging rule of thumb — against real historical observations and " +
                "real historical rainfall.",
            style = MaterialTheme.typography.bodyMedium,
        )

        SeasonalSampleSizeSummary(distribution)
        FruitingLagChart(distribution.buckets, modifier = Modifier.fillMaxWidth())
        FruitingLagBucketCounts(distribution.buckets)

        HorizontalDivider()
        // The observer-effort caveat: not polish, per this feature's own honesty requirement —
        // raw counts conflate "more people were out looking" with "the species was more present."
        Text(
            "Raw iNaturalist counts reflect how many people were out looking that day, not only " +
                "whether ${distribution.filter.label} was actually there — more observers means more " +
                "sightings regardless of the rain.",
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
        )
    }
}

/**
 * The sample size, on screen and prominent rather than in a tooltip — this feature's whole
 * honesty mechanism is that nobody can read a bar off [FruitingLagChart] without also seeing what
 * it's an estimate from.
 */
@Composable
private fun SeasonalSampleSizeSummary(distribution: FruitingLagDistribution) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "Estimate from ${distribution.sampleSize} observations with a known date, not a guarantee.",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            "Based on ${distribution.sightingsConsidered} of ${distribution.totalResultsOnServer} " +
                "total observations iNaturalist reports for this search.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (distribution.observationsExcludedForMissingDate > 0) {
            Text(
                "${distribution.observationsExcludedForMissingDate} observation(s) have no recorded " +
                    "date and are excluded from this estimate.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (distribution.observationsWithNoPrecedingEvent > 0) {
            Text(
                "${distribution.observationsWithNoPrecedingEvent} observation(s) had no qualifying " +
                    "rain event in the fetched history before them.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * A hand-rolled Compose `Canvas` bar chart — no charting dependency, consistent with
 * [com.forager.app.domain.Dbscan]/[com.forager.app.domain.GeoDistance]/
 * [com.forager.app.domain.MgrsConverter] being hand-built rather than pulled from a library for a
 * single use.
 *
 * The bucket whose [FruitingLagBucket.isFruitingLagRule] is true — the range this whole feature
 * exists to test — is drawn in the theme's primary color; every other bucket, including "no
 * preceding event", shares a second, unhighlighted color. That is the entire visual claim this
 * chart makes: whether the data's tallest bar (or not) lines up with the rule of thumb. The exact
 * counts behind each bar are [FruitingLagBucketCounts], not this canvas — pixel heights are for
 * the shape of the distribution, not for reading an exact number off a screen.
 */
@Composable
private fun FruitingLagChart(buckets: List<FruitingLagBucket>, modifier: Modifier = Modifier) {
    val highlightColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.secondary
    val maxCount = buckets.maxOfOrNull { it.count } ?: 0

    Canvas(modifier = modifier.height(160.dp)) {
        if (buckets.isEmpty()) return@Canvas
        val gap = 8.dp.toPx()
        val barWidth = ((size.width - gap * (buckets.size - 1)) / buckets.size).coerceAtLeast(0f)
        buckets.forEachIndexed { index, bucket ->
            val heightFraction = if (maxCount == 0) 0f else bucket.count.toFloat() / maxCount
            val barHeight = size.height * heightFraction
            drawRect(
                color = if (bucket.isFruitingLagRule) highlightColor else barColor,
                topLeft = Offset(x = index * (barWidth + gap), y = size.height - barHeight),
                size = Size(width = barWidth, height = barHeight),
            )
        }
    }
}

/**
 * The exact count behind every bar of [FruitingLagChart], as real on-screen text — the canvas
 * above is unmeasurable in the Robolectric layout tests this project relies on (no rendering
 * happens under Robolectric; see [AvailabilityScreenLayoutTest]'s own doc comment for the same
 * limitation on the map), so the numbers this feature's honesty rests on live here, not only in
 * pixels.
 */
@Composable
private fun FruitingLagBucketCounts(buckets: List<FruitingLagBucket>) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        buckets.forEach { bucket ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (bucket.isFruitingLagRule) "${bucket.label} (the rule of thumb)" else bucket.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (bucket.isFruitingLagRule) FontWeight.Bold else FontWeight.Normal,
                )
                Text("${bucket.count}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * The map tab: the map itself takes the whole content area apart from the foraging-areas toggle
 * and detail below it, which are bounded so they can't repeat the starvation this layout exists
 * to fix.
 *
 * Owns the long-press flow: [MapSlot] only reports *where* a long-press landed (see its own doc
 * comment for why), so the pending location and what it turns into both live here, next to the
 * only place that location can come from. A long-press now offers two outcomes — plan a trip
 * ([TripDatePickerDialog], turning it into a saved [PlannedTrip]) or log a find ([onLogFindHere],
 * which opens the mushroom log's drawer destination — see `docs/plans/mushroom-log.md`'s
 * Navigation section for why this reuses the gesture rather than adding a second one) — so
 * [LongPressActionDialog] asks which before either path runs. [defaultTripName] is computed from
 * the trip count already in state, here rather than inside the dialog, so the dialog stays a dumb
 * presenter of whatever default it's handed.
 */
@Composable
private fun MapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    basemap: Basemap,
    isTopoMode: Boolean,
    onToggleMapMode: () -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    onToggleForagingAreas: (Boolean) -> Unit,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingLongPressLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingTripLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingWaypointLocation by remember { mutableStateOf<LatLng?>(null) }

    when {
        !uiState.hasSearched -> MapMessage(
            "Choose a region in search options to see mapped sightings.",
            modifier = modifier,
        )

        uiState.isLoadingSightings -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.sightingsErrorMessage != null -> MapMessage(
            uiState.sightingsErrorMessage,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
        )

        else -> {
            val region = uiState.region
            if (region != null) {
                Column(modifier = modifier.fillMaxWidth()) {
                    // Areas are only handed to the map when the layer is switched on; the
                    // clustering itself was already computed when the sightings loaded.
                    val visibleAreas = if (uiState.showForagingAreas) {
                        (uiState.foragingAreas as? ForagingAreas.Found)?.areas.orEmpty()
                    } else {
                        emptyList()
                    }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        mapSlot(
                            region,
                            MapOverlayContent(
                                sightings = uiState.sightings,
                                areas = visibleAreas,
                                plannedTrips = uiState.plannedTrips,
                                breadcrumbPoints = breadcrumbPoints,
                                waypoints = waypoints,
                            ),
                            basemap,
                            null,
                            { location -> pendingLongPressLocation = location },
                            {},
                            Modifier.fillMaxSize(),
                        )
                        MapModeToggle(
                            isTopoMode = isTopoMode,
                            onToggle = onToggleMapMode,
                            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                        )
                    }
                    // Always visible below the map, not gated on showForagingAreas itself — the
                    // switch is how the layer gets turned back on, so it has to be reachable while
                    // off. It moved here from the drawer's "Advanced search" section: whether to
                    // show the layer is a decision made while looking at the map, not a setting to
                    // dig into a drawer for.
                    ForagingAreasToggle(
                        checked = uiState.showForagingAreas,
                        onCheckedChange = onToggleForagingAreas,
                        modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    )
                    // A fixed-height Box, always present, rather than conditionally including
                    // ForagingAreasPanel only when the layer is on: the panel used to appear and
                    // disappear from this Column outright, which changed how much of the fixed
                    // parent height was left over for the weighted mapSlot above — the map visibly
                    // grew and shrank as the switch was toggled. Reserving the same
                    // [FORAGING_AREAS_PANEL_MAX_HEIGHT] here regardless of the switch's state means
                    // the sibling stack above the map never changes height, so the map doesn't
                    // either — only what's drawn inside this fixed box changes.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FORAGING_AREAS_PANEL_MAX_HEIGHT)
                            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    ) {
                        if (uiState.showForagingAreas) {
                            ForagingAreasPanel(foragingAreas = uiState.foragingAreas)
                        }
                    }
                }
            }
        }
    }

    pendingLongPressLocation?.let { location ->
        LongPressActionDialog(
            onPlanTrip = {
                pendingLongPressLocation = null
                pendingTripLocation = location
            },
            onLogFind = {
                pendingLongPressLocation = null
                onLogFindHere(location)
            },
            onDropWaypoint = {
                pendingLongPressLocation = null
                pendingWaypointLocation = location
            },
            onDismiss = { pendingLongPressLocation = null },
        )
    }

    pendingTripLocation?.let { location ->
        TripDatePickerDialog(
            defaultName = defaultTripName(uiState.plannedTrips.size),
            onConfirm = { date, name ->
                onPlaceTripPin(location, date, name)
                pendingTripLocation = null
            },
            onDismiss = { pendingTripLocation = null },
        )
    }

    pendingWaypointLocation?.let { location ->
        WaypointNameDialog(
            defaultName = defaultWaypointName(waypoints.size),
            onConfirm = { name ->
                onDropWaypoint(location, name)
                pendingWaypointLocation = null
            },
            onDismiss = { pendingWaypointLocation = null },
        )
    }
}

/** Diameter shared by every icon in [MapIconStack]. */
private val MAP_ICON_STACK_DIAMETER = 48.dp

/**
 * Dark, fully **opaque** circle color for [MapIconStack]'s four non-primary icons (recenter,
 * layers, search, fullscreen) — see [MapStackIconButton].
 *
 * **Decision: opaque, plus a hairline edge, replacing the translucent fill these circles used
 * before.** Confirmed on real hardware (Portland-metro, USGS Topo): at the previous 78%-alpha
 * fill, map data underneath — contour lines, place labels — composited straight through the
 * buttons, reading as barely-there smudges; the stack's one already-opaque icon (the green "add"
 * button, [MaterialTheme.colorScheme.primary]) read perfectly on the same terrain in the same
 * screenshot. Opacity was the only difference.
 *
 * **Rejected alternative: tuning the alpha per basemap.** A single translucency value can be tuned
 * to look right against one basemap's palette, but this app ships pale topo, dark aerial imagery,
 * and (later) hillshade — a fill that reads on one will not read on the others, and per-basemap
 * tuning means re-solving this every time a basemap is added. The principle instead: **map controls
 * never composite with map data.** Full opacity occludes the map outright, by construction,
 * regardless of what's under it — that's what makes a control a control rather than an overlay.
 *
 * The hairline border ([MAP_ICON_STACK_BORDER_COLOR]) is what keeps the fix working on imagery
 * specifically: an opaque dark circle alone reads fine against pale topo but risks merging into
 * imagery, which is dark nearly everywhere. A light edge separates control from map regardless of
 * what's underneath, rather than needing a second opaque-color decision for the dark case. Not
 * independently confirmed on hardware yet — see this project's README, "The topographic basemap,
 * specifically", for what's re-verified and what's still owed a second look on the imagery basemap.
 *
 * This is the overlay-colour decision this section's own history had left deliberately unmade
 * pending a first hardware look (`CLAUDE.md`'s rule against a speculative correction made in advance
 * of evidence) — that look happened, found a real problem, and this is the fix landing as a
 * recorded decision rather than a silent style tweak.
 */
private val MapIconStackButtonColor = Bark.copy(alpha = 1f)

/** The hairline edge on [MapIconStackButtonColor] circles — see that color's own doc comment. */
private val MAP_ICON_STACK_BORDER_COLOR = Color.White.copy(alpha = 0.4f)

/** Dark translucent background for [CompassElevationStripContent] — unaffected by the FAB stack's opaque-fill decision above; this bar sits over the map the same way, but was not reported as illegible in the same hardware pass, so it is deliberately left as-is rather than changed on the strength of a fix aimed at a different element. */
private val CompassStripBackgroundColor = Bark.copy(alpha = 0.78f)

/**
 * The Maps tab in its full-bleed, compact-only form — decision #2 in `docs/plans/map-redesign.md`:
 * the map fills the entire content area, with the top compass/elevation strip and the right-edge
 * icon stack drawn over it. The foraging-areas toggle and summary panel — an earlier revision drew
 * them as a floating overlay here — now live in [CompactSearchDrawerContent] instead, per the
 * project owner's own later call: "move the foraging areas to the side search panel."
 *
 * Scoped to `WindowWidthClass.COMPACT` only; `MEDIUM`/`EXPANDED` keep using the unmodified [MapTab]
 * inside [CombinedResultsPane] — see the plan doc's "Scope decision" section for why this is a
 * separate composable rather than a conditional threaded through [MapTab] itself.
 *
 * Owns the long-press flow exactly as [MapTab] does — see that composable's doc comment for the
 * mechanics [pendingLongPressLocation] drives; [TripDatePickerDialog]/[defaultTripName] are shared,
 * unmodified, but the "what would you like to do here" chooser itself is [AddActionTile] here
 * rather than [MapTab]'s [LongPressActionDialog] — see that composable's own doc comment for why.
 * The icon stack's add (+) button reuses this exact same flow — it sets [pendingLongPressLocation]
 * directly, the identical state variable the long-press gesture sets, rather than a parallel
 * dialog/handler — so the two entry points can never drift apart (decision #3.5). It hands that
 * flow the map's current viewport centre — the real search region if one exists, otherwise
 * whatever `displayRegion` below is standing in for it — rather than requiring its own GPS fetch.
 */
@Composable
private fun CompactMapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    basemap: Basemap,
    isTopoMode: Boolean,
    onToggleMapMode: () -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    onOpenSearchDrawer: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    startRecordingErrorMessage: String?,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    compassProvider: CompassProvider,
    modifier: Modifier = Modifier,
) {
    var pendingLongPressLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingTripLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingWaypointLocation by remember { mutableStateOf<LatLng?>(null) }
    // See MapOverlayContent.resumeTrackingRequestId's own doc comment — incremented alongside the
    // existing onLocateMe() call below, not instead of it: that call still drives the compass
    // strip's own one-shot position/elevation text, this drives the map's live GPS camera puck.
    var resumeTrackingRequestId by remember { mutableStateOf(0) }

    // AddActionTile is a plain overlay, not a real Dialog, so — unlike TripDatePickerDialog below,
    // an M3 DatePickerDialog whose own Dialog window already handles system back for free — this
    // needs its own BackHandler or system back would fall straight through it, same reasoning as
    // AvailabilityScreen's own top-level "unwind before falling through" chain.
    BackHandler(enabled = pendingLongPressLocation != null) {
        pendingLongPressLocation = null
    }

    val context = LocalContext.current
    LaunchedEffect(uiState.locateMeStatus) {
        when (uiState.locateMeStatus) {
            LocateMeStatus.PermissionDenied ->
                Toast.makeText(context, "Location permission denied. Can't center on your position.", Toast.LENGTH_SHORT).show()
            LocateMeStatus.Unavailable ->
                Toast.makeText(context, "Couldn't determine your location.", Toast.LENGTH_SHORT).show()
            else -> Unit
        }
    }
    // Same one-shot-per-transition shape as the locateMeStatus effect above: a failed
    // startRecording() is an event ("the action you just took failed"), not a persistent
    // condition — the field only clears on the next successful startRecording() (see
    // TrackRecordingViewModel), so a banner would outlive the moment it's relevant.
    LaunchedEffect(startRecordingErrorMessage) {
        startRecordingErrorMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    when {
        uiState.isLoadingSightings -> Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }

        uiState.sightingsErrorMessage != null -> MapMessage(
            uiState.sightingsErrorMessage,
            modifier = modifier,
            color = MaterialTheme.colorScheme.error,
        )

        else -> {
            // The map's own viewport, never null — unlike uiState.region (only set once a real
            // search has run), so the map always has *something* to show rather than the earlier
            // revision's "choose a region" placeholder: the real search region once one exists,
            // otherwise the device's live location once locate-me above resolves one, otherwise a
            // fixed fallback while that's still pending. The project owner's own framing: the Maps
            // tab should already be showing a real map, centred on the user, the moment the app
            // opens — not a message asking them to search first.
            val located = (uiState.locateMeStatus as? LocateMeStatus.Located)?.location
            val displayRegion = uiState.region
                ?: located?.let { Region(lat = it.lat, lng = it.lng, radiusKm = JOURNAL_PICKER_DEFAULT_REGION.radiusKm) }
                ?: JOURNAL_PICKER_DEFAULT_REGION

            // The GPS/locate-me pan target for a search that's already run — remember(uiState.region),
            // not just remember, so a brand new search (a different region) drops any earlier
            // locate-me override rather than keeping the map stuck on a now-stale GPS fix; see
            // MapSlot's focusOverride doc comment for why this is independent of region itself.
            // Pre-search, displayRegion already tracks locate-me directly (see above), so this stays
            // null rather than doubly panning the same fix through two different mechanisms.
            var focusOverride by remember(uiState.region) { mutableStateOf<LatLng?>(null) }
            LaunchedEffect(uiState.locateMeStatus) {
                val status = uiState.locateMeStatus
                if (uiState.region != null && status is LocateMeStatus.Located) focusOverride = status.location
            }

            // Sightings/areas/planned trips are only real once a search has actually run — before
            // that, displayRegion is a viewport with nothing plotted on it yet, not a stand-in
            // search.
            val hasSearched = uiState.region != null
            val visibleAreas = if (hasSearched && uiState.showForagingAreas) {
                (uiState.foragingAreas as? ForagingAreas.Found)?.areas.orEmpty()
            } else {
                emptyList()
            }

            Box(modifier = modifier.fillMaxSize()) {
                mapSlot(
                    displayRegion,
                    MapOverlayContent(
                        sightings = if (hasSearched) uiState.sightings else emptyList(),
                        areas = visibleAreas,
                        plannedTrips = if (hasSearched) uiState.plannedTrips else emptyList(),
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        resumeTrackingRequestId = resumeTrackingRequestId,
                    ),
                    basemap,
                    focusOverride,
                    { location -> pendingLongPressLocation = location },
                    // Tapping the map restores chrome while fullscreen — decision #5. Only
                    // meaningful in that state: chrome is never hidden by a tap, only by the
                    // fullscreen icon itself, so a tap while chrome is already showing is a
                    // no-op rather than toggling it away.
                    { if (isFullscreen) onToggleFullscreen() },
                    Modifier.fillMaxSize(),
                )
                CompassElevationStrip(
                    compassProvider = compassProvider,
                    elevationMeters = uiState.liveAltitudeMeters,
                    location = uiState.liveLocation,
                    isRecording = isRecording,
                    onToggleRecording = onToggleRecording,
                    returnToStart = returnToStart,
                    isReturning = isReturning,
                    isOffTrack = isOffTrack,
                    onToggleReturning = onToggleReturning,
                    // Full width, flush against the top of the map — "just below" ActiveSearchSummary
                    // (the sibling Column entry directly above this Box) rather than a narrow
                    // floating pill with margins on both sides, per the project owner's own redesign
                    // call.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
                MapIconStack(
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onLocateMe = {
                        resumeTrackingRequestId++
                        onLocateMe()
                    },
                    isTopoMode = isTopoMode,
                    onToggleMapMode = onToggleMapMode,
                    onOpenSearchDrawer = onOpenSearchDrawer,
                    onAdd = {
                        // Same state variable the long-press gesture sets above — not a
                        // parallel dialog. See this function's own doc comment.
                        pendingLongPressLocation = LatLng(displayRegion.lat, displayRegion.lng)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(Spacing.sm),
                )

                // Inside this Box, not alongside it, so it can align near the add button's own
                // corner of the icon stack above — see AddActionTile's doc comment for why this
                // reads as opening "from" that button rather than as a centered system dialog.
                AddActionTile(
                    visible = pendingLongPressLocation != null,
                    onPlanTrip = {
                        pendingLongPressLocation?.let { pendingTripLocation = it }
                        pendingLongPressLocation = null
                    },
                    onLogFind = {
                        pendingLongPressLocation?.let { onLogFindHere(it) }
                        pendingLongPressLocation = null
                    },
                    onDropWaypoint = {
                        pendingLongPressLocation?.let { pendingWaypointLocation = it }
                        pendingLongPressLocation = null
                    },
                    onDismiss = { pendingLongPressLocation = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    pendingTripLocation?.let { location ->
        TripDatePickerDialog(
            defaultName = defaultTripName(uiState.plannedTrips.size),
            onConfirm = { date, name ->
                onPlaceTripPin(location, date, name)
                pendingTripLocation = null
            },
            onDismiss = { pendingTripLocation = null },
        )
    }

    pendingWaypointLocation?.let { location ->
        WaypointNameDialog(
            defaultName = defaultWaypointName(waypoints.size),
            onConfirm = { name ->
                onDropWaypoint(location, name)
                pendingWaypointLocation = null
            },
            onDismiss = { pendingWaypointLocation = null },
        )
    }
}

/**
 * The right-edge floating icon stack — decision #3 in `docs/plans/map-redesign.md`: exactly five
 * icons, top to bottom, dark translucent circles with a white glyph except the bottom one, which
 * is filled in the app's own forest green. Slot 3 reuses [isTopoMode]/[onToggleMapMode] — the same
 * logic [MapModeToggle] wraps for the untouched MEDIUM/EXPANDED path — restyled to match this
 * stack's other four icons rather than calling that composable directly, so MEDIUM/EXPANDED's own
 * styling stays untouched too.
 */
@Composable
private fun MapIconStack(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    isTopoMode: Boolean,
    onToggleMapMode: () -> Unit,
    onOpenSearchDrawer: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MapStackIconButton(
            icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
            contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
            onClick = onToggleFullscreen,
        )
        MapStackIconButton(
            icon = Icons.Filled.MyLocation,
            contentDescription = "Center on my location",
            onClick = onLocateMe,
        )
        MapStackIconButton(
            icon = Icons.Filled.Layers,
            contentDescription = if (isTopoMode) {
                "Showing topo mode. Switch to regular mode."
            } else {
                "Showing regular mode. Switch to topo mode."
            },
            onClick = onToggleMapMode,
        )
        MapStackIconButton(
            icon = Icons.Filled.Search,
            contentDescription = "Search",
            onClick = onOpenSearchDrawer,
        )
        MapStackIconButton(
            icon = Icons.Filled.Add,
            contentDescription = "Plan a trip or log a find here",
            onClick = onAdd,
            filled = true,
        )
    }
}

/** One circle in [MapIconStack]; [filled] is the stack's one green (rather than dark) icon — the add button. */
@Composable
private fun MapStackIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MapIconStackButtonColor,
        contentColor = Color.White,
        shadowElevation = 2.dp,
        border = if (filled) null else BorderStroke(1.dp, MAP_ICON_STACK_BORDER_COLOR),
        modifier = modifier.size(MAP_ICON_STACK_DIAMETER),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/**
 * Decisions #7-8: compass heading + GPS elevation folded into one bar at the top of the map — a
 * compass-tape-style heading readout, not a separate elevation/speed stats pill (that would be
 * tied to active track recording, out of scope here — see the plan doc's decision #9). The MGRS
 * grid reference on its own line below extends this same strip rather than becoming a separate
 * "navigator screen" — position and heading are the one thing a field navigator needs together at
 * a glance, and [MgrsConverter] already exists ([PlannedTripRow] uses it the same way).
 *
 * A thin wrapper around [CompassElevationStripContent] that does the one impure thing (collecting
 * [compassProvider]'s [Flow][kotlinx.coroutines.flow.Flow]) so that content composable stays a pure
 * function of primitive values — directly testable without a real sensor, per the plan doc's test
 * requirements, and so heading ticks recompose only this small leaf rather than the whole map tab
 * (which would otherwise fight the user's own pan/zoom on every sensor update).
 */
@Composable
private fun CompassElevationStrip(
    compassProvider: CompassProvider,
    elevationMeters: Double?,
    location: LatLng?,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headingDegrees by compassProvider.heading.collectAsState(initial = null)
    CompassElevationStripContent(
        headingDegrees = headingDegrees,
        elevationMeters = elevationMeters,
        location = location,
        isRecording = isRecording,
        onToggleRecording = onToggleRecording,
        returnToStart = returnToStart,
        isReturning = isReturning,
        isOffTrack = isOffTrack,
        onToggleReturning = onToggleReturning,
        modifier = modifier,
    )
}

@Composable
private fun CompassElevationStripContent(
    headingDegrees: Float?,
    elevationMeters: Double?,
    location: LatLng?,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A plain Box + background, not Surface: Surface (even with no onClick) intercepts pointer
    // input for the area it occupies, which — now that this strip is full-width — swallowed the
    // map's own long-press gesture underneath it (the same failure class IntrinsicSize.Max fixed
    // for the narrow pill, but that fix meant shrinking the strip back down, which isn't an option
    // now that full width is the point). A Box with no pointer/click handling of its own doesn't
    // intercept anything, so the map keeps receiving touches everywhere except this strip's own
    // real interactive children (the return-to-vehicle text, the record toggle, and now the
    // coordinates segment below).
    //
    // Local state, not AvailabilityUiState: this is purely which of two always-computable string
    // representations of the same fix to display, nothing the ViewModel or a future session needs
    // to remember — the same reasoning showQuickSearch elsewhere in this file already applies to a
    // similar tap-to-reveal toggle.
    var showDecimalDegrees by remember { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(modifier = modifier.background(color = CompassStripBackgroundColor, shape = RectangleShape)) {
            Column(
                // fillMaxWidth(), not width(IntrinsicSize.Max): this strip is now the full-width bar
                // itself (see this composable's own call site), so its content should actually span
                // that width — in particular the return-to-vehicle row's own fillMaxWidth() below
                // needs to reach the true screen edges to put the record toggle at the far right,
                // not just the edge of an intrinsic-width column. (IntrinsicSize.Max was the fix for
                // a real regression when this strip was still a narrow pill — see
                // AvailabilityScreenTripPlanningFlowTest — not needed now that fillMaxWidth() on the
                // Box above does that job instead.)
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // Heading, elevation, and coordinates on one line — the project owner's own call
                // ("have the text all fit in one single line") to make the strip read as a slim bar
                // rather than a two-line block. labelMedium (down from heading/elevation's earlier
                // labelLarge) is meant to let a typical phone width show the whole line; on a
                // narrower screen, the coordinates segment (the longest of the three, and the one
                // that grew when Lat./Long. were added alongside the MGRS grid) is the one that
                // gives way — Modifier.weight(1f, fill = false) + TextOverflow.Ellipsis on just that
                // Text, not horizontalScroll on the whole Row: horizontalScroll installs a real
                // pointer-input handler even at zero scroll range, which intercepted touches meant
                // for the map underneath this strip (the same touch-interception class
                // CompassElevationStripContent's own Box-not-Surface comment above already
                // documents; AvailabilityScreenTripPlanningFlowTest and
                // AvailabilityScreenWaypointFlowTest caught this one the same way). Weight-based
                // sizing truncates with an ellipsis rather than an abrupt hard clip, and adds no
                // pointer input of its own, so the map stays reachable everywhere under the strip.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(headingDegrees ?: 0f),
                    )
                    Text(
                        text = headingDegrees?.let { "${it.roundToInt()}° ${cardinalDirection(it)}" } ?: "Compass unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Text("·", style = MaterialTheme.typography.labelMedium)
                    Text(
                        // Meters, matching this app's existing metric convention (radiusKm) rather
                        // than introducing feet — nothing else in the app displays imperial units.
                        text = elevationMeters?.let { "${it.roundToInt()} m" } ?: "Elevation unavailable",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                    Text("·", style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = coordinatesStripText(location, showDecimalDegrees),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable(
                                enabled = location != null,
                                onClick = { showDecimalDegrees = !showDecimalDegrees },
                            ),
                    )
                }
                // Return-to-vehicle on the far left, the record start/stop toggle on the far right —
                // opposite ends of the same box, per the project owner's own placement call, rather
                // than a sixth MapIconStack icon (that stack is fixed at exactly five, a settled
                // decision) or a separate return-to-vehicle screen. The text itself is the "returning"
                // toggle — tapping it starts/stops the off-track heuristic (see TrackRecordingViewModel's
                // own doc comment for why that's a distinct state from isRecording) — disabled while
                // nothing is recording, since there is nothing yet to return to.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = returnToStartStripText(isRecording, returnToStart),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isReturning) FontWeight.Bold else null,
                        color = if (isOffTrack) MaterialTheme.colorScheme.error else Color.White,
                        modifier = Modifier.clickable(enabled = isRecording, onClick = onToggleReturning),
                    )
                    RecordToggleButton(
                        isRecording = isRecording,
                        onClick = onToggleRecording,
                    )
                }
            }
        }
    }
}

/**
 * The return-to-vehicle line's text — blank while not recording (nothing to return to), a status
 * message once recording starts but before the first breadcrumb lands (bearing/distance need a
 * start point), then bearing, distance, and elevation difference once [info] is real.
 * [ReturnToStartInfo]'s own doc comment covers why there's no ETA here.
 */
internal fun returnToStartStripText(isRecording: Boolean, info: ReturnToStartInfo?): String {
    if (!isRecording) return ""
    if (info == null) return "Recording — waiting for a fix to compute the way back"
    val distanceText = if (info.distanceMeters < 1000) {
        "${info.distanceMeters.roundToInt()} m"
    } else {
        "${"%.1f".format(info.distanceMeters / 1000)} km"
    }
    val elevationText = info.elevationDifferenceMeters?.let {
        "${if (it >= 0) "+" else ""}${it.roundToInt()} m"
    } ?: "elevation diff. unavailable"
    val bearing = info.bearingDegrees.roundToInt()
    return "Return: $bearing° ${cardinalDirection(info.bearingDegrees.toFloat())} · $distanceText · $elevationText"
}

/** The compass strip's own start/stop control for [com.forager.app.service.TrackRecordingService] — see [CompassElevationStripContent]'s doc comment on why it lives here rather than in [MapIconStack]. */
@Composable
private fun RecordToggleButton(isRecording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isRecording) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.18f),
        contentColor = Color.White,
        modifier = modifier.size(22.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording track" else "Start recording track",
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * "Coordinates unavailable" before a first fix (distinct wording from [MgrsCoordinate.Unsupported]
 * — one is "no fix yet", the other is "this location can't be expressed in MGRS at all", and
 * collapsing them into one message would hide which is actually true).
 *
 * **MGRS by default; decimal degrees only on tap ([showDecimalDegrees]).** This strip used to show
 * both at once ("`<mgrs> · Lat. X Long. Y`"), but a real hardware pass found the combined line
 * truncates mid-coordinate on a metro-width screen ("Lat. 45.3262 Lon…") — half a coordinate is not
 * a coordinate, on the one element whose job is telling you where you are. MGRS is the better fit
 * for a strip this narrow: compact, unambiguous, built for grid work.
 *
 * **Decimal degrees stay reachable, not deleted** — tapping the coordinates segment toggles
 * [showDecimalDegrees] (see [CompassElevationStripContent]) — because the two formats serve
 * different readers and neither substitutes for the other: MGRS suits a paper-map/compass workflow,
 * decimal degrees is what everything else speaks (pasting a point into another app, giving
 * coordinates to a dispatcher, sharing a location by text). Checked before this landed: this
 * project has no configurable-coordinate-format setting (lat/lon vs. UTM vs. MGRS vs. decimal
 * degrees) wired anywhere yet, and no "emergency card" or share/copy path exists to keep the pair
 * reachable through instead — `grep -rn "CoordinateFormat"` finds nothing, and `docs/plans/` doesn't
 * specify either as built. Tap-to-reveal is what's actually shippable today, per the project's own
 * instruction for exactly this case: if the setting isn't wired, hardcode MGRS and say so, rather
 * than build the setting or the emergency card as unplanned scope here.
 *
 * The decimal pair is labeled ("Lat."/"Long.", the project owner's own wording) rather than the bare
 * "45.5231, -122.6414" [PlannedTripRow] shows — that row sits next to a named trip, where the pair
 * reads as "that trip's location" from context; this strip has no such context, so an unlabeled pair
 * here could as easily read as two unrelated numbers. `"%.4f"` matches the same precision every
 * other decimal-degree display in this file already uses (`PlannedTripRow`, `RecentSearchRow`, the
 * offline-map picker), not a new precision invented for this one line.
 */
private fun coordinatesStripText(location: LatLng?, showDecimalDegrees: Boolean): String {
    if (location == null) return "Coordinates unavailable"
    if (showDecimalDegrees) {
        return "Lat. ${"%.4f".format(location.lat)} Long. ${"%.4f".format(location.lng)}"
    }
    return when (val mgrs = MgrsConverter.convert(location)) {
        is MgrsCoordinate.Grid -> mgrs.value
        is MgrsCoordinate.Unsupported -> "MGRS unavailable"
    }
}

/** Nearest 45°-wide compass point for [headingDegrees], `[0, 360)`. */
private fun cardinalDirection(headingDegrees: Float): String {
    val points = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = (((headingDegrees % 360f) + 360f) % 360f / 45f).roundToInt() % points.size
    return points[index]
}

/** What a map long-press means — asked before either [TripDatePickerDialog] or `onLogFindHere` runs; see [MapTab]'s own doc comment. */
@Composable
private fun LongPressActionDialog(
    onPlanTrip: () -> Unit,
    onLogFind: () -> Unit,
    onDropWaypoint: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What would you like to do here?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                TextButton(onClick = onPlanTrip, modifier = Modifier.fillMaxWidth()) { Text("Plan a trip") }
                TextButton(onClick = onLogFind, modifier = Modifier.fillMaxWidth()) { Text("Log a find") }
                TextButton(onClick = onDropWaypoint, modifier = Modifier.fillMaxWidth()) { Text("Drop a waypoint") }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * [CompactMapTab]'s own version of the same "Plan a trip"/"Log a find" chooser [LongPressActionDialog]
 * shows on medium/expanded windows — same two choices, same shared [pendingLongPressLocation] state
 * (see [CompactMapTab]'s doc comment), but presented as a small tile that grows out of the add
 * button's own corner of the icon stack rather than [AlertDialog]'s centered scale-in, per the
 * project owner's own description of how it should open. Compact-only: the medium/expanded window
 * has no floating add button for a tile to originate from, so [MapTab] keeps the plain dialog.
 *
 * A scrim (its own [AnimatedVisibility], faded independently of the tile) makes the map behind it
 * unmistakably unavailable to tap while a choice is pending — the same modal intent the dialog it
 * replaces had, just without borrowing [AlertDialog]'s fixed presentation. The tile itself keeps an
 * explicit "Cancel" row alongside the scrim-tap-to-dismiss, the same two ways out
 * [LongPressActionDialog] gave (its own "Cancel" dismiss button plus tapping outside).
 */
@Composable
private fun AddActionTile(
    visible: Boolean,
    onPlanTrip: () -> Unit,
    onLogFind: () -> Unit,
    onDropWaypoint: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        // docs/motion-spec.md §2 "Panels and navigation": ease-out, grounded; no springy
        // overshoot. MotionTokens.panelMotionSpec is tween-based, never spring, so passing it
        // explicitly here removes any dependence on AnimatedVisibility's own default spec.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec) +
                expandIn(
                    animationSpec = tween(durationMillis = MotionTokens.PANEL_MOTION_DURATION_MS, easing = MotionTokens.EaseOut),
                    expandFrom = Alignment.BottomEnd,
                ),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec) +
                shrinkOut(
                    animationSpec = tween(durationMillis = MotionTokens.PANEL_MOTION_DURATION_MS, easing = MotionTokens.EaseOut),
                    shrinkTowards = Alignment.BottomEnd,
                ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -Spacing.sm, y = ADD_TILE_ANCHOR_OFFSET),
        ) {
            Surface(
                shape = RoundedCornerShape(Spacing.md),
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text("Add...", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onPlanTrip, modifier = Modifier.fillMaxWidth()) { Text("Plan a trip") }
                    TextButton(onClick = onLogFind, modifier = Modifier.fillMaxWidth()) { Text("Log a find") }
                    TextButton(onClick = onDropWaypoint, modifier = Modifier.fillMaxWidth()) { Text("Drop a waypoint") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}

/**
 * How far down from vertical-center-end (where [AddActionTile]'s own alignment starts, matching
 * [MapIconStack]'s alignment) to shift the tile so it lands near the add button — the 5-icon
 * stack's bottom item, not its vertical center. Computed as half the full stack's height (5 icons
 * plus their 4 gaps) minus half one icon's own height, from [MAP_ICON_STACK_DIAMETER] and
 * [MapIconStack]'s `Spacing.sm` gaps, rather than a guessed constant. Not pixel-exact — the tile
 * doesn't track the button's real measured position — but close enough that it visibly grows from
 * that button's corner rather than from an unrelated point on screen.
 */
private val ADD_TILE_ANCHOR_OFFSET = (MAP_ICON_STACK_DIAMETER * 5 + Spacing.sm * 4) / 2 - MAP_ICON_STACK_DIAMETER / 2

/**
 * The name a newly-placed trip pin is pre-filled with: `"Trip N"`, `N` being one more than how
 * many trips already exist. The simplest possible default, per the user's own framing of this —
 * it doesn't guarantee uniqueness against a renamed or since-deleted trip (there is no rename, and
 * deleting trip 1 of 2 then adding a new one names it "Trip 2" again, alongside the surviving
 * "Trip 2"), but a collision here is cosmetic, not a broken invariant: [PlannedTrip.id] is what
 * actually identifies a trip, and the name stays freely editable in [TripDatePickerDialog] before
 * it's saved.
 */
internal fun defaultTripName(existingTripCount: Int): String = "Trip ${existingTripCount + 1}"

/** Same reasoning as [defaultTripName], applied to waypoints — see that function's own doc comment. */
internal fun defaultWaypointName(existingWaypointCount: Int): String = "Waypoint ${existingWaypointCount + 1}"

/**
 * Confirms a date and name for a trip pin dropped via the map's long-press gesture.
 *
 * The date is restricted to today-or-later — planning a trip in the past makes no sense, per the
 * user's own framing of this feature. [SavePlannedTripUseCase][com.forager.app.domain.SavePlannedTripUseCase]
 * enforces the same floor independently; this is the UI convenience, not the invariant itself.
 *
 * The name field starts pre-filled with [defaultTripName] and stays freely editable; the confirm
 * button disables on a blank name so the "name is never blank" invariant
 * ([PlannedTrip.name][com.forager.app.domain.model.PlannedTrip.name]) can't be violated from this
 * dialog, mirroring `SavePlannedTripUseCase`'s own `require` for the same reason the date floor is
 * mirrored there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripDatePickerDialog(
    defaultName: String,
    onConfirm: (LocalDate, String) -> Unit,
    onDismiss: () -> Unit,
) {
    // DatePicker works in UTC-midnight epoch millis regardless of device time zone, so both the
    // floor and the confirmed selection are converted through UTC to stay consistent with it.
    val todayUtcMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = todayUtcMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtcMillis
        },
    )
    var name by remember { mutableStateOf(defaultName) }
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // Guards the "name is never blank" invariant from this dialog — see this
                // function's own doc comment.
                enabled = name.isNotBlank(),
                onClick = {
                    val selectedMillis = datePickerState.selectedDateMillis ?: return@TextButton
                    val date = Instant.ofEpochMilli(selectedMillis).atZone(ZoneOffset.UTC).toLocalDate()
                    onConfirm(date, name)
                },
            ) { Text("Plan trip") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        Column {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Trip name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            )
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Confirms a name for a waypoint dropped via the map's long-press gesture — [TripDatePickerDialog]
 * without the date, since a waypoint has none ([Waypoint] carries no target date, unlike
 * [PlannedTrip]). Same blank-name guard, mirroring
 * [CreateWaypointUseCase][com.forager.app.domain.CreateWaypointUseCase]'s own `require`.
 */
@Composable
private fun WaypointNameDialog(defaultName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    // usePlatformDefaultWidth = false, matching M3's own DatePickerDialog default rather than
    // AlertDialog's (true): an AlertDialog/plain Dialog with an OutlinedTextField as its sole
    // focusable content, reached through the compact scaffold (ModalNavigationDrawer plus its own
    // BackHandlers), never settles under this Robolectric test setup — real, reproduced
    // AppNotIdleException, isolated by removing pieces one at a time (the text field alone is fine;
    // AddActionTile -> this dialog alone is fine; TripDatePickerDialog's own text field, reached
    // through this exact same scaffold, is fine). The one structural difference from
    // TripDatePickerDialog left once AlertDialog vs. plain Dialog was ruled out is this property.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(Spacing.md),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text("Name this waypoint", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Waypoint name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("Drop waypoint") }
                }
            }
        }
    }
}

/**
 * The Trip Planner drawer section's content: the planned-trips list (see [PlannedTripsList])
 * above the existing rain-driven trip windows, gated on a search the same way [MapTab] is —
 * before any region is chosen there is no rainfall history to plan from, so this says that rather
 * than rendering an empty [TripWindowsCard]. The planned-trips list has no such gate: it lists
 * absolute map points the user placed, independent of any search.
 */
@Composable
private fun TripPlannerSection(uiState: AvailabilityUiState, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        PlannedTripsList(plannedTrips = uiState.plannedTrips, onDeletePlannedTrip = onDeletePlannedTrip)
        HorizontalDivider()
        if (uiState.hasSearched) {
            TripWindowsCard(uiState = uiState)
        } else {
            Text(
                "Choose a region in search options to see rain-driven trip windows.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The list of trips the user has placed on the map, sorted by
 * [GetPlannedTripsUseCase][com.forager.app.domain.GetPlannedTripsUseCase] with any dated today
 * already moved to the front — this composable renders that order, it doesn't recompute it.
 */
@Composable
private fun PlannedTripsList(plannedTrips: List<PlannedTrip>, onDeletePlannedTrip: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text("Planned Trips", style = MaterialTheme.typography.titleSmall)
        if (plannedTrips.isEmpty()) {
            Text(
                "No trips planned yet. Long-press the map to plan one.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            val today = LocalDate.now()
            plannedTrips.forEach { trip ->
                PlannedTripRow(
                    trip = trip,
                    isToday = trip.date == today,
                    onDelete = { onDeletePlannedTrip(trip.id) },
                )
            }
        }
    }
}

/**
 * One planned trip: its user-chosen [PlannedTrip.name] as the primary identifying text (more
 * prominent than the coordinates below it, per the user's own framing — the name is what a
 * person recognizes a trip by, the coordinates are supporting detail), its MGRS grid reference
 * (the format asked for on this screen; see [MgrsConverter]) with decimal degrees kept alongside
 * since some readers still want them and MGRS can't cover every point (see
 * [MgrsCoordinate.Unsupported]), a "Directions" action that hands the location to whatever
 * navigation app is installed (see [launchDirections]), and delete.
 */
@Composable
private fun PlannedTripRow(trip: PlannedTrip, isToday: Boolean, onDelete: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isToday) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (isToday) {
                    Text(
                        "Today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(trip.name, style = MaterialTheme.typography.titleSmall)
                Text(TRIP_WINDOW_DATE_FORMAT.format(trip.date), style = MaterialTheme.typography.bodyMedium)
                when (val mgrs = MgrsConverter.convert(trip.location)) {
                    is MgrsCoordinate.Grid -> Text(mgrs.value, style = MaterialTheme.typography.bodySmall)
                    // No line at all rather than a wrong or truncated one — see MgrsCoordinate's
                    // own doc comment. The decimal-degrees line below still locates the trip.
                    is MgrsCoordinate.Unsupported -> Unit
                }
                Text(
                    "${"%.4f".format(trip.location.lat)}, ${"%.4f".format(trip.location.lng)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { launchDirections(context, trip) }) {
                Icon(Icons.Filled.Directions, contentDescription = "Directions to ${trip.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove planned trip for ${trip.date}")
            }
        }
    }
}

/**
 * The saved-waypoints drawer section — same "list plus delete" shape as [PlannedTripsList], the
 * one existing precedent for a user-placed-point list in this drawer. Unlike planned trips,
 * waypoints have no date to sort by, so they're shown newest-first (the order
 * [com.forager.app.domain.GetWaypointsUseCase] already returns — see that use case for why).
 */
@Composable
private fun WaypointsSection(waypoints: List<Waypoint>, onDeleteWaypoint: (String) -> Unit, waypointsErrorMessage: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when {
            waypointsErrorMessage != null -> Text(
                waypointsErrorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            waypoints.isEmpty() -> Text(
                "No waypoints dropped yet. Long-press the map to drop one.",
                style = MaterialTheme.typography.bodySmall,
            )

            else -> {
                waypoints.forEach { waypoint ->
                    WaypointRow(waypoint = waypoint, onDelete = { onDeleteWaypoint(waypoint.id) })
                }
            }
        }
    }
}

/**
 * One saved waypoint: its user-chosen [Waypoint.name] as the primary identifying text, the same
 * MGRS-plus-decimal-degrees coordinate display [PlannedTripRow] uses, a "Directions" action
 * ([launchDirections]) reusing the exact same `geo:` intent machinery, and delete.
 */
@Composable
private fun WaypointRow(waypoint: Waypoint, onDelete: () -> Unit) {
    val context = LocalContext.current
    val location = LatLng(waypoint.lat, waypoint.lng)
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(waypoint.name, style = MaterialTheme.typography.titleSmall)
                when (val mgrs = MgrsConverter.convert(location)) {
                    is MgrsCoordinate.Grid -> Text(mgrs.value, style = MaterialTheme.typography.bodySmall)
                    // No line at all rather than a wrong or truncated one — see PlannedTripRow's
                    // own use of the same MgrsCoordinate branch for why.
                    is MgrsCoordinate.Unsupported -> Unit
                }
                Text(
                    "${"%.4f".format(waypoint.lat)}, ${"%.4f".format(waypoint.lng)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = { launchDirections(context, waypoint.name, location) }) {
                Icon(Icons.Filled.Directions, contentDescription = "Directions to ${waypoint.name}")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove waypoint ${waypoint.name}")
            }
        }
    }
}

/** Shown when no navigation app can handle [directionsIntent] — CLAUDE.md: report, don't swallow. */
private const val NO_MAPS_APP_MESSAGE = "No maps app is installed to show directions."

/**
 * The `geo:` intent for a point named [name] at [location]: a `geo:0,0?q=lat,lng(label)` URI
 * resolves to whatever navigation app is installed rather than assuming Google Maps specifically,
 * which is the portable choice — see this file's own doc comment for why this lives here and not
 * in the ViewModel or `domain/`. Exposed as its own function (rather than inlined into
 * [launchDirections]) so a test can assert on its action/data without needing a resolvable package
 * or a running Activity.
 */
internal fun directionsIntent(name: String, location: LatLng): Intent {
    val label = Uri.encode(name)
    val uri = Uri.parse("geo:0,0?q=${location.lat},${location.lng}($label)")
    return Intent(Intent.ACTION_VIEW, uri)
}

/** [directionsIntent] for a [PlannedTrip] specifically — see [WaypointRow] for the other caller of the shared, name-plus-location overload. */
internal fun directionsIntent(trip: PlannedTrip): Intent = directionsIntent(trip.name, trip.location)

/**
 * Opens directions to [location] in whatever navigation app [directionsIntent] resolves to.
 * `Intent`/`Context` are Android framework types, so this launch has to happen from the Compose UI
 * layer — CLAUDE.md keeps both out of `domain/` and the ViewModel.
 *
 * Resolves the intent before launching, in addition to catching [ActivityNotFoundException]:
 * checking first is what lets this show one exact, always-correct message rather than depending
 * on whichever exception a given OEM build happens to raise for an unresolvable implicit intent —
 * the catch is the belt to the resolve check's suspenders, covering the narrow race where the
 * only maps app is uninstalled between the check and the launch. Either path shows a real message
 * (a [Toast]) rather than crashing or failing silently.
 */
internal fun launchDirections(context: Context, name: String, location: LatLng) {
    val intent = directionsIntent(name, location)
    if (intent.resolveActivity(context.packageManager) == null) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, NO_MAPS_APP_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}

/** [launchDirections] for a [PlannedTrip] specifically — see [WaypointRow] for the other caller of the shared, name-plus-location overload. */
internal fun launchDirections(context: Context, trip: PlannedTrip) = launchDirections(context, trip.name, trip.location)

@Composable
private fun MapMessage(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(Spacing.lg),
    )
}

@Composable
private fun ForagingAreasToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Foraging areas", style = MaterialTheme.typography.titleSmall)
            Text(
                "Group the pins into spots that have produced repeatedly. Switch off to read the " +
                    "individual observations instead.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * The detail below the map when the layer is on: the numbered areas and their stats, or an
 * explicit reason no area was found.
 *
 * Every branch says something. A silent blank panel would look identical to a still-loading one,
 * and "no repeat-producing areas here" is a real answer worth reading (CLAUDE.md: partial or
 * empty results are reported as such).
 */
@Composable
private fun ForagingAreasPanel(foragingAreas: ForagingAreas?, modifier: Modifier = Modifier) {
    when (foragingAreas) {
        null -> Text(
            "Foraging areas are grouped from the mapped sightings, which haven't loaded yet.",
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )

        is ForagingAreas.None -> Text(
            noAreasMessage(foragingAreas),
            style = MaterialTheme.typography.bodySmall,
            modifier = modifier,
        )

        is ForagingAreas.Found -> Column(
            // fillMaxHeight, not heightIn(max = ...): the caller ([MapTab]) now wraps this whole
            // composable in a Box already fixed at FORAGING_AREAS_PANEL_MAX_HEIGHT regardless of
            // whether the layer is on, specifically so the map above never resizes when the
            // switch is toggled — filling that fixed height here (and scrolling within it) is
            // what makes the disclaimer/count caption and the area list behave as a footnote
            // rather than a wrap-content sibling that could still grow the space it's given.
            modifier = modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            // Tertiary rather than error: this is a caution about what the ordering isn't, not a
            // failure. Reusing the error color here would make a real failure (a failed sightings
            // fetch, a couldn't-load message) read as no more urgent than a standing disclaimer.
            Text(
                VISITING_ORDER_DISCLAIMER,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.tertiary,
            )
            if (foragingAreas.ungroupedObservationCount > 0) {
                Text(
                    "${foragingAreas.ungroupedObservationCount} scattered observations belong to no area.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Tapping a numbered marker on the map shows the same summary for that one area, so
            // this list staying a footnote here costs nothing.
            foragingAreas.areas.forEach { area -> ForagingAreaRow(area) }
        }
    }
}

/**
 * The footnote-not-competitor cap for [ForagingAreasPanel]. Bounds the disclaimer/count caption
 * together with the area list below it, so the panel as a whole stays small enough that the
 * mapSlot it shares a weighted region with keeps the larger share — see
 * [AvailabilityScreenLayoutTest]'s `MIN_MAP_SHARE_OF_SCREEN` for the map's own floor.
 */
private val FORAGING_AREAS_PANEL_MAX_HEIGHT = 60.dp

@Composable
private fun ForagingAreaRow(area: ForagingArea) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text("${area.visitOrder}", style = MaterialTheme.typography.titleMedium)
            Text(foragingAreaSummary(area), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * Why nothing was found, stated specifically. The clustering thresholds are never relaxed to
 * manufacture an area, so the honest answer here is sometimes "there isn't one".
 */
private fun noAreasMessage(none: ForagingAreas.None): String {
    val minPoints = ClusterForagingAreasUseCase.MIN_OBSERVATIONS_PER_AREA
    val radiusMeters = ClusterForagingAreasUseCase.NEIGHBORHOOD_RADIUS_METERS.toInt()
    return when (none.reason) {
        ForagingAreas.Reason.NO_OBSERVATIONS ->
            "No mapped observations in this radius, so there's nothing to group into areas. " +
                "Try a wider radius, a different month, or another category."

        ForagingAreas.Reason.TOO_FEW_OBSERVATIONS ->
            "Only ${none.observationsConsidered} mapped observation(s) in this radius — fewer than " +
                "the $minPoints it takes to call anywhere a repeat-producing area."

        ForagingAreas.Reason.NO_GROUP_MET_THRESHOLD ->
            "No repeat-producing areas found in this radius. All ${none.observationsConsidered} " +
                "mapped observations are scattered: none form a group of $minPoints or more within " +
                "${radiusMeters}m of each other. The threshold isn't loosened to find something."
    }
}

/**
 * Recent rainfall, shown as a standalone fact at the top of the ranked list — never described as
 * having factored into it. See [com.forager.app.domain.GetConditionsUseCase]'s doc comment for
 * why this stays unfused with the ranked list.
 */
@Composable
private fun ConditionsCard(conditions: ConditionsSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Current Conditions", style = MaterialTheme.typography.titleSmall)
            val totalMm = conditions.totalPrecipitationMm
            Text(
                "${"%.1f".format(totalMm)}mm of rain in the last 14 days",
                style = MaterialTheme.typography.bodyMedium,
            )
            val daysSince = conditions.daysSinceSignificantRain
            Text(
                when {
                    daysSince == null -> "No significant rain in the last 14 days."
                    daysSince == 0 -> "Rain today."
                    daysSince == 1 -> "1 day since last rain."
                    else -> "$daysSince days since last rain."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private val TRIP_WINDOW_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")

/**
 * Upcoming days that sit inside the stated post-rain lag range, next to the group's general
 * weather pattern.
 *
 * Two owned domain objects meet here and stay visually distinct: [TripWindowReport] is
 * measurements and date arithmetic only (see its own doc comment for why it must never grow a
 * score), and [ForagingWeatherGuidance] is the separately-stated rule of thumb that makes those
 * measurements interesting. The card shows both but never blends them into one sentence.
 *
 * Unlike [ConditionsCard], not gated to the browsed month: the days ahead of today are relevant
 * to planning a trip this week regardless of which month's species ranking is on screen.
 */
@Composable
private fun TripWindowsCard(uiState: AvailabilityUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text("Trip Windows", style = MaterialTheme.typography.titleSmall)

            when {
                uiState.isLoadingTripWindows -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                uiState.tripWindowsErrorMessage != null -> Text(
                    uiState.tripWindowsErrorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                uiState.tripWindowReport != null -> TripWindowReportContent(uiState.tripWindowReport)
            }

            HorizontalDivider()
            ForagingWeatherGuidanceSection(uiState.foragingSelection)
        }
    }
}

@Composable
private fun TripWindowReportContent(report: TripWindowReport) {
    if (report.windows.isEmpty()) {
        Text(noTripWindowMessage(report), style = MaterialTheme.typography.bodySmall)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        report.windows.forEach { window -> TripWindowRow(window) }
    }
}

@Composable
private fun TripWindowRow(window: TripWindow) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            "${TRIP_WINDOW_DATE_FORMAT.format(window.startDate)} – ${TRIP_WINDOW_DATE_FORMAT.format(window.endDate)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val mostRecentRain = window.precedingRainEvents.first()
        Text(
            "${window.daysAfterMostRecentRainAtStart}–${window.daysAfterMostRecentRainAtEnd} days after " +
                "${"%.0f".format(mostRecentRain.totalMm)}mm of rain ending " +
                TRIP_WINDOW_DATE_FORMAT.format(mostRecentRain.endDate) +
                if (mostRecentRain.isForecast) " (forecast)" else "",
            style = MaterialTheme.typography.bodySmall,
        )
        if (window.precipitationDuringWindowMm > 0.0) {
            Text(
                "${"%.1f".format(window.precipitationDuringWindowMm)}mm more rain forecast during the window",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanShallowSoilMoistureM3M3?.let { moisture ->
            Text(
                "Shallow soil moisture: ${"%.2f".format(moisture)} m³/m³",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        window.meanSoilTemperatureC?.let { temp ->
            Text("Soil temperature: ${"%.1f".format(temp)}°C", style = MaterialTheme.typography.bodySmall)
        }
        window.evapotranspirationSinceRainMm?.let { et0 ->
            Text(
                "${"%.1f".format(et0)}mm evapotranspiration since the rain",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Why no window was found, stated specifically with the numbers behind it — never a bare "none
 * found" (CLAUDE.md: partial or empty results are reported as such).
 */
private fun noTripWindowMessage(report: TripWindowReport): String = when (val reason = report.noWindowReason) {
    is NoTripWindowReason.NoQualifyingRainEvent ->
        "No run of rain in the last ${reason.daysExamined} days totaled the " +
            "${"%.0f".format(reason.requiredTotalMm)}mm this search treats as a soaking event — the " +
            "wettest run reached ${"%.0f".format(reason.largestRunTotalMm)}mm."

    is NoTripWindowReason.LagRangeOutsideHorizon ->
        "The most recent qualifying rain ended ${TRIP_WINDOW_DATE_FORMAT.format(reason.mostRecentEventEnd)}. " +
            "The ${FruitingPatternAssumptions.FRUITING_LAG_DAYS.first}–" +
            "${FruitingPatternAssumptions.FRUITING_LAG_DAYS.last} day window it points to is " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeStart)}–" +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.lagRangeEnd)}, past the " +
            "${TRIP_WINDOW_DATE_FORMAT.format(reason.horizonEnd)} horizon this search plans within."

    is NoTripWindowReason.NoForecastDays ->
        "No forecast days were returned for this location, so there's nothing to plan against."

    null -> "" // Unreachable: report.windows.isEmpty() implies a non-null reason.
}

/**
 * The general weather pattern for the current selection, stated as a rule of thumb next to the
 * measurements above it — never combined with them into a score. See
 * [ForagingWeatherGuidance]'s doc comment for the rules this enforces.
 */
@Composable
private fun ForagingWeatherGuidanceSection(selection: ForagingSelection) {
    val guidance = ForagingWeatherGuidance.forSelection(selection)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        // labelMedium + a muted color, not titleSmall/labelLarge: Material3 sizes titleSmall and
        // labelLarge identically (14sp/500), so this heading and the card's own "Trip Windows"
        // title above it were reading as the same weight despite one being nested inside the
        // other. This is deliberately a step down from the card title, not a second one beside it.
        Text(
            guidance.heading,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        guidance.paragraphs.forEach { paragraph ->
            Text(paragraph, style = MaterialTheme.typography.bodySmall)
        }
        guidance.speciesDataCaveat?.let { caveat ->
            Text(caveat, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
        }
    }
}

@Composable
private fun SpeciesRow(entry: AvailabilityEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Text(
                entry.species.commonName ?: entry.species.scientificName,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                entry.species.scientificName,
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
            )
            LinearProgressIndicator(
                progress = { entry.relativeLikelihood },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "${entry.species.observationCount} observations",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
