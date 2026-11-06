package com.zynergylabs.forager.app.data.raw

import com.zynergylabs.forager.app.domain.raw.DngFailureStage
import com.zynergylabs.forager.app.domain.raw.DngImage
import com.zynergylabs.forager.app.domain.raw.DngWriteResult
import com.zynergylabs.forager.app.domain.raw.DngWriter
import java.io.File

/**
 * Forager's own DNG writer (RECORD -1009; dispatch 2026-10-15-02). Pure Kotlin, no Android imports.
 *
 * [write] does three things in order, and any failure in any of them deletes the file and returns
 * [DngWriteResult.Failed] with the cause (RECORD -1003, fail closed); it never throws:
 * 1. refuses an input above the operating limit or with mismatched arrays ([DngImageLimits]), before writing a byte;
 * 2. writes a DNG 1.4 file of the raw CFA image in tiled lossless JPEG ([DngFileWriter], [LosslessJpegEncoder]);
 * 3. re-reads the file from disk and checks it ([DngCheck]): no make, model, time, EXIF or GPS, UniqueCameraModel
 *    "Forager", and none of [forbiddenStrings] in any text or byte value.
 *
 * [forbiddenStrings] is the one addition to the contract the dispatch expects: the camera side passes the phone's make,
 * model, brand and build fingerprint. Blank strings are ignored, since they would match every file.
 *
 * Built for what stage 2 needs, a CFA image. Later stages that write a linear DNG (RAW+, RECORD -1010) are expected to
 * add their own path beside [DngFileWriter] and reuse the TIFF layout, the check and the limits; nothing here is
 * conditional on them yet.
 */
class ForagerDngWriter internal constructor(
    private val forbiddenStrings: List<String>,
    /** Test-only: runs between the write and the check, so a test can corrupt the bytes the check then reads. */
    private val beforeCheck: ((File) -> Unit)?,
) : DngWriter {

    constructor(forbiddenStrings: List<String>) : this(forbiddenStrings, null)

    override fun write(image: DngImage, destination: File): DngWriteResult {
        val refusal = try {
            DngImageLimits.problemWith(image)
        } catch (e: Exception) {
            "the input could not be validated: $e"
        }
        if (refusal != null) return failed(destination, "refused before writing: $refusal", DngFailureStage.Refused)

        try {
            DngFileWriter.write(image, destination)
        } catch (e: Exception) {
            return failed(destination, "the DNG could not be written: $e", DngFailureStage.Writing)
        } catch (e: OutOfMemoryError) {
            return failed(destination, "the DNG could not be written: out of memory", DngFailureStage.Writing)
        }

        val problem = try {
            beforeCheck?.invoke(destination)
            DngCheck.problemWith(destination, forbiddenStrings)
        } catch (e: Exception) {
            "the check could not run: $e"
        } catch (e: OutOfMemoryError) {
            "the check could not run: out of memory"
        }
        if (problem != null) return failed(destination, "the written DNG failed its check: $problem", DngFailureStage.Check)

        return DngWriteResult.Written(destination.length())
    }

    /**
     * [stage] (Amendment 2) is what the camera shows the user by: only [DngFailureStage.Check], the file written and not
     * confirmed clean (including a check that could not run), gets the owner's -1003 words.
     */
    private fun failed(destination: File, reason: String, stage: DngFailureStage): DngWriteResult.Failed {
        val gone = try {
            !destination.exists() || destination.delete()
        } catch (e: SecurityException) {
            false
        }
        return DngWriteResult.Failed(if (gone) reason else "$reason; the file could not be deleted", stage)
    }
}
