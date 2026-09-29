package com.zynergylabs.forager.app.photo

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.zynergylabs.forager.app.data.repository.runCatchingCancellable
import com.zynergylabs.forager.app.domain.model.LogPhoto
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/** What a copy of a stored photo is called and typed when it leaves the app: the name for the picker or the media store, and the type the file's own bytes declare. */
data class ExportFile(val displayName: String, val mimeType: String)

/**
 * Puts a copy of a stored photo where the user can reach it outside the app (owner, 2026-09-29: "Let's also add an export option
 * for photos to the device"; intent `2026-09-28-126`). The interface is this project's own seam over the platform's media store and
 * the Storage Access Framework, so the viewer depends on it and not on either (CLAUDE.md, Architecture).
 *
 * **What is written is the stored file's bytes, unchanged, for every photo.** A capture is already scrubbed of metadata when it is
 * stored ([scrubPhotoMetadata]). An import is deliberately unscrubbed (`FilePhotoStore`), and the owner ruled on 2026-09-29 that
 * imports are outside this feature's scope ("They can use a scrubbing app to remove it if they want it removed"), so an import's
 * exported copy is a byte copy and can carry whatever location or other metadata the file carries. Nothing here reads or writes
 * EXIF, and nothing here changes the stored file.
 *
 * Every method returns a [Result] and logs its failure at WARN before returning it: nothing is swallowed.
 */
interface PhotoExporter {
    /** The name and type an exported copy of [photo] gets, from its own bytes; a failure when they are no picture this app knows. */
    suspend fun describe(photo: LogPhoto): Result<ExportFile>

    /** Inserts [photo] into the device's Gallery, in the "Forager" album (API 29+; a failure below that, where [describe] and [saveToDocument] are the route). */
    suspend fun saveToGallery(photo: LogPhoto): Result<Unit>

    /** Writes the stored file at [relativePath] to [destination], a document the user chose through the create-document picker. */
    suspend fun saveToDocument(relativePath: String, destination: Uri): Result<Unit>
}

/** The real [PhotoExporter], over `context.filesDir` and the content resolver. [now] names a photo whose stored record has no time of its own. */
class FilePhotoExporter(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoExporter {

    override suspend fun describe(photo: LogPhoto): Result<ExportFile> = withContext(Dispatchers.IO) {
        runCatchingCancellable { exportFileFor(photo, storedFile(photo.relativePath)) }.loggedOnFailure("name and type '${photo.relativePath}'")
    }

    override suspend fun saveToGallery(photo: LogPhoto): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Saving to the Gallery needs API 29 or later without a storage permission, and this is API ${Build.VERSION.SDK_INT}"
            }
            val file = storedFile(photo.relativePath)
            val target = exportFileFor(photo, file)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, target.displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, target.mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, GALLERY_RELATIVE_PATH)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                // A time only when the record has one; never a made-up one. No location column is ever written.
                photo.createdAtEpochMillis?.let { put(MediaStore.MediaColumns.DATE_TAKEN, it) }
            }
            val resolver = context.contentResolver
            val row = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                ?: error("The media store returned no row for '${target.displayName}'")
            try {
                val output = resolver.openOutputStream(row) ?: error("The media store gave no stream for $row")
                output.use { out -> file.inputStream().use { it.copyTo(out) } }
                resolver.update(row, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                // Part 2 follow-ups F1 item 7 (Part 2 Session 1, item 61: the Gallery row read back with a NULL
                // datetaken though the insert above wrote one). The planner's unverified guess, not confirmed
                // (device-only): publishing (IS_PENDING to 0) makes the media scan re-derive DATE_TAKEN from the
                // file's own metadata, and a scrubbed photo has none, so the scan replaces what the insert wrote.
                // The time is therefore written again after the publish, so the last word is the record's. Never a
                // made-up time: only when the record has one.
                // A failure here does not undo the save: the photo is published and saved, only its date taken is
                // the scan's. It is logged at WARN, not swallowed.
                photo.createdAtEpochMillis?.let { takenAt ->
                    runCatching { resolver.update(row, ContentValues().apply { put(MediaStore.MediaColumns.DATE_TAKEN, takenAt) }, null, null) }
                        .onSuccess { rows -> if (rows == 0) Log.w(TAG, "The media store updated no row when restoring the date taken of $row.") }
                        .onFailure { Log.w(TAG, "Saved '${photo.relativePath}' to the Gallery but couldn't restore its date taken.", it) }
                }
            } catch (failure: Throwable) {
                // A half-written pending row is worse than none. Deleted even when the save was cancelled.
                withContext(NonCancellable) {
                    runCatching { resolver.delete(row, null, null) }
                        .onFailure { Log.w(TAG, "Couldn't remove the half-written row $row after a failed save.", it) }
                }
                throw failure
            }
            Unit
        }.loggedOnFailure("save '${photo.relativePath}' to the Gallery")
    }

    override suspend fun saveToDocument(relativePath: String, destination: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            val file = storedFile(relativePath)
            val output = context.contentResolver.openOutputStream(destination, "wt") ?: error("The picker gave no stream for $destination")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
            Unit
        }.loggedOnFailure("save '$relativePath' to the chosen document")
    }

    private fun storedFile(relativePath: String): File = File(context.filesDir, relativePath).also {
        check(it.isFile) { "There is no stored photo at '$relativePath'" }
    }

    private fun exportFileFor(photo: LogPhoto, file: File): ExportFile {
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(HEADER_BYTES)
            val read = input.read(buffer) // a file stream fills the buffer unless the file is shorter
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        }
        val type = sniffImageType(header) ?: error("'${photo.relativePath}' is not a picture type this app recognises; its name says .jpg, which is not evidence")
        val takenAt = photo.createdAtEpochMillis ?: now().also { Log.i(TAG, "'${photo.relativePath}' has no capture time; naming its copy from the clock.") }
        // A fresh SimpleDateFormat per call: it is not thread-safe.
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(takenAt))
        return ExportFile("forager-photo-$stamp.${type.extension}", type.mimeType)
    }

    private fun <T> Result<T>.loggedOnFailure(what: String): Result<T> = onFailure { Log.w(TAG, "Couldn't $what.", it) }

    private companion object {
        const val TAG = "PhotoExporter"
        const val GALLERY_RELATIVE_PATH = "Pictures/Forager"
        const val HEADER_BYTES = 16
    }
}

