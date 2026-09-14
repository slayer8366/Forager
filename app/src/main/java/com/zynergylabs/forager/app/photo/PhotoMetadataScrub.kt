package com.zynergylabs.forager.app.photo

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * Strips a stored photo's metadata and puts back only its orientation — owner's design,
 * 2026-09-14: "read the orientation, strip the metadata, and then apply the orientation."
 *
 * ## Why strip-then-reapply rather than removing the tags you don't want
 *
 * `ExifInterface` exposes **155** `TAG_` constants, and `setAttribute(tag, null)` removes exactly
 * the tags you name. Removing "the identifying ones" is a *denylist*: it is only ever as complete
 * as the list someone remembered to write, it cannot reach a tag the library has no constant for,
 * and it does not touch the non-EXIF segments (IPTC in APP13, XMP, a JFXX thumbnail) at all.
 * Rebuilding the file with only the segments named here is an *allowlist*: whatever is not
 * deliberately kept is gone, including anything that did not exist when this was written. The
 * owner's framing, and the stronger of the two by construction.
 *
 * ## Lossless, unlike baking the rotation into the pixels
 *
 * This only rewrites the JPEG's segment structure; the entropy-coded scan data is copied through
 * byte for byte, so the image is untouched and there is no decode, no re-encode and no
 * full-resolution bitmap. That is what makes it affordable at save time: baking the rotation into
 * the pixels instead would cost one generation of JPEG quality and peak around 98 MB for a 12 MP
 * photo, 400 MB for a 50 MP one. Measured, not assumed — [PhotoMetadataScrubTest] asserts the scan
 * bytes are identical across the scrub.
 *
 * ## What is kept, and why each one
 *
 * Only three segment kinds survive, and none of them describes the photographer:
 * - **APP0 `JFIF`** — pixel density and aspect. Structural. `JFXX` (the APP0 *extension*, which can
 *   carry a thumbnail) is dropped, because a thumbnail is exactly the leak this exists to close:
 *   the classic EXIF failure is a cropped photo whose embedded thumbnail still shows the original.
 * - **APP2 `ICC_PROFILE`** — the colour profile. Kept deliberately: a phone shooting Display P3
 *   whose profile is discarded renders with shifted colour in any colour-managed viewer, and this
 *   app exists so a forager can judge gill and cap colour. Dropping it would be a privacy win of
 *   zero and an accuracy loss that matters here.
 * - **APP14 `Adobe`** — declares the colour transform for the scan data. Dropping it can change how
 *   the image is *decoded*, not merely how it is described.
 *
 * Everything else goes: APP1 (EXIF, including GPS, timestamps, maker notes and the IFD1 thumbnail;
 * and XMP), APP13 (IPTC), every other APPn, and COM comments.
 *
 * ## Failure is never destructive, and never silent
 *
 * The rewrite goes to a sibling temp file and replaces the original only once it has been written
 * whole; any failure leaves the original exactly as it was and logs at WARN. That is a deliberate
 * fail-*open* on privacy: a photo that keeps its metadata is the behaviour that existed before this
 * function, whereas a lost or truncated photo is unrecoverable field data. A non-JPEG is left alone
 * for the same reason.
 */
internal fun scrubPhotoMetadata(file: File): ScrubOutcome {
    val original = runCatching { file.readBytes() }.getOrElse { error ->
        Log.w(TAG, "Couldn't read '${file.name}' to scrub its metadata; leaving it as it is.", error)
        return ScrubOutcome.Failed
    }
    if (!original.startsWithJpegMarker()) {
        Log.i(TAG, "'${file.name}' is not a JPEG; leaving its bytes alone.")
        return ScrubOutcome.NotAJpeg
    }

    // Read the one tag that is put back, before anything is removed. Deliberately the raw EXIF
    // value rather than readPhotoOrientation's decomposed form: this round-trips the tag, it does
    // not interpret it, so a value this build does not recognise survives unchanged.
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    }.getOrElse { ExifInterface.ORIENTATION_UNDEFINED }

    val stripped = stripJpegMetadataSegments(original) ?: run {
        Log.w(TAG, "Couldn't parse '${file.name}' as a JPEG segment stream; leaving it as it is.")
        return ScrubOutcome.Failed
    }

    val temp = File(file.parentFile, "${file.name}.scrub")
    val replaced = runCatching {
        temp.writeBytes(stripped)
        if (orientation != ExifInterface.ORIENTATION_UNDEFINED) {
            ExifInterface(temp.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
                saveAttributes()
            }
        }
        check(temp.renameTo(file)) { "couldn't replace '${file.name}' with its scrubbed copy" }
    }
    return replaced.fold(
        onSuccess = { ScrubOutcome.Scrubbed(orientationReapplied = orientation != ExifInterface.ORIENTATION_UNDEFINED) },
        onFailure = { error ->
            temp.delete()
            Log.w(TAG, "Couldn't scrub '${file.name}'; the original is untouched and keeps its metadata.", error)
            ScrubOutcome.Failed
        },
    )
}

