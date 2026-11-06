package com.zynergylabs.forager.app.ui.log

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import com.zynergylabs.forager.app.photo.oriented
import com.zynergylabs.forager.app.photo.readPhotoOrientation
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Decodes a photo off app-private storage and renders it, filling [modifier]'s given size either
 * way — or a plain placeholder box if decoding fails (a missing file, a corrupt image, an I/O
 * error). Workstream G2 (`docs/plans/pr26-rework.md`): this is the single decode-and-render
 * implementation `LogEntryDetailScreen`'s `LogPhotoThumbnail`, `LogGalleryScreen`'s
 * (former) `GalleryCoverThumbnail`, and `LogEntryReportScreen`'s `ReportPhotoThumbnail` each
 * hand-rolled separately before this — one of them cross-referenced the other two in its own doc
 * comment as "same decode pattern," confirming the duplication was known, not accidental drift.
 * Deliberately just the decode-and-render piece, not a full tile: every prior call site wraps this
 * in its own outer `Box` for sizing (a fixed-dp square, or `fillMaxSize` inside an
 * already-sized parent) and, in [LogPhotoThumbnail]'s case, an overlaid remove button — none of
 * that belongs inside a component meant to be reused by call sites with different chrome around
 * the same underlying photo. This shape is also why G3 can add a selection affordance later (a
 * checkmark overlay, a clickable modifier) by wrapping this in its own `Box`, without touching
 * decoding itself — the dispatch's own requirement that this stay extensible without gaining
 * selection now.
 *
 * A decode failure is logged, never silently swallowed the way all three predecessors did — via
 * raw [Log.w], the same choice [MushroomLogViewModel] already made deliberately for this feature
 * area, not the DI'd `ErrorLog` seam [AvailabilityViewModel]/`TrackRecordingViewModel` use
 * elsewhere: this is a `@Composable`, not a ViewModel, so there's no constructor to inject a seam
 * into, and Robolectric's `ShadowLog` already lets a test assert on a raw `Log.w` call without one.
 *
 * **Callers must give it bounded size constraints** (`fillMaxSize`, `size`, or a width and a
 * height). Until the photo loads this draws the same `Image` with a flat-colour painter so that a
 * caller's click stays on one layout node (see the comment at the `Image`), and an `Image` whose
 * painter has no intrinsic size fills whatever bounded space it is given, where the old placeholder
 * `Box` wrapped to nothing. Every call site in `main/` already bounds it; an unsized call would show
 * a placeholder that fills its parent and then jumps to the photo's size.
 *
 * **Decoded to the cell it is shown in** (dispatch 2026-09-28-658; the owner: "Thumbnails are sized
 * to the screen they're shown on"). The photo is decoded once its laid-out size is known, through
 * [decodeThumbnail], at the coarsest sampling that still covers that size, never past the viewer's
 * own [VIEWER_MAX_EDGE_PX]. Until then, and for a size of zero, nothing is decoded and the
 * placeholder shows. Decoded once per photo, at the first size it is laid out at. This replaced a fixed `inSampleSize` of 4 whatever the source: a 12 MP photo
 * then decoded at 1008×756 for a 40 dp row icon, and a large import at many megabytes per cell.
 * Imported photos keep their files and go through the same decode.
 */
@Composable
internal fun DecodedPhoto(
    relativePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Log photo",
    /**
     * The whole photo in its own shape, uncropped, instead of a crop filling the caller's cell: the find page's photo
     * (RECORD -871, the owner: "Under the title, whole photo, next one peeking (Recommended)"). The caller gives a
     * width; this takes the photo's own height for it once decoded, and a 4:3 placeholder until then. A caller that
     * also caps the height gets the whole photo inside the cap, letterboxed (nothing cropped). `false`, the default,
     * is every other caller's crop, unchanged.
     */
    wholePhoto: Boolean = false,
) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
    // The laid-out size's longer edge, in pixels: what the decode is sized to. 0 until measured.
    var cellEdgePx by remember { mutableIntStateOf(0) }
    LaunchedEffect(relativePath) {
        // Once per photo, at the first size it is laid out at, as the decode was once per photo
        // before. Not again on every size change: an unsized caller grows to the decoded bitmap's
        // own size, and re-decoding for that would climb to full resolution.
        val edge = snapshotFlow { cellEdgePx }.first { it > 0 }
        bitmap = withContext(PhotoDecodeDispatcher.current) {
            runCatching {
                decodeThumbnail(File(context.filesDir, relativePath), edge)
            }.onFailure { error ->
                Log.w(TAG, "Couldn't decode photo at '$relativePath'.", error)
            }.getOrNull()?.asImageBitmap()
        }
    }

    // One Image for both states, only its painter (and whether it is described) changes. Before
    // dispatch 2026-09-28-317 a placeholder Box and an Image were two different layout nodes carrying
    // the same caller modifier, so a caller's clickable/combinedClickable was detached and re-attached
    // when the decode landed, and a tap or long-press in flight at that moment was lost
    // (docs/audits/2026-09-30-ci-flake-diagnosis.md). Rejected: a wrapper Box at each call site, or an
    // outer node here with a switching child, because either moves the click above the Image and
    // the merged node loses Role.Image (a parent's role wins the merge). This keeps the click and the
    // Image on one node, so the semantics are what they were: no description and no role while
    // loading (Image adds both only for a non-null description), description plus role Image once
    // loaded. DecodedPhotoSemanticsTest pins that against the old build's dumps.
    val loaded = bitmap
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    val painter = remember(loaded, placeholderColor) {
        if (loaded != null) BitmapPainter(loaded, filterQuality = FilterQuality.Low) else ColorPainter(placeholderColor)
    }
    // wholePhoto: the decoded bitmap is already upright (decodeThumbnail turns it), so its own width over height is the
    // photo's shape. aspectRatio adds no semantics either.
    val shape = if (wholePhoto) {
        Modifier.aspectRatio(loaded?.let { it.width.toFloat() / it.height.toFloat() } ?: WHOLE_PHOTO_PLACEHOLDER_ASPECT)
    } else {
        Modifier
    }
    Image(
        painter = painter,
        contentDescription = if (loaded != null) contentDescription else null,
        // onSizeChanged adds no semantics, so the node DecodedPhotoSemanticsTest pins is unchanged.
        modifier = modifier.then(shape).onSizeChanged { size -> cellEdgePx = max(size.width, size.height) },
        contentScale = if (wholePhoto) ContentScale.Fit else ContentScale.Crop,
    )
}

