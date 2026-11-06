# Archive: "Lock camera to portrait", and the camera gear panel's two rows

Removed by dispatch 2026-10-13-03 (RECORD -968, -970, -971, -972), in commit `1e13df05` on branch
`remove-portrait-lock`, from base `497423d6` (`origin/main`). Every code block below is verbatim from
`497423d6`, with its path and line numbers there. Written 2026-10-13.

## The owner's words

- On the option (RECORD -968): "The bug is in the camera's settings. Tapping the lock to portrait mode
  from the settings freezes the camera. I'm wondering the use of that feature. Seems pretty useless to
  be honest." Then: "Go ahead and remove it. But keep the code used in a document in case people want
  it back."
- On the freeze's cause, once found: "My calls as that the camera mode didn't switch over properly. It
  didn't switch over at all. Good catch."
- On the gear panel (RECORD -971): "Don't remove the settings yet". Then (RECORD -972): "Remove save
  location from the settings, empty it out completely".

## Why it was removed

The owner judged the option of no use, and it froze the camera when toggled with the camera open. It
reversed decision B1 of `docs/audits/2026-09-21-camera-strip-basics-decisions.md` (a dated note there
says so). The camera now always follows the phone, as it did with the option off, its default
(`DEFAULT_LOCK_CAMERA_TO_PORTRAIT = false`).

The photo-location row went from the gear panel at the owner's word (-972). The value itself is
unchanged: the camera strip's Location chip shows and sets it. Settings has not had that row since
dispatch 2026-09-28-707, so the chip is now the only place to set it. The gear and its panel stay
(-971); the panel says "No camera settings yet".

## The freeze's cause

Traced in the code at `497423d6`: steps 1 to 4 are read, step 5 is inferred. Not reproduced: no phone was
used, and CameraX does not run under Robolectric.

1. The gear panel's row called `AvailabilityViewModel.onLockCameraToPortraitChanged`, which put the new
   value in `uiState`. It reached the camera through `AvailabilityScreen` and `InAppCameraHost`.
2. `InAppCameraHost.kt:66`: `val session = remember(lockToPortrait) { CameraXCaptureSession(context, lockToPortrait) }`.
   A new value made a **new session**.
3. `InAppCameraDialog.kt:187-190`: `DisposableEffect(session, lifecycleOwner) { session.open(...); onDispose { session.close() } }`
   closed the old session and opened the new one, which bound a new CameraX `Preview`.
4. `CameraXCaptureSession.kt:479-495`: the viewfinder is `remember { PreviewView(context) }` with
   `DisposableEffect(view)`, **not keyed on its session** (read). It attaches the surface provider
   of whichever `Preview` exists when that effect first runs, and only then (read).
5. *Inferred, not observed:* the slot's lambda, `{ modifier -> session.Viewfinder(modifier) }`, stays
   at the same place in the composition, so Compose keeps the same `PreviewView` and the same effect
   across the session swap. Its `previewView` and surface provider stay with the old, closed session.
   The new session's `Preview` gets no surface, so the picture stops on its last frame. That matches
   the owner's report ("It didn't switch over at all").

Until dispatch 2026-09-28-707 the option was only in Settings, which the camera covered, so it could
not change while the camera was open. Moving it into the camera's gear panel made the change reachable
while open. The code comment on line 66 had flagged "the rebind on a live change is a device check,
not shown by any test".

The trigger went with the option, so nothing in the kept camera can rebuild a session while it is
open. The planner ruled out hardening the viewfinder in this dispatch.

## How to put it back

1. **Fix the freeze first.** It must not come back with the option. Two ways:
   - key the viewfinder on its session, so a new session gets a new `PreviewView` and its surface;
   - or apply the setting **without** a new session. The window request and the arrangement already
     follow a changing value. The capture rotation can read a value the session is handed, rather than
     one fixed at construction.

   Either way, cover it with a test before the option returns. A fake session can show which session
   the viewfinder slot was composed for. Whether CameraX draws again is a device check.
2. **Use a new DataStore key or a new file.** Installs that had the option on still hold
   `camera.lock_to_portrait = true` in `camera_orientation_preferences`. Nothing reads it any more, and
   the file was deliberately left in place. Restoring the old key would silently turn the lock back on
   for those users.
