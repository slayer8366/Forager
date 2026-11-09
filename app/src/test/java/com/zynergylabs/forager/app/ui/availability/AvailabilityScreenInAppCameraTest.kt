package com.zynergylabs.forager.app.ui.availability

import com.zynergylabs.forager.app.ui.adaptive.ProvideConfigurationAndWindow
import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.GridMode
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.sensor.FakeLevelProvider
import com.zynergylabs.forager.app.ui.log.CAMERA_GRID_CHIP_TAG
import com.zynergylabs.forager.app.ui.log.CAMERA_LOCATION_CHIP_TAG
import com.zynergylabs.forager.app.ui.log.CAMERA_SETTINGS_CHIP_TAG
import com.zynergylabs.forager.app.ui.log.CAMERA_SETTINGS_PANEL_TAG
import com.zynergylabs.forager.app.ui.log.LOCATION_OFF_LABEL
import com.zynergylabs.forager.app.ui.log.LOCATION_ON_LABEL
import com.zynergylabs.forager.app.ui.log.CAMERA_SHUTTER_TAG
import com.zynergylabs.forager.app.ui.log.GRID_ON_LABEL
import com.zynergylabs.forager.app.ui.log.pressBackOnCamera
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.IN_APP_CAMERA_TAG
import com.zynergylabs.forager.app.ui.log.InAppCameraDialog
import com.zynergylabs.forager.app.ui.log.InAppCameraSlot
import com.zynergylabs.forager.app.ui.log.InAppCameraTarget
import com.zynergylabs.forager.app.ui.map.MapSlot
import android.content.pm.ActivityInfo
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The in-app camera through [AvailabilityScreen]'s real controls, for the rotation bug found on
 * the device check on 2026-09-15: a Camera button deep in one window-width tree, the width
 * flipping from COMPACT to MEDIUM the way a phone's rotation flipped it (then), and the camera still
 * there afterwards with its photo still routed to the surface that asked.
 *
 * **Since dispatch 2026-09-28-245 there is one tree at every width**, so the width change these tests
 * make (a `Configuration` with `screenWidthDp` set) no longer swaps trees as it did at 600 dp; it is
 * still a width change with the camera open, which is what they assert survives. Activity
 * recreation, mechanism (1) on `InAppCameraHost`, is what the ViewModel is for and is the platform's
 * contract, not composable here; the device check runs it ("Don't keep activities", and a rotation
 * with the camera open). The camera open flag is plain test state standing in for
 * `InAppCameraViewModel`, the same way `cartographyState` below stands in for `CartographyViewModel`.
 *
 * The camera slot is the real [InAppCameraDialog] over [FakeCameraCaptureSession], so "shoot" is
 * the shutter and a file on disk, and the map slot is a box, as in every other test of this screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h640dp-xhdpi")
