package com.zynergylabs.forager.app.data.raw

import java.io.File

/**
 * The check a written DNG must pass before it is kept (dispatch 2026-10-15-02, "The check after writing, failing
 * closed"; RECORD -1003, -1008). It re-parses the file from disk with [TiffReader], independently of the writer's own
 * idea of what it wrote, and returns the first problem it finds, or null when the file is clean.
 *
 * What it asserts, in this order:
 * 1. The file re-parses as TIFF, every IFD and every pointer-reached IFD included.
 * 2. No IFD carries a [FORBIDDEN_TAGS] tag: no Make, Model, DateTime, DateTimeOriginal or DateTimeDigitized, and no
 *    pointer to an EXIF or GPS IFD.
 * 3. Software, if present at all, is exactly "Forager".
 * 4. IFD 0's UniqueCameraModel is exactly "Forager".
 * 5. No ASCII, BYTE or UNDEFINED value anywhere contains any of the caller's forbidden strings (the phone's make,
 *    model, brand and build fingerprint, from the camera side), compared without regard to ASCII case. Pixel data is not
 *    scanned: it is reached through TileOffsets, a LONG value, and is never a BYTE value here.
 *
 * Reasons name the tag, never the matched forbidden string, so the reason can be logged without itself carrying the
 * phone's identity.
 */
internal object DngCheck {

    /** Tags that must never appear in a Forager DNG, with their names for the failure reason. */
    val FORBIDDEN_TAGS: Map<Int, String> = mapOf(
        271 to "Make",
        272 to "Model",
        306 to "DateTime",
        36867 to "DateTimeOriginal",
        36868 to "DateTimeDigitized",
        34665 to "ExifIFD",
        34853 to "GPSInfo",
    )

    const val TAG_SOFTWARE = 305
    const val TAG_UNIQUE_CAMERA_MODEL = 50708
    const val FORAGER = "Forager"

    private val SCANNED_TYPES = setOf(TiffEntry.TYPE_BYTE, TiffEntry.TYPE_ASCII, TiffEntry.TYPE_UNDEFINED)

    fun problemWith(file: File, forbiddenStrings: List<String>): String? {
        val structure = try {
            TiffReader.read(file)
        } catch (e: TiffFormatException) {
            return "the written file could not be re-read: ${e.message}"
        }

        for (ifd in structure.ifds) {
            if (ifd.label.startsWith("EXIF IFD") || ifd.label.startsWith("GPS IFD")) {
                return "an EXIF or GPS IFD is present (${ifd.label})"
            }
            for (entry in ifd.entries) {
                val name = FORBIDDEN_TAGS[entry.tag]
                if (name != null) return "forbidden tag $name (${entry.tag}) is present in ${ifd.label}"
            }
            val software = ifd.find(TAG_SOFTWARE)
            if (software != null && software.asciiOrNull() != FORAGER) {
                return "Software (305) in ${ifd.label} is not \"$FORAGER\""
            }
        }

        val model = structure.ifd0.find(TAG_UNIQUE_CAMERA_MODEL)
            ?: return "UniqueCameraModel (50708) is missing from IFD 0"
        if (model.asciiOrNull() != FORAGER) return "UniqueCameraModel (50708) is not \"$FORAGER\""

        val needles = forbiddenStrings.map { it.trim() }.filter { it.isNotEmpty() }.map { it.lowercase().toByteArray(Charsets.UTF_8) }
        if (needles.isNotEmpty()) {
            for (ifd in structure.ifds) {
                for (entry in ifd.entries) {
                    if (entry.type !in SCANNED_TYPES) continue
                    val haystack = lowercaseAscii(entry.value)
                    if (needles.any { contains(haystack, it) }) {
                        return "a forbidden string is present in tag ${entry.tag} of ${ifd.label}"
                    }
                }
            }
        }
        return null
    }

    private fun TiffEntry.asciiOrNull(): String? = if (type == TiffEntry.TYPE_ASCII) ascii() else null

    private fun lowercaseAscii(bytes: ByteArray): ByteArray = ByteArray(bytes.size) { i ->
        val c = bytes[i].toInt()
        if (c in 'A'.code..'Z'.code) (c + 32).toByte() else bytes[i]
    }

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.size > haystack.size) return false
        outer@ for (start in 0..haystack.size - needle.size) {
            for (j in needle.indices) if (haystack[start + j] != needle[j]) continue@outer
            return true
        }
        return false
    }
}
