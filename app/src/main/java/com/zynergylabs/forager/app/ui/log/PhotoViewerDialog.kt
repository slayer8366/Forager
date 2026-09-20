package com.zynergylabs.forager.app.ui.log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.photo.oriented
import com.zynergylabs.forager.app.photo.readPhotoOrientation
import com.zynergylabs.forager.app.ui.theme.Spacing
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Full-screen viewer for a find's photos (full-screen-photo-viewer dispatch, 2026-09-12). Opened by
 * tapping a thumbnail on [LogEntryDetailScreen] or [LogEntryReportScreen]; fit to screen on open,
 * pinch to zoom, drag to pan while zoomed, double-tap to jump between fit and [DOUBLE_TAP_SCALE].
 * Dismissed by the system back gesture and by its own close control — the dispatch asks for both,
 * not back alone. A viewer only: no editing, cropping, rotation or sharing.
 *
 * **Why a [Dialog] and not an overlay inside the screen.** Both host screens are composed into a
 * `weight(1f)` slot under [JournalTab]'s tab row and above the bottom nav, so "full screen" from
 * inside either would mean the content slot, not the display. Hoisting a viewer state up through
 * [JournalTab]/[LogPanel]/[RecordsTab] to the scaffold would cover the display but thread a new
 * parameter through four composables for one modal. A [Dialog] with `usePlatformDefaultWidth =
 * false` and `decorFitsSystemWindows = false` is its own window over everything, including the
 * nav bars, and owns its back handling: the innermost-enabled-`BackHandler`-wins convention this
 * codebase relies on ([CartographyEntryReportScreen]'s fullscreen map, the find-editing handlers in
 * [JournalTab]/[LogPanel]) is untouched because the dialog window consumes the press before any of
 * them see it. The cost is that the Activity-dispatcher route the existing tests use for back
 * (`onBackPressedDispatcher.onBackPressed()`) does not reach a dialog window under Robolectric
 * either — the same caveat [com.zynergylabs.forager.app.ui.availability.AvailabilityScreen]'s
 * exit-prompt tests already record — so [PhotoViewerDialogTest] sends the back key to the dialog
 * window itself.
 *
 * **Several photos: previous/next controls, not swiping.** The dispatch made swiping optional and
 * asked for a report if it added significant complexity. It does: a `HorizontalPager` and a
 * pinch-zoom page both want horizontal drags, so the page has to hand drags to the pager only at
 * fit scale and keep them while zoomed, which is exactly the Compose gesture-routing region the
 * suite is known to be blind on. Previous/next buttons with a "2 / 3" counter give the same reach
 * with no gesture arbitration at all; zoom resets when the photo changes, since a pan offset for
 * one photo means nothing on the next.
 *
 * **Memory.** One bitmap is held at a time — the current photo's, decoded by [decodeBoundedPhoto]
 * with [VIEWER_MAX_EDGE_PX] as an explicit operating limit on its longest edge — and it is dropped
 * when the photo changes or the dialog leaves composition. See that function for why the limit is
 * what it is.
 */
@Composable
internal fun PhotoViewerDialog(
    photos: List<LogPhoto>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    // rememberSaveable, not remember: the host Activity declares no configChanges, so a rotation
    // while zoomed in on a gill photo recreates it, and the host's own viewer state comes back
    // through rememberSaveable too — losing which of several photos was open would be a reset of
    // something the user set (CLAUDE.md, UX defaults). Clamped rather than trusted: the list can
    // shrink underneath a saved index.
    var currentIndex by rememberSaveable(initialIndex) { mutableIntStateOf(initialIndex) }
    if (photos.isEmpty()) {
        // Nothing to show — every photo was removed from under an open viewer. Closes rather than
        // rendering an empty black window with a close button as the only way out.
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    val index = currentIndex.coerceIn(0, photos.lastIndex)
    val photo = photos[index]

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Plain black, deliberately not a theme surface: this is the one place a photo is
                // inspected for what it actually looks like (gill colour, bruising, a spore print's
                // tint), and a tinted backdrop shifts perceived colour. Same reason every photo
                // viewer is black; not a design-token decision.
                .background(Color.Black)
                .testTag(PHOTO_VIEWER_TAG),
        ) {
            ZoomablePhoto(relativePath = photo.relativePath, modifier = Modifier.fillMaxSize())

            // Controls sit inside the real system-bar insets; the photo behind them does not.
            // Robolectric reports zero insets, so this padding is device-only by construction
            // (CLAUDE.md, known pitfalls) — the tests below say nothing about it.
            Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ViewerControl(
                    onClick = onDismiss,
                    description = "Close photo",
                    modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm),
                ) { Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White) }

                if (photos.size > 1) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ViewerControl(
                            onClick = { currentIndex = (index - 1 + photos.size) % photos.size },
                            description = "Previous photo",
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White) }
                        Text(
                            text = "${index + 1} / ${photos.size}",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = Spacing.md).testTag(PHOTO_VIEWER_COUNTER_TAG),
                        )
                        ViewerControl(
                            onClick = { currentIndex = (index + 1) % photos.size },
                            description = "Next photo",
                        ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White) }
                    }
                }
            }
        }
    }
}

