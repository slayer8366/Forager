package com.zynergylabs.forager.app.photo

import android.content.Context
import android.os.Build
import android.util.Log
import com.zynergylabs.forager.app.domain.rawShotFits
import com.zynergylabs.forager.app.data.raw.ForagerDngWriter
import com.zynergylabs.forager.app.domain.raw.DngFailureStage
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
 * - **The writer failed** ([DngWriteResult.Failed]): it has already deleted its file; the JPG is the photo. The message
 *   is by cause (Amendment 2; RECORD -1003, -1018), read from [DngWriteResult.Failed.stage], never from the reason's
 *   text: [RAW_NOT_CLEAN_MESSAGE], the owner's -1003 words, only when the file failed its check after writing
 *   ([DngFailureStage.Check]); [RAW_NOT_SAVED_MESSAGE] for a refused input, a write error, a writer that throws, or one
 *   that does not say.
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
        if (!rawShotFits(free, image.width, image.height)) {
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
            DngWriteResult.Failed("the writer threw ${error::class.java.simpleName}: ${error.message}", DngFailureStage.Writing)
        }
        when (result) {
            is DngWriteResult.Written -> if (destination.isFile) {
                RawShotOutcome.Kept(destination)
            } else {
                jpgOnly(RAW_NOT_SAVED_MESSAGE, "the writer reported ${result.bytes} bytes written but there is no file at ${destination.name}")
            }
            is DngWriteResult.Failed -> {
                // The writer deletes its file on failure; deleting again costs nothing and makes it true here too.
                destination.delete()
                val message = if (result.stage == DngFailureStage.Check) RAW_NOT_CLEAN_MESSAGE else RAW_NOT_SAVED_MESSAGE
                jpgOnly(message, "the DNG writer refused the file: ${result.reason}")
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
 * Every RAW failure that is not a failed clean check: the camera's metadata could not make a DNG, the writer refused the
 * input, or the file could not be written. The owner approved the words (RECORD -1018, "1 yes").
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
 * The writer production uses: Forager's own (dispatch 2026-10-15-02, merged at `2057bc9f`; switched on by Amendment 2),
 * given this phone's identity strings for its check after writing. Until Amendment 2 this returned a stand-in that refused
 * every file.
 */
internal fun productionDngWriter(forbiddenStrings: List<String>): DngWriter = ForagerDngWriter(forbiddenStrings)
