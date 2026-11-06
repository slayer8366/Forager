package com.zynergylabs.forager.app.domain.raw

// The contract between the camera side (dispatch 2026-10-15-01, part A) and Forager's own DNG writer (dispatch
// 2026-10-15-02, part B), copied exactly as "The contract" in 2026-10-15-02 states it (records-after-173 f557dc65), so
// that part A compiles against it before the writer lands. Plain Kotlin, no Android imports. Not to be changed here
// without the planner: the writer's branch owns this package.

enum class CfaPattern { RGGB, GRBG, GBRG, BGGR }

data class URational(val numerator: Long, val denominator: Long)
data class SRational(val numerator: Int, val denominator: Int)

/** One illuminant's calibration, as the camera reports it. Matrices are row-major, 3x3. */
data class DngCalibration(
    val illuminant: Int,                       // EXIF LightSource code, e.g. 21 = D65, 17 = Standard A
    val colorMatrix: List<SRational>,          // required, 9 values
    val forwardMatrix: List<SRational>? = null,
    val cameraCalibration: List<SRational>? = null,
)

/** Per-colour-channel gain grid, in the camera's lens-shading-map layout (R, Geven, Godd, B). */
data class LensShadingMap(val columns: Int, val rows: Int, val gains: FloatArray)

data class DngImage(
    val width: Int,
    val height: Int,
    /** Row-major, one sample per pixel, unsigned 16-bit in a ShortArray, low-justified as the sensor delivers it. */
    val samples: ShortArray,
    val cfa: CfaPattern,
    val blackLevel: List<Float>,               // 4 values, in CFA order
    val whiteLevel: Int,
    val activeArea: IntArray? = null,          // top, left, bottom, right
    val calibration1: DngCalibration,
    val calibration2: DngCalibration? = null,
    val asShotNeutral: List<URational>,        // 3 values
    val noiseProfile: List<Double>? = null,    // pairs (S, O) per CFA channel
    val lensShading: LensShadingMap? = null,
    val orientation: Int,                      // TIFF Orientation 1..8
    val baselineExposure: SRational? = null,
)

sealed interface DngWriteResult {
    data class Written(val bytes: Long) : DngWriteResult
    /**
     * [stage] says where the write failed, so the camera can tell the user why without reading [reason]'s free text
     * (dispatch 2026-10-15-01, Amendment 2; RECORD -1003, -1018). Added after the contract was agreed, nullable and null
     * by default, so every existing caller is unchanged; null means a writer that does not say.
     */
    data class Failed(val reason: String, val stage: DngFailureStage? = null) : DngWriteResult
}

interface DngWriter {
    /** Writes [image] to [destination], then re-reads it and checks it. On any failure the file is deleted and Failed returned. Never throws. */
    fun write(image: DngImage, destination: java.io.File): DngWriteResult
}

/**
 * Where a DNG write failed (Amendment 2). Only [Check] means the file was written and could not be confirmed clean of
 * camera details, which is what the owner's -1003 message says; the others are a file that could not be made at all.
 */
enum class DngFailureStage {
    /** The input was refused before a byte was written: over the operating limit, or arrays that do not match. */
    Refused,

    /** Writing the file failed: an I/O error, or memory. */
    Writing,

    /** The file was written, and the check after writing failed or could not run: it is not confirmed clean. */
    Check,
}
