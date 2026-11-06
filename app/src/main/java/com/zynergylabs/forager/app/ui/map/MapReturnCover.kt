package com.zynergylabs.forager.app.ui.map

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.Region
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow

/*
 * RECORD -752 and -761 (dispatch 2026-09-28-755, item 4; the owner: "Picture cover, this build (Recommended)").
 *
 * **Why.** Every return to the Maps tab builds a new MapLibre MapView (the tab leaves composition, and the map with it). Seen on
 * the S22 with screenrecords and the ForagerMapsComeback log: about 200 ms of bare background, one or two black frames with only
 * the puck, then tiles; the camera restore itself lands within the new map's first frames, at the right place. So the blank is
 * the rebuild, not the camera.
 *
 * **What.** As the Maps tab starts to leave, the map takes a picture of itself (MapLibre's own `snapshot`, which reads its GL
 * surface; a Compose capture cannot, the map being a SurfaceView), kept on [MapCameraMemory.cover] with the camera it showed. The
 * map that comes back draws that picture over itself until its own first fully rendered frame after its style has loaded, at the
 * restored camera, then fades it out on the state-crossfade token (cut under reduced motion) and lets it go.
 *
 * **When not** ([coverDecision]), each logged and each falling back to the map drawing itself as before: no picture (the snapshot
 * did not arrive before the map went, or this is the first map); the picture is of another camera than the one the map is about
 * to restore; or the map is about to move anyway (a search frame, a new search region, a locate target or a camera request
 * pending), so the picture would show a place the map is leaving. If the new map has not drawn a full frame within
 * [MAP_COVER_READY_CAP_MS], the cover goes and that is logged. If the window is not the picture's size (a turn since), likewise.
 *
 * **Memory.** One picture at a time: a new one replaces (and recycles) the old, and the map that uses it takes it off the memory
 * at once and drops it when the cover has gone. Full window size, ARGB (about 17 MB on the S22) while it is held.
 *
 * **Touches.** The cover is a plain image with no pointer input, so a touch on it goes to the map beneath, exactly as with no
 * cover (MapReturnCoverTest, real touches at several points).
 */

/** The picture a leaving map took of itself, and the camera it showed then. */
class MapCover(val bitmap: Bitmap, val camera: MapCameraSnapshot)

/** Whether a returning map shows the cover, or why not. */
enum class CoverDecision { SHOW, NO_PICTURE, NO_CAMERA, CAMERA_MOVED, MOVE_PENDING }

/**
 * Whether a map coming back draws [pictureCamera]'s picture over itself. [saved] is the camera it will restore, [target] the
 * region and focus it is handed now, [searchFrameRequestId] against [appliedSearchFrameId] a search frame still to apply, and
 * [cameraRequestPending] a camera request it would apply.
 */
fun coverDecision(
    pictureCamera: MapCameraSnapshot?,
    saved: MapCameraSnapshot?,
    target: Pair<Region, LatLng?>,
    searchFrameRequestId: Int,
    appliedSearchFrameId: Int,
    cameraRequestPending: Boolean,
): CoverDecision = when {
    pictureCamera == null -> CoverDecision.NO_PICTURE
    saved == null -> CoverDecision.NO_CAMERA
    !camerasMatch(pictureCamera, saved) -> CoverDecision.CAMERA_MOVED
    searchFrameMove(saved.following, searchFrameRequestId, appliedSearchFrameId) != SearchFrameMove.NONE -> CoverDecision.MOVE_PENDING
    cameraRequestPending -> CoverDecision.MOVE_PENDING
    shouldMoveCameraToTarget(saved.following, target, saved.appliedTarget) -> CoverDecision.MOVE_PENDING
    else -> CoverDecision.SHOW
}

/**
 * Whether two cameras show the same view to the eye: zoom within 0.05, bearing and tilt within 2 degrees, and the centre within
 * [COVER_CENTRE_TOLERANCE_PX] screen pixels at that zoom (MapLibre's 512 px tiles). A following camera idles over and over as the
 * puck creeps, so equality would almost never hold.
 */
fun camerasMatch(a: MapCameraSnapshot, b: MapCameraSnapshot): Boolean {
    if (abs(a.zoom - b.zoom) > 0.05) return false
    val bearing = abs(((a.bearing - b.bearing) % 360 + 540) % 360 - 180)
    if (bearing > 2.0 || abs(a.tilt - b.tilt) > 2.0) return false
    val metresPerPx = METRES_PER_PX_AT_ZOOM_0 * cos(Math.toRadians(a.target.lat)) / 2.0.pow(a.zoom)
    val dy = (a.target.lat - b.target.lat) * METRES_PER_DEGREE
    val dx = (a.target.lng - b.target.lng) * METRES_PER_DEGREE * cos(Math.toRadians(a.target.lat))
    return hypot(dx, dy) / metresPerPx <= COVER_CENTRE_TOLERANCE_PX
}

/**
 * The cover over a returning map: [picture] at full size until [handedOver], then faded out on the state-crossfade token (cut
 * under reduced motion), after which [onGone] runs. No pointer input and no semantics: it takes no touch.
 */
@Composable
fun MapReturnCoverLayer(picture: ImageBitmap, handedOver: Boolean, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val reduceMotion = LocalReduceMotion.current
    val latestOnGone by rememberUpdatedState(onGone)
    val alpha by animateFloatAsState(
        targetValue = if (handedOver) 0f else 1f,
        animationSpec = MotionTokens.stateCrossfadeSpec(),
        label = "mapReturnCover",
    )
    val shown = if (reduceMotion && handedOver) 0f else alpha
    LaunchedEffect(handedOver, shown <= 0f) { if (handedOver && shown <= 0f) latestOnGone() }
    if (shown > 0f) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = shown }.testTag(MAP_RETURN_COVER_TAG),
        )
    }
}

/** How long a returning map has to draw a full frame before its cover goes anyway (logged). */
const val MAP_COVER_READY_CAP_MS = 1_500L

/** The cover's tag, for tests. */
const val MAP_RETURN_COVER_TAG = "map-return-cover"

private const val COVER_CENTRE_TOLERANCE_PX = 16.0
private const val METRES_PER_PX_AT_ZOOM_0 = 78_271.517
private const val METRES_PER_DEGREE = 111_320.0
