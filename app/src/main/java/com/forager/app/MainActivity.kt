package com.forager.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.forager.app.domain.ErrorLog
import com.forager.app.domain.model.AppThemeMode
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.TrackRecordingMode
import com.forager.app.service.TrackRecordingService
import com.forager.app.ui.availability.AvailabilityScreen
import com.forager.app.ui.availability.AvailabilityViewModel
import com.forager.app.ui.log.MushroomLogViewModel
import com.forager.app.ui.theme.ForagerTheme
import com.forager.app.ui.track.TrackRecordingViewModel

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as ForagerApplication).container

    /**
     * The real [ErrorLog] production wires in — see that interface's own doc comment for why
     * [AvailabilityViewModel]/[TrackRecordingViewModel] don't call [Log] directly.
     */
    private val androidErrorLog = ErrorLog { tag, message, error -> Log.w(tag, message, error) }

    private val viewModel: AvailabilityViewModel by viewModels {
        viewModelFactory {
            initializer {
                AvailabilityViewModel(
                    container.locationProvider,
                    container.locationTracker,
                    container.getAvailabilityUseCase,
                    container.getRecentSearchesUseCase,
                    container.getSightingsUseCase,
                    container.searchTaxaUseCase,
                    container.getConditionsUseCase,
                    container.clusterForagingAreasUseCase,
                    container.getTripWindowsUseCase,
                    container.getPlannedTripsUseCase,
                    container.savePlannedTripUseCase,
                    container.deletePlannedTripUseCase,
                    container.getSeasonalPatternUseCase,
                    container.offlineMapRepository,
                    androidErrorLog,
                    container.mapPreferencesRepository,
                    container.distanceUnitPreferenceRepository,
                    container.appThemePreferenceRepository,
                )
            }
        }
    }

    private val mushroomLogViewModel: MushroomLogViewModel by viewModels {
        viewModelFactory {
            initializer {
                MushroomLogViewModel(
                    container.getMushroomLogEntriesUseCase,
                    container.getDraftEntriesUseCase,
                    container.createMushroomLogEntryUseCase,
                    container.startEditingLogEntryUseCase,
                    container.saveMushroomLogEntryUseCase,
                    container.commitDraftEntryUseCase,
                    container.deleteMushroomLogEntryUseCase,
                    container.addPhotoToLogEntryUseCase,
                    container.removePhotoFromLogEntryUseCase,
                    container.getGalleryPhotosUseCase,
                    container.pullPhotoIntoEntryUseCase,
                    container.deleteGalleryPhotoUseCase,
                )
            }
        }
    }

    private val trackRecordingViewModel: TrackRecordingViewModel by viewModels {
        viewModelFactory {
            initializer {
                TrackRecordingViewModel(
                    container.trackRepository,
                    container.startTrackUseCase,
                    container.getWaypointsUseCase,
                    container.createWaypointUseCase,
                    container.deleteWaypointUseCase,
                    container.computeReturnToStartUseCase,
                    container.detectOffTrackUseCase,
                    container.locationTracker,
                    container.getTracksUseCase,
                    androidErrorLog,
                )
            }
        }
    }

    /**
     * Android 13+ only shows a foreground service's notification with this permission granted;
     * without it the service still runs (recording isn't blocked), it just runs silently. Requested
     * once, right when a recording actually starts, rather than at app launch — there's nothing to
     * show a notification for until then. A denial doesn't stop the recording: see
     * [TrackRecordingService]'s own notification, which the OS simply won't display if this was
     * never granted.
     */
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* No follow-up either way — see this property's own doc comment. */ }

    /**
     * Which action the OS permission dialog is standing in front of — [requestLocationPermission]
     * is one shared launcher (Android only allows one in-flight request per contract per Activity),
     * so this is how its grant/deny callback below routes to the right [viewModel] method. Set
     * immediately before every `launch` call site.
     */
    private var pendingLocationAction = PendingLocationAction.USE_CURRENT_LOCATION

    private enum class PendingLocationAction { USE_CURRENT_LOCATION, LOCATE_ME }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        when (pendingLocationAction) {
            PendingLocationAction.USE_CURRENT_LOCATION ->
                if (granted) viewModel.useCurrentLocation() else viewModel.onPermissionDenied()
            PendingLocationAction.LOCATE_ME ->
                if (granted) viewModel.locateMe() else viewModel.onLocateMePermissionDenied()
        }
    }

    /**
     * Same check, same two permissions, as
     * [com.forager.app.location.AndroidLocationProvider.hasLocationPermission] — not shared code
     * across an Activity/domain-layer boundary that owns neither Context nor Manifest, matching
     * that class's own doc comment on why (see also [TrackRecordingService]'s own copy, and
     * `com.forager.app.ui.map.SightingsMap.kt`'s).
     *
     * Two call sites below both gate on this rather than trusting a single check: this one, right
     * before [TrackRecordingViewModel.startRecording] is called at all (the confirmed crash's
     * fix — recording never begins without permission, so [TrackRecordingUiState.activeTrack]
     * never gets set), and a second inside the `LaunchedEffect` that actually issues
     * `startForegroundService` (defence against permission being revoked in the narrow window
     * between the two — that path also rolls the ViewModel's state back if it fires, so
     * `isRecording` can never report true for a service that didn't actually start).
     */
    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Field-test dispatch item 4: a dedicated channel, not TrackRecordingService's own
        // "track_recording" one — that channel is IMPORTANCE_LOW on purpose (an ongoing, silent
        // "recording is running" notice), and a LOW-importance channel won't sound or vibrate a
        // posted notification on its own regardless of what the notification itself requests.
        // off_track_notification_channel_name/_title/_text already existed in strings.xml, unused
        // anywhere in the tree — this wires up exactly the channel they were named for rather than
        // inventing new copy.
        createOffTrackNotificationChannel(this)
        setContent {
            // Read before ForagerTheme wraps content, not inside it: themeMode is this state's own
            // AvailabilityUiState.themeMode (Settings' Light/Dark/System Default choice), so
            // ForagerTheme needs the resolved boolean below rather than the other way around.
            val uiState by viewModel.uiState.collectAsState()
            // AppThemeMode.SYSTEM_DEFAULT is the one choice this app doesn't store as an explicit
            // light/dark value — it means "follow the device" — and isSystemInDarkTheme() is a
            // @Composable-only signal (backed by LocalConfiguration), so this resolution has to
            // happen here, not in the ViewModel or domain/. AppThemeMode.LIGHT/DARK stay direct,
            // device-independent choices either way.
            val systemInDarkTheme = isSystemInDarkTheme()
            val effectiveDarkTheme = when (uiState.themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM_DEFAULT -> systemInDarkTheme
            }
            // enableEdgeToEdge() makes the system bars transparent and lets this app's own
            // background show through underneath them instead of a separately-colored system bar —
            // called here, keyed on effectiveDarkTheme via SideEffect (a synchronous per-recomposition
            // effect, not LaunchedEffect: this call isn't suspending), rather than once with no
            // arguments in onCreate() as before.
            //
            // The no-arg call this replaces picks each bar's icon appearance from the *device's*
            // system dark-mode resource qualifier at the single moment it runs (SystemBarStyle.auto's
            // own default detectDarkMode) — not from this app's own resolved theme, which can also
            // change at runtime (a Light/Dark/System Default switch, or the device's own theme
            // changing while System Default is selected) without recreating this Activity.
            // LocalForagerDarkTheme's own doc comment already names this exact mistake for a
            // different set of call sites (map screen colors reading the device theme instead of
            // this state) as a real, previously-hit bug; this is the same mistake in the system bar
            // icon appearance. A device in system dark mode with this app's own theme set to Light
            // produces exactly the "status bar is white and washes out the notifications" hardware
            // report this fixes: a light (effectiveDarkTheme == false) app background under status
            // bar icons chosen for a dark background (light/white icons, from the device's dark
            // system setting) — invisible against white.
            SideEffect {
                val statusBarStyle = if (effectiveDarkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = statusBarStyle, navigationBarStyle = statusBarStyle)
            }
            ForagerTheme(darkTheme = effectiveDarkTheme) {
                val logUiState by mushroomLogViewModel.uiState.collectAsState()
                val trackUiState by trackRecordingViewModel.uiState.collectAsState()

                // Starts/stops the actual foreground service as a side effect of
                // TrackRecordingViewModel's own state, mirroring the locateMeStatus LaunchedEffect
                // below — the ViewModel owns the Track row (via StartTrackUseCase), this Activity
                // owns the Context-level action of running the service. hasStartedRecordingOnce
                // guards against firing a spurious ACTION_STOP on first composition, when
                // activeTrack is null simply because nothing has ever started yet.
                var hasStartedRecordingOnce by remember { mutableStateOf(false) }
                LaunchedEffect(trackUiState.activeTrack) {
                    val active = trackUiState.activeTrack
                    val intent = Intent(this@MainActivity, TrackRecordingService::class.java)
                    if (active != null) {
                        // Re-checked here, not just in onToggleRecording below: this is the exact
                        // call that would otherwise reproduce the confirmed FGS-location-type
                        // crash, and it runs asynchronously after that first check — see
                        // hasLocationPermission()'s own doc comment on why both exist. Rolling
                        // back through the ViewModel (rather than only skipping the service start)
                        // is what keeps isRecording from reporting true for a service that never
                        // actually started.
                        if (hasLocationPermission()) {
                            hasStartedRecordingOnce = true
                            intent.action = TrackRecordingService.ACTION_START
                            intent.putExtra(TrackRecordingService.EXTRA_TRACK_ID, active.trackId)
                            intent.putExtra(TrackRecordingService.EXTRA_MODE, active.mode.name)
                            ContextCompat.startForegroundService(this@MainActivity, intent)
                        } else {
                            trackRecordingViewModel.onStartRecordingPermissionDenied(
                                getString(R.string.track_recording_needs_location),
                            )
                            trackRecordingViewModel.stopRecording()
                        }
                    } else if (hasStartedRecordingOnce) {
                        intent.action = TrackRecordingService.ACTION_STOP
                        startService(intent)
                    }
                }

                // Field-test dispatch item 4: DetectOffTrackUseCase's own output used to reach a
                // user nowhere but an icon tint — see TrackRecordingViewModel.returnToStart()'s own
                // doc comment for the debounce this id reflects. offTrackAlertId starts at 0, which
                // this LaunchedEffect's own first firing (on initial composition) must not treat as
                // a real alert — 0 is never itself bumped to by returnToStart().
                LaunchedEffect(trackUiState.offTrackAlertId) {
                    if (trackUiState.offTrackAlertId == 0) return@LaunchedEffect
                    postOffTrackAlert(this@MainActivity)
                    vibrateOffTrackAlert(this@MainActivity)
                }

                AvailabilityScreen(
                    uiState = uiState,
                    onUseCurrentLocation = {
                        pendingLocationAction = PendingLocationAction.USE_CURRENT_LOCATION
                        requestLocationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onLocateMe = {
                        pendingLocationAction = PendingLocationAction.LOCATE_ME
                        requestLocationPermission.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    onManualLatChanged = viewModel::onManualLatChanged,
                    onManualLngChanged = viewModel::onManualLngChanged,
                    onSearchManualCoordinates = viewModel::searchManualCoordinates,
                    onRadiusChanged = viewModel::onRadiusChanged,
                    onMonthSelected = viewModel::onMonthSelected,
                    onMapTabSelected = viewModel::onMapTabSelected,
                    onSeasonalTabSelected = viewModel::onSeasonalTabSelected,
                    onToggleForagingAreas = viewModel::onToggleForagingAreas,
                    onCategorySelected = viewModel::onCategorySelected,
                    onTaxonSearchQueryChanged = viewModel::onTaxonSearchQueryChanged,
                    onTaxonSearchResultSelected = viewModel::onTaxonSearchResultSelected,
                    onDismissTaxonSuggestions = viewModel::onDismissTaxonSuggestions,
                    onReopenTaxonSuggestions = viewModel::onReopenTaxonSuggestions,
                    onPlaceTripPin = viewModel::onPlaceTripPin,
                    onDeletePlannedTrip = viewModel::onDeletePlannedTrip,
                    onRecentSearchSelected = viewModel::onRecentSearchSelected,
                    currentTime = container.currentTimeProvider,
                    onOfflineMapLatChanged = viewModel::onOfflineMapLatChanged,
                    onOfflineMapLngChanged = viewModel::onOfflineMapLngChanged,
                    onOfflineMapRadiusChanged = viewModel::onOfflineMapRadiusChanged,
                    onOfflineMapNameChanged = viewModel::onOfflineMapNameChanged,
                    onOfflineMapsOpened = viewModel::onOfflineMapsOpened,
                    onDownloadOfflineMaps = viewModel::onDownloadOfflineMaps,
                    onDeleteOfflineRegion = viewModel::onDeleteOfflineRegion,
                    onDistanceUnitSelected = viewModel::onDistanceUnitSelected,
                    onNightModeMapsChanged = viewModel::onNightModeMapsChanged,
                    onThemeModeChanged = viewModel::onThemeModeChanged,
                    logUiState = logUiState,
                    cameraCaptureFiles = container.cameraCaptureFiles,
                    onStartLogEntry = mushroomLogViewModel::onStartNewEntry,
                    onOpenLogEntry = mushroomLogViewModel::onOpenEntry,
                    onCloseLogEntry = mushroomLogViewModel::onCloseEntry,
                    onLogEntryChanged = mushroomLogViewModel::onEntryEdited,
                    onStartEditingLogEntry = mushroomLogViewModel::onStartEditingEntry,
                    onOpenLogEntryForEditing = mushroomLogViewModel::onOpenEntryForEditing,
                    onSaveLogEntry = mushroomLogViewModel::onSaveEntry,
                    onCancelLogEntryEditing = mushroomLogViewModel::onCancelEditing,
                    onLeaveLogEntryEditingIncidentally = mushroomLogViewModel::onLeaveEditingIncidentally,
                    onDiscardLogDraft = mushroomLogViewModel::onDeleteEntry,
                    onAddLogPhoto = mushroomLogViewModel::onAddPhoto,
                    onRemoveLogPhoto = mushroomLogViewModel::onRemovePhoto,
                    onPullLogPhoto = mushroomLogViewModel::onPullPhoto,
                    onDeleteLogEntry = mushroomLogViewModel::onDeleteEntry,
                    onDeleteGalleryPhoto = mushroomLogViewModel::onDeleteGalleryPhoto,
                    onSaveLogErrorDismissed = mushroomLogViewModel::onSaveErrorDismissed,
                    isRecording = trackUiState.isRecording,
                    onToggleRecording = {
                        if (trackUiState.isRecording) {
                            trackRecordingViewModel.stopRecording()
                        } else if (!hasLocationPermission()) {
                            // Confirmed crash's primary fix: never even ask the ViewModel to start
                            // (never creates the Track row, never sets activeTrack) when the
                            // foreground service could not possibly start without crashing — see
                            // hasLocationPermission()'s own doc comment.
                            trackRecordingViewModel.onStartRecordingPermissionDenied(
                                getString(R.string.track_recording_needs_location),
                            )
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            // Field-test dispatch item 3: BALANCED's 15s/15m gate produced tracks
                            // too sparse to evaluate against Gaia's own recording. Testers already
                            // run Gaia concurrently, so the extra GPS draw is a cost already
                            // committed either way — see startRecording()'s own default-parameter
                            // doc comment for why this is an explicit override here rather than a
                            // changed default: a future non-tester-build caller should still get
                            // BALANCED unless it deliberately asks otherwise.
                            trackRecordingViewModel.startRecording(TrackRecordingMode.HIGH_ACCURACY)
                        }
                    },
                    startRecordingErrorMessage = trackUiState.startRecordingErrorMessage,
                    breadcrumbPoints = trackUiState.breadcrumbPoints.map { LatLng(it.lat, it.lng) },
                    waypoints = trackUiState.waypoints,
                    waypointsErrorMessage = trackUiState.waypointsErrorMessage,
                    onDropWaypoint = { location, name -> trackRecordingViewModel.addWaypoint(location.lat, location.lng, name) },
                    onDeleteWaypoint = trackRecordingViewModel::removeWaypoint,
                    returnToStart = trackUiState.returnToStart,
                    isReturning = trackUiState.isReturning,
                    isOffTrack = trackUiState.isOffTrack,
                    onToggleReturning = {
                        if (trackUiState.isReturning) trackRecordingViewModel.stopReturn() else trackRecordingViewModel.startReturn()
                    },
                    compassProvider = container.compassProvider,
                    crashFileStore = container.crashFileStore,
                    tracks = trackUiState.tracks,
                    onTracksOpened = trackRecordingViewModel::loadTracks,
                )
            }
        }
    }
}

