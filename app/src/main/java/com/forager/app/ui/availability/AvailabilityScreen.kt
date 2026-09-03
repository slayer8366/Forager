package com.forager.app.ui.availability

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forager.app.BuildConfig
import com.forager.app.crash.CrashFileStore
import com.forager.app.domain.CachedSearchSummary
import com.forager.app.domain.CartographyEntryMapData
import com.forager.app.domain.CompassProvider
import com.forager.app.domain.CurrentTimeProvider
import com.forager.app.domain.ForagingSelection
import com.forager.app.domain.ForagingWeatherGuidance
import com.forager.app.domain.FruitingPatternAssumptions
import com.forager.app.domain.MgrsConverter
import com.forager.app.domain.OfflineMapRepository
import com.forager.app.domain.OfflineRegionSummary
import com.forager.app.domain.SystemCurrentTimeProvider
import com.forager.app.domain.estimateOfflineTileCount
import com.forager.app.domain.isOfflineRegionStale
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.AvailabilityEntry
import com.forager.app.domain.model.CartographyEntry
import com.forager.app.domain.model.ConditionsSummary
import com.forager.app.domain.model.DailyWeather
import com.forager.app.domain.model.DistanceUnit
import com.forager.app.domain.model.formatDistanceKm
import com.forager.app.domain.model.FruitingLagBucket
import com.forager.app.domain.model.FruitingLagDistribution
import com.forager.app.domain.model.GalleryPhoto
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.MgrsCoordinate
import com.forager.app.domain.model.MushroomLogEntry
import com.forager.app.domain.model.NoTripWindowReason
import com.forager.app.domain.model.PhotoSource
import com.forager.app.domain.model.PlannedTrip
import com.forager.app.domain.model.Region
import com.forager.app.domain.model.ReturnToStartInfo
import com.forager.app.domain.model.Sighting
import com.forager.app.domain.model.TaxonFilter
import com.forager.app.domain.model.TaxonSearchResult
import com.forager.app.domain.model.Track
import com.forager.app.domain.model.TripWindow
import com.forager.app.domain.model.TripWindowReport
import com.forager.app.domain.model.Waypoint
import com.forager.app.photo.CameraCaptureFiles
import com.forager.app.sensor.AndroidCompassProvider
import com.forager.app.ui.adaptive.WindowWidthClass
import com.forager.app.ui.adaptive.currentWindowWidthClass
import com.forager.app.ui.crash.CrashLogPanel
import com.forager.app.ui.crash.CrashLogsEntryRow
import com.forager.app.ui.log.CartographyUiState
import com.forager.app.ui.log.JournalTab
import com.forager.app.ui.log.LogPanel
import com.forager.app.ui.log.MushroomLogUiState
import com.forager.app.ui.log.PendingJournalDestination
import com.forager.app.ui.log.PhotoGalleryScreen
import com.forager.app.ui.map.Basemap
import com.forager.app.ui.map.CentrePinLocationPicker
import com.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.forager.app.ui.map.MapMode
import com.forager.app.ui.map.MapOverlayContent
import com.forager.app.ui.map.MapSlot
import com.forager.app.ui.map.SightingsMapSlot
import com.forager.app.ui.motion.MotionTokens
import com.forager.app.ui.map.MapRenderMode
import com.forager.app.ui.theme.Bark
import com.forager.app.ui.theme.Cream
import com.forager.app.ui.theme.LocalForagerDarkTheme
import com.forager.app.ui.theme.MapIconBarAccent
import com.forager.app.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class ResultsTab(val label: String) {
    LIST("List"),
    MAP("Maps"),
    SEASONAL("Seasonal"),
}

/**
 * The compact bottom nav's five destinations, in trip order left to right — Pre-trip surfaces
 * (List, Seasonal) then the surface the user is actually in (Maps, a true centre — depends on
 * this being an odd count; a sixth destination would break the centring), then Post-trip and rare
 * (Journal, Tools). [ResultsTab] itself stays a 3-way enum, unchanged, since the medium/expanded
 * window's tab row still switches only between List/Maps/Seasonal, kept in sync with this enum's
 * own `selectedTab` (see [AvailabilityScreen]'s `ForagerBottomNav` call site) whenever the tapped
 * destination is one of those three.
 *
 * **Album is not a destination here any more.** It folds into [JOURNAL] as a third top tab
 * alongside Log/Drafts (see [LogGalleryScreen]'s own doc comment) — Album and log entries share a
 * phase and a frequency (per-find capture at the specimen, Post-trip review at home), so they
 * belong in one destination rather than two separate ones that only existed apart because they
 * were built apart.
 *
 * **[TOOLS] does not behave like the other four.** Tapping it never sets `compactTab` to
 * [TOOLS] — see [ForagerBottomNav]'s own call site — it opens [CompactToolsDrawerContent] as an
 * overlay instead, over whatever tab was already showing, the same drawer the removed MapIconBar
 * search icon used to open (that icon is gone — see `MapIconBar`'s own doc comment — this tab is
 * its replacement entry point, not a new destination alongside it). "Tools" is deliberately a
 * catch-all for things used but not wanted in the immediate way — per-trip and rare items that are
 * not destinations in their own right (trip planner, waypoints, and now Settings
 * too — see that composable's own doc comment). Search itself — basic species/category search,
 * Recent Searches, and Advanced Search — is **not** part of that list any more: it moved out into
 * [SearchDropdown], reached from [ActiveSearchSummary] up top rather than from this drawer, exactly
 * so it would not read as one more Tools entry. That inclusion rule is what the next addition here
 * has to argue against becoming a junk drawer.
 */
private enum class CompactTab(val label: String) {
    LIST("List"),
    SEASONAL("Seasonal"),
    MAP("Maps"),
    JOURNAL("Journal"),
    TOOLS("Tools"),
}

/** The compact bottom nav's icon per destination — see [ForagerBottomNav]. */
private fun CompactTab.icon(): ImageVector = when (this) {
    CompactTab.LIST -> Icons.AutoMirrored.Filled.List
    CompactTab.SEASONAL -> Icons.Filled.WbSunny
    CompactTab.MAP -> Icons.Filled.Map
    CompactTab.JOURNAL -> Icons.Filled.MenuBook
    // Same icon Settings itself used before this dispatch folded it into Tools' own drawer — no
    // "tools/build" icon exists in this app's icon set (only the core Material icons module is a
    // dependency, not the extended one a wrench/handyman glyph would need), and Settings is still
    // genuinely part of what this destination opens.
    CompactTab.TOOLS -> Icons.Filled.Settings
}

/**
 * The medium/expanded window's drawer panels — see [AvailabilityScreen]'s doc comment on
 * `drawerPanel` for how they're switched between. [Settings] and [Log] are both reached from
 * sticky entries at the bottom of [Search] (see [SettingsEntryRow]/`MushroomLogEntryRow`).
 * Closing the drawer entirely resets all the way back to [Search] regardless of which panel was
 * showing.
 *
 * **No longer holds [OfflineMaps] or `Tracks`.** Journal restructure Stage 1 moved both into the
 * Journal's own Records tab ([RecordsTab] in `ui/log/`) — [Settings] now goes straight to
 * [CrashLogs] as its only remaining submenu.
 *
 * [Log] additionally opens directly — bypassing [Search] — from the map's "Log a find" option
 * (chosen from [ThreeWayActionDialog], then placed via [com.forager.app.ui.map.CentrePinLocationPicker]);
 * see [MapTab]'s `onLogFindHere` call site in [AvailabilityScreen].
 *
 * **Compact windows no longer use this at all.** [Log] moved to the bottom nav
 * ([CompactTab.JOURNAL] — see [ForagerBottomNav]'s doc comment); [Settings] is reached one level
 * deeper still, from a sticky entry at the bottom of the compact drawer's own content (see
 * [CompactToolsDrawerContent]'s `showSettings` state, which hosts [CompactSettingsTab] the same
 * way this enum's own [Settings] does here). That compact drawer is now [CompactTab.TOOLS]'s
 * destination (map/navigation redesign dispatch B) rather than search-only, so unlike [Search]
 * above it is not the drawer's single fixed content — [Settings] there is a nested state within
 * it, not a sibling reached by closing and reopening. This enum, [drawerSheetContent], and the
 * `PermanentNavigationDrawer` it feeds stay exactly as they were before any of that — untouched,
 * medium/expanded-only — see docs/plans/map-redesign.md's "Scope decision" section.
 */
private enum class DrawerPanel {
    Search,
    Settings,
    CrashLogs,
    Log,
    // Workstream G2 (`docs/plans/pr26-rework.md`): the medium/expanded half of the gallery's
    // top-level destination — see PhotoGalleryScreen's own doc comment. No longer a
    // both-window-classes destination as of map/navigation redesign dispatch B: the compact side
    // folded into LogGalleryScreen's own Album tab (reached via CompactTab.JOURNAL) rather than
    // keeping a standalone compact counterpart.
    PhotoGallery,
}