3. Restore the call sites below. Each path and line number is at `497423d6`:
   - `AppContainer` (the instance);
   - `MainActivity` (the ViewModel wiring and the screen's handler);
   - `AvailabilityViewModel` (parameters, load, handler);
   - `AvailabilityUiState` (the field);
   - `AvailabilityScreen` (the parameter, and the two lines to the host);
   - `InAppCameraHost` (the slot type, the production slot and the host);
   - `InAppCameraDialog` (the parameter, the handler, the window request, the arrangement's key and the
     panel's values);
   - `CameraSettingsPanel` (the row);
   - `WindowOrientation` (`windowOrientationFor`);
   - `CameraArrangement` (the first input);
   - `CameraXCaptureSession` (the constructor parameter and `effectiveDeviceRotation`).
4. Photo orientation: with the lock on, a sideways photo was saved portrait, through the gate on
   `deviceRotation`. `IntendedOrientation.kt` and the capture's tag handling were not touched by the
   removal, and still handle a `ROTATION_0` target.

To bring back the photo-location row, restore `ExplainedSettingsCheckbox` and the two
`PHOTO_LOCATION_SETTING_*` strings in `AvailabilitySettingsUi.kt`. Then restore the row and its two
parameters in `CameraSettingsPanel.kt`, and the two arguments in `InAppCameraDialog`'s
`CameraSettingsPanelLayer` call. No storage change is needed: the value, its key and its ViewModel
handler are unchanged, and the Location chip uses them.

Doc comments that described the option were also edited in `1e13df05`; `git show 1e13df05` has
them. They are not repeated here.

## The removed code

#### The interface

`app/src/main/java/com/zynergylabs/forager/app/domain/CameraOrientationPreferenceRepository.kt`, whole file at `497423d6`. Deleted in `1e13df05`.

```kotlin
package com.zynergylabs.forager.app.domain

/**
 * Settings' "Lock camera to portrait" (owner request, 2026-09-15, from the device check on the
 * window lock). Backed by DataStore, its own file, one repository per concern, the same reasons
 * [PhotoLocationPreferenceRepository] and [SundownPreferencesRepository] record.
 *
 * ## What the one flag does, and what it does not touch
 *
 * **Off** is the behaviour the camera has without the setting: the window is held portrait while
 * the camera is open, the controls turn in place to meet the phone, and a photo taken sideways is
 * saved landscape, from the orientation sensor, regardless of the system auto-rotate setting.
 * **On** freezes both: nothing in the camera turns, and a photo taken sideways is saved portrait.
 *
 * It is one gate, not two behaviours: the control angle and the shot's `targetRotation` both read
 * `CameraCaptureSession.deviceRotation`, and with the setting on that value is pinned to
 * `Surface.ROTATION_0` and the sensor is ignored, so both effects fall out of the one place
 * (`effectiveDeviceRotation`, in `CameraXCaptureSession.kt`). The window's own orientation lock
 * is not this setting's business: the window is held portrait in both states.
 *
 * **Defaults to `false`**, so an install that predates the setting keeps the camera it had. A
 * failed read is logged and leaves the off-by-default state alone, as the other preferences do.
 */
interface CameraOrientationPreferenceRepository {

    /** `false` until the user has ever turned it on. */
    suspend fun getLockCameraToPortrait(): Result<Boolean>

    suspend fun setLockCameraToPortrait(enabled: Boolean): Result<Unit>
}
```

#### The DataStore repository, its key and its default

`app/src/main/java/com/zynergylabs/forager/app/data/repository/DataStoreCameraOrientationPreferenceRepository.kt`, whole file at `497423d6`. Deleted in `1e13df05`.

```kotlin
package com.zynergylabs.forager.app.data.repository

import com.zynergylabs.forager.app.domain.SettingsResetListener
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository
import kotlinx.coroutines.flow.first

/**
 * [CameraOrientationPreferenceRepository] backed by Jetpack DataStore — its own file, built through
 * [PreferenceDataStoreFactory.create] rather than the process-wide delegate, for the Robolectric
 * isolation reason [DataStoreMapPreferencesRepository] records.
 */
class DataStoreCameraOrientationPreferenceRepository(
    context: Context,
    /** Told when this file was corrupt and has been reset (RECORD -660); `AppContainer` passes its notice. */
    settingsReset: SettingsResetListener = SettingsResetListener.None,
) : CameraOrientationPreferenceRepository {

    private val dataStore = settingsDataStore(context, DATA_STORE_NAME, settingsReset)

    override suspend fun getLockCameraToPortrait(): Result<Boolean> = runCatchingCancellable {
        dataStore.data.first()[KEY_LOCK_TO_PORTRAIT] ?: DEFAULT_LOCK_CAMERA_TO_PORTRAIT
    }

    override suspend fun setLockCameraToPortrait(enabled: Boolean): Result<Unit> = runCatchingCancellable {
        dataStore.edit { prefs -> prefs[KEY_LOCK_TO_PORTRAIT] = enabled }
    }

    private companion object {
        const val DATA_STORE_NAME = "camera_orientation_preferences"
        val KEY_LOCK_TO_PORTRAIT = booleanPreferencesKey("camera.lock_to_portrait")
    }
}

/** Off, so an install that predates the setting keeps the camera it had — see [CameraOrientationPreferenceRepository]'s own doc comment. */
const val DEFAULT_LOCK_CAMERA_TO_PORTRAIT = false
```

#### AppContainer: imports

`app/src/main/java/com/zynergylabs/forager/app/AppContainer.kt`, line 29 at `497423d6`.

```kotlin
import com.zynergylabs.forager.app.data.repository.DataStoreCameraOrientationPreferenceRepository
```

#### AppContainer: import

`app/src/main/java/com/zynergylabs/forager/app/AppContainer.kt`, line 132 at `497423d6`.

```kotlin
import com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository
```

#### AppContainer: the instance

`app/src/main/java/com/zynergylabs/forager/app/AppContainer.kt`, line 303 at `497423d6`.

```kotlin
    val cameraOrientationPreferenceRepository: CameraOrientationPreferenceRepository = DataStoreCameraOrientationPreferenceRepository(context, settingsReset = settingsResetNotice)
```

#### MainActivity: the ViewModel wiring

`app/src/main/java/com/zynergylabs/forager/app/MainActivity.kt`, lines 96 to 97 at `497423d6`.

```kotlin
                    getLockCameraToPortrait = container.cameraOrientationPreferenceRepository::getLockCameraToPortrait,
                    setLockCameraToPortrait = container.cameraOrientationPreferenceRepository::setLockCameraToPortrait,
```

#### MainActivity: the handler passed to the screen

`app/src/main/java/com/zynergylabs/forager/app/MainActivity.kt`, line 550 at `497423d6`.

```kotlin
                    onLockCameraToPortraitChanged = viewModel::onLockCameraToPortraitChanged,
```

#### AvailabilityViewModel: constructor parameters

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityViewModel.kt`, lines 127 to 129 at `497423d6`.

```kotlin
    /** Settings' "Lock camera to portrait" — the same borrowed-capability shape as the pair above, defaulted off, the repository's own default. */
    private val getLockCameraToPortrait: suspend () -> Result<Boolean> = { Result.success(false) },
    private val setLockCameraToPortrait: suspend (Boolean) -> Result<Unit> = { Result.success(Unit) },
```

#### AvailabilityViewModel: the load call in init

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityViewModel.kt`, line 244 at `497423d6`.

```kotlin
        loadLockCameraToPortrait()
```

#### AvailabilityViewModel: load and handler

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityViewModel.kt`, lines 1489 to 1511 at `497423d6`. The handler's closing lines follow the last one shown: `            )`, `        }`, `    }`.

```kotlin
    /** Restores "Lock camera to portrait" — same read-failure treatment as [loadAutoSaveLocationToPhotos]: log, and leave the off-by-default state alone. */
    private fun loadLockCameraToPortrait() {
        viewModelScope.launch {
            getLockCameraToPortrait().fold(
                onSuccess = { enabled -> _uiState.update { it.copy(lockCameraToPortrait = enabled) } },
                onFailure = { error -> errorLog.w(TAG, "Couldn't read the camera-orientation preference.", error) },
            )
        }
    }

    /**
     * Settings' "Lock camera to portrait" checkbox. Reflects the value in state immediately and
     * persists in the background, the same shape as [onAutoSaveLocationToPhotosChanged]. The
     * camera reads the value from this state when it opens, through `AvailabilityScreen`'s host,
     * so a change here reaches the next camera session and never a running one — the dialog
     * covers Settings, so there is no running one to reach.
     */
    fun onLockCameraToPortraitChanged(enabled: Boolean) {
        _uiState.update { it.copy(lockCameraToPortrait = enabled) }
        viewModelScope.launch {
            setLockCameraToPortrait(enabled).fold(
                onSuccess = {},
                onFailure = { error -> errorLog.w(TAG, "Couldn't persist the camera-orientation preference.", error) },
```

#### AvailabilityUiState: the field

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityUiState.kt`, lines 264 to 268 at `497423d6`. Preceded by the line `    /**`.

```kotlin
     * Settings' "Lock camera to portrait" — see
     * [com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository] for the one gate it
     * throws. `false` until the stored preference loads, matching that repository's default.
     */
    val lockCameraToPortrait: Boolean = false,
```

#### AvailabilityScreen: the parameter

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreen.kt`, lines 451 to 452 at `497423d6`.

```kotlin
    /** "Lock camera to portrait" — see [AvailabilityUiState.lockCameraToPortrait]; set from the camera's gear panel (Settings until dispatch 2026-09-28-707). Defaulted like the one above. */
    onLockCameraToPortraitChanged: (Boolean) -> Unit = {},
```

#### AvailabilityScreen: passed to the host (1 of 2)

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreen.kt`, line 2029 at `497423d6`.

```kotlin
        lockToPortrait = uiState.lockCameraToPortrait,
```

#### AvailabilityScreen: passed to the host (2 of 2)

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreen.kt`, line 2034 at `497423d6`.

```kotlin
        onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
```

#### AvailabilitySettingsUi: the explained row both panel rows used

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilitySettingsUi.kt`, lines 453 to 465 at `497423d6`. Preceded by `/**`; followed by `        )`, `    }`, `}`.

```kotlin
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
```

#### AvailabilitySettingsUi: the two rows' strings

`app/src/main/java/com/zynergylabs/forager/app/ui/availability/AvailabilitySettingsUi.kt`, lines 490 to 502 at `497423d6`.

```kotlin
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
```

#### The camera's gear panel, with both rows

`app/src/main/java/com/zynergylabs/forager/app/ui/log/CameraSettingsPanel.kt`, whole file at `497423d6`. Its rows and their parameters removed in `1e13df05`; the panel, the layer, the gear and the closing behaviour stay.

```kotlin
package com.zynergylabs.forager.app.ui.log

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zynergylabs.forager.app.ui.availability.ExplainedSettingsCheckbox
import com.zynergylabs.forager.app.ui.availability.LOCK_CAMERA_SETTING_EXPLANATION
import com.zynergylabs.forager.app.ui.availability.LOCK_CAMERA_SETTING_LABEL
import com.zynergylabs.forager.app.ui.availability.PHOTO_LOCATION_SETTING_EXPLANATION
import com.zynergylabs.forager.app.ui.availability.PHOTO_LOCATION_SETTING_LABEL
import com.zynergylabs.forager.app.ui.theme.DarkColors
import com.zynergylabs.forager.app.ui.theme.Spacing

/**
 * The camera's gear panel (dispatch 2026-09-28-707), opened by [CameraSettingsChip], and everything
 * that closes it: Back, or a tap anywhere outside it. Composed by [InAppCameraDialog] after the
 * strip and the shutter band, only while open, so it draws and hit-tests above both.
 *
 * ## What it holds
 *
 * The two rows that were at the bottom of Settings, with their existing strings and explanations,
 * drawn by Settings' own shared row ([ExplainedSettingsCheckbox]) so the touch target, the
 * checkbox and the supporting line are the ones Settings had. The values are the screen's
 * (`AvailabilityUiState`), and a tap calls the same `AvailabilityViewModel` handlers Settings
 * called: no new key, value or meaning.
 *
 * - **"Automatically Save Location to Photos"** (owner request, 2026-09-14): see
 *   [com.zynergylabs.forager.app.domain.PhotoLocationPreferenceRepository] for what the one flag
 *   gates and why it defaults on. It carries its explanation because it changes what is written to
 *   a record the user cannot see from here; the wording is the owner's own. The strip's Location
 *   chip shows and sets the same value, so the two always agree.
 * - **"Lock camera to portrait"** (owner request, 2026-09-15): its supporting line is there because
 *   the consequence, a sideways photo saved portrait, is not obvious from the label. What it gates
 *   is on [com.zynergylabs.forager.app.domain.CameraOrientationPreferenceRepository]. **New since
 *   this panel:** it can now change while the camera is open. The window's orientation request,
 *   the arrangement and the capture session are all keyed on it and follow (`RequestWindowOrientation`,
 *   `cameraArrangement`, `CameraXInAppCamera`); that a real CameraX session rebinds cleanly on the
 *   change is a device check.
 *
 * ## Where it sits, and what it covers
 *
 * Just inboard of the strip, on the punch-hole edge: under it in portrait, beside it in landscape,
 * inside the same band so it clears the cut-out the way the strip does ([CameraBand]). The shutter
 * band is on the opposite edge ([portEdge]), so the panel never covers the shutter, the count or a
 * capture error; it covers part of the preview while open, which is what a panel is. It turns with
 * the device like every control ([rotateWithDevice], owner, 2026-09-17), so its words read in the
 * current hold, and its square footprint is why its width is capped at [CAMERA_SETTINGS_PANEL_MAX_WIDTH]:
 * turned a quarter it must still fit across a portrait window. Its rows scroll if a large font
 * makes it taller than the window.
 *
 * Opening and closing are instant, like the rest of the camera (motion scout's deliberately-instant
 * list; the camera's rotation stays the system's, RECORD -681).
 *
 * ## Its colours
 *
 * The camera is always black behind its controls, whatever the app theme, so the panel is drawn in
 * the app's own dark scheme ([DarkColors]) in both themes; in the light scheme Settings' supporting
 * line (`onSurfaceVariant`) would be dark grey on black. Its fill is that scheme's container at
 * [CAMERA_SETTINGS_PANEL_ALPHA], 80%, the alpha the owner set for chrome over the map ("nothing
 * should fully obstruct the map view"), borrowed for the same reason over the preview. The camera's
 * overlay rule (`CameraOverlay.kt`: outlined glyphs, no panel of their own) is for the strip's
 * glyphs; a panel of checkbox rows and sentences is not readable that way, and the owner chose a
 * panel. **The fill and its alpha are this dispatch's choice, not the owner's**, and are the first
 * thing to judge on the device.
 */
@Composable
internal fun BoxScope.CameraSettingsPanelLayer(
    /** The strip's edge, the punch-hole edge: the panel sits just inboard of it. */
    stripEdge: ScreenEdge,
    deviceRotation: Int?,
    displayRotation: Int,
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    lockToPortrait: Boolean,
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    // Composed after the camera's own BackHandler, so this one wins while the panel is open: Back
    // closes the panel, and the next Back closes the camera as before.
    BackHandler(onBack = onClose)
    val currentOnClose by rememberUpdatedState(onClose)

    // A tap anywhere outside the panel closes it and does nothing else: this layer is above the
    // shutter, the chips and the preview, and takes the touch. That is the convention for a popup
    // dismissed by a tap outside; a shutter tap while the panel is open closes the panel and takes
    // no photo.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(CAMERA_SETTINGS_DISMISS_TAG)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        currentOnClose()
                    }
                }
            },
    )

    CameraBand(edge = stripEdge) {
        val inboard = STRIP_ROW_HEIGHT + Spacing.sm
        val clearOfStrip = when (stripEdge) {
            ScreenEdge.Top -> Modifier.absolutePadding(top = inboard)
            ScreenEdge.Bottom -> Modifier.absolutePadding(bottom = inboard)
            ScreenEdge.Left -> Modifier.absolutePadding(left = inboard)
            ScreenEdge.Right -> Modifier.absolutePadding(right = inboard)
        }
        Box(modifier = clearOfStrip.padding(Spacing.sm)) {
            CameraSettingsPanel(
                autoSaveLocationToPhotos = autoSaveLocationToPhotos,
                onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
                lockToPortrait = lockToPortrait,
                onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
                modifier = Modifier.rotateWithDevice(deviceRotation, displayRotation),
            )
        }
    }
}

/** The panel itself: the two rows on the dark scheme's container, see [CameraSettingsPanelLayer]. */
@Composable
internal fun CameraSettingsPanel(
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    lockToPortrait: Boolean,
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    MaterialTheme(colorScheme = DarkColors, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Surface(
            modifier = modifier
                .widthIn(max = CAMERA_SETTINGS_PANEL_MAX_WIDTH)
                .testTag(CAMERA_SETTINGS_PANEL_TAG)
                .semantics { paneTitle = CAMERA_SETTINGS_LABEL },
            shape = RoundedCornerShape(Spacing.md),
            color = DarkColors.surfaceContainer.copy(alpha = CAMERA_SETTINGS_PANEL_ALPHA),
            contentColor = DarkColors.onSurface,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                ExplainedSettingsCheckbox(
                    checked = autoSaveLocationToPhotos,
                    onCheckedChange = onAutoSaveLocationToPhotosChanged,
                    label = PHOTO_LOCATION_SETTING_LABEL,
                    explanation = PHOTO_LOCATION_SETTING_EXPLANATION,
                    tag = CAMERA_SETTINGS_PHOTO_LOCATION_TAG,
                )
                ExplainedSettingsCheckbox(
                    checked = lockToPortrait,
                    onCheckedChange = onLockCameraToPortraitChanged,
                    label = LOCK_CAMERA_SETTING_LABEL,
                    explanation = LOCK_CAMERA_SETTING_EXPLANATION,
                    tag = CAMERA_SETTINGS_LOCK_PORTRAIT_TAG,
                )
            }
        }
    }
}

/** Narrow enough that, turned a quarter by [rotateWithDevice], the panel's square still fits across a portrait phone. */
internal val CAMERA_SETTINGS_PANEL_MAX_WIDTH: Dp = 300.dp

/** The panel's fill alpha: the map chrome's 80% (`MAP_CHROME_OVER_MAP_ALPHA`), borrowed; see [CameraSettingsPanelLayer]. */
internal const val CAMERA_SETTINGS_PANEL_ALPHA = 0.8f

internal const val CAMERA_SETTINGS_PANEL_TAG = "in-app-camera-settings-panel"
internal const val CAMERA_SETTINGS_DISMISS_TAG = "in-app-camera-settings-dismiss"
internal const val CAMERA_SETTINGS_PHOTO_LOCATION_TAG = "in-app-camera-settings-photo-location"
internal const val CAMERA_SETTINGS_LOCK_PORTRAIT_TAG = "in-app-camera-settings-lock-portrait"
```

#### The camera host and slot, with the setting and the keyed session

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraHost.kt`, whole file at `497423d6`. Changed in `1e13df05`: the slot and host lose `lockToPortrait` and `onLockCameraToPortraitChanged`, and the session is no longer keyed (line 66 is the freeze's trigger).

```kotlin
package com.zynergylabs.forager.app.ui.log

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.photo.CameraCaptureFiles
import com.zynergylabs.forager.app.photo.CameraXCaptureSession
import com.zynergylabs.forager.app.sensor.AndroidLevelProvider

/**
 * Which surface asked for the in-app camera, so the one hoisted dialog can hand each photo to the
 * right consumer. **Data, not a callback, on purpose**: this value is retained across Activity
 * recreation by [InAppCameraViewModel], and a lambda captured from a composition that no longer
 * exists is exactly the kind of thing that must not be retained. The routing from target to
 * consumer happens in [InAppCameraHost], once, from callbacks that are live in the current
 * composition.
 */
enum class InAppCameraTarget {
    /** A find being edited: the photo attaches to it (`onAddLogPhoto`). */
    LOG_ENTRY,

    /** The Album: the photo is persisted standalone (`onAddGalleryPhoto`). */
    ALBUM,

    /** A Cartography entry being edited: persisted to the Album and kept by the entry (`onAcquirePhotoForCartographyEntry`). */
    CARTOGRAPHY_ENTRY,
}

/**
 * The camera dialog as a slot, for the same reason the map is one ([com.zynergylabs.forager.app.ui.map.MapSlot]):
 * CameraX cannot run under Robolectric, so a test of the screen that hosts the dialog hands in a
 * dialog over `FakeCameraCaptureSession` and asserts the hosting, not CameraX.
 */
typealias InAppCameraSlot = @Composable (
    cameraCaptureFiles: CameraCaptureFiles,
    /** "Lock camera to portrait", handed to the session at creation — see `effectiveDeviceRotation`. */
    lockToPortrait: Boolean,
    /** The persisted grid mode and the way to change it — see `CameraGridModeViewModel`. */
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    /** Settings' "Automatically Save Location to Photos" and its handler — see `AvailabilityViewModel.onAutoSaveLocationToPhotosChanged`. */
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    /** The gear panel's "Lock camera to portrait" handler (dispatch 2026-09-28-707) — see `AvailabilityViewModel.onLockCameraToPortraitChanged`. */
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    onPhotoCaptured: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
) -> Unit

/**
 * The production slot: one [CameraXCaptureSession] per open camera, discarded when the dialog
 * leaves composition, because it holds a bound CameraX use case that must not outlive the
 * viewfinder it draws into. Moved here from `PhotoAcquisitionLaunchers` on 2026-09-15 when the
 * dialog was hoisted; unchanged otherwise.
 */
internal val CameraXInAppCamera: InAppCameraSlot = { cameraCaptureFiles, lockToPortrait, gridMode, onGridModeChanged, autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, onLockCameraToPortraitChanged, onPhotoCaptured, onDismiss ->
    val context = LocalContext.current.applicationContext
    // One provider per open camera; it registers a sensor listener only while the level is shown.
    val levelProvider = remember { AndroidLevelProvider(context) }
    // Keyed on the setting so a session never carries a stale value. Since dispatch 2026-09-28-707 it
    // can change while the camera is open, from the gear panel: the key then makes a new session, and
    // the dialog's DisposableEffect closes the old one and opens this. Until then the dialog covered
    // Settings, so it could not; the rebind on a live change is a device check, not shown by any test.
    val session = remember(lockToPortrait) { CameraXCaptureSession(context, lockToPortrait) }
    InAppCameraDialog(
        session = session,
        cameraCaptureFiles = cameraCaptureFiles,
        lockToPortrait = lockToPortrait,
        onPhotoCaptured = onPhotoCaptured,
        onDismiss = onDismiss,
        gridMode = gridMode,
        onGridModeChanged = onGridModeChanged,
        autoSaveLocationToPhotos = autoSaveLocationToPhotos,
        onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
        onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
        levelProvider = levelProvider,
        viewfinder = { modifier -> session.Viewfinder(modifier) },
    )
}

/**
 * The in-app camera, composed **once, above the window-width branch** — owner's decision,
 * 2026-09-15: *"The camera is not a property of the navigation layout, and the current placement
 * is what ties its lifetime to a breakpoint nobody meant it to depend on. One dialog, composed
 * once, above the branch. The three screens keep their Camera buttons and call up to it."*
 *
 * ## The bug this moved for
 *
 * Found on the device check: rotating the phone with the camera open closed it and dropped the
 * user on the screen underneath, in the wide layout. Two mechanisms, and the one everyone
 * reasoned about first was the smaller. (1) A rotation recreates the Activity, and the open flag
 * lived in `remember` inside the screen. (2) A phone in landscape is `WindowWidthClass.MEDIUM`,
 * and `AvailabilityScreen` composes a different tree per width class; each of the three screens
 * with a Camera button sat inside that per-class tree, so the width flip alone disposed the
 * dialog, its session and its flag, **whether or not the Activity was recreated**.
 * `android:configChanges` would have addressed (1) and left (2) exactly as it was. So: the flag
 * moved to [InAppCameraViewModel], which survives (1), and the dialog moved here, above the
 * branch, which survives (2). The screens' Camera buttons now call up through
 * `onOpenCamera(target)`; the routing back down is the `when` below.
 *
 * One dialog is also one CameraX session, rather than one per screen that could offer a button.
 */
@Composable
internal fun InAppCameraHost(
    target: InAppCameraTarget?,
    cameraCaptureFiles: CameraCaptureFiles,
    /** "Lock camera to portrait", from `AvailabilityUiState`; passed straight to the slot. */
    lockToPortrait: Boolean,
    /** The persisted grid mode, from `CameraGridModeViewModel`; passed straight to the slot. */
    gridMode: GridMode,
    onGridModeChanged: (GridMode) -> Unit,
    /** Settings' "Automatically Save Location to Photos", from `AvailabilityUiState`, and Settings' own handler; passed straight to the slot. */
    autoSaveLocationToPhotos: Boolean,
    onAutoSaveLocationToPhotosChanged: (Boolean) -> Unit,
    /** The gear panel's "Lock camera to portrait" handler, the screen's own; passed straight to the slot. */
    onLockCameraToPortraitChanged: (Boolean) -> Unit,
    onLogEntryPhoto: (PhotoSource) -> Unit,
    onAlbumPhoto: (PhotoSource) -> Unit,
    onCartographyEntryPhoto: (PhotoSource) -> Unit,
    onDismiss: () -> Unit,
    camera: InAppCameraSlot = CameraXInAppCamera,
) {
    if (target == null) return
    val onPhotoCaptured: (PhotoSource) -> Unit = when (target) {
        InAppCameraTarget.LOG_ENTRY -> onLogEntryPhoto
        InAppCameraTarget.ALBUM -> onAlbumPhoto
        InAppCameraTarget.CARTOGRAPHY_ENTRY -> onCartographyEntryPhoto
    }
    camera(cameraCaptureFiles, lockToPortrait, gridMode, onGridModeChanged, autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, onLockCameraToPortraitChanged, onPhotoCaptured, onDismiss)
}
```

#### InAppCameraDialog: the parameter

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialog.kt`, lines 143 to 144 at `497423d6`.

```kotlin
    /** "Lock camera to portrait": decides the window lock and, with the window's shape at open, the arrangement; the gear panel shows it. */
    lockToPortrait: Boolean,
```

#### InAppCameraDialog: the handler parameter

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialog.kt`, lines 162 to 166 at `497423d6`. Preceded by `    /**`.

```kotlin
     * The gear panel's "Lock camera to portrait" handler (dispatch 2026-09-28-707), the screen's own,
     * which stores the value and hands it back as [lockToPortrait]. Defaulted so the camera tests that
     * do not exercise the panel need no argument for it; the production slot passes it explicitly.
     */
    onLockCameraToPortraitChanged: (Boolean) -> Unit = {},
```

#### InAppCameraDialog: the window request

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialog.kt`, lines 192 to 195 at `497423d6`.

```kotlin
    // The window follows the device, or is forced portrait, by the setting; reasoning on
    // RequestWindowOrientation. Outside the Dialog so the context here is the Activity's, not the
    // dialog window's.
    RequestWindowOrientation(lockToPortrait)
```

#### InAppCameraDialog: the arrangement's key

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialog.kt`, lines 220 to 222 at `497423d6`.

```kotlin
    val arrangement = remember(lockToPortrait, windowIsLandscape, displayRotation) {
        cameraArrangement(lockToPortrait, windowIsLandscape, displayRotation)
    }
```

#### InAppCameraDialog: the panel's values

`app/src/main/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialog.kt`, lines 389 to 397 at `497423d6`.

```kotlin
            CameraSettingsPanelLayer(
                stripEdge = punchHoleEdge(arrangement),
                deviceRotation = session.deviceRotation,
                displayRotation = displayRotation,
                autoSaveLocationToPhotos = autoSaveLocationToPhotos,
                onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
                lockToPortrait = lockToPortrait,
                onLockCameraToPortraitChanged = onLockCameraToPortraitChanged,
                onClose = { showCameraSettings = false },
```

#### The window request, with the PORTRAIT branch

`app/src/main/java/com/zynergylabs/forager/app/ui/log/WindowOrientation.kt`, whole file at `497423d6`. Changed in `1e13df05`: `RequestWindowOrientation` takes no argument and requests `CAMERA_WINDOW_ORIENTATION` (`SCREEN_ORIENTATION_SENSOR`); `windowOrientationFor` is gone.

```kotlin
package com.zynergylabs.forager.app.ui.log

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Sets the Activity's `requestedOrientation` for as long as this is in composition — forced
 * portrait with "Lock camera to portrait" on, **following the device through all four
 * orientations** with it off — and puts back what was there when it leaves.
 *
 * ## Superseding note, 2026-09-19: the window follows the device; the lock is gone for setting-off
 *
 * Until `80855c3` this file was `WindowOrientationLock.kt` and this composable `LockWindowOrientation`:
 * with the setting off it pinned the window with `SCREEN_ORIENTATION_LOCKED` so that nothing in the
 * camera moved while it was open, and the arrangement, the glyph rotation, the device check and
 * two audit reports were written against that pin. The owner reversed it (dispatch of 2026-09-19)
 * for a reason the lock could not meet however it was tuned: **the system status bar belongs to the
 * top of the display as the system has it rotated, and an app cannot move it to another edge.**
 * While the window was pinned the display did not rotate, so the bar stayed on whichever physical
 * edge was up at open — raised three times, and impossible under any lock. So the window turns,
 * and the animation the lock existed to prevent is suppressed another way: the Activity's own
 * window asks the platform for seamless rotation ([RequestSeamlessRotation], `CameraWindowChrome.kt`),
 * under which the window is re-laid out in the new rotation with no animation.
 *
 * A first attempt made that request on a `Dialog`'s window and the platform ignored it, for a
 * structural reason recorded at `docs/audits/2026-09-19-unlock-seamless-rotation-stop-report.md`:
 * the Shell picks a rotation's animation from the *task's main window*, which is never a Dialog.
 * The camera now draws in the Activity's own window, which is that window.
 *
 * What the lock's history still explains: the arrangement follows the window
 * ([cameraArrangement], the keyed `remember` in `InAppCameraDialog`) because a `LOCKED` window
 * turned out to turn anyway across a background-and-return (device, 2026-09-18), and that following
 * is exactly what a window that now turns on every hold needs. The glyphs' angle is sensor minus
 * display ([rotateWithDevice]), written from the start so that a window that carries the controls
 * cancels the turn; with the window following, the two terms agree in every steady hold and the
 * glyphs stay put because the window moved them.
 *
 * ## The value depends on the setting: `SENSOR` off, `PORTRAIT` on
 *
 * - **Setting off: `SCREEN_ORIENTATION_SENSOR`.** Portrait and both landscapes, and **never reverse
 *   portrait**. The platform's rotation policy is what draws that line
 *   (`DisplayRotation.rotationForOrientation`, android16-release): for `SENSOR` it takes the
 *   sensor's rotation, except that a sensor reading of `ROTATION_180` is only honoured when the
 *   device's own `config_allowAllRotations` is set (tablets) or the request is `FULL_SENSOR` /
 *   `FULL_USER`. On a phone under `SENSOR` a half turn therefore resolves to `lastRotation`: the
 *   window stays exactly where it was.
 *
 *   **That is the point, not a limitation** (owner, 2026-09-19, from the reference app). Turning the
 *   phone end-over-end with the camera open leaves the window alone, so there is no rotation to
 *   animate and the status bar does not move — which is what Samsung's camera was observed doing,
 *   and why photos still come out upright there: capture rotation comes from the sensor, not the
 *   window. Ours is the same split, and [CameraXCaptureSession]'s `OrientationEventListener` keeps
 *   reading all four regardless of what the window may occupy.
 *
 *   This replaced `SCREEN_ORIENTATION_FULL_SENSOR`, which was set earlier the same day so that the
 *   bar would be on the phone's top edge in *every* hold including upside-down. It bought that one
 *   hold at the price of a visible rotation animation on the half turn, and the owner's ruling is
 *   that the reference app's behaviour is the right one. Do not put `FULL_SENSOR` back without
 *   reading [cameraArrangement]'s note on what becomes reachable again if you do.
 *
 *   One consequence is unchanged from `FULL_SENSOR` and worth knowing: `SENSOR` sits outside the
 *   `USER_ROTATION_FREE` guard in that same branch, so the window follows the sensor **whether or
 *   not the user has auto-rotate locked**, and the rotation sensor runs for the camera's duration
 *   (`needSensorRunning`, same file). `USER` / `FULL_USER` would honour the lock, and were not
 *   taken: the requirement is that the bar travels with the phone, and under a user lock it would
 *   not.
 * - **Setting on: `SCREEN_ORIENTATION_PORTRAIT`**, unchanged since `e51b3ae`. The setting's whole
 *   point is a portrait camera: one flip at open from a landscape window, then nothing turns —
 *   not the window, not a glyph ([effectiveDeviceRotation] pins the sensor term, so the *photo* is
 *   tagged portrait too, whatever the hold). `PORTRAIT` rather than `SENSOR_PORTRAIT`/`USER_PORTRAIT`,
 *   which admit reverse portrait. With reverse portrait now excluded on both sides of the setting,
 *   what the setting still decides is the two landscapes **and** the capture tag.
 *
 * ## Capture and capture rotation are untouched
 *
 * `CameraXCaptureSession.capture` takes each shot's `targetRotation` from the session's own
 * `OrientationEventListener`, snapped by `snapToSurfaceRotation`, and the display enters that path
 * only as a logged fallback for a shot with no reading yet. The JPEG's requested rotation is the
 * sensor's mounting minus that target, and the window's rotation is not a term in it — measured on
 * the emulator before and after this change, four holds each, same `requestDegrees` in every hold
 * (`docs/audits/2026-09-19-unlock-seamless-rotation-prebuild-report.md`).
 *
 * ## Restore, and its edges
 *
 * The previous value is read once, when the effect starts, and written back when it ends; nothing
 * else in this app sets `requestedOrientation` (grep, 2026-09-19), so "previous" is the manifest's
 * `UNSPECIFIED`. *Dismissed with Back:* the effect disposes, restored — and if the user's own
 * rotation setting disagrees with the hold, the Activity's window turns back then, with the app's
 * ordinary rotation animation; the camera is already gone. *Activity recreated while open* (a
 * night-mode toggle; rotation itself does not recreate, `configChanges`): the old instance's
 * dispose restores on its way out, the ViewModel reopens the dialog on the new instance and this
 * requests again. *Process death:* the request lives on the Activity's window token, which is gone.
 * *No Activity behind the context* (a preview, a plain `ContextWrapper`): logged, and the window
 * is left to the platform's default rather than fabricating a request on nothing.
 *
 * Reachable under Robolectric only as the requested value: `Activity.requestedOrientation` reads
 * back what was set. Whether the window actually turns, and whether it turns without an animation,
 * are the device's and the emulator's.
 */
@Composable
internal fun RequestWindowOrientation(lockToPortrait: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, lockToPortrait) {
        if (activity == null) {
            Log.w(TAG, "No Activity behind this context; the camera's window orientation is not requested and is left to the platform.")
            return@DisposableEffect onDispose {}
        }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = windowOrientationFor(lockToPortrait)
        onDispose { activity.requestedOrientation = previous }
    }
}

/** The `requestedOrientation` the camera holds: forced portrait when the setting is on, otherwise following the device through portrait and both landscapes but never reverse portrait. Pure, tested. */
internal fun windowOrientationFor(lockToPortrait: Boolean): Int =
    if (lockToPortrait) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR

/** The Activity behind a composition's context, or null — shared with `CameraWindowChrome`, which needs the same window. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val TAG = "WindowOrientation"
```

#### cameraArrangement with its first input

`app/src/main/java/com/zynergylabs/forager/app/ui/log/CameraArrangement.kt`, lines 123 to 141 at `497423d6`. Closed by `}`.

```kotlin
internal fun cameraArrangement(
    lockToPortrait: Boolean,
    windowIsLandscape: Boolean,
    displayRotation: Int,
): CameraArrangement = when {
    // No reverse-portrait case on purpose: the camera's window cannot occupy ROTATION_180
    // (RequestWindowOrientation asks for SCREEN_ORIENTATION_SENSOR, which the platform will not
    // resolve to 180 on a phone), so this branch's portrait answer is never wrong in practice. It
    // *would* be wrong there — shutter on the punch-hole edge, strip on the port edge, measured on
    // the emulator 2026-09-19 — so anything that makes 180 reachable again needs a fourth
    // arrangement built with it. See this function's doc comment.
    lockToPortrait || !windowIsLandscape -> CameraArrangement.Portrait
    // RECORD -958 (asked whether the in-app camera should look the same both ways, as the map does since RECORD -956, the owner:
    // "Yes"): in a landscape window the layout is the same at ROTATION_90 and ROTATION_270, as it was at ROTATION_90: the shutter on
    // the screen's right, the strip on its left. On-screen placement only: the photo's orientation comes from the sensor
    // (CameraCaptureSession.deviceRotation, CameraXCaptureSession's targetRotation), and the glyphs still turn by sensor minus display
    // (rotateWithDevice), neither of which reads this. Was: portEdgeFor(displayRotation), the port's edge, so ROTATION_270 was the
    // mirror ([CameraArrangement.LandscapePortLeft], now unreached; kept so the enum's history and its tests stay readable).
    else -> CameraArrangement.LandscapePortRight
```

#### CameraXCaptureSession: the constructor parameter

`app/src/main/java/com/zynergylabs/forager/app/photo/CameraXCaptureSession.kt`, lines 159 to 163 at `497423d6`.

```kotlin
internal class CameraXCaptureSession(
    private val appContext: Context,
    /** Settings' "Lock camera to portrait": the one gate, see [effectiveDeviceRotation]. */
    private val lockToPortrait: Boolean = false,
) : CameraCaptureSession {
```

#### CameraXCaptureSession: deviceRotation through the gate

`app/src/main/java/com/zynergylabs/forager/app/photo/CameraXCaptureSession.kt`, lines 187 to 191 at `497423d6`. Preceded by `    /**`.

```kotlin
     * **The one gate.** Both the screen's control angle and this class's own [capture] read this,
     * never [sensorRotation] directly, so "Lock camera to portrait" pins one value and both
     * effects — nothing turns, a sideways photo saves portrait — fall out of that one place.
     */
    override val deviceRotation: Int? get() = effectiveDeviceRotation(lockToPortrait, sensorRotation)
```

#### CameraXCaptureSession: the comment in capture()

`app/src/main/java/com/zynergylabs/forager/app/photo/CameraXCaptureSession.kt`, line 319 at `497423d6`.

```kotlin
        // Read through the gate (deviceRotation), not the field: see effectiveDeviceRotation.
```

#### CameraXCaptureSession: the gate

`app/src/main/java/com/zynergylabs/forager/app/photo/CameraXCaptureSession.kt`, lines 526 to 533 at `497423d6`. Preceded by `/**`; the file also imported `android.view.Surface` for it.

```kotlin
 * The device rotation the camera acts on — for the controls' angle and for each shot's
 * `targetRotation` alike. With "Lock camera to portrait" off it is the sensor's reading (`null`
 * until there is one); on, it is `Surface.ROTATION_0` and the sensor is ignored. One function so
 * the setting is one gate: there is no second place where either effect could be decided
 * differently. Pure, and tested as such.
 */
internal fun effectiveDeviceRotation(lockToPortrait: Boolean, sensorRotation: Int?): Int? =
    if (lockToPortrait) Surface.ROTATION_0 else sensorRotation
```

## The removed tests

Tests removed or changed by the removal, verbatim from `497423d6`. The replacement for the gate's tests
is `CameraXCaptureSessionDeviceRotationTest`. The empty panel's test is `InAppCameraSettingsPanelTest`'s
`the panel holds no settings and says so`.

#### The repository's round-trip tests

`app/src/test/java/com/zynergylabs/forager/app/data/repository/DataStoreCameraOrientationPreferenceRepositoryTest.kt`, whole file at `497423d6`. Deleted in `1e13df05` with the repository.

```kotlin
package com.zynergylabs.forager.app.data.repository

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A real round trip through Jetpack DataStore, same shape and per-test file deletion as [DataStorePhotoLocationPreferenceRepositoryTest]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataStoreCameraOrientationPreferenceRepositoryTest {

    private fun context() = ApplicationProvider.getApplicationContext<Application>()

    private fun dataStoreFile() = File(context().filesDir, "datastore/camera_orientation_preferences.preferences_pb")

    @Before
    fun setUp() {
        dataStoreFile().delete()
    }

    @After
    fun tearDown() {
        dataStoreFile().delete()
    }

    /** Off, so an install that predates the setting keeps the camera it had: controls that turn, sideways photos saved landscape. */
    @Test
    fun `an untouched install has the setting off`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())

        assertEquals(false, repository.getLockCameraToPortrait().getOrThrow())
        assertEquals("the constant the interface documents", false, DEFAULT_LOCK_CAMERA_TO_PORTRAIT)
    }

    @Test
    fun `the setting round-trips both ways`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())

        assertTrue(repository.setLockCameraToPortrait(true).isSuccess)
        assertEquals(true, repository.getLockCameraToPortrait().getOrThrow())

        assertTrue(repository.setLockCameraToPortrait(false).isSuccess)
        assertEquals("a second write replaces the first", false, repository.getLockCameraToPortrait().getOrThrow())
    }

    /** `true` is the value that matters here, the one the user chose; it must be distinguishable from never set, which reads as off. */
    @Test
    fun `an on value is not confused with never having been set`() = runTest {
        val repository = DataStoreCameraOrientationPreferenceRepository(context())
        assertEquals("precondition: never set reads as off", false, repository.getLockCameraToPortrait().getOrThrow())

        repository.setLockCameraToPortrait(true).getOrThrow()

        assertEquals(true, repository.getLockCameraToPortrait().getOrThrow())
    }
}
```

#### The gate's pure and session tests

`app/src/test/java/com/zynergylabs/forager/app/photo/CameraOrientationGateTest.kt`, whole file at `497423d6`. Deleted in `1e13df05`, replaced by `CameraXCaptureSessionDeviceRotationTest` (`deviceRotation is the sensor's reading`).

