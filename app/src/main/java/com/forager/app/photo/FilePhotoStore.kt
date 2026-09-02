package com.forager.app.photo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.forager.app.data.repository.runCatchingCancellable
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * [PhotoStore] backed by app-private storage (`context.filesDir/photos/`) — never `cacheDir`, since
 * these are user-created field-record photos that must survive, the same reasoning already applied
 * to downloaded offline-map tiles. [LogPhoto.relativePath] is always relative to `filesDir`, never
 * absolute, so it survives an app reinstall/restore where `filesDir`'s absolute location can change.
 *
 * [persist] always copies bytes rather than trying to reuse a camera capture's file in place: a
 * gallery pick's `content://` URI (from the system photo picker) isn't a file this app owns at all,
 * so a copy is required there regardless, and using the same copy path for a camera capture too
 * means there is exactly one persistence code path to get right and test, not two.
 *
 * [now] is injected — same reasoning as [com.forager.app.domain.CreateMushroomLogEntryUseCase]'s
 * `today` — so a test can fix [LogPhoto.createdAtEpochMillis] instead of asserting against a live
 * clock.
 *
 * ## Two sources, two streams, never one (photo-geodata dispatch)
 *
 * [source]'s bytes are always copied via [android.content.ContentResolver.openInputStream] against
 * the *original* [android.net.Uri] — the ordinary, non-`setRequireOriginal` open, which is the same
 * call this method made before this dispatch. That choice is deliberate, not an oversight: as of
 * API 29, the platform itself redacts GPS EXIF tags from any stream opened this way unless the
 * caller both holds `ACCESS_MEDIA_LOCATION` *and* explicitly opts in via
 * [MediaStore.setRequireOriginal] — so leaving the byte-copy stream as-is is what keeps the stored
 * copy free of embedded GPS EXIF, for [GalleryImportPhotoSource] and [CameraCapturePhotoSource]
 * alike. A [GalleryImportPhotoSource]'s location and capture timestamp are read *separately*, via
 * [readExifData]'s own [MediaStore.setRequireOriginal]-opened stream — never the same open used for
 * the byte copy — so that the coordinate ends up only in [LogPhoto.latitude]/[LogPhoto.longitude],
 * never leaked into the persisted file's own bytes. See [LogPhoto]'s own doc comment for why a
 * [CameraCapturePhotoSource] never goes through this EXIF path at all: its coordinate, when there is
 * one, comes from a live GPS fix taken after this method returns (see
 * [com.forager.app.ui.log.MushroomLogViewModel]'s own doc comment), never from whatever EXIF our own
 * [CameraCaptureFiles] destination file happens to carry.
 *
 * **Known limitation, not fixed here (reported, not silently accepted):** the redaction this
 * reasoning relies on is a platform behavior introduced in API 29. This app's `minSdk` is 26 — on
 * API 26-28 there is no `setRequireOriginal` to opt into, and the platform does not redact GPS EXIF
 * from an ordinary [android.content.ContentResolver.openInputStream] open at all, so a gallery
 * import's byte copy on those API levels can carry embedded GPS EXIF regardless of anything this
 * class does. [readExifData] is itself SDK-guarded to API 29+ for the same reason
 * [MediaStore.setRequireOriginal] doesn't exist below it — this store never reads or writes EXIF
 * data of any kind on API 26-28, so [LogPhoto.latitude]/[LogPhoto.longitude] simply stay `null` for
 * an import on those API levels, honest-null rather than a best-effort read that can't be made
 * reliable. Stripping GPS EXIF from the destination file directly (rather than relying on
 * per-API-level redaction) would close this gap but is explicitly out of scope for this dispatch —
 * see the photo-geodata amendment's decision 5.
 */
class FilePhotoStore(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoStore {

    private val photosDir: File get() = File(context.filesDir, PHOTOS_SUBDIR).apply { mkdirs() }

    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = runCatchingCancellable {
        val uri = when (source) {
            is CameraCapturePhotoSource -> source.uri
            is GalleryImportPhotoSource -> source.uri
            else -> error("FilePhotoStore only understands CameraCapturePhotoSource/GalleryImportPhotoSource, got $source")
        }
        val id = UUID.randomUUID().toString()
        val destination = File(photosDir, "$id.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open $uri for reading")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }

        // Camera captures never read EXIF at all — see this class's own doc comment for why a
        // camera capture's coordinate comes from a live GPS fix, taken after this method returns,
        // never from whatever EXIF the captured file happens to carry.
        val exifData = if (source is GalleryImportPhotoSource) readExifData(uri) else ExifData(null, null, null)

        LogPhoto(
            id = id,
            relativePath = "$PHOTOS_SUBDIR/$id.jpg",
            createdAtEpochMillis = exifData.capturedAtEpochMillis ?: now(),
            latitude = exifData.latitude,
            longitude = exifData.longitude,
        )
    }

    override suspend fun delete(photo: LogPhoto): Result<Unit> = runCatchingCancellable {
        // delete() returning false (already gone) is a no-op, not a failure — mirrors
        // PlannedTripRepository.delete's "no-op if nothing to remove" convention.
        File(context.filesDir, photo.relativePath).delete()
        Unit
    }

    /**
     * Reads [android.net.Uri]'s own EXIF location and capture timestamp, via a stream distinct
     * from the one [persist] already used for the byte copy — see this class's own doc comment for
     * why that separation matters. `null`/`null`/`null` (never an exception out of [persist]) for
     * any reason the read can't complete: below API 29, no `ACCESS_MEDIA_LOCATION` grant, a `uri`
     * [MediaStore.setRequireOriginal] doesn't recognize, or no EXIF tags present at all — a missing
     * coordinate or timestamp is the ordinary case, not a failure this store surfaces.
     */
    private fun readExifData(uri: Uri): ExifData {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ExifData(null, null, null)
        return runCatching {
            val originalUri = MediaStore.setRequireOriginal(uri)
            context.contentResolver.openInputStream(originalUri)?.use { stream ->
                val exif = ExifInterface(stream)
                val latLong = exif.latLong
                ExifData(
                    latitude = latLong?.get(0),
                    longitude = latLong?.get(1),
                    capturedAtEpochMillis = exif.readCapturedAtEpochMillis(),
                )
            }
        }.getOrNull() ?: ExifData(null, null, null)
    }

    private fun ExifInterface.readCapturedAtEpochMillis(): Long? {
        val raw = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        // A fresh SimpleDateFormat per call, deliberately not a shared instance — SimpleDateFormat
        // is not thread-safe, and persist() is a suspend function this store's single AppContainer
        // instance can be called concurrently from more than one coroutine.
        return runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time }.getOrNull()
    }

    private data class ExifData(val latitude: Double?, val longitude: Double?, val capturedAtEpochMillis: Long?)

    private companion object {
        const val PHOTOS_SUBDIR = "photos"
    }
}
