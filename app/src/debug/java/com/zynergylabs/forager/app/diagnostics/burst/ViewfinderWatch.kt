package com.zynergylabs.forager.app.diagnostics.burst

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import java.lang.ref.WeakReference

/**
 * Which activity is in front, so the fast-burst test (dispatch 2026-10-15-09) can find the camera dialog's viewfinder and
 * watch it: "Log any viewfinder freeze or blink the user would see, as far as the app can tell", and "the time until the
 * viewfinder is live again".
 *
 * The probe starts from a broadcast, with only the application context, and the platform offers no way to ask for the
 * resumed activity after the fact, so this records it as activities resume. Installed from the debug build's
 * `DebugDiagnostics.install(context)`, the one debug hook `ForagerApplication` already calls at start-up; it holds a weak
 * reference and does nothing else. A release build has neither this class nor the call.
 */
internal object ResumedActivityTracker : Application.ActivityLifecycleCallbacks {
    @Volatile private var resumed: WeakReference<Activity>? = null

    fun install(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this) // one registration per application, however often install runs
        application.registerActivityLifecycleCallbacks(this)
    }

    fun current(): Activity? = resumed?.get()

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}

/**
 * The camera dialog's [PreviewView], found by walking [root]'s views. The dialog draws on the activity's own window (see
 * `InAppCameraDialog`), and Compose's `AndroidView` hosts the PreviewView as an ordinary child view, so a walk from the
 * window's decor view reaches it. The first one found; null when there is none.
 */
internal fun findPreviewView(root: View): PreviewView? {
    if (root is PreviewView) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) findPreviewView(root.getChildAt(i))?.let { return it }
    }
    return null
}
