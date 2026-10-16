package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.diagnostics.sensor.LensFacts
import com.zynergylabs.forager.app.diagnostics.sensor.LensView
import com.zynergylabs.forager.app.diagnostics.sensor.MainWideChoice
import com.zynergylabs.forager.app.diagnostics.sensor.MainWideChooser
import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize
import com.zynergylabs.forager.app.domain.RAW_MAX_PIXELS
import com.zynergylabs.forager.app.domain.STANDARD_RAW_SELECTOR_BOUND
import com.zynergylabs.forager.app.domain.raw.DngDefaultCrop
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * The lens chooser on camera engine A (dispatch 2026-10-16-04; design RECORD -1103, -1104, -1105; rulings -1107). Debug
 * source set only, and plain Kotlin: no camera2 type and no Compose here, so every rule below runs headless in
 * `LensChooserTest`. What the camera does with these values is `Camera2PlatformCamera`'s, and what the screen shows is
 * `LensRow.kt`'s.
 *
 * The step path the owner approved, in short: a row of the phone's real back lenses labelled by measured zoom; a tap jumps
 * to that lens at its own zoom; a pinch zooms smoothly from the shortest lens's zoom to 2x past the longest, using the
 * longest lens at or below the zoom and cropping the rest; the last lens chosen is remembered while the app's process lives,
 * at that lens's own zoom; too close for a telephoto, the main lens is used, cropped to the zoom (-1107).
 */

/** One real back lens, as the lens chooser uses it. Built from characteristics by [buildLensSet], never hard-coded. */
internal data class Lens(
    val physicalId: String,
    /** Field of view against the main wide's ([LensView]): 1.0 for the main wide, 0.58 for the S26's ultra-wide. */
    val zoom: Double,
    val isMain: Boolean,
    val sensorOrientation: Int,
    /** The lens's own full-size JPG (RECORD -1107, "Lens's own size"): its largest 4:3 JPEG output. */
    val jpegSize: PixelSize,
    val previewSize: PixelSize,
    /** Null when this lens cannot give RAW within the operating limit: the row keeps its button and the shot is a JPG (-1103). */
    val rawSize: PixelSize?,
    /** The lens's active array, the coordinate system of its crop region (CaptureRequest.SCALER_CROP_REGION). */
    val activeArray: PixelSize?,
    /** LENS_INFO_MINIMUM_FOCUS_DISTANCE in diopters; 0 is a fixed-focus lens. */
    val minFocusDiopters: Float,
) {
    val label: String get() = lensLabel(zoom)
}

/** The phone's back lenses, shortest zoom first, exactly one of them the main wide. */
internal class LensSet(lenses: List<Lens>) {
    val lenses: List<Lens> = lenses.sortedBy { it.zoom }

    init {
        require(this.lenses.isNotEmpty()) { "a lens set has at least one lens" }
        require(this.lenses.count { it.isMain } == 1) { "a lens set has exactly one main wide" }
    }

    val main: Lens get() = lenses.single { it.isMain }

    /** The pinch's floor: the shortest lens's own zoom (0.58 on the S26, shown as .6). */
    val minZoom: Double get() = lenses.first().zoom

    /** The pinch's ceiling: 2x past the longest lens (-1104): about 10 on the S26 and 20 on the S22. */
    val maxZoom: Double get() = lenses.last().zoom * PINCH_PAST_LONGEST

    fun clamp(zoom: Double): Double = zoom.coerceIn(minZoom, maxZoom)

    /** The longest real lens whose zoom is at or below [zoom]; the shortest lens below the floor. */
    fun lensFor(zoom: Double): Lens = lenses.lastOrNull { it.zoom <= zoom + ZOOM_EPSILON } ?: lenses.first()

    fun byId(id: String?): Lens? = lenses.firstOrNull { it.physicalId == id }

    companion object {
        const val PINCH_PAST_LONGEST = 2.0
        const val ZOOM_EPSILON = 1e-6
    }
}