/** How long a first back press keeps "exit on the next one" armed — see [AvailabilityScreen]. */
private const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L

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
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onReopenTaxonSuggestions: () -> Unit,
    /** Called when a date and name are confirmed for a trip pin placed via [com.forager.app.ui.map.CentrePinLocationPicker]. */
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    /**
     * Called when one of the recent searches is tapped; see [RecentSearchesSection]. Reached from
     * the medium/expanded drawer's own [DrawerPanel.Search] panel, or from [SearchDropdown] on
     * compact windows.
     */
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
    /** Set by panning the picker map to the centre pin and confirming, in the Offline Maps submenu — see `OfflineMapsPanel`. */
    onOfflineMapLatChanged: (String) -> Unit,
    onOfflineMapLngChanged: (String) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onOfflineMapsOpened: () -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
    /** Settings' "Night Maps" checkbox — see [AvailabilityUiState.nightModeMaps]'s own doc comment. */
    onNightModeMapsChanged: (Boolean) -> Unit,
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    onThemeModeChanged: (AppThemeMode) -> Unit,
    /**
     * The mushroom log drawer destination's own state — see [com.forager.app.ui.log.LogPanel].
     * Defaulted, like [mapSlot] below, so the many existing tests of this screen that have nothing
     * to do with the log don't need to pass log-specific state and callbacks just to compile.
     */
    logUiState: MushroomLogUiState = MushroomLogUiState(),
    cameraCaptureFiles: CameraCaptureFiles = CameraCaptureFiles(LocalContext.current),
    /** Starts and immediately opens a new log entry — the map's "Log a find" option is the only production caller; entries have no other creation path (see `docs/plans/mushroom-log.md`'s Navigation section). */
    onStartLogEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> },
    onOpenLogEntry: (String) -> Unit = {},
    onCloseLogEntry: () -> Unit = {},
    onLogEntryChanged: (MushroomLogEntry) -> Unit = {},
    /** Begins editing the currently-open (committed) entry — see [com.forager.app.ui.log.MushroomLogViewModel.onStartEditingEntry]'s own doc comment. A no-op if it's already a draft. */
    onStartEditingLogEntry: () -> Unit = {},
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([com.forager.app.ui.log.MushroomLogViewModel.onOpenEntryForEditing]),
     * not [onOpenLogEntry] and [onStartEditingLogEntry] chained here. [LogPanel]'s own entry list
     * is the one caller (see that composable's own doc comment on why its "no report step" shape
     * needs this instead of the two-callback chain [JournalTab] still uses).
     */
    onOpenLogEntryForEditing: (String) -> Unit = {},
    /** Save — commits the currently-open entry. See [com.forager.app.ui.log.MushroomLogViewModel.onSaveEntry]'s own doc comment. */
    onSaveLogEntry: () -> Unit = {},
    /** Cancel — the only exit that discards anything. See [com.forager.app.ui.log.MushroomLogViewModel.onCancelEditing]'s own doc comment. */
    onCancelLogEntryEditing: () -> Unit = {},
    /** Leaving without answering — tab switch, backgrounding, back — persists the draft without committing. See [com.forager.app.ui.log.MushroomLogViewModel.onLeaveEditingIncidentally]'s own doc comment. */
    onLeaveLogEntryEditingIncidentally: () -> Unit = {},
    /** Discards a draft by id outright — the compact scaffold's "Discard" Snackbar action target, since by the time it's tapped [logUiState].editingEntry is already null. Same operation as [onDeleteLogEntry]. */
    onDiscardLogDraft: (String) -> Unit = {},
    onAddLogPhoto: (PhotoSource) -> Unit = {},
    onRemoveLogPhoto: (LogPhoto) -> Unit = {},
    onPullLogPhoto: (LogPhoto) -> Unit = {},
    onDeleteLogEntry: (String) -> Unit = {},
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    /** Standalone-photos dispatch: Camera/Gallery acquisition, no owning find — [PhotoGalleryScreen]'s own buttons, both Album surfaces (Cartography's tab and [DrawerPanel.PhotoGallery]). */
    onAddGalleryPhoto: (PhotoSource) -> Unit = {},
    /** Clears [logUiState]'s `saveErrorMessage` once its Toast has shown — see [LogPanel]/[JournalTab]'s identical parameter. */
    onSaveLogErrorDismissed: () -> Unit = {},
    /** Journal Stage 2b's new authored entity — see [com.forager.app.ui.log.CartographyScreen]'s own doc comment for all of the following. Defaulted, same reasoning as [logUiState]. */
    cartographyUiState: CartographyUiState = CartographyUiState(),
    onOpenCartographyEntry: (String) -> Unit = {},
    onStartCartographyEntry: (LocalDate) -> Unit = {},
    onCloseCartographyEntry: () -> Unit = {},
    onCartographyTextChanged: (String) -> Unit = {},
    onCartographyTagsChanged: (List<String>) -> Unit = {},
    onSetFindDecision: (String, Boolean) -> Unit = { _, _ -> },
    onSetTrackDecision: (String, Boolean) -> Unit = { _, _ -> },
    onSetWaypointDecision: (String, Boolean) -> Unit = { _, _ -> },
    onSetOfflineRegionDecision: (Long, Boolean) -> Unit = { _, _ -> },
    onToggleKeptPhoto: (String) -> Unit = {},
    onFinishCartographyEntry: () -> Unit = {},
    /** Explicit Save for a committed Cartography entry — device-check patch, Item 1. Threaded straight through to CartographyScreen, whose own lifecycle observer also uses it for the backgrounding-return prompt's Commit option (pending-edit-and-fixes dispatch, Item 1). */
    onSaveCartographyEntry: () -> Unit = {},
    /** The leave-prompt's Discard option — device-check patch, Item 1. */
    onDiscardCartographyEntryChanges: () -> Unit = {},
    /** The backgrounding-return prompt's "Save as draft" option — pending-edit-and-fixes dispatch, Item 1. See CartographyScreen's own lifecycle-observer doc comment. */
    onSaveCartographyEntryAsDraft: () -> Unit = {},
    onDeleteCartographyEntry: (String) -> Unit = {},
    /**
     * [com.forager.app.ui.log.CartographyEntryReportScreen]'s own map, Stage 2d — see that
     * composable's doc comment. Defaulted to always report nothing resolved, same reasoning as
     * [logUiState]: the many existing tests of this screen that never open a Cartography entry
     * don't need to pass a real resolver just to compile.
     */
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData = { _, _ ->
        CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    },
    /**
     * [com.forager.app.ui.log.CartographyEntryReportScreen]'s own offline-map toggle, Stage 2e-i —
     * see that composable's doc comment. Defaulted for the same reason [getCartographyEntryMapData]
     * is: the many existing tests of this screen that never open a Cartography entry don't need a
     * real resolver just to compile.
     */
    getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary? = { _, _ -> null },
    /**
     * The compact map icon stack's GPS/locate-me button. Distinct from [onUseCurrentLocation] —
     * see [LocateMeStatus]'s doc comment — and, like it, defers the OS permission dialog to the
     * Activity (see `MainActivity`'s `pendingLocationAction`) rather than requesting it here.
     */
    onLocateMe: () -> Unit = {},
    /**
     * Whether a track is currently being recorded, and [MapIconBar]'s start/stop toggle for it —
     * that bar's 5th row (fullscreen, orientation-reset, GPS/locate-me, map mode, **record**,
     * return-to-vehicle, search, add), not the compass/elevation/MGRS strip: record and
     * return-to-vehicle moved out of the strip and into the icon bar on 2026-08-26 (see
     * `map-redesign.md`'s "Icon stack: superseded from 5 to a 7-icon stopgap" section), and the
     * bar itself grew from floating circles to one panel bar the same session (see [MapIconBar]'s
     * own doc comment for the current 8-row shape). **Corrected 2026-08-28** — this comment
     * previously described the pre-2026-08-26 placement. See `MainActivity`'s `LaunchedEffect` on
     * [isRecording] for what actually starts/stops [com.forager.app.service.TrackRecordingService].
     */
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    /** Set when the most recent [onToggleRecording]-triggered start failed or was refused — shown as a one-shot Toast in [CompactMapTab], the same [LaunchedEffect]-on-a-status-field shape as its existing `locateMeStatus` Toast. */
    startRecordingErrorMessage: String? = null,
    /**
     * The active track's recorded points, oldest first — see [com.forager.app.ui.map.MapSlot]'s own
     * doc comment on this same parameter for how it's drawn. Empty whenever [isRecording] is false.
     */
    breadcrumbPoints: List<LatLng> = emptyList(),
    /**
     * Saved waypoints — independent of any track recording, per [Waypoint]'s own doc comment.
     * Rendered as pins on the map ([com.forager.app.ui.map.MapSlot]) and listed, with delete, in
     * the Tools drawer's "Waypoints" section ([WaypointsSection]).
     */
    waypoints: List<Waypoint> = emptyList(),
    /** Set when the most recent waypoint load/add/remove failed — shown, with error color, in [WaypointsSection] in place of the list. */
    waypointsErrorMessage: String? = null,
    /** How many Cartography entries currently keep a reference to each waypoint (by id) — Journal Stage 2b's 4b deletion warning, shown in [WaypointsSection]'s own confirm dialog. */
    waypointEntryReferenceCounts: Map<String, Int> = emptyMap(),
    /** Called with the placed location and the confirmed name when "Drop a waypoint" is chosen from [ThreeWayActionDialog] — see [WaypointNameDialog]. */
    onDropWaypoint: (LatLng, String) -> Unit = { _, _ -> },
    onDeleteWaypoint: (String) -> Unit = {},
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
    /**
     * The Settings tab's crash-log diagnostic surface — see [CrashLogPanel]. Defaults to the real
     * on-device store, same [mapSlot]/[cameraCaptureFiles]/[compassProvider] pattern above.
     */
    crashFileStore: CrashFileStore = CrashFileStore.forContext(LocalContext.current),
    /** The Settings panel's km/mi toggle — see [AvailabilityUiState.distanceUnit]'s own doc comment for why this is persisted rather than session-local state. */
    onDistanceUnitSelected: (DistanceUnit) -> Unit = {},
    /**
     * Every recorded track, for the Settings "Recorded Tracks" export panel — see
     * [com.forager.app.ui.track.TrackExportPanel]'s own doc comment. Empty by default, same
     * [breadcrumbPoints]/[waypoints] shape above: the real list is [trackUiState.tracks][com.forager.app.ui.track.TrackRecordingUiState.tracks],
     * threaded in by `MainActivity`.
     */
    tracks: List<Track> = emptyList(),
    /** Refreshes [tracks] — called whenever the export panel opens, mirroring [onOfflineMapsOpened]'s own "reload on open" shape. */
    onTracksOpened: () -> Unit = {},
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

    // Device-check patch, Items 2/3: whether a find's camera/gallery round-trip is currently in
    // flight, reported up from whichever of JournalTab/LogPanel is composed via
    // onPhotoAcquisitionInFlightChanged (see LogEntryDetailScreen's own doc comment on that
    // parameter). Read by this screen's own ON_STOP hook below, to suppress the "user backgrounded
    // the app" incidental-exit heuristic while the backgrounding is this app's own doing.
    var logPhotoAcquisitionInFlight by remember { mutableStateOf(false) }

    // "View on Map" on a List-tab species row: which taxon (if any) the map tabs should limit
    // their sightings to. Lives here, alongside selectedTab/compactTab, because both the List and
    // Map tabs read and clear it and neither is an ancestor of the other in either window-class
    // layout (CombinedResultsPane and compactMainScaffold each show only one at a time, but both
    // are built from this same function's state). Null means "no filter" — the ordinary, every
    // sighting view.
    var mapTaxonFilter by remember { mutableStateOf<Long?>(null) }

    // Sets the filter and jumps to whichever map surface the current window class actually shows —
    // both selectedTab and compactTab are updated unconditionally rather than branching on
    // windowWidthClass here, since only the one the active layout reads has any effect; see
    // CompactTab's own doc comment for why the two are kept as separate state instead of one.
    val onViewSpeciesOnMap: (Long) -> Unit = { taxonId ->
        mapTaxonFilter = taxonId
        compactTab = CompactTab.MAP
        selectedTab = ResultsTab.MAP
    }

    val onClearMapTaxonFilter: () -> Unit = {
        mapTaxonFilter = null
    }

    // Local remembered state, same reasoning as selectedTab/mapMode below: purely a display
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
    // One MapMode value, not the earlier two-piece service+mode split — see MapMode's own doc
    // comment for what that split used to buy and why it no longer applies.
    //
    // The cost, stated rather than hidden: like selectedTab, this resets to its default on process
    // death. Persisting it needs somewhere to persist *to*, and adding a Room table or a DataStore
    // for one small piece of display-only state is the speculative build CLAUDE.md warns against.
    var mapMode by remember { mutableStateOf(MapMode.DEFAULT) }
    val basemap = mapMode.basemap

    // Night mode: Settings' "Night Maps" checkbox, a direct persistent preference
    // (uiState.nightModeMaps, backed by MapPreferencesRepository.getNightModeMaps/setNightModeMaps)
    // rather than derived from time of day. Replaces this map's earlier civil-twilight-automatic/
    // long-press-hold control (MapNightMode) per the project owner's own request to move night
    // mode to a plain Settings checkbox instead.
    val isNightMode = uiState.nightModeMaps
    val mapRenderMode = MapRenderMode(basemap = basemap, night = isNightMode)
    // Persisted via the ViewModel/DataStore — see AvailabilityUiState.distanceUnit's own doc
    // comment. mapMode above is still session-local; see the observation in that same doc comment.
    val distanceUnit = uiState.distanceUnit
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
    // Read once here, reused below and inside compactMainScaffold's own showQuickSearch handling —
    // both close points need the same "actually dismiss the IME" fix, not just clear this
    // composable's own idea of which panel is showing. Neither call was made anywhere in this app
    // before this fix (grepped the whole tree) — closing a panel that held focus (the drawer's own
    // search fields, the quick-search species field) unmounts the focused TextField, which doesn't
    // reliably hide the IME on every device/OEM on its own; a stray back press or drawer-close could
    // leave a device's keyboard visibly "stuck" over whatever's shown next.
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var isDrawerOpen by remember { mutableStateOf(false) }

    // Stage 2d's routing fix: a one-shot request into whichever of LogPanel/JournalTab is about to
    // show, set by both onLogFindHere closures below alongside their existing drawerPanel/compactTab
    // switch — see JournalTab's own doc comment, "The map '+' routing bug," for why this exists and
    // why it stays a single-purpose token rather than a shared navigation type. One instance covers
    // both window classes: only whichever of LogPanel/JournalTab is actually composed reads it.
    var pendingJournalDestination by remember { mutableStateOf<PendingJournalDestination?>(null) }
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
            // Reset to the Search panel on every close — scrim tap, back button, or a search action
            // that closes the drawer itself — rather than leaving Settings showing the next time the
            // drawer opens. A minor, easily-revisited default: see this task's own notes.
            drawerPanel = DrawerPanel.Search
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
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
    // reachable while fullscreen — see MapIconBar). Corrected 2026-08-28: named MapIconStack here
    // before that composable was renamed.
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

    // Workstream L4b-R2: the one wrapped "leaving without answering" callback, hoisted here (rather
    // than declared separately inside compactMainScaffold and again wherever the drawer's own
    // LogPanel needed it) so "every in-app exit offers a discard action" stays one fact about one
    // callback shared by every caller — the compact bottom nav's JournalTab, its own tab-switch
    // handler, and DrawerPanel.Log's LogPanel below — rather than N independently-maintained copies.
    // Backgrounding is the deliberate, sole exception: see compactMainScaffold's own
    // DisposableEffect, which calls the *raw* onLeaveLogEntryEditingIncidentally directly, never this
    // wrapper, since there is no window left to show a Snackbar in by the time that fires.
    val logDraftSnackbarHostState = remember { SnackbarHostState() }
    val logDraftSnackbarScope = rememberCoroutineScope()
    val leaveLogEntryEditingOfferingDiscard: () -> Unit = {
        val discardedId = logUiState.editingEntry?.id
        onLeaveLogEntryEditingIncidentally()
        if (discardedId != null) {
            logDraftSnackbarScope.launch {
                val result = logDraftSnackbarHostState.showSnackbar(
                    message = "Saved to Drafts",
                    actionLabel = "Discard",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) onDiscardLogDraft(discardedId)
            }
        }
    }

    // Workstream L4c pre-work: corrects this comment's own claim, found stale by the L4 close-out
    // pulse (2026-08-25). This has exactly one call site — the `PermanentNavigationDrawer` medium+
    // windows get, below — not the compact `ModalNavigationDrawer`, which renders
    // `CompactToolsDrawerContent` instead, a separate composable. [showCloseButton] is therefore
    // always `false` in practice today; it is not dead code removed here only because that is a
    // separate cleanup this pulse's own dispatch did not ask for, not because it still does
    // anything.
    //
    // A local composable lambda rather than a top-level one so it closes over this function's ~25
    // params and local state directly instead of re-threading all of it through an explicit
    // parameter list a second time. ColumnScope receiver, not a plain function type: the drawer
    // sheet that hosts this hands it a ColumnScope (that's what lets the `Modifier.weight(1f)`
    // calls inside resolve at all), and a lambda assigned to a receiver-typed val keeps that
    // receiver rather than losing it.
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
                    onRecentSearchSelected = { summary ->
                        // Closed for the same reason searching from this drawer closes it:
                        // the tap starts a search, and the results are behind the sheet.
                        isDrawerOpen = false
                        onRecentSearchSelected(summary)
                    },
                    currentTime = currentTime,
                )
                // Sticky footer rows: the log is the newer of the two pre-existing ones, placed
                // above Settings so it isn't the last thing in the sheet — see
                // MushroomLogEntryRow. The photo gallery (Workstream G2) joins right below it,
                // the other mushroom-log-area destination.
                MushroomLogEntryRow(onClick = { drawerPanel = DrawerPanel.Log })
                PhotoGalleryEntryRow(onClick = { drawerPanel = DrawerPanel.PhotoGallery })
                // Occupies the search panel's old sticky-footer slot — BuildIdentityFooter
                // moved to the bottom of the Settings panel below.
                SettingsEntryRow(onClick = { drawerPanel = DrawerPanel.Settings })
            }

            DrawerPanel.Settings -> {
                SettingsHeader(onBack = { drawerPanel = DrawerPanel.Search })
                SettingsContent(
                    modifier = Modifier.weight(1f),
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = onDistanceUnitSelected,
                    themeMode = uiState.themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    nightModeMaps = uiState.nightModeMaps,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    onOpenCrashLogs = { drawerPanel = DrawerPanel.CrashLogs },
                )
                BuildIdentityFooter()
            }

            DrawerPanel.CrashLogs -> {
                // Back returns to Settings, one level up — not all the way to Search.
                CrashLogPanel(
                    modifier = Modifier.weight(1f),
                    files = crashFileStore.list(),
                    onBack = { drawerPanel = DrawerPanel.Settings },
                )
            }

            DrawerPanel.Log -> {
                LogPanel(
                    modifier = Modifier.weight(1f),
                    uiState = logUiState,
                    cameraCaptureFiles = cameraCaptureFiles,
                    mapSlot = mapSlot,
                    region = uiState.region ?: JOURNAL_PICKER_DEFAULT_REGION,
                    basemap = basemap,
                    night = isNightMode,
                    onOpenEntryForEditing = onOpenLogEntryForEditing,
                    onCloseEntry = onCloseLogEntry,
                    onEntryChanged = onLogEntryChanged,
                    onSaveEntry = onSaveLogEntry,
                    onCancelEditing = onCancelLogEntryEditing,
                    // Workstream L4b-R2: shares the same wrapped callback as the compact bottom
                    // nav's JournalTab — see this function's own top-level construction of
                    // leaveLogEntryEditingOfferingDiscard for why one callback, not a
                    // window-class-specific copy. The PermanentNavigationDrawer's own drawer sheet
                    // (below) hosts the Snackbar this shows.
                    onLeaveEditingIncidentally = leaveLogEntryEditingOfferingDiscard,
                    onPhotoAcquisitionInFlightChanged = { inFlight -> logPhotoAcquisitionInFlight = inFlight },
                    onAddPhoto = onAddLogPhoto,
                    onRemovePhoto = onRemoveLogPhoto,
                    onPullPhoto = onPullLogPhoto,
                    onDeleteEntry = onDeleteLogEntry,
                    onBackToSearch = { drawerPanel = DrawerPanel.Search },
                    onSaveErrorDismissed = onSaveLogErrorDismissed,
                    galleryPhotos = logUiState.galleryPhotos,
                    isLoadingGalleryPhotos = logUiState.isLoadingGalleryPhotos,
                    onDeleteGalleryPhoto = onDeleteGalleryPhoto,
                    onAddGalleryPhoto = onAddGalleryPhoto,
                    galleryLoadErrorMessage = logUiState.galleryLoadErrorMessage,
                    galleryPhotoEntryReferenceCounts = logUiState.cartographyEntryPhotoReferenceCounts,
                    cartographyUiState = cartographyUiState,
                    onOpenCartographyEntry = onOpenCartographyEntry,
                    onStartCartographyEntry = onStartCartographyEntry,
                    onCloseCartographyEntry = onCloseCartographyEntry,
                    onCartographyTextChanged = onCartographyTextChanged,
                    onCartographyTagsChanged = onCartographyTagsChanged,
                    onSetFindDecision = onSetFindDecision,
                    onSetTrackDecision = onSetTrackDecision,
                    onSetWaypointDecision = onSetWaypointDecision,
                    onSetOfflineRegionDecision = onSetOfflineRegionDecision,
                    onToggleKeptPhoto = onToggleKeptPhoto,
                    onFinishCartographyEntry = onFinishCartographyEntry,
                    onSaveCartographyEntry = onSaveCartographyEntry,
                    onDiscardCartographyEntryChanges = onDiscardCartographyEntryChanges,
                    onSaveCartographyEntryAsDraft = onSaveCartographyEntryAsDraft,
                    onDeleteCartographyEntry = onDeleteCartographyEntry,
                    getCartographyEntryMapData = getCartographyEntryMapData,
                    getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
                    // Journal restructure Stage 1: the Records tab's three submenus — see
                    // RecordsTab's own doc comment. availabilityUiState is what OfflineMapsPanel
                    // reads its offline-map-specific fields off; distanceUnit/currentTime are the
                    // same values SearchControls/CompactSettingsTab already used for it.
                    availabilityUiState = uiState,
                    distanceUnit = distanceUnit,
                    currentTime = currentTime,
                    onOfflineMapLatChanged = onOfflineMapLatChanged,
                    onOfflineMapLngChanged = onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = onOfflineMapNameChanged,
                    onOfflineMapsOpened = onOfflineMapsOpened,
                    onDownloadOfflineMaps = onDownloadOfflineMaps,
                    onDeleteOfflineRegion = onDeleteOfflineRegion,
                    tracks = tracks,
                    onTracksOpened = onTracksOpened,
                    waypoints = waypoints,
                    waypointsErrorMessage = waypointsErrorMessage,
                    onDeleteWaypoint = onDeleteWaypoint,
                    waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                    pendingDestination = pendingJournalDestination,
                    onPendingDestinationConsumed = { pendingJournalDestination = null },
                )
            }

            DrawerPanel.PhotoGallery -> {
                // Back returns all the way to Search, same as DrawerPanel.Log — there's no
                // intermediate panel between this and Search the way Settings has OfflineMaps.
                PhotoGalleryHeader(onBack = { drawerPanel = DrawerPanel.Search })
                PhotoGalleryScreen(
                    modifier = Modifier.weight(1f),
                    photos = logUiState.galleryPhotos,
                    isLoading = logUiState.isLoadingGalleryPhotos,
                    onDeletePhoto = onDeleteGalleryPhoto,
                    cameraCaptureFiles = cameraCaptureFiles,
                    onAddGalleryPhoto = onAddGalleryPhoto,
                    loadErrorMessage = logUiState.galleryLoadErrorMessage,
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
                    // Stage 2d: lands LogPanel on Records -> Finds for the entry onStartLogEntry is
                    // about to create — see JournalTab's own doc comment, "The map '+' routing bug."
                    pendingJournalDestination = PendingJournalDestination.EDIT_NEW_FIND
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
                        renderMode = mapRenderMode,
                        mapMode = mapMode,
                        onMapModeSelected = { mapMode = it },
                        onPlaceTripPin = onPlaceTripPin,
                        onLogFindHere = onLogFindHere,
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        onDropWaypoint = onDropWaypoint,
                        taxonFilter = mapTaxonFilter,
                        onClearTaxonFilter = onClearMapTaxonFilter,
                        onViewOnMap = onViewSpeciesOnMap,
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
    // No top app bar at all any more: the species/category search bar that used to live there has
    // moved twice since — first into the Tools drawer (per the project owner's own framing at the
    // time, "the whole side panel is the search feature"), then out again into [SearchDropdown]
    // alongside Recent Searches (dispatch C's own owner call, "move recent searches, and species
    // search... into the new search drawer" — see [SearchDropdown]'s own doc comment for the
    // resulting shape). [ActiveSearchSummary] is what remains visible up top: a read-only "what am
    // I currently searching" strip whose own tap opens [SearchDropdown] (`onToggleSearch`) — not a
    // second way to reopen the species suggestion popup, which is why `onReopenTaxonSuggestions` is
    // a no-op here.
    val compactMainScaffold: @Composable () -> Unit = {
        // SearchDropdown, under ActiveSearchSummary — see that composable's own onToggleSearch doc
        // comment. Local to this scaffold, not AvailabilityUiState: which panel is showing is a
        // display decision the ViewModel has no part in, same reasoning as mapMode/drawerPanel above.
        var showSearchDropdown by remember { mutableStateOf(false) }
        // Same measured clearance [CompactMapTab] already computes for its own compass strip (see
        // that composable's own compassStripClearance doc comment) — recomputed here rather than
        // threaded through as a parameter, since it depends only on MaterialTheme.typography and
        // this scaffold's own LocalDensity, not on anything CompactMapTab measures live. Used below
        // to keep SearchDropdown's own opaque panel starting below the strip rather than painting
        // over it, on the Map tab specifically — the project owner's own direct ask: "have the
        // search window extend just below the compass strip to avoid overlaying it so people can
        // still track it if needed."
        val searchDropdownCompassStripTextMeasurer = rememberTextMeasurer()
        val searchDropdownCompassStripLabelStyle = MaterialTheme.typography.labelMedium
        val searchDropdownCompassStripDensity = LocalDensity.current
        val compassStripClearance = remember(searchDropdownCompassStripLabelStyle, searchDropdownCompassStripDensity) {
            with(searchDropdownCompassStripDensity) {
                searchDropdownCompassStripTextMeasurer.measure("Mg", searchDropdownCompassStripLabelStyle).size.height.toDp()
            }
        }
        // SearchEntryBar's own real rendered height, on the Map tab where it now composes as an
        // overlay above the map (this scaffold's own CompactMapTab call site below) rather than
        // in normal document flow — CompactMapTab needs it as a top inset so the compass strip,
        // the observation bubble's own minY, and the taxon-filter chip's top padding all shift
        // down to sit below the bar instead of underneath it. Derived the same one-time-
        // measurement way as compassStripClearance itself (fieldHeight there is exactly
        // compassStripClearance * 2 — see SearchEntryBar's own fieldHeight doc comment) plus the
        // bar's own fixed vertical padding and divider, matching its real Column structure
        // exactly. A plain derived Dp, not a live re-measurement: CompactMapTab's own
        // compassStripClearance doc comment documents a confirmed, bisected regression from
        // reading a real onGloballyPositioned height back through state for this exact class of
        // positioning — this stays a one-time text-measurement-derived constant instead, the
        // proven-safe shape that doc comment prescribes.
        val searchBarHeight = compassStripClearance * 2 + Spacing.xs * 3 + DividerDefaults.Thickness
        // "Set on map" (SearchDropdown's own Advanced Search section, dispatch C item 2) hands off to the exact same
        // pan-to-centre-pin-plus-confirm flow every other pin placement in this app uses
        // (CentrePinLocationPickerOverlay) rather than a second picker — see CompactMapTab's own
        // pendingAction-driven overlay for the established shape this mirrors. Lifted to this
        // scaffold's own scope, not into CompactMapTab, because the trigger (the dropdown) lives up
        // here and can be tapped from any bottom-nav tab, not just while already on Maps.
        var pickingSearchLocationOnMap by remember { mutableStateOf(false) }
        // Nested inside this scaffold, so Compose's OnBackPressedDispatcher tries it before the
        // four home-chain handlers above (isDrawerOpen/isMapFullscreen/compactTab/exit-confirmation)
        // — the same "innermost enabled handler wins" precedence those four already rely on for
        // JournalTab/CompactSettingsTab/CompactMapTab. Genuinely missing before this fix: back
        // pressed while this panel was open (species field focused, keyboard up) fell straight
        // through to whichever of those four was enabled instead of just closing this panel first.
        BackHandler(enabled = showSearchDropdown) {
            showSearchDropdown = false
        }
        // Same "actually hide the IME" fix as isDrawerOpen's own LaunchedEffect above — this panel
        // holds the manual-coordinate TextFields, so it's exactly as prone to a stuck keyboard on
        // close (the BackHandler above, or collapsing the bar again) as the drawer's own fields are.
        LaunchedEffect(showSearchDropdown) {
            if (!showSearchDropdown) {
                focusManager.clearFocus(force = true)
                keyboardController?.hide()
            }
        }

        // Pings the device's live location once, as soon as the compact Maps experience is shown,
        // so the map opens already centred on it rather than waiting for an explicit locate-me tap
        // — see CompactMapTab's own doc comment on the pre-search display region this feeds. Fires
        // once per this scaffold's own composition lifetime (i.e. once per app open on a compact
        // window), not once per Maps-tab visit, since it's hoisted here rather than into
        // CompactMapTab itself, which enters and leaves composition on every bottom-nav switch.
        LaunchedEffect(Unit) { onLocateMe() }

        // Workstream L4b-R: backgrounding the app while a log entry is open is "leaving without
        // answering" — persists the draft, never commits (see MushroomLogViewModel's own doc
        // comment on the three exits). Deliberately calls the *raw* callback, never the
        // Snackbar-offering wrapper below: the home button (or any other way of backgrounding)
        // blows straight through any in-app prompt with no window to show one at all, so this
        // defaults straight to "saved to Drafts," silently, with no discard offer attempted.
        // Mirrors SightingsMap's own DisposableEffect(lifecycleOwner)/LifecycleEventObserver
        // pattern — the one existing precedent in this codebase for hooking ON_STOP/ON_PAUSE, since
        // neither MainActivity nor this composable had any lifecycle observer before this.
        // rememberUpdatedState keeps the observer (registered once per lifecycleOwner) reading the
        // *current* tab/entry state and callback rather than whatever was current the moment it was
        // registered.
        val lifecycleOwner = LocalLifecycleOwner.current
        val latestOnLeaveEditingIncidentally by rememberUpdatedState(onLeaveLogEntryEditingIncidentally)
        val latestIsJournalEditing by rememberUpdatedState(compactTab == CompactTab.JOURNAL && logUiState.editingEntry != null)
        // Device-check patch, Items 2/3: suppresses this same incidental-exit call while the open
        // find's own camera/gallery round-trip is this app's own doing, not the user backgrounding
        // it — see PhotoAcquisitionLaunchers.isAcquisitionInFlight's own doc comment for the full
        // trace of why conflating the two was silently closing the find (and losing the photo with
        // it) on every camera capture.
        val latestPhotoAcquisitionInFlight by rememberUpdatedState(logPhotoAcquisitionInFlight)
        // Pending-edit-and-fixes dispatch, Item 1: this observer no longer touches Cartography at
        // all — it used to call onSaveCartographyEntry here on a dirty committed entry, silently
        // committing an edit the user never approved just because the app backgrounded. Backgrounding
        // is not consent to save (owner decision): CartographyScreen now owns its own lifecycle
        // observer for exactly this, holding the pending edit in ViewModel memory on ON_STOP (no
        // call at all) and prompting Continue editing/Commit/Save as draft on ON_RESUME instead — see
        // that composable's own doc comment for the full reasoning, including why it's self-contained
        // there rather than threaded through here.
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) {
                    if (latestIsJournalEditing && !latestPhotoAcquisitionInFlight) latestOnLeaveEditingIncidentally()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // Workstream L4b-R2: every *in-app* incidental exit (back arrow inside the edit form, the
        // journal tab's own BackHandler, switching to another bottom-nav tab, and — via the shared
        // leaveLogEntryEditingOfferingDiscard/logDraftSnackbarHostState this function hoisted to its
        // own top level — DrawerPanel.Log's LogPanel too) shares this one wrapped callback, so the
        // same Snackbar covers every one of them from a single place rather than a
        // window-class-specific copy per host. Backgrounding above is the deliberate exception (see
        // that effect's own comment on why). The exit itself is never blocked on this:
        // onLeaveLogEntryEditingIncidentally() already ran, and the Snackbar only offers an undo: a
        // dismissed or ignored one leaves the draft exactly where that call already put it (owner
        // decision, 2026-08-25: Gmail-drafts-style).
        Scaffold(
            snackbarHost = { SnackbarHost(logDraftSnackbarHostState) },
            bottomBar = {
                if (!isMapFullscreen) {
                    ForagerBottomNav(
                        selectedTab = compactTab,
                        isDrawerOpen = isDrawerOpen,
                        onTabSelected = { tab ->
                            // Workstream L4b: switching away from Journal while an entry is open is
                            // "leaving without answering" — the same incidental-exit auto-save as
                            // the edit form's own back arrow or the app backgrounding (see
                            // MushroomLogViewModel's own doc comment on the three exits). Checked
                            // before compactTab actually changes, so this only fires on a genuine
                            // tab switch, never on tapping the already-selected Journal tab again.
                            // Also correct for Tools, below, since that never sets compactTab at
                            // all — tab != CompactTab.JOURNAL is still true when tab is TOOLS.
                            if (compactTab == CompactTab.JOURNAL && tab != CompactTab.JOURNAL && logUiState.editingEntry != null) {
                                leaveLogEntryEditingOfferingDiscard()
                            }
                            if (tab == CompactTab.TOOLS) {
                                // CompactTab.TOOLS's own doc comment: opens the drawer as an
                                // overlay over whatever tab is already showing, rather than
                                // becoming compactTab itself — the same drawer the removed
                                // MapIconBar search icon used to open (see that composable's own
                                // doc comment), including the same "dismiss any open taxon
                                // suggestion popup first" step openSearchDrawer used to do.
                                onDismissTaxonSuggestions()
                                isDrawerOpen = true
                            } else {
                                compactTab = tab
                                // Keep the shared ResultsTab-driven state in sync for the three
                                // destinations both it and CompactTab describe — see compactTab's
                                // own doc comment for why.
                                when (tab) {
                                    CompactTab.LIST -> selectedTab = ResultsTab.LIST
                                    CompactTab.MAP -> selectedTab = ResultsTab.MAP
                                    CompactTab.SEASONAL -> selectedTab = ResultsTab.SEASONAL
                                    CompactTab.JOURNAL -> Unit
                                    CompactTab.TOOLS -> Unit // unreachable — handled above
                                }
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
                // Map tab only: SearchEntryBar moves into CompactMapTab's own searchBarSlot
                // instead (that call site's own doc comment), composed as a real overlay inside
                // the SAME Box that hosts the map, so its 80% fill reveals map imagery through it
                // the same as the compass strip and the two map-chrome pills — the owner's own
                // direct call, scoped to the Map tab specifically so the other three tabs
                // (List/Seasonal/Journal, none of which have anything worth showing through a
                // translucent bar) keep this bar as ordinary opaque-backed chrome, unchanged.
                if (!isMapFullscreen && compactTab != CompactTab.MAP) {
                    SearchEntryBar(
                        uiState = uiState,
                        distanceUnit = distanceUnit,
                        onUseCurrentLocation = {
                            showSearchDropdown = false
                            onUseCurrentLocation()
                        },
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = { result ->
                            onTaxonSearchResultSelected(result)
                            showSearchDropdown = false
                        },
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        onFieldFocused = { showSearchDropdown = true },
                    )
                    SearchNotice(uiState)
                }

                // Switches to the Journal tab and starts the entry there — the gallery's own edit
                // form ([JournalTab]'s `editingEntry` branch) is what shows it next, the same
                // "just-created entry opens for editing" behavior the drawer used to give this
                // exact call. No drawer to open any more; Journal is a bottom-nav destination now.
                val onLogFindHere: (LatLng) -> Unit = { location ->
                    compactTab = CompactTab.JOURNAL
                    // Stage 2d: lands JournalTab on Records -> Finds, editing, for the entry
                    // onStartLogEntry is about to create — see JournalTab's own doc comment, "The
                    // map '+' routing bug." Before this fix, compactTab alone left JournalTab's own
                    // selectedTopTab at its CARTOGRAPHY default, landing on Cartography instead.
                    pendingJournalDestination = PendingJournalDestination.EDIT_NEW_FIND
                    onStartLogEntry(location, LocalDate.now())
                }

                // A Box, not a plain weighted child, as of map/navigation redesign dispatch C: this
                // is now also where AdvancedSearchDropdown floats over whatever tab content shows
                // below it, composed after that content so it draws on top by composition order
                // alone (AdvancedSearchDropdown's own doc comment). weight(1f) here (unchanged from
                // before this dispatch) states the intent: this gets whatever is left after the
                // wrap-content siblings above (empty in fullscreen, so the map then gets the entire
                // padded area) — see mainScaffold's own doc comment on this same pattern. Each branch
                // below now fills this Box (fillMaxSize()) rather than carrying its own weight(1f),
                // since a Box — unlike the Column this used to be a direct child of — doesn't
                // distribute weight among its children.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (compactTab) {
                        CompactTab.LIST -> ListTab(
                            uiState = uiState,
                            currentTime = currentTime,
                            distanceUnit = distanceUnit,
                            onViewOnMap = onViewSpeciesOnMap,
                            modifier = Modifier.fillMaxSize(),
                        )
                        CompactTab.MAP -> CompactMapTab(
                            uiState = uiState,
                            mapSlot = mapSlot,
                            renderMode = mapRenderMode,
                            mapMode = mapMode,
                            onMapModeSelected = { mapMode = it },
                            isNightMode = isNightMode,
                            onPlaceTripPin = onPlaceTripPin,
                            // Opens straight to the log's edit form for the new entry, bypassing
                            // Search — see DrawerPanel's own doc comment on why Log is reachable
                            // both ways.
                            onLogFindHere = onLogFindHere,
                            isFullscreen = isMapFullscreen,
                            onToggleFullscreen = { isMapFullscreen = !isMapFullscreen },
                            onLocateMe = onLocateMe,
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
                            taxonFilter = mapTaxonFilter,
                            onClearTaxonFilter = onClearMapTaxonFilter,
                            // AdvancedSearchDropdown's own "Set on map" hands off to this same map's
                            // own CentrePinLocationPickerOverlay — see compactMainScaffold's own
                            // pickingSearchLocationOnMap doc comment.
                            pickingSearchLocation = pickingSearchLocationOnMap,
                            onSearchLocationPicked = { location ->
                                onManualLatChanged("%.4f".format(location.lat))
                                onManualLngChanged("%.4f".format(location.lng))
                                onSearchManualCoordinates()
                                pickingSearchLocationOnMap = false
                            },
                            onCancelSearchLocationPick = { pickingSearchLocationOnMap = false },
                            // SearchEntryBar now overlays this tab directly (see searchBarSlot's
                            // own doc comment below) rather than sitting above it in document
                            // flow, so the strip/bubble/filter-chip positioning CompactMapTab
                            // derives from compassStripClearance needs to start below the bar, not
                            // at this Box's own true top edge. 0.dp while fullscreen: searchBarSlot
                            // itself goes empty then (this call site's own comment below), so
                            // there is no bar to clear any more — a fixed searchBarHeight here
                            // regardless of isMapFullscreen left a bar-shaped gap above the strip
                            // with nothing in it once the bar disappeared.
                            topInset = if (isMapFullscreen) 0.dp else searchBarHeight,
                            // Passed as a slot, not composed at this call site directly, so it
                            // renders inside CompactMapTab's own Box — see that parameter's own
                            // doc comment for why this specific nesting is load-bearing, not
                            // cosmetic. Empty while fullscreen: matches the bar's own old
                            // `!isMapFullscreen` gate from when it lived in this scaffold's outer
                            // Column, now reproduced here since the slot is CompactMapTab's to
                            // show or not.
                            searchBarSlot = if (isMapFullscreen) {
                                {}
                            } else {
                                {
                                    Column {
                                        SearchEntryBar(
                                            uiState = uiState,
                                            distanceUnit = distanceUnit,
                                            onUseCurrentLocation = {
                                                showSearchDropdown = false
                                                onUseCurrentLocation()
                                            },
                                            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                                            onTaxonSearchResultSelected = { result ->
                                                onTaxonSearchResultSelected(result)
                                                showSearchDropdown = false
                                            },
                                            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                                            onFieldFocused = { showSearchDropdown = true },
                                        )
                                        SearchNotice(uiState)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                        CompactTab.SEASONAL -> SeasonalTab(uiState = uiState, modifier = Modifier.fillMaxSize())
                        CompactTab.JOURNAL -> JournalTab(
                            uiState = logUiState,
                            cameraCaptureFiles = cameraCaptureFiles,
                            mapSlot = mapSlot,
                            pickerRegion = uiState.region ?: JOURNAL_PICKER_DEFAULT_REGION,
                            basemap = basemap,
                            night = isNightMode,
                            onOpenEntry = onOpenLogEntry,
                            onCloseEntry = onCloseLogEntry,
                            onStartEntry = onStartLogEntry,
                            onEntryChanged = onLogEntryChanged,
                            onStartEditingEntry = onStartEditingLogEntry,
                            onSaveEntry = onSaveLogEntry,
                            onCancelEditing = onCancelLogEntryEditing,
                            onLeaveEditingIncidentally = leaveLogEntryEditingOfferingDiscard,
                            onPhotoAcquisitionInFlightChanged = { inFlight -> logPhotoAcquisitionInFlight = inFlight },
                            onAddPhoto = onAddLogPhoto,
                            onRemovePhoto = onRemoveLogPhoto,
                            onPullPhoto = onPullLogPhoto,
                            onDeleteEntry = onDeleteLogEntry,
                            onSaveErrorDismissed = onSaveLogErrorDismissed,
                            // Album folded into this tab as a third top tab (Log/Drafts/Album) — see
                            // LogGalleryScreen's own doc comment. Threaded through unchanged from
                            // where CompactTab.PHOTOS used to read them directly.
                            galleryPhotos = logUiState.galleryPhotos,
                            isLoadingGalleryPhotos = logUiState.isLoadingGalleryPhotos,
                            onDeleteGalleryPhoto = onDeleteGalleryPhoto,
                            onAddGalleryPhoto = onAddGalleryPhoto,
                            galleryLoadErrorMessage = logUiState.galleryLoadErrorMessage,
                            galleryPhotoEntryReferenceCounts = logUiState.cartographyEntryPhotoReferenceCounts,
                            // Journal Stage 2b: Cartography's own Entries/Drafts/Album — see
                            // CartographyScreen's own doc comment.
                            cartographyUiState = cartographyUiState,
                            onOpenCartographyEntry = onOpenCartographyEntry,
                            onStartCartographyEntry = onStartCartographyEntry,
                            onCloseCartographyEntry = onCloseCartographyEntry,
                            onCartographyTextChanged = onCartographyTextChanged,
                            onCartographyTagsChanged = onCartographyTagsChanged,
                            onSetFindDecision = onSetFindDecision,
                            onSetTrackDecision = onSetTrackDecision,
                            onSetWaypointDecision = onSetWaypointDecision,
                            onSetOfflineRegionDecision = onSetOfflineRegionDecision,
                            onToggleKeptPhoto = onToggleKeptPhoto,
                            onFinishCartographyEntry = onFinishCartographyEntry,
                            onSaveCartographyEntry = onSaveCartographyEntry,
                            onDiscardCartographyEntryChanges = onDiscardCartographyEntryChanges,
                            onSaveCartographyEntryAsDraft = onSaveCartographyEntryAsDraft,
                            onDeleteCartographyEntry = onDeleteCartographyEntry,
                            getCartographyEntryMapData = getCartographyEntryMapData,
                            getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
                            // Journal restructure Stage 1: the Records tab's three submenus — see
                            // RecordsTab's own doc comment.
                            availabilityUiState = uiState,
                            distanceUnit = distanceUnit,
                            currentTime = currentTime,
                            onOfflineMapLatChanged = onOfflineMapLatChanged,
                            onOfflineMapLngChanged = onOfflineMapLngChanged,
                            onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
                            onOfflineMapNameChanged = onOfflineMapNameChanged,
                            onOfflineMapsOpened = onOfflineMapsOpened,
                            onDownloadOfflineMaps = onDownloadOfflineMaps,
                            onDeleteOfflineRegion = onDeleteOfflineRegion,
                            tracks = tracks,
                            onTracksOpened = onTracksOpened,
                            waypoints = waypoints,
                            waypointsErrorMessage = waypointsErrorMessage,
                            onDeleteWaypoint = onDeleteWaypoint,
                            waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                            pendingDestination = pendingJournalDestination,
                            onPendingDestinationConsumed = { pendingJournalDestination = null },
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Never actually reached — CompactTab.TOOLS never becomes compactTab itself,
                        // see that entry's own doc comment. Kept as a real branch (not an else) so
                        // this stays an exhaustive, honest `when` rather than one that silently
                        // compiles around a case the compiler can't see is impossible.
                        CompactTab.TOOLS -> Unit
                    }

                    if (!isMapFullscreen) {
                        // Dismiss-elsewhere scrim for SearchEntryBar's own "tap to focus, dismiss
                        // elsewhere" model (map/navigation redesign dispatch D): while the dropdown
                        // is open, SearchDropdown's own bounds only cover its own (bounded, scrolled)
                        // content height, not the full remaining area below SearchEntryBar — a tap
                        // on visible tab content past that edge would otherwise reach the tab
                        // underneath (panning the map, tapping a sighting dot) with no way to close
                        // the panel except the back button. This is a real, intentional interception
                        // — the opposite of Understory rule 1's "nothing here swallows a touch meant
                        // for the map," which is about the *collapsed* state, not an actively open
                        // modal panel — present only while showSearchDropdown is true, composed
                        // before SearchDropdown so that panel's own controls still win the tap they
                        // sit on (composition-order-is-hit-test-order, the same rule this file's
                        // other overlapping surfaces already rely on).
                        if (showSearchDropdown) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Map tab only: excludes SearchEntryBar's own band from the
                                    // scrim's bounds entirely, top edge down. That bar now composes
                                    // inside CompactMapTab's own searchBarSlot (that call site's own
                                    // doc comment — needed for its 80% fill to actually blend with
                                    // the map, an interop-nesting requirement, not a cosmetic one),
                                    // which puts it earlier in this Box's composition order than
                                    // this scrim — composition-order-is-hit-test-order would
                                    // otherwise mean the scrim wins every tap on the bar while the
                                    // dropdown is open, breaking "type in the bar while the dropdown
                                    // is still showing." Excluding the region outright, rather than
                                    // reordering composition, keeps the scrim's own intercept of the
                                    // map underneath it intact (composed before CompactMapTab would
                                    // let the map's own pan gesture win those same taps instead).
                                    .padding(top = if (compactTab == CompactTab.MAP) searchBarHeight else 0.dp)
                                    .testTag(SEARCH_DROPDOWN_SCRIM_TAG)
                                    .pointerInput(Unit) {
                                        detectTapGestures { showSearchDropdown = false }
                                    },
                            )
                        }
                        // Fully qualified: an implicit ColumnScope receiver is still in scope from
                        // the outer Column this Box sits inside, which makes the bare name resolve
                        // to ColumnScope's own AnimatedVisibility overload instead of this top-level
                        // one — Kotlin then refuses it ("cannot be called with an implicit
                        // receiver") since a BoxScope, not a ColumnScope, is this call's real one.
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showSearchDropdown,
                            enter = expandVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
                            exit = shrinkVertically(animationSpec = MotionTokens.panelMotionSpec()) + fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
                            // Starts below the compass strip rather than painting over it — Map tab
                            // only, since the strip only exists inside CompactMapTab; every other
                            // tab keeps the panel flush against the top like before. No extra gap
                            // beyond the strip's own measured height: the owner's own direct call
                            // ("bar, strip, drawer... each meeting the next without a break") — an
                            // earlier version added Spacing.sm here on top of compassStripClearance,
                            // which read as a seam between the strip and this panel rather than one
                            // continuous piece of chrome. searchBarHeight added on top of that, Map
                            // tab only: SearchEntryBar now overlays the map above the strip on this
                            // tab (see this scaffold's own searchBarHeight doc comment), so the
                            // drawer needs to start below both, not just the strip.
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(top = if (compactTab == CompactTab.MAP) searchBarHeight + compassStripClearance else 0.dp),
                        ) {
                            SearchDropdown(
                                uiState = uiState,
                                distanceUnit = distanceUnit,
                                onRecentSearchSelected = { summary ->
                                    showSearchDropdown = false
                                    onRecentSearchSelected(summary)
                                },
                                currentTime = currentTime,
                                onManualLatChanged = onManualLatChanged,
                                onManualLngChanged = onManualLngChanged,
                                onSearchManualCoordinates = {
                                    showSearchDropdown = false
                                    onSearchManualCoordinates()
                                },
                                onRadiusChanged = onRadiusChanged,
                                onMonthSelected = onMonthSelected,
                                onUseCurrentLocation = {
                                    showSearchDropdown = false
                                    onUseCurrentLocation()
                                },
                                onSetOnMap = {
                                    showSearchDropdown = false
                                    compactTab = CompactTab.MAP
                                    selectedTab = ResultsTab.MAP
                                    pickingSearchLocationOnMap = true
                                },
                            )
                        }
                    }
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
                    CompactToolsDrawerContent(
                        uiState = uiState,
                        distanceUnit = distanceUnit,
                        onDistanceUnitSelected = onDistanceUnitSelected,
                        onClose = { isDrawerOpen = false },
                        onDeletePlannedTrip = onDeletePlannedTrip,
                        currentTime = currentTime,
                        isNightMode = isNightMode,
                        onNightModeMapsChanged = onNightModeMapsChanged,
                        themeMode = uiState.themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        crashFileStore = crashFileStore,
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
                    // Workstream L4b-R2: the drawer sheet is DrawerPanel.Log's own visual area, so
                    // its discard-offer Snackbar docks here — at the bottom of this sheet — rather
                    // than in mainScaffold's Scaffold, which is the search/results pane beside it,
                    // not where the edit session the Snackbar is about actually lives.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            drawerSheetContent(false)
                        }
                        SnackbarHost(
                            logDraftSnackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                        )
                    }
                }
            },
            content = mainScaffold,
        )
    }
}

/**
 * Replaces the compact-only top [SecondaryTabRow] — decision #4 in `docs/plans/map-redesign.md`,
 * back down to [CompactTab]'s **5** destinations as of this dispatch: List, Seasonal, Maps,
 * Journal, Tools. **Corrected twice now**: this comment said "5" before Album was added as its own
 * destination (2026-08-28 correction), then needed correcting again to "6" once Album actually
 * landed, and is "5" again for real now that Album folded into Journal and Settings folded into
 * Tools — re-derived directly against [CompactTab.entries] rather than trusted from either prior
 * count, per this project's own repeated history of exactly this drift.
 *
 * [selectedTab] is [AvailabilityScreen]'s own `compactTab`, with one exception: [CompactTab.TOOLS]
 * never actually becomes the selected `compactTab` (see that entry's own doc comment — tapping it
 * opens a drawer instead), so [isDrawerOpen] stands in for its own highlight specifically. Every
 * other entry highlights the ordinary way.
 */
@Composable
private fun ForagerBottomNav(selectedTab: CompactTab, isDrawerOpen: Boolean, onTabSelected: (CompactTab) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        CompactTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = if (tab == CompactTab.TOOLS) isDrawerOpen else selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(tab.label) },
                // Colored entirely from MaterialTheme.colorScheme rather than the fixed Bark/
                // Color.White an earlier revision used — that hardcoding was a real bug, not a
                // style choice: it left this bar the same dark brown regardless of system light/
                // dark theme, while every other surface in the app (and this same bar's own
                // active-tab color, already colorScheme.primary) switched with it.
                // NavigationBarItemDefaults.colors' own defaults already give the unselected/
                // indicator roles sensible theme-following values, so this only overrides
                // selectedIconColor/selectedTextColor to keep the app's own forest green
                // (colorScheme.primary — ForestGreen in light theme, MossGreen in dark, per that
                // theme's own doc comment) as the active-tab accent, matching this file's other
                // hand-picked accents rather than leaving it at M3's default secondary-container
                // tint.
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
    renderMode: MapRenderMode,
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    /** See [AvailabilityScreen]'s own `mapTaxonFilter`/`onViewSpeciesOnMap` — threaded to both tabs here. */
    taxonFilter: Long?,
    onClearTaxonFilter: () -> Unit,
    onViewOnMap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxHeight()) {
        ListTab(
            uiState = uiState,
            currentTime = currentTime,
            distanceUnit = distanceUnit,
            onViewOnMap = onViewOnMap,
            modifier = Modifier.width(COMBINED_PANE_LIST_WIDTH).fillMaxHeight(),
        )
        VerticalDivider()
        MapTab(
            uiState = uiState,
            mapSlot = mapSlot,
            renderMode = renderMode,
            mapMode = mapMode,
            onMapModeSelected = onMapModeSelected,
            onPlaceTripPin = onPlaceTripPin,
            onLogFindHere = onLogFindHere,
            breadcrumbPoints = breadcrumbPoints,
            waypoints = waypoints,
            onDropWaypoint = onDropWaypoint,
            taxonFilter = taxonFilter,
            onClearTaxonFilter = onClearTaxonFilter,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

/** Same readable-width reasoning as [PERMANENT_DRAWER_WIDTH]; see [CombinedResultsPane]. */
private val COMBINED_PANE_LIST_WIDTH = 360.dp

/**
 * What the screen is currently showing, in one line — "Fungi · August · 8 km". Medium/expanded
 * only now: compact replaced this with [SearchEntryBar], a real entry field, rather than a
 * read-only summary that opens a second one — see that composable's own doc comment. This is what
 * remains for the medium/expanded `mainScaffold`'s own results pane, which already shows
 * [SpeciesSearchControls] directly in its app bar and has no second-summary problem to fix.
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
) {
    Surface(
        onClick = onReopenTaxonSuggestions,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        ) {
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
 * Compact's own entire top search bar, replacing the old [ActiveSearchSummary]-opens-
 * [SearchDropdown] pair — map/navigation redesign dispatch D, the project owner's own direct call:
 * "the search bar at the top should be the entry field for searches," with the old read-only
 * summary-that-opens-a-second-text-field removed as the redundant "second search bar" it had
 * become. [SpeciesSearchControls]' own species field is hosted here directly — the same field
 * [SearchDropdown] used to host a second copy of one tap deeper — so typing happens right where the
 * bar already reads as a search field, not behind an extra tap into a nested panel. No chevron: the
 * leading search icon [SpeciesSearchControls] itself doesn't draw, so this bar draws its own, is
 * what the owner call singled out as already making the field read as tappable without one.
 *
 * **Tap-to-focus, dismiss-elsewhere**, not the old tap-to-toggle: focusing the species field
 * ([onFieldFocused], wired at the call site to `showSearchDropdown = true`) opens [SearchDropdown]
 * below it, the same panel as before (recent searches, location, radius, month) — but there is no
 * second tap on this bar that closes it again, since a real, focused text field doesn't
 * conventionally close on a second tap the way a toggle button did. Closing happens by the back
 * button (existing `BackHandler`, unchanged), by picking a result/recent search, or by tapping
 * outside the panel — see that call site's own `SEARCH_DROPDOWN_SCRIM_TAG` doc comment for the
 * last one, a real gap the old toggle model never had to fill (tapping the bar again always worked
 * before; a focused field has no such self-closing gesture).
 *
 * **80% opacity, themed for night/day, with a themed divider along the bottom** — the project
 * owner's own direct ask, once this bar started sitting immediately above [CompassElevationStrip]
 * on the Map tab (previously two fully-opaque, unrelated-looking strips; now two translucent ones
 * that need a real seam between them to read as separate surfaces rather than one blurred one).
 * [MapIconStackButtonColorDark]/[MapIconStackButtonColorLight] reused as-is for the 80% fill — that
 * pair already sits at exactly 0.8 alpha, so no second multiplier is layered on top — and
 * [MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT] for the [HorizontalDivider],
 * the same hairline-border pair already drawn against that exact fill everywhere else in this map
 * chrome family ([MapIconBar], [ControlPill], [DistanceArm]), rather than a new one-off colour.
 * [LocalForagerDarkTheme], not the app's own system light/dark [MaterialTheme.colorScheme]: this
 * bar sits immediately above map chrome that already keys off that same night/day concept
 * (`isDarkTheme` throughout this file), and reads as one coherent system with it rather than two
 * surfaces following two different theme signals stacked on top of each other.
 */
@Composable
private fun SearchEntryBar(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    onFieldFocused: () -> Unit,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val contentColor = if (isDarkTheme) Color.White else Bark
    // Same "Mg" / labelMedium measurement compactMainScaffold's own compassStripClearance uses
    // for the compass strip's own real text-row height (CompassElevationStripContent wraps
    // content with no extra vertical padding of its own) — the owner's own direct ask is this
    // field's box exactly twice that, not a guess at a fixed dp value that would drift the
    // moment either row's typography or density changes.
    val fieldHeightTextMeasurer = rememberTextMeasurer()
    val fieldHeightLabelStyle = MaterialTheme.typography.labelMedium
    val fieldHeightDensity = LocalDensity.current
    val fieldHeight = remember(fieldHeightLabelStyle, fieldHeightDensity) {
        with(fieldHeightDensity) {
            fieldHeightTextMeasurer.measure("Mg", fieldHeightLabelStyle).size.height.toDp() * 2
        }
    }
    // Text stays at Material's own default style (LocalTextStyle.current, not overridden) — the
    // owner's own direct call, after an earlier attempt that shrank the font instead read as
    // "the way it was before" broken: this box is short because OutlinedTextField reserves its
    // own fixed vertical padding around the text line regardless of style, not because the text
    // itself needs to be smaller. contentPadding below is the actual fix.
    val fieldContentPadding = OutlinedTextFieldDefaults.contentPadding(top = 2.dp, bottom = 2.dp)
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedTextColor = contentColor,
        unfocusedTextColor = contentColor,
        focusedPlaceholderColor = contentColor,
        unfocusedPlaceholderColor = contentColor,
        cursorColor = contentColor,
    )
    // Surface, not a plain Column + background: on the Map tab this now composes as a real
    // overlay above the map (compactMainScaffold's own call site), so — unlike
    // CompassElevationStripContent, which deliberately stays a plain Box so blank areas let
    // map touches through — this bar needs to consume every tap across its own bounds the way
    // it always implicitly did back when it sat in normal document flow with nothing underneath
    // it to fall through to. A plain Box + background wouldn't: the padding around the icon, the
    // spacer, and the divider have no interactive child of their own to consume a tap there, so
    // it would reach the map beneath. Surface intercepts its full bounds by default — the same
    // established shape MapIconBar's own Surface already relies on for the same reason (see this
    // file's own CLAUDE.md-documented precedent) — so this reuses that rather than hand-rolling a
    // pointerInput consumer. tonalElevation/shadowElevation pinned to 0.dp: this bar's own color
    // is exact (contentColor and MapIconStackButtonColorDark/Light are both already the intended
    // finished tone), not something Material's elevation-tint system should be allowed to touch.
    Surface(
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = contentColor,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RectangleShape,
        modifier = Modifier.fillMaxWidth().testTag(SEARCH_ENTRY_BAR_TAG),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.padding(start = Spacing.lg).size(18.dp),
                )
                Box(modifier = Modifier.weight(1f)) {
                    SpeciesSearchControls(
                        uiState = uiState,
                        onUseCurrentLocation = onUseCurrentLocation,
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                        queryFieldModifier = Modifier.testTag(ACTIVE_SEARCH_SUMMARY_TAG).height(fieldHeight),
                        onQueryFieldFocusChanged = { focused -> if (focused) onFieldFocused() },
                        restingPlaceholder = activeSearchSummary(uiState, distanceUnit),
                        showLocationTrailingIcon = false,
                        fieldColors = fieldColors,
                        contentPadding = fieldContentPadding,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            HorizontalDivider(color = if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
        }
    }
}

/**
 * Map/navigation redesign dispatch C: the compact window's entire search surface, floating over
 * whatever tab content is currently showing (usually the map) from where quick species search used
 * to sit — see [ActiveSearchSummary]'s own doc comment on why that quick panel is gone rather than
 * kept alongside this one. Started as just item 1's "advanced search" (location/radius/month); a
 * follow-up owner call folded species search and Recent Searches in too, on the same "one place
 * instead of two" reasoning item 1 itself already argued for its own content — those had been
 * living in the Tools drawer, one tap further away and split across two surfaces from location
 * search's own new home.
 *
 * **Radius and month promoted to this surface's own top level, alongside basic search — Advanced
 * search is location only now.** Originally shaped with radius/month nested inside "Advanced
 * search" alongside location; the project owner asked directly for radius and month to move up to
 * "normal search" instead, on the reasoning both are reached for on nearly every search the same
 * way species/category already are, unlike location (set on map/use current location/manual
 * coordinates), which stays gated one tap deeper since most searches don't need to override it.
 *
 * **Composed after the tab content in the same [Box] at its call site, so it draws on top by
 * composition order alone** — the same "later-composed wins" rule already governing the Tools
 * drawer's own relationship to the tab content it overlays. Understory's four over-the-map rules
 * apply here as an entry condition, not a guideline (dispatch C's own text, since this surface sits
 * over the map when expanded):
 * 1. No [Surface] — a plain [Box] with [background], the same non-intercepting shape
 *    [CompassElevationStripContent] already uses, so nothing here swallows a touch meant for the
 *    map underneath once this closes.
 * 2. No scroll modifier *for content that lets the map still show through it* — the reason this
 *    panel used to call [SpeciesSearchControls] with `chipRowScrollable = false` before dispatch D
 *    moved that composable's own field out into [SearchEntryBar] (a category chip row's own default
 *    `horizontalScroll` is exactly the pointer-handler-with-nothing-to-scroll shape that rule bans
 *    for a thin decorative strip, though not a concern for this panel's own remaining content, none
 *    of which scrolls horizontally). This whole panel is a different case regardless: rule 1's
 *    opaque [background] already blocks every touch within its bounds from reaching the map
 *    underneath, so once opened it behaves like the drawer's own [SearchControls] sheet, not like
 *    the compass strip — and on the smallest
 *    supported phone (`w360dp-h640dp-xhdpi`), fully expanding Advanced search *and* Enter
 *    coordinates manually genuinely doesn't fit in the space [compactMainScaffold] hands this
 *    surface (`Modifier.weight(1f)`'s remaining height below the summary bar and above the bottom
 *    nav), which is exactly the "starved children, nothing scrolled, simply unreachable" failure
 *    this file's own `Column`-without-`verticalScroll` doc comment already describes for the old
 *    stacked layout. So the inner [Column] below carries [Modifier.verticalScroll], the same
 *    "`weight(1f)` gives a bounded, not infinite, height to scroll within" pattern already used for
 *    [SearchControls], the offline map picker, and the settings panel in this same file — proven
 *    safe here by [AvailabilityScreenLayoutTest]'s own "every advanced-search dropdown control is
 *    reachable" test at that exact device config.
 * 3. Full width is the deliberate choice here, not left to default [fillMaxWidth] creep: a
 *    location/radius/month panel needs real width for its fields and slider to be usable, the same
 *    reasoning [CompassElevationStripContent]'s own doc comment already states for going full width.
 * 4. A long-press/real-touch test accompanies this, per [AvailabilityScreenMapIconStackTest]'s own
 *    "item 5" precedent from Dispatch A.
 *
 * Item 2 of dispatch C — location input, SIDE BY SIDE: "Set on map" reuses the exact same
 * pan-to-centre-pin-plus-confirm flow every other pin placement in this app already uses
 * ([CentrePinLocationPickerOverlay], surfaced over [CompactMapTab]'s own real map — see
 * `compactMainScaffold`'s `pickingSearchLocationOnMap` state for the plumbing), not a second
 * picker. "Use current location" is the existing behaviour, unchanged.
 *
 * Item 3 — manual coordinates, kept, collapsed by default: "Enter coordinates manually" is the
 * label chosen for [CollapsibleSection]'s own header row, reused unmodified rather than a bare
 * chevron nobody would find on a first look, per that item's own explicit ask.
 *
 * Item 4 — the "redundant list" this dispatch asks to confirm and remove: none exists. The only
 * list this drawer's advanced-search content has ever shown is [ResultsSection]'s ranked species
 * list, and that has exactly one call site, inside [ListTab] — nothing resembling it lived in
 * [RegionControls]/[MonthSelector] (the content this dropdown's own radius/month controls are
 * drawn from) before this dispatch, confirmed by reading both, so there is nothing to remove.
 */
@Composable
private fun SearchDropdown(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onUseCurrentLocation: () -> Unit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    currentTime: CurrentTimeProvider,
    onManualLatChanged: (String) -> Unit,
    onManualLngChanged: (String) -> Unit,
    onSearchManualCoordinates: () -> Unit,
    onRadiusChanged: (Int) -> Unit,
    onMonthSelected: (Int) -> Unit,
    onSetOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                // Rule 1 above: Box + background, never Surface, over the map.
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                .testTag(SEARCH_DROPDOWN_TAG),
        ) {
            val scrollState = rememberScrollState()
            // Map/navigation search-UI redo dispatch: "scrolling the drawer dismisses the
            // keyboard" — the path to reach Month (and everything below it) without the keyboard,
            // raised by the search bar's own field focus, eating the space this content needs to
            // scroll into view. clearFocus() rather than a manual IME-hide call: the field is what
            // holds focus and raised the keyboard in the first place, so releasing it is what
            // actually lowers the keyboard, the same cause-and-effect Android already wires up.
            val focusManager = LocalFocusManager.current
            LaunchedEffect(scrollState.isScrollInProgress) {
                if (scrollState.isScrollInProgress) focusManager.clearFocus()
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Rule 2 above: bounded by the weight(1f) Box this surface is composed into,
                    // not a no-op — see this composable's own doc comment for why a scroll is safe
                    // here despite Understory rule 2 banning it for content over the visible map.
                    .verticalScroll(scrollState)
                    .padding(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                // Location row — map/navigation search-UI redo dispatch: "Set on map" and "Use
                // current location" promoted out of "Advanced search" up to the drawer's own top
                // level, same reasoning radius/month already got (a control reached for on nearly
                // every search doesn't belong a tap deeper). Removed from Advanced search entirely,
                // not duplicated — Advanced search now holds only "Enter coordinates manually", the
                // one location path most searches don't need to override. These are actions, not
                // selections: OutlinedButton/Button, not FilterChip, so they never read as members
                // of the category-chip row (now in SearchEntryBar, above this drawer entirely).
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedButton(onClick = onSetOnMap, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Set on map")
                    }
                    Button(onClick = onUseCurrentLocation, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Use current location")
                    }
                }

                HorizontalDivider()
                Text(
                    "Search radius: ${formatDistanceKm(uiState.radiusKm, distanceUnit)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = uiState.radiusKm.toFloat(),
                    onValueChange = { onRadiusChanged(it.toInt()) },
                    valueRange = 1f..50f,
                    steps = 48,
                )
                MonthSelector(selectedMonth = uiState.selectedMonth, onMonthSelected = onMonthSelected)

                HorizontalDivider()
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
                    CollapsibleSection(title = "Enter coordinates manually") {
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
                    }
                }
            }
        }
    }
}

/** See [SearchDropdown]'s own doc comment. */
internal const val SEARCH_DROPDOWN_TAG = "search-dropdown"

/** See [SearchEntryBar]'s own dismiss-elsewhere scrim doc comment, at its call site. */
internal const val SEARCH_DROPDOWN_SCRIM_TAG = "search-dropdown-scrim"

/** [SearchEntryBar]'s own species query field — a stable trigger regardless of its current text, for tests that don't want to depend on exact query wording. Also the pre-redesign name for [ActiveSearchSummary]'s own clickable row, kept unrenamed so existing tests that open the dropdown through this tag didn't all need retargeting for a purely mechanical rename. */
internal const val ACTIVE_SEARCH_SUMMARY_TAG = "active-search-summary"

/** [SearchEntryBar]'s own outer bounds — the whole bar, chips/field/divider included, for tests that need "where does the top strip end" (e.g. `topStripBottom()`) rather than a click target on the field specifically ([ACTIVE_SEARCH_SUMMARY_TAG]). */
internal const val SEARCH_ENTRY_BAR_TAG = "search-entry-bar"

private fun activeSearchSummary(uiState: AvailabilityUiState, distanceUnit: DistanceUnit): String {
    val month = Month.of(uiState.selectedMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
    // The radius of the search that actually ran, not the slider's pending value: moving the
    // slider doesn't re-run the search, so reporting it here would describe a search that hasn't
    // happened. Before any search there is no region, and this says so rather than implying one.
    val where = uiState.region?.let { formatDistanceKm(it.radiusKm, distanceUnit) } ?: "no location set"
    // Fungi is the only category now (owner decision) — leading with its name on every search
    // would be a label with nothing left to distinguish it from. A specific searched species is
    // still worth naming up front; nothing selected leads with the month instead of a blank
    // segment (a blank search still means "all fungi in this area and month" — see
    // AvailabilityUiState.taxonFilter's default).
    val species = (uiState.taxonFilter as? TaxonFilter.SpecificTaxon)?.label
    return listOfNotNull(species, month, where).joinToString(" · ")
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
 * The Search panel's sticky-footer entry into the photo gallery (Workstream G2) — same shape as
 * [MushroomLogEntryRow] right above it, since both are entries into mushroom-log-area
 * destinations. No `navigationBarsPadding()` here for the same reason [MushroomLogEntryRow] has
 * none: [SettingsEntryRow] below is still the last row in the sheet and carries that inset.
 */
@Composable
private fun PhotoGalleryEntryRow(onClick: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
        Text("Photo Gallery", style = MaterialTheme.typography.titleSmall)
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

/** [DrawerPanel.PhotoGallery]'s header — mirrors [SettingsHeader]'s back-arrow-plus-title shape exactly, for the same reason: there's nothing else on screen suggesting how to get back to Search. */
@Composable
private fun PhotoGalleryHeader(onBack: () -> Unit) {
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
        Text("Photo Gallery", style = MaterialTheme.typography.titleMedium)
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
 * the Journal's Records tab — see [com.forager.app.ui.log.RecordsTab]. This tab's own `showOfflineMaps`/
 * `showTracks` submenu state is gone with them; only [showCrashLogs] remains.
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
    crashFileStore: CrashFileStore,
    modifier: Modifier = Modifier,
) {
    var showCrashLogs by remember { mutableStateOf(false) }

    // Unwinds this tab's own nested submenu before AvailabilityScreen's top-level "switch away
    // from a non-Maps tab" handler ever sees it — same reasoning as JournalTab's own BackHandler.
    BackHandler(enabled = showCrashLogs) {
        showCrashLogs = false
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

            else -> {
                SettingsContent(
                    modifier = Modifier.weight(1f),
                    distanceUnit = distanceUnit,
                    onDistanceUnitSelected = onDistanceUnitSelected,
                    nightModeMaps = isNightMode,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onOpenCrashLogs = { showCrashLogs = true },
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
private fun SettingsContent(
    modifier: Modifier = Modifier,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    nightModeMaps: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    onOpenCrashLogs: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        DistanceUnitSection(distanceUnit = distanceUnit, onDistanceUnitSelected = onDistanceUnitSelected)
        HorizontalDivider()
        ThemeModeSection(themeMode = themeMode, onThemeModeSelected = onThemeModeChanged)
        NightModeMapsSection(checked = nightModeMaps, onCheckedChange = onNightModeMapsChanged)
        HorizontalDivider()
        CrashLogsEntryRow(onClick = onOpenCrashLogs)
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
 * [com.forager.app.domain.AppThemePreferenceRepository]'s own doc comment for why that independence
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
 * The "Offline Maps" submenu: an interactive topo map to pick a download region via the
 * centre-pin picker, the region's radius, current status, and the Download/Delete actions.
 *
 * Always downloads from the same one fixed source, unconditionally — see
 * `com.forager.app.domain.OfflineMapRepository`'s doc comment for why this is no longer gated on,
 * or reactive to, [MapMode]/the quick-fire map mode picker: the project owner's own call was that
 * offline downloads should "assume [a fixed source] and [be] ready to function" regardless of
 * either. That fixed source is `com.forager.app.map.MapLibreOfflineMapRepository`'s Cloudflare
 * Worker now, not USGS — this panel's own picker map below is unrelated to that choice, see the
 * next paragraph.
 *
 * ## Picking a region via [CentrePinLocationPicker]
 *
 * [onRegionPicked] fires from [CentrePinLocationPicker]'s own OK button — see that composable's
 * class doc comment for why every location-placing site in this app, this one included, replaced
 * long-press with a fixed centre pin (an accessibility decision, not a style one). There is no
 * name-and-date dialog in between OK and the pick landing: a confirmed point becomes the region's
 * centre immediately, since there is nothing else to ask the user for. [Basemap.OPEN_TOPO_MAP] here
 * is only terrain context for choosing *where* to download — this picker map is unrelated to which
 * source the download itself actually reads from underneath. Was `Basemap.USGS_TOPO` (US-only)
 * until [MapMode] removed it from the app entirely; [Basemap.OPEN_TOPO_MAP] is the worldwide
 * equivalent, a better fit for a picker with no reason to inherit a US-only limit it never needed.
 *
 * Before anything is confirmed, [uiState]'s `offlineMapLatText`/`offlineMapLngText` are blank, so
 * the picker centres on [OFFLINE_MAP_PICKER_DEFAULT_CENTER] purely so there is a map to navigate
 * — not a claim about where the user is or wants to download. "Download Maps" stays disabled until
 * a real point has been confirmed (see `hasValidRegion` below), so that default viewport can never
 * itself be submitted as a region.
 *
 * The map keeps a fixed aspect ratio rather than filling leftover space — see the `Box` below's
 * own comment for why `weight(1f)` stopped working once this whole panel became one scrolling unit.
 */
@Composable
internal fun OfflineMapsPanel(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    currentTime: CurrentTimeProvider,
    mapSlot: MapSlot,
    /** Night mode for the region picker this panel hosts — see [CentrePinLocationPicker]. */
    isNightMode: Boolean,
    onRegionPicked: (LatLng) -> Unit,
    onOfflineMapRadiusChanged: (Int) -> Unit,
    onOfflineMapNameChanged: (String) -> Unit,
    onDownloadOfflineMaps: () -> Unit,
    onDeleteOfflineRegion: (Long) -> Unit,
) {
    val pickedLat = uiState.offlineMapLatText.toDoubleOrNull()
    val pickedLng = uiState.offlineMapLngText.toDoubleOrNull()
    val hasValidRegion = pickedLat != null && pickedLat in -90.0..90.0 && pickedLng != null && pickedLng in -180.0..180.0
    val defaultCenter = uiState.offlineMapPickerDefaultCenter ?: OFFLINE_MAP_PICKER_DEFAULT_CENTER
    val now = currentTime.nowEpochMillis()

    // The whole panel scrolls as one unit now that OfflineRegionsSection's list has no bound on
    // its own length — a fixed-aspect-ratio picker map (below) plus a growing region list can
    // exceed whatever height this panel's own parent hands it (Modifier.weight(1f) from the drawer
    // sheet's Column, the same pattern SearchControls already uses for its own scroll in that same
    // parent), so verticalScroll here is meaningful rather than a no-op: weight(1f) gives a bounded,
    // not infinite, height to scroll within.
    Column(modifier = modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            "Offline downloads cover the continental United States with vector map data. " +
                "Pan the map below to position the pin, then tap OK to choose where to download.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        )

        val pickerRegion = Region(
            lat = pickedLat ?: defaultCenter.lat,
            lng = pickedLng ?: defaultCenter.lng,
            radiusKm = uiState.offlineMapRadiusKm,
        )
        // A fixed aspect ratio, not weight(1f): the picker map used to claim all leftover space in
        // an unscrolled panel, but a panel that now scrolls as a whole has no "leftover space" for
        // weight to resolve against.
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(MAP_PICKER_ASPECT_RATIO)) {
            CentrePinLocationPicker(
                mapSlot = mapSlot,
                region = pickerRegion,
                basemap = Basemap.OPEN_TOPO_MAP,
                night = isNightMode,
                onConfirm = onRegionPicked,
                // Nothing to cancel back to: this panel had no confirm step before this picker
                // existed either — the offlineMapLatText/offlineMapLngText fields just keep
                // whatever they already held (blank, or a prior confirmed pick).
                onCancel = {},
                modifier = Modifier.fillMaxSize(),
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
                    "Download region: ${"%.4f".format(pickedLat)}, ${"%.4f".format(pickedLng)}"
                } else {
                    "No location picked yet — pan the map above and tap OK."
                },
                style = MaterialTheme.typography.bodySmall,
            )

            OutlinedTextField(
                value = uiState.offlineMapNameText,
                onValueChange = onOfflineMapNameChanged,
                label = { Text("Name (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Radius: ${formatDistanceKm(uiState.offlineMapRadiusKm, distanceUnit)}", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = uiState.offlineMapRadiusKm.toFloat(),
                onValueChange = { onOfflineMapRadiusChanged(it.toInt()) },
                valueRange = Region.MIN_RADIUS_KM.toFloat()..Region.MAX_RADIUS_KM.toFloat(),
                steps = Region.MAX_RADIUS_KM - Region.MIN_RADIUS_KM - 1,
            )

            // So the tile budget is discovered here, while there's still time to pick a smaller
            // radius, rather than only on a refused download — a user should not discover the
            // ceiling at a trailhead.
            val estimatedTiles = estimateOfflineTileCount(pickerRegion, OfflineMapRepository.MIN_ZOOM, OfflineMapRepository.MAX_ZOOM)
            val remainingBudget = OfflineMapRepository.TILE_COUNT_LIMIT - uiState.offlineRegions.sumOf { it.tileCount }
            val exceedsBudget = estimatedTiles > remainingBudget
            Text(
                if (exceedsBudget) {
                    "~$estimatedTiles tiles — exceeds your remaining budget of $remainingBudget"
                } else {
                    "~$estimatedTiles tiles"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (exceedsBudget) MaterialTheme.colorScheme.error else Color.Unspecified,
            )

            OfflineDownloadStatusContent(uiState.offlineDownloadStatus)

            val isDownloading = uiState.offlineDownloadStatus is OfflineMapStatus.Downloading
            Button(
                onClick = onDownloadOfflineMaps,
                enabled = hasValidRegion && !isDownloading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download Maps") }
        }

        HorizontalDivider()

        OfflineRegionsSection(
            regions = uiState.offlineRegions,
            errorMessage = uiState.offlineRegionsErrorMessage,
            staleThresholdDays = uiState.offlineStaleThresholdDays,
            distanceUnit = distanceUnit,
            nowEpochMillis = now,
            onDeleteOfflineRegion = onDeleteOfflineRegion,
            entryReferenceCounts = uiState.offlineRegionEntryReferenceCounts,
        )
    }
}

/** The picker map's fixed width:height ratio — see [OfflineMapsPanel]'s doc comment for why this replaced `Modifier.weight(1f)`. */
private const val MAP_PICKER_ASPECT_RATIO = 4f / 3f

/**
 * An arbitrary opening viewport for [OfflineMapsPanel]'s picker map before a region has been
 * picked — the geographic center of the contiguous United States (near Lebanon, Kansas), since
 * offline downloads only ever cover the continental-US PMTiles archive
 * `com.forager.app.map.MapLibreOfflineMapRepository` reads from. Not a default region and never
 * submitted as one: "Download Maps" stays disabled until the centre pin has been confirmed with OK.
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

/**
 * What [OfflineMapsPanel]'s picker shows for its own last download attempt — every branch says
 * something, per CLAUDE.md, except [OfflineMapStatus.Idle]/[OfflineMapStatus.Succeeded], which
 * deliberately render nothing: a completed download is already reflected in
 * [OfflineRegionsSection]'s list right below, so there is nothing left for this transient status to
 * say once it succeeds.
 */
@Composable
private fun OfflineDownloadStatusContent(status: OfflineMapStatus) {
    when (status) {
        OfflineMapStatus.Idle, OfflineMapStatus.Succeeded -> Unit

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

        is OfflineMapStatus.Failed -> Text(
            status.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/**
 * Every region currently on disk: name, centre, radius, size, download date, the zoom-readiness
 * note main's old single-region `Downloaded` branch used to carry (see [OfflineRegionRow]'s own
 * doc comment for where that text landed), and per-region delete. [errorMessage] surfaces a read
 * failure without clearing whatever was last successfully loaded — see
 * [AvailabilityViewModel.loadOfflineRegions][com.forager.app.ui.availability.AvailabilityViewModel.loadOfflineRegions].
 *
 * [errorMessage] renders with no error color, deliberately: per the error-presentation spec, a
 * region-list-load failure (or a failed delete, which surfaces through the same field) isn't
 * belief-changing the way a failed download is — the user isn't mid-action, they just want to see
 * what's on disk, so this matches the neutral "Rainfall data unavailable"-style treatment other
 * read failures in this screen already use, not [OfflineDownloadStatusContent]'s error-red.
 *
 * The tile-budget line and the "sizes don't add up" caveat: [OfflineMapRepository.TILE_COUNT_LIMIT]
 * is the ceiling this app sets deliberately (see that constant's doc comment), and the caveat
 * exists because the resource table dedupes tiles across overlapping regions, so summed per-region
 * tile counts overstate real disk usage and a delete can free far less than its region's own
 * reported size — this text deliberately never promises a specific amount reclaimed.
 *
 * Deleting a downloaded region is not reversible without re-downloading it, so each row's "Delete"
 * button opens a confirmation dialog ([pendingDeleteRegion]) rather than deleting immediately on tap.
 */
@Composable
private fun OfflineRegionsSection(
    regions: List<OfflineRegionSummary>,
    errorMessage: String?,
    staleThresholdDays: Int,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDeleteOfflineRegion: (Long) -> Unit,
    /** How many Cartography entries currently keep a reference to each region (by id) — Journal Stage 2b's 4b deletion warning, shown in the confirm dialog below. */
    entryReferenceCounts: Map<Long, Int> = emptyMap(),
) {
    var pendingDeleteRegion by remember { mutableStateOf<OfflineRegionSummary?>(null) }

    // No scroll/height cap of its own: OfflineMapsPanel's whole Column scrolls as one unit (see
    // its doc comment), so this section just renders at its natural height as the last thing in
    // that scroll.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text("Downloaded Maps", style = MaterialTheme.typography.titleSmall)

        val tilesUsed = regions.sumOf { it.tileCount }
        Text(
            "Tile budget: $tilesUsed / ${OfflineMapRepository.TILE_COUNT_LIMIT}. Sizes don't add up to " +
                "total disk usage — overlapping regions share tiles, so deleting one may free less " +
                "than its own size suggests.",
            style = MaterialTheme.typography.bodySmall,
        )

        if (errorMessage != null) {
            Text(errorMessage, style = MaterialTheme.typography.bodySmall)
        }

        if (regions.isEmpty()) {
            Text("No regions downloaded yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            regions.forEach { region ->
                OfflineRegionRow(
                    region = region,
                    isStale = isOfflineRegionStale(region.createdAtEpochMillis, nowEpochMillis, staleThresholdDays),
                    distanceUnit = distanceUnit,
                    nowEpochMillis = nowEpochMillis,
                    onDelete = { pendingDeleteRegion = region },
                )
            }
        }
    }

    pendingDeleteRegion?.let { region ->
        val referencingEntryCount = entryReferenceCounts[region.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDeleteRegion = null },
            title = { Text("Delete \"${region.name}\"?") },
            text = {
                Text(
                    // No permanence claim (a future trash lands this becoming false) — states the
                    // consequence, not that it's irreversible. See amendment-2b-finds-and-trash.md.
                    if (referencingEntryCount > 0) {
                        "This region appears in $referencingEntryCount ${if (referencingEntryCount == 1) "journal entry" else "journal entries"}. " +
                            "This deletes the downloaded map tiles for this region. You can re-download it later."
                    } else {
                        "This deletes the downloaded map tiles for this region. You can re-download it later."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteOfflineRegion(region.id)
                        pendingDeleteRegion = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteRegion = null }) { Text("Cancel") } },
        )
    }
}

/**
 * One downloaded region's row in [OfflineRegionsSection].
 *
 * Carries the zoom-readiness note main's old single-region `OfflineMapStatusContent` used to show
 * in its `Downloaded` branch — per Workstream B's dispatch, the information moves here rather than
 * being dropped: [OfflineMapStatus.Succeeded] (this panel's new download-attempt status) is a bare
 * marker with no region data left to attach it to, and every completed region in this list is
 * exactly the thing that text was originally describing, so it's reworded to apply per-row instead
 * of to "the one download that just finished."
 */
@Composable
private fun OfflineRegionRow(
    region: OfflineRegionSummary,
    isStale: Boolean,
    distanceUnit: DistanceUnit,
    nowEpochMillis: Long,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(region.name, style = MaterialTheme.typography.bodyMedium)
                if (isStale) {
                    Text("Stale", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "${formatDistanceKm(region.region.radiusKm, distanceUnit)} around " +
                    "${"%.4f".format(region.region.lat)}, ${"%.4f".format(region.region.lng)} — " +
                    "${region.tileCount} tiles, ${"%.1f".format(region.sizeBytes / 1_000_000.0)} MB — " +
                    "downloaded ${relativeTimeLabel(region.createdAtEpochMillis, nowEpochMillis)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Ready to zoom ${region.maxZoom.toInt()}: zoom ${region.minZoom.toInt()}–${region.maxZoom.toInt() - 1} " +
                    "from the archive, zoom ${region.maxZoom.toInt()} detail fetched live from Protomaps when this " +
                    "region downloaded — a region that shows here has both, since a zoom-${region.maxZoom.toInt()} " +
                    "fetch failure fails the whole download rather than silently completing without it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        OutlinedButton(onClick = onDelete) { Text("Delete") }
    }
}

/**
 * The quick-fire icon overlaid on the map's own top-right corner — not the app bar, not Settings —
 * because which [MapMode] the map is in is a during-the-walk decision made often, unlike anything
 * that used to live in Settings (deleted; see [MapMode]'s own doc comment for what superseded it).
 * A tap opens [MapModePicker] rather than instantly cycling modes, now that there are three to
 * choose from instead of two — "toggle the two" stopped being the whole rule once Satellite joined
 * Street/Topographical.
 */
@Composable
private fun MapModeToggle(mapMode: MapMode, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // size(MIN_TOUCH_TARGET) rather than sizing this off the icon-plus-padding it draws: the icon
    // is 24dp and Spacing.sm padding is 8dp a side, which wrapped to a 40dp circle — under M3's
    // 48x48dp minimum touch target, a real miss found by auditing this file's tap targets against
    // that rule, not a hypothetical one.
    Surface(
        onClick = onClick,
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
                contentDescription = "Map mode: ${mapMode.label}. Choose Street, Topographical, or Satellite.",
            )
        }
    }
}

/** M3's minimum touch target size — see [MapModeToggle]'s doc comment for where this was missed. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * The picker [MapModeToggle] (medium/expanded) and [MapIconBar]'s layers row (compact) both open —
 * three real [FilterChip]s, not a list of full-width text rows: the exact "too wide" hardware
 * finding [AddActionTile] carried (a full-width button per row makes the whole tile as wide as its
 * longest label) is avoided here from the start rather than repeated. `selected` shows the current
 * mode without a separate label; tapping any chip both applies it and dismisses the picker — one
 * tap, not select-then-confirm.
 *
 * Same theme-aware, 80%-opacity fill as [MapIconBar]
 * ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) rather than a plain
 * [MaterialTheme.colorScheme.surface] popup — one visual language for every control floating over
 * the map, not a Material default that reads as a different kind of thing next to the bar it opens
 * from.
 *
 * **Same animation/sizing/anchoring shape as [AddActionTile]**, per the project owner's own
 * comparison of the two once both existed on screen together: two independent [AnimatedVisibility]s
 * (scrim, then content, so the scrim's own fade doesn't gate the tile's expand/shrink), the content
 * one sized to what three chips need rather than [fillMaxSize], and anchored precisely against
 * whichever control opened it rather than a fixed corner offset that merely sat close by. [anchor]
 * and [anchorOffset] are how the two callers differ here where [AddActionTile] didn't need to: that
 * tile only ever grows from the icon bar's own add row, but this picker opens from two different
 * shapes of control — a lone circular [MapModeToggle] floating at the map's own top-right corner on
 * medium/expanded, and [MapIconBar]'s layers row (row 4 of 8) on compact — so the anchor is a
 * parameter instead of a second constant hardcoded in here. [MAP_MODE_PICKER_COMPACT_ANCHOR_OFFSET]
 * computes the compact case the same way [ADD_TILE_ANCHOR_OFFSET] already computes the add row's;
 * the medium/expanded default below matches [MapModeToggle]'s own fixed position.
 */
@Composable
private fun MapModePicker(
    visible: Boolean,
    mapMode: MapMode,
    onModeSelected: (MapMode) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.TopEnd,
    anchorOffset: DpOffset = DpOffset(x = -Spacing.sm, y = MIN_TOUCH_TARGET + Spacing.lg),
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Box(modifier = modifier.fillMaxSize()) {
        // docs/motion-spec.md §2 "Panels and navigation" — same spec AddActionTile's own scrim uses.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
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
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
                expandIn(animationSpec = MotionTokens.panelMotionSpec(), expandFrom = anchor),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
                shrinkOut(animationSpec = MotionTokens.panelMotionSpec(), shrinkTowards = anchor),
            modifier = Modifier
                .align(anchor)
                .offset(x = anchorOffset.x, y = anchorOffset.y),
        ) {
            Surface(
                shape = RoundedCornerShape(Spacing.md),
                color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                contentColor = if (isDarkTheme) Color.White else Bark,
                shadowElevation = 4.dp,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    MapMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == mapMode,
                            onClick = {
                                onModeSelected(mode)
                                onDismiss()
                            },
                            label = { Text(mode.label) },
                        )
                    }
                }
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
private fun CompactToolsDrawerContent(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onDistanceUnitSelected: (DistanceUnit) -> Unit,
    onClose: () -> Unit,
    onDeletePlannedTrip: (String) -> Unit,
    currentTime: CurrentTimeProvider,
    isNightMode: Boolean,
    onNightModeMapsChanged: (Boolean) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    crashFileStore: CrashFileStore,
) {
    // Own drill-in step, same shape as CompactSettingsTab's own CrashLogs submenu — see this
    // composable's own doc comment, item 2. Composed inside this drawer sheet (which the
    // ModalNavigationDrawer keeps mounted even while visually closed, same as CollapsibleSection's
    // own expand state), so its own BackHandler below takes priority over the top-level
    // isDrawerOpen one — the same "most-recently-composed enabled callback wins" precedence
    // AvailabilityScreen's own top-level BackHandler chain already documents.
    var showSettings by remember { mutableStateOf(false) }
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    if (showSettings) {
        CompactSettingsTab(
            distanceUnit = distanceUnit,
            onDistanceUnitSelected = onDistanceUnitSelected,
            isNightMode = isNightMode,
            onNightModeMapsChanged = onNightModeMapsChanged,
            themeMode = themeMode,
            onThemeModeChanged = onThemeModeChanged,
            crashFileStore = crashFileStore,
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
 * medium/expanded, while [CompactToolsDrawerContent] hosts the identical
 * [SpeciesSearchControls] around this same composable for compact — see that
 * composable's own doc comment for why species search moved there instead.
 *
 * The scroll modifier on the outer [Column] is not optional. This is the same tall stack of
 * controls that starved the map when it lived in the main column; a drawer sheet is a
 * fixed-height container too, so without it the later controls would simply be unreachable on a
 * short screen or at a large font scale. Collapsing all sections by default shortens that stack
 * further, but doesn't remove the need for scroll — a large font scale with all sections expanded
 * still needs it.
 *
 * [includeAdvancedSearch] defaults `true` — medium/expanded's own call site doesn't pass it, so its
 * drawer is untouched. Compact passes `false`: map/navigation redesign dispatch C, item 1 moved
 * "Advanced search" (location, radius, month) to [AdvancedSearchDropdown], floating over the map
 * from where quick species search used to sit, so keeping a second copy here would put it back to
 * "two places instead of one" — the exact duplication that move exists to remove.
 */
@Composable
private fun SearchControls(
    modifier: Modifier = Modifier,
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    /**
     * [onUseCurrentLocation] through [onMonthSelected] are only read inside the
     * [includeAdvancedSearch]-gated section below (by [RegionControls]/[MonthSelector]) — all
     * default to a no-op since compact's own call site has nothing left to wire them to once that
     * section is excluded.
     */
    onUseCurrentLocation: () -> Unit = {},
    onManualLatChanged: (String) -> Unit = {},
    onManualLngChanged: (String) -> Unit = {},
    onSearchManualCoordinates: () -> Unit = {},
    onRadiusChanged: (Int) -> Unit = {},
    onMonthSelected: (Int) -> Unit = {},
    onDeletePlannedTrip: (String) -> Unit,
    onRecentSearchSelected: (CachedSearchSummary) -> Unit = {},
    currentTime: CurrentTimeProvider,
    includeAdvancedSearch: Boolean = true,
    /**
     * Defaults `true` — medium/expanded's own call site doesn't pass it, so its drawer is
     * untouched. Compact passes `false`: map/navigation redesign dispatch C's own follow-up moved
     * Recent Searches (and species search alongside it) into [SearchDropdown], the same "one place
     * instead of two" reasoning [includeAdvancedSearch] already documents for Advanced Search.
     */
    includeRecentSearches: Boolean = true,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (includeRecentSearches) {
            // First in the column, and a section of its own rather than a control inside "Advanced
            // search". Two reasons, both about what this list is: one tap on an entry here *is* a
            // whole search, so burying it under a header about the individual pieces of a search
            // would put the shortest route to results two taps deeper than the long route; and it
            // is the only control in this drawer that still does something useful with no
            // connection, which is exactly when nobody wants to go hunting for it. It keeps the
            // drawer's established one-line-until-tapped behaviour rather than being the one
            // section that starts expanded.
            CollapsibleSection(title = "Recent searches") {
                RecentSearchesSection(
                    recentSearches = uiState.recentSearches,
                    currentTime = currentTime,
                    distanceUnit = distanceUnit,
                    onRecentSearchSelected = onRecentSearchSelected,
                )
            }
        }
        if (includeAdvancedSearch) {
            if (includeRecentSearches) HorizontalDivider()
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
        }
        if (includeRecentSearches || includeAdvancedSearch) HorizontalDivider()
        CollapsibleSection(title = "Trip Planner") {
            TripPlannerSection(uiState = uiState, onDeletePlannedTrip = onDeletePlannedTrip)
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
                        onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
                        onTaxonSearchResultSelected = onTaxonSearchResultSelected,
                        onDismissTaxonSuggestions = onDismissTaxonSuggestions,
                    )
                }
            }
        }
    }
}

/**
 * The species search controls themselves — the species text field and its suggestion dropdown —
 * factored out of [AvailabilitySearchTopBar] so [CompactToolsDrawerContent] can host the identical
 * control inside the drawer instead of the app bar (per the project owner's own framing: "the
 * whole side panel is the search feature"), rather than a second copy of the
 * [ExposedDropdownMenuBox] logic. [AvailabilitySearchTopBar]'s own external shape (the Surface,
 * the tune icon, the two-row layout) is unchanged by this extraction — only where the field piece
 * itself is called from moved.
 *
 * The category chip row this composable used to render alongside the field is gone (owner
 * decision): the app is fungi-only now, so there is nothing left to choose between. See
 * [AvailabilityUiState.taxonFilter]'s default and [activeSearchSummary].
 *
 * [queryFieldModifier]/[onQueryFieldFocusChanged]: both default to no-ops, exercised only by
 * [SearchEntryBar] — the compact top bar now hosts this composable's own species field directly
 * (map/navigation redesign dispatch D's own "the top bar should be the entry field" call; see that
 * composable's own doc comment), so it needs a stable [testTag] on the real field to tap/focus, and
 * a way to know when that focus changes so it can drive [SearchDropdown]'s own visibility. Neither
 * [AvailabilitySearchTopBar] nor [CompactToolsDrawerContent] pass either — their own species field
 * is not a trigger for anything else, so the defaults leave them unchanged.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeciesSearchControls(
    uiState: AvailabilityUiState,
    onUseCurrentLocation: () -> Unit,
    onTaxonSearchQueryChanged: (String) -> Unit,
    onTaxonSearchResultSelected: (TaxonSearchResult) -> Unit,
    onDismissTaxonSuggestions: () -> Unit,
    queryFieldModifier: Modifier = Modifier,
    onQueryFieldFocusChanged: (Boolean) -> Unit = {},
    /**
     * Placeholder text shown while the field is empty, focused or not — map/navigation search-UI
     * redo dispatch, the owner's own direct call: the bar reads as the current filter summary
     * ("August · 9 mi", or a searched species' name ahead of it) always, not a generic hint that
     * blanks out what's currently searched the moment someone taps in to search. The generic hint
     * this used to swap to on focus (and the two other call sites, [AvailabilitySearchTopBar] and
     * [CompactToolsDrawerContent], used to default to outright) is gone from the app entirely, by
     * direct owner instruction — those two call sites now default to a blank placeholder instead
     * of reintroducing it.
     */
    restingPlaceholder: String = "",
    showLocationTrailingIcon: Boolean = true,
    fieldColors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
    /**
     * Text style for the entered/placeholder text — map/navigation search-UI redo dispatch:
     * [SearchEntryBar] pins its field to a short, fixed height (twice the compass strip's own
     * measured row height), and the default [OutlinedTextField] text style (`bodyLarge`, sized for
     * a full ~56dp Material field) doesn't fit inside it — the text was clipped away entirely, not
     * merely cramped. The owner's own direct call: scale the text down to fit the box, rather than
     * the box up to fit default-sized text. The other two call sites keep Material's own default
     * ([LocalTextStyle.current]), unchanged.
     */
    textStyle: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    /**
     * Vertical (and horizontal) padding inside the field's own box — map/navigation search-UI
     * redo dispatch: shrinking [textStyle] alone wasn't enough to fit [SearchEntryBar]'s pinned
     * field height; [OutlinedTextField] reserves its own fixed padding around the text line
     * regardless of that style, so even a correctly-sized line was still being clipped by the box
     * itself. This is the owner's own direct follow-up call — reduce the padding, not the text
     * further. Implemented via the low-level [BasicTextField] + [OutlinedTextFieldDefaults.DecorationBox]
     * pair (the only Material3 path that exposes content padding at all) for every call site, but
     * defaulted to [OutlinedTextFieldDefaults.contentPadding] — Material's own stock value — so
     * the two call sites that don't override it render identically to before this parameter
     * existed.
     */
    contentPadding: PaddingValues = OutlinedTextFieldDefaults.contentPadding(),
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        val suggestionsOpen = uiState.taxonSearchResults.isNotEmpty() || uiState.taxonSearchHasNoResults
        val queryFieldInteractionSource = remember { MutableInteractionSource() }
        ExposedDropdownMenuBox(
            expanded = suggestionsOpen,
            onExpandedChange = {},
            modifier = Modifier.padding(horizontal = Spacing.sm),
        ) {
            // BasicTextField + OutlinedTextFieldDefaults.DecorationBox, not the plain
            // OutlinedTextField composable — see [contentPadding]'s own doc comment for why: it's
            // the only Material3 path that exposes the field's internal padding at all.
            BasicTextField(
                value = uiState.taxonSearchQuery,
                onValueChange = onTaxonSearchQueryChanged,
                textStyle = textStyle.copy(color = fieldColors.focusedTextColor),
                singleLine = true,
                cursorBrush = SolidColor(fieldColors.cursorColor(isError = false)),
                interactionSource = queryFieldInteractionSource,
                modifier = queryFieldModifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .onFocusChanged {
                        onQueryFieldFocusChanged(it.isFocused)
                    },
            ) { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = uiState.taxonSearchQuery,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    visualTransformation = VisualTransformation.None,
                    interactionSource = queryFieldInteractionSource,
                    placeholder = {
                        // The filter summary ("Fungi · August · 9 mi"), not a generic hint,
                        // whether focused or not — the owner's own direct call: focusing the
                        // field to search shouldn't blank out what's currently searched, only
                        // typing should.
                        Text(
                            restingPlaceholder,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = textStyle,
                        )
                    },
                    trailingIcon = if (uiState.isSearchingTaxa || showLocationTrailingIcon) {
                        {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (uiState.isSearchingTaxa) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                                if (showLocationTrailingIcon) {
                                    IconButton(onClick = onUseCurrentLocation) {
                                        Icon(Icons.Filled.MyLocation, contentDescription = "Use current location")
                                    }
                                }
                            }
                        }
                    } else {
                        null
                    },
                    colors = fieldColors,
                    contentPadding = contentPadding,
                )
            }
            // A no-op onDismissRequest here before this fix meant the standard "tap outside
            // the popup" dismissal ExposedDropdownMenu already implements never actually
            // closed anything — the only way to get rid of the list was to pick a result or
            // clear the query back below MIN_QUERY_LENGTH. Wiring the real dismiss action in
            // is the fix, not new behavior invented on top of the component.
            ExposedDropdownMenu(expanded = suggestionsOpen, onDismissRequest = onDismissTaxonSuggestions) {
                if (uiState.taxonSearchResults.isEmpty() && uiState.taxonSearchHasNoResults) {
                    DropdownMenuItem(
                        text = { Text("No matches for “${uiState.taxonSearchQuery.trim()}”") },
                        onClick = {},
                        enabled = false,
                    )
                } else {
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
 * The ranked list.
 *
 * The Current Conditions/forecast weather panel used to sit here, above the list; it now lives in
 * the Seasonal tab instead — see [ConditionsCard] and [SeasonalTab]'s own doc comment for why
 * (PANEL-CONTENTS-DISPATCH.md item 2: Seasonal and weather are both pre-trip checks, so they were
 * consolidated into one destination).
 *
 * Trip windows are shown in the drawer's Trip Planner section — see [TripPlannerSection] — because
 * "what's likely nearby this month" and "when in the next few days is worth going" are different
 * questions with different lifetimes (the ranking depends on the browsed month, trip windows only
 * on the next several days), and fusing them into one scrolling column was one more step to reach
 * whichever one wasn't currently showing.
 */
@Composable
private fun ListTab(
    uiState: AvailabilityUiState,
    currentTime: CurrentTimeProvider,
    distanceUnit: DistanceUnit,
    onViewOnMap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Spacer(Modifier.height(Spacing.xs))
        // Above the ranking: it changes what everything below it means, so it cannot be something
        // the user meets after reading the list.
        if (uiState.isShowingCachedResults) {
            OfflineResultsBanner(
                cachedAtEpochMillis = uiState.cachedResultsAsOfEpochMillis,
                nowEpochMillis = currentTime.nowEpochMillis(),
            )
        }
        // Weighted so the ranked list scrolls within a bounded height.
        ResultsSection(uiState = uiState, distanceUnit = distanceUnit, onViewOnMap = onViewOnMap, modifier = Modifier.weight(1f))
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
 * Tertiary rather than the error palette: nothing failed in a way that cost the user their answer
 * — the answer is right there, it is simply older than it looks. Reusing the error color would
 * make a real failure read as no more urgent than this.
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
private fun ResultsSection(
    uiState: AvailabilityUiState,
    distanceUnit: DistanceUnit,
    onViewOnMap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
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
                        SpeciesRow(entry, onViewOnMap = onViewOnMap)
                    }
                }
            }
        }
    }
}

/**
 * The Seasonal tab: the [ConditionsCard] weather panel (current conditions plus today's forecast),
 * above a test of [FruitingPatternAssumptions.FRUITING_LAG_DAYS] — the 7–21 day rain-to-fruiting-lag
 * rule of thumb [TripWindowsCard] and [ForagingWeatherGuidanceSection] already state as unmeasured
 * field lore — against real historical iNaturalist sightings and real historical Open-Meteo
 * rainfall for the current search.
 *
 * The two sit in one destination per PANEL-CONTENTS-DISPATCH.md item 2: Seasonal and weather are
 * both pre-trip checks (Seasonal rare, weather per-trip), so consolidating them puts everything
 * checked before leaving in one place. The weather panel is listed first — it's the more frequently
 * consulted of the two — and does not gate on or wait for the fruiting-lag fetch below it: they are
 * fetched independently (see [AvailabilityViewModel.refresh] for the conditions/forecast fetch,
 * [AvailabilityViewModel.onSeasonalTabSelected] for the lazily-fetched fruiting-lag pattern below).
 *
 * **The fruiting-lag section does not feed [AvailabilityEntry.relativeLikelihood] or the ranked
 * List tab.** It answers one narrow question — does the data support this one named lag range —
 * and nothing here changes how species are ranked. See [FruitingLagDistribution]'s own doc comment.
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
            if (uiState.conditions != null || uiState.conditionsErrorMessage != null ||
                uiState.isLoadingTodaysForecast || uiState.todaysForecast != null || uiState.todaysForecastErrorMessage != null
            ) {
                ConditionsCard(
                    conditions = uiState.conditions,
                    conditionsErrorMessage = uiState.conditionsErrorMessage,
                    isLoadingTodaysForecast = uiState.isLoadingTodaysForecast,
                    todaysForecast = uiState.todaysForecast,
                    todaysForecastErrorMessage = uiState.todaysForecastErrorMessage,
                )
            }
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
 * [com.forager.app.domain.GeoDistance]/[com.forager.app.domain.MgrsConverter] being hand-built
 * rather than pulled from a library for a single use.
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
 * Which of the three-way menu's options is currently mid-pick, between the menu closing and the
 * centre-pin picker confirming — see [MapTab]/[CompactMapTab]'s own doc comments. There is no
 * "none, but the picker is still showing" state: the picker overlay's own visibility is driven by
 * this being non-null, not a separate flag, so the two can never disagree about whether a pick is
 * in progress.
 */
private enum class PendingMapAction { PLAN_TRIP, LOG_FIND, DROP_WAYPOINT }

/**
 * Owns the location-placing flow: [MapSlot] only reports where the camera currently is (see its
 * own doc comment on [MapSlot.onCameraIdle]), so the pending action and what it turns into both
 * live here, next to the only place that location can come from. The three-way menu's own button
 * offers three outcomes — plan a trip ([TripDatePickerDialog], turning it into a saved
 * [PlannedTrip]), log a find ([onLogFindHere], which opens the mushroom log's drawer destination
 * — see `docs/plans/mushroom-log.md`'s Navigation section for why this reuses the same entry point
 * rather than adding a second one), or drop a waypoint ([WaypointNameDialog]) — so
 * [ThreeWayActionDialog] asks which before any of the three runs, and only *then* does
 * [CentrePinLocationPicker] ask where: a button press carries no location the way a long-press
 * gesture used to, so which comes first had to invert. [defaultTripName] is computed from the trip
 * count already in state, here rather than inside the dialog, so the dialog stays a dumb presenter
 * of whatever default it's handed.
 */
@Composable
private fun MapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    renderMode: MapRenderMode,
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    breadcrumbPoints: List<LatLng>,
    waypoints: List<Waypoint>,
    onDropWaypoint: (LatLng, String) -> Unit,
    /** See [AvailabilityScreen]'s own `mapTaxonFilter` doc comment — "View on Map" from a List-tab row. */
    taxonFilter: Long?,
    onClearTaxonFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var showMapModePicker by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingMapAction?>(null) }
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
                var cameraCenter by remember(region) { mutableStateOf(LatLng(region.lat, region.lng)) }
                var tappedSighting by remember { mutableStateOf<Sighting?>(null) }
                var tappedSightingScreenPosition by remember { mutableStateOf(Offset.Zero) }
                var tappedSightingBearingDeg by remember { mutableStateOf(0f) }
                val context = LocalContext.current
                Column(modifier = modifier.fillMaxWidth()) {
                    // "View on Map" from a List-tab row: limits the map to one species' sightings
                    // rather than every mapped one. Filtered against uiState.sightings itself
                    // (what the map actually draws), not against uiState.forecast's own
                    // observationCount — the two can legitimately disagree (the forecast is a
                    // separate historical query; the map only shows what actually loaded for this
                    // region), so the count in mapTaxonFilterLabel below is what's really on screen.
                    val filteredSightings = if (taxonFilter != null) {
                        uiState.sightings.filter { it.taxonId == taxonFilter }
                    } else {
                        uiState.sightings
                    }
                    val mapTaxonFilterLabel = taxonFilter?.let { id ->
                        val name = uiState.forecast?.entries?.firstOrNull { it.species.taxonId == id }?.species
                            ?.let { it.commonName ?: it.scientificName }
                            ?: filteredSightings.firstOrNull()?.let { it.commonName ?: it.scientificName }
                            ?: "this species"
                        "$name (${filteredSightings.size})"
                    }
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        mapSlot(
                            region,
                            MapOverlayContent(
                                sightings = filteredSightings,
                                plannedTrips = uiState.plannedTrips,
                                breadcrumbPoints = breadcrumbPoints,
                                waypoints = waypoints,
                                focusedObservationId = tappedSighting?.observationId,
                            ),
                            renderMode,
                            null,
                            {},
                            // A plain tap elsewhere on the map is what dismisses the bubble below —
                            // see ObservationBubble's own doc comment for why this replaced a modal
                            // AlertDialog. Harmless to clear when nothing is showing.
                            { tappedSighting = null },
                            { sighting, screenPosition, bearingDeg ->
                                tappedSighting = sighting
                                tappedSightingScreenPosition = screenPosition
                                tappedSightingBearingDeg = bearingDeg
                            },
                            { location -> cameraCenter = location },
                            Modifier.fillMaxSize(),
                        )
                        tappedSighting?.let { sighting ->
                            AnchoredAtScreenPoint(
                                anchorPx = tappedSightingScreenPosition,
                                bearingDeg = tappedSightingBearingDeg,
                                modifier = Modifier.fillMaxSize(),
                            ) { arrowAngleDeg ->
                                ObservationBubble(
                                    sighting = sighting,
                                    onViewOnINaturalist = {
                                        launchINaturalistObservation(context, sighting.observationId)
                                        tappedSighting = null
                                    },
                                    onDismiss = { tappedSighting = null },
                                    arrowAngleDeg = arrowAngleDeg,
                                )
                            }
                        }
                        mapTaxonFilterLabel?.let { label ->
                            TaxonMapFilterChip(
                                label = label,
                                onClear = onClearTaxonFilter,
                                modifier = Modifier.align(Alignment.TopCenter).padding(Spacing.sm),
                            )
                        }
                        MapModeToggle(
                            mapMode = mapMode,
                            onClick = { showMapModePicker = true },
                            modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm),
                        )
                        MapModePicker(
                            visible = showMapModePicker,
                            mapMode = mapMode,
                            onModeSelected = onMapModeSelected,
                            onDismiss = { showMapModePicker = false },
                        )
                        // MEDIUM/EXPANDED's own trigger for the three-way menu — CompactMapTab has
                        // MapIconBar's own add row to repurpose for this; this window class has no
                        // icon bar at all, so this is new here rather than reused. Same icon, same
                        // content description, same corner as the compact bar's own add row, for
                        // the same button to mean the same thing on every layout that has one — see
                        // this file's doc comment on why medium/expanded needed a button added
                        // rather than an existing one converted.
                        MapFloatingIconButton(
                            icon = Icons.Filled.Add,
                            contentDescription = "Plan a trip or log a find here",
                            onClick = { showActionMenu = true },
                            filled = true,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.sm),
                        )
                        if (pendingAction != null) {
                            CentrePinLocationPickerOverlay(
                                onConfirm = {
                                    when (pendingAction) {
                                        PendingMapAction.PLAN_TRIP -> pendingTripLocation = cameraCenter
                                        PendingMapAction.LOG_FIND -> onLogFindHere(cameraCenter)
                                        PendingMapAction.DROP_WAYPOINT -> pendingWaypointLocation = cameraCenter
                                        null -> Unit
                                    }
                                    pendingAction = null
                                },
                                onCancel = { pendingAction = null },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }
            }
        }
    }

    // CentrePinLocationPickerOverlay is a plain overlay, not a real Dialog — unlike
    // ThreeWayActionDialog below (an AlertDialog, which already handles system back for free) —
    // so unlike this composable's menu, its own picker phase needs an explicit BackHandler or
    // system back would fall straight through it.
    BackHandler(enabled = pendingAction != null) {
        pendingAction = null
    }

    if (showActionMenu) {
        ThreeWayActionDialog(
            onPlanTrip = {
                showActionMenu = false
                pendingAction = PendingMapAction.PLAN_TRIP
            },
            onLogFind = {
                showActionMenu = false
                pendingAction = PendingMapAction.LOG_FIND
            },
            onDropWaypoint = {
                showActionMenu = false
                pendingAction = PendingMapAction.DROP_WAYPOINT
            },
            onDismiss = { showActionMenu = false },
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

/** The filled circle behind an active/permanent-accent row (add, record while recording) inside [MapIconBar] — smaller than [MIN_TOUCH_TARGET] so it reads as a badge within the row rather than filling it edge to edge. */
private val MAP_ICON_BAR_FILL_DIAMETER = 36.dp

/** Half [MIN_TOUCH_TARGET], so [MapIconBar]'s rounded corners read as a stadium/pill shape rather than a slightly-rounded rectangle. */
private val MAP_ICON_BAR_CORNER_RADIUS = MIN_TOUCH_TARGET / 2

/**
 * [MapIconBar]'s fill — see [MapBarIconButton]. Picked per [com.forager.app.ui.theme.LocalForagerDarkTheme]
 * (the app's own "Night Mode" checkbox, not the device's system theme — see that local's own doc
 * comment for the bug reading [androidx.compose.foundation.isSystemInDarkTheme] directly here caused), independent of
 * the map's own night mode (deliberately not derived from it, the same way [MapPalette] is
 * deliberately not derived from the ambient scheme — this is app chrome over the map, not a mark
 * on it, but the two axes are still kept separate on principle: a device in light mode looking at
 * a map in night mode, or the reverse, are both real states, and neither should silently steer
 * the other).
 *
 * **History: opaque, plus a hairline edge, replacing the translucent fill these circles used
 * before.** Confirmed on real hardware (Portland-metro, USGS Topo): at a 78%-alpha fill, map data
 * underneath — contour lines, place labels — composited straight through the buttons, reading as
 * barely-there smudges; the stack's one already-opaque icon (the green "add" button,
 * [MaterialTheme.colorScheme.primary]) read perfectly on the same terrain in the same screenshot.
 * Opacity was the only difference. **Requested back down to 80% since** — a deliberate, informed
 * choice after that finding was raised again, not a reversion made without knowing it. Provisional
 * pending a hardware look at this specific value, the same status the border below already had.
 *
 * **Rejected alternative: tuning the alpha per basemap.** A single translucency value can be tuned
 * to look right against one basemap's palette, but this app ships pale topo, dark aerial imagery,
 * and (later) hillshade — a fill that reads on one will not read on the others, and per-basemap
 * tuning means re-solving this every time a basemap is added.
 *
 * The hairline border ([MAP_ICON_STACK_BORDER_COLOR_DARK]/[MAP_ICON_STACK_BORDER_COLOR_LIGHT]) is
 * what keeps a *dark* fill working on imagery specifically: an opaque dark circle alone reads fine
 * against pale topo but risks merging into imagery, which is dark nearly everywhere. A light edge
 * separates control from map regardless of what's underneath. The light-theme fill below is new,
 * unverified on hardware in either direction — its own dark hairline border mirrors the same
 * reasoning for the opposite risk (merging into snow, sand, or other pale terrain), but nobody has
 * looked at it on a real screen yet.
 */
private val MapIconStackButtonColorDark = Bark.copy(alpha = 0.8f)

/** [MapIconStackButtonColorDark]'s light-theme counterpart — see that color's own doc comment. */
private val MapIconStackButtonColorLight = Cream.copy(alpha = 0.8f)

/** The hairline edge on [MapIconStackButtonColorDark] circles — see that color's own doc comment. */
private val MAP_ICON_STACK_BORDER_COLOR_DARK = Color.White.copy(alpha = 0.4f)

/** The hairline edge on [MapIconStackButtonColorLight] circles — the inverse of [MAP_ICON_STACK_BORDER_COLOR_DARK], for the inverse risk (a light fill merging into pale terrain rather than a dark one merging into dark imagery). */
private val MAP_ICON_STACK_BORDER_COLOR_LIGHT = Bark.copy(alpha = 0.4f)

/**
 * The add row's (and [MapFloatingIconButton]'s) permanent accent fill, and the record row's while
 * active — deliberately [MapIconBarAccent], not [MaterialTheme.colorScheme.primary]/
 * [MaterialTheme.colorScheme.error] directly, even though every value it holds is one of those same
 * roles' own hues. See [MapIconBarAccent]'s own doc comment for the full reasoning (Material's tonal
 * inversion exists for legibility against a plain [MaterialTheme.colorScheme.surface]; these two
 * rows sit on [MapIconBar]'s own opaque bar fill instead, which already does that job, so reading
 * Material's roles directly here just reads backwards).
 */
private fun mapIconBarAddAccent(isDarkTheme: Boolean) =
    if (isDarkTheme) MapIconBarAccent.ADD_DARK else MapIconBarAccent.ADD_LIGHT

/** See [mapIconBarAddAccent] — the record row's while-active counterpart. */
private fun mapIconBarRecordAccent(isDarkTheme: Boolean) =
    if (isDarkTheme) MapIconBarAccent.RECORD_DARK else MapIconBarAccent.RECORD_LIGHT

/** Translucent background for [CompassElevationStripContent] and [SearchDropdown]'s own panel — dark-theme value. Was 0.78, one alpha step off the app's settled 80% map-chrome opacity ([MapIconStackButtonColorDark]'s own value); the map/navigation search-UI redo dispatch names 80% as the one value all map chrome shares, so this now matches rather than carrying its own near-miss. */
private val CompassStripBackgroundColorDark = Bark.copy(alpha = 0.8f)

/** [CompassStripBackgroundColorDark]'s light-theme counterpart — same reasoning as [MapIconStackButtonColorLight]: picked per [com.forager.app.ui.theme.LocalForagerDarkTheme], independent of the map's own night mode, unverified on hardware. */
private val CompassStripBackgroundColorLight = Cream.copy(alpha = 0.8f)

/**
 * The Maps tab in its full-bleed, compact-only form — decision #2 in `docs/plans/map-redesign.md`:
 * the map fills the entire content area, with the top compass/elevation strip and the right-edge
 * icon stack drawn over it.
 *
 * Scoped to `WindowWidthClass.COMPACT` only; `MEDIUM`/`EXPANDED` keep using the unmodified [MapTab]
 * inside [CombinedResultsPane] — see the plan doc's "Scope decision" section for why this is a
 * separate composable rather than a conditional threaded through [MapTab] itself.
 *
 * Owns the location-placing flow exactly as [MapTab] does — see that composable's doc comment for
 * the mechanics [PendingMapAction] drives; [TripDatePickerDialog]/[defaultTripName] are shared,
 * unmodified, but the "what would you like to do here" chooser itself is [AddActionTile] here
 * rather than [MapTab]'s [ThreeWayActionDialog] — see that composable's own doc comment for why.
 * The icon stack's add (+) button reuses this exact same flow — it sets [showActionMenu] directly,
 * the identical trigger the map's own dedicated button sets on [MapTab], rather than a parallel
 * dialog/handler — so the two entry points can never drift apart. Unlike before this rework, it no
 * longer needs to hand the flow a starting location itself: [CentrePinLocationPicker]'s own camera
 * tracking supplies that once a choice is made, the same as every other site.
 */
@Composable
private fun CompactMapTab(
    uiState: AvailabilityUiState,
    mapSlot: MapSlot,
    renderMode: MapRenderMode,
    isNightMode: Boolean,
    mapMode: MapMode,
    onMapModeSelected: (MapMode) -> Unit,
    onPlaceTripPin: (LatLng, LocalDate, String) -> Unit,
    onLogFindHere: (LatLng) -> Unit,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
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
    /** See [AvailabilityScreen]'s own `mapTaxonFilter` doc comment — "View on Map" from a List-tab row. */
    taxonFilter: Long?,
    onClearTaxonFilter: () -> Unit,
    /**
     * True while [AdvancedSearchDropdown]'s "Set on map" is active — a [compactMainScaffold]-owned
     * state, not local to this tab, since the dropdown that triggers it lives above the bottom-nav
     * switch and can be reached from any tab. Shows [CentrePinLocationPickerOverlay] over this same
     * map the same way [pendingAction] already does, rather than a second picker.
     */
    pickingSearchLocation: Boolean = false,
    onSearchLocationPicked: (LatLng) -> Unit = {},
    onCancelSearchLocationPick: () -> Unit = {},
    /**
     * Extra top clearance beyond the compass strip's own row, for chrome this tab doesn't know
     * about that now floats above it — SearchEntryBar, on the compact scaffold's own Map tab (see
     * that call site's own doc comment). Defaults to 0.dp rather than being required: this
     * composable has exactly one call site today, but the strip/bubble/filter-chip positioning
     * below already treats "how much is above me" as a real, named input
     * ([compassStripClearance]) rather than assuming 0 — this parameter extends that same
     * assumption to cover chrome composed outside this function entirely, instead of baking a
     * second, undocumented assumption in above it.
     */
    topInset: Dp = 0.dp,
    /**
     * SearchEntryBar (plus its SearchNotice), composed as a slot inside this composable's own
     * Box rather than passed up and rendered at the call site — a deliberate, load-bearing
     * placement, not a style choice: this bar's own 80%-alpha fill needs to blend against real
     * map imagery to read as translucent chrome the way the compass strip and the two
     * TrailheadControls pills already do, and both of those live in this exact Box, as direct
     * siblings of [mapSlot]'s own [AndroidView][androidx.compose.ui.viewinterop.AndroidView]
     * content. Composing the bar even one level further out (a sibling of this whole composable's
     * own call, in [compactMainScaffold]'s outer `Box` instead) was tried first and shipped
     * fully opaque on a real device despite an identical `Surface`/color/alpha to the strip and
     * pills, and despite its own geometry measuring correctly positioned above the map — Compose's
     * alpha-blending coordination with an embedded native `View` (this map is `AndroidView`-hosted)
     * appears to be scoped to the immediate composition that hosts it, not just correct z-order
     * anywhere in the tree above it. Defaults to an empty slot: this composable has exactly one
     * call site today (compactMainScaffold's own Map tab branch), which supplies the bar; nothing
     * else needs to know this parameter exists.
     */
    searchBarSlot: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var showMapModePicker by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingMapAction?>(null) }
    var pendingTripLocation by remember { mutableStateOf<LatLng?>(null) }
    var pendingWaypointLocation by remember { mutableStateOf<LatLng?>(null) }
    var tappedSighting by remember { mutableStateOf<Sighting?>(null) }
    var tappedSightingScreenPosition by remember { mutableStateOf(Offset.Zero) }
    var tappedSightingBearingDeg by remember { mutableStateOf(0f) }
    // See MapOverlayContent.resumeTrackingRequestId's own doc comment — incremented alongside the
    // existing onLocateMe() call below, not instead of it: that call still drives the compass
    // strip's own one-shot position/elevation text, this drives the map's live GPS camera puck.
    var resumeTrackingRequestId by remember { mutableStateOf(0) }
    // See MapOverlayContent.resetOrientationRequestId's own doc comment.
    var resetOrientationRequestId by remember { mutableStateOf(0) }
    // MapIconBar's own real measured bottom edge, in px relative to this Box — the anchor
    // ControlPill/DistanceArm position themselves against below. See MapIconBar's own call site
    // for why this is measured rather than a hardcoded offset or a value computed from the bar's
    // row count (that row count changes twice in quick succession across this dispatch and the
    // next one, so any such constant goes stale before the layout phase finishes).
    var mapIconBarBottomPx by remember { mutableStateOf(0f) }

    // AddActionTile and CentrePinLocationPickerOverlay are both plain overlays, not real Dialogs,
    // so — unlike TripDatePickerDialog below, an M3 DatePickerDialog whose own Dialog window
    // already handles system back for free — this needs its own BackHandler or system back would
    // fall straight through either, same reasoning as AvailabilityScreen's own top-level "unwind
    // before falling through" chain. One pop at a time: the picker phase first if it's showing,
    // the menu only once the picker's already closed. pickingSearchLocation joins the same picker
    // tier as pendingAction (both show the identical CentrePinLocationPickerOverlay, just for a
    // different caller) rather than a third priority level of its own.
    BackHandler(enabled = pendingAction != null || pickingSearchLocation || showActionMenu) {
        when {
            pendingAction != null -> pendingAction = null
            pickingSearchLocation -> onCancelSearchLocationPick()
            else -> showActionMenu = false
        }
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
    // Same one-shot-per-transition shape as the locateMeStatus effect above: a refused/failed
    // startRecording() is an event ("the action you just took didn't happen"), not a persistent
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

            // Sightings/planned trips are only real once a search has actually run — before that,
            // displayRegion is a viewport with nothing plotted on it yet, not a stand-in search.
            val hasSearched = uiState.region != null
            // "View on Map" from a List-tab row — see MapTab's own doc comment on the identical
            // filteredSightings/mapTaxonFilterLabel pair for why this filters uiState.sightings
            // itself rather than trusting uiState.forecast's own observationCount.
            val filteredSightings = when {
                !hasSearched -> emptyList()
                taxonFilter != null -> uiState.sightings.filter { it.taxonId == taxonFilter }
                else -> uiState.sightings
            }
            val mapTaxonFilterLabel = taxonFilter?.let { id ->
                val name = uiState.forecast?.entries?.firstOrNull { it.species.taxonId == id }?.species
                    ?.let { it.commonName ?: it.scientificName }
                    ?: filteredSightings.firstOrNull()?.let { it.commonName ?: it.scientificName }
                    ?: "this species"
                "$name (${filteredSightings.size})"
            }

            var cameraCenter by remember(displayRegion) { mutableStateOf(LatLng(displayRegion.lat, displayRegion.lng)) }
            // A real clearance for "below the compass strip," derived from the strip's own actual
            // type style rather than a hardcoded touch-target constant (Part A item 1 of this
            // dispatch un-pinned the strip's height back to wrapping its text content, so a fixed
            // 48dp guess would now be too generous). Measured once via rememberTextMeasurer — the
            // same approach DistanceArm uses for its own widest-string width below — rather than
            // read back from the strip's real onGloballyPositioned layout: a state value written
            // during layout and read here to construct AnchoredAtScreenPoint's own minY argument
            // was tried and is a confirmed, reproducible regression — AvailabilityScreenMapIconStackTest's
            // own "tapping elsewhere on the map dismisses the observation bubble" test went from
            // passing to reliably failing on exactly that change (bisected line by line), corrupting
            // mapSlot(...)'s own onTap wiring one frame later for reasons this investigation could
            // not fully pin down inside Compose's own recomposition-scope internals. A remembered,
            // one-time measurement carries no such risk: it never changes after first composition,
            // so nothing here ever triggers a later recomposition.
            val compassStripTextMeasurer = rememberTextMeasurer()
            val compassStripLabelStyle = MaterialTheme.typography.labelMedium
            val compassStripDensity = LocalDensity.current
            val compassStripClearance = remember(compassStripLabelStyle, compassStripDensity) {
                with(compassStripDensity) {
                    compassStripTextMeasurer.measure("Mg", compassStripLabelStyle).size.height.toDp()
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                mapSlot(
                    displayRegion,
                    MapOverlayContent(
                        sightings = filteredSightings,
                        plannedTrips = if (hasSearched) uiState.plannedTrips else emptyList(),
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = waypoints,
                        resumeTrackingRequestId = resumeTrackingRequestId,
                        resetOrientationRequestId = resetOrientationRequestId,
                        focusedObservationId = tappedSighting?.observationId,
                    ),
                    renderMode,
                    focusOverride,
                    {},
                    // Tapping the map restores chrome while fullscreen — decision #5 — AND, now,
                    // dismisses the observation bubble below regardless of fullscreen state (a plain
                    // tap elsewhere on the map is its whole dismiss gesture, see ObservationBubble's
                    // own doc comment). Harmless to clear when nothing is showing.
                    {
                        if (isFullscreen) onToggleFullscreen()
                        tappedSighting = null
                    },
                    { sighting, screenPosition, bearingDeg ->
                        tappedSighting = sighting
                        tappedSightingScreenPosition = screenPosition
                        tappedSightingBearingDeg = bearingDeg
                    },
                    { location -> cameraCenter = location },
                    Modifier.fillMaxSize(),
                )
                // Composed right after mapSlot — see searchBarSlot's own doc comment for why this
                // exact nesting (a direct sibling of the map's own AndroidView content, inside
                // this Box) is what makes its translucency actually work.
                searchBarSlot()
                tappedSighting?.let { sighting ->
                    // minY = compassStripClearance, a real measurement of the strip's own type
                    // style — not a hardcoded touch-target constant and not 0 — the strip is
                    // composed after this in the same Box (deliberately, so its own controls win
                    // any overlap — see CompactMapTab's own doc comment above MapIconBar), and is
                    // full-width/flush against the map's top edge. A marker tapped near the map's
                    // own top edge would otherwise anchor a bubble underneath that strip's band: its
                    // own taps (including the close icon's) would never reach this composable,
                    // silently swallowed by the strip's own Surface the exact way CLAUDE.md's
                    // "Known pitfalls" already documents for this app's map overlays — the same
                    // class of miss that entry warns visual review alone won't catch, this time
                    // guarded against directly rather than only caught by this bubble's own
                    // close-icon interaction test. See compassStripClearance's own doc comment for
                    // why it is a one-time text measurement rather than the strip's real measured
                    // layout height.
                    AnchoredAtScreenPoint(
                        anchorPx = tappedSightingScreenPosition,
                        bearingDeg = tappedSightingBearingDeg,
                        minY = topInset + compassStripClearance,
                        modifier = Modifier.fillMaxSize(),
                    ) { arrowAngleDeg ->
                        ObservationBubble(
                            sighting = sighting,
                            onViewOnINaturalist = {
                                launchINaturalistObservation(context, sighting.observationId)
                                tappedSighting = null
                            },
                            onDismiss = { tappedSighting = null },
                            arrowAngleDeg = arrowAngleDeg,
                        )
                    }
                }
                // MapIconBar composed *before* CompassElevationStrip now, not after — field-test
                // dispatch item 2 gave the strip a real touch target at its own far right edge, the
                // same horizontal column MapIconBar's CenterEnd alignment already claims.
                // MapIconBar's Surface intercepts touches across its full bounds (see this
                // composable's own CLAUDE.md-documented precedent), and on a short enough viewport
                // its vertically-centered row stack reaches all the way up into the compass strip's
                // own row — confirmed directly by AvailabilityScreenMapIconStackTest's own
                // touch-interaction test on a w360dp-h640dp viewport, not assumed from visual review
                // alone (the exact class of miss that same file's own history warns visual review
                // alone won't catch). Composition order is paint AND hit-test order for overlapping
                // siblings in a Box, so moving this earlier guarantees the strip's own control wins
                // any overlap on every screen size, not just typical ones — a small cosmetic cost
                // (the strip's opaque background could cover a sliver of one icon bar row on a
                // screen too short for MapIconBar's own rows to fit at all — already a degraded
                // state before this change) traded for a control that always actually works. Still
                // true after MapIconBar's own return-to-vehicle row was removed (see that
                // composable's own doc comment) — the overlap this guards against is with the bar's
                // Surface as a whole, not specifically with that one row.
                MapIconBar(
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = onToggleFullscreen,
                    onLocateMe = {
                        resumeTrackingRequestId++
                        onLocateMe()
                    },
                    onResetOrientation = { resetOrientationRequestId++ },
                    mapMode = mapMode,
                    onOpenMapModePicker = { showMapModePicker = true },
                    isNightMode = isNightMode,
                    onAdd = {
                        // No location to grab any more — the button just opens the menu; the
                        // location comes from CentrePinLocationPickerOverlay's own camera tracking
                        // once a choice is made. See this function's own doc comment.
                        showActionMenu = true
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(Spacing.sm)
                        // Reports this bar's own actual laid-out bottom edge, relative to this Box,
                        // into mapIconBarBottomPx — what ControlPill/DistanceArm anchor against
                        // below. See mapIconBarBottomPx's own doc comment for why this is measured
                        // rather than a hardcoded offset or a value computed from the bar's row
                        // count.
                        .onGloballyPositioned { coordinates ->
                            mapIconBarBottomPx = coordinates.boundsInParent().bottom
                        },
                )
                CompassElevationStrip(
                    compassProvider = compassProvider,
                    elevationMeters = uiState.liveAltitudeMeters,
                    location = uiState.liveLocation,
                    // Full width, "just below" SearchEntryBar rather than a narrow floating pill
                    // with margins on both sides, per the project owner's own redesign call — topInset
                    // is how that clearance reaches here now that the bar composes as a real overlay
                    // in the same Box as this tab's own content (compactMainScaffold's own call
                    // site) instead of a sibling Column entry above it; 0.dp (this parameter's own
                    // default) reproduces the old flush-against-the-map-top behavior exactly.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = topInset),
                )
                // Always composed, regardless of isRecording — record start/stop must stay reachable
                // before the first recording starts, the same as it was as an always-enabled
                // MapIconBar row before this dispatch. isRecording flows in as a plain parameter
                // (see TrailheadControls' own doc comment for how it drives the return-to-vehicle
                // row's disabled state), not a presence check, so a tester never sees this pill
                // appear from nowhere the first time they hit record.
                TrailheadControls(
                    isRecording = isRecording,
                    onToggleRecording = onToggleRecording,
                    returnToStart = returnToStart,
                    isReturning = isReturning,
                    isOffTrack = isOffTrack,
                    onToggleReturning = onToggleReturning,
                    mapIconBarBottomPx = mapIconBarBottomPx,
                    modifier = Modifier.align(Alignment.TopEnd),
                )

                // Below the compass strip (topInset + compassStripClearance as top padding), same
                // reasoning as AnchoredAtScreenPoint's own minY — the strip's Surface intercepts
                // touches across its full width, so a chip placed underneath it would have its own
                // "Show all species" tap silently swallowed the same way a bubble anchored there
                // would. topInset itself (see this composable's own doc comment) clears whatever
                // chrome floats above the strip too — SearchEntryBar, on the Map tab.
                mapTaxonFilterLabel?.let { label ->
                    TaxonMapFilterChip(
                        label = label,
                        onClear = onClearTaxonFilter,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = topInset + compassStripClearance + Spacing.sm),
                    )
                }

                // Inside this Box, not alongside it, so it can align near the add button's own
                // corner of the icon stack above — see AddActionTile's doc comment for why this
                // reads as opening "from" that button rather than as a centered system dialog.
                AddActionTile(
                    visible = showActionMenu,
                    onPlanTrip = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.PLAN_TRIP
                    },
                    onLogFind = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.LOG_FIND
                    },
                    onDropWaypoint = {
                        showActionMenu = false
                        pendingAction = PendingMapAction.DROP_WAYPOINT
                    },
                    onDismiss = { showActionMenu = false },
                    modifier = Modifier.fillMaxSize(),
                )

                MapModePicker(
                    visible = showMapModePicker,
                    mapMode = mapMode,
                    onModeSelected = onMapModeSelected,
                    onDismiss = { showMapModePicker = false },
                    anchor = Alignment.CenterEnd,
                    anchorOffset = DpOffset(x = -Spacing.sm, y = MAP_MODE_PICKER_COMPACT_ANCHOR_OFFSET),
                )

                if (pendingAction != null) {
                    CentrePinLocationPickerOverlay(
                        onConfirm = {
                            when (pendingAction) {
                                PendingMapAction.PLAN_TRIP -> pendingTripLocation = cameraCenter
                                PendingMapAction.LOG_FIND -> onLogFindHere(cameraCenter)
                                PendingMapAction.DROP_WAYPOINT -> pendingWaypointLocation = cameraCenter
                                null -> Unit
                            }
                            pendingAction = null
                        },
                        onCancel = { pendingAction = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (pickingSearchLocation) {
                    // AdvancedSearchDropdown's own "Set on map" — same overlay, same already-shown
                    // map, same cameraCenter this tab already tracks via onCameraIdle; see this
                    // param's own doc comment for why it isn't a second picker.
                    CentrePinLocationPickerOverlay(
                        onConfirm = { onSearchLocationPicked(cameraCenter) },
                        onCancel = onCancelSearchLocationPick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
 * The right-edge panel bar — supersedes the individual floating circles decision #3 in
 * `docs/plans/map-redesign.md` specified (see that doc's "Icon stack: superseded from 5 to a
 * 7-icon stopgap" section for the account of the intermediate step this replaces). One
 * translucent, rounded bar hugging the map's right edge, per the project owner's own request,
 * rather than eight separately-floating circles with gaps between them — every control that used
 * to be its own circle is now one row inside this shared bar instead, including
 * [onResetOrientation], which used to be MapLibre's own native compass view (disabled in
 * [SightingsMap] once this existed — see that composable's own doc comment).
 *
 * Top to bottom: fullscreen, orientation-reset, GPS/locate-me, map mode (slot 4 opens
 * [MapModePicker] — the same picker [MapModeToggle] opens for the untouched MEDIUM/EXPANDED path,
 * restyled rather than reused directly so MEDIUM/EXPANDED's own styling stays untouched), add. The
 * add button keeps its own green fill — real state, not decoration — everything else tints its
 * icon rather than its own background, since the bar itself is the shared background now.
 *
 * **No return-to-vehicle row.** Field-test dispatch item 2 gave the compass strip's own
 * `ReturnToVehicleStripControl` a visible readout and kept this bar's identical row alongside it
 * deliberately, so the field test itself would decide which placement testers actually reached
 * for. The owner's verdict, from real hardware: the compass strip control alone — this row was a
 * confusing duplicate and is removed, not merely hidden.
 *
 * **No record row**, as of an earlier dispatch's Part B — record start/stop moved into
 * `ControlPill` alongside return-to-vehicle, the two Trailhead/Return controls, which belong
 * together rather than split across this bar and the compass strip.
 *
 * **No search row either, as of this dispatch.** The icon here used to open
 * [CompactToolsDrawerContent]; that drawer now opens from the bottom nav's own `CompactTab.TOOLS`
 * entry instead (see that entry's own doc comment) — one entry point, not two, now that Tools is a
 * real bottom-nav destination rather than a row buried in a map-verbs bar. Re-derived directly
 * against the tree rather than assumed: this bar was 6 rows before this change (fullscreen,
 * orientation-reset, locate-me, map mode, search, add), not 7 or 8 as either of the last two
 * dispatches' own text claimed at the time each was written; removing search leaves 5 — fullscreen,
 * orientation-reset, locate-me, map mode, add — which still reads as a coherent group of map verbs
 * (viewport and pin-drop actions only, now that both Trailhead controls and search have moved to
 * homes of their own). [mapIconBarRowAnchorOffset]'s `rowCount` and [ADD_TILE_ANCHOR_OFFSET]'s row
 * index are updated to match.
 */
@Composable
private fun MapIconBar(
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onLocateMe: () -> Unit,
    onResetOrientation: () -> Unit,
    mapMode: MapMode,
    onOpenMapModePicker: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Night mode as it currently resolves — Settings' "Night Maps" checkbox
     * ([AvailabilityUiState.nightModeMaps]), shown here in slot 4's content description so the
     * state is readable rather than merely visible. No longer toggleable from this bar directly
     * (a long-press here used to hold it; that control moved to Settings — see
     * [com.forager.app.domain.MapPreferencesRepository.getNightModeMaps]'s own doc comment).
     */
    isNightMode: Boolean = false,
) {
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = if (isDarkTheme) Color.White else Bark,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapBarIconButton(
                icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = if (isFullscreen) "Exit fullscreen" else "Fullscreen",
                onClick = onToggleFullscreen,
            )
            MapBarIconButton(
                icon = Icons.Filled.Navigation,
                contentDescription = "Reset orientation to north",
                onClick = onResetOrientation,
            )
            MapBarIconButton(
                icon = Icons.Filled.MyLocation,
                contentDescription = "Center on my location",
                onClick = onLocateMe,
            )
            MapBarIconButton(
                icon = Icons.Filled.Layers,
                contentDescription = buildString {
                    append("Map mode: ${mapMode.label}. Choose Street, Topographical, or Satellite.")
                    // Appended rather than replacing the tap description: the button still
                    // primarily opens the map mode picker, and a reader needs to know night mode
                    // is on — now toggled from Settings' "Night Maps" checkbox, not from here.
                    append(if (isNightMode) " Night mode on." else " Night mode off.")
                },
                onClick = onOpenMapModePicker,
            )
            MapBarIconButton(
                icon = Icons.Filled.Add,
                contentDescription = "Plan a trip or log a find here",
                onClick = onAdd,
                filled = true,
                fillColor = mapIconBarAddAccent(isDarkTheme).fill,
                fillContentColor = mapIconBarAddAccent(isDarkTheme).onFill,
            )
        }
    }
}

/**
 * One row inside [MapIconBar] — a plain tap target tinted by state, not its own circle: the bar
 * itself is the shared background now, so a per-icon background is reserved for the two rows that
 * carry real, not merely decorative, fill state ([filled]/[fillColor]/[fillContentColor]: the add
 * button's permanent accent, the record button's error accent while active — see
 * [MapIconBarAccent]'s own doc comment for why each caller passes its own theme-swapped pair here
 * rather than reading [MaterialTheme.colorScheme.primary]/[error] directly).
 */
@Composable
private fun MapBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Gives this row its own filled circle in [fillColor] rather than tinting just the icon. */
    filled: Boolean = false,
    fillColor: Color = Color.Unspecified,
    /** The icon's own tint while [filled] — must be picked for contrast against [fillColor] specifically, not [MapIconBar]'s own bar-level contentColor. */
    fillContentColor: Color = Color.White,
    /**
     * `false` dims and disables the tap target — the return-to-vehicle row's state while nothing
     * is recording, when there is nothing yet to return to.
     */
    enabled: Boolean = true,
    /** Tints just the icon (not a background) for a toggle that is currently "on" but not [filled] — the return-to-vehicle row. */
    activeColor: Color? = null,
) {
    Box(
        modifier = modifier
            .size(MIN_TOUCH_TARGET)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (filled) {
            Box(
                modifier = Modifier
                    .size(MAP_ICON_BAR_FILL_DIAMETER)
                    .background(color = fillColor, shape = CircleShape),
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // filled rows sit on their own saturated circle (green/error), so their icon needs
            // fillColor's own contrast pair, not MapIconBar's bar-level contentColor; everything
            // else inherits that bar-level color via LocalContentColor, unless a state override
            // (activeColor) says otherwise.
            tint = if (filled) fillContentColor else activeColor ?: LocalContentColor.current,
        )
    }
}

/**
 * A single freestanding circular icon button — MEDIUM/EXPANDED's own add-trip/log-find trigger is
 * the one remaining user of the compact bar's old per-icon-circle look, now that [MapIconBar]'s
 * rows share one background instead. Kept as its own small composable rather than folded into
 * [MapBarIconButton]: a lone button floating directly over the map still needs its own opaque
 * fill plus hairline border to read against the map, the way [MapIconStackButtonColorDark]'s own
 * doc comment documents — a bar row can lean on the shared bar background for that instead.
 *
 * `filled`'s own accent is [mapIconBarAddAccent], the same theme-swapped [MapIconBarAccent]
 * [MapIconBar]'s add row uses and for the same reason (see that type's own doc comment) — this is
 * the same button on a window class with no bar for it to sit inside, so it keeps the same accent
 * rather than falling back to `MaterialTheme.colorScheme.primary`.
 */
@Composable
private fun MapFloatingIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val addAccent = mapIconBarAddAccent(isDarkTheme)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            filled -> addAccent.fill
            isDarkTheme -> MapIconStackButtonColorDark
            else -> MapIconStackButtonColorLight
        },
        contentColor = when {
            filled -> addAccent.onFill
            isDarkTheme -> Color.White
            else -> Bark
        },
        shadowElevation = 2.dp,
        border = if (filled) {
            null
        } else {
            BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT)
        },
        modifier = modifier.size(MIN_TOUCH_TARGET),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
    }
}

/** Gap between [MapIconBar]'s own measured bottom edge and [ControlPill]'s top edge — matches [MapIconBar]'s own `Spacing.sm` inset from the screen edge, so the pill reads as continuing the same margin rather than sitting at an arbitrarily different distance. */
private val CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR = Spacing.sm

/**
 * The two Trailhead/Return controls — record start/stop and return-to-vehicle — anchored together
 * below [MapIconBar], per this dispatch's own Part B: they used to be split across a
 * [MapIconBar] row and a duplicate compass-strip readout; now they share one home.
 *
 * **Anchored relative to [MapIconBar]'s own measured bottom edge, not a hardcoded offset and not
 * a value computed from the bar's row count.** [mapIconBarBottomPx] comes from
 * [CompactMapTab]'s own `onGloballyPositioned` on the bar's call site (`boundsInParent().bottom`),
 * not from [mapIconBarRowAnchorOffset]-style row-count arithmetic the way [MapModePicker] and
 * [AddActionTile] anchor themselves — deliberately: the bar's own row count changes twice in quick
 * succession across this dispatch and the next one (record leaves here, search leaves in the
 * follow-up dispatch), so any constant derived from today's row count or height would already be
 * stale by the time the next dispatch lands, and this pill would visibly drift on exactly the
 * build field testers are using. A real measured value tracks whatever the bar's current row count
 * happens to be, automatically, with no second edit required when it changes again.
 *
 * **Composition order: [DistanceArm] first, [ControlPill] second**, both inside one [Box] so a
 * [Box]'s own paint-and-hit-test-order-by-declaration rule (the same rule [MapIconBar] composing
 * before [CompassElevationStrip] already relies on, see that call site's own doc comment) makes
 * the pill win any overlap at their shared junction — the correct precedence, since a tap near
 * that corner should reach a control, not the arm's own plain readout. This is the third surface
 * in the same outer [Box] whose composition order is now load-bearing, alongside the
 * bar-before-strip ordering above; the two are independent (this pair doesn't overlap either the
 * bar or the strip) but both must survive the AvailabilityScreen split this file is scheduled for.
 *
 * `isRecording` is passed through as a plain parameter, not a presence check gating whether this
 * composable runs at all — record start/stop must stay reachable before the first recording
 * starts, the same as when it was an always-enabled [MapIconBar] row.
 *
 * **[DistanceArm]'s own right end is a circle, not a square edge tucked under the pill — see that
 * composable's own doc comment for why a flat edge and a couple px of tuck-under weren't enough.**
 * That circle's diameter is [ControlPill]'s own measured width ([pillSizePx]`.width`), and it must
 * be *centred on the return-to-vehicle control itself*, not flush with the pill's own bottom edge:
 * [ControlPill]'s `Column` wraps its two rows in `Modifier.padding(vertical = Spacing.xs)`, so the
 * return-to-vehicle row's own centre sits [Spacing.xs] above where the pill's own rounded bottom
 * corner is centred (exactly [MAP_ICON_BAR_CORNER_RADIUS] above the pill's bottom edge, by that
 * shape's own definition). A circle of the pill's own cap radius, centred anywhere on the pill's
 * own vertical spine — which the return-to-vehicle control's centre sits on, being horizontally
 * centred in a column no wider than the pill itself — is fully contained in the pill's stadium
 * shape by construction, so no extra tuck-under margin is needed once this offset is right.
 */
@Composable
private fun TrailheadControls(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    mapIconBarBottomPx: Float,
    modifier: Modifier = Modifier,
) {
    // ControlPill's own real measured size, in px — what DistanceArm positions itself against
    // below.
    var pillSizePx by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = modifier
            .padding(end = Spacing.sm)
            .offset {
                IntOffset(x = 0, y = (mapIconBarBottomPx + CONTROL_PILL_GAP_BELOW_MAP_ICON_BAR.toPx()).roundToInt())
            },
    ) {
        // First: DistanceArm. Both children align TopEnd against this Box's own corner — the same
        // corner ControlPill's own top-right lands on. x stays 0: DistanceArm's own right end is a
        // circle the same diameter as the pill's own width (see that composable's own doc
        // comment), so aligning both TopEnd with no x offset makes the two circles — the arm's own
        // cap and the pill's own bottom corner — coincide exactly, with no tuck-under math needed.
        // y lands the arm's own circular cap centred on the return-to-vehicle control's own centre
        // (see this function's own doc comment for why that is Spacing.xs above the pill's bottom
        // edge, not flush with it): pillSizePx.height take away the circle's own diameter
        // (pillSizePx.width) lands the cap's centre flush with the pill's bottom edge, so a further
        // Spacing.xs subtracted moves it up to the control's own centre. Depends on pillSizePx,
        // which is why ControlPill (below) must report its size before this offset can be correct
        // — harmless on the frame or two before the first measurement lands, since DistanceArm is
        // invisible (isReturning starts false) until a real return leg begins.
        DistanceArm(
            visible = isReturning,
            distanceMeters = returnToStart?.distanceMeters,
            isOffTrack = isOffTrack,
            circleDiameterPx = pillSizePx.width,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset {
                    IntOffset(
                        x = 0,
                        y = pillSizePx.height - pillSizePx.width - Spacing.xs.roundToPx(),
                    )
                },
        )
        // Second: ControlPill, so it paints on top of DistanceArm at their shared junction.
        ControlPill(
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            returnToStart = returnToStart,
            isReturning = isReturning,
            isOffTrack = isOffTrack,
            onToggleReturning = onToggleReturning,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .onGloballyPositioned { coordinates -> pillSizePx = coordinates.size },
        )
    }
}

/**
 * The vertical control pill at the bottom right, below [MapIconBar] — record start/stop and
 * return-to-vehicle, the two Trailhead/Return controls, sharing one home per this dispatch's own
 * Part B rather than split across a [MapIconBar] row and a duplicate compass-strip readout.
 *
 * Same visual language as [MapIconBar] on purpose — same [MAP_ICON_BAR_CORNER_RADIUS] stadium
 * shape, same theme-aware surface/border colors, same [MapBarIconButton] rows — so this reads as a
 * sibling of the bar, not a new kind of floating control. Both rows carry over unchanged from
 * [MapIconBar]'s own former record/return-to-vehicle rows, including their accents
 * ([mapIconBarRecordAccent]) and the return row's disabled-while-not-recording treatment.
 */
@Composable
private fun ControlPill(
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    returnToStart: ReturnToStartInfo?,
    isReturning: Boolean,
    isOffTrack: Boolean,
    onToggleReturning: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        shape = RoundedCornerShape(MAP_ICON_BAR_CORNER_RADIUS),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = if (isDarkTheme) Color.White else Bark,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
        modifier = modifier.testTag("control-pill"),
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.xs),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MapBarIconButton(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording track" else "Start recording track",
                onClick = onToggleRecording,
                filled = isRecording,
                fillColor = mapIconBarRecordAccent(isDarkTheme).fill,
                fillContentColor = mapIconBarRecordAccent(isDarkTheme).onFill,
                modifier = Modifier.testTag("control-pill-record"),
            )
            MapBarIconButton(
                icon = Icons.Filled.Directions,
                contentDescription = returnToStartStripText(isRecording, returnToStart)
                    .ifBlank { "Return to vehicle — start recording first" },
                onClick = onToggleReturning,
                enabled = isRecording,
                activeColor = when {
                    isOffTrack -> MaterialTheme.colorScheme.error
                    isReturning -> MaterialTheme.colorScheme.primary
                    else -> null
                },
                modifier = Modifier.testTag("control-pill-return-to-vehicle"),
            )
        }
    }
}

/** The widest plausible [formatReturnDistance] output — measured, not counted: a two-digit km reading with its decimal ("99.9 km") is wider in this typeface than any three-digit metre reading ("999 m"), so this is what [DistanceArm] sizes its own fixed-width readout against. */
private const val DISTANCE_ARM_WIDEST_TEXT = "99.9 km"

/**
 * [DistanceArm]'s own settled opacity, once its enter transition finishes growing out from
 * [ControlPill] — requested directly: the whole arm (fill, border, shadow, and readout text
 * together, via a single [androidx.compose.ui.draw.alpha] on the outer [Surface]) fades in from
 * fully invisible rather than popping in at full strength, and settles at 80%, not 100%, once
 * the width animation completes; deactivating reverses both in lockstep. Coincidentally the same
 * number as [MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]'s own baked-in fill
 * alpha, but a separate, independent property applied to the *entire* Surface — those colors'
 * translucency only ever affected the fill, never the border, shadow, or text this also dims.
 */
private const val DISTANCE_ARM_RESTING_ALPHA = 0.8f

/**
 * The horizontal arm that extends left from [ControlPill] while return-to-vehicle is active,
 * holding the distance-only readout — see [ControlPill]'s own return row for the full
 * bearing/distance/elevation sentence this supplements (that row's `contentDescription` carries
 * it; this arm shows plain visible text only, so a test can assert it via `onNodeWithText` rather
 * than `onNodeWithContentDescription` alone, per this repo's own testing rule).
 *
 * **Sized to the widest plausible string, not to content.** [DISTANCE_ARM_WIDEST_TEXT] is measured
 * at this Text's own real [MaterialTheme.typography] style via [rememberTextMeasurer] — not
 * counted in characters — so the fixed width this arm animates to is the actual rendered pixel
 * width in the actual typeface at the actual size, not a guess.
 *
 * **Tabular figures** (`fontFeatureSettings = "tnum"`): this number updates live while someone
 * walks toward their car, and proportional digits would shimmer the text sideways as the value
 * changes on exactly the leg where someone is watching it.
 *
 * **A width animation, not shape morphing** — [AnimatedVisibility]'s
 * [expandHorizontally]/[shrinkHorizontally], driven by [MotionTokens.navigationMotionSpec] (chrome;
 * no positional truth to distort, per docs/adr/0002-motion-scheme-adoption.md's category table —
 * this is that category's first production caller). Shape morphing is listed experimental in the
 * M3 design study's own stable/experimental table; a width animation reads the same at a fraction
 * of the cost and needs nothing beyond what this codebase already uses elsewhere
 * ([AddActionTile]'s own `expandIn`/`shrinkOut`).
 *
 * **Right end is a full circle, diameter [circleDiameterPx] — not a square edge tucked under
 * [ControlPill].** An earlier revision squared the right end (`topEnd`/`bottomEnd` = 0) and tucked
 * it a couple px under the pill's own rounded left edge. Confirmed broken on real hardware:
 * [MAP_ICON_BAR_CORNER_RADIUS] is exactly half [ControlPill]'s own measured width, so the pill's
 * bottom-left corner is a full semicircular cap spanning this arm's *entire* shared row height —
 * there is no height within that row where the pill's real edge is a flat vertical line for a
 * squared arm edge to tuck under. See [TrailheadControls]' own doc comment for the full geometry.
 *
 * [circleDiameterPx] (the pill's own measured width, passed down rather than assumed — see
 * [TrailheadControls]) sets both this Surface's own fixed height and its `topEnd`/`bottomEnd`
 * corner radius (halved), so the right end is a true full circle, congruent with the pill's own
 * bottom cap, at every width this arm can reach — the enter/exit animations below never go
 * narrower than the diameter itself, so there is no width at which that corner could be less than
 * a full semicircle. A circle of that exact radius is fully contained in the pill's own stadium
 * shape wherever it's centred on the pill's own vertical spine (which is exactly where
 * [TrailheadControls] positions it), so no tuck-under margin is needed, and none remains.
 *
 * **Resting state (not returning) is that circle alone** — [expandHorizontally]'s `initialWidth`
 * and [shrinkHorizontally]'s `targetWidth` are both pinned to [circleDiameterPx], not the default
 * `0`, so growing the arm out is "circle → circle-plus-readout" rather than "nothing → full."
 * [MAP_ICON_BAR_CORNER_RADIUS] governs the *left* end regardless of [circleDiameterPx] — it is
 * [ControlPill]'s own fixed visual language, not derived from this arm's diameter — and since the
 * animation never goes narrower than [circleDiameterPx] itself, the left cap always has its full
 * own diameter of room and can never squash into the right end's curve.
 *
 * Content reserves [circleDiameterPx]'s own width on the right — the circular base's own
 * footprint, always fully covered by [ControlPill], nothing drawn there — with the readout text
 * confined to (and centred within) the region to its left, so the text itself is never drawn under
 * the curve.
 *
 * **Fades in and out alongside the width change**, [fadeIn]/[fadeOut] bundled into the same
 * enter/exit as [expandHorizontally]/[shrinkHorizontally] so both finish together — not a pop-in
 * at full strength, and not fully opaque even once settled: see [DISTANCE_ARM_RESTING_ALPHA]'s own
 * doc comment for the 80% ceiling and why it applies to the whole [Surface], not just its fill.
 */
@Composable
private fun DistanceArm(
    visible: Boolean,
    distanceMeters: Double?,
    isOffTrack: Boolean,
    circleDiameterPx: Int,
    modifier: Modifier = Modifier,
) {
    val isDarkTheme = LocalForagerDarkTheme.current
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val numberStyle = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum")
    val widestNumberWidthPx = remember(numberStyle) {
        textMeasurer.measure(DISTANCE_ARM_WIDEST_TEXT, numberStyle).size.width
    }
    val circleDiameterDp = with(density) { circleDiameterPx.toDp() }
    val readoutWidthDp = with(density) { widestNumberWidthPx.toDp() } + Spacing.md
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = MotionTokens.navigationMotionSpec()) +
            expandHorizontally(
                animationSpec = MotionTokens.navigationMotionSpec(),
                expandFrom = Alignment.End,
                initialWidth = { circleDiameterPx },
            ),
        exit = fadeOut(animationSpec = MotionTokens.navigationMotionSpec()) +
            shrinkHorizontally(
                animationSpec = MotionTokens.navigationMotionSpec(),
                shrinkTowards = Alignment.End,
                targetWidth = { circleDiameterPx },
            ),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = MAP_ICON_BAR_CORNER_RADIUS,
                bottomStart = MAP_ICON_BAR_CORNER_RADIUS,
                topEnd = circleDiameterDp / 2,
                bottomEnd = circleDiameterDp / 2,
            ),
            color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
            contentColor = if (isDarkTheme) Color.White else Bark,
            shadowElevation = 2.dp,
            border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            modifier = Modifier
                .height(circleDiameterDp)
                // fadeIn/fadeOut above animate 0→1→0 in lockstep with the width transition (part of
                // the same AnimatedVisibility Transition, so both finish together); this fixed
                // multiplier caps the settled/fully-grown end of that ramp at
                // DISTANCE_ARM_RESTING_ALPHA instead of fully opaque — see that constant's own doc
                // comment for why, and for why it's a coincidence, not a reuse, that the number
                // matches MapIconStackButtonColorDark/Light's own fill alpha.
                .alpha(DISTANCE_ARM_RESTING_ALPHA)
                .testTag("distance-arm"),
        ) {
            // Outer box reserves the full readout-plus-circle width, readout content start-aligned
            // within it — the trailing circleDiameterDp is deliberately empty (the circular base's
            // own footprint, always hidden under ControlPill) rather than shared with the text.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(readoutWidthDp + circleDiameterDp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(readoutWidthDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = distanceMeters?.let { formatReturnDistance(it) }.orEmpty(),
                        style = numberStyle,
                        color = if (isOffTrack) MaterialTheme.colorScheme.error else LocalContentColor.current,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
    modifier: Modifier = Modifier,
) {
    val headingDegrees by compassProvider.heading.collectAsState(initial = null)
    CompassElevationStripContent(
        headingDegrees = headingDegrees,
        elevationMeters = elevationMeters,
        location = location,
        modifier = modifier,
    )
}

/**
 * Heading, elevation, and coordinates only — one centered line. Pure readout, per this dispatch's
 * Part A item 3: the return-to-vehicle readout field-test dispatch item 2 added here is removed,
 * not merely hidden — it moved into [ControlPill]/[DistanceArm] instead, alongside record
 * start/stop, so the two Trailhead/Return controls live in one place rather than split between
 * this strip and [MapIconBar]. **Corrected 2026-08-28**: named `MapIconStack` here before that
 * composable was renamed.
 */
@Composable
private fun CompassElevationStripContent(
    headingDegrees: Float?,
    elevationMeters: Double?,
    location: LatLng?,
    modifier: Modifier = Modifier,
) {
    // A plain Box + background, not Surface: Surface (even with no onClick) intercepts pointer
    // input for the area it occupies, which — now that this strip is full-width — swallowed the
    // map's own pan/tap gestures underneath it (the same failure class IntrinsicSize.Max fixed
    // for the narrow pill, but that fix meant shrinking the strip back down, which isn't an option
    // now that full width is the point). A Box with no pointer/click handling of its own doesn't
    // intercept anything, so the map keeps receiving touches everywhere except this strip's own
    // real interactive child (the coordinates segment below).
    //
    // Local state, not AvailabilityUiState: this is purely which of two always-computable string
    // representations of the same fix to display, nothing the ViewModel or a future session needs
    // to remember — the same reasoning showQuickSearch elsewhere in this file already applies to a
    // similar tap-to-reveal toggle.
    var showDecimalDegrees by remember { mutableStateOf(false) }
    // Independent of the map's own night mode -- see MapIconStackButtonColorDark's own doc
    // comment for why the two axes are kept separate rather than one steering the other.
    val isDarkTheme = LocalForagerDarkTheme.current
    CompositionLocalProvider(LocalContentColor provides if (isDarkTheme) Color.White else Bark) {
        Box(
            modifier = modifier
                // No heightIn(min = ...) any more — Part A item 1's revert. The 48dp touch-target
                // floor field-test dispatch item 2 pinned here existed only to give the strip's own
                // return-to-vehicle IconButton a real touch target; that control moved out to
                // ControlPill (Part B), so this strip wraps its Row's natural text-content height
                // again, the same as before that dispatch.
                .background(
                    color = if (isDarkTheme) CompassStripBackgroundColorDark else CompassStripBackgroundColorLight,
                    shape = RectangleShape,
                )
                // Lets CompactMapTab's own onGloballyPositioned measure this strip's real height —
                // AnchoredAtScreenPoint's minY and the taxon filter chip's top padding both need to
                // clear it, and now that it wraps content instead of sitting at a fixed 48dp, a
                // measured value is the only one that stays correct as font scale or content change
                // it. Also still what this composable's own regression test targets directly.
                .testTag("compass-elevation-strip"),
        ) {
            Row(
                // fillMaxWidth, not fillMaxSize — see this Box's own doc comment above for the
                // hardware-caught bug an unbounded-height descendant caused here previously; nothing
                // in this Row asks for available height any more, but the fillMaxWidth-not-fillMaxSize
                // choice stays deliberate rather than reverting to whichever one happens to still work.
                modifier = Modifier
                    // A little horizontal padding again, unlike the zero this Row held right before
                    // Part A: that zero relied on two MIN_TOUCH_TARGET end boxes (this icon's, and
                    // the return-to-vehicle control's) to supply real whitespace at both edges by
                    // themselves. The return-to-vehicle box is gone (moved to ControlPill) and this
                    // icon's own box no longer holds itself to a 48dp touch target (it isn't one —
                    // see below), so nothing is left to keep the text off the strip's own edges
                    // without this. Re-added once, in the direction opposite the three prior trims
                    // Part A item 4 warns about, and explained rather than blindly re-trimmed.
                    .padding(horizontal = Spacing.sm)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fixed-width slot, not inline in the text group below — a hardware report found the
                // compass needle visibly drifting left/right as the heading/elevation/coordinates
                // text next to it changed length, because it used to be the first child of that
                // centered group rather than its own fixed slot; this box's position never depends on
                // any text's width. Sized to its own content (no explicit .size()), not
                // MIN_TOUCH_TARGET: Part A item 2's revert — this icon is decorative (no click
                // affordance of its own), so pinning it to a 48dp touch-target box only existed to
                // match the return-to-vehicle control's box that shared this Row; that control is
                // gone, and holding this box at 48dp anyway would defeat item 1's height revert by
                // becoming the Row's own tallest child in the vehicle box's place.
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Navigation,
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(headingDegrees ?: 0f),
                    )
                }
                // Heading, elevation, and coordinates, taking whatever width is left after the fixed
                // compass-icon box above — this group used to share its weight(1f) budget with that
                // icon's inline width, which is exactly the width the coordinates segment's own
                // ellipsis was giving up first on a narrow screen (a hardware report: "cut off for no
                // reason" — there was room, it just wasn't reaching this Text). Pulling the icon out
                // of this Row's own measurement entirely is the fix, not a wider budget. Only one
                // fixed sibling now (Part A item 3 removed the strip's own return-to-vehicle box),
                // so this group's own available width is wider still than when that box also took a
                // share. TextOverflow.Ellipsis on the coordinates segment stays as the last-resort
                // safety net for a screen too narrow for all three fields regardless, not
                // horizontalScroll — see this composable's own doc comment above for why
                // horizontalScroll was rejected (it intercepts touches meant for the map underneath).
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                ) {
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
            }
        }
    }
}

/**
 * The return-to-vehicle line's text — blank while not recording (nothing to return to), a status
 * message once recording starts but before the first breadcrumb lands (bearing/distance need a
 * start point), then bearing, distance, and elevation difference once [info] is real. Now
 * [ControlPill]'s own `contentDescription` for its return-to-vehicle row (this used to also feed
 * the compass strip's own visible readout via `ReturnToVehicleStripControl`, removed in this
 * dispatch's Part A — see [CompassElevationStripContent]'s own doc comment).
 * [ReturnToStartInfo]'s own doc comment covers why there's no ETA here.
 */
internal fun returnToStartStripText(isRecording: Boolean, info: ReturnToStartInfo?): String {
    if (!isRecording) return ""
    if (info == null) return "Recording — waiting for a fix to compute the way back"
    val elevationText = info.elevationDifferenceMeters?.let {
        "${if (it >= 0) "+" else ""}${it.roundToInt()} m"
    } ?: "elevation diff. unavailable"
    val bearing = info.bearingDegrees.roundToInt()
    return "Return: $bearing° ${cardinalDirection(info.bearingDegrees.toFloat())} · ${formatReturnDistance(info.distanceMeters)} · $elevationText"
}

/** "412 m" below 1 km, "1.2 km" at or above — shared by [returnToStartStripText] and [DistanceArm]'s own readout. */
private fun formatReturnDistance(distanceMeters: Double): String =
    if (distanceMeters < 1000) "${distanceMeters.roundToInt()} m" else "${"%.1f".format(distanceMeters / 1000)} km"

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

/**
 * What the three-way menu button means — asked before any of [TripDatePickerDialog],
 * `onLogFindHere`, or [WaypointNameDialog] run, and before [CentrePinLocationPicker] even shows;
 * see [MapTab]'s own doc comment. Named for what it asks, not for the gesture that used to trigger
 * it — nothing here is long-press-specific any more, see decision 5 in
 * `docs/plans/pr26-rework.md`'s Workstream L.
 */
@Composable
private fun ThreeWayActionDialog(
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
 * [CompactMapTab]'s own version of the same three-way chooser [ThreeWayActionDialog] shows on
 * medium/expanded windows — same three choices, same shared [PendingMapAction] state, but
 * presented as a small tile that grows out of the add button's own corner of the icon bar rather
 * than [AlertDialog]'s centered scale-in, per the project owner's own description of how it should
 * open. Compact-only: the medium/expanded window's own add button (added alongside this rework —
 * see [MapTab]'s doc comment) has no icon bar for a tile to grow out of, so [MapTab] keeps the
 * plain dialog instead.
 *
 * **Real [AssistChip]s, short labels, no title row.** The original had a "Add..." title plus four
 * full-width [TextButton]s (three choices, and its own "Cancel") — a real hardware-reported
 * problem, not a style preference: a full-width button is as wide as its longest label, which made
 * the whole tile noticeably wider than the icon bar it grows out of. Short labels ("Trip"/
 * "Find"/"Waypoint") in a [Row] instead of a [Column] let the tile's own width shrink to what three
 * short buttons actually need, rather than the longest of three sentences. No separate "Cancel"
 * button either — the scrim below already dismisses on a tap outside, and a fourth wide row would
 * undo the same fit this rewrite exists for.
 *
 * **[AssistChip], not the filled Material [Button] this tile used at first.** A solid
 * `colorScheme.primary`-filled button is its own large block of colour, and three of them side by
 * side visually crowded out the tile's own theme-aware card fill entirely — from the project
 * owner's own side-by-side comparison against [MapModePicker], whose unselected [FilterChip]s stay
 * outlined and let that same card fill read clearly instead. [AssistChip] is the semantically
 * correct match, not [FilterChip] borrowed wholesale: these three rows perform an action each and
 * share no "currently selected" state the way [MapModePicker]'s three modes do, so nothing here is
 * ever passed a `selected` value.
 *
 * Same theme-aware, 80%-opacity fill as [MapIconBar]
 * ([MapIconStackButtonColorDark]/[MapIconStackButtonColorLight]) rather than a plain
 * [MaterialTheme.colorScheme.surface] — one visual language for every control floating over the
 * map, the same reasoning [MapModePicker] gives for the same choice.
 *
 * A scrim (its own [AnimatedVisibility], faded independently of the tile) makes the map behind it
 * unmistakably unavailable to tap while a choice is pending — the same modal intent the dialog it
 * replaces had, just without borrowing [AlertDialog]'s fixed presentation. The scrim carries no
 * visible label of its own (unlike the three buttons beside it), so it is addressed in tests by
 * [ADD_ACTION_TILE_SCRIM_TAG] rather than text.
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
    val isDarkTheme = LocalForagerDarkTheme.current
    Box(modifier = modifier) {
        // docs/motion-spec.md §2 "Panels and navigation": panels accept mild spring overshoot as
        // a taste call, per docs/adr/0002-motion-scheme-adoption.md. Passing MotionTokens's spec
        // explicitly here removes any dependence on AnimatedVisibility's own default spec.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ADD_ACTION_TILE_SCRIM_TAG)
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
            enter = fadeIn(animationSpec = MotionTokens.panelMotionSpec()) +
                expandIn(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    expandFrom = Alignment.BottomEnd,
                ),
            exit = fadeOut(animationSpec = MotionTokens.panelMotionSpec()) +
                shrinkOut(
                    animationSpec = MotionTokens.panelMotionSpec(),
                    shrinkTowards = Alignment.BottomEnd,
                ),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = -Spacing.sm, y = ADD_TILE_ANCHOR_OFFSET),
        ) {
            Surface(
                shape = RoundedCornerShape(Spacing.md),
                shadowElevation = 4.dp,
                color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
                contentColor = if (isDarkTheme) Color.White else Bark,
                border = BorderStroke(1.dp, if (isDarkTheme) MAP_ICON_STACK_BORDER_COLOR_DARK else MAP_ICON_STACK_BORDER_COLOR_LIGHT),
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    AssistChip(onClick = onPlanTrip, label = { Text("Trip") })
                    AssistChip(onClick = onLogFind, label = { Text("Find") })
                    AssistChip(onClick = onDropWaypoint, label = { Text("Waypoint") })
                }
            }
        }
    }
}