```kotlin
package com.zynergylabs.forager.app.photo

import android.app.Application
import android.view.Surface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Lock camera to portrait" as the one gate it is. [effectiveDeviceRotation] is pure; the real
 * [CameraXCaptureSession] is also read here, constructed but never opened, because its
 * `deviceRotation` getter is plain code that runs without CameraX. That `capture()` assigns the
 * gated value is not this class's: until 2026-09-15 it was established by reading the class, and
 * this doc said a revert pointing `capture()` back at the raw field would pass the suite. It
 * would no longer — [CameraXCaptureSessionShotRotationTest] drives `capture()` against a real,
 * unbound `ImageCapture` and reads `targetRotation` back from it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraOrientationGateTest {

    @Test
    fun `off passes the sensor's reading through, null included`() {
        assertNull(effectiveDeviceRotation(lockToPortrait = false, sensorRotation = null))
        assertEquals(Surface.ROTATION_90, effectiveDeviceRotation(lockToPortrait = false, sensorRotation = Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_270, effectiveDeviceRotation(lockToPortrait = false, sensorRotation = Surface.ROTATION_270))
    }

    @Test
    fun `on pins portrait and ignores the sensor entirely`() {
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = null))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_90))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_180))
        assertEquals(Surface.ROTATION_0, effectiveDeviceRotation(lockToPortrait = true, sensorRotation = Surface.ROTATION_270))
    }

    @Test
    fun `the real session's deviceRotation reads through the gate`() {
        val app = ApplicationProvider.getApplicationContext<Application>()

        assertNull("off, before any reading: nothing to report", CameraXCaptureSession(app, lockToPortrait = false).deviceRotation)
        assertEquals("on, before any reading: portrait, already", Surface.ROTATION_0, CameraXCaptureSession(app, lockToPortrait = true).deviceRotation)
    }
}
```

#### The gated fake

`app/src/test/java/com/zynergylabs/forager/app/photo/GatedFakeCameraCaptureSession.kt`, whole file at `497423d6`. Deleted in `1e13df05`: with no gate it is the plain fake.

```kotlin
package com.zynergylabs.forager.app.photo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LifecycleOwner
import java.io.File