/**
 * The button's number (-1103): below 1 it is the tenths without the leading zero (0.58 is `.6`); from 1 up it is the
 * nearest whole number (2.89 is `3`, 4.91 is `5`, 9.99 is `10`). Measured, never a marketing figure.
 */
internal fun lensLabel(zoom: Double): String {
    if (zoom < 1.0) {
        val tenths = (zoom * 10).roundToInt()
        return if (tenths >= 10) "1" else ".$tenths"
    }
    return zoom.roundToInt().toString()
}

/** How much of [lens]'s frame a zoom crops away: 1.0 at the lens's own zoom, 2.0 at twice it. Never below 1. */
internal fun cropFor(zoom: Double, lens: Lens): Double = (zoom / lens.zoom).coerceAtLeast(1.0)

/**
 * The crop region for [crop] on an active array of [active]: centred (both phones report CENTER_ONLY cropping, the dumpsys
 * tables of 2026-10-15 and -16, READ), in the active array's own coordinates, with even sides and an even origin so the
 * crop starts on the same colour-filter phase.
 */
internal fun cropRegion(active: PixelSize, crop: Double): PixelRect {
    val width = evenAtMost((active.width / crop).roundToInt(), active.width)
    val height = evenAtMost((active.height / crop).roundToInt(), active.height)
    val left = evenFloor((active.width - width) / 2)
    val top = evenFloor((active.height - height) / 2)
    return PixelRect(left, top, left + width, top + height)
}

/**
 * The DNG's DefaultCropOrigin and DefaultCropSize for a RAW shot taken at [crop] (RECORD -1107, the owner: "Hard crop").
 * The mosaic in the file is the lens's whole untouched frame; only these two tags change, and only above crop 1.
 *
 * **A departure from the DNG specification, recorded as -1107 asks.** DNG 1.7.1.0 describes DefaultCrop as the final image
 * area inside "extra pixels around the edges" kept to "help prevent interpolation artifacts", not as a photographer's
 * crop; DefaultUserCrop (DNG 1.4) is the tag for that. The owner chose the hard crop knowing what the Phase 1 report read in
 * the editors' sources: darktable (rawspeed) cuts the image to DefaultCrop and the rest cannot be recovered there; LibRaw
 * applies it only when an application asks, and ignores a crop tighter than 0.55 of the frame by default; RawTherapee
 * ignores it; Adobe's editor was not verified. DefaultUserCrop was considered and not chosen. The pixel data is never cut,
 * so the whole frame stays in the file for any tool that ignores the tags or has them removed.
 *
 * Coordinates are the specification's: relative to the ActiveArea's top-left when there is one ([activeArea] is top,
 * left, bottom, right, as `DngImage.activeArea`), with even origin and size so the crop keeps the CFA phase.
 */
internal fun dngDefaultCrop(width: Int, height: Int, activeArea: IntArray?, crop: Double): DngDefaultCrop? {
    if (crop <= 1.0 + CROP_EPSILON) return null
    val areaWidth = activeArea?.let { it[3] - it[1] } ?: width
    val areaHeight = activeArea?.let { it[2] - it[0] } ?: height
    val sizeH = evenAtMost((areaWidth / crop).roundToInt(), areaWidth)
    val sizeV = evenAtMost((areaHeight / crop).roundToInt(), areaHeight)
    return DngDefaultCrop(
        originH = evenFloor((areaWidth - sizeH) / 2),
        originV = evenFloor((areaHeight - sizeV) / 2),
        sizeH = sizeH,
        sizeV = sizeV,
    )
}

/** A lens's own full-size JPG (-1107): its largest 4:3 JPEG size, or its largest size of any shape if it lists no 4:3. */
internal fun lensJpegSize(sizes: List<PixelSize>): PixelSize? {
    val fourByThree = sizes.filter { abs(it.width.toDouble() / it.height - 4.0 / 3.0) < 0.01 }
    return fourByThree.ifEmpty { sizes }.maxByOrNull { it.width.toLong() * it.height }
}

