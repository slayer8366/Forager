package com.forager.app.photo

import android.content.Context
import com.forager.app.data.repository.runCatchingCancellable
import com.forager.app.domain.PhotoStore
import com.forager.app.domain.model.LogPhoto
import com.forager.app.domain.model.PhotoSource
import java.io.File
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
 */
class FilePhotoStore(private val context: Context) : PhotoStore {

    private val photosDir: File get() = File(context.filesDir, PHOTOS_SUBDIR).apply { mkdirs() }

    override suspend fun persist(source: PhotoSource): Result<LogPhoto> = runCatchingCancellable {
        val uri = (source as? ContentUriPhotoSource)?.uri
            ?: error("FilePhotoStore only understands ContentUriPhotoSource, got $source")
        val id = UUID.randomUUID().toString()
        val destination = File(photosDir, "$id.jpg")
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open $uri for reading")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }
        LogPhoto(id = id, relativePath = "$PHOTOS_SUBDIR/$id.jpg")
    }

    override suspend fun delete(photo: LogPhoto): Result<Unit> = runCatchingCancellable {
        // delete() returning false (already gone) is a no-op, not a failure — mirrors
        // PlannedTripRepository.delete's "no-op if nothing to remove" convention.
        File(context.filesDir, photo.relativePath).delete()
        Unit
    }

    private companion object {
        const val PHOTOS_SUBDIR = "photos"
    }
}