/**
 * A [FakeCameraCaptureSession] whose [deviceRotation] goes through the real gate,
 * [effectiveDeviceRotation], exactly as `CameraXCaptureSession.deviceRotation` does: a test sets
 * the **sensor's** reading and the setting decides whether the screen ever sees it.
 *
 * Why a second fake rather than a flag on the first: every existing test sets
 * `FakeCameraCaptureSession.deviceRotation` directly, which is the right seam for "the dialog
 * turns a control for the value it is handed" — the wiring. It is the wrong seam for "with the
 * setting on, nothing turns whatever the device does", because a test that sets the gated value by
 * hand has bypassed the gate; that is how v4 step 4.2, the test named for it and the code disagreed
 * from the day the setting shipped without anything noticing (2026-09-18). This fake cannot bypass
 * it.
 */
internal class GatedFakeCameraCaptureSession(
    private val lockToPortrait: Boolean,
    private val inner: FakeCameraCaptureSession = FakeCameraCaptureSession(),
) : CameraCaptureSession {

    /** The orientation listener's snapped reading, as `CameraXCaptureSession.sensorRotation` holds it. */
    var sensorRotation: Int? by mutableStateOf(null)

    override val deviceRotation: Int? get() = effectiveDeviceRotation(lockToPortrait, sensorRotation)

    override val state: CameraSessionState get() = inner.state
    override fun open(lifecycleOwner: LifecycleOwner) = inner.open(lifecycleOwner)
    override fun close() = inner.close()
    override suspend fun capture(destination: File): Result<Unit> = inner.capture(destination)
    override val hasFlashUnit: Boolean get() = inner.hasFlashUnit
    override val flashMode: FlashMode get() = inner.flashMode
    override fun setFlashMode(mode: FlashMode) = inner.setFlashMode(mode)
}
```

#### `locked, the shot assigns ROTATION_0 whatever the sensor said`

`app/src/test/java/com/zynergylabs/forager/app/photo/CameraXCaptureSessionShotRotationTest.kt`, lines 58 to 71 at `497423d6`.

```kotlin
    @Test
    fun `locked, the shot assigns ROTATION_0 whatever the sensor said`() = runTest {
        val session = CameraXCaptureSession(app, lockToPortrait = true)
        val capture = imageCaptureAt(Surface.ROTATION_90)
        session.installImageCapture(capture)
        session.onDeviceOrientation(270) // a quarter turn counter-clockwise: ROTATION_90 when unlocked
        assertEquals("precondition: not already where the lock would put it", Surface.ROTATION_90, capture.targetRotation)

        val result = session.capture(destination())

        assertEquals(Surface.ROTATION_0, capture.targetRotation)
        assertTrue("an unbound ImageCapture cannot shoot, and that is reported, not hidden", result.isFailure)
        assertTrue(result.exceptionOrNull() is ImageCaptureException)
    }