/** The RAW size a lens binds at: `domain/CameraFormat.kt`'s `rawCaptureSize` rule, applied to one lens's own RAW sizes. */
internal fun lensRawSize(rawCapable: Boolean, sizes: List<PixelSize>): PixelSize? {
    if (!rawCapable) return null
    val admissible = sizes.filter { it.width.toLong() * it.height <= RAW_MAX_PIXELS }
    if (admissible.isEmpty()) return null
    val bound = STANDARD_RAW_SELECTOR_BOUND
    return admissible.filter { it.width <= bound.width && it.height <= bound.height }.maxByOrNull { it.width.toLong() * it.height }
        ?: admissible.minByOrNull { it.width.toLong() * it.height }
}

/** What the characteristics say about one camera, for [buildLensSet]. Plain values, read by `Camera2PlatformCamera`. */
internal data class LensReading(
    val cameraId: String,
    val back: Boolean,
    val focalLengths: List<Float>,
    val physicalSizeMm: List<Float>?,
    val cfa: Int?,
    val jpegSizes: List<PixelSize>,
    val previewSizes: List<PixelSize>,
    val rawCapable: Boolean,
    val rawSizes: List<PixelSize>,
    val activeArray: PixelSize?,
    val minFocusDiopters: Float?,
    val sensorOrientation: Int?,
)

internal sealed interface LensSetOutcome {
    data class Built(val set: LensSet, val notes: List<String>) : LensSetOutcome

    /** No row: the reason is logged, and the engine runs as it did before the lens chooser. */
    data class NoLenses(val reason: String) : LensSetOutcome
}

/**
 * The lens set behind a logical back camera: the main wide by [MainWideChooser] (the shared rule), each other back lens by
 * its view against the main wide's ([LensView]). A lens whose view, JPEG size or viewfinder size cannot be read is left
 * out with a note rather than guessed.
 */
internal fun buildLensSet(logical: LensReading, physicals: List<LensReading>): LensSetOutcome {
    if (!logical.back) return LensSetOutcome.NoLenses("camera ${logical.cameraId} is not a back camera")
    if (physicals.isEmpty()) return LensSetOutcome.NoLenses("camera ${logical.cameraId} has no physical cameras")
    val mainChoice = MainWideChooser.choose(logical.toLensFacts(), physicals.map { it.toLensFacts() })
    val mainId = (mainChoice as? MainWideChoice.Chosen)?.cameraId
        ?: return LensSetOutcome.NoLenses("no main wide: ${(mainChoice as MainWideChoice.NotFound).reason}")
    val mainReading = physicals.single { it.cameraId == mainId }
    val mainView = LensView.of(mainReading.focalLengths, mainReading.physicalSizeMm)
        ?: return LensSetOutcome.NoLenses("the main wide $mainId's view cannot be read")
    val notes = mutableListOf<String>()
    val lenses = physicals.mapNotNull { reading ->
        if (!reading.back) {
            notes += "camera ${reading.cameraId} is not a back lens; left out"
            return@mapNotNull null
        }
        val view = LensView.of(reading.focalLengths, reading.physicalSizeMm)
        val jpeg = lensJpegSize(reading.jpegSizes)
        val preview = jpeg?.let { choosePreviewSize(reading.previewSizes, it) }
        when {
            view == null -> notes += "camera ${reading.cameraId}: focal length or sensor size not read; left out"
            jpeg == null -> notes += "camera ${reading.cameraId}: no JPEG size listed; left out"
            preview == null -> notes += "camera ${reading.cameraId}: no viewfinder size at or under 1920x1080; left out"
        }
        if (view == null || jpeg == null || preview == null) return@mapNotNull null
        val raw = lensRawSize(reading.rawCapable, reading.rawSizes)
        if (raw == null) notes += "camera ${reading.cameraId}: no RAW within the operating limit; its shots are JPG"
        Lens(
            physicalId = reading.cameraId,
            zoom = if (reading.cameraId == mainId) 1.0 else view / mainView,
            isMain = reading.cameraId == mainId,
            sensorOrientation = reading.sensorOrientation ?: logical.sensorOrientation ?: 90,
            jpegSize = jpeg,
            previewSize = preview,
            rawSize = raw,
            activeArray = reading.activeArray,
            minFocusDiopters = reading.minFocusDiopters ?: 0f,
        )
    }
    if (lenses.none { it.isMain }) return LensSetOutcome.NoLenses("the main wide $mainId could not be described (${notes.joinToString("; ")})")
    return LensSetOutcome.Built(LensSet(lenses), notes)
}

