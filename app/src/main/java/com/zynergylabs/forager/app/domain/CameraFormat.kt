package com.zynergylabs.forager.app.domain

/*
 * The camera's format chip (RAW pipeline stage 2, dispatch 2026-10-15-01; RECORD -1002 to -1007, -1011). Plain Kotlin,
 * no Android imports: which format the chip offers, what it says about size, and when a RAW shot keeps only its JPG are
 * rules, and each rule has a test in `CameraFormatTest`.
 */

/**
 * What a shot saves. [Jpg] is today's photo, byte for byte, and the first-run default (RECORD -974, -1005). [Raw] is the
 * shot's JPG (the photo's preview, thumbnail and placeholder, RECORD -1004) plus one unprocessed sensor DNG that Forager
 * writes itself (RECORD -1009). The other formats the owner listed (-974: HEIF, PNG, TIF, RAW+) come in later stages.
 */
enum class CameraFormat { Jpg, Raw }

/** The chosen format, remembered across restarts in DataStore like the grid mode (RECORD -1005). */
interface CameraFormatRepository {
    suspend fun getCameraFormat(): Result<CameraFormat>

    suspend fun setCameraFormat(format: CameraFormat): Result<Unit>
}

/**
 * The RAW operating limit (CLAUDE.md, architecture: a reported range is what is possible, not what is safe to use).
 * RAW only at the app's standard photo size, about 12 MP (RECORD -655, -975). 12.6 MP admits the two test phones'
 * readings, 4000x3000 (S22 Ultra, -996) and 4080x3060 (S26 Ultra, -998), and is the same bound the writer refuses above
 * (dispatch 2026-10-15-02, "Operating limit").
 */
const val RAW_MAX_PIXELS: Long = 12_600_000L

/**
 * The RAW size this camera binds at, or null when the chip must not offer RAW: no probe yet, RAW+JPEG not reported
 * supported, no RAW size read, or no RAW size within [RAW_MAX_PIXELS].
 *
 * Which size CameraX picks is mirrored here from the selector the app binds with (`STANDARD_PHOTO_SIZE`, 4032x3024, closest
 * lower then higher): for RAW+JPEG CameraX chooses among the RAW sizes (read from the pinned 1.6.2 artifact: the input format
 * is RAW_SENSOR, the JPEG a secondary format at the same size). That the bound size is the one this returns is inferred, and
 * the device run reads it back from the log.
 */
fun rawCaptureSize(capabilities: CameraCapabilities?): PixelSize? {
    if (capabilities == null) return null
    if (capabilities.rawPlusJpegOutput !is Probed.Supported) return null
    val sizes = (capabilities.rawSizes as? Probed.Supported)?.value ?: return null
    val admissible = sizes.filter { it.width.toLong() * it.height <= RAW_MAX_PIXELS }
    if (admissible.isEmpty()) return null
    val bound = STANDARD_RAW_SELECTOR_BOUND
    val lower = admissible.filter { it.width <= bound.width && it.height <= bound.height }
    return lower.maxByOrNull { it.width.toLong() * it.height }
        ?: admissible.minByOrNull { it.width.toLong() * it.height }
}

/** The capture selector's bound, `STANDARD_PHOTO_SIZE` in `CameraXCaptureSession.kt`, restated here without Android types. */
val STANDARD_RAW_SELECTOR_BOUND = PixelSize(4032, 3024)

/**
 * The menu's approximate sizes (RECORD -975: "each format's approximate size shown in the format menu, so nobody is
 * surprised"; Amendment 1 item 11: computed from the bound size and the compression, labelled approximate). **Estimates,
 * not measurements**: no phone has saved a file of either kind for this yet. The device run reads the real sizes and these
 * constants are replaced by them (device item: "file sizes match the menu").
 *
 * - JPG: about a third of a byte per pixel, a typical camera JPEG at default quality.
 * - DNG: the sensor's samples, losslessly compressed (lossless JPEG, dispatch 2026-10-15-02). A sensor that delivers
 *   10 bits per sample with its noise typically compresses to about 1.2 bytes a pixel; the uncompressed 16-bit container
 *   would be 2 bytes a pixel.
 *
 * A RAW shot keeps both files (RECORD -1004), so its figure is the two together.
 */
const val JPG_BYTES_PER_PIXEL_ESTIMATE = 0.33
const val DNG_BYTES_PER_PIXEL_ESTIMATE = 1.2

/** Approximate bytes a shot of [format] saves at [size]. */
fun approximateShotBytes(format: CameraFormat, size: PixelSize): Long {
    val pixels = size.width.toDouble() * size.height
    val jpg = pixels * JPG_BYTES_PER_PIXEL_ESTIMATE
    return when (format) {
        CameraFormat.Jpg -> jpg.toLong()
        CameraFormat.Raw -> (jpg + pixels * DNG_BYTES_PER_PIXEL_ESTIMATE).toLong()
    }
}

/** "~4 MB": whole megabytes (10^6 bytes, as phones show storage), at least 1. */
fun approximateSizeLabel(bytes: Long): String = "~${maxOf(1L, Math.round(bytes / 1_000_000.0))} MB"

/**
 * The low-storage floor (dispatch 2026-10-15-01, item 5 of the planner's answers): below 500 MB plus the worst case for one
 * shot, a RAW shot keeps only its JPG. 500 MB is the line below which the platform itself starts warning that storage is
 * low (inferred, not read from a source here), so a RAW shot never takes the phone there.
 */
const val LOW_STORAGE_FLOOR_BYTES: Long = 500_000_000L

/**
 * The worst case for one RAW shot at [width] x [height], in bytes, counting everything that is on disk at once at its peak:
 * - the DNG at its largest. Lossless JPEG cannot spend more than 32 bits on a 16-bit sample (a 16-bit code and 16 extra
 *   bits), so 4 bytes a pixel, plus 1 MB for tags, tables and the gain maps;
 * - the JPG, bounded at 1.5 bytes a pixel, beyond anything a camera JPEG at default quality produces;
 * - both again, because `FilePhotoStore.persist` copies the scratch files into `photos/` before it releases them.
 */
fun worstCaseRawShotBytes(width: Int, height: Int): Long {
    val pixels = width.toLong() * height
    val dng = pixels * 4 + 1_000_000L
    val jpg = pixels * 3 / 2
    return 2 * (dng + jpg)
}

/** Whether a RAW shot at [width] x [height] may write its DNG with [freeBytes] free. */
fun rawShotFits(freeBytes: Long, width: Int, height: Int): Boolean =
    freeBytes >= LOW_STORAGE_FLOOR_BYTES + worstCaseRawShotBytes(width, height)
