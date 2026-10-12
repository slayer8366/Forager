package com.zynergylabs.forager.app

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.zynergylabs.forager.app.domain.ErrorLog
import com.zynergylabs.forager.app.domain.FixProvider
import com.zynergylabs.forager.app.domain.LocationFix
import com.zynergylabs.forager.app.domain.LocationTracker
import com.zynergylabs.forager.app.domain.model.LatLng
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.MushroomLogEntry
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-09-28-312, item 10: the Journal's delete hooks are connected to the Maps tab.
 *
 * The two hooks (`onFindDeleted`, `onPhotoDeleted`) used to be two lines inside `MainActivity`'s ViewModel
 * factory, and deleting either left the suite green: every test built its own ViewModel and passed the hook
 * itself, so none could tell whether `MainActivity` did. They now sit in [createMushroomLogViewModel], the
 * one function `MainActivity` builds the ViewModel with. This test calls that function with the real
 * [AppContainer] (over the real database), takes a real find and a real album photo through the ViewModel's
 * own request-and-commit calls, and reads the Maps tab's snapshot ([AvailabilityViewModel]'s `mapRecords`),
 * which only the hook edits: the screen's committed-id filter does not touch the snapshot, so a missing hook
 * cannot hide behind it.
 *
 * Revert-checked against the removal of each hook's line in `MushroomLogViewModelFactory.kt`, the line
 * `MainActivity.kt:138` and `:140` became.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MushroomLogViewModelWiringTest {

    private val app = ApplicationProvider.getApplicationContext<ForagerApplication>()

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(20)
        }
        throw AssertionError("timed out waiting for: $what")
    }

    private fun rig(): Pair<AvailabilityViewModel, MushroomLogViewModel> {
        val container = app.container
        val availability = mapLayersViewModel(getMapRecords = { container.getMapRecordsUseCase() })
        val log = createMushroomLogViewModel(container, availability, ErrorLog { _, _, _ -> }) { _, _ -> }
        return availability to log
    }

    @Test
    fun `a find deleted through the Journal's ViewModel leaves the Maps tab's records`() {
        val find = MushroomLogEntry.draft(id = "wired-find", location = LatLng(45.3, -122.6), date = LocalDate.of(2026, 9, 20)).copy(isDraft = false)
        runBlocking { app.container.mushroomLogRepository.save(find).getOrThrow() }
        val (availability, log) = rig()
        availability.onMapShown()
        awaitUntil("the find is on the map") { availability.uiState.value.mapRecords.findMarkers.any { it.recordId == find.id } }
        awaitUntil("the Journal has loaded the find") { log.uiState.value.entries.any { it.id == find.id } }

        log.requestDeleteEntry(find.id)
        awaitUntil("the find is pending") { log.uiState.value.pendingDelete?.item?.id == find.id }
        log.commitDeleteEntry(find.id)
        awaitUntil("the delete finished") { runBlocking { app.container.mushroomLogRepository.getAll().getOrThrow() }.none { it.id == find.id } }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the snapshot still holds the deleted find: onFindDeleted is not wired", emptyList<String>(), availability.uiState.value.mapRecords.findMarkers.map { it.recordId })
    }

    @Test
    fun `an album photo deleted through the Journal's ViewModel leaves the Maps tab's records`() {
        val photo = LogPhoto(id = "wired-photo", relativePath = "photos/wired-photo.jpg", createdAtEpochMillis = 1L, latitude = 45.3, longitude = -122.6)
        runBlocking { app.container.mushroomLogRepository.addPhotoToGallery(photo).getOrThrow() }
        val (availability, log) = rig()
        availability.onMapShown()
        awaitUntil("the photo is on the map") { availability.uiState.value.mapRecords.photoMarkers.any { it.recordId == photo.id } }
        awaitUntil("the Journal has loaded the photo") { log.uiState.value.galleryPhotos.any { it.photo.id == photo.id } }

        log.requestDeleteGalleryPhoto(photo.id)
        awaitUntil("the photo is pending") { log.uiState.value.pendingPhotoDelete?.item?.photo?.id == photo.id }
        log.commitDeleteGalleryPhoto(photo.id)
        awaitUntil("the delete finished") { runBlocking { app.container.mushroomLogRepository.getAllPhotos().getOrThrow() }.none { it.photo.id == photo.id } }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the snapshot still holds the deleted photo: onPhotoDeleted is not wired", emptyList<String>(), availability.uiState.value.mapRecords.photoMarkers.map { it.recordId })
    }

    // ── Dispatch 2026-09-28-527: a new find's location is a GPS fix or none ──
    //
    // The same wiring MainActivity uses: the Journal reads the Maps tab's gated fix through
    // createMushroomLogViewModel's currentFix, and the Maps tab's own live collection decides what that is.

    private fun startFindWith(provider: FixProvider): LatLng? {
        val fixes = MutableSharedFlow<LocationFix>(replay = 1)
        val availability = mapLayersViewModel(locationTracker = object : LocationTracker {
            override val fixes: Flow<LocationFix> = fixes
        })
        availability.onEnteredForeground()
        val log = createMushroomLogViewModel(app.container, availability, ErrorLog { _, _, _ -> }) { _, _ -> }
        val now = System.currentTimeMillis() / 1_000L * 1_000L
        val fix = LocationFix.Update(
            lat = 45.52, lng = -122.68, altitude = null, accuracyMeters = 15f,
            timestampEpochMillis = if (provider == FixProvider.NETWORK) now + 123 else now, provider = provider,
        )
        fixes.tryEmit(fix)
        awaitUntil("the Maps tab has taken the $provider fix") { availability.uiState.value.let { it.liveFix == fix || it.approximateFix == fix } }

        log.onStartNewEntry(null, LocalDate.of(2026, 10, 5))
        awaitUntil("the find has started") { log.uiState.value.editingEntry != null }
        return log.uiState.value.editingEntry?.foundAt
    }

    @Test
    fun `a find started with a 15 m network fix in hand has no location`() {
        assertNull(startFindWith(FixProvider.NETWORK))
    }

    @Test
    fun `a find started with a 15 m fix from an unknown provider in hand has no location`() {
        assertNull(startFindWith(FixProvider.UNKNOWN))
    }

    /** The control: the same fix from GPS is the find's location, as today. */
    @Test
    fun `a find started with a 15 m GPS fix in hand takes its location`() {
        assertEquals(LatLng(45.52, -122.68), startFindWith(FixProvider.GPS))
    }
}
