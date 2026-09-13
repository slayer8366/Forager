package com.zynergylabs.forager.app.photo

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * How a stored photo must be turned to display upright — EXIF-orientation-display dispatch
 * (2026-09-13). Cameras commonly store the sensor's native frame and write an orientation tag
 * asking the reader to rotate it; `BitmapFactory` does not honour the tag, so both display paths
 * ([com.zynergylabs.forager.app.ui.log.DecodedPhoto] and the viewer's `decodeBoundedPhoto`) apply
 * this at display time. **The file is never rewritten and the tag is never stripped**: rotating
 * JPEG bytes is lossy, and the stored copy stays what the camera produced.
 *
 * [rotationDegrees] is clockwise, the direction EXIF and Android's `Matrix`/`rotationZ` both use.
 * [mirrored] is a horizontal flip applied *after* the rotation, in the displayed frame — the same
 * decomposition Glide's `TransformationUtils.initializeMatrixForRotation` uses for all eight
 * values, chosen over a hand-derived one because the transpose/transverse cases are exactly where
 * a sign error would survive until a real photo showed it.
 */
internal data class PhotoOrientation(val rotationDegrees: Int, val mirrored: Boolean) {
    /** 90° and 270° swap width and height on display. */
    val swapsAxes: Boolean get() = rotationDegrees % 180 != 0

    companion object {
        val NORMAL = PhotoOrientation(rotationDegrees = 0, mirrored = false)

        /** The eight defined values; anything else, including the tag's "undefined" 0, displays unrotated. */
        fun fromExifTag(tag: Int): PhotoOrientation = when (tag) {
            ExifInterface.ORIENTATION_NORMAL -> NORMAL
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> PhotoOrientation(0, mirrored = true)
            ExifInterface.ORIENTATION_ROTATE_180 -> PhotoOrientation(180, mirrored = false)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> PhotoOrientation(180, mirrored = true)
            ExifInterface.ORIENTATION_TRANSPOSE -> PhotoOrientation(90, mirrored = true)
            ExifInterface.ORIENTATION_ROTATE_90 -> PhotoOrientation(90, mirrored = false)
            ExifInterface.ORIENTATION_TRANSVERSE -> PhotoOrientation(270, mirrored = true)
            ExifInterface.ORIENTATION_ROTATE_270 -> PhotoOrientation(270, mirrored = false)
            else -> NORMAL
        }
    }
}

/**
 * Reads **only** `TAG_ORIENTATION` from [file]. Deliberately not a general EXIF accessor: the
 * capture path takes a photo's location from a live device fix and never from the file, so that
 * an image's metadata can never become a location claim (see `FilePhotoStore`'s own doc comment
 * and the find-location dispatch). This function has no way to return anything but an
 * orientation, which is the guard against a later change widening it. Read-only: `ExifInterface`
 * writes nothing unless `saveAttributes()` is called, and it is not.
 *
 * Every failure path displays unrotated, never fails: a file with no EXIF, a non-JPEG, an
 * unreadable file, or an unrecognised value all yield [PhotoOrientation.NORMAL]. An exception is
 * logged (a missing tag is not one — `ExifInterface` returns the default for that).
 */
internal fun readPhotoOrientation(file: File): PhotoOrientation = runCatching {
    val tag = ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    PhotoOrientation.fromExifTag(tag)
}.getOrElse { error ->
    Log.w(TAG, "Couldn't read the orientation of '${file.name}'; displaying it unrotated.", error)
    PhotoOrientation.NORMAL
}

/**
 * This bitmap turned upright per [orientation]: the same instance for [PhotoOrientation.NORMAL],
 * otherwise a new bitmap with the source recycled. Costs one extra bitmap of the same pixel count
 * for the duration of the copy — fine for a sampled thumbnail (a 12 MP capture at `inSampleSize
 * 4` is ~3 MB), which is why `DecodedPhoto` uses it for every orientation; the viewer, holding up
 * to a 4096-px-edge bitmap, applies pure rotations as a draw transform instead and only comes
 * here for the mirrored values (see its own doc comment).
 */
internal fun Bitmap.oriented(orientation: PhotoOrientation): Bitmap {
    if (orientation == PhotoOrientation.NORMAL) return this
    val matrix = Matrix().apply {
        setRotate(orientation.rotationDegrees.toFloat())
        if (orientation.mirrored) postScale(-1f, 1f)
    }
    val turned = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (turned !== this) recycle()
    return turned
}

private const val TAG = "PhotoOrientation"
