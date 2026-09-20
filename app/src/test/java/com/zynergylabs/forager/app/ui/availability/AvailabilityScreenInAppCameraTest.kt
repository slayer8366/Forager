package com.zynergylabs.forager.app.ui.availability

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
import com.zynergylabs.forager.app.domain.model.CartographyEntry
import com.zynergylabs.forager.app.domain.model.PhotoSource
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.domain.model.Sighting
import com.zynergylabs.forager.app.photo.CameraCapturePhotoSource
import com.zynergylabs.forager.app.photo.FakeCameraCaptureSession
import com.zynergylabs.forager.app.photo.FileProviderCacheReset
import com.zynergylabs.forager.app.ui.log.CAMERA_SHUTTER_TAG
import com.zynergylabs.forager.app.ui.log.pressBackOnCamera
import com.zynergylabs.forager.app.ui.log.CartographyUiState
import com.zynergylabs.forager.app.ui.log.IN_APP_CAMERA_TAG
import com.zynergylabs.forager.app.ui.log.InAppCameraDialog
import com.zynergylabs.forager.app.ui.log.InAppCameraSlot
import com.zynergylabs.forager.app.ui.log.InAppCameraTarget
import com.zynergylabs.forager.app.ui.map.MapSlot
import java.time.LocalDate
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
 * flipping from COMPACT to MEDIUM the way a phone's rotation flips it, and the camera still
 * there afterwards with its photo still routed to the surface that asked.
 *
 * **The width flip is real, the rotation is not.** `currentWindowWidthClass` reads
 * `LocalConfiguration.screenWidthDp`, so providing a `Configuration` with that field set is what
 * moves the screen between its two trees, which is mechanism (2) on `InAppCameraHost`. Activity
 * recreation, mechanism (1), is what the ViewModel is for and is the platform's contract, not
 * composable here; the device check runs it ("Don't keep activities", and a rotation with the
 * camera open). The camera open flag is plain test state standing in for `InAppCameraViewModel`,
 * the same way `cartographyState` below stands in for `CartographyViewModel`.
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
    private var slotSawLockToPortrait: Boolean? = null
    private val cartographyPhotos = mutableListOf<PhotoSource>()
    private val albumPhotos = mutableListOf<PhotoSource>()
    private val logEntryPhotos = mutableListOf<PhotoSource>()

    private var setWidthDp: (Int) -> Unit = {}
    private var setTarget: (InAppCameraTarget?) -> Unit = {}

    private val boxMapSlot: MapSlot = { _, _, _, _, _, _, _, _, modifier -> Box(modifier) }

    private val fakeCamera: InAppCameraSlot = { cameraCaptureFiles, lockToPortrait, onPhotoCaptured, onDismiss ->
        slotSawLockToPortrait = lockToPortrait
        val session = remember { FakeCameraCaptureSession() }
        InAppCameraDialog(
            session = session,
            cameraCaptureFiles = cameraCaptureFiles,
            lockToPortrait = lockToPortrait,
            onPhotoCaptured = onPhotoCaptured,
            onDismiss = onDismiss,
            viewfinder = { modifier -> Box(modifier) },
        )
    }

    private fun setScreen(cameraPermissionGranted: Boolean = true, lockCameraToPortrait: Boolean = false) {
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
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                AvailabilityScreen(
                    uiState = SEARCHED_STATE.copy(lockCameraToPortrait = lockCameraToPortrait),
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
                    onOfflineMapLatChanged = {},
                    onOfflineMapLngChanged = {},
                    onOfflineMapRadiusChanged = {},
                    onOfflineMapNameChanged = {},
                    onOfflineMapsOpened = {},
                    onDownloadOfflineMaps = {},
                    onDeleteOfflineRegion = {},
                    onNightModeMapsChanged = {},
                    onThemeModeChanged = {},
                    mapSlot = boxMapSlot,
                    inAppCameraTarget = target,
                    onOpenCamera = { opened += it; target = it },
                    onCloseCamera = { closed++; target = null },
                    inAppCamera = fakeCamera,
                    onAddLogPhoto = { logEntryPhotos += it },
                    onAddGalleryPhoto = { albumPhotos += it },
                    onAcquirePhotoForCartographyEntry = { cartographyPhotos += it },
                    cartographyUiState = cartographyState,
                    onStartCartographyEntry = { date ->
                        val started = CartographyEntry.draft(id = "new-cartography-entry", date = date, updatedAtEpochMillis = 0L)
                        cartographyState = cartographyState.copy(editingEntry = started, draftEntries = cartographyState.draftEntries + started)
                    },
                )
            }
        }
    }

    /** Journal → a new Cartography entry → its add-photo picker, whose Camera button is the deepest one in the compact tree. */
    private fun openEditorCamera() {
        composeRule.onNodeWithText("Journal").performClick()
        composeRule.onNodeWithContentDescription("New Cartography entry").performClick()
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

    /** The bug: on the shipped code the width flip disposed the screen holding the dialog, and the camera closed. */
    @Test
    fun `the camera survives the width-class flip a rotation causes, and its photo still routes`() {
        setScreen()
        openEditorCamera()
        composeRule.onAllNodesWithTag(IN_APP_CAMERA_TAG).assertCountEquals(1)

        setWidthDp(700) // MEDIUM: the PermanentNavigationDrawer tree replaces the compact one
        composeRule.waitForIdle()

        composeRule.onAllNodesWithText("Tools").assertCountEquals(0) // the compact bottom nav is gone: the flip happened
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

    @Test
    fun `flipping back to compact keeps the camera too`() {
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
        composeRule.onNodeWithText("Album").performClick()
        composeRule.waitForIdle()

        val button = composeRule.onNodeWithText("Camera").getBoundsInRoot()
        val x = (button.left + button.right) / 2
        val y = (button.top + button.bottom) / 2

        // Positive control: this exact point is live when the camera is not covering it.
        tapAtRoot(x, y)
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
        composeRule.onNodeWithText("Album").performClick()
        composeRule.onNodeWithText("Camera").performClick()
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

    /** Settings' "Lock camera to portrait" reaches the session through the host and the slot, unchanged, in both states. */
    @Test
    fun `the lock-camera setting reaches the camera slot as given`() {
        setScreen(lockCameraToPortrait = true)
        openEditorCamera()
        assertEquals(true, slotSawLockToPortrait)
    }

    @Test
    fun `with the setting off the slot is told off`() {
        setScreen()
        openEditorCamera()
        assertEquals(false, slotSawLockToPortrait)
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