private const val OFF_TRACK_CHANNEL_ID = "off_track_alert"
private const val OFF_TRACK_NOTIFICATION_ID = 1002

/** Two short buzzes, not one — more likely to be felt through fabric than a single pulse, still brief enough not to feel alarmist. */
private val OFF_TRACK_VIBRATION_PATTERN_MILLIS = longArrayOf(0L, 250L, 150L, 250L)

internal fun createOffTrackNotificationChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val channel = NotificationChannel(
        OFF_TRACK_CHANNEL_ID,
        context.getString(R.string.off_track_notification_channel_name),
        // HIGH, not TrackRecordingService's own LOW — this is a safety alert meant to be noticed
        // on a pocketed phone, not a silent ongoing-status notice.
        NotificationManager.IMPORTANCE_HIGH,
    ).apply { enableVibration(true) }
    manager.createNotificationChannel(channel)
}

/**
 * Field-test dispatch item 4 — see [com.forager.app.ui.track.TrackRecordingViewModel.returnToStart]'s
 * own doc comment for the debounce that decides when this gets called at all. Split out as a plain,
 * `Context`-taking top-level function — not a private `MainActivity` method — the same
 * `directionsIntent`/`launchDirections` split `com.forager.app.ui.availability` uses, so the real
 * notification this builds is testable under Robolectric without needing this app's full DI graph.
 *
 * Posting is best-effort: same "declared, not forced" stance [com.forager.app.service.TrackRecordingService]'s
 * own ongoing notification takes on POST_NOTIFICATIONS (see AndroidManifest.xml's own comment) — a
 * denial here means no notification shows, not a crash, and [vibrateOffTrackAlert] (a different,
 * VIBRATE-gated permission) still runs regardless.
 */
internal fun postOffTrackAlert(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val notification = NotificationCompat.Builder(context, OFF_TRACK_CHANNEL_ID)
        .setContentTitle(context.getString(R.string.off_track_notification_title))
        .setContentText(context.getString(R.string.off_track_notification_text))
        .setSmallIcon(R.drawable.ic_track_recording)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .build()
    NotificationManagerCompat.from(context).notify(OFF_TRACK_NOTIFICATION_ID, notification)
}

/** VIBRATE is a normal (install-time) permission — declared in AndroidManifest.xml, no runtime check needed, unlike [postOffTrackAlert]'s own POST_NOTIFICATIONS gate. */
internal fun vibrateOffTrackAlert(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createWaveform(OFF_TRACK_VIBRATION_PATTERN_MILLIS, -1))
}