```

#### `locked before any reading, the shot still assigns ROTATION_0`

`app/src/test/java/com/zynergylabs/forager/app/photo/CameraXCaptureSessionShotRotationTest.kt`, lines 111 to 120 at `497423d6`.

```kotlin
    @Test
    fun `locked before any reading, the shot still assigns ROTATION_0`() = runTest {
        val session = CameraXCaptureSession(app, lockToPortrait = true)
        val capture = imageCaptureAt(Surface.ROTATION_270)
        session.installImageCapture(capture)

        session.capture(destination())

        assertEquals(Surface.ROTATION_0, capture.targetRotation)
    }
```

#### `wiring only, setting on, the portrait arrangement is used and a control turns for whatever value the dialog is handed`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialogLandscapeTest.kt`, lines 202 to 226 at `497423d6`.

```kotlin
    @Test
    fun `wiring only, setting on, the portrait arrangement is used and a control turns for whatever value the dialog is handed`() {
        // This drives the fake's deviceRotation directly, past the gate, and so says nothing about
        // whether the setting stops the controls turning — it says the dialog turns a control for
        // the value it is given. That was the only test named for the setting-on case until
        // 2026-09-18, which is how the code (nothing turns), v4 step 4.2 (controls turn) and this
        // test disagreed for four days unnoticed. The gated behaviour is the next two tests.
        setDialog(lockToPortrait = true)
        val frame = bounds(IN_APP_CAMERA_TAG)
        val shutter = bounds(CAMERA_SHUTTER_TAG)
        assertEquals("shutter along the bottom, centred: the window is about to be forced portrait", frame.centreX(), shutter.centreX(), 0.51f)
        assertEquals((frame.bottom - Spacing.lg).value, shutter.bottom.value, 0.51f)

        // The portrait arrangement's angle is sensor minus display. Robolectric's landscape window
        // reports a turned display (the real one would be forced portrait by the setting, which
        // this harness cannot do), so a device reading equal to the display's would cancel to no
        // turn — correctly. A reading a quarter turn from the display's is what must turn here.
        @Suppress("DEPRECATION") val displayRotation = composeRule.activity.windowManager.defaultDisplay.rotation
        val countBefore = bounds(CAMERA_COUNT_TAG)
        session.deviceRotation = (displayRotation + 1) % 4
        composeRule.waitForIdle()
        val countAfter = bounds(CAMERA_COUNT_TAG)
        assertEquals("a quarter turn from a display at $displayRotation swaps the count's extents", countBefore.width.value, countAfter.height.value, 0.51f)
        assertEquals(countBefore.height.value, countAfter.width.value, 0.51f)
    }
```