private fun LensReading.toLensFacts() = LensFacts(
    cameraId = cameraId,
    inCameraIdList = null,
    focalLengths = focalLengths,
    physicalSizeMm = physicalSizeMm,
    cfa = cfa,
    pixelArray = null,
    rawSizes = rawSizes.map { "${it.width}x${it.height}" },
    capabilities = emptyList(),
)

/** The last lens chosen, for this process only (-1105, -1106): no DataStore key. A restart opens on the main wide, 1. */
internal object LensMemory {
    @Volatile
    var lastLensId: String? = null
}

/**
 * The user's choice: the lens they asked for and the zoom. A tap jumps to a lens at its own zoom; a pinch multiplies the
 * zoom, held between the floor and the ceiling, and the wanted lens follows it. Opens on the remembered lens at that lens's
 * own zoom (-1105: "Start from the chosen lens' own zoom"), or on the main wide.
 */
internal class LensChoice(val set: LensSet, rememberedId: String?) {
    var wanted: Lens = set.byId(rememberedId) ?: set.main
        private set
    var zoom: Double = wanted.zoom
        private set

    fun tap(lens: Lens) {
        wanted = set.byId(lens.physicalId) ?: return
        zoom = wanted.zoom
        LensMemory.lastLensId = wanted.physicalId
    }

    fun pinch(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        zoom = set.clamp(zoom * factor)
        wanted = set.lensFor(zoom)
        LensMemory.lastLensId = wanted.physicalId
    }
}

/** One frame's focus, from the streaming lens's own result. Diopters are LENS_FOCUS_DISTANCE; states CONTROL_AF_STATE. */
internal data class FocusReading(val diopters: Float?, val afState: Int?)

/**
 * "Too close for this telephoto" (RECORD -1107, the owner: "Use the main lens, zoomed"). The rule, chosen by the coder and
 * reported as a call beyond the dispatch:
 *
 * - It applies only to a wanted lens longer than the main wide whose closest focus is farther than the main's (both
 *   telephotos on both phones: 2.5 and 1.25 diopters against the main's 10, the dumpsys tables, READ).
 * - **On the telephoto:** autofocus that cannot reach the subject parks the lens at its near limit without reporting
 *   focus. So "too close" is [FRAMES_TO_DECIDE] frames in a row (about half a second at 30 a second) with the focus
 *   distance at or past [NEAR_LIMIT] of the lens's minimum focus distance and the AF state not focused.
 * - **On the main lens, standing in:** the main wide can focus at 10 cm, so its focus distance measures the subject. The
 *   telephoto comes back after [FRAMES_TO_DECIDE] focused frames in a row with the subject farther than the telephoto's
 *   limit by a margin ([FAR_ENOUGH]: 0.8 of its diopters, 50 cm for the 3x lens's 40 cm), so a subject near the limit does
 *   not flip back and forth.
 * - **Moving to a telephoto from the main lens** (a tap or a pinch): if the main wide's last focused reading already puts
 *   the subject closer than the telephoto can focus, the main lens is kept from the start, rather than switching to a lens
 *   that will only switch back.
 * - A frame without a focus distance never counts either way, so a phone that does not report one never switches by this.
 */