/** See [AddActionTile]'s own doc comment — its scrim has no visible label, so tests address it by tag. */
internal const val ADD_ACTION_TILE_SCRIM_TAG = "add-action-tile-scrim"

/**
 * Offset from [MapIconBar]'s own vertical center (where both [AddActionTile] and [MapModePicker]
 * anchor their `Alignment.CenterEnd`-based popups) to the center of one of its rows, counting from
 * the top — the geometry [ADD_TILE_ANCHOR_OFFSET] originally computed just for the add row (row 8
 * of 8), generalized once [MapModePicker] needed the same reasoning for the layers row (row 4)
 * instead of a second guessed constant. The padding [MapIconBar] wraps its content in is symmetric
 * top/bottom, so it doesn't shift the center and isn't part of this sum. Not pixel-exact — neither
 * caller tracks its own button's real measured position — but close enough that each popup visibly
 * grows from that button's corner rather than from an unrelated point on screen.
 */
// rowCount re-derived directly against the tree, not assumed: this bar sits at 5 rows as of this
// dispatch (fullscreen, orientation-reset, locate-me, map mode, add) — removing the search row
// (see MapIconBar's own doc comment). Both this count and ADD_TILE_ANCHOR_OFFSET's row index below
// are updated to match, rather than left to drift the way this constant already has twice before.
private fun mapIconBarRowAnchorOffset(rowIndexFromTop: Int): Dp {
    val rowCount = 5
    val contentHeight = MIN_TOUCH_TARGET * rowCount + Spacing.xs * (rowCount - 1)
    val rowCenterFromTop = (MIN_TOUCH_TARGET + Spacing.xs) * (rowIndexFromTop - 1) + MIN_TOUCH_TARGET / 2
    return rowCenterFromTop - contentHeight / 2
}