/** What [scrubPhotoMetadata] did, so a caller and a test can tell the three outcomes apart rather than inferring them. */
internal sealed interface ScrubOutcome {
    data class Scrubbed(val orientationReapplied: Boolean) : ScrubOutcome
    data object NotAJpeg : ScrubOutcome
    data object Failed : ScrubOutcome
}

/**
 * Rebuilds [jpeg] with only the segments [isKeptSegment] names, copying the entropy-coded scan data
 * through untouched. `null` when the marker structure does not parse, which is the caller's signal
 * to leave the file alone rather than write something it did not understand.
 */
private fun stripJpegMetadataSegments(jpeg: ByteArray): ByteArray? {
    val out = ArrayList<Byte>(jpeg.size)
    out.add(jpeg[0])
    out.add(jpeg[1]) // SOI
    var i = 2
    while (i < jpeg.size) {
        if (jpeg[i] != MARKER_PREFIX) return null
        if (i + 1 >= jpeg.size) return null
        val marker = jpeg[i + 1].toInt() and 0xFF
        // Standalone markers carry no length. None normally precedes SOS, but a malformed file must
        // not be silently reinterpreted, so they are handled rather than assumed absent.
        if (marker == 0x01 || marker in 0xD0..0xD9) {
            out.add(jpeg[i])
            out.add(jpeg[i + 1])
            i += 2
            continue
        }
        if (i + 3 >= jpeg.size) return null
        val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
        if (length < 2 || i + 2 + length > jpeg.size) return null
        val segment = jpeg.copyOfRange(i, i + 2 + length)
        if (marker == SOS) {
            // The scan header, then every remaining byte verbatim: this is the image itself.
            segment.forEach { out.add(it) }
            for (k in (i + 2 + length) until jpeg.size) out.add(jpeg[k])
            return out.toByteArray()
        }
        if (isKeptSegment(marker, segment)) segment.forEach { out.add(it) }
        i += 2 + length
    }
    return out.toByteArray()
}

/** The allowlist — see [scrubPhotoMetadata]'s own doc comment for why each of the three is kept. */
private fun isKeptSegment(marker: Int, segment: ByteArray): Boolean = when {
    marker !in APP0..APP15 && marker != COM -> true // DQT, DHT, SOF, DRI and friends: the image's own structure
    marker == APP0 -> segment.identifierIs(JFIF_IDENTIFIER)
    marker == APP2 -> segment.identifierIs(ICC_IDENTIFIER)
    marker == APP14 -> segment.identifierIs(ADOBE_IDENTIFIER)
    else -> false
}

/** Whether this segment's payload begins with [identifier], the convention every APPn uses to say what it is. */
private fun ByteArray.identifierIs(identifier: String): Boolean {
    val start = 4 // marker (2) + length (2)
    if (size < start + identifier.length) return false
    return identifier.indices.all { this[start + it].toInt().toChar() == identifier[it] }
}

private fun ByteArray.startsWithJpegMarker(): Boolean =
    size >= 4 && this[0] == MARKER_PREFIX && (this[1].toInt() and 0xFF) == 0xD8

private const val MARKER_PREFIX = 0xFF.toByte()
private const val APP0 = 0xE0
private const val APP2 = 0xE2
private const val APP14 = 0xEE
private const val APP15 = 0xEF
private const val COM = 0xFE
private const val SOS = 0xDA

/** The NUL-terminated identifiers the three kept segments declare themselves with. */
private val JFIF_IDENTIFIER = "JFIF" + Char(0)
private val ICC_IDENTIFIER = "ICC_PROFILE" + Char(0)
private const val ADOBE_IDENTIFIER = "Adobe"

private const val TAG = "PhotoMetadataScrub"