#### `setting on, through the gate, nothing turns for any sensor reading, before or after the window flips`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialogLandscapeTest.kt`, lines 425 to 472 at `497423d6`.

```kotlin
    /**
     * **With the setting on, nothing turns** (owner's ruling, 2026-09-18; the setting's own doc;
     * `effectiveDeviceRotation`). Through the gate, not past it: the sensor is driven through all
     * four readings and neither the count moves a pixel nor the flash chip's angle changes (its
     * angle, because the chip is square and its bounds cannot show a turn; the angle is still, not
     * zero — before the flip the window is at `ROTATION_90` and the pinned sensor is `ROTATION_0`). Then the window is
     * turned underneath the dialog — the one flip a landscape open makes — and the sensor driven
     * again, because the strip's since-retired placeholder label was seen turned only after that
     * flip on the emulator.
     *
     * **What this test cannot see, stated rather than implied:** the emulator's after-flip label
     * turn is a stale display term inside the dialog's composition (`2026-09-18-placeholder-label-
     * stale-display-term.md`), and this harness provides `LocalConfiguration` from outside the
     * dialog, where it does update. So this test passes on the defective code too; it guards the
     * gate, not the invalidation.
     */
    @Test
    fun `setting on, through the gate, nothing turns for any sensor reading, before or after the window flips`() {
        setDisplayRotation(Surface.ROTATION_90)
        val gated = GatedFakeCameraCaptureSession(lockToPortrait = true)
        setDialog(lockToPortrait = true, session = gated)
        val count0 = bounds(CAMERA_COUNT_TAG)
        val angle0 = chipAngle()

        for (reading in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            gated.sensorRotation = reading
            composeRule.waitForIdle()
            assertEquals("count unmoved at sensor $reading", count0, bounds(CAMERA_COUNT_TAG))
            assertEquals("chip unturned at sensor $reading", angle0, chipAngle(), 0.01f)
        }

        // The flip: the window the setting forces.
        setDisplayRotation(Surface.ROTATION_0)
        setOrientation(Configuration.ORIENTATION_PORTRAIT)
        composeRule.waitForIdle()
        val countP = bounds(CAMERA_COUNT_TAG)
        val angleP = chipAngle()
        // No "upright" aspect check on the count: this harness measures it narrower than tall (7x20
        // dp, recorded in this class on 2026-09-18), so width > height is not what upright looks
        // like here and would fail on correct code. The claim is stillness, and for the square chip
        // stillness is its angle.
        for (reading in listOf(Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270, Surface.ROTATION_0)) {
            gated.sensorRotation = reading
            composeRule.waitForIdle()
            assertEquals("count unmoved after the flip at sensor $reading", countP, bounds(CAMERA_COUNT_TAG))
            assertEquals("chip unturned after the flip at sensor $reading", angleP, chipAngle(), 0.01f)
        }
    }
```