/** A picture type read from a file's own first bytes, with the type and file extension that go with it. */
internal enum class ImageType(val mimeType: String, val extension: String) {
    JPEG("image/jpeg", "jpg"),
    PNG("image/png", "png"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp"),
    HEIC("image/heic", "heic"),
    HEIF("image/heif", "heif"),
    AVIF("image/avif", "avif"),
    BMP("image/bmp", "bmp"),
}

/**
 * The picture type [header] (a file's first bytes, sixteen is enough) declares, or `null` when it is none this app names. The store
 * calls every photo `.jpg` whatever it is (`FilePhotoStore`), and the picker can return HEIC, PNG or WebP, so the name says nothing.
 * `null` is an answer, not a default: the caller fails rather than guessing `image/jpeg`.
 */
internal fun sniffImageType(header: ByteArray): ImageType? {
    fun at(offset: Int, text: String): Boolean =
        header.size >= offset + text.length && text.indices.all { header[offset + it] == text[it].code.toByte() }
    fun byte(i: Int): Int = if (i < header.size) header[i].toInt() and 0xFF else -1
    return when {
        byte(0) == 0xFF && byte(1) == 0xD8 && byte(2) == 0xFF -> ImageType.JPEG
        byte(0) == 0x89 && at(1, "PNG") && byte(4) == 0x0D && byte(5) == 0x0A && byte(6) == 0x1A && byte(7) == 0x0A -> ImageType.PNG
        at(0, "GIF87a") || at(0, "GIF89a") -> ImageType.GIF
        at(0, "RIFF") && at(8, "WEBP") -> ImageType.WEBP
        at(4, "ftyp") -> when {
            HEIC_BRANDS.any { at(8, it) } -> ImageType.HEIC
            HEIF_BRANDS.any { at(8, it) } -> ImageType.HEIF
            AVIF_BRANDS.any { at(8, it) } -> ImageType.AVIF
            else -> null
        }
        // 'BM', a four-byte size, then two reserved words that are zero: two letters alone would take any text that starts with them.
        at(0, "BM") && (6..9).all { byte(it) == 0 } -> ImageType.BMP
        else -> null
    }
}

private val HEIC_BRANDS = listOf("heic", "heix", "hevc", "hevx")
private val HEIF_BRANDS = listOf("mif1", "msf1", "heif")
private val AVIF_BRANDS = listOf("avif", "avis")