internal class CloseFocusRule {
    var tooClose: Boolean = false
        private set

    private var nearFrames = 0
    private var farFrames = 0
    private var lastMain: FocusReading? = null

    fun applies(wanted: Lens, main: Lens): Boolean =
        !wanted.isMain && wanted.zoom > main.zoom && wanted.minFocusDiopters > 0f && wanted.minFocusDiopters < main.minFocusDiopters

    /** The wanted lens changed. Returns whether [tooClose] changed. */
    fun wantedChanged(wanted: Lens, main: Lens, streaming: Lens?): Boolean {
        val before = tooClose
        nearFrames = 0
        farFrames = 0
        tooClose = if (!applies(wanted, main)) {
            false
        } else {
            val reading = lastMain
            streaming?.isMain == true && reading != null && focused(reading.afState) &&
                reading.diopters != null && reading.diopters > wanted.minFocusDiopters
        }
        return tooClose != before
    }

    /** One frame from [streaming]. Returns whether [tooClose] changed. */
    fun onFrame(streaming: Lens, wanted: Lens, main: Lens, reading: FocusReading): Boolean {
        if (streaming.isMain && focused(reading.afState) && reading.diopters != null) lastMain = reading
        if (!applies(wanted, main)) {
            val changed = tooClose
            tooClose = false
            nearFrames = 0
            farFrames = 0
            return changed
        }
        val diopters = reading.diopters ?: return false
        if (!tooClose && streaming.physicalId == wanted.physicalId) {
            nearFrames = if (diopters >= wanted.minFocusDiopters * NEAR_LIMIT && !focused(reading.afState)) nearFrames + 1 else 0
            if (nearFrames >= FRAMES_TO_DECIDE) {
                tooClose = true
                nearFrames = 0
                return true
            }
        } else if (tooClose && streaming.isMain) {
            farFrames = if (focused(reading.afState) && diopters <= wanted.minFocusDiopters * FAR_ENOUGH) farFrames + 1 else 0
            if (farFrames >= FRAMES_TO_DECIDE) {
                tooClose = false
                farFrames = 0
                return true
            }
        }
        return false
    }

    private fun focused(afState: Int?): Boolean = afState == AF_PASSIVE_FOCUSED || afState == AF_FOCUSED_LOCKED

    companion object {
        const val FRAMES_TO_DECIDE = 15
        const val NEAR_LIMIT = 1.5f
        const val FAR_ENOUGH = 0.8f

        /** CaptureResult.CONTROL_AF_STATE values, restated so this file needs no camera2 import. */
        const val AF_PASSIVE_FOCUSED = 2
        const val AF_FOCUSED_LOCKED = 4
        const val AF_NOT_FOCUSED_LOCKED = 5
        const val AF_PASSIVE_UNFOCUSED = 6
    }
}

/** The lens the camera uses: the wanted one, or the main wide standing in when the subject is too close for it. */
internal fun effectiveLens(wanted: Lens, set: LensSet, tooClose: Boolean): Lens = if (tooClose) set.main else wanted

/** What a frame is cropped to, and on which lens: the camera's side of a zoom. */
internal data class LensCrop(
    val lensId: String,
    /** The zoom asked for, in the main wide's terms: what the logical camera's zoom ratio means. */
    val zoom: Double,
    /** zoom over the lens's own zoom, at least 1. */
    val crop: Double,
    /** The crop region on the lens's active array, or null when its active array is not read. */
    val region: PixelRect?,
)

internal fun lensCrop(lens: Lens, zoom: Double): LensCrop {
    val crop = cropFor(zoom, lens)
    return LensCrop(lens.physicalId, zoom, crop, lens.activeArray?.let { cropRegion(it, crop) })
}

private fun evenFloor(value: Int): Int = value - (value and 1)

private fun evenAtMost(value: Int, limit: Int): Int = evenFloor(value.coerceIn(2, limit))

private const val CROP_EPSILON = 1e-3