/**
 * Decodes [file] for a cell whose longer edge is [cellEdgePx], at [thumbnailSampleSize], and turns it
 * upright. Reads the file's dimensions with `inJustDecodeBounds` first, then decodes once, as the
 * viewer's [decodeBoundedPhoto] does. Throws rather than returning `null` so the caller logs the cause.
 */
internal fun decodeThumbnail(file: File, cellEdgePx: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        error("BitmapFactory could not read the dimensions of '${file.name}' (${bounds.outWidth}x${bounds.outHeight})")
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = thumbnailSampleSize(width = bounds.outWidth, height = bounds.outHeight, cellEdgePx = cellEdgePx)
    }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options)
        ?: error("BitmapFactory.decodeFile returned null for '${file.name}'")
    // EXIF-orientation-display dispatch: BitmapFactory ignores the orientation tag, so a capture
    // stored in the sensor's landscape frame came out sideways here. Turned at display time on the
    // sampled bitmap (see Bitmap.oriented for the cost); the file is never rewritten. Every
    // thumbnail site in the app goes through this one decode, so this is the one fix for all of them.
    return decoded.oriented(readPhotoOrientation(file))
}

/**
 * The `inSampleSize` for a [width]×[height] photo shown in a cell whose longer edge is [cellEdgePx]:
 * the largest power of two that still leaves the photo's **shorter** edge at least [cellEdgePx],
 * because the cell crops (`ContentScale.Crop` fills it from the shorter edge, whichever way the
 * photo is turned). Then halved further while the longer edge is past [VIEWER_MAX_EDGE_PX], the
 * viewer's own limit, so a long panorama is bounded too. Powers of two only, as `BitmapFactory`
 * honours nothing else ([viewerSampleSize] gives the same reason). Pure, so a test checks it
 * without a bitmap.
 */
internal fun thumbnailSampleSize(width: Int, height: Int, cellEdgePx: Int): Int {
    require(cellEdgePx > 0) { "cellEdgePx must be positive, was $cellEdgePx" }
    var sampleSize = 1
    while (min(width, height) / (sampleSize * 2) >= cellEdgePx) sampleSize *= 2
    while (max(width, height) / sampleSize > VIEWER_MAX_EDGE_PX) sampleSize *= 2
    return sampleSize
}

private const val TAG = "LogPhotoDecode"

/** [DecodedPhoto] with `wholePhoto`, before its photo has decoded: a 4:3 frame, the shape of most phone camera photos held sideways. */
private const val WHOLE_PHOTO_PLACEHOLDER_ASPECT = 4f / 3f
