package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.adaptive.mainWindowSearchEdgeFor
import com.zynergylabs.forager.app.ui.adaptive.mainWindowRailEdgeFor
import com.zynergylabs.forager.app.domain.MapIconClusterPlacement
import com.zynergylabs.forager.app.domain.EntryGroup
import com.zynergylabs.forager.app.domain.RouteLine
import com.zynergylabs.forager.app.domain.WaypointNavigationTarget
import com.zynergylabs.forager.app.domain.waypointNavigationTarget
import com.zynergylabs.forager.app.ui.log.JournalTopTab
import com.zynergylabs.forager.app.ui.log.RecordsSubTab
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOrigin
import com.zynergylabs.forager.app.ui.map.WaypointNavigationOriginSaver
import com.zynergylabs.forager.app.ui.map.MapCompass
import com.zynergylabs.forager.app.ui.map.MapPosition
import com.zynergylabs.forager.app.ui.map.LocalMapPosition
import com.zynergylabs.forager.app.ui.map.rememberShownPosition
import com.zynergylabs.forager.app.ui.map.LocalMapCompass
import com.zynergylabs.forager.app.ui.theme.navigationBarContainerColor
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.State
import androidx.compose.runtime.CompositionLocalProvider
import com.zynergylabs.forager.app.ui.log.LocalTrackDetailsFold
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
import com.zynergylabs.forager.app.domain.CameraFormat
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.ui.map.layers.JOURNAL_ENTRIES_SWITCH_LAYER_ID
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableIntStateOf
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_ACTION_LABEL
import com.zynergylabs.forager.app.domain.SETTINGS_RESET_MESSAGE
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.BuildConfig
import com.zynergylabs.forager.app.domain.CachedSearchSummary
import com.zynergylabs.forager.app.domain.CartographyEntryMapData
import com.zynergylabs.forager.app.domain.CompassProvider
import com.zynergylabs.forager.app.domain.ComputeTrueHeadingUseCase
import com.zynergylabs.forager.app.domain.CurrentTimeProvider
import com.zynergylabs.forager.app.domain.ForagingSelection
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
import com.zynergylabs.forager.app.ui.backup.BackupControls
import com.zynergylabs.forager.app.ui.adaptive.isLandscapeWindow
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
import com.zynergylabs.forager.app.ui.log.rememberJournalScreenState
import com.zynergylabs.forager.app.ui.log.MushroomLogUiState
import com.zynergylabs.forager.app.ui.log.PendingJournalDestination
import com.zynergylabs.forager.app.domain.GpxImportOutcome
import com.zynergylabs.forager.app.importgpx.GpxImportNotice
import com.zynergylabs.forager.app.importgpx.gpxImportMessage
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
import com.zynergylabs.forager.app.ui.map.MapReturnMemory
import com.zynergylabs.forager.app.ui.map.MapRenderMode
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

/** How long a first back press keeps "exit on the next one" armed — see [AvailabilityScreen]. */
private const val DOUBLE_BACK_EXIT_WINDOW_MS = 2000L