/** A white glyph on the theme's scrim, the same legibility-over-any-photo treatment [LogEntryDetailScreen]'s remove glyph uses, at [IconButton]'s ordinary 48dp target. */
@Composable
private fun ViewerControl(
    onClick: () -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        Box(
            modifier = Modifier
                .size(VIEWER_CONTROL_SCRIM_SIZE_DP.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = REMOVE_GLYPH_SCRIM_ALPHA)),
            contentAlignment = Alignment.Center,
        ) { icon() }
    }
}

/**
 * The photo itself: decoded off the IO dispatcher, drawn fit-to-screen, then scaled and panned
 * through a `graphicsLayer` driven by [detectTransformGestures]. The pointer input sits *outside*
 * the layer in the modifier chain so the gesture region stays the whole viewport whatever the
 * current transform; the transform is drawing-only.
 *
 * Zoom keeps the point under the fingers fixed: with the layer's origin at the centre, a content
 * point `p` lands at `p·s + t`, so holding centroid `c` still through a scale change `z` needs
 * `t' = c − (c − t)·z`. Pan is then added and the result clamped so the image never leaves the
 * viewport once it is larger than it — at fit scale it is centred with no pan at all. The current
 * zoom is exposed as the node's `stateDescription`, which is what TalkBack announces for it and
 * what [PhotoViewerDialogTest] reads back.
 */
