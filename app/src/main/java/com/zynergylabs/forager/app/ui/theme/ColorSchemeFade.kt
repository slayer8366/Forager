package com.zynergylabs.forager.app.ui.theme

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import com.zynergylabs.forager.app.ui.motion.LocalReduceMotion
import com.zynergylabs.forager.app.ui.motion.MotionTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Motion Part 3, item 4 (dispatch 2026-09-28-676; the owner, RECORD -651: Night mode "Fade the colours", against the planner's
 * recommendation to keep it instant, and RECORD -652: "the night mode switch is already suggestive by virtue of a night mode being
 * active alone. The results aren't subtle, it's an entire UI shift, so the fade is permissible if it's fast and smooth, and not
 * ceremonial and boring").
 *
 * **What it was, and why it showed one muddy frame (RECORD -752, dispatch 2026-09-28-755 item 3).** Until this change every frame
 * of the blend drew the app with a colour scheme part-way between light and dark (each role lerped). Compose's `lerp(Color, Color)`
 * blends in Oklab, so the colours did not dip through a grey darker than both ends (NightModeFadeTest checks the blend's luminance
 * frame by frame). The cost was the problem: Material 3 provides the scheme through a static composition local, so every one of
 * those frames recomposed the whole app, the map screen included, and the night-mode change also restyles the map in the same
 * moment. The blend is a spring of about a tenth of a second, so on the S22 the first frame's work used most of it, and the next
 * frame already read near the end: light, one grey middle frame, dark. That is the inference from the code and from the
 * per-frame recompositions measured headless; the frame times themselves are the phone's, not measured here.
 *
 * **How it works now.** The change recomposes once. At the moment night mode changes, the screen as last drawn is kept as a
 * picture; the app switches to the new scheme at once, underneath it; and the picture fades out over the new screen on the same
 * fast spring ([MotionTokens.nightModeFadeSpec]). The fade is drawing only, with no recomposition, so each frame is cheap and the
 * spring gets its frames. The picture is held for the switch's own frame (and the next) before the fade's clock starts, so the
 * one heavy frame is spent under it rather than eating the fade. The blend is old over new, pixel by pixel, which also cannot go
 * darker than both ends. Under reduced motion there is no picture and no fade: the scheme changes at once.
 *
 * **What it rejected.** Keeping the per-frame scheme blend and making it cheaper: the scheme local is Material's and static, so
 * no screen can opt out of the recomposition. A longer blend: more frames of the same cost, and "ceremonial", against -652.
 *
 * **Device-only.** How it looks, and whether it is smooth, on the S22. The map is a SurfaceView, which the picture cannot hold (it
 * is drawn by the system, not by Compose), so over the map the picture shows the chrome only and the live map shows through its
 * hole, restyling as it did before (scout G2). If taking the picture fails or takes longer than [SNAPSHOT_TIMEOUT_MS], the change
 * is made at once and logged under [NIGHT_BLEND_LOG_TAG], never silently.
 */

/**
 * The app under [content], drawn in [darkTheme]'s colours, fading from the previous ones when [darkTheme] changes (the header
 * above). [content] is handed the night mode to draw in now, which lags [darkTheme] by the frame the picture takes; the first
 * composition is at the end already, so the app opens in its colours with no fade.
 */
@Composable
internal fun NightModeBlend(darkTheme: Boolean, content: @Composable (shownDark: Boolean) -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    var shownDark by remember { mutableStateOf(darkTheme) }
    val recording = rememberGraphicsLayer()
    var outgoing by remember { mutableStateOf<ImageBitmap?>(null) }
    val outgoingAlpha = remember { Animatable(0f) }
    val spec = MotionTokens.nightModeFadeSpec<Float>(ForagerMotionScheme)
    // Under reduced motion the change is drawn in the same frame, as it was before the fade existed, and kept in step after it.
    val drawDark = if (reduceMotion) darkTheme else shownDark
    if (reduceMotion) SideEffect { shownDark = darkTheme }
    LaunchedEffect(darkTheme, reduceMotion) {
        if (darkTheme == shownDark || reduceMotion) return@LaunchedEffect
        val picture = try {
            withTimeoutOrNull(SNAPSHOT_TIMEOUT_MS) { recording.toImageBitmap() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(NIGHT_BLEND_LOG_TAG, "Could not keep the screen as a picture for the night-mode fade; changing at once.", e)
            null
        }
        if (picture == null) {
            Log.w(NIGHT_BLEND_LOG_TAG, "No picture of the screen for the night-mode fade (failed or timed out); changed at once.")
            shownDark = darkTheme
            return@LaunchedEffect
        }
        Log.i(NIGHT_BLEND_LOG_TAG, "fading from a ${picture.width} x ${picture.height} picture of the screen")
        outgoing = picture
        outgoingAlpha.snapTo(1f)
        shownDark = darkTheme
        try {
            // The switch's frame (the one recomposition) and the one after it, under the full picture.
            withFrameNanos { }
            withFrameNanos { }
            outgoingAlpha.animateTo(0f, spec)
        } finally {
            outgoing = null
        }
    }
    Box(
        Modifier.drawWithContent {
            recording.record { this@drawWithContent.drawContent() }
            drawLayer(recording)
            val picture = outgoing
            if (picture != null) drawImage(picture, alpha = outgoingAlpha.value.coerceIn(0f, 1f))
        },
    ) {
        content(drawDark)
    }
}

/** How long taking the picture may take before the change is made at once instead (logged). */
internal const val SNAPSHOT_TIMEOUT_MS = 250L

/** Logcat tag for the night-mode fade's fallbacks. */
internal const val NIGHT_BLEND_LOG_TAG = "ForagerNightBlend"