/** [MapIconBar]'s add row is its 5th (last) of 5 — see [mapIconBarRowAnchorOffset]. */
private val ADD_TILE_ANCHOR_OFFSET = mapIconBarRowAnchorOffset(rowIndexFromTop = 5)

/** [MapIconBar]'s layers ("Map Mode") row is its 4th of 5 — see [mapIconBarRowAnchorOffset]. */
private val MAP_MODE_PICKER_COMPACT_ANCHOR_OFFSET = mapIconBarRowAnchorOffset(rowIndexFromTop = 4)

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
 * Confirms a date and name for a trip pin placed via [com.forager.app.ui.map.CentrePinLocationPicker].
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
 * Confirms a name for a waypoint placed via [com.forager.app.ui.map.CentrePinLocationPicker] —
 * [TripDatePickerDialog] without the date, since a waypoint has none ([Waypoint] carries no target date, unlike
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
                "No trips planned yet. Tap the add button on the map to plan one.",
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
 *
 * [errorMessage] takes priority over the list, same branch order [TripWindowsCard] uses for
 * [AvailabilityUiState.tripWindowsErrorMessage] — a failed load/add/remove is belief-changing (the
 * list on screen may not be what's actually saved), so it replaces the list rather than sitting
 * beside it.
 *
 * Scrolls itself ([Modifier.verticalScroll]) rather than depending on a scrollable caller — same
 * reasoning as [OfflineMapsPanel]'s own outer `Column`. Journal restructure Stage 1 moved this out
 * of the Tools drawer's `SearchControls`, which supplied that scroll, into [com.forager.app.ui.log.RecordsTab]'s
 * flat `Column(fillMaxSize())`, which does not — this is now `WaypointsSection`'s only caller.
 */
@Composable
internal fun WaypointsSection(
    waypoints: List<Waypoint>,
    errorMessage: String?,
    onDeleteWaypoint: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** How many Cartography entries currently keep a reference to each waypoint (by id) — Journal Stage 2b's 4b deletion warning, shown in the confirm dialog below. */
    entryReferenceCounts: Map<String, Int> = emptyMap(),
) {
    // Journal Stage 2b, 4b: this section had no delete confirmation at all before — every other
    // per-row delete in this drawer (OfflineRegionsSection, PlannedTripsList) already confirms
    // first, and a deletion warning needs somewhere to show itself. Same pendingDelete-then-dialog
    // shape as OfflineRegionsSection.
    var pendingDeleteWaypoint by remember { mutableStateOf<Waypoint?>(null) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when {
            errorMessage != null -> Text(
                errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            waypoints.isEmpty() -> Text(
                "No waypoints dropped yet. Tap the add button on the map to drop one.",
                style = MaterialTheme.typography.bodySmall,
            )

            else -> waypoints.forEach { waypoint ->
                WaypointRow(waypoint = waypoint, onDelete = { pendingDeleteWaypoint = waypoint })
            }
        }
    }

    pendingDeleteWaypoint?.let { waypoint ->
        val referencingEntryCount = entryReferenceCounts[waypoint.id] ?: 0
        AlertDialog(
            onDismissRequest = { pendingDeleteWaypoint = null },
            title = { Text("Delete \"${waypoint.name}\"?") },
            text = {
                Text(
                    // No permanence claim — see OfflineRegionsSection's identical dialog for why.
                    if (referencingEntryCount > 0) {
                        "This waypoint appears in $referencingEntryCount ${if (referencingEntryCount == 1) "journal entry" else "journal entries"}."
                    } else {
                        "Delete this waypoint?"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWaypoint(waypoint.id)
                        pendingDeleteWaypoint = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteWaypoint = null }) { Text("Cancel") } },
        )
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

/** Shown when nothing can handle [inaturalistObservationIntent] — CLAUDE.md: report, don't swallow. */
private const val NO_INATURALIST_LINK_MESSAGE = "Nothing installed can open this observation."

/**
 * iNaturalist's own Android app package — confirmed on the Play Store listing
 * (`https://play.google.com/store/apps/details?id=org.inaturalist.android`), not guessed. Used to
 * target the app explicitly rather than relying on an implicit intent's own app-link resolution:
 * hardware testing found the plain implicit `ACTION_VIEW` below always opened a browser even with
 * the iNaturalist app installed, meaning its own intent filter for inaturalist.org isn't a verified
 * Android App Link on real devices — an unverified `BROWSABLE` filter only wins a disambiguation
 * dialog, never an automatic hand-off, so an implicit intent alone can't do what item 2's dispatch
 * ("open in the app first, else a browser") actually wants.
 */
private const val INATURALIST_PACKAGE = "org.inaturalist.android"

/**
 * The web URL for a single iNaturalist observation — the only per-observation link iNaturalist
 * itself publishes, not a scheme this app owns.
 */
internal fun inaturalistObservationIntent(observationId: Long): Intent {
    val uri = Uri.parse("https://www.inaturalist.org/observations/$observationId")
    return Intent(Intent.ACTION_VIEW, uri)
}

/**
 * Resolves [webIntent] against [INATURALIST_PACKAGE] first, falling back to the plain implicit
 * intent (typically a browser) when the app doesn't claim it, and to a [Toast] when nothing does —
 * shared with [launchINaturalistObservation]'s own doc comment, same as [launchDirections].
 */
private fun launchINaturalist(context: Context, webIntent: Intent) {
    val appIntent = Intent(webIntent).setPackage(INATURALIST_PACKAGE)
    val packageManager = context.packageManager
    val intent = if (appIntent.resolveActivity(packageManager) != null) appIntent else webIntent
    if (intent.resolveActivity(packageManager) == null) {
        Toast.makeText(context, NO_INATURALIST_LINK_MESSAGE, Toast.LENGTH_SHORT).show()
        return
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, NO_INATURALIST_LINK_MESSAGE, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens [observationId]'s iNaturalist page, preferring the installed iNaturalist app over a
 * browser. Tries [INATURALIST_PACKAGE] explicitly first ([Intent.setPackage] skips any
 * disambiguation and resolves straight to that app if it declares a matching intent filter at
 * all, verified or not); only when that doesn't resolve does this fall back to the plain implicit
 * intent, which resolves to whatever the device would otherwise pick (typically a browser).
 */
internal fun launchINaturalistObservation(context: Context, observationId: Long) =
    launchINaturalist(context, inaturalistObservationIntent(observationId))

private val OBSERVATION_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * The direction [ObservationBubble] sits from the dot it names, measured clockwise from up
 * (0°/up, 90°/right, 180°/down, 270°/left), in the *map's own north-up frame* — not screen space.
 * [AnchoredAtScreenPoint] rotates this by the map's live bearing to get where the bubble actually
 * lands on screen, which is the whole point of it being a map-relative constant rather than a
 * screen-relative one: up-and-left of the dot at bearing 0° (the same corner this composable's
 * predecessor hard-coded — see its own git history) stays up-and-left *of the dot*, not up-and-left
 * *of the screen*, as the map turns underneath it.
 */
private const val OBSERVATION_BUBBLE_BASE_DIRECTION_DEG = 315f

/**
 * How far the arrow's own tip extends past [ObservationBubble]'s own edge, toward the dot it
 * names. Purely a visual "sticks out and reads as pointed" clearance — [rectEdgeIntersection]
 * already guarantees the tip's *base* sits exactly on the bubble's boundary before this is added.
 */
private val OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH = Spacing.md

/** Half the width of the arrow's own base, straddling the point [rectEdgeIntersection] returns. */
private val OBSERVATION_BUBBLE_ARROW_BASE_HALF_WIDTH = Spacing.xs

/**
 * The point where a ray from a `[-halfWidth, halfWidth] x [-halfHeight, halfHeight]` rectangle's
 * own center, at [angleDeg] (clockwise from up, screen convention: +x right, +y down), exits the
 * rectangle's boundary — the standard "ray from an axis-aligned box's center" intersection, picking
 * whichever of the box's four edges the ray reaches first. Pure geometry, shared by
 * [AnchoredAtScreenPoint] (to place [ObservationBubble] so its own arrow tip lands exactly on the
 * dot, whichever edge or corner region the current bearing puts the arrow on) and
 * [ObservationBubble] itself (to draw that same arrow, from its own measured [size][androidx.compose.ui.geometry.Size] at
 * draw time) — the one thing both need to agree on, computed once rather than twice.
 */
internal fun rectEdgeIntersection(halfWidth: Float, halfHeight: Float, angleDeg: Float): Offset {
    val radians = Math.toRadians(angleDeg.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val tx = if (dx != 0f) halfWidth / abs(dx) else Float.POSITIVE_INFINITY
    val ty = if (dy != 0f) halfHeight / abs(dy) else Float.POSITIVE_INFINITY
    val t = min(tx, ty)
    return Offset(dx * t, dy * t)
}

/**
 * Places [content] — [ObservationBubble] itself, handed the exact angle (screen-space, clockwise
 * from up) its own arrow should point back along — so that arrow's tip lands precisely on
 * [anchorPx], the tapped sighting's live screen position in the same px coordinate space
 * [MapSlot]'s own `onSightingTap` reports (see that callback's doc comment). Replaces an earlier
 * revision that hard-coded a squared-off bubble corner as an implied arrow at a fixed "5 o'clock"
 * offset from the dot — per the project owner's own follow-up call, that read ambiguously in a
 * dense cluster and didn't survive map rotation, so this version draws a real, explicit arrow
 * ([ObservationBubble]'s own [Canvas]) and keeps its tip glued to the dot's exact center rather
 * than an offset point near it.
 *
 * [bearingDeg] is what makes this "remain static on the map, but rotate with the map orientation"
 * — the project owner's own framing. [OBSERVATION_BUBBLE_BASE_DIRECTION_DEG] fixes where the
 * bubble sits *relative to the dot, in the map's own north-up frame*; rotating that by the map's
 * live bearing below is what keeps the bubble's placement correct — up-and-left of the dot, not
 * up-and-left of the screen — as the map turns, the same way a label pinned to the map surface
 * itself would. [ObservationBubble]'s own text never rotates: only the angle passed to it and the
 * bubble's own screen position change, so it stays legible through a rotate gesture rather than
 * turning upside down at some bearings — the project owner's own explicit constraint ("as long as
 * it's not spinning with the map and can read it legibly while rotating the map, and stays
 * pointing directly on the observation dot").
 *
 * A custom [Layout], not a plain [Box] + `Modifier.offset`, for the same reason as before: placing
 * the bubble so its arrow tip lands exactly on [anchorPx] needs the bubble's own measured size,
 * which isn't known until after it's laid out. Reports its own occupied size as the full incoming
 * [Constraints] (the whole map [Box]), with the child placed freely inside at the computed point,
 * clamped to stay on-screen — placing a child outside its parent's own declared bounds would leave
 * it undependably hit-testable. Clamping can still pull the bubble away from the angle-exact spot
 * near a screen edge, same limitation the predecessor's own corner-anchoring had — the arrow drawn
 * from wherever the bubble actually lands is still geometrically consistent with itself, just no
 * longer touching the dot exactly in that corner case.
 *
 * [minY] raises the lowest the bubble's own top edge may land — [CompactMapTab]'s own call site
 * passes `compassStripClearance`, a real measurement of the compass strip's own type style, so a
 * marker tapped near the map's top edge never anchors a bubble underneath that strip's full-width
 * touch-interception band (the exact hazard this file's own "Known pitfalls" precedent already
 * documents, and the reason the strip is composed after the map's own content in the first place).
 * A plain [Dp] parameter, not a lambda: an earlier version of this call site read the strip's real
 * `onGloballyPositioned` layout height back through a `mutableStateOf`, and separately a version
 * of this parameter itself was made a `() -> Int` lambda — both were tried while chasing a real,
 * reproducible regression (`AvailabilityScreenMapIconStackTest`'s "tapping elsewhere on the map
 * dismisses the observation bubble" test, bisected line by line) and neither survived: this
 * signature and a one-time, non-reactive measurement at the call site is the configuration proven
 * not to reproduce it. [MapTab] has no such strip and passes `0`.
 */
@Composable
private fun AnchoredAtScreenPoint(
    anchorPx: Offset,
    bearingDeg: Float,
    minY: Dp = 0.dp,
    modifier: Modifier = Modifier,
    content: @Composable (arrowAngleDeg: Float) -> Unit,
) {
    // The direction FROM the dot TO the bubble, in screen space: the map-relative base direction,
    // rotated backward by the camera's own clockwise rotation — the map (and everything drawn
    // relative to it) appears to turn counter-clockwise on screen as the camera turns clockwise, so
    // subtracting bearingDeg is what keeps this pinned to the dot's own frame rather than the
    // screen's.
    val screenDirectionFromDotDeg = (OBSERVATION_BUBBLE_BASE_DIRECTION_DEG - bearingDeg).mod(360f)
    // The arrow's own direction: from the bubble back toward the dot, the exact opposite of where
    // the bubble sits relative to it. This is the one value ObservationBubble needs to draw an
    // arrow consistent with wherever this Layout ends up placing it.
    val arrowAngleDeg = (screenDirectionFromDotDeg + 180f).mod(360f)
    Layout(content = { content(arrowAngleDeg) }, modifier = modifier) { measurables, constraints ->
        val minYPx = minY.roundToPx()
        // ObservationBubble's own measured size already includes OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH
        // of reserved margin on every side (its own Modifier.padding — see that composable's doc
        // comment), so the *card's* own half-extents for rectEdgeIntersection are this placeable's,
        // shrunk back down by that same margin — otherwise this would compute where the reserved
        // margin's own outer edge sits, not the visible card's, and the tip would land short of
        // anchorPx by one tail length.
        val placeable = measurables.first().measure(Constraints())
        val tailPx = OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH.toPx()
        val cardHalfWidth = placeable.width / 2f - tailPx
        val cardHalfHeight = placeable.height / 2f - tailPx
        // Where the card's own boundary sits, and how far past it the arrow's tip extends toward
        // the dot — see rectEdgeIntersection's own doc comment. The ideal (pre-clamp) placeable
        // center is anchorPx walked backward along that same tip vector, so placing the placeable
        // there makes the tip land exactly on anchorPx.
        val edgePoint = rectEdgeIntersection(cardHalfWidth, cardHalfHeight, arrowAngleDeg)
        val radians = Math.toRadians(arrowAngleDeg.toDouble())
        val tipOffsetFromCenter = Offset(
            edgePoint.x + sin(radians).toFloat() * tailPx,
            edgePoint.y - cos(radians).toFloat() * tailPx,
        )
        val idealCenter = anchorPx - tipOffsetFromCenter
        val halfWidth = placeable.width / 2f
        val halfHeight = placeable.height / 2f
        val x = (idealCenter.x - halfWidth)
            .roundToInt()
            .coerceIn(0, (constraints.maxWidth - placeable.width).coerceAtLeast(0))
        val y = (idealCenter.y - halfHeight)
            .roundToInt()
            .coerceIn(minYPx, (constraints.maxHeight - placeable.height).coerceAtLeast(minYPx))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.placeRelative(x, y)
        }
    }
}

/**
 * Tapping a sighting dot's own detail — species name and observed date, the two facts
 * [SightingsMap]'s doc comment describes this as rebuilding from the vendor-native
 * title/snippet popup MapLibre's style-layer geometry has no equivalent for. "View on iNaturalist"
 * hands off to [launchINaturalistObservation] rather than showing anything about the observation
 * this app doesn't already have cached in [Sighting] — no confidence score, no candidate species
 * list, nothing this app would need to fetch or judge itself.
 *
 * A small floating bubble over the map, not a modal [AlertDialog] (this composable's first form,
 * replaced after hardware feedback that a blocking dialog for something this minor "got in the
 * way of the UX"). No scrim, no window of its own: it sits in the same [Box] as the map and lets
 * every tap outside its own bounds fall straight through to the map beneath — dismissing itself is
 * the caller's job, wired to the map's plain [onTap] the same way `CompactMapTab`'s "tap to restore
 * chrome while fullscreen" already works, not something this composable can do on its own the way
 * [AlertDialog]'s `onDismissRequest` (a tap on the scrim, or system back) could.
 *
 * [arrowAngleDeg] — [AnchoredAtScreenPoint]'s own computed direction back toward the dot this
 * bubble names, screen-space, clockwise from up — drives a real, explicit triangular tail drawn by
 * [Modifier.drawBehind] rather than the earlier revision's single squared-off corner (an implied
 * arrow that only worked from one fixed relative position). Reserves
 * [OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH] of otherwise-invisible margin on every side via
 * [Modifier.padding] so the tail has room to protrude past the visible card's own rounded-rect
 * silhouette regardless of which side [arrowAngleDeg] currently points it out of — the same margin
 * [AnchoredAtScreenPoint] already accounts for when it places this composable, so the two agree on
 * where the tail's tip actually lands without any state passed between them: both independently
 * apply the identical [rectEdgeIntersection] formula to the same inputs. The card itself is a plain
 * [RoundedCornerShape] again (all four corners), not one squared — the tail is what reads as a
 * speech/notification bubble now, not an asymmetric corner.
 */
/**
 * [Sighting.positionalAccuracyMeters] as a note the bubble can show verbatim.
 *
 * Never omitted for a null accuracy — a missing figure is not the same as a good one, and
 * silence would read as "precise" to a user who has no way to tell the difference.
 */
private fun accuracyLabel(accuracyMeters: Int?): String =
    if (accuracyMeters != null) "±$accuracyMeters m accuracy" else "Accuracy not reported"

@Composable
private fun ObservationBubble(
    sighting: Sighting,
    onViewOnINaturalist: () -> Unit,
    onDismiss: () -> Unit,
    arrowAngleDeg: Float,
    modifier: Modifier = Modifier,
) {
    // Same theme-aware, 80%-opacity fill as MapIconBar/MapModePicker/AddActionTile — one visual
    // language for every control floating over the map, per the project owner's own request to
    // bring this bubble in line with the rest rather than Material's own surfaceContainerHigh. The
    // arrow tail below is filled with this exact same colour so it reads as part of one continuous
    // shape with the card, not a separate decoration.
    val isDarkTheme = LocalForagerDarkTheme.current
    val fillColor = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight
    Box(
        modifier = modifier
            // Drawn before padding is applied below, so this sees the full outer bounds (card plus
            // the reserved tail margin on every side) rather than the shrunken interior padding
            // leaves for the card — the same "background paints the full area, padding only moves
            // content" ordering Modifier.background(...).padding(...) already relies on elsewhere.
            .drawBehind {
                val tailPx = OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH.toPx()
                val baseHalfWidthPx = OBSERVATION_BUBBLE_ARROW_BASE_HALF_WIDTH.toPx()
                val center = Offset(size.width / 2f, size.height / 2f)
                val cardHalfWidth = center.x - tailPx
                val cardHalfHeight = center.y - tailPx
                val edgePoint = rectEdgeIntersection(cardHalfWidth, cardHalfHeight, arrowAngleDeg)
                val radians = Math.toRadians(arrowAngleDeg.toDouble())
                val dirX = sin(radians).toFloat()
                val dirY = -cos(radians).toFloat()
                val baseCenter = center + edgePoint
                val tip = Offset(baseCenter.x + dirX * tailPx, baseCenter.y + dirY * tailPx)
                // Perpendicular to the tip direction, so the tail's base straddles baseCenter along
                // whichever edge it actually sits on — correct for any angle without needing to
                // know which of the card's four edges that is.
                val base1 = Offset(baseCenter.x - dirY * baseHalfWidthPx, baseCenter.y + dirX * baseHalfWidthPx)
                val base2 = Offset(baseCenter.x + dirY * baseHalfWidthPx, baseCenter.y - dirX * baseHalfWidthPx)
                drawPath(
                    Path().apply {
                        moveTo(base1.x, base1.y)
                        lineTo(tip.x, tip.y)
                        lineTo(base2.x, base2.y)
                        close()
                    },
                    color = fillColor,
                )
            }
            .padding(OBSERVATION_BUBBLE_ARROW_TAIL_LENGTH),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .testTag("observation-bubble")
                // Consumes its own taps via a plain pointerInput, not Modifier.clickable — this
                // backdrop isn't itself a button (no ripple, no accessibility click action to fake),
                // it only needs to swallow the gesture so it doesn't fall through to the map beneath.
                // The same "a Surface wins hit-testing over whatever's beneath it in a Box" fact
                // MapIconBar's own doc comment documents, relied on here rather than guarded against:
                // a tap on the bubble should never also count as the map's own "tap elsewhere" dismiss
                // gesture. The close icon and "View on iNaturalist" text below still get first crack at
                // any tap that actually lands on them, same as any nested clickable inside one of these.
                .pointerInput(Unit) { detectTapGestures {} },
            shape = RoundedCornerShape(20.dp),
            color = fillColor,
            contentColor = if (isDarkTheme) Color.White else Bark,
            shadowElevation = 6.dp,
            tonalElevation = 3.dp,
        ) {
            Row(
                // fillMaxWidth() matters here, not just padding: a Row that wraps to its own content's
                // size can't also give the Column below a meaningful weight(1f) — Compose measures a
                // weighted child against "remaining space" that only exists once the Row's own width is
                // bounded/definite. Without this, the Column collapsed to near zero and wrapped
                // "Chanterelle" one character per line (caught by this bubble's own interaction tests,
                // not visual review).
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.md, top = Spacing.sm, end = Spacing.xs, bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        sighting.commonName ?: sighting.scientificName,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (sighting.commonName != null) {
                        Text(sighting.scientificName, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic)
                    }
                    Row(
                        // fillMaxWidth so SpaceBetween has real room to push the link to the far
                        // right — same reasoning as the outer Row's own fillMaxWidth comment above.
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            sighting.observedOn?.format(OBSERVATION_DATE_FORMAT) ?: "Observation date unknown",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "View on iNaturalist",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(onClick = onViewOnINaturalist)
                                .testTag("observation-bubble-view-on-inaturalist"),
                        )
                    }
                    Text(
                        accuracyLabel(sighting.positionalAccuracyMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                        modifier = Modifier.testTag("observation-bubble-accuracy"),
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("observation-bubble-close"),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * The standing indicator that [MapTab]/[CompactMapTab] are showing "View on Map"'s filtered
 * sightings rather than every mapped one — CLAUDE.md: a partial/filtered result has to say so, not
 * render identically to the unfiltered view. [label] is `"<species> (<count>)"`, computed by each
 * caller from the same [Sighting] list the map actually draws (see [MapTab]'s own doc comment on
 * `mapTaxonFilterLabel` for why it isn't read from [AvailabilityForecast] directly). Same
 * theme-aware, 80%-opacity fill as [ObservationBubble]/`MapIconBar`/`MapModePicker` — one visual
 * language for every control floating over the map.
 */
@Composable
private fun TaxonMapFilterChip(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val isDarkTheme = LocalForagerDarkTheme.current
    Surface(
        modifier = modifier.testTag("map-taxon-filter-chip"),
        shape = RoundedCornerShape(percent = 50),
        color = if (isDarkTheme) MapIconStackButtonColorDark else MapIconStackButtonColorLight,
        contentColor = if (isDarkTheme) Color.White else Bark,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = Spacing.md, end = Spacing.xs, top = Spacing.xs, bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Showing: $label", style = MaterialTheme.typography.labelMedium)
            IconButton(
                onClick = onClear,
                modifier = Modifier.size(24.dp).testTag("map-taxon-filter-clear"),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Show all species", modifier = Modifier.size(16.dp))
            }
        }
    }
}

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

/**
 * Recent rainfall plus today's own forecast, shown as a standalone fact at the top of the Seasonal
 * tab — never described as having factored into the ranked List tab. See
 * [com.forager.app.domain.GetConditionsUseCase] and [com.forager.app.domain.GetTodaysForecastUseCase]'s
 * own doc comments for why the two halves are separate fetches from separate provider methods, and
 * [SeasonalTab]'s own doc comment for why this card lives here rather than next to the ranking.
 *
 * [conditionsErrorMessage]/[todaysForecastErrorMessage] are the non-belief-changing empty state for
 * a failed fetch — the user wanted weather data, not a report on the network, so they render with
 * the same neutral (no `color` argument) treatment [WaypointsSection]'s empty state and
 * [MapMessage]'s default use — never `colorScheme.error` — per
 * docs/error-presentation-spec.md's per-field table. Exactly one of
 * [conditions]/[conditionsErrorMessage] is non-null at any call site, and independently the same
 * for [todaysForecast]/[todaysForecastErrorMessage]/[isLoadingTodaysForecast] — the two halves load
 * from separate fetches and can be in different states at once (see [SeasonalTab]).
 */
@Composable
private fun ConditionsCard(
    conditions: ConditionsSummary? = null,
    conditionsErrorMessage: String? = null,
    isLoadingTodaysForecast: Boolean = false,
    todaysForecast: DailyWeather? = null,
    todaysForecastErrorMessage: String? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text("Current Conditions", style = MaterialTheme.typography.titleSmall)
            if (conditions != null) {
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
            } else if (conditionsErrorMessage != null) {
                Text(conditionsErrorMessage, style = MaterialTheme.typography.bodyMedium)
            }

            HorizontalDivider()
            Text("Today's Forecast", style = MaterialTheme.typography.titleSmall)
            when {
                isLoadingTodaysForecast -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )

                todaysForecastErrorMessage != null -> Text(
                    todaysForecastErrorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                )

                todaysForecast != null -> Text(
                    "${"%.1f".format(todaysForecast.precipitationMm)}mm of rain forecast today.",
                    style = MaterialTheme.typography.bodyMedium,
                )

                else -> Text("No forecast available for today.", style = MaterialTheme.typography.bodyMedium)
            }
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

/**
 * Tapping the row (or its own explicit "View on Map" text) hands [entry]'s own
 * [SpeciesObservationCount.taxonId] up to [AvailabilityScreen]'s `onViewSpeciesOnMap`, which both
 * switches to whichever map surface the current window class shows and sets the taxon filter
 * [MapTab]/[CompactMapTab] read to limit their sightings to this one species — see that filter's
 * own doc comment on [AvailabilityScreen] for why it lives there rather than in either tab. The
 * text is a second, explicit affordance on the same line as the observation count (not the row's
 * only way to trigger it) so the action reads as discoverable rather than a hidden tap-anywhere
 * gesture.
 *
 * An earlier revision also offered "View on iNaturalist" here, opening the species' taxon page —
 * removed at the project owner's own request ("the view on iNaturalist idea was a bad one"), not
 * merely hidden: [launchINaturalistTaxon]/[inaturalistTaxonIntent] were deleted outright rather
 * than left unreferenced, since nothing else in this file used them (the map's own observation
 * bubble still uses [launchINaturalistObservation], a distinct per-observation link this removal
 * doesn't touch).
 */
@Composable
private fun SpeciesRow(entry: AvailabilityEntry, onViewOnMap: (Long) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("species-row")
            .clickable { onViewOnMap(entry.species.taxonId) },
    ) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${entry.species.observationCount} observations",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "View on Map",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onViewOnMap(entry.species.taxonId) }
                        .testTag("species-row-view-on-map"),
                )
            }
        }
    }
}