/**
 * Map-first layout: the results (map or ranked list) own the content area, and everything set far
 * less than once per search — location, radius, month, the foraging-areas layer, and trip
 * planning — lives in a navigation drawer behind the app bar's tune icon, as two independently
 * collapsible sections; see [SearchControls]. Species/category, the one control used on nearly
 * every search, lives in the top bar itself; see [SearchEntryBar], which replaced the old
 * `AvailabilitySearchTopBar` (dispatch 2026-09-28-658, scout item F4: this linked a composable that
 * no longer exists).
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
 * squeeze this file's whole layout exists to avoid. The fixed two-row bar this paragraph was
 * written about, `AvailabilitySearchTopBar`, is gone; [SearchEntryBar] pins its field to a short,
 * fixed height for the same reason (see its doc comment), and [AvailabilityScreenLayoutTest] holds
 * the measurement that verifies the squeeze has not come back.
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
     * [SearchDropdown].
     */
    onRecentSearchSelected: (CachedSearchSummary) -> Unit,
    /**
     * RECORD -723: the search bar's Clear, shown only while a search is showing (a region is set). MainActivity
     * wires it to [AvailabilityViewModel.clearSearch]. Defaulted to a no-op only so the many screen tests that
     * never show Clear need not pass it.
     */
    onClearSearch: () -> Unit = {},
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
    /** "Automatically Save Location to Photos" — see [AvailabilityUiState.autoSaveLocationToPhotos]; set from the camera's Location chip (Settings until dispatch 2026-09-28-707, then also the camera's gear panel until dispatch 2026-10-13-03). Defaulted, like [onDistanceUnitSelected], so a screen test that does not exercise this setting needs no argument for it. */
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit = {},
    /** Settings' "Sundown alerts" (dispatch 2026-09-28-592) — see [AvailabilityUiState.sundownAlertsEnabled]. */
    onSundownAlertsEnabledChanged: (Boolean) -> Unit = {},
    /** Settings' "Dark under trees" (dispatch 2026-09-28-592) — see [AvailabilityUiState.darknessMarginMinutes]. */
    onDarknessMarginChanged: (Int) -> Unit = {},
    /** Settings' "Off-track reminder" (dispatch 2026-09-28-626) — see [AvailabilityUiState.offTrackReminderEnabled]. */
    onOffTrackReminderChanged: (Boolean) -> Unit = {},
    /** The quick menu's "Battery saver" (dispatch 2026-09-28-767) — see [AvailabilityUiState.batterySaverEnabled]. */
    onBatterySaverChanged: (Boolean) -> Unit = {},
    /**
     * Settings' Backup section (journal backup and restore, dispatch 2026-09-28-127): its state and callbacks.
     * Defaulted, so a caller with no backup still composes the section, inert.
     */
    backup: BackupControls = BackupControls(),
    /** Counts up when the person taps Done on the restore page: go to the Maps tab and close the drawer. */
    returnToMapRequest: Int = 0,
    /** Counts up when a backup notification is tapped: open the Backup section in Tools, then Settings. */
    openBackupRequest: Int = 0,
    /**
     * RECORD -660/-661: a settings file was found corrupt and reset, and the one-time message is owed.
     * Shown as a snackbar that stays until dismissed, with a "Settings" action that opens Settings;
     * [onSettingsResetNoticeShown] is called as it is shown, so it is shown once. Defaulted to nothing owed.
     */
    settingsResetNoticePending: Boolean = false,
    onSettingsResetNoticeShown: () -> Unit = {},
    /**
     * Plan T16: a finished GPX import to show, from the in-app "Import GPX" or from Open with or Share.
     * The screen goes to Journal > Records > Tracks with the first new track's details open, and says the
     * outcome's message ([gpxImportMessage]) as a Toast, then calls [onGpxImportNoticeShown] with its seq.
     * `null` (the default) is nothing pending.
     */
    gpxImportNotice: GpxImportNotice? = null,
    onGpxImportNoticeShown: (Long) -> Unit = {},
    /** Plan T16: Records > Tracks > "Import GPX", given the picked file. `null` (the default) shows no button. */
    onGpxFilePicked: ((android.net.Uri) -> Unit)? = null,
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
     * The mushroom log's own state, read by the Journal tab ([JournalTab]).
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
    /** The camera's remembered format and the way to change it, from `CameraFormatViewModel` (dispatch 2026-10-15-01). Defaulted like the camera's other inputs. */
    cameraFormat: CameraFormat = CameraFormat.Jpg,
    onCameraFormatChanged: (CameraFormat) -> Unit = {},
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
     * not [onOpenLogEntry] and [onStartEditingLogEntry] chained here. A find tile's long-press Edit
     * in [JournalTab] calls it.
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
    /** Standalone-photos dispatch: Camera/Gallery acquisition, no owning find — the album's own Camera/Import buttons (the Journal's Entries album). */
    onAddGalleryPhoto: (PhotoSource) -> Unit = {},
    /** Clears [logUiState]'s `saveErrorMessage` once its Toast has shown — see [JournalTab]'s identical parameter. */
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
    /** Data part A (dispatch 2026-09-28-667): a group switch in the entry editor's "In this entry" panel, `CartographyViewModel.onSetEntryGroupIncluded`. */
    onSetEntryGroupIncluded: (EntryGroup, Boolean) -> Unit = { _, _ -> },
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
     * (`CartographyViewModel.requestDeleteEntry`), passed to `JournalTab`.
     * `null` (the default) leaves the cards without the swipe.
     */
    onRequestDeleteCartographyEntry: ((String) -> Unit)? = null,
    /**
     * J8-3: shows or hides a saved entry on the Maps tab (`CartographyViewModel.onSetShownOnMap`): the
     * entry report's "Show on map" and "Hide from map", and the Maps-tab chip's "Hide"
     * and "Hide all". Defaulted, so the many tests of this screen that never show an entry are unchanged.
     */
    onSetCartographyEntryShownOnMap: (entryId: String, shown: Boolean) -> Unit = { _, _ -> },
    /** Clears [cartographyUiState]'s `shownOnMapErrorMessage` once its Toast has shown (J8, continuation `2026-09-28-65`). */
    onCartographyShownOnMapErrorDismissed: () -> Unit = {},
    /**
     * Clears [cartographyUiState]'s `saveErrorMessage` once its Toast has shown. That Toast is hosted
     * by the Journal itself, [JournalTab], not here, so this is
     * threaded to it (intent `2026-09-28-68`, continuation `2026-09-28-76`).
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
     * Dispatch 2026-09-28-626 (plan T14): the off-track reminder's background prompt, shown once in
     * the same host as [tripStartWarning]; a tap anywhere on it opens Forager's App info page. See
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.backgroundRunPrompt].
     */
    backgroundRunPrompt: RecordingNotice? = null,
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
    /**
     * RECORD -844, -845: the return has just ended on arrival and the navigation display holds "Arrived",
     * with the start ringed, until the ViewModel's hold ends; see
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.isArrivalHeld]. Counts as navigating, so
     * the display stays up for the hold and then closes to the plain map.
     */
    isArrivalHeld: Boolean = false,
    /** Set once [isReturning] and the walker has gone off the path they walked out, until back on it — see `OffTrackJudge` (dispatch 2026-09-28-425). */
    isOffTrack: Boolean = false,
    /**
     * The recording's sundown line (dispatch 2026-09-28-592, plan task T3), from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.sundownLine]: the strip shows it while
     * recording, the navigation display once Return or Navigate is tapped. `null` with no recording.
     */
    recordingSundownLine: com.zynergylabs.forager.app.domain.SundownLine? = null,
    /**
     * This recording's Back by (dispatch 2026-09-28-645, plan task T15), from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.backBy]: the quick menu's top line,
     * the gear's dot, and the strip's line in its last hour. `null` with none set or no recording.
     */
    recordingBackBy: com.zynergylabs.forager.app.domain.BackByShown? = null,
    /** The quick menu's "+1 h", "+2 h", "+3 h" and picked time: [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.setBackBy]. */
    onBackByChoice: (com.zynergylabs.forager.app.domain.BackByChoice) -> Unit = {},
    /** The quick menu's "Clear": [com.zynergylabs.forager.app.ui.track.TrackRecordingViewModel.clearBackBy]. */
    onClearBackBy: () -> Unit = {},
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
     * The route home while returning (dispatch 2026-09-28-423, plan task T6), read from
     * [com.zynergylabs.forager.app.ui.track.TrackRecordingUiState.routeHome] by [returnRouteOf].
     * The HUD's needle, large figure and "Try again" come from it; see [NavigationHud]. Not
     * nullable: this screen's HUD is the return HUD, and a return is always in route mode (the
     * planner's ruling), so no caller can put it in the straight-line mode by leaving this out.
     * [ReturnRoute.Pending] until there is a result, as `MainActivity` passes it.
     */
    returnRoute: ReturnRoute = ReturnRoute.Pending,
    /** The way back drawn on the map while returning (dispatch 2026-09-28-497); `null` draws none. */
    routeLine: RouteLine? = null,
    /** The HUD's "Try again": `TrackRecordingViewModel.retryRoute`. */
    onRetryRoute: () -> Unit = {},
    /**
     * Dispatch 2026-09-28-502 (plan tasks T8 and T9): "Navigate" on a waypoint, given its id
     * (`AvailabilityViewModel.onNavigateToWaypoint`). Offered in the Maps tab's bubbles, the waypoint's
     * details sheet and the Records rows; `null` (the default) offers it nowhere, as [onDeleteTrack]
     * does for its delete.
     */
    onNavigateToWaypoint: ((String) -> Unit)? = null,
    /** Dispatch 2026-09-28-502: ends the waypoint navigation (`AvailabilityViewModel.onStopWaypointNavigation`). */
    onStopWaypointNavigation: () -> Unit = {},
    /**
     * Dispatch 2026-09-28-502: whether [waypoints] has been read yet (`TrackRecordingUiState.waypointsLoaded`).
     * Until it has, a waypoint being navigated to that is missing from it is waited for, not taken as
     * deleted. `true` by default: a caller that hands a list has loaded it.
     */
    waypointsLoaded: Boolean = true,
    /**
     * What fills the map's box. Defaults to the real map, so no production caller passes it; see
     * [MapSlot] for why the map is reached through a slot rather than named directly here.
     */
    mapSlot: MapSlot = SightingsMapSlot,
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
     * the trip-start warning use, so it docks where those do and outlives a
     * Journal tab change. `MainActivity` builds them from the owning ViewModels' pending state; see
     * [PendingDeleteSnackbarEffects]. Empty by default, so no other caller changes.
     */
    pendingDeleteNotices: List<PendingDeleteNotice> = emptyList(),
    /**
     * Map layers L0b. [onMapShown] runs every time the Maps tab comes into view (the Maps
     * bottom-nav tab or rail item), which
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
    /**
     * Dispatch 2026-09-28-708: the Maps tab's basemap was picked, so the ViewModel can store it
     * ([AvailabilityViewModel.onMapModeSelected]). Defaulted, so no other caller changes.
     */
    onMapModeSelected: (MapMode) -> Unit = {},
    /**
     * RECORD -711: a drag of the Maps tab's icon cluster ended, so the ViewModel can store its side and
     * height ([AvailabilityViewModel.onMapIconClusterPlacementChanged]). Defaulted, so no other caller changes.
     */
    onMapIconClusterPlacementChanged: (MapIconClusterPlacement) -> Unit = {},
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
    // is separate from selectedTab rather than extending ResultsTab itself (a 3-way enum). Kept in sync with selectedTab
    // below whenever the tapped destination is one of the three they share, so onMapTabSelected/
    // onSeasonalTabSelected's LaunchedEffect keeps firing correctly .
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
    // Data part A (dispatch 2026-09-28-667): which groups of the entry editor's "In this entry" panel
    // the user opened, held here so a tab change does not close them (CLAUDE.md, UX defaults). Plain
    // remember, not saveable: it is a convenience within a session, and a recreation closing them
    // loses nothing the user wrote.
    val cartographyOpenEntryGroupsState = remember { mutableStateOf(emptySet<EntryGroup>()) }
    // Intent 2026-09-28-44, F3 ("Keep finds open too (Recommended)"): an open find's mode, and M1's
    // find over the view, held here for the same reason. The mode saves as its enum. FindOverView is
    // plain remember: it survives the tab change the ruling is about, not a recreation.
    val findEntryModeState = rememberSaveable { mutableStateOf(JournalEntryMode.REPORT) }
    val findOverViewState = remember { mutableStateOf<FindOverView?>(null) }

    // Device-check patch, Items 2/3: whether a find's camera/gallery round-trip is currently in
    // flight, reported up from JournalTab via
    // onPhotoAcquisitionInFlightChanged (see LogEntryDetailScreen's own doc comment on that
    // parameter). Read by this screen's own ON_STOP hook below, to suppress the "user backgrounded
    // the app" incidental-exit heuristic while the backgrounding is this app's own doing.
    var logPhotoAcquisitionInFlight by remember { mutableStateOf(false) }

    // "View on Map" on a List-tab species row: which taxon (if any) the map tabs should limit
    // their sightings to. Lives here, alongside selectedTab/compactTab, because both the List and
    // Map tabs read and clear it and neither is an ancestor of the other
    // (compactMainScaffold shows one at a time, but both are built from this same function's state). Null means "no filter" — the ordinary, every
    // sighting view.
    var mapTaxonFilter by remember { mutableStateOf<Long?>(null) }

    // Sets the filter and jumps to the Maps tab — both selectedTab and compactTab are updated; see
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
    //
    // Dispatch 2026-09-28-502 (plan tasks T8 and T9) is that second mode: navigating to a chosen waypoint,
    // with or without a recording. The waypoint is the AvailabilityViewModel's (uiState.waypointNavigation),
    // resolved here against the waypoints the app has loaded; while it is found it is the target, and it
    // overrules the return, which the ViewModel paused when it began. Before the waypoints load it is
    // waited for; once loaded and missing, it was deleted, and the navigation ends with a message (below).
    val waypointTarget = waypointNavigationTarget(uiState.waypointNavigation, waypoints, waypointsLoaded)
    val navigatingToWaypoint = (waypointTarget as? WaypointNavigationTarget.Found)?.waypoint
    val isNavigating = isReturning || isArrivalHeld || navigatingToWaypoint != null
    val target = navigatingToWaypoint ?: navigationTarget
    // Navigation HUD stage one's display rules, applied once here so the map and the
    // Records list agree — see mapVisibleWaypoints. Records keeps the full list.
    val mapWaypoints = remember(waypoints, isNavigating, target) {
        mapVisibleWaypoints(waypoints, isNavigating = isNavigating, target = target)
    }
    // Dispatch -502, Amendment 1 ("Back to where you tapped Navigate"): where Navigate was tapped, so one Back ends the
    // waypoint navigation and puts the walker back at that step. Saved with the screen, so it outlives a tab change, a
    // recreation and the phone closing and restoring the app; a swipe-away loses it, and Back then ends the navigation on
    // the map (the planner accepted). Cleared wherever the navigation ends: Back, the X-circle or the HUD's X, a deletion.
    var waypointNavigationOrigin by rememberSaveable(stateSaver = WaypointNavigationOriginSaver) { mutableStateOf<WaypointNavigationOrigin?>(null) }
    // The step Back opens again, once, on the tab it switched to: a bubble or sheet on the Maps tab, or the sheet in Records.
    var mapWaypointReopen by remember { mutableStateOf<WaypointNavigationOrigin?>(null) }
    var recordsWaypointReopen by remember { mutableStateOf<String?>(null) }

    // Local remembered state, same reasoning as selectedTab below: purely a display
    // decision the ViewModel has no part in. The compact map icon stack's fullscreen toggle sets
    // this — see CompactMapTab's call site. Only reachable while on the
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
    //
    // RECORD -711: its side and height also persist across restarts (the owner: "have the map icon bar
    // persist between restarts so left handed users don't need to change it every time they open the
    // app", "Side and height (Recommended)"). The holder waits for uiState.mapIconClusterPlacement and is
    // not drawn until it has applied it (first effect), so there is no frame on the default side. Every
    // drag that ends is stored (second effect), keyed on the holder's own count of ended drags rather
    // than on the position itself: a write per drag, not per frame of one, and never a write of the
    // default before the read lands (the trap isMapFullscreen's comment above describes).
    val mapIconClusterPosition = rememberMapIconClusterPositionState(awaitingPlacement = true)
    val mapIconClusterDensity = LocalDensity.current
    LaunchedEffect(uiState.mapIconClusterPlacement) {
        uiState.mapIconClusterPlacement?.let { mapIconClusterPosition.applyPlacement(it, mapIconClusterDensity) }
    }
    LaunchedEffect(mapIconClusterPosition.settledCount) {
        if (mapIconClusterPosition.settledCount > 0) {
            onMapIconClusterPlacementChanged(mapIconClusterPosition.placement(mapIconClusterDensity))
        }
    }
    // Part 1 layout fixes, item 4 (planner message 2026-09-28-98, under CLAUDE.md's UX defaults): the
    // camera the user left on the Maps tab, held here for the same reason, since the map and its camera
    // leave composition with the tab. See MapCameraMemory. Session only.
    val mapCameraMemory = remember { MapCameraMemory() }
    // Dispatch 2026-09-29-57, item 8 (amendments -256 and -262): the return request "Open in Journal" leaves, held here for the
    // same reason as the camera. Session only, like the camera: a recreation forgets it and Back does what it did before.
    val mapReturnMemory = remember { MapReturnMemory() }

    // The Maps tab's basemap, one MapMode value (see MapMode's own doc comment for the two-piece
    // service+mode split this replaced). Persisted across restarts since dispatch 2026-09-28-708 (the
    // owner: "have the app remember which map modes you had it on last so we don't have to keep
    // switching to the favorite"): uiState.mapMode is the stored choice, restored by the ViewModel
    // and updated by onMapModeSelected. This comment used to argue the opposite, that the basemap was
    // display-only state the ViewModel had no part in and that persisting it would be speculative;
    // the owner's request is the real need that argument was waiting for.
    //
    // chosenMapMode is the pick made on this screen, applied here as well as sent up. In the app the
    // two agree from the pick on; it is kept so a host that does not wire onMapModeSelected (the test
    // hosts that drive this screen without a ViewModel) still has a picker that changes the map, as
    // every host had before the choice persisted. Until either is known the map is not drawn at all
    // (the gate on mapRenderMode below), so DEFAULT here is never what a restored user sees first.
    var chosenMapMode by remember { mutableStateOf<MapMode?>(null) }
    val mapMode = chosenMapMode ?: uiState.mapMode ?: MapMode.DEFAULT
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
    // Dispatch 2026-09-28-422 (plan task T18): the coordinate format, MGRS or decimal degrees, held
    // here above the tab like the legend's flag, so a format chosen on the Maps tab is the format it
    // shows after leaving the tab and coming back (CLAUDE.md, UX defaults). It was the Maps tab's own
    // `remember`, which leaves composition with the tab, so it reset. Held for the session only,
    // not across a restart (not asked for).
    var showDecimalDegrees by rememberSaveable { mutableStateOf(false) }
    // Dispatch 2026-09-28-677 (data part B): whether the track sheet's "Details" fold is open, held here above every place
    // that sheet opens (Records, a map bubble's Details, an entry report's track row), so it survives within a session
    // (CLAUDE.md, UX defaults). One flag for every track's sheet. Not across a restart (not asked for).
    val trackDetailsFoldOpen = rememberSaveable { mutableStateOf(false) }
    // Dispatch 2026-09-28-430 (plan task T22): whether the user has moved the map away from the
    // navigation view, and the "Return to Route" requests, held here above the tab so that leaving
    // the Maps tab and coming back keeps them (CLAUDE.md, UX defaults; the legend's flag above is
    // held the same way). Cleared when navigation stops, so the next Return starts in the view.
    var navigationFollowBroken by rememberSaveable { mutableStateOf(false) }
    var navigationViewRequestId by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(isNavigating) { if (!isNavigating) navigationFollowBroken = false }
    // Dispatch 2026-09-28-440: the set zoom is applied once per navigation, when it starts: pending
    // from the start until the map reports it applied. Held above the tab, so a tab change does not
    // apply it again; the navigating flag it is set from is saved too, so a configuration change
    // that recomposes this screen mid-navigation is not read as a new start.
    var navigationZoomPending by rememberSaveable { mutableStateOf(false) }
    var navigationWasOn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isNavigating) {
        if (isNavigating && !navigationWasOn) navigationZoomPending = true
        if (!isNavigating) navigationZoomPending = false
        navigationWasOn = isNavigating
    }
    // Dispatch -502: "Navigate" on a waypoint, from the Maps tab's bubble or its sheet, or from Records. The Maps tab comes
    // up with the navigation view. Already navigating (a return, or another waypoint), isNavigating never goes false in
    // between, so there is no new start zoom (the planner's ruling); a view the walker had moved away from is taken back,
    // as "Return to Route" does.
    val startWaypointNavigation: (WaypointNavigationOrigin) -> Unit = { origin ->
        onNavigateToWaypoint?.let { navigate ->
            val wasNavigating = isNavigating
            waypointNavigationOrigin = origin
            navigate(origin.waypointId)
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
            if (wasNavigating && navigationFollowBroken) {
                navigationFollowBroken = false
                navigationViewRequestId++
            }
        }
    }
    // Dispatch -502: ends the waypoint navigation. With [retrace] (Back) the walker is put back where Navigate was tapped,
    // one step, as the owner chose; the X-circle and the HUD's X end it where the walker is. A return it paused picks up
    // again (the ViewModel's doing).
    val endWaypointNavigation: (Boolean) -> Unit = { retrace ->
        val origin = waypointNavigationOrigin
        waypointNavigationOrigin = null
        onStopWaypointNavigation()
        if (retrace) {
            when (origin) {
                null -> Unit
                is WaypointNavigationOrigin.MapBubble, is WaypointNavigationOrigin.MapDetails -> mapWaypointReopen = origin
                is WaypointNavigationOrigin.RecordsRow, is WaypointNavigationOrigin.RecordsDetails -> {
                    journalScreenState.topTab = JournalTopTab.RECORDS
                    compactTab = CompactTab.JOURNAL
                    if (origin is WaypointNavigationOrigin.RecordsDetails) recordsWaypointReopen = origin.waypointId
                }
                // RECORD -890: back to the journal entry whose map Navigate was tapped on. The entry is still the
                // Journal's open one; the bubble is not reopened, as a Records row reopens nothing.
                is WaypointNavigationOrigin.JournalEntryMap -> {
                    journalScreenState.topTab = JournalTopTab.CARTOGRAPHY
                    compactTab = CompactTab.JOURNAL
                }
            }
        }
    }
    // The return control and the HUD's X: a waypoint navigation first, while there is one; otherwise the return, as before.
    val onNavigationControl: () -> Unit = {
        if (navigatingToWaypoint != null) endWaypointNavigation(false) else onToggleReturning()
    }
    // Dispatch -502, the owner's choice at the S22 desk check ("Keep the last line, faded"): the dashed line to the
    // waypoint, and the one drawn last, which nextStraightLine keeps, faded, once the fix is lost. Held here, above the tab
    // switch, so a lost fix does not lose the line by a trip to the Journal; not across a restart, where there is no last
    // line and the first fix draws it (the option the owner chose said so). The owner's "Fade with the HUD" (RECORD -506)
    // fades it from 30 s as well. The clock moves on at each of those two moments, so the line fades then even though
    // no new fix comes to prompt it.
    var waypointStraightLine by remember { mutableStateOf<StraightLine?>(null) }
    val straightLineNow by produceState(currentTime.nowEpochMillis(), uiState.liveFix, currentTime) {
        value = currentTime.nowEpochMillis()
        while (true) {
            val untilNextChange = millisUntilFreshnessChanges(uiState.liveFix, value) ?: break
            delay(untilNextChange)
            value = currentTime.nowEpochMillis()
        }
    }
    val nextWaypointLine = navigatingToWaypoint?.let { nextStraightLine(waypointStraightLine, uiState.liveFix, it, straightLineNow) }
    LaunchedEffect(nextWaypointLine) { waypointStraightLine = nextWaypointLine }
    var forecastCellsShown by remember { mutableStateOf<Map<String, ForecastCellsShown>>(emptyMap()) }
    var forecastZoomedOut by remember { mutableStateOf(false) }
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
                onZoomedOutChanged = { forecastZoomedOut = it },
            )
        }
    }
    val mapRecordsDrawn = uiState.drawnMapRecords(logUiState)
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
        forecastZoomedOut = forecastZoomedOut,
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
        // The cold-launch gate: no style until the Night Maps preference and the stored basemap are
        // both in (colour build C1; dispatch 2026-09-28-708, "Restoring at launch must not flash the
        // wrong basemap first"). The field is named for the first; see MapRenderMode.nightModeLoaded.
        nightModeLoaded = uiState.nightModeMapsLoaded && (uiState.mapMode != null || chosenMapMode != null),
        layers = drawnMapLayers,
        forecast = forecastFeed,
    )
    // Persisted via the ViewModel/DataStore — see AvailabilityUiState.distanceUnit's own doc
    // comment. mapMode above is persisted the same way since dispatch 2026-09-28-708.
    val distanceUnit = uiState.distanceUnit
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
    // Dispatch 2026-09-28-312, items 11 and 12. The rule: a focus handed back by the app's own clearFocus never
    // opens the search dropdown. Up only while one of two clearFocus(force = true) calls is running: the Tools
    // drawer's close just below, and the search dropdown's own close in CompactMainScaffold. Out of touch mode (a
    // hardware keyboard, a D-pad) View.clearFocus hands focus to the first focusable view inside that same call;
    // when that is the search field, its focus callback opened the dropdown: Back could never close the dropdown,
    // and closing Tools opened one nobody asked for, which then took the next Back ahead of a fan or a bubble.
    // The scaffold's two onFieldFocused ignore a focus gain while this is up; a focus the user gives the field
    // opens the dropdown as before. No timer: the hand-back is synchronous (traced: clearOwnerFocus,
    // View.clearFocus, rootViewRequestFocus, AndroidComposeView.requestFocus, onFieldFocused, one call stack).
    // The other two clearFocus calls are outside it on purpose: SearchDropdown's, on its own scroll, runs with
    // the dropdown already open, and ReturnPromptState's runs while an entry is open, when the bar is not composed.
    // A one-element array, not snapshot state: nothing draws from it (TwoStageSwipe's holder is the precedent).
    val appClearFocusInProgress = remember { booleanArrayOf(false) }
    var isDrawerOpen by remember { mutableStateOf(false) }
    // Counts up when the settings-reset snackbar's "Settings" is tapped (RECORD -661): the Tools drawer opens at Settings.
    var openSettingsOnlyRequest by remember { mutableIntStateOf(0) }

    // Stage 2d's routing fix: a one-shot request into JournalTab, set alongside the compactTab
    // switch — see JournalTab's own doc comment, "The map '+' routing bug," for why this exists and
    // why it stays a single-purpose token rather than a shared navigation type. Only JournalTab reads it.
    var pendingJournalDestination by remember { mutableStateOf<PendingJournalDestination?>(null) }
    // M1: the find a PendingJournalDestination.VIEW_FIND request opens, cleared with the request.
    var pendingJournalFindId by remember { mutableStateOf<String?>(null) }
    // J8-4: the day entry a PendingJournalDestination.VIEW_ENTRY request opens, cleared with the request.
    var pendingJournalEntryId by remember { mutableStateOf<String?>(null) }
    // Plan T16: the imported track a PendingJournalDestination.VIEW_IMPORTED_TRACK request opens, cleared with the request.
    var pendingJournalTrackId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isDrawerOpen) {
        if (isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
            // Reset to the Search panel on every close — scrim tap, back button, or a search action
            // that closes the drawer itself — rather than leaving Settings showing the next time the
            // drawer opens. A minor, easily-revisited default: see this task's own notes.
            appClearFocusInProgress[0] = true
            try {
                focusManager.clearFocus(force = true)
            } finally {
                appClearFocusInProgress[0] = false
            }
            keyboardController?.hide()
        }
    }

    // Tapping Done on the restore's loading page goes home (dispatch 2026-09-28-137, item 6): the Maps tab, the drawer
    // closed. The same two writes as "View on Map" above (onViewSpeciesOnMap): both tab states are set unconditionally,
    // since only the one the active layout reads has any effect. Keyed on the request's count, so a tab the person is
    // already on is set again harmlessly and 0, the default, does nothing.
    // A backup notification's tap (dispatch 2026-09-28-153): open Tools, then Settings, at the Backup section. The compact
    // drawer opens over whatever tab is showing.
    LaunchedEffect(openBackupRequest) {
        if (openBackupRequest > 0) {
            isDrawerOpen = true
        }
    }

    // Plan T16: a finished GPX import. Journal > Records > Tracks, the first new track's details open, and
    // the outcome's message (the owner's words for a failure, "Imported N tracks" for several) as a Toast,
    // the way the Journal says its own failures. The Tools drawer closes and fullscreen ends, as leaving the
    // Maps tab ends it (the tab bar's own rule), so the Journal is what shows. A failed import lands on the
    // Tracks list too, where "Import GPX" is.
    LaunchedEffect(gpxImportNotice?.seq) {
        val notice = gpxImportNotice ?: return@LaunchedEffect
        gpxImportMessage(notice.outcome)?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        isDrawerOpen = false
        if (isMapFullscreen) {
            isMapFullscreen = false
            onMapFullscreenChanged(false)
        }
        pendingJournalTrackId = (notice.outcome as? GpxImportOutcome.Imported)?.trackIds?.firstOrNull()
        pendingJournalDestination = PendingJournalDestination.VIEW_IMPORTED_TRACK
        compactTab = CompactTab.JOURNAL
        onGpxImportNoticeShown(notice.seq)
    }

    // RECORD -868 and -871: the Tools drawer's "Offline maps" row. It closes Tools and opens Journal > Records on the
    // Offline maps chip ("Records, on the Offline maps chip (Recommended)"), through the same one-shot request the GPX
    // import above uses, and ends fullscreen for the same reason. It remembers where it was tapped, [toolsReturn], so Back
    // can retrace the way in (the owner: "Reopen Tools, then Seasonal (Recommended)"): the first Back puts the page the
    // user came from back under the drawer and reopens the drawer; the second is the drawer's own Back, which closes it on
    // that page. Once the user moves on inside the Journal, by another chip or tab, the memory is forgotten and Back works
    // as it always has from wherever they are. A second tap on the row while the memory stands keeps the first origin, so
    // Back never returns to Records itself.
    var toolsReturn by remember { mutableStateOf<ToolsOfflineMapsReturn?>(null) }
    // Whether Records has reached the Offline maps chip since the row was tapped: until it has, the request is still on its
    // way (JournalTab consumes it, then RecordsTab selects the chip, a frame or two later), and not being there yet is not
    // the user moving on.
    var toolsReturnArrived by remember { mutableStateOf(false) }
    val openOfflineMapsFromTools: () -> Unit = {
        if (toolsReturn == null) {
            toolsReturn = ToolsOfflineMapsReturn(
                compactTab = compactTab,
                selectedTab = selectedTab,
                journalTopTab = journalScreenState.topTab,
                recordsFilter = journalScreenState.recordsFilter,
                mapFullscreen = isMapFullscreen,
            )
            toolsReturnArrived = false
        }
        isDrawerOpen = false
        if (isMapFullscreen) {
            isMapFullscreen = false
            onMapFullscreenChanged(false)
        }
        pendingJournalDestination = PendingJournalDestination.VIEW_OFFLINE_MAPS
        compactTab = CompactTab.JOURNAL
    }
    LaunchedEffect(toolsReturn != null) {
        if (toolsReturn == null) return@LaunchedEffect
        snapshotFlow { Triple(compactTab, journalScreenState.topTab, journalScreenState.recordsFilter) }.collect { (tab, top, filter) ->
            val onOfflineMaps = tab == CompactTab.JOURNAL && top == JournalTopTab.RECORDS && filter == RecordsSubTab.OFFLINE_MAPS
            if (onOfflineMaps) {
                toolsReturnArrived = true
            } else if (toolsReturnArrived) {
                toolsReturn = null
            }
        }
    }

    LaunchedEffect(returnToMapRequest) {
        if (returnToMapRequest > 0) {
            isDrawerOpen = false
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
        }
    }

    // uiState.searchSerial (RECORD -723): a search run again with the same region, month and filter clears the
    // sightings too, and must fetch them again; see that field's doc comment.
    LaunchedEffect(selectedTab, uiState.region, uiState.selectedMonth, uiState.taxonFilter, uiState.searchSerial) {
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
    // Only isDrawerOpen/isMapFullscreen/compactTab drive this — all three are state of this one tree.
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
    // RECORD -871: Back from Records reached by the Tools drawer's "Offline maps" row (see toolsReturn above). The page the
    // user came from goes back under the drawer, its tab and, when it was the Journal, its top tab and chip, and fullscreen if
    // the map was fullscreen, and the drawer reopens; the next Back is the drawer's own handler, above, which closes it on
    // that page. The Journal's own handlers are off while this is pending (JournalTab's backEnabled, through
    // compactMainScaffold), the same "off rather than outranked" the open drawer uses, so Records' chip step does not take
    // this press. The tab handler below excludes it, so at most one of the two is enabled.
    BackHandler(enabled = !isDrawerOpen && toolsReturn != null) {
        val back = toolsReturn ?: return@BackHandler
        toolsReturn = null
        journalScreenState.topTab = back.journalTopTab
        journalScreenState.recordsFilter = back.recordsFilter
        compactTab = back.compactTab
        selectedTab = back.selectedTab
        if (back.mapFullscreen) {
            isMapFullscreen = true
            onMapFullscreenChanged(true)
        }
        isDrawerOpen = true
    }
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab != CompactTab.MAP && toolsReturn == null) {
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
    // Off while a waypoint is the target: Back has its own step for that, below. With no waypoint chosen it is as it was.
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == CompactTab.MAP && isNavigating && navigatingToWaypoint == null) {
        showExitNavigationPrompt = !showExitNavigationPrompt
    }
    // Dispatch 2026-09-28-502: navigating to a chosen waypoint, "Back from the navigating HUD stops navigating", with no
    // prompt, and goes back to where Navigate was tapped (Amendment 1). The loop above is the return's; it does not apply
    // here. The same exclusions as the handlers above, so what is drawn on top (the drawer, fullscreen, another tab, and
    // the bubbles and menus registered after this) is backed out of first. A return the waypoint overruled picks up again,
    // and the next Back on the bare map is the loop above, untouched.
    BackHandler(enabled = !isDrawerOpen && !isMapFullscreen && compactTab == CompactTab.MAP && navigatingToWaypoint != null) {
        endWaypointNavigation(true)
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

    // Tablets as a big phone (dispatch 2026-09-28-245, amended by -246): this tree is drawn at every
    // window size, and a window that is in landscape, tall or short, gets the phone's landscape
    // layout: a navigation rail on the charger-port edge in place of the bottom bar (see
    // compactMainScaffold's own showRail), and the L. portEdge is only read while showRail is true.
    val isLandscapeWindow = isLandscapeWindow()
    // Map layers L0b, B2: the Maps tab's freshness mechanism. Whenever the Maps tab comes into view
    // (the Maps bottom-nav tab or rail item) the saved records reload and the forecast store is
    // asked again, so a find saved on the Journal tab shows the next time the map is shown.
    val isMapsTabShown = compactTab == CompactTab.MAP
    LaunchedEffect(isMapsTabShown) {
        if (isMapsTabShown) onMapShown()
    }
    // Map chrome at 80% (dispatch 2026-09-28-56 as amended by -58; planner message -77, Q4): raised
    // only on the Maps tab, and follows the tab.
    if (showExitNavigationPrompt) {
        ExitNavigationPrompt(
            onExit = {
                showExitNavigationPrompt = false
                onToggleReturning()
            },
            onKeepNavigating = { showExitNavigationPrompt = false },
            overMap = compactTab == CompactTab.MAP,
        )
    }
    // RECORD -956 ("One should look exactly line the other"): the rail's edge and the search bar's edge are the screen's right and
    // left in landscape, whatever the rotation (mainWindowRailEdgeFor). The names stay portEdge and punchHoleEdge because every
    // site below reads them; they no longer follow the charging port or the camera. Was currentWindowPortEdge() and
    // punchHoleEdgeFor(rotation), which mirrored the screen at ROTATION_270.
    val mainWindowLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val portEdge = mainWindowRailEdgeFor(mainWindowLandscape)
    // Landscape B2 (S1): where the search bar, its filter chip and the icon cluster's default side go: the screen's left (-956).
    val punchHoleEdge = mainWindowSearchEdgeFor(mainWindowLandscape)

    // Workstream L4b-R2: the one wrapped "leaving without answering" callback, hoisted here (rather
    // than declared separately inside compactMainScaffold) so "every in-app exit offers a discard action" stays one fact about one
    // callback shared by every caller — the bottom nav's JournalTab and its own tab-switch
    // handler — rather than N independently-maintained copies.
    // Backgrounding is the deliberate, sole exception: see compactMainScaffold's own
    // DisposableEffect, which calls the *raw* onLeaveLogEntryEditingIncidentally directly, never this
    // wrapper, since there is no window left to show a Snackbar in by the time that fires.
    val logDraftSnackbarHostState = remember { SnackbarHostState() }
    val logDraftSnackbarScope = rememberCoroutineScope()
    // RECORD -660/-661 (the owner: "Snackbar with 'Settings' button"): a corrupt settings file was reset.
    // Cleared before it is shown, and shown on the host's own scope, as the backup launch notice below
    // is, so clearing it (which changes this effect's key) cannot cancel it. It stays until dismissed;
    // "Settings" opens Tools, then Settings, as a backup notification's tap does, without the Backup scroll.
    LaunchedEffect(settingsResetNoticePending) {
        if (!settingsResetNoticePending) return@LaunchedEffect
        onSettingsResetNoticeShown()
        logDraftSnackbarScope.launch {
            val result = logDraftSnackbarHostState.showSnackbar(
                message = SETTINGS_RESET_MESSAGE,
                actionLabel = SETTINGS_RESET_ACTION_LABEL,
                withDismissAction = true,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                openSettingsOnlyRequest++
                isDrawerOpen = true
            }
        }
    }
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
    val promptContext = LocalContext.current
    LaunchedEffect(backgroundRunPrompt?.id) {
        backgroundRunPrompt?.let { prompt ->
            val result = logDraftSnackbarHostState.showSnackbar(TappableNoticeVisuals(prompt.message))
            if (result == SnackbarResult.ActionPerformed) launchAppDetailsSettings(promptContext)
        }
    }
    LaunchedEffect(networkFixesNotice?.id) {
        networkFixesNotice?.let { notice ->
            logDraftSnackbarHostState.showSnackbar(message = notice.message, duration = SnackbarDuration.Long)
        }
    }
    // Dispatch -502, Amendment 1: the waypoint being navigated to was deleted, while navigating (its pending delete hides it
    // at once) or while the app was closed. The navigation ends and says so, in this same host, not a silent drop. Shown on
    // the host's own scope, as the backup notice is: ending the navigation changes this effect's key and would cancel a
    // snackbar shown from inside it.
    val waypointTargetGone = waypointTarget is WaypointNavigationTarget.Gone
    LaunchedEffect(waypointTargetGone) {
        if (waypointTargetGone) {
            endWaypointNavigation(false)
            logDraftSnackbarScope.launch { logDraftSnackbarHostState.showSnackbar(message = WAYPOINT_NAVIGATION_GONE_TEXT, duration = SnackbarDuration.Long) }
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
    // Item 8: leaving the find that was opened from the Maps tab with Back, its own arrow, or a delete returns to the Maps tab,
    // where the bubble (and the fan) reopen; any other way of leaving it (another bottom tab, an edit, another record) forgets
    // the origin, and Back does what it did before. The origin is forgotten on a tab change here; the edit and the other
    // record are forgotten where they happen (the callbacks below and `onOpenEntry`).
    LaunchedEffect(compactTab) {
        if (compactTab != CompactTab.JOURNAL) mapReturnMemory.forget()
    }
    val onFindReportClosed: (String?) -> Unit = { closing ->
        if (closing != null && mapReturnMemory.onFindClosed(closing)) {
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
        }
    }
    // Dispatch 2026-09-28-387, Part B: an entry opened from a bubble's "kept in" line returns to the Maps tab, with that bubble and the fan, when it is left from its
    // report; the Maps tab it comes back to is built with them (MapReturnMemory.takeEntryBubble, takeFanKeys).
    val onEntryClosed: (String, Boolean) -> Unit = { entryId, fromReport ->
        if (mapReturnMemory.onEntryClosed(entryId, fromReport)) {
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
        }
    }
    val onFindDeleted: (String) -> Unit = { deleted ->
        if (mapReturnMemory.onFindDeleted(deleted)) {
            compactTab = CompactTab.MAP
            selectedTab = ResultsTab.MAP
        }
    }
    // Declared after leaveLogEntryEditingOfferingDiscard, which its onOpenFind calls (F3).
    // M1 (B3, B4): what the Maps tab's glyph bubbles look records up in, and the J5c sheet's inputs,
    // all already in hand here. A find's "Open in Journal" opens the find in its report over whatever
    // the Journal was showing (PendingJournalDestination.VIEW_FIND), so the saved Records chip and top
    // tab are never changed (planner's ruling on F3, continuation 2026-09-28-30). The
    // Journal tab comes up.
    // Checked against the Maps search-bar gate (AvailabilityCompactScaffold's isEditingJournalEntry):
    // the find opens as the Journal tab comes up, where that gate hides the header as it does for any
    // open find; the Maps tab's own bar is gated on the Journal showing, so it is unaffected.
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
            compactTab = CompactTab.JOURNAL
        },
        openFindLabel = OPEN_IN_JOURNAL_LABEL,
        // J8-4: a highlighted record's keeping entries, while the Layers sheet's "Journal entries"
        // switch shows the highlights; with it off no record is highlighted, so no bubble names one.
        journalEntriesKeeping = if (drawnMapLayers.stateOf(JOURNAL_ENTRIES_SWITCH_LAYER_ID).visible) journalHighlights.keptIn else emptyMap(),
        // J8-4, the owner's Q1 ruling ("Open in Journal, prompt first (Recommended)"): switches to the
        // Journal with the
        // entry in its report on Entries, the one top-tab change opening it requires; the saved Records
        // chip is untouched. The Journal side (CartographyScreen's openEntryRequest) asks the existing
        // "Save your changes?" first when another entry is open in its editor with unsaved changes, and
        // just closes an unchanged one. A find kept open on the Journal is left first through the one
        // wrapper, as M1's find route does (F3), since it would otherwise show over the entry.
        onOpenEntry = { entryId ->
            if (logUiState.editingEntry != null) leaveLogEntryEditingOfferingDiscard()
            // Not forgotten any more (dispatch 2026-09-28-387, Part B): the bubble the line was tapped in has just remembered the way back.
            pendingJournalEntryId = entryId
            pendingJournalDestination = PendingJournalDestination.VIEW_ENTRY
            compactTab = CompactTab.JOURNAL
        },
        // Dispatch -502: the Maps tab's bubbles and the sheet opened from them offer Navigate; an entry map's do not.
        onNavigateToWaypoint = onNavigateToWaypoint?.let { startWaypointNavigation },
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
            appClearFocusInProgress = appClearFocusInProgress,
            keyboardController = keyboardController,
            compactTab = { compactTab },
            logUiState = logUiState,
            logPhotoAcquisitionInFlight = { logPhotoAcquisitionInFlight },
            cartographyUiState = cartographyUiState,
            isDrawerOpen = { isDrawerOpen },
            toolsReturnPending = { toolsReturn != null },
            isLandscapeWindow = isLandscapeWindow,
            portEdge = portEdge,
            punchHoleEdge = punchHoleEdge,
            logDraftSnackbarHostState = logDraftSnackbarHostState,
            uiState = uiState,
            distanceUnit = distanceUnit,
            pendingJournalDestination = { pendingJournalDestination },
            pendingJournalTrackId = { pendingJournalTrackId },
            onGpxFilePicked = onGpxFilePicked,
            currentTime = currentTime,
            mapSlot = mapSlot,
            mapIconClusterPosition = mapIconClusterPosition,
            mapCameraMemory = mapCameraMemory,
            mapReturnMemory = mapReturnMemory,
            onFindReportClosed = onFindReportClosed,
            onEntryClosed = onEntryClosed,
            onFindDeleted = onFindDeleted,
            onFindEditStarted = mapReturnMemory::forget,
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
            isArrivalHeld = isArrivalHeld,
            isOffTrack = isOffTrack,
            recordingSundownLine = recordingSundownLine,
            // Dispatch 2026-09-28-645: the map's quick settings. Its sundown and off-track rows are
            // the Tools drawer's own values and callbacks (the same two classes its Sundown section is given below).
            quickSettings = MapQuickSettings(
                isRecording = isRecording,
                backBy = recordingBackBy,
                onBackByChoice = onBackByChoice,
                onClearBackBy = onClearBackBy,
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
                batterySaver = BatterySaverSettings(
                    enabled = uiState.batterySaverEnabled,
                    onEnabledChanged = onBatterySaverChanged,
                ),
                currentTime = currentTime,
            ),
            compassProvider = compassProvider,
            computeTrueHeading = computeTrueHeading,
            navigationTarget = target,
            returnRoute = returnRoute,
            routeLine = routeLine,
            onRetryRoute = onRetryRoute,
            waypointNavigate = WaypointNavigateControls(
                isNavigatingToWaypoint = navigatingToWaypoint != null,
                straightLine = nextWaypointLine,
                onNavigate = onNavigateToWaypoint?.let { startWaypointNavigation },
                mapReopen = mapWaypointReopen,
                onMapReopenConsumed = { mapWaypointReopen = null },
                recordsReopenDetails = recordsWaypointReopen,
                onRecordsReopenConsumed = { recordsWaypointReopen = null },
            ),
            showDecimalDegrees = showDecimalDegrees,
            onToggleCoordinateFormat = { showDecimalDegrees = !showDecimalDegrees },
            navigationFollowing = !navigationFollowBroken,
            navigationViewRequestId = navigationViewRequestId,
            onLeftNavigationView = { navigationFollowBroken = true },
            onReturnToRoute = {
                navigationFollowBroken = false
                navigationViewRequestId++
            },
            navigationZoomPending = navigationZoomPending,
            onNavigationZoomApplied = { navigationZoomPending = false },
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
                    pendingJournalTrackId = null
                }
            },
            pendingJournalFindId = { pendingJournalFindId },
            pendingJournalEntryId = { pendingJournalEntryId },
            onSetCartographyEntryShownOnMap = onSetCartographyEntryShownOnMap,
            mapBubbleSources = mapBubbleSources,
            onStartLogEntry = onStartLogEntry,
            onViewSpeciesOnMap = onViewSpeciesOnMap,
            onMapModeChange = {
                chosenMapMode = it
                onMapModeSelected(it)
            },
            onPlaceTripPin = onPlaceTripPin,
            onToggleRecording = onToggleRecording,
            onDropWaypoint = onDropWaypoint,
            // Dispatch -502: the return control and the HUD's X end a waypoint navigation first, while there is one.
            onToggleReturning = onNavigationControl,
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
            onSetEntryGroupIncluded = onSetEntryGroupIncluded,
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
            // J4b L1: a find tile's long-press Edit uses the one open-and-edit call.
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
            onClearSearch = onClearSearch,
            onRadiusChanged = onRadiusChanged,
            onMonthSelected = onMonthSelected,
            journalScreenState = journalScreenState,
            cartographyEntryModeState = cartographyEntryModeState,
            cartographyOpenEntryGroupsState = cartographyOpenEntryGroupsState,
            findEntryModeState = findEntryModeState,
            findOverViewState = findOverViewState,
        )
    }


    // The phone tree at every window size (dispatch 2026-09-28-245): a tablet is a big phone, so the tree does not
    // depend on the width. Landscape windows get the phone's landscape layout (the rail, the L), see isLandscapeWindow.
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
    val drawerDirection = if (isLandscapeWindow) {
        if (portEdge == ScreenEdge.Right) LayoutDirection.Rtl else LayoutDirection.Ltr
    } else {
        ambientDirection
    }
    // Where the controls over the map are, for the marker fan-out to keep clear of (MapKeepOut.kt).
    val mapKeepOuts = remember { MapKeepOuts() }
    // Ruling A (continuation 2026-09-28-432): every map under this screen can point its puck to true
    // north, the pickers included, with no new parameter through the Journal (the planner's ruling (i)).
    val liveFixForMaps = rememberUpdatedState(uiState.liveFix)
    // Dispatch 2026-09-28-510: true north is worked out at the best position there is, approximate or
    // last known included (AvailabilityUiState.headingFix), so the puck's heading does not wait for GPS.
    val headingFixForMaps = rememberUpdatedState(uiState.headingFix)
    val mapCompass = remember(compassProvider, computeTrueHeading, headingFixForMaps) { MapCompass(compassProvider, computeTrueHeading, headingFixForMaps) }
    // Dispatch 2026-09-28-510 (the owner: "App feeds the dot"): the position every map under this screen
    // draws its dot at, the same way the compass reaches them.
    val shownPosition = rememberShownPosition(uiState.liveFix, uiState.approximateFix, uiState.lastKnownFix, currentTime)
    val mapPosition = remember(shownPosition, liveFixForMaps) { MapPosition(shownPosition, liveFixForMaps) }
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
        // closed it stays false, so swipe-to-open stays off. See the pre-build report above.
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
            val drawerColor = mapChromeFill(navigationBarContainerColor(), compactTab == CompactTab.MAP)
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
                    onClose = { isDrawerOpen = false },
                    onDeletePlannedTrip = onDeletePlannedTrip,
                    isNightMode = isNightMode,
                    onNightModeMapsChanged = onNightModeMapsChanged,
                    themeMode = uiState.themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    backup = backup,
                    openSettingsRequest = openBackupRequest,
                    openSettingsOnlyRequest = openSettingsOnlyRequest,
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
    // The in-app camera, once, after the tree above. Its own open flag lives in InAppCameraViewModel,
    // which survives a recreation. See InAppCameraHost for both mechanisms and the owner's decision.
    // Until dispatch 2026-09-28-245 a window crossing 600 dp flipped between two trees, which is why
    // it stayed outside them; there is one tree now, and the placement (after it) is kept for the
    // reason below.
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
        gridMode = cameraGridMode,
        onGridModeChanged = onCameraGridModeChanged,
        cameraFormat = cameraFormat,
        onCameraFormatChanged = onCameraFormatChanged,
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

/**
 * Where the Tools drawer's "Offline maps" row was tapped (RECORD -871): the bottom-nav tab and its results tab, the
 * Journal's top tab and Records chip as they were, and whether the map was fullscreen. Back from Records puts these
 * back under the reopened drawer; see `toolsReturn` in [AvailabilityScreen].
 */
private data class ToolsOfflineMapsReturn(
    val compactTab: CompactTab,
    val selectedTab: ResultsTab,
    val journalTopTab: JournalTopTab,
    val recordsFilter: RecordsSubTab,
    val mapFullscreen: Boolean,
)

/**
 * Dispatch 2026-09-28-502, Amendment 1: what the walker is told when the waypoint they are navigating to
 * was deleted, meanwhile or while the app was closed. Provisional words, accepted by the planner.
 */
internal const val WAYPOINT_NAVIGATION_GONE_TEXT = "Stopped navigating: that waypoint was deleted."
