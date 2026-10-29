package com.zynergylabs.forager.app

import com.zynergylabs.forager.app.LiveCounts.REGION
import com.zynergylabs.forager.app.LiveCounts.awaitUntil
import com.zynergylabs.forager.app.LiveCounts.committed
import com.zynergylabs.forager.app.LiveCounts.container
import com.zynergylabs.forager.app.LiveCounts.deleteThroughJournal
import com.zynergylabs.forager.app.LiveCounts.draft
import com.zynergylabs.forager.app.LiveCounts.finishThroughJournal
import com.zynergylabs.forager.app.LiveCounts.idleUntil
import com.zynergylabs.forager.app.LiveCounts.save
import com.zynergylabs.forager.app.LiveCounts.silentLog
import com.zynergylabs.forager.app.domain.model.LogPhoto
import com.zynergylabs.forager.app.domain.model.OfflineRegionDecision
import com.zynergylabs.forager.app.domain.model.PhotoAttachment
import com.zynergylabs.forager.app.ui.availability.AvailabilityViewModel
import com.zynergylabs.forager.app.ui.availability.requestDeleteOfflineRegion
import com.zynergylabs.forager.app.ui.log.MushroomLogViewModel
import com.zynergylabs.forager.app.ui.availability.mapLayersViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Dispatch 2026-10-28-08, the offline-map and photo halves of [LiveWaypointTrackCountsTest]: an offline map's and a
 * photo's "used in N journal entries" is the true number the moment an entry keeping it is deleted or finished. The
 * photo's count is the one its delete message, its album badge and its map bubble line all read
 * (`MushroomLogUiState.cartographyEntryPhotoReferenceCounts`).
 *
 * The photo's screen is built as `MainActivity` builds it ([createMushroomLogViewModel]). The offline map's is the Maps
 * tab's ViewModel with a fixed region list ([LiveCounts.availabilityViewModel]): the container's offline store is the
 * map library's, which these tests do not stand up, so `createAvailabilityViewModel`'s own wiring line is not what is
 * tested here, only the ViewModel's use of the counts it is given.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LiveRegionPhotoCountsTest {

    private fun regionDecision(kept: Boolean) = OfflineRegionDecision(
        offlineRegionId = REGION.id, name = REGION.name, lat = REGION.region.lat, lng = REGION.region.lng, radiusKm = REGION.region.radiusKm, kept = kept,
    )

    private val photo = LogPhoto(id = "photo-live", relativePath = "photos/photo-live.jpg", createdAtEpochMillis = 1L)
    private val attachment = PhotoAttachment(photoId = photo.id, attachedAtEpochMillis = 1_000L)

    private fun AvailabilityViewModel.regionCount() = uiState.value.offlineRegionEntryReferenceCounts[REGION.id] ?: 0
    private fun MushroomLogViewModel.photoCount() = uiState.value.cartographyEntryPhotoReferenceCounts[photo.id] ?: 0

    private fun seedPhoto() = runBlocking { container.mushroomLogRepository.addPhotoToGallery(photo).getOrThrow() }

    private fun photoScreen(): MushroomLogViewModel = createMushroomLogViewModel(container, mapLayersViewModel(), silentLog) { _, _ -> }

    @Test
    fun `deleting the one entry that keeps an offline map takes its delete message's count to 0`() {
        save(committed("entry-region").copy(offlineRegionDecisions = listOf(regionDecision(kept = true))))
        val maps = LiveCounts.availabilityViewModel(listOf(REGION))
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the region is listed and counted in 1 entry") {
            maps.uiState.value.offlineRegions.any { it.id == REGION.id } && maps.regionCount() == 1
        }

        deleteThroughJournal(journal, "entry-region")
        idleUntil { maps.regionCount() == 0 }

        assertEquals("the offline map's count still includes the deleted entry", 0, maps.regionCount())
        maps.requestDeleteOfflineRegion(REGION.id)
        assertEquals("the offline map's delete message still says used in the deleted entry", 0, maps.uiState.value.pendingOfflineRegionDelete?.entryReferenceCount)
    }

    @Test
    fun `finishing an entry that keeps an already-listed offline map takes its delete message's count to 1`() {
        save(draft("entry-region").copy(offlineRegionDecisions = listOf(regionDecision(kept = false))))
        val maps = LiveCounts.availabilityViewModel(listOf(REGION))
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the region is listed") { maps.uiState.value.offlineRegions.any { it.id == REGION.id } }
        assertEquals("positive control: a draft keeps nothing yet", 0, maps.regionCount())

        finishThroughJournal(journal, "entry-region", decide = { it.onSetOfflineRegionDecision(REGION.id, kept = true) }, saved = { e -> e.offlineRegionDecisions.single().kept })
        idleUntil { maps.regionCount() == 1 }

        assertEquals("the offline map's count does not include the entry just finished", 1, maps.regionCount())
        maps.requestDeleteOfflineRegion(REGION.id)
        assertEquals("the offline map's delete message does not count the entry just finished", 1, maps.uiState.value.pendingOfflineRegionDelete?.entryReferenceCount)
    }

    @Test
    fun `deleting the one entry that keeps a photo takes its count to 0, for its badge, bubble and delete message`() {
        seedPhoto()
        save(committed("entry-photo").copy(photos = listOf(attachment)))
        val album = photoScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the photo is listed and counted in 1 entry") {
            album.uiState.value.galleryPhotos.any { it.photo.id == photo.id } && album.photoCount() == 1
        }

        deleteThroughJournal(journal, "entry-photo")
        idleUntil { album.photoCount() == 0 }

        assertEquals("the photo's count still includes the deleted entry", 0, album.photoCount())
        album.requestDeleteGalleryPhoto(photo.id)
        assertEquals("the photo's delete message still says used in the deleted entry", 0, album.uiState.value.pendingPhotoDelete?.entryReferenceCount)
    }

    @Test
    fun `finishing an entry that keeps an already-listed photo takes its count to 1, for its badge, bubble and delete message`() {
        seedPhoto()
        save(draft("entry-photo"))
        val album = photoScreen()
        val journal = LiveCounts.cartographyViewModel()
        awaitUntil("positive control: the photo is listed") { album.uiState.value.galleryPhotos.any { it.photo.id == photo.id } }
        assertEquals("positive control: a draft keeps nothing yet", 0, album.photoCount())

        finishThroughJournal(journal, "entry-photo", decide = { it.onToggleKeptPhoto(photo.id) }, saved = { e -> e.photos.any { it.photoId == photo.id } })
        idleUntil { album.photoCount() == 1 }

        assertEquals("the photo's count does not include the entry just finished", 1, album.photoCount())
        album.requestDeleteGalleryPhoto(photo.id)
        assertEquals("the photo's delete message does not count the entry just finished", 1, album.uiState.value.pendingPhotoDelete?.entryReferenceCount)
    }
}
