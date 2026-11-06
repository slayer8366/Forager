package com.zynergylabs.forager.app.photo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.zynergylabs.forager.app.data.repository.runCatchingCancellable
import com.zynergylabs.forager.app.domain.PhotoStore
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.PhotoSource
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * [now] is injected — same reasoning as [com.zynergylabs.forager.app.domain.CreateMushroomLogEntryUseCase]'s
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
 * copy free of embedded GPS EXIF **for a [GalleryImportPhotoSource]**.
 *
 * **Corrected 2026-09-14: that redaction never covered a [CameraCapturePhotoSource], and this
 * comment used to claim it did ("alike").** The redaction is a `MediaStore` behaviour, and a
 * capture's `Uri` is not a `MediaStore` one — [CameraCaptureFiles] hands out
 * `FileProvider.getUriForFile` over this app's own `filesDir/captures/` file, so no `MediaStore`
 * is in the path and nothing was ever redacted on that half. A capture's stored copy carried
 * whatever EXIF the camera app wrote, GPS included, for as long as this comment said otherwise.
 * What makes the claim true for captures now is [scrubPhotoMetadata], called from [persist]. A [GalleryImportPhotoSource]'s location and capture timestamp are read *separately*, via
 * [readExifData]'s own [MediaStore.setRequireOriginal]-opened stream — never the same open used for
 * the byte copy — so that the coordinate ends up only in [LogPhoto.latitude]/[LogPhoto.longitude],
 * never leaked into the persisted file's own bytes. See [LogPhoto]'s own doc comment for why a
 * [CameraCapturePhotoSource] never goes through this EXIF path at all: its coordinate, when there is
 * one, comes from a live GPS fix taken after this method returns (see
 * [com.zynergylabs.forager.app.ui.log.MushroomLogViewModel]'s own doc comment), never from whatever EXIF our own
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
 * reliable. For an **import** that remains the position. For a **capture**, [scrubPhotoMetadata]
 * has stripped the destination file directly since 2026-09-14 (the sentence that used to end this
 * paragraph, saying that was out of scope, was true when written and is not now).
 *
 * ## Main-safe since 2026-09-14, and why that is stated rather than assumed
 *
 * [persist] runs its whole body on [Dispatchers.IO]. Before that it ran wherever it was called
 * from, which was the main thread: `viewModelScope.launch` with no dispatcher, through a use case
 * with no dispatcher, into a byte copy and a metadata rewrite of a full-size JPEG, per shot. The
 * multi-shot camera is exactly the case that shows. **That before-state was inferred from reading
 * every frame of the chain, not observed** — no frame switched, and `runCatchingCancellable` is a
 * plain try/catch — and it goes on the device check as a StrictMode run rather than being reported
 * as measured.
 *
 * This is not this repo's convention, and the record should say so: none of the eighteen files in
 * `data/repository/` switch dispatchers, and the only IO switches in `main/` are in four UI
 * callers. The Room-backed repositories are main-safe because Room switches for them. This store
 * has no Room underneath it, so it switches for itself — matching the main-safety the others
 * already have, not a rule they follow.
 *
 * ## [persist] consumes its source
 *
 * [PhotoSource.release] is called in a `finally`, succeed or fail, so a capture's temporary file
 * is deleted once its bytes are copied (or once the copy has failed and the user will retake). An
 * import's `release` is a no-op by default: the user's photo is never touched. See
 * [CameraCapturePhotoSource] for the leak this closes.
 */
class FilePhotoStore(
    private val context: Context,
    private val now: () -> Long = System::currentTimeMillis,
) : PhotoStore {

    private val photosDir: File get() = File(context.filesDir, PHOTOS_SUBDIR).apply { mkdirs() }

    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = withContext(Dispatchers.IO) {
        runCatchingCancellable {
            try {
                persistOnIo(source)
            } finally {
                source.release()
            }
        }
    }

    /** The body of [persist], on the IO dispatcher, with its source still unreleased. */
    private fun persistOnIo(source: PhotoSource): LogPhoto {
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

        // Owner's design, 2026-09-14: read the orientation, strip the metadata, reapply the
        // orientation — see scrubPhotoMetadata. **Captures only**, per the owner's instruction that
        // photos imported from outside the app remain untouched. The copy above is a byte copy of
        // whatever the camera app wrote, and nothing else in this app has ever removed anything
        // from it, so this is the only point at which a capture's GPS EXIF stops travelling with
        // the file. Lossless and never destructive; that function's own doc comment carries the
        // reasoning and the failure behaviour.
        if (source is CameraCapturePhotoSource) scrubPhotoMetadata(destination)

        // Camera captures never read EXIF for a coordinate — see this class's own doc comment for
        // why a camera capture's coordinate comes from a live GPS fix, taken after this method
        // returns, never from whatever EXIF the captured file happens to carry. Read from the
        // source `uri`, not from `destination`, so the scrub above cannot affect an import.
        val exifData = if (source is GalleryImportPhotoSource) readExifData(uri) else ExifData(null, null, null)

        // A RAW shot's DNG, beside its JPG (dispatch 2026-10-15-01). Its own step, after the JPG's: nothing above changes.
        val rawRelativePath = (source as? CameraCapturePhotoSource)?.rawFile?.let { raw -> persistRawCompanion(raw, id, destination) }

        return LogPhoto(
            id = id,
            relativePath = "$PHOTOS_SUBDIR/$id.jpg",
            createdAtEpochMillis = exifData.capturedAtEpochMillis ?: now(),
            latitude = exifData.latitude,
            longitude = exifData.longitude,
            rawRelativePath = rawRelativePath,
        )
    }

    /**
     * Copies a RAW shot's DNG to `photos/<id>.dng` and returns its relative path. The DNG is already Forager's own file,
     * written and checked in the shot step (`RawShotSaver`), so it is copied as it is: nothing here reads or changes its
     * metadata. A copy that fails fails the whole persist and removes what this persist wrote, the JPG included, rather
     * than keeping a photo the user took as RAW as a JPG without saying so; the shot step's own fallback is the one that
     * keeps a JPG, and it says so (RECORD -1003).
     */
    private fun persistRawCompanion(raw: File, id: String, jpegDestination: File): String {
        val target = File(photosDir, "$id.$RAW_EXTENSION")
        try {
            check(raw.isFile) { "the RAW shot's DNG '${raw.name}' is not there to persist" }
            raw.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        } catch (failure: Throwable) {
            target.delete()
            jpegDestination.delete()
            throw failure
        }
        return "$PHOTOS_SUBDIR/${target.name}"
    }

    override suspend fun delete(photo: LogPhoto): Result<Unit> = runCatchingCancellable {
        // delete() returning false (already gone) is a no-op, not a failure — mirrors
        // PlannedTripRepository.delete's "no-op if nothing to remove" convention.
        File(context.filesDir, photo.relativePath).delete()
        // A RAW shot is one photo of two files (RECORD -1004); delete removes everything the shot saved.
        photo.rawRelativePath?.let { File(context.filesDir, it).delete() }
        Unit
    }

    /**
     * Reads [android.net.Uri]'s own EXIF location and capture timestamp, via a stream distinct
     * from the one [persist] already used for the byte copy — see this class's own doc comment for
     * why that separation matters. `null`/`null`/`null` (never an exception out of [persist]) for
     * any reason the read can't complete: below API 29, no `ACCESS_MEDIA_LOCATION` grant, a `uri`
     * [MediaStore.setRequireOriginal] doesn't recognize, or no EXIF tags present at all — a missing
     * coordinate or timestamp is the ordinary case, not a failure this store surfaces.
     *
     * An absent tag and a read that threw still give the same null result, but a throw is logged
     * with its cause (dispatch 2026-09-28-658, J4), so a refused permission or an IO error is not
     * mistaken in the log for a photo that simply had no location.
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
        }.onFailure { error ->
            Log.w(TAG, "Couldn't read the imported photo's EXIF location and time; it is kept with none.", error)
        }.getOrNull() ?: ExifData(null, null, null)
    }

    private fun ExifInterface.readCapturedAtEpochMillis(): Long? {
        val raw = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: getAttribute(ExifInterface.TAG_DATETIME) ?: return null
        // A fresh SimpleDateFormat per call, deliberately not a shared instance — SimpleDateFormat
        // is not thread-safe, and persist() is a suspend function this store's single AppContainer
        // instance can be called concurrently from more than one coroutine.
        return runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(raw)?.time }
            .onFailure { error -> Log.w(TAG, "The imported photo's EXIF time '$raw' did not parse; using the time of import.", error) }
            .getOrNull()
    }

    private data class ExifData(val latitude: Double?, val longitude: Double?, val capturedAtEpochMillis: Long?)

    private companion object {
        const val TAG = "FilePhotoStore"
        const val PHOTOS_SUBDIR = "photos"
        const val RAW_EXTENSION = "dng"
    }
}
