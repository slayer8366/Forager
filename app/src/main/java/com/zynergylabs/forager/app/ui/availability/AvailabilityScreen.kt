package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.map.LocalMapKeepOuts
import com.zynergylabs.forager.app.ui.map.fanout.MapKeepOuts
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material3.DrawerDefaults
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
import androidx.compose.material3.contentColorFor
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
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.zynergylabs.forager.app.domain.GetJournalEntryHighlightsUseCase
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.crash.CrashFileStore
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ForagingSelection
import com.zynergylabs.forager.app.domain.ForagingWeatherGuidance
import com.zynergylabs.forager.app.domain.FruitingPatternAssumptions
import com.zynergylabs.forager.app.domain.LocationResult
import com.zynergylabs.forager.app.domain.MgrsConverter
import com.zynergylabs.forager.app.domain.OfflineMapRepository
import com.zynergylabs.forager.app.domain.OfflineRegionSummary
import com.zynergylabs.forager.app.domain.SystemCurrentTimeProvider
import com.zynergylabs.forager.app.domain.estimateOfflineTileCount
import com.zynergylabs.forager.app.domain.isOfflineRegionStale
import com.zynergylabs.forager.app.domain.model.AppThemeMode
import com.zynergylabs.forager.app.domain.model.AvailabilityEntry
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.ConditionsSummary
import com.zynergylabs.forager.app.domain.model.DailyWeather
import com.zynergylabs.forager.app.domain.model.DistanceUnit
import com.zynergylabs.forager.app.domain.model.UnitSystem
import com.zynergylabs.forager.app.domain.model.formatDistanceKm
import com.zynergylabs.forager.app.domain.model.formatDistanceMeters
import com.zynergylabs.forager.app.domain.model.FruitingLagBucket
import com.zynergylabs.forager.app.domain.model.FruitingLagDistribution
import com.zynergylabs.forager.app.domain.model.GalleryPhoto
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MgrsCoordinate
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.domain.model.NoTripWindowReason
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.PlannedTrip
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.ReturnToStartInfo
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.domain.model.TaxonFilter
import com.zynergylabs.forager.app.domain.model.TaxonSearchResult
import com.zynergylabs.forager.app.domain.model.Track
import com.zynergylabs.forager.app.domain.model.TrackPointRecord
import com.zynergylabs.forager.app.domain.model.TripWindow
import com.zynergylabs.forager.app.domain.model.TripWindowReport
import com.zynergylabs.forager.app.domain.model.Waypoint
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.sensor.AndroidCompassProvider
import com.zynergylabs.forager.app.sensor.AndroidDeclinationProvider
import com.zynergylabs.forager.app.ui.adaptive.WindowWidthClass
import com.zynergylabs.forager.app.ui.adaptive.currentWindowWidthClass
import android.content.res.Configuration
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.union
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.ui.platform.LocalConfiguration
import com.zynergylabs.forager.app.ui.log.ScreenEdge
import com.zynergylabs.forager.app.ui.adaptive.isShortWindow
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.adaptive.currentWindowPortEdge
import com.zynergylabs.forager.app.ui.adaptive.punchHoleEdgeFor
import com.zynergylabs.forager.app.ui.log.currentDisplayRotation
import com.zynergylabs.forager.app.ui.crash.CrashLogPanel
import com.zynergylabs.forager.app.ui.crash.CrashLogsEntryRow
import com.zynergylabs.forager.app.ui.diagnostics.DiagnosticsEntryRow
import com.zynergylabs.forager.app.ui.diagnostics.DiagnosticsPanel
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.CameraXInAppCamera
import com.zynergylabs.forager.app.ui.log.InAppCameraHost
import com.zynergylabs.forager.app.ui.log.InAppCameraSlot
import com.zynergylabs.forager.app.ui.log.InAppCameraTarget
import com.zynergylabs.forager.app.ui.log.CartographyEntryMode
import com.zynergylabs.forager.app.ui.log.FindOverView
import com.zynergylabs.forager.app.ui.log.JournalEntryMode
import com.zynergylabs.forager.app.ui.log.JournalTab
import com.zynergylabs.forager.app.ui.log.leaveKeepsDraft
import com.zynergylabs.forager.app.ui.log.PendingDeleteNotice
import com.zynergylabs.forager.app.ui.log.PendingDeleteSnackbarEffects
import com.zynergylabs.forager.app.ui.log.JournalDetailPane
import com.zynergylabs.forager.app.ui.log.JournalDetailSlot
import com.zynergylabs.forager.app.ui.log.rememberJournalScreenState
import com.zynergylabs.forager.app.ui.log.LogPanel
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.ui.map.MapRecordSources
import com.zynergylabs.forager.app.ui.map.OPEN_IN_JOURNAL_LABEL
import com.zynergylabs.forager.app.ui.map.Basemap
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker
import com.zynergylabs.forager.app.ui.map.CentrePinLocationPickerOverlay
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_CORNER_RADIUS
import com.zynergylabs.forager.app.ui.map.MAP_ICON_BAR_EDGE_INSET
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_DARK
import com.zynergylabs.forager.app.ui.map.MAP_ICON_STACK_BORDER_COLOR_LIGHT
import com.zynergylabs.forager.app.ui.map.MIN_TOUCH_TARGET
import com.zynergylabs.forager.app.ui.map.MapBarIconButton
import com.zynergylabs.forager.app.ui.map.MapFloatingIconButton
import com.zynergylabs.forager.app.ui.map.MapIconBar
import com.zynergylabs.forager.app.ui.map.MapIconBarMinimizeHandle
import com.zynergylabs.forager.app.ui.map.MapIconBarRestoreHandle
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorDark
import com.zynergylabs.forager.app.ui.map.mapIconStackBorderColor
import com.zynergylabs.forager.app.ui.map.mapChromeContainerColor
import com.zynergylabs.forager.app.ui.map.mapChromeContentColor
import com.zynergylabs.forager.app.ui.map.mapChromeFill
import com.zynergylabs.forager.app.ui.map.mapIconClusterContainerColor
import com.zynergylabs.forager.app.ui.map.mapIconClusterChildColor
import com.zynergylabs.forager.app.ui.map.MapIconStackButtonColorLight
import com.zynergylabs.forager.app.ui.map.MapMode
import com.zynergylabs.forager.app.ui.map.layers.ColourFieldMove
import com.zynergylabs.forager.app.ui.map.layers.COLOUR_FIELDS
import com.zynergylabs.forager.app.ui.map.layers.ForecastCellsShown
import com.zynergylabs.forager.app.ui.map.layers.MAP_LAYER_REGISTRY
import com.zynergylabs.forager.app.ui.map.layers.withUnavailableColourFieldsHidden
import com.zynergylabs.forager.app.ui.map.MapForecastFeed
import com.zynergylabs.forager.app.ui.map.MapLayersControls
import com.zynergylabs.forager.app.domain.AbsentForecastCellStore
import com.zynergylabs.forager.app.domain.ForecastCellStore
import com.zynergylabs.forager.app.ui.map.MapModePicker
import com.zynergylabs.forager.app.ui.map.MapOverlayContent
import com.zynergylabs.forager.app.ui.map.MapSlot
import com.zynergylabs.forager.app.ui.map.TrueHeadingReading
import com.zynergylabs.forager.app.ui.map.rememberTrueHeading
import com.zynergylabs.forager.app.ui.map.SightingsMapSlot
import com.zynergylabs.forager.app.ui.map.mapIconBarRecordAccent
import com.zynergylabs.forager.app.ui.map.mapIconBarRowAnchorOffset
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import com.zynergylabs.forager.app.ui.map.MapCameraMemory
import com.zynergylabs.forager.app.ui.map.MapRenderMode
import com.zynergylabs.forager.app.ui.theme.Bark
import com.zynergylabs.forager.app.ui.theme.Cream
import com.zynergylabs.forager.app.ui.theme.LocalForagerDarkTheme
import com.zynergylabs.forager.app.ui.theme.Spacing
import com.zynergylabs.forager.app.ui.track.RecordingNotice
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