@Composable
private fun ZoomablePhoto(relativePath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
    var rotationDegrees by remember(relativePath) { mutableIntStateOf(0) }
    var decodeFailed by remember(relativePath) { mutableStateOf(false) }
    LaunchedEffect(relativePath) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching { decodeBoundedPhoto(File(context.filesDir, relativePath), VIEWER_MAX_EDGE_PX) }
                .onFailure { error -> Log.w(TAG, "Couldn't decode photo at '$relativePath' for the viewer.", error) }
                .getOrNull()
        }
        bitmap = decoded?.bitmap?.asImageBitmap()
        rotationDegrees = decoded?.rotationDegrees ?: 0
        decodeFailed = decoded == null
    }

    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(relativePath) { mutableFloatStateOf(1f) }
    var offset by remember(relativePath) { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier.onSizeChanged { viewport = it }, contentAlignment = Alignment.Center) {
        val loaded = bitmap
        when {
            loaded != null -> {
                // EXIF-orientation-display dispatch: a 90°/270° photo is drawn by rotating the
                // layer, not by allocating a turned copy of a bitmap this large. ContentScale.Fit
                // has already fitted the *unrotated* bitmap, so the layer also scales by
                // viewerRotationFit's ratio to make the rotated result fit the viewport instead.
                val fitCorrection = viewerRotationFit(loaded.width, loaded.height, rotationDegrees, viewport.width, viewport.height)
                val swaps = rotationDegrees % 180 != 0
                val shownBaseWidth = if (swaps) loaded.height else loaded.width
                val shownBaseHeight = if (swaps) loaded.width else loaded.height
                fun clamped(candidate: Offset, atScale: Float): Offset {
                    val fit = min(viewport.width / shownBaseWidth.toFloat(), viewport.height / shownBaseHeight.toFloat())
                    val shownWidth = shownBaseWidth * fit * atScale
                    val shownHeight = shownBaseHeight * fit * atScale
                    // An explicit zero when the image fits its axis, not coerceIn(-0f, 0f): that
                    // returns -0.0f for a leftward drag, which is equal to nothing but itself.
                    fun axis(value: Float, shown: Float, available: Int): Float {
                        val slack = (shown - available) / 2f
                        return if (slack <= 0f) 0f else value.coerceIn(-slack, slack)
                    }
                    return Offset(axis(candidate.x, shownWidth, viewport.width), axis(candidate.y, shownHeight, viewport.height))
                }
                val centre = Offset(viewport.width / 2f, viewport.height / 2f)
                val zoomLabel = String.format(Locale.getDefault(), "Zoom %.1f×", scale)

                Image(
                    bitmap = loaded,
                    contentDescription = VIEWER_PHOTO_DESCRIPTION,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics {
                            stateDescription = zoomLabel
                            this[PhotoViewerPanKey] = offset
                        }
                        .pointerInput(loaded) {
                            detectTransformGestures { centroid, pan, zoom, _ ->
                                val nextScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                val applied = nextScale / scale
                                val c = centroid - centre
                                offset = clamped(c - (c - offset) * applied + pan, nextScale)
                                scale = nextScale
                            }
                        }
                        .pointerInput(loaded) {
                            detectTapGestures(
                                onDoubleTap = { tap ->
                                    if (abs(scale - 1f) < 0.01f) {
                                        val c = tap - centre
                                        offset = clamped(c * (1f - DOUBLE_TAP_SCALE), DOUBLE_TAP_SCALE)
                                        scale = DOUBLE_TAP_SCALE
                                    } else {
                                        scale = 1f
                                        offset = Offset.Zero
                                    }
                                },
                            )
                        }
                        .graphicsLayer {
                            rotationZ = rotationDegrees.toFloat()
                            scaleX = scale * fitCorrection
                            scaleY = scale * fitCorrection
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            decodeFailed -> Text(
                text = "Couldn't load this photo.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(PHOTO_VIEWER_FAILED_TAG),
            )

            else -> CircularProgressIndicator()
        }
    }
}

/**
 * What the viewer draws: the decoded bitmap plus the clockwise rotation the layer must apply to
 * show it upright. A mirrored orientation (EXIF 2, 4, 5, 7 — editing artefacts, not what a camera
 * writes) is baked into [bitmap] at decode and [rotationDegrees] is then 0; a pure rotation is
 * left to the draw transform so no second bitmap of viewer size is ever allocated.
 */
internal class ViewerPhoto(val bitmap: Bitmap, val rotationDegrees: Int) {
    /** The size the photo occupies once turned upright, before fitting. */
    val displayWidth: Int get() = if (rotationDegrees % 180 != 0) bitmap.height else bitmap.width
    val displayHeight: Int get() = if (rotationDegrees % 180 != 0) bitmap.width else bitmap.height
}

/**
 * Decodes [file] with the smallest power-of-two `inSampleSize` that brings its longest edge to at
 * most [maxEdgePx]. Reads the file's dimensions with `inJustDecodeBounds` first, then decodes once.
 * The only EXIF read is the orientation tag, through [readPhotoOrientation] (which can return
 * nothing else); nothing writes to the file. Throws rather than returning `null` so the caller's
 * `runCatching` logs the cause.
 */
internal fun decodeBoundedPhoto(file: File, maxEdgePx: Int): ViewerPhoto {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        error("BitmapFactory could not read the dimensions of '${file.name}' (${bounds.outWidth}x${bounds.outHeight})")
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = viewerSampleSize(width = bounds.outWidth, height = bounds.outHeight, maxEdgePx = maxEdgePx)
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("BitmapFactory.decodeFile returned null for '${file.name}'")
    val orientation = readPhotoOrientation(file)
    return if (orientation.mirrored) {
        ViewerPhoto(decoded.oriented(orientation), rotationDegrees = 0)
    } else {
        ViewerPhoto(decoded, rotationDegrees = orientation.rotationDegrees)
    }
}

/**
 * The factor the viewer's layer multiplies into its scale so a bitmap `ContentScale.Fit` has
 * fitted *unrotated* fits the viewport once rotated by [rotationDegrees]. 1 for 0°/180°. For
 * 90°/270° it is `fitRotated / fitUnrotated`, where each fit is `min(viewportW / w, viewportH / h)`
 * with the bitmap's own or its swapped dimensions. Pure, so [PhotoViewerDecodeTest] checks it
 * without a composition; 1 whenever any dimension is zero, since there is nothing to fit yet.
 */
internal fun viewerRotationFit(bitmapWidth: Int, bitmapHeight: Int, rotationDegrees: Int, viewportWidth: Int, viewportHeight: Int): Float {
    if (rotationDegrees % 180 == 0) return 1f
    if (bitmapWidth <= 0 || bitmapHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 1f
    val unrotated = min(viewportWidth / bitmapWidth.toFloat(), viewportHeight / bitmapHeight.toFloat())
    val rotated = min(viewportWidth / bitmapHeight.toFloat(), viewportHeight / bitmapWidth.toFloat())
    return rotated / unrotated
}

/**
 * The smallest power of two that divides `max(width, height)` down to at most [maxEdgePx]. Powers
 * of two only, because that is what `BitmapFactory` honours — it rounds any other value down to one.
 * Integer division on purpose: Skia's `GetSampledDimension` is `srcDimension / sampleSize`, floored,
 * so a 16385-wide image at `inSampleSize = 4` decodes 4096 wide and needs no further halving.
 * Pure arithmetic, so [PhotoViewerDecodeTest] checks it without a bitmap.
 */
internal fun viewerSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
    require(maxEdgePx > 0) { "maxEdgePx must be positive, was $maxEdgePx" }
    var sampleSize = 1
    while (max(width, height) / sampleSize > maxEdgePx) sampleSize *= 2
    return sampleSize
}

/**
 * Operating limit on the decoded bitmap's longest edge, in pixels. 4096 is the floor of
 * `GL_MAX_TEXTURE_SIZE` on every OpenGL ES 3.0 device, and a bitmap past a device's maximum is one
 * the hardware renderer cannot upload and draws as nothing — the classic "Bitmap too large to be
 * uploaded into a texture" failure. Below it, a 12 MP camera photo (4032×3024) decodes at full
 * resolution, ~49 MB at ARGB_8888; a 50 MP one halves to 4080×3060, ~50 MB. One at a time (see the
 * dialog's own doc comment), so the ceiling is one such bitmap, not a gallery of them. ARGB_8888
 * rather than RGB_565 on purpose: colour is part of what a forager is inspecting.
 */
internal const val VIEWER_MAX_EDGE_PX = 4096

/** Pinch ceiling relative to fit. Past this the source pixels are already spread wider than the screen for any photo the limit above lets through. */
internal const val MAX_SCALE = 5f

/** Where a double-tap from fit lands — enough to read gill attachment on a phone without leaving the subject. */
internal const val DOUBLE_TAP_SCALE = 2.5f

private const val VIEWER_CONTROL_SCRIM_SIZE_DP = 36

/**
 * The pan the layer is drawing with, published on the photo's semantics node. Zoom is user-visible
 * through `stateDescription`; pan has no user-visible text and the layer's own transform is not
 * readable from a semantics node whose modifiers sit outside it, so this is the one seam
 * [PhotoViewerDialogTest] has for asserting that a drag moved the photo and a drag at fit did not.
 */
internal val PhotoViewerPanKey = SemanticsPropertyKey<Offset>("PhotoViewerPan")

internal const val PHOTO_VIEWER_TAG = "photo-viewer"
internal const val PHOTO_VIEWER_COUNTER_TAG = "photo-viewer-counter"
internal const val PHOTO_VIEWER_FAILED_TAG = "photo-viewer-failed"
internal const val VIEWER_PHOTO_DESCRIPTION = "Full-screen photo"

private const val TAG = "PhotoViewer"
