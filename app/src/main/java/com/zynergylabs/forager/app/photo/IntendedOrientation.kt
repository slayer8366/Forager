package com.zynergylabs.forager.app.photo

import android.util.Log
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import java.io.DataInputStream
import java.io.File

/**
 * Puts the orientation tag the shot asked for onto a JPEG whose camera HAL tagged it otherwise.
 *
 * ## Why this exists — device result on `b586195`, 2026-09-15
 *
 * With "Lock camera to portrait" on and the phone held landscape, the saved photo came out
 * landscape and upright, identical to the setting being off, although the session had assigned
 * `ImageCapture.targetRotation = ROTATION_0` for that shot. The trace (window-lock report,
 * addendum of 2026-09-15 on the trace) found every hop intact from the stored setting to the
 * capture request: CameraX turns the target into a `JPEG_ORIENTATION` request to the HAL. Then, on
 * every device not on its two-model quirk list, CameraX takes the *file's* tag from the HAL's own
 * EXIF (`ProcessingInput2Packet.createPacketWithHalRotation` and `FileUtil.updateFileExif`,
 * camera-core 1.6.2), never from the request. A HAL that tags from its own motion sensor rather
 * than from the request produces exactly the device result, in both states of the setting, and
 * nothing in the app or in CameraX corrects it. That mechanism is inferred from the reads and the
 * device result, not observed: the log line `CameraXCaptureSession` writes per shot is what the
 * next device run confirms it with.
 *
 * ## What it does, and the one case it declines
 *
 * The intended tag is a pure rotation of the sensor frame, so it is only right if the pixels
 * *are* the sensor frame. The JPEG's own pixel size, read from its SOF segment, is compared with
 * the capture resolution CameraX reports: equal means the HAL did not rotate the pixels and the
 * tag is set to the intended rotation; transposed means the HAL rotated them in memory — the case
 * CameraX's HAL-rotation path was written for, where its tag stands — and this declines, logged.
 * Anything else declines too. **Orientation value only**: no pixel is decoded, moved or
 * re-encoded, and `ExifInterface.saveAttributes` rewrites the container around the same scan
 * data, as the scrub already does on persist.
 *
 * A square capture resolution cannot tell the two cases apart and takes the first branch; noted,
 * not handled, since no device here produces one.
 */
internal sealed interface OrientationReapplyOutcome {
    /** The tag was [fromTag] and is now [toTag]. This is the fallback firing, and it is logged. */
    data class Rewritten(val fromTag: Int, val toTag: Int) : OrientationReapplyOutcome

    /** The tag already said what the shot intended; the file was not touched. */
    data class Kept(val tag: Int) : OrientationReapplyOutcome

    /** Left as CameraX wrote it, with why. [tag] is what the file carries, when it could be read. */
    data class Declined(val reason: String, val tag: Int?) : OrientationReapplyOutcome

    /** Reading or writing failed; the file is as CameraX wrote it. */
    data class Failed(val error: Throwable) : OrientationReapplyOutcome
}

/**
 * The EXIF orientation value for a clockwise right-angle rotation, the four pure rotations of the
 * eight the tag can hold; `null` for anything else, because a rotation that is not a right angle
 * has no tag and must not be rounded to one. The inverse of [PhotoOrientation.fromExifTag] on
 * these four, and tested as such.
 */
internal fun exifOrientationTagFor(rotationDegrees: Int): Int? = when (rotationDegrees) {
    0 -> ExifInterface.ORIENTATION_NORMAL
    90 -> ExifInterface.ORIENTATION_ROTATE_90
    180 -> ExifInterface.ORIENTATION_ROTATE_180
    270 -> ExifInterface.ORIENTATION_ROTATE_270
    else -> null
}

/**
 * Width and height of the encoded image, from the JPEG's frame header (any SOF marker), or `null`
 * when [file] is not a JPEG, has no frame header before its scan, or cannot be read. Reads
 * headers only: it stops at the first SOF and never reaches the scan data.
 */
internal fun readJpegPixelSize(file: File): Size? = runCatching {
    DataInputStream(file.inputStream().buffered()).use { input -> parseFrameHeader(input) }
}.getOrElse { error ->
    Log.w(TAG, "Couldn't read the pixel size of '${file.name}'.", error)
    null
}

private fun parseFrameHeader(input: DataInputStream): Size? {
    if (input.readUnsignedByte() != 0xFF || input.readUnsignedByte() != 0xD8) return null
    while (true) {
        var marker = input.readUnsignedByte()
        if (marker != 0xFF) return null
        while (marker == 0xFF) marker = input.readUnsignedByte() // fill bytes before a marker are legal
        when (marker) {
            0xD8, 0x01, in 0xD0..0xD7 -> continue // standalone markers carry no length
            0xD9, 0xDA -> return null // end of image, or the scan began with no frame header
            in SOF_MARKERS -> {
                input.readUnsignedShort() // segment length
                input.readUnsignedByte() // sample precision
                val height = input.readUnsignedShort()
                val width = input.readUnsignedShort()
                return Size(width, height)
            }

            else -> {
                val length = input.readUnsignedShort()
                if (length < 2) return null
                input.readFully(ByteArray(length - 2))
            }
        }
    }
}

/**
 * Sets [file]'s orientation tag to [intendedRotationDegrees] when its pixels are the unrotated
 * capture frame of [captureResolution]; see the file doc for the decision and the declined cases.
 * Never throws: every failure is an [OrientationReapplyOutcome], and the caller logs it.
 */
internal fun reapplyIntendedOrientation(file: File, intendedRotationDegrees: Int, captureResolution: Size): OrientationReapplyOutcome {
    val intendedTag = exifOrientationTagFor(intendedRotationDegrees)
        ?: return OrientationReapplyOutcome.Declined("the shot's rotation of $intendedRotationDegrees° is not a right angle", tag = null)
    val pixelSize = readJpegPixelSize(file)
        ?: return OrientationReapplyOutcome.Declined("the pixel size of '${file.name}' could not be read", tag = null)
    val transposed = Size(captureResolution.height, captureResolution.width)
    return runCatching {
        val exif = ExifInterface(file.absolutePath)
        val current = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
        when (pixelSize) {
            captureResolution -> {
                // An absent tag displays exactly as NORMAL does, so intending 0° on an untagged
                // file invents nothing (the scrub's own rule for a missing tag).
                val currentAsDisplayed = if (current == ExifInterface.ORIENTATION_UNDEFINED) ExifInterface.ORIENTATION_NORMAL else current
                if (currentAsDisplayed == intendedTag) {
                    OrientationReapplyOutcome.Kept(current)
                } else {
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, intendedTag.toString())
                    exif.saveAttributes()
                    OrientationReapplyOutcome.Rewritten(fromTag = current, toTag = intendedTag)
                }
            }

            transposed -> OrientationReapplyOutcome.Declined(
                "the HAL rotated the pixels ($pixelSize for a $captureResolution capture); CameraX's tag stands",
                tag = current,
            )

            else -> OrientationReapplyOutcome.Declined(
                "the pixel size $pixelSize matches neither the capture resolution $captureResolution nor its transpose",
                tag = current,
            )
        }
    }.getOrElse { error -> OrientationReapplyOutcome.Failed(error) }
}

/** SOF0–SOF15 less the three markers in that range that are not frame headers (DHT, JPG, DAC). */
private val SOF_MARKERS = (0xC0..0xCF).filter { it != 0xC4 && it != 0xC8 && it != 0xCC }.toSet()

private const val TAG = "IntendedOrientation"
