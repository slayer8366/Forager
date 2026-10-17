package com.zynergylabs.forager.app.photo.engine

import com.zynergylabs.forager.app.domain.PixelRect
import com.zynergylabs.forager.app.domain.PixelSize

/*
 * The lens chooser's zoom diagnostics (dispatch 2026-10-17-01, Phase 2a; RECORD -1149, -1150). Debug source set only, and
 * plain Kotlin: no camera2 type here, so every rule below runs headless in `ZoomDiagnosticsTest`. `Camera2PlatformCamera`
 * reads the phone and applies the plan; the engine formats and rate-limits the lines.
 *
 * Why this exists (Phase 1 report, R from the Android camera service source, frameworks/av): the service converts a
 * zooming crop region into a zoom ratio only for the logical camera's settings, never a lens's own
 * (`Camera3Device.cpp`, the `mZoomRatioUpdated` loop), and it folds whatever zoom ratio a lens's result carries into that
 * result's crop region (`Camera3OutputUtils.cpp`, "Physical camera continues to use SCALER_CROP_REGION"). The S22 lists
 * both a per-lens crop region and a per-lens zoom ratio, and ignored the per-lens crop (RECORD -1148). Which form it does
 * honour is what the [ZoomForm] switch lets one device run find out; no fix is chosen here (the planner's -1150).
 */

/** How the zoom inside one lens is asked of the camera: the adb `zoom_form` extra (RECORD -1150, call i). */
internal enum class ZoomForm(val id: String) {
    /** Today's form, the default: the lens's own crop region where the phone lists it per lens, else the logical zoom ratio. */
    Auto("auto"),

    /** The lens's own SCALER_CROP_REGION (the S22's form before this dispatch). */
    Crop("crop"),

    /** The lens's own CONTROL_ZOOM_RATIO equal to the crop, with its crop region at the full active array. */
    LensRatio("lens_ratio"),

    /** The logical camera's CONTROL_ZOOM_RATIO equal to the zoom in the main wide's terms (the S26's form). */
    LogicalRatio("logical_ratio"),
}

/** The extra's value as a form; null for anything else (the receiver logs it and changes nothing). */
internal fun parseZoomForm(value: String?): ZoomForm? = null

/** The form chosen by adb; process-wide, so it holds across camera opens until the process ends or `auto` is sent. */
internal object ZoomFormSwitch {
    @Volatile
    var form: ZoomForm = ZoomForm.Auto
}

/** Which zoom keys the phone offers: the per-lens ones from availablePhysicalCameraRequestKeys. */
internal data class ZoomKeys(
    val lensCropRegion: Boolean,
    val lensZoomRatio: Boolean,
    /** The logical CONTROL_ZOOM_RATIO: API 30 and later. */
    val logicalZoomRatio: Boolean,
    val logicalZoomRange: ClosedFloatingPointRange<Double>?,
)

/** What one request asks for the zoom, and in which form. [asked] is the switch; [used] what the phone could take. */
internal data class ZoomPlan(
    val asked: ZoomForm,
    val used: ZoomForm,
    val lensId: String,
    val lensCropRegion: PixelRect? = null,
    val lensZoomRatio: Double? = null,
    val logicalZoomRatio: Double? = null,
    /** Why [used] differs from [asked], or a clamp; null when neither. */
    val note: String? = null,
) {
    /** Whether the request must carry the lens's own settings (`createCaptureRequest` with the lens's id). */
    val needsLensSettings: Boolean get() = lensCropRegion != null || lensZoomRatio != null
}

/**
 * The plan for [crop] on the streaming lens, in form [asked]. A form the phone does not list falls back to [ZoomForm.Auto],
 * with the reason in [ZoomPlan.note] (logged, never silent). [lensActive] is the lens's active array; [lensZoomRange] its
 * own CONTROL_ZOOM_RATIO_RANGE.
 */
internal fun zoomPlan(
    asked: ZoomForm,
    crop: LensCrop,
    lensActive: PixelSize?,
    lensZoomRange: ClosedFloatingPointRange<Double>?,
    keys: ZoomKeys,
): ZoomPlan = ZoomPlan(asked, asked, crop.lensId)

internal const val OIS_OFF = 0
internal const val OIS_ON = 1

/**
 * Optical stabilization asked of the lens in use (RECORD -1150, call ii): ON only where the lens offers it and the phone
 * lists the key per lens (the S22); otherwise nothing is asked, and only the result's mode is logged (the S26).
 */
internal fun lensOisRequest(lensOisModes: List<Int>, lensOisKeyListed: Boolean): Int? = null

/**
 * The crop a result reports, in the streaming lens's own units, as `crop_requested` is (dispatch item 3). Under the
 * logical zoom ratio the camera service folds the logical ratio, in the main wide's terms, into the lens's result crop
 * (Phase 1, I): 2.89 on the S26's 3x lens with no crop asked (RECORD -1147's log). It is divided by the lens's own zoom.
 * The other forms report the lens's own crop already. Null [used] (no plan read) leaves the value as reported.
 */
internal fun cropInLensUnits(reported: Double?, used: ZoomForm?, lens: Lens?): Double? = reported

/** At most one line per [intervalNanos] for an unchanged key; a changed key is logged at once. */
internal class LineLimiter(private val intervalNanos: Long = DIAG_INTERVAL_NANOS) {
    fun allow(nowNanos: Long, key: Any?): Boolean = false
}

internal const val DIAG_INTERVAL_NANOS = 1_000_000_000L

/** The template's optical stabilization before anything is asked, and what was asked of the lens. */
internal data class OisAsk(val lensAsked: Int?, val lensTemplate: Int?, val logicalTemplate: Int?)

/** What a result reports for the zoom and stabilization, logical and the streaming lens's own. */
internal data class ZoomResult(
    val logicalZoomRatio: Float? = null,
    val logicalCrop: PixelRect? = null,
    val activePhysicalId: String? = null,
    /** LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_SENSOR_CROP_REGION (API 31): the crop on the active lens, in its own array. */
    val activePhysicalCrop: PixelRect? = null,
    /** Whether the result carried the streaming lens's own result at all. */
    val lensResult: Boolean = false,
    val lensCrop: PixelRect? = null,
    val lensOis: Int? = null,
    val logicalOis: Int? = null,
    val distortionMode: Int? = null,
)

/** One frame's or one shot's request and result, for the `crop_diag` line. */
internal data class FrameZoomDiag(val plan: ZoomPlan?, val ois: OisAsk?, val result: ZoomResult)

/** One camera's static zoom facts: a lens, or the logical camera with the per-lens keys it lists. */
internal data class LensStatic(
    val id: String,
    val logical: Boolean,
    val activeArray: PixelRect?,
    val preCorrectionArray: PixelRect?,
    val maxDigitalZoom: Float?,
    val zoomRatioRange: ClosedFloatingPointRange<Double>?,
    val oisModes: List<Int>,
    val distortionModes: List<Int>,
    /** The logical camera only: which of crop_region, zoom_ratio and ois it lists per lens. */
    val perLensKeys: List<String>? = null,
)

/** The `crop_diag` line: what was asked and what the result reports, with the lens's achieved crop in its own units. */
internal fun cropDiagLine(kind: String, diag: FrameZoomDiag, lens: Lens?, crop: LensCrop?): String = ""

/** The `lens_static` line, one per camera, at open. */
internal fun lensStaticLine(static: LensStatic): String = ""
