package com.zynergylabs.forager.app.photo

import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.io.OutputStream

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
 * ## Lossless, and streamed
 *
 * Only the JPEG's segment structure is rewritten; the entropy-coded scan data is copied through
 * byte for byte, so the image is untouched and there is no decode, no re-encode and no
 * full-resolution bitmap. [PhotoMetadataScrubTest] asserts the scan bytes are identical across the
 * scrub. Baking the rotation into the pixels instead would cost one generation of JPEG quality and
 * peak around 98 MB for a 12 MP photo, 400 MB for a 50 MP one.
 *
 * Since 2026-09-14 the walk is a stream, input file to temp file through one small buffer, and
 * never holds the image. The first version read the whole file and rebuilt it in an
 * `ArrayList<Byte>` — one object reference per byte, so 20 to 40 MB of heap for a 5 MB JPEG, per
 * shot, on the main thread — which the multi-shot camera turned from a wart into a stall. The
 * orientation reapply that follows is `ExifInterface.saveAttributes`, which rewrites the whole file
 * to reinsert APP1, so this is two passes rather than one. A single pass is possible by emitting a
 * minimal APP1 with only the orientation entry during the walk; **not done**, because these photos
 * are meant to be shared outside the app and `ExifInterface`'s own writer is the emitter other
 * readers have been tested against, whereas a hand-rolled segment that `ExifInterface` reads back
 * correctly could still trip a third-party one. Off the main thread now, two passes is cheap.
 * Recorded as the option it is.
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
 * and XMP), APP13 (IPTC), every other APPn, COM comments, **and anything after EOI**. That last one
 * was added 2026-09-14 after a verification pass found the first version copying the whole tail
 * verbatim: a Motion Photo's MP4, a depth map, an OEM trailer all live after EOI precisely because a
 * reader that stops there never sees them, which is exactly where something identifying hides. The
 * output now ends at the first EOI that follows the scans. Between the first SOS and that EOI,
 * nothing is filtered — a progressive JPEG's later tables and scans are the image, and
 * [PhotoMetadataScrubTest] holds a ten-scan fixture byte-identical across the scrub.
 *
 * ## Failure is never destructive, and never silent
 *
 * The rewrite goes to a sibling temp file and replaces the original only once it has been written
 * whole; any failure — a read error, a segment stream that does not parse, a truncated file —
 * deletes the temp, leaves the original exactly as it was, and logs at WARN. That is a deliberate
 * fail-*open* on privacy: a photo that keeps its metadata is the behaviour that existed before this
 * function, whereas a lost or truncated photo is unrecoverable field data. A non-JPEG is left alone
 * for the same reason.
 */
