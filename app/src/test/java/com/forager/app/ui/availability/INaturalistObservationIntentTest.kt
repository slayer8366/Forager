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
 * "View on iNaturalist" on a tapped observation's [ObservationDetailDialog]: the web [Intent] it
 * builds ([inaturalistObservationIntent]) and what [launchINaturalistObservation] actually does
 * with it — same "real (shadowed) framework object, not a hand-rolled fake" reasoning
 * [DirectionsIntentTest] documents for [directionsIntent]/[launchDirections].
 *
 * A plain `https://` URL rather than a scheme this app owns: iNaturalist registers
 * inaturalist.org as a verified Android App Link, so the OS itself is what decides whether the
 * installed iNaturalist app or a browser handles it — this project has no way to force that
 * choice, and [inaturalistObservationIntent]'s own doc comment explains why it doesn't try.
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
    fun `when a handler is registered, launchINaturalistObservation starts it with the built intent`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        registerFakeINaturalistApp(activity)

        launchINaturalistObservation(activity, 123456L)

        val started = Shadows.shadowOf(activity).nextStartedActivity
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

    private fun registerFakeINaturalistApp(activity: Activity) {
        val componentName = ComponentName(activity, "org.inaturalist.android.ObservationActivity")
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
