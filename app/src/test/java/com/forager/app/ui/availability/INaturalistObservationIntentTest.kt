package com.forager.app.ui.availability

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/**
 * "View on iNaturalist" on a tapped observation's bubble: the web [Intent] it builds
 * ([inaturalistObservationIntent]) and what [launchINaturalistObservation] actually does with it —
 * same "real (shadowed) framework object, not a hand-rolled fake" reasoning [DirectionsIntentTest]
 * documents for [directionsIntent]/[launchDirections].
 *
 * A hardware-reported bug drives the app-preferring behavior tested here: a plain implicit
 * `ACTION_VIEW` against the observation's web URL always opened a browser, never the installed
 * iNaturalist app — its own intent filter for inaturalist.org isn't a verified Android App Link on
 * real devices, so nothing routes there automatically without [launchINaturalistObservation]
 * explicitly targeting [INATURALIST_PACKAGE] first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class INaturalistObservationIntentTest {

    @Test
    fun `builds an ACTION_VIEW intent to the observation's web page`() {
        val intent = inaturalistObservationIntent(123456L)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", intent.data?.scheme)
        assertEquals("https://www.inaturalist.org/observations/123456", intent.data.toString())
    }

    @Test
    fun `when the iNaturalist app is installed, launchINaturalistObservation targets it directly`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        registerFakeINaturalistApp(activity)
        // A browser is also installed here — the app must still win over it.
        registerFakeBrowser(activity)

        launchINaturalistObservation(activity, 123456L)

        val started = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals("org.inaturalist.android", started?.`package`)
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.inaturalist.org/observations/123456", started?.data.toString())
    }

    @Test
    fun `when only a browser is installed, launchINaturalistObservation falls back to the plain web intent`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        registerFakeBrowser(activity)

        launchINaturalistObservation(activity, 123456L)

        val started = Shadows.shadowOf(activity).nextStartedActivity
        assertNull("the fallback intent must not target any specific package", started?.`package`)
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("https://www.inaturalist.org/observations/123456", started?.data.toString())
    }

    /**
     * The "unsupported, not a crash" path CLAUDE.md asks for: with nothing registered to handle
     * `https:` (Robolectric's package manager starts with none), this must neither start an
     * activity nor throw — it has to say so, visibly, via the real [android.widget.Toast] call.
     */
    @Test
    fun `when nothing can handle it, launchINaturalistObservation shows a message instead of starting anything`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        launchINaturalistObservation(activity, 123456L)

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals("Nothing installed can open this observation.", ShadowToast.getTextOfLatestToast())
    }

    /**
     * A [ComponentName] built from a package name string, not [ComponentName]'s `(Context,
     * String)` overload — that overload takes the *context's own* package
     * (`com.forager.app`/whatever the test package is), which would silently register this
     * component under the wrong package and defeat the exact `setPackage("org.inaturalist.android")`
     * targeting this test exists to verify.
     */
    private fun registerFakeINaturalistApp(activity: Activity) {
        val componentName = ComponentName("org.inaturalist.android", "org.inaturalist.android.ObservationActivity")
        val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }

    private fun registerFakeBrowser(activity: Activity) {
        val componentName = ComponentName("com.example.fakebrowser", "com.example.fakebrowser.BrowserActivity")
        val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
    }
}