internal enum class ResultsTab(val label: String) {
    LIST("List"),
    MAP("Maps"),
    SEASONAL("Seasonal"),
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
 * (chosen from [ThreeWayActionDialog], then placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]);
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
    // Debug builds only: the entry row that reaches it composes nothing in release, so this
    // value is unreachable there — see ui/diagnostics/DiagnosticsPanel.kt (both source sets).
    Diagnostics,
    Log,
    // The standalone Photo Gallery panel that stood here (Workstream G2) was removed in J6a (owner,
    // 2026-09-28, ruling 2: "the old Photo Gallery panel is removed. Only the album remains, as on the
    // phone"); its photos, and now their long-press delete, are the Journal's album.
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
    /** Called when a date and name are confirmed for a trip pin placed via [com.zynergylabs.forager.app.ui.map.CentrePinLocationPicker]. */
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
    /** Settings' "Automatically Save Location to Photos" checkbox — see [AvailabilityUiState.autoSaveLocationToPhotos]. Defaulted, like [onDistanceUnitSelected], so a screen test that does not exercise this setting needs no argument for it. */
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit = {},
    /** Settings' "Lock camera to portrait" checkbox — see [AvailabilityUiState.lockCameraToPortrait]. Defaulted like the one above. */
    onLockCameraToPortraitChanged: (Boolean) -> Unit = {},
    /**
     * Settings' Backup section (journal backup and restore, dispatch 2026-09-28-127): its state and callbacks.
     * Defaulted, so a caller with no backup still composes the section, inert.
     */
    backup: BackupControls = BackupControls(),
    /** Counts up when the person taps Done on the restore page: go to the Maps tab and close the drawer. */
    returnToMapRequest: Int = 0,
    /** Counts up when a backup notification is tapped: open the Backup section in Tools, then Settings. */
    openBackupRequest: Int = 0,
    /** "Download again" on a restored offline region. */
    onDownloadAgain: (Long) -> Unit = {},
    /** Settings' Light/Dark/System Default theme choice — see [AvailabilityUiState.themeMode]'s own doc comment. */
    onThemeModeChanged: (AppThemeMode) -> Unit,
    /**
     * The Maps tab entered or left fullscreen by a user action — persisted across restarts, see
     * [AvailabilityUiState.persistedMapFullscreen]. Called only from the three user-driven
     * mutation sites (the icon bar's toggle, system back, leaving the Maps tab), never from an
     * effect on the flag — see `isMapFullscreen`'s own doc comment for why. Defaulted so callers
     * that don't persist (tests) need not care.
     */
    onMapFullscreenChanged: (Boolean) -> Unit = {},
    /**
     * The mushroom log drawer destination's own state — see [com.zynergylabs.forager.app.ui.log.LogPanel].
     * Defaulted, like [mapSlot] below, so the many existing tests of this screen that have nothing
     * to do with the log don't need to pass log-specific state and callbacks just to compile.
     */
    logUiState: MushroomLogUiState = MushroomLogUiState(),
    cameraCaptureFiles: CameraCaptureFiles = CameraCaptureFiles(LocalContext.current),
    /**
     * Which surface the in-app camera is open for, or null when closed. Retained by
     * `InAppCameraViewModel` across Activity recreation, and composed by this screen **above the
     * window-width branch** so a rotation's width-class flip does not dispose it — see
     * [InAppCameraHost] for the bug and the decision.
     */
    inAppCameraTarget: InAppCameraTarget? = null,
    onOpenCamera: (InAppCameraTarget) -> Unit = {},
    onCloseCamera: () -> Unit = {},
    /** The camera dialog itself; defaults to CameraX. A slot for the same reason [mapSlot] is one: CameraX cannot run under Robolectric. */
    inAppCamera: InAppCameraSlot = CameraXInAppCamera,
    /** The camera's persisted grid mode and the way to change it, from `CameraGridModeViewModel`. Defaulted like the camera's other inputs. */
    cameraGridMode: GridMode = GridMode.Off,
    onCameraGridModeChanged: (GridMode) -> Unit = {},
    /** Starts and immediately opens a new log entry — the map's "Log a find" option is the only production caller; entries have no other creation path (see `docs/plans/mushroom-log.md`'s Navigation section). */
    onStartLogEntry: (LatLng?, LocalDate) -> Unit = { _, _ -> },
    onOpenLogEntry: (String) -> Unit = {},
    onCloseLogEntry: () -> Unit = {},
    onLogEntryChanged: (MushroomLogEntry) -> Unit = {},
    /** Begins editing the currently-open (committed) entry — see [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onStartEditingEntry]'s own doc comment. A no-op if it's already a draft. */
    onStartEditingLogEntry: () -> Unit = {},
    /**
     * Opens a row and, if it's a committed entry, immediately begins editing it — one atomic
     * ViewModel operation ([com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onOpenEntryForEditing]),
     * not [onOpenLogEntry] and [onStartEditingLogEntry] chained here. [LogPanel]'s own entry list
     * is the one caller (see that composable's own doc comment on why its "no report step" shape
     * needs this instead of the two-callback chain [JournalTab] still uses).
     */
    onOpenLogEntryForEditing: (String) -> Unit = {},
    /** Save — commits the currently-open entry. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onSaveEntry]'s own doc comment. */
    onSaveLogEntry: () -> Unit = {},
    /** Cancel — the only exit that discards anything. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onCancelEditing]'s own doc comment. */
    onCancelLogEntryEditing: () -> Unit = {},
    /** Leaving without answering — tab switch, backgrounding, back — persists the draft without committing. See [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel.onLeaveEditingIncidentally]'s own doc comment. */
    onLeaveLogEntryEditingIncidentally: () -> Unit = {},
    /** Discards a draft by id outright — the compact scaffold's "Discard" Snackbar action target, since by the time it's tapped [logUiState].editingEntry is already null. Same operation as [onDeleteLogEntry]. */
    onDiscardLogDraft: (String) -> Unit = {},
    onAddLogPhoto: (PhotoSource) -> Unit = {},
    onRemoveLogPhoto: (LogPhoto) -> Unit = {},
    onPullLogPhoto: (LogPhoto) -> Unit = {},
    onDeleteLogEntry: (String) -> Unit = {},
    onDeleteGalleryPhoto: (GalleryPhoto) -> Unit = {},
    /** Standalone-photos dispatch: Camera/Gallery acquisition, no owning find — the album's own Camera/Import buttons (the Journal's Entries album, on both trees). */
    onAddGalleryPhoto: (PhotoSource) -> Unit = {},
    /** Clears [logUiState]'s `saveErrorMessage` once its Toast has shown — see [LogPanel]/[JournalTab]'s identical parameter. */
    onSaveLogErrorDismissed: () -> Unit = {},
    /** Journal Stage 2b's new authored entity — see [com.zynergylabs.forager.app.ui.log.CartographyScreen]'s own doc comment for all of the following. Defaulted, same reasoning as [logUiState]. */
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
    /** Entry-photo-acquisition dispatch, Item 2. See [CartographyScreen]'s own doc comment on this same parameter. */
    onAcquirePhotoForCartographyEntry: (PhotoSource) -> Unit = {},
    onFinishCartographyEntry: () -> Unit = {},
    /** Explicit Save for a committed Cartography entry — device-check patch, Item 1. Threaded straight through to CartographyScreen, whose own lifecycle observer also uses it for the backgrounding-return prompt's Commit option (pending-edit-and-fixes dispatch, Item 1). */
    onSaveCartographyEntry: () -> Unit = {},
    /** The leave-prompt's Discard option — device-check patch, Item 1. */
    onDiscardCartographyEntryChanges: () -> Unit = {},
    /** The backgrounding-return prompt's "Save as draft" option — pending-edit-and-fixes dispatch, Item 1. See CartographyScreen's own lifecycle-observer doc comment. */
    onSaveCartographyEntryAsDraft: () -> Unit = {},
    onDeleteCartographyEntry: (String) -> Unit = {},
    /**
     * An Entries card's swipe Delete (journal redesign J4b L2): a *pending* delete with Undo
     * (`CartographyViewModel.requestDeleteEntry`), passed to the compact tree's `JournalTab` only.
     * `null` (the default) leaves the cards without the swipe; the wide tree's `LogPanel` is not
     * given it (J6 owns the wide tree).
     */
    onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /**
     * J8-3: shows or hides a saved entry on the Maps tab (`CartographyViewModel.onSetShownOnMap`): the
     * entry report's "Show on map" and "Hide from map", on both trees, and the Maps-tab chip's "Hide"
     * and "Hide all". Defaulted, so the many tests of this screen that never show an entry are unchanged.
     */
    onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = { _, _ -> },
    /** Clears [cartographyUiState]'s `shownOnMapErrorMessage` once its Toast has shown (J8, continuation `2026-09-28-65`). */
    onCartographyShownOnMapErrorDismissed: () -> Unit = {},
    /**
     * Clears [cartographyUiState]'s `saveErrorMessage` once its Toast has shown. That Toast is hosted
     * by the Journal itself, [JournalTab] (compact) and [LogPanel] (wide), not here, so this is
     * threaded to them (intent `2026-09-28-68`, continuation `2026-09-28-76`).
     */
    onCartographySaveErrorDismissed: () -> Unit = {},
    /**
     * An album photo's long-press Delete (J4b L3): a *pending* delete with Undo
     * (`MushroomLogViewModel.requestDeleteGalleryPhoto`), for the compact tree's album only. `null`
     * (the default) leaves the photos without the menu.
     */
    onRequestDeleteGalleryPhoto: ((String) -> Unit)? = null,
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own map, Stage 2d — see that
     * composable's doc comment. Defaulted to always report nothing resolved, same reasoning as
     * [logUiState]: the many existing tests of this screen that never open a Cartography entry
     * don't need to pass a real resolver just to compile.
     */
    getCartographyEntryMapData: suspend (CartographyEntry, List<GalleryPhoto>) -> CartographyEntryMapData = { _, _ ->
        CartographyEntryMapData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    },
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own offline-map toggle, Stage 2e-i —
     * see that composable's doc comment. Defaulted for the same reason [getCartographyEntryMapData]
     * is: the many existing tests of this screen that never open a Cartography entry don't need a
     * real resolver just to compile.
     */
    getCartographyEntryOfflineRegion: suspend (CartographyEntry, List<LatLng>) -> OfflineRegionSummary? = { _, _ -> null },
    /** F3 (owner, "C: list screen loads lazily"): one entry's saved track paths, by track id, for the Journal cards' thumbnails. Defaulted like [getCartographyEntryMapData]. */
    getSavedTrackPaths: suspend (String) -> Map<String, List<LatLng>> = { emptyMap() },
    /**
     * [com.zynergylabs.forager.app.ui.log.CartographyEntryReportScreen]'s own fullscreen recenter button —
     * fullscreen-maps dispatch, see that composable's own doc comment, "Fullscreen." Defaulted for
     * the same reason [getCartographyEntryMapData] is.
     */
    getCartographyEntryCurrentLocation: suspend () -> LocationResult = { LocationResult.LocationUnavailable },
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
     * [isRecording] for what actually starts/stops [com.zynergylabs.forager.app.service.TrackRecordingService].
     */
    isRecording: Boolean = false,
    onToggleRecording: () -> Unit = {},
    /** Set when the most recent [onToggleRecording]-triggered start failed or was refused — shown as a one-shot Toast in [CompactMapTab], the same [LaunchedEffect]-on-a-status-field shape as its existing `locateMeStatus` Toast. */
    startRecordingErrorMessage: String? = null,
    /**
     * Alert-delivery dispatch, Item 3: the one-time silenced-phone warning for the recording that
     * just started — shown once as a `SnackbarDuration.Long` Snackbar in the map Scaffold's existing
     * host, no action button (the copy never tells the user to change a setting). Keyed on its id so
     * the same text re-shows on a later trip. See [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.tripStartWarning].
     */
    tripStartWarning: RecordingNotice? = null,
    /**
     * Timestamp-filter dispatch, Item 3: the once-per-recording notice that most of the active
     * track is being excluded as network-provider fixes — same host, same shape as
     * [tripStartWarning]. See [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.networkFixesNotice].
     */
    networkFixesNotice: RecordingNotice? = null,
    /**
     * The active track's recorded points, oldest first — see [com.zynergylabs.forager.app.ui.map.MapSlot]'s own
     * doc comment on this same parameter for how it's drawn. Empty whenever [isRecording] is false.
     */
    breadcrumbPoints: List<LatLng> = emptyList(),
    /**
     * Saved waypoints — independent of any track recording, per [Waypoint]'s own doc comment.
     * Rendered as pins on the map ([com.zynergylabs.forager.app.ui.map.MapSlot]) and listed, with delete, in
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
    /** Part 2 follow-ups F1 item 5 (owner "Option A"): a finished track's swipe or details Delete asks for a pending delete with Undo; `null` (the default) leaves tracks without a delete. */
    onDeleteTrack: ((String) -> Unit)? = null,
    /** Set when a committed track delete failed and the track is back; shown above the Tracks list. */
    tracksErrorMessage: String? = null,
    /**
     * Bearing/distance/elevation difference back to the active track's start point, from the
     * device's current position — `null` whenever nothing is being recorded, no fix has come in
     * yet, or no breadcrumb has landed to compute a start from. See [ReturnToStartInfo]'s own doc
     * comment for why there's no ETA, and [CompassElevationStripContent] for where this renders.
     */
    returnToStart: ReturnToStartInfo? = null,
    /** Whether the walker has said they're heading back — see [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.startReturn]'s own doc comment. */
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
     * Navigation HUD stage one: what turns [compassProvider]'s magnetic heading into a true one
     * (see [rememberTrueHeading]). Defaults to the real declination model so no production caller
     * *has* to pass it, same pattern as [compassProvider]; `MainActivity` passes the container's
     * own instance, and a test passes one over a fake [com.zynergylabs.forager.app.domain.DeclinationProvider]
     * to pin the sign.
     */
    computeTrueHeading: ComputeTrueHeadingUseCase = remember { ComputeTrueHeadingUseCase(AndroidDeclinationProvider()) },
    /**
     * Navigation HUD stage one's target — the active track's origin waypoint
     * ([com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.originWaypoint]), `null` when there is
     * none. Threaded separately from [waypoints] because it decides which of them the *map* draws
     * ([mapVisibleWaypoints]) as well as what the HUD points at.
     */
    navigationTarget: Waypoint? = null,
    /**
     * Path-home join dispatch: the walk back to [navigationTarget] along the recorded track joined
     * to itself, in metres ([com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.pathHome]'s
     * `totalMeters`), or `null` when there is none — not returning, no gated fix yet, no usable
     * points. The HUD's status line carries it as one number; see [navigationReadout].
     */
    pathHomeMeters: Double? = null,
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
     * [com.zynergylabs.forager.app.ui.track.TrackExportPanel]'s own doc comment. Empty by default, same
     * [breadcrumbPoints]/[waypoints] shape above: the real list is [trackUiState.tracks][com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.tracks],
     * threaded in by `MainActivity`.
     */
    tracks: List<Track> = emptyList(),
    /** Refreshes [tracks] — called whenever the export panel opens, mirroring [onOfflineMapsOpened]'s own "reload on open" shape. */
    onTracksOpened: () -> Unit = {},
    /**
     * GPX full-record export dispatch: the unfiltered read for a shared track's `<extensions>`
     * block — see [com.zynergylabs.forager.app.ui.track.TrackExportList]'s own doc comment. Empty by default,
     * same [tracks]/[waypoints] shape above; the real function is
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.getFullRecord], threaded in by
     * `MainActivity`.
     */
    getFullRecord: suspend (String) -> Result<List<TrackPointRecord>> = { Result.success(emptyList()) },
    /**
     * The Journal's pending deletes, one per record type at most (journal redesign J4): each is shown
     * as an Undo snackbar in this screen's one snackbar host, the same slot "Saved to Drafts" and
     * the trip-start warning use, so it docks where those do in both window classes and outlives a
     * Journal tab change. `MainActivity` builds them from the owning ViewModels' pending state; see
     * [PendingDeleteSnackbarEffects]. Empty by default, so no other caller changes.
     */
    pendingDeleteNotices: List<PendingDeleteNotice> = emptyList(),
    /**
     * Map layers L0b. [onMapShown] runs every time the Maps tab comes into view (compact: the Maps
     * bottom-nav tab; medium and expanded: the List and Map pane, which always shows the map), which
     * is how the saved records and the forecast availability stay fresh (the dispatch, B2:
     * `AvailabilityViewModel.onMapShown`). The next three are the Layers sheet's changes, and
     * [forecastCellStore] is where the Maps tab's colour fields read their cells (the app's one store,
     * `AppContainer.forecastCellStore`). All defaulted, so no other caller changes.
     */
    onMapShown: () -> Unit = {},
    onMapLayerVisibilityChanged: (String, Boolean) -> Unit = { _, _ -> },
    onMapLayerOpacityChanged: (String, Float) -> Unit = { _, _ -> },
    onColourFieldMoved: (String, ColourFieldMove) -> Unit = { _, _ -> },
    forecastCellStore: ForecastCellStore = AbsentForecastCellStore,
) {
    // Map up front. The list is one tap away; the map is the thing this screen is arranged around.
    //
    // Saveable, like compactTab below (journal redesign J2, second coder; planner's call in
    // prompts/preserved/2026-09-27-19.md): compactTab restores after an Activity recreation, and a
    // plain-remember selectedTab reset to MAP beside it, so after a recreation on Seasonal the bottom
    // nav showed Seasonal while the LaunchedEffect below asked for the map's sightings, and a new
    // search did not reload Seasonal until a tab tap re-synced the two. Saving both keeps them equal
    // across a restore; nothing else about tab selection changed.
    var selectedTab by rememberSaveable { mutableStateOf(ResultsTab.MAP) }

    // The compact bottom nav's own 5-way selection — see [CompactTab]'s doc comment for why this
    // is separate from selectedTab rather than extending ResultsTab itself (which the medium/
    // expanded window's tab row also reads and must stay 3-way). Kept in sync with selectedTab
    // below whenever the tapped destination is one of the three they share, so onMapTabSelected/
    // onSeasonalTabSelected's LaunchedEffect keeps firing correctly and a later resize to a wider
    // window lands on the same List/Maps/Seasonal tab compact was just showing.
    //
    // Journal redesign J2, T5 (owner ruling "Yes, fold into J2 (Recommended)"): saveable, so an
    // Activity recreation (a night-mode toggle, a fold, process death; a plain rotation does not
    // recreate, see AndroidManifest.xml's configChanges) no longer drops the user back on Maps. An
    // enum saves as-is through the default saver. selectedTab above was left plain remember by T5,
    // which let the two disagree after a recreation; it is saveable too since the second J2 coder
    // (see its own comment).
    var compactTab by rememberSaveable { mutableStateOf(CompactTab.MAP) }

    // Journal redesign J1, S1: the Journal's user-set UI state (top tab, Records selection), held
    // here beside compactTab rather than inside the Journal branch of `when (compactTab)`, so leaving
    // the Journal tab no longer disposes it, and saveable so an Activity recreation does not either.
    // See JournalScreenState's own doc comment.
    val journalScreenState = rememberJournalScreenState()
    // Intent 2026-09-28-44, F2 (the owner: "Return to the editor (Recommended)"): whether an open day
    // entry shows its report or its editor. Held here beside journalScreenState for the same reason,
    // not in it: JournalScreenState holds the places a user chose to be, and this is the state of an
    // open entry, which the ViewModel also holds. Saveable, so a recreation (a night-mode toggle, a
    // fold) does not bring an unsaved edit back in its report view either; the enum saves as-is.
    val cartographyEntryModeState = rememberSaveable { mutableStateOf(CartographyEntryMode.VIEW) }
    // Intent 2026-09-28-44, F3 ("Keep finds open too (Recommended)"): an open find's mode, and M1's
    // find over the view, held here for the same reason. The mode saves as its enum. FindOverView is
    // plain remember: it survives the tab change the ruling is about, not a recreation.
    val findEntryModeState = rememberSaveable { mutableStateOf(JournalEntryMode.REPORT) }
    val findOverViewState = remember { mutableStateOf<FindOverView?>(null) }
    // J6a (ruling 1, list-detail): the wide tree's right side. The Journal's open entry, find, record
    // details and pickers register here (JournalDetailSlot); the wide branch below draws the top one over
    // the results pane. The compact tree never reads it.
    val journalDetailSlot = remember { JournalDetailSlot() }

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

    // THE navigation predicate — defined once, here, and nowhere else (navigation-chrome dispatch,
    // item 1). Everything that means "a navigation mode is active" reads this: the HUD's presence,
    // the compass strip's absence, and which waypoints the map shows. Stage one has exactly one
    // mode, the return leg, so this is isReturning; stage two's target picker ORs its own state
    // into this one line, and the strip, the HUD and the map cannot drift apart because none of
    // them was ever written against isReturning directly.
    val isNavigating = isReturning
    // Navigation HUD stage one's display rules, applied once here so the compact map and the
    // wide-layout MapTab agree — see mapVisibleWaypoints. Records keeps the full list.
    val mapWaypoints = remember(waypoints, isNavigating, navigationTarget) {
        mapVisibleWaypoints(waypoints, isNavigating = isNavigating, target = navigationTarget)
    }

    // Local remembered state, same reasoning as selectedTab/mapMode below: purely a display
    // decision the ViewModel has no part in. The compact map icon stack's fullscreen toggle sets
    // this — see CompactMapTab's call site. Compact-only: WindowWidthClass.MEDIUM/EXPANDED never
    // render the icon stack that can set it, so it stays false there. Only reachable while on the
    // Maps tab (the toggle icon lives in that tab's own icon stack), so this being true while
    // selectedTab != MAP cannot happen in practice.
    var isMapFullscreen by remember { mutableStateOf(false) }
    // Persisted across restarts (persist-fullscreen dispatch — see
    // MapPreferencesRepository.getMapFullscreen for the precedent and its reasoning). The loaded
    // value is applied exactly once, when it first arrives, and only if it arrived at all (a
    // failed read leaves it null and the screen starts as it always did). Two load-bearing
    // choices here:
    //
    // 1. Writes happen at the three user-driven mutation sites (the toggle below, the fullscreen
    //    BackHandler, the tab handler's exit) — never from a LaunchedEffect on isMapFullscreen.
    //    Such an effect would fire on first composition with the default `false` and write it
    //    before the DataStore read lands, clobbering a persisted `true`: a data-loss bug that
    //    would present as "the feature doesn't work" and be hard to trace.
    // 2. The read is asynchronous, so the first frame composes with `false`; when a persisted
    //    `true` lands the search bar, nav and cluster slide away rather than starting hidden.
    //    Chosen, not overlooked (owner's call): the alternatives were gating the map's first
    //    composition on a disk read, or recreating the map once loaded, which would re-fit it —
    //    the very thing the "nothing may re-fit the map" rule exists to protect. A DataStore
    //    first read is a few milliseconds, typically before any tile has appeared.
    var hasAppliedPersistedMapFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.persistedMapFullscreen) {
        val persisted = uiState.persistedMapFullscreen
        if (persisted != null && !hasAppliedPersistedMapFullscreen) {
            hasAppliedPersistedMapFullscreen = true
            isMapFullscreen = persisted
        }
    }
    // The icon cluster's position, held here rather than in CompactMapTab so it survives leaving
    // and returning to the Map tab — see MapIconClusterPositionState's own doc comment.
    val mapIconClusterPosition = rememberMapIconClusterPositionState()
    // Part 1 layout fixes, item 4 (planner message 2026-09-28-98, under CLAUDE.md's UX defaults): the
    // camera the user left on the Maps tab, held here for the same reason, since the map and its camera
    // leave composition with the tab. See MapCameraMemory. Session only.
    val mapCameraMemory = remember { MapCameraMemory() }

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
    // nightModeLoaded: the cold-launch gate (colour build C1) — the map loads no style until the
    // preference read above has landed, so a night user's first style is the night one. Only this
    // map gets it; see MapRenderMode.nightModeLoaded for why the others keep the default.
    // Map layers L0b (B1 to B5). The colour fields with data this week (the store's answer, from
    // onMapShown), the state the map draws with (the user's choices with every colour field that has
    // no data hidden, so a release build never draws, lists, credits or legends one: planner's ruling
    // on F2), the forecast feed the map reads its cells through, and every saved record with the
    // pending deletes left out (planner's ruling on Q7). The legend's expanded flag is held here, above
    // the tab, so it survives leaving the Maps tab and coming back (CLAUDE.md, UX defaults).
    var mapLegendExpanded by rememberSaveable { mutableStateOf(false) }
    var forecastCellsShown by remember { mutableStateOf<Map<String, ForecastCellsShown>>(emptyMap()) }
    val availableColourFieldGroups = COLOUR_FIELDS.filter { it.group in uiState.forecastGroups }.associate { it.layerId to it.group }
    val drawnMapLayers = withUnavailableColourFieldsHidden(uiState.mapLayers, MAP_LAYER_REGISTRY, availableColourFieldGroups.keys)
    val forecastWeek = uiState.forecastWeek
    val forecastFeed = remember(forecastCellStore, forecastWeek, availableColourFieldGroups) {
        if (forecastWeek == null || availableColourFieldGroups.isEmpty()) {
            null
        } else {
            MapForecastFeed(
                store = forecastCellStore,
                week = forecastWeek,
                groupsByLayer = availableColourFieldGroups,
                onCellsShown = { forecastCellsShown = it },
            )
        }
    }
    val mapRecordsDrawn = uiState.mapRecords.withoutPending(
        findId = logUiState.pendingDelete?.item?.id,
        photoId = logUiState.pendingPhotoDelete?.item?.photo?.id,
        offlineRegionId = uiState.pendingOfflineRegionDelete?.item?.id?.toString(),
    )
    // J8-2 (owner: "Highlight in place", "Live records", "Saved entries only"): the saved entries shown
    // on the map and the records they keep, among the records the Maps tab draws. cartographyUiState is
    // what MainActivity passes, with a pending entry delete already left out (hidingPendingDelete), and
    // its entries are the saved ones; the use case holds the draft rule itself as well. Only the Maps
    // tab reads this: every other map draws no highlight. The waypoints are the ones this map draws,
    // mapWaypoints, not every waypoint (J8 follow-ups, continuation 2026-09-28-87, item 2): an ORIGIN
    // waypoint the map is not navigating to, and any END waypoint, is not drawn, so it gets no ring,
    // by J8's rule that a record not drawn is not highlighted.
    val journalHighlights = remember(cartographyUiState.entries, mapRecordsDrawn, mapWaypoints) {
        GetJournalEntryHighlightsUseCase()(cartographyUiState.entries, mapRecordsDrawn, mapWaypoints)
    }
    val mapLayersControls = MapLayersControls(
        stored = uiState.mapLayers,
        availableColourFields = availableColourFieldGroups.keys,
        cellsShown = forecastCellsShown.filterKeys { it in availableColourFieldGroups },
        records = mapRecordsDrawn,
        journalHighlights = journalHighlights,
        // J8-3: the chip's list. Hiding writes shownOnMap for each entry, one write each; the Layers
        // sheet's "Journal entries" switch is a separate, display-only choice and is never touched here.
        onHideJournalEntry = { id -> onSetCartographyEntryShownOnMap(id, false) },
        onHideAllJournalEntries = { journalHighlights.shownEntries.forEach { onSetCartographyEntryShownOnMap(it.entryId, false) } },
        legendExpanded = mapLegendExpanded,
        onLegendExpandedChange = { mapLegendExpanded = it },
        onVisibilityChanged = onMapLayerVisibilityChanged,
        onOpacityChanged = onMapLayerOpacityChanged,
        onColourFieldMoved = onColourFieldMoved,
    )
    val mapRenderMode = MapRenderMode(
        basemap = basemap,
        night = isNightMode,
        nightModeLoaded = uiState.nightModeMapsLoaded,
        layers = drawnMapLayers,
        forecast = forecastFeed,
    )
    // Persisted via the ViewModel/DataStore — see AvailabilityUiState.distanceUnit's own doc
    // comment. mapMode above is still session-local; see the observation in that same doc comment.
    val distanceUnit = uiState.distanceUnit
    var drawerPanel by remember { mutableStateOf(DrawerPanel.Search) }
    // The medium/expanded search summary's one-shot "open Advanced search" (continuation 2026-09-28-38);
    // see SearchControls' expandAdvancedSearchRequested.
    var expandAdvancedSearchRequested by remember { mutableStateOf(false) }
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
    // M1: the find a PendingJournalDestination.VIEW_FIND request opens, cleared with the request.
    var pendingJournalFindId by remember { mutableStateOf<String?>(null) }
    // J8-4: the day entry a PendingJournalDestination.VIEW_ENTRY request opens, cleared with the request.
    var pendingJournalEntryId by remember { mutableStateOf<String?>(null) }
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

    // Tapping Done on the restore's loading page goes home (dispatch 2026-09-28-137, item 6): the Maps tab, the drawer
    // closed. The same two writes as "View on Map" above (onViewSpeciesOnMap): both tab states are set unconditionally,
    // since only the one the active layout reads has any effect. Keyed on the request's count, so a tab the person is
    // already on is set again harmlessly and 0, the default, does nothing.
    // A backup notification's tap (dispatch 2026-09-28-153): open Tools, then Settings, at the Backup section. The compact
    // drawer opens over whatever tab is showing; the wide layout's permanent drawer is switched to its Settings panel.
    LaunchedEffect(openBackupRequest) {
        if (openBackupRequest > 0) {
            isDrawerOpen = true
            drawerPanel = DrawerPanel.Settings
        }
    }

    LaunchedEffect(returnToMapRequest) {
        if (returnToMapRequest > 0) {
            isDrawerOpen = false
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
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
    // before that composable was renamed. Navigation-chrome amendment: a fifth step, navigation,
    // sits between "return to the Maps tab" and the exit handler — see its own comment below.
    //
    // Only isDrawerOpen/isMapFullscreen/compactTab drive this — all three are compact-only state
    // that a medium/expanded window never changes away from its own defaults (isDrawerOpen stays
    // false there; a PermanentNavigationDrawer is never "closed"), so none of this fires on that
    // width class.
    //
    // The drawer opens over whatever tab is showing, and that tab's nested handlers are registered
    // after this one, so they would win while the drawer is open. The Journal's are turned off while
    // it is (JournalTab's backEnabled, intent 2026-09-28-28), so Back closes the drawer there.
    BackHandler(enabled = isDrawerOpen) {
        isDrawerOpen = false
    }
    BackHandler(enabled = !isDrawerOpen && isMapFullscreen) {
        isMapFullscreen = false
        onMapFullscreenChanged(false)
    }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab != CompactTab.MAP) {
        compactTab = CompactTab.MAP
        // Keeps the shared ResultsTab-driven state in sync, same as ForagerBottomNav's own tap
        // handler does — see compactTab's own doc comment for why the two exist side by side.
        selectedTab = ResultsTab.MAP
    }

    // Navigation is the last thing backed out of before the app closes, and backing out of it
    // asks first (navigation-chrome amendment, owner-directed, found on device). This handler is
    // enabled only once everything nested is unwound — dropdown, drawer, fullscreen, other tab —
    // so a back press while navigating in fullscreen exits fullscreen and does not touch the
    // navigation; the next press asks. Stage one had this in CompactMapTab's own handler with no
    // exclusions, which — being the deepest registered — exited navigation BEFORE fullscreen, the
    // drawer or the dropdown unwound: a user in fullscreen lost the return leg to a press they
    // expected to exit fullscreen. That inversion was the bug this replaces.
    //
    // THIS HANDLER PRODUCES A LOOP ON PURPOSE. Back raises the prompt; back dismisses it; back
    // raises it again. Back can never exit navigation, and — because this handler is enabled
    // whenever the exit handler below would otherwise be — back can no longer close the app from
    // this screen while navigating. Home and the app switcher still work, so nobody is trapped.
    // The reason, in the owner's words: backing out can kill a process a person is relying on.
    // Someone navigating out of the woods must not lose it to a stray press. The user decides when
    // to exit — the HUD's ✕ and the control pill's toggle are the only exits, both deliberate
    // presses, and neither asks. Do not "fix" the loop; do not let a second press confirm (that
    // would be the accidental exit with one extra step: the second press cancels); do not remove
    // this handler without another that consumes back here, or a stray press falls through to
    // the exit handler and drops the user out of the app while a track is recording.
    //
    // Toggle, not raise-only: on a device the open AlertDialog is its own window and consumes
    // back itself (onDismissRequest → keep navigating), so this handler only ever sees the
    // raising press there. Under Robolectric, onBackPressedDispatcher.onBackPressed() reaches the
    // Activity, not the dialog window, so the tests' second press lands here — the toggle makes
    // that path end in the same place. The tests therefore exercise THIS toggle path, not the
    // dialog window's own dismiss; the guarantee (back never exits) holds by either route, but
    // the dialog's own back handling is device-only coverage.
    var showExitNavigationPrompt by remember { mutableStateOf(false) }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == CompactTab.MAP && isNavigating) {
        showExitNavigationPrompt = !showExitNavigationPrompt
    }
    // If navigation ends by any other route while the prompt is up (the recording stopping under
    // it, say), the prompt has nothing left to ask about.
    LaunchedEffect(isNavigating) {
        if (!isNavigating) showExitNavigationPrompt = false
    }
    // The prompt itself is composed below, once the window's tree is known (map chrome at 80%).

    // "Home": drawer closed, chrome visible, Maps tab selected, not navigating. A second back
    // press within the window actually exits; a lone press just warns. This is a single-Activity
    // app with nothing else back could sensibly navigate to once nothing is nested, so an
    // un-warned single back press (which a pan gesture can graze) would otherwise dump the user
    // straight out. Never reached while navigating — the handler above holds that state, by
    // design (see its comment); this exclusion is what makes the two mutually exclusive, the same
    // way the tab and fullscreen handlers exclude the states nested inside them.
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == CompactTab.MAP && !isNavigating) {
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
    // Landscape B1 (docs/plans/landscape-phone-design.md, P1-P3, Resolutions R1 and R12-R18). A
    // short window (under 480dp tall — a phone held sideways) takes the compact tree whatever its
    // width; see the branch at the bottom of this function. In a short *landscape* window the
    // compact tree swaps its bottom bar for a navigation rail on the charger-port edge — see
    // compactMainScaffold's own showRail. portEdge is only read while showRail is true.
    val isShortWindow = isShortWindow()
    // Map layers L0b, B2: the Maps tab's freshness mechanism. Whenever the Maps tab comes into view
    // (compact, the Maps bottom-nav tab, the same layout test as below; medium and expanded, the List
    // and Map pane, whose map is always on screen) the saved records reload and the forecast store is
    // asked again, so a find saved on the Journal tab, or the Diagnostics switch turned on, shows the
    // next time the map is shown.
    val isMapsTabShown = if (windowWidthClass == WindowWidthClass.COMPACT || isShortWindow) {
        compactTab == CompactTab.MAP
    } else {
        selectedTab == ResultsTab.MAP || selectedTab == ResultsTab.LIST
    }
    LaunchedEffect(isMapsTabShown) {
        if (isMapsTabShown) onMapShown()
    }
    // Map chrome at 80% (dispatch 2026-09-28-56 as amended by -58; planner message -77, Q2 and Q5): on
    // the medium and expanded layout a map is drawn on screen when the results pane shows List or Map
    // and its MapTab draws its map, which is exactly a searched region that is neither loading nor in
    // error (MapTab's own branches). A surface spanning the window, or the pane, covers it then.
    val wideResultsMapShown = (selectedTab == ResultsTab.LIST || selectedTab == ResultsTab.MAP) &&
        uiState.region != null && !uiState.isLoadingSightings && uiState.sightingsErrorMessage == null
    if (showExitNavigationPrompt) {
        ExitNavigationPrompt(
            onExit = {
                showExitNavigationPrompt = false
                onToggleReturning()
            },
            onKeepNavigating = { showExitNavigationPrompt = false },
            // Compact: raised only on the Maps tab, and follows the tab (-77, Q4). Medium and expanded,
            // reached only when the window changes class while navigating: over the results map when
            // it is drawn (-77, Q5).
            overMap = if (windowWidthClass == WindowWidthClass.COMPACT || isShortWindow) {
                compactTab == CompactTab.MAP
            } else {
                wideResultsMapShown
            },
        )
    }
    val isShortLandscapeWindow = isShortWindow &&
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portEdge = currentWindowPortEdge()
    // Landscape B2 (S1): the punch-hole edge, where the search bar, its filter chip and the icon
    // cluster's default side go in a short landscape window. From punchHoleEdgeFor — the port
    // edge's opposite, by the one rotation-to-edge mapping in PortEdge.kt — never derived a second
    // way here. Read only while the rail shows, like portEdge.
    val punchHoleEdge = punchHoleEdgeFor(
        windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE,
        displayRotation = currentDisplayRotation(),
    )

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
    // Journal redesign J4: the pending deletes' Undo snackbars share this host too.
    PendingDeleteSnackbarEffects(pendingDeleteNotices, logDraftSnackbarHostState)
    // A scheduled-backup notice that could not be a notification is shown here once, at launch (dispatch 2026-09-28-153,
    // item 1; owner "1 A"): the approved text through this same host, no action and no new surface. It is forgotten before it
    // is shown (showSnackbar suspends until it goes), so a launch that is interrupted does not show it twice.
    LaunchedEffect(backup.state.launchNotice) {
        val notice = backup.state.launchNotice ?: return@LaunchedEffect
        backup.onLaunchNoticeShown()
        // Launched on the host's own scope: forgetting the notice changes this effect's key and cancels it, which would
        // cancel a snackbar shown from inside it before it was ever drawn.
        logDraftSnackbarScope.launch { logDraftSnackbarHostState.showSnackbar(message = notice.text, duration = SnackbarDuration.Long) }
    }
    // Alert-delivery dispatch, Item 3: the trip-start audibility warning shares this host — a host
    // is a slot, not a message — rather than adding a second surface over the map. A foreground
    // moment by construction (the user just tapped record), so a composed effect is the right
    // place for it, unlike the off-track alert it warns about.
    LaunchedEffect(tripStartWarning?.id) {
        tripStartWarning?.let { warning ->
            logDraftSnackbarHostState.showSnackbar(message = warning.message, duration = SnackbarDuration.Long)
        }
    }
    LaunchedEffect(networkFixesNotice?.id) {
        networkFixesNotice?.let { notice ->
            logDraftSnackbarHostState.showSnackbar(message = notice.message, duration = SnackbarDuration.Long)
        }
    }
    // Intent 2026-09-28-44, F1 (the owner: "Snackbar only for real drafts (Recommended)"). This used
    // to offer "Saved to Drafts" for whatever entry was open, and its Discard deleted that id through
    // the general delete: after only viewing a committed find, Discard deleted the committed find,
    // with no Undo (docs/audits/2026-09-28-leaving-the-journal-investigation.md, Behaviour 1). Now the
    // snackbar is offered only when the leave keeps a draft (leaveKeepsDraft, the ViewModel's own rule
    // for what it keeps), and Discard deletes that id only while it is still in Drafts when tapped: a
    // new find's draft saved while the snackbar is still up is committed under the same id, and a
    // Discard then would delete it.
    val latestLogUiState by rememberUpdatedState(logUiState)
    val leaveLogEntryEditingOfferingDiscard: () -> Unit = {
        val left = logUiState.editingEntry
        val keptDraftId = left?.takeIf { leaveKeepsDraft(it, logUiState.entries) }?.id
        onLeaveLogEntryEditingIncidentally()
        if (keptDraftId != null) {
            logDraftSnackbarScope.launch {
                val result = logDraftSnackbarHostState.showSnackbar(
                    message = "Saved to Drafts",
                    actionLabel = "Discard",
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    if (latestLogUiState.draftEntries.any { it.id == keptDraftId }) {
                        onDiscardLogDraft(keptDraftId)
                    } else {
                        Log.w("AvailabilityScreen", "Discard on \"Saved to Drafts\" ignored: '$keptDraftId' is no longer a draft.")
                    }
                }
            }
        }
    }
    // Declared after leaveLogEntryEditingOfferingDiscard, which its onOpenFind calls (F3).
    // M1 (B3, B4): what the Maps tab's glyph bubbles look records up in, and the J5c sheet's inputs,
    // all already in hand here. A find's "Open in Journal" opens the find in its report over whatever
    // the Journal was showing (PendingJournalDestination.VIEW_FIND), so the saved Records chip and top
    // tab are never changed (planner's ruling on F3, continuation 2026-09-28-30). Compact: the
    // Journal tab. Medium and expanded: the drawer's LogPanel (owner, Q3: "Open drawer to the find").
    // Checked against the Maps search-bar gate (AvailabilityCompactScaffold's isEditingJournalEntry):
    // the find opens as the Journal tab comes up, where that gate hides the header as it does for any
    // open find; the Maps tab's own bar is gated on the Journal showing, so it is unaffected.
    val usesCompactTree = windowWidthClass == WindowWidthClass.COMPACT || isShortWindow
    val mapBubbleSources = MapRecordSources(
        finds = logUiState.entries,
        galleryPhotos = logUiState.galleryPhotos,
        photoEntryReferenceCounts = logUiState.cartographyEntryPhotoReferenceCounts,
        waypoints = waypoints,
        waypointEntryReferenceCounts = waypointEntryReferenceCounts,
        tracks = tracks,
        plannedTrips = uiState.plannedTrips,
        offlineRegions = uiState.visibleOfflineRegions,
        distanceUnit = uiState.distanceUnit,
        staleThresholdDays = uiState.offlineStaleThresholdDays,
        nowEpochMillis = currentTime::nowEpochMillis,
        getFullRecord = getFullRecord,
        onOpenFind = { findId ->
            // Intent 2026-09-28-44, F3 (the owner, continuation 2026-09-28-45: "Leave the kept one
            // first (Recommended)"): a find kept open on the Journal is left before this one opens
            // over it, through the one wrapper, so a changed one lands in Drafts at once with "Saved
            // to Drafts", an unchanged re-edit's copy is deleted, and a viewed one just closes.
            if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
            pendingJournalFindId = findId
            pendingJournalDestination = PendingJournalDestination.VIEW_FIND
            onOpenLogEntry(findId)
            if (usesCompactTree) {
                compactTab = CompactTab.JOURNAL
            } else {
                // J6a, ruling 5: the wide Journal's Back is one order whatever the route; no
                // `isDrawerOpen` (the compact drawer's flag) is set for it.
                drawerPanel = DrawerPanel.Log
            }
        },
        openFindLabel = OPEN_IN_JOURNAL_LABEL,
        // J8-4: a highlighted record's keeping entries, while the Layers sheet's "Journal entries"
        // switch shows the highlights; with it off no record is highlighted, so no bubble names one.
        journalEntriesKeeping = if (drawnMapLayers.stateOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID).visible) journalHighlights.keptIn else emptyMap(),
        // J8-4, the owner's Q1 ruling ("Open in Journal, prompt first (Recommended)"): switches to the
        // Journal (compact) or opens the drawer's LogPanel (wide, as M1's find route does) with the
        // entry in its report on Entries, the one top-tab change opening it requires; the saved Records
        // chip is untouched. The Journal side (CartographyScreen's openEntryRequest) asks the existing
        // "Save your changes?" first when another entry is open in its editor with unsaved changes, and
        // just closes an unchanged one. A find kept open on the Journal is left first through the one
        // wrapper, as M1's find route does (F3), since it would otherwise show over the entry.
        onOpenEntry = { entryId ->
            if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
            pendingJournalEntryId = entryId
            pendingJournalDestination = PendingJournalDestination.VIEW_ENTRY
            if (usesCompactTree) {
                compactTab = CompactTab.JOURNAL
            } else {
                drawerPanel = DrawerPanel.Log
            }
        },
    )

    // J8, continuation 2026-09-28-65 (the owner: "Set it to "Changes not applied. Try again.""): a failed
    // Show or Hide on map, from the Journal's report or the Maps tab's chip, told on whichever tab is up,
    // as a Toast like the Journal's own save failures, then cleared.
    LaunchedEffect(cartographyUiState.shownOnMapErrorMessage) {
        cartographyUiState.shownOnMapErrorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            onCartographyShownOnMapErrorDismissed()
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
                    // The one visible way to close this drawer: tapping the scrim and
                    // swipe-to-close (on only while the drawer is open, see gesturesEnabled
                    // below) are both undiscoverable.
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
                    expandAdvancedSearchRequested = expandAdvancedSearchRequested,
                    onAdvancedSearchExpandConsumed = { expandAdvancedSearchRequested = false },
                )
                // Sticky footer rows: the log is the newer of the two pre-existing ones, placed
                // above Settings so it isn't the last thing in the sheet — see
                // MushroomLogEntryRow.
                MushroomLogEntryRow(onClick = { drawerPanel = DrawerPanel.Log })
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
                    autoSaveLocationToPhotos = uiState.autoSaveLocationToPhotos,
                    onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
                    lockCameraToPortrait = uiState.lockCameraToPortrait,
                    onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
                    onOpenCrashLogs = { drawerPanel = DrawerPanel.CrashLogs },
                    onOpenDiagnostics = { drawerPanel = DrawerPanel.Diagnostics },
                    backup = backup,
                    showBackupRequest = openBackupRequest,
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

            DrawerPanel.Diagnostics -> {
                // Same one-level-up back as CrashLogs. Debug builds only; see the enum entry.
                DiagnosticsPanel(
                    modifier = Modifier.weight(1f),
                    onBack = { drawerPanel = DrawerPanel.Settings },
                )
            }

            DrawerPanel.Log -> {
                LogPanel(
                    modifier = Modifier.weight(1f),
                    uiState = logUiState,
                    onOpenCameraForLogEntry = { onOpenCamera(InAppCameraTarget.LOG_ENTRY) },
                    onOpenCameraForAlbum = { onOpenCamera(InAppCameraTarget.ALBUM) },
                    onOpenCameraForCartographyEntry = { onOpenCamera(InAppCameraTarget.CARTOGRAPHY_ENTRY) },
                    mapSlot = mapSlot,
                    region = uiState.region ?: JOURNAL_PICKER_DEFAULT_REGION,
                    deviceLocation = uiState.liveFix?.let { LatLng(it.lat, it.lng) },
                    basemap = basemap,
                    night = isNightMode,
                    mapLayers = mapLayersControls.stored,
                    onMapLayerVisibilityChanged = mapLayersControls.onVisibilityChanged,
                    onOpenEntryForEditing = onOpenLogEntryForEditing,
                    onOpenEntryForReport = onOpenLogEntry,
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
                    onAcquirePhotoForCartographyEntry = onAcquirePhotoForCartographyEntry,
                    onFinishCartographyEntry = onFinishCartographyEntry,
                    onSaveCartographyEntry = onSaveCartographyEntry,
                    onDiscardCartographyEntryChanges = onDiscardCartographyEntryChanges,
                    onSaveCartographyEntryAsDraft = onSaveCartographyEntryAsDraft,
                    onCartographySaveErrorDismissed = onCartographySaveErrorDismissed,
                    onDeleteCartographyEntry = onDeleteCartographyEntry,
                    getCartographyEntryMapData = getCartographyEntryMapData,
                    getSavedTrackPaths = getSavedTrackPaths,
                    getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
                    getCartographyEntryCurrentLocation = getCartographyEntryCurrentLocation,
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
                    onDownloadAgain = onDownloadAgain,
                    tracks = tracks,
                    onTracksOpened = onTracksOpened,
                    getFullRecord = getFullRecord,
                    waypoints = waypoints,
                    waypointsErrorMessage = waypointsErrorMessage,
                    onDeleteWaypoint = onDeleteWaypoint,
                    onDeleteTrack = onDeleteTrack,
                    tracksErrorMessage = tracksErrorMessage,
                    waypointEntryReferenceCounts = waypointEntryReferenceCounts,
                    pendingDestination = pendingJournalDestination,
                    pendingFindId = pendingJournalFindId,
                    pendingEntryId = pendingJournalEntryId,
                    onPendingDestinationConsumed = {
                        pendingJournalDestination = null
                        pendingJournalFindId = null
                        pendingJournalEntryId = null
                    },
                    onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
                    // J6a: the same holders the compact tree gets (ruling 5, J10), so an open detail, the
                    // view choice, the Records chip and a find over a view survive a panel switch and a
                    // change of tree; the phone's find "+" tile and report step; the J4b delete paths.
                    onStartEntry = onStartLogEntry,
                    onStartEditingEntry = onStartEditingLogEntry,
                    onRequestDeleteCartographyEntry = onRequestDeleteCartographyEntry,
                    onRequestDeleteGalleryPhoto = onRequestDeleteGalleryPhoto,
                    journalState = journalScreenState,
                    cartographyEntryModeState = cartographyEntryModeState,
                    findEntryModeState = findEntryModeState,
                    findOverViewState = findOverViewState,
                    detailSlot = journalDetailSlot,
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
                // J6c: fullscreen hides the search bar (and, below, the summary, the notice, the tab row and the
                // drawer), so the map fills the window; exit restores them.
                if (!isMapFullscreen) AvailabilitySearchTopBar(
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
                    // The suggestions span the results pane, over its map when it draws one (-77, Q2).
                    suggestionsOverMap = wideResultsMapShown,
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
                // The summary's tap opens the search (owner, 2026-09-28, "Make the tap open search"):
                // the permanent drawer comes back to its search panel from whichever panel it shows,
                // even before any search, and the last species query is reopened as before.
                if (!isMapFullscreen) {
                    ActiveSearchSummary(
                        uiState,
                        distanceUnit,
                        onClick = {
                            drawerPanel = DrawerPanel.Search
                            // Continuation 2026-09-28-38 (owner: "Yes it should"): straight to the
                            // location controls, with "Advanced search" open. A one-shot request that
                            // the section consumes, so a later collapse by the user stands.
                            expandAdvancedSearchRequested = true
                            onReopenTaxonSuggestions()
                        },
                    )
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
                }

                val onLogFindHere: (LatLng) -> Unit = { location ->
                    // F3 (continuation 2026-09-28-45): the find open in the drawer's LogPanel is left
                    // first, as on compact; see onOpenFind above.
                    if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
                    drawerPanel = DrawerPanel.Log
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
                        mapLayers = mapLayersControls,
                        bubbleSources = mapBubbleSources,
                        onPlaceTripPin = onPlaceTripPin,
                        onLogFindHere = onLogFindHere,
                        breadcrumbPoints = breadcrumbPoints,
                        waypoints = mapWaypoints,
                        onDropWaypoint = onDropWaypoint,
                        taxonFilter = mapTaxonFilter,
                        onClearTaxonFilter = onClearMapTaxonFilter,
                        onViewOnMap = onViewSpeciesOnMap,
                        selectedTab = selectedTab,
                        controls = WideMapControls(
                            isFullscreen = isMapFullscreen,
                            // The phone's own toggle and its persistence (the one preference), so fullscreen
                            // behaves as it does there, restarts included (J6c).
                            onToggleFullscreen = {
                                isMapFullscreen = !isMapFullscreen
                                onMapFullscreenChanged(isMapFullscreen)
                            },
                            onLocateMe = onLocateMe,
                            isRecording = isRecording,
                            onToggleRecording = onToggleRecording,
                            startRecordingErrorMessage = startRecordingErrorMessage,
                            returnToStart = returnToStart,
                            isReturning = isReturning,
                            isNavigating = isNavigating,
                            isOffTrack = isOffTrack,
                            onToggleReturning = onToggleReturning,
                            compassProvider = compassProvider,
                            computeTrueHeading = computeTrueHeading,
                            navigationTarget = navigationTarget,
                            pathHomeMeters = pathHomeMeters,
                            currentTime = currentTime,
                            clusterPosition = mapIconClusterPosition,
                        ),
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
        CompactMainScaffold(
            isMapFullscreen = { isMapFullscreen },
            focusManager = focusManager,
            keyboardController = keyboardController,
            compactTab = { compactTab },
            logUiState = logUiState,
            logPhotoAcquisitionInFlight = { logPhotoAcquisitionInFlight },
            cartographyUiState = cartographyUiState,
            isDrawerOpen = { isDrawerOpen },
            isShortLandscapeWindow = isShortLandscapeWindow,
            portEdge = portEdge,
            punchHoleEdge = punchHoleEdge,
            logDraftSnackbarHostState = logDraftSnackbarHostState,
            uiState = uiState,
            distanceUnit = distanceUnit,
            pendingJournalDestination = { pendingJournalDestination },
            currentTime = currentTime,
            mapSlot = mapSlot,
            mapIconClusterPosition = mapIconClusterPosition,
            mapCameraMemory = mapCameraMemory,
            mapRenderMode = mapRenderMode,
            mapLayers = mapLayersControls,
            mapMode = { mapMode },
            isNightMode = isNightMode,
            isRecording = isRecording,
            startRecordingErrorMessage = startRecordingErrorMessage,
            breadcrumbPoints = breadcrumbPoints,
            mapWaypoints = mapWaypoints,
            returnToStart = returnToStart,
            isReturning = isReturning,
            isNavigating = isNavigating,
            isOffTrack = isOffTrack,
            compassProvider = compassProvider,
            computeTrueHeading = computeTrueHeading,
            navigationTarget = navigationTarget,
            pathHomeMeters = pathHomeMeters,
            mapTaxonFilter = { mapTaxonFilter },
            basemap = basemap,
            tracks = tracks,
            waypoints = waypoints,
            waypointsErrorMessage = waypointsErrorMessage,
            waypointEntryReferenceCounts = waypointEntryReferenceCounts,
            onLocateMe = onLocateMe,
            onLeaveLogEntryEditingIncidentally = onLeaveLogEntryEditingIncidentally,
            leaveLogEntryEditingOfferingDiscard = leaveLogEntryEditingOfferingDiscard,
            onDismissTaxonSuggestions = onDismissTaxonSuggestions,
            onIsDrawerOpenChange = { isDrawerOpen = it },
            onIsMapFullscreenChange = { isMapFullscreen = it },
            onMapFullscreenChanged = onMapFullscreenChanged,
            onCompactTabChange = { compactTab = it },
            onSelectedTabChange = { selectedTab = it },
            onUseCurrentLocation = onUseCurrentLocation,
            onTaxonSearchQueryChanged = onTaxonSearchQueryChanged,
            onTaxonSearchResultSelected = onTaxonSearchResultSelected,
            onPendingJournalDestinationChange = {
                pendingJournalDestination = it
                if (it == null) {
                    pendingJournalFindId = null
                    pendingJournalEntryId = null
                }
            },
            pendingJournalFindId = { pendingJournalFindId },
            pendingJournalEntryId = { pendingJournalEntryId },
            onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
            mapBubbleSources = mapBubbleSources,
            onStartLogEntry = onStartLogEntry,
            onViewSpeciesOnMap = onViewSpeciesOnMap,
            onMapModeChange = { mapMode = it },
            onPlaceTripPin = onPlaceTripPin,
            onToggleRecording = onToggleRecording,
            onDropWaypoint = onDropWaypoint,
            onToggleReturning = onToggleReturning,
            onClearMapTaxonFilter = onClearMapTaxonFilter,
            onManualLatChanged = onManualLatChanged,
            onManualLngChanged = onManualLngChanged,
            onSearchManualCoordinates = onSearchManualCoordinates,
            onOpenCamera = onOpenCamera,
            onOpenLogEntry = onOpenLogEntry,
            onCloseLogEntry = onCloseLogEntry,
            onLogEntryChanged = onLogEntryChanged,
            onStartEditingLogEntry = onStartEditingLogEntry,
            onSaveLogEntry = onSaveLogEntry,
            onCancelLogEntryEditing = onCancelLogEntryEditing,
            onLogPhotoAcquisitionInFlightChange = { logPhotoAcquisitionInFlight = it },
            onAddLogPhoto = onAddLogPhoto,
            onRemoveLogPhoto = onRemoveLogPhoto,
            onPullLogPhoto = onPullLogPhoto,
            onDeleteLogEntry = onDeleteLogEntry,
            onSaveLogErrorDismissed = onSaveLogErrorDismissed,
            onDeleteGalleryPhoto = onDeleteGalleryPhoto,
            onAddGalleryPhoto = onAddGalleryPhoto,
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
            onAcquirePhotoForCartographyEntry = onAcquirePhotoForCartographyEntry,
            onFinishCartographyEntry = onFinishCartographyEntry,
            onSaveCartographyEntry = onSaveCartographyEntry,
            onDiscardCartographyEntryChanges = onDiscardCartographyEntryChanges,
            onSaveCartographyEntryAsDraft = onSaveCartographyEntryAsDraft,
            onCartographySaveErrorDismissed = onCartographySaveErrorDismissed,
            onDeleteCartographyEntry = onDeleteCartographyEntry,
            onRequestDeleteCartographyEntry = onRequestDeleteCartographyEntry,
            onRequestDeleteGalleryPhoto = onRequestDeleteGalleryPhoto,
            // J4b L1: a find tile's long-press Edit uses the same open-and-edit call the wide tree's
            // LogPanel already uses for every find it opens.
            onOpenLogEntryForEditing = onOpenLogEntryForEditing,
            getCartographyEntryMapData = getCartographyEntryMapData,
            getSavedTrackPaths = getSavedTrackPaths,
            getCartographyEntryOfflineRegion = getCartographyEntryOfflineRegion,
            getCartographyEntryCurrentLocation = getCartographyEntryCurrentLocation,
            onOfflineMapLatChanged = onOfflineMapLatChanged,
            onOfflineMapLngChanged = onOfflineMapLngChanged,
            onOfflineMapRadiusChanged = onOfflineMapRadiusChanged,
            onOfflineMapNameChanged = onOfflineMapNameChanged,
            onOfflineMapsOpened = onOfflineMapsOpened,
            onDownloadOfflineMaps = onDownloadOfflineMaps,
            onDeleteOfflineRegion = onDeleteOfflineRegion,
            onDownloadAgain = onDownloadAgain,
            onTracksOpened = onTracksOpened,
            getFullRecord = getFullRecord,
            onDeleteWaypoint = onDeleteWaypoint,
            onDeleteTrack = onDeleteTrack,
            tracksErrorMessage = tracksErrorMessage,
            onRecentSearchSelected = onRecentSearchSelected,
            onRadiusChanged = onRadiusChanged,
            onMonthSelected = onMonthSelected,
            journalScreenState = journalScreenState,
            cartographyEntryModeState = cartographyEntryModeState,
            findEntryModeState = findEntryModeState,
            findOverViewState = findOverViewState,
        )
    }


    // Landscape B1, P1 and Resolution R1 ("Classify by window"): a short window takes the compact
    // tree whatever its width. Every other window is chosen by width exactly as before.
    if (windowWidthClass == WindowWidthClass.COMPACT || isShortWindow) {
        // Landscape B3 (P12 as corrected by R2; owner ruling 1 in
        // docs/audits/2026-09-27-landscape-b3-prebuild-report.md, section 5). With gestures on
        // while the drawer is open, M3's scrim tap and swipe-to-close call drawerState.close()
        // themselves and never touch isDrawerOpen, the authority for "should the drawer be open"
        // (its comment, above). Left alone, the flag stays true after such a close: the drawer is
        // visibly shut, but Back is still routed to the close-drawer handler and Tools sets a flag
        // that is already true, so the drawer will not open again (seen in CompactToolsDrawerTest
        // with only the gesturesEnabled change applied). So the flag follows a close the drawer
        // made itself, once that close has settled. Only the settled-closed edge is acted on: a
        // just-requested open (flag true, drawer still closed and idle) emits nothing, because
        // the watched value has not changed.
        LaunchedEffect(drawerState) {
            snapshotFlow { drawerState.currentValue == DrawerValue.Closed && !drawerState.isAnimationRunning }
                .collect { settledClosed -> if (settledClosed && isDrawerOpen) isDrawerOpen = false }
        }
        // Landscape B3 (P12, owner ruling 2, "Flip layout direction"): in a short landscape window
        // the drawer opens from the rail side, the port edge. ModalNavigationDrawer has no edge
        // parameter; it anchors to the start edge of LocalLayoutDirection. So the direction is
        // set around the drawer to the one whose start edge is the port edge, and the ambient
        // direction is restored inside both the sheet's content and the screen content, so only
        // the drawer's anchoring changes. Portrait and every non-short window keep the ambient
        // direction untouched. The mapping is physical (port on the right -> Rtl, on the left ->
        // Ltr), not "the opposite of ambient": in an RTL locale the drawer already starts on the
        // right, and flipping it would move it away from the rail. With the app's LTR locale
        // this is exactly "flip at ROTATION_90, unchanged at ROTATION_270". See the B3 drawer
        // completion report for that choice, which is open for the planner to confirm.
        val ambientDirection = LocalLayoutDirection.current
        val drawerDirection = if (isShortLandscapeWindow) {
            if (portEdge == ScreenEdge.Right) LayoutDirection.Rtl else LayoutDirection.Ltr
        } else {
            ambientDirection
        }
        // Where the controls over the map are, for the marker fan-out to keep clear of (MapKeepOut.kt).
        val mapKeepOuts = remember { MapKeepOuts() }
        CompositionLocalProvider(LocalLayoutDirection provides drawerDirection, LocalMapKeepOuts provides mapKeepOuts) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            // Swipe-to-open is off on purpose: the content behind the drawer is a full-screen
            // pannable map, and a horizontal drag there means "pan", not "open the drawer". Tools
            // is the way in. Gestures are on only while the drawer is open, which is what lets a
            // scrim tap close it: in material3 1.5.0-alpha26 both the scrim's dismiss and the
            // drag are gated on this flag (`if (gesturesEnabled && ...)` and
            // `anchoredDraggable(enabled = gesturesEnabled)`), so with it off neither a scrim tap
            // nor swipe-to-close worked. While open it also re-enables swipe-to-close; while
            // closed it stays false, so swipe-to-open stays off. See the pre-build report above.
            gesturesEnabled = drawerState.isOpen,
            drawerContent = {
                // The sheet itself stays in drawerDirection, so its rounded edge faces the
                // content and its start-side inset padding lands on the window edge it sits on.
                // Only what is inside it goes back to the ambient direction.
                // Material3's own default container role for a modal drawer, passed explicitly, and
                // its content colour pinned to the role's own (`contentColorFor` matches a colour-scheme
                // role exactly; see `MapLayersSheet`).
                // Over the Maps tab at the map chrome's alpha, following the tab (owner: "80% over Maps
                // (Recommended)"; planner message -77, Q4); solid over the other tabs.
                val drawerColor = mapChromeFill(DrawerDefaults.modalContainerColor, compactTab == CompactTab.MAP)
                val drawerContentColor = contentColorFor(DrawerDefaults.modalContainerColor)
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
                        onClose = { isDrawerOpen = false },
                        onDeletePlannedTrip = onDeletePlannedTrip,
                        currentTime = currentTime,
                        isNightMode = isNightMode,
                        onNightModeMapsChanged = onNightModeMapsChanged,
                        autoSaveLocationToPhotos = uiState.autoSaveLocationToPhotos,
                        onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
                        lockCameraToPortrait = uiState.lockCameraToPortrait,
                        onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
                        themeMode = uiState.themeMode,
                        onThemeModeChanged = onThemeModeChanged,
                        crashFileStore = crashFileStore,
                        backup = backup,
                        openSettingsRequest = openBackupRequest,
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
    } else {
        // M3's "swapped" adaptive pattern: the same drawer panel, but always on screen and never
        // covering the content — see drawerSheetContent's own doc comment for what's shared.
        // PERMANENT_DRAWER_WIDTH keeps the panel's text at a readable line length rather than
        // stretching it as the window grows past the medium breakpoint.
        PermanentNavigationDrawer(
            drawerContent = {
                // J6c: fullscreen hides the Journal column (this drawer); exit restores it. Nothing is
                // composed here meanwhile, so the drawer takes no width and the map fills the window.
                if (!isMapFullscreen) PermanentDrawerSheet(modifier = Modifier.width(PERMANENT_DRAWER_WIDTH)) {
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
            content = {
                // J6a (ruling 1, list-detail): an opened Journal entry, find, record's details or picker
                // takes the whole right side, in place of the results pane and its search bar, while the
                // list stays in the drawer's 360 dp column. The pane is drawn over mainScaffold, which stays
                // composed beneath it so what the person had there (the chosen tab, the list's scroll, the
                // map's camera) is as it was when the detail closes; while covered its semantics are cleared,
                // so nothing beneath is reachable by TalkBack or a test, and its focus is dropped so a
                // keyboard left up over the search field goes. The pane is opaque and takes every touch.
                val journalDetail = if (drawerPanel == DrawerPanel.Log) journalDetailSlot.top else null
                LaunchedEffect(journalDetail != null) {
                    if (journalDetail != null) {
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (journalDetail != null) Modifier.clearAndSetSemantics { } else Modifier),
                    ) {
                        mainScaffold()
                    }
                    if (journalDetail != null) JournalDetailPane(journalDetail)
                }
            },
        )
    }
    // The in-app camera, once, outside the width-class branch above — deliberately not inside
    // either tree, so the flip a rotation causes on a phone (COMPACT to MEDIUM) does not dispose
    // it. Its own open flag lives in InAppCameraViewModel, which survives the recreation.
    // See InAppCameraHost for both mechanisms and the owner's decision.
    // Landscape B1 (2026-09-26): a phone's landscape window is now short and stays in the compact
    // tree, so a phone's rotation no longer flips trees; a window crossing 600dp while tall (a
    // foldable, a resized window) still does, which is why the placement stays as it is.
    //
    // It sits *after* the branch rather than before it (2026-09-19): since the camera draws in the
    // Activity's own window instead of a dialog's, nothing but composition order puts it on top,
    // and a sibling composed first draws and hit-tests underneath. zIndex was tried and measured
    // insufficient — see InAppCameraDialog's own Box. The reason for the original placement is
    // untouched: the camera is still outside both width-class trees, so the flip still cannot
    // dispose it, which is what "above the branch" was always about.
    InAppCameraHost(
        target = inAppCameraTarget,
        cameraCaptureFiles = cameraCaptureFiles,
        lockToPortrait = uiState.lockCameraToPortrait,
        gridMode = cameraGridMode,
        onGridModeChanged = onCameraGridModeChanged,
        autoSaveLocationToPhotos = uiState.autoSaveLocationToPhotos,
        onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
        onLogEntryPhoto = onAddLogPhoto,
        onAlbumPhoto = onAddGalleryPhoto,
        onCartographyEntryPhoto = onAcquirePhotoForCartographyEntry,
        onDismiss = onCloseCamera,
        camera = inAppCamera,
    )
}

/** The compact Tools drawer's sheet, for tests (map chrome at 80%, dispatch 2026-09-28-56 as amended by -58). */
internal const val TOOLS_DRAWER_SHEET_TAG = "tools-drawer-sheet"