internal fun scrubPhotoMetadata(file: File): ScrubOutcome {
    val soi = ByteArray(2)
    val headerBytes = runCatching { file.inputStream().use { it.readFully(soi) } }.getOrElse { error ->
        Log.w(TAG, "Couldn't read '${file.name}' to scrub its metadata; leaving it as it is.", error)
        return ScrubOutcome.Failed
    }
    if (headerBytes != 2 || !soi.isJpegSoi()) {
        Log.i(TAG, "'${file.name}' is not a JPEG; leaving its bytes alone.")
        return ScrubOutcome.NotAJpeg
    }

    // Read the one tag that is put back, before anything is removed. Deliberately the raw EXIF
    // value rather than readPhotoOrientation's decomposed form: this round-trips the tag, it does
    // not interpret it, so a value this build does not recognise survives unchanged.
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
    }.getOrElse { ExifInterface.ORIENTATION_UNDEFINED }

    val temp = File(file.parentFile, "${file.name}.scrub")
    val replaced = runCatching {
        val parsed = file.inputStream().buffered().use { input ->
            temp.outputStream().buffered().use { output -> stripJpegMetadataSegments(input, output) }
        }
        check(parsed) { "'${file.name}' did not parse as a JPEG segment stream" }
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
 * Walks [input]'s segments and writes only the ones [isKeptSegment] names to [output], copying the
 * entropy-coded scan data through untouched. Returns `false` when the marker structure does not
 * parse or the stream ends inside a segment, which is the caller's signal to leave the file alone
 * rather than write something it did not understand. Holds at most one identifier prefix and one
 * copy buffer at a time; never the image.
 */
private fun stripJpegMetadataSegments(input: InputStream, output: OutputStream): Boolean {
    val soi = ByteArray(2)
    if (input.readFully(soi) != 2 || !soi.isJpegSoi()) return false
    output.write(soi)
    val buffer = ByteArray(COPY_BUFFER_BYTES)
    while (true) {
        val prefix = input.read()
        if (prefix < 0) return true // segments ended without SOS: written as they were
        if (prefix != 0xFF) return false
        val marker = input.read()
        if (marker < 0) return false
        // Standalone markers carry no length. None normally precedes SOS, but a malformed file must
        // not be silently reinterpreted, so they are handled rather than assumed absent.
        if (marker == 0x01 || marker in 0xD0..0xD9) {
            output.write(0xFF)
            output.write(marker)
            continue
        }
        val hi = input.read()
        val lo = input.read()
        if (hi < 0 || lo < 0) return false
        val length = (hi shl 8) or lo
        if (length < 2) return false
        val payload = length - 2
        if (marker == SOS) {
            // The scan header, then the image itself through its EOI, and nothing after.
            output.writeSegmentHeader(marker, hi, lo)
            if (!copyExactly(input, output, payload, buffer)) return false
            return copyScansThroughEoi(input, output, buffer)
        }
        val prefixLength = minOf(payload, MAX_IDENTIFIER_BYTES)
        val identifier = ByteArray(prefixLength)
        if (input.readFully(identifier) != prefixLength) return false
        if (isKeptSegment(marker, identifier)) {
            output.writeSegmentHeader(marker, hi, lo)
            output.write(identifier)
            if (!copyExactly(input, output, payload - prefixLength, buffer)) return false
        } else {
            if (!skipExactly(input, payload - prefixLength, buffer)) return false
        }
    }
}

/**
 * From just after the first SOS header to the first EOI, inclusive; `false` if the stream ends
 * before one. Entropy-coded data is copied until a real marker: `FF 00` is a stuffed byte and
 * `FF D0`–`FF D7` a restart marker, both part of the data, and a run of `FF` is fill before a
 * marker. The marker is then written and, if it is EOI, that is the end of the output — whatever
 * follows is dropped, which is the point. Any other marker is a length-prefixed segment of a
 * multi-scan file (a DHT or DQT between scans, another SOS, a DNL) and is copied whole, with no
 * allowlist applied: after the first SOS everything is the image. A marker straight after a table
 * segment is found by the same loop with zero data bytes in front of it.
 *
 * Byte-at-a-time reads on a buffered stream. Measured nowhere yet; a chunked scan is the obvious
 * next step if a real capture makes this slow, and it is off the main thread either way.
 */
private fun copyScansThroughEoi(input: InputStream, output: OutputStream, buffer: ByteArray): Boolean {
    while (true) {
        // Entropy-coded data up to the next real marker.
        var marker: Int
        while (true) {
            val b = input.read()
            if (b < 0) return false
            if (b != 0xFF) {
                output.write(b)
                continue
            }
            var next = input.read()
            if (next < 0) return false
            while (next == 0xFF) { // fill bytes
                output.write(0xFF)
                next = input.read()
                if (next < 0) return false
            }
            if (next == 0x00 || next in 0xD0..0xD7) {
                output.write(0xFF)
                output.write(next)
                continue
            }
            marker = next
            break
        }
        output.write(0xFF)
        output.write(marker)
        if (marker == EOI) return true
        if (marker == 0x01 || marker in 0xD0..0xD8) continue // standalone; not expected here, not reinterpreted
        val hi = input.read()
        val lo = input.read()
        if (hi < 0 || lo < 0) return false
        val length = (hi shl 8) or lo
        if (length < 2) return false
        output.write(hi)
        output.write(lo)
        if (!copyExactly(input, output, length - 2, buffer)) return false
    }
}

/** The allowlist — see [scrubPhotoMetadata]'s own doc comment for why each of the three is kept. [payloadPrefix] is the segment's first bytes after the length. */
private fun isKeptSegment(marker: Int, payloadPrefix: ByteArray): Boolean = when {
    marker !in APP0..APP15 && marker != COM -> true // DQT, DHT, SOF, DRI and friends: the image's own structure
    marker == APP0 -> payloadPrefix.startsWith(JFIF_IDENTIFIER)
    marker == APP2 -> payloadPrefix.startsWith(ICC_IDENTIFIER)
    marker == APP14 -> payloadPrefix.startsWith(ADOBE_IDENTIFIER)
    else -> false
}

/** Whether this payload begins with [identifier], the convention every APPn uses to say what it is. */
private fun ByteArray.startsWith(identifier: String): Boolean {
    if (size < identifier.length) return false
    return identifier.indices.all { this[it].toInt().toChar() == identifier[it] }
}

private fun ByteArray.isJpegSoi(): Boolean = this[0] == MARKER_PREFIX && (this[1].toInt() and 0xFF) == 0xD8

private fun OutputStream.writeSegmentHeader(marker: Int, lengthHi: Int, lengthLo: Int) {
    write(0xFF)
    write(marker)
    write(lengthHi)
    write(lengthLo)
}

/** Fills [target] as far as the stream allows and returns how many bytes it got; short means EOF. */
private fun InputStream.readFully(target: ByteArray): Int {
    var filled = 0
    while (filled < target.size) {
        val n = read(target, filled, target.size - filled)
        if (n < 0) break
        filled += n
    }
    return filled
}

/** Copies exactly [count] bytes; `false` if the stream ends first. */
private fun copyExactly(input: InputStream, output: OutputStream, count: Int, buffer: ByteArray): Boolean {
    var remaining = count
    while (remaining > 0) {
        val n = input.read(buffer, 0, minOf(remaining, buffer.size))
        if (n < 0) return false
        output.write(buffer, 0, n)
        remaining -= n
    }
    return true
}

/** Discards exactly [count] bytes; `false` if the stream ends first. `skip` may legitimately skip fewer, so it falls back to reading. */
private fun skipExactly(input: InputStream, count: Int, buffer: ByteArray): Boolean {
    var remaining = count.toLong()
    while (remaining > 0) {
        val skipped = input.skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
            continue
        }
        val n = input.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
        if (n < 0) return false
        remaining -= n
    }
    return true
}

private const val MARKER_PREFIX = 0xFF.toByte()
private const val APP0 = 0xE0
private const val APP2 = 0xE2
private const val APP14 = 0xEE
private const val APP15 = 0xEF
private const val COM = 0xFE
private const val SOS = 0xDA
private const val EOI = 0xD9
private const val COPY_BUFFER_BYTES = 8 * 1024

/** The NUL-terminated identifiers the three kept segments declare themselves with. */
private val JFIF_IDENTIFIER = "JFIF" + Char(0)
private val ICC_IDENTIFIER = "ICC_PROFILE" + Char(0)
private const val ADOBE_IDENTIFIER = "Adobe"

/** The longest of the three identifiers above; a segment's first bytes up to this many are read to classify it. */
private val MAX_IDENTIFIER_BYTES = maxOf(JFIF_IDENTIFIER.length, ICC_IDENTIFIER.length, ADOBE_IDENTIFIER.length)

private const val TAG = "PhotoMetadataScrub"