class AvailabilityScreenInAppCameraTest {

    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val declareHostActivity = object : ExternalResource() {
        override fun before() {
            val app = ApplicationProvider.getApplicationContext<Application>()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(FileProviderCacheReset()).around(declareHostActivity).around(composeRule)

    private val opened = mutableListOf<InAppCameraTarget>()
    private var closed = 0
    private val cartographyPhotos = mutableListOf<PhotoSource>()
    private val albumPhotos = mutableListOf<PhotoSource>()
    private val logEntryPhotos = mutableListOf<PhotoSource>()

    private var setWidthDp: (Int) -> Unit = {}
    private var setTarget: (InAppCameraTarget?) -> Unit = {}

    private val boxMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private val fakeCamera: InAppCameraSlot = { cameraCaptureFiles, gridMode, onGridModeChanged, cameraFormat, onCameraFormatChanged, autoSaveLocationToPhotos, onAutoSaveLocationToPhotosChanged, onPhotoCaptured, onDismiss ->
        val session = remember { FakeCameraCaptureSession() }
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = cameraCaptureFiles,
            onPhotoCaptured = onPhotoCaptured,
            onDismiss = onDismiss,
            gridMode = gridMode,
            onGridModeChanged = onGridModeChanged,
            cameraFormat = cameraFormat,
            onCameraFormatChanged = onCameraFormatChanged,
            autoSaveLocationToPhotos = autoSaveLocationToPhotos,
            onAutoSaveLocationToPhotosChanged = onAutoSaveLocationToPhotosChanged,
            levelProvider = FakeLevelProvider(),
            viewfinder = { modifier -> Box(modifier) },
        )
    }

    private val gridModeRequests = mutableListOf<GridMode>()

    /**
     * Settings' "Automatically Save Location to Photos" as the screen sees it, and every value the
     * screen's handler was asked for. The handler stores what it is asked, as
     * `AvailabilityViewModel.onAutoSaveLocationToPhotosChanged` does to `uiState`.
     */
    private var autoSaveLocation by mutableStateOf(true)
    private val autoSaveLocationRequests = mutableListOf<Boolean>()

    private fun setScreen(cameraPermissionGranted: Boolean = true, cameraGridMode: GridMode = GridMode.Off) {
        if (cameraPermissionGranted) {
            Shadows.shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.CAMERA)
        }
        composeRule.setContent {
            var widthDp by remember { mutableIntStateOf(360) }
            var target by remember { mutableStateOf<InAppCameraTarget?>(null) }
            var cartographyState by remember { mutableStateOf(CartographyUiState()) }
            setWidthDp = { widthDp = it }
            setTarget = { target = it }
            val configuration = Configuration(LocalConfiguration.current).apply { screenWidthDp = widthDp }
            ProvideConfigurationAndWindow(configuration) {
                AvailabilityScreen(
                    uiState = SEARCHED_STATE.copy(autoSaveLocationToPhotos = autoSaveLocation),
                    searchInputs = AvailabilityScreenSearchInputs(
                        onUseCurrentLocation = {},
                        onManualLatChanged = {},
                        onManualLngChanged = {},
                        onSearchManualCoordinates = {},
                        onRadiusChanged = {},
                        onMonthSelected = {},
                        onMapTabSelected = {},
                        onSeasonalTabSelected = {},
                        onTaxonSearchQueryChanged = {},
                        onTaxonSearchResultSelected = {},
                        onDismissTaxonSuggestions = {},
                        onReopenTaxonSuggestions = {},
                        onPlaceTripPin = { _, _, _ -> },
                        onDeletePlannedTrip = {},
                        onRecentSearchSelected = {},
                    ),
                    offlineMaps = AvailabilityScreenOfflineMaps(
                        onOfflineMapLatChanged = {},
                        onOfflineMapLngChanged = {},
                        onOfflineMapRadiusChanged = {},
                        onOfflineMapNameChanged = {},
                        onOfflineMapsOpened = {},
                        onDownloadOfflineMaps = {},
                        onDeleteOfflineRegion = {},
                    ),
                    settings = AvailabilityScreenSettings(
                        onNightModeMapsChanged = {},
                        onThemeModeChanged = {},
                        onAutoSaveLocationToPhotosChanged = { requested ->
                            autoSaveLocationRequests += requested
                            autoSaveLocation = requested
                        },
                    ),
                    mapSlot = boxMapSlot,
                    cameraInputs = AvailabilityScreenCamera(
                        inAppCameraTarget = target,
                        onOpenCamera = { opened += it; target = it },
                        onCloseCamera = { closed++; target = null },
                        inAppCamera = fakeCamera,
                        cameraGridMode = cameraGridMode,
                        onCameraGridModeChanged = { gridModeRequests += it },
                    ),
                    findsLog = AvailabilityScreenFindsLog(
                        onAddLogPhoto = { logEntryPhotos += it },
                        onAddGalleryPhoto = { albumPhotos += it },
                    ),
                    journalEntries = AvailabilityScreenJournalEntries(
                        onAcquirePhotoForCartographyEntry = { cartographyPhotos += it },
                        cartographyUiState = cartographyState,
                        onStartCartographyEntry = { date ->
                            val started = CartographyEntry.draft(id = "new-cartography-entry", date = date, updatedAtEpochMillis = 0L)
                            cartographyState = cartographyState.copy(editingEntry = started, draftEntries = cartographyState.draftEntries + started)
                        },
                    ),
                )
            }
        }
    }

