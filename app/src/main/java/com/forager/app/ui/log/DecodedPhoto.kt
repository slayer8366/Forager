package com.forager.app.ui.log

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 */
@Composable
internal fun DecodedPhoto(
    relativePath: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = "Log photo",
) {
    val context = LocalContext.current
    var bitmap by remember(relativePath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relativePath) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = DECODE_SAMPLE_SIZE }
                BitmapFactory.decodeFile(File(context.filesDir, relativePath).absolutePath, options)
                    ?: error("BitmapFactory.decodeFile returned null for '$relativePath'")
            }.onFailure { error ->
                Log.w(TAG, "Couldn't decode photo at '$relativePath'.", error)
            }.getOrNull()?.asImageBitmap()
        }
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private const val DECODE_SAMPLE_SIZE = 4
private const val TAG = "LogPhotoDecode"