#### `setting off, through the gate, the count still turns when the sensor does`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraDialogLandscapeTest.kt`, lines 475 to 488 at `497423d6`. A duplicate of `setting off in a landscape window, the count turns in place when the device does, as in portrait` (line 170 of the same file) through the gated fake.

```kotlin
    @Test
    fun `setting off, through the gate, the count still turns when the sensor does`() {
        setDisplayRotation(Surface.ROTATION_90)
        val gated = GatedFakeCameraCaptureSession(lockToPortrait = false)
        setDialog(lockToPortrait = false, session = gated)
        val before = bounds(CAMERA_COUNT_TAG)

        gated.sensorRotation = Surface.ROTATION_0
        composeRule.waitForIdle()
        val after = bounds(CAMERA_COUNT_TAG)

        assertEquals("a quarter turn swaps the extents", before.width.value, after.height.value, 0.51f)
        assertEquals(before.height.value, after.width.value, 0.51f)
    }
```

#### `setting on, the window is forced portrait while the camera is open and the previous request is put back after`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/WindowOrientationTest.kt`, lines 89 to 100 at `497423d6`.

```kotlin
    @Test
    fun `setting on, the window is forced portrait while the camera is open and the previous request is put back after`() {
        // A non-default previous value, so "restored" is distinguishable from "reset to unspecified".
        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
        setDialog(lockToPortrait = true)

        assertEquals("forced portrait is the point of the setting", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, composeRule.activity.requestedOrientation)

        setShown(false)
        composeRule.waitForIdle()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_USER, composeRule.activity.requestedOrientation)
    }
```

#### `the requested orientation is a pure function of the setting`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/WindowOrientationTest.kt`, lines 123 to 127 at `497423d6`.

```kotlin
    @Test
    fun `the requested orientation is a pure function of the setting`() {
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, windowOrientationFor(lockToPortrait = true))
        assertEquals("portrait and both landscapes, never reverse portrait", ActivityInfo.SCREEN_ORIENTATION_SENSOR, windowOrientationFor(lockToPortrait = false))
    }
```

#### `setting on is portrait whatever the window was when the camera opened`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/CameraArrangementTest.kt`, lines 70 to 85 at `497423d6`.

```kotlin
    @Test
    fun `setting on is portrait whatever the window was when the camera opened`() {
        // The window is about to be forced portrait; reading its pre-flip landscape shape would hold the wrong answer.
        for (rotation in listOf(Surface.ROTATION_0, Surface.ROTATION_90, Surface.ROTATION_180, Surface.ROTATION_270)) {
            assertEquals(
                "setting on, rotation $rotation, landscape window",
                CameraArrangement.Portrait,
                cameraArrangement(lockToPortrait = true, windowIsLandscape = true, displayRotation = rotation),
            )
            assertEquals(
                "setting on, rotation $rotation, portrait window",
                CameraArrangement.Portrait,
                cameraArrangement(lockToPortrait = true, windowIsLandscape = false, displayRotation = rotation),
            )
        }
    }
```

#### `the panel shows both settings with their explanations, location on and lock off by default`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraSettingsPanelTest.kt`, lines 175 to 188 at `497423d6`.

```kotlin
    @Test
    fun `the panel shows both settings with their explanations, location on and lock off by default`() {
        assertEquals("the screen's default for photo location is on", true, defaults.autoSaveLocationToPhotos)
        assertEquals("and for the lock, off", false, defaults.lockCameraToPortrait)
        setDialog()
        openPanel()

        for (text in listOf(PHOTO_LOCATION_SETTING_LABEL, PHOTO_LOCATION_SETTING_EXPLANATION, LOCK_CAMERA_SETTING_LABEL, LOCK_CAMERA_SETTING_EXPLANATION)) {
            assertEquals("'$text' shows once", 1, composeRule.onAllNodesWithText(text).fetchSemanticsNodes().size)
        }
        checkbox(PHOTO_LOCATION_SETTING_LABEL).assertIsOn()
        checkbox(LOCK_CAMERA_SETTING_LABEL).assertIsOff()
        assertEquals("nothing is written just by opening the panel", emptyList<String>(), calls)
    }
```

