package com.forager.app.ui.availability

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import com.forager.app.domain.model.LatLng
import com.forager.app.domain.model.PlannedTrip
import java.time.LocalDate
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
 * The "Directions" action on a [PlannedTripRow]: the `geo:` [Intent] it builds ([directionsIntent])
 * and what happens when [launchDirections] actually fires it.
 *
 * `Intent`/`Context`/`PackageManager` are real (shadowed) Android framework objects here, not
 * hand-rolled fakes — this is the same reason [com.forager.app.data.repository.RoomPlannedTripRepositoryTest]
 * uses a real Room database instead of a fake DAO: the thing worth testing is what the framework
 * actually does with what this code hands it, including the "no app can handle this" path
 * launching a real app can't be driven under Robolectric, but what *did* get fired (or didn't) can
 * be read back from [Shadows.shadowOf], per CLAUDE.md's "assert on actual output, not a proxy for
 * it".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DirectionsIntentTest {

    private val trip = PlannedTrip(
        id = "trip-1",
        name = "Chanterelle Ridge",
        location = LatLng(45.4, -122.7),
        date = LocalDate.of(2026, 9, 1),
    )

    @Test
    fun `builds an ACTION_VIEW geo intent carrying the trip's coordinates and name`() {
        val intent = directionsIntent(trip)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("geo", intent.data?.scheme)
        assertEquals("geo:0,0?q=45.4,-122.7(Chanterelle%20Ridge)", intent.data.toString())
    }

    /**
     * A name with characters that are meaningful in a URI has to survive round-tripping through
     * one. `'` is left literal rather than percent-encoded — that is [Uri.encode]'s own documented
     * behavior (it treats `!()*'~._-` as unreserved), not a gap in this code, so the expectation
     * here is pinned to what the platform actually does rather than what might look "more encoded".
     */
    @Test
    fun `a trip name with space and ampersand is percent-encoded correctly`() {
        val trickyTrip = trip.copy(name = "Mom & Dad's Spot")

        val intent = directionsIntent(trickyTrip)

        assertEquals("geo:0,0?q=45.4,-122.7(Mom%20%26%20Dad's%20Spot)", intent.data.toString())
    }

    @Test
    fun `a negative latitude and longitude are carried through unencoded (no plus-sign mangling)`() {
        val southernTrip = trip.copy(location = LatLng(-33.8688, -70.6483))

        val intent = directionsIntent(southernTrip)

        assertEquals("geo:0,0?q=-33.8688,-70.6483(Chanterelle%20Ridge)", intent.data.toString())
    }

    @Test
    fun `when a maps app is registered, launchDirections starts it with the built intent`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        registerFakeMapsApp(activity)

        launchDirections(activity, trip)

        val started = Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals("geo:0,0?q=45.4,-122.7(Chanterelle%20Ridge)", started?.data.toString())
    }

    /**
     * The "unsupported, not a crash" path CLAUDE.md asks for: with no app registered to handle a
     * `geo:` intent (Robolectric's package manager starts with none), this must neither start an
     * activity nor throw — it has to say so, visibly, via the real [android.widget.Toast] call.
     */
    @Test
    fun `when no maps app is registered, launchDirections shows a message instead of starting anything`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()

        launchDirections(activity, trip)

        assertNull(Shadows.shadowOf(activity).nextStartedActivity)
        assertEquals("No maps app is installed to show directions.", ShadowToast.getTextOfLatestToast())
    }

    private fun registerFakeMapsApp(activity: Activity) {
        val componentName = ComponentName(activity, "com.example.fakemaps.MapsActivity")
        val shadowPackageManager = Shadows.shadowOf(activity.packageManager)
        shadowPackageManager.addActivityIfNotPresent(componentName)
        shadowPackageManager.addIntentFilterForActivity(
            componentName,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("geo")
            },
        )
    }
}
