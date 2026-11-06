package com.zynergylabs.forager.app.ui.motion

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Whether Reduce Motion should apply, per docs/motion-spec.md §4. Android's "Remove animations"
 * accessibility/developer setting sets both [Settings.Global.ANIMATOR_DURATION_SCALE] and
 * [Settings.Global.TRANSITION_ANIMATION_SCALE] to 0 together, but they are two independent
 * settings; either one alone at 0 is treated as the user's intent to reduce motion.
 */
fun isReduceMotionEnabled(contentResolver: ContentResolver): Boolean {
    val animatorScale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    val transitionScale = Settings.Global.getFloat(contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
    return animatorScale == 0f || transitionScale == 0f
}

/**
 * Whether Reduce Motion is active for the current composition. Defaults to false; the app root
 * provides the real value from [isReduceMotionEnabled] against the device's ContentResolver
 * ([ProvideReduceMotion], called by `ForagerTheme`, so every screen under the app's theme reads the
 * phone's setting). A composable tested or previewed outside `ForagerTheme` sees `false`.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * Provides [LocalReduceMotion] from the phone's own animation settings (docs/motion-spec.md §4; the
 * owner, RECORD -651: "Yes, follow it"). Read once on entry and again whenever either setting
 * changes while the app is running, so turning animations off in the system Settings and coming
 * back takes effect without a restart. Wired by `ForagerTheme`, the app's one theme root, so it is
 * app-wide without each screen asking (dispatch 2026-09-28-652, item 1).
 *
 * What a reader of the local does with it is a mapping, not a kill switch: under it things fade or
 * simply appear, and every change still shows (§4).
 */
@Composable
fun ProvideReduceMotion(content: @Composable () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    var reduceMotion by remember(resolver) { mutableStateOf(isReduceMotionEnabled(resolver)) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduceMotion = isReduceMotionEnabled(resolver)
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.TRANSITION_ANIMATION_SCALE), false, observer)
        // Read again once the observer is registered, so a change between the first read and the registration is not lost.
        reduceMotion = isReduceMotionEnabled(resolver)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion, content = content)
}

// MotionTreatment, ReducedMotionTreatment and reducedMotionEquivalent were removed on 2026-10-08 with no production caller
// (motion Part 3, Amendment 1, RECORD -681: "Use it, then prune"). docs/motion-spec.md §4's table is the rule they encoded.