#### `a real touch anywhere across each row writes the toggled value`

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraSettingsPanelTest.kt`, lines 190 to 214 at `497423d6`.

```kotlin
    /**
     * Three real touches across each row (what `SettingsRowsTouchTest` held for these rows in
     * Settings): each reaches the screen's handler with the toggled value, and the row then shows
     * the stored value. The panel stays open throughout: a touch on it is not a touch outside it.
     */
    @Test
    fun `a real touch anywhere across each row writes the toggled value`() {
        setDialog()
        openPanel()
        for ((tag, name) in listOf(CAMERA_SETTINGS_PHOTO_LOCATION_TAG to "photo location", CAMERA_SETTINGS_LOCK_PORTRAIT_TAG to "camera lock")) {
            for (fraction in listOf(0.05f, 0.5f, 0.95f)) {
                val before = if (name == "photo location") saveLocation else lockPortrait
                calls.clear()
                val b = bounds(tag)
                touchAt(b.left.value + b.width.value * fraction, ((b.top + b.bottom) / 2).value)
                assertEquals("a touch at $fraction across the $name row", listOf("$name ${!before}"), calls)
                assertEquals("the panel is still open", 1, panelCount())
            }
        }
        assertEquals("three flips from on is off", false, saveLocation)
        assertEquals("three flips from off is on", true, lockPortrait)
        checkbox(PHOTO_LOCATION_SETTING_LABEL).assertIsOff()
        checkbox(LOCK_CAMERA_SETTING_LABEL).assertIsOn()
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_OFF_LABEL)
    }
```

#### `the lock-camera setting reaches the camera slot as given`

`app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenInAppCameraTest.kt`, lines 364 to 369 at `497423d6`.

```kotlin
    @Test
    fun `the lock-camera setting reaches the camera slot as given`() {
        setScreen(lockCameraToPortrait = true)
        openEditorCamera()
        assertEquals(true, slotSawLockToPortrait)
    }
```

#### `with the setting off the slot is told off`

`app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenInAppCameraTest.kt`, lines 371 to 376 at `497423d6`.

```kotlin
    @Test
    fun `with the setting off the slot is told off`() {
        setScreen()
        openEditorCamera()
        assertEquals(false, slotSawLockToPortrait)
    }
```

#### `after a tap on the camera's chip, the gear panel's checkbox shows the same value`

`app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenInAppCameraTest.kt`, lines 434 to 450 at `497423d6`.

```kotlin
    /**
     * The chip and the gear panel are one value: turned off on the camera's strip, the panel's
     * checkbox reads off. Until dispatch 2026-09-28-707 this closed the camera and read Settings'
     * checkbox, which is where the row was; the row is the gear panel's now, so the claim, one value
     * seen in two places, is read there.
     */
    @Test
    fun `after a tap on the camera's chip, the gear panel's checkbox shows the same value`() {
        autoSaveLocation = true
        setScreen()
        openEditorCamera()
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).performClick()
        composeRule.waitForIdle()

        touchCentreOf(CAMERA_SETTINGS_CHIP_TAG)
        composeRule.onNode(isToggleable() and hasAnySibling(hasText(PHOTO_LOCATION_SETTING_LABEL)), useUnmergedTree = true).assertIsOff()
    }
```

#### `the gear opens the panel, its two rows write the screen's values, and Back closes only the panel`

`app/src/test/java/com/zynergylabs/forager/app/ui/availability/AvailabilityScreenInAppCameraTest.kt`, lines 452 to 485 at `497423d6`.

```kotlin
    /**
     * Dispatch 2026-09-28-707, through the screen: real touches open the gear panel, its two rows reach
     * the screen's own handlers with the toggled values (the ones `MainActivity` wires to
     * `AvailabilityViewModel`), the value each shows is the screen's, and Back closes the panel
     * without closing the camera; a second Back closes the camera as before.
     */
    @Test
    fun `the gear opens the panel, its two rows write the screen's values, and Back closes only the panel`() {
        autoSaveLocation = true
        setScreen(lockCameraToPortrait = false)
        openEditorCamera()
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(0)

        touchCentreOf(CAMERA_SETTINGS_CHIP_TAG)
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(1)

        touchCentreOf(CAMERA_SETTINGS_PHOTO_LOCATION_TAG)
        assertEquals("the photo-location row asked for off", listOf(false), autoSaveLocationRequests)
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_OFF_LABEL)

        touchCentreOf(CAMERA_SETTINGS_LOCK_PORTRAIT_TAG)
        assertEquals("the lock row asked for on", listOf(true), lockCameraRequests)
        assertEquals("and the slot was handed the stored value", true, slotSawLockToPortrait)
        composeRule.onNode(isToggleable() and hasAnySibling(hasText(LOCK_CAMERA_SETTING_LABEL)), useUnmergedTree = true).assertIsOn()

        composeRule.pressBackOnCamera()
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        assertEquals("Back closed the panel, not the camera", 0, closed)

        composeRule.pressBackOnCamera()
        assertEquals("the next Back closes the camera", 1, closed)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }
```

#### InAppCameraHostTest: the slot-saw-the-setting assertion

`app/src/test/java/com/zynergylabs/forager/app/ui/log/InAppCameraHostTest.kt`, line 124 at `497423d6`.

```kotlin
        assertEquals("the host hands the setting to the slot unchanged", false, slotSawLockToPortrait)
```

## Added 2026-10-13, RECORD -980 to -982: the camera keeps a held portrait screen, with no setting

**What the owner found.** Testing PR #223, the owner reported: "the camera rotates completely". Then: "I never asked the
lock to be removed. I only wanted the keep in portrait feature removed. The camera is to remain as is." Then (-981): "The
camera is to never, ever, rotate like this. This is a cardinal sin." The owner's wish was to drop the *setting*, not the
held screen. The removal above made the camera follow the phone, which was the setting-off default. That reading was
wrong, and this section corrects it. Nothing above is edited.

**What was decided (-982).** The owner: "Tablets can lock portrait and rotate based on accelerometer position rather than
screen orientation". The planner read this as:
- the window is held portrait;
- the icons turn in place by the phone's own sensor;
- photos follow the phone.

**What is built** (`21fcb413`, tests first in `fe213eed`):
- `CAMERA_WINDOW_ORIENTATION` is `SCREEN_ORIENTATION_PORTRAIT`, always, with no setting.
- The session's device rotation is still the sensor's reading. So the controls turn in place, and a photo taken sideways
  saves the right way up, in landscape.

**What is not restored, on purpose.** The old lock's `effectiveDeviceRotation` gate pinned rotation to `ROTATION_0`. That
is why, with the lock on, nothing turned and a sideways photo saved portrait (the option's own text: "Photos are always
saved in portrait, even when you hold the phone sideways"). The fixed behaviour keeps the lock's held screen and leaves
out that pin. If the setting is ever restored, its "on" state is now the default for every install, and the steps under
"How to put it back" apply only to the pin and the toggle.

**The freeze stays impossible.** Nothing changes the session while the camera is open. The viewfinder is still not keyed
on its session, so the note under "How to put it back" stands for anything that would rebuild the session.

**Large screens are not handled yet.** With `targetSdk` 37 (`app/build.gradle.kts:249`), Android 17 ignores orientation
requests on displays 600 dp or wider. There the window still turns, and the arrangement switches to the landscape
layout. The owner's rule (-981) says it must not. How to hold the layout there is proposed to the planner first.

## Added 2026-10-14, RECORD -985: large screens hold the device's natural frame

On displays whose smallest width is greater than 600 dp, Android 17 ignores the portrait request for this app's
`targetSdk` 37, and there is no opt-out. The window turns with the tablet. The owner's rule (-981) is that the camera
never appears to rotate. So in a full-screen window with the display turned, `HoldNaturalOrientation`
(`ui/log/NaturalOrientationHold.kt`, `2c49232a`, tests first in `a4a75f24` and `3a804867`) does the following:
- lays the camera out in the device's natural frame and turns it back by the display's rotation;
- turns the insets into that frame for the bands;
- tells the viewfinder to use a `TextureView`, with CameraX's preview targeted at `ROTATION_0`.

The icons still turn by the sensor. Landscape-natural tablets hold their landscape (the owner: "Option A"). Split screen
and freeform windows lay out to the window's own shape, as before. At `ROTATION_0` the hold does nothing, so phones are
unchanged.

The viewfinder note under "The freeze's cause" still holds. The hold switches the viewfinder's mode only when it enters
or leaves the hold. It does not change the session, so no session is rebuilt.