    /** Journal → a new Cartography entry → its add-photo picker, whose Camera button is the deepest one in the compact tree. */
    private fun openEditorCamera() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG).performClick() // J2 T4: the New entry floating button, was the "+" tile
        composeRule.onNodeWithContentDescription("Add a photo from the Album").performClick()
        composeRule.onNodeWithText("Camera").performClick()
        composeRule.waitForIdle()
    }

    private fun shoot(expectedTotal: Int) {
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { cartographyPhotos.size + albumPhotos.size + logEntryPhotos.size == expectedTotal }
    }

    @Test
    fun `the editor's Camera button opens one camera, for the Cartography entry`() {
        setScreen()
        openEditorCamera()

        assertEquals(listOf(InAppCameraTarget.CARTOGRAPHY_ENTRY), opened)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsEnabled()
    }

    /**
     * Reworked in dispatch 2026-09-28-245 (tablets as a big phone). This was the bug where a width flip at
     * 600 dp swapped the compact tree for the tablet's, disposing the screen that held the dialog. There is
     * one tree at every width now, so nothing swaps, and the test asserts that (the bottom nav is still
     * there at 700 dp) and keeps the rest of its claim: a width change with the camera open leaves it open,
     * and its photo still routes.
     */
    @Test
    fun `the camera survives a width change, and its photo still routes`() {
        setScreen()
        openEditorCamera()
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)

        setWidthDp(700) // formerly MEDIUM, where the tablet tree replaced the compact one; now the same tree
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Tools").assertCountEquals(1) // the bottom nav is still there: nothing swapped
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        composeRule.onNodeWithTag(CAMERA_SHUTTER_TAG).assertIsEnabled()

        shoot(expectedTotal = 1)
        assertEquals(1, cartographyPhotos.size)
        assertTrue(cartographyPhotos.single() is CameraCapturePhotoSource)
        assertEquals(0, albumPhotos.size + logEntryPhotos.size)

        composeRule.pressBackOnCamera()
        composeRule.waitForIdle()
        assertEquals(1, closed)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }

    /** Reworked with the test above (dispatch 2026-09-28-245): a second width change, back to 360 dp, keeps the camera too. */
    @Test
    fun `changing the width back keeps the camera too`() {
        setScreen()
        openEditorCamera()
        setWidthDp(700)
        composeRule.waitForIdle()
        setWidthDp(360)
        composeRule.waitForIdle()

        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        assertEquals(0, closed)
    }

    /**
     * **The camera is drawn in the Activity's own window now, over this screen's content, and it has
     * to take the touches that land on it.** While it was a `Dialog` this was the platform's job:
     * a separate window above the Activity's swallows everything by construction. There is no second
     * window any more, so what puts the camera on top is `Modifier.zIndex(1f)` on its root
     * (`InAppCameraDialog`), and zIndex has to hold for hit-testing and not only for drawing.
     *
     * A semantic click would pass either way — it invokes the node's own action and bypasses
     * hit-testing entirely (CLAUDE.md) — so this is a real touch at root coordinates, twice, at the
     * **same point**. The first is the positive control: with the camera closed that point really
     * does reach the Album's Camera button and open the camera, so a "nothing happened" second
     * result cannot be a touch that simply missed. The second is the claim.
     */
    @Test
    fun `with the camera open, a real touch where the Camera button sits is taken by the camera and not by the button behind it`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_VIEW_ALBUM_TAG).performClick() // J2 T3: the album view toggle, was the "Album" sub-tab
        composeRule.waitForIdle()

        // J2 (second coder): the album's Camera button became the Add photo floating button's Take
        // photo item (owner ruling "Menu of both"). The point under test is now the floating button,
        // the album control that stays on screen; the camera opens through its menu.
        val button = composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG).getBoundsInRoot()
        val x = (button.left + button.right) / 2
        val y = (button.top + button.bottom) / 2

        // Positive control: this exact point is live when the camera is not covering it.
        tapAtRoot(x, y)
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_MENU_TAKE_PHOTO_TAG).performTouchInput { click(center) }
        composeRule.waitForIdle()
        assertEquals("the control: the touch reached the button", listOf(InAppCameraTarget.ALBUM), opened)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)

        // The claim: the same point, now under the camera, does not reach the button.
        tapAtRoot(x, y)

        assertEquals("the camera took the touch; the button behind it never fired", listOf(InAppCameraTarget.ALBUM), opened)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        assertEquals("and nothing behind it closed the camera either", 0, closed)
    }

    /** A touch at a point of the whole screen, hit-tested from the root as a finger is — never a semantic click on a node. */
    private fun tapAtRoot(x: Dp, y: Dp) {
        composeRule.onRoot().performTouchInput { click(Offset(x.toPx(), y.toPx())) }
        composeRule.waitForIdle()
    }

    @Test
    fun `the Album's Camera button opens the camera for the Album`() {
        setScreen()
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_VIEW_ALBUM_TAG).performClick() // J2 T3: the album view toggle, was the "Album" sub-tab
        // J2 (second coder): the album's Camera button is now Add photo's Take photo item.
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_TAG).performClick()
        composeRule.onNodeWithTag(com.zynergylabs.forager.app.ui.log.ENTRIES_FAB_MENU_TAKE_PHOTO_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(InAppCameraTarget.ALBUM), opened)
        shoot(expectedTotal = 1)
        assertEquals(1, albumPhotos.size)
        assertEquals(0, cartographyPhotos.size + logEntryPhotos.size)
    }

    @Test
    fun `without the CAMERA permission the tap asks for it and opens nothing`() {
        setScreen(cameraPermissionGranted = false)
        openEditorCamera()

        assertEquals(emptyList<InAppCameraTarget>(), opened)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }

    /**
     * Dispatch 2026-10-13-03 (RECORD -968): an install that had "Lock camera to portrait" on before the option was removed
     * gets the camera every install gets: since RECORD -980 and -981 its window is held portrait (was: "a camera whose window
     * follows the device", asserting SENSOR, before the owner's correction). The old value is put where the removed repository kept
     * it (`camera_orientation_preferences`, key `camera.lock_to_portrait`) and read back first, so the install this
     * describes is the one on disk. Nothing reads that file any more, so this cannot fail on this build; it states the
     * case. Written first against the code that still had the option, where it handed the screen the on value the
     * ViewModel loaded and failed (PORTRAIT). Its PORTRAIT form was then written first against `cdf8b8b1`, where it failed
     * with SENSOR.
     */
    @Test
    fun `an install that had the portrait lock on gets the held portrait camera`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val oldFile = app.preferencesDataStoreFile("camera_orientation_preferences")
        oldFile.delete()
        val oldKey = booleanPreferencesKey("camera.lock_to_portrait")
        val seedScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val oldStore = PreferenceDataStoreFactory.create(scope = seedScope) { oldFile }
            runBlocking {
                oldStore.edit { it[oldKey] = true }
                assertEquals("precondition: the old on value is on disk", true, oldStore.data.first()[oldKey])
            }
            seedScope.cancel()

            setScreen()
            openEditorCamera()
            assertEquals(
                "the camera's window is held portrait, as every install's is (RECORD -980, -981)",
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                composeRule.activity.requestedOrientation,
            )
        } finally {
            seedScope.cancel()
            oldFile.delete()
        }
    }

    /**
     * The grid mode reaches the camera through the screen, the host and the slot, and a tap on the
     * grid chip reaches the screen's callback — the path `MainActivity` wires to
     * `CameraGridModeViewModel`. Without this, a screen that dropped either would pass every camera
     * test, since those build the dialog directly.
     */
    @Test
    fun `the grid mode reaches the camera, and a tap on the grid chip reaches the screen's callback`() {
        setScreen(cameraGridMode = GridMode.Grid)
        openEditorCamera()
        composeRule.onNodeWithTag(CAMERA_GRID_CHIP_TAG).assertContentDescriptionEquals(GRID_ON_LABEL)

        composeRule.onNodeWithTag(CAMERA_GRID_CHIP_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(GridMode.GridLevel), gridModeRequests)
    }

    /**
     * Settings' photo-location value reaches the camera's Location chip through the screen, the
     * host and the slot, and a tap on the chip reaches the screen's handler with the toggled value
     * (decision B8 / Closed decision D). Without this, a screen that dropped either would pass
     * every camera test, since those build the dialog directly.
     */
    @Test
    fun `the photo-location setting reaches the camera's chip, and a tap reaches the screen's handler`() {
        autoSaveLocation = true
        setScreen()
        openEditorCamera()
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_ON_LABEL)

        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).performClick()
        composeRule.waitForIdle()

        assertEquals("the handler got the toggled value", listOf(false), autoSaveLocationRequests)
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_OFF_LABEL)
    }

    /** A value changed outside the camera (in Settings) is what the chip shows at the next open. */
    @Test
    fun `a changed photo-location setting shows on the chip at the next open`() {
        autoSaveLocation = true
        setScreen()
        openEditorCamera()
        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_ON_LABEL)
        setTarget(null)
        composeRule.waitForIdle()

        autoSaveLocation = false
        setTarget(InAppCameraTarget.ALBUM)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CAMERA_LOCATION_CHIP_TAG).assertContentDescriptionEquals(LOCATION_OFF_LABEL)
        assertEquals("nothing was asked of the handler", emptyList<Boolean>(), autoSaveLocationRequests)
    }

    /**
     * Dispatch 2026-09-28-707, through the screen: a real touch opens the gear panel, and Back closes the panel without
     * closing the camera; a second Back closes the camera as before. Since dispatch 2026-10-13-03 the panel is empty
     * (RECORD -968, -972), so this no longer touches its two rows; what it held for them, that each row wrote the screen's
     * own handler, is recorded with the rows in `docs/archive/camera-portrait-lock.md`. The Location chip's write is
     * `the photo-location setting reaches the camera's chip, and a tap reaches the screen's handler`, above.
     */
    @Test
    fun `the gear opens the empty panel, and Back closes only the panel`() {
        autoSaveLocation = true
        setScreen()
        openEditorCamera()
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(0)

        touchCentreOf(CAMERA_SETTINGS_CHIP_TAG)
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(1)
        composeRule.onAllNodesWithText("No camera settings yet").assertCountEquals(1)
        assertEquals("opening the panel wrote nothing", emptyList<Boolean>(), autoSaveLocationRequests)

        composeRule.pressBackOnCamera()
        composeRule.onAllNodesWithTag(CAMERA_SETTINGS_PANEL_TAG).assertCountEquals(0)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)
        assertEquals("Back closed the panel, not the camera", 0, closed)

        composeRule.pressBackOnCamera()
        assertEquals("the next Back closes the camera", 1, closed)
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }

    /** A real touch at the centre of the node tagged [tag], hit-tested from the root. */
    private fun touchCentreOf(tag: String) {
        val bounds = composeRule.onNodeWithTag(tag).getBoundsInRoot()
        tapAtRoot((bounds.left + bounds.right) / 2, (bounds.top + bounds.bottom) / 2)
    }

    /** The holder is what closes it: clearing the target from outside, as the ViewModel would, removes the dialog. */
    @Test
    fun `the dialog follows the holder's target`() {
        setScreen()
        openEditorCamera()
        setTarget(null)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(0)
    }
}

private val REGION = Region(lat = 45.326, lng = -122.634, radiusKm = 15)

private fun sighting(index: Int) = Sighting(
    observationId = index.toLong(),
    taxonId = 100L + index,
    scientificName = "Species $index",
    commonName = "Species $index",
    lat = REGION.lat + index * 0.001,
    lng = REGION.lng + index * 0.001,
    observedOn = LocalDate.of(2025, 8, 1),
    photoUrl = null,
)

private val SEARCHED_STATE = AvailabilityUiState(region = REGION, sightings = List(4) { sighting(it) })

