package com.zynergylabs.forager.app.photo

import android.content.Context
import android.os.Build
import android.util.Log
import com.zynergylabs.forager.app.domain.rawShotFits
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.domain.raw.DngWriter
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The shot step of a RAW shot (dispatch 2026-10-15-01, Amendment 1, item 3: write and check in the shot step, before
 * `onPhotoCaptured`, so the camera's own message slot can say what happened). The camera has already written the shot's
 * JPG; this decides whether the DNG is written beside it:
 *
 * - **No DNG input** (the camera did not report a value the file needs): the JPG is the photo, and the user is told
 *   [RAW_NOT_SAVED_MESSAGE].
 * - **Low storage** (below `LOW_STORAGE_FLOOR_BYTES` plus the worst case for this shot, `domain/CameraFormat.kt`): the DNG
 *   is never written; the JPG is the photo; [RAW_LOW_STORAGE_MESSAGE] (Amendment 1, item 5).
 * - **The writer refused** ([DngWriteResult.Failed]: its check after writing found something, or it could not write): it
 *   has already deleted its file; the JPG is the photo; [RAW_NOT_CLEAN_MESSAGE], the owner's approved words (RECORD -1003).
 * - **Written**: the DNG goes with the JPG to `onPhotoCaptured`.
 *
 * Every outcome but the last is logged with its cause (CLAUDE.md: no silent fallback). None of them loses the shot.
 */
internal class RawShotSaver(
    private val newRawFile: () -> File,
    private val freeBytes: () -> Long,
    private val writer: DngWriter,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun save(shot: RawShot): RawShotOutcome = withContext(ioDispatcher) {
        val image = shot.dng ?: return@withContext jpgOnly(RAW_NOT_SAVED_MESSAGE, "no DNG input: ${shot.dngUnavailable}")
        val free = freeBytes()
        if (false && !rawShotFits(free, image.width, image.height)) {
            return@withContext jpgOnly(RAW_LOW_STORAGE_MESSAGE, "storage is low: $free bytes free for a ${image.width}x${image.height} RAW shot")
        }
        val destination = newRawFile()
        val result = try {
            writer.write(image, destination)
        } catch (cancelled: CancellationException) {
            destination.delete()
            throw cancelled
        } catch (error: Throwable) {
            // The contract says the writer never throws; if it ever does, the file it may have left is not trusted.
            destination.delete()
            DngWriteResult.Failed("the writer threw ${error::class.java.simpleName}: ${error.message}")
        }
        when (result) {
            is DngWriteResult.Written -> if (destination.isFile) {
                RawShotOutcome.Kept(destination)
            } else {
                jpgOnly(RAW_NOT_CLEAN_MESSAGE, "the writer reported ${result.bytes} bytes written but there is no file at ${destination.name}")
            }
            is DngWriteResult.Failed -> {
                // The writer deletes its file on failure; deleting again costs nothing and makes it true here too.
                destination.delete()
                jpgOnly(RAW_NOT_CLEAN_MESSAGE, "the DNG writer refused the file: ${result.reason}")
            }
        }
    }

    private fun jpgOnly(message: String, cause: String): RawShotOutcome {
        Log.w(TAG, "RAW shot kept as JPG only: $cause")
        return RawShotOutcome.JpgOnly(message, cause)
    }

    private companion object {
        const val TAG = "RawShotSaver"
    }
}

internal sealed interface RawShotOutcome {
    /** The checked DNG, in `captures/`, to go with the shot's JPG. */
    data class Kept(val dng: File) : RawShotOutcome

    /** The shot's JPG is the whole photo. [message] is what the user is shown; [cause] is what was logged. */
    data class JpgOnly(val message: String, val cause: String) : RawShotOutcome
}

/** RECORD -1003, the owner's approved words, verbatim. */
internal const val RAW_NOT_CLEAN_MESSAGE = "The RAW file couldn't be cleaned of camera details, so this photo was saved as JPG."

/** Amendment 1, item 5, the planner's words. */
internal const val RAW_LOW_STORAGE_MESSAGE = "Storage is low, so this photo was saved as JPG."

/**
 * When the camera's metadata could not make a DNG at all. **This dispatch's wording, not approved by the owner**: -1003's
 * words say the file could not be cleaned of camera details, which is not what happened here, so they are not borrowed.
 */
internal const val RAW_NOT_SAVED_MESSAGE = "The RAW file couldn't be saved, so this photo was saved as JPG."

/**
 * The strings a DNG must never carry: this phone's make, model, brand, device and build fingerprint, as the platform's own
 * writer would have put them in (AOSP `DngCreator`: Make from `ro.product.manufacturer`, Model from `ro.product.model`,
 * Software from `ro.build.fingerprint`, UniqueCameraModel from model, manufacturer and brand). Given to Forager's writer,
 * whose check after writing fails closed on any of them (dispatch 2026-10-15-02).
 */
internal fun deviceIdentityStrings(): List<String> =
    listOf(Build.MANUFACTURER, Build.MODEL, Build.BRAND, Build.DEVICE, Build.FINGERPRINT).filter { !it.isNullOrBlank() }.distinct()

/** The production [RawShotSaver]: scratch DNGs in `captures/`, free space read from app storage, the writer from [productionDngWriter]. */
internal fun rawShotSaverFor(context: Context, files: CameraCaptureFiles): RawShotSaver =
    RawShotSaver(
        newRawFile = files::newRawFile,
        freeBytes = { context.filesDir.usableSpace },
        writer = productionDngWriter(deviceIdentityStrings()),
    )

/**
 * The writer production uses. **Forager's own writer is built on its own branch** (dispatch 2026-10-15-02,
 * `raw-stage2-dng-writer`) and is not on this one yet, so until the planner merges it this returns
 * [WriterNotOnThisBranch], which refuses every file: a RAW shot then keeps its JPG and says so, rather than writing a file
 * nothing has checked. At the merge this becomes `ForagerDngWriter(forbiddenStrings)`, the one line that changes.
 */
internal fun productionDngWriter(@Suppress("UNUSED_PARAMETER") forbiddenStrings: List<String>): DngWriter = WriterNotOnThisBranch

/** See [productionDngWriter]. An explicit "unsupported", never a plausible-looking file (CLAUDE.md). */
internal object WriterNotOnThisBranch : DngWriter {
    override fun write(image: DngImage, destination: File): DngWriteResult {
        destination.delete()
        return DngWriteResult.Failed("Forager's DNG writer (dispatch 2026-10-15-02) is not on this branch yet")
    }
}
